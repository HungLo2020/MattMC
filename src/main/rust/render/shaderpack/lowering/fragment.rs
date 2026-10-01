//! Fragment-surface lowering: outputs, material coordinates, target sampling and storage externalization.

use super::*;

/// The selected Rust runtime generates the semantic colored-light occupancy
/// volume before source terrain/shadow passes execute. Legacy shadow sources
/// that populate the same volume with `imageStore` therefore need their
/// storage mutation externalized during source lowering: retaining it would
/// race the Rust-owned sampled volume in the very draw that consumes it.
///
/// This is deliberately narrow. Only standalone writes to declared
/// `writeonly uimage3D` resources are externalized; any other storage-image
/// form or expression is rejected so a pack cannot silently lose unrelated
/// shader behavior.
pub(super) fn externalized_shadow_storage_roles(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
    owned_storage_bindings: &TerrainSourceResourceBindings,
) -> GalResult<Vec<TerrainSourceResourceRole>> {
    let mut roles = BTreeSet::new();
    for source in [vertex, fragment] {
        for line in source.expanded_source().lines() {
            let Some(declaration) = parse_opaque_resource_declaration(line.trim())? else {
                continue;
            };
            if declaration.kind != TerrainSourceOpaqueResourceKind::StorageImage
                || declaration.type_name != "uimage2D"
                || !declaration
                    .qualifiers
                    .split_whitespace()
                    .any(|qualifier| qualifier == "writeonly")
            {
                continue;
            }
            let Some(role) = owned_storage_bindings.role_for(&declaration.name) else {
                continue;
            };
            if role == TerrainSourceResourceRole::PuddleOccupancy {
                roles.insert(role);
            }
        }
    }
    Ok(roles.into_iter().collect())
}

pub(super) fn externalize_owned_semantic_storage_writes(
    source: &PreprocessedShaderSource,
    owned_storage_roles: &TerrainSourceResourceBindings,
) -> GalResult<PreprocessedShaderSource> {
    let mut externalized = std::collections::BTreeSet::new();
    for line in source.expanded_source().lines() {
        let Some(declaration) = parse_opaque_resource_declaration(line.trim())? else {
            continue;
        };
        if declaration.kind != TerrainSourceOpaqueResourceKind::StorageImage {
            continue;
        }
        let writeonly = declaration
            .qualifiers
            .split_whitespace()
            .any(|qualifier| qualifier == "writeonly");
        let semantic_writer = match declaration.type_name.as_str() {
            // The existing colored-light runtime owns its D3 occupancy field.
            "uimage3D" => true,
            // A 2D image is externalizable only when pack configuration maps
            // this exact source name to the installed puddle semantic writer.
            "uimage2D" => {
                owned_storage_roles.role_for(&declaration.name)
                    == Some(TerrainSourceResourceRole::PuddleOccupancy)
            }
            _ => false,
        };
        if !writeonly || !semantic_writer {
            return Err(GalError::unsupported_feature(format!(
                "source storage image '{}' requires an explicit Rust semantic writer",
                declaration.name
            )));
        }
        externalized.insert(declaration.name);
    }
    if externalized.is_empty() {
        return Ok(source.clone());
    }

    let rewritten =
        rewrite_externalized_image_store_calls(source.expanded_source(), &externalized)?;
    let mut without_declarations = String::with_capacity(rewritten.len());
    for line in rewritten.lines() {
        let Some(declaration) = parse_opaque_resource_declaration(line.trim())? else {
            without_declarations.push_str(line);
            without_declarations.push('\n');
            continue;
        };
        if externalized.contains(&declaration.name) {
            without_declarations.push_str("// Rust-owned semantic storage update\n");
        } else {
            without_declarations.push_str(line);
            without_declarations.push('\n');
        }
    }
    for name in &externalized {
        if glsl_identifiers(&without_declarations).contains(name) {
            return Err(GalError::unsupported_feature(format!(
                "source storage image '{name}' has unsupported uses outside standalone imageStore calls"
            )));
        }
    }
    source.rewritten_for_lowering(without_declarations)
}

pub(super) fn rewrite_externalized_image_store_calls(
    source: &str,
    externalized: &std::collections::BTreeSet<String>,
) -> GalResult<String> {
    let mut output = String::with_capacity(source.len());
    let mut cursor = 0usize;
    while let Some(relative) = source[cursor..].find("imageStore") {
        let start = cursor + relative;
        let name_end = start + "imageStore".len();
        let before_is_identifier = start > 0 && is_identifier_byte(source.as_bytes()[start - 1]);
        let after_is_identifier = source
            .as_bytes()
            .get(name_end)
            .is_some_and(|byte| is_identifier_byte(*byte));
        if before_is_identifier || after_is_identifier {
            output.push_str(&source[cursor..name_end]);
            cursor = name_end;
            continue;
        }
        let mut open = name_end;
        while source
            .as_bytes()
            .get(open)
            .is_some_and(|byte| byte.is_ascii_whitespace())
        {
            open += 1;
        }
        if source.as_bytes().get(open) != Some(&b'(') {
            output.push_str(&source[cursor..name_end]);
            cursor = name_end;
            continue;
        }
        let mut argument = open + 1;
        while source
            .as_bytes()
            .get(argument)
            .is_some_and(|byte| byte.is_ascii_whitespace())
        {
            argument += 1;
        }
        let argument_end = source[argument..]
            .find(|character: char| !is_identifier_byte(character as u8))
            .map(|offset| argument + offset)
            .unwrap_or(source.len());
        let target = &source[argument..argument_end];
        if target.is_empty() || !externalized.contains(target) {
            return Err(GalError::unsupported_feature(
                "shadow source imageStore target has no Rust-owned semantic writer",
            ));
        }
        let mut depth = 0u32;
        let mut end = open;
        loop {
            let byte = *source.as_bytes().get(end).ok_or_else(|| {
                GalError::invalid_argument("shadow source imageStore call is unterminated")
            })?;
            match byte {
                b'(' => depth = depth.saturating_add(1),
                b')' => {
                    depth = depth.checked_sub(1).ok_or_else(|| {
                        GalError::invalid_argument(
                            "shadow source imageStore parentheses are invalid",
                        )
                    })?;
                    if depth == 0 {
                        end += 1;
                        break;
                    }
                }
                _ => {}
            }
            end += 1;
        }
        let mut statement_end = end;
        while source
            .as_bytes()
            .get(statement_end)
            .is_some_and(|byte| byte.is_ascii_whitespace())
        {
            statement_end += 1;
        }
        if source.as_bytes().get(statement_end) != Some(&b';') {
            return Err(GalError::unsupported_feature(
                "shadow source imageStore must be a standalone statement",
            ));
        }
        output.push_str(&source[cursor..start]);
        output.push_str("/* Rust-owned semantic storage update */");
        cursor = end;
    }
    output.push_str(&source[cursor..]);
    Ok(output)
}

pub(super) fn lower_terrain_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<LoweredTerrainFragmentSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    let uses_legacy_fog = lower_legacy_fog(&mut lowered);
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        ("shadow2D", "vulkanic_source_shadow2D"),
        ("shadow2DLod", "vulkanic_source_shadow2DLod"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    let mut outputs = Vec::new();
    for output in [
        TerrainFragmentOutput::LitColor,
        TerrainFragmentOutput::MaterialAuxiliary,
        TerrainFragmentOutput::ViewSpaceNormal,
    ] {
        let (rewritten, occurrences) =
            replace_fragment_output(&lowered, output.legacy_index(), output.semantic_name())?;
        lowered = rewritten;
        if occurrences > 0 {
            outputs.push(output);
        }
    }
    if contains_fragment_output(&lowered)? {
        return Err(GalError::unsupported_feature(format!(
            "terrain fragment '{}' writes an unsupported gl_FragData index",
            source.entry_path()
        )));
    }
    if outputs.is_empty() {
        return Err(GalError::invalid_argument(format!(
            "terrain fragment '{}' has no named terrain output to lower",
            source.entry_path()
        )));
    }
    let declarations = outputs
        .iter()
        .map(|output| {
            format!(
                "layout(location = {}) out vec4 {};\n",
                output.legacy_index(),
                output.semantic_name()
            )
        })
        .collect::<String>();
    if uses_legacy_fog {
        lowered = insert_after_version(&lowered, LEGACY_FOG_SEMANTIC_PREAMBLE)?;
    }
    // Source terrain fragments use OpenGL's lower-left gl_FragCoord contract.
    // Keep that source convention explicit for every world-material writer;
    // Vulkan's negative viewport otherwise inverts screen-space water/fog
    // reconstruction while geometry itself remains correctly transformed.
    lowered = lower_world_material_fragment_coordinates(lowered, uniform_contract, source.world_custom_samplers())?;
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    lowered = insert_after_version(&lowered, &declarations)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredTerrainFragmentSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs,
        remaining_dialect,
    })
}

pub(super) fn lower_textured_material_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<LoweredTexturedMaterialFragmentSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    let uses_legacy_fog = lower_legacy_fog(&mut lowered);
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        ("shadow2D", "vulkanic_source_shadow2D"),
        ("shadow2DLod", "vulkanic_source_shadow2DLod"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    let mut outputs = Vec::new();
    for output in [
        TexturedMaterialFragmentOutput::LitColor,
        TexturedMaterialFragmentOutput::MaterialAuxiliary,
        TexturedMaterialFragmentOutput::TranslucencyAuxiliary,
    ] {
        let (rewritten, occurrences) =
            replace_fragment_output(&lowered, output.legacy_index(), output.semantic_name())?;
        lowered = rewritten;
        if occurrences > 0 {
            outputs.push(output);
        }
    }
    if contains_fragment_output(&lowered)? {
        return Err(GalError::unsupported_feature(format!(
            "textured material fragment '{}' writes an unsupported gl_FragData index",
            source.entry_path()
        )));
    }
    let required_outputs = [
        TexturedMaterialFragmentOutput::LitColor,
        TexturedMaterialFragmentOutput::MaterialAuxiliary,
        TexturedMaterialFragmentOutput::TranslucencyAuxiliary,
    ];
    if required_outputs
        .iter()
        .any(|output| !outputs.contains(output))
    {
        return Err(GalError::invalid_argument(format!(
            "textured material fragment '{}' lacks one or more required named outputs",
            source.entry_path()
        )));
    }
    let declarations = outputs
        .iter()
        .map(|output| {
            format!(
                "layout(location = {}) out vec4 {};\n",
                output.legacy_index(),
                output.semantic_name()
            )
        })
        .collect::<String>();
    if uses_legacy_fog {
        lowered = insert_after_version(&lowered, LEGACY_FOG_SEMANTIC_PREAMBLE)?;
    }
    lowered = lower_world_material_fragment_coordinates(lowered, uniform_contract, source.world_custom_samplers())?;
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    lowered = insert_after_version(&lowered, &declarations)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredTexturedMaterialFragmentSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs,
        remaining_dialect,
    })
}

pub(super) fn lower_weather_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<LoweredWeatherFragmentSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    let uses_legacy_fog = lower_legacy_fog(&mut lowered);
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        ("shadow2D", "vulkanic_source_shadow2D"),
        ("shadow2DLod", "vulkanic_source_shadow2DLod"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = replace_identifier(&lowered, "varying", "in");
    lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    let output = WeatherFragmentOutput::LitColor;
    let (rewritten, occurrences) =
        replace_fragment_output(&lowered, output.legacy_index(), output.semantic_name())?;
    lowered = rewritten;
    if occurrences == 0 {
        return Err(GalError::invalid_argument(format!(
            "weather fragment '{}' has no lit-color output",
            source.entry_path()
        )));
    }
    if contains_fragment_output(&lowered)? {
        return Err(GalError::unsupported_feature(format!(
            "weather fragment '{}' writes an unsupported gl_FragData index",
            source.entry_path()
        )));
    }
    if uses_legacy_fog {
        lowered = insert_after_version(&lowered, LEGACY_FOG_SEMANTIC_PREAMBLE)?;
    }
    lowered = lower_world_material_fragment_coordinates(lowered, uniform_contract, source.world_custom_samplers())?;
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    lowered = insert_after_version(
        &lowered,
        "layout(location = 0) out vec4 out_weather_lit_color;\n",
    )?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredWeatherFragmentSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs: vec![output],
        remaining_dialect,
    })
}

pub(super) fn lower_cloud_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<LoweredCloudFragmentSource> {
    lower_material_stream_fragment_surface_with_contracts(
        source,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
        &[
            CloudFragmentOutput::LitColor,
            CloudFragmentOutput::MaterialAuxiliary,
            CloudFragmentOutput::TranslucencyAuxiliary,
        ],
        "cloud",
    )
}

/// Shared fragment lowering for compact material-stream writers (clouds,
/// block-selection lines). `required_outputs` is the exact named-output set
/// the selected source contract admitted; any other `gl_FragData` write is
/// rejected rather than dropped.
pub(super) fn lower_material_stream_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
    required_outputs: &[CloudFragmentOutput],
    writer: &str,
) -> GalResult<LoweredCloudFragmentSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    let uses_legacy_fog = lower_legacy_fog(&mut lowered);
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        ("shadow2D", "vulkanic_source_shadow2D"),
        ("shadow2DLod", "vulkanic_source_shadow2DLod"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = replace_identifier(&lowered, "varying", "in");
    lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    let mut outputs = Vec::new();
    for &output in required_outputs {
        let (rewritten, occurrences) =
            replace_fragment_output(&lowered, output.legacy_index(), output.semantic_name())?;
        lowered = rewritten;
        if occurrences > 0 {
            outputs.push(output);
        }
    }
    if contains_fragment_output(&lowered)? {
        return Err(GalError::unsupported_feature(format!(
            "{writer} fragment '{}' writes an unsupported gl_FragData index",
            source.entry_path()
        )));
    }
    if required_outputs
        .iter()
        .any(|output| !outputs.contains(output))
    {
        return Err(GalError::invalid_argument(format!(
            "{writer} fragment '{}' lacks one or more required named outputs",
            source.entry_path()
        )));
    }
    let declarations = outputs
        .iter()
        .map(|output| {
            format!(
                "layout(location = {}) out vec4 {};\n",
                output.legacy_index(),
                output.semantic_name()
            )
        })
        .collect::<String>();
    if uses_legacy_fog {
        lowered = insert_after_version(&lowered, LEGACY_FOG_SEMANTIC_PREAMBLE)?;
    }
    lowered = lower_world_material_fragment_coordinates(lowered, uniform_contract, source.world_custom_samplers())?;
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    lowered = insert_after_version(&lowered, &declarations)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredCloudFragmentSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs,
        remaining_dialect,
    })
}

pub(super) fn lower_translucent_terrain_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<LoweredTranslucentTerrainFragmentSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    let uses_legacy_fog = lower_legacy_fog(&mut lowered);
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        ("shadow2D", "vulkanic_source_shadow2D"),
        ("shadow2DLod", "vulkanic_source_shadow2DLod"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    let mut outputs = Vec::new();
    for output in [
        TranslucentTerrainFragmentOutput::LitColor,
        TranslucentTerrainFragmentOutput::TranslucencyAuxiliary,
        TranslucentTerrainFragmentOutput::MaterialAuxiliary,
    ] {
        let (rewritten, occurrences) =
            replace_fragment_output(&lowered, output.legacy_index(), output.semantic_name())?;
        lowered = rewritten;
        if occurrences > 0 {
            outputs.push(output);
        }
    }
    if contains_fragment_output(&lowered)? {
        return Err(GalError::unsupported_feature(format!(
            "translucent terrain fragment '{}' writes an unsupported gl_FragData index",
            source.entry_path()
        )));
    }
    if !outputs.contains(&TranslucentTerrainFragmentOutput::LitColor)
        || !outputs.contains(&TranslucentTerrainFragmentOutput::TranslucencyAuxiliary)
    {
        return Err(GalError::invalid_argument(format!(
            "translucent terrain fragment '{}' lacks a required named color or translucency output",
            source.entry_path()
        )));
    }
    let declarations = outputs
        .iter()
        .map(|output| {
            format!(
                "layout(location = {}) out vec4 {};\n",
                output.legacy_index(),
                output.semantic_name()
            )
        })
        .collect::<String>();
    if uses_legacy_fog {
        lowered = insert_after_version(&lowered, LEGACY_FOG_SEMANTIC_PREAMBLE)?;
    }
    // The water/translucent source shares the same lower-left fragment-space
    // contract as opaque terrain. Do not let it silently diverge from the
    // semantic source convention just because it has a distinct output pass.
    lowered = lower_world_material_fragment_coordinates(lowered, uniform_contract, source.world_custom_samplers())?;
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    lowered = insert_after_version(&lowered, &declarations)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredTranslucentTerrainFragmentSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs,
        remaining_dialect,
    })
}

pub(super) fn lower_distant_horizons_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: &TerrainSourceVaryingContract,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<LoweredDistantHorizonsFragmentSource> {
    let mut lowered = upgrade_version(source.expanded_source())?;
    lowered = strip_nonopaque_uniforms(&lowered)?;
    let uses_legacy_fog = lower_legacy_fog(&mut lowered);
    for (legacy, explicit) in [
        ("texture2DLod", "textureLod"),
        ("texture3DLod", "textureLod"),
        ("textureCubeLod", "textureLod"),
        ("texture2D", "texture"),
        ("texture3D", "texture"),
        ("textureCube", "texture"),
        ("shadow2D", "vulkanic_source_shadow2D"),
        ("shadow2DLod", "vulkanic_source_shadow2DLod"),
    ] {
        lowered = replace_identifier(&lowered, legacy, explicit);
    }
    lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    lowered = apply_opaque_resource_bindings(&lowered, opaque_resource_contract)?;
    let output = DistantHorizonsFragmentOutput::LitColor;
    let (rewritten, occurrences) =
        replace_fragment_output(&lowered, output.legacy_index(), output.semantic_name())?;
    lowered = rewritten;
    if contains_fragment_output(&lowered)? {
        return Err(GalError::unsupported_feature(format!(
            "Distant Horizons fragment '{}' writes an unsupported gl_FragData index",
            source.entry_path()
        )));
    }
    if occurrences == 0 {
        return Err(GalError::invalid_argument(format!(
            "Distant Horizons fragment '{}' has no lit color output to lower",
            source.entry_path()
        )));
    }
    let declaration = format!(
        "layout(location = {}) out vec4 {};\n",
        output.legacy_index(),
        output.semantic_name()
    );
    if uses_legacy_fog {
        lowered = insert_after_version(&lowered, LEGACY_FOG_SEMANTIC_PREAMBLE)?;
    }
    // Insert the coordinate helper before the source uniform block so the
    // subsequent insertion leaves `viewHeight` declared before the helper.
    lowered = lower_world_material_fragment_coordinates(lowered, uniform_contract, source.world_custom_samplers())?;
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    lowered = insert_after_version(&lowered, &declaration)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredDistantHorizonsFragmentSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs: vec![output],
        remaining_dialect,
    })
}

pub(super) const FRAGMENT_SEMANTIC_PREAMBLE: &str = r#"#define vulkanic_source_shadow2D(source_texture, source_coordinates) vec4(texture(source_texture, source_coordinates))
#define vulkanic_source_shadow2DLod(source_texture, source_coordinates, source_lod) vec4(textureLod(source_texture, source_coordinates, source_lod))
"#;

/// Preserves the source pack's lower-left screen-coordinate contract for
/// world-material fragments. Vulkan's native fragment Y origin is opposite,
/// and Rust-owned targets are stored in native (top-down) image order, so
/// every source-space access to a target -- filtered, explicit-LOD, integer,
/// or through a sampler function parameter -- is flipped exactly once.
/// Samplers the pack binds to its own images keep their authored addressing.
pub(super) fn lower_world_material_fragment_coordinates(
    mut source: String,
    uniform_contract: &TerrainSourceUniformContract,
    custom_samplers: &[String],
) -> GalResult<String> {
    let reads_fragment_coordinate = glsl_identifiers(&source).contains("gl_FragCoord");
    if reads_fragment_coordinate
        && !uniform_contract
            .fields()
            .iter()
            .any(|field| field.name() == "viewHeight")
    {
        return Err(GalError::unsupported_feature(
            "world-material source reads gl_FragCoord but does not declare viewHeight for explicit coordinate conversion",
        ));
    }
    if reads_fragment_coordinate {
        source = replace_identifier(
            &source,
            "gl_FragCoord",
            "vulkanic_source_world_fragment_coord()",
        );
    }
    let (source, source_target_sampling_preamble) =
        lower_world_material_source_target_sampling(source, custom_samplers);
    if !reads_fragment_coordinate && source_target_sampling_preamble.is_empty() {
        return Ok(source);
    }
    let fragment_coordinate = if reads_fragment_coordinate {
        r#"vec4 vulkanic_source_world_fragment_coord() {
    vec4 coordinate = gl_FragCoord;
#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
    coordinate.y = viewHeight - coordinate.y;
#endif
    return coordinate;
}
"#
    } else {
        ""
    };
    insert_after_version(
        &source,
        &format!("{fragment_coordinate}{source_target_sampling_preamble}"),
    )
}

pub(super) const WORLD_SOURCE_TARGET_SAMPLERS: &[&str] = &[
    "depthtex0",
    "depthtex1",
    "depthtex2",
    "dhDepthTex",
    "dhDepthTex0",
    "dhDepthTex1",
    "gaux1",
    "gaux2",
    "gaux3",
    "gaux4",
    "colortex0",
    "colortex1",
    "colortex2",
    "colortex3",
    "colortex4",
    "colortex5",
    "colortex6",
    "colortex7",
    "colortex8",
    "colortex9",
    "colortex10",
    "colortex11",
    "colortex12",
    "colortex13",
    "colortex14",
    "colortex15",
];

/// Source terrain fragments express screen coordinates in the OpenGL
/// lower-left domain. Rust-owned pass targets are sampled in their native
/// image domain, so source-target samplers must flip that coordinate exactly
/// once. Atlas/material samplers and pack-bound images remain untouched.
/// Returns an empty preamble when the stage touches no target.
pub(super) fn lower_world_material_source_target_sampling(
    mut source: String,
    custom_samplers: &[String],
) -> (String, String) {
    let targets = WORLD_SOURCE_TARGET_SAMPLERS
        .iter()
        .copied()
        .filter(|name| !custom_samplers.iter().any(|custom| custom == name))
        .collect::<Vec<_>>();
    let mut helpers = String::new();
    let mut uses_uv = false;
    let mut uses_texel = false;
    for sampler in &targets {
        for (call, helper, definition) in [
            (
                format!("texture({sampler},"),
                format!("vulkanic_source_sample_target_{sampler}("),
                format!("#define vulkanic_source_sample_target_{sampler}(source_uv) texture({sampler}, vulkanic_source_world_target_uv(source_uv))\n"),
            ),
            (
                format!("textureLod({sampler},"),
                format!("vulkanic_source_sample_target_lod_{sampler}("),
                format!("#define vulkanic_source_sample_target_lod_{sampler}(source_uv, source_lod) textureLod({sampler}, vulkanic_source_world_target_uv(source_uv), source_lod)\n"),
            ),
            (
                format!("texelFetch({sampler},"),
                format!("vulkanic_source_fetch_target_{sampler}("),
                format!("#define vulkanic_source_fetch_target_{sampler}(source_texel, source_lod) texelFetch({sampler}, vulkanic_source_world_target_texel(ivec2(source_texel), textureSize({sampler}, source_lod)), source_lod)\n"),
            ),
        ] {
            if !source.contains(&call) {
                continue;
            }
            source = source.replace(&call, &helper);
            // A macro expands at the original call site. Sampler declarations
            // can follow this preamble, which a function body could not see.
            helpers.push_str(&definition);
            if call.starts_with("texelFetch") {
                uses_texel = true;
            } else {
                uses_uv = true;
            }
        }
    }
    // Sampler function parameters (for example a reflection helper taking
    // `sampler2D depthtex`) are flipped when every call site passes a target.
    let parameter_rewrite = lower_target_sampler_parameters(&source, &targets);
    if let Some(rewritten) = parameter_rewrite {
        source = rewritten;
        uses_uv = true;
        helpers.push_str(
            "#define vulkanic_source_sample_target_parameter(source_sampler, source_uv) texture(source_sampler, vulkanic_source_world_target_uv(source_uv))\n\
#define vulkanic_source_sample_target_parameter_lod(source_sampler, source_uv, source_lod) textureLod(source_sampler, vulkanic_source_world_target_uv(source_uv), source_lod)\n",
        );
    }
    if !uses_uv && !uses_texel {
        return (source, String::new());
    }
    let preamble = format!(
        r#"#ifdef VULKANIC_GAL_ZERO_TO_ONE_CLIP_DEPTH
#define vulkanic_source_world_target_uv(source_uv) vec2((source_uv).x, 1.0 - (source_uv).y)
ivec2 vulkanic_source_world_target_texel(ivec2 texel, ivec2 size) {{ return ivec2(texel.x, size.y - 1 - texel.y); }}
#else
#define vulkanic_source_world_target_uv(source_uv) (source_uv)
ivec2 vulkanic_source_world_target_texel(ivec2 texel, ivec2 size) {{ return texel; }}
#endif
{helpers}"#
    );
    (source, preamble)
}

/// Rewrites `texture`/`textureLod` on `sampler2D` function parameters whose
/// every call site passes a Rust-owned target. Mixed call sites are left as
/// authored rather than guessed.
pub(super) fn lower_target_sampler_parameters(source: &str, targets: &[&str]) -> Option<String> {
    let bytes = source.as_bytes();
    let mut rewritten = source.to_string();
    let mut search = 0usize;
    while let Some(relative) = source[search..].find("sampler2D ") {
        let at = search + relative;
        search = at + "sampler2D ".len();
        // Only parameters: the nearest preceding structural character must be
        // '(' or ',' inside a function signature (not a `uniform` declaration).
        let before = source[..at].trim_end();
        if !(before.ends_with('(') || before.ends_with(',')) {
            continue;
        }
        let name: String = source[search..]
            .chars()
            .take_while(|c| c.is_ascii_alphanumeric() || *c == '_')
            .collect();
        if name.is_empty() {
            continue;
        }
        // Locate the enclosing signature and function name.
        let Some(open) = before.rfind('(').filter(|&open| {
            !source[open..at].contains(')') && !source[open..at].contains(';')
        }) else {
            continue;
        };
        let function: String = source[..open]
            .trim_end()
            .chars()
            .rev()
            .take_while(|c| c.is_ascii_alphanumeric() || *c == '_')
            .collect::<Vec<_>>()
            .into_iter()
            .rev()
            .collect();
        if function.is_empty() {
            continue;
        }
        let parameter_index = source[open + 1..at].matches(',').count();
        let Some(close) = matching_paren(bytes, open) else {
            continue;
        };
        let Some(body_open) = source[close..].find('{').map(|offset| close + offset) else {
            continue;
        };
        if !source[close + 1..body_open].trim().is_empty() {
            continue;
        }
        let Some(body_close) = matching_brace(bytes, body_open) else {
            continue;
        };
        // Every call of `function` (outside its own definition) must pass a
        // target at this parameter position.
        let mut calls = 0usize;
        let mut all_targets = true;
        let mut cursor = 0usize;
        while let Some(relative) = source[cursor..].find(&format!("{function}(")) {
            let call = cursor + relative;
            cursor = call + function.len() + 1;
            let preceded_by_identifier = call > 0
                && (bytes[call - 1].is_ascii_alphanumeric() || bytes[call - 1] == b'_');
            if preceded_by_identifier || call + function.len() == open {
                continue;
            }
            let call_open = call + function.len();
            let Some(call_close) = matching_paren(bytes, call_open) else {
                all_targets = false;
                break;
            };
            let arguments = split_top_level_arguments(&source[call_open + 1..call_close]);
            calls += 1;
            match arguments.get(parameter_index) {
                Some(argument) if targets.contains(&argument.trim()) => {}
                _ => all_targets = false,
            }
        }
        if calls == 0 || !all_targets {
            continue;
        }
        let body = &rewritten[body_open..body_close];
        let new_body = body
            .replace(
                &format!("textureLod({name},"),
                &format!("vulkanic_source_sample_target_parameter_lod({name},"),
            )
            .replace(
                &format!("texture({name},"),
                &format!("vulkanic_source_sample_target_parameter({name},"),
            );
        if new_body != body {
            // Offsets stay valid: the rewrite only grows text after `body_open`
            // and parameters are processed in source order below it.
            rewritten = format!("{}{}{}", &rewritten[..body_open], new_body, &rewritten[body_close..]);
            // Re-run on the updated text for any further parameters.
            return lower_target_sampler_parameters(&rewritten, targets).or(Some(rewritten));
        }
    }
    None
}

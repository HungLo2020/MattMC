//! Scalar uniform contracts: std140 layout, legacy transform and fog uniforms.

use super::*;

#[derive(Clone, Debug, Eq, PartialEq)]
pub(super) struct SourceUniformDeclaration {
    pub(super) name: String,
    pub(super) declaration: String,
}

/// Derives one ordered layout from both stages. Equal names must have exactly
/// equal declarations: accepting a type or array mismatch would make the
/// eventual UBO ABI ambiguous.
pub fn derive_terrain_source_uniform_contract(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<TerrainSourceUniformContract> {
    derive_source_uniform_contract(vertex, fragment, SourceTransformSemantics::Terrain)
}

pub(super) fn derive_source_uniform_contract(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
    transforms: SourceTransformSemantics,
) -> GalResult<TerrainSourceUniformContract> {
    let mut declarations = BTreeMap::new();
    for source in [vertex, fragment] {
        for uniform in collect_nonopaque_uniforms(source.expanded_source(), transforms)? {
            match declarations.get(&uniform.name) {
                Some(existing) if existing != &uniform.declaration => {
                    return Err(GalError::invalid_argument(format!(
                        "terrain source uniform '{}' has incompatible paired declarations: '{}' versus '{}'",
                        uniform.name, existing, uniform.declaration
                    )));
                }
                Some(_) => {}
                None => {
                    declarations.insert(uniform.name, uniform.declaration);
                }
            }
        }
        for (name, declaration) in
            required_legacy_transform_uniforms(source.expanded_source(), transforms)
        {
            match declarations.get(name) {
                Some(existing) if existing != declaration => {
                    return Err(GalError::invalid_argument(format!(
                        "terrain source legacy transform '{}' must be declared as '{}' rather than '{}'",
                        name, declaration, existing
                    )));
                }
                Some(_) => {}
                None => {
                    declarations.insert(name.to_string(), declaration.to_string());
                }
            }
        }
        // World fragments convert gl_FragCoord to OpenGL's lower-left origin
        // with the explicit viewport height, even when the pack itself never
        // names `viewHeight` (for example a Bayer dither on gl_FragCoord.xy).
        if std::ptr::eq(source, fragment)
            && !matches!(
                transforms,
                SourceTransformSemantics::Shadow | SourceTransformSemantics::Fullscreen
            )
            && glsl_identifiers(source.expanded_source()).contains("gl_FragCoord")
        {
            match declarations.get("viewHeight") {
                Some(existing) if existing != "float viewHeight;" => {
                    return Err(GalError::invalid_argument(format!(
                        "world fragment viewHeight must be declared as 'float viewHeight;' rather than '{existing}'"
                    )));
                }
                Some(_) => {}
                None => {
                    declarations.insert("viewHeight".to_string(), "float viewHeight;".to_string());
                }
            }
        }
        for (name, declaration) in required_legacy_fog_uniforms(source.expanded_source()) {
            match declarations.get(name) {
                Some(existing) if existing != declaration => {
                    return Err(GalError::invalid_argument(format!(
                        "terrain source legacy fog '{}' must be declared as '{}' rather than '{}'",
                        name, declaration, existing
                    )));
                }
                Some(_) => {}
                None => {
                    declarations.insert(name.to_string(), declaration.to_string());
                }
            }
        }
    }
    let declarations = declarations.into_values().collect::<Vec<_>>();
    let (fields, std140_size) = terrain_source_std140_layout(&declarations)?;
    Ok(TerrainSourceUniformContract {
        declarations,
        fields,
        std140_size,
    })
}

/// Fullscreen source stages usually have no camera geometry. The owned vanilla
/// sky disc is the one explicit exception: its legacy source program receives
/// a real model-view/projection transform and reconstructs rays from the
/// resulting fragment depth. Keep those two fields in the same semantic UBO
/// contract rather than sourcing them from Java/Iris state.
pub(super) fn derive_fullscreen_source_uniform_contract(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
    raster_primitive: FullscreenSourceRasterPrimitive,
) -> GalResult<TerrainSourceUniformContract> {
    let mut contract =
        derive_source_uniform_contract(vertex, fragment, SourceTransformSemantics::Fullscreen)?;
    // Fragment-coordinate source stages address an owned target in pixel
    // space. The runtime owns this viewport semantic even when the selected
    // stage reaches it through an include rather than a local declaration.
    if fragment.expanded_source().contains("gl_FragCoord") {
        ensure_fullscreen_uniform(&mut contract, "viewHeight", "float viewHeight;")?;
    }
    if !matches!(
        raster_primitive,
        FullscreenSourceRasterPrimitive::VanillaSkyDisc
            | FullscreenSourceRasterPrimitive::VanillaCelestialQuad
    ) {
        return Ok(contract);
    }
    for (name, declaration) in [
        ("gbufferModelView", "mat4 gbufferModelView;"),
        ("gbufferProjection", "mat4 gbufferProjection;"),
    ] {
        ensure_fullscreen_uniform(&mut contract, name, declaration)?;
    }
    if raster_primitive == FullscreenSourceRasterPrimitive::VanillaCelestialQuad {
        for (name, declaration) in [
            ("sunAngle", "float sunAngle;"),
            ("moonPhase", "int moonPhase;"),
            (
                "vulkanic_source_celestial_is_moon",
                "int vulkanic_source_celestial_is_moon;",
            ),
            (
                "vulkanic_source_celestial_alpha",
                "float vulkanic_source_celestial_alpha;",
            ),
            (
                "vulkanic_source_celestial_sun_path_rotation",
                "float vulkanic_source_celestial_sun_path_rotation;",
            ),
        ] {
            ensure_fullscreen_uniform(&mut contract, name, declaration)?;
        }
    }
    Ok(contract)
}

pub(super) fn ensure_fullscreen_uniform(
    contract: &mut TerrainSourceUniformContract,
    name: &str,
    declaration: &str,
) -> GalResult<()> {
    let already_declared = contract
        .declarations
        .iter()
        .any(|existing| uniform_name(existing).is_ok_and(|existing_name| existing_name == name));
    if already_declared {
        return Ok(());
    }
    contract.declarations.push(declaration.to_string());
    contract.declarations.sort_by(|left, right| {
        uniform_name(left)
            .expect("validated fullscreen uniform declaration")
            .cmp(&uniform_name(right).expect("validated fullscreen uniform declaration"))
    });
    let (fields, std140_size) = terrain_source_std140_layout(&contract.declarations)?;
    contract.fields = fields;
    contract.std140_size = std140_size;
    Ok(())
}

pub(super) const MAX_TERRAIN_SOURCE_UNIFORM_FIELDS: usize = 512;

pub(super) const MAX_TERRAIN_SOURCE_UNIFORM_BYTES: u32 = 64 * 1024;

pub(super) fn terrain_source_std140_layout(
    declarations: &[String],
) -> GalResult<(Vec<TerrainSourceUniformField>, u32)> {
    if declarations.len() > MAX_TERRAIN_SOURCE_UNIFORM_FIELDS {
        return Err(GalError::invalid_argument(format!(
            "terrain source declares {} scalar uniforms, exceeding {}",
            declarations.len(),
            MAX_TERRAIN_SOURCE_UNIFORM_FIELDS
        )));
    }
    let mut fields = Vec::with_capacity(declarations.len());
    let mut offset = 0_u32;
    for declaration in declarations {
        let (type_name, name, array_length) =
            parse_terrain_source_uniform_declaration(declaration)?;
        let ty = TerrainSourceUniformType::from_glsl(type_name)?;
        let alignment = if array_length > 1 {
            16
        } else {
            ty.std140_alignment()
        };
        offset = align_up_std140(offset, alignment)?;
        let element_size = ty.std140_size();
        let array_stride = if array_length > 1 {
            align_up_std140(element_size, 16)?
        } else {
            0
        };
        let size = if array_length > 1 {
            array_stride.checked_mul(array_length).ok_or_else(|| {
                GalError::invalid_argument("terrain source uniform array size overflows u32")
            })?
        } else {
            element_size
        };
        let end = offset.checked_add(size).ok_or_else(|| {
            GalError::invalid_argument("terrain source std140 layout size overflows u32")
        })?;
        if end > MAX_TERRAIN_SOURCE_UNIFORM_BYTES {
            return Err(GalError::invalid_argument(format!(
                "terrain source std140 scalar block exceeds {} bytes",
                MAX_TERRAIN_SOURCE_UNIFORM_BYTES
            )));
        }
        fields.push(TerrainSourceUniformField {
            name: name.to_string(),
            ty,
            array_length,
            offset,
            size,
            array_stride,
        });
        offset = end;
    }
    Ok((fields, align_up_std140(offset, 16)?))
}

pub(super) fn parse_terrain_source_uniform_declaration(declaration: &str) -> GalResult<(&str, &str, u32)> {
    let declaration = declaration.trim().trim_end_matches(';');
    let mut parts = declaration.split_whitespace();
    let type_name = parts
        .next()
        .ok_or_else(|| GalError::invalid_argument("terrain uniform type is missing"))?;
    let name = parts
        .next()
        .ok_or_else(|| GalError::invalid_argument("terrain uniform name is missing"))?;
    if parts.next().is_some() {
        return Err(GalError::invalid_argument(
            "terrain uniform declaration has unsupported qualifiers or tokens",
        ));
    }
    let (name, array_length) = match name.split_once('[') {
        Some((name, suffix)) => {
            let count = suffix.strip_suffix(']').ok_or_else(|| {
                GalError::invalid_argument("terrain uniform array declaration is malformed")
            })?;
            let count = count.parse::<u32>().map_err(|_| {
                GalError::invalid_argument("terrain uniform array length is not a u32")
            })?;
            if count == 0 {
                return Err(GalError::invalid_argument(
                    "terrain uniform array length must be non-zero",
                ));
            }
            (name, count)
        }
        None => (name, 1),
    };
    if !valid_identifier(name) {
        return Err(GalError::invalid_argument(format!(
            "terrain uniform has invalid name '{name}'"
        )));
    }
    Ok((type_name, name, array_length))
}

pub(super) fn align_up_std140(value: u32, alignment: u32) -> GalResult<u32> {
    debug_assert!(alignment.is_power_of_two());
    value
        .checked_add(alignment - 1)
        .map(|value| value & !(alignment - 1))
        .ok_or_else(|| GalError::invalid_argument("terrain source std140 alignment overflows u32"))
}

/// Removes scalar/vector/matrix uniforms after their paired declaration has
/// been captured. Samplers/images remain source-declared named resources for
/// a later binding contract.
pub(super) fn strip_nonopaque_uniforms(source: &str) -> GalResult<String> {
    let mut output = String::with_capacity(source.len());
    for line in source.lines() {
        let trimmed = line.trim();
        let Some(declaration) = trimmed.strip_prefix("uniform ") else {
            output.push_str(line);
            output.push('\n');
            continue;
        };
        let type_name = declaration
            .split_whitespace()
            .next()
            .ok_or_else(|| GalError::invalid_argument("malformed terrain uniform declaration"))?;
        if is_opaque_uniform_type(type_name) {
            output.push_str(line);
            output.push('\n');
            continue;
        }
        validate_nonopaque_uniform_declaration(declaration)?;
    }
    Ok(output)
}

pub(super) fn collect_nonopaque_uniforms(
    source: &str,
    transforms: SourceTransformSemantics,
) -> GalResult<Vec<SourceUniformDeclaration>> {
    // Expanded packs commonly include a broad global uniform header. Only a
    // source-stage reference belongs in this program's explicit ABI; merely
    // declaring a value in an inactive terrain path must not create a fake
    // semantic input requirement. Strip scalar declarations before scanning
    // so a declaration cannot count as its own use.
    let source_without_scalar_uniforms = strip_nonopaque_uniforms(source)?;
    let mut referenced = glsl_identifiers(&source_without_scalar_uniforms);
    // Vertex lowering replaces these legacy built-ins with explicit source
    // uniforms after the contract has been derived. Preserve their declared
    // semantic matrices when the original source requires the replacement.
    for (name, _) in required_legacy_transform_uniforms(source, transforms) {
        referenced.insert(name.to_string());
    }
    for (name, _) in required_legacy_fog_uniforms(source) {
        referenced.insert(name.to_string());
    }
    let mut uniforms = Vec::new();
    for line in source.lines() {
        let trimmed = line.trim();
        let Some(declaration) = trimmed.strip_prefix("uniform ") else {
            continue;
        };
        let type_name = declaration
            .split_whitespace()
            .next()
            .ok_or_else(|| GalError::invalid_argument("malformed terrain uniform declaration"))?;
        if is_opaque_uniform_type(type_name) {
            continue;
        }
        let declaration = validate_nonopaque_uniform_declaration(declaration)?;
        let name = uniform_name(&declaration)?;
        if referenced.contains(&name) {
            uniforms.push(SourceUniformDeclaration { name, declaration });
        }
    }
    Ok(uniforms)
}

/// Legacy matrix built-ins are transformed into these named source semantics.
/// They are added to the uniform ABI even when the original GLSL relied on
/// built-ins and never declared them explicitly.
pub(super) fn required_legacy_transform_uniforms(
    source: &str,
    transforms: SourceTransformSemantics,
) -> Vec<(&'static str, &'static str)> {
    if transforms == SourceTransformSemantics::Fullscreen {
        // Source fullscreen stages use their own fixed Rust-owned position/UV
        // stream and texture-matrix block. They do not inherit terrain or
        // shadow camera transforms merely because legacy GLSL spells
        // `ftransform()`.
        return Vec::new();
    }
    let referenced = glsl_identifiers(source);
    let mut requirements = Vec::with_capacity(2);
    if referenced.contains("gl_ModelViewMatrix")
        || referenced.contains("gl_NormalMatrix")
        || referenced.contains("ftransform")
    {
        requirements.push((
            transforms.model_view_uniform(),
            match transforms {
                SourceTransformSemantics::Terrain
                | SourceTransformSemantics::Entity
                | SourceTransformSemantics::Hand => "mat4 gbufferModelView;",
                SourceTransformSemantics::TexturedMaterial
                | SourceTransformSemantics::Weather
                | SourceTransformSemantics::Cloud => "mat4 gbufferModelView;",
                SourceTransformSemantics::Shadow => "mat4 shadowModelView;",
                SourceTransformSemantics::DistantHorizons => "mat4 dhModelView;",
                SourceTransformSemantics::Fullscreen => unreachable!(
                    "fullscreen source stages do not derive legacy camera transform uniforms"
                ),
            },
        ));
    }
    // Hand clip projection lives in the hand-only legacy transform block.
    // Iris still exposes the world gbufferProjection to source uniforms, so
    // a legacy built-in must not inject a second scalar declaration here.
    if transforms != SourceTransformSemantics::Hand
        && (referenced.contains("gl_ProjectionMatrix") || referenced.contains("ftransform"))
    {
        requirements.push((
            transforms.projection_uniform(),
            match transforms {
                SourceTransformSemantics::Terrain
                | SourceTransformSemantics::Entity
                | SourceTransformSemantics::Hand => "mat4 gbufferProjection;",
                SourceTransformSemantics::TexturedMaterial
                | SourceTransformSemantics::Weather
                | SourceTransformSemantics::Cloud => "mat4 gbufferProjection;",
                SourceTransformSemantics::Shadow => "mat4 shadowProjection;",
                SourceTransformSemantics::DistantHorizons => "mat4 dhProjection;",
                SourceTransformSemantics::Fullscreen => unreachable!(
                    "fullscreen source stages do not derive legacy camera transform uniforms"
                ),
            },
        ));
    }
    if transforms == SourceTransformSemantics::DistantHorizons
        && referenced.contains("dhProjectionInverse")
    {
        requirements.push(("dhProjectionInverse", "mat4 dhProjectionInverse;"));
    }
    requirements
}

/// Legacy `gl_Fog` is a semantic fog record, not fixed-function backend
/// state. The lowered source receives the exact copied fog-parameter RGBA
/// and environmental range needed to construct its legacy fields.
pub(super) fn required_legacy_fog_uniforms(source: &str) -> Vec<(&'static str, &'static str)> {
    if !glsl_identifiers(source).contains("gl_Fog") {
        return Vec::new();
    }
    vec![
        (
            "vulkanic_source_fog_parameter_color",
            "vec4 vulkanic_source_fog_parameter_color;",
        ),
        (
            "vulkanic_source_fog_environmental_start",
            "float vulkanic_source_fog_environmental_start;",
        ),
        (
            "vulkanic_source_fog_environmental_end",
            "float vulkanic_source_fog_environmental_end;",
        ),
    ]
}

/// Returns identifier tokens outside GLSL line/block comments. GLSL has no
/// string literals, so this bounded lexical scan is sufficient for deciding
/// whether a scalar declaration is referenced by the already-expanded source.
/// It deliberately treats identifiers in active preprocessor definitions as
/// references, which is conservative and avoids dropping macro-fed inputs.
#[cfg(test)]
pub(crate) fn glsl_identifiers_for_test(source: &str) -> BTreeSet<String> {
    glsl_identifiers(source)
}

pub(super) fn glsl_identifiers(source: &str) -> BTreeSet<String> {
    let bytes = source.as_bytes();
    let mut identifiers = BTreeSet::new();
    let mut index = 0_usize;
    while index < bytes.len() {
        if bytes[index] == b'/' && index + 1 < bytes.len() {
            match bytes[index + 1] {
                b'/' => {
                    index += 2;
                    while index < bytes.len() && bytes[index] != b'\n' {
                        index += 1;
                    }
                    continue;
                }
                b'*' => {
                    index += 2;
                    while index + 1 < bytes.len()
                        && !(bytes[index] == b'*' && bytes[index + 1] == b'/')
                    {
                        index += 1;
                    }
                    index = (index + 2).min(bytes.len());
                    continue;
                }
                _ => {}
            }
        }
        if bytes[index] == b'_' || bytes[index].is_ascii_alphabetic() {
            let start = index;
            index += 1;
            while index < bytes.len()
                && (bytes[index] == b'_' || bytes[index].is_ascii_alphanumeric())
            {
                index += 1;
            }
            identifiers.insert(source[start..index].to_string());
            continue;
        }
        index += 1;
    }
    identifiers
}

pub(super) fn validate_nonopaque_uniform_declaration(declaration: &str) -> GalResult<String> {
    let declaration = declaration.trim();
    if !declaration.ends_with(';') || declaration.contains('{') || declaration.contains(',') {
        return Err(GalError::unsupported_feature(
            "terrain scalar uniform declarations must be one named value ending in a semicolon",
        ));
    }
    Ok(declaration.to_string())
}

pub(super) fn uniform_name(declaration: &str) -> GalResult<String> {
    let name = declaration
        .trim_end_matches(';')
        .split_whitespace()
        .last()
        .ok_or_else(|| GalError::invalid_argument("malformed terrain uniform declaration"))?
        .split('[')
        .next()
        .unwrap_or_default();
    if !valid_identifier(name) {
        return Err(GalError::invalid_argument(format!(
            "terrain uniform has invalid name '{name}'"
        )));
    }
    Ok(name.to_string())
}

pub(super) fn is_opaque_uniform_type(type_name: &str) -> bool {
    type_name.contains("sampler")
        || type_name.contains("image")
        || type_name.starts_with("atomic_uint")
}

pub(super) fn uniform_block(contract: &TerrainSourceUniformContract) -> String {
    if contract.declarations.is_empty() {
        return String::new();
    }
    let mut block = String::from(
        "layout(set = 0, binding = 2, std140) uniform VulkanicSourceTerrainUniforms {\n",
    );
    for declaration in &contract.declarations {
        block.push_str("    ");
        block.push_str(declaration);
        block.push('\n');
    }
    block.push_str("};\n");
    block
}

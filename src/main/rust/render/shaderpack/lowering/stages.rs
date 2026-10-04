//! Entry points that lower each stage's selected source pair.

use super::*;

/// Lowers only the two legacy fragment constructs whose mapping is fully
/// source-derived today: `texture2D` and `gl_FragData[n]`. This does not
/// attempt to guess vertex interfaces, fixed transforms, varying locations,
/// samplers, or backend descriptor bindings.
pub fn lower_terrain_fragment_surface(
    source: &PreprocessedShaderSource,
) -> GalResult<LoweredTerrainFragmentSource> {
    let uniform_contract = derive_terrain_source_uniform_contract(source, source)?;
    let varying_contract = derive_terrain_source_varying_contract(source, source)?;
    let opaque_resource_contract = derive_terrain_source_opaque_resource_contract(source, source)?;
    lower_terrain_fragment_surface_with_contracts(
        source,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
    )
}

/// Lowers a legacy shadow fragment into named shadow-pass outputs. This is
/// deliberately fragment-only preparation: execution still requires a
/// separately lowered shadow vertex stage and a complete Rust-owned shadow
/// attachment/resource contract.
pub fn lower_shadow_fragment_surface(
    source: &PreprocessedShaderSource,
) -> GalResult<LoweredShadowFragmentSource> {
    let uniform_contract = derive_terrain_source_uniform_contract(source, source)?;
    let opaque_resource_contract = derive_terrain_source_opaque_resource_contract(source, source)?;
    lower_shadow_fragment_surface_with_contracts(
        source,
        &uniform_contract,
        None,
        &opaque_resource_contract,
    )
}

pub(super) fn lower_shadow_fragment_surface_with_contracts(
    source: &PreprocessedShaderSource,
    uniform_contract: &TerrainSourceUniformContract,
    varying_contract: Option<&TerrainSourceVaryingContract>,
    opaque_resource_contract: &TerrainSourceOpaqueResourceContract,
) -> GalResult<LoweredShadowFragmentSource> {
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
    // A shadow fragment may declare inputs that only its paired vertex stage
    // produces. Fragment-only preparation deliberately leaves those locations
    // untouched; complete pair lowering validates and assigns them.
    if let Some(varying_contract) = varying_contract {
        lowered = replace_identifier(&lowered, "varying", "in");
        lowered = apply_varying_locations(&lowered, VaryingStorage::In, varying_contract)?;
    }
    lowered = apply_opaque_resource_bindings(&lowered, &opaque_resource_contract)?;
    let mut outputs = Vec::new();
    for output in [
        ShadowFragmentOutput::ShadowColor,
        ShadowFragmentOutput::LightShaftColor,
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
            "shadow fragment '{}' writes an unsupported gl_FragData index",
            source.entry_path()
        )));
    }
    if !outputs.contains(&ShadowFragmentOutput::ShadowColor) {
        return Err(GalError::invalid_argument(format!(
            "shadow fragment '{}' has no shadow color output to lower",
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
    lowered = insert_after_version(&lowered, &uniform_block(uniform_contract))?;
    lowered = insert_after_version(&lowered, FRAGMENT_SEMANTIC_PREAMBLE)?;
    lowered = insert_after_version(&lowered, &declarations)?;
    let remaining_dialect = analyze_glsl_text(source.entry_path(), &lowered);
    Ok(LoweredShadowFragmentSource {
        entry_path: source.entry_path().to_string(),
        source: lowered,
        outputs,
        remaining_dialect,
    })
}

/// Lowers both stages with one exact scalar uniform layout. This is still a
/// preparation artifact, but it prevents a future program from silently using
/// mismatched stage-local UBO layouts.
pub fn lower_terrain_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredTerrainSourcePair> {
    // The selected runtime owns colored-light volume production before every
    // terrain phase. Source writes to that owned semantic volume must be
    // externalized consistently for normal terrain as well as shadow passes.
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let uniform_contract = derive_terrain_source_uniform_contract(&vertex, &fragment)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    let mut lowered_fragment = lower_terrain_fragment_surface_with_contracts(
        &fragment,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
    )?;
    apply_selected_source_fragment_probe(&mut lowered_fragment)?;
    dump_selected_source_lowered_shader(
        lowered_fragment.entry_path(),
        "fragment",
        lowered_fragment.source(),
    );
    Ok(LoweredTerrainSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Terrain,
        )?,
        fragment: lowered_fragment,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers the selected pack's generic textured-material stage through the
/// same explicit Rust-owned transform and sampler contracts as terrain, but
/// preserves the pass's own `DRAWBUFFERS:063` output meanings. In particular,
/// output location two is translucency auxiliary data, not a terrain normal.
pub fn lower_textured_material_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredTexturedMaterialSourcePair> {
    // Shared pack headers may declare terrain attributes for every program.
    // An unused declaration is harmless. The compact stream models the
    // disabled generic mc_Entity attribute explicitly; no other terrain-only
    // lane may receive invented values.
    let vertex_identifiers = glsl_identifiers(&remove_known_legacy_attributes(
        vertex.expanded_source(),
    )?);
    for name in ["mc_midTexCoord", "at_tangent", "at_midBlock"] {
        if vertex_identifiers.contains(name) {
            return Err(GalError::unsupported_feature(format!(
                "selected textured material source requires unsupported terrain-only attribute '{name}'"
            )));
        }
    }
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let uniform_contract = derive_terrain_source_uniform_contract(&vertex, &fragment)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    Ok(LoweredTexturedMaterialSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::TexturedMaterial,
        )?,
        fragment: lower_textured_material_fragment_surface_with_contracts(
            &fragment,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
        )?,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers a selected ordinary entity stage through the Rust-owned indexed
/// source stream. This only prepares source code and semantic layouts; a
/// caller still needs an exact entity-instance contract and named target
/// writer before selected-source execution can be admitted.
pub fn lower_entity_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredEntitySourcePair> {
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let uniform_contract =
        derive_source_uniform_contract(&vertex, &fragment, SourceTransformSemantics::Entity)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    let mut lowered_fragment = lower_terrain_fragment_surface_with_contracts(
        &fragment,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
    )?;
    install_entity_alpha_cutout_hook(&mut lowered_fragment)?;
    apply_selected_source_entity_fragment_probe(&mut lowered_fragment)?;
    Ok(LoweredEntitySourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Entity,
        )?,
        fragment: lowered_fragment,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers the selected pack's `shadow` stages for the entity mesh stream.
/// Iris draws entity (and local-player) shadow casters with the same shadow
/// program as terrain, but through the entity vertex format and a per-draw
/// entity texture. The shadow matrices feed `ftransform()`/`gl_ModelView*`
/// exactly as in the terrain shadow pass; `gl_FragData[n]` maps by index to
/// shadow colour attachment `n`.
pub fn lower_entity_shadow_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredEntitySourcePair> {
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let uniform_contract =
        derive_source_uniform_contract(&vertex, &fragment, SourceTransformSemantics::Shadow)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    // Shadow fragments keep their shadow-space gl_FragCoord (the shadow pass
    // rasterizes natively), so use the shadow lowering, not the screen-space
    // world-material rewrite, then express its index-mapped outputs at the
    // same locations for the entity pipeline.
    let shadow_fragment = lower_shadow_fragment_surface_with_contracts(
        &fragment,
        &uniform_contract,
        Some(&varying_contract),
        &opaque_resource_contract,
    )?;
    let outputs = shadow_fragment
        .outputs
        .iter()
        .map(|output| match output {
            ShadowFragmentOutput::ShadowColor => TerrainFragmentOutput::LitColor,
            ShadowFragmentOutput::LightShaftColor => TerrainFragmentOutput::MaterialAuxiliary,
        })
        .collect::<Vec<_>>();
    let wrapped = crate::render::shaderpack::lowering::rename_glsl_main(&shadow_fragment.source, "vulkanic_entity_shadow_main")?;
    // Iris applies the pack's shadow alpha test to the shadow colour output.
    // The entity pipeline supplies the cutoff; the default keeps every texel.
    let wrapped = insert_after_version(
        &format!(
            "{wrapped}\nvoid main() {{\n    vulkanic_entity_shadow_main();\n    if (!(out_shadow_color.a > VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF)) discard;\n}}\n"
        ),
        "#ifndef VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF\n#define VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF -1.0\n#endif\n",
    )?;
    let lowered_fragment = LoweredTerrainFragmentSource {
        entry_path: shadow_fragment.entry_path,
        source: wrapped,
        outputs,
        remaining_dialect: shadow_fragment.remaining_dialect,
    };
    Ok(LoweredEntitySourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Shadow,
        )?,
        fragment: lowered_fragment,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers an explicitly discovered first-person hand/item stage. The
/// resulting pair is intentionally separate from entity lowering even though
/// legacy source names both transforms `gbuffer*`: later execution must bind
/// copied first-person matrices and the hand depth domain, never the world
/// camera matrices or a borrowed Iris hand pass.
pub fn lower_hand_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredHandSourcePair> {
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let uniform_contract =
        derive_source_uniform_contract(&vertex, &fragment, SourceTransformSemantics::Hand)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    let mut lowered_fragment = lower_terrain_fragment_surface_with_contracts(
        &fragment,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
    )?;
    install_entity_alpha_cutout_hook(&mut lowered_fragment)?;
    apply_selected_source_entity_fragment_probe(&mut lowered_fragment)?;
    let mut lowered_vertex = lower_source_vertex_surface_with_contracts(
        &vertex,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
        SourceTransformSemantics::Hand,
    )?;
    apply_selected_source_hand_vertex_probe(&mut lowered_vertex)?;
    Ok(LoweredHandSourcePair {
        vertex: lowered_vertex,
        fragment: lowered_fragment,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Iris appends the program's alpha test to the end of `main` on output 0
/// (`if (!(iris_FragData0.a > iris_currentAlphaTest)) discard;`), so it also
/// catches texels a pack never modulates (Complementary skips `color *=
/// glColor` for alpha 0 and still writes them). The Rust-owned writer has no
/// legacy fixed-function stage, so wrap the pack's `main` and apply the same
/// output test when the material-mode specialization defines a cutoff. A
/// program without an output 0 keeps no alpha test, as in Iris.
pub(super) fn install_entity_alpha_cutout_hook(fragment: &mut LoweredTerrainFragmentSource) -> GalResult<()> {
    if !fragment.outputs.contains(&TerrainFragmentOutput::LitColor) {
        return Ok(());
    }
    let wrapped = crate::render::shaderpack::lowering::rename_glsl_main(&fragment.source, "vulkanic_source_entity_main")?;
    fragment.source = format!(
        "{wrapped}\nvoid main() {{\n    vulkanic_source_entity_main();\n#ifdef VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF\n    if (!({}.a > VULKANIC_SOURCE_ENTITY_ALPHA_CUTOFF)) discard;\n#endif\n}}\n",
        TerrainFragmentOutput::LitColor.semantic_name()
    );
    Ok(())
}

/// Lowers the selected weather stage through the same explicit camera-relative
/// material vertex stream as other world quads. The pass's one color output is
/// retained as weather semantics rather than being treated as a terrain or
/// generic material schema.
pub fn lower_weather_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredWeatherSourcePair> {
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let uniform_contract =
        derive_source_uniform_contract(&vertex, &fragment, SourceTransformSemantics::Weather)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    Ok(LoweredWeatherSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Weather,
        )?,
        fragment: lower_weather_fragment_surface_with_contracts(
            &fragment,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
        )?,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers the selected pack's vanilla cloud stage through the explicit
/// camera-relative material stream, retaining cloud's distinct semantic
/// `DRAWBUFFERS:063` outputs.
pub fn lower_cloud_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredCloudSourcePair> {
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let uniform_contract =
        derive_source_uniform_contract(&vertex, &fragment, SourceTransformSemantics::Cloud)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    Ok(LoweredCloudSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Cloud,
        )?,
        fragment: lower_cloud_fragment_surface_with_contracts(
            &fragment,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
        )?,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers the selected `gbuffers_damagedblock` pair onto the compact material
/// stream. Iris draws block-breaking progress with this program through the
/// fixed-function inputs `ftransform()`, `gl_MultiTexCoord0` and `gl_Color`;
/// the Rust material stream supplies those from copied camera-relative
/// crumbling quads, so no terrain-only lanes are required.
pub fn lower_damaged_block_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
    required_outputs: &[CloudFragmentOutput],
) -> GalResult<LoweredCloudSourcePair> {
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let uniform_contract =
        derive_source_uniform_contract(&vertex, &fragment, SourceTransformSemantics::Cloud)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    Ok(LoweredCloudSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Cloud,
        )?,
        fragment: lower_material_stream_fragment_surface_with_contracts(
            &fragment,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            required_outputs,
            "damagedblock",
        )?,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers a block-selection line pair (`gbuffers_line`) onto the compact
/// material stream. Iris injects core-profile inputs for this stage: the
/// camera-relative `vaPosition`, the segment direction in `vaNormal`, and the
/// world `modelViewMatrix`/`projectionMatrix`. The pack expands each segment
/// itself from `gl_VertexID` parity, so the stored (indexed) vertex id is
/// exposed rather than the expanded draw index.
pub fn lower_line_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
    required_outputs: &[CloudFragmentOutput],
) -> GalResult<LoweredCloudSourcePair> {
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let vertex = lower_line_core_profile_inputs(&vertex)?;
    let uniform_contract =
        derive_source_uniform_contract(&vertex, &fragment, SourceTransformSemantics::Cloud)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    Ok(LoweredCloudSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Cloud,
        )?,
        fragment: lower_material_stream_fragment_surface_with_contracts(
            &fragment,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            required_outputs,
            "line",
        )?,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

pub(super) fn lower_line_core_profile_inputs(
    source: &PreprocessedShaderSource,
) -> GalResult<PreprocessedShaderSource> {
    const INJECTED: [&str; 4] = ["vaPosition", "vaNormal", "modelViewMatrix", "projectionMatrix"];
    let mut text = String::with_capacity(source.expanded_source().len());
    for line in source.expanded_source().lines() {
        let trimmed = line.trim();
        // A pack may redeclare the Iris-injected inputs; the Rust stream owns them.
        let redeclares = (trimmed.starts_with("in ")
            || trimmed.starts_with("attribute ")
            || trimmed.starts_with("uniform "))
            && trimmed.ends_with(';')
            && INJECTED
                .iter()
                .any(|name| glsl_identifiers(trimmed).contains(*name));
        if !redeclares {
            text.push_str(line);
        }
        text.push('\n');
    }
    for (injected, explicit) in [
        ("vaPosition", "vulkanic_source_position.xyz"),
        ("vaNormal", "vulkanic_source_normal"),
        ("modelViewMatrix", "gbufferModelView"),
        ("projectionMatrix", "gbufferProjection"),
        ("gl_VertexID", "vulkanic_source_stored_vertex_id"),
    ] {
        text = replace_identifier(&text, injected, explicit);
    }
    source.rewritten_for_lowering(text)
}

/// Lowers the pack's distinct translucent terrain pair using the same
/// semantic vertex/uniform/resource contracts as normal terrain, while
/// retaining its own named fragment-output schema. This does not construct a
/// blend pass or claim executable source-route admission.
pub fn lower_translucent_terrain_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredTranslucentTerrainSourcePair> {
    let owned_storage_bindings = TerrainSourceResourceBindings::default();
    let vertex = externalize_owned_semantic_storage_writes(vertex, &owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, &owned_storage_bindings)?;
    let uniform_contract = derive_terrain_source_uniform_contract(&vertex, &fragment)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    let mut lowered_fragment = lower_translucent_terrain_fragment_surface_with_contracts(
        &fragment,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
    )?;
    // Keep selected-source diagnostics symmetric with opaque terrain. The
    // translucent writer has a distinct lowering function, so it must opt in
    // explicitly or atlas/output probes silently exercise only gbuffers_terrain.
    apply_selected_source_fragment_probe(&mut lowered_fragment)?;
    dump_selected_source_lowered_shader(
        lowered_fragment.entry_path(),
        "fragment",
        lowered_fragment.source(),
    );
    Ok(LoweredTranslucentTerrainSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Terrain,
        )?,
        fragment: lowered_fragment,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers the exact scoped shadow-source pair into explicit source semantics.
/// Shadow transforms deliberately remain distinct from G-buffer transforms,
/// and shadow outputs remain distinct from normal terrain outputs. This is
/// not an executable pass: a future Rust-owned shadow-color attachment and
/// full named resource plan are still required before source execution can be
/// admitted.
pub fn lower_shadow_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredShadowSourcePair> {
    lower_shadow_source_pair_with_owned_storage(
        vertex,
        fragment,
        &TerrainSourceResourceBindings::default(),
    )
}

/// Lowers a shadow pair after explicitly establishing which pack-declared
/// storage images have a Rust-owned semantic writer. The binding table is a
/// source-level name-to-role contract, never an OpenGL/Vulkan binding table;
/// this lets a future pack rename its image without making backend state part
/// of source lowering.
pub fn lower_shadow_source_pair_with_owned_storage(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
    owned_storage_bindings: &TerrainSourceResourceBindings,
) -> GalResult<LoweredShadowSourcePair> {
    let owned_storage_roles =
        externalized_shadow_storage_roles(vertex, fragment, owned_storage_bindings)?;
    let vertex = externalize_owned_semantic_storage_writes(vertex, owned_storage_bindings)?;
    let fragment = externalize_owned_semantic_storage_writes(fragment, owned_storage_bindings)?;
    let uniform_contract =
        derive_source_uniform_contract(&vertex, &fragment, SourceTransformSemantics::Shadow)?;
    let varying_contract = derive_terrain_source_varying_contract(&vertex, &fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(&vertex, &fragment)?;
    Ok(LoweredShadowSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            &vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::Shadow,
        )?,
        fragment: lower_shadow_fragment_surface_with_contracts(
            &fragment,
            &uniform_contract,
            Some(&varying_contract),
            &opaque_resource_contract,
        )?,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
        owned_storage_roles,
    })
}

/// Lowers the exact DH source pair with its own semantic transforms and
/// single named color output. The returned artifact has no pipeline, target,
/// or route-selection effect; the future executor must still provide a
/// complete Rust-owned DH depth/composite contract.
pub fn lower_distant_horizons_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
) -> GalResult<LoweredDistantHorizonsSourcePair> {
    let uniform_contract = derive_source_uniform_contract(
        vertex,
        fragment,
        SourceTransformSemantics::DistantHorizons,
    )?;
    let varying_contract = derive_terrain_source_varying_contract(vertex, fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(vertex, fragment)?;
    let mut lowered_fragment = lower_distant_horizons_fragment_surface_with_contracts(
        fragment,
        &uniform_contract,
        &varying_contract,
        &opaque_resource_contract,
    )?;
    apply_selected_source_distant_horizons_fragment_probe(&mut lowered_fragment)?;
    Ok(LoweredDistantHorizonsSourcePair {
        vertex: lower_source_vertex_surface_with_contracts(
            vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            SourceTransformSemantics::DistantHorizons,
        )?,
        fragment: lowered_fragment,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
    })
}

/// Lowers a source-defined fullscreen stage using only a Rust-owned
/// position/UV stream, scalar source uniforms, and pack-declared semantic
/// resources. Output slots are resolved immediately to named pack-color
/// roles, so later pass scheduling never has to reason about `gl_FragData` or
/// `colortexN` identifiers.
///
/// This function intentionally returns preparation only. It cannot compile a
/// program, allocate an attachment, or cause Distant Horizons to leave its
/// explicit Java compatibility route.
pub fn lower_fullscreen_source_pair(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
    bindings: &TerrainSourceResourceBindings,
) -> GalResult<LoweredFullscreenSourcePair> {
    lower_fullscreen_source_pair_with_raster_primitive(
        vertex,
        fragment,
        bindings,
        FullscreenSourceRasterPrimitive::FullscreenTriangle,
    )
}

/// Lowers a source stage pair with its explicit owned raster primitive.
/// Callers normally use [`lower_fullscreen_source_pair`]; source sky is the
/// currently audited non-fullscreen consumer.
pub fn lower_fullscreen_source_pair_with_raster_primitive(
    vertex: &PreprocessedShaderSource,
    fragment: &PreprocessedShaderSource,
    bindings: &TerrainSourceResourceBindings,
    raster_primitive: FullscreenSourceRasterPrimitive,
) -> GalResult<LoweredFullscreenSourcePair> {
    let uniform_contract =
        derive_fullscreen_source_uniform_contract(vertex, fragment, raster_primitive)?;
    let varying_contract = derive_terrain_source_varying_contract(vertex, fragment)?;
    let opaque_resource_contract =
        derive_terrain_source_opaque_resource_contract(vertex, fragment)?;
    Ok(LoweredFullscreenSourcePair {
        vertex: lower_fullscreen_source_vertex_with_contracts(
            vertex,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            bindings,
            raster_primitive,
        )?,
        fragment: lower_fullscreen_source_fragment_with_contracts(
            fragment,
            &uniform_contract,
            &varying_contract,
            &opaque_resource_contract,
            bindings,
            raster_primitive,
        )?,
        uniform_contract,
        varying_contract,
        opaque_resource_contract,
        raster_primitive,
    })
}

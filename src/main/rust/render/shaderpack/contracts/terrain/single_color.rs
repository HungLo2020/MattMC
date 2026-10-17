//! Discovery of paired terrain sources whose fragment writes one color target.
//!
//! Lighting stays in the selected GLSL. This contract does not infer a
//! Complementary material auxiliary stream or substitute a lighting function.

use super::*;

pub fn derive_terrain_contract_for_scope(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<TerrainPassContract> {
    derive_single_color_contract(source, scope, TerrainSourcePassKind::OpaqueCutout)
}

pub fn derive_translucent_terrain_contract_for_scope(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<TerrainPassContract> {
    derive_single_color_contract(source, scope, TerrainSourcePassKind::Translucent)
}

fn derive_single_color_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
    pass_kind: TerrainSourcePassKind,
) -> GalResult<TerrainPassContract> {
    let candidates = match pass_kind {
        TerrainSourcePassKind::OpaqueCutout => scope.entry_candidates(),
        TerrainSourcePassKind::Translucent => scope.translucent_entry_candidates(),
    };
    let specific_contract = || match pass_kind {
        TerrainSourcePassKind::OpaqueCutout => derive_complementary_terrain_contract_for_scope(source, scope),
        TerrainSourcePassKind::Translucent => derive_complementary_translucent_terrain_contract_for_scope(source, scope),
    };
    let path = candidates.iter().copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| GalError::invalid_argument(format!(
            "missing {}terrain fragment source for {scope:?}; tried {}",
            if pass_kind == TerrainSourcePassKind::Translucent { "translucent " } else { "" },
            candidates.join(", "),
        )))?;
    let stages = terrain_source_stages(path)?;
    let defines = stages.fragment.defines.iter()
        .map(|(key, value)| (key.as_str(), value.as_str())).collect::<Vec<_>>();
    let artifact = preprocess_artifact_with_runtime_options(source, path, &defines);
    // Existing curated Complementary fixtures deliberately omit libraries.
    // Their discovery remains supported by the specific contract, but an
    // unrelated single-color program must provide its complete paired source.
    let artifact = match artifact {
        Ok(artifact) => artifact,
        Err(error) if error.to_string().contains("missing shader source") => {
            return specific_contract();
        }
        Err(error) => return Err(error),
    };
    let fragment = artifact.expanded_source();
    let slots = parse_draw_buffers_slots(fragment)?;
    if slots.len() != 1 {
        return specific_contract();
    }
    require_expression(fragment, "gl_FragData[0]")?;
    let vertex_defines = stages.vertex.defines.iter()
        .map(|(key, value)| (key.as_str(), value.as_str())).collect::<Vec<_>>();
    preprocess_artifact_with_runtime_options(source, &stages.vertex.path, &vertex_defines)?;
    let (material_ids, runtime_block_state_material_ids) =
        terrain_material_identity_resolution(source)?;
    Ok(TerrainPassContract {
        pass_kind,
        pack_name: source.name().to_owned(),
        generation: source.generation(),
        program_path: path.to_owned(),
        material_classes: match pass_kind {
            TerrainSourcePassKind::OpaqueCutout => BTreeSet::from([TerrainMaterialClass::Opaque, TerrainMaterialClass::Cutout]),
            TerrainSourcePassKind::Translucent => BTreeSet::from([TerrainMaterialClass::Translucent]),
        },
        // These are the owned semantic streams available to the original
        // vertex shader, not statements about the pack's lighting algorithm.
        inputs: BTreeSet::from([
            TerrainPassInput::AtlasColor, TerrainPassInput::AtlasUv,
            TerrainPassInput::Tint, TerrainPassInput::AmbientOcclusion,
            TerrainPassInput::PackedBlockLight, TerrainPassInput::PackedSkyLight,
            TerrainPassInput::GeometricNormal, TerrainPassInput::MaterialIdentity,
            TerrainPassInput::WorldPosition, TerrainPassInput::Camera,
            TerrainPassInput::DirectionalLight, TerrainPassInput::Environment,
        ]),
        outputs: BTreeSet::from([TerrainPassOutput::LitTerrainColor]),
        output_color_slots: BTreeMap::from([(TerrainPassOutput::LitTerrainColor, slots[0])]),
        property_defines: artifact.defines().iter().cloned().collect(),
        material_ids,
        runtime_block_state_material_ids,
        operations: vec![TerrainPassOperation::LitColorOutput],
        // Sampler/image declarations must still resolve through source
        // lowering and owned resource admission; no resource is fabricated.
        required_resources: BTreeSet::new(),
        voxel_light_volume_requirements: None,
        normal_alpha_test: NormalTerrainAlphaTestPolicy::from_source(source, artifact.defines())?,
        translucent_raster_state: if pass_kind == TerrainSourcePassKind::Translucent {
            Some(single_color_translucent_raster_state(source, artifact.defines())?)
        } else {
            None
        },
        unsupported: BTreeSet::new(),
    })
}

fn single_color_translucent_raster_state(
    source: &ShaderPackSource,
    defines: &[(String, String)],
) -> GalResult<TerrainTranslucentRasterState> {
    let properties = crate::render::shaderpack::properties::directives::resolved_properties(source, defines)?;
    let text = properties.as_ref().map_or("", |artifact| artifact.expanded_source());
    let blend = match selected_property_value(text, "blend.gbuffers_water")? {
        None | Some("SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA") => TerrainTranslucentBlend::SourceAlphaOver,
        Some(value) => return Err(GalError::unsupported_feature(format!(
            "selected translucent terrain blend '{value}' is not modeled"
        ))),
    };
    // Frozen SodiumPrograms defaults ordinary translucent terrain to ALWAYS;
    // only a selected override installs an output-zero alpha test.
    let alpha_test = match selected_property_value(text, "alphaTest.gbuffers_water")? {
        None | Some("off" | "false") => None,
        Some(value) => Some(parse_translucent_alpha_test(value)?),
    };
    Ok(TerrainTranslucentRasterState { blend, alpha_test })
}

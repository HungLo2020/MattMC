//! World glyph source policy, including framed maps and their decorations.
//!
//! Frozen's TEXT writer selects EntitiesTrans, then its source fallback chain.
//! This owner resolves that chain and canonical producer IDs from copied pack
//! source. Its geometry is an owned compact quad stream, never an Iris buffer.

use crate::render::shaderpack::contracts::material::{
    TexturedMaterialPassContract, TexturedMaterialSourceInput, TexturedMaterialSourceOutput,
};
use crate::render::shaderpack::contracts::terrain::{
    parse_draw_buffers_slots, terrain_source_stages, TerrainProgramScope, TerrainSourceStage,
};
use crate::render::shaderpack::lowering::lower_world_glyph_source_pair;
use crate::render::shaderpack::programs::{
    prepare_lowered_textured_material_source_program, LoweredTexturedMaterialSourceProgram,
    ProgramIdentity,
};
use crate::render::shaderpack::properties::entity_ids::ShaderPackEntityIdMap;
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceResourceBindings, TerrainSourceResourceRole,
};
use crate::render::shaderpack::source::preprocess::{
    preprocess_artifact_with_runtime_options, PreprocessedShaderSource,
};
use crate::render::shaderpack::source::ShaderPackSource;
use crate::render::vulkanic::error::{GalError, GalResult};

#[derive(Clone, Debug)]
pub(crate) struct WorldGlyphSourceProgram {
    pub(crate) program: std::sync::Arc<LoweredTexturedMaterialSourceProgram>,
    entity_ids: ShaderPackEntityIdMap,
}

impl WorldGlyphSourceProgram {
    pub(crate) fn entity_id(&self, identity: &str) -> GalResult<i32> {
        self.entity_ids.resolve(identity)
    }
}

pub(crate) fn prepare_world_glyph_source_program(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<WorldGlyphSourceProgram> {
    let prefix = match scope {
        TerrainProgramScope::Default => "",
        TerrainProgramScope::Overworld => "world0/",
        TerrainProgramScope::Nether => "world-1/",
        TerrainProgramScope::End => "world1/",
    };
    let mut selected = None;
    // Program fallback is source policy, not a fallback renderer or presenter.
    for family in [
        "gbuffers_entities_translucent",
        "gbuffers_entities",
        "gbuffers_textured_lit",
        "gbuffers_textured",
        "gbuffers_basic",
    ] {
        let candidates = [format!("{prefix}{family}.fsh"), format!("{family}.fsh")];
        if let Some(path) = candidates
            .into_iter()
            .find(|path| source.get(path).is_some())
        {
            selected = Some((family, path));
            break;
        }
    }
    let (family, path) = selected.ok_or_else(|| {
        GalError::unsupported_feature("world glyph has no selected source writer")
    })?;
    let stages = terrain_source_stages(&path)?;
    let preprocess = |stage: &TerrainSourceStage| -> GalResult<PreprocessedShaderSource> {
        let defines = stage
            .defines
            .iter()
            .map(|(k, v)| (k.as_str(), v.as_str()))
            .collect::<Vec<_>>();
        preprocess_artifact_with_runtime_options(source, &stage.path, &defines)
    };
    let vertex = preprocess(&stages.vertex)?;
    let fragment = preprocess(&stages.fragment)?;
    let slots = parse_draw_buffers_slots(fragment.expanded_source())?;
    use TexturedMaterialSourceOutput::*;
    let outputs = match slots.as_slice() {
        [_] => vec![LitColor],
        [0, 6] => vec![LitColor, MaterialAuxiliary],
        [0, 6, 5] => vec![LitColor, MaterialAuxiliary, ViewSpaceNormal],
        [0, 6, 3] => vec![LitColor, MaterialAuxiliary, TranslucencyAuxiliary],
        _ => {
            return Err(GalError::unsupported_feature(format!(
                "world glyph source has unsupported color schema {slots:?}"
            )))
        }
    };
    let contract = TexturedMaterialPassContract {
        pack_name: source.name().to_string(),
        generation: source.generation(),
        scope,
        program_path: path,
        stages,
        inputs: vec![
            TexturedMaterialSourceInput::MaterialTexture,
            TexturedMaterialSourceInput::VertexColor,
            TexturedMaterialSourceInput::PackedLight,
            TexturedMaterialSourceInput::ViewSpaceNormal,
            TexturedMaterialSourceInput::CameraAndEnvironment,
            TexturedMaterialSourceInput::CanonicalEntityIdentity,
            TexturedMaterialSourceInput::QuadMidTextureCoordinate,
            TexturedMaterialSourceInput::QuadTangent,
        ],
        outputs,
        output_color_slots: slots,
        alpha_cutoff_bits: crate::render::shaderpack::properties::shadow::source_alpha_test_cutoff(
            source,
            &format!("alphaTest.{family}"),
            0.1,
        )?
        .map(f32::to_bits),
    };
    let lowered = lower_world_glyph_source_pair(&vertex, &fragment)?;
    let declarations =
        TerrainSourceResourceBindings::from_source_stage(source, &contract.stages.fragment)?;
    let mut bindings = lowered
        .opaque_resource_contract()
        .bind_semantic_roles(&declarations)?;
    let material_names = bindings
        .bindings()
        .iter()
        .filter(|binding| binding.role() == TerrainSourceResourceRole::MaterialAtlas)
        .map(|binding| binding.resource_name().to_string())
        .collect::<Vec<_>>();
    for name in material_names {
        bindings = bindings.with_sampled_role_override(
            &name,
            TerrainSourceResourceRole::MaterialAtlas,
            TerrainSourceResourceRole::MaterialTexture,
        )?;
    }
    let mut program =
        prepare_lowered_textured_material_source_program(&contract, &lowered, &bindings)?;
    program.identity = ProgramIdentity::new(format!(
        "vulkanic:shader-pack/{}/world_glyph/{scope:?}/{family}/gen{}",
        source.name().to_ascii_lowercase(),
        source.generation()
    ));
    Ok(WorldGlyphSourceProgram {
        program: std::sync::Arc::new(program),
        entity_ids: ShaderPackEntityIdMap::from_source(source)?,
    })
}

#[cfg(test)]
mod tests;

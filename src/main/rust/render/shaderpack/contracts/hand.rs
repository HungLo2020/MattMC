//! Source-derived semantic contract for first-person hand/item material.
//!
//! This is deliberately discovery and lowering preparation only. It does not
//! select a route, bind an Iris program, or make the Java hand renderer part
//! of a Rust frame. A later first-person pass must supply its own copied hand
//! projection and depth semantics before this contract can execute.

use crate::render::vulkanic::error::{GalError, GalResult};

use crate::render::shaderpack::contracts::entity::EntitySourceVertexAttribute;
use crate::render::shaderpack::lowering::lower_hand_source_pair as lower_hand_source_stages;
pub(crate) use crate::render::shaderpack::lowering::LoweredHandSourcePair;
use crate::render::shaderpack::source::preprocess::{preprocess_artifact_with_runtime_options, PreprocessedShaderSource};
use crate::render::shaderpack::source::ShaderPackSource;
use crate::render::shaderpack::contracts::terrain::{
    parse_draw_buffers_slots, terrain_source_stages, TerrainProgramScope, TerrainSourceStages,
};
use crate::render::shaderpack::resources::bindings::{TerrainSourceResourceBindings, TerrainSourceResourceRole};

/// Semantic inputs needed by the selected pack's hand source. These are
/// gameplay/material values, never Java renderer or backend objects.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum HandSourceInput {
    MaterialTexture,
    VertexColor,
    PackedLight,
    ViewSpaceNormal,
    FirstPersonModelView,
    FirstPersonProjection,
    CameraAndEnvironment,
    HeldItemSemantics,
}

/// Named outputs of the bounded hand source stage. Source draw-buffer slots
/// are retained as pack metadata and are not GAL attachment indices.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum HandSourceOutput {
    LitColor,
    MaterialAuxiliary,
    ViewSpaceNormal,
}

/// Immutable source discovery result. It contains no program, target,
/// renderer state, or route selection.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct HandPassContract {
    pub pack_name: String,
    pub generation: u64,
    pub scope: TerrainProgramScope,
    pub program_path: String,
    pub stages: TerrainSourceStages,
    pub inputs: Vec<HandSourceInput>,
    pub vertex_attributes: Vec<EntitySourceVertexAttribute>,
    pub outputs: Vec<HandSourceOutput>,
    pub output_color_slots: Vec<u32>,
}

/// Discovers a pack's `gbuffers_hand` source contract without using Iris
/// runtime state. Discovery alone cannot admit first-person rendering.
pub fn derive_hand_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<HandPassContract> {
    let program_path = scope
        .hand_entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing hand fragment source for {scope:?}; tried {}",
                scope.hand_entry_candidates().join(", ")
            ))
        })?;
    let stages = terrain_source_stages(program_path)?;
    let vertex = preprocess_stage(source, &stages.vertex.path, &stages.vertex.defines)?;
    let fragment = preprocess_stage(source, &stages.fragment.path, &stages.fragment.defines)?;

    require_any(&vertex, &["gl_Vertex", "ftransform"])?;
    let output_color_slots = parse_draw_buffers_slots(&fragment)?;
    require(&fragment, "gl_FragData[0]")?;
    if output_color_slots.len() != 1 {
        require(&vertex, "GetLightMapCoordinates()")?;
        require(&vertex, "gl_Normal")?;
        require(&vertex, "gl_Color")?;
        require(&fragment, "texture2D(tex, texCoord)")?;
        require(&fragment, "color *= glColor")?;
        require(&fragment, "DoLighting(")?;
        require(&fragment, "gl_FragData[0] = color")?;
    }
    let outputs = match output_color_slots.as_slice() {
        [_] => vec![HandSourceOutput::LitColor],
        [0, 6] => vec![
            HandSourceOutput::LitColor,
            HandSourceOutput::MaterialAuxiliary,
        ],
        [0, 6, 5] => vec![
            HandSourceOutput::LitColor,
            HandSourceOutput::MaterialAuxiliary,
            HandSourceOutput::ViewSpaceNormal,
        ],
        slots => {
            return Err(GalError::unsupported_feature(format!(
                "selected hand source requires unsupported DRAWBUFFERS schema {slots:?}; expected one color slot, [0, 6] or [0, 6, 5]"
            )));
        }
    };

    let mut vertex_attributes = Vec::new();
    if uses_identifier(&vertex, "mc_midTexCoord") {
        vertex_attributes.push(EntitySourceVertexAttribute::MidTextureCoordinate);
    }
    if uses_identifier(&vertex, "at_tangent") {
        vertex_attributes.push(EntitySourceVertexAttribute::Tangent);
    }

    Ok(HandPassContract {
        pack_name: source.name().to_string(),
        generation: source.generation(),
        scope,
        program_path: program_path.to_string(),
        stages,
        inputs: vec![
            HandSourceInput::MaterialTexture,
            HandSourceInput::VertexColor,
            HandSourceInput::PackedLight,
            HandSourceInput::ViewSpaceNormal,
            HandSourceInput::FirstPersonModelView,
            HandSourceInput::FirstPersonProjection,
            HandSourceInput::CameraAndEnvironment,
            HandSourceInput::HeldItemSemantics,
        ],
        vertex_attributes,
        outputs,
        output_color_slots,
    })
}

/// Discovers the pack's glint program for first-person draws. Iris's hand pass
/// flushes hand glint with `ShaderKey.GLINT` too, so the same
/// `gbuffers_armor_glint` source runs with the first-person model view and
/// projection.
pub fn derive_hand_glint_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<HandPassContract> {
    let entity = crate::render::shaderpack::contracts::entity::derive_entity_glint_contract(source, scope)?;
    let outputs = entity
        .outputs
        .iter()
        .map(|output| match output {
            crate::render::shaderpack::contracts::entity::EntitySourceOutput::LitColor => Ok(HandSourceOutput::LitColor),
            crate::render::shaderpack::contracts::entity::EntitySourceOutput::MaterialAuxiliary => {
                Ok(HandSourceOutput::MaterialAuxiliary)
            }
            other => Err(GalError::unsupported_feature(format!(
                "hand glint source output {other:?} is not modeled"
            ))),
        })
        .collect::<GalResult<Vec<_>>>()?;
    Ok(HandPassContract {
        pack_name: entity.pack_name,
        generation: entity.generation,
        scope,
        program_path: entity.program_path,
        stages: entity.stages,
        inputs: vec![
            HandSourceInput::MaterialTexture,
            HandSourceInput::VertexColor,
            HandSourceInput::FirstPersonModelView,
            HandSourceInput::FirstPersonProjection,
            HandSourceInput::CameraAndEnvironment,
        ],
        vertex_attributes: Vec::new(),
        outputs,
        output_color_slots: entity.output_color_slots,
    })
}

/// Derives, lowers, binds, and prepares the first-person glint program.
pub fn prepare_hand_glint_source_program(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<crate::render::shaderpack::programs::LoweredHandSourceProgram> {
    let contract = derive_hand_glint_contract(source, scope)?;
    let lowered = lower_hand_source_pair(source, &contract)?;
    let declarations = TerrainSourceResourceBindings::from_source_stage(
        source, &contract.stages.fragment,
    )?;
    let bindings = bind_hand_source_resources(&lowered, &declarations)?;
    let mut program =
        crate::render::shaderpack::programs::prepare_lowered_hand_source_program(&contract, &lowered, &bindings)?;
    program.identity = crate::render::shaderpack::programs::ProgramIdentity::new(format!(
        "vulkanic:shader-pack/{}/hand_glint_source_gen{}",
        contract.pack_name.to_ascii_lowercase(),
        contract.generation
    ));
    Ok(program)
}

/// Attempts to lower the hand source through the existing owned local-texture
/// indexed stream. A hand-specific vertex adapter must first account for every
/// discovered compatibility attribute; otherwise this deliberately returns a
/// precise unsupported-feature error before route selection.
pub fn lower_hand_source_pair(
    source: &ShaderPackSource,
    contract: &HandPassContract,
) -> GalResult<LoweredHandSourcePair> {
    if contract.pack_name != source.name() || contract.generation != source.generation() {
        return Err(GalError::invalid_argument(
            "hand contract does not belong to the supplied shader-pack source",
        ));
    }
    let vertex = preprocess_stage_artifact(
        source,
        &contract.stages.vertex.path,
        &contract.stages.vertex.defines,
    )?;
    let fragment = preprocess_stage_artifact(
        source,
        &contract.stages.fragment.path,
        &contract.stages.fragment.defines,
    )?;
    let lowered = lower_hand_source_stages(&vertex, &fragment)?;
    lowered.require_backend_neutral_lowering()?;
    if contract.outputs.len() == 1 && lowered.fragment().outputs().len() != 1 {
        return Err(GalError::invalid_argument(
            "single-color hand source writes undeclared auxiliary outputs",
        ));
    }
    Ok(lowered)
}

/// Hand and entity sources both resolve the legacy `tex` sampler through a
/// Rust-owned local material texture. No atlas object or Iris binding crosses
/// this boundary.
pub fn bind_hand_source_resources(
    lowered: &LoweredHandSourcePair,
    declarations: &TerrainSourceResourceBindings,
) -> GalResult<crate::render::shaderpack::lowering::TerrainSourceOpaqueResourceBindingPlan> {
    lowered
        .opaque_resource_contract()
        .bind_semantic_roles(declarations)?
        .with_sampled_role_override(
            "tex",
            TerrainSourceResourceRole::MaterialAtlas,
            TerrainSourceResourceRole::MaterialTexture,
        )
}

fn preprocess_stage(
    source: &ShaderPackSource,
    path: &str,
    defines: &std::collections::BTreeMap<String, String>,
) -> GalResult<String> {
    Ok(preprocess_stage_artifact(source, path, defines)?
        .expanded_source()
        .to_string())
}

fn preprocess_stage_artifact(
    source: &ShaderPackSource,
    path: &str,
    defines: &std::collections::BTreeMap<String, String>,
) -> GalResult<PreprocessedShaderSource> {
    let defines = defines
        .iter()
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    preprocess_artifact_with_runtime_options(source, path, &defines)
}

fn require(source: &str, expression: &str) -> GalResult<()> {
    if source.contains(expression) {
        Ok(())
    } else {
        Err(GalError::unsupported_feature(format!(
            "selected hand source is missing required expression '{expression}'"
        )))
    }
}

fn require_any(source: &str, expressions: &[&str]) -> GalResult<()> {
    if expressions
        .iter()
        .any(|expression| source.contains(expression))
    {
        Ok(())
    } else {
        Err(GalError::unsupported_feature(format!(
            "selected hand source is missing every required expression in {expressions:?}"
        )))
    }
}

fn uses_identifier(source: &str, identifier: &str) -> bool {
    let mut start = 0;
    while let Some(relative) = source[start..].find(identifier) {
        let index = start + relative;
        let before = source[..index].chars().next_back();
        let after = source[index + identifier.len()..].chars().next();
        if !before.is_some_and(is_identifier_character)
            && !after.is_some_and(is_identifier_character)
        {
            return true;
        }
        start = index + identifier.len();
    }
    false
}

fn is_identifier_character(character: char) -> bool {
    character == '_' || character.is_ascii_alphanumeric()
}

#[cfg(test)]
mod tests {
    use crate::render::shaderpack::contracts::hand::*;
    use crate::render::shaderpack::source::preprocess::complete_bundled_pack_source_for_test;
    use crate::render::shaderpack::source::ShaderSourceFile;

    fn source(draw_buffers: &str, attributes: &str) -> ShaderPackSource {
        ShaderPackSource::new(
            "hand-fixture",
            9,
            vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_hand.vsh",
                    format!(
                        "#version 130\nvoid main() {{ vec4 p = gl_Vertex; vec2 light = GetLightMapCoordinates(); vec3 normal = gl_Normal; vec4 color = gl_Color; {attributes} gl_Position = ftransform(); }}"
                    ),
                ),
                ShaderSourceFile::new(
                    "world0/gbuffers_hand.fsh",
                    format!(
                        "#version 130\nuniform sampler2D tex;\nvoid DoLighting() {{}}\nvoid main() {{ vec4 color = texture2D(tex, texCoord); color *= glColor; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; /* DRAWBUFFERS:{draw_buffers} */ }}"
                    ),
                ),
            ],
        )
        .unwrap()
    }

    #[test]
    fn discovers_owned_hand_semantics_and_named_outputs() {
        let source = source(
            "06",
            "attribute vec4 mc_midTexCoord; attribute vec4 at_tangent;",
        );
        let contract = derive_hand_contract(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!("world0/gbuffers_hand.fsh", contract.program_path);
        assert_eq!(vec![0, 6], contract.output_color_slots);
        assert_eq!(
            vec![
                HandSourceOutput::LitColor,
                HandSourceOutput::MaterialAuxiliary
            ],
            contract.outputs
        );
        assert!(contract
            .inputs
            .contains(&HandSourceInput::HeldItemSemantics));
        assert!(contract
            .inputs
            .contains(&HandSourceInput::FirstPersonModelView));
        assert!(contract
            .inputs
            .contains(&HandSourceInput::FirstPersonProjection));
        assert_eq!(
            vec![
                EntitySourceVertexAttribute::MidTextureCoordinate,
                EntitySourceVertexAttribute::Tangent,
            ],
            contract.vertex_attributes
        );
        let error = lower_hand_source_pair(&source, &contract)
            .expect_err("the generic entity adapter must not invent hand vertex attributes");
        assert!(error
            .to_string()
            .contains("compatibility_vertex_attributes"));
    }

    #[test]
    fn bundled_complementary_hand_source_has_a_discoverable_owned_contract() {
        let source = complete_bundled_pack_source_for_test();
        let contract = derive_hand_contract(&source, TerrainProgramScope::Overworld)
            .expect("bundled hand source must be discoverable without Iris state");

        assert_eq!("world0/gbuffers_hand.fsh", contract.program_path);
        assert!(contract.inputs.contains(&HandSourceInput::MaterialTexture));
        assert!(contract.inputs.contains(&HandSourceInput::PackedLight));
        assert!(contract
            .inputs
            .contains(&HandSourceInput::HeldItemSemantics));
        assert!(contract.outputs.contains(&HandSourceOutput::LitColor));
        assert!(contract
            .outputs
            .contains(&HandSourceOutput::MaterialAuxiliary));
    }

    #[test]
    fn rejects_unknown_hand_output_schema_before_route_selection() {
        let error =
            derive_hand_contract(&source("012", ""), TerrainProgramScope::Overworld).unwrap_err();
        assert!(error.to_string().contains("unsupported DRAWBUFFERS schema"));
    }
}

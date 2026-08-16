//! Source-derived semantic contract for ordinary indexed world entities.
//!
//! Entity source programs are not generic textured-material programs. They
//! commonly consume an entity identifier/color and optional mid-UV or tangent
//! attributes in addition to the ordinary texture, light, normal, and vertex
//! color stream. This module discovers that requirement without borrowing an
//! Iris program, vertex array, texture binding, or other native state.

use crate::render::vulkanic::error::{GalError, GalResult};

use super::entity_id_map::ShaderPackEntityIdMap;
use super::lowering::lower_entity_source_pair as lower_entity_source_stages;
pub use super::lowering::LoweredEntitySourcePair;
use super::preprocess::preprocess_artifact_with_runtime_options;
use super::source::ShaderPackSource;
use super::terrain_contract::{
    parse_draw_buffers_slots, terrain_source_stages, TerrainPassOutput, TerrainProgramScope,
    TerrainSourceStages,
};
use super::terrain_source_resources::{TerrainSourceResourceBindings, TerrainSourceResourceRole};

/// Semantic values that a source-selected entity pass may require. These are
/// independent of Java renderer objects and backend bindings.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum EntitySourceInput {
    MaterialTexture,
    VertexColor,
    PackedLight,
    ViewSpaceNormal,
    EntityIdentity,
    EntityColor,
    CameraAndEnvironment,
}

/// Optional source vertex fields that cannot be safely invented from the
/// common indexed-mesh stream. A later lowerer must either receive the exact
/// semantic value or reject selected-source execution before submission.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum EntitySourceVertexAttribute {
    MidTextureCoordinate,
    Tangent,
}

/// Named outputs of the bounded entity pass. Their source color slots are
/// retained separately as pack metadata and are never GAL attachment indices.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum EntitySourceOutput {
    LitColor,
    MaterialAuxiliary,
    ViewSpaceNormal,
}

impl EntitySourceOutput {
    pub(crate) fn terrain_output(self) -> TerrainPassOutput {
        match self {
            Self::LitColor => TerrainPassOutput::LitTerrainColor,
            Self::MaterialAuxiliary => TerrainPassOutput::MaterialAuxiliary,
            Self::ViewSpaceNormal => TerrainPassOutput::ViewSpaceNormal,
        }
    }
}

/// Immutable source discovery result. It contains no compiled program, route
/// selection, backend resource, or native renderer state.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct EntityPassContract {
    pub pack_name: String,
    pub generation: u64,
    pub scope: TerrainProgramScope,
    pub program_path: String,
    pub stages: TerrainSourceStages,
    pub inputs: Vec<EntitySourceInput>,
    pub vertex_attributes: Vec<EntitySourceVertexAttribute>,
    pub outputs: Vec<EntitySourceOutput>,
    pub output_color_slots: Vec<u32>,
    /// Rust-owned source-generation mapping from canonical entity identity to
    /// pack ID. Java never supplies this pack-specific integer.
    pub entity_ids: ShaderPackEntityIdMap,
}

impl EntityPassContract {
    /// Resolves copied canonical gameplay identity through the selected source
    /// generation. Java never carries this pack-specific numeric value.
    pub fn entity_id_for_identity(&self, identity: &str) -> GalResult<i32> {
        self.entity_ids.resolve(identity)
    }

    pub fn entity_id_generation(&self) -> u64 {
        self.entity_ids.generation()
    }

    /// Resolves one copied entity draw's shader-pack semantics. The pack ID
    /// is derived in Rust from the immutable source generation; Java's
    /// transport never supplies or caches pack-specific numeric IDs.
    pub fn resolve_draw_semantics(
        &self,
        entity_identity: &str,
        entity_color_argb: u32,
    ) -> GalResult<EntitySourceDrawSemantics> {
        let canonical_identity = canonical_entity_identity(entity_identity)?;
        Ok(EntitySourceDrawSemantics {
            entity_identity: canonical_identity.clone(),
            entity_id: self.entity_id_for_identity(&canonical_identity)?,
            entity_color: argb_to_rgba(entity_color_argb),
            entity_id_generation: self.entity_id_generation(),
        })
    }
}

/// Fully resolved per-draw source semantics. This is intentionally a CPU-only
/// record: it has no uniform location, descriptor, texture unit, Java object,
/// or backend handle. A future entity writer must group only compatible values
/// and pack them through the typed source-uniform contract.
#[derive(Clone, Debug, PartialEq)]
pub struct EntitySourceDrawSemantics {
    pub entity_identity: String,
    pub entity_id: i32,
    /// Normalized RGBA color. An all-zero value is the source-standard
    /// transparent override and therefore preserves the mesh vertex color.
    pub entity_color: [f32; 4],
    pub entity_id_generation: u64,
}

fn canonical_entity_identity(identity: &str) -> GalResult<String> {
    let Some((namespace, path)) = identity.split_once(':') else {
        return Err(GalError::invalid_argument(format!(
            "entity source draw identity '{identity}' must be a canonical resource location"
        )));
    };
    if namespace.is_empty()
        || path.is_empty()
        || namespace.bytes().any(|byte| {
            !(byte.is_ascii_lowercase()
                || byte.is_ascii_digit()
                || byte == b'_'
                || byte == b'-'
                || byte == b'.')
        })
        || path.bytes().any(|byte| {
            !(byte.is_ascii_lowercase()
                || byte.is_ascii_digit()
                || matches!(byte, b'_' | b'-' | b'.' | b'/'))
        })
    {
        return Err(GalError::invalid_argument(format!(
            "entity source draw identity '{identity}' is not canonical"
        )));
    }
    Ok(identity.to_string())
}

fn argb_to_rgba(argb: u32) -> [f32; 4] {
    [
        ((argb >> 16) & 0xff) as f32 / 255.0,
        ((argb >> 8) & 0xff) as f32 / 255.0,
        (argb & 0xff) as f32 / 255.0,
        ((argb >> 24) & 0xff) as f32 / 255.0,
    ]
}

/// Discovers the selected pack's ordinary entity contract. Discovery is not
/// execution admission: a later Rust-owned lowerer must prove it supplies the
/// required vertex fields and semantic entity values exactly.
pub fn derive_entity_contract(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<EntityPassContract> {
    let program_path = scope
        .entity_entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing entity fragment source for {scope:?}; tried {}",
                scope.entity_entry_candidates().join(", ")
            ))
        })?;
    let stages = terrain_source_stages(program_path)?;
    let vertex = preprocess_stage(source, &stages.vertex.path, &stages.vertex.defines)?;
    let fragment = preprocess_stage(source, &stages.fragment.path, &stages.fragment.defines)?;

    require_any(&vertex, &["gl_Vertex", "ftransform"])?;
    require(&vertex, "GetLightMapCoordinates()")?;
    require(&vertex, "gl_Normal")?;
    require(&vertex, "gl_Color")?;
    require(&fragment, "texture2D(tex, texCoord)")?;
    require(&fragment, "color *= glColor")?;
    require(&fragment, "DoLighting(")?;
    require(&fragment, "gl_FragData[0] = color")?;
    require(&fragment, "gl_FragData[1]")?;

    let slots = parse_draw_buffers_slots(&fragment)?;
    let outputs = match slots.as_slice() {
        [0, 6] => vec![
            EntitySourceOutput::LitColor,
            EntitySourceOutput::MaterialAuxiliary,
        ],
        [0, 6, 5] => vec![
            EntitySourceOutput::LitColor,
            EntitySourceOutput::MaterialAuxiliary,
            EntitySourceOutput::ViewSpaceNormal,
        ],
        _ => {
            return Err(GalError::unsupported_feature(format!(
                "selected entity source requires unsupported DRAWBUFFERS schema {slots:?}; expected [0, 6] or [0, 6, 5]"
            )));
        }
    };
    if outputs.len() != slots.len() {
        return Err(GalError::unsupported_feature(format!(
            "selected entity source has inconsistent DRAWBUFFERS schema {slots:?}"
        )));
    }

    let mut inputs = vec![
        EntitySourceInput::MaterialTexture,
        EntitySourceInput::VertexColor,
        EntitySourceInput::PackedLight,
        EntitySourceInput::ViewSpaceNormal,
        EntitySourceInput::CameraAndEnvironment,
    ];
    if uses_identifier(&fragment, "entityId") {
        inputs.push(EntitySourceInput::EntityIdentity);
    }
    if uses_identifier(&fragment, "entityColor") {
        inputs.push(EntitySourceInput::EntityColor);
    }

    let mut vertex_attributes = Vec::new();
    if uses_identifier(&vertex, "mc_midTexCoord") {
        vertex_attributes.push(EntitySourceVertexAttribute::MidTextureCoordinate);
    }
    if uses_identifier(&vertex, "at_tangent") {
        vertex_attributes.push(EntitySourceVertexAttribute::Tangent);
    }

    Ok(EntityPassContract {
        pack_name: source.name().to_string(),
        generation: source.generation(),
        scope,
        program_path: program_path.to_string(),
        stages,
        inputs,
        vertex_attributes,
        outputs,
        output_color_slots: slots,
        entity_ids: ShaderPackEntityIdMap::from_source(source)?,
    })
}

/// Lowers the paired selected entity source through the common Rust-owned
/// indexed source stream. This validates only source and semantic resource
/// shape; it cannot create a program, select a route, or make an entity draw
/// executable without the separate entity-instance contract.
pub fn lower_entity_source_pair(
    source: &ShaderPackSource,
    contract: &EntityPassContract,
) -> GalResult<LoweredEntitySourcePair> {
    if contract.pack_name != source.name() || contract.generation != source.generation() {
        return Err(GalError::invalid_argument(
            "entity contract does not belong to the supplied shader-pack source",
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
    let lowered = lower_entity_source_stages(&vertex, &fragment)?;
    lowered.require_backend_neutral_lowering()?;
    Ok(lowered)
}

/// Binds the selected entity source's base-color sampler to the explicit
/// draw-local material-texture role. Complementary's pack-wide declaration
/// maps `tex` to the terrain atlas because terrain owns the same legacy
/// sampler spelling; entity source is a separate material domain and must
/// override only that one compatible active sampler.
pub fn bind_entity_source_resources(
    lowered: &LoweredEntitySourcePair,
    declarations: &TerrainSourceResourceBindings,
) -> GalResult<super::lowering::TerrainSourceOpaqueResourceBindingPlan> {
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
) -> GalResult<super::preprocess::PreprocessedShaderSource> {
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
            "selected entity source is missing required expression '{expression}'"
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
            "selected entity source is missing every required expression in {expressions:?}"
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
    use super::*;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;
    use crate::render::vulkanic::shader_pack::terrain_source_resources::{
        TerrainSourceResourceBindings, TerrainSourceResourceRole, TERRAIN_RESOURCE_BINDINGS_PATH,
    };

    fn source(draw_buffers: &str, entity_inputs: &str, attributes: &str) -> ShaderPackSource {
        ShaderPackSource::new(
            "entity-fixture",
            19,
            vec![
                ShaderSourceFile::new(
                    "world0/gbuffers_entities.vsh",
                    format!(
                        "#version 130\nvoid main() {{ vec4 p = gl_Vertex; vec2 light = GetLightMapCoordinates(); vec3 normal = gl_Normal; vec4 color = gl_Color; {attributes} gl_Position = ftransform(); }}"
                    ),
                ),
                ShaderSourceFile::new(
                    "world0/gbuffers_entities.fsh",
                    format!(
                        "#version 130\nuniform sampler2D tex;\nvoid DoLighting() {{}}\nvoid main() {{ vec4 color = texture2D(tex, texCoord); color *= glColor; {entity_inputs} DoLighting(); gl_FragData[0] = color; gl_FragData[1] = color; /* DRAWBUFFERS:{draw_buffers} */ }}"
                    ),
                ),
                ShaderSourceFile::new("entity.properties", "entity.50076=boat\n"),
                ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
            ],
        )
        .unwrap()
    }

    #[test]
    fn discovers_entity_identity_color_and_optional_vertex_attributes() {
        let source = source(
            "06",
            "if (entityId == 50004) color.rgb = entityColor.rgb;",
            "vec4 mid = mc_midTexCoord; vec4 tangent = at_tangent;",
        );
        let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();

        assert_eq!("world0/gbuffers_entities.fsh", contract.program_path);
        assert_eq!(vec![0, 6], contract.output_color_slots);
        assert!(contract.inputs.contains(&EntitySourceInput::EntityIdentity));
        assert!(contract.inputs.contains(&EntitySourceInput::EntityColor));
        assert_eq!(
            vec![
                EntitySourceVertexAttribute::MidTextureCoordinate,
                EntitySourceVertexAttribute::Tangent,
            ],
            contract.vertex_attributes
        );
        assert_eq!(
            TerrainPassOutput::MaterialAuxiliary,
            EntitySourceOutput::MaterialAuxiliary.terrain_output()
        );
    }

    #[test]
    fn requires_the_bounded_entity_draw_buffer_schema() {
        let source = source("015", "", "");
        let error = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap_err();
        assert!(error.to_string().contains("DRAWBUFFERS schema"));
    }

    #[test]
    fn does_not_invent_entity_fields_when_the_selected_source_does_not_use_them() {
        let source = source("06", "", "");
        let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();

        assert!(!contract.inputs.contains(&EntitySourceInput::EntityIdentity));
        assert!(!contract.inputs.contains(&EntitySourceInput::EntityColor));
        assert!(contract.vertex_attributes.is_empty());
    }

    #[test]
    fn retains_the_optional_named_view_space_normal_output() {
        let source = source("065", "", "");
        let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();

        assert_eq!(
            vec![
                EntitySourceOutput::LitColor,
                EntitySourceOutput::MaterialAuxiliary,
                EntitySourceOutput::ViewSpaceNormal,
            ],
            contract.outputs
        );
        assert_eq!(
            TerrainPassOutput::ViewSpaceNormal,
            EntitySourceOutput::ViewSpaceNormal.terrain_output()
        );
    }

    #[test]
    fn lowers_entity_source_through_the_owned_indexed_mesh_stream() {
        let source = source(
            "06",
            "if (entityId == 50004) color.rgb = entityColor.rgb;",
            "vec4 mid = mc_midTexCoord; vec4 tangent = at_tangent;",
        );
        let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();
        let lowered = lower_entity_source_pair(&source, &contract).unwrap();

        lowered.require_backend_neutral_lowering().unwrap();
        assert!(lowered
            .vertex()
            .source()
            .contains("VulkanicSourceTerrainVertices"));
        assert!(lowered.vertex().source().contains("vulkanic_source_entity"));
        assert!(lowered
            .fragment()
            .source()
            .contains("out_terrain_material_auxiliary"));
    }

    #[test]
    fn entity_source_rebinds_only_the_base_sampler_to_local_material_texture() {
        let source = source("06", "", "");
        let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();
        let lowered = lower_entity_source_pair(&source, &contract).unwrap();
        let declarations = TerrainSourceResourceBindings::from_source(&source).unwrap();
        let bindings = bind_entity_source_resources(&lowered, &declarations).unwrap();

        assert_eq!(
            Some(TerrainSourceResourceRole::MaterialTexture),
            bindings.role_for("tex")
        );
    }

    #[test]
    fn resolves_pack_entity_ids_from_canonical_gameplay_identity() {
        let source = source("06", "", "");
        let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!(19, contract.entity_id_generation());
        assert_eq!(
            50076,
            contract.entity_id_for_identity("minecraft:boat").unwrap()
        );
        assert_eq!(
            -1,
            contract.entity_id_for_identity("minecraft:arrow").unwrap()
        );
    }

    #[test]
    fn resolves_per_draw_entity_semantics_without_java_pack_ids() {
        let source = source("06", "", "");
        let contract = derive_entity_contract(&source, TerrainProgramScope::Overworld).unwrap();

        let boat = contract
            .resolve_draw_semantics("minecraft:boat", 0x8040_80ff)
            .unwrap();
        assert_eq!("minecraft:boat", boat.entity_identity);
        assert_eq!(50_076, boat.entity_id);
        assert_eq!(
            [64.0 / 255.0, 128.0 / 255.0, 1.0, 128.0 / 255.0],
            boat.entity_color
        );
        assert_eq!(19, boat.entity_id_generation);

        let arrow = contract
            .resolve_draw_semantics("minecraft:arrow", 0)
            .unwrap();
        assert_eq!(-1, arrow.entity_id);
        assert_eq!([0.0; 4], arrow.entity_color);
        assert!(contract
            .resolve_draw_semantics("Minecraft:Arrow", 0)
            .unwrap_err()
            .to_string()
            .contains("not canonical"));
    }
}

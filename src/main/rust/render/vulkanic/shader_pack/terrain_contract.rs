//! Source-derived, backend-neutral contract for a normal terrain material pass.
//!
//! This module deliberately describes inputs and outputs, rather than Iris
//! attachment slots, shader objects, or API state. Backends lower the contract
//! through the ordinary shader-pack runtime.

use std::collections::{BTreeMap, BTreeSet};
use std::path::Path;

use crate::render::vulkanic::error::{GalError, GalResult};

use super::preprocess::preprocess_artifact_with_runtime_options;
use super::source::{
    ShaderPackSource, ShaderSourceFile, RUNTIME_BLOCK_STATE_IDENTITIES_PATH,
    RUNTIME_ENVIRONMENT_PATH,
};
use super::voxel_light_volume::{
    VoxelLightVolumeReadiness, VoxelLightVolumeRequirements, VoxelLightVolumeUpdatePolicy,
};

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainMaterialClass {
    Opaque,
    Cutout,
    Translucent,
}

/// Semantic source-pass family. This distinguishes the pack's normal terrain
/// contract from its separate translucent/material-water stage without
/// importing Iris pass names, attachments, or API state into the renderer.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainSourcePassKind {
    OpaqueCutout,
    Translucent,
}

/// Semantic world scope for source-pack terrain program selection. This is a
/// pack-source identity only: it carries no Iris program, framebuffer, or GL
/// state. Runtime admission must still provide the matching explicit world
/// environment before any selected source can execute.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainProgramScope {
    Default,
    Overworld,
    Nether,
    End,
}

impl TerrainProgramScope {
    fn entry_candidates(self) -> &'static [&'static str] {
        match self {
            // The internal fixture uses the explicit program namespace. Real
            // packs conventionally expose the default fragment at root.
            Self::Default => &["program/gbuffers_terrain.glsl", "gbuffers_terrain.fsh"],
            // A pack's root terrain program is its explicit universal source,
            // not an accidental fallback to a different dimension. The
            // internal program path exists only for the Rust source fixture.
            Self::Overworld => &[
                "world0/gbuffers_terrain.fsh",
                "program/gbuffers_terrain.glsl",
                "gbuffers_terrain.fsh",
            ],
            Self::Nether => &[
                "world-1/gbuffers_terrain.fsh",
                "program/gbuffers_terrain.glsl",
                "gbuffers_terrain.fsh",
            ],
            Self::End => &[
                "world1/gbuffers_terrain.fsh",
                "program/gbuffers_terrain.glsl",
                "gbuffers_terrain.fsh",
            ],
        }
    }

    fn shadow_entry_candidates(self) -> &'static [&'static str] {
        match self {
            Self::Default => &["shadow.fsh", "program/shadow.glsl"],
            Self::Overworld => &["world0/shadow.fsh", "shadow.fsh", "program/shadow.glsl"],
            Self::Nether => &["world-1/shadow.fsh", "shadow.fsh", "program/shadow.glsl"],
            Self::End => &["world1/shadow.fsh", "shadow.fsh", "program/shadow.glsl"],
        }
    }

    /// Candidate source entries for generic textured world material. This is
    /// a pack-source identity, not an Iris render phase or an OpenGL program.
    /// It intentionally remains separate from terrain because its vertex
    /// contract carries standalone texture, lightmap, and normal semantics.
    pub(crate) fn textured_material_entry_candidates(self) -> &'static [&'static str] {
        match self {
            Self::Default => &["program/gbuffers_textured.glsl", "gbuffers_textured.fsh"],
            Self::Overworld => &[
                "world0/gbuffers_textured.fsh",
                "program/gbuffers_textured.glsl",
                "gbuffers_textured.fsh",
            ],
            Self::Nether => &[
                "world-1/gbuffers_textured.fsh",
                "program/gbuffers_textured.glsl",
                "gbuffers_textured.fsh",
            ],
            Self::End => &[
                "world1/gbuffers_textured.fsh",
                "program/gbuffers_textured.glsl",
                "gbuffers_textured.fsh",
            ],
        }
    }

    /// Candidate source entries for ordinary indexed world entities. This is
    /// intentionally separate from both terrain and generic textured material:
    /// entity programs may require entity identity, entity-color, and optional
    /// mesh attributes that are not valid terrain defaults.
    pub(crate) fn entity_entry_candidates(self) -> &'static [&'static str] {
        match self {
            Self::Default => &["program/gbuffers_entities.glsl", "gbuffers_entities.fsh"],
            Self::Overworld => &[
                "world0/gbuffers_entities.fsh",
                "program/gbuffers_entities.glsl",
                "gbuffers_entities.fsh",
            ],
            Self::Nether => &[
                "world-1/gbuffers_entities.fsh",
                "program/gbuffers_entities.glsl",
                "gbuffers_entities.fsh",
            ],
            Self::End => &[
                "world1/gbuffers_entities.fsh",
                "program/gbuffers_entities.glsl",
                "gbuffers_entities.fsh",
            ],
        }
    }

    /// Candidate source entries for the first-person hand/item stage. This is
    /// a shader-pack source identity only: it does not borrow Iris's hand
    /// pass, projection buffer, or transient OpenGL state.
    pub(crate) fn hand_entry_candidates(self) -> &'static [&'static str] {
        match self {
            Self::Default => &["program/gbuffers_hand.glsl", "gbuffers_hand.fsh"],
            Self::Overworld => &[
                "world0/gbuffers_hand.fsh",
                "program/gbuffers_hand.glsl",
                "gbuffers_hand.fsh",
            ],
            Self::Nether => &[
                "world-1/gbuffers_hand.fsh",
                "program/gbuffers_hand.glsl",
                "gbuffers_hand.fsh",
            ],
            Self::End => &[
                "world1/gbuffers_hand.fsh",
                "program/gbuffers_hand.glsl",
                "gbuffers_hand.fsh",
            ],
        }
    }

    pub(crate) fn distant_horizons_entry_candidates(self) -> &'static [&'static str] {
        match self {
            // The internal fixture has no DH producer. Real packs must expose
            // an explicit DH stage rather than borrowing the ordinary terrain
            // source or an unrelated active renderer program.
            Self::Default => &["dh_terrain.fsh", "program/dh_terrain.glsl"],
            Self::Overworld => &[
                "world0/dh_terrain.fsh",
                "dh_terrain.fsh",
                "program/dh_terrain.glsl",
            ],
            Self::Nether => &[
                "world-1/dh_terrain.fsh",
                "dh_terrain.fsh",
                "program/dh_terrain.glsl",
            ],
            Self::End => &[
                "world1/dh_terrain.fsh",
                "dh_terrain.fsh",
                "program/dh_terrain.glsl",
            ],
        }
    }

    /// The source pack's Distant Horizons translucent/material-water entry
    /// is distinct from both ordinary `gbuffers_water` and DH opaque terrain.
    /// Keeping the source identity separate prevents a later executor from
    /// binding a Minecraft terrain vertex stream to a DH program or treating
    /// a DH water range as a near-terrain draw.
    pub(crate) fn distant_horizons_translucent_entry_candidates(self) -> &'static [&'static str] {
        match self {
            Self::Default => &["dh_water.fsh", "program/dh_water.glsl"],
            Self::Overworld => &[
                "world0/dh_water.fsh",
                "dh_water.fsh",
                "program/dh_water.glsl",
            ],
            Self::Nether => &[
                "world-1/dh_water.fsh",
                "dh_water.fsh",
                "program/dh_water.glsl",
            ],
            Self::End => &[
                "world1/dh_water.fsh",
                "dh_water.fsh",
                "program/dh_water.glsl",
            ],
        }
    }

    fn translucent_entry_candidates(self) -> &'static [&'static str] {
        match self {
            Self::Default => &["program/gbuffers_water.glsl", "gbuffers_water.fsh"],
            Self::Overworld => &[
                "world0/gbuffers_water.fsh",
                "program/gbuffers_water.glsl",
                "gbuffers_water.fsh",
            ],
            Self::Nether => &[
                "world-1/gbuffers_water.fsh",
                "program/gbuffers_water.glsl",
                "gbuffers_water.fsh",
            ],
            Self::End => &[
                "world1/gbuffers_water.fsh",
                "program/gbuffers_water.glsl",
                "gbuffers_water.fsh",
            ],
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainPassInput {
    AtlasColor,
    AtlasUv,
    Tint,
    AmbientOcclusion,
    PackedBlockLight,
    PackedSkyLight,
    GeometricNormal,
    MaterialIdentity,
    WorldPosition,
    Camera,
    DirectionalLight,
    Environment,
    ShadowMap,
    ColoredVoxelLightVolume,
    MainDepth,
    SceneColor,
    ViewDirection,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainPassOutput {
    LitTerrainColor,
    MaterialAuxiliary,
    ViewSpaceNormal,
    TranslucencyAuxiliary,
}

/// Backend-neutral blend behavior explicitly requested by a selected
/// translucent source stage. These are source-material semantics, not GL
/// blend enums or Vulkan pipeline bits.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum TerrainTranslucentBlend {
    /// `src.rgb * src.a + dst.rgb * (1 - src.a)`, with matching source-over
    /// alpha accumulation.
    SourceAlphaOver,
}

/// Source-derived alpha test retained separately from opaque/cutout policy.
/// The selected translucent pass owns its own threshold because a pack may
/// use alpha blending and a minimal discard in the same material stage.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainTranslucentAlphaTest {
    /// Canonical IEEE-754 bits retain an exact source-generation identity
    /// without making NaN or platform formatting part of cache equality.
    greater_than_bits: u32,
}

impl TerrainTranslucentAlphaTest {
    pub fn greater_than(self) -> f32 {
        f32::from_bits(self.greater_than_bits)
    }
}

/// Explicit raster semantics required before a selected translucent source
/// stage can be prepared. Missing state is intentionally represented as
/// unavailable rather than guessed from a legacy renderer default.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainTranslucentRasterState {
    pub blend: TerrainTranslucentBlend,
    pub alpha_test: TerrainTranslucentAlphaTest,
}

/// Ordered normal-terrain expressions recovered from the selected program.
/// These are shader semantics, never API attachment slots or native state.
#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainPassOperation {
    AtlasSample,
    AlphaDiscard,
    TintMultiply,
    TerrainLighting,
    ColoredVoxelLighting,
    LitColorOutput,
    MaterialAuxiliaryOutput,
    ViewSpaceNormalOutput,
    TranslucencyAuxiliaryOutput,
    TranslucentFog,
}

impl TerrainPassOutput {
    pub fn semantic_name(self) -> &'static str {
        match self {
            Self::LitTerrainColor => "terrain_lit_color",
            Self::MaterialAuxiliary => "terrain_material_auxiliary",
            Self::ViewSpaceNormal => "terrain_view_space_normal",
            Self::TranslucencyAuxiliary => "terrain_translucency_auxiliary",
        }
    }
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum UnsupportedTerrainFeature {
    ParallaxOcclusionMapping,
    GeneratedNormals,
    AnisotropicFiltering,
    ColoredVoxelLighting,
}

#[derive(Clone, Copy, Debug, Eq, Ord, PartialEq, PartialOrd)]
pub enum TerrainPassRequiredResource {
    ColoredVoxelLightVolume,
}

/// One source stage selected by semantic pack/world metadata. The stage carries
/// only source path and preprocessor choices; it is not a compiler object,
/// backend binding, or Iris program reference.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceStage {
    pub path: String,
    pub defines: BTreeMap<String, String>,
}

/// Complete normal-terrain source pairing. A later executable source lowering
/// must resolve and validate both artifacts; discovering a fragment alone can
/// never establish shader-pack route readiness.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainSourceStages {
    pub vertex: TerrainSourceStage,
    pub fragment: TerrainSourceStage,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct TerrainPassContract {
    pub pass_kind: TerrainSourcePassKind,
    pub pack_name: String,
    pub generation: u64,
    /// Backward-compatible fragment source identity for existing diagnostics.
    pub program_path: String,
    pub material_classes: BTreeSet<TerrainMaterialClass>,
    pub inputs: BTreeSet<TerrainPassInput>,
    pub outputs: BTreeSet<TerrainPassOutput>,
    /// Source-declared pack-color slot for each named terrain output. This is
    /// semantic shader-pack metadata recovered from `DRAWBUFFERS`, not a GAL
    /// attachment index or backend binding.
    pub output_color_slots: BTreeMap<TerrainPassOutput, u32>,
    pub property_defines: BTreeMap<String, String>,
    pub material_ids: BTreeMap<i32, Vec<String>>,
    /// Rust-owned resolution of canonical Minecraft state IDs to the selected
    /// pack's material IDs. `-1` is an explicit resolved pack default, while
    /// an absent table means the source generation never supplied the bounded
    /// game-semantic identity snapshot required for selected execution.
    pub runtime_block_state_material_ids: Option<BTreeMap<i32, i32>>,
    pub operations: Vec<TerrainPassOperation>,
    pub required_resources: BTreeSet<TerrainPassRequiredResource>,
    pub voxel_light_volume_requirements: Option<VoxelLightVolumeRequirements>,
    /// Present only for the distinct translucent source stage. It is derived
    /// from selected pack configuration and later lowered to ordinary GAL
    /// pipeline state by the Rust runtime.
    pub translucent_raster_state: Option<TerrainTranslucentRasterState>,
    pub unsupported: BTreeSet<UnsupportedTerrainFeature>,
}

impl TerrainPassContract {
    pub fn output_color_slot(&self, output: TerrainPassOutput) -> Option<u32> {
        self.output_color_slots.get(&output).copied()
    }

    /// Resolves a copied canonical block-state identity through the selected
    /// pack's `block.properties` rules. This accepts both the runtime snapshot
    /// form (`minecraft:block|property=value`) and the Distant Horizons
    /// semantic form (`minecraft:block_STATE_{property:value}`), but never a
    /// renderer object, atlas identity, or backend handle.
    pub(crate) fn material_id_for_block_state_identity(&self, identity: &str) -> GalResult<i32> {
        let state = parse_semantic_block_state_identity(identity)?;
        Ok(self
            .material_ids
            .iter()
            .find(|(_, selectors)| {
                selectors
                    .iter()
                    .any(|selector| selector_matches_block_state(selector, &state))
            })
            .map_or(-1, |(material_id, _)| *material_id))
    }

    /// Reconstructs the complete normal-terrain source pairing from the
    /// canonical selected fragment identity. A future source route must use
    /// both returned stages before it may claim executable readiness.
    pub fn source_stages(&self) -> GalResult<TerrainSourceStages> {
        terrain_source_stages(&self.program_path)
    }

    pub fn supports_selected_subset(&self) -> bool {
        self.unsupported.is_empty()
    }

    pub fn require_selected_subset(&self) -> GalResult<()> {
        if self.supports_selected_subset() {
            return Ok(());
        }
        Err(GalError::unsupported_feature(format!(
            "terrain shader-pass contract contains unsupported features: {}",
            self.unsupported
                .iter()
                .map(|feature| format!("{feature:?}"))
                .collect::<Vec<_>>()
                .join(", ")
        )))
    }

    /// Admission for executable selected-source terrain. Feature discovery is
    /// separate from resource readiness: a pack may require a semantic volume
    /// while all of its shader branches are otherwise supported.
    pub fn require_selected_subset_with_resources(
        &self,
        voxel_light_volume: Option<&VoxelLightVolumeReadiness>,
        frame_counter: u64,
    ) -> GalResult<()> {
        self.require_selected_subset()?;
        if self
            .required_resources
            .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume)
        {
            let volume = voxel_light_volume.ok_or_else(|| {
                GalError::invalid_argument("missing required colored voxel-light volume generation")
            })?;
            volume.binding_for_frame(frame_counter)?;
            volume
                .descriptor()
                .requirements
                .update_policy
                .require_selected_source_support()?;
        }
        Ok(())
    }
}

pub fn derive_complementary_terrain_contract(
    source: &ShaderPackSource,
) -> GalResult<TerrainPassContract> {
    derive_complementary_terrain_contract_for_scope(source, TerrainProgramScope::Default)
}

/// Discovers a terrain fragment source for an explicitly supplied semantic
/// world scope. A missing scoped entry may use only the pack's explicit
/// universal terrain source; callers must never infer a program from an
/// unrelated active Iris pass.
pub fn derive_complementary_terrain_contract_for_scope(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<TerrainPassContract> {
    let terrain_path = scope
        .entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing terrain fragment source for {scope:?}; tried {}",
                scope.entry_candidates().join(", ")
            ))
        })?;
    // Contract discovery must use the same selected conditional source as
    // executable lowering. Raw include expansion sees mutually exclusive pack
    // branches together and can falsely reject a configured profile.
    let terrain_stage = terrain_source_stages(terrain_path)?.fragment;
    let terrain_defines = terrain_stage
        .defines
        .iter()
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    let terrain_artifact =
        preprocess_artifact_with_runtime_options(source, terrain_path, &terrain_defines);
    // Curated source-only fixtures intentionally omit unrelated nested
    // libraries. They cannot be executable and later lowering remains strict,
    // but contract discovery may retain its old bounded include audit there.
    // A complete selected generation always takes the configured path below.
    let terrain = match &terrain_artifact {
        Ok(artifact) => artifact.expanded_source().to_string(),
        Err(error) if error.to_string().contains("missing shader source") => {
            expand_contract_includes(source, terrain_path)?
        }
        Err(error) => return Err(error.clone()),
    };
    require_expression(&terrain, "DoLighting(")?;
    require_expression(&terrain, "vec4 color = texture2D(tex, texCoord)")?;
    require_expression(&terrain, "if (color.a <= 0.00001) discard")?;
    require_expression(&terrain, "color.rgb *= glColor.rgb")?;
    require_expression(&terrain, "gl_FragData[0] = color")?;
    require_expression(
        &terrain,
        "gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0)",
    )?;
    let draw_buffer_slots = parse_draw_buffers_slots(&terrain)?;
    if !matches!(draw_buffer_slots.as_slice(), [0, 6] | [0, 6, 5]) {
        return Err(GalError::unsupported_feature(format!(
            "selected terrain source requires unsupported DRAWBUFFERS schema {:?}; the bounded Rust terrain contract currently admits [0, 6] or [0, 6, 5]",
            draw_buffer_slots
        )));
    }

    source.get("lib/common.glsl").ok_or_else(|| {
        GalError::invalid_argument("missing lib/common.glsl for terrain contract")
    })?;
    let properties = source.get("shaders.properties").ok_or_else(|| {
        GalError::invalid_argument("missing shaders.properties for terrain contract")
    })?;
    let property_defines =
        match preprocess_artifact_with_runtime_options(source, "lib/common.glsl", &[]) {
            Ok(artifact) => artifact
                .defines()
                .iter()
                .cloned()
                .collect::<BTreeMap<_, _>>(),
            Err(error) if error.to_string().contains("missing shader source") => BTreeMap::new(),
            Err(error) => return Err(error),
        };
    let (material_ids, runtime_block_state_material_ids) =
        terrain_material_identity_resolution(source)?;
    let mut outputs = BTreeSet::from([
        TerrainPassOutput::LitTerrainColor,
        TerrainPassOutput::MaterialAuxiliary,
    ]);
    // A source file may contain optional auxiliary writes behind a pack
    // setting. Only expose them when the selected DRAWBUFFERS declaration
    // gives that fragment output a real color slot. Source text alone is not
    // an executable output contract.
    if draw_buffer_slots.len() > 2
        && terrain.contains("gl_FragData[2] = vec4(mat3(gbufferModelViewInverse) * normalM, 1.0)")
    {
        outputs.insert(TerrainPassOutput::ViewSpaceNormal);
    }
    let mut inputs = BTreeSet::from([
        TerrainPassInput::AtlasColor,
        TerrainPassInput::AtlasUv,
        TerrainPassInput::Tint,
        TerrainPassInput::AmbientOcclusion,
        TerrainPassInput::PackedBlockLight,
        TerrainPassInput::PackedSkyLight,
        TerrainPassInput::GeometricNormal,
        TerrainPassInput::MaterialIdentity,
        TerrainPassInput::WorldPosition,
        TerrainPassInput::Camera,
        TerrainPassInput::DirectionalLight,
        TerrainPassInput::Environment,
    ]);
    if terrain.contains("shadow") || terrain.contains("Shadow") {
        inputs.insert(TerrainPassInput::ShadowMap);
    }
    let mut required_resources = BTreeSet::new();
    let voxel_light_volume_requirements =
        if let Some(colored_lighting) = colored_voxel_lighting_setting(&property_defines) {
            let shadow_compute = source.get("program/shadowcomp.glsl").ok_or_else(|| {
                GalError::invalid_argument(
                    "colored voxel-light terrain requires program/shadowcomp.glsl update semantics",
                )
            })?;
            let mut requirements = VoxelLightVolumeRequirements::complementary(colored_lighting)?;
            requirements.update_policy =
                VoxelLightVolumeUpdatePolicy::from_shadow_compute_source(shadow_compute)?;
            require_voxel_image(
                properties,
                "voxel_img",
                "voxel_sampler red_integer r8ui unsigned_int true false",
                requirements.extent,
            )?;
            require_voxel_image(
                properties,
                "floodfill_img",
                "floodfill_sampler rgba rgba16f half_float false false",
                requirements.extent,
            )?;
            require_voxel_image(
                properties,
                "floodfill_img_copy",
                "floodfill_sampler_copy rgba rgba16f half_float false false",
                requirements.extent,
            )?;
            inputs.insert(TerrainPassInput::ColoredVoxelLightVolume);
            required_resources.insert(TerrainPassRequiredResource::ColoredVoxelLightVolume);
            Some(requirements)
        } else {
            None
        };
    let mut operations = vec![
        TerrainPassOperation::AtlasSample,
        TerrainPassOperation::AlphaDiscard,
        TerrainPassOperation::TintMultiply,
        TerrainPassOperation::TerrainLighting,
        TerrainPassOperation::LitColorOutput,
        TerrainPassOperation::MaterialAuxiliaryOutput,
    ];
    if required_resources.contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume) {
        operations.insert(4, TerrainPassOperation::ColoredVoxelLighting);
    }
    if outputs.contains(&TerrainPassOutput::ViewSpaceNormal) {
        operations.push(TerrainPassOperation::ViewSpaceNormalOutput);
    }
    let output_order = [
        TerrainPassOutput::LitTerrainColor,
        TerrainPassOutput::MaterialAuxiliary,
        TerrainPassOutput::ViewSpaceNormal,
    ];
    let declared_outputs = output_order
        .into_iter()
        .filter(|output| outputs.contains(output))
        .collect::<Vec<_>>();
    if declared_outputs.len() > draw_buffer_slots.len() {
        return Err(GalError::unsupported_feature(format!(
            "selected terrain source declares {} named outputs but DRAWBUFFERS exposes only {} slots",
            declared_outputs.len(),
            draw_buffer_slots.len()
        )));
    }
    let output_color_slots = declared_outputs
        .into_iter()
        .zip(draw_buffer_slots)
        .collect::<BTreeMap<_, _>>();
    Ok(TerrainPassContract {
        pass_kind: TerrainSourcePassKind::OpaqueCutout,
        pack_name: source.name().to_string(),
        generation: source.generation(),
        program_path: terrain_path.to_string(),
        material_classes: BTreeSet::from([
            TerrainMaterialClass::Opaque,
            TerrainMaterialClass::Cutout,
        ]),
        inputs,
        outputs,
        output_color_slots,
        property_defines: property_defines.clone(),
        material_ids,
        runtime_block_state_material_ids,
        operations,
        required_resources,
        voxel_light_volume_requirements,
        translucent_raster_state: None,
        unsupported: unsupported_features(&terrain, &property_defines),
    })
}

/// Discovers the selected pack's distinct translucent terrain stage. The
/// returned contract is intentionally source provenance and semantic IO only:
/// it does not make the stage executable, borrow pack state, or merge water
/// into the ordinary opaque/cutout contract.
pub fn derive_complementary_translucent_terrain_contract_for_scope(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<TerrainPassContract> {
    let program_path = scope
        .translucent_entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing translucent terrain fragment source for {scope:?}; tried {}",
                scope.translucent_entry_candidates().join(", ")
            ))
        })?;
    let stages = terrain_source_stages(program_path)?;
    let defines = stages
        .fragment
        .defines
        .iter()
        .map(|(key, value)| (key.as_str(), value.as_str()))
        .collect::<Vec<_>>();
    let artifact = preprocess_artifact_with_runtime_options(source, program_path, &defines);
    let translucent = match &artifact {
        Ok(artifact) => artifact.expanded_source().to_string(),
        Err(error) if error.to_string().contains("missing shader source") => {
            expand_contract_includes(source, program_path)?
        }
        Err(error) => return Err(error.clone()),
    };
    require_expression(&translucent, "vec4 colorP = texture2D(tex, texCoord)")?;
    require_expression(&translucent, "vec4 color = colorP * vec4(glColor.rgb, 1.0)")?;
    require_expression(&translucent, "DoLighting(")?;
    require_expression(&translucent, "DoFog(")?;
    require_expression(&translucent, "gl_FragData[0] = color")?;
    require_expression(
        &translucent,
        "gl_FragData[1] = vec4(1.0 - translucentMult.rgb, translucentMult.a)",
    )?;
    let draw_buffer_slots = parse_draw_buffers_slots(&translucent)?;
    if !matches!(draw_buffer_slots.as_slice(), [0, 3] | [0, 3, 6]) {
        return Err(GalError::unsupported_feature(format!(
            "selected translucent terrain source requires unsupported DRAWBUFFERS schema {:?}; the bounded Rust translucent contract currently admits [0, 3] or [0, 3, 6]",
            draw_buffer_slots
        )));
    }

    source.get("lib/common.glsl").ok_or_else(|| {
        GalError::invalid_argument("missing lib/common.glsl for translucent terrain contract")
    })?;
    source.get("shaders.properties").ok_or_else(|| {
        GalError::invalid_argument("missing shaders.properties for translucent terrain contract")
    })?;
    let property_defines =
        match preprocess_artifact_with_runtime_options(source, "lib/common.glsl", &[]) {
            Ok(artifact) => artifact
                .defines()
                .iter()
                .cloned()
                .collect::<BTreeMap<_, _>>(),
            Err(error) if error.to_string().contains("missing shader source") => BTreeMap::new(),
            Err(error) => return Err(error),
        };
    let (material_ids, runtime_block_state_material_ids) =
        terrain_material_identity_resolution(source)?;
    let translucent_raster_state = derive_translucent_raster_state(source)?;
    let mut inputs = BTreeSet::from([
        TerrainPassInput::AtlasColor,
        TerrainPassInput::AtlasUv,
        TerrainPassInput::Tint,
        TerrainPassInput::PackedBlockLight,
        TerrainPassInput::PackedSkyLight,
        TerrainPassInput::GeometricNormal,
        TerrainPassInput::MaterialIdentity,
        TerrainPassInput::WorldPosition,
        TerrainPassInput::Camera,
        TerrainPassInput::DirectionalLight,
        TerrainPassInput::Environment,
        TerrainPassInput::MainDepth,
        TerrainPassInput::ViewDirection,
    ]);
    if translucent.contains("shadow") || translucent.contains("Shadow") {
        inputs.insert(TerrainPassInput::ShadowMap);
    }
    if translucent.contains("colortex") || translucent.contains("gaux") {
        inputs.insert(TerrainPassInput::SceneColor);
    }
    let mut outputs = BTreeSet::from([
        TerrainPassOutput::LitTerrainColor,
        TerrainPassOutput::TranslucencyAuxiliary,
    ]);
    if draw_buffer_slots.len() == 3 {
        outputs.insert(TerrainPassOutput::MaterialAuxiliary);
    }
    let output_order = [
        TerrainPassOutput::LitTerrainColor,
        TerrainPassOutput::TranslucencyAuxiliary,
        TerrainPassOutput::MaterialAuxiliary,
    ];
    let output_color_slots = output_order
        .into_iter()
        .filter(|output| outputs.contains(output))
        .zip(draw_buffer_slots)
        .collect::<BTreeMap<_, _>>();
    // Reflections are expressed through the same explicit source samplers as
    // every other terrain operation: named color history, depth snapshots,
    // shadow inputs, and pack assets. Their presence is not an implicit
    // backend feature. Later program preparation validates the complete
    // active semantic resource table and the executor validates generation
    // coherent availability before any source draw can run.
    let unsupported = unsupported_features(&translucent, &property_defines);
    Ok(TerrainPassContract {
        pass_kind: TerrainSourcePassKind::Translucent,
        pack_name: source.name().to_string(),
        generation: source.generation(),
        program_path: program_path.to_string(),
        material_classes: BTreeSet::from([TerrainMaterialClass::Translucent]),
        inputs,
        outputs,
        output_color_slots,
        property_defines,
        material_ids,
        runtime_block_state_material_ids,
        operations: vec![
            TerrainPassOperation::AtlasSample,
            TerrainPassOperation::TintMultiply,
            TerrainPassOperation::TerrainLighting,
            TerrainPassOperation::TranslucentFog,
            TerrainPassOperation::LitColorOutput,
            TerrainPassOperation::TranslucencyAuxiliaryOutput,
        ],
        required_resources: BTreeSet::new(),
        voxel_light_volume_requirements: None,
        translucent_raster_state,
        unsupported,
    })
}

fn derive_translucent_raster_state(
    source: &ShaderPackSource,
) -> GalResult<Option<TerrainTranslucentRasterState>> {
    let properties = preprocess_artifact_with_runtime_options(source, "shaders.properties", &[])?;
    let blend = selected_property_value(properties.expanded_source(), "blend.gbuffers_water")?;
    let alpha_test =
        selected_property_value(properties.expanded_source(), "alphaTest.gbuffers_water")?;
    match (blend, alpha_test) {
        (None, None) => Ok(None),
        // A normal terrain translucent stage inherits the semantic
        // source-alpha-over material policy when the pack only overrides its
        // alpha test. Complementary declares its blend override solely for
        // the DISTANT_HORIZONS branch, while the regular gbuffers_water pass
        // intentionally relies on this standard terrain-material default.
        // This is source-pass policy, not borrowed Iris/OpenGL state.
        (None, Some(alpha_test)) => Ok(Some(TerrainTranslucentRasterState {
            blend: TerrainTranslucentBlend::SourceAlphaOver,
            alpha_test: parse_translucent_alpha_test(alpha_test)?,
        })),
        (Some(_), None) => Err(GalError::invalid_argument(
            "selected translucent terrain source declares blend.gbuffers_water without alphaTest.gbuffers_water",
        )),
        (Some(blend), Some(alpha_test)) => {
            let blend = match blend {
                "SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA" => {
                    TerrainTranslucentBlend::SourceAlphaOver
                }
                _ => {
                    return Err(GalError::unsupported_feature(format!(
                        "selected translucent terrain blend '{blend}' is not modeled"
                    )));
                }
            };
            Ok(Some(TerrainTranslucentRasterState {
                blend,
                alpha_test: parse_translucent_alpha_test(alpha_test)?,
            }))
        }
    }
}

fn parse_translucent_alpha_test(alpha_test: &str) -> GalResult<TerrainTranslucentAlphaTest> {
    let mut alpha_parts = alpha_test.split_ascii_whitespace();
    let comparison = alpha_parts.next();
    let threshold = alpha_parts.next();
    if comparison != Some("GREATER") || alpha_parts.next().is_some() {
        return Err(GalError::unsupported_feature(format!(
            "selected translucent terrain alpha test '{alpha_test}' is not modeled"
        )));
    }
    let greater_than = threshold
        .ok_or_else(|| {
            GalError::invalid_argument(
                "selected translucent terrain alpha test is missing its threshold",
            )
        })?
        .parse::<f32>()
        .map_err(|_| {
            GalError::invalid_argument(
                "selected translucent terrain alpha-test threshold is not finite",
            )
        })?;
    if !greater_than.is_finite() || !(0.0..=1.0).contains(&greater_than) {
        return Err(GalError::invalid_argument(
            "selected translucent terrain alpha-test threshold is outside [0, 1]",
        ));
    }
    Ok(TerrainTranslucentAlphaTest {
        greater_than_bits: greater_than.to_bits(),
    })
}

/// Resolves one source-declared alpha blend property under the active
/// semantic preprocessor configuration. This is reusable source policy, not
/// a backend blend enum: callers still choose their own material/pass
/// contract and reject any unmodeled source declaration.
pub(crate) fn source_alpha_over_blend_property(
    source: &ShaderPackSource,
    property_name: &str,
) -> GalResult<Option<TerrainTranslucentBlend>> {
    let properties = preprocess_artifact_with_runtime_options(source, "shaders.properties", &[])?;
    let Some(value) = selected_property_value(properties.expanded_source(), property_name)? else {
        return Ok(None);
    };
    match value {
        "SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA" => {
            Ok(Some(TerrainTranslucentBlend::SourceAlphaOver))
        }
        _ => Err(GalError::unsupported_feature(format!(
            "selected translucent source blend '{value}' for {property_name} is not modeled"
        ))),
    }
}

fn selected_property_value<'a>(source: &'a str, key: &str) -> GalResult<Option<&'a str>> {
    let mut value = None;
    for raw_line in source.lines() {
        let line = raw_line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((candidate, candidate_value)) = line.split_once('=') else {
            continue;
        };
        if candidate.trim() != key {
            continue;
        }
        let candidate_value = candidate_value.trim();
        if candidate_value.is_empty() {
            return Err(GalError::invalid_argument(format!(
                "selected shader-pack property '{key}' is empty"
            )));
        }
        if value.replace(candidate_value).is_some() {
            return Err(GalError::invalid_argument(format!(
                "selected shader-pack property '{key}' is declared more than once"
            )));
        }
    }
    Ok(value)
}

pub fn derive_complementary_translucent_terrain_contract(
    source: &ShaderPackSource,
) -> GalResult<TerrainPassContract> {
    derive_complementary_translucent_terrain_contract_for_scope(
        source,
        TerrainProgramScope::Default,
    )
}

/// Parses the final active source `DRAWBUFFERS` declaration into semantic
/// pack-color slots. Pack source commonly declares a base target list, then
/// replaces it later in an enabled conditional with an extended list. The
/// preprocessor removes inactive branches, so the final retained declaration
/// is the schema associated with the lowered fragment outputs. The returned
/// index is the destination slot for the corresponding `gl_FragData` output
/// ordinal; it is never a GAL attachment index.
pub(crate) fn parse_draw_buffers_slots(source: &str) -> GalResult<Vec<u32>> {
    let marker = "DRAWBUFFERS:";
    let Some(start) = source.rfind(marker) else {
        return Err(GalError::invalid_argument(
            "selected terrain source is missing a DRAWBUFFERS declaration",
        ));
    };
    let slots = source[start + marker.len()..]
        .chars()
        .take_while(|character| character.is_ascii_digit())
        .map(|character| {
            character.to_digit(10).ok_or_else(|| {
                GalError::invalid_argument("DRAWBUFFERS declaration contains a non-decimal slot")
            })
        })
        .collect::<GalResult<Vec<_>>>()?;
    if slots.is_empty() {
        return Err(GalError::invalid_argument(
            "DRAWBUFFERS declaration has no color slots",
        ));
    }
    if slots.iter().any(|slot| *slot >= 8) {
        return Err(GalError::unsupported_feature(
            "DRAWBUFFERS declaration exceeds the bounded eight shader-pack color slots",
        ));
    }
    if slots.windows(2).any(|pair| pair[0] == pair[1]) {
        return Err(GalError::invalid_argument(
            "DRAWBUFFERS declaration repeats a color slot",
        ));
    }
    Ok(slots)
}

/// Resolves the paired shadow-source stages for one semantic world scope.
/// This is source provenance only: it neither compiles the source nor permits
/// a route to execute it. A future Rust-owned shadow-color pass must consume
/// these exact artifacts instead of inferring an Iris program or phase.
pub fn shadow_source_stages_for_scope(
    source: &ShaderPackSource,
    scope: TerrainProgramScope,
) -> GalResult<TerrainSourceStages> {
    let entry = scope
        .shadow_entry_candidates()
        .iter()
        .copied()
        .find(|path| source.get(path).is_some())
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "missing shadow source for {scope:?}; tried {}",
                scope.shadow_entry_candidates().join(", ")
            ))
        })?;
    terrain_source_stages(entry)
}

/// Expands only the include graph needed to inspect immutable pack source
/// semantics. This is intentionally not the executable preprocessor: it does
/// not select conditional branches, inject environment defines, or retain a
/// compiler artifact. Scoped shader-pack entries are commonly wrappers around
/// shared program sources, so reading the wrapper alone cannot establish a
/// terrain contract.
pub(crate) fn expand_contract_includes(
    source: &ShaderPackSource,
    entry: &str,
) -> GalResult<String> {
    let mut stack = BTreeSet::new();
    let mut output = String::new();
    append_contract_source(source, entry, &mut stack, &mut output)?;
    if output.len() > ShaderPackSource::MAX_TOTAL_BYTES {
        return Err(GalError::invalid_argument(
            "terrain contract source expansion exceeds shader-pack byte limit",
        ));
    }
    Ok(output)
}

fn append_contract_source(
    source: &ShaderPackSource,
    path: &str,
    stack: &mut BTreeSet<String>,
    output: &mut String,
) -> GalResult<()> {
    let canonical = canonical_contract_path(path)?;
    if !stack.insert(canonical.clone()) {
        return Err(GalError::invalid_argument(format!(
            "cyclic terrain contract include {canonical}"
        )));
    }
    let contents = source.get(&canonical).ok_or_else(|| {
        GalError::invalid_argument(format!("missing terrain contract source {canonical}"))
    })?;
    for line in contents.lines() {
        if let Some(include) = contract_include(line) {
            let include_path = resolve_contract_include(&canonical, include)?;
            if source.get(&include_path).is_some() {
                append_contract_source(source, &include_path, stack, output)?;
            } else {
                // Contract discovery is a bounded semantic audit, not source
                // compilation. Curated source fixtures may omit unrelated
                // nested libraries; the executable preprocessor remains the
                // strict gate that rejects a selected source generation with
                // any missing active dependency.
                output.push_str(line);
                output.push('\n');
            }
        } else {
            output.push_str(line);
            output.push('\n');
        }
    }
    stack.remove(&canonical);
    Ok(())
}

fn contract_include(line: &str) -> Option<&str> {
    let rest = line.trim().strip_prefix("#include")?.trim();
    rest.strip_prefix('"')
        .and_then(|include| include.strip_suffix('"'))
        .or_else(|| {
            rest.strip_prefix('<')
                .and_then(|include| include.strip_suffix('>'))
        })
}

fn resolve_contract_include(parent: &str, include: &str) -> GalResult<String> {
    if include.starts_with('/') {
        return canonical_contract_path(include);
    }
    let parent = Path::new(parent);
    canonical_contract_path(
        &parent
            .parent()
            .unwrap_or_else(|| Path::new(""))
            .join(include)
            .to_string_lossy()
            .replace('\\', "/"),
    )
}

fn canonical_contract_path(path: &str) -> GalResult<String> {
    let normalized = path.trim_start_matches('/').replace('\\', "/");
    let mut components = Vec::new();
    for component in normalized.split('/') {
        match component {
            "" | "." => {}
            ".." => {
                if components.pop().is_none() {
                    return Err(GalError::invalid_argument(
                        "terrain contract include escapes shader-pack root",
                    ));
                }
            }
            component => components.push(component),
        }
    }
    if components.is_empty() {
        return Err(GalError::invalid_argument(
            "terrain contract include path is empty",
        ));
    }
    Ok(components.join("/"))
}

pub(crate) fn terrain_source_stages(fragment_path: &str) -> GalResult<TerrainSourceStages> {
    if let Some(vertex_path) = fragment_path.strip_suffix(".fsh") {
        return Ok(TerrainSourceStages {
            vertex: TerrainSourceStage {
                path: format!("{vertex_path}.vsh"),
                defines: BTreeMap::new(),
            },
            fragment: TerrainSourceStage {
                path: fragment_path.to_string(),
                defines: BTreeMap::new(),
            },
        });
    }
    if fragment_path.ends_with(".glsl") {
        return Ok(TerrainSourceStages {
            vertex: TerrainSourceStage {
                path: fragment_path.to_string(),
                defines: BTreeMap::from([("VERTEX_SHADER".to_string(), "1".to_string())]),
            },
            fragment: TerrainSourceStage {
                path: fragment_path.to_string(),
                defines: BTreeMap::from([("FRAGMENT_SHADER".to_string(), "1".to_string())]),
            },
        });
    }
    Err(GalError::invalid_argument(format!(
        "terrain fragment source {fragment_path} has no semantic vertex-stage pairing"
    )))
}

pub fn bundled_complementary_hung_loified_source(generation: u64) -> GalResult<ShaderPackSource> {
    ShaderPackSource::new(
        "ComplementaryHungLoIfied",
        generation,
        vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/program/gbuffers_terrain.glsl")),
            ShaderSourceFile::new("program/dh_terrain.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/program/dh_terrain.glsl")),
            ShaderSourceFile::new("lib/common.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/common.glsl")),
            ShaderSourceFile::new("lib/uniforms.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/uniforms.glsl")),
            ShaderSourceFile::new("lib/util/commonFunctions.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/util/commonFunctions.glsl")),
            ShaderSourceFile::new("lib/lighting/mainLighting.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/lighting/mainLighting.glsl")),
            ShaderSourceFile::new("lib/misc/voxelization.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/misc/voxelization.glsl")),
            ShaderSourceFile::new("lib/colors/blocklightColors.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/colors/blocklightColors.glsl")),
            ShaderSourceFile::new("program/shadowcomp.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/program/shadowcomp.glsl")),
            ShaderSourceFile::new("lib/util/spaceConversion.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/util/spaceConversion.glsl")),
            ShaderSourceFile::new("lib/util/dither.glsl", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/lib/util/dither.glsl")),
            ShaderSourceFile::new("world0/dh_terrain.vsh", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/world0/dh_terrain.vsh")),
            ShaderSourceFile::new("world0/dh_terrain.fsh", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/world0/dh_terrain.fsh")),
            ShaderSourceFile::new("shaders.properties", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/shaders.properties")),
            ShaderSourceFile::new("block.properties", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/block.properties")),
			ShaderSourceFile::new("item.properties", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/item.properties")),
			ShaderSourceFile::new("entity.properties", include_str!("../../../../resources/shaders/ComplementaryHungLoIfied/shaders/entity.properties")),
            ShaderSourceFile::new(
                RUNTIME_ENVIRONMENT_PATH,
                concat!(
                    "IRIS_VERSION=12000\n",
                    "MC_VERSION=12105\n",
                    "IS_IRIS=1\n",
                    "IRIS_FEATURE_CUSTOM_IMAGES=1\n",
                    "IRIS_FEATURE_SSBO=1\n",
                    "MC_OS_LINUX=1\n",
                    "MC_MIPMAP_LEVEL=4\n",
                    "MC_RENDER_STAGE_TERRAIN_SOLID=8\n",
                    "MC_RENDER_STAGE_TERRAIN_CUTOUT_MIPPED=9\n",
                    "MC_RENDER_STAGE_TERRAIN_CUTOUT=10\n",
                    "MC_RENDER_STAGE_TERRAIN_TRANSLUCENT=15\n",
                ),
            ),
        ],
    )
}

fn require_expression(source: &str, expression: &str) -> GalResult<()> {
    if source.contains(expression) {
        Ok(())
    } else {
        Err(GalError::invalid_argument(format!(
            "terrain source is missing required normal-pass expression {expression}"
        )))
    }
}

fn require_voxel_image(
    properties: &str,
    identity: &str,
    format: &str,
    extent: super::voxel_light_volume::VoxelLightVolumeExtent,
) -> GalResult<()> {
    let expected = format!(
        "image.{identity} = {format} {} {} {}",
        extent.width, extent.height, extent.depth
    );
    require_expression(properties, &expected)
}

/// Resolves pack material IDs from copied game-semantic block-state identities.
/// Both normal and translucent contracts use this exact source-level mapping;
/// neither contract receives a renderer atlas or material handle.
fn terrain_material_identity_resolution(
    source: &ShaderPackSource,
) -> GalResult<(BTreeMap<i32, Vec<String>>, Option<BTreeMap<i32, i32>>)> {
    let raw_block_properties = source.get("block.properties").ok_or_else(|| {
        GalError::invalid_argument("missing block.properties for terrain material identity")
    })?;
    let block_properties =
        preprocess_artifact_with_runtime_options(source, "block.properties", &[])
            .map(|artifact| artifact.expanded_source().to_string())
            .unwrap_or_else(|_| raw_block_properties.to_string());
    let material_rules = parse_block_property_rules(&block_properties)?;
    let material_ids = material_ids_from_rules(&material_rules);
    let runtime_block_state_material_ids =
        resolve_runtime_block_state_material_ids(source, &material_rules)?;
    Ok((material_ids, runtime_block_state_material_ids))
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct BlockMaterialRule {
    material_id: i32,
    selectors: Vec<String>,
}

#[derive(Clone, Debug, Eq, PartialEq)]
struct RuntimeBlockStateIdentity {
    block_id: String,
    properties: BTreeMap<String, String>,
}

fn parse_block_property_rules(source: &str) -> GalResult<Vec<BlockMaterialRule>> {
    let mut rules = Vec::new();
    for line in source.lines() {
        let line = line.trim();
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let Some((key, value)) = line.split_once('=') else {
            continue;
        };
        let Some(id) = key
            .trim()
            .strip_prefix("block.")
            .and_then(|id| id.parse::<i32>().ok())
        else {
            continue;
        };
        let values = value
            .split_whitespace()
            .map(str::trim)
            .filter(|value| !value.is_empty())
            .map(ToOwned::to_owned)
            .collect::<Vec<_>>();
        if values.iter().any(|value| value.starts_with('#')) {
            return Err(GalError::unsupported_feature(format!(
                "block.properties material {id} uses an unsupported tag selector"
            )));
        }
        if !values.is_empty() {
            rules.push(BlockMaterialRule {
                material_id: id,
                selectors: values,
            });
        }
    }
    Ok(rules)
}

fn material_ids_from_rules(rules: &[BlockMaterialRule]) -> BTreeMap<i32, Vec<String>> {
    let mut ids = BTreeMap::new();
    for rule in rules {
        ids.entry(rule.material_id)
            .or_insert_with(Vec::new)
            .extend(rule.selectors.iter().cloned());
    }
    ids
}

fn resolve_runtime_block_state_material_ids(
    source: &ShaderPackSource,
    rules: &[BlockMaterialRule],
) -> GalResult<Option<BTreeMap<i32, i32>>> {
    let Some(snapshot) = source.get(RUNTIME_BLOCK_STATE_IDENTITIES_PATH) else {
        return Ok(None);
    };
    let identities = parse_runtime_block_state_identities(snapshot)?;
    if identities.is_empty() {
        return Err(GalError::invalid_argument(
            "runtime block-state identity snapshot is empty",
        ));
    }
    let mut resolved = BTreeMap::new();
    for (state_id, identity) in identities {
        let material_id = rules
            .iter()
            .find(|rule| {
                rule.selectors
                    .iter()
                    .any(|selector| selector_matches_block_state(selector, &identity))
            })
            .map_or(-1, |rule| rule.material_id);
        resolved.insert(state_id, material_id);
    }
    Ok(Some(resolved))
}

fn parse_runtime_block_state_identities(
    snapshot: &str,
) -> GalResult<BTreeMap<i32, RuntimeBlockStateIdentity>> {
    let mut identities = BTreeMap::new();
    for (line_number, raw_line) in snapshot.lines().enumerate() {
        let line = raw_line.trim();
        if line.is_empty() {
            continue;
        }
        let (key, value) = line.split_once('=').ok_or_else(|| {
            GalError::invalid_argument(format!(
                "runtime block-state identity line {} has no '='",
                line_number + 1
            ))
        })?;
        let state_id = key
            .strip_prefix("state.")
            .and_then(|value| value.parse::<i32>().ok())
            .filter(|value| *value >= 0)
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "runtime block-state identity line {} has invalid state key '{key}'",
                    line_number + 1
                ))
            })?;
        let mut components = value.split('|');
        let block_id = components
            .next()
            .filter(|value| is_resource_location(value))
            .ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "runtime block-state identity line {} has invalid block identity '{value}'",
                    line_number + 1
                ))
            })?
            .to_string();
        let mut properties = BTreeMap::new();
        for component in components {
            let (name, property_value) = component.split_once('=').ok_or_else(|| {
                GalError::invalid_argument(format!(
                    "runtime block-state identity line {} has malformed property '{component}'",
                    line_number + 1
                ))
            })?;
            if !is_property_token(name) || !is_property_token(property_value) {
                return Err(GalError::invalid_argument(format!(
                    "runtime block-state identity line {} has invalid property '{component}'",
                    line_number + 1
                )));
            }
            if properties
                .insert(
                    name.to_ascii_lowercase(),
                    property_value.to_ascii_lowercase(),
                )
                .is_some()
            {
                return Err(GalError::invalid_argument(format!(
                    "runtime block-state identity line {} repeats property '{name}'",
                    line_number + 1
                )));
            }
        }
        if identities
            .insert(
                state_id,
                RuntimeBlockStateIdentity {
                    block_id,
                    properties,
                },
            )
            .is_some()
        {
            return Err(GalError::invalid_argument(format!(
                "runtime block-state identity snapshot repeats state {state_id}"
            )));
        }
    }
    Ok(identities)
}

/// Parses one copied producer-level state identity into the same semantic
/// representation used for the runtime block-state snapshot. Keeping this at
/// the pack-contract boundary prevents each producer from reimplementing
/// block-property matching or smuggling texture policy into its transport.
fn parse_semantic_block_state_identity(value: &str) -> GalResult<RuntimeBlockStateIdentity> {
    let value = value.trim();
    if let Some((block_id, properties)) = value.split_once("_STATE_") {
        if !is_resource_location(block_id) {
            return Err(GalError::invalid_argument(format!(
                "semantic block-state identity has invalid block identity '{block_id}'"
            )));
        }
        let parsed = parse_distant_horizons_state_properties(properties)?;
        return Ok(RuntimeBlockStateIdentity {
            block_id: block_id.to_owned(),
            properties: parsed,
        });
    }

    let mut components = value.split('|');
    let block_id = components
        .next()
        .filter(|value| is_resource_location(value))
        .ok_or_else(|| {
            GalError::invalid_argument(format!(
                "semantic block-state identity has invalid block identity '{value}'"
            ))
        })?
        .to_owned();
    let mut properties = BTreeMap::new();
    for property in components {
        let (name, property_value) = property.split_once('=').ok_or_else(|| {
            GalError::invalid_argument(format!(
                "semantic block-state property '{property}' is malformed"
            ))
        })?;
        if !is_property_token(name) || !is_property_token(property_value) {
            return Err(GalError::invalid_argument(format!(
                "semantic block-state property '{property}' is invalid"
            )));
        }
        if properties
            .insert(
                name.to_ascii_lowercase(),
                property_value.to_ascii_lowercase(),
            )
            .is_some()
        {
            return Err(GalError::invalid_argument(format!(
                "semantic block-state repeats property '{name}'"
            )));
        }
    }
    Ok(RuntimeBlockStateIdentity {
        block_id,
        properties,
    })
}

/// DH serializes a propertyless state as a bare `_STATE_` suffix and a state
/// with properties as adjacent groups, for example
/// `minecraft:piston_STATE_{facing:east}{extended:true}`. This parser keeps
/// that producer encoding isolated at the semantic input boundary and rejects
/// anything that cannot be represented by the pack-rule matcher.
fn parse_distant_horizons_state_properties(value: &str) -> GalResult<BTreeMap<String, String>> {
    let mut properties = BTreeMap::new();
    let mut remaining = value;
    while !remaining.is_empty() {
        let Some(after_open) = remaining.strip_prefix('{') else {
            return Err(GalError::invalid_argument(
                "Distant Horizons semantic block-state properties must use repeated {property:value} groups",
            ));
        };
        let Some(close) = after_open.find('}') else {
            return Err(GalError::invalid_argument(
                "Distant Horizons semantic block-state property group is unterminated",
            ));
        };
        let property = &after_open[..close];
        if property.is_empty() && close + 1 == after_open.len() && properties.is_empty() {
            return Ok(properties);
        }
        let (name, property_value) = property.split_once(':').ok_or_else(|| {
            GalError::invalid_argument(format!(
                "Distant Horizons semantic block-state property '{property}' is malformed"
            ))
        })?;
        if !is_property_token(name) || !is_property_token(property_value) {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons semantic block-state property '{property}' is invalid"
            )));
        }
        if properties
            .insert(
                name.to_ascii_lowercase(),
                property_value.to_ascii_lowercase(),
            )
            .is_some()
        {
            return Err(GalError::invalid_argument(format!(
                "Distant Horizons semantic block-state repeats property '{name}'"
            )));
        }
        remaining = &after_open[close + 1..];
    }
    Ok(properties)
}

fn selector_matches_block_state(selector: &str, state: &RuntimeBlockStateIdentity) -> bool {
    let selector = selector.strip_prefix("minecraft:").unwrap_or(selector);
    let (block, constraints) = selector.split_once(':').unwrap_or((selector, ""));
    if block
        != state
            .block_id
            .strip_prefix("minecraft:")
            .unwrap_or(&state.block_id)
    {
        return false;
    }
    constraints
        .split(':')
        .filter(|constraint| !constraint.is_empty())
        .all(|constraint| {
            constraint.split_once('=').is_some_and(|(name, value)| {
                state
                    .properties
                    .get(name)
                    .is_some_and(|actual| actual == value)
            })
        })
}

fn is_resource_location(value: &str) -> bool {
    value.split_once(':').is_some_and(|(namespace, path)| {
        !namespace.is_empty()
            && !path.is_empty()
            && value.bytes().all(|byte| {
                byte.is_ascii_lowercase()
                    || byte.is_ascii_digit()
                    || matches!(byte, b'_' | b'-' | b'.' | b'/' | b':')
            })
    })
}

fn is_property_token(value: &str) -> bool {
    !value.is_empty()
        && value.bytes().all(|byte| {
            byte.is_ascii_alphanumeric() || matches!(byte, b'_' | b'-' | b'.')
        })
}

fn unsupported_features(
    source: &str,
    defines: &BTreeMap<String, String>,
) -> BTreeSet<UnsupportedTerrainFeature> {
    let mut unsupported = BTreeSet::new();
    let enabled = |key: &str| defines.get(key).map(String::as_str).unwrap_or("0") != "0";
    if source.contains("#ifdef POM") && enabled("POM") {
        unsupported.insert(UnsupportedTerrainFeature::ParallaxOcclusionMapping);
    }
    if source.contains("GENERATED_NORMALS") && enabled("GENERATED_NORMALS") {
        unsupported.insert(UnsupportedTerrainFeature::GeneratedNormals);
    }
    // Custom PBR is not an implicit backend feature. Its source-visible
    // normal/specular samplers are already represented by the ordinary
    // semantic material-resource contract and are admitted only when that
    // contract resolves every active resource. Treating the preprocessor
    // define itself as unsupported would reject a fully owned source path
    // before resource validation has a chance to make that decision.
    if enabled("ANISOTROPIC_FILTER") {
        unsupported.insert(UnsupportedTerrainFeature::AnisotropicFiltering);
    }
    // Rain-puddle occupancy is no longer a blanket source-admission blocker.
    // The active source path declares `PuddleOccupancy` as a semantic sampled
    // and storage resource, and the Rust runtime owns its generation-bound
    // upload, shadow-scene update, and rollback. Resource completeness still
    // rejects a source generation that does not provide that exact role.
    unsupported
}

fn colored_voxel_lighting_setting(defines: &BTreeMap<String, String>) -> Option<u32> {
    // `common.glsl` defines both conditional branches textually. Source
    // discovery must therefore use the selected user/profile value rather
    // than the last textual `COLORED_LIGHTING_INTERNAL` branch.
    let value = defines
        .get("COLORED_LIGHTING")
        .or_else(|| defines.get("COLORED_LIGHTING_INTERNAL"))
        .map(String::as_str)
        .unwrap_or("0");
    value.parse::<u32>().ok().filter(|value| *value != 0)
}

#[cfg(test)]
mod tests {
    use super::super::source::{RUNTIME_BLOCK_STATE_IDENTITIES_PATH, RUNTIME_OPTIONS_PATH};
    use super::*;

    fn source() -> ShaderPackSource {
        ShaderPackSource::new("test", 1, vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", "#include \"/lib/common.glsl\"\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"),
            ShaderSourceFile::new("lib/common.glsl", "#define SHADOW_QUALITY 2\n"),
            ShaderSourceFile::new("shaders.properties", "profile.MATTMC=SHADOW_QUALITY=2\n"),
            ShaderSourceFile::new("block.properties", "block.10009=minecraft:oak_leaves\n"),
        ]).unwrap()
    }

    fn terrain_fragment_source() -> &'static str {
        "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"
    }

    fn translucent_fragment_source() -> &'static str {
        "void DoLighting() {}\nvoid DoFog(inout vec3 color, inout float sky, float distance, vec3 player, float up, float sun, float dither) {}\n/* DRAWBUFFERS:03 */\nvoid main() { vec4 colorP = texture2D(tex, texCoord); vec4 color = colorP * vec4(glColor.rgb, 1.0); vec3 viewPos = vec3(0.0); vec3 playerPos = vec3(0.0); float lViewPos = 0.0; float VdotU = 0.0; float VdotS = 0.0; float dither = 0.0; vec4 translucentMult = vec4(1.0); DoLighting(); float sky = 0.0; DoFog(color.rgb, sky, lViewPos, playerPos, VdotU, VdotS, dither); gl_FragData[0] = color; gl_FragData[1] = vec4(1.0 - translucentMult.rgb, translucentMult.a); }"
    }

    #[test]
    fn runtime_block_state_snapshot_resolves_pack_materials_in_rule_order() {
        let mut files = source().files();
        files.retain(|file| file.path != "block.properties");
        files.push(ShaderSourceFile::new(
            "block.properties",
            "block.7=sand\nblock.9=sand:color=red\nblock.11=oak_leaves:persistent=true\n",
        ));
        files.push(ShaderSourceFile::new(
            RUNTIME_BLOCK_STATE_IDENTITIES_PATH,
            concat!(
                "state.41=minecraft:sand\n",
                "state.42=minecraft:sand|color=red\n",
                "state.43=minecraft:oak_leaves|persistent=true\n",
                "state.44=minecraft:stone\n",
            ),
        ));
        let source = ShaderPackSource::new("state-map", 1, files).unwrap();

        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let resolved = contract.runtime_block_state_material_ids.unwrap();

        // Pack order is authoritative, matching Iris's first successful rule.
        assert_eq!(Some(&7), resolved.get(&41));
        assert_eq!(Some(&7), resolved.get(&42));
        assert_eq!(Some(&11), resolved.get(&43));
        assert_eq!(Some(&-1), resolved.get(&44));
    }

    #[test]
    fn runtime_block_state_snapshot_rejects_duplicate_or_malformed_semantics() {
        let error = parse_runtime_block_state_identities(
            "state.4=minecraft:sand\nstate.4=minecraft:stone\n",
        )
        .unwrap_err();
        assert!(error.to_string().contains("repeats state 4"));

        let error = parse_runtime_block_state_identities("state.x=minecraft:sand\n").unwrap_err();
        assert!(error.to_string().contains("invalid state key"));
    }

    #[test]
    fn semantic_block_state_identity_reuses_pack_material_rules_for_distant_horizons() {
        let mut files = source().files();
        files.retain(|file| file.path != "block.properties");
        files.push(ShaderSourceFile::new(
            "block.properties",
            "block.7=grass_block:snowy=false\nblock.9=redstone_ore\n",
        ));
        let contract = derive_complementary_terrain_contract(
            &ShaderPackSource::new("dh-state-map", 1, files).unwrap(),
        )
        .unwrap();

        assert_eq!(
            contract
                .material_id_for_block_state_identity("minecraft:grass_block_STATE_{snowy:false}")
                .unwrap(),
            7
        );
        assert_eq!(
            contract
                .material_id_for_block_state_identity("minecraft:redstone_ore_STATE_")
                .unwrap(),
            9
        );
        assert_eq!(
            contract
                .material_id_for_block_state_identity("minecraft:stone_STATE_{}")
                .unwrap(),
            -1
        );

        let piston = parse_semantic_block_state_identity(
            "minecraft:piston_STATE_{facing:east}{extended:true}",
        )
        .unwrap();
        assert_eq!(piston.properties["facing"], "east");
        assert_eq!(piston.properties["extended"], "true");

        let trial_spawner = parse_semantic_block_state_identity(
            "minecraft:trial_spawner_STATE_{trial_spawner_state:WAITING_FOR_PLAYERS}",
        )
        .unwrap();
        assert_eq!(
            trial_spawner.properties["trial_spawner_state"],
            "waiting_for_players"
        );
    }

    #[test]
    fn semantic_block_state_identity_rejects_malformed_distant_horizons_properties() {
        let error =
            parse_semantic_block_state_identity("minecraft:grass_block_STATE_{snowy}").unwrap_err();
        assert!(error.to_string().contains("malformed"));

        let error = parse_semantic_block_state_identity(
            "minecraft:grass_block_STATE_{snowy:false}{snowy:true}",
        )
        .unwrap_err();
        assert!(error.to_string().contains("repeats property"));
    }

    #[test]
    fn source_derived_contract_names_complementary_outputs_not_draw_buffers() {
        let contract = derive_complementary_terrain_contract(&source()).unwrap();
        assert_eq!(TerrainSourcePassKind::OpaqueCutout, contract.pass_kind);
        assert!(contract
            .outputs
            .contains(&TerrainPassOutput::LitTerrainColor));
        assert!(contract
            .outputs
            .contains(&TerrainPassOutput::MaterialAuxiliary));
        assert_eq!(
            Some(0),
            contract.output_color_slot(TerrainPassOutput::LitTerrainColor)
        );
        assert_eq!(
            Some(6),
            contract.output_color_slot(TerrainPassOutput::MaterialAuxiliary)
        );
        assert_eq!(
            None,
            contract.output_color_slot(TerrainPassOutput::ViewSpaceNormal)
        );
        assert_eq!(
            vec![
                TerrainPassOperation::AtlasSample,
                TerrainPassOperation::AlphaDiscard,
                TerrainPassOperation::TintMultiply,
                TerrainPassOperation::TerrainLighting,
                TerrainPassOperation::LitColorOutput,
                TerrainPassOperation::MaterialAuxiliaryOutput,
            ],
            contract.operations
        );
        assert_eq!(
            Some(&vec!["minecraft:oak_leaves".to_string()]),
            contract.material_ids.get(&10009)
        );
        assert!(!contract.inputs.contains(&TerrainPassInput::ShadowMap));
    }

    #[test]
    fn translucent_contract_is_a_distinct_source_stage_with_named_outputs() {
        let source = ShaderPackSource::new(
            "translucent",
            4,
            vec![
                ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", "block.32000=minecraft:water\n"),
            ],
        )
        .unwrap();
        let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
        assert_eq!(TerrainSourcePassKind::Translucent, contract.pass_kind);
        assert_eq!("gbuffers_water.fsh", contract.program_path);
        assert_eq!(
            BTreeSet::from([TerrainMaterialClass::Translucent]),
            contract.material_classes
        );
        assert_eq!(
            Some(0),
            contract.output_color_slot(TerrainPassOutput::LitTerrainColor)
        );
        assert_eq!(
            Some(3),
            contract.output_color_slot(TerrainPassOutput::TranslucencyAuxiliary)
        );
        assert!(contract.inputs.contains(&TerrainPassInput::MainDepth));
        assert!(contract.inputs.contains(&TerrainPassInput::ViewDirection));
        assert!(!contract.inputs.contains(&TerrainPassInput::SceneColor));
    }

    #[test]
    fn translucent_contract_derives_only_explicit_source_alpha_raster_state() {
        let source = ShaderPackSource::new(
            "translucent-raster",
            5,
            vec![
                ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
                ShaderSourceFile::new(
                    "shaders.properties",
                    "alphaTest.gbuffers_water=GREATER 0.0001\nblend.gbuffers_water=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA\n",
                ),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();

        let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
        let raster = contract
            .translucent_raster_state
            .expect("explicit source raster directives must survive contract derivation");
        assert_eq!(TerrainTranslucentBlend::SourceAlphaOver, raster.blend);
        assert!((raster.alpha_test.greater_than() - 0.0001).abs() < f32::EPSILON);
    }

    #[test]
    fn translucent_contract_defers_custom_pbr_admission_to_semantic_resources() {
        let source = ShaderPackSource::new(
            "translucent-custom-pbr",
            8,
            vec![
                ShaderSourceFile::new(
                    "gbuffers_water.fsh",
                    concat!(
                        "#ifdef CUSTOM_PBR\n",
                        "uniform sampler2D normals;\n",
                        "uniform sampler2D specular;\n",
                        "#endif\n",
                        "void DoLighting() {}\n",
                        "void DoFog(inout vec3 color, inout float sky, float distance, vec3 player, float up, float sun, float dither) {}\n",
                        "/* DRAWBUFFERS:03 */\n",
                        "void main() { vec4 colorP = texture2D(tex, texCoord); vec4 color = colorP * vec4(glColor.rgb, 1.0);\n",
                        "#ifdef CUSTOM_PBR\ncolor.rgb += texture2D(normals, texCoord).rgb * 0.0 + texture2D(specular, texCoord).rgb * 0.0;\n#endif\n",
                        "vec3 playerPos = vec3(0.0); vec3 viewPos = vec3(0.0); float lViewPos = 0.0; float VdotU = 0.0; float VdotS = 0.0; float dither = 0.0; vec4 translucentMult = vec4(1.0); DoLighting(); float sky = 0.0; DoFog(color.rgb, sky, lViewPos, playerPos, VdotU, VdotS, dither); gl_FragData[0] = color; gl_FragData[1] = vec4(1.0 - translucentMult.rgb, translucentMult.a); }"
                    ),
                ),
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
                ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "CUSTOM_PBR=1\n"),
            ],
        )
        .unwrap();

        let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
        assert!(contract.supports_selected_subset());
    }

    #[test]
    fn translucent_contract_defers_reflections_to_semantic_history_resources() {
        let source = ShaderPackSource::new(
            "translucent-reflections",
            9,
            vec![
                ShaderSourceFile::new(
                    "gbuffers_water.fsh",
                    concat!(
                        "void DoLighting() {}\n",
                        "void DoFog(inout vec3 color, inout float sky, float distance, vec3 player, float up, float sun, float dither) {}\n",
                        "vec4 GetReflection() { return vec4(0.0); }\n",
                        "/* DRAWBUFFERS:03 */\n",
                        "void main() { vec4 colorP = texture2D(tex, texCoord); vec4 color = colorP * vec4(glColor.rgb, 1.0);\n",
                        "vec3 playerPos = vec3(0.0); vec3 viewPos = vec3(0.0); float lViewPos = 0.0; float VdotU = 0.0; float VdotS = 0.0; float dither = 0.0; vec4 translucentMult = vec4(1.0);\n",
                        "color.rgb += GetReflection().rgb * 0.0; DoLighting(); float sky = 0.0; DoFog(color.rgb, sky, lViewPos, playerPos, VdotU, VdotS, dither); gl_FragData[0] = color; gl_FragData[1] = vec4(1.0 - translucentMult.rgb, translucentMult.a); }"
                    ),
                ),
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();

        let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
        assert!(contract.supports_selected_subset());
    }

    #[test]
    fn translucent_raster_directives_follow_runtime_environment_conditionals() {
        let source = ShaderPackSource::new(
            "translucent-raster-environment",
            7,
            vec![
                ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
                ShaderSourceFile::new(
                    "shaders.properties",
                    "#ifdef DISTANT_HORIZONS\nalphaTest.gbuffers_water=GREATER 0.0001\nblend.gbuffers_water=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA\n#endif\n",
                ),
                ShaderSourceFile::new("block.properties", ""),
                ShaderSourceFile::new(RUNTIME_ENVIRONMENT_PATH, "DISTANT_HORIZONS=1\n"),
            ],
        )
        .unwrap();

        let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
        assert!(contract.translucent_raster_state.is_some());
    }

    #[test]
    fn translucent_contract_uses_standard_blend_when_source_declares_only_alpha_test() {
        let source = ShaderPackSource::new(
            "translucent-raster-invalid",
            6,
            vec![
                ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
                ShaderSourceFile::new(
                    "shaders.properties",
                    "alphaTest.gbuffers_water=GREATER 0.0001\n",
                ),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();

        let contract = derive_complementary_translucent_terrain_contract(&source).unwrap();
        let raster = contract
            .translucent_raster_state
            .expect("an explicit alpha test admits the standard terrain translucent blend");
        assert_eq!(TerrainTranslucentBlend::SourceAlphaOver, raster.blend);
        assert!((raster.alpha_test.greater_than() - 0.0001).abs() < f32::EPSILON);

        let blend_only = ShaderPackSource::new(
            "translucent-raster-blend-only",
            7,
            vec![
                ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", "#define WATER_REFLECT_QUALITY -1\n"),
                ShaderSourceFile::new(
                    "shaders.properties",
                    "blend.gbuffers_water=SRC_ALPHA ONE_MINUS_SRC_ALPHA ONE ONE_MINUS_SRC_ALPHA\n",
                ),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let error = derive_complementary_translucent_terrain_contract(&blend_only).unwrap_err();
        assert!(error
            .to_string()
            .contains("without alphaTest.gbuffers_water"));
    }

    #[test]
    fn translucent_stage_uses_the_explicit_world_scope_without_borrowing_terrain() {
        let source = ShaderPackSource::new(
            "scoped-translucent",
            1,
            vec![
                ShaderSourceFile::new("gbuffers_water.fsh", translucent_fragment_source()),
                ShaderSourceFile::new("world0/gbuffers_water.fsh", translucent_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let contract = derive_complementary_translucent_terrain_contract_for_scope(
            &source,
            TerrainProgramScope::Overworld,
        )
        .unwrap();
        assert_eq!("world0/gbuffers_water.fsh", contract.program_path);
        let stages = contract.source_stages().unwrap();
        assert_eq!("world0/gbuffers_water.vsh", stages.vertex.path);
        assert_eq!("world0/gbuffers_water.fsh", stages.fragment.path);
    }

    #[test]
    fn terrain_contract_rejects_unsupported_or_malformed_draw_buffer_schema() {
        let unsupported = ShaderPackSource::new(
            "unsupported-draw-buffers",
            1,
            vec![
                ShaderSourceFile::new(
                    "program/gbuffers_terrain.glsl",
                    terrain_fragment_source().replace("DRAWBUFFERS:06", "DRAWBUFFERS:01"),
                ),
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        assert!(derive_complementary_terrain_contract(&unsupported)
            .unwrap_err()
            .to_string()
            .contains("unsupported DRAWBUFFERS schema"));

        let malformed = ShaderPackSource::new(
            "malformed-draw-buffers",
            1,
            vec![
                ShaderSourceFile::new(
                    "program/gbuffers_terrain.glsl",
                    terrain_fragment_source().replace("DRAWBUFFERS:06", "DRAWBUFFERS:"),
                ),
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        assert!(derive_complementary_terrain_contract(&malformed)
            .unwrap_err()
            .to_string()
            .contains("has no color slots"));
    }

    #[test]
    fn drawbuffers_uses_the_final_active_schema_after_an_enabled_extension() {
        let slots = parse_draw_buffers_slots("/* DRAWBUFFERS:054 */\n").unwrap();
        assert_eq!(vec![0, 5, 4], slots);

        let slots =
            parse_draw_buffers_slots("/* DRAWBUFFERS:054 */\n/* DRAWBUFFERS:0547 */\n").unwrap();
        assert_eq!(vec![0, 5, 4, 7], slots);
    }

    #[test]
    fn terrain_contract_admits_the_optional_named_view_space_normal_target() {
        let mut files = source().files();
        let terrain = files
            .iter_mut()
            .find(|file| file.path == "program/gbuffers_terrain.glsl")
            .expect("terrain fixture must include the selected source");
        terrain.contents = concat!(
            "void DoLighting() {}\n",
            "/* DRAWBUFFERS:065 */\n",
            "void main() {\n",
            "  vec4 color = texture2D(tex, texCoord);\n",
            "  if (color.a <= 0.00001) discard;\n",
            "  color.rgb *= glColor.rgb;\n",
            "  DoLighting();\n",
            "  gl_FragData[0] = color;\n",
            "  gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0);\n",
            "  gl_FragData[2] = vec4(mat3(gbufferModelViewInverse) * normalM, 1.0);\n",
            "}\n"
        )
        .to_string();
        let source = ShaderPackSource::new("terrain-normal", 2, files).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        assert!(contract
            .outputs
            .contains(&TerrainPassOutput::ViewSpaceNormal));
        assert_eq!(
            Some(5),
            contract.output_color_slot(TerrainPassOutput::ViewSpaceNormal)
        );
    }

    #[test]
    fn terrain_entry_discovery_uses_explicit_world_scope_not_an_active_renderer_pass() {
        let source = ShaderPackSource::new(
            "standard-layout",
            1,
            vec![
                ShaderSourceFile::new("gbuffers_terrain.fsh", terrain_fragment_source()),
                ShaderSourceFile::new("world0/gbuffers_terrain.fsh", terrain_fragment_source()),
                ShaderSourceFile::new("world-1/gbuffers_terrain.fsh", terrain_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();

        assert_eq!(
            "gbuffers_terrain.fsh",
            derive_complementary_terrain_contract(&source)
                .unwrap()
                .program_path
        );
        assert_eq!(
            "world0/gbuffers_terrain.fsh",
            derive_complementary_terrain_contract_for_scope(
                &source,
                TerrainProgramScope::Overworld
            )
            .unwrap()
            .program_path
        );
        assert_eq!(
            "world-1/gbuffers_terrain.fsh",
            derive_complementary_terrain_contract_for_scope(&source, TerrainProgramScope::Nether)
                .unwrap()
                .program_path
        );
        assert_eq!(
            "gbuffers_terrain.fsh",
            derive_complementary_terrain_contract_for_scope(&source, TerrainProgramScope::End)
                .unwrap()
                .program_path
        );
        let missing = ShaderPackSource::new(
            "missing-terrain",
            1,
            vec![
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let error =
            derive_complementary_terrain_contract_for_scope(&missing, TerrainProgramScope::End)
                .unwrap_err();
        assert!(format!("{error}").contains("missing terrain fragment source for End"));
    }

    #[test]
    fn terrain_contract_pairs_scoped_stage_files_and_shared_stage_defines() {
        let scoped = terrain_source_stages("world0/gbuffers_terrain.fsh").unwrap();
        assert_eq!("world0/gbuffers_terrain.vsh", scoped.vertex.path);
        assert!(scoped.vertex.defines.is_empty());
        assert_eq!("world0/gbuffers_terrain.fsh", scoped.fragment.path);

        let shared = terrain_source_stages("program/gbuffers_terrain.glsl").unwrap();
        assert_eq!("1", shared.vertex.defines["VERTEX_SHADER"]);
        assert_eq!("1", shared.fragment.defines["FRAGMENT_SHADER"]);
        assert!(terrain_source_stages("program/gbuffers_terrain.vert").is_err());
    }

    #[test]
    fn shadow_source_stages_are_scoped_and_never_infer_another_dimension() {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![
                ShaderSourceFile::new("world0/shadow.vsh", "#version 130\nvoid main() {}"),
                ShaderSourceFile::new("world0/shadow.fsh", "#version 130\nvoid main() {}"),
                ShaderSourceFile::new("world-1/shadow.vsh", "#version 130\nvoid main() {}"),
                ShaderSourceFile::new("world-1/shadow.fsh", "#version 130\nvoid main() {}"),
            ],
        )
        .unwrap();

        let overworld =
            shadow_source_stages_for_scope(&source, TerrainProgramScope::Overworld).unwrap();
        assert_eq!("world0/shadow.vsh", overworld.vertex.path);
        assert_eq!("world0/shadow.fsh", overworld.fragment.path);

        let nether = shadow_source_stages_for_scope(&source, TerrainProgramScope::Nether).unwrap();
        assert_eq!("world-1/shadow.vsh", nether.vertex.path);
        assert_eq!("world-1/shadow.fsh", nether.fragment.path);

        let error = shadow_source_stages_for_scope(&source, TerrainProgramScope::End).unwrap_err();
        assert!(error.to_string().contains("missing shadow source for End"));
        assert!(error.to_string().contains("world1/shadow.fsh"));
    }

    #[test]
    fn missing_normal_terrain_output_is_rejected() {
        let source = ShaderPackSource::new(
            "bad",
            1,
            vec![
                ShaderSourceFile::new("program/gbuffers_terrain.glsl", "void main() {}"),
                ShaderSourceFile::new("lib/common.glsl", ""),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        assert!(derive_complementary_terrain_contract(&source).is_err());
    }

    #[test]
    fn selected_profile_requires_a_complete_semantic_voxel_light_volume() {
        let source = ShaderPackSource::new("unsupported", 1, vec![
            ShaderSourceFile::new("program/gbuffers_terrain.glsl", "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }"),
            ShaderSourceFile::new("lib/common.glsl", "#define COLORED_LIGHTING 128\n"),
            ShaderSourceFile::new(
                "program/shadowcomp.glsl",
                "void main() { vec3 posOffset = floor(previousCameraPosition) - floor(cameraPosition); }",
            ),
            ShaderSourceFile::new("shaders.properties", "profile.MATTMC=COLORED_LIGHTING=128\nimage.voxel_img = voxel_sampler red_integer r8ui unsigned_int true false 128 64 128\nimage.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 128 64 128\nimage.floodfill_img_copy = floodfill_sampler_copy rgba rgba16f half_float false false 128 64 128\n"),
            ShaderSourceFile::new("block.properties", ""),
        ]).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        assert!(contract.require_selected_subset().is_ok());
        assert!(contract
            .require_selected_subset_with_resources(None, 0)
            .is_err());
    }

    #[test]
    fn rain_puddles_are_admitted_by_the_semantic_resource_contract_not_a_define_gate() {
        let mut files = source().files();
        files.push(ShaderSourceFile::new(
            RUNTIME_OPTIONS_PATH,
            "RAIN_PUDDLES=1\nDETAIL_QUALITY=3\n",
        ));
        let source = ShaderPackSource::new("rain-puddles", 2, files).unwrap();

        let contract = derive_complementary_terrain_contract(&source).unwrap();
        assert!(contract.supports_selected_subset());
        assert!(contract.require_selected_subset().is_ok());
    }

    #[test]
    fn runtime_option_snapshot_overrides_pack_profile_defines() {
        let source = ShaderPackSource::new(
            "configured",
            1,
            vec![
                ShaderSourceFile::new("program/gbuffers_terrain.glsl", terrain_fragment_source()),
                ShaderSourceFile::new("lib/common.glsl", "#define COLORED_LIGHTING 0\n"),
                ShaderSourceFile::new(
                    "program/shadowcomp.glsl",
                    "void main() { vec3 posOffset = floor(previousCameraPosition) - floor(cameraPosition); }",
                ),
                ShaderSourceFile::new(
                    "shaders.properties",
                    "profile.MATTMC=COLORED_LIGHTING=0\nimage.voxel_img = voxel_sampler red_integer r8ui unsigned_int true false 128 64 128\nimage.floodfill_img = floodfill_sampler rgba rgba16f half_float false false 128 64 128\nimage.floodfill_img_copy = floodfill_sampler_copy rgba rgba16f half_float false false 128 64 128\n",
                ),
                ShaderSourceFile::new("block.properties", ""),
                ShaderSourceFile::new(RUNTIME_OPTIONS_PATH, "COLORED_LIGHTING=128\n"),
            ],
        )
        .unwrap();

        let contract = derive_complementary_terrain_contract(&source).unwrap();
        assert_eq!(
            Some(&"128".to_string()),
            contract.property_defines.get("COLORED_LIGHTING")
        );
        assert!(contract
            .required_resources
            .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume));
    }

    #[test]
    fn runtime_option_snapshot_rejects_non_identifier_keys() {
        let mut files = source().files();
        files.push(ShaderSourceFile::new(
            RUNTIME_OPTIONS_PATH,
            "BAD-OPTION=1\n",
        ));
        let source = ShaderPackSource::new("bad-runtime-option", 2, files).unwrap();
        let error = derive_complementary_terrain_contract(&source).unwrap_err();
        assert!(format!("{error}").contains("not a preprocessor identifier"));
    }

    #[test]
    fn runtime_option_snapshot_rejects_non_scalar_values() {
        let mut files = source().files();
        files.push(ShaderSourceFile::new(
            RUNTIME_OPTIONS_PATH,
            "VALID_OPTION=not a token\n",
        ));
        let source = ShaderPackSource::new("bad-runtime-option", 2, files).unwrap();
        let error = derive_complementary_terrain_contract(&source).unwrap_err();
        assert!(format!("{error}").contains("not one preprocessor token"));
    }

    #[test]
    fn runtime_option_snapshot_rejects_empty_value() {
        let mut files = source().files();
        files.push(ShaderSourceFile::new(
            RUNTIME_OPTIONS_PATH,
            "VALID_OPTION=\n",
        ));
        let source = ShaderPackSource::new("empty-runtime-option", 2, files).unwrap();
        let error = derive_complementary_terrain_contract(&source).unwrap_err();
        assert!(format!("{error}").contains("not one preprocessor token"));
    }

    #[test]
    fn bundled_terrain_source_contract_does_not_reject_expanded_includes() {
        let contract = derive_complementary_terrain_contract(
            &bundled_complementary_hung_loified_source(9).unwrap(),
        )
        .unwrap();
        assert!(contract.require_selected_subset().is_ok());
    }

    #[test]
    fn bundled_complementary_volume_requirement_is_source_derived() {
        let contract = derive_complementary_terrain_contract(
            &bundled_complementary_hung_loified_source(3).unwrap(),
        )
        .unwrap();
        let requirements = contract.voxel_light_volume_requirements.unwrap();
        assert_eq!(256, requirements.extent.width);
        assert_eq!(128, requirements.extent.height);
        assert_eq!(256, requirements.extent.depth);
        assert_eq!(
            super::super::voxel_light_volume::VoxelLightVolumeFormat::OccupancyR8Uint,
            requirements.occupancy_format
        );
        assert_eq!(
            super::super::voxel_light_volume::VoxelLightVolumeFormat::LightingRgba16Float,
            requirements.lighting_format
        );
        assert!(requirements.update_policy.temporal_reprojection);
        assert!(requirements.update_policy.alternate_x_half_rate);
        assert!(requirements.update_policy.preserve_behind_view);
    }
}

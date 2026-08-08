use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, ClearColor, CommandOp, PassAttachment, ResourceBarrier,
    TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    CombinedTextureSamplerDesc, CompareOp, IndexType, QueueClass, SamplerAddressMode, SamplerDesc,
    SamplerFilter,
};
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::Path;

use super::assets::{ShaderPackAssets, TerrainShaderPackAssetBindings};
use super::interface::{analyze_terrain_vertex_interface, TerrainVertexInterface};
use super::lowering::{
    lower_shadow_source_pair, lower_terrain_source_pair, LoweredTerrainSourcePair,
    TerrainSourceLoweringSummary, TerrainSourceOpaqueResourceBindingPlan,
};
use super::pass_graph::{AttachmentRole, PassIdentity};
use super::preprocess::{preprocess_terrain_sources, PreprocessedTerrainSourceSummary};
use super::programs::{
    complementary_terrain_subset_program_with_resources, prepare_lowered_terrain_source_program,
    LoweredTerrainSourceProgram, TerrainMaterialProgram, TerrainMaterialProgramKind,
    TerrainProgramResource,
};
use super::resources::ShaderPackRuntimePlan;
use super::source::ShaderPackSource;
use super::source_assets::TerrainSourceAssetResources;
use super::source_uniforms::{
    TerrainSourceUniformRequirementSummary, TerrainSourceUniformRequirements,
};
use super::terrain_contract::bundled_complementary_hung_loified_source;
use super::terrain_contract::{
    shadow_source_stages_for_scope, TerrainPassContract, TerrainPassRequiredResource,
    TerrainProgramScope,
};
use super::terrain_source_resources::{
    TerrainSourceOwnedResourceSet, TerrainSourceResourceBindings, TerrainSourceResourceRole,
};
use super::terrain_voxelization::{
    TerrainColoredLightRuntime, TerrainOccupancyRuntime, TerrainVoxelLightSamplingBinding,
};
use super::voxel_emission_table::VoxelEmissionTable;
use super::voxel_light_volume::{
    VoxelLightVolumeDescriptor, VoxelLightVolumeIdentity, VoxelLightVolumeMapping,
    VoxelLightVolumeViewDirection,
};
use super::voxel_material_map::VoxelMaterialMap;
use crate::render::vulkanic::world_primitive_frontend::TerrainVoxelSourceMesh;

pub(crate) const TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES: u64 = 16 * 4 + 4 * 4 + 4 * 4 + 4 * 4;

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) enum TerrainMaterialPassMode {
    Opaque,
    Cutout,
    Translucent,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
enum TerrainGraphIsolation {
    Full,
    TerrainOnly,
    GBufferNoShadow,
    TerrainPlusShadow,
    FullDrawsSkipped,
}

impl TerrainGraphIsolation {
    fn from_env() -> Self {
        match std::env::var("MATTMC_RUST_SHADER_GRAPH_ISOLATION")
            .unwrap_or_default()
            .trim()
        {
            "terrain-only" => Self::TerrainOnly,
            "terrain-plus-gbuffer-no-shadow" | "gbuffer-no-shadow" => Self::GBufferNoShadow,
            "terrain-plus-shadow" => Self::TerrainPlusShadow,
            "full-draws-skipped" => Self::FullDrawsSkipped,
            _ => Self::Full,
        }
    }
}

#[derive(Clone, Debug)]
pub(crate) struct TerrainMeshDraw {
    pub shadow_pipeline: Option<Handle>,
    pub pipeline: Handle,
    pub pipeline_layout: Handle,
    pub resource_set: Handle,
    /// Dynamic offsets required by the semantic mesh resource set. Fixture
    /// meshes use one streamed-instance offset; source-derived terrain sets
    /// may own static bindings and therefore require none.
    pub resource_set_dynamic_offsets: Vec<u64>,
    /// Optional shader-pack-owned semantic resources. This is distinct from
    /// the mesh/material set and intentionally carries only GAL handles; the
    /// frontend never sees backend state or shader-pack internals.
    pub shader_resource_set: Option<TerrainShaderResourceSet>,
    pub index_buffer: Handle,
    pub index_offset: u64,
    pub index_type: IndexType,
    pub index_count: u32,
    pub instance_count: u32,
    pub material_mode: TerrainMaterialPassMode,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainShaderResourceSet {
    pub set_index: u32,
    pub set: Handle,
}

/// A complete Rust-owned semantic binding for a terrain program requirement.
/// The contained handles are GAL resources visible only inside Rust frontend
/// and runtime code; Java and the shader-pack contract never see them.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainShaderProgramBinding {
    pub resource: TerrainProgramResource,
    pub resource_layout: Handle,
    pub resource_set: TerrainShaderResourceSet,
    pub resource_generation: u64,
}

/// An indivisible built-in candidate-subset terrain program and its matching
/// semantic shader resource set. It exercises resource-generation coherence
/// in focused tests, but is not lowered shader-pack source execution. The
/// latter remains unavailable until its source vertex and semantic resource
/// interfaces are assembled into explicit GAL layouts.
#[derive(Clone, Debug)]
pub(crate) struct TerrainSourceProgramCandidate {
    pub program: TerrainMaterialProgram,
    pub binding: Option<TerrainShaderProgramBinding>,
}

/// Fully source-derived, backend-neutral inputs needed to create an owned
/// colored-light runtime for one world/resource generation. It is deliberately
/// preparation data only: constructing it cannot allocate a native resource,
/// select a shader program, or alter route ownership.
#[derive(Clone, Debug)]
pub(crate) struct TerrainColoredLightPreparation {
    pub descriptor: VoxelLightVolumeDescriptor,
    pub materials: VoxelMaterialMap,
    pub emission: VoxelEmissionTable,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainRuntimeTargets {
    pub shadow_depth_texture: Handle,
    pub shadow_depth_view: Handle,
    pub shadow_target: Handle,
    pub shadow_pass: Handle,
    pub albedo_texture: Handle,
    pub albedo_view: Handle,
    pub normal_texture: Handle,
    pub normal_view: Handle,
    pub material_light_texture: Handle,
    pub material_light_view: Handle,
    pub world_position_texture: Handle,
    pub world_position_view: Handle,
    pub depth_texture: Handle,
    pub depth_view: Handle,
    pub target: Handle,
    pub g_buffer_pass: Handle,
    pub deferred_lit_texture: Handle,
    pub deferred_lit_view: Handle,
    pub deferred_lit_target: Handle,
    pub deferred_lighting_pass: Handle,
    pub deferred_lighting_pipeline: Handle,
    pub deferred_lighting_resource_set: Handle,
    pub translucent_target: Handle,
    pub translucent_pass: Handle,
    pub composite0_texture: Handle,
    pub composite0_view: Handle,
    pub composite0_target: Handle,
    pub composite0_pass: Handle,
    pub composite0_pipeline: Handle,
    pub composite0_resource_set: Handle,
    pub composite1_texture: Handle,
    pub composite1_view: Handle,
    pub composite1_target: Handle,
    pub composite1_pass: Handle,
    pub composite1_pipeline: Handle,
    pub composite1_resource_set: Handle,
    pub final_pass: Handle,
    pub final_pipeline: Handle,
    pub final_resource_set: Handle,
    pub screen_pipeline_layout: Handle,
    pub composite_uniform_buffer: Handle,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainRuntimeFrame {
    pub frame_target: Handle,
    pub color_attachment: Handle,
    pub background_color: ClearColor,
    pub final_depth_view: Option<Handle>,
    pub uniforms: TerrainCompositeUniforms,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainCompositeUniforms {
    pub light_view_projection: [f32; 16],
    pub shadow_params: [f32; 4],
    pub color_grade_params: [f32; 4],
    pub fog_params: [f32; 4],
}

/// Owned-source discovery is deliberately separate from the executable plan.
/// A discovered contract proves only that Rust has parsed semantic pack input;
/// it cannot route any draw through an incomplete selected-source pipeline.
#[derive(Clone, Debug, PartialEq)]
pub(crate) enum TerrainSourceCandidateState {
    Unavailable,
    Disabled {
        generation: u64,
        pack_name: String,
    },
    Discovered {
        generation: u64,
        pack_name: String,
        requires_colored_voxel_light: bool,
        contract: TerrainPassContract,
        /// Both terrain stages are source-expanded and fingerprinted before a
        /// source candidate can progress beyond discovery. This is provenance
        /// only; it is not a compiled program or render-route decision.
        source_summary: Option<PreprocessedTerrainSourceSummary>,
        source_preprocess_error: Option<String>,
        /// The semantically scoped shadow-stage pair is expanded separately
        /// from normal terrain. It is future Rust-owned shadow-color pass
        /// provenance only and cannot make the terrain source executable.
        source_shadow_summary: Option<PreprocessedTerrainSourceSummary>,
        source_shadow_preprocess_error: Option<String>,
        /// Bounded diagnostic from lowering the scoped shadow fragment's
        /// named outputs. It cannot create a program or a shadow attachment.
                source_shadow_output_count: Option<u32>,
                source_shadow_lowering_error: Option<String>,
                /// Active resource roles for the separately lowered shadow
                /// source pair. These remain distinct from the normal terrain
                /// plan because binding numbers are pass-local, while shared
                /// pack assets may be prepared from their semantic union.
                source_shadow_resource_binding_count: Option<u32>,
                source_shadow_resource_binding_error: Option<String>,
                source_shadow_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// Source-derived, backend-neutral vertex requirements observed after
        /// preprocessing the paired vertex stage. This is a strict future
        /// lowering prerequisite, never a Java/Iris vertex layout.
        source_vertex_interface: Option<TerrainVertexInterface>,
        /// Compact proof that both source stages reached the private lowering
        /// contract. It is diagnostics only and never admits execution.
        source_lowering_summary: Option<TerrainSourceLoweringSummary>,
        source_lowering_error: Option<String>,
        /// Bounded source-derived summary of scalar inputs which are (or are
        /// not) backed by named Rust gameplay semantics. It is diagnostic
        /// provenance only and cannot admit a source execution route.
        source_uniform_requirement_summary: Option<TerrainSourceUniformRequirementSummary>,
        source_uniform_requirement_error: Option<String>,
        /// The validated lowered source pair is retained privately so later
        /// preparation uses exactly the source that discovery inspected.
        /// Retaining it does not compile, bind, or execute a program.
        source_lowered_pair: Option<LoweredTerrainSourcePair>,
        /// Every lowered opaque resource needs a pack-declared semantic role
        /// before it can be assembled into a Rust-owned resource layout.
        /// This is still discovery provenance, never a GAL resource set.
        source_resource_binding_count: Option<u32>,
        source_resource_binding_error: Option<String>,
        /// Stable source-name to semantic-role mapping retained for a future
        /// Rust-owned resource-set builder. It carries no native handles,
        /// texture units, or backend descriptors.
        source_resource_bindings: Option<TerrainSourceOpaqueResourceBindingPlan>,
        /// Source-derived terrain PNG declarations. They are retained only as
        /// semantic pack paths until the runtime validates matching Rust-owned
        /// binary assets and creates explicit GAL resources.
        source_asset_binding_count: Option<u32>,
        source_asset_binding_error: Option<String>,
        source_asset_bindings: Option<TerrainShaderPackAssetBindings>,
        voxel_materials: Option<VoxelMaterialMap>,
        voxel_emission: Option<VoxelEmissionTable>,
    },
    Rejected {
        generation: u64,
        pack_name: String,
        reason: String,
    },
}

#[derive(Debug)]
pub(crate) struct ShaderPackRuntimeExecutor {
    plan: ShaderPackRuntimePlan,
    source_candidate: TerrainSourceCandidateState,
    /// Private preparation state for a future selected-source terrain pass.
    /// It owns Rust D3 resources and copied mesh semantics only; it cannot
    /// select source programs or bind terrain material resources.
    terrain_occupancy: Option<TerrainOccupancyRuntime>,
    /// Full private colored-light preparation. It stays unavailable outside
    /// explicit test installation and cannot select a source terrain program.
    terrain_colored_light: Option<TerrainColoredLightRuntime>,
    /// Decoded pack PNGs referenced by the lowered source binding plan.
    /// These are private Rust-owned GAL resources only. Creating them cannot
    /// select the source program or alter the internal fixture execution.
    source_asset_resources: Option<TerrainSourceAssetResources>,
    /// Rust-owned semantic wrappers around copied material atlases. The world
    /// frontend supplies only private GAL view/sampler pairs; source-plan
    /// lifetime and combined-sampler retirement stay here. Keeping roles
    /// distinct prevents albedo, specular, and future normal atlases from
    /// aliasing one another merely because their extents happen to match.
    source_material_texture_resources: BTreeMap<
        TerrainSourceResourceRole,
        TerrainSourceMaterialTextureResources,
    >,
    /// Private semantic wrappers around the Rust-owned shadow-depth target.
    /// They are generation-bound compare samplers, not a source program
    /// binding and not evidence that selected-source execution is admitted.
    source_shadow_depth_resources: Option<TerrainSourceShadowDepthResources>,
    /// Private semantic wrapper for a future Rust-owned shadow-color target.
    /// The current fixture allocates the distinct backing attachment but does
    /// not write it; that absence of a source shadow pass keeps selected-source
    /// execution unavailable rather than aliasing depth as color.
    source_shadow_color_resources: Option<TerrainSourceShadowColorResources>,
}

/// Rust-internal handoff from the world texture cache to the shader runtime.
/// It has no Java, OpenGL, Vulkan, or native-handle representation.
#[derive(Clone, Debug)]
pub(crate) struct TerrainSourceMaterialTextureInput {
    pub role: TerrainSourceResourceRole,
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub mesh_asset_generation: u64,
    pub texture_view: Handle,
    pub sampler: Handle,
}

/// Rust-internal handoff from the owned terrain runtime targets to source
/// resource preparation. This is intentionally a GAL view identity only:
/// neither backend objects nor shader-pack-specific binding slots escape.
#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainSourceShadowDepthInput {
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub shader_graph_generation: u64,
    pub shadow_depth_view: Handle,
}

/// Rust-internal handoff from a future owned shadow-color target. The input
/// carries only GAL resource identities and generation semantics; it has no
/// Java, Iris, OpenGL, Vulkan, or native-handle representation.
#[derive(Clone, Copy, Debug)]
pub(crate) struct TerrainSourceShadowColorInput {
    pub shader_pack_generation: u64,
    pub world_generation: u64,
    pub shader_graph_generation: u64,
    pub shadow_color_view: Handle,
    pub sampler: Handle,
}

#[derive(Debug)]
struct TerrainSourceMaterialTextureResources {
    role: TerrainSourceResourceRole,
    shader_pack_generation: u64,
    world_generation: u64,
    mesh_asset_generation: u64,
    combined_sampler: Handle,
}

impl TerrainSourceMaterialTextureResources {
    fn compatible_with(&self, input: &TerrainSourceMaterialTextureInput) -> bool {
        self.role == input.role
            && self.shader_pack_generation == input.shader_pack_generation
            && self.world_generation == input.world_generation
            && self.mesh_asset_generation == input.mesh_asset_generation
    }

    fn semantic_resource_set(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        let availability = super::terrain_source_resources::TerrainSourceResourceAvailabilitySet::new(
            self.shader_pack_generation,
            self.world_generation,
            [super::terrain_source_resources::TerrainSourceResourceAvailability {
                role: self.role.clone(),
                shape: super::terrain_source_resources::TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: self.shader_pack_generation,
            }],
        )?;
        TerrainSourceOwnedResourceSet::new(
            availability,
            [
                super::terrain_source_resources::TerrainSourceOwnedResource {
                    role: self.role.clone(),
                    combined_sampler: self.combined_sampler,
                },
            ],
        )
    }
}

#[derive(Debug)]
struct TerrainSourceShadowDepthResources {
    shader_pack_generation: u64,
    world_generation: u64,
    shader_graph_generation: u64,
    primary_sampler: Handle,
    secondary_sampler: Handle,
    primary_combined_sampler: Handle,
    secondary_combined_sampler: Handle,
}

#[derive(Debug)]
struct TerrainSourceShadowColorResources {
    shader_pack_generation: u64,
    world_generation: u64,
    shader_graph_generation: u64,
    shadow_color_view: Handle,
    sampler: Handle,
    combined_sampler: Handle,
}

impl TerrainSourceShadowDepthResources {
    fn compatible_with(&self, input: TerrainSourceShadowDepthInput) -> bool {
        self.shader_pack_generation == input.shader_pack_generation
            && self.world_generation == input.world_generation
            && self.shader_graph_generation == input.shader_graph_generation
    }

    fn semantic_resource_set(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        use super::terrain_source_resources::{
            TerrainSourceResourceAvailability, TerrainSourceResourceAvailabilitySet,
            TerrainSourceSampledResourceShape,
        };

        let availability = TerrainSourceResourceAvailabilitySet::new(
            self.shader_pack_generation,
            self.world_generation,
            [
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::ShadowDepthPrimary,
                    shape: TerrainSourceSampledResourceShape::DepthCompareTexture2d,
                    resource_generation: self.shader_graph_generation,
                },
                TerrainSourceResourceAvailability {
                    role: TerrainSourceResourceRole::ShadowDepthSecondary,
                    shape: TerrainSourceSampledResourceShape::DepthCompareTexture2d,
                    resource_generation: self.shader_graph_generation,
                },
            ],
        )?;
        TerrainSourceOwnedResourceSet::new(
            availability,
            [
                super::terrain_source_resources::TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::ShadowDepthPrimary,
                    combined_sampler: self.primary_combined_sampler,
                },
                super::terrain_source_resources::TerrainSourceOwnedResource {
                    role: TerrainSourceResourceRole::ShadowDepthSecondary,
                    combined_sampler: self.secondary_combined_sampler,
                },
            ],
        )
    }

    fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        for handle in [
            self.secondary_combined_sampler,
            self.primary_combined_sampler,
            self.secondary_sampler,
            self.primary_sampler,
        ] {
            gal.destroy(handle)?;
        }
        Ok(())
    }
}

impl TerrainSourceShadowColorResources {
    fn compatible_with(&self, input: TerrainSourceShadowColorInput) -> bool {
        self.shader_pack_generation == input.shader_pack_generation
            && self.world_generation == input.world_generation
            && self.shader_graph_generation == input.shader_graph_generation
            && self.shadow_color_view == input.shadow_color_view
            && self.sampler == input.sampler
    }

    fn semantic_resource_set(&self) -> GalResult<TerrainSourceOwnedResourceSet> {
        use super::terrain_source_resources::{
            TerrainSourceResourceAvailability, TerrainSourceResourceAvailabilitySet,
            TerrainSourceSampledResourceShape,
        };

        let availability = TerrainSourceResourceAvailabilitySet::new(
            self.shader_pack_generation,
            self.world_generation,
            [TerrainSourceResourceAvailability {
                role: TerrainSourceResourceRole::ShadowColor,
                shape: TerrainSourceSampledResourceShape::Texture2d,
                resource_generation: self.shader_graph_generation,
            }],
        )?;
        TerrainSourceOwnedResourceSet::new(
            availability,
            [super::terrain_source_resources::TerrainSourceOwnedResource {
                role: TerrainSourceResourceRole::ShadowColor,
                combined_sampler: self.combined_sampler,
            }],
        )
    }

    fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        gal.destroy(self.combined_sampler)
    }
}

impl ShaderPackRuntimeExecutor {
    pub(crate) fn terrain_material_multipass_v1(generation: u64) -> GalResult<Self> {
        let source = bundled_complementary_hung_loified_source(generation)?;
        Self::terrain_material_fixture_from_source(&source)
    }

    /// Installs an owned source generation only for contract discovery and
    /// diagnostics. The executable plan remains the internal Rust fixture;
    /// callers cannot accidentally claim selected-source execution merely by
    /// loading pack text.
    pub(crate) fn terrain_material_fixture_from_source(
        source: &ShaderPackSource,
    ) -> GalResult<Self> {
        let generation = source.generation();
        let mut plan = ShaderPackRuntimePlan::terrain_material_multipass_v1(generation)?;
        plan.terrain_contract =
            Some(ShaderPackRuntimePlan::discover_terrain_contract_from_source(generation, source)?);
        write_contract_diagnostic(&plan);
        Ok(Self {
            plan,
            source_candidate: TerrainSourceCandidateState::Unavailable,
            terrain_occupancy: None,
            terrain_colored_light: None,
            source_asset_resources: None,
            source_material_texture_resources: BTreeMap::new(),
            source_shadow_depth_resources: None,
            source_shadow_color_resources: None,
        })
    }

    pub(crate) fn plan(&self) -> &ShaderPackRuntimePlan {
        &self.plan
    }

    pub(crate) fn generation(&self) -> u64 {
        self.plan.generation
    }

    pub(crate) fn observe_source_candidate(&mut self, source: &ShaderPackSource) {
        self.observe_source_candidate_for_scope(source, TerrainProgramScope::Default);
    }

    /// Re-discovers only semantic source metadata for the supplied world
    /// scope. It is not a route-selection method and cannot bind or execute
    /// an Iris/OpenGL program.
    pub(crate) fn observe_source_candidate_for_scope(
        &mut self,
        source: &ShaderPackSource,
        scope: TerrainProgramScope,
    ) {
        if source.is_empty() {
            self.source_candidate = TerrainSourceCandidateState::Disabled {
                generation: source.generation(),
                pack_name: source.name().to_string(),
            };
            return;
        }
        let candidate = match ShaderPackRuntimePlan::discover_terrain_contract_from_source_for_scope(
            source.generation(),
            source,
            scope,
        ) {
            Ok(contract) => {
                let (
                    source_summary,
                    source_preprocess_error,
                    source_vertex_interface,
                    source_lowering_summary,
                    source_lowering_error,
                    source_lowered_pair,
                    source_uniform_requirement_summary,
                    source_uniform_requirement_error,
                    source_resource_binding_count,
                    source_resource_binding_error,
                    source_resource_bindings,
                ) = match contract
                    .source_stages()
                    .and_then(|stages| preprocess_terrain_sources(source, &stages))
                {
                    Ok(artifacts) => {
                        match lower_terrain_source_pair(&artifacts.vertex, &artifacts.fragment) {
                            Ok(lowered) => {
                                let uniform_requirements =
                                    TerrainSourceUniformRequirements::from_contract(
                                        lowered.uniform_contract(),
                                    );
                                let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                    let bindings =
                                        TerrainSourceResourceBindings::from_source(source)?;
                                    let plan = lowered
                                        .opaque_resource_contract()
                                        .bind_semantic_roles(&bindings)?;
                                    bindings.require_contract_roles(&contract)?;
                                    Ok(plan)
                                })(
                                );
                                (
                                    Some(artifacts.summary()),
                                    None,
                                    Some(analyze_terrain_vertex_interface(&artifacts.vertex)),
                                    Some(lowered.summary()),
                                    None,
                                    Some(lowered),
                                    uniform_requirements
                                        .as_ref()
                                        .ok()
                                        .map(TerrainSourceUniformRequirements::summary),
                                    uniform_requirements
                                        .as_ref()
                                        .err()
                                        .map(|error| error.to_string()),
                                    resource_bindings
                                        .as_ref()
                                        .ok()
                                        .map(|plan| plan.bindings().len() as u32),
                                    resource_bindings
                                        .as_ref()
                                        .err()
                                        .map(|error| error.to_string()),
                                    resource_bindings.ok(),
                                )
                            }
                            Err(error) => (
                                Some(artifacts.summary()),
                                None,
                                Some(analyze_terrain_vertex_interface(&artifacts.vertex)),
                                None,
                                Some(error.to_string()),
                                None,
                                None,
                                None,
                                None,
                                None,
                                None,
                            ),
                        }
                    }
                    Err(error) => (
                        None,
                        Some(error.to_string()),
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                        None,
                    ),
                };
                let requires_colored_voxel_light = contract
                    .required_resources
                    .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume);
                let (
                    source_shadow_summary,
                    source_shadow_preprocess_error,
                    source_shadow_output_count,
                    source_shadow_lowering_error,
                    source_shadow_resource_binding_count,
                    source_shadow_resource_binding_error,
                    source_shadow_resource_bindings,
                ) = match shadow_source_stages_for_scope(source, scope)
                    .and_then(|stages| preprocess_terrain_sources(source, &stages))
                {
                    Ok(artifacts) => match lower_shadow_source_pair(
                        &artifacts.vertex,
                        &artifacts.fragment,
                    ) {
                        Ok(lowered) => {
                            let resource_bindings: GalResult<_> = (|| -> GalResult<_> {
                                let declarations = TerrainSourceResourceBindings::from_source(source)?;
                                lowered
                                    .opaque_resource_contract()
                                    .bind_semantic_roles(&declarations)
                            })();
                            (
                                Some(artifacts.summary()),
                                None,
                                Some(lowered.fragment().outputs().len() as u32),
                                None,
                                resource_bindings
                                    .as_ref()
                                    .ok()
                                    .map(|plan| plan.bindings().len() as u32),
                                resource_bindings
                                    .as_ref()
                                    .err()
                                    .map(|error| error.to_string()),
                                resource_bindings.ok(),
                            )
                        }
                        Err(error) => (
                            Some(artifacts.summary()),
                            None,
                            None,
                            Some(error.to_string()),
                            None,
                            None,
                            None,
                        ),
                    },
                    Err(error) => (
                        None,
                        Some(error.to_string()),
                        None,
                        None,
                        None,
                        None,
                        None,
                    ),
                };
                let source_asset_bindings = TerrainShaderPackAssetBindings::from_source(source);
                let source_asset_binding_count = source_asset_bindings
                    .as_ref()
                    .ok()
                    .map(|bindings| bindings.samplers().count() as u32);
                let source_asset_binding_error = source_asset_bindings
                    .as_ref()
                    .err()
                    .map(ToString::to_string);
                let (voxel_materials, voxel_emission) = if requires_colored_voxel_light {
                    match (
                        VoxelMaterialMap::derive(source, &contract),
                        VoxelEmissionTable::derive(source, &contract),
                    ) {
                        (Ok(materials), Ok(emission)) => (Some(materials), Some(emission)),
                        (Err(error), _) | (_, Err(error)) => {
                            self.source_candidate = TerrainSourceCandidateState::Rejected {
                                generation: source.generation(),
                                pack_name: source.name().to_string(),
                                reason: format!(
                                    "selected source colored voxel-light semantics are unsupported: {error}"
                                ),
                            };
                            return;
                        }
                    }
                } else {
                    (None, None)
                };
                TerrainSourceCandidateState::Discovered {
                    generation: source.generation(),
                    pack_name: source.name().to_string(),
                    requires_colored_voxel_light,
                    contract,
                    source_summary,
                    source_preprocess_error,
                    source_shadow_summary,
                    source_shadow_preprocess_error,
                    source_shadow_output_count,
                    source_shadow_lowering_error,
                    source_shadow_resource_binding_count,
                    source_shadow_resource_binding_error,
                    source_shadow_resource_bindings,
                    source_vertex_interface,
                    source_lowering_summary,
                    source_lowering_error,
                    source_lowered_pair,
                    source_uniform_requirement_summary,
                    source_uniform_requirement_error,
                    source_resource_binding_count,
                    source_resource_binding_error,
                    source_resource_bindings,
                    source_asset_binding_count,
                    source_asset_binding_error,
                    source_asset_bindings: source_asset_bindings.ok(),
                    voxel_materials,
                    voxel_emission,
                }
            }
            Err(error) => TerrainSourceCandidateState::Rejected {
                generation: source.generation(),
                pack_name: source.name().to_string(),
                reason: error.to_string(),
            },
        };
        self.source_candidate = candidate;
    }

    pub(crate) fn source_candidate(&self) -> &TerrainSourceCandidateState {
        &self.source_candidate
    }

    /// Returns the validated, source-derived semantic resource plan for the
    /// current candidate. This is preparation metadata only: it contains no
    /// resource handles and cannot select a route or issue a draw.
    pub(crate) fn source_resource_binding_plan(
        &self,
    ) -> Option<&TerrainSourceOpaqueResourceBindingPlan> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                source_resource_bindings,
                ..
            } => source_resource_bindings.as_ref(),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => None,
        }
    }

    /// Returns the pass-local plans that can share copied pack assets. They
    /// are intentionally not merged into one executable layout: terrain and
    /// shadow bindings remain local to their respective source programs.
    fn source_resource_binding_plans(&self) -> Vec<&TerrainSourceOpaqueResourceBindingPlan> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                source_resource_bindings,
                source_shadow_resource_bindings,
                ..
            } => source_resource_bindings
                .iter()
                .chain(source_shadow_resource_bindings.iter())
                .collect(),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Vec::new(),
        }
    }

    /// Returns source-derived PNG sampler paths for diagnostics and a future
    /// Rust-owned resource preparer. This has no native resource identity and
    /// cannot select a shader or rendering route.
    pub(crate) fn source_asset_binding_plan(&self) -> Option<&TerrainShaderPackAssetBindings> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                source_asset_bindings,
                ..
            } => source_asset_bindings.as_ref(),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => None,
        }
    }

    /// Creates generation-coherent copied PNG resources for the active,
    /// lowered source terrain plan. This is a private preparation operation:
    /// it deliberately does not construct a program resource set, bind a
    /// pipeline, or make source-selected terrain executable.
    pub(crate) fn ensure_candidate_source_asset_resources(
        &mut self,
        gal: &mut VulkanicGal,
        assets: &ShaderPackAssets,
    ) -> GalResult<bool> {
        let (generation, pack_name, asset_bindings) =
            match &self.source_candidate {
                TerrainSourceCandidateState::Discovered {
                    generation,
                    pack_name,
                    source_asset_bindings: Some(asset_bindings),
                    ..
                } => (*generation, pack_name.as_str(), asset_bindings),
                TerrainSourceCandidateState::Unavailable
                | TerrainSourceCandidateState::Disabled { .. }
                | TerrainSourceCandidateState::Rejected { .. }
                | TerrainSourceCandidateState::Discovered { .. } => return Ok(false),
            };
        if assets.generation() != generation || assets.pack_name() != pack_name {
            return Err(GalError::invalid_argument(format!(
                "shader-pack asset resources '{}'/{} do not match source candidate '{}'/{}",
                assets.pack_name(),
                assets.generation(),
                pack_name,
                generation
            )));
        }
        if self
            .source_asset_resources
            .as_ref()
            .is_some_and(|resources| {
                resources.generation() == generation && resources.pack_name() == pack_name
            })
        {
            return Ok(false);
        }
        let resource_binding_plans = self.source_resource_binding_plans();
        if resource_binding_plans.is_empty() {
            return Ok(false);
        }
        let replacement = TerrainSourceAssetResources::create(
            gal,
            assets,
            asset_bindings,
            &resource_binding_plans,
        )?;
        if let Some(previous) = self.source_asset_resources.replace(replacement) {
            previous.destroy(gal)?;
        }
        Ok(true)
    }

    /// Discards copied PNG preparation when its matching source/assets no
    /// longer form a complete generation. This does not affect the fixture
    /// plan or source-candidate discovery state.
    pub(crate) fn clear_candidate_source_asset_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        self.clear_candidate_source_shadow_depth_resources(gal)?;
        self.clear_candidate_source_shadow_color_resources(gal)?;
        self.clear_candidate_source_material_texture_resources(gal)?;
        if let Some(previous) = self.source_asset_resources.take() {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    pub(crate) fn has_candidate_source_asset_resources(&self) -> bool {
        self.source_asset_resources.is_some()
    }

    /// Builds the active copied-PNG semantic subset for a concrete world
    /// generation. It is intentionally incomplete when atlas, lightmap,
    /// shadow, or voxel roles have not yet been supplied by other Rust-owned
    /// runtime components.
    pub(crate) fn candidate_source_asset_resource_set(
        &self,
        world_generation: u64,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let Some(resources) = self.source_asset_resources.as_ref() else {
            return Ok(None);
        };
        let binding_plans = self.source_resource_binding_plans();
        if binding_plans.is_empty() {
            return Ok(None);
        }
        Ok(Some(
            resources.declared_semantic_resources(&binding_plans, world_generation)?,
        ))
    }

    /// Reports whether the lowered candidate references a semantic resource
    /// role. This keeps world-resource preparation driven by source lowering,
    /// not by pack filenames or backend-specific binding slots.
    pub(crate) fn candidate_source_requires_resource(
        &self,
        role: TerrainSourceResourceRole,
    ) -> bool {
        self.source_resource_binding_plan().is_some_and(|bindings| {
            bindings
                .bindings()
                .iter()
                .any(|binding| binding.role() == role)
        })
    }

    /// Returns active lowered semantic roles that are absent from the current
    /// Rust-owned resource subsets. This is diagnostic-only; callers must not
    /// treat partial residency as source-program admission.
    pub(crate) fn candidate_source_missing_resource_roles(
        &self,
        prepared: Option<&TerrainSourceOwnedResourceSet>,
    ) -> Vec<TerrainSourceResourceRole> {
        let Some(bindings) = self.source_resource_binding_plan() else {
            return Vec::new();
        };
        bindings
            .bindings()
            .iter()
            .map(|binding| binding.role())
            .collect::<BTreeSet<_>>()
            .into_iter()
            .filter(|role| {
                prepared
                    .and_then(|set| set.availability().resource_for(role.clone()))
                    .is_none()
            })
            .collect()
    }

    /// Returns the parity-correct owned voxel subset only after the matching
    /// source candidate and D3 generation are fully confirmed. This is not a
    /// source-program binding and cannot alter route selection.
    pub(crate) fn candidate_colored_light_resource_set(
        &self,
        frame_counter: u64,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let source_generation = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered { generation, .. } => *generation,
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
        };
        let Some(runtime) = self.terrain_colored_light.as_ref() else {
            return Ok(None);
        };
        if runtime.descriptor().shader_pack_generation != source_generation {
            return Err(GalError::invalid_argument(
                "colored voxel-light resources do not match the discovered shader-pack generation",
            ));
        }
        if !runtime.is_ready_for_frame(frame_counter) {
            return Ok(None);
        }
        runtime
            .semantic_resource_set_for_frame(frame_counter)
            .map(Some)
    }

    /// Creates a source-runtime-owned semantic material texture wrapper. The
    /// supplied view and sampler already belong to Rust's world texture cache;
    /// this method owns only the role-specific combined source-resource object.
    pub(crate) fn ensure_candidate_source_material_texture_resources(
        &mut self,
        gal: &mut VulkanicGal,
        input: TerrainSourceMaterialTextureInput,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        if !matches!(
            input.role,
            TerrainSourceResourceRole::MaterialAtlas
                | TerrainSourceResourceRole::MaterialNormalMap
                | TerrainSourceResourceRole::MaterialSpecularMap
        ) {
            return Err(GalError::invalid_argument(
                "source material texture wrapper received an unsupported semantic role",
            ));
        }
        if !self.candidate_source_requires_resource(input.role.clone()) {
            self.clear_candidate_source_material_texture_role(gal, &input.role)?;
            return Ok(None);
        }
        if input.shader_pack_generation == 0
            || input.world_generation == 0
            || input.mesh_asset_generation == 0
        {
            return Err(GalError::invalid_argument(
                "source material texture requires non-zero shader-pack, world, and mesh generations",
            ));
        }
        if input.shader_pack_generation != self.expected_shader_pack_generation_for_resources() {
            return Err(GalError::invalid_argument(
                "source material texture shader-pack generation does not match the discovered candidate",
            ));
        }
        if let Some(resources) = self.source_material_texture_resources.get(&input.role) {
            if resources.compatible_with(&input) {
                return resources.semantic_resource_set().map(Some);
            }
        }
        let combined_sampler = gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
            label: format!(
                "shader-pack.source-material-{}.pack{}.world{}.mesh{}",
                input.role.semantic_name(),
                input.shader_pack_generation,
                input.world_generation,
                input.mesh_asset_generation
            ),
            texture_view: input.texture_view,
            sampler: input.sampler,
        })?;
        let replacement = TerrainSourceMaterialTextureResources {
            role: input.role.clone(),
            shader_pack_generation: input.shader_pack_generation,
            world_generation: input.world_generation,
            mesh_asset_generation: input.mesh_asset_generation,
            combined_sampler,
        };
        if let Some(previous) = self
            .source_material_texture_resources
            .insert(input.role.clone(), replacement)
        {
            gal.destroy(previous.combined_sampler)?;
        }
        self.source_material_texture_resources
            .get(&input.role)
            .expect("material texture wrapper was inserted")
            .semantic_resource_set()
            .map(Some)
    }

    pub(crate) fn clear_candidate_source_material_texture_role(
        &mut self,
        gal: &mut VulkanicGal,
        role: &TerrainSourceResourceRole,
    ) -> GalResult<()> {
        if let Some(previous) = self.source_material_texture_resources.remove(role) {
            gal.destroy(previous.combined_sampler)?;
        }
        Ok(())
    }

    pub(crate) fn clear_candidate_source_material_texture_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        let resources = std::mem::take(&mut self.source_material_texture_resources);
        for (_, resource) in resources {
            gal.destroy(resource.combined_sampler)?;
        }
        Ok(())
    }

    /// Creates private comparison samplers for the two source semantic shadow
    /// roles. The source route remains unavailable: this owns only a
    /// generation-coherent GAL resource subset for diagnostics and later
    /// explicit source-program assembly.
    pub(crate) fn ensure_candidate_source_shadow_depth_resources(
        &mut self,
        gal: &mut VulkanicGal,
        input: TerrainSourceShadowDepthInput,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        let requires_primary =
            self.candidate_source_requires_resource(TerrainSourceResourceRole::ShadowDepthPrimary);
        let requires_secondary = self
            .candidate_source_requires_resource(TerrainSourceResourceRole::ShadowDepthSecondary);
        if !requires_primary && !requires_secondary {
            self.clear_candidate_source_shadow_depth_resources(gal)?;
            return Ok(None);
        }
        if input.shader_pack_generation == 0
            || input.world_generation == 0
            || input.shader_graph_generation == 0
        {
            return Err(GalError::invalid_argument(
                "source shadow depth requires non-zero shader-pack, world, and shader-graph generations",
            ));
        }
        if input.shader_pack_generation != self.expected_shader_pack_generation_for_resources() {
            return Err(GalError::invalid_argument(
                "source shadow depth shader-pack generation does not match the discovered candidate",
            ));
        }
        if self
            .source_shadow_depth_resources
            .as_ref()
            .is_some_and(|resources| resources.compatible_with(input))
        {
            return self
                .source_shadow_depth_resources
                .as_ref()
                .map(TerrainSourceShadowDepthResources::semantic_resource_set)
                .transpose();
        }

        let label_prefix = format!(
            "shader-pack.source-shadow-depth.pack{}.world{}.graph{}",
            input.shader_pack_generation, input.world_generation, input.shader_graph_generation
        );
        let compare_desc = |label: String| SamplerDesc {
            label,
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
            comparison: Some(CompareOp::LessOrEqual),
        };
        let mut created = Vec::new();
        let result = (|| -> GalResult<TerrainSourceShadowDepthResources> {
            let primary_sampler =
                gal.create_sampler(compare_desc(format!("{label_prefix}.primary")))?;
            created.push(primary_sampler);
            let secondary_sampler =
                gal.create_sampler(compare_desc(format!("{label_prefix}.secondary")))?;
            created.push(secondary_sampler);
            let primary_combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("{label_prefix}.primary.combined"),
                    texture_view: input.shadow_depth_view,
                    sampler: primary_sampler,
                })?;
            created.push(primary_combined_sampler);
            let secondary_combined_sampler =
                gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                    label: format!("{label_prefix}.secondary.combined"),
                    texture_view: input.shadow_depth_view,
                    sampler: secondary_sampler,
                })?;
            created.push(secondary_combined_sampler);
            Ok(TerrainSourceShadowDepthResources {
                shader_pack_generation: input.shader_pack_generation,
                world_generation: input.world_generation,
                shader_graph_generation: input.shader_graph_generation,
                primary_sampler,
                secondary_sampler,
                primary_combined_sampler,
                secondary_combined_sampler,
            })
        })();
        let replacement = match result {
            Ok(resources) => resources,
            Err(error) => {
                for handle in created.into_iter().rev() {
                    let _ = gal.destroy(handle);
                }
                return Err(error);
            }
        };
        if let Some(previous) = self.source_shadow_depth_resources.replace(replacement) {
            previous.destroy(gal)?;
        }
        self.source_shadow_depth_resources
            .as_ref()
            .map(TerrainSourceShadowDepthResources::semantic_resource_set)
            .transpose()
    }

    pub(crate) fn clear_candidate_source_shadow_depth_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        if let Some(previous) = self.source_shadow_depth_resources.take() {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    /// Creates the semantic combined sampler for a future Rust-owned source
    /// shadow-color attachment. This method does not allocate, clear, render
    /// to, or otherwise invent that attachment: callers must provide an owned
    /// color view from a matching shader-graph generation.
    pub(crate) fn ensure_candidate_source_shadow_color_resources(
        &mut self,
        gal: &mut VulkanicGal,
        input: TerrainSourceShadowColorInput,
    ) -> GalResult<Option<TerrainSourceOwnedResourceSet>> {
        if !self.candidate_source_requires_resource(TerrainSourceResourceRole::ShadowColor) {
            self.clear_candidate_source_shadow_color_resources(gal)?;
            return Ok(None);
        }
        if input.shader_pack_generation == 0
            || input.world_generation == 0
            || input.shader_graph_generation == 0
        {
            return Err(GalError::invalid_argument(
                "source shadow color requires non-zero shader-pack, world, and shader-graph generations",
            ));
        }
        if input.shader_pack_generation != self.expected_shader_pack_generation_for_resources() {
            return Err(GalError::invalid_argument(
                "source shadow color shader-pack generation does not match the discovered candidate",
            ));
        }
        if self
            .source_shadow_color_resources
            .as_ref()
            .is_some_and(|resources| resources.compatible_with(input))
        {
            return self
                .source_shadow_color_resources
                .as_ref()
                .map(TerrainSourceShadowColorResources::semantic_resource_set)
                .transpose();
        }
        let replacement = TerrainSourceShadowColorResources {
            shader_pack_generation: input.shader_pack_generation,
            world_generation: input.world_generation,
            shader_graph_generation: input.shader_graph_generation,
            shadow_color_view: input.shadow_color_view,
            sampler: input.sampler,
            combined_sampler: gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!(
                    "shader-pack.source-shadow-color.pack{}.world{}.graph{}",
                    input.shader_pack_generation,
                    input.world_generation,
                    input.shader_graph_generation
                ),
                texture_view: input.shadow_color_view,
                sampler: input.sampler,
            })?,
        };
        if let Some(previous) = self.source_shadow_color_resources.replace(replacement) {
            previous.destroy(gal)?;
        }
        self.source_shadow_color_resources
            .as_ref()
            .map(TerrainSourceShadowColorResources::semantic_resource_set)
            .transpose()
    }

    pub(crate) fn clear_candidate_source_shadow_color_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        if let Some(previous) = self.source_shadow_color_resources.take() {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    #[cfg(test)]
    pub(crate) fn candidate_source_material_atlas_identity(&self) -> Option<(Handle, u64)> {
        self.source_material_texture_resources
            .get(&TerrainSourceResourceRole::MaterialAtlas)
            .map(|resources| (resources.combined_sampler, resources.mesh_asset_generation))
    }

    #[cfg(test)]
    pub(crate) fn candidate_source_material_texture_identity(
        &self,
        role: TerrainSourceResourceRole,
    ) -> Option<(Handle, u64)> {
        self.source_material_texture_resources
            .get(&role)
            .map(|resources| (resources.combined_sampler, resources.mesh_asset_generation))
    }

    #[cfg(test)]
    pub(crate) fn candidate_source_asset_resource_count(&self) -> Option<usize> {
        self.source_asset_resources
            .as_ref()
            .map(TerrainSourceAssetResources::len)
    }

    /// Returns an owned binding only when the currently discovered source
    /// requires it and the exact source generation has a complete matching
    /// colored-light volume. This does not select a source program; pipeline
    /// composition remains the frontend's explicit next step.
    pub(crate) fn candidate_shader_binding(
        &self,
        frame_counter: u64,
    ) -> GalResult<Option<TerrainShaderProgramBinding>> {
        let (source_generation, requires_colored_voxel_light) = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                generation,
                requires_colored_voxel_light,
                ..
            } => (*generation, *requires_colored_voxel_light),
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
        };
        if !requires_colored_voxel_light {
            return Ok(None);
        }
        let colored_light = self.terrain_colored_light.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "selected terrain source requires a complete owned colored voxel-light volume",
            )
        })?;
        if colored_light.descriptor().shader_pack_generation != source_generation {
            return Err(GalError::invalid_argument(
                "selected terrain source and colored voxel-light volume generations differ",
            ));
        }
        let TerrainVoxelLightSamplingBinding {
            resource_layout,
            resource_set,
            resource_generation,
            ..
        } = colored_light.sampling_binding(frame_counter)?;
        Ok(Some(TerrainShaderProgramBinding {
            resource: TerrainProgramResource::ColoredVoxelLightVolume,
            resource_layout,
            resource_set: TerrainShaderResourceSet {
                set_index: 1,
                set: resource_set,
            },
            resource_generation,
        }))
    }

    /// Prepares the exact paired source stages and semantic sampler plan
    /// retained during discovery. This cannot compile a backend program,
    /// allocate a resource layout, select a route, or issue a draw.
    pub(crate) fn prepared_lowered_terrain_source_program(
        &self,
        kind: TerrainMaterialProgramKind,
    ) -> GalResult<Option<LoweredTerrainSourceProgram>> {
        match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => Ok(None),
            TerrainSourceCandidateState::Discovered {
                contract,
                source_summary: Some(_),
                source_preprocess_error: None,
                source_lowering_summary: Some(_),
                source_lowering_error: None,
                source_uniform_requirement_summary: Some(summary),
                source_uniform_requirement_error: None,
                source_lowered_pair: Some(lowered),
                source_resource_binding_count: Some(_),
                source_resource_binding_error: None,
                source_resource_bindings: Some(bindings),
                ..
            } if summary.field_count == summary.resolved_field_count => {
                prepare_lowered_terrain_source_program(contract, lowered, bindings, kind).map(Some)
            }
            TerrainSourceCandidateState::Discovered {
                contract,
                source_preprocess_error,
                source_lowering_error,
                source_uniform_requirement_summary,
                source_uniform_requirement_error,
                source_resource_binding_error,
                ..
            } => {
                let contract_status = contract
                    .require_selected_subset()
                    .err()
                    .map(|error| error.to_string())
                    .unwrap_or_else(|| "otherwise source-contract supported".to_string());
                let uniform_status = source_uniform_requirement_error.clone().or_else(|| {
                    source_uniform_requirement_summary
                        .as_ref()
                        .and_then(|summary| {
                            (summary.field_count != summary.resolved_field_count).then(|| {
                                format!(
                                    "unresolved scalar source uniforms: {}",
                                    summary.unresolved_field_names.join(", ")
                                )
                            })
                        })
                });
                Err(GalError::unsupported_feature(format!(
                    "selected terrain source has no complete paired-stage source contract: {}; source contract: {contract_status}",
                    source_preprocess_error
                        .as_deref()
                        .or(source_lowering_error.as_deref())
                        .or(uniform_status.as_deref())
                        .or(source_resource_binding_error.as_deref())
                        .unwrap_or("missing paired lowering/resource provenance")
                )))
            }
        }
    }

    /// Builds the internal terrain fixture after source discovery. This
    /// exists only for focused tests of resource-generation coherence; it
    /// neither prepares nor executes the selected shader-pack source.
    pub(crate) fn candidate_fixture_terrain_program(
        &self,
        kind: TerrainMaterialProgramKind,
        frame_counter: u64,
    ) -> GalResult<Option<TerrainSourceProgramCandidate>> {
        let (contract, source_vertex_interface) = match &self.source_candidate {
            TerrainSourceCandidateState::Unavailable
            | TerrainSourceCandidateState::Disabled { .. }
            | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
            TerrainSourceCandidateState::Discovered {
                contract,
                source_summary: Some(_),
                source_preprocess_error: None,
                source_vertex_interface: Some(interface),
                source_lowering_summary: Some(_),
                source_lowering_error: None,
                source_uniform_requirement_summary: Some(summary),
                source_uniform_requirement_error: None,
                source_lowered_pair: Some(_),
                source_resource_binding_count: Some(_),
                source_resource_binding_error: None,
                source_resource_bindings: Some(_),
                ..
            } if summary.field_count == summary.resolved_field_count => (contract, interface),
            TerrainSourceCandidateState::Discovered {
                contract,
                source_preprocess_error,
                source_lowering_error,
                source_uniform_requirement_summary,
                source_uniform_requirement_error,
                source_resource_binding_error,
                ..
            } => {
                let contract_status = contract
                    .require_selected_subset()
                    .err()
                    .map(|error| error.to_string())
                    .unwrap_or_else(|| "otherwise source-contract supported".to_string());
                let uniform_status = source_uniform_requirement_error.clone().or_else(|| {
                    source_uniform_requirement_summary
                        .as_ref()
                        .and_then(|summary| {
                            (summary.field_count != summary.resolved_field_count).then(|| {
                                format!(
                                    "unresolved scalar source uniforms: {}",
                                    summary.unresolved_field_names.join(", ")
                                )
                            })
                        })
                });
                return Err(GalError::unsupported_feature(format!(
                    "selected terrain source has no complete paired-stage source contract: {}; source contract: {contract_status}",
                    source_preprocess_error
                        .as_deref()
                        .or(source_lowering_error.as_deref())
                        .or(uniform_status.as_deref())
                        .or(source_resource_binding_error.as_deref())
                        .unwrap_or("missing paired lowering/resource provenance")
                )));
            }
        };
        let binding = self.candidate_shader_binding(frame_counter)?;
        let readiness = if contract
            .required_resources
            .contains(&TerrainPassRequiredResource::ColoredVoxelLightVolume)
        {
            Some(
                self.terrain_colored_light
                    .as_ref()
                    .ok_or_else(|| {
                        GalError::invalid_argument(
                            "selected terrain source requires a complete owned colored voxel-light volume",
                        )
                    })?
                    .readiness()?,
            )
        } else {
            None
        };
        let program = complementary_terrain_subset_program_with_resources(
            contract,
            kind,
            readiness.as_ref(),
            frame_counter,
        )?;
        // Keep source admission honest if the source contract becomes
        // lowerable before the shared mesh ABI grows the required semantics.
        // The check remains after contract admission so today's explicit
        // `UnloweredTerrainSource` status is retained as the primary reason.
        source_vertex_interface.require_current_world_mesh_support()?;
        if program.requires(TerrainProgramResource::ColoredVoxelLightVolume) != binding.is_some() {
            return Err(GalError::invalid_argument(
                "selected terrain source program/resource binding requirements disagree",
            ));
        }
        Ok(Some(TerrainSourceProgramCandidate { program, binding }))
    }

    /// Prepares the complete semantic identity for an owned colored-light
    /// runtime. The source contract owns extent/format/material/emission
    /// policy; the caller supplies only copied world/resource/camera facts.
    pub(crate) fn candidate_colored_light_preparation(
        &self,
        world_generation: u64,
        resource_generation: u64,
        camera_world_position: [f32; 3],
    ) -> GalResult<Option<TerrainColoredLightPreparation>> {
        let (generation, pack_name, contract, requires_colored_voxel_light, materials, emission) =
            match &self.source_candidate {
                TerrainSourceCandidateState::Unavailable
                | TerrainSourceCandidateState::Disabled { .. }
                | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
                TerrainSourceCandidateState::Discovered {
                    generation,
                    pack_name,
                    contract,
                    requires_colored_voxel_light,
                    voxel_materials,
                    voxel_emission,
                    ..
                } => (
                    *generation,
                    pack_name,
                    contract,
                    *requires_colored_voxel_light,
                    voxel_materials,
                    voxel_emission,
                ),
            };
        if !requires_colored_voxel_light {
            return Ok(None);
        }
        if generation != contract.generation || world_generation == 0 || resource_generation == 0 {
            return Err(GalError::invalid_argument(
                "colored voxel-light preparation requires matching non-zero source, world, and resource generations",
            ));
        }
        let requirements = contract.voxel_light_volume_requirements.ok_or_else(|| {
            GalError::invalid_argument(
                "selected terrain source requires ColoredVoxelLighting without a volume descriptor",
            )
        })?;
        let materials = materials.as_ref().ok_or_else(|| {
            GalError::invalid_argument("selected terrain source has no derived voxel material map")
        })?;
        let emission = emission.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "selected terrain source has no derived colored-light emission table",
            )
        })?;
        let mut camera_cell = [0_i32; 3];
        let mut camera_fraction = [0.0_f32; 3];
        for axis in 0..3 {
            let coordinate = camera_world_position[axis];
            if !coordinate.is_finite() {
                return Err(GalError::invalid_argument(
                    "colored voxel-light camera position must be finite",
                ));
            }
            let cell = coordinate.floor();
            if cell < i32::MIN as f32 || cell > i32::MAX as f32 {
                return Err(GalError::invalid_argument(
                    "colored voxel-light camera cell is outside i32 range",
                ));
            }
            camera_cell[axis] = cell as i32;
            camera_fraction[axis] = coordinate - cell;
        }
        let descriptor = VoxelLightVolumeDescriptor {
            identity: VoxelLightVolumeIdentity::new(format!(
                "shader-pack:{}/colored-voxel-light",
                pack_name.to_ascii_lowercase()
            ))?,
            shader_pack_generation: generation,
            world_generation,
            resource_generation,
            extent: requirements.extent,
            requirements,
            mapping: VoxelLightVolumeMapping::complementary(
                requirements.extent,
                camera_cell,
                camera_fraction,
            )?,
        };
        descriptor.validate()?;
        Ok(Some(TerrainColoredLightPreparation {
            descriptor,
            materials: materials.clone(),
            emission: emission.clone(),
        }))
    }

    pub(crate) fn install_private_terrain_occupancy(
        &mut self,
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
    ) -> GalResult<()> {
        if descriptor.shader_pack_generation != self.expected_shader_pack_generation_for_resources()
        {
            return Err(GalError::invalid_argument(
                "private terrain occupancy generation must match its shader runtime",
            ));
        }
        let replacement = TerrainOccupancyRuntime::create(gal, descriptor, materials)?;
        if let Some(previous) = self.terrain_colored_light.take() {
            previous.destroy(gal)?;
        }
        if let Some(previous) = self.terrain_occupancy.replace(replacement) {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    pub(crate) fn install_private_terrain_colored_light(
        &mut self,
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
        emission: VoxelEmissionTable,
    ) -> GalResult<()> {
        if descriptor.shader_pack_generation != self.expected_shader_pack_generation_for_resources()
        {
            return Err(GalError::invalid_argument(
                "private colored-light generation must match its shader runtime",
            ));
        }
        let replacement = TerrainColoredLightRuntime::create(gal, descriptor, materials, emission)?;
        if let Some(previous) = self.terrain_occupancy.take() {
            previous.destroy(gal)?;
        }
        if let Some(previous) = self.terrain_colored_light.replace(replacement) {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    /// Installs a complete source-derived colored-light generation exactly
    /// once. A changed source/world/resource descriptor replaces the previous
    /// owned runtime atomically; an identical descriptor keeps persistent D3
    /// resources alive for bounded per-frame updates.
    pub(crate) fn ensure_candidate_colored_light_runtime(
        &mut self,
        gal: &mut VulkanicGal,
        preparation: TerrainColoredLightPreparation,
    ) -> GalResult<bool> {
        if self.terrain_colored_light.as_ref().is_some_and(|runtime| {
            runtime
                .descriptor()
                .resource_compatible_with(&preparation.descriptor)
        }) {
            return Ok(false);
        }
        self.install_private_terrain_colored_light(
            gal,
            preparation.descriptor,
            preparation.materials,
            preparation.emission,
        )?;
        Ok(true)
    }

    /// Removes only a source-candidate preparation runtime. This does not
    /// select or deselect any terrain program; callers use it when the
    /// semantic source contract or world-volume mapping is no longer valid.
    pub(crate) fn clear_candidate_colored_light_runtime(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        if let Some(previous) = self.terrain_colored_light.take() {
            previous.destroy(gal)?;
        }
        Ok(())
    }

    pub(crate) fn expected_shader_pack_generation_for_resources(&self) -> u64 {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered { generation, .. } => *generation,
            _ => self.plan.generation,
        }
    }

    pub(crate) fn has_private_terrain_occupancy(&self) -> bool {
        self.terrain_occupancy.is_some() || self.terrain_colored_light.is_some()
    }

    #[cfg(test)]
    pub(crate) fn private_terrain_occupancy_mesh_count(&self) -> Option<usize> {
        self.terrain_occupancy
            .as_ref()
            .map(TerrainOccupancyRuntime::mesh_snapshot_count)
            .or_else(|| {
                self.terrain_colored_light
                    .as_ref()
                    .map(TerrainColoredLightRuntime::mesh_snapshot_count)
            })
    }

    pub(crate) fn private_terrain_occupancy_descriptor(
        &self,
    ) -> Option<&VoxelLightVolumeDescriptor> {
        self.terrain_occupancy
            .as_ref()
            .map(TerrainOccupancyRuntime::descriptor)
            .or_else(|| {
                self.terrain_colored_light
                    .as_ref()
                    .map(TerrainColoredLightRuntime::descriptor)
            })
    }

    #[cfg(test)]
    pub(crate) fn private_terrain_occupancy_mapping(&self) -> Option<VoxelLightVolumeMapping> {
        self.private_terrain_occupancy_descriptor()
            .map(|descriptor| descriptor.mapping)
    }

    #[cfg(test)]
    pub(crate) fn private_terrain_colored_light_ready(&self, frame_counter: u64) -> Option<bool> {
        self.terrain_colored_light
            .as_ref()
            .map(|runtime| runtime.is_ready_for_frame(frame_counter))
    }

    pub(crate) fn append_private_terrain_occupancy(
        &mut self,
        frame_counter: u64,
        mapping: VoxelLightVolumeMapping,
        view_direction: Option<VoxelLightVolumeViewDirection>,
        meshes: impl IntoIterator<Item = TerrainVoxelSourceMesh>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        let meshes = meshes.into_iter().collect::<Vec<_>>();
        if let Some(colored_light) = self.terrain_colored_light.as_mut() {
            colored_light.append_terrain_source_snapshot_for_mapping(
                frame_counter,
                mapping,
                view_direction,
                meshes,
                operations,
            )?;
        } else if let Some(occupancy) = self.terrain_occupancy.as_mut() {
            occupancy.append_terrain_source_snapshot_for_mapping(mapping, meshes, operations)?;
        }
        Ok(())
    }

    pub(crate) fn confirm_private_terrain_occupancy_submission(&mut self) -> GalResult<()> {
        if let Some(colored_light) = self.terrain_colored_light.as_mut() {
            if colored_light.has_pending_submission() {
                colored_light.confirm_submission()?;
            }
        } else if let Some(occupancy) = self.terrain_occupancy.as_mut() {
            if occupancy.has_pending_submission() {
                occupancy.confirm_submission()?;
            }
        }
        Ok(())
    }

    pub(crate) fn discard_private_terrain_occupancy_submission(&mut self) {
        if let Some(colored_light) = self.terrain_colored_light.as_mut() {
            colored_light.discard_submission();
        } else if let Some(occupancy) = self.terrain_occupancy.as_mut() {
            occupancy.discard_submission();
        }
    }

    pub(crate) fn destroy(mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        if let Some(resources) = self.source_shadow_color_resources.take() {
            resources.destroy(gal)?;
        }
        if let Some(resources) = self.source_shadow_depth_resources.take() {
            resources.destroy(gal)?;
        }
        self.clear_candidate_source_material_texture_resources(gal)?;
        if let Some(resources) = self.source_asset_resources.take() {
            resources.destroy(gal)?;
        }
        if let Some(occupancy) = self.terrain_occupancy.take() {
            occupancy.destroy(gal)?;
        }
        if let Some(colored_light) = self.terrain_colored_light.take() {
            colored_light.destroy(gal)?;
        }
        Ok(())
    }

    pub(crate) fn append_terrain_material_graph(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        frame: TerrainRuntimeFrame,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        self.validate_terrain_material_graph()?;
        if draws.is_empty() {
            return Ok(());
        }
        let isolation = TerrainGraphIsolation::from_env();
        let effective_draws_storage;
        let effective_draws = if isolation == TerrainGraphIsolation::FullDrawsSkipped {
            effective_draws_storage = Vec::new();
            effective_draws_storage.as_slice()
        } else {
            draws
        };

        if matches!(
            isolation,
            TerrainGraphIsolation::Full
                | TerrainGraphIsolation::TerrainPlusShadow
                | TerrainGraphIsolation::FullDrawsSkipped
        ) {
            self.append_shadow_depth_pass(ops, targets, effective_draws)?;
        }
        self.append_g_buffer_passes(
            ops,
            targets,
            frame.background_color,
            effective_draws,
            isolation == TerrainGraphIsolation::FullDrawsSkipped,
        )?;
        if matches!(
            isolation,
            TerrainGraphIsolation::Full
                | TerrainGraphIsolation::GBufferNoShadow
                | TerrainGraphIsolation::FullDrawsSkipped
        ) {
            self.append_deferred_and_composites(ops, targets, frame, effective_draws)?;
        }
        Ok(())
    }

    /// Exercises one validated source-derived G-buffer transaction without
    /// selecting a gameplay route or claiming the incomplete source shadow
    /// and composite passes. This is intentionally test-only: production
    /// callers must enter through the complete runtime graph.
    #[cfg(test)]
    pub(crate) fn append_terrain_g_buffer_for_test(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        background_color: ClearColor,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        self.validate_terrain_material_graph()?;
        self.append_g_buffer_passes(ops, targets, background_color, draws, false)
    }

    fn validate_terrain_material_graph(&self) -> GalResult<()> {
        let passes = self
            .plan
            .graph
            .passes()
            .iter()
            .map(|pass| pass.identity.as_str())
            .collect::<Vec<_>>();
        let expected = [
            "vulkanic:pass/shadow_depth",
            "vulkanic:pass/terrain_opaque",
            "vulkanic:pass/terrain_cutout",
            "vulkanic:pass/deferred_lighting",
            "vulkanic:pass/terrain_translucent",
            "vulkanic:pass/composite_0",
            "vulkanic:pass/composite_1",
            "vulkanic:pass/final_output",
        ];
        if passes != expected {
            return Err(GalError::invalid_argument(format!(
                "terrain material runtime graph has unexpected pass order: {:?}",
                passes
            )));
        }
        Ok(())
    }

    fn append_shadow_depth_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        let pass = self.pass_identity(AttachmentRole::ShadowDepth)?;
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.shadow_depth_texture,
            TextureUsageState::Undefined,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: targets.shadow_pass,
            target: targets.shadow_target,
            colors: Vec::new(),
            depth_stencil: Some(PassAttachment {
                view: targets.shadow_depth_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        let mut draw_state = IndexedDrawState::default();
        for draw in draws
            .iter()
            .filter(|draw| draw.material_mode != TerrainMaterialPassMode::Translucent)
        {
            let shadow_pipeline = draw.shadow_pipeline.ok_or_else(|| {
                GalError::backend(format!(
                    "{} mesh draw missing shadow pipeline",
                    pass.as_str()
                ))
            })?;
            append_indexed_draw(
                ops,
                &mut draw_state,
                shadow_pipeline,
                draw.pipeline_layout,
                draw.resource_set,
                &draw.resource_set_dynamic_offsets,
                draw.shader_resource_set,
                draw.index_buffer,
                draw.index_offset,
                draw.index_type,
                draw.index_count,
                draw.instance_count,
            );
        }
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.shadow_depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    fn append_g_buffer_passes(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        background_color: ClearColor,
        draws: &[TerrainMeshDraw],
        force_empty_clear: bool,
    ) -> GalResult<()> {
        for texture in [
            targets.albedo_texture,
            targets.normal_texture,
            targets.material_light_texture,
            targets.world_position_texture,
        ] {
            ops.push(CommandOp::Barrier(texture_barrier(
                texture,
                TextureUsageState::Undefined,
                TextureUsageState::ColorAttachment,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::Undefined,
            TextureUsageState::DepthStencilAttachment,
        )));

        let mut wrote_g_buffer = false;
        for mode in [
            TerrainMaterialPassMode::Opaque,
            TerrainMaterialPassMode::Cutout,
        ] {
            let has_mode_draws = draws.iter().any(|draw| draw.material_mode == mode);
            if !has_mode_draws && !force_empty_clear {
                continue;
            }
            let load_op = if wrote_g_buffer {
                AttachmentLoadOp::Load
            } else {
                AttachmentLoadOp::Clear
            };
            ops.push(CommandOp::BeginPass {
                pass: targets.g_buffer_pass,
                target: targets.target,
                colors: vec![
                    PassAttachment {
                        view: targets.albedo_view,
                        load_op,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(background_color),
                    },
                    PassAttachment {
                        view: targets.normal_view,
                        load_op,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(ClearColor {
                            r: 0.5,
                            g: 0.5,
                            b: 1.0,
                            a: 1.0,
                        }),
                    },
                    PassAttachment {
                        view: targets.material_light_view,
                        load_op,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(ClearColor {
                            r: 0.0,
                            g: 1.0,
                            b: 1.0,
                            a: 0.0,
                        }),
                    },
                    PassAttachment {
                        view: targets.world_position_view,
                        load_op,
                        store_op: AttachmentStoreOp::Store,
                        clear_color: Some(ClearColor {
                            r: 0.5,
                            g: 0.5,
                            b: 0.5,
                            a: 0.0,
                        }),
                    },
                ],
                depth_stencil: Some(PassAttachment {
                    view: targets.depth_view,
                    load_op,
                    store_op: AttachmentStoreOp::Store,
                    clear_color: None,
                }),
            });
            let mut draw_state = IndexedDrawState::default();
            for draw in draws.iter().filter(|draw| draw.material_mode == mode) {
                append_indexed_draw(
                    ops,
                    &mut draw_state,
                    draw.pipeline,
                    draw.pipeline_layout,
                    draw.resource_set,
                    &draw.resource_set_dynamic_offsets,
                    draw.shader_resource_set,
                    draw.index_buffer,
                    draw.index_offset,
                    draw.index_type,
                    draw.index_count,
                    draw.instance_count,
                );
            }
            ops.push(CommandOp::EndPass);
            wrote_g_buffer = true;
        }

        for texture in [
            targets.albedo_texture,
            targets.normal_texture,
            targets.material_light_texture,
            targets.world_position_texture,
        ] {
            ops.push(CommandOp::Barrier(texture_barrier(
                texture,
                TextureUsageState::ColorAttachment,
                TextureUsageState::ShaderRead,
            )));
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    fn append_deferred_and_composites(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        frame: TerrainRuntimeFrame,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        ops.push(CommandOp::Barrier(buffer_barrier(
            targets.composite_uniform_buffer,
            TextureUsageState::ShaderRead,
            TextureUsageState::TransferDst,
        )));
        ops.push(CommandOp::HostWriteBuffer {
            buffer: targets.composite_uniform_buffer,
            offset: 0,
            data: frame.uniforms.pack(),
        });
        ops.push(CommandOp::Barrier(buffer_barrier(
            targets.composite_uniform_buffer,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));

        self.append_screen_pass(
            ops,
            targets.deferred_lit_texture,
            targets.deferred_lighting_pass,
            targets.deferred_lit_target,
            targets.deferred_lit_view,
            targets.deferred_lighting_pipeline,
            targets.deferred_lighting_resource_set,
            targets.screen_pipeline_layout,
            transparent_clear(frame.background_color),
        );
        self.append_translucent_pass(ops, targets, draws)?;
        self.append_screen_pass(
            ops,
            targets.composite0_texture,
            targets.composite0_pass,
            targets.composite0_target,
            targets.composite0_view,
            targets.composite0_pipeline,
            targets.composite0_resource_set,
            targets.screen_pipeline_layout,
            transparent_clear(frame.background_color),
        );
        self.append_screen_pass(
            ops,
            targets.composite1_texture,
            targets.composite1_pass,
            targets.composite1_target,
            targets.composite1_view,
            targets.composite1_pipeline,
            targets.composite1_resource_set,
            targets.screen_pipeline_layout,
            transparent_clear(frame.background_color),
        );

        ops.push(CommandOp::BeginPass {
            pass: targets.final_pass,
            target: frame.frame_target,
            colors: vec![PassAttachment {
                view: frame.color_attachment,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: frame.final_depth_view.map(|view| PassAttachment {
                view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        ops.push(CommandOp::BindGraphicsPipeline(targets.final_pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout: targets.screen_pipeline_layout,
            set_index: 0,
            set: targets.final_resource_set,
            dynamic_offsets: Vec::new(),
        });
        ops.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        ops.push(CommandOp::EndPass);
        Ok(())
    }

    fn append_translucent_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        targets: TerrainRuntimeTargets,
        draws: &[TerrainMeshDraw],
    ) -> GalResult<()> {
        if !draws
            .iter()
            .any(|draw| draw.material_mode == TerrainMaterialPassMode::Translucent)
        {
            return Ok(());
        }
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.deferred_lit_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::ShaderRead,
            TextureUsageState::DepthStencilAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass: targets.translucent_pass,
            target: targets.translucent_target,
            colors: vec![PassAttachment {
                view: targets.deferred_lit_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }],
            depth_stencil: Some(PassAttachment {
                view: targets.depth_view,
                load_op: AttachmentLoadOp::Load,
                store_op: AttachmentStoreOp::Store,
                clear_color: None,
            }),
        });
        let mut draw_state = IndexedDrawState::default();
        for draw in draws
            .iter()
            .filter(|draw| draw.material_mode == TerrainMaterialPassMode::Translucent)
        {
            append_indexed_draw(
                ops,
                &mut draw_state,
                draw.pipeline,
                draw.pipeline_layout,
                draw.resource_set,
                &draw.resource_set_dynamic_offsets,
                draw.shader_resource_set,
                draw.index_buffer,
                draw.index_offset,
                draw.index_type,
                draw.index_count,
                draw.instance_count,
            );
        }
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.deferred_lit_texture,
            TextureUsageState::ColorAttachment,
            TextureUsageState::ShaderRead,
        )));
        ops.push(CommandOp::Barrier(texture_barrier(
            targets.depth_texture,
            TextureUsageState::DepthStencilAttachment,
            TextureUsageState::ShaderRead,
        )));
        Ok(())
    }

    #[allow(clippy::too_many_arguments)]
    fn append_screen_pass(
        &self,
        ops: &mut Vec<CommandOp>,
        texture: Handle,
        pass: Handle,
        target: Handle,
        color_view: Handle,
        pipeline: Handle,
        resource_set: Handle,
        pipeline_layout: Handle,
        clear_color: ClearColor,
    ) {
        ops.push(CommandOp::Barrier(texture_barrier(
            texture,
            TextureUsageState::Undefined,
            TextureUsageState::ColorAttachment,
        )));
        ops.push(CommandOp::BeginPass {
            pass,
            target,
            colors: vec![PassAttachment {
                view: color_view,
                load_op: AttachmentLoadOp::Clear,
                store_op: AttachmentStoreOp::Store,
                clear_color: Some(clear_color),
            }],
            depth_stencil: None,
        });
        ops.push(CommandOp::BindGraphicsPipeline(pipeline));
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: Vec::new(),
        });
        ops.push(CommandOp::Draw {
            vertices: 3,
            instances: 1,
        });
        ops.push(CommandOp::EndPass);
        ops.push(CommandOp::Barrier(texture_barrier(
            texture,
            TextureUsageState::ColorAttachment,
            TextureUsageState::ShaderRead,
        )));
    }

    fn pass_identity(&self, role: AttachmentRole) -> GalResult<&PassIdentity> {
        self.plan
            .graph
            .passes()
            .iter()
            .find(|pass| pass.depth == Some(role) || pass.colors.contains(&role))
            .map(|pass| &pass.identity)
            .ok_or_else(|| {
                GalError::invalid_argument(format!("shader runtime graph is missing {role:?} pass"))
            })
    }
}

fn write_contract_diagnostic(plan: &ShaderPackRuntimePlan) {
    let Some(dir) = std::env::var_os("MATTMC_TERRAIN_PASS_CONTRACT_DIAGNOSTIC_DIR") else {
        return;
    };
    let dir = Path::new(&dir);
    if fs::create_dir_all(dir).is_err() {
        return;
    }
    let _ = fs::write(
        dir.join(format!(
            "terrain-pass-contract-generation-{}.json",
            plan.generation
        )),
        format!("{}\n", plan.terrain_contract_diagnostic_json()),
    );
}

impl TerrainCompositeUniforms {
    pub(crate) fn pack(self) -> Vec<u8> {
        let mut out = Vec::with_capacity(TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES as usize);
        for value in self.light_view_projection {
            push_f32(&mut out, value);
        }
        for value in self.shadow_params {
            push_f32(&mut out, value);
        }
        for value in self.color_grade_params {
            push_f32(&mut out, value);
        }
        for value in self.fog_params {
            push_f32(&mut out, value);
        }
        out
    }
}

#[derive(Default)]
struct IndexedDrawState {
    pipeline: Option<Handle>,
    resource_set: Option<(Handle, u32, Handle, Vec<u64>)>,
    shader_resource_set: Option<(Handle, u32, Handle)>,
    index_buffer: Option<(Handle, u64, IndexType)>,
}

#[allow(clippy::too_many_arguments)]
fn append_indexed_draw(
    ops: &mut Vec<CommandOp>,
    state: &mut IndexedDrawState,
    pipeline: Handle,
    pipeline_layout: Handle,
    resource_set: Handle,
    resource_set_dynamic_offsets: &[u64],
    shader_resource_set: Option<TerrainShaderResourceSet>,
    index_buffer: Handle,
    index_offset: u64,
    index_type: IndexType,
    index_count: u32,
    instance_count: u32,
) {
    if state.pipeline != Some(pipeline) {
        ops.push(CommandOp::BindGraphicsPipeline(pipeline));
        state.pipeline = Some(pipeline);
        state.resource_set = None;
        state.shader_resource_set = None;
    }
    let resource_set_binding = (
        pipeline_layout,
        0,
        resource_set,
        resource_set_dynamic_offsets.to_vec(),
    );
    if state.resource_set.as_ref() != Some(&resource_set_binding) {
        ops.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: resource_set_dynamic_offsets.to_vec(),
        });
        state.resource_set = Some(resource_set_binding);
    }
    let shader_resource_set_binding =
        shader_resource_set.map(|binding| (pipeline_layout, binding.set_index, binding.set));
    if state.shader_resource_set != shader_resource_set_binding {
        if let Some((pipeline_layout, set_index, set)) = shader_resource_set_binding {
            ops.push(CommandOp::BindResourceSet {
                pipeline_layout,
                set_index,
                set,
                dynamic_offsets: Vec::new(),
            });
        }
        state.shader_resource_set = shader_resource_set_binding;
    }
    let index_binding = (index_buffer, index_offset, index_type);
    if state.index_buffer != Some(index_binding) {
        ops.push(CommandOp::SetIndexBuffer {
            buffer: index_buffer,
            offset: index_offset,
            index_type,
        });
        state.index_buffer = Some(index_binding);
    }
    ops.push(CommandOp::DrawIndexed {
        indices: index_count,
        instances: instance_count,
    });
}

fn buffer_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

fn texture_barrier(
    resource: Handle,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources: None,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

fn transparent_clear(color: ClearColor) -> ClearColor {
    ClearColor {
        r: color.r,
        g: color.g,
        b: color.b,
        a: 0.0,
    }
}

fn push_f32(out: &mut Vec<u8>, value: f32) {
    out.extend_from_slice(&value.to_ne_bytes());
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::vulkanic::backends::{
        mock::MockBackend, presentation_capabilities, vulkan_capabilities,
    };
    use crate::render::vulkanic::handles::HandleKind;
    use crate::render::vulkanic::resources::{
        Extent3d, TextureDesc, TextureDimension, TextureFormat, TextureUsage, TextureViewDesc,
    };
    use crate::render::vulkanic::shader_pack::assets::{
        ShaderPackAssetFile, ShaderPackAssetUpdate,
    };
    use crate::render::vulkanic::shader_pack::preprocess::complete_bundled_pack_source_for_test;
    use crate::render::vulkanic::shader_pack::source::ShaderSourceFile;
    use crate::render::vulkanic::shader_pack::terrain_source_resources::{
        TerrainSourceResourceRole, TERRAIN_RESOURCE_BINDINGS_PATH,
    };
    use std::path::PathBuf;

    fn gal() -> VulkanicGal {
        VulkanicGal::new_with_backend(
            Box::new(MockBackend::with_capabilities(presentation_capabilities(
                vulkan_capabilities(),
            ))),
            false,
        )
    }

    fn copied_png_assets_for(source: &ShaderPackSource) -> ShaderPackAssets {
        let root = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("../resources/shaders/ComplementaryHungLoIfied/shaders");
        let bindings = TerrainShaderPackAssetBindings::from_source(source).unwrap();
        let mut files = Vec::new();
        for (_, path) in bindings.samplers() {
            files.push(ShaderPackAssetFile::new(
                path,
                fs::read(root.join(path)).unwrap(),
            ));
            let sidecar = format!("{path}.mcmeta");
            if root.join(&sidecar).is_file() {
                files.push(ShaderPackAssetFile::new(
                    &sidecar,
                    fs::read(root.join(&sidecar)).unwrap(),
                ));
            }
        }
        ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: source.name().to_string(),
            generation: source.generation(),
            files,
        })
        .unwrap()
    }
    use crate::render::vulkanic::shader_pack::terrain_contract::bundled_complementary_hung_loified_source;

    #[test]
    fn runtime_executor_owns_expected_gameplay_pass_order() {
        let executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(9).unwrap();
        let passes = executor
            .plan()
            .graph
            .passes()
            .iter()
            .map(|pass| pass.identity.as_str())
            .collect::<Vec<_>>();
        assert_eq!(
            passes,
            vec![
                "vulkanic:pass/shadow_depth",
                "vulkanic:pass/terrain_opaque",
                "vulkanic:pass/terrain_cutout",
                "vulkanic:pass/deferred_lighting",
                "vulkanic:pass/terrain_translucent",
                "vulkanic:pass/composite_0",
                "vulkanic:pass/composite_1",
                "vulkanic:pass/final_output",
            ]
        );
        assert!(executor.validate_terrain_material_graph().is_ok());
    }

    #[test]
    fn owned_source_generation_is_discovered_without_admitting_source_execution() {
        let source = bundled_complementary_hung_loified_source(11).unwrap();
        let executor =
            ShaderPackRuntimeExecutor::terrain_material_fixture_from_source(&source).unwrap();
        let contract = executor.plan().terrain_contract.as_ref().unwrap();
        assert_eq!(source.generation(), contract.generation);
        assert_eq!(source.name(), contract.pack_name);
        assert_eq!(
            "vulkanic:builtin/terrain_opaque_v1",
            executor.plan().programs.terrain_opaque.identity.as_str()
        );
        assert!(executor
            .plan()
            .terrain_contract_diagnostic_json()
            .contains("\"selected_source_plan_prepared\":false"));
    }

    #[test]
    fn owned_source_candidate_is_observed_without_replacing_the_fixture_plan() {
        let source = bundled_complementary_hung_loified_source(13).unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        let fixture_program = executor.plan().programs.terrain_opaque.identity.clone();

        executor.observe_source_candidate(&source);

        let candidate = executor.source_candidate();
        assert!(
            matches!(
                candidate,
                TerrainSourceCandidateState::Discovered {
                    generation: 13,
                    requires_colored_voxel_light: true,
                    ..
                }
            ),
            "{candidate:?}"
        );
        assert!(executor
            .candidate_shader_binding(0)
            .unwrap_err()
            .to_string()
            .contains("complete owned colored voxel-light volume"));
        assert!(matches!(
            candidate,
            TerrainSourceCandidateState::Discovered {
                source_shadow_summary: None,
                source_shadow_preprocess_error: Some(error),
                source_shadow_output_count: None,
                source_shadow_lowering_error: None,
                ..
            } if error.contains("missing shadow source for Default")
        ));
        assert_eq!(
            fixture_program,
            executor.plan().programs.terrain_opaque.identity
        );
        assert_eq!(
            Some("lib/textures/noise.png"),
            executor
                .source_asset_binding_plan()
                .and_then(|bindings| bindings.sampler_path("noisetex"))
        );
    }

    #[test]
    fn material_texture_wrapper_rejects_a_non_material_semantic_role_before_handle_use() {
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        let mut gal = gal();
        let error = executor
            .ensure_candidate_source_material_texture_resources(
                &mut gal,
                TerrainSourceMaterialTextureInput {
                    role: TerrainSourceResourceRole::Lightmap,
                    shader_pack_generation: 1,
                    world_generation: 1,
                    mesh_asset_generation: 1,
                    texture_view: Handle::NULL,
                    sampler: Handle::NULL,
                },
            )
            .unwrap_err();
        assert!(error
            .to_string()
            .contains("unsupported semantic role"));
    }

    #[test]
    fn active_normal_map_binding_owns_a_distinct_semantic_material_wrapper() {
        let source = ShaderPackSource::new(
            "normal-map-resource",
            29,
            vec![
                ShaderSourceFile::new(
                    "gbuffers_terrain.vsh",
                    "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nuniform sampler2D tex;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
                ),
                ShaderSourceFile::new(
                    "gbuffers_terrain.fsh",
                    "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nuniform sampler2D normals;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); color.rgb += texture2D(normals, texCoord).rgb * 0.0; if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
                ),
                ShaderSourceFile::new(
                    TERRAIN_RESOURCE_BINDINGS_PATH,
                    "tex=material_atlas\nnormals=material_normal_map\n",
                ),
                ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        let mut gal = gal();
        executor.observe_source_candidate(&source);
        assert!(executor.candidate_source_requires_resource(
            TerrainSourceResourceRole::MaterialNormalMap
        ));

        let texture = gal
            .create_texture(TextureDesc {
                label: "normal-map-resource.texture".to_string(),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent: Extent3d {
                    width: 4,
                    height: 4,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::TransferDst],
            })
            .unwrap();
        let view = gal
            .create_texture_view(TextureViewDesc {
                label: "normal-map-resource.view".to_string(),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })
            .unwrap();
        let sampler = gal
            .create_sampler(SamplerDesc {
                label: "normal-map-resource.sampler".to_string(),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })
            .unwrap();
        let resources = executor
            .ensure_candidate_source_material_texture_resources(
                &mut gal,
                TerrainSourceMaterialTextureInput {
                    role: TerrainSourceResourceRole::MaterialNormalMap,
                    shader_pack_generation: source.generation(),
                    world_generation: 3,
                    mesh_asset_generation: 5,
                    texture_view: view,
                    sampler,
                },
            )
            .unwrap()
            .expect("the active normal-map source role must receive a wrapper");
        assert!(resources
            .combined_sampler_for(TerrainSourceResourceRole::MaterialNormalMap)
            .is_some());
        executor
            .clear_candidate_source_material_texture_resources(&mut gal)
            .unwrap();
        gal.destroy(sampler).unwrap();
        gal.destroy(view).unwrap();
        gal.destroy(texture).unwrap();
    }

    #[test]
    fn complete_source_discovery_records_paired_lowering_provenance_without_admission() {
        let source = complete_bundled_pack_source_for_test();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

        executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

        assert!(matches!(
            executor.source_candidate(),
            TerrainSourceCandidateState::Discovered {
                source_summary: Some(_),
                source_preprocess_error: None,
                source_shadow_summary: Some(shadow_summary),
                source_shadow_preprocess_error: None,
                source_shadow_output_count: Some(2),
                source_shadow_lowering_error: None,
                source_lowering_summary: Some(summary),
                source_lowering_error: None,
                source_resource_binding_count: Some(8),
                source_resource_binding_error: None,
                ..
            } if summary.varying_count > 0
                && summary.opaque_resource_count > summary.active_opaque_resource_count
                && summary.active_opaque_resource_count > 0
                && shadow_summary.vertex_entry == "world0/shadow.vsh"
                && shadow_summary.fragment_entry == "world0/shadow.fsh"
        ), "{:#?}", executor.source_candidate());
        assert_eq!(
            executor.plan().programs.terrain_opaque.identity.as_str(),
            "vulkanic:builtin/terrain_opaque_v1"
        );
    }

    #[test]
    fn source_shadow_depth_resources_are_generation_bound_compare_samplers() {
        let source = complete_bundled_pack_source_for_test();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        let mut gal = gal();
        executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

        let texture = gal
            .create_texture(TextureDesc {
                label: "source-shadow-depth-test.texture".to_string(),
                dimension: TextureDimension::D2,
                format: TextureFormat::Depth32Float,
                extent: Extent3d {
                    width: 16,
                    height: 16,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::DepthStencilAttachment, TextureUsage::Sampled],
            })
            .unwrap();
        let view = gal
            .create_texture_view(TextureViewDesc {
                label: "source-shadow-depth-test.view".to_string(),
                texture,
                format: TextureFormat::Depth32Float,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })
            .unwrap();
        let input = TerrainSourceShadowDepthInput {
            shader_pack_generation: source.generation(),
            world_generation: 4,
            shader_graph_generation: 9,
            shadow_depth_view: view,
        };

        let first = executor
            .ensure_candidate_source_shadow_depth_resources(&mut gal, input)
            .unwrap()
            .expect("source declares two shadow compare samplers");
        let primary = first
            .combined_sampler_for(TerrainSourceResourceRole::ShadowDepthPrimary)
            .unwrap();
        let secondary = first
            .combined_sampler_for(TerrainSourceResourceRole::ShadowDepthSecondary)
            .unwrap();
        assert_ne!(primary, secondary);
        assert_eq!(2, first.len());
        assert_eq!(
            Some(TerrainSourceResourceRole::ShadowDepthPrimary.expected_sampled_resource_shape()),
            first
                .availability()
                .resource_for(TerrainSourceResourceRole::ShadowDepthPrimary)
                .map(|resource| resource.shape)
        );

        let cached = executor
            .ensure_candidate_source_shadow_depth_resources(&mut gal, input)
            .unwrap()
            .unwrap();
        assert_eq!(
            Some(primary),
            cached.combined_sampler_for(TerrainSourceResourceRole::ShadowDepthPrimary)
        );

        let replaced = executor
            .ensure_candidate_source_shadow_depth_resources(
                &mut gal,
                TerrainSourceShadowDepthInput {
                    shader_graph_generation: 10,
                    ..input
                },
            )
            .unwrap()
            .unwrap();
        assert_ne!(
            Some(primary),
            replaced.combined_sampler_for(TerrainSourceResourceRole::ShadowDepthPrimary)
        );
        executor
            .clear_candidate_source_shadow_depth_resources(&mut gal)
            .unwrap();
        gal.destroy(view).unwrap();
        gal.destroy(texture).unwrap();
    }

    #[test]
    fn source_shadow_color_requires_an_owned_color_view_and_retires_generation_bound_wrappers() {
        let source = complete_bundled_pack_source_for_test();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        let mut gal = gal();
        executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

        let texture = gal
            .create_texture(TextureDesc {
                label: "source-shadow-color-test.texture".to_string(),
                dimension: TextureDimension::D2,
                format: TextureFormat::Rgba8Unorm,
                extent: Extent3d {
                    width: 16,
                    height: 16,
                    depth: 1,
                },
                mip_levels: 1,
                array_layers: 1,
                usages: vec![
                    TextureUsage::ColorAttachment,
                    TextureUsage::Sampled,
                    TextureUsage::TransferSrc,
                ],
            })
            .unwrap();
        let view = gal
            .create_texture_view(TextureViewDesc {
                label: "source-shadow-color-test.view".to_string(),
                texture,
                format: TextureFormat::Rgba8Unorm,
                base_mip: 0,
                mip_count: 1,
                base_layer: 0,
                layer_count: 1,
            })
            .unwrap();
        let sampler = gal
            .create_sampler(SamplerDesc {
                label: "source-shadow-color-test.sampler".to_string(),
                min_filter: SamplerFilter::Nearest,
                mag_filter: SamplerFilter::Nearest,
                mip_filter: SamplerFilter::Nearest,
                address_u: SamplerAddressMode::ClampToEdge,
                address_v: SamplerAddressMode::ClampToEdge,
                address_w: SamplerAddressMode::ClampToEdge,
                comparison: None,
            })
            .unwrap();
        let input = TerrainSourceShadowColorInput {
            shader_pack_generation: source.generation(),
            world_generation: 4,
            shader_graph_generation: 9,
            shadow_color_view: view,
            sampler,
        };

        let first = executor
            .ensure_candidate_source_shadow_color_resources(&mut gal, input)
            .unwrap()
            .expect("source declares shadowcolor0 as a color resource");
        let combined = first
            .combined_sampler_for(TerrainSourceResourceRole::ShadowColor)
            .unwrap();
        assert_eq!(1, first.len());
        assert_eq!(
            Some(TerrainSourceResourceRole::ShadowColor.expected_sampled_resource_shape()),
            first
                .availability()
                .resource_for(TerrainSourceResourceRole::ShadowColor)
                .map(|resource| resource.shape)
        );
        assert_eq!(
            Some(combined),
            executor
                .ensure_candidate_source_shadow_color_resources(&mut gal, input)
                .unwrap()
                .unwrap()
                .combined_sampler_for(TerrainSourceResourceRole::ShadowColor)
        );

        let replaced = executor
            .ensure_candidate_source_shadow_color_resources(
                &mut gal,
                TerrainSourceShadowColorInput {
                    shader_graph_generation: 10,
                    ..input
                },
            )
            .unwrap()
            .unwrap();
        assert_ne!(
            Some(combined),
            replaced.combined_sampler_for(TerrainSourceResourceRole::ShadowColor)
        );
        executor
            .clear_candidate_source_shadow_color_resources(&mut gal)
            .unwrap();
        gal.destroy(sampler).unwrap();
        gal.destroy(view).unwrap();
        gal.destroy(texture).unwrap();
    }

    #[test]
    fn copied_source_assets_are_generation_coherent_private_runtime_preparation() {
        let source = complete_bundled_pack_source_for_test();
        let assets = copied_png_assets_for(&source);
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        let mut gal = gal();

        executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);
        assert!(executor
            .ensure_candidate_source_asset_resources(&mut gal, &assets)
            .unwrap());
        assert!(executor
            .candidate_source_asset_resource_count()
            .is_some_and(|count| count > 0));
        assert!(
            executor
                .source_asset_resources
                .as_ref()
                .and_then(|resources| resources.combined_sampler_for("gaux4"))
                .is_some(),
            "shadow-only gaux4 must be retained from the separately lowered shadow plan"
        );
        assert!(!executor
            .ensure_candidate_source_asset_resources(&mut gal, &assets)
            .unwrap());

        let mismatched = ShaderPackAssets::new(ShaderPackAssetUpdate {
            pack_name: "different-pack".to_string(),
            generation: source.generation(),
            files: Vec::new(),
        })
        .unwrap();
        assert!(executor
            .ensure_candidate_source_asset_resources(&mut gal, &mismatched)
            .unwrap_err()
            .to_string()
            .contains("do not match source candidate"));

        executor
            .clear_candidate_source_asset_resources(&mut gal)
            .unwrap();
        assert_eq!(None, executor.candidate_source_asset_resource_count());
        executor.destroy(&mut gal).unwrap();
        assert!(gal.metrics().resource_destroys > 0);
    }

    #[test]
    fn complete_source_distinguishes_active_terrain_resources_from_global_declarations() {
        let source = complete_bundled_pack_source_for_test();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();

        executor.observe_source_candidate_for_scope(&source, TerrainProgramScope::Overworld);

        let TerrainSourceCandidateState::Discovered {
            source_lowered_pair: Some(lowered),
            source_lowering_summary: Some(summary),
            source_uniform_requirement_summary: Some(uniform_summary),
            source_resource_binding_count: Some(8),
            source_resource_binding_error: None,
            ..
        } = executor.source_candidate()
        else {
            panic!("expected complete source to remain a diagnostic-only candidate");
        };
        assert_eq!(
            vec![
                "atlasSize",
                "cameraPosition",
                "cameraPositionFract",
                "darknessLightFactor",
                "far",
                "fogColor",
                "frameCounter",
                "frameTimeCounter",
                "framemod8",
                "gbufferModelView",
                "gbufferModelViewInverse",
                "gbufferProjection",
                "gbufferProjectionInverse",
                "heldBlockLightValue",
                "heldBlockLightValue2",
                "heldItemId",
                "heldItemId2",
                "inBasaltDeltas",
                "inCrimsonForest",
                "inDry",
                "inNetherWastes",
                "inSnowy",
                "inSoulValley",
                "inWarpedForest",
                "isEyeInWater",
                "moonPhase",
                "nightVision",
                "rainFactor",
                "relativeEyePosition",
                "screenBrightness",
                "shadowModelView",
                "shadowModelViewInverse",
                "shadowProjection",
                "shadowProjectionInverse",
                "skyColor",
                "sunAngle",
                "viewHeight",
                "viewWidth",
                "worldDay",
                "worldTime",
            ],
            lowered
                .uniform_contract()
                .fields()
                .iter()
                .map(|field| field.name())
                .collect::<Vec<_>>(),
            "the source-derived environment contract must not be silently narrowed"
        );
        assert_eq!(27, summary.opaque_resource_count);
        assert_eq!(8, summary.active_opaque_resource_count);
        assert_eq!(40, uniform_summary.field_count);
        assert_eq!(40, uniform_summary.resolved_field_count);
        assert!(uniform_summary.unresolved_field_names.is_empty());
        assert_eq!(
            vec![
                "floodfill_sampler",
                "floodfill_sampler_copy",
                "noisetex",
                "shadowcolor0",
                "shadowtex0",
                "shadowtex1",
                "specular",
                "tex",
            ],
            lowered
                .opaque_resource_contract()
                .active_resources()
                .map(|resource| resource.name())
                .collect::<Vec<_>>()
        );
        assert_eq!(
            8,
            executor
                .source_resource_binding_plan()
                .unwrap()
                .bindings()
                .len()
        );
        assert!(matches!(
            executor.source_candidate(),
            TerrainSourceCandidateState::Discovered {
                source_shadow_resource_binding_count: Some(count),
                source_shadow_resource_binding_error: None,
                source_shadow_resource_bindings: Some(_),
                ..
            } if *count > 0
        ));
        assert!(executor
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
            .unwrap()
            .is_some());
    }

    #[test]
    fn declared_source_resources_progress_through_private_candidate_preparation() {
        let source = ShaderPackSource::new(
            "declared-resource-source",
            19,
            vec![
                ShaderSourceFile::new(
                    "program/gbuffers_terrain.glsl",
                    "#version 130\nuniform sampler2D tex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
                ),
                ShaderSourceFile::new(
                    TERRAIN_RESOURCE_BINDINGS_PATH,
                    "tex=material_atlas\n",
                ),
                ShaderSourceFile::new("lib/common.glsl", "#define SHADOW_QUALITY 2\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        executor.observe_source_candidate(&source);

        let candidate = executor.source_candidate();
        assert!(
            matches!(
                candidate,
                TerrainSourceCandidateState::Discovered {
                    source_lowering_summary: Some(_),
                    source_lowering_error: None,
                    source_resource_binding_count: Some(1),
                    source_resource_binding_error: None,
                    ..
                }
            ),
            "{candidate:?}"
        );
        assert!(executor
            .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Opaque, 0)
            .unwrap()
            .is_some());
        let preparation_error = executor
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
            .unwrap_err()
            .to_string();
        assert!(preparation_error.contains("compatibility_fragment_outputs"));
        let bindings = executor.source_resource_binding_plan().unwrap().bindings();
        assert_eq!(1, bindings.len());
        assert_eq!("tex", bindings[0].resource_name());
        assert_eq!(TerrainSourceResourceRole::MaterialAtlas, bindings[0].role());
        assert_eq!(
            crate::render::vulkanic::shader_pack::lowering::TerrainSourceOpaqueResourceKind::CombinedTextureSampler,
            bindings[0].kind()
        );
        assert_eq!(0, bindings[0].binding());
        assert_eq!(
            "vulkanic:builtin/terrain_opaque_v1",
            executor.plan().programs.terrain_opaque.identity.as_str()
        );
    }

    #[test]
    fn fully_lowered_source_is_retained_for_private_preparation_only() {
        let source = ShaderPackSource::new(
            "retained-lowered-source",
            31,
            vec![
                ShaderSourceFile::new(
                    "gbuffers_terrain.vsh",
                    "#version 130\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nuniform sampler2D tex;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform(); }",
                ),
                ShaderSourceFile::new(
                    "gbuffers_terrain.fsh",
                    "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
                ),
                ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
                ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        executor.observe_source_candidate(&source);

        assert!(matches!(
            executor.source_candidate(),
            TerrainSourceCandidateState::Discovered {
                source_uniform_requirement_summary: Some(summary),
                source_uniform_requirement_error: None,
                ..
            } if summary.field_count == 2
                && summary.resolved_field_count == 2
                && summary.unresolved_field_names.is_empty()
        ));

        let prepared = executor
            .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
            .unwrap()
            .expect("complete paired source must be retained privately");
        assert_eq!(
            "vulkanic:shader-pack/retained-lowered-source/terrain_opaque_source_gen31",
            prepared.identity.as_str()
        );
        assert!(prepared.vertex.source.contains("#version 450"));
        assert!(prepared.fragment.source.contains("#version 450"));
        assert_eq!(1, prepared.opaque_resource_bindings.bindings().len());
        assert_eq!(
            crate::render::vulkanic::shader_pack::lowering::TerrainSourceOpaqueResourceKind::CombinedTextureSampler,
            prepared.opaque_resource_bindings.bindings()[0].kind()
        );
        assert_eq!(
            "vulkanic:builtin/terrain_opaque_v1",
            executor.plan().programs.terrain_opaque.identity.as_str(),
            "source preparation must not replace the executable fixture plan"
        );
    }

    #[test]
    fn unresolved_source_scalar_uniforms_block_all_candidate_preparation() {
        let source = ShaderPackSource::new(
            "unresolved-scalar-source",
            32,
            vec![
                ShaderSourceFile::new(
                    "gbuffers_terrain.vsh",
                    "#version 130\nuniform float packSpecificValue;\nout vec2 texCoord;\nout vec4 glColor;\nout float smoothnessD;\nout float materialMask;\nout float skyLightFactor;\nuniform sampler2D tex;\nvoid main() { texCoord = gl_MultiTexCoord0.xy; glColor = vec4(1.0); smoothnessD = 0.0; materialMask = 0.0; skyLightFactor = 1.0; gl_Position = ftransform() + vec4(packSpecificValue); }",
                ),
                ShaderSourceFile::new(
                    "gbuffers_terrain.fsh",
                    "#version 130\nin vec2 texCoord;\nin vec4 glColor;\nin float smoothnessD;\nin float materialMask;\nin float skyLightFactor;\nuniform sampler2D tex;\nvoid DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
                ),
                ShaderSourceFile::new(TERRAIN_RESOURCE_BINDINGS_PATH, "tex=material_atlas\n"),
                ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        executor.observe_source_candidate(&source);

        assert!(matches!(
            executor.source_candidate(),
            TerrainSourceCandidateState::Discovered {
                source_uniform_requirement_summary: Some(summary),
                source_uniform_requirement_error: None,
                ..
            } if summary.field_count == 3
                && summary.resolved_field_count == 2
                && summary.unresolved_field_names == vec!["packSpecificValue".to_string()]
        ));
        for error in [
            executor
                .prepared_lowered_terrain_source_program(TerrainMaterialProgramKind::Opaque)
                .unwrap_err()
                .to_string(),
            executor
                .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Opaque, 0)
                .unwrap_err()
                .to_string(),
        ] {
            assert!(
                error.contains("unresolved scalar source uniforms"),
                "{error}"
            );
            assert!(error.contains("packSpecificValue"), "{error}");
        }
    }

    #[test]
    fn explicit_empty_source_is_observed_as_disabled_not_rejected() {
        let source = ShaderPackSource::new("disabled", 17, Vec::new()).unwrap();
        let mut runtime = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(3).unwrap();

        runtime.observe_source_candidate(&source);

        assert!(matches!(
            runtime.source_candidate(),
            TerrainSourceCandidateState::Disabled { generation: 17, pack_name }
                if pack_name == "disabled"
        ));
        assert_eq!(
            "vulkanic:builtin/terrain_opaque_v1",
            runtime.plan().programs.terrain_opaque.identity.as_str()
        );
        assert_eq!(None, runtime.candidate_shader_binding(0).unwrap());
    }

    #[test]
    fn discovered_source_candidate_with_failed_lowering_cannot_prepare_a_program() {
        let source = ShaderPackSource::new(
            "bounded-source-candidate",
            23,
            vec![
                ShaderSourceFile::new(
                    "program/gbuffers_terrain.glsl",
                    "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
                ),
                ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        executor.observe_source_candidate(&source);

        let error = executor
            .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Opaque, 0)
            .unwrap_err()
            .to_string();
        assert!(error.contains("no complete paired-stage source contract"));
        assert!(error.contains("no GLSL #version directive"));
        assert!(matches!(
            executor.source_candidate(),
            TerrainSourceCandidateState::Discovered {
                source_summary: Some(summary),
                source_preprocess_error: None,
                source_lowering_summary: None,
                source_lowering_error: Some(error),
                ..
            } if summary.vertex_entry == "program/gbuffers_terrain.glsl"
                && summary.fragment_entry == "program/gbuffers_terrain.glsl"
                && error.contains("no GLSL #version directive")
        ));
    }

    #[test]
    fn fragment_only_source_candidate_cannot_prepare_a_selected_program() {
        let source = ShaderPackSource::new(
            "fragment-only",
            24,
            vec![
                ShaderSourceFile::new(
                    "gbuffers_terrain.fsh",
                    "void DoLighting() {}\n/* DRAWBUFFERS:06 */\nvoid main() { vec4 color = texture2D(tex, texCoord); if (color.a <= 0.00001) discard; color.rgb *= glColor.rgb; DoLighting(); gl_FragData[0] = color; gl_FragData[1] = vec4(smoothnessD, materialMask, skyLightFactor, 1.0); }",
                ),
                ShaderSourceFile::new("lib/common.glsl", "#define TEST 1\n"),
                ShaderSourceFile::new("shaders.properties", ""),
                ShaderSourceFile::new("block.properties", ""),
            ],
        )
        .unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        executor.observe_source_candidate(&source);

        assert!(matches!(
            executor.source_candidate(),
            TerrainSourceCandidateState::Discovered {
                source_summary: None,
                source_preprocess_error: Some(error),
                ..
            } if error.contains("missing shader source gbuffers_terrain.vsh")
        ));
        assert!(executor
            .candidate_fixture_terrain_program(TerrainMaterialProgramKind::Opaque, 0)
            .unwrap_err()
            .to_string()
            .contains("no complete paired-stage source contract"));
    }

    #[test]
    fn colored_source_candidate_prepares_owned_volume_identity_from_semantics() {
        let source = bundled_complementary_hung_loified_source(29).unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        executor.observe_source_candidate(&source);

        let preparation = executor
            .candidate_colored_light_preparation(41, 53, [-1.25, 64.5, 7.75])
            .unwrap()
            .expect("Complementary source should prepare ColoredVoxelLighting semantics");

        assert_eq!(29, preparation.descriptor.shader_pack_generation);
        assert_eq!(41, preparation.descriptor.world_generation);
        assert_eq!(53, preparation.descriptor.resource_generation);
        assert_eq!([-2, 64, 7], preparation.descriptor.mapping.camera_cell);
        assert_eq!(
            [0.75, 0.5, 0.75],
            preparation.descriptor.mapping.camera_fraction
        );
        assert_eq!(29, preparation.materials.shader_pack_generation());
        assert_eq!(29, preparation.emission.shader_pack_generation());
        assert!(executor
            .candidate_colored_light_preparation(0, 53, [0.0; 3])
            .is_err());
    }

    #[test]
    fn prepared_colored_light_generation_installs_once_and_reuses_matching_descriptor() {
        let source = bundled_complementary_hung_loified_source(31).unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        executor.observe_source_candidate(&source);
        let preparation = executor
            .candidate_colored_light_preparation(43, 59, [0.25, 64.5, 0.75])
            .unwrap()
            .unwrap();
        let mut gal = gal();

        assert!(executor
            .ensure_candidate_colored_light_runtime(&mut gal, preparation.clone())
            .unwrap());
        assert!(!executor
            .ensure_candidate_colored_light_runtime(&mut gal, preparation)
            .unwrap());
        let fractional = executor
            .candidate_colored_light_preparation(43, 59, [0.75, 64.5, 0.25])
            .unwrap()
            .unwrap();
        assert!(!executor
            .ensure_candidate_colored_light_runtime(&mut gal, fractional)
            .unwrap());
        assert!(executor.has_private_terrain_occupancy());
        assert_eq!(
            31,
            executor
                .private_terrain_occupancy_descriptor()
                .unwrap()
                .shader_pack_generation
        );
    }

    #[test]
    fn incomplete_owned_source_is_rejected_without_changing_fixture_execution() {
        let source = ShaderPackSource::new(
            "incomplete",
            14,
            vec![ShaderSourceFile::new(
                "program/gbuffers_terrain.glsl",
                "void main() {}",
            )],
        )
        .unwrap();
        let mut executor = ShaderPackRuntimeExecutor::terrain_material_multipass_v1(7).unwrap();
        let fixture_program = executor.plan().programs.terrain_opaque.identity.clone();

        executor.observe_source_candidate(&source);

        assert!(matches!(
            executor.source_candidate(),
            TerrainSourceCandidateState::Rejected {
                generation: 14,
                pack_name,
                ..
            } if pack_name == "incomplete"
        ));
        assert_eq!(
            fixture_program,
            executor.plan().programs.terrain_opaque.identity
        );
    }

    #[test]
    fn composite_uniform_block_layout_is_stable() {
        let uniforms = TerrainCompositeUniforms {
            light_view_projection: [1.0; 16],
            shadow_params: [2.0; 4],
            color_grade_params: [3.0; 4],
            fog_params: [4.0; 4],
        };
        assert_eq!(
            TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES as usize,
            uniforms.pack().len()
        );
    }

    #[test]
    fn indexed_draw_emission_skips_redundant_state_binds_inside_pass() {
        let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
        let other_pipeline = test_handle(HandleKind::GraphicsPipeline, 2);
        let layout = test_handle(HandleKind::PipelineLayout, 3);
        let set = test_handle(HandleKind::ResourceSet, 4);
        let other_set = test_handle(HandleKind::ResourceSet, 5);
        let index_buffer = test_handle(HandleKind::Buffer, 6);
        let mut ops = Vec::new();
        let mut state = IndexedDrawState::default();

        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            &[0],
            None,
            index_buffer,
            0,
            IndexType::U32,
            6,
            1,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            &[0],
            None,
            index_buffer,
            0,
            IndexType::U32,
            6,
            2,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            other_set,
            &[0],
            None,
            index_buffer,
            0,
            IndexType::U32,
            6,
            3,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            other_set,
            &[0],
            None,
            index_buffer,
            12,
            IndexType::U32,
            6,
            4,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            other_pipeline,
            layout,
            other_set,
            &[0],
            None,
            index_buffer,
            12,
            IndexType::U32,
            6,
            5,
        );

        let pipeline_binds = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::BindGraphicsPipeline(_)))
            .count();
        let resource_set_binds = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::BindResourceSet { .. }))
            .count();
        let index_binds = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::SetIndexBuffer { .. }))
            .count();
        let draws = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::DrawIndexed { .. }))
            .count();

        assert_eq!(2, pipeline_binds);
        assert_eq!(3, resource_set_binds);
        assert_eq!(2, index_binds);
        assert_eq!(5, draws);
    }

    #[test]
    fn indexed_draw_emission_distinguishes_static_and_dynamic_set_bindings() {
        let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
        let layout = test_handle(HandleKind::PipelineLayout, 2);
        let set = test_handle(HandleKind::ResourceSet, 3);
        let index_buffer = test_handle(HandleKind::Buffer, 4);
        let mut ops = Vec::new();
        let mut state = IndexedDrawState::default();

        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            &[],
            None,
            index_buffer,
            0,
            IndexType::U32,
            6,
            1,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            &[32],
            None,
            index_buffer,
            0,
            IndexType::U32,
            6,
            1,
        );
        append_indexed_draw(
            &mut ops,
            &mut state,
            pipeline,
            layout,
            set,
            &[32],
            None,
            index_buffer,
            0,
            IndexType::U32,
            6,
            1,
        );

        let dynamic_offsets = ops
            .iter()
            .filter_map(|op| match op {
                CommandOp::BindResourceSet {
                    set_index: 0,
                    dynamic_offsets,
                    ..
                } => Some(dynamic_offsets.as_slice()),
                _ => None,
            })
            .collect::<Vec<_>>();
        assert_eq!(vec![&[][..], &[32][..]], dynamic_offsets);
    }

    #[test]
    fn indexed_draw_emission_binds_optional_shader_resources_by_semantic_set() {
        let pipeline = test_handle(HandleKind::GraphicsPipeline, 1);
        let layout = test_handle(HandleKind::PipelineLayout, 2);
        let mesh_set = test_handle(HandleKind::ResourceSet, 3);
        let shader_set = test_handle(HandleKind::ResourceSet, 4);
        let replacement_shader_set = test_handle(HandleKind::ResourceSet, 5);
        let index_buffer = test_handle(HandleKind::Buffer, 6);
        let shader_binding = TerrainShaderResourceSet {
            set_index: 1,
            set: shader_set,
        };
        let mut ops = Vec::new();
        let mut state = IndexedDrawState::default();
        for binding in [
            Some(shader_binding),
            Some(shader_binding),
            None,
            Some(TerrainShaderResourceSet {
                set_index: 1,
                set: replacement_shader_set,
            }),
        ] {
            append_indexed_draw(
                &mut ops,
                &mut state,
                pipeline,
                layout,
                mesh_set,
                &[0],
                binding,
                index_buffer,
                0,
                IndexType::U32,
                6,
                1,
            );
        }
        let shader_binds = ops
            .iter()
            .filter(|op| matches!(op, CommandOp::BindResourceSet { set_index: 1, .. }))
            .count();
        assert_eq!(2, shader_binds);
        assert!(ops.iter().any(|op| {
            matches!(op, CommandOp::BindResourceSet { set_index: 1, set, .. } if *set == replacement_shader_set)
        }));
    }

    fn test_handle(kind: HandleKind, index: u32) -> Handle {
        Handle::new(kind, index, 1).unwrap()
    }
}

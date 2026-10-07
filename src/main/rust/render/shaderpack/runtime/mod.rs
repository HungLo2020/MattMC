//! The shader-pack runtime executor.
//!
//! `ShaderPackRuntimeExecutor` owns a pack generation's runtime state. Its
//! behavior is split by concern: `candidates` (admitting the selected
//! source), `programs` (lowered programs, memoized), `color_targets`,
//! `source_resources`, `lightmap`, `voxels`, `fullscreen_stages` and
//! `fullscreen` (fullscreen passes), and the recording modules `graph`,
//! `source_passes` and `draw`. `frame` holds the per-frame inputs the world
//! renderer supplies.

mod frame;
mod candidates;
mod color_targets;
mod programs;
mod fullscreen_stages;
mod source_resources;
mod voxels;
mod lightmap;
mod graph;
mod source_passes;
mod draw;

pub(crate) use self::frame::*;
pub(crate) use self::candidates::*;
pub(crate) use self::color_targets::*;
use self::programs::*;
pub(crate) use self::fullscreen_stages::*;
pub(crate) use self::source_resources::*;
use self::graph::*;
pub(crate) use self::draw::*;

pub(crate) mod fullscreen;

use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, ClearColor, CommandOp, PassAttachment, ResourceBarrier,
    TextureImageCopyRegion, TextureOrigin3d, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    CombinedTextureSamplerDesc, CompareOp, Extent3d, IndexType, QueueClass, SamplerAddressMode,
    SamplerDesc, SamplerFilter,
};
use smallvec::SmallVec;
use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::path::Path;

use crate::render::scene::strata::WORLD_STRATUM_ENTITY_MESH;
use crate::render::scene::strata::WORLD_STRATUM_TERRAIN;
use crate::render::shaderpack::source::assets::{ShaderPackAssets, TerrainShaderPackAssetBindings};
use crate::render::shaderpack::contracts::cloud::{
    derive_cloud_pass_contract, lower_cloud_source_pair, CloudFaceDisposition, CloudPassContract,
};
use crate::render::shaderpack::contracts::distant_horizons::{
    derive_distant_horizons_opaque_contract, derive_distant_horizons_translucent_contract,
    DistantHorizonsPassContract,
};
use crate::render::shaderpack::contracts::entity::{
    bind_entity_source_resources, derive_entity_contract, lower_entity_source_pair,
    EntityPassContract, LoweredEntitySourcePair,
};
use crate::render::shaderpack::runtime::fullscreen::{FullscreenSourceExecutionPlan, FullscreenSourcePassFrame};
use crate::render::shaderpack::contracts::fullscreen::{
    derive_fullscreen_source_chain, derive_pre_terrain_fullscreen_source_chain, derive_sky_source_stage, derive_sky_textured_source_stage,
    FullscreenSourceStage, FullscreenSourceStageKind,
};
use crate::render::shaderpack::contracts::hand::{
    bind_hand_source_resources, derive_hand_contract, lower_hand_source_pair, HandPassContract,
    LoweredHandSourcePair,
};
use crate::render::shaderpack::contracts::vertex_interface::{analyze_terrain_vertex_interface, TerrainVertexInterface};
use crate::render::shaderpack::vanilla::lightmap::{
    VanillaLightmapBinding, VanillaLightmapCache, VanillaLightmapCacheUpdate, VanillaLightmapFrame,
    VanillaLightmapResidency,
};
use crate::render::shaderpack::lowering::{
    lower_distant_horizons_source_pair, lower_fullscreen_source_pair,
    lower_fullscreen_source_pair_with_raster_primitive,
    lower_shadow_source_pair_with_owned_storage, lower_terrain_source_pair,
    lower_translucent_terrain_source_pair, FullscreenSourceRasterPrimitive, LoweredCloudSourcePair,
    LoweredDistantHorizonsSourcePair, LoweredShadowSourcePair, LoweredTerrainSourcePair,
    LoweredTranslucentTerrainSourcePair, LoweredWeatherSourcePair, ShadowFragmentOutput,
    TerrainSourceLoweringSummary, TerrainSourceOpaqueResourceBindingPlan,
};
use crate::render::shaderpack::contracts::material::{
    derive_textured_material_contract, lower_textured_material_source_pair,
    LoweredTexturedMaterialSourcePair, TexturedMaterialPassContract,
};
use crate::render::shaderpack::plan::pass_graph::{AttachmentRole, PassIdentity};
use crate::render::shaderpack::source::preprocess::{
    preprocess_distant_horizons_fullscreen_stage_pair, preprocess_distant_horizons_sources,
    preprocess_source_stage_pair, preprocess_terrain_sources, PreprocessedTerrainSourceSummary,
};
use crate::render::shaderpack::programs::{
    complementary_terrain_subset_program_with_resources, prepare_lowered_cloud_source_program,
    prepare_lowered_distant_horizons_source_program, prepare_lowered_entity_source_program,
    prepare_lowered_fullscreen_source_program, prepare_lowered_hand_source_program,
    prepare_lowered_shadow_source_program, prepare_lowered_terrain_source_program,
    prepare_lowered_textured_material_source_program,
    prepare_lowered_translucent_terrain_source_program, prepare_lowered_weather_source_program,
    LoweredCloudSourceProgram, LoweredDistantHorizonsSourceProgram, LoweredEntitySourceProgram,
    LoweredFullscreenSourceProgram, LoweredHandSourceProgram, LoweredTerrainSourceProgram,
    LoweredTexturedMaterialSourceProgram, LoweredWeatherSourceProgram, TerrainMaterialProgram,
    TerrainMaterialProgramKind, TerrainProgramResource,
};
use crate::render::shaderpack::plan::ShaderPackRuntimePlan;
use crate::render::shaderpack::source::ShaderPackSource;
use crate::render::shaderpack::resources::assets::TerrainSourceAssetResources;
use crate::render::shaderpack::resources::color_targets::{
    resolve_terrain_source_color_attachments, source_color_clear_color,
    ShaderPackColorClearValues, ShaderPackColorFramePlan,
    ShaderPackColorSamplingPlan, ShaderPackColorTargetCache, ShaderPackColorTargetIdentity,
    ShaderPackColorTargetManifest, ShaderPackColorTargets, ShaderPackSourceColorResourceCache,
    TerrainSourceColorAttachment,
};
use crate::render::shaderpack::uniforms::source::{
    TerrainSourceUniformRequirementSummary, TerrainSourceUniformRequirements,
};
#[cfg(test)]
use crate::render::shaderpack::contracts::terrain::TerrainPassOutput;
use crate::render::shaderpack::contracts::terrain::{
    derive_translucent_terrain_contract_for_scope, shadow_source_stages_for_scope,
    TerrainPassContract, TerrainPassRequiredResource, TerrainProgramScope,
};
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResourceSet, TerrainSourceResourceBindings, TerrainSourceResourceRole,
};
use crate::render::shaderpack::voxels::occupancy::{
    PuddleOccupancyDescriptor, TerrainColoredLightDiagnosticState, TerrainColoredLightRuntime,
    TerrainOccupancyRuntime, TerrainPuddleDiagnosticState, TerrainPuddleRuntime,
    TerrainVoxelLightSamplingBinding,
};
use crate::render::shaderpack::voxels::emission_table::VoxelEmissionTable;
use crate::render::shaderpack::voxels::light_volume::{
    VoxelLightVolumeDescriptor, VoxelLightVolumeIdentity, VoxelLightVolumeMapping,
    VoxelLightVolumeViewDirection,
};
use crate::render::shaderpack::voxels::material_map::VoxelMaterialMap;
use crate::render::shaderpack::contracts::weather::{
    derive_weather_pass_contract, lower_weather_source_pair, WeatherPassContract,
};
use crate::render::scene::voxel_source::TerrainVoxelSourceMesh;

#[derive(Debug)]
pub(crate) struct ShaderPackRuntimeExecutor {
    plan: ShaderPackRuntimePlan,
    source_candidate: TerrainSourceCandidateState,
    /// Advances on every `source_candidate` replacement; prepared program
    /// memos below are valid only for the epoch that built them.
    source_candidate_epoch: u64,
    prepared_program_memos: PreparedSourceProgramMemos,
    /// Semantic roles declared by writer programs outside the terrain/DH
    /// candidate (textured materials, weather, clouds, ...), for the epoch
    /// that prepared them. A dimension whose terrain program samples no
    /// shadow map can still ship a writer that declares one.
    writer_required_roles: std::cell::RefCell<(u64, BTreeSet<TerrainSourceResourceRole>)>,
    /// Required source roles per DH flag, keyed by both candidate epochs and
    /// the noted writer roles (which only grow within an epoch).
    required_roles_memo: std::cell::RefCell<[Option<((u64, u64, usize), std::sync::Arc<BTreeSet<TerrainSourceResourceRole>>)>; 2]>,
    /// Discovery expands a whole pack and lowers several independent source
    /// families. Keep that work generation-and-scope keyed: source discovery
    /// is immutable until either input changes and must not recur on every
    /// render frame while the selected route is preparing its owned resources.
    source_candidate_scope: Option<TerrainProgramScope>,
    distant_horizons_source_candidate: DistantHorizonsSourceCandidateState,
    /// Advances on every `distant_horizons_source_candidate` replacement.
    distant_horizons_source_candidate_epoch: u64,
    /// Compiled fullscreen pipelines reused across frames for these epochs.
    fullscreen_pipeline_cache: crate::render::shaderpack::runtime::fullscreen::FullscreenPipelineCache,
    /// The DH source pair is discovered independently, but has the same
    /// immutable-input rule as ordinary terrain discovery.
    distant_horizons_source_candidate_scope: Option<TerrainProgramScope>,
    /// Generation-coherent copied vanilla lightmap semantics. This owns only
    /// Rust bytes at this stage; the later sampled-image owner will consume
    /// this cache rather than Java's legacy lightmap texture.
    vanilla_lightmap: VanillaLightmapCache,
    /// Last confirmed Rust-owned sampled lightmap. It remains distinct from a
    /// staged replacement until the exact combined submission has succeeded.
    vanilla_lightmap_residency: Option<VanillaLightmapResidency>,
    pending_vanilla_lightmap_residency: Option<VanillaLightmapResidency>,
    /// Whether the pending lightmap's upload is in operations that will be
    /// submitted. A caller that stages into operations it then drops clears
    /// this, so the next consumer records the upload again and an unrecorded
    /// pending lightmap is never promoted to the confirmed residency.
    pending_vanilla_lightmap_upload_recorded: bool,
    /// Replaced lightmaps stay alive until their pass-owned resource sets are
    /// retired.  A confirmed frame can still have consumers in the frontend;
    /// destroying the image view here would violate GAL dependency ordering.
    retired_vanilla_lightmap_residencies: Vec<VanillaLightmapResidency>,
    /// Private preparation state for a future selected-source terrain pass.
    /// It owns Rust D3 resources and copied mesh semantics only; it cannot
    /// select source programs or bind terrain material resources.
    terrain_occupancy: Option<TerrainOccupancyRuntime>,
    /// Full private colored-light preparation. It stays unavailable outside
    /// explicit test installation and cannot select a source terrain program.
    terrain_colored_light: Option<TerrainColoredLightRuntime>,
    /// Private source-derived puddle occupancy field. It is a semantic
    /// unsigned image reconstructed from copied translucent terrain, never an
    /// Iris image or an implicit backend object. Resource preparation alone
    /// cannot admit the selected source route.
    terrain_puddle: Option<TerrainPuddleRuntime>,
    /// Decoded pack PNGs referenced by the lowered source binding plan.
    /// These are private Rust-owned GAL resources only. Creating them cannot
    /// select the source program or alter the internal fixture execution.
    source_asset_resources: Option<TerrainSourceAssetResources>,
    /// Rust-owned semantic wrappers around copied material atlases. The world
    /// frontend supplies only private GAL view/sampler pairs; source-plan
    /// lifetime and combined-sampler retirement stay here. Keeping roles
    /// distinct prevents albedo, specular, and future normal atlases from
    /// aliasing one another merely because their extents happen to match.
    source_material_texture_resources:
        BTreeMap<TerrainSourceResourceRole, TerrainSourceMaterialTextureResources>,
    /// Private semantic wrappers around the Rust-owned shadow-depth target.
    /// They are generation-bound compare samplers, not a source program
    /// binding and not evidence that selected-source execution is admitted.
    source_shadow_depth_resources: Option<TerrainSourceShadowDepthResources>,
    /// Private semantic wrappers around the two Rust-owned shadow color
    /// attachments. They expose only the declared source roles and remain
    /// preparation resources until a matching source shadow pass is admitted.
    source_shadow_color_resources: Option<TerrainSourceShadowColorResources>,
    /// Rust-owned semantic wrappers around the current and confirmed main
    /// depth snapshots. These are preparation resources only; incomplete
    /// history remains absent instead of being silently aliased to live depth.
    source_main_depth_resources: Option<TerrainSourceMainDepthResources>,
    /// Exact named source color targets prepared privately for a later
    /// fullscreen executor. Staging these images cannot select a source
    /// route: confirmation remains tied to an eventual combined submission.
    source_color_targets: ShaderPackColorTargetCache,
    /// Program-local combined samplers for the exact named source-color
    /// target generation. Their pending/confirmed lifecycle is coupled to
    /// `source_color_targets`, so discarded target staging cannot leave a
    /// resource set that points at discarded views.
    source_color_resources: ShaderPackSourceColorResourceCache,
}

impl ShaderPackRuntimeExecutor {
    pub(crate) fn terrain_material_multipass_v1(generation: u64) -> GalResult<Self> {
        // The ordinary Rust-owned graph is self-contained. Selected-source
        // discovery is an explicit later step because parsing and lowering a
        // pack is meaningful only when a real semantic terrain input exists.
        Self::from_fixture_plan(ShaderPackRuntimePlan::terrain_material_multipass_v1(
            generation,
        )?)
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
        Self::from_fixture_plan(plan)
    }

    fn from_fixture_plan(plan: ShaderPackRuntimePlan) -> GalResult<Self> {
        write_contract_diagnostic(&plan);
        Ok(Self {
            plan,
            source_candidate: TerrainSourceCandidateState::Unavailable,
            source_candidate_epoch: 0,
            prepared_program_memos: PreparedSourceProgramMemos::default(),
            writer_required_roles: Default::default(),
            required_roles_memo: Default::default(),
            source_candidate_scope: None,
            distant_horizons_source_candidate: DistantHorizonsSourceCandidateState::Unavailable,
            distant_horizons_source_candidate_epoch: 0,
            fullscreen_pipeline_cache: Default::default(),
            distant_horizons_source_candidate_scope: None,
            vanilla_lightmap: VanillaLightmapCache::default(),
            vanilla_lightmap_residency: None,
            pending_vanilla_lightmap_residency: None,
            pending_vanilla_lightmap_upload_recorded: false,
            retired_vanilla_lightmap_residencies: Vec::new(),
            terrain_occupancy: None,
            terrain_colored_light: None,
            terrain_puddle: None,
            source_asset_resources: None,
            source_material_texture_resources: BTreeMap::new(),
            source_shadow_depth_resources: None,
            source_shadow_color_resources: None,
            source_main_depth_resources: None,
            source_color_targets: ShaderPackColorTargetCache::default(),
            source_color_resources: ShaderPackSourceColorResourceCache::default(),
        })
    }

    pub(crate) fn plan(&self) -> &ShaderPackRuntimePlan {
        &self.plan
    }

    pub(crate) fn generation(&self) -> u64 {
        self.plan.generation
    }

    fn fullscreen_pipeline_epochs(&self) -> (u64, u64) {
        (self.source_candidate_epoch, self.distant_horizons_source_candidate_epoch)
    }

    /// Parked fullscreen plans keep pack-resources sets that bind this
    /// runtime's sampled inputs (voxel volumes, source depth/shadow/color
    /// samplers, source assets). Drop them before any of those inputs is
    /// destroyed or replaced; the GAL refuses to destroy a sampler that a
    /// live set still binds (world unload, reload, resize). Plans staged
    /// before the release are refused when they try to park.
    pub(crate) fn release_parked_fullscreen_plans(&self, gal: &mut VulkanicGal) {
        self.fullscreen_pipeline_cache.destroy_parked(gal);
    }

    pub(crate) fn destroy(mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        self.fullscreen_pipeline_cache.destroy(gal);
        self.discard_vanilla_lightmap_submission(gal);
        if let Some(resources) = self.vanilla_lightmap_residency.take() {
            resources.destroy(gal)?;
        }
        for resources in self.retired_vanilla_lightmap_residencies.drain(..) {
            resources.destroy(gal)?;
        }
        self.vanilla_lightmap.clear();
        if let Some(resources) = self.source_shadow_color_resources.take() {
            resources.destroy(gal)?;
        }
        if let Some(resources) = self.source_main_depth_resources.take() {
            resources.destroy(gal)?;
        }
        if let Some(resources) = self.source_shadow_depth_resources.take() {
            resources.destroy(gal)?;
        }
        self.clear_candidate_source_material_texture_resources(gal)?;
        self.source_color_resources.destroy(gal);
        self.source_color_targets.destroy(gal);
        if let Some(resources) = self.source_asset_resources.take() {
            resources.destroy(gal)?;
        }
        if let Some(occupancy) = self.terrain_occupancy.take() {
            occupancy.destroy(gal)?;
        }
        if let Some(colored_light) = self.terrain_colored_light.take() {
            colored_light.destroy(gal)?;
        }
        if let Some(puddle) = self.terrain_puddle.take() {
            puddle.destroy(gal)?;
        }
        Ok(())
    }

}

#[cfg(test)]
mod tests;

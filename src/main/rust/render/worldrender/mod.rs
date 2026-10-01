//! The world renderer.
//!
//! `WorldPrimitiveFrontend` turns each world frame decoded by the bridge
//! (`frame`, built on `render::scene`) into GAL command lists. Its state lives
//! in this module; its behavior is split by concern:
//!
//! - `submit`: whole-frame and partial submission entry points;
//! - `vanilla`: the built-in route's recording and resources;
//! - `source`: the selected shader-pack route (admission, programs, frames,
//!   plans, resources, submission, receipts), executed with
//!   `render::shaderpack`'s runtime;
//! - `fabulous`, `post_effects`, `lod` (Distant Horizons), `features`;
//! - `assets`, `geometry`, `passes`, `terrain`: assets, GPU geometry, render
//!   passes and the static chunk-terrain boundary;
//! - `diagnostics`, `teardown`, `util`.
//!
//! The renderer uses the GAL's public API only and never names a backend.
//! On the whole-frame route it composes `render::guirender` into its frame and,
//! as the owner of stitched atlases, implements `GuiAtlasOwner` for it.

pub(crate) mod assets;
pub(crate) mod diagnostics;
pub(crate) mod features;
pub(crate) mod frame;
pub(crate) mod geometry;
pub(crate) mod lod;
pub(crate) mod passes;
mod fabulous;
mod source;
mod submit;
mod vanilla;
mod util;
mod post_effects;
mod teardown;

pub use self::source::*;
pub use self::frame::limits::*;
use self::geometry::batching::*;
use self::submit::*;
pub use self::geometry::arenas::*;
pub(crate) use self::vanilla::*;
use self::diagnostics::capture::*;
use self::diagnostics::traces::*;
pub use self::frame::requests::*;
use self::passes::targets::*;
use self::assets::stores::*;
use self::lod::staging::*;
use self::util::*;
#[cfg(test)]
use self::assets::atlas_animation::*;
#[cfg(test)]
use self::vanilla::shaders::*;
pub use self::frame::validation::*;
pub(crate) use self::passes::pipelines::*;

pub mod terrain;
use smallvec::{smallvec, SmallVec};
use std::borrow::Cow;
use std::collections::{BTreeMap, BTreeSet, HashMap};
use std::path::{Path, PathBuf};
use std::sync::{
    atomic::{AtomicBool, AtomicUsize, Ordering},
    Arc, Mutex,
};

use crate::render::scene::background::*;
use crate::render::scene::lod::*;
use crate::render::scene::material::*;
use crate::render::scene::mesh::*;
use crate::render::scene::overlays::*;
use crate::render::scene::strata::*;
use crate::render::scene::textures::*;
use crate::render::scene::voxel_source::{TerrainVoxelSourceMesh, TerrainVoxelSourceVertex};

use crate::render::vulkanic::gal::CompletedHostRead;
use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, ClearColor, CommandOp, PassAttachment, ResourceBarrier,
    SubmissionBatch, TextureOrigin3d, TextureRowOrder, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult, StatusCode};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::guirender::frontend::{
    CustomPostEffectImage, CustomPostEffectSource, GuiAffineQuadRequest, GuiFrontend,
    GuiSpriteRequest, GuiSubmitStats, GuiTiledQuadRequest,
};
use crate::render::guirender::mesh::GuiMeshBatchRequest;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::metrics::elapsed_nanos_u64;
use crate::render::worldrender::diagnostics::profile::WholeFrameProfile;
use crate::render::vulkanic::resources::{
    AccessFlags, BackendFeature, BlendMode, BufferDesc, BufferUsage, ColorFormat, ShaderConventions,
    CombinedTextureSamplerDesc, CompareOp, DepthBias, Extent3d, FrontFace, GraphicsPipelineDesc,
    IndexType, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags, PrimitiveTopology, QueueClass,
    RasterYDirection, RenderPassDesc, RenderTargetDesc, ResourceBinding, ResourceBindingDesc,
    ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc,
    SamplerFilter, ShaderCodeFormat, ShaderModuleDesc, ShaderStage, StencilFaceState, StencilState,
    TextureDesc, TextureDimension, TextureFormat, TextureSubresourceRange, TextureUsage,
    TextureViewDesc,
};
#[cfg(test)]
use crate::render::shaderpack::source::assets::TerrainShaderPackAssetBindings;
use crate::render::shaderpack::source::assets::{ShaderPackAssetStore, ShaderPackAssetUpdate, ShaderPackAssets};
use crate::render::shaderpack::contracts::cloud::CloudBlend;
use crate::render::shaderpack::runtime::fullscreen::{
    FullscreenSourceExecutionPlan, FullscreenSourcePassFrame, SourceFinalOutputCache,
    SourceFinalOutputPlan, SourceFinalOutputReservation, SourceFinalPresentationCapture,
};
use crate::render::shaderpack::properties::item_ids::canonical_resource_location;
use crate::render::shaderpack::vanilla::lightmap::VanillaLightmapBinding;
use crate::render::shaderpack::lowering::TerrainSourceUniformField;
use crate::render::shaderpack::contracts::material::{
    pack_textured_material_source_primitives,
    stage_textured_material_primitive_with_vertex_modulation, TexturedMaterialPositionSpace,
    TexturedMaterialSourcePrimitive, TexturedMaterialSourceVertex,
    TexturedMaterialTextureCoordinates, TexturedMaterialWinding,
};
use crate::render::shaderpack::programs::{
    minimal_compact_direct_terrain_program, minimal_direct_model_translucent_cutout_program,
    minimal_direct_standard_item_foil_program, minimal_direct_terrain_cutout_program,
    minimal_direct_terrain_solid_program, minimal_direct_terrain_translucent_program,
    minimal_direct_world_decal_foil_program, minimal_entity_outline_program,
    minimal_optical_stencil_write_program, minimal_shadow_depth_program,
    minimal_static_compact_direct_terrain_program, minimal_terrain_cutout_program,
    minimal_terrain_solid_program, prepare_lowered_distant_horizons_exact_atlas_source_program,
    shader_stage_code, CompositeProgram, DistantHorizonsMaterialIdentityContract,
    LocalTexturedSourceProgram, LoweredCloudSourceProgram, LoweredDistantHorizonsSourceProgram,
    LoweredEntitySourceProgram, LoweredFullscreenSourceProgram, LoweredHandSourceProgram,
    LoweredTerrainSourceProgram, LoweredTexturedMaterialSourceProgram, LoweredWeatherSourceProgram,
    ProgramIdentity, TerrainMaterialProgram, TerrainMaterialProgramKind,
    TerrainSourceExecutionLayouts, TerrainSourceTextureTransforms,
    COMPACT_DIRECT_TERRAIN_CUTOUT_PROGRAM_ID, COMPACT_DIRECT_TERRAIN_OPAQUE_PROGRAM_ID,
    COMPACT_DIRECT_TERRAIN_TRANSLUCENT_PROGRAM_ID, MINIMAL_ENTITY_OUTLINE_BLIT_FRAGMENT,
    MINIMAL_ENTITY_OUTLINE_BLUR_FRAGMENT, MINIMAL_ENTITY_OUTLINE_FULLSCREEN_VERTEX,
    MINIMAL_ENTITY_OUTLINE_SOBEL_FRAGMENT, STANDARD_ITEM_FOIL_PROGRAM_ID,
    STATIC_COMPACT_DIRECT_TERRAIN_CUTOUT_PROGRAM_ID,
    STATIC_COMPACT_DIRECT_TERRAIN_OPAQUE_PROGRAM_ID,
    STATIC_COMPACT_DIRECT_TERRAIN_TRANSLUCENT_PROGRAM_ID, TERRAIN_SOURCE_INSTANCE_BYTES,
    TERRAIN_SOURCE_VERTEX_BYTES, WORLD_DECAL_FOIL_PROGRAM_ID,
};
use crate::render::shaderpack::runtime::{
    append_indexed_draw, DistantHorizonsTranslucentSourceCandidate, EntitySourceDraw,
    IndexedDrawState, ShaderPackRuntimeExecutor, ShaderPackSourceColorFrameTransaction,
    TerrainCompositeUniforms, TerrainDepthHistoryPlan, TerrainDepthHistoryTargets,
    TerrainForwardMaterialDraw, TerrainIndexedIndirect, TerrainMaterialPassMode, TerrainMeshDraw,
    TerrainRuntimeFrame, TerrainRuntimeTargets, TerrainShaderResourceSet, TerrainShadowDraw,
    TerrainShadowMeshDraw, TerrainShadowParticipation, TerrainSourceColorPassPhase, TerrainSourceColorPassTargets,
    TerrainSourceMainDepthInput, TerrainSourceMaterialTextureInput, TerrainSourceProgramCandidate,
    TerrainSourceShadowColorInput, TerrainSourceShadowDepthInput, TerrainSourceShadowPassTargets,
    TerrainTranslucentCaptureTargets, TexturedMaterialSourceDraw,
    TERRAIN_RUNTIME_COMPOSITE_UNIFORM_BYTES,
};
use crate::render::shaderpack::source::{ShaderPackSourceStore, ShaderPackSourceUpdate};
use crate::render::shaderpack::resources::color_targets::{
    ShaderPackColorBootstrapClearValues, ShaderPackColorTargets,
};
use crate::render::shaderpack::vanilla::post_effect::executor::{
    lower_post_effect_fragment_source, lower_post_effect_vertex_source_for_pass,
    pack_uniform_blocks,
};

use crate::render::shaderpack::uniforms::temporal::{
    TerrainSourceTemporalKey, TerrainSourceTemporalUniforms,
};
use crate::render::shaderpack::uniforms::source::{
    TerrainSourceUniformFrame, TerrainSourceUniformSemantic,
};
use crate::render::shaderpack::contracts::terrain::{TerrainPassOutput, TerrainProgramScope};
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResource, TerrainSourceOwnedResourceSet, TerrainSourceResourceAvailability,
    TerrainSourceResourceAvailabilitySet, TerrainSourceResourceRole,
};
use crate::render::shaderpack::voxels::occupancy::PuddleOccupancyDescriptor;
#[cfg(test)]
use crate::render::shaderpack::voxels::emission_table::VoxelEmissionTable;
use crate::render::shaderpack::voxels::light_volume::{
    invert_column_major_mat4, VoxelLightVolumeDescriptor, VoxelLightVolumeMapping,
    VoxelLightVolumeViewDirection,
};
#[cfg(test)]
use crate::render::shaderpack::voxels::material_map::VoxelMaterialMap;
use crate::render::vulkanic::sync::SubmissionId;
use crate::render::vulkanic::{BufferImageCopyRegion, CommandList, CommandListDesc, CullMode};
use xxhash_rust::xxh32::xxh32;

#[derive(Default)]
pub struct WorldPrimitiveFrontend {
    generation: u64,
    border_asset_generation: u64,
    border_asset_override: Option<WorldBorderTextureAsset>,
    border_asset_payload_bytes: u64,
    border_asset_update_failures: u64,
    crack_asset_generation: u64,
    crack_asset_overrides: BTreeMap<u32, WorldCrackTextureAsset>,
    crack_asset_payload_bytes: u64,
    crack_asset_update_failures: u64,
    material_asset_generation: u64,
    material_asset_overrides: BTreeMap<u32, WorldMaterialTextureAsset>,
    material_asset_payload_bytes: u64,
    material_asset_update_failures: u64,
    mesh_asset_generation: u64,
    mesh_assets: BTreeMap<u64, MeshAssetStore>,
    /// Reuses frame-local mesh batch topology while instance transforms and
    /// colours animate. Entries contain semantic ranges and frame indices
    /// only; they own no GAL handles and are cleared at mesh replacement.
    mesh_batch_plan_cache: Vec<MeshBatchPlanCacheEntry>,
    /// Reusable semantic identity staging for mesh-plan lookup. A settled
    /// terrain frame can contain hundreds of instances, so allocating this
    /// complete identity sequence merely to hit the topology cache defeats a
    /// substantial part of that cache. Entries still own an exact clone on a
    /// miss; steady frames reuse this capacity without retaining GPU state.
    mesh_batch_identity_scratch: Vec<MeshBatchInstanceKey>,
    /// Reuses the material batch topology while per-quad geometry, lighting,
    /// and colors change. The key contains the complete ordered material
    /// identity sequence, so cloud adjacency and every raster/source policy
    /// remain part of the reuse decision. Entries own no GAL handles.
    material_batch_plan_cache: Vec<MaterialBatchPlanCacheEntry>,
    /// Reusable identity staging for the material-plan lookup. The vector is
    /// frame-local semantic scratch only; cache entries clone it only on a
    /// topology miss, so steady-state lookups do not allocate 20k keys.
    material_batch_identity_scratch: Vec<MaterialResourceKey>,
    mesh_texture_assets: BTreeMap<u32, WorldMaterialTextureAsset>,
    /// Bumped whenever `mesh_texture_assets` changes; keys per-asset texture
    /// animation signatures.
    mesh_texture_animation_generation: u64,
    /// Private animation declarations bound to the copied terrain incarnation.
    /// No tick or draw admission until explicit upload transactions consume it.
    staged_atlas_animations: BTreeMap<u32, crate::render::shared::sprite_interpolation::OwnedAtlasAnimationUpdate>,
    /// One latest accepted receipt, retained even after the diagnostic log cap.
    latest_atlas_animation_observations: Vec<String>,
    latest_atlas_animation_texture: Option<u32>,
    /// Per-capture keys for bounded DH audit receipts.  The receipts describe
    /// copied semantic state, so rewriting identical multi-megabyte summaries
    /// every frame only measures diagnostic I/O and vertex scanning.
    last_dh_channel_receipt: Option<(PathBuf, u64)>,
    last_dh_transform_receipt: Option<(PathBuf, u64, String)>,
    last_dh_exact_atlas_status: Option<(PathBuf, u64)>,
    pending_atlas_animation: Option<crate::render::shared::sprite_interpolation::PreparedAtlasTick>,
    pending_atlas_animation_event: Option<crate::render::shared::sprite_interpolation::AtlasAnimationTickEvent>,
    atlas_animation_uploads: assets::animation_upload::UploadQueue,
    /// Cumulative accepted animation patch receipts for the current frontend
    /// incarnation. These are diagnostics only; upload ownership remains in
    /// `UploadQueue` and no draw path reads these counters.
    atlas_animation_patch_uploads: u64,
    atlas_animation_patch_bytes: u64,
    atlas_animation_empty_ticks: u64,
    mesh_asset_payload_bytes: u64,
    mesh_asset_update_failures: u64,
    lod_asset_generation: u64,
    lod_column_assets: BTreeMap<u64, WorldLodColumnAsset>,
    /// Typed, Rust-owned expansion of the copied DH quad stream. This stays
    /// separate from ordinary indexed Minecraft meshes because DH has its own
    /// light/color/material contract and no atlas UVs.
    lod_expanded_column_assets: BTreeMap<u64, lod::WorldLodExpandedColumnAsset>,
    /// Immutable, backend-neutral upload payload derived from the expanded
    /// semantic column. It may acquire private buffers for visible whole-frame
    /// work, but cannot create a pipeline or draw without material admission.
    lod_gpu_column_assets: BTreeMap<u64, lod::WorldLodGpuColumnAsset>,
    /// Immutable per-quad source identities paired with each LOD column
    /// generation. The current color-only route does not bind these as
    /// textures; a future resolver must explicitly admit exact entries.
    lod_material_provenance: BTreeMap<u64, WorldLodColumnMaterialProvenance>,
    /// Exact atlas-UV plans derived from generation-bound LOD provenance.
    /// These remain frontend-only until a complete Rust-owned DH material
    /// contract can consume them; incomplete quads are never guessed.
    lod_textured_column_plans: BTreeMap<u64, lod::WorldLodTexturedColumnPlan>,
    /// Generation-keyed compact DH source geometry for the shared terrain
    /// occupancy preparation. It has no GPU residency, pipeline, or route
    /// policy; cache invalidation follows copied column/source generations.
    lod_voxel_source_meshes: BTreeMap<(u64, u32), DistantHorizonsVoxelSourceCacheEntry>,
    /// Owned exact-atlas vertex/index payloads for resolved quads in each DH
    /// column. Partial source segments retain a complementary reduced-color
    /// index range, so the two material paths never overlap.
    lod_textured_gpu_column_assets: BTreeMap<u64, lod::WorldLodTexturedGpuColumnAsset>,
    /// Private immutable GPU residency for resolved exact-atlas DH quads and
    /// the complementary reduced-color index ranges of partial segments.
    lod_textured_gpu_residency: lod::WorldLodTexturedGpuResidency,
    /// Private GAL residency for the immutable upload payload. It owns only
    /// buffers and transfer transactions; draw/pipeline admission remains a
    /// later LOD material-pass decision.
    lod_gpu_residency: lod::WorldLodGpuResidency,
    /// Reusable frame-local DH classification storage. It retains only bounded
    /// vector capacity; draw uniforms and ordering are rebuilt every frame.
    lod_frame_plan_scratch: lod::WorldLodFramePlan,
    /// Reusable resolved DH geometry metadata for the ordinary frame planner.
    /// Residency retains its own validated identity cache; this vector only
    /// avoids allocating a second owned copy every frame while other frontend
    /// state is mutably staged.
    lod_visible_draw_scratch: Vec<lod::WorldLodGpuDraw>,
    /// Generation-keyed private resources for the first Rust-owned DH opaque
    /// pass. The route remains unselected until all required material and
    /// shadow semantics are available, but its cache follows the same asset
    /// retirement boundary as geometry residency.
    lod_opaque_pass_resources: lod::WorldLodOpaquePassResources,
    lod_forward_opaque_pass_resources: lod::WorldLodForwardOpaquePassResources,
    /// Separate exact-atlas opaque route. It draws only provenance-resolved
    /// quads; partial source segments retain their unresolved quads through
    /// the complementary reduced-color path exactly once.
    lod_exact_atlas_opaque_pass_resources: lod::WorldLodExactAtlasOpaquePassResources,
    lod_exact_atlas_forward_opaque_pass_resources:
        Option<lod::WorldLodExactAtlasOpaquePassResources>,
    /// Direct-route exact-atlas owners for complete transparent/water
    /// segments. Each owner has the source layer's blend/cull/depth state;
    /// partial alpha segments remain a single reduced-color draw so source
    /// ordering is never changed by splitting one segment.
    lod_exact_atlas_forward_transparent_side_pass_resources:
        Option<lod::WorldLodExactAtlasPassResources>,
    lod_exact_atlas_forward_transparent_up_pass_resources:
        Option<lod::WorldLodExactAtlasPassResources>,
    lod_exact_atlas_forward_water_pass_resources: Option<lod::WorldLodExactAtlasPassResources>,
    /// Deferred exact-atlas owners for the translucent attachment. These are
    /// separate from the direct-presentation owners because the acquired
    /// target may be BGRA while the deferred graph's semantic color target is
    /// RGBA; a pipeline's attachment format is immutable for its lifetime.
    lod_exact_atlas_deferred_transparent_side_pass_resources:
        Option<lod::WorldLodExactAtlasPassResources>,
    lod_exact_atlas_deferred_transparent_up_pass_resources:
        Option<lod::WorldLodExactAtlasPassResources>,
    lod_exact_atlas_deferred_water_pass_resources: Option<lod::WorldLodExactAtlasPassResources>,
    /// Exact-atlas writer for the selected DH source transaction. It owns no
    /// pack program or route decision: it only supplies provenance-resolved
    /// block texture ranges to the named Rust-owned primary target.
    lod_exact_atlas_source_pass_resources: lod::WorldLodExactAtlasSourcePassResources,
    /// Separate Rust-owned alpha pass for visible non-water DH segments. Its
    /// resources share only the semantic column/lightmap inputs with opaque
    /// DH; blend, depth-write, and execution order remain explicit.
    lod_transparent_pass_resources: lod::WorldLodTransparentPassResources,
    /// Rust-owned water-surface pass. DH's water stream has distinct cull,
    /// depth, and blend semantics, so it never silently shares general
    /// transparent state.
    lod_water_pass_resources: lod::WorldLodWaterPassResources,
    /// Private ordinary-route DH color/depth target used when copied DH fog
    /// is enabled. It is kept outside the vanilla terrain depth domain and
    /// outside shader-pack named targets.
    lod_direct_composition_resources: Option<lod::WorldLodDirectCompositionResources>,
    /// Private source-derived DH column pipeline/data owner. It cannot select
    /// a route by itself: pack-resource sets, named targets, consumers, and
    /// the combined submission transaction remain separately explicit.
    lod_source_pass_resources: lod::WorldLodSourcePassResources,
    /// Current DH generic-box geometry for the selected-source DH pass.
    dh_generic_source_buffers: Option<DistantHorizonsGenericSourceBuffers>,
    dh_generic_source_generation: u64,
    /// Private two-phase ownership for the distinct DH depth stream used by
    /// a future source-derived shader-pack pass. This is deliberately kept
    /// apart from the ordinary terrain G-buffer depth attachments: DH source
    /// writes the pack's named color target but later source stages consume a
    /// separately sampled distant-depth stream.
    lod_source_targets: lod::WorldLodSourceTargetCache,
    pending_distant_horizons_source_targets: Option<DistantHorizonsSourceTargetSubmission>,
    /// A depth-only far-stream initialization staged by normal Rust gameplay
    /// while selected-source admission is explicitly requested. This is kept
    /// separate from the later DH color/depth transaction: an empty far
    /// horizon still has a real cleared depth stream, but it owns no color
    /// target, shader draw, or presenter.
    pending_candidate_source_distant_depth: Option<DistantHorizonsSourceTargetSubmission>,
    source_terrain_color_pass_targets:
        BTreeMap<SourceTerrainColorPassTargetKey, SourceTerrainColorPassTargetResources>,
    /// Rust-owned first-person depth attachments. These are deliberately not
    /// frame-target or terrain-depth aliases: the eventual hand writer clears
    /// one before use while it loads the completed named world colors.
    hand_source_depth_targets: BTreeMap<HandSourceDepthKey, HandSourceDepthResources>,
    /// Per-generation entity-stream shadow caster program (pack `shadow`).
    entity_shadow_program_cache: Option<(u64, LoweredEntitySourceProgram)>,
    /// Per-generation/scope block-selection line program (pack `gbuffers_line`).
    line_source_program_cache: Option<((u64, TerrainProgramScope), LoweredTexturedMaterialSourceProgram)>,
    damaged_block_source_program_cache:
        Option<((u64, TerrainProgramScope), LoweredTexturedMaterialSourceProgram)>,
    entity_glint_source_program_cache: Option<((u64, TerrainProgramScope), LoweredEntitySourceProgram)>,
    hand_glint_source_program_cache: Option<((u64, TerrainProgramScope), LoweredHandSourceProgram)>,
    /// Receipt evidence for the last prepared `gbuffers_line` writer:
    /// (frame, program identity, segments, opaque draws, translucent draws).
    last_source_line_execution: Option<(u64, String, usize, usize, usize)>,
    /// Converted source terrain meshes memoized for one named plan
    /// preparation only. Colour and shadow writers each resolve every batch
    /// mesh twice; the memo is cleared when that preparation returns so it
    /// never retains a second CPU copy of resident terrain across frames.
    source_terrain_mesh_frame_memo:
        Option<std::collections::HashMap<(u64, u64, bool), Arc<SourceTerrainMeshAsset>>>,
    /// Mesh identities whose source terrain conversion has already succeeded.
    /// Mesh generations are immutable content, so frame coverage validation
    /// need not re-derive the (large, deliberately uncached) source stream
    /// every frame. Bounded by clearing when it exceeds the frame bound.
    validated_source_terrain_meshes: std::collections::HashSet<(u64, u64, bool)>,
    /// Terrain mesh identities (stratum, key, generation) already proven
    /// admissible for the current pack/material stamp, with whether each has
    /// translucent sections. Streaming changes the visible set every frame;
    /// only newly seen identities are validated.
    source_terrain_validated_identities: HashMap<(u32, u64, u64), bool>,
    source_terrain_validated_stamp: Option<(bool, Option<u64>)>,
    /// (source generation, scope) -> whether DH joins the pack's shadow pass.
    distant_horizons_shadow_pass_memo: std::cell::Cell<Option<((u64, TerrainProgramScope), bool)>>,
    /// `source_uniform_frame_for_owned_resources` result for one frame id:
    /// every input is fixed within a frame, and entity groups, terrain passes
    /// and fullscreen stages each asked for it again (~1k times per frame).
    source_uniform_frame_memo: Option<(SourceUniformFrameMemoKey, TerrainSourceUniformFrame)>,
    /// Per-frame memo of `source_resources_for_local_material`: every entity
    /// draw rebuilt the same merged availability/resource sets per texture.
    local_material_resource_memo: Option<(u64, Vec<LocalMaterialMemoGroup>)>,
    /// Last voxel source mesh list, keyed by the exact terrain instances
    /// (key, generation, world transform bits) and cull box it was built for.
    terrain_voxel_source_memo: Option<TerrainVoxelSourceMemo>,
    /// Converted source terrain streams reused across frames. Streaming a
    /// render distance of sections would make an unbounded cache grow without
    /// limit, so entries are evicted least-recently-used above a byte budget.
    source_terrain_mesh_cache: SourceTerrainMeshCache,
    source_entity_mesh_cache: SourceEntityMeshCache,
    /// Consecutive whole-frame submissions entered with the shader route
    /// armed; bounds the post-arming retry window.
    armed_submission_streak: u32,
    /// Per-frame packed uniform bytes, reused for equal uniform inputs.
    source_uniform_pack_memo: Option<(u64, Vec<SourceUniformPackMemoEntry>)>,
    /// Active only while one frame's terrain batch loops run; see
    /// `SourceTerrainBatchScope`.
    source_terrain_batch_scope: Option<SourceTerrainBatchScope>,
    /// Source geometry staging buffers whose upload submission was confirmed;
    /// destroyed (completion-deferred by GAL) at the next source frame.
    retired_source_geometry_staging: Vec<Handle>,
    /// Frame whose selected-source coverage the armed entry just validated.
    coverage_validated_frame_id: Option<u64>,
    /// Persistent semantic final-copy bindings for the private source frame.
    /// Entries are keyed by world/pack/source/swapchain-slot compatibility,
    /// never by native or transient target handles.
    source_final_output_cache: SourceFinalOutputCache,
    lod_asset_update_failures: u64,
    // These pipelines target both the ordinary acquired frame target and the
    // source graph's owned final-color intermediate. Keep the variants keyed
    // by the explicit GAL color format so recording one route never retires a
    // compatible pipeline needed by the other.
    resources: BTreeMap<(ColorFormat, RasterYDirection), WorldLineResources>,
    oriented_world_target: Option<passes::oriented_target::OrientedWorldTarget>,
    canonical_world_target: Option<passes::oriented_target::OrientedWorldTarget>,
    /// Frozen's vanilla Overworld sky disc, recreated from copied semantic
    /// colour/camera data through a Rust-owned explicit GAL pass.
    sky_disc_resources: BTreeMap<(ColorFormat, RasterYDirection), WorldSkyDiscResources>,
    /// Direct vanilla uses a single acquired color attachment; source shader
    /// execution retains its separate four-target G-buffer sky pipeline.
    sky_disc_forward_resources: BTreeMap<(ColorFormat, RasterYDirection), WorldSkyDiscResources>,
    /// Persistent Rust-owned outline mask/intermediate targets. This cache is
    /// created only by an admitted semantic outline frame transaction; no Java
    /// target or post-chain object participates.
    entity_outline_targets: Option<features::outline::EntityOutlineTargetResources>,
    entity_outline_mask_gpu: Option<features::outline::EntityOutlineMaskGpuResources>,
    entity_outline_post_effect_pipelines: Option<features::outline::EntityOutlinePostEffectPipelines>,
    entity_outline_post_effect_sets: Option<features::outline::EntityOutlinePostEffectResourceSets>,
    entity_outline_targets_initialized: bool,
    pending_entity_outline_targets_written: bool,
    crack_resources: BTreeMap<(ColorFormat, RasterYDirection), CrackResources>,
    border_resources: BTreeMap<(ColorFormat, RasterYDirection), BorderResources>,
    material_resources: BTreeMap<MaterialResourceKey, MaterialResources>,
    mesh_pipeline_resources: BTreeMap<MeshPipelineResourceKey, MeshPipelineResources>,
    /// ABI-only set-one layout for the normal Rust terrain material.  The
    /// actual descriptor set belongs to the current lightmap residency so a
    /// replacement always retires consumers before its sampled image view.
    builtin_terrain_lightmap_layout: Option<Handle>,
    /// One immutable upload per semantic mesh/index generation. Per-section
    /// resources below reference explicit ranges in shared frontend-owned
    /// buffers instead of owning native buffer objects.
    mesh_geometry_resources: BTreeMap<MeshGeometryResourceKey, MeshGeometryResources>,
    mesh_geometry_arena: MeshGeometryArena,
    // Mesh section bindings are looked up for every visible instance batch;
    // retain insertion order nowhere in this cache, so hash lookup avoids a
    // logarithmic tree walk during streamed-terrain preparation.
    mesh_resources: HashMap<MeshResourceKey, MeshResources>,
    /// Shared page-wide bindings used only by the builtin indexed route. The
    /// legacy per-section sets remain available to source, foil, layered and
    /// diagnostic paths whose addressing contracts differ.
    mesh_page_resource_sets: HashMap<MeshPageResourceSetKey, Handle>,
    source_mesh_resources: BTreeMap<SourceMeshResourceKey, SourceMeshResources>,
    /// Replaced streamed resources remain live until the next explicit frame
    /// submission has validated all semantic batches that may still reference
    /// the previous generation.  Destroying them during asset replacement
    /// invalidates those queued handles before GAL can mark them in flight.
    deferred_mesh_resource_destroys: Vec<Handle>,
    /// Stream buffers superseded while a whole-frame command transaction is
    /// still being assembled. Their descriptor sets retire first, after the
    /// complete submission has validated and claimed every recorded handle.
    deferred_mesh_stream_buffer_destroys: Vec<Handle>,
    /// Immutable geometry ranges retire with their descriptor sets and are
    /// reusable only after GAL reports the associated submission complete.
    deferred_mesh_geometry_range_releases: Vec<MeshGeometryResources>,
    lowered_source_terrain_geometry_resources:
        BTreeMap<LoweredSourceTerrainDataKey, LoweredSourceTerrainGeometryResources>,
    source_terrain_geometry_pages: SourceTerrainGeometryPages,
    /// Special-foil glint meshes whose UVs are the SheetedDecal projection of
    /// one instance pose, and the keys used by the frame being prepared.
    source_decal_glint_meshes: std::collections::HashMap<u64, Arc<SourceEntityMeshAsset>>,
    source_decal_glint_used: std::collections::HashSet<u64>,
    source_terrain_range_memo: std::collections::HashMap<SourceTerrainRangeKey, SourceTerrainRangeSelection>,
    /// Frame whose terrain draws may use shared-page multi-draw.
    source_terrain_multidraw_frame: Option<u64>,
    lowered_source_terrain_frame_data_resources:
        BTreeMap<LoweredSourceTerrainFrameDataKey, LoweredSourceTerrainFrameDataResources>,
    lowered_source_terrain_pack_resources:
        BTreeMap<LoweredSourceTerrainPackKey, LoweredSourceTerrainPackResources>,
    lowered_source_terrain_program_layouts:
        BTreeMap<LoweredSourceTerrainProgramKey, LoweredSourceTerrainProgramLayouts>,
    lowered_source_terrain_pipeline_resources:
        BTreeMap<LoweredSourceTerrainPipelineKey, LoweredSourceTerrainPipelineResources>,
    /// The selected `gbuffers_textured` writer owns a separate compact quad
    /// stream and therefore cannot reuse terrain's set-zero resources. Its
    /// pack-resource cache still uses the same semantic generations.
    lowered_textured_material_source_frame_data_resources: BTreeMap<
        LoweredTexturedMaterialSourceFrameDataKey,
        LoweredTexturedMaterialSourceFrameDataResources,
    >,
    lowered_textured_material_source_pack_resources:
        BTreeMap<LoweredTexturedMaterialSourcePackKey, LoweredTexturedMaterialSourcePackResources>,
    lowered_entity_source_pack_resources:
        BTreeMap<LoweredEntitySourcePackKey, LoweredEntitySourcePackResources>,
    lowered_textured_material_source_program_layouts:
        BTreeMap<LoweredSourceTerrainProgramKey, LoweredTexturedMaterialSourceProgramLayouts>,
    lowered_textured_material_source_pipeline_resources: BTreeMap<
        LoweredTexturedMaterialSourcePipelineKey,
        LoweredTexturedMaterialSourcePipelineResources,
    >,
    lowered_entity_source_pipeline_resources:
        BTreeMap<LoweredEntitySourcePipelineKey, LoweredEntitySourcePipelineResources>,
    mesh_texture_resources: BTreeMap<u32, MeshTextureResources>,
    /// Upload commands discovered while preparing one Vulkan frame. Keeping
    /// compatible world-resource transfers in one explicit submission avoids
    /// a queue-submit per newly visible mesh/texture without changing the
    /// resource or usage declarations consumed by the frame graph.
    pending_world_upload_ops: Vec<CommandOp>,
    defer_world_uploads: bool,
    /// Standalone source-material textures are intentionally separate from
    /// terrain atlas assets and normal-route material pipelines. They are
    /// owned/cached by Rust and only become source-program bindings through
    /// the semantic local-texture map below.
    source_material_texture_resources: BTreeMap<u32, MeshTextureResources>,
    /// A local source texture is bindable only after the upload-containing
    /// source transaction has been submitted. This prevents a rejected
    /// private candidate from reusing an image that is still `UNDEFINED`.
    source_material_texture_resident: BTreeSet<u32>,
    source_material_texture_upload_confirmed: BTreeSet<u32>,
    /// Textures staged into the current private source frame plan but not yet
    /// confirmed by its combined GAL submission.
    source_material_texture_staged: BTreeSet<u32>,
    /// Canonical first-use upload operations retained until the owning source
    /// submission is confirmed. This lets the final frame assembly detect a
    /// dropped transaction segment without borrowing backend state.
    source_material_texture_upload_operations: BTreeMap<u32, Vec<CommandOp>>,
    lowered_textured_material_source_local_texture_resources: BTreeMap<
        LoweredTexturedMaterialSourceLocalTextureKey,
        LoweredTexturedMaterialSourceLocalTextureResources,
    >,
    mesh_instance_stream_slots: Vec<MeshInstanceStreamSlot>,
    mesh_indirect_stream: Option<MeshIndirectStreamSlot>,
    mesh_sorted_index_stream: Option<MeshIndirectStreamSlot>,
    world_text: features::world_text::WorldTextFrontend,
    source_terrain_frame_stream_slots: Vec<SourceTerrainFrameStreamSlot>,
    /// Asset-generation uploads discovered while preparing a private lowered
    /// source frame. They are drained into that frame's single combined
    /// submission rather than issuing an implicit upload submission.
    pending_lowered_source_terrain_geometry_uploads:
        BTreeMap<LoweredSourceTerrainDataKey, Vec<CommandOp>>,
    /// First-use local material uploads stay coupled to the source frame that
    /// consumes them. This avoids an implicit asset submission between source
    /// plan preparation and the one owned whole-frame submission.
    pending_source_material_texture_uploads: BTreeMap<u64, Vec<PendingSourceMaterialTextureUpload>>,
    pending_source_terrain_frame_transactions: BTreeMap<u64, SourceTerrainFrameTransaction>,
    /// Present only while a test-only lowered source frame is being assembled
    /// into `submit_whole_frame`. The token is confirmed only after that one
    /// combined GAL submission succeeds; production never selects this path.
    pending_lowered_source_terrain_submission: Option<SourceTerrainFrameSubmission>,
    /// Shader-pack resources belong to the runtime. This frontend supplies
    /// only copied semantic world data and render work.
    shader_runtime: Option<ShaderPackRuntimeExecutor>,
    /// Complete source generations are owned and validated by Rust. Loading
    /// them alone cannot select source-derived shader execution.
    shader_pack_sources: ShaderPackSourceStore,
    /// Immutable binary assets paired with a shader-pack source generation.
    /// They are not GPU objects and do not make selected-source execution
    /// available until every semantic resource is resolved by Rust.
    shader_pack_assets: ShaderPackAssetStore,
    /// Temporal scalar semantics belong to the Rust-owned source contract,
    /// never to Java/Iris state or a backend upload path.
    source_temporal_uniforms: TerrainSourceTemporalUniforms,
    /// Private test-only selector for the built-in candidate terrain subset.
    /// This must never be mistaken for execution of lowered shader-pack
    /// source, which still lacks its source vertex and semantic resource
    /// interface.
    #[cfg(test)]
    candidate_subset_execution_enabled: bool,
    /// Test-only admission switch for a complete lowered selected-source
    /// terrain pair. This is deliberately separate from the internal fixture
    /// selector so tests cannot accidentally mix the two shader contracts.
    #[cfg(test)]
    candidate_lowered_source_execution_enabled: bool,
    /// Test-only preparation switch. This exercises the unadmitted source
    /// resource lifecycle without selecting either source draw route.
    #[cfg(test)]
    candidate_source_preparation_enabled: bool,
    /// True only when the current runtime was installed from the discovered
    /// source candidate. This lets source/world changes retire preparation
    /// data without affecting diagnostic runtimes installed by focused tests.
    candidate_colored_light_runtime: bool,
    /// Rust-owned PNG resource preparation for a discovered source candidate.
    /// This remains diagnostic/private until every required source resource
    /// and vertex interface has been admitted for execution.
    candidate_source_asset_runtime: bool,
    /// Bounded reason why copied source assets could not be prepared. This is
    /// intentionally separate from normal world rendering so a partial pack
    /// cannot break the internal fixture route or masquerade as admitted.
    candidate_source_asset_error: Option<String>,
    /// Count of generation-coherent source-resource roles prepared privately
    /// for diagnostics. It is not admission evidence by itself.
    candidate_source_resource_role_count: usize,
    /// Active lowered semantic roles still absent from the private Rust-owned
    /// source-resource assembly. A non-empty list blocks source selection.
    candidate_source_missing_resource_roles: Vec<TerrainSourceResourceRole>,
    /// Bounded explanation of the latest attempt to stage the distinct DH
    /// opaque-depth stream for selected-source admission. This is diagnostic
    /// correlation only: it never makes the route eligible by itself.
    candidate_source_distant_depth_admission: Option<String>,
    /// Most recent exact-frame assembly of the private source resource roles.
    /// This is deliberately separate from cached assets: voxel ping-pong
    /// parity and shadow resources are frame-local semantic inputs.
    candidate_source_resource_snapshot: Option<CandidateSourceResourceSnapshot>,
    /// Final-target correlation for the complete source snapshot. It remains
    /// absent until the Rust-owned G-buffer has created the exact output
    /// binding for the same frame.
    candidate_source_frame_target_snapshot: Option<CandidateSourceFrameTargetSnapshot>,
    /// The last complete semantic terrain input observed for source-volume
    /// preparation. This is separate from the confirmed D3 snapshot: it is
    /// only a bounded debounce while sections are streaming into a world.
    candidate_source_occupancy_input_identity: Option<CandidateSourceOccupancyInputIdentity>,
    candidate_source_occupancy_stable_frames: u8,
    /// A Rust-owned source route may only activate after a normal Rust
    /// whole-frame submission has confirmed a coherent source snapshot. This
    /// keeps the transition entirely inside Rust: Java never supplies a
    /// program, target, or fallback draw.
    source_execution_armed: bool,
    /// The first failed predicate from the most recent source-route admission
    /// decision. This is bounded audit state only; it cannot select a route or
    /// alter source-resource preparation.
    source_execution_admission_reason: Option<String>,
    /// Last shader-route outcome printed for the user (see
    /// `report_shader_route_outcome`); printed again only when it changes.
    last_reported_shader_route_outcome: Option<String>,
    /// The most recent admission decision (reason), retained for reporting.
    last_admission_decision: Option<String>,
    /// Viewport of the previous whole-frame submission. Source-route
    /// resources (G-buffer, depth history, far-depth snapshot) are bound to
    /// one extent, so a resize disarms the route until they are re-confirmed.
    last_source_route_extent: Option<(u32, u32)>,
    /// Vanilla cloud quads dropped at armed-frame entry because the selected
    /// pack suppresses them; folded into this frame's cloud evidence.
    pre_dropped_suppressed_cloud_quads: u64,
    /// The source route is recorded once for bounded audit evidence after it
    /// actually replaces the internal graph. The record is not an ABI field
    /// and cannot be mistaken for Java-side route selection.
    source_execution_activation_reported: bool,
    /// Retains one additional audit record only when a successful selected
    /// source submission actually included semantic Distant Horizons work.
    /// A generic early terrain frame must never stand in for DH execution.
    source_execution_distant_horizons_reported: bool,
    depth_attachment: Option<DepthAttachmentResources>,
    g_buffer_resources: Option<GBufferResources>,
    /// Private six-target Fabulous attachment graph. It is prepared only when
    /// translucent semantics are present, and remains unadmitted until the
    /// corresponding family routing and post-effect lowering are complete.
    fabulous_attachment_set: Option<crate::render::shaderpack::vanilla::fabulous::FabulousAttachmentSet>,
    /// Whether the persistent Fabulous attachments have been written by a
    /// successful material submission.
    fabulous_attachment_set_initialized: bool,
    pending_graph_targets_written: bool,
    pending_translucent_capture_written: bool,
    lod_direct_composition_initialized: bool,
    pending_lod_direct_composition_written: bool,
    lod_ssao_initialized: bool,
    pending_lod_ssao_written: bool,
    lod_vanilla_snapshot_initialized: bool,
    pending_lod_vanilla_snapshot_written: bool,
    pending_terrain_fabulous_handoff: bool,
    pending_terrain_external_item_entity_written: bool,
    g_buffer_depth_history: GBufferDepthHistoryState,
    pending_g_buffer_depth_history_submission: Option<GBufferDepthHistorySubmission>,
    g_buffer_final_bindings: BTreeMap<GBufferFinalBindingKey, GBufferFinalBindingResources>,
    pending_g_buffer_resources_retired: u64,
    cached_passes: Vec<CachedPass>,
    cached_color_only_passes: Vec<CachedColorOnlyPass>,
    pending_depth_attachment_retires: u64,
}

impl WorldPrimitiveFrontend {

    /// Keep native command recording and validation bounded even when a
    /// single semantic frame contains thousands of complete render passes.
    /// Passes remain atomic; this is only a command-list packing limit.
    const MAX_COMMAND_OPS_PER_LIST: usize = 16 * 1024;

}

#[cfg(test)]
mod tests;

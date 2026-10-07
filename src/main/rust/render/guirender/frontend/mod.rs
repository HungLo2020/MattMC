//! The GUI frontend: turns ordered GUI requests into GAL command operations.
//!
//! A frame's sprite, affine, tiled and mesh requests are validated
//! (`requests`, `ordering`), merged into one stratum-ordered stream and
//! recorded (`recording`) into either a standalone submission or a target
//! the world renderer supplies (`submit`). Draws are batched per texture
//! group; each group owns its texture, uniforms and binding set
//! (`resources`) over shared pipelines (`pipelines`).
//!
//! - `limits`: resource bounds, strata and post-effect identifiers.
//! - `sprites`, `sprite_table`: bundled sprites and the packed sprite atlas.
//! - `assets`: sprite overrides and raw images from asset updates.
//! - `atlas`: quads that sample world-owned atlases via `GuiAtlasOwner`.
//! - `item_rasters`: offscreen item renders composited as GUI quads.
//! - `mesh_items`: 3D GUI meshes, their geometry and composite batches.
//! - `post_effects`: shader-pack chains, invert, creeper, spider and blur.
//! - `commands`, `shaders`: command-stream helpers and GLSL (`glsl/`).

mod limits;
mod shaders;
mod sprites;
mod sprite_table;
mod requests;
mod ordering;
mod resources;
mod pipelines;
mod assets;
mod atlas;
mod item_rasters;
mod submit;
mod recording;
mod commands;
mod mesh_items;
mod post_effects;

pub use self::limits::*;
use self::shaders::*;
use self::sprites::*;
use self::sprite_table::*;
pub use self::requests::*;
pub(crate) use self::ordering::*;
use self::resources::*;
use self::pipelines::*;
use self::assets::*;
use self::atlas::*;
pub(crate) use self::item_rasters::*;
use self::recording::*;
use self::commands::*;
use self::mesh_items::*;
pub(crate) use self::post_effects::*;

use std::collections::{BTreeMap, BTreeSet};
use std::io::BufReader;

use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, CommandList, CommandOp, PassAttachment, ResourceBarrier,
    SubmissionBatch, TextureImageCopyRegion, TextureOrigin3d, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult, StatusCode};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::guirender::atlas_reference::{
    AcceptedAtlasIncarnation, GuiAtlasOwner, GuiAtlasReference, GuiAtlasReferences,
};
#[cfg(test)]
use crate::render::guirender::mesh::prepare_draws as prepare_gui_mesh_draws;
use crate::render::guirender::mesh::{
    geometry_fingerprint as gui_mesh_geometry_fingerprint,
    resolved_item_raster as resolve_gui_mesh_item_raster, GuiMeshBatchRequest,
    GuiMeshCompositeResources, GuiMeshLightingMode, GuiMeshMaterialMode,
    GuiMeshOffscreenTargetCache, GuiMeshPassResources, GuiMeshPreparedDraw, GuiMeshSharedProgram,
    GuiMeshStreamRange, GUI_MESH_COMPOSITE_UNIFORM_STRIDE,
};
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    AccessFlags, BlendMode, GlslDialect, BufferDesc, BufferUsage, ColorFormat,
    CombinedTextureSamplerDesc, CompareOp, Extent3d, GraphicsPipelineDesc, MemoryDomain,
    PipelineLayoutDesc, PipelineStageFlags, PrimitiveTopology, QueueClass, RenderPassDesc,
    RenderTargetDesc, ResourceBinding, ResourceBindingDesc, ResourceBindingKind,
    ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc, SamplerFilter,
    ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension, TextureFormat,
    TextureSubresourceRange, TextureUsage, TextureViewDesc,
};
use crate::render::shaderpack::vanilla::post_effect::executor::VanillaPostEffectExternalTargetBindings;
use crate::render::vulkanic::sync::SubmissionId;
use crate::render::vulkanic::{BufferImageCopyRegion, CommandListDesc, CullMode};

/// GUI renderer state that persists across frames: staged assets and atlas
/// references, per-group resources, shared pipelines, cached frame passes,
/// mesh and item-raster caches and post-effect resources.
///
/// Every handle here belongs to the GAL the frontend records against;
/// `GuiFrontend::reset` releases them and the staged assets together.
#[derive(Default)]
pub struct GuiFrontend {
    generation: u64,
    asset_generation: u64,
    raw_image_generation: u64,
    asset_overrides: BTreeMap<u32, Vec<u8>>,
    raw_images: BTreeMap<u64, RawGuiImage>,
    atlas_references: GuiAtlasReferences,
    atlas_views: BTreeMap<u64, (AcceptedAtlasIncarnation, Handle)>,
    atlases: BTreeMap<TextureGroupKey, TextureAtlas>,
    resources: BTreeMap<ResourceKey, GuiResources>,
    dynamic_textures: BTreeMap<(u64, GuiRawImageFormat), SharedDynamicGuiTexture>,
    shared_pipelines: BTreeMap<GuiSharedPipelineKey, GuiSharedPipeline>,
    cached_pass: Option<CachedPass>,
    mesh_targets: GuiMeshOffscreenTargetCache,
    mesh_rasters: BTreeMap<GuiMeshRasterKey, GuiMeshPassResources>,
    /// Prepared geometry of recent explicit meshes without a reusable raster.
    prepared_geometry: crate::render::guirender::mesh::PreparedGeometryMemo,
    mesh_geometry_streams: Option<(Handle, Handle)>,
    mesh_shared_programs: BTreeMap<GuiMeshSharedProgramKey, GuiMeshSharedProgram>,
    mesh_composites: BTreeMap<GuiMeshCompositeKey, GuiMeshCompositeResources>,
    item_rasters: BTreeMap<GuiMeshCompositeKey, GuiItemRasterResources>,
    item_raster_slots: crate::render::guirender::items::raster::GuiItemRasterSlots,
    // A prepared upload is reusable only within its command transaction.
    // Older allocations remain reserved until completion, but preparation
    // alone is never evidence that their bytes reached the GPU.
    mesh_geometry_transaction: u64,
    mesh_geometry_cache: BTreeMap<(GuiMeshRasterKey, u64, u64), GuiMeshGeometryResidency>,
    /// Ranges displaced while pending commands still referenced them.
    mesh_geometry_retired: Vec<GuiMeshGeometryResidency>,
    mesh_geometry_free_ranges: BTreeMap<(), Vec<GuiMeshGeometryResidency>>,
    mesh_composite_uniform_cursor: u64,
    blur_resources: Option<GuiBlurResources>,
    custom_post_effect_resources: Vec<CustomPostEffectResources>,
    custom_post_effect_intermediates: BTreeMap<String, CustomPostEffectIntermediate>,
    /// Cached color-only alias for a depth-bearing render target. It aliases
    /// the original color view but deliberately has no depth attachment, so a
    /// custom pass can sample the original depth image without an attachment
    /// layout conflict. The alias is Rust-owned and never exposed as a native
    /// or Java handle.
    custom_post_effect_depth_target: Option<CustomPostEffectDepthTarget>,
    blur_snapshot_initialized: bool,
    custom_post_effect_snapshot_initialized: Vec<Vec<bool>>,
    custom_post_effect_image_initialized: Vec<Vec<bool>>,
    creeper_intermediate_initialized: bool,
    spider_initialized: [bool; 4],
}

impl GuiFrontend {
    /// Destroys all GPU resources and forgets staged assets and atlas
    /// references, returning the frontend to its initial generation.
    pub fn reset(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        self.destroy_render_resources(gal);
        self.item_raster_slots = Default::default();
        self.retire_atlas_views_for_assets(gal, &self.atlas_views.keys().copied().collect())?;
        self.asset_overrides.clear();
        self.raw_images.clear();
        self.atlas_references.clear();
        self.asset_generation = 0;
        self.raw_image_generation = 0;
        self.generation = 0;
        Ok(())
    }

    /// Destroys every GAL object this frontend owns, consumers before the
    /// images and samplers they bind, keeping staged assets.
    fn destroy_render_resources(&mut self, gal: &mut VulkanicGal) {
        for (_, resources) in std::mem::take(&mut self.item_rasters) {
            resources.composite.destroy(gal);
            let _ = resources.target.destroy(gal);
        }
        if let Some(pass) = self.cached_pass.take() {
            let _ = gal.retire(pass.pass);
        }
        let mesh_rasters = std::mem::take(&mut self.mesh_rasters);
        for resources in mesh_rasters.into_values() {
            resources.destroy_asset_resources(gal);
        }
        if let Some((vertices, indices)) = self.mesh_geometry_streams.take() {
            let _ = gal.retire(indices);
            let _ = gal.retire(vertices);
        }
        let resources = std::mem::take(&mut self.resources);
        for resource in resources.values() {
            for handle in resource.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        for pipeline in std::mem::take(&mut self.shared_pipelines).into_values() {
            for handle in pipeline.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        // Mesh raster resource sets also reference these images/samplers.
        // Release every consuming set before its owned image: otherwise GAL
        // correctly rejects destruction and taking the map would orphan it.
        for texture in std::mem::take(&mut self.dynamic_textures).into_values() {
            for handle in [
                texture.texture_view,
                texture.linear_sampler,
                texture.nearest_sampler,
                texture.texture,
                texture.upload_buffer,
            ] {
                let _ = gal.retire(handle);
            }
        }
        for program in std::mem::take(&mut self.mesh_shared_programs).into_values() {
            program.destroy(gal);
        }
        self.mesh_geometry_cache.clear();
        self.mesh_geometry_retired.clear();
        self.mesh_geometry_free_ranges.clear();
        let mesh_composites = std::mem::take(&mut self.mesh_composites);
        let mut mesh_composites = mesh_composites.into_values().collect::<Vec<_>>();
        mesh_composites.sort_by_key(|resources| resources.owns_shared_resources());
        for resources in mesh_composites {
            resources.destroy(gal);
        }
        self.discard_prepared_post_effects(gal);
        self.mesh_targets.clear(gal);
        self.atlases.clear();
    }
}

#[cfg(test)]
mod raster_gpu_tests;
#[cfg(test)]
mod tests;


//! Distant Horizons LOD rendering.
//!
//! The renderer expands the immutable semantic LOD records copied over FFI
//! into explicit quad geometry (`geometry`, `packing`), keeps it GPU-resident
//! (`residency`), and draws it through Rust-owned passes: built-in opaque,
//! transparent and water passes (`passes`, `contract`, `uniforms`), exact-atlas
//! passes, pack-source passes (`source`) and direct composition
//! (`composition`). `staging` holds the frontend's per-frame DH work. Nothing
//! here knows about DH VBOs, VAOs, shaders or framebuffers.

pub(crate) mod staging;
mod composition;
mod contract;
mod exact_atlas;
mod geometry;
mod packing;
mod passes;
mod residency;
mod source;
#[cfg(test)]
mod tests;
mod uniforms;


pub(crate) use self::uniforms::*;
pub(crate) use self::contract::*;
pub(crate) use self::passes::*;
pub(crate) use self::composition::*;
pub(crate) use self::exact_atlas::*;
pub(crate) use self::source::*;
pub(crate) use self::geometry::*;
pub(crate) use self::residency::*;
pub(crate) use self::packing::*;


use std::collections::{BTreeMap, BTreeSet};

use crate::render::worldrender::selected_source_raster_probe_cull_mode;
use crate::render::worldrender::selected_source_raster_probe_depth_compare;
use crate::render::worldrender::selected_source_raster_probe_front_face;
use crate::render::worldrender::validate_world_lod_column_asset;
use crate::render::worldrender::GalError;
use crate::render::worldrender::GalResult;
use crate::render::worldrender::WorldLodColumnAsset;
use crate::render::worldrender::WorldLodColumnInstanceRequest;
use crate::render::worldrender::WorldLodColumnMaterialProvenance;
use crate::render::worldrender::WorldLodFaceMaterial;
use crate::render::worldrender::WorldLodRenderFrame;
use crate::render::worldrender::WorldLodSegment;
use crate::render::worldrender::WorldLodVertex;
use crate::render::worldrender::SHADER_G_BUFFER_COLOR_FORMAT;
use crate::render::scene::lod::WORLD_LOD_LAYER_OPAQUE;
use crate::render::scene::lod::WORLD_LOD_LAYER_TRANSPARENT_SIDE;
use crate::render::scene::lod::WORLD_LOD_LAYER_TRANSPARENT_UP;
use crate::render::scene::lod::WORLD_LOD_LAYER_TRANSPARENT_WATER_UP;
use crate::render::worldrender::WORLD_LOD_MAX_NORMAL_INDEX;
use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, ClearColor, CommandOp, PassAttachment, ResourceBarrier,
    TextureImageCopyRegion, TextureOrigin3d, TextureRowOrder, TextureUsageState,
};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    AccessFlags, BlendMode, BufferDesc, BufferUsage, CombinedTextureSamplerDesc,
    CompareOp, Extent3d, FrontFace, GraphicsPipelineDesc, IndexType, MemoryDomain,
    PipelineLayoutDesc, PipelineStageFlags, PrimitiveTopology, QueueClass, RasterYDirection,
    RenderPassDesc, RenderTargetDesc, ResourceBinding, ResourceBindingDesc, ResourceBindingKind,
    ResourceLayoutDesc, ResourceSetDesc, SamplerAddressMode, SamplerDesc, SamplerFilter,
    ShaderCodeFormat, ShaderModuleDesc, ShaderStage, TextureDesc, TextureDimension, TextureFormat,
    TextureUsage, TextureViewDesc,
};
use crate::render::shaderpack::contracts::distant_horizons::DistantHorizonsPassKind;
use crate::render::shaderpack::vanilla::lightmap::VanillaLightmapBinding;
use crate::render::shaderpack::programs::{
    distant_horizons_exact_atlas_source_resource_layout,
    distant_horizons_lod_exact_atlas_resource_layouts,
    distant_horizons_lod_opaque_resource_layouts,
    minimal_distant_horizons_lod_exact_atlas_forward_opaque_program,
    minimal_distant_horizons_lod_exact_atlas_opaque_program,
    minimal_distant_horizons_lod_forward_opaque_program,
    minimal_distant_horizons_lod_opaque_program, minimal_distant_horizons_lod_transparent_program,
    shader_stage_code, LoweredDistantHorizonsExactAtlasSourceProgram,
    LoweredDistantHorizonsSourceProgram, MINIMAL_DISTANT_HORIZONS_DIRECT_APPLY_FRAGMENT,
    MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_FRAGMENT,
    MINIMAL_DISTANT_HORIZONS_DIRECT_COMPOSITE_VERTEX,
    MINIMAL_DISTANT_HORIZONS_DIRECT_FADE_FRAGMENT, MINIMAL_DISTANT_HORIZONS_SSAO_FRAGMENT,
};
use crate::render::shaderpack::resources::color_targets::{
    source_color_clear_color, ShaderPackColorTargets, TerrainSourceColorAttachment,
};
use crate::render::shaderpack::uniforms::source::TerrainSourceUniformFrame;
use crate::render::shaderpack::contracts::terrain::TerrainPassOutput;
use crate::render::shaderpack::resources::bindings::{
    TerrainSourceOwnedResource, TerrainSourceOwnedResourceSet, TerrainSourceResourceAvailability,
    TerrainSourceResourceAvailabilitySet, TerrainSourceResourceRole,
    TerrainSourceSampledResourceShape,
};
use crate::render::vulkanic::CullMode;

const MICRO_OFFSET_SCALE: f32 = 0.01;
const MAX_PACKED_LOD_UNIFORM_SLOTS: usize = 16_384;
const PACKED_LOD_UNIFORM_BYTES: usize = 240;
const MAX_INLINE_BUFFER_UPDATE_BYTES: usize = 65_536;
// The private shader carries a segment's vertex base in an existing float
// lane. Integers through 2^24 are exact in f32, so never pack a larger stream.
const MAX_SHARED_LOD_VERTEX_BYTES: u64 = (1 << 24) * WORLD_LOD_GPU_VERTEX_BYTES as u64;

/// Private Rust shader-input layout for expanded DH columns. This is not the
/// FFI record layout and intentionally has no OpenGL/Vulkan vertex-format
/// meaning. Backends receive it only after a later LOD material pass defines
/// an explicit pipeline interface.
pub(crate) const WORLD_LOD_GPU_VERTEX_LAYOUT_V2: u32 = 2;
pub(crate) const WORLD_LOD_GPU_VERTEX_BYTES: usize = 16;
/// Private Rust-owned exact-atlas DH vertex ABI. Unlike the legacy DH stream,
/// this carries copied atlas UVs and has no Java/OpenGL layout meaning.
pub(crate) const WORLD_LOD_TEXTURED_GPU_VERTEX_LAYOUT_V2: u32 = 2;
pub(crate) const WORLD_LOD_TEXTURED_GPU_VERTEX_BYTES: usize = 56;
pub(crate) const WORLD_LOD_TERRAIN_ATLAS_IDENTITY: &str = "minecraft:textures/atlas/blocks.png";


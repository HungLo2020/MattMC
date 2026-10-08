//! The Java boundary: the C ABI that Java calls through FFM downcalls.
//!
//! Every `mattmc_vulkanic_gal_*` entry point reads a versioned `#[repr(C)]`
//! request (`abi`), bounds and copies what Java sent (`memory`, `accounting`),
//! decodes wire values into GAL and renderer types (`wire`, `capabilities`),
//! calls the GAL or a renderer, and writes a status result back (`status`).
//! The bridge makes no rendering decisions. It owns the context registry and
//! is the one place that chooses a GAL backend (`context`).
//!
//! - `abi`: wire records by family, ABI versions and payload limits.
//! - `layout`: the struct-layout table Java checks against at load.
//! - `context`: context registry and lifetime, backend choice, capabilities.
//! - `resources`, `submission`, `frame`: raw GAL resource, command and frame
//!   entry points.
//! - `gui`, `world`, `shader_pack`, `sprite_animation`: semantic frames and
//!   asset updates handed to the GUI renderer, world renderer and shader pack.
//! - `canonical` (tests only): canonical encoding of decoded batches.

pub(crate) mod abi;
pub(crate) mod accounting;
#[cfg(test)]
pub(crate) mod canonical;
pub(crate) mod capabilities;
pub(crate) mod context;
pub(crate) mod dh_collector;
pub(crate) mod frame;
pub(crate) mod gui;
pub(crate) mod layout;
pub(crate) mod memory;
pub(crate) mod pipeline;
pub(crate) mod resources;
pub(crate) mod shader_pack;
pub(crate) mod sprite_animation;
pub(crate) mod status;
pub(crate) mod submission;
pub(crate) mod wire;
pub(crate) mod world;

pub use self::abi::*;
pub(crate) use self::accounting::*;
#[cfg(test)]
pub(crate) use self::canonical::*;
pub(crate) use self::capabilities::*;
pub(crate) use self::context::*;
pub(crate) use self::gui::*;
pub(crate) use self::memory::*;
pub(crate) use self::pipeline::*;
#[cfg(test)]
pub(crate) use self::resources::*;
pub(crate) use self::status::*;
pub(crate) use self::submission::*;
pub(crate) use self::wire::*;
#[cfg(test)]
pub(crate) use self::world::*;

use std::cell::RefCell;
use std::collections::BTreeMap;
use std::mem::{align_of, size_of};
use std::ptr;
use std::slice;

use crate::render::vulkanic::create::{BackendChoice, NativeWindow};
use crate::render::vulkanic::commands::{
    AttachmentLoadOp, AttachmentStoreOp, BufferImageCopyRegion, ClearColor, CommandList,
    CommandListDesc, CommandOp, PassAttachment, ResourceBarrier, SubmissionBatch, TextureOrigin3d,
    TextureUsageState,
};
use crate::render::vulkanic::error::{ErrorDomain, GalError, GalResult, StatusCode};
use crate::render::vulkanic::frame::{
    FrameAcquireDesc, FrameAcquireStatus, FrameCorrelationId, FrameId as VulkanicFrameId,
    FramePresentStatus, FrameRenderTargetId, FrameResizeDesc, FrameSurfaceDesc, PresentFrameDesc,
    PresentMode,
};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::guirender::frontend::{
    GuiAffineQuadRequest, GuiAssetPayload, GuiFrontend, GuiRawImageAssetPayload, GuiRawImageFormat,
    GuiSpriteRequest, GuiSubmitStats, GuiTiledQuadRequest, GUI_MAX_RAW_IMAGES,
    GUI_MAX_VIEWPORT_AXIS,
};
use crate::render::guirender::mesh::{
    GuiMeshBatchRequest, GuiMeshLightingMode, GuiMeshMaterialMode, GuiMeshVertex,
    GUI_MESH_MAX_BATCHES, GUI_MESH_MAX_INDICES, GUI_MESH_MAX_VERTICES,
};
use crate::render::vulkanic::handles::{Handle, HandleKind};
use crate::render::vulkanic::metrics::Metrics;
use crate::render::vulkanic::resources::{
    AccessFlags, BackendCapabilities, BackendFeature, BackendLimits, BlendMode,
    BufferDesc, BufferUsage, ColorFormat, CompareOp, ComputePipelineDesc, CullMode, Extent3d,
    FrameTargetDesc, GraphicsPipelineDesc, IndexType, MemoryDomain, PipelineLayoutDesc,
    PipelineStageFlags, PrimitiveTopology, QueueClass, RenderPassDesc, RenderTargetDesc,
    ResourceBinding, ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc,
    SamplerAddressMode, SamplerDesc, SamplerFilter, ShaderCodeFormat, ShaderModuleDesc,
    ShaderStage, TextureDesc, TextureDimension, TextureFormat, TextureSubresourceRange,
    TextureUsage, TextureViewDesc,
};
use crate::render::vulkanic::sync::SubmissionId;
use crate::render::worldrender::WorldBackgroundRequest;
use crate::render::worldrender::WorldBorderAssetPayload;
use crate::render::worldrender::WorldBorderQuadRequest;
use crate::render::worldrender::WorldCrackAssetPayload;
use crate::render::worldrender::WorldCrackQuadRequest;
use crate::render::worldrender::WorldLineSegmentRequest;
use crate::render::worldrender::WorldLodColumnAsset;
use crate::render::worldrender::WorldLodColumnInstanceRequest;
use crate::render::worldrender::WorldLodColumnMaterialProvenance;
use crate::render::worldrender::WorldLodColumnRetirement;
use crate::render::worldrender::WorldLodFaceMaterial;
use crate::render::worldrender::WorldLodMaterialIdentity;
use crate::render::worldrender::WorldLodSegment;
use crate::render::worldrender::WorldLodSegmentMaterialProvenance;
use crate::render::worldrender::WorldLodVertex;
use crate::render::worldrender::WorldMaterialAssetPayload;
use crate::render::worldrender::WorldMaterialQuadRequest;
use crate::render::scene::mesh::WorldMeshAsset;
use crate::render::worldrender::WorldMeshInstanceRequest;
use crate::render::scene::mesh::WorldMeshSection;
use crate::render::worldrender::WorldMeshSortedIndexUpdate;
use crate::render::worldrender::WorldMeshTextureAssetPayload;
use crate::render::scene::mesh::WorldMeshVertex;
use crate::render::worldrender::WorldPrimitiveFrame;
use crate::render::worldrender::WorldPrimitiveFrontend;
use crate::render::worldrender::WorldPrimitiveSubmitStats;
use crate::render::scene::background::WORLD_BACKGROUND_LOAD_CLEAR;
use crate::render::scene::background::WORLD_BACKGROUND_SKY_CUSTOM;
use crate::render::scene::background::WORLD_BACKGROUND_SKY_END;
use crate::render::scene::background::WORLD_BACKGROUND_SKY_NETHER;
use crate::render::scene::background::WORLD_BACKGROUND_SKY_OVERWORLD;
use crate::render::scene::background::WORLD_BACKGROUND_STORE_STORE;
use crate::render::scene::mesh::WORLD_CULL_BACK;
use crate::render::scene::mesh::WORLD_CULL_FRONT;
use crate::render::scene::mesh::WORLD_CULL_NONE;
use crate::render::scene::material::WORLD_DEPTH_POLICY_DISABLED;
use crate::render::scene::material::WORLD_DEPTH_POLICY_TEST_EQUAL_WRITE;
use crate::render::scene::material::WORLD_DEPTH_POLICY_TEST_NO_WRITE;
use crate::render::scene::material::WORLD_DEPTH_POLICY_TEST_WRITE;
use crate::render::worldrender::WORLD_LOD_MAX_MATERIAL_IDENTITIES_PER_COLUMN;
use crate::render::scene::material::WORLD_MATERIAL_MODE_CUTOUT;
use crate::render::scene::material::WORLD_MATERIAL_MODE_OPAQUE;
use crate::render::scene::material::WORLD_MATERIAL_MODE_TRANSLUCENT;
use crate::render::scene::strata::WORLD_STRATUM_ENTITY_MESH;
use crate::render::scene::strata::WORLD_STRATUM_MOVING_MESH;
use crate::render::scene::strata::WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY;
use crate::render::scene::strata::WORLD_STRATUM_TERRAIN;
use crate::render::scene::mesh::WORLD_TOPOLOGY_TRIANGLES;
use crate::render::scene::mesh::WORLD_WINDING_CCW;
use crate::render::scene::mesh::WORLD_WINDING_CW;

#[cfg(test)]
mod tests;

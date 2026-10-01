use std::cell::RefCell;
use std::collections::BTreeMap;
use std::mem::{align_of, size_of};
use std::ptr;
use std::slice;

use super::backends::{
    create_backend, create_borrowed_opengl_backend, create_native_windowed_vulkan_backend,
    BackendKind,
};
use super::commands::{
    AttachmentLoadOp, AttachmentStoreOp, BufferImageCopyRegion, ClearColor, CommandList,
    CommandListDesc, CommandOp, PassAttachment, ResourceBarrier, SubmissionBatch, TextureOrigin3d,
    TextureUsageState,
};
use super::error::{ErrorDomain, GalError, GalResult, StatusCode};
use super::frame::{
    FrameAcquireDesc, FrameAcquireStatus, FrameCorrelationId, FrameId as VulkanicFrameId,
    FramePresentStatus, FrameRenderTargetId, FrameResizeDesc, FrameSurfaceDesc, PresentFrameDesc,
    PresentMode,
};
use super::gal::VulkanicGal;
use super::gui_frontend::{
    GuiAffineQuadRequest, GuiAssetPayload, GuiFrontend, GuiRawImageAssetPayload, GuiRawImageFormat,
    GuiSpriteRequest, GuiSubmitStats, GuiTiledQuadRequest, GUI_MAX_RAW_IMAGES,
    GUI_MAX_VIEWPORT_AXIS,
};
use super::gui_mesh_frontend::{
    validate_batch as validate_gui_mesh_batch, validate_batches as validate_gui_mesh_batches,
    GuiMeshBatchRequest, GuiMeshLightingMode, GuiMeshMaterialMode, GuiMeshVertex,
    GUI_MESH_MAX_BATCHES, GUI_MESH_MAX_INDICES, GUI_MESH_MAX_VERTICES,
};
use super::handles::{Handle, HandleKind};
use super::metrics::Metrics;
use super::resources::{
    AccessFlags, BackendCapabilities, BackendFeature, BackendLimits, BlendMode,
    BufferDesc, BufferUsage, ColorFormat, CompareOp, ComputePipelineDesc, CullMode, Extent3d,
    FrameTargetDesc, GraphicsPipelineDesc, IndexType, MemoryDomain, PipelineLayoutDesc,
    PipelineStageFlags, PrimitiveTopology, QueueClass, RenderPassDesc, RenderTargetDesc,
    ResourceBinding, ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc,
    SamplerAddressMode, SamplerDesc, SamplerFilter, ShaderCodeFormat, ShaderModuleDesc,
    ShaderStage, TextureDesc, TextureDimension, TextureFormat, TextureSubresourceRange,
    TextureUsage, TextureViewDesc,
};
use super::sync::SubmissionId;
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

pub(crate) mod abi;
pub(crate) mod context;
pub(crate) mod frame;
pub(crate) mod gui;
pub(crate) mod layout;
pub(crate) mod material;
pub(crate) mod memory;
pub(crate) mod resources;
pub(crate) mod shader_pack;
pub(crate) mod sprite_animation;
pub(crate) mod status;
pub(crate) mod submission;
pub(crate) mod world;

pub use self::abi::*;
pub(crate) use self::context::*;
pub(crate) use self::gui::*;
#[cfg(test)]
pub(crate) use self::material::*;
pub(crate) use self::memory::*;
#[cfg(test)]
pub(crate) use self::resources::*;
pub(crate) use self::status::*;
pub(crate) use self::submission::*;
#[cfg(test)]
pub(crate) use self::world::*;

#[cfg(test)]
mod tests;

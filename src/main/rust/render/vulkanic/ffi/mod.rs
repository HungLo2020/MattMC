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
    FramePresentStatus, FrameResizeDesc, FrameSurfaceDesc, PresentFrameDesc, PresentMode,
};
use super::gal::VulkanicGal;
use super::gui_frontend::{GuiAssetPayload, GuiFrontend, GuiSpriteRequest, GuiSubmitStats};
use super::handles::{Handle, HandleKind};
use super::metrics::Metrics;
use super::resources::{
    AccessFlags, BackendApi, BackendCapabilities, BackendFeature, BackendLimits, BlendMode,
    BufferDesc, BufferUsage, ColorFormat, CompareOp, ComputePipelineDesc, CullMode, Extent3d,
    FrameTargetDesc, GraphicsPipelineDesc, IndexType, MemoryDomain, PipelineLayoutDesc,
    PipelineStageFlags, PrimitiveTopology, QueueClass, RenderPassDesc, RenderTargetDesc,
    ResourceBinding, ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc,
    SamplerAddressMode, SamplerDesc, SamplerFilter, ShaderCodeFormat, ShaderModuleDesc,
    ShaderStage, TextureDesc, TextureDimension, TextureFormat, TextureSubresourceRange,
    TextureUsage, TextureViewDesc,
};
use super::sync::SubmissionId;
use super::world_primitive_frontend::{
    WorldBackgroundRequest, WorldBorderAssetPayload, WorldBorderQuadRequest,
    WorldCrackAssetPayload, WorldCrackQuadRequest, WorldLineSegmentRequest,
    WorldMaterialAssetPayload, WorldMaterialQuadRequest, WorldPrimitiveFrame,
    WorldPrimitiveFrontend, WorldPrimitiveSubmitStats, WORLD_BACKGROUND_LOAD_CLEAR,
    WORLD_BACKGROUND_SKY_CUSTOM, WORLD_BACKGROUND_SKY_END, WORLD_BACKGROUND_SKY_NETHER,
    WORLD_BACKGROUND_SKY_OVERWORLD, WORLD_BACKGROUND_STORE_STORE, WORLD_CULL_BACK,
    WORLD_CULL_FRONT, WORLD_CULL_NONE, WORLD_DEPTH_POLICY_DISABLED,
    WORLD_DEPTH_POLICY_TEST_NO_WRITE, WORLD_DEPTH_POLICY_TEST_WRITE,
    WORLD_MATERIAL_ID_OPAQUE_TEXTURED, WORLD_MATERIAL_MODE_CUTOUT, WORLD_MATERIAL_MODE_OPAQUE,
    WORLD_STRATUM_OPAQUE_TEXTURED_GEOMETRY, WORLD_TOPOLOGY_TRIANGLES, WORLD_WINDING_CCW,
    WORLD_WINDING_CW,
};

pub(crate) mod abi;
pub(crate) mod context;
pub(crate) mod frame;
pub(crate) mod gui;
pub(crate) mod layout;
pub(crate) mod material;
pub(crate) mod memory;
pub(crate) mod resources;
pub(crate) mod status;
pub(crate) mod submission;
pub(crate) mod world;

pub use self::abi::*;
pub(crate) use self::context::*;
pub(crate) use self::gui::*;
pub(crate) use self::material::*;
pub(crate) use self::memory::*;
pub(crate) use self::resources::*;
pub(crate) use self::status::*;
pub(crate) use self::submission::*;
pub(crate) use self::world::*;

#[cfg(test)]
mod tests;

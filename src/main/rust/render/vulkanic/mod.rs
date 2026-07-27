#[cfg(test)]
mod architecture_boundary;

mod backends;

pub mod commands;
pub mod error;
pub mod ffi;
pub mod frame;
pub mod gal;
pub mod gui_frontend;
pub mod handles;
pub mod metrics;
pub mod resources;
pub mod sync;
pub mod world_primitive_frontend;

pub use commands::{
    AttachmentLoadOp, AttachmentStoreOp, BufferImageCopyRegion, ClearColor, CommandList,
    CommandListDesc, CommandOp, PassAttachment, ResourceBarrier, SubmissionBatch, TextureOrigin3d,
    TextureUsageState,
};
pub use error::{GalError, GalResult, StatusCode};
pub use frame::{
    AcquiredFrame, FrameAcquireDesc, FrameAcquireStatus, FrameCorrelationId, FrameId,
    FramePresentStatus, FrameRenderTargetId, FrameResizeDesc, FrameResizeResult, FrameSurfaceDesc,
    PresentFrameDesc, PresentMode, PresentedFrame,
};
pub use gal::VulkanicGal;
pub use handles::{Handle, HandleKind};
pub use metrics::{Metrics, TracyZone};
pub use resources::{
    AccessFlags, BackendCapabilities, BackendFeature, BackendFeatureFlags, BackendLimits,
    BlendMode, BufferDesc, BufferUsage, ColorFormat, CompareOp, ComputePipelineDesc, CullMode,
    Extent3d, GraphicsPipelineDesc, IndexType, MemoryDomain, PipelineLayoutDesc,
    PipelineStageFlags, PrimitiveTopology, QueueClass, RenderPassDesc, RenderTargetDesc,
    ResourceBinding, ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc,
    SamplerAddressMode, SamplerDesc, SamplerFilter, ShaderCodeFormat, ShaderModuleDesc,
    ShaderStage, TextureDesc, TextureDimension, TextureFormat, TextureSubresourceRange,
    TextureUsage, TextureViewDesc,
};
pub use sync::{RetirementQueue, SubmissionId, SyncToken};

#[cfg(test)]
mod tests;

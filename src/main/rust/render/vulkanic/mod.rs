#[cfg(test)]
mod architecture_boundary;

mod backends;

mod buffer_upload_capture;
pub mod commands;
pub mod create;
pub mod error;
pub mod frame;
pub mod gal;
pub mod handles;
pub mod metrics;
pub mod resources;
pub mod sync;
#[cfg(test)]
pub(crate) mod test_support;

pub use create::{set_gpu_timestamps_requested, BackendChoice, NativeWindow};
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

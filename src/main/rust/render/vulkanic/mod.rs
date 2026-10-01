//! VulkanicGAL: the graphics abstraction layer every renderer records into.
//!
//! The GAL owns one device through a private backend (Vulkan or OpenGL) and
//! exposes resources by generational `Handle`, explicit command lists, and
//! submissions identified by `SubmissionId`. It validates every descriptor and
//! command against `BackendCapabilities`, rejects submissions whose resource
//! accesses conflict without an explicit barrier between them, and defers
//! destroying in-flight resources until their last submission completes. It knows nothing about the game: renderers decide
//! what to draw, the GAL checks and executes it.
//!
//! - `create`: creating a GAL and choosing its backend (bridge only).
//! - `gal`: `VulkanicGal`, the resource, command, submission and frame API.
//! - `resources`: descriptors, formats, usages and backend capabilities.
//! - `commands`: command ops, command lists and submission batches.
//! - `frame`: swapchain surfaces and frame acquire/present.
//! - `handles`, `sync`, `error`, `metrics`: handles, submission ids, errors
//!   and profiling counters.
//!
//! Backends live in the private `backends` module; nothing outside this
//! module names them (enforced by `architecture_boundary.rs`).
#![warn(missing_docs)]
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
pub use metrics::Metrics;
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
pub use sync::{SubmissionId, SyncToken};

#[cfg(test)]
mod tests;

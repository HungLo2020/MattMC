//! Presentation: the swapchain surface, frame acquire, present, cancel and
//! resize, and the ids that tie them together.

use super::resources::{Extent3d, TextureFormat};
use super::sync::SubmissionId;

/// Identifies one acquired swapchain frame until it is presented or
/// cancelled.
#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct FrameId(pub u64);

/// A caller-chosen id echoed through acquire, present and resize, so
/// results can be matched to the caller's own frame.
#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct FrameCorrelationId(pub u64);

/// Identifies the swapchain image an acquired frame renders into; pass it
/// to `FrameTargetDesc` to create a frame target for it.
#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct FrameRenderTargetId(pub u64);

/// How presentation is paced; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PresentMode {
    /// Present immediately; may tear.
    Immediate = 1,
    /// Replace the queued image; no tearing, no vsync wait.
    Mailbox = 2,
    /// Wait for vsync.
    Fifo = 3,
    /// Vsync: FIFO when available, else the backend's fallback.
    AutoVsync = 4,
    /// No vsync: mailbox, else immediate, else the backend's fallback.
    AutoNoVsync = 5,
    /// Vsync, but present a late image immediately (may tear).
    FifoRelaxed = 6,
}

/// The outcome of acquiring a frame; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FrameAcquireStatus {
    /// An image was acquired.
    Ready = 1,
    /// An image was acquired, but the surface should be resized.
    Suboptimal = 2,
    /// The surface was out of date and has been recreated; no image was
    /// acquired this time.
    Resized = 3,
    /// The window is minimized; no image was acquired.
    Minimized = 4,
}

/// The outcome of presenting a frame; values are part of the C ABI.
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FramePresentStatus {
    /// The image was presented.
    Presented = 1,
    /// Presented, but the surface should be resized.
    Suboptimal = 2,
    /// The surface is out of date and must be resized.
    OutOfDate = 3,
    /// The window is minimized.
    Minimized = 4,
}

/// Describes the presentation surface.
#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FrameSurfaceDesc {
    /// A debug label.
    pub label: String,
    /// The surface size (depth 1).
    pub extent: Extent3d,
    /// The swapchain color format.
    pub color_format: TextureFormat,
    /// How presentation is paced.
    pub present_mode: PresentMode,
    /// How many frames may be acquired but not yet completed.
    pub max_frames_in_flight: u32,
}

/// Requests the next swapchain image.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FrameAcquireDesc {
    /// Echoed in the result.
    pub correlation_id: FrameCorrelationId,
    /// The window size the caller expects; used if the surface must be
    /// recreated.
    pub expected_extent: Extent3d,
}

/// The result of `VulkanicGal::acquire_frame`.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct AcquiredFrame {
    /// The acquired frame, to present or cancel.
    pub frame: FrameId,
    /// Echoed from the request.
    pub correlation_id: FrameCorrelationId,
    /// Whether an image was acquired.
    pub status: FrameAcquireStatus,
    /// The swapchain image to create a frame target for.
    pub render_target: FrameRenderTargetId,
    /// The image size.
    pub extent: Extent3d,
    /// The image format.
    pub color_format: TextureFormat,
}

/// Requests presentation of an acquired frame.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct PresentFrameDesc {
    /// The frame to present.
    pub frame: FrameId,
    /// Echoed in the result.
    pub correlation_id: FrameCorrelationId,
    /// The submission that renders the frame; presentation waits for it.
    pub wait_for: SubmissionId,
}

/// The result of `VulkanicGal::present_frame`.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct PresentedFrame {
    /// The presented frame.
    pub frame: FrameId,
    /// Echoed from the request.
    pub correlation_id: FrameCorrelationId,
    /// The swapchain image presented.
    pub render_target: FrameRenderTargetId,
    /// The present outcome.
    pub status: FramePresentStatus,
    /// The newest submission known complete after presenting.
    pub completed_submission: SubmissionId,
}

/// Requests a surface resize.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FrameResizeDesc {
    /// Echoed for the caller's diagnostics.
    pub correlation_id: FrameCorrelationId,
    /// The new window size (depth 1).
    pub extent: Extent3d,
}

/// The result of `VulkanicGal::resize_frame_surface`.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FrameResizeResult {
    /// The surface state after resizing.
    pub status: FrameAcquireStatus,
    /// The surface size after resizing.
    pub extent: Extent3d,
}

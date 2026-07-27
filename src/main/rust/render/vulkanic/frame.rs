use super::resources::{Extent3d, TextureFormat};
use super::sync::SubmissionId;

#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct FrameId(pub u64);

#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct FrameCorrelationId(pub u64);

#[repr(transparent)]
#[derive(Clone, Copy, Debug, Default, Eq, Hash, Ord, PartialEq, PartialOrd)]
pub struct FrameRenderTargetId(pub u64);

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum PresentMode {
    Immediate = 1,
    Mailbox = 2,
    Fifo = 3,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FrameAcquireStatus {
    Ready = 1,
    Suboptimal = 2,
    Resized = 3,
    Minimized = 4,
}

#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum FramePresentStatus {
    Presented = 1,
    Suboptimal = 2,
    OutOfDate = 3,
    Minimized = 4,
}

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct FrameSurfaceDesc {
    pub label: String,
    pub extent: Extent3d,
    pub color_format: TextureFormat,
    pub present_mode: PresentMode,
    pub max_frames_in_flight: u32,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FrameAcquireDesc {
    pub correlation_id: FrameCorrelationId,
    pub expected_extent: Extent3d,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct AcquiredFrame {
    pub frame: FrameId,
    pub correlation_id: FrameCorrelationId,
    pub status: FrameAcquireStatus,
    pub render_target: FrameRenderTargetId,
    pub extent: Extent3d,
    pub color_format: TextureFormat,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct PresentFrameDesc {
    pub frame: FrameId,
    pub correlation_id: FrameCorrelationId,
    pub wait_for: SubmissionId,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct PresentedFrame {
    pub frame: FrameId,
    pub correlation_id: FrameCorrelationId,
    pub render_target: FrameRenderTargetId,
    pub status: FramePresentStatus,
    pub completed_submission: SubmissionId,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FrameResizeDesc {
    pub correlation_id: FrameCorrelationId,
    pub extent: Extent3d,
}

#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct FrameResizeResult {
    pub status: FrameAcquireStatus,
    pub extent: Extent3d,
}

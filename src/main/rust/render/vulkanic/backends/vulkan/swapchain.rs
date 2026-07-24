use ash::vk;

use crate::render::vulkanic::error::GalResult;
use crate::render::vulkanic::resources::{Extent3d, TextureFormat};
use crate::render::vulkanic::sync::SubmissionId;

#[allow(dead_code)]
pub(super) trait SurfaceOwner {
    fn create_surface(&self, instance: &ash::Instance) -> GalResult<vk::SurfaceKHR>;
    fn stable_window_id(&self) -> u64;
}

#[allow(dead_code)]
#[derive(Clone, Debug, Eq, PartialEq)]
pub(super) struct SwapchainDesc {
    pub(super) label: String,
    pub(super) extent: Extent3d,
    pub(super) format: TextureFormat,
    pub(super) image_count: u32,
    pub(super) present_mode: PresentMode,
}

#[allow(dead_code)]
#[repr(u32)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) enum PresentMode {
    Immediate = 1,
    Mailbox = 2,
    Fifo = 3,
}

#[allow(dead_code)]
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(super) struct AcquiredFrame {
    pub(super) image_index: u32,
    pub(super) acquire_submission: SubmissionId,
}

#[allow(dead_code)]
pub(super) trait SwapchainOwner {
    fn recreate(&mut self, desc: SwapchainDesc) -> GalResult<()>;
    fn acquire(&mut self) -> GalResult<AcquiredFrame>;
    fn present(&mut self, frame: AcquiredFrame, after: SubmissionId) -> GalResult<()>;
    fn retire_presented(&mut self, completed: SubmissionId) -> GalResult<()>;
}

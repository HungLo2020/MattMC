//! Frame surface lifecycle: configure, acquire, resize, present, cancel and shutdown.

use super::*;

impl VulkanicGal {
    /// Configures the presentation surface: extent (at most
    /// `MAX_FRAME_SURFACE_AXIS` per axis, depth 1), color format, present mode and
    /// frames in flight (1 to `MAX_FRAMES_IN_FLIGHT`). Requires the
    /// `Presentation` feature.
    pub fn configure_frame_surface(&mut self, desc: FrameSurfaceDesc) -> GalResult<()> {
        if desc.extent.width == 0 || desc.extent.height == 0 || desc.extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame surface extent must be non-zero",
            ));
        }
        if desc.extent.width > MAX_FRAME_SURFACE_AXIS
            || desc.extent.height > MAX_FRAME_SURFACE_AXIS
            || desc.extent.depth != 1
        {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                format!(
                    "frame surface extent exceeds the explicit {}x{}x1 bound",
                    MAX_FRAME_SURFACE_AXIS, MAX_FRAME_SURFACE_AXIS
                ),
            ));
        }
        if desc.max_frames_in_flight == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame surface must allow at least one frame in flight",
            ));
        }
        if desc.max_frames_in_flight > MAX_FRAMES_IN_FLIGHT {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                format!(
                    "frame surface exceeds the explicit {}-frame in-flight bound",
                    MAX_FRAMES_IN_FLIGHT
                ),
            ));
        }
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        self.backend.configure_frame_surface(&desc)
    }

    /// Acquires the next swapchain image. The result's status is `Ready`,
    /// `Suboptimal`, `Resized` or `Minimized`; it names the frame, the render
    /// target id to create a frame target for, and the image extent and format.
    pub fn acquire_frame(&mut self, desc: FrameAcquireDesc) -> GalResult<AcquiredFrame> {
        if desc.expected_extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame acquire extent depth must be non-zero",
            ));
        }
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        self.backend.acquire_frame(&desc)
    }

    /// Resizes the presentation surface to a new window extent.
    pub fn resize_frame_surface(&mut self, desc: FrameResizeDesc) -> GalResult<FrameResizeResult> {
        if desc.extent.depth == 0 {
            return self.validation_error(GalError::resource(
                StatusCode::InvalidArgument,
                "frame resize extent depth must be non-zero",
            ));
        }
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        self.backend.resize_frame_surface(&desc)
    }

    /// Presents an acquired frame once the submission named by `wait_for`
    /// completes. Also advances the GAL's completed-submission watermark.
    pub fn present_frame(&mut self, desc: PresentFrameDesc) -> GalResult<PresentedFrame> {
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        let presented = self.backend.present_frame(&desc)?;
        if presented.completed_submission > self.completed_submission {
            self.completed_submission = presented.completed_submission;
        }
        Ok(presented)
    }

    /// Releases an acquired frame without presenting it.
    pub fn cancel_frame(&mut self, frame: FrameId) -> GalResult<()> {
        let capabilities = self.capabilities();
        if !capabilities.supports(BackendFeature::Presentation) {
            return self.unsupported(format!(
                "backend '{}' was not created with presentation support",
                capabilities.name
            ));
        }
        self.backend.cancel_frame(frame)
    }

    /// Tears down the presentation surface and its swapchain.
    pub fn shutdown_frame_surface(&mut self) -> GalResult<()> {
        self.backend.shutdown_frame_surface()
    }
}

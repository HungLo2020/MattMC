//! Creating a GAL: the one public place a backend is chosen.
//!
//! The composition root (the bridge) picks a backend once, from what Java
//! requested, and gets back a `VulkanicGal`. Backend types stay private to
//! `vulkanic`, and after creation every caller sees capabilities only
//! (`BackendCapabilities`), never which backend is running.

use super::backends::{
    self, create_backend, create_borrowed_opengl_backend, create_native_windowed_vulkan_backend,
};
use super::error::GalResult;
use super::frame::FrameSurfaceDesc;
use super::gal::VulkanicGal;

/// The graphics API a new GAL runs on.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub enum BackendChoice {
    Vulkan,
    OpenGl,
}

/// A native window a Vulkan GAL presents to, as handed over by the host.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct NativeWindow {
    /// Host windowing platform id (X11 or Wayland) from the window request.
    pub platform: u32,
    /// Non-zero id that stays stable for the lifetime of the host window.
    pub stable_window_id: u64,
    pub native_display: u64,
    pub native_window: u64,
}

impl VulkanicGal {
    /// A GAL that owns its device and has no window of its own.
    pub fn create(backend: BackendChoice, label: &str, tracy_enabled: bool) -> GalResult<Self> {
        Ok(Self::new_with_backend(create_backend(backend, label)?, tracy_enabled))
    }

    /// An OpenGL GAL that renders through the host's current GL context.
    pub fn create_borrowed_opengl(
        label: &str,
        stable_window_id: u64,
        tracy_enabled: bool,
    ) -> GalResult<Self> {
        Ok(Self::new_with_backend(
            create_borrowed_opengl_backend(label, stable_window_id)?,
            tracy_enabled,
        ))
    }

    /// A Vulkan GAL that presents to the host's native window.
    pub fn create_native_windowed_vulkan(
        label: &str,
        window: NativeWindow,
        surface: FrameSurfaceDesc,
        tracy_enabled: bool,
    ) -> GalResult<Self> {
        Ok(Self::new_with_backend(
            create_native_windowed_vulkan_backend(
                label,
                window.platform,
                window.stable_window_id,
                window.native_display,
                window.native_window,
                surface,
            )?,
            tracy_enabled,
        ))
    }
}

/// Turns GPU frame timestamps on or off for every backend that records them.
pub fn set_gpu_timestamps_requested(requested: bool) {
    backends::set_gpu_timestamps_requested(requested);
}

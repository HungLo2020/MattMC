//! Test-only construction of GALs for code outside `render::vulkanic`.
//!
//! Backends stay private to the GAL; tests elsewhere build a mock, Vulkan or
//! OpenGL GAL through these functions and never name a backend type.

use super::backends::{self, mock::MockBackend, opengl::OpenGlBackend, vulkan::VulkanBackend};
use super::error::GalResult;
use super::gal::VulkanicGal;
use super::resources::BackendCapabilities;

/// A GAL over the recording mock backend with Vulkan-like capabilities.
pub(crate) fn mock_gal() -> VulkanicGal {
    VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false)
}

/// A GAL over the recording mock backend reporting `capabilities`.
pub(crate) fn mock_gal_with_capabilities(capabilities: BackendCapabilities) -> VulkanicGal {
    VulkanicGal::new_with_backend(Box::new(MockBackend::with_capabilities(capabilities)), false)
}

/// A GAL over a real headless Vulkan device, or the environment gap.
pub(crate) fn vulkan_gal(label: &str) -> GalResult<VulkanicGal> {
    Ok(VulkanicGal::new_with_backend(Box::new(VulkanBackend::new(label)?), false))
}

/// A GAL over a real isolated OpenGL context, or the environment gap.
pub(crate) fn opengl_gal(label: &str) -> GalResult<VulkanicGal> {
    Ok(VulkanicGal::new_with_backend(Box::new(OpenGlBackend::new(label)?), false))
}

pub(crate) fn vulkan_capabilities() -> BackendCapabilities {
    backends::vulkan_capabilities()
}

pub(crate) fn opengl_capabilities() -> BackendCapabilities {
    backends::opengl_capabilities()
}

pub(crate) fn presentation_capabilities(capabilities: BackendCapabilities) -> BackendCapabilities {
    backends::presentation_capabilities(capabilities)
}

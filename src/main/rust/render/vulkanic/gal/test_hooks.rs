//! Test-only access to backends, descriptors and failure injection.

use super::*;

impl VulkanicGal {
    #[cfg(test)]
    pub(in crate::render::vulkanic) fn force_buffer_generation_for_test(&mut self, handle: Handle, generation: u32) {
        self.buffers.force_generation(handle, generation);
    }

    #[cfg(test)]
    pub(crate) fn mock_backend(&self) -> Option<&crate::render::vulkanic::backends::mock::MockBackend> {
        self.backend.as_any().downcast_ref()
    }

    #[cfg(test)]
    pub(crate) fn sampler_descriptor_for_test(&self, handle: Handle) -> GalResult<&SamplerDesc> {
        self.sampler_descriptor_for_capture(handle)
    }

    #[cfg(test)]
    pub(crate) fn resource_set_descriptor_for_test(
        &self,
        handle: Handle,
    ) -> GalResult<&ResourceSetDesc> {
        self.resource_set_descriptor_for_capture(handle)
    }

    #[cfg(test)]
    pub(crate) fn graphics_pipeline_descriptor_for_test(
        &self,
        handle: Handle,
    ) -> GalResult<&GraphicsPipelineDesc> {
        self.graphics_pipeline_descriptor_for_capture(handle)
    }

    /// Makes the mock backend's next submission fail (test GALs only).
    #[cfg(test)]
    pub(crate) fn fail_next_submit_for_test(&mut self) {
        self.mock_backend_mut()
            .expect("GAL built by test_support::mock_gal")
            .fail_next_submit();
    }

    #[cfg(test)]
    pub(crate) fn mock_backend_mut(&mut self) -> Option<&mut crate::render::vulkanic::backends::mock::MockBackend> {
        self.backend.as_any_mut().downcast_mut()
    }

    #[cfg(test)]
    pub(in crate::render::vulkanic) fn vulkan_backend(&self) -> Option<&crate::render::vulkanic::backends::vulkan::VulkanBackend> {
        self.backend.as_any().downcast_ref()
    }

    /// Clears an acquired presentation frame on the Vulkan backend (test GALs only).
    #[cfg(test)]
    pub(crate) fn clear_acquired_frame_for_test(
        &mut self,
        frame: crate::render::vulkanic::frame::FrameId,
        color: [f32; 4],
    ) -> GalResult<()> {
        self.vulkan_backend_mut()
            .ok_or_else(|| GalError::backend("acquired-frame clear requires a Vulkan test GAL"))?
            .clear_acquired_frame_for_test(frame, color)
    }

    #[cfg(test)]
    pub(in crate::render::vulkanic) fn vulkan_backend_mut(
        &mut self,
    ) -> Option<&mut crate::render::vulkanic::backends::vulkan::VulkanBackend> {
        self.backend.as_any_mut().downcast_mut()
    }

    #[cfg(test)]
    pub(in crate::render::vulkanic) fn opengl_backend(&self) -> Option<&crate::render::vulkanic::backends::opengl::OpenGlBackend> {
        self.backend.as_any().downcast_ref()
    }

    #[cfg(test)]
    pub(crate) fn retire_through_for_test(&mut self, id: SubmissionId) -> GalResult<Vec<Handle>> {
        self.retire_through(id)
    }
}

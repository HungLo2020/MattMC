//! Diagnostic captures: descriptor and buffer-upload snapshots for capture tooling.

use super::*;

impl VulkanicGal {
    /// Backend-neutral debug capture for tests: the active backend starts a
    /// RenderDoc capture if requested, ending when the guard drops.
    #[cfg(test)]
    pub(crate) fn begin_debug_capture(&self) -> Option<Box<dyn std::any::Any>> {
        self.backend.begin_debug_capture()
    }

    /// The descriptor a sampler was created with, for diagnostic captures.
    pub fn sampler_descriptor_for_capture(&self, handle: Handle) -> GalResult<&SamplerDesc> {
        Ok(&self.samplers.get(handle)?.desc)
    }

    /// Starts recording the host uploads that later submissions make to a
    /// buffer range, for diagnostic captures.
    pub fn watch_buffer_upload_for_capture(
        &mut self,
        buffer: Handle,
        offset: u64,
        size: usize,
    ) -> GalResult<()> {
        let limit = self.buffers.get(buffer)?.desc.size;
        if offset
            .checked_add(size as u64)
            .is_none_or(|end| end > limit)
        {
            return Err(GalError::invalid_argument(
                "capture range exceeds owned buffer",
            ));
        }
        self.buffer_upload_capture.watch(buffer, offset, size)
    }

    /// The bytes last uploaded to a watched buffer range and the submission
    /// that accepted them.
    pub fn buffer_upload_for_capture(
        &self,
        buffer: Handle,
        offset: u64,
        size: usize,
    ) -> GalResult<(&[u8], SubmissionId)> {
        self.buffers.get(buffer)?;
        self.buffer_upload_capture
            .get(buffer, offset, size)
            .ok_or_else(|| GalError::invalid_argument("missing accepted buffer upload proof"))
    }

    /// Stops recording uploads to a buffer range.
    pub fn unwatch_buffer_upload_for_capture(
        &mut self,
        buffer: Handle,
        offset: u64,
        size: usize,
    ) {
        self.buffer_upload_capture.unwatch(buffer, offset, size);
    }

    /// Immutable GAL declaration for a selected submission diagnostic. This
    /// exposes no backend descriptor, native handle, or reconstructed state.
    pub fn resource_set_descriptor_for_capture(
        &self,
        handle: Handle,
    ) -> GalResult<&ResourceSetDesc> {
        Ok(&self.resource_sets.get(handle)?.desc)
    }

    /// Immutable GAL pipeline declaration; no backend GPU state is exposed.
    pub fn graphics_pipeline_descriptor_for_capture(
        &self,
        handle: Handle,
    ) -> GalResult<&GraphicsPipelineDesc> {
        Ok(&self.graphics_pipelines.get(handle)?.desc)
    }
}

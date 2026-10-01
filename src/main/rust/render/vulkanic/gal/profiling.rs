//! Profiling: GPU profile scopes, metrics, and per-submit profile accounting.

use super::*;

impl VulkanicGal {
    /// Installs the frontend's GPU profiling classification. Passes and
    /// pipelines created afterwards carry its tags; backends see only scope
    /// indices, and the frontend alone knows what each scope measures.
    pub fn install_gpu_profile_classifier(&mut self, classifier: GpuProfileClassifier) {
        self.gpu_profile_classifier = Some(classifier);
        self.backend
            .set_gpu_profile_scope_names(classifier.statistics_scope_name);
    }

    pub(super) fn tag_gpu_profile(&mut self, handle: Handle, object: GpuProfiledObject, label: &str) {
        if let Some(classifier) = self.gpu_profile_classifier {
            let tag = (classifier.classify)(object, label);
            if tag != GpuProfileTag::default() {
                self.backend.set_gpu_profile_tag(handle, tag);
            }
        }
    }

    /// Running GAL counters: creates, destroys, validation failures and
    /// submissions.
    pub fn metrics(&self) -> &Metrics {
        &self.metrics
    }

    /// The backend's native counters and timings (see `BackendRuntimeMetrics`).
    pub fn backend_runtime_metrics(&self) -> BackendRuntimeMetrics {
        self.backend.runtime_metrics()
    }
}

pub(super) fn add_command_profile(profile: &mut SubmitProfile, batch: &SubmissionBatch) {
    for list in &batch.command_lists {
        for op in &list.operations {
            match op {
                CommandOp::BeginPass { .. } => profile.pass_count += 1,
                CommandOp::BindGraphicsPipeline(_) | CommandOp::BindComputePipeline(_) => {
                    profile.pipeline_binds += 1;
                }
                CommandOp::BindResourceSet { .. } => profile.resource_set_binds += 1,
                CommandOp::Draw { .. } => profile.draw_ops += 1,
                CommandOp::DrawIndexed { .. } | CommandOp::DrawIndexedIndirect { .. } => {
                    profile.draw_indexed_ops += 1
                }
                CommandOp::HostWriteBuffer { data, .. } => {
                    profile.host_write_ops += 1;
                    profile.host_write_bytes =
                        profile.host_write_bytes.saturating_add(data.len() as u64);
                }
                CommandOp::Barrier(_) => profile.barrier_ops += 1,
                CommandOp::TrackSubmission(_)
                | CommandOp::EndPass
                | CommandOp::SetVertexBuffer { .. }
                | CommandOp::SetIndexBuffer { .. }
                | CommandOp::DrawIndirect { .. }
                | CommandOp::Dispatch { .. }
                | CommandOp::DispatchIndirect { .. }
                | CommandOp::CopyBuffer { .. }
                | CommandOp::CopyBufferRegion { .. }
                | CommandOp::CopyBufferToTexture(_)
                | CommandOp::CopyTextureToBuffer(_)
                | CommandOp::CopyTexture(_)
                | CommandOp::CopyFrameTargetToTexture { .. }
                | CommandOp::CopyTextureToFrameTarget { .. }
                | CommandOp::GenerateMipmaps { .. }
                | CommandOp::HostReadBuffer { .. }
                | CommandOp::Present { .. } => {}
            }
        }
    }
}

pub(super) fn add_backend_metric_deltas(
    profile: &mut SubmitProfile,
    before: BackendRuntimeMetrics,
    after: BackendRuntimeMetrics,
) {
    profile.native_command_buffer_alloc_nanos = after
        .native_command_buffer_alloc_nanos
        .saturating_sub(before.native_command_buffer_alloc_nanos);
    profile.native_command_buffer_begin_nanos = after
        .native_command_buffer_begin_nanos
        .saturating_sub(before.native_command_buffer_begin_nanos);
    profile.native_command_recording_nanos = after
        .native_command_recording_nanos
        .saturating_sub(before.native_command_recording_nanos);
    profile.native_command_buffer_end_nanos = after
        .native_command_buffer_end_nanos
        .saturating_sub(before.native_command_buffer_end_nanos);
    profile.native_queue_submit_nanos = after
        .native_queue_submit_nanos
        .saturating_sub(before.native_queue_submit_nanos);
    profile.native_timeline_poll_nanos = after
        .native_timeline_poll_nanos
        .saturating_sub(before.native_timeline_poll_nanos);
    profile.native_timeline_wait_nanos = after
        .native_timeline_wait_nanos
        .saturating_sub(before.native_timeline_wait_nanos);
    profile.native_device_wait_idle_nanos = after
        .native_device_wait_idle_nanos
        .saturating_sub(before.native_device_wait_idle_nanos);
    profile.native_command_buffers_allocated = after
        .native_command_buffers_allocated
        .saturating_sub(before.native_command_buffers_allocated);
    profile.native_command_buffers_freed = after
        .native_command_buffers_freed
        .saturating_sub(before.native_command_buffers_freed);
    profile.native_wait_count = after
        .native_wait_count
        .saturating_sub(before.native_wait_count);
    profile.native_device_wait_idle_count = after
        .native_device_wait_idle_count
        .saturating_sub(before.native_device_wait_idle_count);
    profile.native_acquire_nanos = after
        .native_acquire_nanos
        .saturating_sub(before.native_acquire_nanos);
    profile.native_present_nanos = after
        .native_present_nanos
        .saturating_sub(before.native_present_nanos);
    profile.native_present_wait_nanos = after
        .native_present_wait_nanos
        .saturating_sub(before.native_present_wait_nanos);
    profile.native_present_mode = after.native_present_mode;
    profile.native_requested_present_mode = after.native_requested_present_mode;
    profile.native_supported_present_modes = after.native_supported_present_modes;
    profile.native_present_mode_fallback_reason = after.native_present_mode_fallback_reason;
    profile.native_acquired_image_index = after.native_acquired_image_index;
    profile.native_swapchain_generation = after.native_swapchain_generation;
    profile.native_swapchain_image_count = after.native_swapchain_image_count;
    profile.native_surface_min_image_count = after.native_surface_min_image_count;
    profile.native_surface_max_image_count = after.native_surface_max_image_count;
    profile.native_configured_frames_in_flight = after.native_configured_frames_in_flight;
    profile.native_images_in_flight = after.native_images_in_flight;
    profile.native_available_frame_slots = after.native_available_frame_slots;
    profile.gpu_timestamp_status = after.gpu_timestamp_status;
    profile.gpu_scope_nanos = after.gpu_scope_nanos;
    profile.gpu_frame_total_nanos = after.gpu_frame_total_nanos;
}

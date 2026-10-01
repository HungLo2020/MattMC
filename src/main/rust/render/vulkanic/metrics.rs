use std::collections::BTreeMap;
use std::time::Instant;

pub use super::backends::BackendRuntimeMetrics;

/// What the GAL measured while validating, recording and submitting one
/// frame's commands: op counts, hazard analysis, validation and backend
/// timings, native present/swapchain state and GPU timestamps. Renderers embed
/// it in their own frame profiles.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct SubmitProfile {
    pub gal_command_generation_nanos: u64,
    pub gal_submit_total_nanos: u64,
    pub gal_validate_ops_nanos: u64,
    pub gal_validate_handles_nanos: u64,
    pub gal_hazard_analysis_nanos: u64,
    pub backend_encode_nanos: u64,
    pub backend_submit_nanos: u64,
    pub backend_retire_nanos: u64,
    pub native_command_buffer_alloc_nanos: u64,
    pub native_command_buffer_begin_nanos: u64,
    pub native_command_recording_nanos: u64,
    pub native_command_buffer_end_nanos: u64,
    pub native_queue_submit_nanos: u64,
    pub native_timeline_poll_nanos: u64,
    pub native_timeline_wait_nanos: u64,
    pub native_device_wait_idle_nanos: u64,
    // These are per-submission deltas.  Allocation is recorded while encoding
    // and retirement normally happens at presentation (after this profile is
    // sampled), so `freed == 0` in a frame profile is expected and does not
    // represent the number of command buffers still owned by the backend.
    pub native_command_buffers_allocated: u64,
    pub native_command_buffers_freed: u64,
    pub native_wait_count: u64,
    pub native_device_wait_idle_count: u64,
    pub resource_creates_delta: u64,
    pub resource_destroys_delta: u64,
    pub host_write_ops: u64,
    pub host_write_bytes: u64,
    pub barrier_ops: u64,
    pub pass_count: u64,
    pub draw_ops: u64,
    pub draw_indexed_ops: u64,
    pub pipeline_binds: u64,
    pub resource_set_binds: u64,
    pub gpu_timestamp_status: u64,
    /// GPU nanoseconds per frontend-assigned profiling scope.
    pub gpu_scope_nanos: [u64; super::resources::GPU_PROFILE_SCOPE_COUNT],
    pub gpu_frame_total_nanos: u64,
    pub native_acquire_nanos: u64,
    pub native_present_nanos: u64,
    pub native_present_wait_nanos: u64,
    pub native_present_mode: u64,
    pub native_requested_present_mode: u64,
    pub native_supported_present_modes: u64,
    pub native_present_mode_fallback_reason: u64,
    pub native_acquired_image_index: u64,
    pub native_swapchain_generation: u64,
    pub native_swapchain_image_count: u64,
    pub native_surface_min_image_count: u64,
    pub native_surface_max_image_count: u64,
    pub native_configured_frames_in_flight: u64,
    pub native_images_in_flight: u64,
    pub native_available_frame_slots: u64,
    pub gal_hazard_read_events: u64,
    pub gal_hazard_write_events: u64,
    pub gal_hazard_candidates_examined: u64,
    pub gal_hazard_conflicts: u64,
    pub gal_hazard_barriers_applied: u64,
    pub gal_hazard_active_read_entries: u64,
    pub gal_hazard_active_write_entries: u64,
    pub gal_command_ops_before_normalize: u64,
    pub gal_command_ops_after_normalize: u64,
    pub gal_redundant_pipeline_binds_removed: u64,
    pub gal_redundant_resource_set_binds_removed: u64,
    pub gal_redundant_vertex_buffer_binds_removed: u64,
    pub gal_redundant_index_buffer_binds_removed: u64,
    /// Exit cost of the nestable command-recording lifetime guard, including
    /// the outermost deferred-destroy drain.
    pub gal_command_recording_finish_nanos: u64,
    pub gal_command_recording_deferred_destroys: u64,
}

#[inline]
pub fn elapsed_nanos_u64(start: Instant) -> u64 {
    start.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct Metrics {
    pub tracy_enabled: bool,
    pub zones: BTreeMap<&'static str, ZoneMetrics>,
    pub resource_creates: u64,
    pub resource_destroys: u64,
    pub validation_failures: u64,
    pub submissions: u64,
    pub deferred_retires: u64,
}

#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct ZoneMetrics {
    pub count: u64,
    pub total_nanos: u128,
}

impl Metrics {
    pub fn new(tracy_enabled: bool) -> Self {
        Self {
            tracy_enabled,
            ..Self::default()
        }
    }

    pub fn zone(&mut self, name: &'static str) -> TracyZone<'_> {
        let enabled = self.tracy_enabled;
        TracyZone {
            name,
            metrics: self,
            start: Instant::now(),
            enabled,
        }
    }
}

pub struct TracyZone<'a> {
    name: &'static str,
    metrics: &'a mut Metrics,
    start: Instant,
    enabled: bool,
}

impl Drop for TracyZone<'_> {
    fn drop(&mut self) {
        let record = self.metrics.zones.entry(self.name).or_default();
        record.count += 1;
        if self.enabled {
            record.total_nanos += self.start.elapsed().as_nanos();
        }
    }
}

//! Profiling: GAL counters (`Metrics`), the per-submit `SubmitProfile` the
//! GAL fills while validating and submitting, and backend runtime metrics.

use std::time::Instant;

pub use super::backends::BackendRuntimeMetrics;

/// What the GAL measured while validating, recording and submitting one
/// frame's commands: op counts, hazard analysis, validation and backend
/// timings, native present/swapchain state and GPU timestamps. Renderers embed
/// it in their own frame profiles.
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct SubmitProfile {
    /// Caller-measured time spent building the frame's command lists.
    pub gal_command_generation_nanos: u64,
    /// Total time inside `submit_profiled`.
    pub gal_submit_total_nanos: u64,
    /// Time validating command ops.
    pub gal_validate_ops_nanos: u64,
    /// Time checking that every handle the batch references is live.
    pub gal_validate_handles_nanos: u64,
    /// Time in hazard analysis.
    pub gal_hazard_analysis_nanos: u64,
    /// Time the backend spent encoding the batch.
    pub backend_encode_nanos: u64,
    /// Time the backend spent submitting the batch.
    pub backend_submit_nanos: u64,
    /// Caller-measured time retiring completed work after submitting.
    pub backend_retire_nanos: u64,
    /// Vulkan command-buffer allocation time during this submit.
    pub native_command_buffer_alloc_nanos: u64,
    /// Vulkan command-buffer begin time during this submit.
    pub native_command_buffer_begin_nanos: u64,
    /// Time recording native commands during this submit.
    pub native_command_recording_nanos: u64,
    /// Vulkan command-buffer end time during this submit.
    pub native_command_buffer_end_nanos: u64,
    /// Queue-submit time during this submit.
    pub native_queue_submit_nanos: u64,
    /// Time polling the completion timeline during this submit.
    pub native_timeline_poll_nanos: u64,
    /// Time blocked waiting on the completion timeline during this submit.
    pub native_timeline_wait_nanos: u64,
    /// Time in device-wait-idle during this submit.
    pub native_device_wait_idle_nanos: u64,
    // These are per-submission deltas.  Allocation is recorded while encoding
    // and retirement normally happens at presentation (after this profile is
    // sampled), so `freed == 0` in a frame profile is expected and does not
    // represent the number of command buffers still owned by the backend.
    /// Command buffers allocated during this submit.
    pub native_command_buffers_allocated: u64,
    /// Command buffers freed during this submit.
    pub native_command_buffers_freed: u64,
    /// Blocking waits on the completion timeline during this submit.
    pub native_wait_count: u64,
    /// Device-wait-idle calls during this submit.
    pub native_device_wait_idle_count: u64,
    /// Resources created during the submit.
    pub resource_creates_delta: u64,
    /// Resources destroyed during the submit.
    pub resource_destroys_delta: u64,
    /// Host buffer writes in the batch.
    pub host_write_ops: u64,
    /// Bytes written by those host writes.
    pub host_write_bytes: u64,
    /// Barrier ops in the batch.
    pub barrier_ops: u64,
    /// Passes begun in the batch.
    pub pass_count: u64,
    /// Non-indexed draws in the batch.
    pub draw_ops: u64,
    /// Indexed and indexed-indirect draws in the batch.
    pub draw_indexed_ops: u64,
    /// Pipeline binds in the batch (before normalization).
    pub pipeline_binds: u64,
    /// Resource-set binds in the batch (before normalization).
    pub resource_set_binds: u64,
    /// 0 when no GPU timings are available; otherwise the id of the
    /// presentation submission the GPU timings were measured for.
    pub gpu_timestamp_status: u64,
    /// GPU nanoseconds per frontend-assigned profiling scope.
    pub gpu_scope_nanos: [u64; super::resources::GPU_PROFILE_SCOPE_COUNT],
    /// GPU nanoseconds for that whole frame.
    pub gpu_frame_total_nanos: u64,
    /// Swapchain acquire time during this submit.
    pub native_acquire_nanos: u64,
    /// Present time during this submit.
    pub native_present_nanos: u64,
    /// Time waiting for presentation during this submit.
    pub native_present_wait_nanos: u64,
    /// The present mode in use, as the raw Vulkan `VkPresentModeKHR` value.
    pub native_present_mode: u64,
    /// The requested `PresentMode`, as its wire value.
    pub native_requested_present_mode: u64,
    /// Present modes the surface supports, as a bitmask: bit 0 immediate,
    /// bit 1 mailbox, bit 2 FIFO, bit 3 FIFO relaxed, bit 60 any other mode.
    pub native_supported_present_modes: u64,
    /// Why the present mode in use was chosen: 1 the request was supported,
    /// 2 auto-vsync chose FIFO, 3/4 auto-no-vsync chose mailbox/immediate,
    /// 5-7 fallback to FIFO, immediate or FIFO relaxed.
    pub native_present_mode_fallback_reason: u64,
    /// The swapchain image index last acquired.
    pub native_acquired_image_index: u64,
    /// How many times the swapchain has been recreated.
    pub native_swapchain_generation: u64,
    /// Images in the swapchain.
    pub native_swapchain_image_count: u64,
    /// The surface's minimum image count.
    pub native_surface_min_image_count: u64,
    /// The surface's maximum image count (0 means unlimited).
    pub native_surface_max_image_count: u64,
    /// Frames in flight the surface was configured with.
    pub native_configured_frames_in_flight: u64,
    /// Swapchain images currently acquired.
    pub native_images_in_flight: u64,
    /// Frame slots free for the next acquire.
    pub native_available_frame_slots: u64,
    /// Read accesses recorded by hazard analysis.
    pub gal_hazard_read_events: u64,
    /// Write accesses recorded by hazard analysis.
    pub gal_hazard_write_events: u64,
    /// Earlier accesses compared against new ones.
    pub gal_hazard_candidates_examined: u64,
    /// Conflicting accesses found (the submit is rejected).
    pub gal_hazard_conflicts: u64,
    /// Barriers that cleared tracked accesses.
    pub gal_hazard_barriers_applied: u64,
    /// Read accesses still tracked at the end of the batch.
    pub gal_hazard_active_read_entries: u64,
    /// Write accesses still tracked at the end of the batch.
    pub gal_hazard_active_write_entries: u64,
    /// Command ops before normalization.
    pub gal_command_ops_before_normalize: u64,
    /// Command ops after normalization.
    pub gal_command_ops_after_normalize: u64,
    /// Redundant pipeline binds removed.
    pub gal_redundant_pipeline_binds_removed: u64,
    /// Redundant resource-set binds removed.
    pub gal_redundant_resource_set_binds_removed: u64,
    /// Redundant vertex-buffer binds removed.
    pub gal_redundant_vertex_buffer_binds_removed: u64,
    /// Redundant index-buffer binds removed.
    pub gal_redundant_index_buffer_binds_removed: u64,
    /// Exit cost of the nestable command-recording lifetime guard, including
    /// the outermost deferred-destroy drain.
    pub gal_command_recording_finish_nanos: u64,
    /// Caller-recorded count of destroys deferred until the outermost
    /// command-recording scope finished.
    pub gal_command_recording_deferred_destroys: u64,
}

/// Nanoseconds since `start`, saturated to `u64`.
#[inline]
pub fn elapsed_nanos_u64(start: Instant) -> u64 {
    start.elapsed().as_nanos().min(u128::from(u64::MAX)) as u64
}

/// Running counters a GAL keeps across its lifetime.
#[derive(Clone, Debug, Default, Eq, PartialEq)]
pub struct Metrics {
    /// Resources created.
    pub resource_creates: u64,
    /// Resources destroyed on the backend (including deferred ones).
    pub resource_destroys: u64,
    /// Requests rejected by validation.
    pub validation_failures: u64,
    /// Submissions accepted.
    pub submissions: u64,
    /// Destroys deferred until their last submission completed.
    pub deferred_retires: u64,
}

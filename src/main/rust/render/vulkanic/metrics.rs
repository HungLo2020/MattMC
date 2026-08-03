use std::collections::BTreeMap;
use std::time::Instant;

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct WholeFrameProfile {
    pub ffi_decode_nanos: u64,
    pub gui_frontend_nanos: u64,
    pub world_frontend_total_nanos: u64,
    pub world_validate_frame_nanos: u64,
    pub world_batching_nanos: u64,
    pub world_resource_prepare_nanos: u64,
    pub world_prepare_target_query_nanos: u64,
    pub world_prepare_render_resources_nanos: u64,
    pub world_prepare_depth_attachment_nanos: u64,
    pub world_prepare_g_buffer_resources_nanos: u64,
    pub world_prepare_g_buffer_cache_check_nanos: u64,
    pub world_prepare_g_buffer_destroy_nanos: u64,
    pub world_prepare_g_buffer_plan_nanos: u64,
    pub world_prepare_g_buffer_create_nanos: u64,
    pub world_prepare_frame_pass_nanos: u64,
    pub world_mesh_section_expand_group_nanos: u64,
    pub shader_plan_lookup_nanos: u64,
    pub gal_command_generation_nanos: u64,
    pub gal_submit_total_nanos: u64,
    pub gal_validate_ops_nanos: u64,
    pub gal_validate_handles_nanos: u64,
    pub gal_hazard_analysis_nanos: u64,
    pub backend_encode_nanos: u64,
    pub backend_submit_nanos: u64,
    pub backend_retire_nanos: u64,
    pub vulkan_command_buffer_alloc_nanos: u64,
    pub vulkan_command_buffer_begin_nanos: u64,
    pub vulkan_command_recording_nanos: u64,
    pub vulkan_command_buffer_end_nanos: u64,
    pub vulkan_queue_submit_nanos: u64,
    pub vulkan_timeline_poll_nanos: u64,
    pub vulkan_timeline_wait_nanos: u64,
    pub vulkan_device_wait_idle_nanos: u64,
    pub vulkan_command_buffers_allocated: u64,
    pub vulkan_command_buffers_freed: u64,
    pub vulkan_wait_count: u64,
    pub vulkan_device_wait_idle_count: u64,
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
    pub gpu_shadow_depth_nanos: u64,
    pub gpu_terrain_opaque_nanos: u64,
    pub gpu_terrain_cutout_nanos: u64,
    pub gpu_deferred_lighting_nanos: u64,
    pub gpu_composite0_nanos: u64,
    pub gpu_composite1_nanos: u64,
    pub gpu_final_output_nanos: u64,
    pub gpu_frame_total_nanos: u64,
    pub g_buffer_persistent_cache_hits: u64,
    pub g_buffer_persistent_cache_misses: u64,
    pub g_buffer_final_binding_cache_hits: u64,
    pub g_buffer_final_binding_cache_misses: u64,
    pub g_buffer_attachment_creates: u64,
    pub g_buffer_pipeline_creates: u64,
    pub g_buffer_shader_module_creates: u64,
    pub g_buffer_descriptor_creates: u64,
    pub g_buffer_render_target_creates: u64,
    pub g_buffer_resources_retired: u64,
    pub world_prepare_g_buffer_persistent_key_nanos: u64,
    pub world_prepare_g_buffer_persistent_lookup_nanos: u64,
    pub world_prepare_g_buffer_final_key_nanos: u64,
    pub world_prepare_g_buffer_final_lookup_nanos: u64,
    pub world_prepare_g_buffer_final_create_nanos: u64,
    pub world_prepare_frame_target_attachment_query_nanos: u64,
    pub world_prepare_mesh_material_asset_nanos: u64,
    pub world_prepare_metrics_accounting_nanos: u64,
    pub g_buffer_final_pass_creates: u64,
    pub vulkan_acquire_nanos: u64,
    pub vulkan_present_nanos: u64,
    pub vulkan_present_wait_nanos: u64,
    pub vulkan_present_mode: u64,
    pub vulkan_requested_present_mode: u64,
    pub vulkan_supported_present_modes: u64,
    pub vulkan_present_mode_fallback_reason: u64,
    pub vulkan_acquired_image_index: u64,
    pub vulkan_swapchain_generation: u64,
    pub vulkan_swapchain_image_count: u64,
    pub vulkan_surface_min_image_count: u64,
    pub vulkan_surface_max_image_count: u64,
    pub vulkan_configured_frames_in_flight: u64,
    pub vulkan_images_in_flight: u64,
    pub vulkan_available_frame_slots: u64,
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
    pub world_prepare_mesh_cache_scan_nanos: u64,
    pub world_prepare_material_resource_nanos: u64,
    pub world_prepare_mesh_stream_capacity_nanos: u64,
    pub world_prepare_mesh_stream_lookup_nanos: u64,
    pub world_prepare_mesh_stream_grow_nanos: u64,
    pub world_prepare_mesh_resource_nanos: u64,
    pub world_prepare_material_slot_check_nanos: u64,
    pub world_prepare_mesh_slot_check_nanos: u64,
    pub world_prepare_mesh_batch_count: u64,
    pub world_prepare_mesh_stream_required_bytes: u64,
    pub world_prepare_mesh_stream_capacity_bytes: u64,
    pub world_prepare_mesh_stream_grows: u64,
    pub world_mesh_stream_payload_pack_nanos: u64,
    pub world_mesh_draw_record_nanos: u64,
    pub world_mesh_stream_payload_bytes: u64,
    pub world_mesh_dynamic_offset_count: u64,
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

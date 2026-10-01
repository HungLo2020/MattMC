//! Status results written back to Java: success and error codes, the last
//! error, metrics snapshots and GUI/whole-frame submit results.

use crate::render::bridge::*;
use crate::render::worldrender::diagnostics::profile::WholeFrameProfile;
use crate::render::worldrender::diagnostics::gpu_profile_scopes as scopes;

pub fn status_result_from_error(error: &GalError) -> FfiStatusResult {
    FfiStatusResult {
        status: error.code as i32,
        error_domain: error.domain as u32,
        unsupported_feature: unsupported_feature_from_message(error.message.as_str()),
        ..FfiStatusResult::default()
    }
}

pub(crate) fn context_metrics(context: &BridgeContext) -> FfiMetricsSnapshot {
    let mut metrics = FfiMetricsSnapshot::from(context.gal.metrics());
    let backend = context.gal.backend_runtime_metrics();
    metrics.command_lists = backend.command_lists;
    metrics.command_ops = backend.command_ops;
    metrics.backend_submissions = backend.command_batches;
    metrics.backend_waits = backend.native_fences_waited;
    metrics.ffi_calls = context.ffi_calls;
    metrics.ffi_input_bytes = context.ffi_input_bytes;
    metrics.ffi_output_bytes = context.ffi_output_bytes;
    metrics
}

pub(crate) fn status_ok(context: &BridgeContext) -> FfiStatusResult {
    FfiStatusResult {
        metrics: context_metrics(context),
        ..FfiStatusResult::default()
    }
}

pub(crate) fn gui_frame_result_ok(
    context: &BridgeContext,
    stats: GuiSubmitStats,
) -> FfiGuiFrameSubmitResult {
    FfiGuiFrameSubmitResult {
        submission_id: stats.submission_id,
        sprite_count: stats.sprite_count,
        sprite_batch_count: stats.sprite_batch_count,
        cache_hits: stats.cache_hits,
        cache_misses: stats.cache_misses,
        resource_creates: stats.resource_creates,
        command_lists: stats.command_lists,
        command_ops: stats.command_ops,
        metrics: context_metrics(context),
        ..FfiGuiFrameSubmitResult::default()
    }
}

pub(crate) fn whole_frame_result_ok(
    context: &BridgeContext,
    world: WorldPrimitiveSubmitStats,
    gui: GuiSubmitStats,
) -> FfiWholeFrameSubmitResult {
    FfiWholeFrameSubmitResult {
        submission_id: world.submission_id,
        world_segment_count: world.segment_count,
        world_vertex_count: world.vertex_count,
        world_batch_count: world.primitive_batch_count,
        world_draw_count: world.world_draws,
        world_crack_quad_count: world.crack_quad_count,
        world_crack_batch_count: world.crack_batch_count,
        world_crack_draw_count: world.crack_draw_count,
        world_border_quad_count: world.border_quad_count,
        world_border_batch_count: world.border_batch_count,
        world_border_draw_count: world.border_draw_count,
        world_material_quad_count: world.material_quad_count,
        world_material_batch_count: world.material_batch_count,
        world_material_draw_count: world.material_draw_count,
        world_mesh_instance_count: world.mesh_instance_count,
        world_mesh_batch_count: world.mesh_batch_count,
        world_mesh_draw_count: world.mesh_draw_count,
        world_background_clear_count: world.background_clear_count,
        world_background_diagnostic_fallback_count: world.background_diagnostic_fallback_count,
        world_background_sky_type: world.background_sky_type,
        world_background_color_argb: world.background_color_argb,
        depth_attachment_creates: world.depth_attachment_creates,
        depth_attachment_reuses: world.depth_attachment_reuses,
        depth_attachment_retires: world.depth_attachment_retires,
        outline_cache_hits: world.outline_cache_hits,
        outline_cache_misses: world.outline_cache_misses,
        crack_cache_hits: world.crack_cache_hits,
        crack_cache_misses: world.crack_cache_misses,
        border_cache_hits: world.border_cache_hits,
        border_cache_misses: world.border_cache_misses,
        material_cache_hits: world.material_cache_hits,
        material_cache_misses: world.material_cache_misses,
        mesh_cache_hits: world.mesh_cache_hits,
        mesh_cache_misses: world.mesh_cache_misses,
        sprite_count: gui.sprite_count,
        sprite_batch_count: gui.sprite_batch_count,
        gui_mesh_item_count: gui.mesh_item_count,
        gui_mesh_batch_count: gui.mesh_batch_count,
        gui_mesh_draw_count: gui.mesh_draw_count,
        gui_entity_preview_item_count: gui.entity_preview_item_count,
        gui_entity_preview_batch_count: gui.entity_preview_batch_count,
        gui_entity_preview_draw_count: gui.entity_preview_draw_count,
        gui_entity_preview_material_mask: gui.entity_preview_material_mask,
        gui_entity_preview_vertex_count: gui.entity_preview_vertex_count,
        gui_entity_preview_index_count: gui.entity_preview_index_count,
        cache_hits: world.cache_hits.saturating_add(gui.cache_hits),
        cache_misses: world.cache_misses.saturating_add(gui.cache_misses),
        resource_creates: world.resource_creates.saturating_add(gui.resource_creates),
        command_lists: world.command_lists.max(gui.command_lists),
        command_ops: world.command_ops,
        metrics: context_metrics(context),
        profile: FfiWholeFrameProfileSnapshot::from(world.profile),
        ..FfiWholeFrameSubmitResult::default()
    }
}

impl From<WholeFrameProfile> for FfiWholeFrameProfileSnapshot {
    fn from(profile: WholeFrameProfile) -> Self {
        Self {
            ffi_decode_nanos: profile.ffi_decode_nanos,
            gui_frontend_nanos: profile.gui_frontend_nanos,
            world_frontend_total_nanos: profile.world_frontend_total_nanos,
            world_validate_frame_nanos: profile.world_validate_frame_nanos,
            world_batching_nanos: profile.world_batching_nanos,
            world_resource_prepare_nanos: profile.world_resource_prepare_nanos,
            world_prepare_target_query_nanos: profile.world_prepare_target_query_nanos,
            world_prepare_render_resources_nanos: profile.world_prepare_render_resources_nanos,
            world_prepare_depth_attachment_nanos: profile.world_prepare_depth_attachment_nanos,
            world_prepare_g_buffer_resources_nanos: profile.world_prepare_g_buffer_resources_nanos,
            world_prepare_g_buffer_cache_check_nanos: profile
                .world_prepare_g_buffer_cache_check_nanos,
            world_prepare_g_buffer_destroy_nanos: profile.world_prepare_g_buffer_destroy_nanos,
            world_prepare_g_buffer_plan_nanos: profile.world_prepare_g_buffer_plan_nanos,
            world_prepare_g_buffer_create_nanos: profile.world_prepare_g_buffer_create_nanos,
            world_prepare_frame_pass_nanos: profile.world_prepare_frame_pass_nanos,
            world_mesh_section_expand_group_nanos: profile.world_mesh_section_expand_group_nanos,
            shader_plan_lookup_nanos: profile.shader_plan_lookup_nanos,
            gal_command_generation_nanos: profile.gal.gal_command_generation_nanos,
            gal_submit_total_nanos: profile.gal.gal_submit_total_nanos,
            gal_validate_ops_nanos: profile.gal.gal_validate_ops_nanos,
            gal_validate_handles_nanos: profile.gal.gal_validate_handles_nanos,
            gal_hazard_analysis_nanos: profile.gal.gal_hazard_analysis_nanos,
            backend_encode_nanos: profile.gal.backend_encode_nanos,
            backend_submit_nanos: profile.gal.backend_submit_nanos,
            backend_retire_nanos: profile.gal.backend_retire_nanos,
            vulkan_command_buffer_alloc_nanos: profile.gal.native_command_buffer_alloc_nanos,
            vulkan_command_buffer_begin_nanos: profile.gal.native_command_buffer_begin_nanos,
            vulkan_command_recording_nanos: profile.gal.native_command_recording_nanos,
            vulkan_command_buffer_end_nanos: profile.gal.native_command_buffer_end_nanos,
            vulkan_queue_submit_nanos: profile.gal.native_queue_submit_nanos,
            vulkan_timeline_poll_nanos: profile.gal.native_timeline_poll_nanos,
            vulkan_timeline_wait_nanos: profile.gal.native_timeline_wait_nanos,
            vulkan_device_wait_idle_nanos: profile.gal.native_device_wait_idle_nanos,
            vulkan_command_buffers_allocated: profile.gal.native_command_buffers_allocated,
            vulkan_command_buffers_freed: profile.gal.native_command_buffers_freed,
            vulkan_wait_count: profile.gal.native_wait_count,
            vulkan_device_wait_idle_count: profile.gal.native_device_wait_idle_count,
            resource_creates_delta: profile.gal.resource_creates_delta,
            resource_destroys_delta: profile.gal.resource_destroys_delta,
            host_write_ops: profile.gal.host_write_ops,
            host_write_bytes: profile.gal.host_write_bytes,
            barrier_ops: profile.gal.barrier_ops,
            pass_count: profile.gal.pass_count,
            draw_ops: profile.gal.draw_ops,
            draw_indexed_ops: profile.gal.draw_indexed_ops,
            pipeline_binds: profile.gal.pipeline_binds,
            resource_set_binds: profile.gal.resource_set_binds,
            gpu_timestamp_status: profile.gal.gpu_timestamp_status,
            gpu_shadow_depth_nanos: profile.gal.gpu_scope_nanos[usize::from(scopes::SHADOW_DEPTH)],
            gpu_terrain_opaque_nanos: profile.gal.gpu_scope_nanos[usize::from(scopes::TERRAIN_OPAQUE)],
            gpu_terrain_cutout_nanos: profile.gal.gpu_scope_nanos[usize::from(scopes::TERRAIN_CUTOUT)],
            gpu_deferred_lighting_nanos: profile.gal.gpu_scope_nanos[usize::from(scopes::DEFERRED_LIGHTING)],
            gpu_composite0_nanos: profile.gal.gpu_scope_nanos[usize::from(scopes::COMPOSITE_0)],
            gpu_composite1_nanos: profile.gal.gpu_scope_nanos[usize::from(scopes::COMPOSITE_1)],
            gpu_final_output_nanos: profile.gal.gpu_scope_nanos[usize::from(scopes::FINAL_OUTPUT)],
            gpu_frame_total_nanos: profile.gal.gpu_frame_total_nanos,
            g_buffer_persistent_cache_hits: profile.g_buffer_persistent_cache_hits,
            g_buffer_persistent_cache_misses: profile.g_buffer_persistent_cache_misses,
            g_buffer_final_binding_cache_hits: profile.g_buffer_final_binding_cache_hits,
            g_buffer_final_binding_cache_misses: profile.g_buffer_final_binding_cache_misses,
            g_buffer_attachment_creates: profile.g_buffer_attachment_creates,
            g_buffer_pipeline_creates: profile.g_buffer_pipeline_creates,
            g_buffer_shader_module_creates: profile.g_buffer_shader_module_creates,
            g_buffer_descriptor_creates: profile.g_buffer_descriptor_creates,
            g_buffer_render_target_creates: profile.g_buffer_render_target_creates,
            g_buffer_resources_retired: profile.g_buffer_resources_retired,
            world_prepare_g_buffer_persistent_key_nanos: profile
                .world_prepare_g_buffer_persistent_key_nanos,
            world_prepare_g_buffer_persistent_lookup_nanos: profile
                .world_prepare_g_buffer_persistent_lookup_nanos,
            world_prepare_g_buffer_final_key_nanos: profile.world_prepare_g_buffer_final_key_nanos,
            world_prepare_g_buffer_final_lookup_nanos: profile
                .world_prepare_g_buffer_final_lookup_nanos,
            world_prepare_g_buffer_final_create_nanos: profile
                .world_prepare_g_buffer_final_create_nanos,
            world_prepare_frame_target_attachment_query_nanos: profile
                .world_prepare_frame_target_attachment_query_nanos,
            world_prepare_mesh_material_asset_nanos: profile
                .world_prepare_mesh_material_asset_nanos,
            world_prepare_metrics_accounting_nanos: profile.world_prepare_metrics_accounting_nanos,
            g_buffer_final_pass_creates: profile.g_buffer_final_pass_creates,
            vulkan_acquire_nanos: profile.gal.native_acquire_nanos,
            vulkan_present_nanos: profile.gal.native_present_nanos,
            vulkan_present_wait_nanos: profile.gal.native_present_wait_nanos,
            vulkan_present_mode: profile.gal.native_present_mode,
            vulkan_requested_present_mode: profile.gal.native_requested_present_mode,
            vulkan_supported_present_modes: profile.gal.native_supported_present_modes,
            vulkan_present_mode_fallback_reason: profile.gal.native_present_mode_fallback_reason,
            vulkan_acquired_image_index: profile.gal.native_acquired_image_index,
            vulkan_swapchain_generation: profile.gal.native_swapchain_generation,
            vulkan_swapchain_image_count: profile.gal.native_swapchain_image_count,
            vulkan_surface_min_image_count: profile.gal.native_surface_min_image_count,
            vulkan_surface_max_image_count: profile.gal.native_surface_max_image_count,
            vulkan_configured_frames_in_flight: profile.gal.native_configured_frames_in_flight,
            vulkan_images_in_flight: profile.gal.native_images_in_flight,
            vulkan_available_frame_slots: profile.gal.native_available_frame_slots,
            gal_hazard_read_events: profile.gal.gal_hazard_read_events,
            gal_hazard_write_events: profile.gal.gal_hazard_write_events,
            gal_hazard_candidates_examined: profile.gal.gal_hazard_candidates_examined,
            gal_hazard_conflicts: profile.gal.gal_hazard_conflicts,
            gal_hazard_barriers_applied: profile.gal.gal_hazard_barriers_applied,
            gal_hazard_active_read_entries: profile.gal.gal_hazard_active_read_entries,
            gal_hazard_active_write_entries: profile.gal.gal_hazard_active_write_entries,
            gal_command_ops_before_normalize: profile.gal.gal_command_ops_before_normalize,
            gal_command_ops_after_normalize: profile.gal.gal_command_ops_after_normalize,
            gal_redundant_pipeline_binds_removed: profile.gal.gal_redundant_pipeline_binds_removed,
            gal_redundant_resource_set_binds_removed: profile
                .gal.gal_redundant_resource_set_binds_removed,
            gal_redundant_vertex_buffer_binds_removed: profile
                .gal.gal_redundant_vertex_buffer_binds_removed,
            gal_redundant_index_buffer_binds_removed: profile
                .gal.gal_redundant_index_buffer_binds_removed,
            world_prepare_mesh_cache_scan_nanos: profile.world_prepare_mesh_cache_scan_nanos,
            world_prepare_material_resource_nanos: profile.world_prepare_material_resource_nanos,
            world_prepare_mesh_stream_capacity_nanos: profile
                .world_prepare_mesh_stream_capacity_nanos,
            world_prepare_mesh_stream_lookup_nanos: profile.world_prepare_mesh_stream_lookup_nanos,
            world_prepare_mesh_stream_grow_nanos: profile.world_prepare_mesh_stream_grow_nanos,
            world_prepare_mesh_resource_nanos: profile.world_prepare_mesh_resource_nanos,
            world_prepare_material_slot_check_nanos: profile
                .world_prepare_material_slot_check_nanos,
            world_prepare_mesh_slot_check_nanos: profile.world_prepare_mesh_slot_check_nanos,
            world_prepare_mesh_batch_count: profile.world_prepare_mesh_batch_count,
            world_prepare_mesh_stream_required_bytes: profile
                .world_prepare_mesh_stream_required_bytes,
            world_prepare_mesh_stream_capacity_bytes: profile
                .world_prepare_mesh_stream_capacity_bytes,
            world_prepare_mesh_stream_grows: profile.world_prepare_mesh_stream_grows,
            world_mesh_stream_payload_pack_nanos: profile.world_mesh_stream_payload_pack_nanos,
            world_mesh_draw_record_nanos: profile.world_mesh_draw_record_nanos,
            world_mesh_stream_payload_bytes: profile.world_mesh_stream_payload_bytes,
            world_mesh_dynamic_offset_count: profile.world_mesh_dynamic_offset_count,
            gui_mesh_prepare_nanos: profile.gui_mesh_prepare_nanos,
            gui_mesh_lower_nanos: profile.gui_mesh_lower_nanos,
            gpu_distant_horizons_opaque_nanos: profile.gal.gpu_scope_nanos[usize::from(scopes::DISTANT_HORIZONS_OPAQUE)],
            world_mesh_page_indirect_batch_count: profile.world_mesh_page_indirect_batch_count,
            world_mesh_page_indirect_run_count: profile.world_mesh_page_indirect_run_count,
            world_mesh_dynamic_terrain_batch_count: profile.world_mesh_dynamic_terrain_batch_count,
            world_mesh_dynamic_non_terrain_batch_count: profile
                .world_mesh_dynamic_non_terrain_batch_count,
            world_mesh_terrain_translucent_batch_count: profile
                .world_mesh_terrain_translucent_batch_count,
            whole_frame_native_total_nanos: profile.whole_frame_native_total_nanos,
            world_post_submit_confirm_nanos: profile.world_post_submit_confirm_nanos,
            gal_command_recording_finish_nanos: profile.gal.gal_command_recording_finish_nanos,
            gal_command_recording_deferred_destroys: profile
                .gal.gal_command_recording_deferred_destroys,
        }
    }
}

pub(crate) fn status_error(context: Option<&BridgeContext>, error: &GalError) -> FfiStatusResult {
    let mut status = status_result_from_error(error);
    if let Some(context) = context {
        status.metrics = context_metrics(context);
    }
    status
}

pub(crate) fn set_last_error(context: &mut BridgeContext, error: &GalError) {
    context.last_error = error.to_string();
}


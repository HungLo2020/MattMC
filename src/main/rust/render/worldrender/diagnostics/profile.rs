//! The whole-frame profile: renderer timings and counters for one frame,
//! with the GAL's own submit profile embedded. The bridge reports it to Java
//! as `FfiWholeFrameProfileSnapshot`.

use crate::render::vulkanic::metrics::SubmitProfile;

/// One whole frame's CPU timings and counters: bridge decoding, the GUI and
/// world renderers' phases and G-buffer cache activity, plus everything the
/// GAL measured while submitting (`gal`).
#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct WholeFrameProfile {
    /// Measured by the GAL during validation, recording and submission.
    pub gal: SubmitProfile,
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
    pub gui_mesh_prepare_nanos: u64,
    pub gui_mesh_lower_nanos: u64,
    pub world_mesh_page_indirect_batch_count: u64,
    pub world_mesh_page_indirect_run_count: u64,
    pub world_mesh_dynamic_terrain_batch_count: u64,
    pub world_mesh_dynamic_non_terrain_batch_count: u64,
    pub world_mesh_terrain_translucent_batch_count: u64,
    /// Complete native whole-frame boundary, including world/GUI frontend
    /// work, GAL submission, and post-submit ownership confirmation.
    pub whole_frame_native_total_nanos: u64,
    /// Work after GAL accepts the submission: resource ownership commits,
    /// deferred retirement, and optional observation/capture completion.
    pub world_post_submit_confirm_nanos: u64,
}

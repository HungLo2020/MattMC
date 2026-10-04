//! Destruction of the renderer's GPU resources.

use super::*;

impl WorldPrimitiveFrontend {
    pub fn reset(&mut self, gal: &mut VulkanicGal) {
        self.destroy_resources(gal);
        if let Some(fabulous) = self.fabulous_attachment_set.take() {
            fabulous.destroy(gal);
        }
        self.world_text.reset(gal);
        self.reset_candidate_source_occupancy_stability();
        self.generation = 0;
        self.border_asset_generation = 0;
        self.border_asset_override = None;
        self.border_asset_payload_bytes = 0;
        self.border_asset_update_failures = 0;
        self.crack_asset_generation = 0;
        self.crack_asset_overrides.clear();
        self.crack_asset_payload_bytes = 0;
        self.crack_asset_update_failures = 0;
        self.material_asset_generation = 0;
        self.material_asset_overrides.clear();
        self.material_asset_payload_bytes = 0;
        self.material_asset_update_failures = 0;
        self.mesh_asset_generation = 0;
        self.mesh_assets.clear();
        self.source_terrain_validated_identities.clear();
        self.mesh_batch_plan_cache.clear();
        self.mesh_texture_assets.clear();
        self.mesh_texture_animation_generation =
            self.mesh_texture_animation_generation.wrapping_add(1);
        self.staged_atlas_animations.clear();
        self.latest_atlas_animation_observations.clear();
        self.latest_atlas_animation_texture = None;
        self.pending_atlas_animation = None;
        self.pending_atlas_animation_event = None;
        self.pending_atlas_animation_failed = false;
        self.retry_atlas_animation_events.clear();
        self.atlas_animation_patch_uploads = 0;
        self.atlas_animation_patch_bytes = 0;
        self.atlas_animation_empty_ticks = 0;
        self.mesh_asset_payload_bytes = 0;
        self.mesh_asset_update_failures = 0;
        self.lod_asset_generation = 0;
        self.lod_column_assets.clear();
        self.lod_expanded_column_assets.clear();
        self.lod_gpu_column_assets.clear();
        self.lod_material_provenance.clear();
        self.lod_textured_column_plans.clear();
        self.lod_voxel_source_meshes.clear();
        self.lod_textured_gpu_column_assets.clear();
        self.lod_asset_update_failures = 0;
        self.pending_depth_attachment_retires = 0;
    }

    pub(super) fn destroy_resources(&mut self, gal: &mut VulkanicGal) {
        // GAL defers physical destruction of accepted upload leases until
        // their submission completes, including teardown without an idle wait.
        let _ = self.atlas_animation_uploads.release(gal);
        self.lod_opaque_pass_resources.destroy(gal);
        self.lod_forward_opaque_pass_resources.destroy(gal);
        self.lod_exact_atlas_opaque_pass_resources.destroy(gal);
        if let Some(resources) = self.lod_exact_atlas_forward_opaque_pass_resources.as_mut() {
            resources.destroy(gal);
        }
        self.lod_exact_atlas_forward_opaque_pass_resources = None;
        for mut resources in [
            self.lod_exact_atlas_forward_transparent_side_pass_resources
                .take(),
            self.lod_exact_atlas_forward_transparent_up_pass_resources
                .take(),
            self.lod_exact_atlas_forward_water_pass_resources.take(),
        ]
        .into_iter()
        .flatten()
        {
            resources.destroy(gal);
        }
        for mut resources in [
            self.lod_exact_atlas_deferred_transparent_side_pass_resources
                .take(),
            self.lod_exact_atlas_deferred_transparent_up_pass_resources
                .take(),
            self.lod_exact_atlas_deferred_water_pass_resources.take(),
        ]
        .into_iter()
        .flatten()
        {
            resources.destroy(gal);
        }
        self.lod_exact_atlas_source_pass_resources.destroy(gal);
        self.lod_transparent_pass_resources.destroy(gal);
        self.lod_water_pass_resources.destroy(gal);
        if let Some(resources) = self.lod_direct_composition_resources.take() {
            resources.destroy(gal);
        }
        self.lod_direct_composition_initialized = false;
        self.pending_lod_direct_composition_written = false;
        self.lod_ssao_initialized = false;
        self.pending_lod_ssao_written = false;
        self.lod_vanilla_sample_state_initialized = false;
        self.pending_lod_vanilla_sample_state_established = false;
        self.lod_source_pass_resources.destroy(gal);
        self.discard_distant_horizons_generic_source_buffers(gal);
        self.lod_source_targets.destroy(gal);
        self.pending_distant_horizons_source_targets = None;
        self.pending_candidate_source_distant_depth = None;
        self.release_source_color_consumers(gal);
        self.destroy_source_terrain_color_pass_targets(gal);
        self.lod_gpu_residency.destroy(gal);
        self.lod_textured_gpu_residency.destroy(gal);
        self.pending_source_terrain_frame_transactions.clear();
        self.pending_lowered_source_terrain_submission = None;
        self.pending_lowered_source_terrain_geometry_uploads.clear();
        self.destroy_lowered_textured_material_source_resources(gal);
        self.destroy_lowered_source_terrain_pipeline_resources(gal);
        self.destroy_lowered_source_terrain_pack_resources(gal);
        if let Some(runtime) = self.shader_runtime.take() {
            let _ = runtime.destroy(gal);
        }
        self.candidate_colored_light_runtime = false;
        self.candidate_source_asset_runtime = false;
        self.candidate_source_asset_error = None;
        self.clear_candidate_source_resource_snapshot();
        self.clear_frame_pass(gal);
        self.destroy_render_resources(gal);
    }

    pub(super) fn destroy_render_resources(&mut self, gal: &mut VulkanicGal) {
        self.entity_outline_targets_initialized = false;
        self.pending_entity_outline_targets_written = false;
        self.destroy_entity_outline_mask_gpu_resources(gal);
        if let Some(sets) = self.entity_outline_post_effect_sets.take() {
            for handle in sets.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        if let Some(pipelines) = self.entity_outline_post_effect_pipelines.take() {
            for handle in pipelines.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        let resources = std::mem::take(&mut self.resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        let sky_disc_resources = std::mem::take(&mut self.sky_disc_resources);
        for (_, resources) in sky_disc_resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        let sky_disc_forward_resources = std::mem::take(&mut self.sky_disc_forward_resources);
        for (_, resources) in sky_disc_forward_resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        if let Some(resources) = self.entity_outline_targets.take() {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        self.destroy_crack_resources(gal);
        self.destroy_border_resources(gal);
        self.destroy_material_resources(gal);
        self.destroy_mesh_resources(gal);
        self.destroy_lowered_source_terrain_program_layouts(gal);
        self.destroy_mesh_instance_streams(gal);
        self.destroy_source_terrain_frame_streams(gal);
        self.destroy_mesh_texture_resources(gal);
        self.destroy_mesh_pipeline_resources(gal);
        if let Some(layout) = self.builtin_terrain_lightmap_layout.take() {
            let _ = gal.destroy(layout);
        }
        let retired = self.destroy_g_buffer_resources(gal);
        self.pending_g_buffer_resources_retired = self
            .pending_g_buffer_resources_retired
            .saturating_add(retired);
        if let Some(depth) = self.depth_attachment.take() {
            for handle in depth.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
            self.pending_depth_attachment_retires =
                self.pending_depth_attachment_retires.saturating_add(1);
        }
        if let Some(owner) = self.oriented_world_target.take() {
            self.clear_frame_passes_for_targets(gal, &[owner.target]);
            for handle in owner.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        if let Some(owner) = self.canonical_world_target.take() {
            self.clear_frame_passes_for_targets(gal, &[owner.target]);
            for handle in owner.handles_in_destroy_order() {
                let _ = gal.destroy(handle);
            }
        }
        self.flush_deferred_mesh_resource_destroys(gal);
    }
}

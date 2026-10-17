//! Destruction and release of lowered source program resources.

use super::*;

impl WorldPrimitiveFrontend {
    pub(crate) fn destroy_lowered_source_terrain_resources(&mut self, gal: &mut VulkanicGal) {
        let frame_resources = std::mem::take(&mut self.lowered_source_terrain_frame_data_resources);
        for (_, resources) in frame_resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        let mut geometry_resources =
            std::mem::take(&mut self.lowered_source_terrain_geometry_resources)
                .into_iter().collect::<Vec<_>>();
        geometry_resources.sort_unstable_by(|a, b| a.0.cmp(&b.0));
        for (_, resources) in geometry_resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        self.source_terrain_geometry_pages.destroy_all(gal);
        self.source_terrain_range_memo.clear();
        self.clear_retained_source_terrain_meshes();
        self.pending_lowered_source_terrain_geometry_uploads.clear();
    }

    pub(crate) fn destroy_lowered_source_terrain_resources_for_keys(
        &mut self,
        gal: &mut VulkanicGal,
        keys: Vec<LoweredSourceTerrainDataKey>,
    ) {
        for key in &keys {
            let frame_keys = self
                .lowered_source_terrain_frame_data_resources
                .keys()
                .filter(|frame_key| frame_key.geometry == *key)
                .cloned()
                .collect::<Vec<_>>();
            for frame_key in frame_keys {
                if let Some(resources) = self
                    .lowered_source_terrain_frame_data_resources
                    .remove(&frame_key)
                {
                    for handle in resources.handles_in_destroy_order() {
                        let _ = gal.retire(handle);
                    }
                }
            }
        }
        if !self.source_terrain_range_memo.is_empty() {
            let evicted = keys
                .iter()
                .filter(|key| key.abi == SourceGeometryAbi::Terrain)
                .map(|key| (key.mesh_key, key.mesh_generation))
                .collect::<std::collections::HashSet<_>>();
            if !evicted.is_empty() {
                self.source_terrain_range_memo
                    .retain(|key, _| !evicted.contains(&(key.mesh_key, key.mesh_generation)));
            }
        }
        self.forget_retained_source_terrain_meshes(
            keys.iter().filter(|key| key.abi == SourceGeometryAbi::Terrain).map(|key| key.mesh_key),
        );
        for key in keys {
            self.pending_lowered_source_terrain_geometry_uploads
                .remove(&key);
            if let Some(resources) = self.lowered_source_terrain_geometry_resources.remove(&key) {
                if let Some((vertex, index)) = resources.paged {
                    // Another in-flight or not-yet-submitted frame may still
                    // read these ranges; reuse them only after completion.
                    let release_after = gal.next_submission_id();
                    self.source_terrain_geometry_pages.release_after(release_after, vertex);
                    self.source_terrain_geometry_pages.release_after(release_after, index);
                }
                for handle in resources.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
        }
    }

    /// Releases bindings that refer to a source stream buffer about to be
    /// destroyed. A completed slot keeps its bindings because the next frame
    /// uses the same backing resource with new dynamic offsets.
    pub(crate) fn destroy_lowered_source_terrain_frame_data_resources_for_stream_buffer(
        &mut self,
        gal: &mut VulkanicGal,
        stream_buffer: Handle,
    ) {
        let frame_keys = self
            .lowered_source_terrain_frame_data_resources
            .keys()
            .filter(|key| key.stream_buffer == stream_buffer)
            .cloned()
            .collect::<Vec<_>>();
        for frame_key in frame_keys {
            if let Some(resources) = self
                .lowered_source_terrain_frame_data_resources
                .remove(&frame_key)
            {
                for handle in resources.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
        }
    }

    pub(crate) fn destroy_lowered_textured_material_source_resources(&mut self, gal: &mut VulkanicGal) {
        let frame_resources =
            std::mem::take(&mut self.lowered_textured_material_source_frame_data_resources);
        for (_, resources) in frame_resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        let pack_resources =
            std::mem::take(&mut self.lowered_textured_material_source_pack_resources);
        for (_, resources) in pack_resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        self.destroy_lowered_textured_material_source_local_texture_resources(gal);
        let pipelines =
            std::mem::take(&mut self.lowered_textured_material_source_pipeline_resources);
        for (_, resources) in pipelines {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
        let layouts = std::mem::take(&mut self.lowered_textured_material_source_program_layouts);
        for (_, layouts) in layouts {
            for handle in layouts.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }

    pub(crate) fn destroy_lowered_textured_material_source_frame_data_resources_for_stream_buffer(
        &mut self,
        gal: &mut VulkanicGal,
        stream_buffer: Handle,
    ) {
        let keys = self
            .lowered_textured_material_source_frame_data_resources
            .keys()
            .filter(|key| key.stream_buffer == stream_buffer)
            .cloned()
            .collect::<Vec<_>>();
        for key in keys {
            if let Some(resources) = self
                .lowered_textured_material_source_frame_data_resources
                .remove(&key)
            {
                for handle in resources.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
        }
    }

    pub(crate) fn release_lightmap_dependent_source_pack_resources(&mut self, gal: &mut VulkanicGal) {
        self.release_role_dependent_source_pack_resources(gal, |role| {
            *role == TerrainSourceResourceRole::Lightmap
        });
    }

    /// Destroys source set-one consumers that bind a colored-light or puddle
    /// volume role before the runtime replaces or drops that volume. Cached
    /// pack sets are otherwise retired lazily when a replacement set is
    /// requested, which is after the volume samplers they reference would be
    /// destroyed (a toggle that re-sizes the volume, such as enabling DH).
    pub(crate) fn release_voxel_volume_dependent_source_pack_resources(&mut self, gal: &mut VulkanicGal) {
        self.release_role_dependent_source_pack_resources(gal, |role| {
            matches!(
                role,
                TerrainSourceResourceRole::ColoredVoxelOccupancy
                    | TerrainSourceResourceRole::ColoredVoxelLightCurrent
                    | TerrainSourceResourceRole::ColoredVoxelLightPrevious
                    | TerrainSourceResourceRole::PuddleOccupancy
            )
        });
    }

    pub(crate) fn release_role_dependent_source_pack_resources(
        &mut self,
        gal: &mut VulkanicGal,
        binds_role: impl Fn(&TerrainSourceResourceRole) -> bool,
    ) {
        let binds = |generations: &[(TerrainSourceResourceRole, u64)]| {
            generations.iter().any(|(role, _)| binds_role(role))
        };
        let terrain = self
            .lowered_source_terrain_pack_resources
            .keys()
            .filter(|key| binds(&key.resource_generations))
            .cloned()
            .collect();
        self.destroy_lowered_source_terrain_pack_resources_for_keys(gal, terrain);
        let textured = self
            .lowered_textured_material_source_pack_resources
            .keys()
            .filter(|key| binds(&key.resource_generations))
            .cloned()
            .collect();
        self.destroy_lowered_textured_material_source_pack_resources_for_keys(gal, textured);
        let entity: Vec<_> = self
            .lowered_entity_source_pack_resources
            .keys()
            .filter(|key| binds(&key.resource_generations))
            .cloned()
            .collect();
        self.destroy_lowered_entity_source_pack_resources_for_keys(gal, entity);
        self.lod_exact_atlas_source_pass_resources.destroy(gal);
        self.lod_source_pass_resources.release_pack_resources(gal, binds);
    }

    pub(crate) fn destroy_lowered_source_terrain_pack_resources(&mut self, gal: &mut VulkanicGal) {
        let resources = std::mem::take(&mut self.lowered_source_terrain_pack_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }

    pub(crate) fn destroy_lowered_textured_material_source_pack_resources(&mut self, gal: &mut VulkanicGal) {
        let resources = std::mem::take(&mut self.lowered_textured_material_source_pack_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }

    pub(crate) fn destroy_lowered_textured_material_source_pack_resources_for_keys(
        &mut self,
        gal: &mut VulkanicGal,
        keys: Vec<LoweredTexturedMaterialSourcePackKey>,
    ) {
        for key in keys {
            if let Some(resources) = self
                .lowered_textured_material_source_pack_resources
                .remove(&key)
            {
                for handle in resources.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
        }
    }

    pub(crate) fn destroy_lowered_entity_source_pack_resources(&mut self, gal: &mut VulkanicGal) {
        self.local_source_pack_memo = FrameMemo::default();
        let resources = std::mem::take(&mut self.lowered_entity_source_pack_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }

    /// Drops every cached set that binds shared source inputs (DH depth
    /// targets, material and pack textures, color targets): fullscreen
    /// plans and stages, entity/terrain/material pack sets and the final
    /// output cache. Call before those inputs are destroyed.
    pub(crate) fn release_cached_source_consumers(&mut self, gal: &mut VulkanicGal) {
        if let Some(runtime) = self.shader_runtime.as_ref() {
            runtime.release_fullscreen_consumers(gal);
        }
        self.destroy_lowered_entity_source_pack_resources(gal);
        self.destroy_lowered_source_terrain_pack_resources(gal);
        self.destroy_lowered_textured_material_source_resources(gal);
        self.source_final_output_cache.destroy(gal);
    }

    pub(crate) fn destroy_lowered_entity_source_resources(&mut self, gal: &mut VulkanicGal) {
        self.destroy_lowered_entity_source_pack_resources(gal);
        self.local_source_pipeline_memo = FrameMemo::default();
        let pipelines = std::mem::take(&mut self.lowered_entity_source_pipeline_resources);
        for (_, resources) in pipelines {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }

    pub(crate) fn destroy_lowered_entity_source_pack_resources_for_keys(
        &mut self,
        gal: &mut VulkanicGal,
        keys: Vec<LoweredEntitySourcePackKey>,
    ) {
        if !keys.is_empty() {
            // Memoized handles may name the sets being destroyed.
            self.local_source_pack_memo = FrameMemo::default();
        }
        for key in keys {
            if let Some(resources) = self.lowered_entity_source_pack_resources.remove(&key) {
                for handle in resources.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
        }
    }

    pub(crate) fn destroy_lowered_textured_material_source_local_texture_resources(
        &mut self,
        gal: &mut VulkanicGal,
    ) {
        let local_pack_keys = self
            .lowered_textured_material_source_pack_resources
            .keys()
            .filter(|key| key.local_texture.is_some())
            .cloned()
            .collect::<Vec<_>>();
        self.destroy_lowered_textured_material_source_pack_resources_for_keys(gal, local_pack_keys);
        let bindings =
            std::mem::take(&mut self.lowered_textured_material_source_local_texture_resources);
        for (_, resources) in bindings {
            let _ = gal.retire(resources.combined_sampler);
        }
        let textures = std::mem::take(&mut self.source_material_texture_resources);
        self.source_material_texture_upload_confirmed.clear();
        self.source_material_texture_resident.clear();
        self.source_material_texture_staged.clear();
        self.source_material_texture_upload_operations.clear();
        for (_, resources) in textures {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }

    pub(crate) fn destroy_lowered_source_terrain_pack_resources_for_keys(
        &mut self,
        gal: &mut VulkanicGal,
        keys: Vec<LoweredSourceTerrainPackKey>,
    ) {
        for key in keys {
            if let Some(resources) = self.lowered_source_terrain_pack_resources.remove(&key) {
                for handle in resources.handles_in_destroy_order() {
                    let _ = gal.retire(handle);
                }
            }
        }
    }

    pub(crate) fn destroy_lowered_source_terrain_program_layouts(&mut self, gal: &mut VulkanicGal) {
        let layouts = std::mem::take(&mut self.lowered_source_terrain_program_layouts);
        for (_, layouts) in layouts {
            for handle in layouts.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }

    pub(crate) fn destroy_lowered_source_terrain_pipeline_resources(&mut self, gal: &mut VulkanicGal) {
        let resources = std::mem::take(&mut self.lowered_source_terrain_pipeline_resources);
        for (_, resources) in resources {
            for handle in resources.handles_in_destroy_order() {
                let _ = gal.retire(handle);
            }
        }
    }
}

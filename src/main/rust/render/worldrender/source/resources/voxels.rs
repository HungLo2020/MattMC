//! Candidate colored-light and private terrain occupancy volumes.

use super::*;

impl WorldPrimitiveFrontend {
    #[cfg(test)]
    pub(crate) fn prepare_source_colored_light_for_test(
        &mut self,
        gal: &mut VulkanicGal,
        runtime_generation: u64,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<bool> {
        self.ensure_shader_runtime(gal, runtime_generation)?;
        let preparation = self
            .shader_runtime
            .as_ref()
            .ok_or_else(|| GalError::backend("shader runtime vanished before source preparation"))?
            .candidate_colored_light_preparation(
                frame.voxel_volume.world_generation,
                frame.voxel_volume.resource_generation,
                frame.lod_render_frame.camera_world_position,
            )?
            .ok_or_else(|| {
                GalError::unsupported_feature(
                    "source test requested colored-light preparation without a source volume requirement",
                )
            })?;
        if !self
            .shader_runtime
            .as_ref()
            .expect("shader runtime checked before source preparation")
            .candidate_colored_light_runtime_compatible(&preparation.descriptor)
        {
            self.release_voxel_volume_dependent_source_pack_resources(gal);
        }
        self.shader_runtime
            .as_mut()
            .expect("shader runtime checked before source preparation")
            .ensure_candidate_colored_light_runtime(gal, preparation)
    }

    /// Source discovery prepares only Rust-owned semantic volume resources.
    /// It deliberately does not enable source-derived terrain execution: the
    /// internal terrain plan remains the production route until a complete
    /// source plan is explicitly admitted.
    pub(crate) fn ensure_candidate_colored_light_for_frame(
        &mut self,
        gal: &mut VulkanicGal,
        runtime_generation: u64,
        frame: &WorldPrimitiveFrame,
    ) -> GalResult<bool> {
        // Loading/menu frames explicitly carry no world background semantics.
        // They are not a custom dimension and must not enter terrain-source
        // selection before the real client level has supplied a sky type.
        if !frame.background.enabled {
            self.clear_candidate_colored_light_runtime(gal)?;
            self.clear_candidate_source_asset_runtime(gal)?;
            return Ok(false);
        }
        // The internal Rust shader graph remains independent of a discovered
        // pack source until the complete selected-source route is explicitly
        // requested. Preparing occupancy/flood-fill resources with no active
        // consumer only burdens ordinary shaders-on gameplay.
        if !self.runtime_source_preparation_requested() {
            // A disabled source route is the steady state for vanilla and
            // direct DH. Do not repeatedly run the source teardown there:
            // that teardown also invalidates the shared Rust lightmap sets,
            // forcing every direct DH segment to rebuild descriptors in the
            // graph assembled immediately below. A transition away from an
            // admitted/prepared source still performs the complete ordered
            // teardown exactly once.
            if self.candidate_colored_light_runtime {
                self.clear_candidate_colored_light_runtime(gal)?;
            }
            if self.candidate_source_asset_runtime
                || self.candidate_source_resource_snapshot.is_some()
                || self.candidate_source_frame_target_snapshot.is_some()
                || self.source_execution_armed
            {
                self.clear_candidate_source_asset_runtime(gal)?;
            }
            return Ok(false);
        }
        let Some(scope) = terrain_program_scope_for_sky_type(frame.background.sky_type)? else {
            self.clear_candidate_colored_light_runtime(gal)?;
            self.clear_candidate_source_asset_runtime(gal)?;
            return Ok(false);
        };
        if !frame.voxel_volume.enabled {
            self.clear_candidate_colored_light_runtime(gal)?;
            self.clear_candidate_source_asset_runtime(gal)?;
            return Ok(false);
        }
        // Observe the exact visible input on every selected-source frame. The
        // owned volume uses this identity to track settling, but source asset
        // assembly is exact-frame work and must not wait for a second
        // identical visible list: normal section streaming may legitimately
        // change that list every frame. The resource set below is rebuilt only
        // as a small immutable semantic snapshot; all heavyweight resources
        // remain generation-owned caches and source execution still requires
        // its current-frame resources to be complete and validated.
        let _source_input_stable = !self.source_preparation_requires_stable_input()
            || self.candidate_source_occupancy_input_is_stable(frame)?;
        self.ensure_shader_runtime(gal, runtime_generation)?;
        if frame.shader_environment.enabled {
            self.shader_runtime
                .as_mut()
                .expect("shader runtime is installed before lightmap observation")
                .observe_vanilla_lightmap(
                    frame.shader_environment.world_generation,
                    frame.shader_environment.vanilla_lightmap,
                )?;
        }
        // The first world frame may contain sky and entities before terrain
        // streams in. Its selected pack still needs discovery and resource
        // preparation; an empty terrain list cannot select a vanilla frame.
        self.observe_shader_pack_source_candidate_for_scope(scope);
        // A discovered source candidate is not a selected production route.
        // Keep normal whole-frame execution independent of a private feature
        // that a particular backend cannot yet provide.
        if !supports_private_colored_light_volume(gal) {
            self.clear_candidate_colored_light_runtime(gal)?;
            self.clear_candidate_source_asset_runtime(gal)?;
            return Ok(false);
        }
        let descriptor = self
            .shader_runtime
            .as_ref()
            .expect("shader runtime is installed before source preparation")
            .candidate_colored_light_descriptor(
                frame.voxel_volume.world_generation,
                frame.voxel_volume.resource_generation,
                frame.voxel_volume.camera_world_position,
            )?;
        let Some(descriptor) = descriptor else {
            self.clear_candidate_colored_light_runtime(gal)?;
            self.clear_candidate_source_asset_runtime(gal)?;
            return Ok(false);
        };
        let runtime_compatible = self
            .shader_runtime
            .as_ref()
            .expect("shader runtime is installed before colored-light compatibility check")
            .candidate_colored_light_runtime_compatible(&descriptor);
        let replaced = if runtime_compatible {
            false
        } else {
            self.release_voxel_volume_dependent_source_pack_resources(gal);
            let preparation = self
                .shader_runtime
                .as_ref()
                .expect("shader runtime is installed before source preparation")
                .candidate_colored_light_preparation(
                    frame.voxel_volume.world_generation,
                    frame.voxel_volume.resource_generation,
                    frame.voxel_volume.camera_world_position,
                )?
                .ok_or_else(|| {
                    GalError::invalid_argument(
                        "colored-light descriptor disappeared before runtime preparation",
                    )
                })?;
            self.shader_runtime
                .as_mut()
                .expect("shader runtime is installed before colored-light replacement")
                .ensure_candidate_colored_light_runtime(gal, preparation)?
        };
        self.candidate_colored_light_runtime = true;
        // PNG preparation is intentionally independent of selection, but it
        // shares the discovered source scope and volume lifetime. A broken or
        // incomplete pack snapshot is recorded as unavailable without
        // changing the active fixture route.
        self.ensure_candidate_source_assets_for_frame(
            gal,
            frame.voxel_volume.world_generation,
            frame.frame_id,
            false,
            source_frame_includes_distant_horizons(frame),
        )?;
        let puddle_descriptor = self.candidate_puddle_descriptor_for_frame(frame)?;
        if self
            .shader_runtime
            .as_ref()
            .expect("shader runtime is installed before puddle preparation")
            .candidate_puddle_runtime_retires(puddle_descriptor)
        {
            self.release_voxel_volume_dependent_source_pack_resources(gal);
        }
        let puddle_replaced = match puddle_descriptor {
            Some(descriptor) => self
                .shader_runtime
                .as_mut()
                .expect("shader runtime is installed before puddle preparation")
                .ensure_candidate_puddle_runtime(gal, descriptor)?,
            None => {
                self.shader_runtime
                    .as_mut()
                    .expect("shader runtime is installed before puddle cleanup")
                    .clear_candidate_puddle_runtime(gal)?;
                false
            }
        };
        // Reassemble diagnostics after the D3 and 2D private runtimes exist
        // so the common source-resource table reflects only confirmed,
        // generation-matched semantic fields.
        let previously_armed = self.source_execution_armed;
        self.ensure_candidate_source_assets_for_frame(
            gal,
            frame.voxel_volume.world_generation,
            frame.frame_id,
            false,
            source_frame_includes_distant_horizons(frame),
        )?;
        // The source route is chosen before the ordinary graph's color
        // preparation. Stage the current frame's named targets here so a
        // previously confirmed route sees complete roles at that decision.
        self.prepare_candidate_source_color_resources_for_admission(
            gal,
            frame.voxel_volume.world_generation,
            Extent3d {
                width: frame.viewport_width,
                height: frame.viewport_height,
                depth: 1,
            },
            source_frame_includes_distant_horizons(frame),
        )?;
        if previously_armed
            && self.candidate_source_missing_resource_roles.is_empty()
            && self.candidate_source_resource_snapshot.is_some()
            && self.candidate_source_asset_error.is_none()
        {
            self.source_execution_armed = true;
        }
        Ok(replaced || puddle_replaced)
    }

    pub(crate) fn clear_candidate_colored_light_runtime(&mut self, gal: &mut VulkanicGal) -> GalResult<()> {
        if self.shader_runtime.as_ref().is_some_and(|runtime| {
            runtime.has_private_terrain_occupancy()
        }) {
            self.release_voxel_volume_dependent_source_pack_resources(gal);
        }
        if let Some(runtime) = self.shader_runtime.as_mut() {
            // Test-only/private callers may install a colored-light runtime
            // directly without making it a discovered source candidate. Do
            // not tear that independent preparation down merely because this
            // frame has no source pack; candidate-owned puddle state always
            // follows source discovery and may be retired here.
            if self.candidate_colored_light_runtime {
                runtime.clear_candidate_colored_light_runtime(gal)?;
            }
            runtime.clear_candidate_puddle_runtime(gal)?;
        }
        self.candidate_colored_light_runtime = false;
        Ok(())
    }
}

pub(crate) fn supports_private_colored_light_volume(gal: &VulkanicGal) -> bool {
    let capabilities = gal.capabilities();
    capabilities.supports(BackendFeature::Compute)
        && [
            (TextureFormat::R8Uint, TextureUsage::Sampled),
            (TextureFormat::R8Uint, TextureUsage::Storage),
            (TextureFormat::R8Uint, TextureUsage::TransferDst),
            (TextureFormat::Rgba16Float, TextureUsage::Sampled),
            (TextureFormat::Rgba16Float, TextureUsage::Storage),
        ]
        .into_iter()
        .all(|(format, usage)| capabilities.supports_texture_3d_usage(format, usage))
}

impl WorldPrimitiveFrontend {
    /// Installs a private Rust-owned occupancy transaction for an already
    /// validated shader-pack generation. This accepts semantic descriptors
    /// only; no Java state, native resource, or selected shader execution is
    /// exposed here.
    #[cfg(test)]
    pub(crate) fn install_private_terrain_occupancy(
        &mut self,
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
    ) -> GalResult<()> {
        self.shader_runtime_mut(gal, descriptor.shader_pack_generation)?
            .install_private_terrain_occupancy(gal, descriptor, materials)
    }

    #[cfg(test)]
    pub(crate) fn install_private_terrain_colored_light(
        &mut self,
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
        emission: VoxelEmissionTable,
    ) -> GalResult<()> {
        self.shader_runtime_mut(gal, descriptor.shader_pack_generation)?
            .install_private_terrain_colored_light(gal, descriptor, materials, emission)
    }

    #[cfg(test)]
    pub(crate) fn private_terrain_occupancy_mesh_count(&self) -> Option<usize> {
        self.shader_runtime
            .as_ref()
            .and_then(ShaderPackRuntimeExecutor::private_terrain_occupancy_mesh_count)
    }

    #[cfg(test)]
    pub(crate) fn private_terrain_occupancy_mapping(&self) -> Option<VoxelLightVolumeMapping> {
        self.shader_runtime
            .as_ref()
            .and_then(ShaderPackRuntimeExecutor::private_terrain_occupancy_mapping)
    }

    #[cfg(test)]
    pub(crate) fn private_terrain_colored_light_ready(&self, frame_counter: u64) -> Option<bool> {
        self.shader_runtime
            .as_ref()
            .and_then(|runtime| runtime.private_terrain_colored_light_ready(frame_counter))
    }
}

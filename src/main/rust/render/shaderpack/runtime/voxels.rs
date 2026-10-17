//! Private voxel occupancy, colored-light and puddle runtimes.

use super::*;

impl ShaderPackRuntimeExecutor {
    /// Builds the semantic identity for an owned colored-light runtime without
    /// cloning its immutable material and emission tables. The source
    /// contract owns extent/format/material/emission policy; the caller
    /// supplies only copied world/resource/camera facts.
    pub(crate) fn candidate_colored_light_descriptor(
        &self,
        world_generation: u64,
        resource_generation: u64,
        camera_world_position: [f32; 3],
    ) -> GalResult<Option<VoxelLightVolumeDescriptor>> {
        let (generation, pack_name, contract, requires_colored_voxel_light, materials, emission) =
            match &self.source_candidate {
                TerrainSourceCandidateState::Unavailable
                | TerrainSourceCandidateState::Disabled { .. }
                | TerrainSourceCandidateState::Rejected { .. } => return Ok(None),
                TerrainSourceCandidateState::Discovered {
                    generation,
                    pack_name,
                    contract,
                    requires_colored_voxel_light,
                    voxel_materials,
                    voxel_emission,
                    ..
                } => (
                    *generation,
                    pack_name,
                    contract,
                    *requires_colored_voxel_light,
                    voxel_materials,
                    voxel_emission,
                ),
            };
        if !requires_colored_voxel_light {
            return Ok(None);
        }
        if generation != contract.generation || world_generation == 0 || resource_generation == 0 {
            return Err(GalError::invalid_argument(
                "colored voxel-light preparation requires matching non-zero source, world, and resource generations",
            ));
        }
        let requirements = contract.voxel_light_volume_requirements.ok_or_else(|| {
            GalError::invalid_argument(
                "selected terrain source requires ColoredVoxelLighting without a volume descriptor",
            )
        })?;
        let _materials = materials.as_ref().ok_or_else(|| {
            GalError::invalid_argument("selected terrain source has no derived voxel material map")
        })?;
        let _emission = emission.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "selected terrain source has no derived colored-light emission table",
            )
        })?;
        let mut camera_cell = [0_i32; 3];
        let mut camera_fraction = [0.0_f32; 3];
        for axis in 0..3 {
            let coordinate = camera_world_position[axis];
            if !coordinate.is_finite() {
                return Err(GalError::invalid_argument(
                    "colored voxel-light camera position must be finite",
                ));
            }
            let cell = coordinate.floor();
            if cell < i32::MIN as f32 || cell > i32::MAX as f32 {
                return Err(GalError::invalid_argument(
                    "colored voxel-light camera cell is outside i32 range",
                ));
            }
            camera_cell[axis] = cell as i32;
            camera_fraction[axis] = coordinate - cell;
        }
        let descriptor = VoxelLightVolumeDescriptor {
            identity: VoxelLightVolumeIdentity::new(format!(
                "shader-pack:{}/colored-voxel-light",
                pack_name.to_ascii_lowercase()
            ))?,
            shader_pack_generation: generation,
            world_generation,
            resource_generation,
            extent: requirements.extent,
            requirements,
            mapping: VoxelLightVolumeMapping::complementary(
                requirements.extent,
                camera_cell,
                camera_fraction,
            )?,
        };
        descriptor.validate()?;
        Ok(Some(descriptor))
    }

    /// Prepares the complete semantic identity for an owned colored-light
    /// runtime. This clones immutable source tables only when the caller has
    /// determined that a compatible runtime cannot be reused.
    pub(crate) fn candidate_colored_light_preparation(
        &self,
        world_generation: u64,
        resource_generation: u64,
        camera_world_position: [f32; 3],
    ) -> GalResult<Option<TerrainColoredLightPreparation>> {
        let Some(descriptor) = self.candidate_colored_light_descriptor(
            world_generation,
            resource_generation,
            camera_world_position,
        )?
        else {
            return Ok(None);
        };
        let (materials, emission) = match &self.source_candidate {
            TerrainSourceCandidateState::Discovered {
                voxel_materials,
                voxel_emission,
                ..
            } => (
                voxel_materials.as_ref().ok_or_else(|| {
                    GalError::invalid_argument(
                        "selected terrain source has no derived voxel material map",
                    )
                })?,
                voxel_emission.as_ref().ok_or_else(|| {
                    GalError::invalid_argument(
                        "selected terrain source has no derived colored-light emission table",
                    )
                })?,
            ),
            _ => {
                return Err(GalError::invalid_argument(
                    "colored-light descriptor exists without a discovered source candidate",
                ));
            }
        };
        Ok(Some(TerrainColoredLightPreparation {
            descriptor,
            materials: materials.clone(),
            emission: emission.clone(),
        }))
    }

    pub(crate) fn install_private_terrain_occupancy(
        &mut self,
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
    ) -> GalResult<()> {
        if descriptor.shader_pack_generation != self.expected_shader_pack_generation_for_resources()
        {
            return Err(GalError::invalid_argument(
                "private terrain occupancy generation must match its shader runtime",
            ));
        }
        let replacement = TerrainOccupancyRuntime::create(gal, descriptor, materials)?;
        if let Some(previous) = self.terrain_colored_light.take() {
            self.release_parked_fullscreen_plans(gal);
            previous.destroy(gal)?;
        }
        if let Some(previous) = self.terrain_occupancy.replace(replacement) {
            self.release_parked_fullscreen_plans(gal);
            previous.destroy(gal)?;
        }
        Ok(())
    }

    pub(crate) fn install_private_terrain_colored_light(
        &mut self,
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
        emission: VoxelEmissionTable,
    ) -> GalResult<()> {
        if descriptor.shader_pack_generation != self.expected_shader_pack_generation_for_resources()
        {
            return Err(GalError::invalid_argument(
                "private colored-light generation must match its shader runtime",
            ));
        }
        let replacement = TerrainColoredLightRuntime::create(gal, descriptor, materials, emission)?;
        if let Some(previous) = self.terrain_occupancy.take() {
            self.release_parked_fullscreen_plans(gal);
            previous.destroy(gal)?;
        }
        if let Some(previous) = self.terrain_colored_light.replace(replacement) {
            self.release_parked_fullscreen_plans(gal);
            previous.destroy(gal)?;
        }
        Ok(())
    }

    /// Installs a complete source-derived colored-light generation exactly
    /// once. A changed source/world/resource descriptor replaces the previous
    /// owned runtime atomically; an identical descriptor keeps persistent D3
    /// resources alive for bounded per-frame updates.
    pub(crate) fn ensure_candidate_colored_light_runtime(
        &mut self,
        gal: &mut VulkanicGal,
        preparation: TerrainColoredLightPreparation,
    ) -> GalResult<bool> {
        if self.terrain_colored_light.as_ref().is_some_and(|runtime| {
            runtime
                .descriptor()
                .resource_compatible_with(&preparation.descriptor)
        }) {
            return Ok(false);
        }
        self.install_private_terrain_colored_light(
            gal,
            preparation.descriptor,
            preparation.materials,
            preparation.emission,
        )?;
        Ok(true)
    }

    /// Reports whether the existing Rust-owned volume can serve the supplied
    /// semantic descriptor without rebuilding GPU resources. Camera mapping
    /// remains frame-local and is deliberately excluded by the descriptor's
    /// resource-compatibility policy.
    pub(crate) fn candidate_colored_light_runtime_compatible(
        &self,
        descriptor: &VoxelLightVolumeDescriptor,
    ) -> bool {
        self.terrain_colored_light
            .as_ref()
            .is_some_and(|runtime| runtime.descriptor().resource_compatible_with(descriptor))
    }

    /// Installs or reuses the private source-derived puddle field. Its stable
    /// resource identity excludes camera fraction and shadow transform, which
    /// are content updates to the same owned image; changing any generation
    /// replaces the image atomically.
    /// Whether preparing `descriptor` (or dropping the puddle requirement)
    /// would destroy the installed puddle runtime, so callers can first
    /// release pack sets that bind it.
    pub(crate) fn candidate_puddle_runtime_retires(
        &self,
        descriptor: Option<PuddleOccupancyDescriptor>,
    ) -> bool {
        let Some(installed) = self.terrain_puddle.as_ref() else {
            return false;
        };
        match descriptor {
            Some(descriptor)
                if self.candidate_source_requires_resource(
                    TerrainSourceResourceRole::PuddleOccupancy,
                ) =>
            {
                !installed.resource_compatible_with(descriptor)
            }
            _ => true,
        }
    }

    pub(crate) fn ensure_candidate_puddle_runtime(
        &mut self,
        gal: &mut VulkanicGal,
        descriptor: PuddleOccupancyDescriptor,
    ) -> GalResult<bool> {
        if !self.candidate_source_requires_resource(TerrainSourceResourceRole::PuddleOccupancy) {
            self.clear_candidate_puddle_runtime(gal)?;
            return Ok(false);
        }
        if descriptor.shader_pack_generation != self.expected_shader_pack_generation_for_resources()
        {
            return Err(GalError::invalid_argument(
                "private puddle occupancy generation must match its shader runtime",
            ));
        }
        if self
            .terrain_puddle
            .as_ref()
            .is_some_and(|runtime| runtime.resource_compatible_with(descriptor))
        {
            return Ok(false);
        }
        let replacement = TerrainPuddleRuntime::create(gal, descriptor)?;
        if let Some(previous) = self.terrain_puddle.replace(replacement) {
            self.release_parked_fullscreen_plans(gal);
            previous.destroy(gal)?;
        }
        Ok(true)
    }

    pub(crate) fn clear_candidate_puddle_runtime(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        if let Some(previous) = self.terrain_puddle.take() {
            self.release_parked_fullscreen_plans(gal);
            previous.destroy(gal)?;
        }
        Ok(())
    }

    /// Removes only a source-candidate preparation runtime. This does not
    /// select or deselect any terrain program; callers use it when the
    /// semantic source contract or world-volume mapping is no longer valid.
    pub(crate) fn clear_candidate_colored_light_runtime(
        &mut self,
        gal: &mut VulkanicGal,
    ) -> GalResult<()> {
        if let Some(previous) = self.terrain_colored_light.take() {
            self.release_parked_fullscreen_plans(gal);
            previous.destroy(gal)?;
        }
        Ok(())
    }

    pub(crate) fn expected_shader_pack_generation_for_resources(&self) -> u64 {
        match &self.source_candidate {
            TerrainSourceCandidateState::Discovered { generation, .. } => *generation,
            _ => self.plan.generation,
        }
    }

    pub(crate) fn has_private_terrain_occupancy(&self) -> bool {
        self.terrain_occupancy.is_some()
            || self.terrain_colored_light.is_some()
            || self.terrain_puddle.is_some()
    }

    /// Bounded admission diagnostics for the Rust-owned colored-light
    /// runtime. These values expose no resource handles or backend state and
    /// do not affect source-route selection.
    pub(crate) fn candidate_colored_light_diagnostic_state(
        &self,
        frame_counter: u64,
    ) -> Option<TerrainColoredLightDiagnosticState> {
        self.terrain_colored_light
            .as_ref()
            .map(|runtime| runtime.diagnostic_state(frame_counter))
    }

    #[cfg(test)]
    pub(crate) fn private_terrain_occupancy_mesh_count(&self) -> Option<usize> {
        self.terrain_occupancy
            .as_ref()
            .map(TerrainOccupancyRuntime::mesh_snapshot_count)
            .or_else(|| {
                self.terrain_colored_light
                    .as_ref()
                    .map(TerrainColoredLightRuntime::mesh_snapshot_count)
            })
    }

    pub(crate) fn private_terrain_occupancy_descriptor(
        &self,
    ) -> Option<&VoxelLightVolumeDescriptor> {
        self.terrain_occupancy
            .as_ref()
            .map(TerrainOccupancyRuntime::descriptor)
            .or_else(|| {
                self.terrain_colored_light
                    .as_ref()
                    .map(TerrainColoredLightRuntime::descriptor)
            })
    }

    #[cfg(test)]
    pub(crate) fn private_terrain_occupancy_mapping(&self) -> Option<VoxelLightVolumeMapping> {
        self.private_terrain_occupancy_descriptor()
            .map(|descriptor| descriptor.mapping)
    }

    #[cfg(test)]
    pub(crate) fn private_terrain_colored_light_ready(&self, frame_counter: u64) -> Option<bool> {
        self.terrain_colored_light
            .as_ref()
            .map(|runtime| runtime.is_ready_for_frame(frame_counter))
    }

    pub(crate) fn append_private_terrain_occupancy(
        &mut self,
        frame_counter: u64,
        mapping: Option<VoxelLightVolumeMapping>,
        view_direction: Option<VoxelLightVolumeViewDirection>,
        puddle_descriptor: Option<PuddleOccupancyDescriptor>,
        meshes: impl Into<std::sync::Arc<[TerrainVoxelSourceMesh]>>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        let meshes = meshes.into();
        if let Some(colored_light) = self.terrain_colored_light.as_mut() {
            let mapping = mapping.ok_or_else(|| {
                GalError::invalid_argument(
                    "colored voxel-light preparation requires a semantic volume mapping",
                )
            })?;
            colored_light.append_terrain_source_snapshot_for_mapping(
                frame_counter,
                mapping,
                view_direction,
                std::sync::Arc::clone(&meshes),
                operations,
            )?;
        } else if let Some(occupancy) = self.terrain_occupancy.as_mut() {
            let mapping = mapping.ok_or_else(|| {
                GalError::invalid_argument(
                    "terrain occupancy preparation requires a semantic volume mapping",
                )
            })?;
            occupancy.append_terrain_source_snapshot_for_mapping(
                mapping,
                std::sync::Arc::clone(&meshes),
                operations,
            )?;
        }
        if let Some(puddle) = self.terrain_puddle.as_mut() {
            let descriptor = puddle_descriptor.ok_or_else(|| {
                GalError::invalid_argument(
                    "puddle occupancy preparation requires a semantic shadow-scene descriptor",
                )
            })?;
            puddle.append_terrain_source_snapshot(descriptor, meshes.iter().cloned(), operations)?;
        }
        Ok(())
    }

    pub(crate) fn confirm_private_terrain_occupancy_submission(&mut self) -> GalResult<()> {
        if let Some(colored_light) = self.terrain_colored_light.as_mut() {
            if colored_light.has_pending_submission() {
                colored_light.confirm_submission()?;
            }
        } else if let Some(occupancy) = self.terrain_occupancy.as_mut() {
            if occupancy.has_pending_submission() {
                occupancy.confirm_submission()?;
            }
        }
        if let Some(puddle) = self.terrain_puddle.as_mut() {
            if puddle.has_pending_submission() {
                puddle.confirm_submission()?;
            }
        }
        Ok(())
    }

    /// Private voxel volumes that rest in storage layout between submissions
    /// but are sampled by source passes (colored light / occupancy).
    pub(crate) fn private_terrain_storage_volume_textures(&self) -> Vec<Handle> {
        if let Some(colored_light) = self.terrain_colored_light.as_ref() {
            colored_light.storage_volume_textures()
        } else if let Some(occupancy) = self.terrain_occupancy.as_ref() {
            occupancy.storage_volume_textures()
        } else {
            Vec::new()
        }
    }

    pub(crate) fn has_pending_private_terrain_occupancy_submission(&self) -> bool {
        self.terrain_colored_light
            .as_ref()
            .is_some_and(TerrainColoredLightRuntime::has_pending_submission)
            || self
                .terrain_occupancy
                .as_ref()
                .is_some_and(TerrainOccupancyRuntime::has_pending_submission)
            || self
                .terrain_puddle
                .as_ref()
                .is_some_and(TerrainPuddleRuntime::has_pending_submission)
    }

    pub(crate) fn discard_private_terrain_occupancy_submission(&mut self) {
        if let Some(colored_light) = self.terrain_colored_light.as_mut() {
            colored_light.discard_submission();
        } else if let Some(occupancy) = self.terrain_occupancy.as_mut() {
            occupancy.discard_submission();
        }
        if let Some(puddle) = self.terrain_puddle.as_mut() {
            puddle.discard_submission();
        }
    }
}

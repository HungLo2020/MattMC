//! CPU voxelization of static terrain meshes into occupancy samples.

use super::*;

/// Copied semantic values corresponding to one terrain vertex. `mid_block`
/// is the source packed signed-byte `at_midBlock` value, decoded only for the
/// source-derived `gl_Vertex.xyz + at_midBlock / 64` occupancy position.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct TerrainVoxelSample {
    pub vertex_position: [f32; 3],
    pub mid_block_packed: u32,
    pub shader_material_id: i32,
    /// Column-major model transform, matching the mesh instance semantic ABI.
    pub model_transform: [f32; 16],
}

impl TerrainVoxelSample {
    pub fn from_mesh_vertex(vertex: &WorldMeshVertex, model_transform: [f32; 16]) -> Self {
        Self {
            vertex_position: vertex.position,
            mid_block_packed: vertex.mid_block_packed,
            shader_material_id: vertex.shader_block_id,
            model_transform,
        }
    }

    pub fn world_block_center(self) -> GalResult<[f32; 3]> {
        if self.vertex_position.iter().any(|value| !value.is_finite()) {
            return Err(GalError::invalid_argument(
                "terrain voxel sample has a non-finite vertex position",
            ));
        }
        let model = [
            self.vertex_position[0] + mid_block_component(self.mid_block_packed, 0),
            self.vertex_position[1] + mid_block_component(self.mid_block_packed, 8),
            self.vertex_position[2] + mid_block_component(self.mid_block_packed, 16),
        ];
        transform_point(self.model_transform, model)
    }
}

#[derive(Clone, Copy, Debug, Default, Eq, PartialEq)]
pub struct TerrainOccupancyUpdateStats {
    pub input_samples: u32,
    pub emitted_samples: u32,
    /// Coarse cells written by different source material values. The selected
    /// pack's vertex-stage image stores permit this, so the ordered copied
    /// source stream deterministically retains its later value.
    pub overwritten_samples: u32,
    pub skipped_non_solid_samples: u32,
    pub skipped_out_of_bounds_samples: u32,
    pub changed_voxels: u32,
    pub uploaded_bytes: u32,
    pub updated_region: Option<VoxelLightVolumeRegion>,
}

/// CPU-owned semantic occupancy field. A complete initial generation is
/// followed by the smallest changed axis-aligned box. GPU residency and
/// flood-fill remain separate, later stages.
#[derive(Clone, Debug)]
pub struct TerrainOccupancyVoxelizer {
    pub(super) descriptor: VoxelLightVolumeDescriptor,
    pub(super) materials: VoxelMaterialMap,
    pub(super) cache: VoxelLightVolumeCache,
    /// Last successfully submitted occupancy field. A candidate never becomes
    /// visible to the semantic cache until its GPU upload is accepted.
    pub(super) occupancy: Vec<u8>,
    pub(super) initialized: bool,
    pub(super) pending_upload: Option<VoxelLightVolumeUpdate>,
    pub(super) pending_occupancy: Option<Vec<u8>>,
    /// Region patches of an in-place update (unchanged camera cell). They
    /// replace `pending_upload`/`pending_occupancy` for small changes so the
    /// whole field is neither copied nor re-uploaded.
    pub(super) pending_patches: Vec<VoxelLightVolumeUpdate>,
}

impl TerrainOccupancyVoxelizer {
    pub fn new(
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
    ) -> GalResult<Self> {
        descriptor.validate()?;
        if descriptor.shader_pack_generation != materials.shader_pack_generation() {
            return Err(GalError::invalid_argument(
                "terrain occupancy material map does not match shader-pack generation",
            ));
        }
        let byte_len = usize::try_from(
            descriptor
                .extent
                .byte_len(crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::OccupancyR8Uint),
        )
        .map_err(|_| {
            GalError::invalid_argument("terrain occupancy extent exceeds address space")
        })?;
        let mut cache = VoxelLightVolumeCache::new();
        cache.replace_descriptor(descriptor.clone())?;
        Ok(Self {
            descriptor,
            materials,
            cache,
            occupancy: vec![0; byte_len],
            initialized: false,
            pending_upload: None,
            pending_occupancy: None,
            pending_patches: Vec::new(),
        })
    }

    pub fn descriptor(&self) -> &VoxelLightVolumeDescriptor {
        &self.descriptor
    }

    pub fn cache(&self) -> &VoxelLightVolumeCache {
        &self.cache
    }

    pub fn is_initialized(&self) -> bool {
        self.initialized && self.pending_upload.is_none() && self.pending_patches.is_empty()
    }

    pub fn replace_descriptor(&mut self, descriptor: VoxelLightVolumeDescriptor) -> GalResult<()> {
        descriptor.validate()?;
        if descriptor.shader_pack_generation != self.materials.shader_pack_generation() {
            return Err(GalError::invalid_argument(
                "terrain occupancy descriptor would mix a stale shader material map",
            ));
        }
        let byte_len = usize::try_from(
            descriptor
                .extent
                .byte_len(crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::OccupancyR8Uint),
        )
        .map_err(|_| {
            GalError::invalid_argument("terrain occupancy extent exceeds address space")
        })?;
        self.cache.replace_descriptor(descriptor.clone())?;
        self.descriptor = descriptor;
        self.occupancy = vec![0; byte_len];
        self.initialized = false;
        self.pending_upload = None;
        self.pending_occupancy = None;
        self.pending_patches.clear();
        Ok(())
    }

    /// Updates the camera-relative semantic mapping. Fractional movement
    /// leaves occupied cells intact; crossing an integer camera cell makes the
    /// field incomplete until a full source snapshot re-voxelizes it.
    pub fn update_mapping(&mut self, mapping: VoxelLightVolumeMapping) -> GalResult<bool> {
        if self.pending_upload.is_some() || !self.pending_patches.is_empty() {
            return Err(GalError::invalid_argument(
                "cannot update terrain occupancy mapping while an upload is pending",
            ));
        }
        mapping.validate(self.descriptor.extent)?;
        if mapping == self.descriptor.mapping {
            return Ok(false);
        }
        let requires_revoxelization = mapping.camera_cell != self.descriptor.mapping.camera_cell;
        self.descriptor.mapping = mapping;
        self.cache.update_mapping(mapping)?;
        if requires_revoxelization {
            self.occupancy.fill(0);
            self.cache.invalidate_fields();
            self.initialized = false;
        }
        Ok(requires_revoxelization)
    }

    pub fn retire_world_generation(&mut self, world_generation: u64) {
        if self.descriptor.world_generation == world_generation {
            self.cache.retire_world_generation(world_generation);
            self.occupancy.clear();
            self.initialized = false;
            self.pending_upload = None;
            self.pending_occupancy = None;
            self.pending_patches.clear();
        }
    }

    /// The candidate upload is immutable until its containing submission is
    /// known to have been accepted. This prevents the shader-plan cache from
    /// observing CPU data that never reached the owned 3D resource.
    pub fn pending_upload(&self) -> Option<&VoxelLightVolumeUpdate> {
        self.pending_upload.as_ref()
    }

    /// Commits the exact pending payload only after the caller has accepted
    /// the submission that contains its upload operations.
    pub fn confirm_pending_upload(&mut self) -> GalResult<()> {
        if self.pending_upload.is_none() && !self.pending_patches.is_empty() {
            for patch in std::mem::take(&mut self.pending_patches) {
                let region = patch.region;
                let width = self.descriptor.extent.width;
                let height = self.descriptor.extent.height;
                let row = region.extent.width as usize;
                for z in 0..region.extent.depth {
                    for y in 0..region.extent.height {
                        let start = voxel_index(width, height, [region.x, region.y + y, region.z + z])?;
                        let source = ((z * region.extent.height + y) * region.extent.width) as usize;
                        self.occupancy[start..start + row]
                            .copy_from_slice(&patch.texels[source..source + row]);
                    }
                }
                self.cache.apply_update(patch)?;
            }
            return Ok(());
        }
        let update = self.pending_upload.take().ok_or_else(|| {
            GalError::invalid_argument("no terrain occupancy upload is pending confirmation")
        })?;
        let occupancy = self.pending_occupancy.take().ok_or_else(|| {
            GalError::invalid_argument("terrain occupancy pending data is missing")
        })?;
        self.cache.apply_update(update)?;
        self.occupancy = occupancy;
        self.initialized = true;
        Ok(())
    }

    /// Discards a candidate whose command list was rejected or never
    /// submitted. The last complete semantic generation remains active.
    pub fn discard_pending_upload(&mut self) {
        self.pending_upload = None;
        self.pending_occupancy = None;
        self.pending_patches.clear();
    }

    /// Reinstates a previous mapping after a rejected in-place update. The
    /// confirmed field was never modified, so unlike `update_mapping` this
    /// never clears it, even across a camera-cell change.
    pub(super) fn restore_mapping(&mut self, mapping: VoxelLightVolumeMapping) {
        self.descriptor.mapping = mapping;
        let _ = self.cache.update_mapping(mapping);
    }

    pub(crate) fn pending_patches(&self) -> &[VoxelLightVolumeUpdate] {
        &self.pending_patches
    }

    /// In-place form of `update_incremental` for an unchanged camera cell:
    /// each dirty world box is recomputed into its own buffer (same last-writer
    /// order as a full rebuild) and staged as a patch only when it differs
    /// from the confirmed field.
    pub(super) fn stage_patches(
        &mut self,
        meshes: &BTreeMap<u64, TerrainOccupancyMeshSnapshot>,
        dirty: &[[[i32; 3]; 2]],
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        if !self.is_initialized() {
            return Err(GalError::invalid_argument(
                "terrain occupancy patches require a confirmed field without a pending upload",
            ));
        }
        let range = [
            self.descriptor.mapping.valid_world_min,
            self.descriptor.mapping.valid_world_max_exclusive,
        ];
        let mut stats = TerrainOccupancyUpdateStats::default();
        let mut bounds: Option<[[u32; 3]; 2]> = None;
        // Patches become separate copies into one texture within a single
        // transfer phase, so they must not overlap.
        let regions = disjoint_world_boxes(
            dirty
                .iter()
                .filter_map(|dirty| intersect_world_boxes(*dirty, range))
                .collect(),
        );
        for region in regions {
            let size = [0, 1, 2].map(|axis| (region[1][axis] - region[0][axis]) as u32);
            let local = [0, 1, 2].map(|axis| (region[0][axis] - range[0][axis]) as u32);
            let mut values = vec![0_u8; (size[0] * size[1] * size[2]) as usize];
            for mesh in meshes.values() {
                let Some(mesh_bounds) = mesh.world_bounds else {
                    continue;
                };
                if (0..3).any(|axis| {
                    mesh_bounds[1][axis] < region[0][axis] || mesh_bounds[0][axis] >= region[1][axis]
                }) {
                    continue;
                }
                for sample in mesh.samples.iter() {
                    stats.input_samples = stats.input_samples.saturating_add(1);
                    let Some(value) = self.materials.occupancy_value(sample.shader_material_id)
                    else {
                        stats.skipped_non_solid_samples =
                            stats.skipped_non_solid_samples.saturating_add(1);
                        continue;
                    };
                    let center = sample.world_block_center()?;
                    if center.iter().any(|value| !value.is_finite()) {
                        continue;
                    }
                    let point = center.map(|value| value.floor() as i32);
                    if (0..3).any(|axis| point[axis] < region[0][axis] || point[axis] >= region[1][axis]) {
                        continue;
                    }
                    let offset = [0, 1, 2].map(|axis| (point[axis] - region[0][axis]) as u32);
                    values[((offset[2] * size[1] + offset[1]) * size[0] + offset[0]) as usize] = value;
                    stats.emitted_samples = stats.emitted_samples.saturating_add(1);
                }
            }
            let mut changed = 0_u32;
            let width = self.descriptor.extent.width;
            let height = self.descriptor.extent.height;
            for z in 0..size[2] {
                for y in 0..size[1] {
                    let start = voxel_index(width, height, [local[0], local[1] + y, local[2] + z])?;
                    let source = ((z * size[1] + y) * size[0]) as usize;
                    let row = size[0] as usize;
                    changed += self.occupancy[start..start + row]
                        .iter()
                        .zip(&values[source..source + row])
                        .filter(|(old, new)| old != new)
                        .count() as u32;
                }
            }
            if changed == 0 {
                continue;
            }
            stats.changed_voxels = stats.changed_voxels.saturating_add(changed);
            stats.uploaded_bytes = stats.uploaded_bytes.saturating_add(values.len() as u32);
            let end = [0, 1, 2].map(|axis| local[axis] + size[axis]);
            bounds = Some(match bounds {
                None => [local, end],
                Some([min, max]) => [
                    [0, 1, 2].map(|axis| min[axis].min(local[axis])),
                    [0, 1, 2].map(|axis| max[axis].max(end[axis])),
                ],
            });
            let update = VoxelLightVolumeUpdate {
                identity: self.descriptor.identity.clone(),
                shader_pack_generation: self.descriptor.shader_pack_generation,
                world_generation: self.descriptor.world_generation,
                resource_generation: self.descriptor.resource_generation,
                kind: VoxelLightVolumeKind::Occupancy,
                region: VoxelLightVolumeRegion {
                    x: local[0],
                    y: local[1],
                    z: local[2],
                    extent: crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeExtent {
                        width: size[0],
                        height: size[1],
                        depth: size[2],
                    },
                },
                texels: values,
            };
            update.validate(&self.descriptor)?;
            self.pending_patches.push(update);
        }
        stats.updated_region = bounds.map(|[min, max]| VoxelLightVolumeRegion {
            x: min[0],
            y: min[1],
            z: min[2],
            extent: crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeExtent {
                width: max[0] - min[0],
                height: max[1] - min[1],
                depth: max[2] - min[2],
            },
        });
        Ok(stats)
    }

    /// Whether `mapping` only translates the camera cell of a confirmed field,
    /// so the field can be shifted rather than re-voxelized.
    pub(super) fn can_shift_to(&self, mapping: &VoxelLightVolumeMapping) -> bool {
        let current = &self.descriptor.mapping;
        self.initialized
            && self.pending_upload.is_none()
            && self.pending_patches.is_empty()
            && mapping.scene_to_volume_scale == current.scene_to_volume_scale
            && mapping.sample_normal_offset == current.sample_normal_offset
            && (0..3).all(|axis| {
                mapping.valid_world_max_exclusive[axis] - mapping.valid_world_min[axis]
                    == current.valid_world_max_exclusive[axis] - current.valid_world_min[axis]
            })
    }

    /// Exact incremental form of `update_from_samples` over every sample of
    /// `meshes` (in key order): the confirmed field is shifted to `mapping`,
    /// then the newly exposed cells and every `dirty` world box are cleared
    /// and recomputed from all meshes that touch them, in the same order, so
    /// each recomputed cell keeps the full rebuild's last-writer value.
    pub(super) fn update_incremental(
        &mut self,
        mapping: VoxelLightVolumeMapping,
        meshes: &BTreeMap<u64, TerrainOccupancyMeshSnapshot>,
        dirty: &[[[i32; 3]; 2]],
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        if self.pending_upload.is_some() {
            return Err(GalError::invalid_argument(
                "terrain occupancy upload is already pending submission confirmation",
            ));
        }
        mapping.validate(self.descriptor.extent)?;
        let old = [
            self.descriptor.mapping.valid_world_min,
            self.descriptor.mapping.valid_world_max_exclusive,
        ];
        let new = [mapping.valid_world_min, mapping.valid_world_max_exclusive];
        let mut next = vec![0; self.occupancy.len()];
        if let Some(overlap) = intersect_world_boxes(old, new) {
            let row = usize::try_from(overlap[1][0] - overlap[0][0]).unwrap_or(0);
            for z in overlap[0][2]..overlap[1][2] {
                for y in overlap[0][1]..overlap[1][1] {
                    let from = self.world_cell_index(old[0], [overlap[0][0], y, z])?;
                    let to = self.world_cell_index(new[0], [overlap[0][0], y, z])?;
                    next[to..to + row].copy_from_slice(&self.occupancy[from..from + row]);
                }
            }
        }
        let mut boxes = subtract_world_box(new, old);
        boxes.extend(dirty.iter().filter_map(|dirty| intersect_world_boxes(*dirty, new)));
        let mut stats = TerrainOccupancyUpdateStats::default();
        for region in &boxes {
            self.recompute_world_box(&mut next, new[0], *region, meshes, &mut stats)?;
        }
        let shifted = old != new;
        self.descriptor.mapping = mapping;
        self.cache.update_mapping(mapping)?;
        if shifted {
            // Every resident texel moved: upload the whole field without
            // scanning 2x the volume for a bounding region.
            let update = VoxelLightVolumeUpdate {
                identity: self.descriptor.identity.clone(),
                shader_pack_generation: self.descriptor.shader_pack_generation,
                world_generation: self.descriptor.world_generation,
                resource_generation: self.descriptor.resource_generation,
                kind: VoxelLightVolumeKind::Occupancy,
                region: VoxelLightVolumeRegion::whole(self.descriptor.extent),
                texels: next.clone(),
            };
            update.validate(&self.descriptor)?;
            stats.uploaded_bytes = u32::try_from(next.len()).unwrap_or(u32::MAX);
            stats.updated_region = Some(update.region);
            self.pending_upload = Some(update);
            self.pending_occupancy = Some(next);
            return Ok(stats);
        }
        self.stage_update(next, stats)
    }

    /// Full rebuild over `meshes` for the current mapping. Meshes wholly
    /// outside the volume are skipped; their samples could only be counted as
    /// out of bounds, so the field equals `update_from_samples` over all.
    pub(super) fn rebuild_from_meshes(
        &mut self,
        meshes: &BTreeMap<u64, TerrainOccupancyMeshSnapshot>,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        if self.pending_upload.is_some() {
            return Err(GalError::invalid_argument(
                "terrain occupancy upload is already pending submission confirmation",
            ));
        }
        let range = [
            self.descriptor.mapping.valid_world_min,
            self.descriptor.mapping.valid_world_max_exclusive,
        ];
        let mut next = vec![0; self.occupancy.len()];
        let mut stats = TerrainOccupancyUpdateStats::default();
        self.recompute_world_box(&mut next, range[0], range, meshes, &mut stats)?;
        self.stage_update(next, stats)
    }

    pub(super) fn recompute_world_box(
        &self,
        next: &mut [u8],
        volume_min: [i32; 3],
        region: [[i32; 3]; 2],
        meshes: &BTreeMap<u64, TerrainOccupancyMeshSnapshot>,
        stats: &mut TerrainOccupancyUpdateStats,
    ) -> GalResult<()> {
        let row = usize::try_from(region[1][0] - region[0][0]).unwrap_or(0);
        for z in region[0][2]..region[1][2] {
            for y in region[0][1]..region[1][1] {
                let start = self.world_cell_index(volume_min, [region[0][0], y, z])?;
                next[start..start + row].fill(0);
            }
        }
        for mesh in meshes.values() {
            let Some(bounds) = mesh.world_bounds else {
                continue;
            };
            if (0..3).any(|axis| bounds[1][axis] < region[0][axis] || bounds[0][axis] >= region[1][axis]) {
                continue;
            }
            for sample in mesh.samples.iter() {
                stats.input_samples = stats.input_samples.saturating_add(1);
                let Some(value) = self.materials.occupancy_value(sample.shader_material_id) else {
                    stats.skipped_non_solid_samples = stats.skipped_non_solid_samples.saturating_add(1);
                    continue;
                };
                let center = sample.world_block_center()?;
                if center.iter().any(|value| !value.is_finite()) {
                    continue;
                }
                let point = center.map(|value| value.floor() as i32);
                if (0..3).any(|axis| point[axis] < region[0][axis] || point[axis] >= region[1][axis]) {
                    continue;
                }
                let index = self.world_cell_index(volume_min, point)?;
                next[index] = value;
                stats.emitted_samples = stats.emitted_samples.saturating_add(1);
            }
        }
        Ok(())
    }

    pub(super) fn world_cell_index(&self, volume_min: [i32; 3], world: [i32; 3]) -> GalResult<usize> {
        let local = [0, 1, 2].map(|axis| world[axis] - volume_min[axis]);
        if local.iter().any(|value| *value < 0) {
            return Err(GalError::invalid_argument("terrain voxel cell is below the volume"));
        }
        voxel_index(
            self.descriptor.extent.width,
            self.descriptor.extent.height,
            local.map(|value| value as u32),
        )
    }

    pub fn update_from_samples<I>(&mut self, samples: I) -> GalResult<TerrainOccupancyUpdateStats>
    where
        I: IntoIterator<Item = TerrainVoxelSample>,
    {
        if self.pending_upload.is_some() {
            return Err(GalError::invalid_argument(
                "terrain occupancy upload is already pending submission confirmation",
            ));
        }
        let mut next = vec![0; self.occupancy.len()];
        let mut stats = TerrainOccupancyUpdateStats::default();
        for sample in samples {
            stats.input_samples = stats.input_samples.saturating_add(1);
            let Some(value) = self.materials.occupancy_value(sample.shader_material_id) else {
                stats.skipped_non_solid_samples = stats.skipped_non_solid_samples.saturating_add(1);
                continue;
            };
            let center = sample.world_block_center()?;
            let Some(voxel) = self.world_to_voxel(center) else {
                stats.skipped_out_of_bounds_samples =
                    stats.skipped_out_of_bounds_samples.saturating_add(1);
                continue;
            };
            let index = voxel_index(
                self.descriptor.extent.width,
                self.descriptor.extent.height,
                voxel,
            )?;
            let existing = next[index];
            if existing != 0 && existing != value {
                // Complementary calls imageStore for every eligible vertex;
                // its source contains no uniqueness test for a coarse cell.
                // Preserve the input execution order in our immutable copied
                // stream rather than rejecting normal overlapping terrain.
                stats.overwritten_samples = stats.overwritten_samples.saturating_add(1);
            }
            next[index] = value;
            stats.emitted_samples = stats.emitted_samples.saturating_add(1);
        }

        self.stage_update(next, stats)
    }

    pub(super) fn stage_update(
        &mut self,
        next: Vec<u8>,
        mut stats: TerrainOccupancyUpdateStats,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        let changed = changed_bounds(
            &self.occupancy,
            &next,
            self.descriptor.extent.width,
            self.descriptor.extent.height,
            self.descriptor.extent.depth,
        );
        // Iris clears the voxel image at creation, so a pack always samples
        // a defined (possibly empty) field. An uninitialized volume therefore
        // uploads its whole field even when no terrain is loaded yet (e.g.
        // right after a teleport), instead of staying unavailable.
        let update_region = match (changed, self.initialized) {
            (Some(region), true) => region,
            (None, true) => return Ok(stats),
            (_, false) => VoxelLightVolumeRegion::whole(self.descriptor.extent),
        };
        let texels = copy_region_bytes(
            &next,
            self.descriptor.extent.width,
            self.descriptor.extent.height,
            update_region,
        );
        let update = VoxelLightVolumeUpdate {
            identity: self.descriptor.identity.clone(),
            shader_pack_generation: self.descriptor.shader_pack_generation,
            world_generation: self.descriptor.world_generation,
            resource_generation: self.descriptor.resource_generation,
            kind: VoxelLightVolumeKind::Occupancy,
            region: update_region,
            texels,
        };
        update.validate(&self.descriptor)?;
        stats.changed_voxels = count_changed(&self.occupancy, &next);
        stats.uploaded_bytes = u32::try_from(
            update_region
                .extent
                .byte_len(crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::OccupancyR8Uint),
        )
        .unwrap_or(u32::MAX);
        stats.updated_region = Some(update_region);
        self.pending_upload = Some(update);
        self.pending_occupancy = Some(next);
        Ok(stats)
    }

    pub(super) fn world_to_voxel(&self, world: [f32; 3]) -> Option<[u32; 3]> {
        if world.iter().any(|value| !value.is_finite()) {
            return None;
        }
        let mapping = self.descriptor.mapping;
        let point = [
            world[0].floor() as i32,
            world[1].floor() as i32,
            world[2].floor() as i32,
        ];
        if (0..3).any(|axis| {
            point[axis] < mapping.valid_world_min[axis]
                || point[axis] >= mapping.valid_world_max_exclusive[axis]
        }) {
            return None;
        }
        Some([
            (point[0] - mapping.valid_world_min[0]) as u32,
            (point[1] - mapping.valid_world_min[1]) as u32,
            (point[2] - mapping.valid_world_min[2]) as u32,
        ])
    }
}

pub(super) fn mid_block_component(packed: u32, shift: u32) -> f32 {
    ((packed >> shift) as u8 as i8) as f32 / 64.0
}

pub(super) fn resource_barrier(
    resource: Handle,
    subresources: Option<crate::render::vulkanic::resources::TextureSubresourceRange>,
    before: TextureUsageState,
    after: TextureUsageState,
) -> ResourceBarrier {
    ResourceBarrier {
        resource,
        subresources,
        before,
        after,
        src_queue: QueueClass::Graphics,
        dst_queue: QueueClass::Graphics,
    }
}

pub(super) fn transform_point(matrix: [f32; 16], point: [f32; 3]) -> GalResult<[f32; 3]> {
    if matrix.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument(
            "terrain voxel model transform is not finite",
        ));
    }
    let x = matrix[0] * point[0] + matrix[4] * point[1] + matrix[8] * point[2] + matrix[12];
    let y = matrix[1] * point[0] + matrix[5] * point[1] + matrix[9] * point[2] + matrix[13];
    let z = matrix[2] * point[0] + matrix[6] * point[1] + matrix[10] * point[2] + matrix[14];
    let w = matrix[3] * point[0] + matrix[7] * point[1] + matrix[11] * point[2] + matrix[15];
    if !w.is_finite() || w.abs() < f32::EPSILON {
        return Err(GalError::invalid_argument(
            "terrain voxel model transform has a zero homogeneous component",
        ));
    }
    Ok([x / w, y / w, z / w])
}

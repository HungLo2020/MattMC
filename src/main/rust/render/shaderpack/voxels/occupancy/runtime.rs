//! The occupancy runtime and its GPU volume resources.

use super::*;

/// Private explicit GAL residency for occupancy. It is not connected to any
/// shader runtime or public semantic binding yet.
#[derive(Clone, Debug)]
pub struct TerrainOccupancyGpuResources {
    pub texture: Handle,
    pub view: Handle,
    pub(super) descriptor: VoxelLightVolumeDescriptor,
    pub(super) upload_buffer: Handle,
    pub(super) initialized: bool,
    pub(super) upload_pending: bool,
}

/// One private, generation-bound occupancy upload transaction. It owns only
/// static-terrain semantic mesh extraction and an `R8Uint` D3 residency; it
/// cannot bind a terrain program or admit selected shader-pack execution.
#[derive(Debug)]
pub struct TerrainOccupancyRuntime {
    pub(super) voxelizer: TerrainOccupancyVoxelizer,
    pub(super) resources: TerrainOccupancyGpuResources,
    pub(super) upload_pending: bool,
    /// Last GPU-confirmed complete mesh set. Rebuilding a candidate from this
    /// set avoids deleting unrelated occupancy when one section changes.
    pub(super) meshes: BTreeMap<u64, TerrainOccupancyMeshSnapshot>,
    /// The exact mesh set whose occupancy payload is currently in flight.
    pub(super) pending_meshes: Option<BTreeMap<u64, TerrainOccupancyMeshSnapshot>>,
    /// Mapping-aware updates stage a cloned voxelizer so rejected submissions
    /// can restore the previous complete world-to-volume interpretation.
    pub(super) pending_voxelizer_rollback: Option<TerrainOccupancyVoxelizer>,
    /// In-place patch updates keep the voxelizer and only need its previous
    /// (same-cell) mapping restored when the submission is rejected.
    pub(super) pending_mapping_rollback: Option<VoxelLightVolumeMapping>,
    /// The shared source list last found equal to `meshes`. The frontend
    /// hands the same list back while the volume's instances are unchanged,
    /// so identity proves equality without re-checking every mesh. Cleared
    /// whenever `meshes` changes.
    pub(super) matched_source_list: Option<Arc<[TerrainVoxelSourceMesh]>>,
}

impl TerrainOccupancyRuntime {
    /// The occupancy field rests in `ShaderStorageRead` (GENERAL) between
    /// submissions; returns it when it holds data a pass may sample.
    pub(crate) fn storage_volume_textures(&self) -> Vec<Handle> {
        if self.is_initialized() || self.has_pending_submission() {
            vec![self.resources.texture]
        } else {
            Vec::new()
        }
    }

    pub fn create(
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
    ) -> GalResult<Self> {
        descriptor.validate()?;
        if materials.shader_pack_generation() != descriptor.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "terrain occupancy material map must match the volume shader-pack generation",
            ));
        }
        let voxelizer = TerrainOccupancyVoxelizer::new(descriptor.clone(), materials)?;
        let resources = TerrainOccupancyGpuResources::create(gal, &descriptor)?;
        Ok(Self {
            voxelizer,
            resources,
            upload_pending: false,
            meshes: BTreeMap::new(),
            pending_meshes: None,
            pending_voxelizer_rollback: None,
            pending_mapping_rollback: None,
            matched_source_list: None,
        })
    }

    pub fn descriptor(&self) -> &VoxelLightVolumeDescriptor {
        self.voxelizer.descriptor()
    }

    pub fn is_initialized(&self) -> bool {
        self.voxelizer.is_initialized() && self.resources.is_initialized()
    }

    /// Number of GPU-confirmed static terrain mesh identities represented by
    /// the occupancy field. This is diagnostic state only; it exposes neither
    /// geometry nor a native resource.
    pub fn mesh_snapshot_count(&self) -> usize {
        self.meshes.len()
    }

    pub(crate) fn has_pending_submission(&self) -> bool {
        self.upload_pending
    }

    /// Atomically replaces the private owned D3 residency for a new semantic
    /// volume generation. The replacement is constructed before retiring the
    /// previous resources, so malformed descriptors or material maps leave the
    /// last complete generation usable. This intentionally does not preserve
    /// mesh entries across a world/resource generation boundary.
    pub fn replace_descriptor(
        &mut self,
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
    ) -> GalResult<()> {
        if self.upload_pending {
            return Err(GalError::invalid_argument(
                "cannot replace terrain occupancy descriptor while an upload is pending",
            ));
        }
        let voxelizer = TerrainOccupancyVoxelizer::new(descriptor.clone(), materials)?;
        let resources = TerrainOccupancyGpuResources::create(gal, &descriptor)?;
        let old_resources = std::mem::replace(&mut self.resources, resources);
        old_resources.destroy(gal)?;
        self.voxelizer = voxelizer;
        self.meshes.clear();
        self.matched_source_list = None;
        self.pending_meshes = None;
        self.pending_voxelizer_rollback = None;
        Ok(())
    }

    /// Copies a complete static-terrain snapshot containing only stable
    /// Rust-owned mesh semantics. Delta caching is deliberately not inferred
    /// from mesh keys here: a later section-cache owner must make removals and
    /// replacement generations explicit before it can submit partial updates.
    /// A caller must confirm the enclosing GAL submission before this snapshot
    /// becomes the live semantic generation.
    pub fn append_static_terrain_snapshot<'a>(
        &mut self,
        meshes: impl IntoIterator<Item = (&'a WorldMeshAsset, [f32; 16], u32)>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        let candidate = static_terrain_mesh_snapshot(meshes)?;
        self.validate_snapshot_generations(&candidate)?;
        self.append_mesh_candidate(candidate, operations)
    }

    /// Consumes the compact semantic source cache held by the Rust world
    /// frontend. It has the same complete-snapshot semantics as raw asset
    /// input, but never asks Java to reconstruct vertices or send a separate
    /// voxel payload.
    pub(crate) fn append_terrain_source_snapshot(
        &mut self,
        meshes: impl IntoIterator<Item = TerrainVoxelSourceMesh>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        let meshes = meshes.into_iter().collect::<Vec<_>>();
        if self.confirmed_terrain_source_meshes_match(&meshes)? {
            return Ok(TerrainOccupancyUpdateStats::default());
        }
        let candidate = terrain_voxel_source_snapshot(meshes)?;
        self.validate_snapshot_generations(&candidate)?;
        self.append_mesh_candidate(candidate, operations)
    }

    /// Stages a complete copied terrain snapshot against one explicit
    /// camera-relative mapping. This remains private runtime preparation: it
    /// neither selects a source program nor creates a Java/FFI transport.
    /// Crossing a camera cell shifts the confirmed field and recomputes only
    /// the exposed and changed cells (see `update_incremental`) while
    /// retaining the owned D3 allocation; fractional motion updates only the
    /// mapping.
    pub(crate) fn append_terrain_source_snapshot_for_mapping(
        &mut self,
        mapping: VoxelLightVolumeMapping,
        meshes: impl Into<Arc<[TerrainVoxelSourceMesh]>>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        let meshes = meshes.into();
        if self.upload_pending {
            return Err(GalError::invalid_argument(
                "terrain occupancy upload is already pending submission confirmation",
            ));
        }
        let source_meshes_match = match &self.matched_source_list {
            Some(matched) if Arc::ptr_eq(matched, &meshes) => true,
            _ => {
                let matched = self.confirmed_terrain_source_meshes_match(&meshes)?;
                if matched {
                    self.matched_source_list = Some(Arc::clone(&meshes));
                }
                matched
            }
        };
        if source_meshes_match && mapping == self.voxelizer.descriptor().mapping {
            // Nothing changed: skip staging a copy of the whole field.
            return Ok(TerrainOccupancyUpdateStats::default());
        }

        let previous_mapping = self.voxelizer.descriptor().mapping;
        let cell_changed = mapping.camera_cell != previous_mapping.camera_cell;
        if source_meshes_match && !cell_changed {
            // The mapping may have changed fractionally, but voxel occupancy
            // is cell-addressed. Preserve the same complete D3 field and
            // update only the semantic mapping; do not rebuild the CPU field
            // or enqueue another upload for an unchanged terrain snapshot.
            self.voxelizer.update_mapping(mapping)?;
            let descriptor = self.voxelizer.descriptor().clone();
            if let Err(error) = self
                .resources
                .validate_mapping(&descriptor)
                .and_then(|()| self.resources.update_mapping(&descriptor))
            {
                let _ = self.voxelizer.update_mapping(previous_mapping);
                return Err(error);
            }
            return Ok(TerrainOccupancyUpdateStats::default());
        }
        let (candidate, dirty) = self.incremental_source_candidate(&meshes)?;
        if !cell_changed && self.voxelizer.is_initialized() && self.resources.initialized {
            // Same camera cell: patch only the changed boxes in place, without
            // copying or re-uploading the whole field.
            self.voxelizer.update_mapping(mapping)?;
            let descriptor = self.voxelizer.descriptor().clone();
            let staged = self
                .voxelizer
                .stage_patches(&candidate, &dirty)
                .and_then(|stats| {
                    self.resources.validate_mapping(&descriptor)?;
                    let recorded = self.voxelizer.pending_patches().is_empty()
                        || self
                            .resources
                            .append_patch_uploads(self.voxelizer.pending_patches(), operations)?;
                    Ok(recorded.then_some(stats))
                });
            match staged {
                Ok(Some(stats)) => {
                    self.resources.update_mapping(&descriptor)?;
                    if self.voxelizer.pending_patches().is_empty() {
                        self.matched_source_list = None;
                        self.meshes = candidate;
                    } else {
                        self.pending_mapping_rollback = Some(previous_mapping);
                        self.pending_meshes = Some(candidate);
                        self.upload_pending = true;
                    }
                    return Ok(stats);
                }
                // Patches exceed the upload buffer: use the whole-field path.
                Ok(None) => {
                    self.voxelizer.discard_pending_upload();
                    self.voxelizer.restore_mapping(previous_mapping);
                }
                Err(error) => {
                    self.voxelizer.discard_pending_upload();
                    self.voxelizer.restore_mapping(previous_mapping);
                    return Err(error);
                }
            }
        }

        // Complementary re-voxelizes every frame on the GPU. The exact CPU
        // equivalent only recomputes cells that can differ: cells of changed
        // meshes and, after a camera-cell move, the newly exposed slabs of a
        // shifted field (staged in place; a rejected submission restores the
        // previous mapping and keeps the confirmed field).
        if self.voxelizer.can_shift_to(&mapping) && self.resources.initialized {
            let staged = self
                .voxelizer
                .update_incremental(mapping, &candidate, &dirty)
                .and_then(|stats| {
                    let descriptor = self.voxelizer.descriptor().clone();
                    self.resources.validate_mapping(&descriptor)?;
                    let uploaded = match self.voxelizer.pending_upload() {
                        Some(update) => {
                            self.resources.append_upload(update, operations)?;
                            true
                        }
                        None => false,
                    };
                    self.resources.update_mapping(&descriptor)?;
                    Ok((stats, uploaded))
                });
            return match staged {
                Ok((stats, uploaded)) => {
                    if uploaded {
                        self.pending_mapping_rollback = Some(previous_mapping);
                        self.pending_meshes = Some(candidate);
                        self.upload_pending = true;
                    } else {
                        self.matched_source_list = None;
                        self.meshes = candidate;
                    }
                    Ok(stats)
                }
                Err(error) => {
                    if self.resources.upload_pending {
                        self.resources.discard_pending_submission();
                    }
                    self.voxelizer.discard_pending_upload();
                    self.voxelizer.restore_mapping(previous_mapping);
                    Err(error)
                }
            };
        }

        // Work on a copy so an invalid mesh or failed command append cannot
        // change the live volume's world-to-cell interpretation.
        let mut staged_voxelizer = self.voxelizer.clone();
        staged_voxelizer.update_mapping(mapping)?;
        let stats = staged_voxelizer.rebuild_from_meshes(&candidate)?;
        self.resources
            .validate_mapping(staged_voxelizer.descriptor())?;
        let Some(update) = staged_voxelizer.pending_upload() else {
            self.resources
                .update_mapping(staged_voxelizer.descriptor())?;
            self.voxelizer = staged_voxelizer;
            self.matched_source_list = None;
            self.meshes = candidate;
            return Ok(stats);
        };
        if let Err(error) = self.resources.append_upload(update, operations) {
            staged_voxelizer.discard_pending_upload();
            return Err(error);
        }
        self.resources
            .update_mapping(staged_voxelizer.descriptor())?;
        self.pending_voxelizer_rollback = Some(self.voxelizer.clone());
        self.voxelizer = staged_voxelizer;
        self.pending_meshes = Some(candidate);
        self.upload_pending = true;
        Ok(stats)
    }

    /// Applies a bounded replacement/removal set to the last complete terrain
    /// snapshot. Each removal carries the generation it intends to remove, so
    /// a late visibility event cannot erase a newer section generation.
    pub fn append_static_terrain_deltas<'a>(
        &mut self,
        deltas: impl IntoIterator<Item = TerrainOccupancyMeshDelta<'a>>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        if self.upload_pending {
            return Err(GalError::invalid_argument(
                "terrain occupancy upload is already pending submission confirmation",
            ));
        }
        let mut candidate = self.meshes.clone();
        for delta in deltas {
            match delta {
                TerrainOccupancyMeshDelta::Upsert {
                    mesh,
                    model_transform,
                    stratum,
                } => {
                    let Some((mesh_key, snapshot)) =
                        static_terrain_mesh_entry(mesh, model_transform, stratum)?
                    else {
                        return Err(GalError::invalid_argument(
                            "terrain occupancy deltas must use the terrain stratum; remove non-terrain state explicitly",
                        ));
                    };
                    if let Some(existing) = candidate.get(&mesh_key) {
                        // Terrain mesh generations are content identities
                        // (FNV hashes), not a numeric sequence. A different
                        // identity may therefore be numerically smaller than
                        // the prior one while still representing the current
                        // complete semantic snapshot.
                        if snapshot.mesh_generation == existing.mesh_generation {
                            if &snapshot != existing {
                                return Err(GalError::invalid_argument(format!(
                                    "terrain occupancy mesh {mesh_key} changed semantic data without advancing generation {}; first difference={}",
                                    snapshot.mesh_generation,
                                    snapshot_difference(existing, &snapshot)
                                )));
                            }
                            continue;
                        }
                    }
                    candidate.insert(mesh_key, snapshot);
                }
                TerrainOccupancyMeshDelta::Remove {
                    mesh_key,
                    mesh_generation,
                } => {
                    if mesh_key == 0 || mesh_generation == 0 {
                        return Err(GalError::invalid_argument(
                            "terrain occupancy removal requires a non-zero mesh key and generation",
                        ));
                    }
                    let Some(existing) = candidate.get(&mesh_key) else {
                        return Err(GalError::invalid_argument(format!(
                            "terrain occupancy removal references missing mesh {mesh_key}",
                        )));
                    };
                    if existing.mesh_generation != mesh_generation {
                        return Err(GalError::invalid_argument(format!(
                            "terrain occupancy removal generation {mesh_generation} is stale for mesh {mesh_key}; live generation is {}",
                            existing.mesh_generation
                        )));
                    }
                    candidate.remove(&mesh_key);
                }
            }
        }
        self.append_mesh_candidate(candidate, operations)
    }

    /// Commits both CPU semantics and owned D3 residency only after the GAL
    /// submission containing the exact candidate upload has been accepted.
    pub fn confirm_submission(&mut self) -> GalResult<()> {
        if !self.upload_pending {
            return Err(GalError::invalid_argument(
                "no terrain occupancy upload is pending confirmation",
            ));
        }
        self.resources.confirm_submission()?;
        self.voxelizer.confirm_pending_upload()?;
        self.matched_source_list = None;
        self.meshes = self.pending_meshes.take().ok_or_else(|| {
            GalError::invalid_argument("terrain occupancy pending mesh set is missing")
        })?;
        self.pending_voxelizer_rollback = None;
        self.pending_mapping_rollback = None;
        self.upload_pending = false;
        Ok(())
    }

    /// Leaves the last complete occupancy generation intact when command-list
    /// creation, submission, or completion fails.
    pub fn discard_submission(&mut self) {
        if self.upload_pending {
            self.resources.discard_pending_submission();
            if let Some(previous) = self.pending_voxelizer_rollback.take() {
                self.voxelizer = previous;
            } else {
                self.voxelizer.discard_pending_upload();
                if let Some(mapping) = self.pending_mapping_rollback.take() {
                    self.voxelizer.restore_mapping(mapping);
                }
            }
            self.pending_mapping_rollback = None;
            self.pending_meshes = None;
            self.upload_pending = false;
        }
    }

    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        self.resources.destroy(gal)
    }

    pub(super) fn append_mesh_candidate(
        &mut self,
        candidate: BTreeMap<u64, TerrainOccupancyMeshSnapshot>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        if self.upload_pending {
            return Err(GalError::invalid_argument(
                "terrain occupancy upload is already pending submission confirmation",
            ));
        }
        if self.is_initialized() && candidate == self.meshes {
            // Static terrain snapshots are authoritative complete sets. Once
            // the exact set is GPU-confirmed, a repeat has no semantic work
            // to do and must not allocate or scan a replacement volume.
            return Ok(TerrainOccupancyUpdateStats::default());
        }
        let samples = candidate
            .values()
            .flat_map(|mesh| mesh.samples.iter().copied());
        let stats = self.voxelizer.update_from_samples(samples)?;
        let Some(update) = self.voxelizer.pending_upload() else {
            // The mesh ownership changed only in semantic data which emitted
            // no occupancy difference, so the current D3 field already
            // represents the candidate exactly.
            self.matched_source_list = None;
            self.meshes = candidate;
            return Ok(stats);
        };
        if let Err(error) = self.resources.append_upload(update, operations) {
            self.voxelizer.discard_pending_upload();
            return Err(error);
        }
        self.pending_meshes = Some(candidate);
        self.upload_pending = true;
        Ok(stats)
    }

    /// Builds the complete candidate set, reusing the confirmed snapshot of
    /// every mesh whose immutable source arrays are unchanged. Returns the
    /// world boxes (half-open) whose cells may differ from the confirmed set.
    pub(super) fn incremental_source_candidate(
        &self,
        meshes: &[TerrainVoxelSourceMesh],
    ) -> GalResult<(BTreeMap<u64, TerrainOccupancyMeshSnapshot>, Vec<[[i32; 3]; 2]>)> {
        let mut candidate = BTreeMap::new();
        let mut rebuilt = BTreeMap::new();
        let mut dirty = Vec::new();
        let half_open = |bounds: [[i32; 3]; 2]| {
            [bounds[0], bounds[1].map(|value| value.saturating_add(1))]
        };
        for mesh in meshes {
            let existing = self.meshes.get(&mesh.mesh_key);
            let reusable = existing.filter(|existing| {
                existing.mesh_generation == mesh.mesh_generation
                    && existing.source_identity.as_ref().is_some_and(|identity| {
                        identity.transform == mesh.transform
                            && Arc::ptr_eq(&identity.vertices, &mesh.vertices)
                            && Arc::ptr_eq(&identity.indices, &mesh.indices)
                    })
            });
            let entry = match reusable {
                Some(existing) => existing.clone(),
                None => {
                    let entry = terrain_voxel_source_entry(mesh)?;
                    dirty.extend(existing.and_then(|existing| existing.world_bounds).map(half_open));
                    dirty.extend(entry.world_bounds.map(half_open));
                    rebuilt.insert(mesh.mesh_key, entry.clone());
                    entry
                }
            };
            if candidate.insert(mesh.mesh_key, entry).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "terrain voxel source snapshot contains duplicate mesh key {}",
                    mesh.mesh_key
                )));
            }
        }
        self.validate_snapshot_generations(&rebuilt)?;
        for (mesh_key, removed) in &self.meshes {
            if !candidate.contains_key(mesh_key) {
                dirty.extend(removed.world_bounds.map(half_open));
            }
        }
        Ok((candidate, dirty))
    }

    pub(super) fn validate_snapshot_generations(
        &self,
        candidate: &BTreeMap<u64, TerrainOccupancyMeshSnapshot>,
    ) -> GalResult<()> {
        for (mesh_key, incoming) in candidate {
            let Some(existing) = self.meshes.get(mesh_key) else {
                continue;
            };
            if incoming.mesh_generation == existing.mesh_generation && incoming != existing {
                if let Some(difference) =
                    snapshot_difference_excluding_transform(existing, incoming)
                {
                    return Err(GalError::invalid_argument(format!(
                        "terrain occupancy snapshot mesh {mesh_key} changed semantic data without advancing generation {}; first difference={difference}",
                        incoming.mesh_generation,
                    )));
                }
            }
        }
        Ok(())
    }

    /// Confirms that a complete source snapshot is backed by the exact
    /// immutable arrays already validated and retained by this runtime. A
    /// mesh generation is a content identity, not an ordered counter: a
    /// changed identity invalidates the match regardless of its numeric value.
    pub(super) fn confirmed_terrain_source_meshes_match(
        &self,
        meshes: &[TerrainVoxelSourceMesh],
    ) -> GalResult<bool> {
        if !self.is_initialized() || meshes.len() != self.meshes.len() {
            return Ok(false);
        }
        let mut seen = std::collections::HashMap::with_capacity(meshes.len());
        for mesh in meshes {
            if mesh.mesh_key == 0 || mesh.mesh_generation == 0 {
                return Err(GalError::invalid_argument(
                    "terrain voxel source mesh key and generation must be non-zero",
                ));
            }
            if mesh.transform.iter().any(|value| !value.is_finite()) {
                return Err(GalError::invalid_argument(
                    "terrain voxel source mesh transform is not finite",
                ));
            }
            if seen.insert(mesh.mesh_key, ()).is_some() {
                return Err(GalError::invalid_argument(format!(
                    "terrain voxel source snapshot contains duplicate mesh key {}",
                    mesh.mesh_key
                )));
            }
            let Some(existing) = self.meshes.get(&mesh.mesh_key) else {
                return Ok(false);
            };
            if mesh.mesh_generation != existing.mesh_generation {
                return Ok(false);
            }
            let Some(identity) = &existing.source_identity else {
                return Ok(false);
            };
            if identity.transform != mesh.transform
                || !Arc::ptr_eq(&identity.vertices, &mesh.vertices)
                || !Arc::ptr_eq(&identity.indices, &mesh.indices)
            {
                return Ok(false);
            }
        }
        Ok(true)
    }
}

pub(super) fn create_volume_view(
    gal: &mut VulkanicGal,
    descriptor: &VoxelLightVolumeDescriptor,
    parity: &str,
    texture: Handle,
) -> GalResult<Handle> {
    gal.create_texture_view(TextureViewDesc {
        label: format!("{}.flood-fill.{parity}.view", descriptor.identity.as_str()),
        texture,
        format: TextureFormat::Rgba16Float,
        base_mip: 0,
        mip_count: 1,
        base_layer: 0,
        layer_count: 1,
    })
}

impl TerrainOccupancyGpuResources {
    pub fn create(
        gal: &mut VulkanicGal,
        descriptor: &VoxelLightVolumeDescriptor,
    ) -> GalResult<Self> {
        descriptor.validate()?;
        let extent = Extent3d {
            width: descriptor.extent.width,
            height: descriptor.extent.height,
            depth: descriptor.extent.depth,
        };
        let texture = gal.create_texture(TextureDesc {
            label: format!("{}.occupancy", descriptor.identity.as_str()),
            dimension: TextureDimension::D3,
            format: TextureFormat::R8Uint,
            extent,
            mip_levels: 1,
            array_layers: 1,
            usages: vec![
                TextureUsage::Sampled,
                TextureUsage::Storage,
                TextureUsage::TransferDst,
            ],
        })?;
        let view = match gal.create_texture_view(TextureViewDesc {
            label: format!("{}.occupancy.view", descriptor.identity.as_str()),
            texture,
            format: TextureFormat::R8Uint,
            base_mip: 0,
            mip_count: 1,
            base_layer: 0,
            layer_count: 1,
        }) {
            Ok(view) => view,
            Err(error) => {
                let _ = gal.retire(texture);
                return Err(error);
            }
        };
        let upload_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{}.occupancy.upload", descriptor.identity.as_str()),
            size: descriptor
                .extent
                .byte_len(crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::OccupancyR8Uint),
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::HostWrite,
                BufferUsage::TransferSrc,
                BufferUsage::TransferDst,
            ],
        }) {
            Ok(buffer) => buffer,
            Err(error) => {
                let _ = gal.retire(view);
                let _ = gal.retire(texture);
                return Err(error);
            }
        };
        Ok(Self {
            texture,
            view,
            descriptor: descriptor.clone(),
            upload_buffer,
            initialized: false,
            upload_pending: false,
        })
    }

    /// Creation alone never authorizes a flood-fill dispatch: a successful
    /// occupancy upload must first establish the owned D3 field.
    pub fn is_initialized(&self) -> bool {
        self.initialized && !self.upload_pending
    }

    /// The enclosing transaction has already ordered the upload through a
    /// transfer-to-shader-read transition. It is usable by a later command in
    /// that same list, but cannot be exposed as a confirmed residency yet.
    pub(super) fn has_pending_submission(&self) -> bool {
        self.upload_pending
    }

    pub(super) fn validate_mapping(&self, descriptor: &VoxelLightVolumeDescriptor) -> GalResult<()> {
        descriptor.validate()?;
        let active = &self.descriptor;
        if descriptor.identity != active.identity
            || descriptor.shader_pack_generation != active.shader_pack_generation
            || descriptor.world_generation != active.world_generation
            || descriptor.resource_generation != active.resource_generation
            || descriptor.extent != active.extent
            || descriptor.requirements != active.requirements
        {
            return Err(GalError::invalid_argument(
                "terrain occupancy mapping update would reuse an incompatible D3 resource",
            ));
        }
        Ok(())
    }

    pub(super) fn update_mapping(&mut self, descriptor: &VoxelLightVolumeDescriptor) -> GalResult<()> {
        self.validate_mapping(descriptor)?;
        self.descriptor = descriptor.clone();
        Ok(())
    }

    /// Records upload operations but does not change residency state until the
    /// caller confirms that the containing submission was accepted.
    pub fn append_upload(
        &mut self,
        update: &VoxelLightVolumeUpdate,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.upload_pending {
            return Err(GalError::invalid_argument(
                "terrain occupancy GPU upload is already awaiting submission confirmation",
            ));
        }
        update.validate(&self.descriptor)?;
        if update.kind != VoxelLightVolumeKind::Occupancy {
            return Err(GalError::invalid_argument(
                "occupancy residency accepts only occupancy updates",
            ));
        }
        let region = update.region;
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.upload_buffer,
            offset: 0,
            data: update.texels.clone(),
        });
        operations.push(CommandOp::Barrier(resource_barrier(
            self.upload_buffer,
            None,
            TextureUsageState::TransferDst,
            TextureUsageState::TransferSrc,
        )));
        operations.push(CommandOp::Barrier(resource_barrier(
            self.texture,
            None,
            if self.initialized {
                TextureUsageState::ShaderStorageRead
            } else {
                TextureUsageState::Undefined
            },
            TextureUsageState::TransferDst,
        )));
        operations.push(CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
            buffer: self.upload_buffer,
            buffer_offset: 0,
            bytes_per_row: region.extent.width,
            rows_per_image: region.extent.height,
            texture: self.texture,
            texture_mip: 0,
            texture_layer: 0,
            texture_origin: TextureOrigin3d {
                x: region.x,
                y: region.y,
                z: region.z,
            },
            extent: Extent3d {
                width: region.extent.width,
                height: region.extent.height,
                depth: region.extent.depth,
            },
        }));
        operations.push(CommandOp::Barrier(resource_barrier(
            self.texture,
            None,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderStorageRead,
        )));
        self.upload_pending = true;
        Ok(())
    }

    /// Uploads several region patches of an initialized field in one
    /// transfer, packed into the upload buffer. Returns `Ok(false)` without
    /// recording anything when they do not fit (the caller then falls back
    /// to a single whole-field upload).
    pub(crate) fn append_patch_uploads(
        &mut self,
        patches: &[VoxelLightVolumeUpdate],
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        if self.upload_pending {
            return Err(GalError::invalid_argument(
                "terrain occupancy GPU upload is already awaiting submission confirmation",
            ));
        }
        if !self.initialized {
            return Err(GalError::invalid_argument(
                "terrain occupancy patch uploads require an initialized texture",
            ));
        }
        let capacity = self.descriptor.extent.byte_len(
            crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeFormat::OccupancyR8Uint,
        );
        let mut offsets = Vec::with_capacity(patches.len());
        let mut cursor = 0_u64;
        for patch in patches {
            patch.validate(&self.descriptor)?;
            if patch.kind != VoxelLightVolumeKind::Occupancy {
                return Err(GalError::invalid_argument(
                    "occupancy residency accepts only occupancy updates",
                ));
            }
            offsets.push(cursor);
            cursor = (cursor + patch.texels.len() as u64 + 15) & !15;
        }
        if cursor > capacity {
            return Ok(false);
        }
        for (patch, offset) in patches.iter().zip(&offsets) {
            operations.push(CommandOp::HostWriteBuffer {
                buffer: self.upload_buffer,
                offset: *offset,
                data: patch.texels.clone(),
            });
        }
        operations.push(CommandOp::Barrier(resource_barrier(
            self.upload_buffer,
            None,
            TextureUsageState::TransferDst,
            TextureUsageState::TransferSrc,
        )));
        // GAL tracks texture hazards per subresource, so every patch copy is
        // its own transfer-write scope, bracketed by real state transitions.
        for (patch, offset) in patches.iter().zip(&offsets) {
            let region = patch.region;
            operations.push(CommandOp::Barrier(resource_barrier(
                self.texture,
                None,
                TextureUsageState::ShaderStorageRead,
                TextureUsageState::TransferDst,
            )));
            operations.push(CommandOp::CopyBufferToTexture(BufferImageCopyRegion {
                buffer: self.upload_buffer,
                buffer_offset: *offset,
                bytes_per_row: region.extent.width,
                rows_per_image: region.extent.height,
                texture: self.texture,
                texture_mip: 0,
                texture_layer: 0,
                texture_origin: TextureOrigin3d {
                    x: region.x,
                    y: region.y,
                    z: region.z,
                },
                extent: Extent3d {
                    width: region.extent.width,
                    height: region.extent.height,
                    depth: region.extent.depth,
                },
            }));
            operations.push(CommandOp::Barrier(resource_barrier(
                self.texture,
                None,
                TextureUsageState::TransferDst,
                TextureUsageState::ShaderStorageRead,
            )));
        }
        self.upload_pending = true;
        Ok(true)
    }

    pub fn confirm_submission(&mut self) -> GalResult<()> {
        if !self.upload_pending {
            return Err(GalError::invalid_argument(
                "no terrain occupancy GPU upload is pending confirmation",
            ));
        }
        self.initialized = true;
        self.upload_pending = false;
        Ok(())
    }

    pub fn discard_pending_submission(&mut self) {
        self.upload_pending = false;
    }

    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        gal.destroy(self.upload_buffer)?;
        gal.destroy(self.view)?;
        gal.destroy(self.texture)
    }
}

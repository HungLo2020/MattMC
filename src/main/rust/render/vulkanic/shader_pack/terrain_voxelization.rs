//! Rust-owned semantic occupancy generation for shader-pack voxel volumes.
//!
//! This is deliberately a private preparation stage. It consumes copied mesh
//! semantics and emits typed volume updates; it has no Java renderer objects,
//! Iris state, backend handles, or selected-source admission side effects.

use std::collections::BTreeMap;
use std::sync::Arc;

use crate::render::vulkanic::commands::{
    BufferImageCopyRegion, CommandOp, ResourceBarrier, TextureOrigin3d, TextureUsageState,
};
use crate::render::vulkanic::error::{GalError, GalResult};
use crate::render::vulkanic::gal::VulkanicGal;
use crate::render::vulkanic::handles::Handle;
use crate::render::vulkanic::resources::{
    AccessFlags, BufferDesc, BufferUsage, CombinedTextureSamplerDesc, ComputePipelineDesc,
    Extent3d, MemoryDomain, PipelineLayoutDesc, PipelineStageFlags, QueueClass, ResourceBinding,
    ResourceBindingDesc, ResourceBindingKind, ResourceLayoutDesc, ResourceSetDesc,
    SamplerAddressMode, SamplerDesc, SamplerFilter, ShaderCodeFormat, ShaderModuleDesc,
    ShaderStage, TextureDesc, TextureDimension, TextureFormat, TextureUsage, TextureViewDesc,
};
use crate::render::vulkanic::world_primitive_frontend::{
    TerrainVoxelSourceMesh, TerrainVoxelSourceVertex, WorldMeshAsset, WorldMeshVertex,
    WORLD_MESH_VERTEX_LAYOUT_V3, WORLD_STRATUM_TERRAIN,
};

use super::programs::shader_stage_code_for_backend;
use super::voxel_emission_table::{VoxelEmissionTable, VOXEL_TINT_COUNT};
use super::voxel_light_volume::{
    flood_fill_output_field_for_frame, flood_fill_source_field_for_frame, VoxelLightVolumeCache,
    VoxelLightVolumeDescriptor, VoxelLightVolumeFrameMapping, VoxelLightVolumeKind,
    VoxelLightVolumeMapping, VoxelLightVolumeReadiness, VoxelLightVolumeRegion,
    VoxelLightVolumeShaderMapping, VoxelLightVolumeTemporalMapping, VoxelLightVolumeUpdate,
    VoxelLightVolumeViewDirection,
};
use super::voxel_material_map::VoxelMaterialMap;

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
    descriptor: VoxelLightVolumeDescriptor,
    materials: VoxelMaterialMap,
    cache: VoxelLightVolumeCache,
    /// Last successfully submitted occupancy field. A candidate never becomes
    /// visible to the semantic cache until its GPU upload is accepted.
    occupancy: Vec<u8>,
    initialized: bool,
    pending_upload: Option<VoxelLightVolumeUpdate>,
    pending_occupancy: Option<Vec<u8>>,
}

/// Private explicit GAL residency for occupancy. It is not connected to any
/// shader runtime or public semantic binding yet.
#[derive(Clone, Debug)]
pub struct TerrainOccupancyGpuResources {
    pub texture: Handle,
    pub view: Handle,
    descriptor: VoxelLightVolumeDescriptor,
    upload_buffer: Handle,
    initialized: bool,
    upload_pending: bool,
}

/// An explicit update to one stable static-terrain mesh identity. A partial
/// update is never inferred from a snapshot omission: callers either replace a
/// generation or remove that exact generation. This keeps the CPU occupancy
/// field coherent with the D3 resource when section visibility changes.
#[derive(Clone, Copy, Debug)]
pub enum TerrainOccupancyMeshDelta<'a> {
    Upsert {
        mesh: &'a WorldMeshAsset,
        model_transform: [f32; 16],
        stratum: u32,
    },
    Remove {
        mesh_key: u64,
        mesh_generation: u64,
    },
}

#[derive(Clone, Debug, PartialEq)]
struct TerrainOccupancyMeshSnapshot {
    mesh_generation: u64,
    /// Complementary's `UpdateVoxelMap` is a vertex-stage image store. Keep
    /// the referenced indexed vertices rather than approximating the source
    /// with a CPU triangle-volume rasterizer.
    samples: Vec<TerrainVoxelSample>,
    /// Immutable, Rust-owned source arrays identify an unchanged source mesh
    /// before transforming it into voxel samples. The runtime retains these
    /// only as an ownership-safe cache key; no Java or backend buffer is
    /// borrowed across submission.
    source_identity: Option<TerrainOccupancySourceIdentity>,
}

#[derive(Clone, Debug, PartialEq)]
struct TerrainOccupancySourceIdentity {
    vertices: Arc<Vec<TerrainVoxelSourceVertex>>,
    indices: Arc<Vec<u32>>,
    transform: [f32; 16],
}

/// One private, generation-bound occupancy upload transaction. It owns only
/// static-terrain semantic mesh extraction and an `R8Uint` D3 residency; it
/// cannot bind a terrain program or admit selected shader-pack execution.
#[derive(Debug)]
pub struct TerrainOccupancyRuntime {
    voxelizer: TerrainOccupancyVoxelizer,
    resources: TerrainOccupancyGpuResources,
    upload_pending: bool,
    /// Last GPU-confirmed complete mesh set. Rebuilding a candidate from this
    /// set avoids deleting unrelated occupancy when one section changes.
    meshes: BTreeMap<u64, TerrainOccupancyMeshSnapshot>,
    /// The exact mesh set whose occupancy payload is currently in flight.
    pending_meshes: Option<BTreeMap<u64, TerrainOccupancyMeshSnapshot>>,
    /// Mapping-aware updates stage a cloned voxelizer so rejected submissions
    /// can restore the previous complete world-to-volume interpretation.
    pending_voxelizer_rollback: Option<TerrainOccupancyVoxelizer>,
}

/// Private, Rust-owned occupancy plus colored-light preparation. It combines
/// the independently validated D3 occupancy upload, source-derived emission
/// tables, and ping-pong compute lifecycle into one submission transaction.
/// It still has no terrain-program binding or selected-source admission path.
#[derive(Debug)]
pub struct TerrainColoredLightRuntime {
    occupancy: TerrainOccupancyRuntime,
    flood_fill: TerrainFloodFillGpuResources,
    compute: TerrainFloodFillComputeResources,
    sampling: TerrainVoxelLightSamplingResources,
    emission: VoxelEmissionTable,
    submission_pending: bool,
    pending_compute_rollback: Option<TerrainFloodFillComputeResources>,
    pending_flood_mapping_rollback: Option<VoxelLightVolumeDescriptor>,
}

/// Private, backend-neutral draw binding for one confirmed semantic volume.
/// The handles stay wholly inside Rust; the frontend will eventually place
/// this layout after its existing mesh layout when it lowers a selected source
/// program. No Java, FFI, or backend-native object sees this contract.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub(crate) struct TerrainVoxelLightSamplingBinding {
    pub resource_layout: Handle,
    pub resource_set: Handle,
    pub active_light_field: VoxelLightVolumeKind,
    pub resource_generation: u64,
}

#[derive(Debug)]
struct TerrainVoxelLightSamplingResources {
    resource_layout: Handle,
    even_resource_set: Handle,
    odd_resource_set: Handle,
    occupancy_sampler: Handle,
    even_light_sampler: Handle,
    odd_light_sampler: Handle,
    mapping_buffer: Handle,
    confirmed_mapping: Option<VoxelLightVolumeMapping>,
    mapping_upload_pending: bool,
}

impl TerrainOccupancyRuntime {
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
    /// Crossing a camera cell rebuilds the semantic field while retaining the
    /// owned D3 allocation; fractional motion updates only the mapping.
    pub(crate) fn append_terrain_source_snapshot_for_mapping(
        &mut self,
        mapping: VoxelLightVolumeMapping,
        meshes: impl IntoIterator<Item = TerrainVoxelSourceMesh>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        if self.upload_pending {
            return Err(GalError::invalid_argument(
                "terrain occupancy upload is already pending submission confirmation",
            ));
        }
        let meshes = meshes.into_iter().collect::<Vec<_>>();
        let source_meshes_match = self.confirmed_terrain_source_meshes_match(&meshes)?;

        // Work on a copy so an invalid mesh or failed command append cannot
        // change the live volume's world-to-cell interpretation.
        let mut staged_voxelizer = self.voxelizer.clone();
        let revoxelization_required = staged_voxelizer.update_mapping(mapping)?;
        if source_meshes_match && !revoxelization_required {
            // The mapping may have changed fractionally, but voxel occupancy
            // is cell-addressed. Preserve the same complete D3 field and
            // update only the semantic mapping; do not rebuild the CPU field
            // or enqueue another upload for an unchanged terrain snapshot.
            self.resources
                .validate_mapping(staged_voxelizer.descriptor())?;
            self.resources
                .update_mapping(staged_voxelizer.descriptor())?;
            self.voxelizer = staged_voxelizer;
            return Ok(TerrainOccupancyUpdateStats::default());
        }
        let candidate = terrain_voxel_source_snapshot(meshes)?;
        self.validate_snapshot_generations(&candidate)?;
        let samples = candidate
            .values()
            .flat_map(|mesh| mesh.samples.iter().copied());
        let stats = staged_voxelizer.update_from_samples(samples)?;
        self.resources
            .validate_mapping(staged_voxelizer.descriptor())?;
        let Some(update) = staged_voxelizer.pending_upload() else {
            self.resources
                .update_mapping(staged_voxelizer.descriptor())?;
            self.voxelizer = staged_voxelizer;
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
                        if snapshot.mesh_generation < existing.mesh_generation {
                            return Err(GalError::invalid_argument(format!(
                                "terrain occupancy mesh {mesh_key} update generation {} is older than live generation {}",
                                snapshot.mesh_generation, existing.mesh_generation
                            )));
                        }
                        if snapshot.mesh_generation == existing.mesh_generation {
                            if &snapshot != existing {
                                return Err(GalError::invalid_argument(format!(
                                    "terrain occupancy mesh {mesh_key} changed semantic data without advancing generation {}",
                                    snapshot.mesh_generation
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
        self.meshes = self.pending_meshes.take().ok_or_else(|| {
            GalError::invalid_argument("terrain occupancy pending mesh set is missing")
        })?;
        self.pending_voxelizer_rollback = None;
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
            }
            self.pending_meshes = None;
            self.upload_pending = false;
        }
    }

    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        self.resources.destroy(gal)
    }

    fn append_mesh_candidate(
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

    fn validate_snapshot_generations(
        &self,
        candidate: &BTreeMap<u64, TerrainOccupancyMeshSnapshot>,
    ) -> GalResult<()> {
        for (mesh_key, incoming) in candidate {
            let Some(existing) = self.meshes.get(mesh_key) else {
                continue;
            };
            if incoming.mesh_generation < existing.mesh_generation {
                return Err(GalError::invalid_argument(format!(
                    "terrain occupancy snapshot mesh {mesh_key} generation {} is older than live generation {}",
                    incoming.mesh_generation, existing.mesh_generation
                )));
            }
            if incoming.mesh_generation == existing.mesh_generation && incoming != existing {
                return Err(GalError::invalid_argument(format!(
                    "terrain occupancy snapshot mesh {mesh_key} changed semantic data without advancing generation {}",
                    incoming.mesh_generation
                )));
            }
        }
        Ok(())
    }

    /// Confirms that a complete source snapshot is backed by the exact
    /// immutable arrays already validated and retained by this runtime. This
    /// is a Rust-owned cache identity, not a content hash: asset replacement
    /// creates new arrays and advances a semantic generation before it can be
    /// observed here.
    fn confirmed_terrain_source_meshes_match(
        &self,
        meshes: &[TerrainVoxelSourceMesh],
    ) -> GalResult<bool> {
        if !self.is_initialized() || meshes.len() != self.meshes.len() {
            return Ok(false);
        }
        let mut seen = BTreeMap::new();
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
            if mesh.mesh_generation < existing.mesh_generation {
                return Err(GalError::invalid_argument(format!(
                    "terrain occupancy snapshot mesh {} generation {} is older than live generation {}",
                    mesh.mesh_key, mesh.mesh_generation, existing.mesh_generation
                )));
            }
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

impl TerrainVoxelLightSamplingResources {
    fn create(
        gal: &mut VulkanicGal,
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
    ) -> GalResult<Self> {
        if occupancy.descriptor != flood_fill.descriptor {
            return Err(GalError::invalid_argument(
                "voxel-light sampling resources require matching D3 generations",
            ));
        }
        let label = occupancy.descriptor.identity.as_str();
        let occupancy_sampler =
            gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!("{label}.terrain-volume.occupancy-sampler"),
                texture_view: occupancy.view,
                sampler: flood_fill.sampler,
            })?;
        let even_light_sampler =
            match gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!("{label}.terrain-volume.even-light-sampler"),
                texture_view: flood_fill.even_view,
                sampler: flood_fill.sampler,
            }) {
                Ok(handle) => handle,
                Err(error) => {
                    let _ = gal.destroy(occupancy_sampler);
                    return Err(error);
                }
            };
        let odd_light_sampler =
            match gal.create_combined_texture_sampler(CombinedTextureSamplerDesc {
                label: format!("{label}.terrain-volume.odd-light-sampler"),
                texture_view: flood_fill.odd_view,
                sampler: flood_fill.sampler,
            }) {
                Ok(handle) => handle,
                Err(error) => {
                    let _ = gal.destroy(even_light_sampler);
                    let _ = gal.destroy(occupancy_sampler);
                    return Err(error);
                }
            };
        let resource_layout = match gal.create_resource_layout(ResourceLayoutDesc {
            label: format!("{label}.terrain-volume.sample-layout"),
            bindings: vec![
                ResourceBindingDesc {
                    binding: 0,
                    kind: ResourceBindingKind::CombinedTextureSampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 1,
                    kind: ResourceBindingKind::CombinedTextureSampler,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
                ResourceBindingDesc {
                    binding: 2,
                    kind: ResourceBindingKind::UniformBuffer,
                    stages: PipelineStageFlags::DRAW,
                    array_count: 1,
                    optional: false,
                    dynamic_offset_count: 0,
                },
            ],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(odd_light_sampler);
                let _ = gal.destroy(even_light_sampler);
                let _ = gal.destroy(occupancy_sampler);
                return Err(error);
            }
        };
        let mapping_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{label}.terrain-volume.mapping"),
            size: VoxelLightVolumeShaderMapping::STD140_SIZE as u64,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(resource_layout);
                let _ = gal.destroy(odd_light_sampler);
                let _ = gal.destroy(even_light_sampler);
                let _ = gal.destroy(occupancy_sampler);
                return Err(error);
            }
        };
        let create_set = |gal: &mut VulkanicGal, name: &str, light_sampler: Handle| {
            gal.create_resource_set(ResourceSetDesc {
                label: format!("{label}.terrain-volume.{name}.set"),
                layout: resource_layout,
                bindings: vec![
                    ResourceBinding {
                        binding: 0,
                        array_index: 0,
                        resource: occupancy_sampler,
                        kind: ResourceBindingKind::CombinedTextureSampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 1,
                        array_index: 0,
                        resource: light_sampler,
                        kind: ResourceBindingKind::CombinedTextureSampler,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: None,
                    },
                    ResourceBinding {
                        binding: 2,
                        array_index: 0,
                        resource: mapping_buffer,
                        kind: ResourceBindingKind::UniformBuffer,
                        access: AccessFlags::READ,
                        dynamic_offsets: Vec::new(),
                        buffer_range: Some(VoxelLightVolumeShaderMapping::STD140_SIZE as u64),
                    },
                ],
            })
        };
        let even_resource_set = match create_set(gal, "even", even_light_sampler) {
            Ok(set) => set,
            Err(error) => {
                let _ = gal.destroy(mapping_buffer);
                let _ = gal.destroy(resource_layout);
                let _ = gal.destroy(odd_light_sampler);
                let _ = gal.destroy(even_light_sampler);
                let _ = gal.destroy(occupancy_sampler);
                return Err(error);
            }
        };
        let odd_resource_set = match create_set(gal, "odd", odd_light_sampler) {
            Ok(set) => set,
            Err(error) => {
                let _ = gal.destroy(even_resource_set);
                let _ = gal.destroy(mapping_buffer);
                let _ = gal.destroy(resource_layout);
                let _ = gal.destroy(odd_light_sampler);
                let _ = gal.destroy(even_light_sampler);
                let _ = gal.destroy(occupancy_sampler);
                return Err(error);
            }
        };
        Ok(Self {
            resource_layout,
            even_resource_set,
            odd_resource_set,
            occupancy_sampler,
            even_light_sampler,
            odd_light_sampler,
            mapping_buffer,
            confirmed_mapping: None,
            mapping_upload_pending: false,
        })
    }

    fn append_mapping_upload(
        &mut self,
        descriptor: &VoxelLightVolumeDescriptor,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        descriptor.validate()?;
        if self.mapping_upload_pending {
            return Err(GalError::invalid_argument(
                "terrain voxel-light mapping upload is already pending confirmation",
            ));
        }
        if self.confirmed_mapping.as_ref() == Some(&descriptor.mapping) {
            return Ok(false);
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.mapping_buffer,
            offset: 0,
            data: VoxelLightVolumeShaderMapping::from_descriptor(descriptor)?
                .std140_bytes()
                .to_vec(),
        });
        self.mapping_upload_pending = true;
        Ok(true)
    }

    fn mapping_ready_for(&self, descriptor: &VoxelLightVolumeDescriptor) -> bool {
        !self.mapping_upload_pending && self.confirmed_mapping.as_ref() == Some(&descriptor.mapping)
    }

    fn confirm_mapping_submission(
        &mut self,
        descriptor: &VoxelLightVolumeDescriptor,
    ) -> GalResult<()> {
        if self.mapping_upload_pending {
            self.confirmed_mapping = Some(descriptor.mapping);
            self.mapping_upload_pending = false;
        }
        Ok(())
    }

    fn discard_pending_mapping_upload(&mut self) {
        self.mapping_upload_pending = false;
    }

    fn binding_for_frame(
        &self,
        readiness: &VoxelLightVolumeReadiness,
        frame_counter: u64,
    ) -> GalResult<TerrainVoxelLightSamplingBinding> {
        let binding = readiness.binding_for_frame(frame_counter)?;
        let resource_set = match binding.active_light_field {
            VoxelLightVolumeKind::FloodFillEven => self.even_resource_set,
            VoxelLightVolumeKind::FloodFillOdd => self.odd_resource_set,
            VoxelLightVolumeKind::Occupancy => {
                return Err(GalError::invalid_argument(
                    "terrain voxel-light sampling cannot select the occupancy field as colored light",
                ));
            }
        };
        Ok(TerrainVoxelLightSamplingBinding {
            resource_layout: self.resource_layout,
            resource_set,
            active_light_field: binding.active_light_field,
            resource_generation: binding.resource_generation,
        })
    }

    fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        gal.destroy(self.odd_resource_set)?;
        gal.destroy(self.even_resource_set)?;
        gal.destroy(self.odd_light_sampler)?;
        gal.destroy(self.even_light_sampler)?;
        gal.destroy(self.occupancy_sampler)?;
        gal.destroy(self.mapping_buffer)?;
        gal.destroy(self.resource_layout)
    }
}

impl TerrainColoredLightRuntime {
    pub fn create(
        gal: &mut VulkanicGal,
        descriptor: VoxelLightVolumeDescriptor,
        materials: VoxelMaterialMap,
        emission: VoxelEmissionTable,
    ) -> GalResult<Self> {
        if emission.shader_pack_generation() != descriptor.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "voxel emission table must match the colored-light generation",
            ));
        }
        let occupancy = TerrainOccupancyRuntime::create(gal, descriptor.clone(), materials)?;
        let flood_fill = match TerrainFloodFillGpuResources::create(gal, &descriptor) {
            Ok(resources) => resources,
            Err(error) => {
                let _ = occupancy.destroy(gal);
                return Err(error);
            }
        };
        let sampling = match TerrainVoxelLightSamplingResources::create(
            gal,
            &occupancy.resources,
            &flood_fill,
        ) {
            Ok(resources) => resources,
            Err(error) => {
                let _ = flood_fill.destroy(gal);
                let _ = occupancy.destroy(gal);
                return Err(error);
            }
        };
        let compute = match TerrainFloodFillComputeResources::create(
            gal,
            &occupancy.resources,
            &flood_fill,
        ) {
            Ok(resources) => resources,
            Err(error) => {
                let _ = sampling.destroy(gal);
                let _ = flood_fill.destroy(gal);
                let _ = occupancy.destroy(gal);
                return Err(error);
            }
        };
        Ok(Self {
            occupancy,
            flood_fill,
            compute,
            sampling,
            emission,
            submission_pending: false,
            pending_compute_rollback: None,
            pending_flood_mapping_rollback: None,
        })
    }

    pub fn descriptor(&self) -> &VoxelLightVolumeDescriptor {
        self.occupancy.descriptor()
    }

    pub fn mesh_snapshot_count(&self) -> usize {
        self.occupancy.mesh_snapshot_count()
    }

    pub fn is_ready_for_frame(&self, frame_counter: u64) -> bool {
        self.occupancy.is_initialized()
            && self.flood_fill.emission_ready()
            && self.flood_fill.tint_ready()
            && self.sampling.mapping_ready_for(self.occupancy.descriptor())
            && self.compute.is_initialized_for_frame(frame_counter)
            && self.compute.mapping_ready_for(self.occupancy.descriptor())
            && !self.submission_pending
    }

    /// Returns semantic admission data only after the exact owned occupancy
    /// and both ping-pong fields have reached confirmed, matching state. The
    /// value has no texture handles and cannot bind or select a terrain pass.
    pub(crate) fn readiness(&self) -> GalResult<VoxelLightVolumeReadiness> {
        if self.submission_pending {
            return Err(GalError::invalid_argument(
                "colored-light volume generation has a submission pending confirmation",
            ));
        }
        if !self.flood_fill.emission_ready() || !self.flood_fill.tint_ready() {
            return Err(GalError::invalid_argument(
                "colored-light volume source tables are not confirmed for this generation",
            ));
        }
        if !self.sampling.mapping_ready_for(self.occupancy.descriptor()) {
            return Err(GalError::invalid_argument(
                "colored-light volume mapping uniform is not confirmed for this generation",
            ));
        }
        if !self.compute.mapping_ready_for(self.occupancy.descriptor()) {
            return Err(GalError::invalid_argument(
                "colored-light flood-fill parity is not confirmed for the current camera mapping",
            ));
        }
        VoxelLightVolumeReadiness::new(
            self.occupancy.descriptor().clone(),
            self.occupancy.is_initialized(),
            self.compute.even_initialized,
            self.compute.odd_initialized,
        )
    }

    /// Returns the parity-selected owned resource set only for a complete
    /// semantic generation. This is preparation for a future selected terrain
    /// pipeline and cannot issue a draw by itself.
    pub(crate) fn sampling_binding(
        &self,
        frame_counter: u64,
    ) -> GalResult<TerrainVoxelLightSamplingBinding> {
        self.sampling
            .binding_for_frame(&self.readiness()?, frame_counter)
    }

    pub(crate) fn has_pending_submission(&self) -> bool {
        self.submission_pending
    }

    /// Appends at most one colored-light compute step once its exact owned
    /// occupancy and source-derived tables are confirmed. The first complete
    /// occupancy generation seeds both ping-pong fields. Later occupancy and
    /// camera-cell updates preserve that history and require a source-derived
    /// temporal propagation before the changed mapping can be sampled.
    pub(crate) fn append_terrain_source_snapshot_for_mapping(
        &mut self,
        frame_counter: u64,
        mapping: VoxelLightVolumeMapping,
        view_direction: Option<VoxelLightVolumeViewDirection>,
        meshes: impl IntoIterator<Item = TerrainVoxelSourceMesh>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<TerrainOccupancyUpdateStats> {
        if self.submission_pending {
            return Err(GalError::invalid_argument(
                "colored-light runtime already has a submission awaiting confirmation",
            ));
        }
        let result = (|| {
            let had_confirmed_occupancy = self.occupancy.is_initialized();
            let stats = self
                .occupancy
                .append_terrain_source_snapshot_for_mapping(mapping, meshes, operations)?;
            if self.flood_fill.descriptor.mapping != self.occupancy.descriptor().mapping {
                self.pending_flood_mapping_rollback = Some(self.flood_fill.descriptor.clone());
                self.flood_fill
                    .update_mapping(self.occupancy.descriptor())?;
            }
            // Complementary re-voxelizes occupancy independently of its
            // flood-fill history. Only the first complete field needs a seed;
            // later mesh or camera-cell updates are consumed by the next
            // `previousCameraPosition - cameraPosition` propagation.
            if stats.updated_region.is_some() && !had_confirmed_occupancy {
                self.pending_compute_rollback = Some(self.compute.clone());
                self.compute.invalidate();
            }
            self.flood_fill
                .append_emission_upload(&self.emission, operations)?;
            self.flood_fill
                .append_tint_upload(&self.emission, operations)?;
            self.sampling
                .append_mapping_upload(self.occupancy.descriptor(), operations)?;

            if !self.occupancy.has_pending_submission()
                && self.flood_fill.emission_ready()
                && self.flood_fill.tint_ready()
            {
                if !self.compute.has_seed() {
                    self.compute.append_initialization(
                        &self.occupancy.resources,
                        &self.flood_fill,
                        frame_counter,
                        operations,
                    )?;
                } else {
                    self.compute.append_propagation(
                        &self.occupancy.resources,
                        &self.flood_fill,
                        frame_counter,
                        view_direction,
                        operations,
                    )?;
                }
            }
            self.submission_pending = self.occupancy.has_pending_submission()
                || self.flood_fill.has_pending_upload()
                || self.sampling.mapping_upload_pending
                || self.compute.has_pending_submission();
            Ok(stats)
        })();
        if result.is_err() {
            self.discard_submission();
        }
        result
    }

    pub fn confirm_submission(&mut self) -> GalResult<()> {
        if !self.submission_pending {
            return Err(GalError::invalid_argument(
                "no colored-light submission is pending confirmation",
            ));
        }
        if self.occupancy.has_pending_submission() {
            self.occupancy.confirm_submission()?;
        }
        self.flood_fill.confirm_pending_uploads()?;
        self.sampling
            .confirm_mapping_submission(self.occupancy.descriptor())?;
        self.compute.confirm_pending_submission()?;
        self.submission_pending = false;
        self.pending_compute_rollback = None;
        self.pending_flood_mapping_rollback = None;
        Ok(())
    }

    pub fn discard_submission(&mut self) {
        self.occupancy.discard_submission();
        self.flood_fill.discard_pending_uploads();
        self.sampling.discard_pending_mapping_upload();
        if let Some(previous) = self.pending_compute_rollback.take() {
            self.compute = previous;
        } else {
            self.compute.discard_pending_submission();
        }
        if let Some(previous) = self.pending_flood_mapping_rollback.take() {
            self.flood_fill.descriptor = previous;
        }
        self.submission_pending = false;
    }

    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        self.compute.destroy(gal)?;
        self.sampling.destroy(gal)?;
        self.flood_fill.destroy(gal)?;
        self.occupancy.destroy(gal)
    }
}

fn static_terrain_mesh_snapshot<'a>(
    meshes: impl IntoIterator<Item = (&'a WorldMeshAsset, [f32; 16], u32)>,
) -> GalResult<BTreeMap<u64, TerrainOccupancyMeshSnapshot>> {
    let mut snapshot = BTreeMap::new();
    for (mesh, model_transform, stratum) in meshes {
        let Some((mesh_key, entry)) = static_terrain_mesh_entry(mesh, model_transform, stratum)?
        else {
            continue;
        };
        if snapshot.insert(mesh_key, entry).is_some() {
            return Err(GalError::invalid_argument(format!(
                "terrain occupancy snapshot contains duplicate mesh key {mesh_key}",
            )));
        }
    }
    Ok(snapshot)
}

fn static_terrain_mesh_entry(
    mesh: &WorldMeshAsset,
    model_transform: [f32; 16],
    stratum: u32,
) -> GalResult<Option<(u64, TerrainOccupancyMeshSnapshot)>> {
    if stratum != WORLD_STRATUM_TERRAIN {
        return Ok(None);
    }
    if mesh.mesh_key == 0 || mesh.mesh_generation == 0 {
        return Err(GalError::invalid_argument(
            "terrain occupancy mesh key and generation must be non-zero",
        ));
    }
    if mesh.vertex_layout_version != WORLD_MESH_VERTEX_LAYOUT_V3 {
        return Err(GalError::invalid_argument(
            "static terrain occupancy requires world mesh vertex layout v3 at_midBlock semantics",
        ));
    }
    if model_transform.iter().any(|value| !value.is_finite()) {
        return Err(GalError::invalid_argument(
            "terrain occupancy mesh transform is not finite",
        ));
    }
    Ok(Some((
        mesh.mesh_key,
        indexed_terrain_snapshot(
            mesh.mesh_generation,
            mesh.vertices
                .iter()
                .map(|vertex| TerrainVoxelSample::from_mesh_vertex(vertex, model_transform))
                .collect(),
            decoded_mesh_indices(mesh)?,
            None,
        )?,
    )))
}

fn terrain_voxel_source_snapshot(
    meshes: impl IntoIterator<Item = TerrainVoxelSourceMesh>,
) -> GalResult<BTreeMap<u64, TerrainOccupancyMeshSnapshot>> {
    let mut snapshot = BTreeMap::new();
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
        let source_identity = TerrainOccupancySourceIdentity {
            vertices: Arc::clone(&mesh.vertices),
            indices: Arc::clone(&mesh.indices),
            transform: mesh.transform,
        };
        let entry = indexed_terrain_snapshot(
            mesh.mesh_generation,
            mesh.vertices
                .iter()
                .map(|vertex| TerrainVoxelSample {
                    vertex_position: vertex.position,
                    mid_block_packed: vertex.mid_block_packed,
                    shader_material_id: vertex.shader_material_id,
                    model_transform: mesh.transform,
                })
                .collect(),
            mesh.indices.as_ref().clone(),
            Some(source_identity),
        )?;
        if snapshot.insert(mesh.mesh_key, entry).is_some() {
            return Err(GalError::invalid_argument(format!(
                "terrain voxel source snapshot contains duplicate mesh key {}",
                mesh.mesh_key
            )));
        }
    }
    Ok(snapshot)
}

fn decoded_mesh_indices(mesh: &WorldMeshAsset) -> GalResult<Vec<u32>> {
    let stride = match mesh.index_type {
        crate::render::vulkanic::resources::IndexType::U16 => 2,
        crate::render::vulkanic::resources::IndexType::U32 => 4,
    };
    if mesh.index_bytes.len() % stride != 0 {
        return Err(GalError::invalid_argument(
            "terrain occupancy mesh indices are not aligned to their index type",
        ));
    }
    let count = mesh.index_bytes.len() / stride;
    let mut indices = Vec::with_capacity(count);
    for index in 0..count {
        let offset = index * stride;
        let value = match mesh.index_type {
            crate::render::vulkanic::resources::IndexType::U16 => {
                u16::from_ne_bytes([mesh.index_bytes[offset], mesh.index_bytes[offset + 1]]) as u32
            }
            crate::render::vulkanic::resources::IndexType::U32 => u32::from_ne_bytes([
                mesh.index_bytes[offset],
                mesh.index_bytes[offset + 1],
                mesh.index_bytes[offset + 2],
                mesh.index_bytes[offset + 3],
            ]),
        };
        indices.push(value);
    }
    Ok(indices)
}

fn indexed_terrain_snapshot(
    mesh_generation: u64,
    vertices: Vec<TerrainVoxelSample>,
    indices: Vec<u32>,
    source_identity: Option<TerrainOccupancySourceIdentity>,
) -> GalResult<TerrainOccupancyMeshSnapshot> {
    if vertices.is_empty() {
        return Err(GalError::invalid_argument(
            "terrain occupancy source has no vertices",
        ));
    }
    if indices.is_empty() || indices.len() % 3 != 0 {
        return Err(GalError::invalid_argument(
            "terrain occupancy source requires a non-empty triangle index stream",
        ));
    }
    let mut referenced = vec![false; vertices.len()];
    for index in indices {
        let index = usize::try_from(index).map_err(|_| {
            GalError::invalid_argument("terrain occupancy triangle index exceeds address space")
        })?;
        let Some(reference) = referenced.get_mut(index) else {
            return Err(GalError::invalid_argument(format!(
                "terrain occupancy triangle index {index} is outside {} source vertices",
                vertices.len()
            )));
        };
        *reference = true;
    }
    Ok(TerrainOccupancyMeshSnapshot {
        mesh_generation,
        samples: vertices
            .into_iter()
            .zip(referenced)
            .filter_map(|(sample, referenced)| referenced.then_some(sample))
            .collect(),
        source_identity,
    })
}

/// Private owned ping-pong storage for the semantic colored-light field. The
/// resource pair is intentionally separate from the flood-fill algorithm: it
/// establishes backend-neutral generation, parity, binding, and retirement
/// before any shader-pack program can consume the data.
#[derive(Clone, Debug)]
pub struct TerrainFloodFillGpuResources {
    descriptor: VoxelLightVolumeDescriptor,
    pub even_texture: Handle,
    pub even_view: Handle,
    pub odd_texture: Handle,
    pub odd_view: Handle,
    pub sampler: Handle,
    emission_buffer: Handle,
    tint_buffer: Handle,
    emission_generation: Option<u64>,
    emission_upload_pending: bool,
    tint_generation: Option<u64>,
    tint_upload_pending: bool,
}

/// A single source-derived flood-fill work step. This is intentionally only a
/// GAL command contract: resource-set construction and native descriptor
/// lowering remain outside the shader-pack semantic layer.
#[derive(Clone, Copy, Debug, Eq, PartialEq)]
pub struct TerrainFloodFillDispatch {
    pub frame_counter: u64,
    pub groups_x: u32,
    pub groups_y: u32,
    pub groups_z: u32,
}

/// Private owned compute objects for initializing and propagating the
/// source-derived voxel field. This deliberately has no terrain-pass binding
/// or source-plan admission path: it exists solely to establish the explicit
/// GAL resource, descriptor, and synchronization contract first.
#[derive(Clone, Debug)]
pub struct TerrainFloodFillComputeResources {
    init_layout: Handle,
    init_pipeline_layout: Handle,
    init_shader: Handle,
    init_pipeline: Handle,
    even_init_set: Handle,
    odd_init_set: Handle,
    propagate_layout: Handle,
    propagate_pipeline_layout: Handle,
    propagate_shader: Handle,
    propagate_pipeline: Handle,
    temporal_mapping_buffer: Handle,
    even_to_odd_set: Handle,
    odd_to_even_set: Handle,
    even_initialized: bool,
    odd_initialized: bool,
    pending_initialization: Option<u64>,
    pending_propagation: Option<u64>,
    confirmed_frame_mapping: Option<VoxelLightVolumeFrameMapping>,
    pending_frame_mapping: Option<VoxelLightVolumeFrameMapping>,
}

impl TerrainFloodFillDispatch {
    pub fn prepare(
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
        frame_counter: u64,
    ) -> GalResult<Self> {
        if occupancy.descriptor != flood_fill.descriptor {
            return Err(GalError::invalid_argument(
                "flood-fill occupancy and light resources use different generations",
            ));
        }
        if !occupancy.is_initialized() {
            return Err(GalError::invalid_argument(
                "flood-fill dispatch requires initialized occupancy data",
            ));
        }
        if !flood_fill.emission_ready() {
            return Err(GalError::invalid_argument(
                "flood-fill dispatch requires a confirmed source-derived emission table",
            ));
        }
        if !flood_fill.tint_ready() {
            return Err(GalError::invalid_argument(
                "flood-fill dispatch requires a confirmed source-derived tint table",
            ));
        }
        let extent = occupancy.descriptor.extent;
        Ok(Self {
            frame_counter,
            groups_x: extent.width.div_ceil(8),
            groups_y: extent.height.div_ceil(8),
            groups_z: extent.depth.div_ceil(8),
        })
    }

    pub fn append(
        self,
        pipeline: Handle,
        pipeline_layout: Handle,
        resource_set: Handle,
        operations: &mut Vec<CommandOp>,
    ) {
        operations.push(CommandOp::BindComputePipeline(pipeline));
        operations.push(CommandOp::BindResourceSet {
            pipeline_layout,
            set_index: 0,
            set: resource_set,
            dynamic_offsets: Vec::new(),
        });
        operations.push(CommandOp::Dispatch {
            groups_x: self.groups_x,
            groups_y: self.groups_y,
            groups_z: self.groups_z,
        });
    }
}

impl TerrainFloodFillComputeResources {
    pub fn create(
        gal: &mut VulkanicGal,
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
    ) -> GalResult<Self> {
        if occupancy.descriptor != flood_fill.descriptor {
            return Err(GalError::invalid_argument(
                "flood-fill compute resources require matching occupancy and light generations",
            ));
        }
        let label = flood_fill.descriptor.identity.as_str();
        let init_layout = gal.create_resource_layout(ResourceLayoutDesc {
            label: format!("{label}.flood-fill.init.layout"),
            bindings: vec![
                compute_binding(0, ResourceBindingKind::StorageTexture),
                compute_binding(1, ResourceBindingKind::StorageTexture),
                compute_binding(2, ResourceBindingKind::UniformBuffer),
            ],
        })?;
        let init_pipeline_layout = match gal.create_pipeline_layout(PipelineLayoutDesc {
            label: format!("{label}.flood-fill.init.pipeline-layout"),
            resource_layouts: vec![init_layout],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(init_layout);
                return Err(error);
            }
        };
        let init_shader = match gal.create_shader_module(ShaderModuleDesc {
            label: format!("{label}.flood-fill.init.compute"),
            stage: ShaderStage::Compute,
            code_format: ShaderCodeFormat::Glsl,
            code: shader_stage_code_for_backend(gal.capabilities().api, FLOOD_FILL_INIT_SHADER),
            entry_point: "main".to_owned(),
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(init_pipeline_layout);
                let _ = gal.destroy(init_layout);
                return Err(error);
            }
        };
        let init_pipeline = match gal.create_compute_pipeline(ComputePipelineDesc {
            label: format!("{label}.flood-fill.init.pipeline"),
            layout: init_pipeline_layout,
            shader: init_shader,
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(init_shader);
                let _ = gal.destroy(init_pipeline_layout);
                let _ = gal.destroy(init_layout);
                return Err(error);
            }
        };
        let even_init_set = match gal.create_resource_set(ResourceSetDesc {
            label: format!("{label}.flood-fill.init.even.set"),
            layout: init_layout,
            bindings: vec![
                compute_resource(
                    0,
                    occupancy.view,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::READ,
                ),
                compute_resource(
                    1,
                    flood_fill.even_view,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::WRITE,
                ),
                compute_resource(
                    2,
                    flood_fill.emission_buffer,
                    ResourceBindingKind::UniformBuffer,
                    AccessFlags::READ,
                ),
            ],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(init_pipeline);
                let _ = gal.destroy(init_shader);
                let _ = gal.destroy(init_pipeline_layout);
                let _ = gal.destroy(init_layout);
                return Err(error);
            }
        };

        let odd_init_set = match gal.create_resource_set(ResourceSetDesc {
            label: format!("{label}.flood-fill.init.odd.set"),
            layout: init_layout,
            bindings: vec![
                compute_resource(
                    0,
                    occupancy.view,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::READ,
                ),
                compute_resource(
                    1,
                    flood_fill.odd_view,
                    ResourceBindingKind::StorageTexture,
                    AccessFlags::WRITE,
                ),
                compute_resource(
                    2,
                    flood_fill.emission_buffer,
                    ResourceBindingKind::UniformBuffer,
                    AccessFlags::READ,
                ),
            ],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                destroy_init_compute(
                    gal,
                    even_init_set,
                    None,
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };

        let propagate_layout = match gal.create_resource_layout(ResourceLayoutDesc {
            label: format!("{label}.flood-fill.propagate.layout"),
            bindings: vec![
                compute_binding(0, ResourceBindingKind::StorageTexture),
                compute_binding(1, ResourceBindingKind::StorageTexture),
                compute_binding(2, ResourceBindingKind::StorageTexture),
                compute_binding(3, ResourceBindingKind::UniformBuffer),
                compute_binding(4, ResourceBindingKind::UniformBuffer),
                compute_binding(5, ResourceBindingKind::UniformBuffer),
            ],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let propagate_pipeline_layout = match gal.create_pipeline_layout(PipelineLayoutDesc {
            label: format!("{label}.flood-fill.propagate.pipeline-layout"),
            resource_layouts: vec![propagate_layout],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(propagate_layout);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let propagate_shader = match gal.create_shader_module(ShaderModuleDesc {
            label: format!("{label}.flood-fill.propagate.compute"),
            stage: ShaderStage::Compute,
            code_format: ShaderCodeFormat::Glsl,
            code: shader_stage_code_for_backend(
                gal.capabilities().api,
                FLOOD_FILL_PROPAGATE_SHADER,
            ),
            entry_point: "main".to_owned(),
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(propagate_pipeline_layout);
                let _ = gal.destroy(propagate_layout);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let propagate_pipeline = match gal.create_compute_pipeline(ComputePipelineDesc {
            label: format!("{label}.flood-fill.propagate.pipeline"),
            layout: propagate_pipeline_layout,
            shader: propagate_shader,
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(propagate_shader);
                let _ = gal.destroy(propagate_pipeline_layout);
                let _ = gal.destroy(propagate_layout);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let temporal_mapping_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{label}.flood-fill.temporal-mapping"),
            size: VoxelLightVolumeTemporalMapping::STD140_SIZE as u64,
            memory: MemoryDomain::Upload,
            usages: vec![BufferUsage::Uniform, BufferUsage::HostWrite],
        }) {
            Ok(handle) => handle,
            Err(error) => {
                destroy_propagate_compute(
                    gal,
                    propagate_pipeline,
                    propagate_shader,
                    propagate_pipeline_layout,
                    propagate_layout,
                );
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let even_to_odd_set = match gal.create_resource_set(ResourceSetDesc {
            label: format!("{label}.flood-fill.even-to-odd.set"),
            layout: propagate_layout,
            bindings: propagation_bindings(
                occupancy.view,
                flood_fill.even_view,
                flood_fill.odd_view,
                flood_fill.emission_buffer,
                flood_fill.tint_buffer,
                temporal_mapping_buffer,
            ),
        }) {
            Ok(handle) => handle,
            Err(error) => {
                destroy_propagate_compute(
                    gal,
                    propagate_pipeline,
                    propagate_shader,
                    propagate_pipeline_layout,
                    propagate_layout,
                );
                let _ = gal.destroy(temporal_mapping_buffer);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        let odd_to_even_set = match gal.create_resource_set(ResourceSetDesc {
            label: format!("{label}.flood-fill.odd-to-even.set"),
            layout: propagate_layout,
            bindings: propagation_bindings(
                occupancy.view,
                flood_fill.odd_view,
                flood_fill.even_view,
                flood_fill.emission_buffer,
                flood_fill.tint_buffer,
                temporal_mapping_buffer,
            ),
        }) {
            Ok(handle) => handle,
            Err(error) => {
                let _ = gal.destroy(even_to_odd_set);
                destroy_propagate_compute(
                    gal,
                    propagate_pipeline,
                    propagate_shader,
                    propagate_pipeline_layout,
                    propagate_layout,
                );
                let _ = gal.destroy(temporal_mapping_buffer);
                destroy_init_compute(
                    gal,
                    even_init_set,
                    Some(odd_init_set),
                    init_pipeline,
                    init_shader,
                    init_pipeline_layout,
                    init_layout,
                );
                return Err(error);
            }
        };
        Ok(Self {
            init_layout,
            init_pipeline_layout,
            init_shader,
            init_pipeline,
            even_init_set,
            odd_init_set,
            propagate_layout,
            propagate_pipeline_layout,
            propagate_shader,
            propagate_pipeline,
            temporal_mapping_buffer,
            even_to_odd_set,
            odd_to_even_set,
            even_initialized: false,
            odd_initialized: false,
            pending_initialization: None,
            pending_propagation: None,
            confirmed_frame_mapping: None,
            pending_frame_mapping: None,
        })
    }

    pub fn append_initialization(
        &mut self,
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
        frame_counter: u64,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.pending_initialization.is_some() || self.pending_frame_mapping.is_some() {
            return Err(GalError::invalid_argument(
                "flood-fill initialization is already pending confirmation",
            ));
        }
        let dispatch = TerrainFloodFillDispatch::prepare(occupancy, flood_fill, frame_counter)?;
        let frame_mapping = VoxelLightVolumeFrameMapping::from_descriptor(&occupancy.descriptor);
        // `shadowcomp` writes the copy field on even frames and the primary
        // field on odd frames. Seed the same output field so its first
        // terrain sample observes the source-defined parity.
        let target_field = flood_fill_output_field_for_frame(frame_counter);
        let (target_texture, init_set) = match target_field {
            VoxelLightVolumeKind::FloodFillEven => (flood_fill.even_texture, self.even_init_set),
            VoxelLightVolumeKind::FloodFillOdd => (flood_fill.odd_texture, self.odd_init_set),
            VoxelLightVolumeKind::Occupancy => {
                return Err(GalError::invalid_argument(
                    "flood-fill initialization cannot target occupancy",
                ));
            }
        };
        let target_initialized = self.field_initialized(target_field);
        operations.push(CommandOp::Barrier(resource_barrier(
            target_texture,
            None,
            if target_initialized {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            },
            TextureUsageState::ShaderWrite,
        )));
        dispatch.append(
            self.init_pipeline,
            self.init_pipeline_layout,
            init_set,
            operations,
        );
        operations.push(CommandOp::Barrier(resource_barrier(
            target_texture,
            None,
            TextureUsageState::ShaderWrite,
            TextureUsageState::ShaderRead,
        )));
        self.pending_initialization = Some(frame_counter);
        self.pending_frame_mapping = Some(frame_mapping);
        Ok(())
    }

    pub fn confirm_initialization(&mut self) -> GalResult<()> {
        let frame_counter = self.pending_initialization.take().ok_or_else(|| {
            GalError::invalid_argument("no flood-fill initialization is pending confirmation")
        })?;
        self.mark_field_initialized(flood_fill_output_field_for_frame(frame_counter))?;
        self.confirmed_frame_mapping = self.pending_frame_mapping.take();
        Ok(())
    }

    fn has_seed(&self) -> bool {
        self.even_initialized || self.odd_initialized
    }

    fn is_initialized_for_frame(&self, frame_counter: u64) -> bool {
        self.field_initialized(flood_fill_output_field_for_frame(frame_counter))
    }

    fn mapping_ready_for(&self, descriptor: &VoxelLightVolumeDescriptor) -> bool {
        self.pending_frame_mapping.is_none()
            && self
                .confirmed_frame_mapping
                .as_ref()
                .is_some_and(|mapping| mapping.matches_descriptor(descriptor))
    }

    fn has_pending_submission(&self) -> bool {
        self.pending_initialization.is_some() || self.pending_propagation.is_some()
    }

    fn confirm_pending_submission(&mut self) -> GalResult<()> {
        if self.pending_initialization.is_some() {
            self.confirm_initialization()
        } else if self.pending_propagation.is_some() {
            self.confirm_propagation()
        } else {
            Ok(())
        }
    }

    fn discard_pending_submission(&mut self) {
        self.discard_initialization();
        self.discard_propagation();
    }

    fn invalidate(&mut self) {
        self.discard_pending_submission();
        self.even_initialized = false;
        self.odd_initialized = false;
    }

    pub fn discard_initialization(&mut self) {
        self.pending_initialization = None;
        self.pending_frame_mapping = None;
    }

    pub fn append_propagation(
        &mut self,
        occupancy: &TerrainOccupancyGpuResources,
        flood_fill: &TerrainFloodFillGpuResources,
        frame_counter: u64,
        view_direction: Option<VoxelLightVolumeViewDirection>,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<()> {
        if self.pending_initialization.is_some()
            || self.pending_propagation.is_some()
            || self.pending_frame_mapping.is_some()
        {
            return Err(GalError::invalid_argument(
                "flood-fill propagation requires a confirmed seed and no pending compute submission",
            ));
        }
        let dispatch = TerrainFloodFillDispatch::prepare(occupancy, flood_fill, frame_counter)?;
        let current_frame_mapping =
            VoxelLightVolumeFrameMapping::from_descriptor(&occupancy.descriptor);
        let previous_frame_mapping = self.confirmed_frame_mapping.as_ref().ok_or_else(|| {
            GalError::invalid_argument(
                "flood-fill propagation requires a confirmed previous frame mapping",
            )
        })?;
        let temporal_mapping = VoxelLightVolumeTemporalMapping::from_frame_mappings(
            &current_frame_mapping,
            previous_frame_mapping,
            occupancy.descriptor.extent,
            occupancy.descriptor.requirements.update_policy,
            frame_counter,
            view_direction,
        )?;
        let source_field = flood_fill_source_field_for_frame(frame_counter);
        let target_field = flood_fill_output_field_for_frame(frame_counter);
        let (target_texture, set) = match (source_field, target_field) {
            (VoxelLightVolumeKind::FloodFillEven, VoxelLightVolumeKind::FloodFillOdd) => {
                (flood_fill.odd_texture, self.even_to_odd_set)
            }
            (VoxelLightVolumeKind::FloodFillOdd, VoxelLightVolumeKind::FloodFillEven) => {
                (flood_fill.even_texture, self.odd_to_even_set)
            }
            _ => {
                return Err(GalError::invalid_argument(
                    "flood-fill source/output parity contract is invalid",
                ));
            }
        };
        let source_initialized = self.field_initialized(source_field);
        let target_initialized = self.field_initialized(target_field);
        if !source_initialized {
            return Err(GalError::invalid_argument(
                "flood-fill propagation source parity is not initialized",
            ));
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.temporal_mapping_buffer,
            offset: 0,
            data: temporal_mapping.std140_bytes().to_vec(),
        });
        operations.push(CommandOp::Barrier(resource_barrier(
            self.temporal_mapping_buffer,
            None,
            TextureUsageState::TransferDst,
            TextureUsageState::ShaderRead,
        )));
        // The source was made shader-readable by its preceding seed or
        // propagation submission. A read-to-read barrier is intentionally
        // omitted because GAL requires semantic state transitions only.
        operations.push(CommandOp::Barrier(resource_barrier(
            target_texture,
            None,
            if target_initialized {
                TextureUsageState::ShaderRead
            } else {
                TextureUsageState::Undefined
            },
            TextureUsageState::ShaderWrite,
        )));
        dispatch.append(
            self.propagate_pipeline,
            self.propagate_pipeline_layout,
            set,
            operations,
        );
        operations.push(CommandOp::Barrier(resource_barrier(
            target_texture,
            None,
            TextureUsageState::ShaderWrite,
            TextureUsageState::ShaderRead,
        )));
        self.pending_propagation = Some(frame_counter);
        self.pending_frame_mapping = Some(current_frame_mapping);
        Ok(())
    }

    pub fn confirm_propagation(&mut self) -> GalResult<()> {
        let frame_counter = self.pending_propagation.take().ok_or_else(|| {
            GalError::invalid_argument("no flood-fill propagation is pending confirmation")
        })?;
        self.mark_field_initialized(flood_fill_output_field_for_frame(frame_counter))?;
        self.confirmed_frame_mapping = self.pending_frame_mapping.take();
        Ok(())
    }

    fn field_initialized(&self, field: VoxelLightVolumeKind) -> bool {
        match field {
            VoxelLightVolumeKind::FloodFillEven => self.even_initialized,
            VoxelLightVolumeKind::FloodFillOdd => self.odd_initialized,
            VoxelLightVolumeKind::Occupancy => false,
        }
    }

    fn mark_field_initialized(&mut self, field: VoxelLightVolumeKind) -> GalResult<()> {
        match field {
            VoxelLightVolumeKind::FloodFillEven => self.even_initialized = true,
            VoxelLightVolumeKind::FloodFillOdd => self.odd_initialized = true,
            VoxelLightVolumeKind::Occupancy => {
                return Err(GalError::invalid_argument(
                    "occupancy is not a flood-fill parity field",
                ));
            }
        }
        Ok(())
    }

    pub fn discard_propagation(&mut self) {
        self.pending_propagation = None;
        self.pending_frame_mapping = None;
    }

    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        gal.destroy(self.odd_to_even_set)?;
        gal.destroy(self.even_to_odd_set)?;
        gal.destroy(self.propagate_pipeline)?;
        gal.destroy(self.propagate_shader)?;
        gal.destroy(self.propagate_pipeline_layout)?;
        gal.destroy(self.propagate_layout)?;
        gal.destroy(self.temporal_mapping_buffer)?;
        gal.destroy(self.odd_init_set)?;
        gal.destroy(self.even_init_set)?;
        gal.destroy(self.init_pipeline)?;
        gal.destroy(self.init_shader)?;
        gal.destroy(self.init_pipeline_layout)?;
        gal.destroy(self.init_layout)
    }
}

fn compute_binding(binding: u32, kind: ResourceBindingKind) -> ResourceBindingDesc {
    ResourceBindingDesc {
        binding,
        kind,
        stages: PipelineStageFlags::COMPUTE,
        array_count: 1,
        optional: false,
        dynamic_offset_count: 0,
    }
}

fn compute_resource(
    binding: u32,
    resource: Handle,
    kind: ResourceBindingKind,
    access: AccessFlags,
) -> ResourceBinding {
    ResourceBinding {
        binding,
        array_index: 0,
        resource,
        kind,
        access,
        dynamic_offsets: Vec::new(),
        buffer_range: None,
    }
}

fn propagation_bindings(
    occupancy: Handle,
    source: Handle,
    target: Handle,
    emission: Handle,
    tint: Handle,
    temporal_mapping: Handle,
) -> Vec<ResourceBinding> {
    vec![
        compute_resource(
            0,
            occupancy,
            ResourceBindingKind::StorageTexture,
            AccessFlags::READ,
        ),
        compute_resource(
            1,
            source,
            ResourceBindingKind::StorageTexture,
            AccessFlags::READ,
        ),
        compute_resource(
            2,
            target,
            ResourceBindingKind::StorageTexture,
            AccessFlags::WRITE,
        ),
        compute_resource(
            3,
            emission,
            ResourceBindingKind::UniformBuffer,
            AccessFlags::READ,
        ),
        compute_resource(
            4,
            tint,
            ResourceBindingKind::UniformBuffer,
            AccessFlags::READ,
        ),
        compute_resource(
            5,
            temporal_mapping,
            ResourceBindingKind::UniformBuffer,
            AccessFlags::READ,
        ),
    ]
}

fn destroy_init_compute(
    gal: &mut VulkanicGal,
    even_set: Handle,
    odd_set: Option<Handle>,
    pipeline: Handle,
    shader: Handle,
    pipeline_layout: Handle,
    layout: Handle,
) {
    if let Some(odd_set) = odd_set {
        let _ = gal.destroy(odd_set);
    }
    let _ = gal.destroy(even_set);
    let _ = gal.destroy(pipeline);
    let _ = gal.destroy(shader);
    let _ = gal.destroy(pipeline_layout);
    let _ = gal.destroy(layout);
}

fn destroy_propagate_compute(
    gal: &mut VulkanicGal,
    pipeline: Handle,
    shader: Handle,
    pipeline_layout: Handle,
    layout: Handle,
) {
    let _ = gal.destroy(pipeline);
    let _ = gal.destroy(shader);
    let _ = gal.destroy(pipeline_layout);
    let _ = gal.destroy(layout);
}

// These kernels encode source-derived initialization, six-neighbor propagation,
// temporal reprojection, and Complementary's alternating X half-rate schedule.
// These semantics remain private until the broader selected-source terrain
// route is explicitly admitted.
const FLOOD_FILL_INIT_SHADER: &str = r#"#version 450
layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
layout(set = 0, binding = 0, r8ui) uniform readonly uimage3D Occupancy;
layout(set = 0, binding = 1, rgba16f) uniform writeonly image3D TargetLight;
layout(set = 0, binding = 2, std140) uniform Uniforms2 {
    vec4 emission[256];
};
void main() {
    ivec3 position = ivec3(gl_GlobalInvocationID);
    if (any(greaterThanEqual(position, imageSize(Occupancy)))) return;
    uint voxel = imageLoad(Occupancy, position).r;
    vec4 light = vec4(0.0);
    if (voxel > 1u && voxel < 200u) {
        vec4 color = emission[voxel];
        light = vec4(color.rgb * color.rgb, color.a);
    }
    imageStore(TargetLight, position, light);
}
"#;

const FLOOD_FILL_PROPAGATE_SHADER: &str = r#"#version 450
layout(local_size_x = 8, local_size_y = 8, local_size_z = 8) in;
layout(set = 0, binding = 0, r8ui) uniform readonly uimage3D Occupancy;
layout(set = 0, binding = 1, rgba16f) uniform readonly image3D SourceLight;
layout(set = 0, binding = 2, rgba16f) uniform writeonly image3D TargetLight;
layout(set = 0, binding = 3, std140) uniform Uniforms3 {
    vec4 emission[256];
};
layout(set = 0, binding = 4, std140) uniform Uniforms4 {
    vec4 tint[20];
};
layout(set = 0, binding = 5, std140) uniform Uniforms5 {
    ivec4 PreviousCameraMinusCurrent;
    ivec4 VolumeExtent;
    ivec4 UpdateSchedule;
    vec4 CameraForward;
};
vec4 sampleReprojectedLight(ivec3 position, ivec3 extent) {
    // Complementary's un-clamped texelFetch of previousPos is meaningful only
    // inside the prior camera-relative field. Make its edge behavior explicit
    // and backend-neutral rather than duplicating the nearest border texel.
    if (any(lessThan(position, ivec3(0))) || any(greaterThanEqual(position, extent))) {
        return vec4(0.0);
    }
    return imageLoad(SourceLight, position);
}
vec4 sampleNeighborLight(ivec3 position, ivec3 extent) {
    // `GetLightAverage` clamps just its six neighbor probes.
    return imageLoad(SourceLight, clamp(position, ivec3(0), extent - 1));
}
void main() {
    ivec3 position = ivec3(gl_GlobalInvocationID);
    ivec3 extent = VolumeExtent.xyz;
    if (any(greaterThanEqual(position, extent))) return;
    ivec3 previousPosition = position - PreviousCameraMinusCurrent.xyz;
    bool preserveBehindView = UpdateSchedule.z != 0 &&
        (abs(position - extent / 2).x + abs(position - extent / 2).y + abs(position - extent / 2).z > 16) &&
        dot(normalize(vec3(position) / vec3(extent) - 0.5), normalize(CameraForward.xyz)) < 0.0;
    if (preserveBehindView) {
        imageStore(TargetLight, position, sampleReprojectedLight(previousPosition, extent));
        return;
    }
    bool halfRatePreserved = UpdateSchedule.x != 0 &&
        ((UpdateSchedule.y == 0 && position.x * 2 < extent.x) ||
         (UpdateSchedule.y != 0 && position.x * 2 > extent.x));
    if (halfRatePreserved) {
        imageStore(TargetLight, position, sampleReprojectedLight(previousPosition, extent));
        return;
    }
    vec4 light = sampleReprojectedLight(previousPosition, extent);
    light += sampleNeighborLight(previousPosition + ivec3(1, 0, 0), extent);
    light += sampleNeighborLight(previousPosition + ivec3(-1, 0, 0), extent);
    light += sampleNeighborLight(previousPosition + ivec3(0, 1, 0), extent);
    light += sampleNeighborLight(previousPosition + ivec3(0, -1, 0), extent);
    light += sampleNeighborLight(previousPosition + ivec3(0, 0, 1), extent);
    light += sampleNeighborLight(previousPosition + ivec3(0, 0, -1), extent);
    light /= 7.2;
    uint voxel = imageLoad(Occupancy, position).r;
    if (voxel == 1u) {
        light = vec4(0.0);
    } else if (voxel >= 200u) {
        light.rgb *= tint[min(voxel - 200u, 19u)].rgb;
    } else if (voxel > 1u && voxel < 200u) {
        vec4 color = emission[voxel];
        light = max(light, vec4(color.rgb * color.rgb, color.a));
    }
    imageStore(TargetLight, position, light);
}
"#;

impl TerrainFloodFillGpuResources {
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
        let create_texture = |gal: &mut VulkanicGal, label: &str| {
            gal.create_texture(TextureDesc {
                label: format!("{}.flood-fill.{label}", descriptor.identity.as_str()),
                dimension: TextureDimension::D3,
                format: TextureFormat::Rgba16Float,
                extent,
                mip_levels: 1,
                array_layers: 1,
                usages: vec![TextureUsage::Sampled, TextureUsage::Storage],
            })
        };
        let even_texture = create_texture(gal, "even")?;
        let even_view = match create_volume_view(gal, descriptor, "even", even_texture) {
            Ok(view) => view,
            Err(error) => {
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let odd_texture = match create_texture(gal, "odd") {
            Ok(texture) => texture,
            Err(error) => {
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let odd_view = match create_volume_view(gal, descriptor, "odd", odd_texture) {
            Ok(view) => view,
            Err(error) => {
                let _ = gal.destroy(odd_texture);
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let sampler = match gal.create_sampler(SamplerDesc {
            label: format!("{}.flood-fill.sampler", descriptor.identity.as_str()),
            // The existing private volume contract samples discrete voxel
            // cells with one nearest sampler. Sampling-resource creation now
            // pairs that sampler with each view explicitly without changing
            // the established filter semantics.
            min_filter: SamplerFilter::Nearest,
            mag_filter: SamplerFilter::Nearest,
            mip_filter: SamplerFilter::Nearest,
            address_u: SamplerAddressMode::ClampToEdge,
            address_v: SamplerAddressMode::ClampToEdge,
            address_w: SamplerAddressMode::ClampToEdge,
        }) {
            Ok(sampler) => sampler,
            Err(error) => {
                let _ = gal.destroy(odd_view);
                let _ = gal.destroy(odd_texture);
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let emission_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{}.flood-fill.emission-table", descriptor.identity.as_str()),
            size: 256 * 16,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::Uniform,
                BufferUsage::HostWrite,
                BufferUsage::TransferDst,
            ],
        }) {
            Ok(buffer) => buffer,
            Err(error) => {
                let _ = gal.destroy(sampler);
                let _ = gal.destroy(odd_view);
                let _ = gal.destroy(odd_texture);
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        let tint_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{}.flood-fill.tint-table", descriptor.identity.as_str()),
            size: (VOXEL_TINT_COUNT * 16) as u64,
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::Uniform,
                BufferUsage::HostWrite,
                BufferUsage::TransferDst,
            ],
        }) {
            Ok(buffer) => buffer,
            Err(error) => {
                let _ = gal.destroy(emission_buffer);
                let _ = gal.destroy(sampler);
                let _ = gal.destroy(odd_view);
                let _ = gal.destroy(odd_texture);
                let _ = gal.destroy(even_view);
                let _ = gal.destroy(even_texture);
                return Err(error);
            }
        };
        Ok(Self {
            descriptor: descriptor.clone(),
            even_texture,
            even_view,
            odd_texture,
            odd_view,
            sampler,
            emission_buffer,
            tint_buffer,
            emission_generation: None,
            emission_upload_pending: false,
            tint_generation: None,
            tint_upload_pending: false,
        })
    }

    pub fn descriptor(&self) -> &VoxelLightVolumeDescriptor {
        &self.descriptor
    }

    fn update_mapping(&mut self, descriptor: &VoxelLightVolumeDescriptor) -> GalResult<()> {
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
                "flood-fill mapping update would reuse an incompatible D3 resource",
            ));
        }
        self.descriptor = descriptor.clone();
        Ok(())
    }

    /// A compute pass may bind the table only after the exact source-derived
    /// bytes reached the owned buffer.
    pub fn emission_ready(&self) -> bool {
        self.emission_generation == Some(self.descriptor.shader_pack_generation)
            && !self.emission_upload_pending
    }

    pub fn tint_ready(&self) -> bool {
        self.tint_generation == Some(self.descriptor.shader_pack_generation)
            && !self.tint_upload_pending
    }

    fn has_pending_upload(&self) -> bool {
        self.emission_upload_pending || self.tint_upload_pending
    }

    fn confirm_pending_uploads(&mut self) -> GalResult<()> {
        if self.emission_upload_pending {
            self.confirm_emission_submission()?;
        }
        if self.tint_upload_pending {
            self.confirm_tint_submission()?;
        }
        Ok(())
    }

    fn discard_pending_uploads(&mut self) {
        self.discard_emission_submission();
        self.discard_tint_submission();
    }

    /// Returns the field read by the source-derived compute update for this
    /// frame. Native views remain private to the backend resource layer.
    pub fn propagation_source_view(&self, frame_counter: u64) -> Handle {
        if frame_counter & 1 == 0 {
            self.even_view
        } else {
            self.odd_view
        }
    }

    /// Returns the field written and then sampled by terrain for this frame.
    pub fn terrain_sample_view(&self, frame_counter: u64) -> Handle {
        if frame_counter & 1 == 0 {
            self.odd_view
        } else {
            self.even_view
        }
    }

    /// Records the selected source's semantic emission table exactly once per
    /// shader-pack generation. The table stays private until an explicit
    /// compute pass binds it through a normal GAL resource set.
    pub fn append_emission_upload(
        &mut self,
        table: &VoxelEmissionTable,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        if table.shader_pack_generation() != self.descriptor.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "voxel emission table does not match flood-fill shader-pack generation",
            ));
        }
        if self.emission_upload_pending {
            return Err(GalError::invalid_argument(
                "voxel emission upload is already awaiting submission confirmation",
            ));
        }
        if self.emission_generation == Some(table.shader_pack_generation()) {
            return Ok(false);
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.emission_buffer,
            offset: 0,
            data: table.std140_bytes(),
        });
        self.emission_upload_pending = true;
        Ok(true)
    }

    pub fn confirm_emission_submission(&mut self) -> GalResult<()> {
        if !self.emission_upload_pending {
            return Err(GalError::invalid_argument(
                "no voxel emission upload is pending confirmation",
            ));
        }
        self.emission_generation = Some(self.descriptor.shader_pack_generation);
        self.emission_upload_pending = false;
        Ok(())
    }

    pub fn discard_emission_submission(&mut self) {
        self.emission_upload_pending = false;
    }

    /// Records the selected source's `specialTintColor` table once per
    /// shader-pack generation. Propagation never substitutes an identity
    /// tint when this source-derived table is missing.
    pub fn append_tint_upload(
        &mut self,
        table: &VoxelEmissionTable,
        operations: &mut Vec<CommandOp>,
    ) -> GalResult<bool> {
        if table.shader_pack_generation() != self.descriptor.shader_pack_generation {
            return Err(GalError::invalid_argument(
                "voxel tint table does not match flood-fill shader-pack generation",
            ));
        }
        if self.tint_upload_pending {
            return Err(GalError::invalid_argument(
                "voxel tint upload is already awaiting submission confirmation",
            ));
        }
        if self.tint_generation == Some(table.shader_pack_generation()) {
            return Ok(false);
        }
        operations.push(CommandOp::HostWriteBuffer {
            buffer: self.tint_buffer,
            offset: 0,
            data: table.tint_std140_bytes(),
        });
        self.tint_upload_pending = true;
        Ok(true)
    }

    pub fn confirm_tint_submission(&mut self) -> GalResult<()> {
        if !self.tint_upload_pending {
            return Err(GalError::invalid_argument(
                "no voxel tint upload is pending confirmation",
            ));
        }
        self.tint_generation = Some(self.descriptor.shader_pack_generation);
        self.tint_upload_pending = false;
        Ok(())
    }

    pub fn discard_tint_submission(&mut self) {
        self.tint_upload_pending = false;
    }

    pub fn destroy(self, gal: &mut VulkanicGal) -> GalResult<()> {
        gal.destroy(self.tint_buffer)?;
        gal.destroy(self.emission_buffer)?;
        gal.destroy(self.sampler)?;
        gal.destroy(self.odd_view)?;
        gal.destroy(self.odd_texture)?;
        gal.destroy(self.even_view)?;
        gal.destroy(self.even_texture)
    }
}

fn create_volume_view(
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
                let _ = gal.destroy(texture);
                return Err(error);
            }
        };
        let upload_buffer = match gal.create_buffer(BufferDesc {
            label: format!("{}.occupancy.upload", descriptor.identity.as_str()),
            size: descriptor
                .extent
                .byte_len(super::voxel_light_volume::VoxelLightVolumeFormat::OccupancyR8Uint),
            memory: MemoryDomain::Upload,
            usages: vec![
                BufferUsage::HostWrite,
                BufferUsage::TransferSrc,
                BufferUsage::TransferDst,
            ],
        }) {
            Ok(buffer) => buffer,
            Err(error) => {
                let _ = gal.destroy(view);
                let _ = gal.destroy(texture);
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

    fn validate_mapping(&self, descriptor: &VoxelLightVolumeDescriptor) -> GalResult<()> {
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

    fn update_mapping(&mut self, descriptor: &VoxelLightVolumeDescriptor) -> GalResult<()> {
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
                TextureUsageState::ShaderRead
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
            TextureUsageState::ShaderRead,
        )));
        self.upload_pending = true;
        Ok(())
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
                .byte_len(super::voxel_light_volume::VoxelLightVolumeFormat::OccupancyR8Uint),
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
        })
    }

    pub fn descriptor(&self) -> &VoxelLightVolumeDescriptor {
        &self.descriptor
    }

    pub fn cache(&self) -> &VoxelLightVolumeCache {
        &self.cache
    }

    pub fn is_initialized(&self) -> bool {
        self.initialized && self.pending_upload.is_none()
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
                .byte_len(super::voxel_light_volume::VoxelLightVolumeFormat::OccupancyR8Uint),
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
        Ok(())
    }

    /// Updates the camera-relative semantic mapping. Fractional movement
    /// leaves occupied cells intact; crossing an integer camera cell makes the
    /// field incomplete until a full source snapshot re-voxelizes it.
    pub fn update_mapping(&mut self, mapping: VoxelLightVolumeMapping) -> GalResult<bool> {
        if self.pending_upload.is_some() {
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
                return Err(GalError::invalid_argument(format!(
                    "conflicting terrain occupancy values {existing} and {value} at voxel {:?}",
                    voxel
                )));
            }
            next[index] = value;
            stats.emitted_samples = stats.emitted_samples.saturating_add(1);
        }

        self.stage_update(next, stats)
    }

    fn stage_update(
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
        let Some(region) = changed else {
            return Ok(stats);
        };
        let update_region = if self.initialized {
            region
        } else {
            VoxelLightVolumeRegion::whole(self.descriptor.extent)
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
                .byte_len(super::voxel_light_volume::VoxelLightVolumeFormat::OccupancyR8Uint),
        )
        .unwrap_or(u32::MAX);
        stats.updated_region = Some(update_region);
        self.pending_upload = Some(update);
        self.pending_occupancy = Some(next);
        Ok(stats)
    }

    fn world_to_voxel(&self, world: [f32; 3]) -> Option<[u32; 3]> {
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

fn mid_block_component(packed: u32, shift: u32) -> f32 {
    ((packed >> shift) as u8 as i8) as f32 / 64.0
}

fn resource_barrier(
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

fn transform_point(matrix: [f32; 16], point: [f32; 3]) -> GalResult<[f32; 3]> {
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

fn voxel_index(width: u32, height: u32, voxel: [u32; 3]) -> GalResult<usize> {
    let row = u64::from(voxel[2])
        .checked_mul(u64::from(height))
        .and_then(|value| value.checked_add(u64::from(voxel[1])))
        .and_then(|value| value.checked_mul(u64::from(width)))
        .and_then(|value| value.checked_add(u64::from(voxel[0])))
        .ok_or_else(|| GalError::invalid_argument("terrain voxel index overflows"))?;
    usize::try_from(row)
        .map_err(|_| GalError::invalid_argument("terrain voxel index exceeds address space"))
}

fn changed_bounds(
    old: &[u8],
    next: &[u8],
    width: u32,
    height: u32,
    depth: u32,
) -> Option<VoxelLightVolumeRegion> {
    let mut min = [u32::MAX; 3];
    let mut max = [0; 3];
    let mut changed = false;
    for z in 0..depth {
        for y in 0..height {
            for x in 0..width {
                let index = voxel_index(width, height, [x, y, z]).ok()?;
                if old[index] != next[index] {
                    changed = true;
                    min = [min[0].min(x), min[1].min(y), min[2].min(z)];
                    max = [max[0].max(x), max[1].max(y), max[2].max(z)];
                }
            }
        }
    }
    if !changed {
        return None;
    }
    Some(VoxelLightVolumeRegion {
        x: min[0],
        y: min[1],
        z: min[2],
        extent: super::voxel_light_volume::VoxelLightVolumeExtent {
            width: max[0] - min[0] + 1,
            height: max[1] - min[1] + 1,
            depth: max[2] - min[2] + 1,
        },
    })
}

fn copy_region_bytes(
    source: &[u8],
    width: u32,
    height: u32,
    region: VoxelLightVolumeRegion,
) -> Vec<u8> {
    let mut result = Vec::with_capacity(region.extent.texel_count() as usize);
    for z in region.z..region.z + region.extent.depth {
        for y in region.y..region.y + region.extent.height {
            let first =
                voxel_index(width, height, [region.x, y, z]).expect("validated voxel region");
            result.extend_from_slice(&source[first..first + region.extent.width as usize]);
        }
    }
    result
}

fn count_changed(old: &[u8], next: &[u8]) -> u32 {
    u32::try_from(
        old.iter()
            .zip(next)
            .filter(|(left, right)| left != right)
            .count(),
    )
    .unwrap_or(u32::MAX)
}

#[cfg(test)]
mod tests {
    use super::super::voxel_light_volume::{
        VoxelLightVolumeExtent, VoxelLightVolumeIdentity, VoxelLightVolumeMapping,
        VoxelLightVolumeRequirements,
    };
    use super::*;
    use crate::render::vulkanic::backends::mock::MockBackend;
    use crate::render::vulkanic::backends::opengl::OpenGlBackend;
    use crate::render::vulkanic::backends::vulkan::VulkanBackend;
    use crate::render::vulkanic::commands::{CommandListDesc, SubmissionBatch};
    use crate::render::vulkanic::resources::IndexType;
    use crate::render::vulkanic::shader_pack::source::{ShaderPackSource, ShaderSourceFile};
    use crate::render::vulkanic::shader_pack::terrain_contract::{
        bundled_complementary_hung_loified_source, derive_complementary_terrain_contract,
    };
    use crate::render::vulkanic::shader_pack::terrain_contract::{
        TerrainPassContract, TerrainPassInput, TerrainPassOperation, TerrainPassOutput,
    };
    use crate::render::vulkanic::world_primitive_frontend::TerrainVoxelSourceVertex;

    fn descriptor() -> VoxelLightVolumeDescriptor {
        let extent = VoxelLightVolumeExtent {
            width: 8,
            height: 4,
            depth: 8,
        };
        VoxelLightVolumeDescriptor {
            identity: VoxelLightVolumeIdentity::new("shader-pack:test/occupancy").unwrap(),
            shader_pack_generation: 1,
            world_generation: 2,
            resource_generation: 3,
            extent,
            requirements: VoxelLightVolumeRequirements {
                extent,
                occupancy_format:
                    super::super::voxel_light_volume::VoxelLightVolumeFormat::OccupancyR8Uint,
                lighting_format:
                    super::super::voxel_light_volume::VoxelLightVolumeFormat::LightingRgba16Float,
                ping_pong_by_frame_parity: true,
                linear_filtered_lighting: true,
                update_policy:
                    super::super::voxel_light_volume::VoxelLightVolumeUpdatePolicy::full_rate(),
            },
            mapping: VoxelLightVolumeMapping::complementary(extent, [0, 0, 0], [0.0; 3]).unwrap(),
        }
    }

    fn sample(position: [f32; 3], material: i32) -> TerrainVoxelSample {
        TerrainVoxelSample {
            vertex_position: position,
            mid_block_packed: 0,
            shader_material_id: material,
            model_transform: [
                1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
            ],
        }
    }

    fn materials() -> VoxelMaterialMap {
        let source = ShaderPackSource::new(
            "test",
            1,
            vec![ShaderSourceFile::new(
                "lib/misc/voxelization.glsl",
                "int GetVoxelIDs(int mat) { if (mat == 30008) return 2; if (mat == 30012) return 3; return 1; }\nvoid UpdateVoxelMap(int mat) { if (mat == 32000 || mat < 30000 && mat % 4 == 1) return; }",
            )],
        )
        .unwrap();
        let contract = TerrainPassContract {
            pack_name: "test".to_string(),
            generation: 1,
            program_path: "unused".to_string(),
            material_classes: Default::default(),
            inputs: std::collections::BTreeSet::from([TerrainPassInput::ColoredVoxelLightVolume]),
            outputs: std::collections::BTreeSet::from([TerrainPassOutput::LitTerrainColor]),
            property_defines: Default::default(),
            material_ids: Default::default(),
            operations: vec![TerrainPassOperation::ColoredVoxelLighting],
            required_resources: Default::default(),
            voxel_light_volume_requirements: None,
            unsupported: Default::default(),
        };
        VoxelMaterialMap::derive(&source, &contract).unwrap()
    }

    fn new_voxelizer(descriptor: VoxelLightVolumeDescriptor) -> TerrainOccupancyVoxelizer {
        TerrainOccupancyVoxelizer::new(descriptor, materials()).unwrap()
    }

    fn static_terrain_mesh_with_identity(
        mesh_key: u64,
        mesh_generation: u64,
        position: [f32; 3],
        layout_version: u32,
    ) -> WorldMeshAsset {
        WorldMeshAsset {
            mesh_key,
            mesh_generation,
            vertex_layout_version: layout_version,
            index_type: IndexType::U16,
            vertices: vec![
                WorldMeshVertex {
                    position,
                    uv: [0.0, 0.0],
                    shader_atlas_uv: [0.0, 0.0],
                    shader_block_id: 30_008,
                    shader_material_type: 0,
                    mid_block_packed: 0,
                    color_argb: u32::MAX,
                    normal_packed: 0,
                    light: 0,
                };
                3
            ],
            index_bytes: vec![0, 0, 1, 0, 2, 0],
            sections: Vec::new(),
        }
    }

    fn static_terrain_mesh(position: [f32; 3], layout_version: u32) -> WorldMeshAsset {
        static_terrain_mesh_with_identity(0x76_6f_78_65_6c, 1, position, layout_version)
    }

    fn identity_transform() -> [f32; 16] {
        [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ]
    }

    fn submit_runtime_update(
        gal: &mut VulkanicGal,
        runtime: &mut TerrainOccupancyRuntime,
        label: &str,
        operations: Vec<CommandOp>,
    ) {
        let list = gal
            .create_command_list(CommandListDesc {
                label: format!("{label}.list"),
                operations,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: label.to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        runtime.confirm_submission().unwrap();
    }

    #[test]
    fn initial_generation_uses_source_midpoint_and_subsequent_update_is_bounded() {
        let mut voxelizer = new_voxelizer(descriptor());
        let first = voxelizer
            .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
            .unwrap();
        assert_eq!(
            Some(VoxelLightVolumeRegion::whole(descriptor().extent)),
            first.updated_region
        );
        assert_eq!(1, first.emitted_samples);
        assert!(voxelizer.cache().binding_for_frame(0).is_err());
        voxelizer.confirm_pending_upload().unwrap();
        let second = voxelizer
            .update_from_samples([sample([1.0, 0.0, 0.0], 30_008)])
            .unwrap();
        assert_eq!(
            Some(VoxelLightVolumeRegion {
                x: 4,
                y: 2,
                z: 4,
                extent: VoxelLightVolumeExtent {
                    width: 2,
                    height: 1,
                    depth: 1
                }
            }),
            second.updated_region
        );
    }

    #[test]
    fn skips_non_solids_and_rejects_conflicting_voxel_materials() {
        let mut voxelizer = new_voxelizer(descriptor());
        let stats = voxelizer
            .update_from_samples([sample([0.0, 0.0, 0.0], 32_000)])
            .unwrap();
        assert_eq!(1, stats.skipped_non_solid_samples);
        assert!(stats.updated_region.is_none());
        assert!(voxelizer
            .update_from_samples([
                sample([0.0, 0.0, 0.0], 30_008),
                sample([0.0, 0.0, 0.0], 30_012)
            ])
            .is_err());
    }

    #[test]
    fn indexed_vertex_occupancy_does_not_invent_triangle_volume_coverage() {
        let descriptor = descriptor();
        let mut voxelizer = new_voxelizer(descriptor.clone());
        let snapshot = indexed_terrain_snapshot(
            1,
            vec![
                sample([0.0, 0.0, 0.0], 30_008),
                sample([3.0, 0.0, 0.0], 30_008),
                sample([0.0, 0.0, 3.0], 30_008),
                sample([3.0, 0.0, 3.0], 30_008),
            ],
            vec![0, 1, 2],
            None,
        )
        .unwrap();
        assert_eq!(3, snapshot.samples.len());
        let stats = voxelizer.update_from_samples(snapshot.samples).unwrap();
        assert_eq!(3, stats.changed_voxels);
        let interior =
            voxel_index(descriptor.extent.width, descriptor.extent.height, [5, 2, 5]).unwrap();
        let unreferenced =
            voxel_index(descriptor.extent.width, descriptor.extent.height, [7, 2, 7]).unwrap();
        assert_eq!(0, voxelizer.pending_occupancy.as_ref().unwrap()[interior]);
        assert_eq!(
            0,
            voxelizer.pending_occupancy.as_ref().unwrap()[unreferenced]
        );
    }

    #[test]
    fn d3_residency_records_bounded_copy_and_retires_owned_resources() {
        let descriptor = descriptor();
        let mut voxelizer = new_voxelizer(descriptor.clone());
        voxelizer
            .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
            .unwrap();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut resources = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
        let mut operations = Vec::new();
        resources
            .append_upload(voxelizer.pending_upload().unwrap(), &mut operations)
            .unwrap();
        assert!(!resources.is_initialized());
        assert!(operations.iter().any(|operation| matches!(
            operation,
            CommandOp::CopyBufferToTexture(region)
                if region.extent.depth == descriptor.extent.depth && region.texture_origin.z == 0
        )));
        let list = gal
            .create_command_list(CommandListDesc {
                label: "occupancy-upload".to_owned(),
                operations,
            })
            .unwrap();
        let token = gal
            .submit(SubmissionBatch {
                label: "occupancy-upload".to_owned(),
                command_lists: vec![list],
            })
            .unwrap();
        resources.confirm_submission().unwrap();
        assert!(resources.is_initialized());
        voxelizer.confirm_pending_upload().unwrap();
        let texture = resources.texture;
        resources.destroy(&mut gal).unwrap();
        let retired = gal.retire_through_for_test(token.submission).unwrap();
        assert!(retired.contains(&texture));
    }

    #[test]
    fn gpu_residency_rejects_mixed_generations_and_can_discard_a_rejected_submission() {
        let descriptor = descriptor();
        let mut voxelizer = new_voxelizer(descriptor.clone());
        voxelizer
            .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
            .unwrap();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut resources = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
        let mut operations = Vec::new();
        resources
            .append_upload(voxelizer.pending_upload().unwrap(), &mut operations)
            .unwrap();
        assert!(resources
            .append_upload(voxelizer.pending_upload().unwrap(), &mut operations)
            .is_err());
        resources.discard_pending_submission();
        voxelizer.discard_pending_upload();

        let mut stale = descriptor.clone();
        stale.resource_generation += 1;
        let mut stale_voxelizer = new_voxelizer(stale);
        stale_voxelizer
            .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
            .unwrap();
        assert!(resources
            .append_upload(stale_voxelizer.pending_upload().unwrap(), &mut Vec::new())
            .is_err());
    }

    #[test]
    fn static_terrain_occupancy_runtime_copies_meshes_and_rolls_back_rejected_uploads() {
        let descriptor = descriptor();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut runtime =
            TerrainOccupancyRuntime::create(&mut gal, descriptor, materials()).unwrap();
        let identity = [
            1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.0, 1.0,
        ];
        let first = static_terrain_mesh([0.0, 0.0, 0.0], WORLD_MESH_VERTEX_LAYOUT_V3);
        let mut first_ops = Vec::new();
        let first_stats = runtime
            .append_static_terrain_snapshot(
                [(&first, identity, WORLD_STRATUM_TERRAIN)],
                &mut first_ops,
            )
            .unwrap();
        assert_eq!(
            Some(VoxelLightVolumeRegion::whole(runtime.descriptor().extent)),
            first_stats.updated_region
        );
        assert!(first_ops.iter().any(|operation| matches!(
            operation,
            CommandOp::CopyBufferToTexture(region) if region.extent.depth == runtime.descriptor().extent.depth
        )));
        let first_list = gal
            .create_command_list(CommandListDesc {
                label: "occupancy-runtime-first".to_owned(),
                operations: first_ops,
            })
            .unwrap();
        let first_submission = gal
            .submit(SubmissionBatch {
                label: "occupancy-runtime-first".to_owned(),
                command_lists: vec![first_list],
            })
            .unwrap();
        runtime.confirm_submission().unwrap();
        assert!(runtime.is_initialized());

        let mut unchanged_ops = Vec::new();
        let unchanged = runtime
            .append_static_terrain_snapshot(
                [(&first, identity, WORLD_STRATUM_TERRAIN)],
                &mut unchanged_ops,
            )
            .unwrap();
        assert!(unchanged.updated_region.is_none());
        assert_eq!(0, unchanged.input_samples);
        assert_eq!(0, unchanged.emitted_samples);
        assert_eq!(0, unchanged.uploaded_bytes);
        assert!(unchanged_ops.is_empty());

        let mut changed = static_terrain_mesh([1.0, 0.0, 0.0], WORLD_MESH_VERTEX_LAYOUT_V3);
        changed.mesh_generation = 2;
        let mut rejected_ops = Vec::new();
        let changed_stats = runtime
            .append_static_terrain_snapshot(
                [(&changed, identity, WORLD_STRATUM_TERRAIN)],
                &mut rejected_ops,
            )
            .unwrap();
        assert_eq!(2, changed_stats.changed_voxels);
        assert!(changed_stats.updated_region.is_some());
        runtime.discard_submission();
        assert!(runtime.is_initialized());

        let mut replacement_ops = Vec::new();
        runtime
            .append_static_terrain_snapshot(
                [(&changed, identity, WORLD_STRATUM_TERRAIN)],
                &mut replacement_ops,
            )
            .unwrap();
        let replacement_list = gal
            .create_command_list(CommandListDesc {
                label: "occupancy-runtime-replacement".to_owned(),
                operations: replacement_ops,
            })
            .unwrap();
        let replacement_submission = gal
            .submit(SubmissionBatch {
                label: "occupancy-runtime-replacement".to_owned(),
                command_lists: vec![replacement_list],
            })
            .unwrap();
        runtime.confirm_submission().unwrap();

        let unsupported_layout = static_terrain_mesh([2.0, 0.0, 0.0], 2);
        assert!(runtime
            .append_static_terrain_snapshot(
                [(&unsupported_layout, identity, WORLD_STRATUM_TERRAIN)],
                &mut Vec::new(),
            )
            .is_err());

        let texture = runtime.resources.texture;
        runtime.destroy(&mut gal).unwrap();
        let retired = gal
            .retire_through_for_test(replacement_submission.submission)
            .unwrap();
        assert!(retired.contains(&texture));
        assert!(first_submission.submission < replacement_submission.submission);
    }

    #[test]
    fn occupancy_runtime_deltas_preserve_other_meshes_and_reject_stale_generations() {
        let descriptor = descriptor();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut runtime =
            TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
        let transform = identity_transform();
        let first = static_terrain_mesh_with_identity(
            0x10,
            1,
            [0.0, 0.0, 0.0],
            WORLD_MESH_VERTEX_LAYOUT_V3,
        );
        let second = static_terrain_mesh_with_identity(
            0x20,
            1,
            [2.0, 0.0, 0.0],
            WORLD_MESH_VERTEX_LAYOUT_V3,
        );
        let mut initial_operations = Vec::new();
        runtime
            .append_static_terrain_snapshot(
                [
                    (&first, transform, WORLD_STRATUM_TERRAIN),
                    (&second, transform, WORLD_STRATUM_TERRAIN),
                ],
                &mut initial_operations,
            )
            .unwrap();
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-initial",
            initial_operations,
        );
        assert_eq!(2, runtime.mesh_snapshot_count());
        let second_index =
            voxel_index(descriptor.extent.width, descriptor.extent.height, [6, 2, 4]).unwrap();
        assert_ne!(0, runtime.voxelizer.occupancy[second_index]);

        let first_replacement = static_terrain_mesh_with_identity(
            0x10,
            2,
            [1.0, 0.0, 0.0],
            WORLD_MESH_VERTEX_LAYOUT_V3,
        );
        let mut replacement_operations = Vec::new();
        let replacement = runtime
            .append_static_terrain_deltas(
                [TerrainOccupancyMeshDelta::Upsert {
                    mesh: &first_replacement,
                    model_transform: transform,
                    stratum: WORLD_STRATUM_TERRAIN,
                }],
                &mut replacement_operations,
            )
            .unwrap();
        assert_eq!(2, replacement.changed_voxels);
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-replace",
            replacement_operations,
        );
        assert_eq!(2, runtime.mesh_snapshot_count());
        assert_ne!(0, runtime.voxelizer.occupancy[second_index]);

        assert!(runtime
            .append_static_terrain_deltas(
                [TerrainOccupancyMeshDelta::Remove {
                    mesh_key: first_replacement.mesh_key,
                    mesh_generation: 1,
                }],
                &mut Vec::new(),
            )
            .is_err());
        assert!(runtime
            .append_static_terrain_snapshot(
                [
                    (&first, transform, WORLD_STRATUM_TERRAIN),
                    (&second, transform, WORLD_STRATUM_TERRAIN),
                ],
                &mut Vec::new(),
            )
            .is_err());

        let third = static_terrain_mesh_with_identity(
            0x30,
            1,
            [-2.0, 0.0, 0.0],
            WORLD_MESH_VERTEX_LAYOUT_V3,
        );
        let mut discarded_operations = Vec::new();
        runtime
            .append_static_terrain_deltas(
                [TerrainOccupancyMeshDelta::Upsert {
                    mesh: &third,
                    model_transform: transform,
                    stratum: WORLD_STRATUM_TERRAIN,
                }],
                &mut discarded_operations,
            )
            .unwrap();
        runtime.discard_submission();
        assert_eq!(2, runtime.mesh_snapshot_count());
        assert_ne!(0, runtime.voxelizer.occupancy[second_index]);

        let mut retry_operations = Vec::new();
        runtime
            .append_static_terrain_deltas(
                [TerrainOccupancyMeshDelta::Upsert {
                    mesh: &third,
                    model_transform: transform,
                    stratum: WORLD_STRATUM_TERRAIN,
                }],
                &mut retry_operations,
            )
            .unwrap();
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-retry",
            retry_operations,
        );
        assert_eq!(3, runtime.mesh_snapshot_count());

        let mut remove_operations = Vec::new();
        runtime
            .append_static_terrain_deltas(
                [TerrainOccupancyMeshDelta::Remove {
                    mesh_key: first_replacement.mesh_key,
                    mesh_generation: first_replacement.mesh_generation,
                }],
                &mut remove_operations,
            )
            .unwrap();
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-remove",
            remove_operations,
        );
        assert_eq!(2, runtime.mesh_snapshot_count());
        assert_ne!(0, runtime.voxelizer.occupancy[second_index]);
    }

    #[test]
    fn occupancy_runtime_replaces_generations_atomically_and_drops_old_mesh_state() {
        let descriptor = descriptor();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut runtime =
            TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
        let mesh = static_terrain_mesh([0.0, 0.0, 0.0], WORLD_MESH_VERTEX_LAYOUT_V3);
        let mut initial_operations = Vec::new();
        runtime
            .append_static_terrain_snapshot(
                [(&mesh, identity_transform(), WORLD_STRATUM_TERRAIN)],
                &mut initial_operations,
            )
            .unwrap();
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-generation-initial",
            initial_operations,
        );
        let old_texture = runtime.resources.texture;
        assert!(runtime.is_initialized());
        assert_eq!(1, runtime.mesh_snapshot_count());

        let mut incompatible = descriptor.clone();
        incompatible.shader_pack_generation += 1;
        assert!(runtime
            .replace_descriptor(&mut gal, incompatible, materials())
            .is_err());
        assert_eq!(old_texture, runtime.resources.texture);
        assert!(runtime.is_initialized());
        assert_eq!(1, runtime.mesh_snapshot_count());

        let mut replacement = descriptor;
        replacement.world_generation += 1;
        replacement.resource_generation += 1;
        runtime
            .replace_descriptor(&mut gal, replacement, materials())
            .unwrap();
        assert_ne!(old_texture, runtime.resources.texture);
        assert!(!runtime.is_initialized());
        assert_eq!(0, runtime.mesh_snapshot_count());
    }

    #[test]
    fn occupancy_runtime_accepts_compact_frontend_terrain_sources() {
        let descriptor = descriptor();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut runtime =
            TerrainOccupancyRuntime::create(&mut gal, descriptor, materials()).unwrap();
        let vertices = [TerrainVoxelSourceVertex {
            position: [0.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: 30_008,
        }];
        let source = TerrainVoxelSourceMesh {
            mesh_key: 0x88,
            mesh_generation: 1,
            vertices: Arc::new(vertices.to_vec()),
            indices: Arc::new(vec![0, 0, 0]),
            transform: identity_transform(),
        };
        let mut operations = Vec::new();
        let stats = runtime
            .append_terrain_source_snapshot([source.clone()], &mut operations)
            .unwrap();
        assert_eq!(1, stats.input_samples);
        assert_eq!(1, stats.emitted_samples);
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-frontend-source",
            operations,
        );
        assert!(runtime.is_initialized());
        assert_eq!(1, runtime.mesh_snapshot_count());

        let mut repeated_operations = Vec::new();
        let repeated = runtime
            .append_terrain_source_snapshot([source], &mut repeated_operations)
            .unwrap();
        assert_eq!(TerrainOccupancyUpdateStats::default(), repeated);
        assert!(repeated_operations.is_empty());
    }

    #[test]
    fn occupancy_runtime_revoxelizes_on_camera_cell_crossing_without_recreating_d3_resources() {
        let descriptor = descriptor();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut runtime =
            TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
        let source = TerrainVoxelSourceMesh {
            mesh_key: 0x8b,
            mesh_generation: 1,
            vertices: Arc::new(vec![TerrainVoxelSourceVertex {
                position: [0.0, 0.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 30_008,
            }]),
            indices: Arc::new(vec![0, 0, 0]),
            transform: identity_transform(),
        };
        let mut initial_ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(
                descriptor.mapping,
                [source.clone()],
                &mut initial_ops,
            )
            .unwrap();
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-mapping-initial",
            initial_ops,
        );
        let texture = runtime.resources.texture;

        let fractional =
            VoxelLightVolumeMapping::complementary(descriptor.extent, [0, 0, 0], [0.25, 0.0, 0.5])
                .unwrap();
        let mut fractional_ops = Vec::new();
        let fractional_stats = runtime
            .append_terrain_source_snapshot_for_mapping(
                fractional,
                [source.clone()],
                &mut fractional_ops,
            )
            .unwrap();
        assert!(fractional_stats.updated_region.is_none());
        assert_eq!(0, fractional_stats.input_samples);
        assert_eq!(0, fractional_stats.emitted_samples);
        assert_eq!(0, fractional_stats.uploaded_bytes);
        assert!(fractional_ops.is_empty());
        assert_eq!(texture, runtime.resources.texture);
        assert!(runtime.is_initialized());

        let moved =
            VoxelLightVolumeMapping::complementary(descriptor.extent, [1, 0, 0], [0.0, 0.0, 0.0])
                .unwrap();
        let mut moved_ops = Vec::new();
        let moved_stats = runtime
            .append_terrain_source_snapshot_for_mapping(moved, [source], &mut moved_ops)
            .unwrap();
        assert_eq!(
            Some(VoxelLightVolumeRegion::whole(descriptor.extent)),
            moved_stats.updated_region
        );
        assert!(!moved_ops.is_empty());
        assert_eq!(texture, runtime.resources.texture);
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-mapping-cell-crossing",
            moved_ops,
        );
        assert!(runtime.is_initialized());
        assert_eq!([1, 0, 0], runtime.descriptor().mapping.camera_cell);
    }

    #[test]
    fn rejected_mapping_submission_restores_the_previous_complete_volume() {
        let descriptor = descriptor();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut runtime =
            TerrainOccupancyRuntime::create(&mut gal, descriptor.clone(), materials()).unwrap();
        let source = TerrainVoxelSourceMesh {
            mesh_key: 0x8c,
            mesh_generation: 1,
            vertices: Arc::new(vec![TerrainVoxelSourceVertex {
                position: [0.0, 0.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 30_008,
            }]),
            indices: Arc::new(vec![0, 0, 0]),
            transform: identity_transform(),
        };
        let mut initial_ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(
                descriptor.mapping,
                [source.clone()],
                &mut initial_ops,
            )
            .unwrap();
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-mapping-rollback-initial",
            initial_ops,
        );
        let previous_mapping = runtime.descriptor().mapping;

        let moved =
            VoxelLightVolumeMapping::complementary(descriptor.extent, [1, 0, 0], [0.0, 0.0, 0.0])
                .unwrap();
        let mut moved_ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(moved, [source], &mut moved_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "occupancy-runtime-mapping-rollback.list".to_owned(),
                operations: moved_ops,
            })
            .unwrap();
        gal.mock_backend_mut().unwrap().fail_next_submit();
        assert!(gal
            .submit(SubmissionBatch {
                label: "occupancy-runtime-mapping-rollback".to_owned(),
                command_lists: vec![list],
            })
            .is_err());
        runtime.discard_submission();
        assert_eq!(previous_mapping, runtime.descriptor().mapping);
        assert!(runtime.is_initialized());
        assert_eq!(1, runtime.mesh_snapshot_count());
    }

    #[test]
    fn occupancy_mapping_rejects_invalid_input_without_changing_live_generation() {
        let descriptor = descriptor();
        let mut voxelizer = new_voxelizer(descriptor.clone());
        let mut invalid = descriptor.mapping;
        invalid.camera_fraction[1] = 1.0;
        assert!(voxelizer.update_mapping(invalid).is_err());
        assert_eq!(descriptor.mapping, voxelizer.descriptor().mapping);
    }

    #[test]
    fn occupancy_runtime_rejects_malformed_compact_triangle_indices() {
        let descriptor = descriptor();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut runtime =
            TerrainOccupancyRuntime::create(&mut gal, descriptor, materials()).unwrap();
        let source = TerrainVoxelSourceMesh {
            mesh_key: 0x89,
            mesh_generation: 1,
            vertices: Arc::new(vec![TerrainVoxelSourceVertex {
                position: [0.0, 0.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 30_008,
            }]),
            indices: Arc::new(vec![0, 0]),
            transform: identity_transform(),
        };
        let error = runtime
            .append_terrain_source_snapshot([source], &mut Vec::new())
            .unwrap_err();
        assert!(error.to_string().contains("triangle index stream"));
    }

    #[test]
    fn occupancy_runtime_rejects_compact_topology_changes_without_a_generation_advance() {
        let descriptor = descriptor();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut runtime =
            TerrainOccupancyRuntime::create(&mut gal, descriptor, materials()).unwrap();
        let source = TerrainVoxelSourceMesh {
            mesh_key: 0x8a,
            mesh_generation: 1,
            vertices: Arc::new(vec![TerrainVoxelSourceVertex {
                position: [0.0, 0.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 30_008,
            }]),
            indices: Arc::new(vec![0, 0, 0]),
            transform: identity_transform(),
        };
        let mut operations = Vec::new();
        runtime
            .append_terrain_source_snapshot([source.clone()], &mut operations)
            .unwrap();
        submit_runtime_update(
            &mut gal,
            &mut runtime,
            "occupancy-runtime-compact-topology-initial",
            operations,
        );

        let mut changed = source;
        let mut changed_vertices = (*changed.vertices).clone();
        changed_vertices.push(TerrainVoxelSourceVertex {
            position: [1.0, 0.0, 0.0],
            mid_block_packed: 0,
            shader_material_id: 30_008,
        });
        changed.vertices = Arc::new(changed_vertices);
        changed.indices = Arc::new(vec![0, 1, 1]);
        let error = runtime
            .append_terrain_source_snapshot([changed], &mut Vec::new())
            .unwrap_err();
        assert!(error
            .to_string()
            .contains("changed semantic data without advancing generation"));
    }

    #[test]
    fn flood_fill_resources_own_parity_and_retire_both_3d_fields() {
        let descriptor = descriptor();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let resources = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
        assert_eq!(&descriptor, resources.descriptor());
        assert_eq!(resources.even_view, resources.propagation_source_view(0));
        assert_eq!(resources.odd_view, resources.terrain_sample_view(0));
        assert_eq!(resources.odd_view, resources.propagation_source_view(1));
        assert_eq!(resources.even_view, resources.terrain_sample_view(1));
        let even_texture = resources.even_texture;
        let odd_texture = resources.odd_texture;
        let list = gal
            .create_command_list(CommandListDesc {
                label: "flood-fill-volume-use".to_owned(),
                operations: vec![
                    CommandOp::Barrier(resource_barrier(
                        even_texture,
                        None,
                        TextureUsageState::Undefined,
                        TextureUsageState::ShaderWrite,
                    )),
                    CommandOp::Barrier(resource_barrier(
                        odd_texture,
                        None,
                        TextureUsageState::Undefined,
                        TextureUsageState::ShaderWrite,
                    )),
                ],
            })
            .unwrap();
        let token = gal
            .submit(SubmissionBatch {
                label: "flood-fill-volume-use".to_owned(),
                command_lists: vec![list],
            })
            .unwrap();
        resources.destroy(&mut gal).unwrap();
        let retired = gal.retire_through_for_test(token.submission).unwrap();
        assert!(retired.contains(&even_texture));
        assert!(retired.contains(&odd_texture));
    }

    #[test]
    fn flood_fill_emission_table_upload_is_generation_bound_and_reused() {
        let descriptor = descriptor();
        let source = bundled_complementary_hung_loified_source(1).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let table = VoxelEmissionTable::derive(&source, &contract).unwrap();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut resources = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
        let mut operations = Vec::new();
        assert!(resources
            .append_emission_upload(&table, &mut operations)
            .unwrap());
        assert!(!resources.emission_ready());
        assert!(
            matches!(operations.as_slice(), [CommandOp::HostWriteBuffer { data, .. }] if data.len() == 4096)
        );
        resources.confirm_emission_submission().unwrap();
        assert!(resources.emission_ready());
        assert!(resources
            .append_tint_upload(&table, &mut operations)
            .unwrap());
        assert!(!resources.tint_ready());
        assert!(
            matches!(operations.last(), Some(CommandOp::HostWriteBuffer { data, .. }) if data.len() == VOXEL_TINT_COUNT * 16)
        );
        resources.confirm_tint_submission().unwrap();
        assert!(resources.tint_ready());
        assert!(!resources
            .append_tint_upload(&table, &mut operations)
            .unwrap());
        assert!(!resources
            .append_emission_upload(&table, &mut operations)
            .unwrap());
        let stale_source = bundled_complementary_hung_loified_source(2).unwrap();
        let stale_contract = derive_complementary_terrain_contract(&stale_source).unwrap();
        let stale = VoxelEmissionTable::derive(&stale_source, &stale_contract).unwrap();
        assert!(resources
            .append_emission_upload(&stale, &mut Vec::new())
            .is_err());
    }

    #[test]
    fn colored_light_runtime_confirms_occupancy_tables_and_ping_pong_in_separate_submissions() {
        let descriptor = descriptor();
        let source = bundled_complementary_hung_loified_source(1).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let emission = VoxelEmissionTable::derive(&source, &contract).unwrap();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut runtime =
            TerrainColoredLightRuntime::create(&mut gal, descriptor.clone(), materials(), emission)
                .unwrap();
        assert!(runtime.readiness().is_err());
        let mesh = TerrainVoxelSourceMesh {
            mesh_key: 0xc01,
            mesh_generation: 1,
            vertices: Arc::new(vec![TerrainVoxelSourceVertex {
                position: [0.0, 0.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 30_008,
            }]),
            indices: Arc::new(vec![0, 0, 0]),
            transform: identity_transform(),
        };

        let submit = |gal: &mut VulkanicGal,
                      runtime: &mut TerrainColoredLightRuntime,
                      label: &str,
                      operations: Vec<CommandOp>| {
            assert!(runtime.has_pending_submission());
            let list = gal
                .create_command_list(CommandListDesc {
                    label: format!("{label}.list"),
                    operations,
                })
                .unwrap();
            gal.submit(SubmissionBatch {
                label: label.to_string(),
                command_lists: vec![list],
            })
            .unwrap();
            runtime.confirm_submission().unwrap();
        };

        let mut upload_ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(
                0,
                descriptor.mapping,
                None,
                [mesh.clone()],
                &mut upload_ops,
            )
            .unwrap();
        assert!(upload_ops
            .iter()
            .any(|operation| matches!(operation, CommandOp::CopyBufferToTexture(_))));
        assert_eq!(
            1,
            upload_ops
                .iter()
                .filter(|operation| {
                    matches!(operation, CommandOp::HostWriteBuffer { data, .. } if data.len() == VoxelLightVolumeShaderMapping::STD140_SIZE)
                })
                .count()
        );
        assert!(!upload_ops
            .iter()
            .any(|operation| matches!(operation, CommandOp::Dispatch { .. })));
        submit(&mut gal, &mut runtime, "colored-light.upload", upload_ops);
        assert!(!runtime.is_ready_for_frame(0));
        assert!(runtime.readiness().is_err());

        let mut seed_ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(
                1,
                descriptor.mapping,
                None,
                [mesh.clone()],
                &mut seed_ops,
            )
            .unwrap();
        assert_eq!(
            1,
            seed_ops
                .iter()
                .filter(|operation| matches!(operation, CommandOp::Dispatch { .. }))
                .count()
        );
        assert!(!seed_ops.iter().any(|operation| {
            matches!(operation, CommandOp::HostWriteBuffer { data, .. } if data.len() == VoxelLightVolumeShaderMapping::STD140_SIZE)
        }));
        submit(&mut gal, &mut runtime, "colored-light.seed", seed_ops);
        assert!(!runtime.is_ready_for_frame(0));
        assert!(runtime.is_ready_for_frame(1));
        assert!(runtime.readiness().unwrap().binding_for_frame(0).is_err());

        let mut propagate_ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(
                0,
                descriptor.mapping,
                None,
                [mesh.clone()],
                &mut propagate_ops,
            )
            .unwrap();
        assert_eq!(
            1,
            propagate_ops
                .iter()
                .filter(|operation| matches!(operation, CommandOp::Dispatch { .. }))
                .count()
        );
        submit(
            &mut gal,
            &mut runtime,
            "colored-light.propagate",
            propagate_ops,
        );
        assert!(runtime.is_ready_for_frame(0));
        assert!(runtime.is_ready_for_frame(1));
        let ready = runtime.readiness().unwrap();
        assert_eq!(
            VoxelLightVolumeKind::FloodFillOdd,
            ready.binding_for_frame(0).unwrap().active_light_field
        );
        assert_eq!(
            VoxelLightVolumeKind::FloodFillEven,
            ready.binding_for_frame(1).unwrap().active_light_field
        );
        let even_binding = runtime.sampling_binding(0).unwrap();
        let odd_binding = runtime.sampling_binding(1).unwrap();
        assert_eq!(
            VoxelLightVolumeKind::FloodFillOdd,
            even_binding.active_light_field
        );
        assert_eq!(
            VoxelLightVolumeKind::FloodFillEven,
            odd_binding.active_light_field
        );
        assert_eq!(even_binding.resource_layout, odd_binding.resource_layout);
        assert_ne!(even_binding.resource_set, odd_binding.resource_set);
        assert_eq!(
            descriptor.resource_generation,
            even_binding.resource_generation
        );

        let even_texture = runtime.flood_fill.even_texture;
        let moved =
            VoxelLightVolumeMapping::complementary(descriptor.extent, [1, 0, 0], [0.25, 0.5, 0.75])
                .unwrap();
        let mut moved_ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(
                2,
                moved,
                None,
                [mesh.clone()],
                &mut moved_ops,
            )
            .unwrap();
        assert_eq!(
            1,
            moved_ops
                .iter()
                .filter(|operation| {
                    matches!(operation, CommandOp::HostWriteBuffer { data, .. } if data.len() == VoxelLightVolumeShaderMapping::STD140_SIZE)
                })
                .count()
        );
        assert!(!moved_ops
            .iter()
            .any(|operation| matches!(operation, CommandOp::Dispatch { .. })));
        assert!(runtime.sampling_binding(0).is_err());
        submit(
            &mut gal,
            &mut runtime,
            "colored-light.mapping-upload",
            moved_ops,
        );
        assert!(!runtime.is_ready_for_frame(0));
        assert!(!runtime.is_ready_for_frame(1));

        let mut temporal_ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(
                3,
                moved,
                None,
                [mesh.clone()],
                &mut temporal_ops,
            )
            .unwrap();
        assert!(temporal_ops.iter().any(|operation| matches!(
            operation,
            CommandOp::BindResourceSet { set, .. } if *set == runtime.compute.odd_to_even_set
        )));
        assert!(temporal_ops.iter().any(|operation| matches!(
            operation,
            CommandOp::HostWriteBuffer { buffer, offset, data }
                if *buffer == runtime.compute.temporal_mapping_buffer
                    && *offset == 0
                    && data.len() == VoxelLightVolumeTemporalMapping::STD140_SIZE
        )));
        let temporal_bytes = temporal_ops
            .iter()
            .find_map(|operation| match operation {
                CommandOp::HostWriteBuffer { buffer, data, .. }
                    if *buffer == runtime.compute.temporal_mapping_buffer =>
                {
                    Some(data)
                }
                _ => None,
            })
            .expect("temporal propagation must upload its camera-cell mapping");
        assert_eq!(
            -1,
            i32::from_le_bytes(temporal_bytes[0..4].try_into().unwrap()),
            "previousCameraPosition - cameraPosition must reproject a +X camera move",
        );
        submit(
            &mut gal,
            &mut runtime,
            "colored-light.mapping-temporal-propagation",
            temporal_ops,
        );
        assert!(runtime.is_ready_for_frame(0));
        assert!(runtime.is_ready_for_frame(1));

        let previous_mapping = runtime.descriptor().mapping;
        let later =
            VoxelLightVolumeMapping::complementary(descriptor.extent, [2, 0, 0], [0.25, 0.5, 0.75])
                .unwrap();
        let mut rollback_ops = Vec::new();
        runtime
            .append_terrain_source_snapshot_for_mapping(4, later, None, [mesh], &mut rollback_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "colored-light.mapping-rollback.list".to_owned(),
                operations: rollback_ops,
            })
            .unwrap();
        gal.mock_backend_mut().unwrap().fail_next_submit();
        assert!(gal
            .submit(SubmissionBatch {
                label: "colored-light.mapping-rollback".to_owned(),
                command_lists: vec![list],
            })
            .is_err());
        runtime.discard_submission();
        assert_eq!(previous_mapping, runtime.descriptor().mapping);
        assert_eq!(even_texture, runtime.flood_fill.even_texture);
        assert!(runtime.is_ready_for_frame(0));
        assert!(runtime.is_ready_for_frame(1));
        assert!(runtime.readiness().is_ok());
    }

    #[test]
    fn flood_fill_compute_lifecycle_requires_confirmed_inputs_and_ping_pongs() {
        let descriptor = descriptor();
        let source = bundled_complementary_hung_loified_source(1).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let table = VoxelEmissionTable::derive(&source, &contract).unwrap();
        let mut voxelizer = new_voxelizer(descriptor.clone());
        voxelizer
            .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
            .unwrap();
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut occupancy = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
        let mut flood_fill = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
        let mut compute =
            TerrainFloodFillComputeResources::create(&mut gal, &occupancy, &flood_fill).unwrap();

        assert!(compute
            .append_initialization(&occupancy, &flood_fill, 0, &mut Vec::new())
            .is_err());

        let mut occupancy_ops = Vec::new();
        occupancy
            .append_upload(voxelizer.pending_upload().unwrap(), &mut occupancy_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "occupancy-ready".to_owned(),
                operations: occupancy_ops,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: "occupancy-ready".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        occupancy.confirm_submission().unwrap();
        voxelizer.confirm_pending_upload().unwrap();

        let mut emission_ops = Vec::new();
        flood_fill
            .append_emission_upload(&table, &mut emission_ops)
            .unwrap();
        flood_fill
            .append_tint_upload(&table, &mut emission_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "emission-ready".to_owned(),
                operations: emission_ops,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: "emission-ready".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        flood_fill.confirm_emission_submission().unwrap();
        flood_fill.confirm_tint_submission().unwrap();

        let mut seed_ops = Vec::new();
        compute
            .append_initialization(&occupancy, &flood_fill, 0, &mut seed_ops)
            .unwrap();
        assert!(matches!(seed_ops.first(), Some(CommandOp::Barrier(_))));
        assert!(seed_ops.iter().any(|operation| matches!(
            operation,
            CommandOp::Dispatch {
                groups_x: 1,
                groups_y: 1,
                groups_z: 1
            }
        )));
        assert!(seed_ops.iter().any(|operation| matches!(
            operation,
            CommandOp::BindResourceSet { set, .. } if *set == compute.odd_init_set
        )));
        assert!(compute
            .append_propagation(&occupancy, &flood_fill, 0, None, &mut Vec::new())
            .is_err());
        let list = gal
            .create_command_list(CommandListDesc {
                label: "flood-fill-seed".to_owned(),
                operations: seed_ops,
            })
            .unwrap();
        let _token = gal
            .submit(SubmissionBatch {
                label: "flood-fill-seed".to_owned(),
                command_lists: vec![list],
            })
            .unwrap();
        compute.confirm_initialization().unwrap();
        assert!(!compute.even_initialized);
        assert!(compute.odd_initialized);
        assert!(compute.is_initialized_for_frame(0));
        assert!(!compute.is_initialized_for_frame(1));

        let mut propagation_ops = Vec::new();
        compute
            .append_propagation(&occupancy, &flood_fill, 1, None, &mut propagation_ops)
            .unwrap();
        assert!(propagation_ops
            .iter()
            .any(|operation| matches!(operation, CommandOp::BindComputePipeline(_))));
        assert!(propagation_ops.iter().any(|operation| matches!(
            operation,
            CommandOp::BindResourceSet { set, .. } if *set == compute.odd_to_even_set
        )));
        assert!(propagation_ops.iter().any(|operation| matches!(
            operation,
            CommandOp::HostWriteBuffer { buffer, offset, data }
                if *buffer == compute.temporal_mapping_buffer
                    && *offset == 0
                    && data.len() == VoxelLightVolumeTemporalMapping::STD140_SIZE
        )));
        assert!(propagation_ops.iter().any(|operation| matches!(
            operation,
            CommandOp::Barrier(ResourceBarrier {
                resource,
                subresources: None,
                before: TextureUsageState::TransferDst,
                after: TextureUsageState::ShaderRead,
                ..
            }) if *resource == compute.temporal_mapping_buffer
        )));
        let list = gal
            .create_command_list(CommandListDesc {
                label: "flood-fill-propagate".to_owned(),
                operations: propagation_ops,
            })
            .unwrap();
        let token = gal
            .submit(SubmissionBatch {
                label: "flood-fill-propagate".to_owned(),
                command_lists: vec![list],
            })
            .unwrap();
        compute.confirm_propagation().unwrap();
        assert!(compute.even_initialized);
        assert!(compute.odd_initialized);

        let odd_texture = flood_fill.odd_texture;
        compute.destroy(&mut gal).unwrap();
        flood_fill.destroy(&mut gal).unwrap();
        occupancy.destroy(&mut gal).unwrap();
        let retired = gal.retire_through_for_test(token.submission).unwrap();
        assert!(retired.contains(&odd_texture));
    }

    #[test]
    fn flood_fill_behind_view_policy_uploads_the_source_derived_camera_direction() {
        let mut descriptor = descriptor();
        descriptor.requirements.update_policy.preserve_behind_view = true;
        let mut gal = VulkanicGal::new_with_backend(Box::new(MockBackend::default()), false);
        let mut occupancy = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
        let mut flood_fill = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
        let source = bundled_complementary_hung_loified_source(1).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let emission = VoxelEmissionTable::derive(&source, &contract).unwrap();
        let mut voxelizer = new_voxelizer(descriptor.clone());
        voxelizer
            .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
            .unwrap();
        let mut occupancy_ops = Vec::new();
        occupancy
            .append_upload(voxelizer.pending_upload().unwrap(), &mut occupancy_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "behind-view.occupancy".to_owned(),
                operations: occupancy_ops,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: "behind-view.occupancy".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        occupancy.confirm_submission().unwrap();
        voxelizer.confirm_pending_upload().unwrap();
        let mut material_ops = Vec::new();
        flood_fill
            .append_emission_upload(&emission, &mut material_ops)
            .unwrap();
        flood_fill
            .append_tint_upload(&emission, &mut material_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "behind-view.materials".to_owned(),
                operations: material_ops,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: "behind-view.materials".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        flood_fill.confirm_emission_submission().unwrap();
        flood_fill.confirm_tint_submission().unwrap();
        let mut compute =
            TerrainFloodFillComputeResources::create(&mut gal, &occupancy, &flood_fill).unwrap();
        let mut seed_ops = Vec::new();
        compute
            .append_initialization(&occupancy, &flood_fill, 0, &mut seed_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "behind-view.seed".to_owned(),
                operations: seed_ops,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: "behind-view.seed".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        compute.confirm_initialization().unwrap();

        let mut propagation_ops = Vec::new();
        compute
            .append_propagation(
                &occupancy,
                &flood_fill,
                1,
                Some(VoxelLightVolumeViewDirection {
                    normalized_camera_forward: [1.0, 0.0, 0.0],
                }),
                &mut propagation_ops,
            )
            .unwrap();
        let bytes = propagation_ops
            .iter()
            .find_map(|operation| match operation {
                CommandOp::HostWriteBuffer { buffer, data, .. }
                    if *buffer == compute.temporal_mapping_buffer =>
                {
                    Some(data)
                }
                _ => None,
            })
            .expect("behind-view propagation must upload a temporal mapping");
        assert_eq!(1.0f32.to_le_bytes(), bytes[48..52]);
        assert!(FLOOD_FILL_PROPAGATE_SHADER.contains("preserveBehindView"));
        assert!(FLOOD_FILL_PROPAGATE_SHADER.contains("CameraForward"));
    }

    #[test]
    fn flood_fill_compute_kernels_create_on_opengl_when_the_real_d3_path_is_available() {
        let backend = match OpenGlBackend::new("MattMC private voxel flood-fill OpenGL") {
            Ok(backend) => backend,
            Err(error) => {
                let text = error.to_string();
                assert!(
                    text.contains("OpenGL") || text.contains("EGL") || text.contains("GL"),
                    "unexpected OpenGL voxel compute setup failure: {text}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        let descriptor = descriptor();
        let occupancy = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
        let flood_fill = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
        let compute =
            TerrainFloodFillComputeResources::create(&mut gal, &occupancy, &flood_fill).unwrap();
        compute.destroy(&mut gal).unwrap();
        flood_fill.destroy(&mut gal).unwrap();
        occupancy.destroy(&mut gal).unwrap();
    }

    #[test]
    fn flood_fill_compute_kernels_create_on_vulkan_when_available() {
        let backend = match VulkanBackend::new("MattMC private voxel flood-fill Vulkan") {
            Ok(backend) => backend,
            Err(error) => {
                let text = error.to_string();
                assert!(
                    text.contains("Vulkan")
                        || text.contains("vulkan")
                        || text.contains("physical device"),
                    "unexpected Vulkan voxel compute setup failure: {text}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        let descriptor = descriptor();
        let occupancy = TerrainOccupancyGpuResources::create(&mut gal, &descriptor).unwrap();
        let flood_fill = TerrainFloodFillGpuResources::create(&mut gal, &descriptor).unwrap();
        let compute =
            TerrainFloodFillComputeResources::create(&mut gal, &occupancy, &flood_fill).unwrap();
        compute.destroy(&mut gal).unwrap();
        flood_fill.destroy(&mut gal).unwrap();
        occupancy.destroy(&mut gal).unwrap();
    }

    fn run_private_flood_fill_compute_dispatch(gal: &mut VulkanicGal) {
        let descriptor = descriptor();
        let source = bundled_complementary_hung_loified_source(1).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let table = VoxelEmissionTable::derive(&source, &contract).unwrap();
        let mut voxelizer = new_voxelizer(descriptor.clone());
        voxelizer
            .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
            .unwrap();
        let mut occupancy = TerrainOccupancyGpuResources::create(gal, &descriptor).unwrap();
        let mut flood_fill = TerrainFloodFillGpuResources::create(gal, &descriptor).unwrap();
        let mut compute =
            TerrainFloodFillComputeResources::create(gal, &occupancy, &flood_fill).unwrap();

        let mut upload_ops = Vec::new();
        occupancy
            .append_upload(voxelizer.pending_upload().unwrap(), &mut upload_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "private-flood-fill.occupancy".to_owned(),
                operations: upload_ops,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: "private-flood-fill.occupancy".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        occupancy.confirm_submission().unwrap();
        voxelizer.confirm_pending_upload().unwrap();

        let mut emission_ops = Vec::new();
        flood_fill
            .append_emission_upload(&table, &mut emission_ops)
            .unwrap();
        flood_fill
            .append_tint_upload(&table, &mut emission_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "private-flood-fill.emission".to_owned(),
                operations: emission_ops,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: "private-flood-fill.emission".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        flood_fill.confirm_emission_submission().unwrap();
        flood_fill.confirm_tint_submission().unwrap();

        let mut seed_ops = Vec::new();
        compute
            .append_initialization(&occupancy, &flood_fill, 0, &mut seed_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "private-flood-fill.seed".to_owned(),
                operations: seed_ops,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: "private-flood-fill.seed".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        compute.confirm_initialization().unwrap();

        let mut propagation_ops = Vec::new();
        compute
            .append_propagation(&occupancy, &flood_fill, 1, None, &mut propagation_ops)
            .unwrap();
        let list = gal
            .create_command_list(CommandListDesc {
                label: "private-flood-fill.propagate".to_owned(),
                operations: propagation_ops,
            })
            .unwrap();
        gal.submit(SubmissionBatch {
            label: "private-flood-fill.propagate".to_owned(),
            command_lists: vec![list],
        })
        .unwrap();
        compute.confirm_propagation().unwrap();

        compute.destroy(gal).unwrap();
        flood_fill.destroy(gal).unwrap();
        occupancy.destroy(gal).unwrap();
    }

    /// Exercises the exact combined transaction used by the private world
    /// frontend. The independent dispatch tests below prove the kernels; this
    /// closes the gap where an occupancy upload, source tables, and ping-pong
    /// dispatches might each work alone but fail when recorded across real
    /// backend submissions.
    fn run_private_colored_light_transaction(gal: &mut VulkanicGal) {
        let descriptor = descriptor();
        let source = bundled_complementary_hung_loified_source(1).unwrap();
        let contract = derive_complementary_terrain_contract(&source).unwrap();
        let emission = VoxelEmissionTable::derive(&source, &contract).unwrap();
        let mut runtime =
            TerrainColoredLightRuntime::create(gal, descriptor.clone(), materials(), emission)
                .unwrap();
        let mesh = TerrainVoxelSourceMesh {
            mesh_key: 0xc01,
            mesh_generation: 1,
            vertices: Arc::new(vec![TerrainVoxelSourceVertex {
                position: [0.0, 0.0, 0.0],
                mid_block_packed: 0,
                shader_material_id: 30_008,
            }]),
            indices: Arc::new(vec![0, 0, 0]),
            transform: identity_transform(),
        };

        for (frame_counter, label) in [
            (0_u64, "private-colored-light.upload"),
            (1_u64, "private-colored-light.seed"),
            (0_u64, "private-colored-light.propagate"),
        ] {
            let mut operations = Vec::new();
            runtime
                .append_terrain_source_snapshot_for_mapping(
                    frame_counter,
                    descriptor.mapping,
                    None,
                    [mesh.clone()],
                    &mut operations,
                )
                .unwrap();
            assert!(runtime.has_pending_submission());
            let list = gal
                .create_command_list(CommandListDesc {
                    label: format!("{label}.list"),
                    operations,
                })
                .unwrap();
            gal.submit(SubmissionBatch {
                label: label.to_owned(),
                command_lists: vec![list],
            })
            .unwrap();
            runtime.confirm_submission().unwrap();
        }
        assert!(runtime.is_ready_for_frame(0));
        assert!(runtime.is_ready_for_frame(1));
        runtime.destroy(gal).unwrap();
    }

    #[test]
    fn flood_fill_compute_dispatches_on_opengl_when_the_real_d3_path_is_available() {
        let backend = match OpenGlBackend::new("MattMC private voxel flood-fill OpenGL dispatch") {
            Ok(backend) => backend,
            Err(error) => {
                let text = error.to_string();
                assert!(
                    text.contains("OpenGL") || text.contains("EGL") || text.contains("GL"),
                    "unexpected OpenGL voxel dispatch setup failure: {text}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        run_private_flood_fill_compute_dispatch(&mut gal);
    }

    #[test]
    fn colored_light_transaction_dispatches_on_opengl_when_the_real_d3_path_is_available() {
        let backend = match OpenGlBackend::new("MattMC private colored-light OpenGL transaction") {
            Ok(backend) => backend,
            Err(error) => {
                let text = error.to_string();
                assert!(
                    text.contains("OpenGL") || text.contains("EGL") || text.contains("GL"),
                    "unexpected OpenGL colored-light setup failure: {text}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        run_private_colored_light_transaction(&mut gal);
    }

    #[test]
    fn flood_fill_compute_dispatches_on_vulkan_when_available() {
        let backend = match VulkanBackend::new("MattMC private voxel flood-fill Vulkan dispatch") {
            Ok(backend) => backend,
            Err(error) => {
                let text = error.to_string();
                assert!(
                    text.contains("Vulkan")
                        || text.contains("vulkan")
                        || text.contains("physical device"),
                    "unexpected Vulkan voxel dispatch setup failure: {text}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        run_private_flood_fill_compute_dispatch(&mut gal);
    }

    #[test]
    fn colored_light_transaction_dispatches_on_vulkan_when_available() {
        let backend = match VulkanBackend::new("MattMC private colored-light Vulkan transaction") {
            Ok(backend) => backend,
            Err(error) => {
                let text = error.to_string();
                assert!(
                    text.contains("Vulkan")
                        || text.contains("vulkan")
                        || text.contains("physical device"),
                    "unexpected Vulkan colored-light setup failure: {text}"
                );
                return;
            }
        };
        let mut gal = VulkanicGal::new_with_backend(Box::new(backend), false);
        run_private_colored_light_transaction(&mut gal);
    }

    #[test]
    fn rejected_upload_rolls_back_and_rejects_overlapping_candidates() {
        let mut voxelizer = new_voxelizer(descriptor());
        voxelizer
            .update_from_samples([sample([0.0, 0.0, 0.0], 30_008)])
            .unwrap();
        assert!(voxelizer
            .update_from_samples([sample([1.0, 0.0, 0.0], 30_008)])
            .is_err());
        voxelizer.discard_pending_upload();
        assert!(voxelizer.cache().binding_for_frame(0).is_err());

        voxelizer
            .update_from_samples([sample([1.0, 0.0, 0.0], 30_012)])
            .unwrap();
        voxelizer.confirm_pending_upload().unwrap();
        assert!(voxelizer.cache().binding_for_frame(0).is_err());
        // Occupancy alone is intentionally insufficient for selected-source
        // admission until both flood-fill fields exist.
    }

    #[test]
    fn rejects_descriptor_reload_that_would_reuse_a_stale_material_map() {
        let mut voxelizer = new_voxelizer(descriptor());
        let mut next = descriptor();
        next.shader_pack_generation += 1;
        next.resource_generation += 1;
        assert!(voxelizer.replace_descriptor(next).is_err());
        assert_eq!(1, voxelizer.descriptor().shader_pack_generation);
    }
}

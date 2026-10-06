//! Static-terrain mesh snapshots and the deltas between frames.

use super::*;

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
pub(super) struct TerrainOccupancyMeshSnapshot {
    pub(super) mesh_generation: u64,
    /// Complementary's `UpdateVoxelMap` is a vertex-stage image store. Keep
    /// the referenced indexed vertices rather than approximating the source
    /// with a CPU triangle-volume rasterizer.
    pub(super) samples: Arc<Vec<TerrainVoxelSample>>,
    /// `world_block_center` of each sample, derived once; `None` where it
    /// fails. Patch staging reuses it instead of re-transforming every sample.
    pub(super) world_centers: Arc<Vec<Option<[f32; 3]>>>,
    /// Inclusive min/max world block (floored sample block centre) over every
    /// finite sample. Incremental voxel updates use it to find the meshes a
    /// dirty region depends on; it is derived from `samples` and adds no
    /// semantics of its own.
    pub(super) world_bounds: Option<[[i32; 3]; 2]>,
    /// Immutable, Rust-owned source arrays identify an unchanged source mesh
    /// before transforming it into voxel samples. The runtime retains these
    /// only as an ownership-safe cache key; no Java or backend buffer is
    /// borrowed across submission.
    pub(super) source_identity: Option<TerrainOccupancySourceIdentity>,
}

#[derive(Clone, Debug, PartialEq)]
pub(super) struct TerrainOccupancySourceIdentity {
    pub(super) vertices: Arc<Vec<TerrainVoxelSourceVertex>>,
    pub(super) indices: Arc<Vec<u32>>,
    pub(super) transform: [f32; 16],
}

pub(super) fn snapshot_difference(
    existing: &TerrainOccupancyMeshSnapshot,
    incoming: &TerrainOccupancyMeshSnapshot,
) -> String {
    if existing.samples.len() != incoming.samples.len() {
        return format!(
            "sample-count {} -> {}",
            existing.samples.len(),
            incoming.samples.len()
        );
    }
    for (index, (previous, next)) in existing.samples.iter().zip(incoming.samples.iter()).enumerate() {
        if previous.vertex_position != next.vertex_position {
            return format!("sample[{index}].vertex-position");
        }
        if previous.mid_block_packed != next.mid_block_packed {
            return format!("sample[{index}].mid-block");
        }
        if previous.shader_material_id != next.shader_material_id {
            return format!("sample[{index}].shader-material");
        }
        if previous.model_transform != next.model_transform {
            return format!("sample[{index}].world-transform");
        }
    }
    match (&existing.source_identity, &incoming.source_identity) {
        (Some(previous), Some(next)) => {
            if previous.vertices != next.vertices {
                "source-vertices".to_owned()
            } else if previous.indices != next.indices {
                "source-indices".to_owned()
            } else if previous.transform != next.transform {
                "source-world-transform".to_owned()
            } else {
                "unclassified-snapshot-field".to_owned()
            }
        }
        (None, None) => "unclassified-snapshot-field".to_owned(),
        _ => "source-identity-presence".to_owned(),
    }
}

/// Returns the first semantic difference that cannot be explained by a
/// camera-relative transform update. Mesh generations identify immutable
/// geometry/material content; the transform is per-frame placement and may
/// legitimately change while the camera crosses orbits around a static
/// section. Transform changes still flow through the voxel upload, but must
/// not be mistaken for an illicit mesh-content mutation.
pub(super) fn snapshot_difference_excluding_transform(
    existing: &TerrainOccupancyMeshSnapshot,
    incoming: &TerrainOccupancyMeshSnapshot,
) -> Option<String> {
    if existing.samples.len() != incoming.samples.len() {
        return Some(format!(
            "sample-count {} -> {}",
            existing.samples.len(),
            incoming.samples.len()
        ));
    }
    for (index, (previous, next)) in existing.samples.iter().zip(incoming.samples.iter()).enumerate() {
        if previous.vertex_position != next.vertex_position {
            return Some(format!("sample[{index}].vertex-position"));
        }
        if previous.mid_block_packed != next.mid_block_packed {
            return Some(format!("sample[{index}].mid-block"));
        }
        if previous.shader_material_id != next.shader_material_id {
            return Some(format!("sample[{index}].shader-material"));
        }
    }
    match (&existing.source_identity, &incoming.source_identity) {
        (Some(previous), Some(next)) => {
            if previous.vertices != next.vertices {
                Some("source-vertices".to_owned())
            } else if previous.indices != next.indices {
                Some("source-indices".to_owned())
            } else {
                None
            }
        }
        (None, None) => None,
        _ => Some("source-identity-presence".to_owned()),
    }
}

pub(super) fn static_terrain_mesh_snapshot<'a>(
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

pub(super) fn static_terrain_mesh_entry(
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

/// Floored block-centre bounds of the finite samples (non-finite samples are
/// rejected when voxelized, so they never contribute a cell).
fn centers_world_bounds(centers: &[Option<[f32; 3]>]) -> Option<[[i32; 3]; 2]> {
    let mut bounds: Option<[[i32; 3]; 2]> = None;
    for center in centers {
        let Some(center) = *center else {
            continue;
        };
        if center.iter().any(|value| !value.is_finite()) {
            continue;
        }
        let point = center.map(|value| value.floor() as i32);
        bounds = Some(match bounds {
            None => [point, point],
            Some([min, max]) => [
                [0, 1, 2].map(|axis| min[axis].min(point[axis])),
                [0, 1, 2].map(|axis| max[axis].max(point[axis])),
            ],
        });
    }
    bounds
}

pub(super) fn terrain_voxel_source_entry(
    mesh: &TerrainVoxelSourceMesh,
) -> GalResult<TerrainOccupancyMeshSnapshot> {
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
    indexed_terrain_snapshot(
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
    )
}

pub(super) fn terrain_voxel_source_snapshot(
    meshes: impl IntoIterator<Item = TerrainVoxelSourceMesh>,
) -> GalResult<BTreeMap<u64, TerrainOccupancyMeshSnapshot>> {
    let mut snapshot = BTreeMap::new();
    for mesh in meshes {
        let entry = terrain_voxel_source_entry(&mesh)?;
        if snapshot.insert(mesh.mesh_key, entry).is_some() {
            return Err(GalError::invalid_argument(format!(
                "terrain voxel source snapshot contains duplicate mesh key {}",
                mesh.mesh_key
            )));
        }
    }
    Ok(snapshot)
}

pub(super) fn decoded_mesh_indices(mesh: &WorldMeshAsset) -> GalResult<Vec<u32>> {
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

pub(super) fn indexed_terrain_snapshot(
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
    let samples: Vec<TerrainVoxelSample> = vertices
        .into_iter()
        .zip(referenced)
        .filter_map(|(sample, referenced)| referenced.then_some(sample))
        .collect();
    let world_centers: Vec<Option<[f32; 3]>> =
        samples.iter().map(|sample| sample.world_block_center().ok()).collect();
    let world_bounds = centers_world_bounds(&world_centers);
    Ok(TerrainOccupancyMeshSnapshot {
        mesh_generation,
        samples: Arc::new(samples),
        world_centers: Arc::new(world_centers),
        world_bounds,
        source_identity,
    })
}

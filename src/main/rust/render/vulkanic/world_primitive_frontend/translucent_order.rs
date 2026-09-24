//! Rust-owned ordering over immutable semantic terrain buffers. The output is
//! explicit indexed draw ranges; it neither rewrites GPU memory nor reconstructs
//! backend state. One cache per mesh generation bounds retained sort state.

use super::*;
use crate::render::chunk::translucent::semantic::SemanticTranslucentGeometry;

pub(super) struct CachedOrder {
    geometry: SemanticTranslucentGeometry,
    quads: Vec<(u32, u64)>,
    camera: Option<[f32; 3]>,
    order: Vec<usize>,
    stable_camera_frames: u8,
    direct_template: Option<CachedDirectBatches>,
}

struct CachedDirectBatches {
    instance: MeshBatchInstanceKey,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    batches: Vec<MeshBatch>,
    indices: Vec<u8>,
}

pub(super) fn validate_instance(instance: &WorldMeshInstanceRequest) -> GalResult<()> {
    if instance.stratum != WORLD_STRATUM_TERRAIN
        || instance.mesh_section_index != WORLD_MESH_SECTION_ALL
        || !matches!(
            instance.depth_policy,
            WORLD_DEPTH_POLICY_TEST_WRITE | WORLD_DEPTH_POLICY_TEST_NO_WRITE
        )
    {
        return Err(GalError::invalid_argument(
            "camera-sorted quads require a complete translucent terrain instance",
        ));
    }
    // The semantic contract is a camera-relative translation. Do not silently
    // interpret an entity/model transform as a camera position.
    for i in 0..16 {
        if (12..15).contains(&i) {
            continue;
        }
        let expected = if [0, 5, 10, 15].contains(&i) {
            1.0
        } else {
            0.0
        };
        if instance.transform[i] != expected {
            return Err(GalError::invalid_argument(
                "camera-sorted terrain requires a translation-only transform",
            ));
        }
    }
    if !instance.transform.iter().all(|value| value.is_finite()) {
        return Err(GalError::invalid_argument(
            "camera-sorted terrain transform must be finite",
        ));
    }
    Ok(())
}

fn prepare(asset: &MeshAssetStore) -> GalResult<CachedOrder> {
    let stride = index_stride(asset.index_type) as usize;
    let mut positions = Vec::new();
    let mut quads = Vec::new();
    let mut expected_offset = 0usize;
    for (section_index, section) in asset.sections.iter().enumerate() {
        if section.material_mode != WORLD_MATERIAL_MODE_TRANSLUCENT
            || section.index_count % 6 != 0
            || section.index_offset as usize != expected_offset
        {
            return Err(GalError::invalid_argument(
                "camera-sorted terrain requires complete non-overlapping translucent quad ranges",
            ));
        }
        for quad in 0..section.index_count as usize / 6 {
            let offset = section.index_offset as usize + quad * 6 * stride;
            let mut indices = [0u32; 6];
            for (lane, value) in indices.iter_mut().enumerate() {
                *value =
                    mesh_index_value(&asset.index_bytes, asset.index_type, offset / stride + lane)?;
            }
            if indices[2] != indices[3] || indices[0] != indices[5] {
                return Err(GalError::invalid_argument(
                    "camera-sorted terrain requires canonical quad triangle indices",
                ));
            }
            let mut corners = [[0f32; 3]; 4];
            for (corner, lane) in [0, 1, 2, 4].into_iter().enumerate() {
                let start = indices[lane] as usize * WORLD_MESH_GPU_VERTEX_BYTES;
                let vertex = asset.vertex_bytes.get(start..start + 12).ok_or_else(|| {
                    GalError::invalid_argument(
                        "camera-sorted terrain vertex is outside its immutable buffer",
                    )
                })?;
                for axis in 0..3 {
                    corners[corner][axis] =
                        f32::from_ne_bytes(vertex[axis * 4..axis * 4 + 4].try_into().unwrap());
                }
            }
            if section.winding == WORLD_WINDING_CW {
                corners.reverse();
            }
            positions.push(corners);
            quads.push((section_index as u32, offset as u64));
        }
        expected_offset += section.index_count as usize * stride;
    }
    if expected_offset != asset.index_bytes.len() {
        return Err(GalError::invalid_argument(
            "camera-sorted terrain ranges do not cover their index buffer",
        ));
    }
    let geometry = SemanticTranslucentGeometry::new(&positions)
        .ok_or_else(|| GalError::invalid_argument("invalid camera-sorted terrain geometry"))?;
    Ok(CachedOrder {
        geometry,
        quads,
        camera: None,
        order: Vec::new(),
        stable_camera_frames: 0,
        direct_template: None,
    })
}

pub(super) fn append_batches(
    instance: &WorldMeshInstanceRequest,
    asset: &MeshAssetStore,
    instance_index: usize,
    color_format: ColorFormat,
    raster_y_direction: RasterYDirection,
    g_buffer: bool,
    batches: &mut Vec<MeshBatch>,
    mut sorted_indices: Option<&mut Vec<u8>>,
) -> GalResult<()> {
    validate_instance(instance)?;
    let camera = [
        -instance.transform[12],
        -instance.transform[13],
        -instance.transform[14],
    ];
    let mut retained = asset.translucent_order.borrow_mut();
    if retained.is_none() {
        *retained = Some(prepare(asset)?);
    }
    let cache = retained.as_mut().unwrap();
    if cache.camera != Some(camera) {
        let order = cache
            .geometry
            .order(camera)
            .ok_or_else(|| GalError::invalid_argument("invalid translucent camera ordering"))?;
        cache.order = order;
        cache.camera = Some(camera);
        cache.stable_camera_frames = 0;
        cache.direct_template = None;
    } else {
        cache.stable_camera_frames = cache.stable_camera_frames.saturating_add(1);
    }
    // Immutable terrain topology and an unchanged camera produce the same
    // material runs and index order. Keep a bounded template with the asset;
    // only the frame's instance index and stream offset need rebinding.
    let direct_key = (cache.stable_camera_frames > 0 && !g_buffer && sorted_indices.is_some())
        .then(|| mesh_batch_instance_key(instance));
    if let (Some(key), Some(template), Some(bytes)) = (
        direct_key.as_ref(),
        cache.direct_template.as_ref(),
        sorted_indices.as_mut(),
    ) {
        if template.instance == *key
            && template.color_format == color_format
            && template.raster_y_direction == raster_y_direction
        {
            let base = (bytes.len() + 3) & !3;
            bytes.resize(base, 0);
            bytes.extend_from_slice(&template.indices);
            for batch in &template.batches {
                let mut rebound = batch.clone();
                rebound.indices.clear();
                rebound.indices.push(instance_index);
                rebound.sorted_index_offset =
                    batch.sorted_index_offset.map(|offset| offset + base as u64);
                batches.push(rebound);
            }
            return Ok(());
        }
    }
    let batch_start = batches.len();
    let index_start = sorted_indices.as_ref().map(|bytes| (bytes.len() + 3) & !3);
    for &ordinal in &cache.order {
        let (section_index, index_offset) = cache.quads[ordinal];
        let section = &asset.sections[section_index as usize];
        let sorted_index_offset = if let Some(bytes) = sorted_indices.as_mut() {
            let byte_count = 6 * index_stride(asset.index_type) as usize;
            let source = usize::try_from(index_offset)
                .ok()
                .and_then(|start| asset.index_bytes.get(start..start + byte_count))
                .ok_or_else(|| GalError::invalid_argument("sorted quad index range is invalid"))?;
            // U16 and U32 draws share one stream; align each new run to the
            // stricter index type so both direct and indirect offsets are valid.
            let aligned = (bytes.len() + 3) & !3;
            bytes.resize(aligned, 0);
            let offset = bytes.len() as u64;
            bytes.extend_from_slice(source);
            Some(offset)
        } else {
            None
        };
        let key = mesh_key_for_section(
            instance,
            section,
            section_index,
            section.cull_policy,
            asset_generation_for_key(instance.mesh_key, asset)?,
            asset.vertex_bytes.len() / super::WORLD_MESH_GPU_VERTEX_BYTES,
            color_format,
            raster_y_direction,
            g_buffer,
            false,
        );
        // Only contiguous indices with identical resources and the same
        // instance can form one draw. Never move a pane across another material.
        if let Some(last) = batches.last_mut().filter(|last| {
            // Direct sorted indices address the whole immutable mesh, so
            // identical bindings may span authored section boundaries. Keep
            // the first section as the resource identity for the run.
            let mut comparable = last.key;
            if sorted_index_offset.is_some() {
                comparable.section_index = key.section_index;
            }
            comparable == key
                && last.indices.as_slice() == [instance_index]
                && match (last.sorted_index_offset, sorted_index_offset) {
                    (Some(start), Some(next)) => {
                        start + u64::from(last.index_count) * index_stride(asset.index_type) == next
                    }
                    (None, None) => {
                        last.index_offset
                            + u64::from(last.index_count) * index_stride(asset.index_type)
                            == index_offset
                    }
                    _ => false,
                }
        }) {
            last.index_count += 6;
        } else {
            batches.push(MeshBatch {
                model_submission_order: instance.model_submission_order,
                key,
                index_offset,
                index_count: 6,
                sorted_index_offset,
                indices: vec![instance_index].into(),
            });
        }
    }
    if let (Some(key), Some(start), Some(bytes)) =
        (direct_key, index_start, sorted_indices.as_ref())
    {
        if bytes.len() < start || batches.len() == batch_start {
            return Ok(());
        }
        let mut cached_batches = batches[batch_start..].to_vec();
        for batch in &mut cached_batches {
            batch.sorted_index_offset = batch
                .sorted_index_offset
                .map(|offset| offset - start as u64);
        }
        cache.direct_template = Some(CachedDirectBatches {
            instance: key,
            color_format,
            raster_y_direction,
            batches: cached_batches,
            indices: bytes[start..].to_vec(),
        });
    }
    Ok(())
}

#[cfg(test)]
pub(super) fn has_direct_template(asset: &MeshAssetStore) -> bool {
    asset
        .translucent_order
        .borrow()
        .as_ref()
        .is_some_and(|order| order.direct_template.is_some())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn camera_sort_rejects_partial_geometry_and_non_quad_indices() {
        let mut asset = MeshAssetStore::default();
        asset.vertex_bytes = vec![0; WORLD_MESH_GPU_VERTEX_BYTES * 4];
        asset.index_bytes = [0u16, 1, 2, 2, 3, 0]
            .into_iter()
            .flat_map(u16::to_ne_bytes)
            .collect();
        asset.sections = vec![WorldMeshSection {
            material_id: WORLD_MATERIAL_ID_TRANSLUCENT_TEXTURED,
            texture_id: WORLD_MESH_TEXTURE_TERRAIN_BLOCK_ATLAS,
            material_mode: WORLD_MATERIAL_MODE_TRANSLUCENT,
            cull_policy: WORLD_CULL_BACK,
            winding: WORLD_WINDING_CCW,
            index_offset: 0,
            index_count: 6,
            source_facing: 6,
        }];
        assert!(prepare(&asset).is_ok());
        asset.sections[0].index_offset = 2;
        assert!(prepare(&asset).is_err());
        asset.sections[0].index_offset = 0;
        asset.sections[0].index_count = 3;
        assert!(prepare(&asset).is_err());
        asset.sections[0].index_count = 6;
        asset.index_bytes[6] = 1;
        assert!(prepare(&asset).is_err());
        asset.index_bytes[6] = 2;
        asset.sections[0].material_mode = WORLD_MATERIAL_MODE_OPAQUE;
        assert!(prepare(&asset).is_err());
    }
}

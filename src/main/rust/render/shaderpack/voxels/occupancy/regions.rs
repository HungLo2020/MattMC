//! World-box set arithmetic and changed-region byte copies.

use super::*;

/// Intersection of two half-open world boxes, if non-empty.
pub(super) fn intersect_world_boxes(a: [[i32; 3]; 2], b: [[i32; 3]; 2]) -> Option<[[i32; 3]; 2]> {
    let min = [0, 1, 2].map(|axis| a[0][axis].max(b[0][axis]));
    let max = [0, 1, 2].map(|axis| a[1][axis].min(b[1][axis]));
    (0..3).all(|axis| min[axis] < max[axis]).then_some([min, max])
}

/// Merges intersecting half-open boxes into their bounding boxes until the
/// set is pairwise disjoint (touching boxes stay separate).
pub(super) fn disjoint_world_boxes(mut boxes: Vec<[[i32; 3]; 2]>) -> Vec<[[i32; 3]; 2]> {
    let mut merged = true;
    while merged {
        merged = false;
        'outer: for i in 0..boxes.len() {
            for j in i + 1..boxes.len() {
                if intersect_world_boxes(boxes[i], boxes[j]).is_some() {
                    let other = boxes.swap_remove(j);
                    let current = boxes[i];
                    boxes[i] = [
                        [0, 1, 2].map(|axis| current[0][axis].min(other[0][axis])),
                        [0, 1, 2].map(|axis| current[1][axis].max(other[1][axis])),
                    ];
                    merged = true;
                    break 'outer;
                }
            }
        }
    }
    boxes
}

/// Half-open boxes covering `a` minus `b` (at most six, disjoint).
pub(super) fn subtract_world_box(a: [[i32; 3]; 2], b: [[i32; 3]; 2]) -> Vec<[[i32; 3]; 2]> {
    let Some(inner) = intersect_world_boxes(a, b) else {
        return vec![a];
    };
    let mut result = Vec::new();
    let mut rest = a;
    for axis in 0..3 {
        if rest[0][axis] < inner[0][axis] {
            let mut slab = rest;
            slab[1][axis] = inner[0][axis];
            result.push(slab);
            rest[0][axis] = inner[0][axis];
        }
        if inner[1][axis] < rest[1][axis] {
            let mut slab = rest;
            slab[0][axis] = inner[1][axis];
            result.push(slab);
            rest[1][axis] = inner[1][axis];
        }
    }
    result
}

pub(super) fn voxel_index(width: u32, height: u32, voxel: [u32; 3]) -> GalResult<usize> {
    let row = u64::from(voxel[2])
        .checked_mul(u64::from(height))
        .and_then(|value| value.checked_add(u64::from(voxel[1])))
        .and_then(|value| value.checked_mul(u64::from(width)))
        .and_then(|value| value.checked_add(u64::from(voxel[0])))
        .ok_or_else(|| GalError::invalid_argument("terrain voxel index overflows"))?;
    usize::try_from(row)
        .map_err(|_| GalError::invalid_argument("terrain voxel index exceeds address space"))
}

pub(super) fn changed_bounds(
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
        extent: crate::render::shaderpack::voxels::light_volume::VoxelLightVolumeExtent {
            width: max[0] - min[0] + 1,
            height: max[1] - min[1] + 1,
            depth: max[2] - min[2] + 1,
        },
    })
}

pub(super) fn copy_region_bytes(
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

pub(super) fn count_changed(old: &[u8], next: &[u8]) -> u32 {
    u32::try_from(
        old.iter()
            .zip(next)
            .filter(|(left, right)| left != right)
            .count(),
    )
    .unwrap_or(u32::MAX)
}

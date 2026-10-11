//! Frozen DH's complete column occlusion operation over native point storage.
use super::{mutable::Column, materials::Materials};
use std::slice;
fn bottom(point: u64) -> i32 { ((point >> 44) & 4095) as i32 }
fn height(point: u64) -> i32 { ((point >> 32) & 4095) as i32 }

#[inline(always)]
fn coverage(center: u64, adjacent: &[u64], mut index: i32, transparent: &impl Fn(u64) -> bool) -> i32 {
    if adjacent.is_empty() || adjacent.len() == 1 && adjacent[0] == 0 { return !index; }
    let (low, high) = (bottom(center), bottom(center) + height(center));
    while index >= 0 {
        let point = adjacent[index as usize];
        let top = bottom(point) + height(point);
        if top <= low { index -= 1; continue; }
        if transparent(point) { return !index; }
        if top >= high { return index; }
        index -= 1;
    }
    // Preserve the production index/bitwise-negation convention, including
    // exhaustion at -1. Do not reinterpret suspected Frozen behavior.
    !index
}
#[inline]
pub(crate) fn cull(center: &mut Vec<u64>, adjacent: [&[u64]; 4], transparent: &impl Fn(u64) -> bool) -> bool {
    if adjacent.iter().any(|c| c.is_empty()) { return false; }
    let mut indices = adjacent.map(|c| c.len() as i32 - 1);
    let mut changed = false;
    for index in (0..center.len()).rev() {
        let point = center[index];
        if transparent(point) || index == 0
            || transparent(center[index - 1])
            || index + 1 == center.len()
            || transparent(center[index + 1]) { continue; }
        let mut covered = true;
        for direction in 0..4 {
            indices[direction] = coverage(point, adjacent[direction], indices[direction], transparent);
            if indices[direction] < 0 {
                indices[direction] = !indices[direction]; covered = false; break;
            }
        }
        if covered {
            center.remove(index);
            let above = center[index - 1];
            // Exact production setters: the inserted value is not masked.
            let above = (above & !(4095u64 << 44)) | ((bottom(point) as u64) << 44);
            center[index - 1] = (above & !(4095u64 << 32)) | (((height(point) + height(above)) as u64) << 32);
            changed = true;
        }
    }
    changed
}

/// Neighbors are four borrowed pointer/count pairs. Decline aliases or malformed
/// IDs before mutation so compatibility still owns its original callback order.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_cull(owner: *mut Column, neighbors: *const usize, materials: *const Materials) -> i32 {
    if owner.is_null() || materials.is_null() || neighbors.is_null() || neighbors as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    let raw = slice::from_raw_parts(neighbors, 8);
    let previous = (*owner).points();
    let (start,end) = (*owner).allocation().unwrap_or((0,0));
    let mut adjacent = [&[][..]; 4];
    for i in 0..4 {
        let (pointer, count) = (raw[i * 2], raw[i * 2 + 1]);
        if count == 0 { return 1; }
        if pointer == 0 || pointer % 8 != 0 || count > i32::MAX as usize
            || pointer < end && pointer.saturating_add(count * 8) > start { return 0; }
        adjacent[i] = slice::from_raw_parts(pointer as *const u64, count);
    }
    let facts = &(*materials).0;
    if previous.iter().chain(adjacent.iter().flat_map(|c| c.iter())).any(|p| *p as u32 as usize >= facts.len()) { return 0; }
    if cull(Column::points_mut(owner), adjacent, &|point| facts[point as u32 as usize].opacity < 16) { 2 } else { 1 }
}

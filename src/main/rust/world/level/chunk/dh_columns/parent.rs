//! Frozen's cross-detail source updates, committed straight into the recipient
//! table: `updateFromOneBelowDetailLevel` (four child cells reduce into one
//! parent cell) and `downsampleFromOneAboveDetailLevel` (one parent cell
//! projects onto four child cells). Each runs as one call per source update.
use super::{contributors::capture, materials::Materials, reduction::{reduce, MissingId}, source::Source};
use std::sync::Arc;

/// Source updates hold at most this many footprints (`MAX_SEMANTIC_HORIZONTAL_CONTRIBUTOR_CELLS`).
const FOOTPRINT_CELLS: usize = 1024;
/// Each footprint column keeps this many points (`MAX_SEMANTIC_HORIZONTAL_CONTRIBUTOR_POINTS`).
const FOOTPRINT_POINTS: usize = 256;
const DOWN_SAMPLED: i8 = -1;
const MERGE_SAME_BLOCKS: i8 = 0;

/// Why an update stopped. Cells before the failing one keep their changes, as
/// Java's loops do when they throw.
#[derive(Debug, PartialEq)]
pub(crate) enum Failure {
    /// A sampled material ID has no mapping entry (`getBlockStateWrapper` throws).
    Material(usize),
    /// A point ID is outside the remap (`FullDataPointUtil.remap` throws).
    Remap(usize),
    /// An input column has an invalid (negative) logical size.
    InvalidColumn(usize),
}
impl From<MissingId> for Failure { fn from(id: MissingId) -> Self { Failure::Material(id.0) } }

/// `FullDataPointUtil.remap`: replace the low 32 bits with the signed new ID.
fn remap_point(point: u64, remap: &[i32]) -> Result<u64, Failure> {
    let id = *remap.get(point as u32 as usize).ok_or(Failure::Remap(point as u32 as usize))?;
    Ok(point & !(u32::MAX as u64) | id as i64 as u64)
}
fn remap_column(points: &[u64], remap: &[i32]) -> Result<Vec<u64>, Failure> {
    let mut output = Vec::with_capacity(points.len());
    for &point in points { output.push(remap_point(point, remap)?); }
    Ok(output)
}
fn hash(points: &[u64]) -> i32 {
    points.iter().fold(1i32, |h, &p| h.wrapping_mul(31).wrapping_add((p ^ (p >> 32)) as i32))
}
/// Java `LongList.hashCode` of the recipient's current column, or `None` when
/// it differs in size from `len` (or is missing/invalid), which Java treats as a change.
unsafe fn same_size_hash(source: &Source, index: usize, len: usize) -> Option<i32> {
    if !source.present(index) { return None; }
    let points = source.column(index)?;
    (points.len() == len).then(|| hash(points))
}
unsafe fn input_column(source: &Source, index: usize) -> Result<Option<&[u64]>, Failure> {
    if !source.present(index) { return Ok(None); }
    source.column(index).map(Some).ok_or(Failure::InvalidColumn(index))
}

/// `semanticallyUniformTwoByTwo`: all four cells keep MERGE_SAME_BLOCKS
/// compression and a uniform proof, and every column has the same geometry and
/// remapped material per point. Lighting is ignored.
fn uniform(columns: [Option<&[u64]>; 4], compression: [i8; 4], proven: [bool; 4], remap: &[i32]) -> bool {
    if compression.iter().any(|&c| c != MERGE_SAME_BLOCKS) || proven.iter().any(|&u| !u) { return false; }
    let reference = columns[0];
    columns.into_iter().all(|column| match (reference, column) {
        (None, None) => true,
        (Some(a), Some(b)) if a.len() == b.len() => a.iter().zip(b).all(|(&a, &b)| {
            a & 0x00ff_ffff_0000_0000 == b & 0x00ff_ffff_0000_0000
                && remap.get(a as u32 as usize).zip(remap.get(b as u32 as usize)).is_some_and(|(a, b)| a == b)
        }),
        _ => false,
    })
}

/// Reduce the child `input` into its quadrant (`offset`, in cells) of `target`.
/// Returns whether point data changed, by Frozen's global rule: a missing or
/// resized recipient column, or else a different list hash.
pub(crate) unsafe fn update_parent(target: &mut Source, input: &Source, materials: &Materials, remap: &[i32],
    offset: (usize, usize)) -> Result<bool, Failure> {
    let (generation, compression, proofs) = (input.metadata.plane(0), input.metadata.plane(1), input.metadata.plane(2));
    let mut changed = false;
    for x in (0..64).step_by(2) {
        for z in (0..64).step_by(2) {
            let recipient = (x / 2 + offset.0) * 64 + z / 2 + offset.1;
            let cells = [x * 64 + z, x * 64 + z + 1, (x + 1) * 64 + z, (x + 1) * 64 + z + 1];
            target.metadata.plane_mut(0)[recipient] = cells.map(|i| generation[i]).into_iter().min().unwrap();
            target.metadata.plane_mut(1)[recipient] = cells.map(|i| compression[i]).into_iter().max().unwrap();
            let mut columns = [None; 4];
            for (slot, &cell) in cells.iter().enumerate() { columns[slot] = input_column(input, cell)?; }
            let reduced = reduce(columns.map(|c| c.unwrap_or(&[])), &materials.0)?;
            let points = remap_column(&reduced, remap)?;
            let is_uniform = uniform(columns, cells.map(|i| compression[i]), cells.map(|i| proofs[i] != 0), remap);
            target.metadata.plane_mut(2)[recipient] = i8::from(is_uniform);
            target.set_footprint(recipient, None);
            if !is_uniform && target.footprint_count() < FOOTPRINT_CELLS {
                let snapshot = capture(columns, Some(remap), FOOTPRINT_POINTS).ok_or_else(|| first_bad_id(columns, remap))?;
                target.set_footprint(recipient, Some(Arc::new(snapshot)));
            }
            if !changed { changed = same_size_hash(target, recipient, points.len()) != Some(hash(&points)); }
            let column_hash = hash(&points);
            target.replace(recipient, points, column_hash);
        }
    }
    Ok(changed)
}

/// Project the coarser `input` onto `target`, whose cells occupy the input
/// quadrant at `offset`. Columns are written only where the recipient holds
/// no data (missing, or only empty points); each gets a once-remapped copy of
/// its input cell, so the input source is never changed.
pub(crate) unsafe fn downsample(target: &mut Source, input: &Source, remap: &[i32], offset: (usize, usize),
    replaced: &mut [u64; 64]) -> Result<bool, Failure> {
    let (compression, proofs) = (input.metadata.plane(1), input.metadata.plane(2));
    let mut changed = false;
    for x in 0..64 {
        for z in 0..64 {
            let recipient = x * 64 + z;
            let cell = (x / 2 + offset.0) * 64 + z / 2 + offset.1;
            target.metadata.plane_mut(0)[recipient] = DOWN_SAMPLED;
            target.metadata.plane_mut(1)[recipient] = compression[cell];
            let is_uniform = proofs[cell] != 0 && compression[cell] == MERGE_SAME_BLOCKS;
            target.metadata.plane_mut(2)[recipient] = i8::from(is_uniform);
            target.set_footprint(recipient, None);
            if !is_uniform && target.footprint_count() < FOOTPRINT_CELLS {
                if let Some(footprint) = input.footprint(cell) {
                    let spans = footprint.spans();
                    let snapshot = capture(spans, Some(remap), FOOTPRINT_POINTS).ok_or_else(|| first_bad_id(spans, remap))?;
                    target.set_footprint(recipient, Some(Arc::new(snapshot)));
                }
            }
            // An invalid (negative-size) column iterates as empty in Java.
            let empty = !target.present(recipient) || target.column(recipient).is_none_or(|p| p.iter().all(|&p| p == 0));
            if empty {
                let points = remap_column(input_column(input, cell)?.unwrap_or(&[]), remap)?;
                let column_hash = hash(&points);
                target.replace(recipient, points, column_hash);
                replaced[recipient / 64] |= 1 << (recipient % 64);
                changed = true;
            }
        }
    }
    Ok(changed)
}
/// The ID that made a footprint capture fail; capture fails only on a bad remap.
fn first_bad_id(columns: [Option<&[u64]>; 4], remap: &[i32]) -> Failure {
    columns.iter().flatten().flat_map(|c| c.iter().take(FOOTPRINT_POINTS))
        .map(|&p| p as u32 as usize).find(|&id| id >= remap.len()).map_or(Failure::Remap(0), Failure::Remap)
}

/// Shared ABI: `out` receives `[changed, failure kind, failure value]` where the
/// kind is 0 none, 1 material ID, 2 remap ID, 3 invalid input column index.
unsafe fn finish(result: Result<bool, Failure>, out: *mut i64) -> i32 {
    let out = std::slice::from_raw_parts_mut(out, 3);
    match result {
        Ok(changed) => { out.copy_from_slice(&[i64::from(changed), 0, 0]); 1 }
        Err(failure) => {
            let (kind, value) = match failure { Failure::Material(v) => (1, v), Failure::Remap(v) => (2, v), Failure::InvalidColumn(v) => (3, v) };
            out.copy_from_slice(&[0, kind, value as i64]); -1
        }
    }
}
unsafe fn arguments<'a>(target: *mut Source, input: *const Source, remap: *const i32, count: i32,
    offset_x: i32, offset_z: i32, out: *mut i64) -> Option<(&'a mut Source, &'a Source, &'a [i32], (usize, usize))> {
    if target.is_null() || input.is_null() || target as *const Source == input || count < 0
        || count != 0 && (remap.is_null() || remap as usize % 4 != 0) || out.is_null() || out as usize % 8 != 0
        || ![0, 32].contains(&offset_x) || ![0, 32].contains(&offset_z) { return None; }
    let remap = if count == 0 { &[][..] } else { std::slice::from_raw_parts(remap, count as usize) };
    Some((&mut *target, &*input, remap, (offset_x as usize, offset_z as usize)))
}
/// One call for a whole child-to-parent update; returns 1, -1 on a failure
/// described in `out`, or 0 for malformed arguments.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_update_parent(target: *mut Source, input: *const Source, materials: *const Materials,
    remap: *const i32, count: i32, offset_x: i32, offset_z: i32, out: *mut i64) -> i32 {
    let (Some((target, input, remap, offset)), Some(materials)) =
        (arguments(target, input, remap, count, offset_x, offset_z, out), materials.as_ref()) else { return 0; };
    finish(update_parent(target, input, materials, remap, offset), out)
}
/// One call for a whole parent-to-child downsample; same results as above.
/// `replaced` (64 words) receives a bit per written cell.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_source_downsample(target: *mut Source, input: *const Source,
    remap: *const i32, count: i32, offset_x: i32, offset_z: i32, out: *mut i64, replaced: *mut u64) -> i32 {
    let Some((target, input, remap, offset)) = arguments(target, input, remap, count, offset_x, offset_z, out) else { return 0; };
    if replaced.is_null() || replaced as usize % 8 != 0 { return 0; }
    let replaced = &mut *(replaced as *mut [u64; 64]); *replaced = [0; 64];
    finish(downsample(target, input, remap, offset, replaced), out)
}

#[cfg(test)]
mod tests;

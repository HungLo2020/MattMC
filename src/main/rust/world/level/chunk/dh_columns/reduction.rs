//! Complete Frozen DH 2x2 reduction, directly from retained CPU point owners.
use super::{materials::{Fact, Materials}, mutable::Column};
fn bottom(p: u64) -> usize { ((p >> 44) & 4095) as usize }
fn height(p: u64) -> usize { ((p >> 32) & 4095) as usize }
fn material(values: [usize; 4], facts: &[Fact]) -> usize {
    let mut counts = [0; 4];
    for value in values {
        if !facts[value].air {
            counts[values.iter().position(|&v| v == value).unwrap()] += 1;
        }
    }
    let max = *counts.iter().max().unwrap();
    let fallback = values[counts.iter().position(|&c| c == max).unwrap()];
    values.into_iter().find(|&v| facts[v].liquid).unwrap_or(fallback)
}
/// Unsupported IDs/geometry or unencodable output decline before publication.
/// Java then preserves its original callbacks, corrupt-point warnings and skips.
pub(crate) fn reduce(columns: [&[u64]; 4], facts: &[Fact]) -> Option<Vec<u64>> {
    if columns.iter().all(|c| c.is_empty()) { return Some(Vec::new()); }
    if facts.is_empty() { return None; }
    // Valid DH transition coordinates are 0..=4096. A bounded bitset replaces
    // repeated Java linear contains scans without changing sorted transitions.
    let mut transitions = [0u64; 65];
    let (mut low, mut high) = (4096, 0);
    for column in columns {
        for &point in column {
            let (id, y, size) = (point as u32 as usize, bottom(point), height(point));
            if id >= facts.len() || y + size > 4096 || size == 0 && point != 0 { return None; }
            for edge in [y, y + size] {
                transitions[edge / 64] |= 1 << (edge % 64);
                low = low.min(edge); high = high.max(edge);
            }
        }
    }
    let mut indices = columns.map(|c| c.len() as isize - 1);
    let mut points = Vec::new();
    let mut last = None;
    let mut min_y = low;
    let mut accumulated = 0;
    let mut slice_min = low;
    let emit = |points: &mut Vec<u64>, (id, block, sky): (usize, u64, u64), y: usize, size: usize| -> Option<()> {
        if y >= 4096 || size == 0 || size >= 4096 || y + size > 4096 { return None; }
        points.push(id as u64 | (size as u64) << 32 | (y as u64) << 44 | sky << 56 | block << 60);
        Some(())
    };
    for slice_max in low + 1..=high {
        if transitions[slice_max / 64] & (1 << (slice_max % 64)) == 0 { continue; }
        let sample = slice_min + (slice_max - slice_min) / 2;
        let mut ids = [0; 4];
        let (mut block, mut sky) = (0, 0);
        for i in 0..4 {
            while indices[i] >= 0 {
                let p = columns[i][indices[i] as usize];
                let (y, top) = (bottom(p), bottom(p) + height(p));
                if sample >= top { indices[i] -= 1; }
                else {
                    if sample >= y { ids[i] = p as u32 as usize; block += (p >> 60) & 15; sky += (p >> 56) & 15; }
                    break;
                }
            }
        }
        let value = (material(ids, facts), block / 4, sky / 4);
        if let Some(previous) = last {
            if previous != value { emit(&mut points, previous, min_y, accumulated)?; min_y = slice_min; accumulated = 0; }
        }
        accumulated += slice_max - slice_min;
        last = Some(value); slice_min = slice_max;
    }
    if let Some(value) = last { emit(&mut points, value, min_y, accumulated)?; }
    points.reverse();
    Some(points)
}

/// Four pointer/count pairs, one native material lease, one result-owner slot.
/// 0 declines; 1 accepts empty without allocation; 2 transfers a new point owner.
#[no_mangle]
pub unsafe extern "C" fn mattmc_dh_column_reduce(inputs: *const usize, materials: *const Materials, output: *mut usize) -> i32 {
    if inputs.is_null() || materials.is_null() || output.is_null()
        || inputs as usize % std::mem::align_of::<usize>() != 0
        || output as usize % std::mem::align_of::<usize>() != 0 { return 0; }
    *output = 0;
    let raw = std::slice::from_raw_parts(inputs, 8);
    let mut columns = [&[][..]; 4];
    for i in 0..4 {
        let (p, n) = (raw[i * 2], raw[i * 2 + 1]);
        if n > i32::MAX as usize || n != 0 && (p == 0 || p % 8 != 0) { return 0; }
        if n != 0 { columns[i] = std::slice::from_raw_parts(p as *const u64, n); }
    }
    let Some(points) = reduce(columns, &(*materials).0) else { return 0; };
    if points.is_empty() { return 1; }
    *output = Box::into_raw(Box::new(Column(points))) as usize;
    2
}

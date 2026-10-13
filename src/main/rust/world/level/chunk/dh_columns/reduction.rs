//! Frozen DH's complete 2x2 column reduction (`mergeInputTwoByTwoDataColumn`).
use super::materials::Fact;
fn bottom(p: u64) -> usize { ((p >> 44) & 4095) as usize }
fn height(p: u64) -> usize { ((p >> 32) & 4095) as usize }
/// A material ID with no mapping entry. Java's `getBlockStateWrapper` throws here.
#[derive(Debug, PartialEq)]
pub(crate) struct MissingId(pub(crate) usize);
fn fact(facts: &[Fact], id: usize) -> Result<Fact, MissingId> { facts.get(id).copied().ok_or(MissingId(id)) }
/// `determineMostCommonValueInColumnSlice` then `selectWaterValueForColumnSlice`:
/// air never counts, ties keep the earliest value, and any liquid wins.
fn material(values: [usize; 4], facts: &[Fact]) -> Result<usize, MissingId> {
    let mut counts = [0; 4];
    for value in values {
        if !fact(facts, value)?.air {
            counts[values.iter().position(|&v| v == value).unwrap()] += 1;
        }
    }
    let max = *counts.iter().max().unwrap();
    let fallback = values[counts.iter().position(|&c| c == max).unwrap()];
    for value in values { if fact(facts, value)?.liquid { return Ok(value); } }
    Ok(fallback)
}
/// `FullDataPointUtil.encode` without validation (DH release builds): fields are masked.
fn encode(id: usize, size: usize, y: usize, block: u64, sky: u64) -> u64 {
    (id as u64 & 0x7fff_ffff) | ((size as u64 & 4095) << 32) | ((y as u64 & 4095) << 44) | (sky << 56) | (block << 60)
}
/// Bottom-to-top slices at every transition, sampled at each midpoint. Missing
/// and empty inputs contribute nothing. IDs are looked up only for sampled
/// slices, as in Java, so an unused bad ID in an input is not an error.
pub(crate) fn reduce(columns: [&[u64]; 4], facts: &[Fact]) -> Result<Vec<u64>, MissingId> {
    // Transition edges are bottom + height, at most 4095 + 4095.
    let mut transitions = [0u64; 128];
    let (mut low, mut high, mut any) = (usize::MAX, 0, false);
    for column in columns {
        for &point in column {
            let (y, size) = (bottom(point), height(point));
            for edge in [y, y + size] {
                transitions[edge / 64] |= 1 << (edge % 64);
                low = low.min(edge); high = high.max(edge); any = true;
            }
        }
    }
    let mut points = Vec::new();
    if !any { return Ok(points); }
    let mut indices = columns.map(|c| c.len() as isize - 1);
    let mut last = None;
    let (mut min_y, mut accumulated, mut slice_min) = (low, 0, low);
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
        let value = (material(ids, facts)?, block / 4, sky / 4);
        if let Some(previous) = last {
            if previous != value {
                let (id, block, sky) = previous;
                points.push(encode(id, accumulated, min_y, block, sky));
                min_y = slice_min; accumulated = 0;
            }
        }
        accumulated += slice_max - slice_min;
        last = Some(value); slice_min = slice_max;
    }
    if let Some((id, block, sky)) = last { points.push(encode(id, accumulated, min_y, block, sky)); }
    // `ensureDataColumnOrder`: reverse into top-down order only when the first
    // (masked) bottom is below the last one.
    if points.len() >= 2 && bottom(points[0]) < bottom(points[points.len() - 1]) { points.reverse(); }
    Ok(points)
}

//! Prove a full grid or a full grid with one strictly interior rectangular void.
//! The six boxes below are the exact original y/x/z greedy decomposition.
//! This is occupancy-derived, with no input/result cache or coordinate assumptions.
use super::extract::next;

#[inline]
fn mask(bits: usize) -> u64 {
    if bits == 64 { u64::MAX } else { (1u64 << bits) - 1 }
}

#[inline]
fn equal(words: &[u64], expected: u64) -> bool {
    words.iter().fold(0, |difference, &word| difference | (word ^ expected)) == 0
}

pub(super) fn visit<F: FnMut([i32; 6])>(words: &[u64], dims: [usize; 3], emit: &mut F) -> Option<usize> {
    let [nx, ny, nz] = dims;
    let cells = nx * ny * nz;
    if cells == 0 { return Some(0); }
    let first = next(words, 0, cells, false);
    if first == cells {
        emit([0, 0, 0, nx as i32, ny as i32, nz as i32]);
        return Some(1);
    }
    // A strictly interior cavity requires a complete first X slab. Most
    // arbitrary occupancy declines after the first word, before divisions.
    if first < ny * nz || !nz.is_power_of_two() || nz > 64 { return None; }
    let rows = 64 / nz;
    if ny % rows != 0 { return None; }
    let low = [first / (ny * nz), first / nz % ny, first % nz];
    if low.iter().any(|&v| v == 0) { return None; }
    let last_word = words.iter().rposition(|&word| word != u64::MAX)?;
    let last = last_word * 64 + 63 - (!words[last_word]).leading_zeros() as usize;
    let high = [last / (ny * nz) + 1, last / nz % ny + 1, last % nz + 1];
    if (0..3).any(|a| high[a] >= dims[a] || low[a] >= high[a]) { return None; }
    let groups = ny / rows;
    let first_group = low[1] / rows;
    let last_group = (high[1] - 1) / rows;
    let pattern = (mask(high[2] - low[2]) << low[2]) * (u64::MAX / mask(nz));
    let row_mask = |group: usize| {
        let begin = low[1].saturating_sub(group * rows);
        let end = (high[1] - group * rows).min(rows);
        !(pattern & mask(end * nz) & !mask(begin * nz))
    };
    for x in 0..nx {
        let slab = &words[x * groups..(x + 1) * groups];
        if x < low[0] || x >= high[0] {
            if !equal(slab, u64::MAX) { return None; }
        } else if !equal(&slab[..first_group], u64::MAX)
            || slab[first_group] != row_mask(first_group)
            || !equal(&slab[last_group + 1..], u64::MAX)
            || (last_group != first_group &&
                (!equal(&slab[first_group + 1..last_group], !pattern)
                    || slab[last_group] != row_mask(last_group))) {
            return None;
        }
    }
    let [x, y, z] = low.map(|v| v as i32);
    let [xx, yy, zz] = high.map(|v| v as i32);
    let [nx, ny, nz] = dims.map(|v| v as i32);
    for box_ in [[0, 0, 0, nx, y, nz], [0, y, 0, x, ny, nz],
        [x, y, 0, nx, ny, z], [x, y, zz, nx, ny, nz],
        [xx, y, z, nx, ny, zz], [x, yy, z, xx, ny, zz]] { emit(box_); }
    Some(6)
}

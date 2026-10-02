#[inline(always)]
fn mask(n: usize) -> u64 {
    if n == 64 { u64::MAX } else { (1u64 << n) - 1 }
}

// Both searches stop at the exclusive row/range limit, including partial words.
#[inline(always)]
fn next(words: &[u64], mut at: usize, end: usize, set: bool) -> usize {
    while at < end {
        let shift = at & 63;
        let count = (64 - shift).min(end - at);
        let word = if set { words[at >> 6] } else { !words[at >> 6] };
        let bits = (word >> shift) & mask(count);
        if bits != 0 { return at + bits.trailing_zeros() as usize; }
        at += count;
    }
    end
}

#[inline(always)]
fn clear(words: &mut [u64], mut at: usize, end: usize) {
    while at < end {
        let shift = at & 63;
        let count = (64 - shift).min(end - at);
        words[at >> 6] &= !(mask(count) << shift);
        at += count;
    }
}

#[inline(always)]
fn strip_full(words: &[u64], mut at: usize, end: usize) -> bool {
    while at < end {
        let shift = at & 63;
        let count = (64 - shift).min(end - at);
        let expected = mask(count);
        if (words[at >> 6] >> shift) & expected != expected { return false; }
        at += count;
    }
    true
}

#[inline(always)]
fn emit_run(words: &mut [u64], dims: [usize; 3], x: usize, y: usize,
    start: usize, end: usize, output: &mut [i32], count: usize) {
    let [nx, ny, nz] = dims;
    let base = (x * ny + y) * nz;
    clear(words, base + start, base + end);
    let mut x_end = x + 1;
    while x_end < nx {
        let row = (x_end * ny + y) * nz;
        if !strip_full(words, row + start, row + end) { break; }
        clear(words, row + start, row + end);
        x_end += 1;
    }
    let mut y_end = y + 1;
    while y_end < ny {
        if !(x..x_end).all(|xx| {
            let row = (xx * ny + y_end) * nz;
            strip_full(words, row + start, row + end)
        }) { break; }
        for xx in x..x_end {
            let row = (xx * ny + y_end) * nz;
            clear(words, row + start, row + end);
        }
        y_end += 1;
    }
    output[count * 6..count * 6 + 6].copy_from_slice(&[
        x as i32, y as i32, start as i32, x_end as i32, y_end as i32, end as i32,
    ]);
}

#[inline(always)]
pub(super) fn row_bits(words: &[u64], base: usize, nz: usize) -> u64 {
    let shift = base & 63;
    let mut bits = words[base >> 6] >> shift;
    if nz + shift > 64 { bits |= words[(base >> 6) + 1] << (64 - shift); }
    bits & mask(nz)
}

/// Same y/x/z traversal, then Z run, X expansion, Y expansion as Java.
/// `words` is a private snapshot; output contains six ordered endpoints per box.
pub(super) fn extract(words: &mut [u64], dims: [usize; 3], output: &mut [i32]) -> usize {
    let [nx, ny, nz] = dims;
    if let Some(count) = super::isolated::extract(words, dims, output) {
        clear(words, 0, nx * ny * nz);
        return count;
    }
    let mut count = 0;
    for y in 0..ny {
        for x in 0..nx {
            let base = (x * ny + y) * nz;
            if nz <= 64 {
                // Expansion touches only other rows. The current row's local mask
                // therefore changes only by clearing the run just emitted.
                let mut bits = row_bits(words, base, nz);
                while bits != 0 {
                    let start = bits.trailing_zeros() as usize;
                    let end = start + (bits >> start).trailing_ones() as usize;
                    emit_run(words, dims, x, y, start, end, output, count);
                    count += 1;
                    bits &= !(mask(end - start) << start);
                }
            } else {
                let mut z = 0;
                while z < nz {
                    let start = next(words, base + z, base + nz, true) - base;
                    if start == nz { break; }
                    let end = next(words, base + start, base + nz, false) - base;
                    emit_run(words, dims, x, y, start, end, output, count);
                    count += 1;
                    z = end;
                }
            }
        }
    }
    count
}

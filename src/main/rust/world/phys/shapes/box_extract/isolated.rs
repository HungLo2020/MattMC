use super::extract::row_bits;

/// Any merge requires a face-adjacent pair. When none exists, every original
/// box is one cell, emitted in y/x/z order. This checks actual occupancy, not a
/// fixture name or density heuristic, and writes nothing when it declines.
pub(super) fn extract(words: &[u64], dims: [usize; 3], output: &mut [i32]) -> Option<usize> {
    let [nx, ny, nz] = dims;
    if nz > 64 { return None; }
    for x in 0..nx {
        for y in 0..ny {
            let row = (x * ny + y) * nz;
            let bits = row_bits(words, row, nz);
            if bits == 0 { continue; }
            if bits & (bits >> 1) != 0
                || (x + 1 < nx && bits & row_bits(words, row + ny * nz, nz) != 0)
                || (y + 1 < ny && bits & row_bits(words, row + nz, nz) != 0)
            { return None; }
        }
    }
    let mut count = 0;
    for y in 0..ny {
        for x in 0..nx {
            let mut bits = row_bits(words, (x * ny + y) * nz, nz);
            while bits != 0 {
                let z = bits.trailing_zeros() as i32;
                output[count * 6..count * 6 + 6].copy_from_slice(&[
                    x as i32, y as i32, z, x as i32 + 1, y as i32 + 1, z + 1,
                ]);
                count += 1;
                bits &= bits - 1;
            }
        }
    }
    Some(count)
}

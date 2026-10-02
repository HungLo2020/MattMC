/// Map each occupied source cell by the supplied permutation and reflections.
/// Source padding is ignored; bounds are rebuilt from actual logical occupancy.
pub(super) fn transform(input: &[u64], dims: [usize; 3], axes: [usize; 3], flips: u32,
    output: &mut [u64], bounds: &mut [i32]) {
    let target = axes.map(|a| dims[a]);
    output.fill(0);
    let strides = [target[1] * target[2], target[2], 1];
    let mut step = [0isize; 3];
    let mut offset = 0isize;
    for t in 0..3 {
        let invert = flips & (1 << t) != 0;
        step[axes[t]] = if invert { -(strides[t] as isize) } else { strides[t] as isize };
        if invert { offset += ((target[t] - 1) * strides[t]) as isize; }
    }
    let mut min = dims;
    let mut max = [0usize; 3];
    for x in 0..dims[0] {
        for y in 0..dims[1] {
            let row = (x * dims[1] + y) * dims[2];
            let base = offset + x as isize * step[0] + y as isize * step[1];
            let mut z = 0;
            while z < dims[2] {
                let at = row + z;
                let shift = at & 63;
                let count = (64 - shift).min(dims[2] - z);
                let mask = u64::MAX >> (64 - count);
                let mut bits = (input.get(at >> 6).copied().unwrap_or(0) >> shift) & mask;
                if bits != 0 {
                    min[0] = min[0].min(x); min[1] = min[1].min(y);
                    max[0] = max[0].max(x + 1); max[1] = max[1].max(y + 1);
                    min[2] = min[2].min(z + bits.trailing_zeros() as usize);
                    max[2] = max[2].max(z + 64 - bits.leading_zeros() as usize);
                }
                while bits != 0 {
                    let zz = z + bits.trailing_zeros() as usize;
                    let dest = (base + zz as isize * step[2]) as usize;
                    output[dest >> 6] |= 1 << (dest & 63);
                    bits &= bits - 1;
                }
                z += count;
            }
        }
    }
    for t in 0..3 {
        let a = axes[t];
        if flips & (1 << t) != 0 {
            bounds[t] = (target[t] - max[a]) as i32;
            bounds[t + 3] = (target[t] - min[a]) as i32;
        } else {
            bounds[t] = min[a] as i32;
            bounds[t + 3] = max[a] as i32;
        }
    }
    // The original fresh result retains dimension minima and zero maxima when empty.
    if max == [0; 3] {
        for t in 0..3 { bounds[t] = target[t] as i32; bounds[t + 3] = 0; }
    }
}

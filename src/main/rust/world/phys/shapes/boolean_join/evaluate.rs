fn word_op(a: u64, b: u64, truth: u32) -> u64 {
    let mut value = 0;
    if truth & 1 != 0 {
        value |= !a & !b;
    }
    if truth & 2 != 0 {
        value |= !a & b;
    }
    if truth & 4 != 0 {
        value |= a & !b;
    }
    if truth & 8 != 0 {
        value |= a & b;
    }
    value
}

fn base(x: i32, y: i32, dims: &[i32]) -> Option<usize> {
    if x < 0 || x >= dims[0] || y < 0 || y >= dims[1] {
        None
    } else {
        Some((x as usize * dims[1] as usize + y as usize) * dims[2] as usize)
    }
}

fn full(words: &[u64], base: Option<usize>, z: i32, z_size: i32) -> bool {
    if z < 0 || z >= z_size {
        return false;
    }
    match base {
        Some(base) => {
            let at = base + z as usize;
            words.get(at / 64).copied().unwrap_or(0) & (1u64 << (at % 64)) != 0
        }
        None => false,
    }
}

fn low_mask(count: usize) -> u64 {
    if count == 64 {
        u64::MAX
    } else {
        (1u64 << count) - 1
    }
}

fn uniform(words: &[u64], sizes: &[i32], maps: [&[i32]; 3], side: usize) -> Option<bool> {
    let count = sizes.iter().map(|&n| n as usize).product::<usize>();
    if count == 0 {
        return Some(false);
    }
    let mut empty = true;
    let mut filled = true;
    for at in (0..count).step_by(64) {
        let mask = low_mask(64.min(count - at));
        let value = words.get(at / 64).copied().unwrap_or(0) & mask;
        empty &= value == 0;
        filled &= value == mask;
        if !empty && !filled {
            return None;
        }
    }
    if empty {
        return Some(false);
    }
    if filled
        && (0..3).all(|axis| {
            maps[axis]
                .chunks_exact(2)
                .all(|pair| pair[side] >= 0 && pair[side] < sizes[axis])
        })
    {
        return Some(true);
    }
    None
}

fn constant(truth: u32, a: Option<bool>, b: Option<bool>) -> Option<bool> {
    let value = |a: bool, b: bool| truth & (1 << ((a as u32) * 2 + b as u32)) != 0;
    match (a, b) {
        (Some(a), Some(b)) => Some(value(a, b)),
        (Some(a), None) if value(a, false) == value(a, true) => Some(value(a, false)),
        (None, Some(b)) if value(false, b) == value(true, b) => Some(value(false, b)),
        _ => None,
    }
}

fn row_word(words: &[u64], base: Option<usize>, z: usize, size: usize, count: usize) -> u64 {
    if z >= size {
        return 0;
    }
    match base {
        Some(base) => {
            let at = base + z;
            let shift = at % 64;
            let mut value = words.get(at / 64).copied().unwrap_or(0) >> shift;
            if shift != 0 {
                value |= words.get(at / 64 + 1).copied().unwrap_or(0) << (64 - shift);
            }
            value & low_mask(count.min(size - z))
        }
        None => 0,
    }
}

pub(super) fn join(
    a: &[u64],
    b: &[u64],
    sizes: &[i32],
    maps: &[i32],
    dims: [usize; 3],
    truth: u32,
    out: &mut [u64],
    bounds: &mut [i32],
) {
    let [nx, ny, nz] = dims;
    let x_map = &maps[..nx * 2];
    let y_map = &maps[nx * 2..(nx + ny) * 2];
    let z_map = &maps[(nx + ny) * 2..];
    let identical = (0..3)
        .all(|axis| sizes[axis] == dims[axis] as i32 && sizes[axis + 3] == dims[axis] as i32)
        && [x_map, y_map, z_map].iter().all(|map| {
            map.chunks_exact(2)
                .enumerate()
                .all(|(i, pair)| pair[0] == i as i32 && pair[1] == i as i32)
        });
    out.fill(0);
    let maps_by_axis = [x_map, y_map, z_map];
    let uniform_a = uniform(a, &sizes[..3], maps_by_axis, 0);
    let uniform_b = uniform(b, &sizes[3..], maps_by_axis, 1);
    if let Some(value) = constant(truth, uniform_a, uniform_b) {
        out.fill(if value { u64::MAX } else { 0 });
        let tail = nx * ny * nz % 64;
        if tail != 0 {
            *out.last_mut().unwrap() &= low_mask(tail);
        }
    } else if identical {
        for (i, value) in out.iter_mut().enumerate() {
            *value = word_op(
                a.get(i).copied().unwrap_or(0),
                b.get(i).copied().unwrap_or(0),
                truth,
            );
        }
        let tail = nx * ny * nz % 64;
        if tail != 0 {
            *out.last_mut().unwrap() &= (1u64 << tail) - 1;
        }
    } else if z_map
        .chunks_exact(2)
        .enumerate()
        .all(|(i, pair)| pair[0] == i as i32 && pair[1] == i as i32)
    {
        // Different X/Y grids with consecutive Z coordinates still join packed
        // row segments. Clip each source row independently before applying the op.
        for x in 0..nx {
            for y in 0..ny {
                let a_base = base(x_map[x * 2], y_map[y * 2], &sizes[..3]);
                let b_base = base(x_map[x * 2 + 1], y_map[y * 2 + 1], &sizes[3..]);
                let start = (x * ny + y) * nz;
                let mut z = 0;
                while z < nz {
                    let at = start + z;
                    let count = (64 - at % 64).min(nz - z);
                    let av = row_word(a, a_base, z, sizes[2] as usize, count);
                    let bv = row_word(b, b_base, z, sizes[5] as usize, count);
                    out[at / 64] |= (word_op(av, bv, truth) & low_mask(count)) << (at % 64);
                    z += count;
                }
            }
        }
    } else {
        for x in 0..nx {
            for y in 0..ny {
                let a_base = base(x_map[x * 2], y_map[y * 2], &sizes[..3]);
                let b_base = base(x_map[x * 2 + 1], y_map[y * 2 + 1], &sizes[3..]);
                let out_base = (x * ny + y) * nz;
                for z in 0..nz {
                    let av = full(a, a_base, z_map[z * 2], sizes[2]);
                    let bv = full(b, b_base, z_map[z * 2 + 1], sizes[5]);
                    if truth & (1 << ((av as u32) * 2 + bv as u32)) != 0 {
                        let at = out_base + z;
                        out[at / 64] |= 1 << (at % 64);
                    }
                }
            }
        }
    }
    bounds[..3].fill(i32::MAX);
    bounds[3..].fill(i32::MIN);
    // Scan rows by packed-word segments, preserving the original empty sentinels.
    for x in 0..nx {
        for y in 0..ny {
            let start = (x * ny + y) * nz;
            let mut z = 0;
            let mut row = false;
            while z < nz {
                let at = start + z;
                let count = (64 - at % 64).min(nz - z);
                let mask = if count == 64 {
                    u64::MAX
                } else {
                    (1u64 << count) - 1
                };
                let value = (out[at / 64] >> (at % 64)) & mask;
                if value != 0 {
                    bounds[2] = bounds[2].min((z + value.trailing_zeros() as usize) as i32);
                    bounds[5] = bounds[5].max((z + 63 - value.leading_zeros() as usize) as i32);
                    row = true;
                }
                z += count;
            }
            if row {
                bounds[0] = bounds[0].min(x as i32);
                bounds[3] = bounds[3].max(x as i32);
                bounds[1] = bounds[1].min(y as i32);
                bounds[4] = bounds[4].max(y as i32);
            }
        }
    }
    for value in &mut bounds[3..] {
        *value += 1;
    }
}

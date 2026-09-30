//! Fiddled-distance biome corner selection for a bounded column.
use std::slice;
const MAX_COLUMN: usize = 4096;

#[inline]
fn next(seed: i64, salt: i64) -> i64 {
    seed.wrapping_mul(
        seed.wrapping_mul(6364136223846793005)
            .wrapping_add(1442695040888963407),
    )
    .wrapping_add(salt)
}
fn jitter(seed: i64, x: i32, y: i32, z: i32) -> [f64; 3] {
    let mut s = seed;
    for v in [x, y, z, x, y, z] {
        s = next(s, v as i64);
    }
    let mut result = [0.; 3];
    for (i, v) in result.iter_mut().enumerate() {
        if i != 0 {
            s = next(s, seed);
        }
        *v = (((s >> 24) & 1023) as f64 / 1024. - 0.5) * 0.9;
    }
    result
}

/// # Safety
/// `out` is writable and aligned for `count` i32 values, exclusively borrowed.
pub(crate) unsafe fn surface_biomes(
    seed: i64,
    x: i32,
    z: i32,
    min_y: i32,
    count: i32,
    out: *mut i32,
) -> i32 {
    if count <= 0 || count as usize > MAX_COLUMN || out.is_null() || out as usize % 4 != 0 {
        return -1;
    }
    let out = unsafe { slice::from_raw_parts_mut(out, count as usize) };
    let bx = x.wrapping_sub(2);
    let bz = z.wrapping_sub(2);
    let qx = bx >> 2;
    let qz = bz >> 2;
    let base = min_y.wrapping_sub(2) >> 2;
    let height = (min_y.wrapping_add(count - 1).wrapping_sub(2) >> 2)
        .wrapping_sub(base)
        .wrapping_add(2);
    let dx = (bx & 3) as f64 / 4.;
    let dz = (bz & 3) as f64 / 4.;
    let mut last = i32::MIN;
    let mut offsets = [[0.; 3]; 8];
    for (i, result) in out.iter_mut().enumerate() {
        let by = min_y.wrapping_add(i as i32).wrapping_sub(2);
        let qy = by >> 2;
        if qy != last {
            for (p, offset) in offsets.iter_mut().enumerate() {
                *offset = jitter(
                    seed,
                    qx.wrapping_add((p >> 2) as i32),
                    qy.wrapping_add(((p >> 1) & 1) as i32),
                    qz.wrapping_add((p & 1) as i32),
                );
            }
            last = qy;
        }
        let dy = (by & 3) as f64 / 4.;
        let mut best = f64::INFINITY;
        let mut winner = 0;
        for (p, offset) in offsets.iter().enumerate() {
            let xx = dx - (p >> 2) as f64 + offset[0];
            let yy = dy - ((p >> 1) & 1) as f64 + offset[1];
            let zz = dz - (p & 1) as f64 + offset[2];
            let distance = zz * zz + yy * yy + xx * xx;
            if best > distance {
                best = distance;
                winner = p;
            }
        }
        *result = (((winner >> 2) * 2 + (winner & 1)) as i32)
            .wrapping_mul(height)
            .wrapping_add(
                qy.wrapping_add(((winner >> 1) & 1) as i32)
                    .wrapping_sub(base),
            );
    }
    0
}

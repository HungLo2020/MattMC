//! Bounded cell material loop. The density array is the already-filled Java
//! CacheAllInCell in descending Y, X, Z order. Only built-in pure sources use
//! this path; cache misses suspend before visiting the next block.
use super::super::synth::{noise_eval, State};
use super::{decision, nearest};

pub(super) fn centers(
    x: i32,
    y: i32,
    z: i32,
    grid: &[i64],
    shape: &[i32],
    out: &mut [[i32; 4]; 12],
) -> bool {
    let gx = x.wrapping_sub(5) >> 4;
    let gy = y.wrapping_add(1).div_euclid(12);
    let gz = z.wrapping_sub(5) >> 4;
    let mut n = 0;
    for dx in 0..2 {
        for dy in -1..2 {
            for dz in 0..2 {
                let rx = gx.wrapping_add(dx).wrapping_sub(shape[0]);
                let ry = gy.wrapping_add(dy).wrapping_sub(shape[1]);
                let rz = gz.wrapping_add(dz).wrapping_sub(shape[2]);
                let index = (ry as i64 * shape[4] as i64 + rz as i64) * shape[3] as i64 + rx as i64;
                if rx < 0
                    || rx >= shape[3]
                    || ry < 0
                    || rz < 0
                    || rz >= shape[4]
                    || index < 0
                    || index >= grid.len() as i64
                {
                    return false;
                }
                let pos = grid[index as usize];
                if pos == i64::MAX {
                    return false;
                }
                out[n] = [
                    index as i32,
                    (pos >> 38) as i32,
                    ((pos << 52) >> 52) as i32,
                    ((pos << 26) >> 38) as i32,
                ];
                n += 1;
            }
        }
    }
    true
}

/// Safety: barrier is null (uninitialized noise) or a live validated normal
/// noise state. All slice lengths and cell dimensions are checked by the ABI.
pub(super) unsafe fn materials(
    f: &mut [i32],
    values: &mut [f64],
    density: &[f64],
    out: &mut [i32],
    grid: &[i64],
    shape: &[i32],
    cache: &[i32],
    barrier: *const State,
    xz: f64,
    ys: f64,
) -> i32 {
    let width = f[21];
    let height = f[22];
    let total = width * width * height;
    if f[16] < 0 || f[16] > total {
        return -2;
    }
    let end = (f[16] + 128).min(total);
    let mut points = [[0; 4]; 12];
    let mut prepared: [Option<nearest::Prepared>; 4] = std::array::from_fn(|_| None);
    let mut prepared_y = None;
    let min_gx = f[18].wrapping_sub(5) >> 4;
    let min_gz = f[20].wrapping_sub(5) >> 4;
    let mut position = [
        (f[16] / width) % width,
        height - 1 - f[16] / (width * width),
        f[16] % width,
    ];
    while f[16] < end {
        let index = f[16];
        let y = f[19].wrapping_add(position[1]);
        let x = f[18].wrapping_add(position[0]);
        let z = f[20].wrapping_add(position[2]);
        if f[17] == 0 {
            f[13] = 0;
            f[12] = -1;
            if density[index as usize] > 0.0 {
                finish(f, out, &mut position, width);
                continue;
            }
            let (level, id, kind) = if f[30] != 0 {
                (f[31], f[14], 0)
            } else if y < f[24] {
                (f[25], f[26], 2)
            } else {
                (f[27], f[28], f[29])
            };
            let state = if y < level { id } else { f[14] };
            if y > f[23] || (y < level && kind == 2) {
                f[12] = state;
                finish(f, out, &mut position, width);
                continue;
            }
            let key = (
                x.wrapping_sub(5) >> 4,
                y.wrapping_add(1).div_euclid(12),
                z.wrapping_sub(5) >> 4,
            );
            if prepared_y != Some(key.1) {
                prepared = std::array::from_fn(|_| None);
                prepared_y = Some(key.1);
            }
            let cx = key.0.wrapping_sub(min_gx);
            let cz = key.2.wrapping_sub(min_gz);
            if !(0..2).contains(&cx) || !(0..2).contains(&cz) {
                return -3;
            }
            let slot = &mut prepared[(cx * 2 + cz) as usize];
            if slot.is_none() {
                if !centers(x, y, z, grid, shape, &mut points) {
                    return -3;
                }
                *slot = Some(nearest::Prepared::new(&points));
            }
            slot.as_ref().unwrap().point(x, y, z, &mut f[..8]);
            f[8] = 0;
            f[10] = y;
            f[15] = 0;
            let below = y.wrapping_sub(1);
            f[11] = (f[30] == 0
                && if below < f[24] {
                    below < f[25]
                } else {
                    f[29] == 2 && below < f[27]
                }) as i32;
            values[0] = density[index as usize];
            values[1] = f64::NAN;
            f[17] = 1;
        }
        loop {
            match decision::step(f, values, cache, false) {
                0 => break,
                3 => {
                    values[1] = if barrier.is_null() {
                        0.0
                    } else {
                        unsafe {
                            noise_eval(
                                barrier,
                                x as f64 * xz,
                                y as f64 * ys,
                                z as f64 * xz,
                                0.0,
                                0.0,
                                0,
                            )
                        }
                    };
                    f[15] = 1;
                }
                request => return request,
            }
        }
        if f[30] != 0 && f[12] != -1 {
            f[12] = f[14];
        }
        finish(f, out, &mut position, width);
    }
    if f[16] == total {
        0
    } else {
        4
    }
}
fn finish(f: &mut [i32], out: &mut [i32], position: &mut [i32; 3], width: i32) {
    position[2] += 1;
    if position[2] == width {
        position[2] = 0;
        position[0] += 1;
        if position[0] == width {
            position[0] = 0;
            position[1] -= 1;
        }
    }
    let offset = f[16] as usize * 2;
    out[offset] = f[12];
    out[offset + 1] = f[13];
    f[16] += 1;
    f[17] = 0;
}

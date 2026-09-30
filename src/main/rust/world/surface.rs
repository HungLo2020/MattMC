//! Surface column scanner and forward-only rule program. Borrowed buffers are
//! never retained. Java owns block commits and services lazy external requests;
//! yielding before those requests preserves observation of preceding writes.
use std::slice;

const STRIDE: usize = 8;
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
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_biomes(
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

/// # Safety
/// The program is a readable, aligned array of `len` i32 values. Header word 0
/// is the instruction area length; instructions start at word 8, and all jumps
/// are forward. Trailing data contains biome registry IDs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_validate(program: *const i32, len: i32) -> i32 {
    if program.is_null() || program as usize % 4 != 0 || len < 16 {
        return -1;
    }
    let p = unsafe { slice::from_raw_parts(program, len as usize) };
    let end = p[0] as usize;
    if end < 16 || end > p.len() || end % STRIDE != 0 {
        return -2;
    }
    for pc in (8..end).step_by(STRIDE) {
        let n = &p[pc..pc + STRIDE];
        if !(0..=11).contains(&n[0]) || n[6] < 0 || n[6] > 1 {
            return -3;
        }
        if ((2..=8).contains(&n[0]) || n[0] == 11)
            && (n[5] as usize <= pc || n[5] as usize > end || n[5] as usize % STRIDE != 0)
        {
            return -4;
        }
        if (n[0] == 7 || n[0] == 10)
            && (n[1] < end as i32
                || n[2] < 0
                || (n[0] == 10 && n[2] == 0)
                || n[1] as usize > p.len()
                || n[2] as usize > p.len() - n[1] as usize)
        {
            return -5;
        }
        if n[0] == 7
            && p[n[1] as usize..n[1] as usize + n[2] as usize]
                .windows(2)
                .any(|pair| pair[0] >= pair[1])
        {
            return -7;
        }
        if (n[0] == 2 || n[0] == 9 || n[0] == 11) && n[1] < 0 {
            return -6;
        }
    }
    0
}

/// # Safety
/// A validated immutable program of `program_len` i32s, readable flags/biomes
/// and exclusively writable output of `len` i32s, 24 writable frame words and
/// `program[0]/8` writable cache words. All pointers are aligned and nonaliasing.
/// Frame: y-index,q,water,bottom,pc,phase,updates,answer,answer-ready,unused,
/// depth-below,surface-depth,min-y,unused,min-surface,min-ready,secondary-ready,
/// request,unused,unused,below-world-sentinel,unused,unused,unused.
/// Returns 1=done, 2=external request, 0=bounded work yield, negative=bad input.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_step(
    program: *const i32,
    program_len: i32,
    flags: *const i32,
    output: *mut i32,
    biomes: *const i32,
    len: i32,
    frame: *mut i32,
    cache: *mut i32,
    secondary: f64,
) -> i32 {
    if len <= 0
        || len as usize > MAX_COLUMN
        || program_len < 16
        || [
            program as usize,
            flags as usize,
            output as usize,
            biomes as usize,
            frame as usize,
            cache as usize,
        ]
        .iter()
        .any(|p| *p == 0 || *p % 4 != 0)
    {
        return -1;
    }
    let p = unsafe { slice::from_raw_parts(program, program_len as usize) };
    let end = p[0] as usize;
    if end < 16 || end > p.len() || end % STRIDE != 0 {
        return -2;
    }
    let flags = unsafe { slice::from_raw_parts(flags, len as usize) };
    let out = unsafe { slice::from_raw_parts_mut(output, len as usize) };
    let biomes = unsafe { slice::from_raw_parts(biomes, len as usize) };
    let f = unsafe { slice::from_raw_parts_mut(frame, 24) };
    let cache = unsafe { slice::from_raw_parts_mut(cache, end / STRIDE) };
    for _ in 0..8192 {
        if f[0] < 0 {
            return 1;
        }
        let y = f[0] as usize;
        if y >= flags.len() {
            return -3;
        }
        let world_y = f[12].wrapping_add(y as i32);
        if f[5] == 0 {
            match flags[y] {
                0 => {
                    f[1] = 0;
                    f[2] = i32::MIN;
                    f[0] -= 1;
                    continue;
                }
                1 => {
                    if f[2] == i32::MIN {
                        f[2] = world_y.wrapping_add(1);
                    }
                    f[0] -= 1;
                    continue;
                }
                _ => {
                    if f[3] >= world_y {
                        f[3] = f[20];
                        let mut v = y as i32 - 1;
                        while v >= 0 && flags[v as usize] >= 2 {
                            v -= 1;
                        }
                        f[3] = f[12].wrapping_add(v).wrapping_add(1);
                    }
                    f[1] = f[1].wrapping_add(1);
                    f[6] = f[6].wrapping_add(1);
                    f[10] = world_y.wrapping_sub(f[3]).wrapping_add(1);
                    if flags[y] != 3 {
                        f[0] -= 1;
                        continue;
                    }
                    f[4] = 8;
                    f[5] = 1;
                }
            }
        }
        let pc = f[4] as usize;
        if pc == end {
            f[0] -= 1;
            f[5] = 0;
            continue;
        }
        if pc < 8 || pc + STRIDE > end || pc % STRIDE != 0 {
            return -4;
        }
        let n = &p[pc..pc + STRIDE];
        let test = match n[0] {
            0 => {
                f[0] -= 1;
                f[5] = 0;
                continue;
            }
            1 => {
                out[y] = n[1];
                f[0] -= 1;
                f[5] = 0;
                continue;
            }
            11 if world_y <= n[2] => true,
            11 if world_y >= n[3] => false,
            2 | 9 | 11 => {
                let answer;
                if n[0] == 2 && n[2] != 0 && cache[pc / STRIDE] >= 0 {
                    answer = cache[pc / STRIDE];
                } else if f[8] != 0 {
                    answer = f[7];
                    f[8] = 0;
                    if n[0] == 2 && n[2] != 0 {
                        cache[pc / STRIDE] = answer;
                    }
                } else {
                    f[17] = if n[0] == 9 { -4 - n[1] } else { n[1] };
                    return 2;
                }
                if n[0] == 9 {
                    if answer >= 0 {
                        out[y] = answer;
                        f[0] -= 1;
                        f[5] = 0;
                    } else {
                        f[4] += STRIDE as i32;
                    }
                    continue;
                }
                answer != 0
            }
            3 => {
                if n[3] != 0 && f[16] == 0 {
                    f[17] = -2;
                    return 2;
                }
                let below = if n[4] != 0 { f[10] } else { f[1] };
                let depth = if n[2] != 0 { f[11] } else { 0 };
                let extra = if n[3] == 0 {
                    0
                } else {
                    (0. + ((secondary - -1.) / (1. - -1.)) * (n[3] as f64 - 0.)) as i32
                };
                below
                    <= 1i32
                        .wrapping_add(n[1])
                        .wrapping_add(depth)
                        .wrapping_add(extra)
            }
            4 => {
                f[2] == i32::MIN
                    || world_y.wrapping_add(if n[3] != 0 { f[1] } else { 0 })
                        >= f[2]
                            .wrapping_add(n[1])
                            .wrapping_add(f[11].wrapping_mul(n[2]))
            }
            5 => {
                world_y.wrapping_add(if n[3] != 0 { f[1] } else { 0 })
                    >= n[1].wrapping_add(f[11].wrapping_mul(n[2]))
            }
            6 => f[11] <= 0,
            7 => {
                let start = n[1] as usize;
                let count = n[2] as usize;
                if start > p.len() || count > p.len() - start {
                    return -5;
                }
                p[start..start + count].binary_search(&biomes[y]).is_ok()
            }
            8 => {
                if f[15] == 0 {
                    f[17] = -3;
                    return 2;
                }
                world_y >= f[14]
            }
            10 => {
                if f[19] == 0 {
                    f[17] = -1;
                    return 2;
                }
                let index = world_y.wrapping_add(f[18]).wrapping_add(n[2]) % n[2];
                if index < 0 {
                    return -10;
                }
                out[y] = p[n[1] as usize + index as usize];
                f[0] -= 1;
                f[5] = 0;
                continue;
            }
            _ => return -6,
        };
        f[4] = if test != (n[6] != 0) {
            (pc + STRIDE) as i32
        } else {
            n[5]
        };
    }
    0
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_invalid_programs() {
        let mut p = vec![
            32, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 24, 0, 0, 1, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0,
        ];
        unsafe {
            assert_eq!(mattmc_surface_validate(p.as_ptr(), p.len() as i32), 0);
            p[13] = 8;
            assert_ne!(mattmc_surface_validate(p.as_ptr(), p.len() as i32), 0);
            p[13] = 24;
            p[8] = 7;
            p[9] = 31;
            p[10] = 2;
            assert_ne!(mattmc_surface_validate(p.as_ptr(), p.len() as i32), 0);
            assert_ne!(mattmc_surface_validate(std::ptr::null(), 32), 0);
            assert_ne!(mattmc_surface_validate(p.as_ptr(), 8), 0);
        }
    }
    #[test]
    fn lazy_requests_preserve_block_order_and_xz_cache() {
        for xz in [0, 1] {
            let p = [
                32, 0, 0, 0, 0, 0, 0, 0, 2, 0, xz, 0, 0, 24, 0, 0, 1, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0,
            ];
            let flags = [3; 129];
            let mut out = [-1; 129];
            let biomes = [0; 129];
            let mut f = [0; 24];
            let mut cache = [-1; 4];
            f[0] = 128;
            f[2] = i32::MIN;
            f[3] = i32::MAX;
            f[12] = -64;
            let mut requests = 0;
            loop {
                let status = unsafe {
                    mattmc_surface_step(
                        p.as_ptr(),
                        p.len() as i32,
                        flags.as_ptr(),
                        out.as_mut_ptr(),
                        biomes.as_ptr(),
                        129,
                        f.as_mut_ptr(),
                        cache.as_mut_ptr(),
                        0.,
                    )
                };
                if status == 1 {
                    break;
                }
                assert_eq!(status, 2);
                assert_eq!(out[f[0] as usize], -1);
                for y in f[0] as usize + 1..out.len() {
                    assert_eq!(out[y], 7);
                }
                assert_eq!(f[1], 129 - f[0]);
                assert_eq!(f[10], f[0] + 1);
                requests += 1;
                f[7] = 1;
                f[8] = 1;
            }
            assert!(out.iter().all(|v| *v == 7));
            assert_eq!(requests, if xz == 0 { 129 } else { 1 });
        }
    }
    #[test]
    fn scan_resets_air_and_tracks_water_and_nondefault_stone() {
        // Top-down: other stone, fluid, default, air, default, default.
        let flags = [3, 3, 0, 3, 1, 2];
        let mut out = [-1; 6];
        let biomes = [0; 6];
        let p = [
            32, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 0, 0, 24, 0, 0, 1, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0,
        ];
        let mut f = [0; 24];
        let mut cache = [-1; 4];
        f[0] = 5;
        f[2] = i32::MIN;
        f[3] = i32::MAX;
        f[12] = -10;
        let mut trace = Vec::new();
        loop {
            let status = unsafe {
                mattmc_surface_step(
                    p.as_ptr(),
                    32,
                    flags.as_ptr(),
                    out.as_mut_ptr(),
                    biomes.as_ptr(),
                    6,
                    f.as_mut_ptr(),
                    cache.as_mut_ptr(),
                    0.,
                )
            };
            if status == 1 {
                break;
            }
            assert_eq!(status, 2);
            trace.push((f[0], f[1], f[10], f[2]));
            f[7] = 1;
            f[8] = 1;
        }
        assert_eq!(
            trace,
            vec![(3, 2, 1, -5), (1, 1, 2, i32::MIN), (0, 2, 1, i32::MIN)]
        );
    }
}

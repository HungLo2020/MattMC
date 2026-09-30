//! Resumable column scan fused with ordered rule execution.
use super::{frame::*, program::*};
use std::slice;
const MAX_COLUMN: usize = 4096;
/// # Safety
/// A validated immutable program of `program_len` i32s, readable flags/biomes
/// and exclusively writable output of `len` i32s, 24 writable frame words and
/// `program[0]/8` writable cache words. All pointers are aligned and nonaliasing.
/// Frame: y-index,q,water,bottom,pc,phase,updates,answer,answer-ready,unused,
/// depth-below,surface-depth,min-y,unused,min-surface,min-ready,secondary-ready,
/// request,band-offset,band-offset-ready,below-world-sentinel,unused,unused,unused.
/// Returns 1=done, 2=external request, 0=bounded work yield, negative=bad input.
pub(crate) unsafe fn surface_step(
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
    let f = unsafe { slice::from_raw_parts_mut(frame, FRAME_WORDS) };
    let cache = unsafe { slice::from_raw_parts_mut(cache, end / STRIDE) };
    for _ in 0..8192 {
        if f[Y_INDEX] < 0 {
            return 1;
        }
        let y = f[Y_INDEX] as usize;
        if y >= flags.len() {
            return -3;
        }
        let world_y = f[MIN_Y].wrapping_add(y as i32);
        if f[PHASE] == 0 {
            match flags[y] {
                0 => {
                    f[STONE_ABOVE] = 0;
                    f[WATER_HEIGHT] = i32::MIN;
                    f[Y_INDEX] -= 1;
                    continue;
                }
                1 => {
                    if f[WATER_HEIGHT] == i32::MIN {
                        f[WATER_HEIGHT] = world_y.wrapping_add(1);
                    }
                    f[Y_INDEX] -= 1;
                    continue;
                }
                _ => {
                    if f[STONE_BOTTOM] >= world_y {
                        f[STONE_BOTTOM] = f[BELOW_WORLD_SENTINEL];
                        let mut v = y as i32 - 1;
                        while v >= 0 && flags[v as usize] >= 2 {
                            v -= 1;
                        }
                        f[STONE_BOTTOM] = f[MIN_Y].wrapping_add(v).wrapping_add(1);
                    }
                    f[STONE_ABOVE] = f[STONE_ABOVE].wrapping_add(1);
                    f[CONTEXT_UPDATES] = f[CONTEXT_UPDATES].wrapping_add(1);
                    f[STONE_BELOW] = world_y.wrapping_sub(f[STONE_BOTTOM]).wrapping_add(1);
                    if flags[y] != 3 {
                        f[Y_INDEX] -= 1;
                        continue;
                    }
                    f[PROGRAM_COUNTER] = 8;
                    f[PHASE] = 1;
                }
            }
        }
        let pc = f[PROGRAM_COUNTER] as usize;
        if pc == end {
            f[Y_INDEX] -= 1;
            f[PHASE] = 0;
            continue;
        }
        if pc < 8 || pc + STRIDE > end || pc % STRIDE != 0 {
            return -4;
        }
        let n = &p[pc..pc + STRIDE];
        let test = match n[0] {
            STOP => {
                f[Y_INDEX] -= 1;
                f[PHASE] = 0;
                continue;
            }
            BLOCK => {
                out[y] = n[1];
                f[Y_INDEX] -= 1;
                f[PHASE] = 0;
                continue;
            }
            VERTICAL_GRADIENT if world_y <= n[2] => true,
            VERTICAL_GRADIENT if world_y >= n[3] => false,
            EXTERNAL_CONDITION | EXTERNAL_RULE | VERTICAL_GRADIENT => {
                let answer;
                if n[0] == EXTERNAL_CONDITION && n[2] != 0 && cache[pc / STRIDE] >= 0 {
                    answer = cache[pc / STRIDE];
                } else if f[ANSWER_READY] != 0 {
                    answer = f[ANSWER];
                    f[ANSWER_READY] = 0;
                    if n[0] == EXTERNAL_CONDITION && n[2] != 0 {
                        cache[pc / STRIDE] = answer;
                    }
                } else {
                    f[REQUEST] = if n[0] == EXTERNAL_RULE {
                        -4 - n[1]
                    } else {
                        n[1]
                    };
                    return 2;
                }
                if n[0] == EXTERNAL_RULE {
                    if answer >= 0 {
                        out[y] = answer;
                        f[Y_INDEX] -= 1;
                        f[PHASE] = 0;
                    } else {
                        f[PROGRAM_COUNTER] += STRIDE as i32;
                    }
                    continue;
                }
                answer != 0
            }
            STONE_DEPTH => {
                if n[3] != 0 && f[SECONDARY_READY] == 0 {
                    f[REQUEST] = -2;
                    return 2;
                }
                let below = if n[4] != 0 {
                    f[STONE_BELOW]
                } else {
                    f[STONE_ABOVE]
                };
                let depth = if n[2] != 0 { f[SURFACE_DEPTH] } else { 0 };
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
            WATER => {
                f[WATER_HEIGHT] == i32::MIN
                    || world_y.wrapping_add(if n[3] != 0 { f[STONE_ABOVE] } else { 0 })
                        >= f[WATER_HEIGHT]
                            .wrapping_add(n[1])
                            .wrapping_add(f[SURFACE_DEPTH].wrapping_mul(n[2]))
            }
            Y_CHECK => {
                world_y.wrapping_add(if n[3] != 0 { f[STONE_ABOVE] } else { 0 })
                    >= n[1].wrapping_add(f[SURFACE_DEPTH].wrapping_mul(n[2]))
            }
            HOLE => f[SURFACE_DEPTH] <= 0,
            BIOME => {
                let start = n[1] as usize;
                let count = n[2] as usize;
                if start > p.len() || count > p.len() - start {
                    return -5;
                }
                p[start..start + count].binary_search(&biomes[y]).is_ok()
            }
            ABOVE_PRELIMINARY => {
                if f[MIN_SURFACE_READY] == 0 {
                    f[REQUEST] = -3;
                    return 2;
                }
                world_y >= f[MIN_SURFACE]
            }
            BANDS => {
                if f[BAND_OFFSET_READY] == 0 {
                    f[REQUEST] = -1;
                    return 2;
                }
                let index = world_y.wrapping_add(f[BAND_OFFSET]).wrapping_add(n[2]) % n[2];
                if index < 0 {
                    return -10;
                }
                out[y] = p[n[1] as usize + index as usize];
                f[Y_INDEX] -= 1;
                f[PHASE] = 0;
                continue;
            }
            _ => return -6,
        };
        f[PROGRAM_COUNTER] = if test != (n[6] != 0) {
            (pc + STRIDE) as i32
        } else {
            n[5]
        };
    }
    0
}

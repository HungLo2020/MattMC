// Frame: nearest IDs[0..4], distances[4..8], phase, requested cache index,
// Y, below-is-lava(-1 unknown), result state(-1 solid), update flag,
// air state, barrier response ready. Doubles: density, cached barrier noise.
// Cache entries: fluid level, exact state ID (-1 means uninitialized), kind
// (1 water, 2 lava, 0 other). At/equals use the exact state, not just the kind.
fn similarity(a: i32, b: i32) -> f64 {
    1.0 - b.wrapping_sub(a) as f64 / 25.0
}
const FLOW: f64 = 1.0 - 44.0 / 25.0;
fn at(s: &[i32], y: i32, air: i32) -> i32 {
    if y < s[0] {
        s[1]
    } else {
        air
    }
}
fn kind(s: &[i32], y: i32) -> i32 {
    if y < s[0] {
        s[2]
    } else {
        0
    }
}
fn same(a: &[i32], b: &[i32]) -> bool {
    a[0] == b[0] && a[1] == b[1]
}

// None requests the barrier sample. NaN samples are consumed once and remain
// uncached for the next pressure calculation, exactly like MutableDouble.
fn pressure(a: &[i32], b: &[i32], f: &mut [i32], d: &mut [f64], custom: bool) -> Option<f64> {
    let y = if custom { f[17] } else { f[10] };
    if matches!((kind(a, y), kind(b, y)), (1, 2) | (2, 1)) {
        return Some(2.0);
    }
    let difference = a[0].wrapping_sub(b[0]).wrapping_abs();
    if difference == 0 {
        return Some(0.0);
    }
    let midpoint = 0.5 * a[0].wrapping_add(b[0]) as f64;
    let offset = y as f64 + 0.5 - midpoint;
    let overlap = difference as f64 / 2.0 - offset.abs();
    let q = if offset > 0.0 {
        let p = 0.0 + overlap;
        if p > 0.0 {
            p / 1.5
        } else {
            p / 2.5
        }
    } else {
        let p = 3.0 + overlap;
        if p > 0.0 {
            p / 3.0
        } else {
            p / 10.0
        }
    };
    let barrier = if !(q < -2.0) && !(q > 2.0) {
        if d[1].is_nan() && f[15] == 0 {
            return None;
        }
        f[15] = 0;
        d[1]
    } else {
        0.0
    };
    Some(2.0 * (barrier + q))
}

pub(super) fn step(f: &mut [i32], d: &mut [f64], cache: &[i32], custom: bool) -> i32 {
    for &id in &f[..4] {
        if id < 0 || id as usize >= cache.len() / 3 {
            return -2;
        }
    }
    let a0 = f[0] as usize * 3;
    let b0 = f[1] as usize * 3;
    let c0 = f[2] as usize * 3;
    let e0 = f[3] as usize * 3;
    let a = &cache[a0..a0 + 3];
    let b = &cache[b0..b0 + 3];
    let c = &cache[c0..c0 + 3];
    let fourth = &cache[e0..e0 + 3];
    let ab = similarity(f[4], f[5]);
    let ac = similarity(f[4], f[6]);
    let bc = similarity(f[5], f[6]);
    loop {
        let needed = match f[8] {
            0 => Some(0),
            1 | 3 => Some(1),
            4 => Some(2),
            7 => Some(3),
            _ => None,
        };
        if let Some(rank) = needed {
            if cache[f[rank] as usize * 3 + 1] == -1 {
                f[9] = f[rank];
                return 1;
            }
        }
        match f[8] {
            0 => {
                f[12] = at(a, f[10], f[14]);
                f[13] = 0;
                if ab <= 0.0 {
                    if ab >= FLOW {
                        f[8] = 1;
                    } else {
                        return 0;
                    }
                } else {
                    f[8] = 2;
                }
            }
            1 => {
                f[13] = (!same(a, b)) as i32;
                return 0;
            }
            2 => {
                if kind(a, f[10]) == 1 {
                    if f[11] == -1 {
                        return 2;
                    }
                    if f[11] != 0 {
                        f[13] = 1;
                        return 0;
                    }
                }
                f[8] = 3;
            }
            3 => {
                if custom && f[18] == 0 {
                    return 4;
                }
                let Some(p) = pressure(a, b, f, d, custom) else {
                    return 3;
                };
                if custom {
                    f[18] = 0;
                }
                if d[0] + ab * p > 0.0 {
                    f[12] = -1;
                    f[13] = 0;
                    return 0;
                }
                f[8] = 4;
            }
            4 => {
                if ac > 0.0 {
                    if custom && f[18] == 0 {
                        return 4;
                    }
                    let Some(p) = pressure(a, c, f, d, custom) else {
                        return 3;
                    };
                    if custom {
                        f[18] = 0;
                    }
                    if d[0] + (ab * ac) * p > 0.0 {
                        f[12] = -1;
                        f[13] = 0;
                        return 0;
                    }
                }
                f[8] = 5;
            }
            5 => {
                if bc > 0.0 {
                    if custom && f[18] == 0 {
                        return 4;
                    }
                    let Some(p) = pressure(b, c, f, d, custom) else {
                        return 3;
                    };
                    if custom {
                        f[18] = 0;
                    }
                    if d[0] + (ab * bc) * p > 0.0 {
                        f[12] = -1;
                        f[13] = 0;
                        return 0;
                    }
                }
                f[8] = 6;
            }
            6 => {
                if !same(a, b) || (bc >= FLOW && !same(b, c)) || (ac >= FLOW && !same(a, c)) {
                    f[13] = 1;
                    return 0;
                }
                if ac >= FLOW && similarity(f[4], f[7]) >= FLOW {
                    f[8] = 7;
                } else {
                    f[13] = 0;
                    return 0;
                }
            }
            7 => {
                f[13] = (!same(a, fourth)) as i32;
                return 0;
            }
            _ => return -3,
        }
    }
}

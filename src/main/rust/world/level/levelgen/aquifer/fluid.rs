//! Lazy aquifer-source evaluation. All integer operations follow Java wrapping
//! semantics; float constants in the deep-dark predicate are widened from f32.
const OFFSETS: [[i32; 2]; 13] = [
    [0, 0],
    [-2, -1],
    [-1, -1],
    [0, -1],
    [1, -1],
    [-3, 0],
    [-2, 0],
    [-1, 0],
    [1, 0],
    [-2, 1],
    [-1, 1],
    [0, 1],
    [1, 1],
];
fn request(f: &mut [i32], kind: i32, phase: i32, x: i32, y: i32, z: i32) -> i32 {
    f[0] = phase;
    f[14] = x;
    f[15] = y;
    f[16] = z;
    kind
}
fn next_surface(f: &mut [i32]) {
    f[5] = f[5].min(f[10]);
    f[4] += 1;
    f[0] = 2;
}
fn select_level(f: &mut [i32], level: i32) {
    f[12] = level;
    f[13] = f[8];
    f[0] = 10;
}
pub(super) fn step(f: &mut [i32], noise: f64) -> i32 {
    if !(0..=13).contains(&f[4]) {
        return -2;
    }
    let x = f[1];
    let y = f[2];
    let z = f[3];
    loop {
        match f[0] {
            0 => return request(f, 1, 1, x, y, z),
            1 => {
                f[7] = f[17];
                f[8] = f[18];
                f[4] = 0;
                f[5] = i32::MAX;
                f[6] = 0;
                f[0] = 2;
            }
            2 => {
                if f[4] == 13 {
                    return request(f, 3, 5, x, y, z);
                }
                if !(0..13).contains(&f[4]) {
                    return -2;
                }
                let [dx, dz] = OFFSETS[f[4] as usize];
                return request(f, 2, 3, x.wrapping_add(dx << 4), 0, z.wrapping_add(dz << 4));
            }
            3 => {
                f[10] = f[17];
                f[11] = f[10].wrapping_add(8);
                if f[4] == 0 && y.wrapping_sub(12) > f[11] {
                    f[12] = f[7];
                    f[13] = f[8];
                    return 0;
                }
                if y.wrapping_add(12) > f[11] || f[4] == 0 {
                    return request(f, 1, 4, f[14], f[11], f[16]);
                }
                next_surface(f);
            }
            4 => {
                // Java computes status.at(adjustedSurface).isAir(), including custom
                // air states. The binding supplies that predicate without collapsing IDs.
                if f[19] == 0 {
                    if f[4] == 0 {
                        f[6] = 1;
                    }
                    if y.wrapping_add(12) > f[11] {
                        f[12] = f[17];
                        f[13] = f[18];
                        return 0;
                    }
                }
                next_surface(f);
            }
            5 => {
                if noise < -0.225_f32 as f64 {
                    return request(f, 4, 6, x, y, z);
                }
                return request(f, 5, 7, x, y, z);
            }
            6 => {
                if noise > 0.9_f32 as f64 {
                    select_level(f, f[21]);
                } else {
                    return request(f, 5, 7, x, y, z);
                }
            }
            7 => {
                let m = f[5].wrapping_add(8).wrapping_sub(y);
                let t = (m as f64 - 0.0) / (64.0 - 0.0);
                let a = if f[6] != 0 {
                    if t < 0.0 {
                        1.0
                    } else if t > 1.0 {
                        0.0
                    } else {
                        1.0 + t * (0.0 - 1.0)
                    }
                } else {
                    0.0
                };
                let g = if noise < -1.0 {
                    -1.0
                } else if noise > 1.0 {
                    1.0
                } else {
                    noise
                };
                let fraction = (a - 1.0) / (0.0 - 1.0);
                let h = -0.3 + fraction * (0.8 - -0.3);
                let o = -0.8 + fraction * (0.4 - -0.8);
                let d = g - o;
                let e = g - h;
                if e > 0.0 {
                    select_level(f, f[7]);
                } else if d > 0.0 {
                    return request(
                        f,
                        6,
                        8,
                        x.div_euclid(16),
                        y.div_euclid(40),
                        z.div_euclid(16),
                    );
                } else {
                    select_level(f, f[21]);
                }
            }
            8 => {
                let value = (noise * 10.0) / 3.0;
                let cast = value as i32;
                let floor = if value < (cast as f64) {
                    cast.wrapping_sub(1)
                } else {
                    cast
                };
                let level = y
                    .div_euclid(40)
                    .wrapping_mul(40)
                    .wrapping_add(20)
                    .wrapping_add(floor.wrapping_mul(3));
                select_level(f, f[5].min(level));
            }
            10 => {
                if f[12] <= -10 && f[12] != f[21] && f[8] != f[20] {
                    return request(
                        f,
                        7,
                        11,
                        x.div_euclid(64),
                        y.div_euclid(40),
                        z.div_euclid(64),
                    );
                }
                return 0;
            }
            11 => {
                if noise.abs() > 0.3 {
                    f[13] = f[20];
                }
                return 0;
            }
            _ => return -3,
        }
    }
}

// Repeated source lookups at the same quart column are pure for the built-in
// router. Keep that cache next to the native loop; yield only on a cache miss.
pub(super) fn pure_step(f: &mut [i32], noise: f64, policy: &[i32], surface: &[i32]) -> i32 {
    loop {
        match step(f, noise) {
            1 => {
                let y = f[15];
                let (level, id, air) = if policy[5] != 0 {
                    (policy[6], policy[7], true)
                } else if y < policy[0].min(policy[2]) {
                    (policy[0], policy[1], false)
                } else {
                    (policy[2], policy[3], policy[4] != 0)
                };
                f[17] = level;
                f[18] = id;
                f[19] = (air || y >= level) as i32;
            }
            2 => {
                let x = (f[14] >> 2).wrapping_sub(f[22]);
                let z = (f[16] >> 2).wrapping_sub(f[23]);
                if x < 0 || z < 0 || x >= f[24] || z >= f[25] {
                    return -4;
                }
                let index = (z * f[24] + x) as usize * 2;
                if surface[index + 1] == 0 {
                    return 2;
                }
                f[17] = surface[index];
            }
            result => return result,
        }
    }
}

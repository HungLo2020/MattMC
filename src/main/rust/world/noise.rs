//! Seed-preserving noise evaluation. Java builds immutable permutation/octave state;
//! Rust evaluates it without allocation, callbacks, locks, or contracted arithmetic.
//! This module deliberately also compiles standalone for differential benchmarks.
use std::hint::black_box;

const GRAD: [[f64; 3]; 16] = [
    [1., 1., 0.],
    [-1., 1., 0.],
    [1., -1., 0.],
    [-1., -1., 0.],
    [1., 0., 1.],
    [-1., 0., 1.],
    [1., 0., -1.],
    [-1., 0., -1.],
    [0., 1., 1.],
    [0., -1., 1.],
    [0., 1., -1.],
    [0., -1., -1.],
    [1., 1., 0.],
    [0., -1., 1.],
    [-1., 1., 0.],
    [0., -1., -1.],
];
/// ABI v1: native endian, 8-byte alignment, followed by n1+n2+n3 Octaves.
#[repr(C)]
pub struct State {
    kind: u32,
    n1: u32,
    n2: u32,
    n3: u32,
    params: [f64; 8],
}
#[repr(C)]
#[derive(Clone)]
pub struct Octave {
    x: f64,
    y: f64,
    z: f64,
    amplitude: f64,
    present: u64,
    p: [u8; 256],
    p32: [i32; 256],
}

// Rust saturating casts match Java casts, but Java's subtraction wraps at MIN_VALUE.
#[inline(always)]
fn floor(x: f64) -> i32 {
    let i = x as i32;
    if x < i as f64 {
        i.wrapping_sub(1)
    } else {
        i
    }
}
#[inline(always)]
fn lfloor(x: f64) -> i64 {
    let i = x as i64;
    if x < i as f64 {
        i.wrapping_sub(1)
    } else {
        i
    }
}
#[inline(always)]
fn wrap(x: f64) -> f64 {
    x - lfloor(x / 33554432.0 + 0.5) as f64 * 33554432.0
}
#[inline(always)]
pub(super) fn lerp(t: f64, a: f64, b: f64) -> f64 {
    a + t * (b - a)
}
#[inline(always)]
pub(super) fn lerp2(x: f64, y: f64, a: f64, b: f64, c: f64, d: f64) -> f64 {
    lerp(y, lerp(x, a, b), lerp(x, c, d))
}
#[inline(always)]
pub(super) fn lerp3(x: f64, y: f64, z: f64, v: [f64; 8]) -> f64 {
    lerp(
        z,
        lerp2(x, y, v[0], v[1], v[2], v[3]),
        lerp2(x, y, v[4], v[5], v[6], v[7]),
    )
}
#[inline(always)]
fn smooth(x: f64) -> f64 {
    x * x * x * (x * (x * 6.0 - 15.0) + 10.0)
}
#[inline(always)]
fn derivative(x: f64) -> f64 {
    30.0 * x * x * (x - 1.0) * (x - 1.0)
}
#[inline(always)]
fn dot(g: usize, x: f64, y: f64, z: f64) -> f64 {
    let g = GRAD[g];
    g[0] * x + g[1] * y + g[2] * z
}
impl Octave {
    #[inline(always)]
    fn p(&self, i: i32) -> i32 {
        self.p[(i & 255) as usize] as i32
    }
    #[inline(always)]
    fn p12(&self, i: i32) -> i32 {
        // Packed by Java alongside the permutation; avoids division in Simplex.
        (self.p32[(i & 255) as usize] >> 8) & 15
    }
    #[inline(always)]
    fn corners(&self, x: i32, y: i32, z: i32) -> [usize; 8] {
        let a = self.p(x);
        let b = self.p(x.wrapping_add(1));
        let c = self.p(a.wrapping_add(y));
        let d = self.p(a.wrapping_add(y).wrapping_add(1));
        let e = self.p(b.wrapping_add(y));
        let f = self.p(b.wrapping_add(y).wrapping_add(1));
        [c, e, d, f, c, e, d, f].map_with_index(|n, v| {
            (self.p(v.wrapping_add(z).wrapping_add((n / 4) as i32)) & 15) as usize
        })
    }
    #[inline(always)]
    fn noise(&self, x: f64, y: f64, z: f64, ys: f64, ym: f64) -> f64 {
        let x = x + self.x;
        let y = y + self.y;
        let z = z + self.z;
        let ix = floor(x);
        let iy = floor(y);
        let iz = floor(z);
        let dx = x - ix as f64;
        let dy = y - iy as f64;
        let dz = z - iz as f64;
        let shift = if ys != 0.0 {
            let r = if ym >= 0.0 && ym < dy { ym } else { dy };
            floor(r / ys + 1.0e-7_f32 as f64) as f64 * ys
        } else {
            0.0
        };
        let ey = dy - shift;
        let c = self.corners(ix, iy, iz);
        let v = std::array::from_fn(|n| {
            dot(
                c[n],
                dx - (n & 1) as f64,
                ey - ((n >> 1) & 1) as f64,
                dz - (n >> 2) as f64,
            )
        });
        lerp3(smooth(dx), smooth(dy), smooth(dz), v)
    }
    fn with_derivative(&self, x: f64, y: f64, z: f64, out: &mut [f64]) -> f64 {
        let x = x + self.x;
        let y = y + self.y;
        let z = z + self.z;
        let ix = floor(x);
        let iy = floor(y);
        let iz = floor(z);
        let x = x - ix as f64;
        let y = y - iy as f64;
        let z = z - iz as f64;
        let c = self.corners(ix, iy, iz);
        let v: [f64; 8] = std::array::from_fn(|n| {
            dot(
                c[n],
                x - (n & 1) as f64,
                y - ((n >> 1) & 1) as f64,
                z - (n >> 2) as f64,
            )
        });
        let sx = smooth(x);
        let sy = smooth(y);
        let sz = smooth(z);
        let gx = lerp3(sx, sy, sz, c.map(|g| GRAD[g][0]));
        let gy = lerp3(sx, sy, sz, c.map(|g| GRAD[g][1]));
        let gz = lerp3(sx, sy, sz, c.map(|g| GRAD[g][2]));
        let dx = lerp2(sy, sz, v[1] - v[0], v[3] - v[2], v[5] - v[4], v[7] - v[6]);
        let dy = lerp2(sz, sx, v[2] - v[0], v[6] - v[4], v[3] - v[1], v[7] - v[5]);
        let dz = lerp2(sx, sy, v[4] - v[0], v[5] - v[1], v[6] - v[2], v[7] - v[3]);
        out[0] += gx + derivative(x) * dx;
        out[1] += gy + derivative(y) * dy;
        out[2] += gz + derivative(z) * dz;
        lerp3(sx, sy, sz, v)
    }
    #[inline(always)]
    fn corner(g: i32, x: f64, y: f64, z: f64, r: f64) -> f64 {
        let mut h = r - x * x - y * y - z * z;
        if h < 0.0 {
            0.0
        } else {
            h *= h;
            h * h * dot(g as usize, x, y, z)
        }
    }
    fn simplex2(&self, x: f64, y: f64) -> f64 {
        // Java's compile-time Math.sqrt(3.0) rounded constants.
        let f2 = 0.3660254037844386;
        let g2 = 0.21132486540518713;
        let f = (x + y) * f2;
        let i = floor(x + f);
        let j = floor(y + f);
        let g = i.wrapping_add(j) as f64 * g2;
        let a = x - (i as f64 - g);
        let b = y - (j as f64 - g);
        let (u, v) = if a > b { (1, 0) } else { (0, 1) };
        let c = a - u as f64 + g2;
        let d = b - v as f64 + g2;
        let e = a - 1.0 + 2.0 * g2;
        let f = b - 1.0 + 2.0 * g2;
        let i = i & 255;
        let j = j & 255;
        let p = self.p12(i + self.p(j));
        let q = self.p12(i + u + self.p(j + v));
        let r = self.p12(i + 1 + self.p(j + 1));
        70.0 * (Self::corner(p, a, b, 0.0, 0.5)
            + Self::corner(q, c, d, 0.0, 0.5)
            + Self::corner(r, e, f, 0.0, 0.5))
    }
    fn simplex3(&self, x: f64, y: f64, z: f64) -> f64 {
        let s = (x + y + z) * 0.3333333333333333;
        let i = floor(x + s);
        let j = floor(y + s);
        let k = floor(z + s);
        let t = i.wrapping_add(j).wrapping_add(k) as f64 * 0.16666666666666666;
        let a = x - (i as f64 - t);
        let b = y - (j as f64 - t);
        let c = z - (k as f64 - t);
        let (u, v, w, p, q, r) = if a >= b {
            if b >= c {
                (1, 0, 0, 1, 1, 0)
            } else if a >= c {
                (1, 0, 0, 1, 0, 1)
            } else {
                (0, 0, 1, 1, 0, 1)
            }
        } else if b < c {
            (0, 0, 1, 0, 1, 1)
        } else if a < c {
            (0, 1, 0, 0, 1, 1)
        } else {
            (0, 1, 0, 1, 1, 0)
        };
        let d = a - u as f64 + 0.16666666666666666;
        let e = b - v as f64 + 0.16666666666666666;
        let f = c - w as f64 + 0.16666666666666666;
        let g = a - p as f64 + 0.3333333333333333;
        let h = b - q as f64 + 0.3333333333333333;
        let l = c - r as f64 + 0.3333333333333333;
        let m = a - 1.0 + 0.5;
        let n = b - 1.0 + 0.5;
        let o = c - 1.0 + 0.5;
        let i = i & 255;
        let j = j & 255;
        let k = k & 255;
        let ga = self.p12(i + self.p(j + self.p(k)));
        let gb = self.p12(i + u + self.p(j + v + self.p(k + w)));
        let gc = self.p12(i + p + self.p(j + q + self.p(k + r)));
        let gd = self.p12(i + 1 + self.p(j + 1 + self.p(k + 1)));
        32.0 * (Self::corner(ga, a, b, c, 0.6)
            + Self::corner(gb, d, e, f, 0.6)
            + Self::corner(gc, g, h, l, 0.6)
            + Self::corner(gd, m, n, o, 0.6))
    }
}
trait ArrayIndexMap<T, const N: usize> {
    fn map_with_index<U>(self, f: impl FnMut(usize, T) -> U) -> [U; N];
}
impl<T: Copy, const N: usize> ArrayIndexMap<T, N> for [T; N] {
    fn map_with_index<U>(self, mut f: impl FnMut(usize, T) -> U) -> [U; N] {
        std::array::from_fn(|i| f(i, self[i]))
    }
}
#[inline(always)]
fn perlin(
    o: &[Octave],
    mut freq: f64,
    mut weight: f64,
    x: f64,
    y: f64,
    z: f64,
    ys: f64,
    ym: f64,
    fixed: bool,
) -> f64 {
    let mut sum = 0.0;
    for n in o {
        if n.present != 0 {
            let v = n.noise(
                wrap(x * freq),
                if fixed { -n.y } else { wrap(y * freq) },
                wrap(z * freq),
                ys * freq,
                ym * freq,
            );
            sum += n.amplitude * v * weight;
        }
        freq *= 2.0;
        weight /= 2.0;
    }
    sum
}
impl State {
    // SAFETY: the bridge allocates/validates the complete immutable trailing array.
    unsafe fn octaves(&self) -> &[Octave] {
        unsafe {
            std::slice::from_raw_parts(
                (self as *const Self).add(1).cast(),
                (self.n1 + self.n2 + self.n3) as usize,
            )
        }
    }
    #[inline(always)]
    fn eval(&self, o: &[Octave], x: f64, y: f64, z: f64, ys: f64, ym: f64, flags: u32) -> f64 {
        let n = self.n1 as usize;
        match self.kind {
            1 => o[0].noise(x, y, z, ys, ym),
            2 => perlin(
                o,
                self.params[0],
                self.params[1],
                x,
                y,
                z,
                ys,
                ym,
                flags != 0,
            ),
            3 => {
                let a = perlin(
                    &o[..n],
                    self.params[0],
                    self.params[1],
                    x,
                    y,
                    z,
                    0.,
                    0.,
                    false,
                );
                let b = perlin(
                    &o[n..],
                    self.params[2],
                    self.params[3],
                    x * 1.0181268882175227,
                    y * 1.0181268882175227,
                    z * 1.0181268882175227,
                    0.,
                    0.,
                    false,
                );
                (a + b) * self.params[4]
            }
            4 => self.blended(o, x, y, z),
            5 => {
                if flags == 0 {
                    o[0].simplex2(x, y)
                } else {
                    o[0].simplex3(x, y, z)
                }
            }
            6 => {
                let mut sum = 0.0;
                let mut freq = self.params[0];
                let mut weight = self.params[1];
                for a in o {
                    if a.present != 0 {
                        sum += a.simplex2(
                            x * freq + if flags != 0 { a.x } else { 0. },
                            y * freq + if flags != 0 { a.y } else { 0. },
                        ) * weight;
                    }
                    freq /= 2.0;
                    weight *= 2.0;
                }
                sum
            }
            _ => unreachable!(),
        }
    }
    fn blended(&self, o: &[Octave], x: f64, y: f64, z: f64) -> f64 {
        let d = x * self.params[0];
        let e = y * self.params[1];
        let f = z * self.params[0];
        let g = d / self.params[2];
        let h = e / self.params[3];
        let i = f / self.params[2];
        let j = self.params[1] * self.params[4];
        let k = j / self.params[3];
        let mut a = 0.;
        let mut b = 0.;
        let mut c = 0.;
        let mut freq = 1.;
        for n in o[32..40].iter().rev() {
            if n.present != 0 {
                c += n.noise(
                    wrap(g * freq),
                    wrap(h * freq),
                    wrap(i * freq),
                    k * freq,
                    h * freq,
                ) / freq;
            }
            freq /= 2.;
        }
        let q = (c / 10. + 1.) / 2.;
        let hi = q >= 1.;
        let lo = q <= 0.;
        freq = 1.;
        for r in 0..16 {
            let x = wrap(d * freq);
            let y = wrap(e * freq);
            let z = wrap(f * freq);
            let ys = j * freq;
            let n = &o[15 - r];
            if !hi && n.present != 0 {
                a += n.noise(x, y, z, ys, e * freq) / freq;
            }
            let n = &o[31 - r];
            if !lo && n.present != 0 {
                b += n.noise(x, y, z, ys, e * freq) / freq;
            }
            freq /= 2.;
        }
        let a = a / 512.;
        let b = b / 512.;
        (if q < 0. {
            a
        } else if q > 1. {
            b
        } else {
            lerp(q, a, b)
        }) / 128.
    }
}
/// Validate the Java-owned state once before publishing it to other threads.
/// # Safety
/// `state` must point to `bytes` readable bytes aligned to eight bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_validate(state: *const State, bytes: u64) -> i32 {
    if state.is_null() || (state as usize) % 8 != 0 || bytes < 80 {
        return -1;
    }
    let s = unsafe { &*state };
    let count = s.n1 as u64 + s.n2 as u64 + s.n3 as u64;
    if count > i32::MAX as u64 || bytes != 80 + 1320 * count {
        return -2;
    }
    let valid = match s.kind {
        1 | 5 => s.n1 == 1 && s.n2 == 0 && s.n3 == 0,
        2 | 6 => s.n2 == 0 && s.n3 == 0,
        3 => s.n3 == 0,
        4 => s.n1 == 16 && s.n2 == 16 && s.n3 == 8,
        _ => false,
    };
    if !valid {
        return -3;
    }
    for octave in unsafe { s.octaves() } {
        // Java seed constructors generate offsets in [0,256). Enforce the
        // invariant relied on by the optimized floor-to-int conversions.
        if ![octave.x, octave.y, octave.z]
            .iter()
            .all(|x| *x >= 0. && *x <= 256.)
        {
            return -5;
        }
        for i in 0..256 {
            let p = octave.p[i] as i32;
            if octave.p32[i] != (p | ((p % 12) << 8)) {
                return -4;
            }
        }
    }
    // Complete feature detection before any critical downcall can evaluate.
    #[cfg(target_arch = "x86_64")]
    black_box(is_x86_feature_detected!("avx2"));
    0
}
#[inline]
fn sample_simplex2(o: &Octave, x: f64, y: f64) -> f64 {
    #[cfg(target_arch = "x86_64")]
    if x.abs() <= 30000000. && y.abs() <= 30000000. && is_x86_feature_detected!("avx2") {
        return unsafe { simd::sample_simplex2(o, x, y) };
    }
    o.simplex2(x, y)
}
/// Complete bounded End island-height expression. Preserves Java int wrapping,
/// float arithmetic, double sqrt followed by float rounding, and NaN propagation.
/// # Safety
/// `state` is a live immutable validated kind-5 state containing one octave.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_end_island(state: *const State, x: i32, z: i32) -> f32 {
    let octave = unsafe { &*state.add(1).cast::<Octave>() };
    let sqrt = |v: f32| (v as f64).sqrt() as f32;
    let clamp = |v: f32| {
        if v < -100. {
            -100.
        } else if v > 80. {
            80.
        } else {
            v
        }
    };
    let k = x / 2;
    let l = z / 2;
    let m = x % 2;
    let n = z % 2;
    let mut height =
        clamp(100.0 - sqrt(x.wrapping_mul(x).wrapping_add(z.wrapping_mul(z)) as f32) * 8.0);
    // Java Math.max propagates this NaN through every iteration. Sampling is
    // pure and consumes no RNG state, so no iteration can change the result.
    if height.is_nan() {
        return height;
    }
    // If the farthest corner is inside the exclusion circle, the original
    // loop's strict >4096 condition is false at every point, for every seed.
    let far_x = ((k as i64) - 12).abs().max(((k as i64) + 12).abs());
    let far_z = ((l as i64) - 12).abs().max(((l as i64) + 12).abs());
    if far_x * far_x + far_z * far_z <= 4096 {
        return height;
    }
    for dx in -12i32..=12 {
        for dz in -12i32..=12 {
            let q = k.wrapping_add(dx) as i64;
            let r = l.wrapping_add(dz) as i64;
            if q.wrapping_mul(q).wrapping_add(r.wrapping_mul(r)) > 4096
                && sample_simplex2(octave, q as f64, r as f64) < (-0.9f32) as f64
            {
                let factor = ((q as f32).abs() * 3439.0 + (r as f32).abs() * 147.0) % 13.0 + 9.0;
                let a = (m - dx * 2) as f32;
                let b = (n - dz * 2) as f32;
                let candidate = clamp(100.0 - sqrt(a * a + b * b) * factor);
                // Java Math.max propagates NaNs and prefers positive zero.
                height = if height.is_nan() {
                    height
                } else if candidate.is_nan() {
                    candidate
                } else if height == 0. && candidate == 0. {
                    f32::from_bits(height.to_bits() & candidate.to_bits())
                } else if height >= candidate {
                    height
                } else {
                    candidate
                };
            }
        }
    }
    height
}
/// Specialized hot entry: no unused z coordinate, flags, or family dispatch.
/// # Safety
/// Java-owned validated kind=5 state with one live immutable octave.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_simplex2(state: *const State, x: f64, y: f64) -> f64 {
    let o = unsafe { &*state.add(1).cast::<Octave>() };
    sample_simplex2(o, x, y)
}
/// # Safety
/// `state` is validated immutable state, live for this call. No allocations or callbacks.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_eval(
    state: *const State,
    x: f64,
    y: f64,
    z: f64,
    ys: f64,
    ym: f64,
    flags: u32,
) -> f64 {
    let s = unsafe { &*state };
    eval_dispatch(s, unsafe { s.octaves() }, x, y, z, ys, ym, flags)
}
#[inline]
fn eval_dispatch(
    s: &State,
    o: &[Octave],
    x: f64,
    y: f64,
    z: f64,
    ys: f64,
    ym: f64,
    flags: u32,
) -> f64 {
    if s.kind == 5 && flags == 0 {
        return sample_simplex2(&o[0], x, y);
    }
    #[cfg(target_arch = "x86_64")]
    if is_x86_feature_detected!("avx2") {
        if (1..=3).contains(&s.kind)
            && ys == 0.
            && ym == 0.
            && flags == 0
            && s.n1 <= 32
            && s.n2 <= 32
            && s.params[0] >= 0.
            && s.params[0] <= 256.
            && (s.kind != 3 || (s.params[2] >= 0. && s.params[2] <= 256.))
            && x.abs() <= 30000000.
            && y.abs() <= 30000000.
            && z.abs() <= 30000000.
        {
            if s.kind != 1 && s.n1 >= 4 {
                return unsafe { simd::evaluate_single(s, o, x, y, z) };
            }
            return unsafe { simd::evaluate_bounded(s, o, x, y, z) };
        }
        if x.abs() <= 30000000. && y.abs() <= 30000000. && z.abs() <= 30000000. {
            if s.kind == 5 || (s.kind == 6 && s.params[0] >= 0. && s.params[0] <= 4.) {
                return unsafe { simd::evaluate_simplex(s, o, x, y, z, flags) };
            }
            if s.kind == 4
                && s.params[..2]
                    .iter()
                    .all(|v| *v >= 0.684412 && *v <= 684412.)
                && s.params[2..4].iter().all(|v| *v >= 0.001 && *v <= 1000.)
                && s.params[4] >= 1.
                && s.params[4] <= 8.
            {
                return unsafe { simd::blended(s, o, x, y, z) };
            }
        }
        return unsafe { eval_avx2(s, o, x, y, z, ys, ym, flags) };
    }
    s.eval(o, x, y, z, ys, ym, flags)
}
#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2")]
unsafe fn eval_avx2(
    s: &State,
    o: &[Octave],
    x: f64,
    y: f64,
    z: f64,
    ys: f64,
    ym: f64,
    flags: u32,
) -> f64 {
    s.eval(o, x, y, z, ys, ym, flags)
}
/// # Safety
/// Valid state, `xyz` contains count*3 doubles, `out` count doubles; buffers do not alias.
/// Bounded for Java critical downcalls; Java splits larger requests.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_batch(
    state: *const State,
    xyz: *const f64,
    out: *mut f64,
    count: u32,
    ys: f64,
    ym: f64,
    flags: u32,
) -> i32 {
    if state.is_null() || xyz.is_null() || out.is_null() || count > 256 {
        return -1;
    }
    let s = unsafe { &*state };
    let o = unsafe { s.octaves() };
    let xyz = unsafe { std::slice::from_raw_parts(xyz, count as usize * 3) };
    let out = unsafe { std::slice::from_raw_parts_mut(out, count as usize) };
    evaluate_batch(s, o, xyz, out, ys, ym, flags);
    0
}
fn evaluate_batch(
    s: &State,
    o: &[Octave],
    xyz: &[f64],
    out: &mut [f64],
    ys: f64,
    ym: f64,
    flags: u32,
) {
    #[cfg(target_arch = "x86_64")]
    if s.kind == 4
        && ys == 0.
        && ym == 0.
        && flags == 0
        && s.params[..2]
            .iter()
            .all(|v| *v >= 0.684412 && *v <= 684412.)
        && s.params[2..4].iter().all(|v| *v >= 0.001 && *v <= 1000.)
        && s.params[4] >= 1.
        && s.params[4] <= 8.
        && is_x86_feature_detected!("avx2")
    {
        let groups = out.len() / 4;
        for group in 0..groups {
            let p = &xyz[group * 12..group * 12 + 12];
            if p.iter().all(|v| v.abs() <= 30_000_000.) {
                unsafe {
                    simd::blended4(s, o, p, &mut out[group * 4..group * 4 + 4]);
                }
            } else {
                for lane in 0..4 {
                    out[group * 4 + lane] = s.eval(
                        o,
                        p[lane * 3],
                        p[lane * 3 + 1],
                        p[lane * 3 + 2],
                        ys,
                        ym,
                        flags,
                    );
                }
            }
        }
        for i in groups * 4..out.len() {
            out[i] = s.eval(o, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], ys, ym, flags);
        }
        return;
    }
    #[cfg(target_arch = "x86_64")]
    if ys == 0.0
        && ym == 0.0
        && flags == 0
        && (1..=3).contains(&s.kind)
        && s.n1 <= 32
        && s.n2 <= 32
        && (s.kind == 1 || (s.params[0] >= 0.0 && s.params[0] <= 256.0))
        && (s.kind != 3 || (s.params[2] >= 0.0 && s.params[2] <= 256.0))
        && is_x86_feature_detected!("avx2")
    {
        let groups = out.len() / 4;
        for group in 0..groups {
            let p = &xyz[group * 12..group * 12 + 12];
            if p.iter().all(|x| x.abs() <= 30_000_000.0) {
                // Eligibility bounds keep floor casts in Java's nonsaturating range.
                unsafe {
                    simd::evaluate4(s, o, p, &mut out[group * 4..group * 4 + 4]);
                }
            } else {
                for lane in 0..4 {
                    out[group * 4 + lane] = s.eval(
                        o,
                        p[lane * 3],
                        p[lane * 3 + 1],
                        p[lane * 3 + 2],
                        ys,
                        ym,
                        flags,
                    );
                }
            }
        }
        for i in groups * 4..out.len() {
            out[i] = s.eval(o, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2], ys, ym, flags);
        }
        return;
    }
    for (v, p) in out.iter_mut().zip(xyz.chunks_exact(3)) {
        *v = s.eval(o, p[0], p[1], p[2], ys, ym, flags);
    }
}
/// # Safety
/// Valid kind=1 state and writable, nonaliasing three-double derivative accumulator.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_derivative(
    state: *const State,
    x: f64,
    y: f64,
    z: f64,
    out: *mut f64,
) -> f64 {
    unsafe {
        (&*state).octaves()[0].with_derivative(x, y, z, std::slice::from_raw_parts_mut(out, 3))
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn abi_layout() {
        assert_eq!(std::mem::size_of::<State>(), 80);
        assert_eq!(std::mem::size_of::<Octave>(), 1320);
        assert_eq!(std::mem::offset_of!(Octave, p), 40);
        assert_eq!(std::mem::offset_of!(Octave, p32), 296);
    }
    #[test]
    fn java_cast_edges() {
        assert_eq!(floor(f64::NAN), 0);
        assert_eq!(floor(f64::INFINITY), i32::MAX);
        assert_eq!(floor(f64::NEG_INFINITY), i32::MAX);
        assert_eq!(lfloor(f64::NEG_INFINITY), i64::MAX);
        assert_eq!(floor(-0.1), -1);
        assert_eq!(floor(-1.), -1);
    }
    #[test]
    fn scalar_and_vector_paths_match() {
        let mut o = Octave {
            x: 17.25,
            y: 91.5,
            z: 231.75,
            amplitude: 1.,
            present: 1,
            p: [0; 256],
            p32: [0; 256],
        };
        for i in 0..256 {
            o.p[i] = ((i * 137 + 19) & 255) as u8;
            o.p32[i] = o.p[i] as i32 | (((o.p[i] as i32) % 12) << 8);
        }
        let mut octaves = vec![o; 8];
        octaves[2].present = 0;
        octaves[6].present = 0;
        for kind in [1, 2, 3, 5, 6] {
            let mut s = State {
                kind,
                n1: if kind == 1 || kind == 5 { 1 } else { 4 },
                n2: if kind == 3 { 4 } else { 0 },
                n3: 0,
                params: [0.; 8],
            };
            s.params[..5].copy_from_slice(&[0.125, 0.5, 0.125, 0.5, 1.25]);
            let used = &octaves[..(s.n1 + s.n2) as usize];
            let mut xyz = Vec::new();
            for i in -300..300 {
                xyz.extend_from_slice(&[
                    i as f64 * 100003.25,
                    i as f64 * 0.125,
                    i as f64 * -7101.5,
                ]);
            }
            xyz.extend_from_slice(&[f64::NAN, f64::INFINITY, f64::NEG_INFINITY]);
            xyz.extend_from_slice(&[-0., 0., f64::MIN_POSITIVE]);
            let mut out = vec![0.; xyz.len() / 3];
            for flags in [0, 1] {
                evaluate_batch(&s, used, &xyz, &mut out, 0., 0., flags);
                for (i, p) in xyz.chunks_exact(3).enumerate() {
                    let a = s.eval(used, p[0], p[1], p[2], 0., 0., flags);
                    let b = eval_dispatch(&s, used, p[0], p[1], p[2], 0., 0., flags);
                    assert!(
                        a.to_bits() == out[i].to_bits() || (a.is_nan() && out[i].is_nan()),
                        "batch kind={kind} flags={flags} point={i}"
                    );
                    assert!(
                        a.to_bits() == b.to_bits() || (a.is_nan() && b.is_nan()),
                        "scalar kind={kind} flags={flags} point={i}"
                    );
                }
            }
        }
    }

    #[test]
    fn rejects_stale_permutation_encoding() {
        #[repr(C)]
        struct Fixture {
            state: State,
            octave: Octave,
        }
        let mut f = Fixture {
            state: State {
                kind: 1,
                n1: 1,
                n2: 0,
                n3: 0,
                params: [0.; 8],
            },
            octave: Octave {
                x: 0.,
                y: 0.,
                z: 0.,
                amplitude: 1.,
                present: 1,
                p: [0; 256],
                p32: [0; 256],
            },
        };
        assert_eq!(unsafe { mattmc_noise_validate(&f.state, 1400) }, 0);
        f.octave.p[0] = 1;
        assert_eq!(unsafe { mattmc_noise_validate(&f.state, 1400) }, -4);
        f.octave.p32[0] = 1 | (1 << 8);
        f.octave.x = f64::NAN;
        assert_eq!(unsafe { mattmc_noise_validate(&f.state, 1400) }, -5);
    }

    #[test]
    fn rejects_bad_state() {
        let s = State {
            kind: 9,
            n1: 0,
            n2: 0,
            n3: 0,
            params: [0.; 8],
        };
        assert_eq!(unsafe { mattmc_noise_validate(&s, 80) }, -3);
        assert_eq!(unsafe { mattmc_noise_validate(std::ptr::null(), 80) }, -1);
        assert_eq!(unsafe { mattmc_noise_validate(&s, 79) }, -1);
    }
}

#[cfg(target_arch = "x86_64")]
#[path = "noise_simd.rs"]
mod simd;

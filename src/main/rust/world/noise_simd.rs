//! Four independent samples in AVX2 lanes. Every lane uses the scalar operation
//! order; no reassociation or FMA. Dispatch/coordinate bounds live in noise.rs.
use super::{Octave, State};
use std::arch::x86_64::*;
static GX: [f64; 16] = [
    1., -1., 1., -1., 1., -1., 1., -1., 0., 0., 0., 0., 1., 0., -1., 0.,
];
static GY: [f64; 16] = [
    1., 1., -1., -1., 0., 0., 0., 0., 1., -1., 1., -1., 1., -1., 1., -1.,
];
static GZ: [f64; 16] = [
    0., 0., 0., 0., 1., 1., -1., -1., 1., 1., -1., -1., 0., 1., 0., -1.,
];
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn lookup(o: &Octave, i: __m128i) -> __m128i {
    unsafe { _mm_i32gather_epi32::<4>(o.p32.as_ptr(), _mm_and_si128(i, _mm_set1_epi32(255))) }
}
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn dot(i: __m128i, x: __m256d, y: __m256d, z: __m256d) -> __m256d {
    unsafe {
        let i = _mm_and_si128(i, _mm_set1_epi32(15));
        let mask = |m| _mm256_castsi256_pd(_mm256_cvtepi32_epi64(m));
        let u = _mm256_blendv_pd(y, x, mask(_mm_cmplt_epi32(i, _mm_set1_epi32(8))));
        let v = _mm256_blendv_pd(
            z,
            x,
            mask(_mm_or_si128(
                _mm_cmpeq_epi32(i, _mm_set1_epi32(12)),
                _mm_cmpeq_epi32(i, _mm_set1_epi32(14)),
            )),
        );
        let v = _mm256_blendv_pd(v, y, mask(_mm_cmplt_epi32(i, _mm_set1_epi32(4))));
        let sign_u = _mm256_castsi256_pd(_mm256_slli_epi64::<63>(_mm256_cvtepi32_epi64(i)));
        let sign_v = _mm256_castsi256_pd(_mm256_slli_epi64::<63>(_mm256_cvtepi32_epi64(
            _mm_srli_epi32::<1>(i),
        )));
        let result = _mm256_add_pd(_mm256_xor_pd(u, sign_u), _mm256_xor_pd(v, sign_v));
        // All inputs here are finite cell coordinates. Only zero needs the
        // otherwise unused zero term, to reproduce Java's signed zero exactly.
        if _mm256_movemask_pd(_mm256_cmp_pd::<_CMP_EQ_OQ>(result, _mm256_setzero_pd())) == 0 {
            return result;
        }
        let gx = _mm256_i32gather_pd::<8>(GX.as_ptr(), i);
        let gy = _mm256_i32gather_pd::<8>(GY.as_ptr(), i);
        let gz = _mm256_i32gather_pd::<8>(GZ.as_ptr(), i);
        _mm256_add_pd(
            _mm256_add_pd(_mm256_mul_pd(gx, x), _mm256_mul_pd(gy, y)),
            _mm256_mul_pd(gz, z),
        )
    }
}
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn smooth(x: __m256d) -> __m256d {
    _mm256_mul_pd(
        _mm256_mul_pd(_mm256_mul_pd(x, x), x),
        _mm256_add_pd(
            _mm256_mul_pd(
                x,
                _mm256_sub_pd(_mm256_mul_pd(x, _mm256_set1_pd(6.)), _mm256_set1_pd(15.)),
            ),
            _mm256_set1_pd(10.),
        ),
    )
}
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn lerp(t: __m256d, a: __m256d, b: __m256d) -> __m256d {
    _mm256_add_pd(a, _mm256_mul_pd(t, _mm256_sub_pd(b, a)))
}
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn wrap(x: __m256d) -> __m256d {
    let k = _mm256_set1_pd(33554432.);
    _mm256_sub_pd(
        x,
        _mm256_mul_pd(
            _mm256_floor_pd(_mm256_add_pd(_mm256_div_pd(x, k), _mm256_set1_pd(0.5))),
            k,
        ),
    )
}
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn noise_impl<const SCALED: bool>(
    o: &Octave,
    x: __m256d,
    y: __m256d,
    z: __m256d,
    ys: __m256d,
    ym: __m256d,
) -> __m256d {
    unsafe {
        let x = _mm256_add_pd(x, _mm256_set1_pd(o.x));
        let y = _mm256_add_pd(y, _mm256_set1_pd(o.y));
        let z = _mm256_add_pd(z, _mm256_set1_pd(o.z));
        let ix = _mm256_cvttpd_epi32(_mm256_floor_pd(x));
        let iy = _mm256_cvttpd_epi32(_mm256_floor_pd(y));
        let iz = _mm256_cvttpd_epi32(_mm256_floor_pd(z));
        let x = _mm256_sub_pd(x, _mm256_cvtepi32_pd(ix));
        let y = _mm256_sub_pd(y, _mm256_cvtepi32_pd(iy));
        let z = _mm256_sub_pd(z, _mm256_cvtepi32_pd(iz));
        let fade_y = y;
        let y = if SCALED {
            let use_max = _mm256_and_pd(
                _mm256_cmp_pd::<_CMP_GE_OQ>(ym, _mm256_setzero_pd()),
                _mm256_cmp_pd::<_CMP_LT_OQ>(ym, y),
            );
            let r = _mm256_blendv_pd(y, ym, use_max);
            // Dispatch bounds keep r/ys inside Java's nonsaturating int range.
            let snapped = _mm256_floor_pd(_mm256_add_pd(
                _mm256_div_pd(r, ys),
                _mm256_set1_pd(1.0e-7_f32 as f64),
            ));
            _mm256_sub_pd(y, _mm256_mul_pd(snapped, ys))
        } else {
            y
        };
        let one = _mm_set1_epi32(1);
        let fone = _mm256_set1_pd(1.);
        let a = lookup(o, ix);
        let b = lookup(o, _mm_add_epi32(ix, one));
        let c = lookup(o, _mm_add_epi32(a, iy));
        let d = lookup(o, _mm_add_epi32(_mm_add_epi32(a, iy), one));
        let e = lookup(o, _mm_add_epi32(b, iy));
        let f = lookup(o, _mm_add_epi32(_mm_add_epi32(b, iy), one));
        let xm = _mm256_sub_pd(x, fone);
        let ym = _mm256_sub_pd(y, fone);
        let zm = _mm256_sub_pd(z, fone);
        let v0 = dot(lookup(o, _mm_add_epi32(c, iz)), x, y, z);
        let v1 = dot(lookup(o, _mm_add_epi32(e, iz)), xm, y, z);
        let v2 = dot(lookup(o, _mm_add_epi32(d, iz)), x, ym, z);
        let v3 = dot(lookup(o, _mm_add_epi32(f, iz)), xm, ym, z);
        let iz = _mm_add_epi32(iz, one);
        let v4 = dot(lookup(o, _mm_add_epi32(c, iz)), x, y, zm);
        let v5 = dot(lookup(o, _mm_add_epi32(e, iz)), xm, y, zm);
        let v6 = dot(lookup(o, _mm_add_epi32(d, iz)), x, ym, zm);
        let v7 = dot(lookup(o, _mm_add_epi32(f, iz)), xm, ym, zm);
        let sx = smooth(x);
        let sy = smooth(fade_y);
        let sz = smooth(z);
        lerp(
            sz,
            lerp(sy, lerp(sx, v0, v1), lerp(sx, v2, v3)),
            lerp(sy, lerp(sx, v4, v5), lerp(sx, v6, v7)),
        )
    }
}
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn noise(o: &Octave, x: __m256d, y: __m256d, z: __m256d) -> __m256d {
    unsafe { noise_impl::<false>(o, x, y, z, _mm256_setzero_pd(), _mm256_setzero_pd()) }
}
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn perlin(
    o: &[Octave],
    mut frequency: f64,
    mut weight: f64,
    x: __m256d,
    y: __m256d,
    z: __m256d,
) -> __m256d {
    unsafe {
        let mut sum = _mm256_setzero_pd();
        for n in o {
            if n.present != 0 {
                let f = _mm256_set1_pd(frequency);
                let v = noise(
                    n,
                    wrap(_mm256_mul_pd(x, f)),
                    wrap(_mm256_mul_pd(y, f)),
                    wrap(_mm256_mul_pd(z, f)),
                );
                sum = _mm256_add_pd(
                    sum,
                    _mm256_mul_pd(
                        _mm256_mul_pd(_mm256_set1_pd(n.amplitude), v),
                        _mm256_set1_pd(weight),
                    ),
                );
            }
            frequency *= 2.;
            weight /= 2.;
        }
        sum
    }
}
#[target_feature(enable = "avx2")]
pub(super) unsafe fn evaluate4(s: &State, o: &[Octave], p: &[f64], out: &mut [f64]) {
    unsafe {
        let x = _mm256_setr_pd(p[0], p[3], p[6], p[9]);
        let y = _mm256_setr_pd(p[1], p[4], p[7], p[10]);
        let z = _mm256_setr_pd(p[2], p[5], p[8], p[11]);
        let result = match s.kind {
            1 => noise(&o[0], x, y, z),
            2 => perlin(o, s.params[0], s.params[1], x, y, z),
            3 => {
                let n = s.n1 as usize;
                let f = _mm256_set1_pd(1.0181268882175227);
                let a = perlin(&o[..n], s.params[0], s.params[1], x, y, z);
                let b = perlin(
                    &o[n..],
                    s.params[2],
                    s.params[3],
                    _mm256_mul_pd(x, f),
                    _mm256_mul_pd(y, f),
                    _mm256_mul_pd(z, f),
                );
                _mm256_mul_pd(_mm256_add_pd(a, b), _mm256_set1_pd(s.params[4]))
            }
            _ => unreachable!(),
        };
        _mm256_storeu_pd(out.as_mut_ptr(), result);
    }
}

#[inline]
#[target_feature(enable = "avx2")]
unsafe fn noise_octaves(
    o: &[Octave],
    indices: __m128i,
    x: __m256d,
    y: __m256d,
    z: __m256d,
) -> __m256d {
    unsafe {
        let fields = _mm_mullo_epi32(
            indices,
            _mm_set1_epi32((std::mem::size_of::<Octave>() / 8) as i32),
        );
        let base = o.as_ptr().cast::<f64>();
        let x = _mm256_add_pd(x, _mm256_i32gather_pd::<8>(base, fields));
        let y = _mm256_add_pd(y, _mm256_i32gather_pd::<8>(base.add(1), fields));
        let z = _mm256_add_pd(z, _mm256_i32gather_pd::<8>(base.add(2), fields));
        let tables = _mm_mullo_epi32(
            indices,
            _mm_set1_epi32((std::mem::size_of::<Octave>() / 4) as i32),
        );
        let lookup = |_: &[Octave], i| {
            _mm_i32gather_epi32::<4>(
                o[0].p32.as_ptr(),
                _mm_add_epi32(tables, _mm_and_si128(i, _mm_set1_epi32(255))),
            )
        };
        let ix = _mm256_cvttpd_epi32(_mm256_floor_pd(x));
        let iy = _mm256_cvttpd_epi32(_mm256_floor_pd(y));
        let iz = _mm256_cvttpd_epi32(_mm256_floor_pd(z));
        let x = _mm256_sub_pd(x, _mm256_cvtepi32_pd(ix));
        let y = _mm256_sub_pd(y, _mm256_cvtepi32_pd(iy));
        let z = _mm256_sub_pd(z, _mm256_cvtepi32_pd(iz));
        let one = _mm_set1_epi32(1);
        let fone = _mm256_set1_pd(1.);
        let a = lookup(o, ix);
        let b = lookup(o, _mm_add_epi32(ix, one));
        let c = lookup(o, _mm_add_epi32(a, iy));
        let d = lookup(o, _mm_add_epi32(_mm_add_epi32(a, iy), one));
        let e = lookup(o, _mm_add_epi32(b, iy));
        let f = lookup(o, _mm_add_epi32(_mm_add_epi32(b, iy), one));
        let xm = _mm256_sub_pd(x, fone);
        let ym = _mm256_sub_pd(y, fone);
        let zm = _mm256_sub_pd(z, fone);
        let v0 = dot(lookup(o, _mm_add_epi32(c, iz)), x, y, z);
        let v1 = dot(lookup(o, _mm_add_epi32(e, iz)), xm, y, z);
        let v2 = dot(lookup(o, _mm_add_epi32(d, iz)), x, ym, z);
        let v3 = dot(lookup(o, _mm_add_epi32(f, iz)), xm, ym, z);
        let iz = _mm_add_epi32(iz, one);
        let v4 = dot(lookup(o, _mm_add_epi32(c, iz)), x, y, zm);
        let v5 = dot(lookup(o, _mm_add_epi32(e, iz)), xm, y, zm);
        let v6 = dot(lookup(o, _mm_add_epi32(d, iz)), x, ym, zm);
        let v7 = dot(lookup(o, _mm_add_epi32(f, iz)), xm, ym, zm);
        let sx = smooth(x);
        let sy = smooth(y);
        let sz = smooth(z);
        lerp(
            sz,
            lerp(sy, lerp(sx, v0, v1), lerp(sx, v2, v3)),
            lerp(sy, lerp(sx, v4, v5), lerp(sx, v6, v7)),
        )
    }
}

// Vectorize octave evaluation but reduce in the original Java octave order.
#[target_feature(enable = "avx2")]
unsafe fn perlin_single(
    o: &[Octave],
    mut frequency: f64,
    mut weight: f64,
    x: f64,
    y: f64,
    z: f64,
) -> f64 {
    unsafe {
        let mut sum = 0.;
        for start in (0..o.len()).step_by(4) {
            let count = (o.len() - start).min(4);
            let mut f = [0.; 4];
            let mut w = [0.; 4];
            let mut a = [0.; 4];
            let mut indices = [0; 4];
            for lane in 0..count {
                indices[lane] = (start + lane) as i32;
                f[lane] = frequency;
                w[lane] = weight;
                a[lane] = o[start + lane].amplitude;
                frequency *= 2.;
                weight /= 2.;
            }
            if count < 3
                || o[start..start + count]
                    .iter()
                    .filter(|n| n.present != 0)
                    .count()
                    < 3
            {
                for lane in 0..count {
                    let n = &o[start + lane];
                    if n.present != 0 {
                        sum += n.amplitude
                            * bounded_noise(
                                n,
                                bounded_wrap(x * f[lane]),
                                bounded_wrap(y * f[lane]),
                                bounded_wrap(z * f[lane]),
                                0.,
                                0.,
                            )
                            * w[lane];
                    }
                }
                continue;
            }
            // Unused lanes duplicate a valid index and never contribute to the sum.
            let freq = _mm256_loadu_pd(f.as_ptr());
            let v = noise_octaves(
                o,
                _mm_loadu_si128(indices.as_ptr().cast()),
                wrap(_mm256_mul_pd(_mm256_set1_pd(x), freq)),
                wrap(_mm256_mul_pd(_mm256_set1_pd(y), freq)),
                wrap(_mm256_mul_pd(_mm256_set1_pd(z), freq)),
            );
            let weighted = _mm256_mul_pd(
                _mm256_mul_pd(_mm256_loadu_pd(a.as_ptr()), v),
                _mm256_loadu_pd(w.as_ptr()),
            );
            let mut values = [0.; 4];
            _mm256_storeu_pd(values.as_mut_ptr(), weighted);
            for lane in 0..count {
                if o[start + lane].present != 0 {
                    sum += values[lane];
                }
            }
        }
        sum
    }
}
#[target_feature(enable = "avx2")]
pub(super) unsafe fn evaluate_single(s: &State, o: &[Octave], x: f64, y: f64, z: f64) -> f64 {
    unsafe {
        if s.kind == 2 {
            return perlin_single(o, s.params[0], s.params[1], x, y, z);
        }
        let n = s.n1 as usize;
        let a = perlin_single(&o[..n], s.params[0], s.params[1], x, y, z);
        let f = 1.0181268882175227;
        let b = perlin_single(&o[n..], s.params[2], s.params[3], x * f, y * f, z * f);
        (a + b) * s.params[4]
    }
}

#[inline]
#[target_feature(enable = "avx2")]
unsafe fn bounded_noise(o: &Octave, x: f64, y: f64, z: f64, ys: f64, ym: f64) -> f64 {
    let x = x + o.x;
    let y = y + o.y;
    let z = z + o.z;
    let fx = x.floor();
    let fy = y.floor();
    let fz = z.floor();
    // Caller checks finite coordinate/frequency bounds before this path.
    let (ix, iy, iz) = unsafe {
        (
            fx.to_int_unchecked::<i32>(),
            fy.to_int_unchecked::<i32>(),
            fz.to_int_unchecked::<i32>(),
        )
    };
    let dx = x - ix as f64;
    let dy = y - iy as f64;
    let dz = z - iz as f64;
    let shift = if ys != 0. {
        let r = if ym >= 0. && ym < dy { ym } else { dy };
        super::floor(r / ys + 1.0e-7_f32 as f64) as f64 * ys
    } else {
        0.
    };
    let ey = dy - shift;
    let c = o.corners(ix, iy, iz);
    let v = std::array::from_fn(|n| {
        super::dot(
            c[n],
            dx - (n & 1) as f64,
            ey - ((n >> 1) & 1) as f64,
            dz - (n >> 2) as f64,
        )
    });
    super::lerp3(super::smooth(dx), super::smooth(dy), super::smooth(dz), v)
}
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn bounded_wrap(x: f64) -> f64 {
    x - (x / 33554432. + 0.5).floor() * 33554432.
}
#[inline]
#[target_feature(enable = "avx2")]
unsafe fn bounded_perlin(
    o: &[Octave],
    mut frequency: f64,
    mut weight: f64,
    x: f64,
    y: f64,
    z: f64,
) -> f64 {
    let mut sum = 0.;
    for n in o {
        if n.present != 0 {
            unsafe {
                sum += n.amplitude
                    * bounded_noise(
                        n,
                        bounded_wrap(x * frequency),
                        bounded_wrap(y * frequency),
                        bounded_wrap(z * frequency),
                        0.,
                        0.,
                    )
                    * weight;
            }
        }
        frequency *= 2.;
        weight /= 2.;
    }
    sum
}
#[target_feature(enable = "avx2")]
pub(super) unsafe fn evaluate_bounded(s: &State, o: &[Octave], x: f64, y: f64, z: f64) -> f64 {
    unsafe {
        match s.kind {
            1 => bounded_noise(&o[0], x, y, z, 0., 0.),
            2 => bounded_perlin(o, s.params[0], s.params[1], x, y, z),
            3 => {
                let n = s.n1 as usize;
                let f = 1.0181268882175227;
                (bounded_perlin(&o[..n], s.params[0], s.params[1], x, y, z)
                    + bounded_perlin(&o[n..], s.params[2], s.params[3], x * f, y * f, z * f))
                    * s.params[4]
            }
            _ => unreachable!(),
        }
    }
}

#[target_feature(enable = "avx2")]
pub(super) unsafe fn blended(s: &State, o: &[Octave], x: f64, y: f64, z: f64) -> f64 {
    let d = x * s.params[0];
    let e = y * s.params[1];
    let f = z * s.params[0];
    let g = d / s.params[2];
    let h = e / s.params[3];
    let i = f / s.params[2];
    let j = s.params[1] * s.params[4];
    let k = j / s.params[3];
    let mut a = 0.;
    let mut b = 0.;
    let mut c = 0.;
    let mut freq = 1.;
    for n in o[32..40].iter().rev() {
        if n.present != 0 {
            c += bounded_noise(
                n,
                bounded_wrap(g * freq),
                bounded_wrap(h * freq),
                bounded_wrap(i * freq),
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
        let x = bounded_wrap(d * freq);
        let y = bounded_wrap(e * freq);
        let z = bounded_wrap(f * freq);
        let ys = j * freq;
        let n = &o[15 - r];
        if !hi && n.present != 0 {
            a += bounded_noise(n, x, y, z, ys, e * freq) / freq;
        }
        let n = &o[31 - r];
        if !lo && n.present != 0 {
            b += bounded_noise(n, x, y, z, ys, e * freq) / freq;
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
        super::lerp(q, a, b)
    }) / 128.
}
#[inline(always)]
fn bounded_floor(x: f64) -> i32 {
    unsafe { x.floor().to_int_unchecked::<i32>() }
}

#[inline(always)]
unsafe fn simplex2(o: &Octave, x: f64, y: f64) -> f64 {
    // Java's compile-time Math.sqrt(3.0) rounded constants.
    let f2 = 0.3660254037844386;
    let g2 = 0.21132486540518713;
    let f = (x + y) * f2;
    let i = bounded_floor(x + f);
    let j = bounded_floor(y + f);
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
    let p = o.p12(i + o.p(j));
    let q = o.p12(i + u + o.p(j + v));
    let r = o.p12(i + 1 + o.p(j + 1));
    70.0 * (corner2(p, a, b) + corner2(q, c, d) + corner2(r, e, f))
}

#[inline(always)]
unsafe fn simplex3(octave: &Octave, x: f64, y: f64, z: f64) -> f64 {
    let s = (x + y + z) * 0.3333333333333333;
    let i = bounded_floor(x + s);
    let j = bounded_floor(y + s);
    let k = bounded_floor(z + s);
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
    let ga = octave.p12(i + octave.p(j + octave.p(k)));
    let gb = octave.p12(i + u + octave.p(j + v + octave.p(k + w)));
    let gc = octave.p12(i + p + octave.p(j + q + octave.p(k + r)));
    let gd = octave.p12(i + 1 + octave.p(j + 1 + octave.p(k + 1)));
    32.0 * (Octave::corner(ga, a, b, c, 0.6)
        + Octave::corner(gb, d, e, f, 0.6)
        + Octave::corner(gc, g, h, l, 0.6)
        + Octave::corner(gd, m, n, o, 0.6))
}
#[target_feature(enable = "avx2")]
pub(super) unsafe fn evaluate_simplex(
    s: &State,
    o: &[Octave],
    x: f64,
    y: f64,
    z: f64,
    flags: u32,
) -> f64 {
    unsafe {
        if s.kind == 5 {
            return if flags == 0 {
                simplex2(&o[0], x, y)
            } else {
                simplex3(&o[0], x, y, z)
            };
        }
        let mut sum = 0.;
        let mut frequency = s.params[0];
        let mut weight = s.params[1];
        for n in o {
            if n.present != 0 {
                sum += simplex2(
                    n,
                    x * frequency + if flags != 0 { n.x } else { 0. },
                    y * frequency + if flags != 0 { n.y } else { 0. },
                ) * weight;
            }
            frequency /= 2.;
            weight *= 2.;
        }
        sum
    }
}

#[inline(always)]
fn corner2(g: i32, x: f64, y: f64) -> f64 {
    let h = 0.5 - x * x - y * y;
    if h < 0. {
        return 0.;
    }
    let v = super::GRAD[g as usize];
    let mut r = v[0] * x + v[1] * y;
    if r == 0. {
        r += v[2] * 0.;
    }
    let h = h * h;
    h * h * r
}

#[target_feature(enable = "avx2")]
pub(super) unsafe fn sample_simplex2(o: &Octave, x: f64, y: f64) -> f64 {
    unsafe { simplex2(o, x, y) }
}

/// Four independent BlendedNoise evaluations. Octave accumulation stays ordered
/// within each lane; branch masks preserve skipped accumulators and q==0/1 rules.
#[target_feature(enable = "avx2")]
pub(super) unsafe fn blended4(s: &State, o: &[Octave], p: &[f64], out: &mut [f64]) {
    unsafe {
        let splat = |v| _mm256_set1_pd(v);
        let zero = _mm256_setzero_pd();
        let one = splat(1.);
        let d = _mm256_mul_pd(_mm256_setr_pd(p[0], p[3], p[6], p[9]), splat(s.params[0]));
        let e = _mm256_mul_pd(_mm256_setr_pd(p[1], p[4], p[7], p[10]), splat(s.params[1]));
        let f = _mm256_mul_pd(_mm256_setr_pd(p[2], p[5], p[8], p[11]), splat(s.params[0]));
        let g = _mm256_div_pd(d, splat(s.params[2]));
        let h = _mm256_div_pd(e, splat(s.params[3]));
        let i = _mm256_div_pd(f, splat(s.params[2]));
        let j = s.params[1] * s.params[4];
        let k = j / s.params[3];
        let mut c = zero;
        let mut frequency = 1.;
        for n in o[32..40].iter().rev() {
            if n.present != 0 {
                let freq = splat(frequency);
                let value = noise_impl::<true>(
                    n,
                    wrap(_mm256_mul_pd(g, freq)),
                    wrap(_mm256_mul_pd(h, freq)),
                    wrap(_mm256_mul_pd(i, freq)),
                    splat(k * frequency),
                    _mm256_mul_pd(h, freq),
                );
                c = _mm256_add_pd(c, _mm256_div_pd(value, freq));
            }
            frequency /= 2.;
        }
        let q = _mm256_div_pd(_mm256_add_pd(_mm256_div_pd(c, splat(10.)), one), splat(2.));
        let active_a = _mm256_cmp_pd::<_CMP_NGE_UQ>(q, one);
        let active_b = _mm256_cmp_pd::<_CMP_NLE_UQ>(q, zero);
        let mut a = zero;
        let mut b = zero;
        frequency = 1.;
        for r in 0..16 {
            let freq = splat(frequency);
            let x = wrap(_mm256_mul_pd(d, freq));
            let y = wrap(_mm256_mul_pd(e, freq));
            let z = wrap(_mm256_mul_pd(f, freq));
            let ys = splat(j * frequency);
            let ym = _mm256_mul_pd(e, freq);
            if o[15 - r].present != 0 && _mm256_movemask_pd(active_a) != 0 {
                let value = _mm256_div_pd(noise_impl::<true>(&o[15 - r], x, y, z, ys, ym), freq);
                a = _mm256_blendv_pd(a, _mm256_add_pd(a, value), active_a);
            }
            if o[31 - r].present != 0 && _mm256_movemask_pd(active_b) != 0 {
                let value = _mm256_div_pd(noise_impl::<true>(&o[31 - r], x, y, z, ys, ym), freq);
                b = _mm256_blendv_pd(b, _mm256_add_pd(b, value), active_b);
            }
            frequency /= 2.;
        }
        a = _mm256_div_pd(a, splat(512.));
        b = _mm256_div_pd(b, splat(512.));
        let value = lerp(q, a, b);
        let value = _mm256_blendv_pd(value, a, _mm256_cmp_pd::<_CMP_LT_OQ>(q, zero));
        let value = _mm256_blendv_pd(value, b, _mm256_cmp_pd::<_CMP_GT_OQ>(q, one));
        _mm256_storeu_pd(out.as_mut_ptr(), _mm256_div_pd(value, splat(128.)));
    }
}

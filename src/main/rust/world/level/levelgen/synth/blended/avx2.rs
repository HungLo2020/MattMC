//! Blended scalar specialization and four-sample AVX2 batches.
use super::super::{
    avx2_helpers::*,
    state::{Octave, State},
};
use super::super::{
    improved::avx2::{bounded_noise, noise_impl},
    perlin::avx2::bounded_wrap,
};
use std::arch::x86_64::*;

#[target_feature(enable = "avx2")]
pub(crate) unsafe fn blended(s: &State, o: &[Octave], x: f64, y: f64, z: f64) -> f64 {
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
        super::super::super::math::lerp(q, a, b)
    }) / 128.
}
/// Four independent BlendedNoise evaluations. Octave accumulation stays ordered
/// within each lane; branch masks preserve skipped accumulators and q==0/1 rules.
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn blended4(s: &State, o: &[Octave], p: &[f64], out: &mut [f64]) {
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

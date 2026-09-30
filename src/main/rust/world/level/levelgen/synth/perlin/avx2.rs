//! Perlin vector samples and octave lanes with ordered reduction.
use super::super::improved::avx2::{bounded_noise, noise, noise_octaves};
use super::super::{avx2_helpers::*, state::Octave};
use std::arch::x86_64::*;
#[inline]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn perlin(
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
// Vectorize octave evaluation but reduce in the original Java octave order.
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn perlin_single(
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
#[inline]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn bounded_wrap(x: f64) -> f64 {
    x - (x / 33554432. + 0.5).floor() * 33554432.
}
#[inline]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn bounded_perlin(
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

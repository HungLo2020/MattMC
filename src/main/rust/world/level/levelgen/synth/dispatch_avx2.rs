//! Family dispatch within the guarded AVX2 route.
use super::{
    improved::avx2::{bounded_noise, noise},
    perlin::avx2::{bounded_perlin, perlin, perlin_single},
    state::{Octave, State},
};
use std::arch::x86_64::*;
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn evaluate4(s: &State, o: &[Octave], p: &[f64], out: &mut [f64]) {
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

#[target_feature(enable = "avx2")]
pub(crate) unsafe fn evaluate_single(s: &State, o: &[Octave], x: f64, y: f64, z: f64) -> f64 {
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

#[target_feature(enable = "avx2")]
pub(crate) unsafe fn evaluate_bounded(s: &State, o: &[Octave], x: f64, y: f64, z: f64) -> f64 {
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

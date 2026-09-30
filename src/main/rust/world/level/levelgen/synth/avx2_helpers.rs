//! Shared AVX2 gradient and interpolation primitives.
use super::state::Octave;
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
pub(crate) unsafe fn lookup(o: &Octave, i: __m128i) -> __m128i {
    unsafe { _mm_i32gather_epi32::<4>(o.p32.as_ptr(), _mm_and_si128(i, _mm_set1_epi32(255))) }
}
#[inline]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn dot(i: __m128i, x: __m256d, y: __m256d, z: __m256d) -> __m256d {
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
pub(crate) unsafe fn smooth(x: __m256d) -> __m256d {
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
pub(crate) unsafe fn lerp(t: __m256d, a: __m256d, b: __m256d) -> __m256d {
    _mm256_add_pd(a, _mm256_mul_pd(t, _mm256_sub_pd(b, a)))
}
#[inline]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn wrap(x: __m256d) -> __m256d {
    let k = _mm256_set1_pd(33554432.);
    _mm256_sub_pd(
        x,
        _mm256_mul_pd(
            _mm256_floor_pd(_mm256_add_pd(_mm256_div_pd(x, k), _mm256_set1_pd(0.5))),
            k,
        ),
    )
}

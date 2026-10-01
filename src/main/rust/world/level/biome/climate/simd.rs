use super::Node;
use std::arch::x86_64::*;

// Low 64 bits of a square: low^2 + 2*low*high*2^32. This preserves
// Java long overflow even for values outside the usual climate range.
#[target_feature(enable = "avx2")]
unsafe fn square(v: __m256i) -> __m256i {
    _mm256_add_epi64(
        _mm256_mul_epu32(v, v),
        _mm256_slli_epi64::<33>(_mm256_mul_epu32(v, _mm256_srli_epi64::<32>(v))),
    )
}

#[target_feature(enable = "avx2")]
pub(super) unsafe fn distance(n: &Node, point: &[i64; 8]) -> i64 {
    let mut sum = _mm256_setzero_si256();
    for offset in [0, 4] {
        let p = unsafe { _mm256_loadu_si256(point.as_ptr().add(offset).cast()) };
        let lo = unsafe { _mm256_loadu_si256(n.min.as_ptr().add(offset).cast()) };
        let hi = unsafe { _mm256_loadu_si256(n.max.as_ptr().add(offset).cast()) };
        let a = _mm256_sub_epi64(p, hi);
        let b = _mm256_sub_epi64(lo, p);
        let zero = _mm256_setzero_si256();
        let positive_b = _mm256_and_si256(b, _mm256_cmpgt_epi64(b, zero));
        let d = _mm256_blendv_epi8(positive_b, a, _mm256_cmpgt_epi64(a, zero));
        sum = _mm256_add_epi64(sum, unsafe { square(d) });
    }
    let mut lanes = [0i64; 4];
    unsafe { _mm256_storeu_si256(lanes.as_mut_ptr().cast(), sum) };
    lanes.into_iter().fold(0i64, i64::wrapping_add)
}

#[target_feature(enable = "avx2")]
pub(super) unsafe fn lookup(nodes: &[Node], point: &[i64; 8], previous: i32) -> i32 {
    unsafe { super::search::vector_lookup(nodes, point, previous) }
}

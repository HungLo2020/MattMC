use std::arch::x86_64::*;

/// Wrapping integer distances. Floating-point evaluation is untouched.
#[target_feature(enable = "avx2")]
pub(super) unsafe fn distances(c: &super::Prepared, x: i32, y: i32, z: i32, out: &mut [i32; 16]) {
    let x = _mm256_set1_epi32(x);
    let y = _mm256_set1_epi32(y);
    let z = _mm256_set1_epi32(z);
    for offset in [0, 8] {
        let dx = _mm256_sub_epi32(_mm256_loadu_si256(c.x.as_ptr().add(offset).cast()), x);
        let dy = _mm256_sub_epi32(_mm256_loadu_si256(c.y.as_ptr().add(offset).cast()), y);
        let dz = _mm256_sub_epi32(_mm256_loadu_si256(c.z.as_ptr().add(offset).cast()), z);
        let d = _mm256_add_epi32(
            _mm256_add_epi32(_mm256_mullo_epi32(dx, dx), _mm256_mullo_epi32(dy, dy)),
            _mm256_mullo_epi32(dz, dz),
        );
        _mm256_storeu_si256(out.as_mut_ptr().add(offset).cast(), d);
    }
}

//! Cell interpolation and exact-order evaluation.
use super::*;
#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2")]
pub(crate) unsafe fn unary_cell_array_avx2<const ADD: bool>(
    n: &[Node],
    frame: &[f64],
    output: *mut f64,
    start: u32,
    count: u32,
    width: u32,
    height: u32,
    add: f64,
) -> i64 {
    use std::arch::x86_64::*;
    let at = n[0].a as usize * 8;
    let v = &frame[at..at + 8];
    let mut fractions = [0.; 64];
    for i in 0..width as usize {
        fractions[i] = i as f64 / width as f64;
    }
    let factor = _mm256_set1_pd(n[1].p);
    let low = _mm256_set1_pd(-1.);
    let high = _mm256_set1_pd(1.);
    let mut j = 0;
    while j < count {
        let index = start + j;
        let ix = (index / width) % width;
        let iy = height - 1 - index / (width * width);
        let mut iz = index % width;
        let x = fractions[ix as usize];
        let y = iy as f64 / height as f64;
        let a = crate::world::level::levelgen::math::lerp2(x, y, v[0], v[1], v[2], v[3]);
        let b = crate::world::level::levelgen::math::lerp2(x, y, v[4], v[5], v[6], v[7]);
        let end = j + (width - iz).min(count - j);
        let av = _mm256_set1_pd(a);
        let delta = _mm256_set1_pd(b - a);
        while j + 4 <= end {
            let z = unsafe { _mm256_loadu_pd(fractions.as_ptr().add(iz as usize)) };
            let value = _mm256_mul_pd(_mm256_add_pd(av, _mm256_mul_pd(z, delta)), factor);
            let value = _mm256_blendv_pd(value, low, _mm256_cmp_pd::<_CMP_LT_OQ>(value, low));
            let value = _mm256_blendv_pd(value, high, _mm256_cmp_pd::<_CMP_GT_OQ>(value, high));
            let cube = _mm256_mul_pd(_mm256_mul_pd(value, value), value);
            let value = _mm256_sub_pd(
                _mm256_div_pd(value, _mm256_set1_pd(2.)),
                _mm256_div_pd(cube, _mm256_set1_pd(24.)),
            );
            let value = if ADD {
                _mm256_add_pd(value, _mm256_set1_pd(add))
            } else {
                value
            };
            unsafe {
                _mm256_storeu_pd(output.add(j as usize), value);
            }
            j += 4;
            iz += 4;
        }
        while j < end {
            let value = crate::world::level::levelgen::math::lerp(fractions[iz as usize], a, b);
            let value = math(20, math(12, value, 0., n[1].p, 0.), 0., 0., 0.);
            unsafe {
                *output.add(j as usize) = if ADD { value + add } else { value };
            }
            j += 1;
            iz += 1;
        }
    }
    (3i64 << 32) | (start as i64 + count as i64 - 1)
}

//! Exact four-lane predicates. Inputs are ABI-validated; caller checks AVX2.
use std::arch::x86_64::*;

#[target_feature(enable = "avx2")]
pub(super) unsafe fn evaluate<const N: usize>(
    f: &[i32],
    s: &[f64],
    widths: &[f32],
    out: &mut [u64],
) {
    let height = (f[5] as i64 - f[4] as i64) as usize;
    let words = height.div_ceil(64);
    let mut ys = [0.0_f64; N];
    let mut factors = [0.0_f64; N];
    for iy in 0..height {
        let y = f[5] as i64 - iy as i64;
        let normalized = (y as f64 - 0.5 - s[1]) / s[4];
        ys[iy] = normalized * normalized;
        ys[iy] /= 6.0;
        factors[iy] = widths[(y - f[8] as i64 - 1) as usize] as f64;
    }
    out.fill(0);
    let one = _mm256_set1_pd(1.0);
    let mut col = 0;
    for x in f[2]..=f[3] {
        let w = (f[0].wrapping_add(x) as f64 + 0.5 - s[0]) / s[3];
        let w2 = w * w;
        for z in f[6]..=f[7] {
            let q = (f[1].wrapping_add(z) as f64 + 0.5 - s[2]) / s[3];
            let q2 = q * q;
            let horizontal = w2 + q2;
            if !(horizontal >= 1.0) {
                let vh = _mm256_set1_pd(horizontal);
                for word in 0..words {
                    let first = word * 64;
                    let end = height.min(first + 64);
                    let mut bits = 0_u64;
                    let mut iy = first;
                    while iy + 4 <= end {
                        let vy = _mm256_loadu_pd(ys.as_ptr().add(iy));
                        let vf = _mm256_loadu_pd(factors.as_ptr().add(iy));
                        let value = _mm256_add_pd(_mm256_mul_pd(vh, vf), vy);
                        // Java uses !(value >= 1), including unordered comparisons.
                        let accepted = _mm256_cmp_pd::<_CMP_NGE_UQ>(value, one);
                        bits |= (_mm256_movemask_pd(accepted) as u64) << (iy - first);
                        iy += 4;
                    }
                    while iy < end {
                        let value = horizontal * factors[iy] + ys[iy];
                        if !(value >= 1.0) {
                            bits |= 1 << (iy - first);
                        }
                        iy += 1;
                    }
                    out[col * words + word] = bits;
                }
            }
            col += 1;
        }
    }
}

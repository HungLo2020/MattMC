/// Original Java X/Z/descending-Y shape decisions, packed by column.
/// All dimensions and factor indices have been validated by the ABI adapter.
#[cfg(target_arch = "x86_64")]
static AVX2: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

/// Ordinary calls may initialize std CPU detection; heap-borrowing calls only
/// read our dispatch flag and otherwise use the scalar evaluator.
pub(super) fn initialize() -> i32 {
    #[cfg(target_arch = "x86_64")]
    {
        let enabled = std::is_x86_feature_detected!("avx2");
        AVX2.store(enabled, std::sync::atomic::Ordering::Relaxed);
        enabled as i32
    }
    #[cfg(not(target_arch = "x86_64"))]
    {
        0
    }
}
pub(super) fn evaluate(frame: &[i32], shape: &[f64], widths: &[f32], output: &mut [u64]) {
    #[cfg(target_arch = "x86_64")]
    if AVX2.load(std::sync::atomic::Ordering::Relaxed) {
        // Feature check dominates both specialized calls; no FMA is enabled.
        unsafe {
            if frame[5] as i64 - frame[4] as i64 <= 64 {
                super::simd::evaluate::<64>(frame, shape, widths, output);
            } else {
                super::simd::evaluate::<2048>(frame, shape, widths, output);
            }
        }
        return;
    }
    scalar(frame, shape, widths, output);
}
pub(super) fn scalar(frame: &[i32], shape: &[f64], widths: &[f32], output: &mut [u64]) {
    if frame[5] as i64 - frame[4] as i64 <= 64 {
        evaluate_bounded::<64>(frame, shape, widths, output);
    } else {
        evaluate_bounded::<2048>(frame, shape, widths, output);
    }
}
fn evaluate_bounded<const N: usize>(
    frame: &[i32],
    shape: &[f64],
    widths: &[f32],
    output: &mut [u64],
) {
    let height = (frame[5] as i64 - frame[4] as i64) as usize;
    let words = height.div_ceil(64);
    let mut ys = [0.0_f64; N];
    for iy in 0..height {
        let y = (frame[5] as i64 - iy as i64) as f64;
        let normalized = (y - 0.5 - shape[1]) / shape[4];
        ys[iy] = normalized * normalized;
        ys[iy] /= 6.0;
    }
    output.fill(0);
    let mut column = 0;
    for x in frame[2]..=frame[3] {
        let wx = frame[0].wrapping_add(x);
        let w = (wx as f64 + 0.5 - shape[0]) / shape[3];
        let w2 = w * w;
        for z in frame[6]..=frame[7] {
            let wz = frame[1].wrapping_add(z);
            let q = (wz as f64 + 0.5 - shape[2]) / shape[3];
            let q2 = q * q;
            let horizontal = w2 + q2;
            if !(horizontal >= 1.0) {
                for iy in 0..height {
                    let y = frame[5] as i64 - iy as i64;
                    let index = (y - frame[8] as i64 - 1) as usize;
                    let skip = horizontal * widths[index] as f64 + ys[iy] >= 1.0;
                    if !skip {
                        output[column * words + iy / 64] |= 1 << (iy % 64);
                    }
                }
            }
            column += 1;
        }
    }
}

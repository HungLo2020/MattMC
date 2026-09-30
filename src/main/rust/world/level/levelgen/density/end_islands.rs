//! End island density policy; preserves Java integer and float semantics.
use super::super::synth::{sample_simplex2, State};
/// Complete bounded End island-height expression. Preserves Java int wrapping,
/// float arithmetic, double sqrt followed by float rounding, and NaN propagation.
/// # Safety
/// `state` is a live immutable validated kind-5 state containing one octave.
pub(crate) unsafe fn noise_end_island(state: *const State, x: i32, z: i32) -> f32 {
    let octave = &unsafe { (&*state).octaves() }[0];
    let sqrt = |v: f32| (v as f64).sqrt() as f32;
    let clamp = |v: f32| {
        if v < -100. {
            -100.
        } else if v > 80. {
            80.
        } else {
            v
        }
    };
    let k = x / 2;
    let l = z / 2;
    let m = x % 2;
    let n = z % 2;
    let mut height =
        clamp(100.0 - sqrt(x.wrapping_mul(x).wrapping_add(z.wrapping_mul(z)) as f32) * 8.0);
    // Java Math.max propagates this NaN through every iteration. Sampling is
    // pure and consumes no RNG state, so no iteration can change the result.
    if height.is_nan() {
        return height;
    }
    // If the farthest corner is inside the exclusion circle, the original
    // loop's strict >4096 condition is false at every point, for every seed.
    let far_x = ((k as i64) - 12).abs().max(((k as i64) + 12).abs());
    let far_z = ((l as i64) - 12).abs().max(((l as i64) + 12).abs());
    if far_x * far_x + far_z * far_z <= 4096 {
        return height;
    }
    for dx in -12i32..=12 {
        for dz in -12i32..=12 {
            let q = k.wrapping_add(dx) as i64;
            let r = l.wrapping_add(dz) as i64;
            if q.wrapping_mul(q).wrapping_add(r.wrapping_mul(r)) > 4096
                && sample_simplex2(octave, q as f64, r as f64) < (-0.9f32) as f64
            {
                let factor = ((q as f32).abs() * 3439.0 + (r as f32).abs() * 147.0) % 13.0 + 9.0;
                let a = (m - dx * 2) as f32;
                let b = (n - dz * 2) as f32;
                let candidate = clamp(100.0 - sqrt(a * a + b * b) * factor);
                // Java Math.max propagates NaNs and prefers positive zero.
                height = if height.is_nan() {
                    height
                } else if candidate.is_nan() {
                    candidate
                } else if height == 0. && candidate == 0. {
                    f32::from_bits(height.to_bits() & candidate.to_bits())
                } else if height >= candidate {
                    height
                } else {
                    candidate
                };
            }
        }
    }
    height
}

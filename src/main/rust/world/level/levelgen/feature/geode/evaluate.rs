use super::super::super::synth::{noise_eval, State};

#[repr(C)]
#[derive(Clone, Copy)]
pub(super) struct Point {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub offset: i32,
}

#[inline]
fn accumulate(points: &[Point], x: f64, y: f64, z: f64, noise: f64) -> f64 {
    let mut value = 0.0;
    for p in points {
        let dx = x - p.x as f64;
        let dy = y - p.y as f64;
        let dz = z - p.z as f64;
        // Keep Java Vec3i.distSqr(), JOML invsqrt(), and += association exact.
        let distance = dx * dx + dy * dy + dz * dz;
        value += 1.0 / (distance + p.offset as f64).sqrt() + noise;
    }
    value
}

/// # Safety
/// Noise state is validated, immutable and live for the complete call.
pub(super) unsafe fn evaluate(
    state: *const State,
    minimum: [i32; 3],
    dims: [usize; 3],
    points: &[Point],
    cracks: &[Point],
    multiplier: f64,
    output: &mut [f64],
) {
    let mut at = 0;
    for iz in 0..dims[2] {
        let z = (minimum[2] as i64 + iz as i64) as f64;
        for iy in 0..dims[1] {
            let y = (minimum[1] as i64 + iy as i64) as f64;
            for ix in 0..dims[0] {
                let x = (minimum[0] as i64 + ix as i64) as f64;
                let noise = noise_eval(state, x, y, z, 0.0, 0.0, 0) * multiplier;
                output[at] = accumulate(points, x, y, z, noise);
                output[at + 1] = accumulate(cracks, x, y, z, noise);
                at += 2;
            }
        }
    }
}

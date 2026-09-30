//! Exact-order interpolation shared by generation kernels.
#[inline(always)]
pub(crate) fn lerp(t: f64, a: f64, b: f64) -> f64 {
    a + t * (b - a)
}
#[inline(always)]
pub(crate) fn lerp2(x: f64, y: f64, a: f64, b: f64, c: f64, d: f64) -> f64 {
    lerp(y, lerp(x, a, b), lerp(x, c, d))
}
#[inline(always)]
pub(crate) fn lerp3(x: f64, y: f64, z: f64, v: [f64; 8]) -> f64 {
    lerp(
        z,
        lerp2(x, y, v[0], v[1], v[2], v[3]),
        lerp2(x, y, v[4], v[5], v[6], v[7]),
    )
}

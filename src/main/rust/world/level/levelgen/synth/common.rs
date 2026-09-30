//! Shared gradients and Java-compatible numerical primitives.
pub(crate) const GRAD: [[f64; 3]; 16] = [
    [1., 1., 0.],
    [-1., 1., 0.],
    [1., -1., 0.],
    [-1., -1., 0.],
    [1., 0., 1.],
    [-1., 0., 1.],
    [1., 0., -1.],
    [-1., 0., -1.],
    [0., 1., 1.],
    [0., -1., 1.],
    [0., 1., -1.],
    [0., -1., -1.],
    [1., 1., 0.],
    [0., -1., 1.],
    [-1., 1., 0.],
    [0., -1., -1.],
];
// Rust saturating casts match Java casts; subtraction at MIN_VALUE must wrap.
#[inline(always)]
pub(crate) fn floor(x: f64) -> i32 {
    let i = x as i32;
    if x < i as f64 {
        i.wrapping_sub(1)
    } else {
        i
    }
}
#[inline(always)]
pub(crate) fn lfloor(x: f64) -> i64 {
    let i = x as i64;
    if x < i as f64 {
        i.wrapping_sub(1)
    } else {
        i
    }
}
#[inline(always)]
pub(crate) fn wrap(x: f64) -> f64 {
    x - lfloor(x / 33554432.0 + 0.5) as f64 * 33554432.0
}
#[inline(always)]
pub(crate) fn smooth(x: f64) -> f64 {
    x * x * x * (x * (x * 6.0 - 15.0) + 10.0)
}
#[inline(always)]
pub(crate) fn derivative(x: f64) -> f64 {
    30.0 * x * x * (x - 1.0) * (x - 1.0)
}
#[inline(always)]
pub(crate) fn dot(g: usize, x: f64, y: f64, z: f64) -> f64 {
    let g = GRAD[g];
    g[0] * x + g[1] * y + g[2] * z
}

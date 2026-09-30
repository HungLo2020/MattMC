//! Scalar and array arithmetic adapters.
use super::math::*;
pub(crate) fn density_math(op: u32, a: f64, b: f64, p: f64, q: f64) -> f64 {
    math(op, a, b, p, q)
}
// 0: evaluate the right child; 1: retain the input; 2: return positive zero.
pub(crate) fn density_branch(op: u32, a: f64, p: f64, q: f64) -> i32 {
    if needs_right(op, a, p, q) {
        0
    } else if op == 9 {
        2
    } else {
        1
    }
}
/// # Safety
/// Values has count readable doubles, mask has count writable bytes, no aliasing.
/// Java only batches decisions when later child visits cannot mutate these inputs.
pub(crate) unsafe fn density_mask(
    op: u32,
    values: *const f64,
    mask: *mut u8,
    count: u32,
    p: f64,
    q: f64,
) -> i32 {
    if values.is_null() || mask.is_null() || count > 256 {
        return -1;
    }
    for i in 0..count as usize {
        unsafe {
            let a = *values.add(i);
            *mask.add(i) = if op == 22 {
                in_range(a, p, q)
            } else {
                needs_right(op, a, p, q)
            } as u8;
        }
    }
    0
}
/// # Safety
/// `values` has count writable doubles, `right` is null or has count readable
/// doubles. Elementwise aliasing is allowed. Java bounds each call to 256 points.
pub(crate) unsafe fn density_math_batch(
    op: u32,
    values: *mut f64,
    right: *const f64,
    count: u32,
    p: f64,
    q: f64,
) -> i32 {
    if values.is_null() || count > 256 || !(8..=24).contains(&op) {
        return -1;
    }
    for i in 0..count as usize {
        unsafe {
            let a = *values.add(i);
            let b = if right.is_null() { 0. } else { *right.add(i) };
            *values.add(i) = math(op, a, b, p, q);
        }
    }
    0
}

//! Bounded unary pipelines.
use super::math::*;
#[repr(C)]
struct UnaryStep {
    op: u32,
    reserved: u32,
    p: f64,
    q: f64,
}
/// # Safety
/// Base denotes bytes readable bytes, aligned to eight.
pub(crate) unsafe fn density_unary_validate(base: *const u8, bytes: u64) -> i32 {
    if base.is_null() || base as usize % 8 != 0 || bytes < 8 {
        return -1;
    }
    let count = unsafe { *base.cast::<u32>() } as usize;
    if count == 0 || count > 16 || bytes != 8 + 24 * count as u64 {
        return -2;
    }
    let steps = unsafe { std::slice::from_raw_parts(base.add(8).cast::<UnaryStep>(), count) };
    if steps.iter().any(|s| !(10..=21).contains(&s.op)) {
        return -3;
    }
    0
}
#[inline]
fn unary(steps: &[UnaryStep], mut value: f64) -> f64 {
    for s in steps {
        value = if s.op == 10 || s.op == 11 {
            math(s.op, value, s.p, s.p, s.p)
        } else {
            math(s.op, value, 0., s.p, s.q)
        };
    }
    value
}
/// # Safety
/// Base is an immutable allocation accepted by unary_validate.
pub(crate) unsafe fn density_unary(base: *const u8, value: f64) -> f64 {
    let count = unsafe { *base.cast::<u32>() } as usize;
    let steps = unsafe { std::slice::from_raw_parts(base.add(8).cast::<UnaryStep>(), count) };
    unary(steps, value)
}
/// # Safety
/// Validated immutable program and count writable doubles, no aliasing.
pub(crate) unsafe fn density_unary_array(base: *const u8, values: *mut f64, count: u32) -> i32 {
    if base.is_null() || values.is_null() || count > 256 {
        return -1;
    }
    let length = unsafe { *base.cast::<u32>() } as usize;
    let steps = unsafe { std::slice::from_raw_parts(base.add(8).cast::<UnaryStep>(), length) };
    let values = unsafe { std::slice::from_raw_parts_mut(values, count as usize) };
    // Keep the operation invariant across the inner loop so LLVM can vectorize
    // each pass. There are no child calls or observable writes between steps.
    for s in steps {
        if s.op == 10 || s.op == 11 {
            for value in &mut *values {
                *value = math(s.op, *value, s.p, s.p, s.p);
            }
        } else {
            for value in &mut *values {
                *value = math(s.op, *value, 0., s.p, s.q);
            }
        }
    }
    0
}

//! Coordinate transforms execute beside the curve, avoiding nested FFM calls.
pub(super) const STRIDE: usize = 8 + 24 * 16;
pub(super) unsafe fn validate(base: *const u8, bytes: u64) -> i32 {
    if base.is_null() || base as usize % 8 != 0 || bytes != (4 * STRIDE) as u64 {
        return -1;
    }
    for axis in 0..4 {
        let p = unsafe { base.add(axis * STRIDE) };
        let count = unsafe { *p.cast::<u32>() };
        if count > 16 {
            return -2;
        }
        if count > 0
            && unsafe { super::super::unary::density_unary_validate(p, 8 + 24 * count as u64) } != 0
        {
            return -3;
        }
    }
    0
}
pub(super) unsafe fn evaluate(base: *const u8, values: [f64; 4]) -> [f32; 4] {
    let mut result = [0.0; 4];
    for i in 0..4 {
        let p = unsafe { base.add(i * STRIDE) };
        result[i] = if unsafe { *p.cast::<u32>() } == 0 {
            values[i] as f32
        } else {
            (unsafe { super::super::unary::density_unary(p, values[i]) }) as f32
        };
    }
    result
}

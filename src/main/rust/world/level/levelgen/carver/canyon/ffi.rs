use super::evaluate::evaluate;

/// Initialize runtime dispatch outside heap-borrowing calls. Returns 1 for AVX2.
#[no_mangle]
pub extern "C" fn mattmc_canyon_initialize() -> i32 {
    super::evaluate::initialize()
}

/// # Safety
/// Inputs cover the declared lengths and remain immutable. Output is disjoint,
/// writable, aligned, and covers output_len words. No pointers are retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_canyon_candidates(
    frame: *const i32,
    frame_len: i32,
    shape: *const f64,
    shape_len: i32,
    widths: *const f32,
    width_len: i32,
    output: *mut u64,
    output_len: i32,
) -> i32 {
    super::evaluate::initialize();
    candidates(
        frame, frame_len, shape, shape_len, widths, width_len, output, output_len,
    )
}

unsafe fn candidates(
    frame: *const i32,
    frame_len: i32,
    shape: *const f64,
    shape_len: i32,
    widths: *const f32,
    width_len: i32,
    output: *mut u64,
    output_len: i32,
) -> i32 {
    if frame.is_null()
        || shape.is_null()
        || output.is_null()
        || frame_len != 9
        || shape_len != 5
        || frame as usize % 4 != 0
        || shape as usize % 8 != 0
        || output as usize % 8 != 0
        || !(0..=2048).contains(&width_len)
    {
        return -1;
    }
    let f = std::slice::from_raw_parts(frame, 9);
    let s = std::slice::from_raw_parts(shape, 5);
    if !s.iter().all(|v| v.is_finite())
        || s[3] <= 0.0
        || s[4] <= 0.0
        || f[2] < 0
        || f[3] > 15
        || f[6] < 0
        || f[7] > 15
        || f[2] > f[3]
        || f[6] > f[7]
    {
        return -1;
    }
    let height = f[5] as i64 - f[4] as i64;
    if !(1..=2048).contains(&height) {
        return -1;
    }
    let count =
        (f[3] - f[2] + 1) as usize * (f[7] - f[6] + 1) as usize * (height as usize).div_ceil(64);
    if output_len != count as i32 {
        return -1;
    }
    if widths.is_null()
        || widths as usize % 4 != 0
        || width_len == 0
        || f[4] as i64 - (f[8] as i64) < 0
        || f[5] as i64 - (f[8] as i64) - 1 >= width_len as i64
    {
        return -1;
    }
    let factors = std::slice::from_raw_parts(widths, width_len as usize);
    let first = (f[4] as i64 - f[8] as i64) as usize;
    let end = (f[5] as i64 - f[8] as i64) as usize;
    if factors[first..end]
        .iter()
        .any(|v| !v.is_finite() || *v < 0.0)
    {
        return -1;
    }
    evaluate(f, s, factors, std::slice::from_raw_parts_mut(output, count));
    0
}

/// Short heap-borrowing entry: at most 64 Y samples and 8,192 decisions.
/// # Safety
/// Same buffer contract as mattmc_canyon_candidates. No allocations, blocking,
/// callbacks or pointer retention while Java arrays are borrowed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_canyon_candidates_small(
    frame: *const i32,
    frame_len: i32,
    shape: *const f64,
    shape_len: i32,
    widths: *const f32,
    width_len: i32,
    output: *mut u64,
    output_len: i32,
) -> i32 {
    if frame.is_null() || frame as usize % 4 != 0 || frame_len != 9 {
        return -1;
    }
    let f = std::slice::from_raw_parts(frame, 9);
    let height = f[5] as i64 - f[4] as i64;
    if !(1..=64).contains(&height)
        || f[2] < 0
        || f[3] > 15
        || f[6] < 0
        || f[7] > 15
        || f[2] > f[3]
        || f[6] > f[7]
        || (f[3] - f[2] + 1) as i64 * (f[7] - f[6] + 1) as i64 * height > 8192
    {
        return -1;
    }
    candidates(
        frame, frame_len, shape, shape_len, widths, width_len, output, output_len,
    )
}

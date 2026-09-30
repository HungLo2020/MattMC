//! Stable exported density and End-height ABI.
use super::super::synth::State;
/// Complete bounded End island-height expression. Preserves Java int wrapping,
/// float arithmetic, double sqrt followed by float rounding, and NaN propagation.
/// # Safety
/// `state` is a live immutable validated kind-5 state containing one octave.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_end_island(state: *const State, x: i32, z: i32) -> f32 {
    unsafe { super::end_islands::noise_end_island(state, x, z) }
}
/// # Safety
/// `base` denotes `bytes` readable bytes, aligned to eight bytes. Validation must
/// succeed before evaluation; the allocation must remain immutable and live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_validate(base: *const u8, bytes: u64) -> i32 {
    unsafe { super::validation::density_validate(base, bytes) }
}
/// # Safety
/// State is null (an unbound noise holder) or an immutable, validated NormalNoise
/// state. Java selects an ordinary downcall for unusually large octave counts.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_noise(
    state: *const State,
    op: u32,
    x: f64,
    y: f64,
    z: f64,
    sx: f64,
    sy: f64,
    sz: f64,
    p: f64,
    q: f64,
) -> f64 {
    unsafe { super::evaluator::density_noise(state, op, x, y, z, sx, sy, sz, p, q) }
}

/// # Safety
/// Same state contract as density_noise. Inputs contain count * 6 readable
/// doubles (raw xyz and shift/input values); output contains count writable
/// doubles and does not alias input. Java bounds count and critical-call work.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_noise_batch(
    state: *const State,
    op: u32,
    input: *const f64,
    output: *mut f64,
    count: u32,
    p: f64,
    q: f64,
) -> i32 {
    unsafe { super::evaluator::density_noise_batch(state, op, input, output, count, p, q) }
}

/// # Safety
/// `base` is a live immutable allocation accepted by mattmc_density_validate.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_eval(base: *const u8, x: f64, y: f64, z: f64) -> f64 {
    unsafe { super::evaluator::density_eval(base, x, y, z) }
}

/// # Safety
/// Base is a live immutable validated program. Operand programs have no noise
/// nodes and at most four inputs. Inputs are copied scalars, never retained pointers.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_operands(
    base: *const u8,
    a: f64,
    b: f64,
    c: f64,
    d: f64,
) -> f64 {
    unsafe { super::evaluator::density_operands(base, a, b, c, d) }
}
/// # Safety
/// Base denotes bytes readable bytes, aligned to eight.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_unary_validate(base: *const u8, bytes: u64) -> i32 {
    unsafe { super::unary::density_unary_validate(base, bytes) }
}

/// # Safety
/// Base is an immutable allocation accepted by unary_validate.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_unary(base: *const u8, value: f64) -> f64 {
    unsafe { super::unary::density_unary(base, value) }
}

/// # Safety
/// Validated immutable program and count writable doubles, no aliasing.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_unary_array(
    base: *const u8,
    values: *mut f64,
    count: u32,
) -> i32 {
    unsafe { super::unary::density_unary_array(base, values, count) }
}
/// # Safety
/// Base is an immutable validated cell program. Frame has 8 doubles per cell
/// input, remains live through the call, and is never retained by Rust.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_cell(
    base: *const u8,
    frame: *const f64,
    x: f64,
    y: f64,
    z: f64,
) -> f64 {
    unsafe { super::cell::density_cell(base, frame, x, y, z) }
}

/// # Safety
/// Same validated program/frame contract as density_cell. Output has count
/// writable doubles and cannot alias frame or program. Each call is <=256 points.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_cell_array(
    base: *const u8,
    frame: *const f64,
    output: *mut f64,
    start: u32,
    count: u32,
    width: u32,
    height: u32,
) -> i64 {
    unsafe { super::cell::density_cell_array(base, frame, output, start, count, width, height) }
}
#[no_mangle]
pub extern "C" fn mattmc_density_math(op: u32, a: f64, b: f64, p: f64, q: f64) -> f64 {
    super::operations::density_math(op, a, b, p, q)
}

#[no_mangle]
pub extern "C" fn mattmc_density_branch(op: u32, a: f64, p: f64, q: f64) -> i32 {
    super::operations::density_branch(op, a, p, q)
}

/// # Safety
/// Values has count readable doubles, mask has count writable bytes, no aliasing.
/// Java only batches decisions when later child visits cannot mutate these inputs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_mask(
    op: u32,
    values: *const f64,
    mask: *mut u8,
    count: u32,
    p: f64,
    q: f64,
) -> i32 {
    unsafe { super::operations::density_mask(op, values, mask, count, p, q) }
}

/// # Safety
/// `values` has count writable doubles, `right` is null or has count readable
/// doubles. Elementwise aliasing is allowed. Java bounds each call to 256 points.
#[no_mangle]
pub unsafe extern "C" fn mattmc_density_math_batch(
    op: u32,
    values: *mut f64,
    right: *const f64,
    count: u32,
    p: f64,
    q: f64,
) -> i32 {
    unsafe { super::operations::density_math_batch(op, values, right, count, p, q) }
}

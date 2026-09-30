//! Stable exported noise ABI.
use super::state::State;
/// Specialized hot entry: no unused z coordinate, flags, or family dispatch.
/// # Safety
/// Java-owned validated kind=5 state with one live immutable octave.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_simplex2(state: *const State, x: f64, y: f64) -> f64 {
    unsafe { super::dispatch::noise_simplex2(state, x, y) }
}

/// # Safety
/// `state` is validated immutable state, live for this call. No allocations or callbacks.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_eval(
    state: *const State,
    x: f64,
    y: f64,
    z: f64,
    ys: f64,
    ym: f64,
    flags: u32,
) -> f64 {
    unsafe { super::dispatch::noise_eval(state, x, y, z, ys, ym, flags) }
}

/// # Safety
/// Valid state, `xyz` contains count*3 doubles, `out` count doubles; buffers do not alias.
/// Bounded for Java critical downcalls; Java splits larger requests.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_batch(
    state: *const State,
    xyz: *const f64,
    out: *mut f64,
    count: u32,
    ys: f64,
    ym: f64,
    flags: u32,
) -> i32 {
    unsafe { super::dispatch::noise_batch(state, xyz, out, count, ys, ym, flags) }
}

/// # Safety
/// Valid kind=1 state and writable, nonaliasing three-double derivative accumulator.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_derivative(
    state: *const State,
    x: f64,
    y: f64,
    z: f64,
    out: *mut f64,
) -> f64 {
    unsafe { super::dispatch::noise_derivative(state, x, y, z, out) }
}
/// Validate the Java-owned state once before publishing it to other threads.
/// # Safety
/// `state` must point to `bytes` readable bytes aligned to eight bytes.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_validate(state: *const State, bytes: u64) -> i32 {
    unsafe { super::validation::noise_validate(state, bytes) }
}

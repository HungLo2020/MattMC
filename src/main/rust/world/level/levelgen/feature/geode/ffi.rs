use super::super::super::synth::{noise_validate, State};
use super::evaluate::{evaluate, Point};

/// One bounded grid, X fastest, with interleaved (body, crack) density output.
///
/// # Safety
/// Aligned, disjoint buffers must cover their stated lengths and remain live.
/// State is immutable. No allocations, callbacks, blocking or pointer retention.
#[no_mangle]
pub unsafe extern "C" fn mattmc_geode_fields(
    state: *const State,
    state_len: u64,
    frame: *const i32,
    frame_len: i32,
    points: *const Point,
    point_count: i32,
    crack_count: i32,
    multiplier: f64,
    output: *mut f64,
    output_len: i32,
) -> i32 {
    if state.is_null()
        || frame.is_null()
        || points.is_null()
        || output.is_null()
        || state as usize % 8 != 0
        || frame as usize % 4 != 0
        || points as usize % 4 != 0
        || output as usize % 8 != 0
        || frame_len != 6
        || !(0..=64).contains(&point_count)
        || !(0..=64).contains(&crack_count)
        || !(multiplier >= 0.0 && multiplier <= 1.0)
        || state_len > 80 + 1320 * 64
    {
        return -1;
    }
    let f = std::slice::from_raw_parts(frame, 6);
    if f[3..].iter().any(|&d| d < 1 || d > 64) {
        return -1;
    }
    let dims = [f[3] as usize, f[4] as usize, f[5] as usize];
    let cells = dims.iter().product::<usize>();
    if cells > 65536 || output_len != (cells * 2) as i32 {
        return -1;
    }
    for axis in 0..3 {
        if f[axis] as i64 + dims[axis] as i64 - 1 > i32::MAX as i64 {
            return -1;
        }
    }
    if noise_validate(state, state_len) != 0 || (*state).kind != 3 {
        return -2;
    }
    let all = std::slice::from_raw_parts(points, (point_count + crack_count) as usize);
    if all.iter().any(|p| p.offset < 0) {
        return -1;
    }
    evaluate(
        state,
        [f[0], f[1], f[2]],
        dims,
        &all[..point_count as usize],
        &all[point_count as usize..],
        multiplier,
        std::slice::from_raw_parts_mut(output, output_len as usize),
    );
    0
}

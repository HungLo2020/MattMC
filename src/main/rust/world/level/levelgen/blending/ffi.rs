use super::evaluate::evaluate;
use std::{mem::align_of, slice};

fn aligned<T>(p: *const T) -> bool {
    !p.is_null() && (p as usize) % align_of::<T>() == 0
}

/// Ordinary (GC-permitting) FFM. Caller owns valid aligned disjoint buffers for
/// their declared lengths throughout the call. No allocation or retained pointer.
/// 0 success; 1 nonfinite arithmetic (Java compatibility); -1 invalid metadata.
#[no_mangle]
pub unsafe extern "C" fn mattmc_blending_heights(
    coordinates: *const i32,
    heights: *const f64,
    count: i32,
    queries: *const i32,
    direct: *const f64,
    query_count: i32,
    output: *mut f64,
    output_len: i32,
) -> i32 {
    if !(1..=4096).contains(&count)
        || !(1..=289).contains(&query_count)
        || output_len != query_count * 2
        || !aligned(coordinates)
        || !aligned(heights)
        || !aligned(queries)
        || !aligned(direct)
        || !aligned(output)
    {
        return -1;
    }
    let coordinates = slice::from_raw_parts(coordinates, count as usize * 2);
    let heights = slice::from_raw_parts(heights, count as usize);
    let queries = slice::from_raw_parts(queries, query_count as usize * 2);
    let direct = slice::from_raw_parts(direct, query_count as usize);
    // Sentinel heights were omitted by iterateHeights. Direct sentinels retained.
    if heights.iter().any(|v| !v.is_finite() || *v == f64::MAX)
        || direct.iter().any(|v| !v.is_finite())
    {
        return -1;
    }
    let output = slice::from_raw_parts_mut(output, output_len as usize);
    if evaluate(coordinates, heights, queries, direct, output) {
        0
    } else {
        1
    }
}

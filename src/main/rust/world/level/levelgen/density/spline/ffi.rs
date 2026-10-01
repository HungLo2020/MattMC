use super::{
    evaluate::evaluate,
    program::{valid, Knot, Node, MAX_KNOTS, MAX_NODES},
};
/// # Safety
/// Base denotes bytes readable bytes, aligned to eight.
#[no_mangle]
pub unsafe extern "C" fn mattmc_spline_coordinates_validate(base: *const u8, bytes: u64) -> i32 {
    unsafe { super::coordinates::validate(base, bytes) }
}
/// # Safety
/// Curve and coordinate programs passed their validators and remain live and immutable.
#[no_mangle]
pub unsafe extern "C" fn mattmc_spline_cached(
    nodes: *const Node,
    count: i32,
    knots: *const Knot,
    nknots: i32,
    coordinates: *const u8,
    a: f64,
    b: f64,
    c: f64,
    d: f64,
) -> f32 {
    let ns = unsafe { std::slice::from_raw_parts(nodes, count as usize) };
    let ks = unsafe { std::slice::from_raw_parts(knots, nknots as usize) };
    let axes = unsafe { super::coordinates::evaluate(coordinates, [a, b, c, d]) };
    evaluate(ns, ks, ns.len() - 1, &axes)
}
/// # Safety
/// Buffers are live, aligned readable arrays of the specified lengths.
#[no_mangle]
pub unsafe extern "C" fn mattmc_spline_validate(
    nodes: *const Node,
    count: i32,
    knots: *const Knot,
    nknots: i32,
) -> i32 {
    if nodes.is_null()
        || knots.is_null()
        || count < 1
        || count as usize > MAX_NODES
        || nknots < 0
        || nknots as usize > MAX_KNOTS
    {
        return -1;
    }
    let ns = unsafe { std::slice::from_raw_parts(nodes, count as usize) };
    let ks = unsafe { std::slice::from_raw_parts(knots, nknots as usize) };
    if valid(ns, ks) {
        0
    } else {
        -2
    }
}
/// # Safety
/// Arrays passed validation, remain immutable and live for this call. All four
/// coordinates are values, not pointers; no Java heap is pinned or retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_spline_eval(
    nodes: *const Node,
    count: i32,
    knots: *const Knot,
    nknots: i32,
    a: f32,
    b: f32,
    c: f32,
    d: f32,
) -> f32 {
    let ns = unsafe { std::slice::from_raw_parts(nodes, count as usize) };
    let ks = unsafe { std::slice::from_raw_parts(knots, nknots as usize) };
    evaluate(ns, ks, ns.len() - 1, &[a, b, c, d])
}

//! C ABI for `NativeNoiseRouter`. Java compiles one router per NOISE fill,
//! creates it from borrowed heap arrays (a bounded copy), fills each slice with
//! an ordinary downcall into an off-heap buffer, and releases it in `finally`.
//! Noise states are referenced, not copied: they are immutable segments that
//! NativeNoise validated when it was created, and Java keeps them reachable
//! until release.
use super::{Geometry, Node, Router, Spline, MAX_NODES};
use crate::world::level::levelgen::density::spline::program::{Knot, Node as SplineNode, MAX_KNOTS, MAX_NODES as MAX_SPLINE_NODES};
use crate::world::level::levelgen::synth::State;

const HEADER: usize = 16;
const NODE_INTS: usize = 5;
const NODE_DOUBLES: usize = 4;
const SPLINE_INTS: usize = 9;

/// `ints`: header [nodes, roots, splines, splineNodes, splineKnots, states,
/// flatSlots, columns, points, cellWidth, cellHeight, firstCellZ, cellMinY,
/// firstNoiseX, firstNoiseZ, flatSize], then per node [op, a, b, c, state
/// index or -1], the root nodes, per spline [nodeStart, nodeCount, knotStart,
/// knotCount, axisCount, axes...4], per spline node [axis, start, count, value
/// bits] and per knot [location bits, derivative bits, child].
/// `doubles`: per node [p, q, r, s], then every FlatCache's values.
/// `longs`: per state [address, byte size]. Returns 0 if invalid.
/// # Safety
/// The arrays hold the stated counts; every state address is a live validated
/// noise state of the stated size that stays reachable until release.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_router_create(
    ints: *const i32,
    int_count: i32,
    doubles: *const f64,
    double_count: i32,
    longs: *const i64,
    long_count: i32,
) -> u64 {
    if ints.is_null() || doubles.is_null() || longs.is_null() || int_count < HEADER as i32 || double_count < 0 || long_count < 0 {
        return 0;
    }
    let ints = unsafe { std::slice::from_raw_parts(ints, int_count as usize) };
    let doubles = unsafe { std::slice::from_raw_parts(doubles, double_count as usize) };
    let longs = unsafe { std::slice::from_raw_parts(longs, long_count as usize) };
    match parse(ints, doubles, longs) {
        Some(router) => Box::into_raw(Box::new(router)) as u64,
        None => 0,
    }
}

fn parse(ints: &[i32], doubles: &[f64], longs: &[i64]) -> Option<Router> {
    let count = |index: usize, max: usize| -> Option<usize> {
        let value = usize::try_from(ints[index]).ok()?;
        (value <= max).then_some(value)
    };
    let nodes = count(0, MAX_NODES)?;
    let roots = count(1, 64)?;
    let splines = count(2, 1024)?;
    let spline_nodes = count(3, MAX_SPLINE_NODES * 64)?;
    let spline_knots = count(4, MAX_KNOTS * 64)?;
    let states = count(5, MAX_NODES)?;
    let flat_slots = count(6, 1024)?;
    let geometry = Geometry {
        columns: count(7, 64)?,
        points: count(8, 8192)?,
        cell_width: ints[9],
        cell_height: ints[10],
        first_cell_z: ints[11],
        cell_min_y: ints[12],
        first_noise_x: ints[13],
        first_noise_z: ints[14],
        flat_size: ints[15],
    };
    if !(1..=16).contains(&geometry.cell_width) || !(1..=64).contains(&geometry.cell_height) || !(1..=64).contains(&geometry.flat_size) {
        return None;
    }
    let flat_len = flat_slots * (geometry.flat_size * geometry.flat_size) as usize;
    let expected = HEADER + nodes * NODE_INTS + roots + splines * SPLINE_INTS + spline_nodes * 4 + spline_knots * 3;
    if ints.len() != expected || doubles.len() != nodes * NODE_DOUBLES + flat_len || longs.len() != states * 2 {
        return None;
    }
    let mut state_pointers = Vec::with_capacity(states);
    for k in 0..states {
        let (address, bytes) = (longs[k * 2], longs[k * 2 + 1]);
        if address == 0 || address % 8 != 0 || bytes < 80 {
            return None;
        }
        let state = address as usize as *const State;
        // The full permutation check ran when NativeNoise published the state;
        // confirm the header describes exactly the referenced allocation.
        let s = unsafe { &*state };
        let octaves = s.n1 as i64 + s.n2 as i64 + s.n3 as i64;
        if bytes != 80 + 1320 * octaves {
            return None;
        }
        state_pointers.push(state);
    }
    let mut at = HEADER;
    let mut parsed = Vec::with_capacity(nodes);
    for k in 0..nodes {
        let i = &ints[at..at + NODE_INTS];
        let d = &doubles[k * NODE_DOUBLES..k * NODE_DOUBLES + NODE_DOUBLES];
        let state = match i[4] {
            -1 => std::ptr::null(),
            index => *state_pointers.get(usize::try_from(index).ok()?)?,
        };
        parsed.push(Node { op: i[0] as u32, a: i[1] as u32, b: i[2] as u32, c: i[3] as u32, state, p: d[0], q: d[1], r: d[2], s: d[3] });
        at += NODE_INTS;
    }
    let root_ids: Vec<u32> = ints[at..at + roots].iter().map(|root| *root as u32).collect();
    at += roots;
    let headers = &ints[at..at + splines * SPLINE_INTS];
    at += splines * SPLINE_INTS;
    let node_table = &ints[at..at + spline_nodes * 4];
    at += spline_nodes * 4;
    let knot_table = &ints[at..at + spline_knots * 3];
    let mut parsed_splines = Vec::with_capacity(splines);
    for h in headers.chunks_exact(SPLINE_INTS) {
        let (node_start, node_count) = (usize::try_from(h[0]).ok()?, usize::try_from(h[1]).ok()?);
        let (knot_start, knot_count) = (usize::try_from(h[2]).ok()?, usize::try_from(h[3]).ok()?);
        let axis_count = usize::try_from(h[4]).ok()?;
        if node_count == 0 || node_count > MAX_SPLINE_NODES || knot_count > MAX_KNOTS || axis_count > 4
            || node_start.checked_add(node_count)? > spline_nodes || knot_start.checked_add(knot_count)? > spline_knots
        {
            return None;
        }
        let nodes = node_table[node_start * 4..(node_start + node_count) * 4]
            .chunks_exact(4)
            .map(|n| SplineNode { axis: n[0], start: n[1] as u32, count: n[2] as u32, value: f32::from_bits(n[3] as u32) })
            .collect();
        let knots = knot_table[knot_start * 3..(knot_start + knot_count) * 3]
            .chunks_exact(3)
            .map(|k| Knot { location: f32::from_bits(k[0] as u32), derivative: f32::from_bits(k[1] as u32), child: k[2] as u32 })
            .collect();
        let mut axes = [0u32; 4];
        for (axis, value) in axes.iter_mut().zip(&h[5..]).take(axis_count) {
            *axis = u32::try_from(*value).ok()?;
        }
        parsed_splines.push(Spline { nodes, knots, axes, axis_count });
    }
    let flat = doubles[nodes * NODE_DOUBLES..].to_vec();
    Router::new(parsed, parsed_splines, flat, flat_slots, root_ids, geometry).ok()
}

/// Fills one slice into `out` (interpolator, column, Y). Returns 0 on success,
/// 1 when Java must fill this slice itself, negative for invalid arguments.
/// # Safety
/// `handle` came from create and is used by one thread; `out` has `length`
/// writable doubles that do not alias the router.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_router_slice(handle: u64, x: i32, out: *mut f64, length: i64) -> i32 {
    if handle == 0 || out.is_null() {
        return -1;
    }
    let router = unsafe { &mut *(handle as *mut Router) };
    if length != router.output_len() as i64 {
        return -2;
    }
    let out = unsafe { std::slice::from_raw_parts_mut(out, length as usize) };
    match router.slice(x, out) {
        Ok(()) => 0,
        Err(_) => 1,
    }
}

/// # Safety
/// `handle` came from create and is not used afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_router_release(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut Router) });
    }
}

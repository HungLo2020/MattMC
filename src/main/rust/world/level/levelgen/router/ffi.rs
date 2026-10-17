//! C ABI for `NativeNoiseRouter`. Java compiles one router per NOISE fill,
//! creates it from borrowed heap arrays (a bounded copy), fills each slice with
//! an ordinary downcall into an off-heap buffer, and releases it in `finally`.
//! Noise states are referenced, not copied: they are immutable segments that
//! NativeNoise validated when it was created, and Java keeps them reachable
//! until release.
use super::{Geometry, Node, Program, Router, Spline, MAX_NODES};
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
        Some((nodes, splines, flat, slots, roots, geometry)) => match Router::new(nodes, splines, flat, slots, roots, geometry) {
            Ok(router) => Box::into_raw(Box::new(router)) as u64,
            Err(_) => 0,
        },
        None => 0,
    }
}

type Parsed = (Vec<Node>, Vec<Spline>, Vec<f64>, usize, Vec<u32>, Geometry);

fn parse(ints: &[i32], doubles: &[f64], longs: &[i64]) -> Option<Parsed> {
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
    Some((parsed, parsed_splines, flat, flat_slots, root_ids, geometry))
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

/// A shared point program for `NoiseChunk`'s preliminary surface level, in the
/// create layout with one root, no FlatCache slots and unused slice geometry.
/// It lives as long as its `RandomState`; Java releases it with a Cleaner.
/// Returns 0 if invalid.
/// # Safety
/// Same contract as `mattmc_noise_router_create`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_program_create(
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
    let Some((nodes, splines, flat, slots, roots, geometry)) = parse(ints, doubles, longs) else { return 0 };
    if slots != 0 || roots.len() != 1 || roots[0] as usize + 1 != nodes.len() {
        return 0;
    }
    match Program::new(nodes, splines, flat, slots, roots, geometry) {
        Ok(program) => Box::into_raw(Box::new(program)) as u64,
        Err(_) => 0,
    }
}

fn levels(program: &Program, xs: &[i32], zs: &[i32], out: &mut [i32]) {
    // Without FlatCache slots no node can leave the grid.
    if super::with_frame(program, |frame| program.surface_levels(frame, xs, zs, out)).is_err() {
        out.fill(i32::MIN);
    }
}

/// # Safety
/// `handle` came from `mattmc_surface_program_create` and is not released.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_level(handle: u64, x: i32, z: i32) -> i32 {
    let mut out = [0];
    levels(unsafe { &*(handle as *const Program) }, &[x], &[z], &mut out);
    out[0]
}

/// Levels for `count` (<= 64) columns. Returns 0, or -1 for invalid arguments.
/// # Safety
/// As `mattmc_surface_level`; `xs`, `zs` and `out` hold `count` i32s.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_levels(handle: u64, xs: *const i32, zs: *const i32, out: *mut i32, count: i32) -> i32 {
    if handle == 0 || xs.is_null() || zs.is_null() || out.is_null() || !(0..=64).contains(&count) {
        return -1;
    }
    let count = count as usize;
    let (xs, zs) = unsafe { (std::slice::from_raw_parts(xs, count), std::slice::from_raw_parts(zs, count)) };
    levels(unsafe { &*(handle as *const Program) }, xs, zs, unsafe { std::slice::from_raw_parts_mut(out, count) });
    0
}

/// # Safety
/// `handle` came from `mattmc_surface_program_create` and is not used afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_program_release(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut Program) });
    }
}

/// A shared point program for the aquifer's noise sources: five roots (erosion,
/// depth, fluid level floodedness, fluid level spread, lava), in the create
/// layout with no FlatCache slots. Lives as long as its `RandomState`; Java
/// releases it with a Cleaner. Returns 0 if invalid.
/// # Safety
/// Same contract as `mattmc_noise_router_create`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_fluid_sources_create(
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
    let Some((nodes, splines, flat, slots, roots, geometry)) = parse(ints, doubles, longs) else { return 0 };
    if slots != 0 || roots.len() != 5 {
        return 0;
    }
    match Program::new(nodes, splines, flat, slots, roots, geometry) {
        Ok(program) => Box::into_raw(Box::new(program)) as u64,
        Err(_) => 0,
    }
}

/// A RandomState's six climate functions (temperature, vegetation,
/// continents, erosion, depth, ridges) as a point program, in the create
/// layout: no FlatCache tables, FlatCache markers as FLAT_POINT. Released with
/// `mattmc_surface_program_release`. 0 if invalid.
/// # Safety
/// Same contract as `mattmc_fluid_sources_create`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_climate_program_create(
    ints: *const i32,
    int_count: i32,
    doubles: *const f64,
    double_count: i32,
    longs: *const i64,
    long_count: i32,
) -> u64 {
    let Some((nodes, splines, flat, slots, roots, geometry)) = (unsafe { parse_raw(ints, int_count, doubles, double_count, longs, long_count) }) else {
        return 0;
    };
    if slots != 0 || roots.len() != 6 {
        return 0;
    }
    match Program::new(nodes, splines, flat, slots, roots, geometry) {
        Ok(program) => Box::into_raw(Box::new(program)) as u64,
        Err(_) => 0,
    }
}

/// FLAT_POINT memo slots the sources program needs per chunk binding.
/// # Safety
/// `handle` came from `mattmc_fluid_sources_create` and is not released.
#[no_mangle]
pub unsafe extern "C" fn mattmc_fluid_sources_slots(handle: u64) -> i32 {
    unsafe { &*(handle as *const Program) }.point_slots() as i32
}

/// Verification only: source `root` at one block position with a chunk
/// binding (as `mattmc_aquifer_fluid_native` takes it). NaN for bad arguments.
/// # Safety
/// `handle` is a live sources program; `grid` holds three i32s; `memo` and
/// `present` hold `memo_len` entries; all borrowed for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_fluid_sources_value(handle: u64, root: i32, x: i32, y: i32, z: i32, grid: *const i32, memo: *mut f64,
    present: *mut u8, memo_len: i32) -> f64 {
    let program = unsafe { &*(handle as *const Program) };
    if grid.is_null() || memo.is_null() || present.is_null() || !(0..program.root_count() as i32).contains(&root) || memo_len < 0 {
        return f64::NAN;
    }
    let g = unsafe { std::slice::from_raw_parts(grid, 3) };
    if !(1..=64).contains(&g[2]) || memo_len as usize != program.point_slots() * (g[2] * g[2]) as usize {
        return f64::NAN;
    }
    let mut binding = super::Binding {
        first_x: g[0],
        first_z: g[1],
        size: g[2],
        memo: unsafe { std::slice::from_raw_parts_mut(memo, memo_len as usize) },
        present: unsafe { std::slice::from_raw_parts_mut(present, memo_len as usize) },
    };
    super::with_frame(program, |frame| program.point_value(frame, root as usize, x, y, z, &mut binding)).unwrap_or(f64::NAN)
}

/// A per-seed slice template in the create layout (slice geometry and
/// FlatCache tables unused: flatSize 1 with zeroed tables). Instances supply
/// both per chunk. Released with `mattmc_surface_program_release`. 0 if invalid.
/// # Safety
/// Same contract as `mattmc_noise_router_create`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_router_template(
    ints: *const i32,
    int_count: i32,
    doubles: *const f64,
    double_count: i32,
    longs: *const i64,
    long_count: i32,
) -> u64 {
    let Some((nodes, splines, flat, slots, roots, geometry)) = (unsafe { parse_raw(ints, int_count, doubles, double_count, longs, long_count) }) else {
        return 0;
    };
    match Program::new(nodes, splines, flat, slots, roots, geometry) {
        Ok(program) if !program.has_point_nodes() => Box::into_raw(Box::new(program)) as u64,
        _ => 0,
    }
}

/// A per-seed program of FlatCache inputs: one root per template slot, in slot
/// order, compiled in point mode (nested FlatCaches as FLAT_POINT with the
/// same slots). Released with `mattmc_surface_program_release`. 0 if invalid.
/// # Safety
/// Same contract as `mattmc_noise_router_create`.
#[no_mangle]
pub unsafe extern "C" fn mattmc_flat_program_create(
    ints: *const i32,
    int_count: i32,
    doubles: *const f64,
    double_count: i32,
    longs: *const i64,
    long_count: i32,
) -> u64 {
    let Some((nodes, splines, flat, slots, roots, geometry)) = (unsafe { parse_raw(ints, int_count, doubles, double_count, longs, long_count) }) else {
        return 0;
    };
    if slots != 0 {
        return 0;
    }
    match Program::new(nodes, splines, flat, slots, roots, geometry) {
        Ok(program) => Box::into_raw(Box::new(program)) as u64,
        Err(_) => 0,
    }
}

/// One chunk's router over a template. `geometry`: [columns, points,
/// cellWidth, cellHeight, firstCellZ, cellMinY, firstNoiseX, firstNoiseZ,
/// flatSize]. `flats` is 0 when the template has no FlatCache slots. The
/// tables are computed here. Released with `mattmc_noise_router_release`.
/// # Safety
/// `template` and `flats` are live programs that outlive the router;
/// `geometry` holds nine i32s, borrowed for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_router_instance(template: u64, flats: u64, geometry: *const i32) -> u64 {
    if template == 0 || geometry.is_null() {
        return 0;
    }
    let g = unsafe { std::slice::from_raw_parts(geometry, 9) };
    let (Ok(columns), Ok(points)) = (usize::try_from(g[0]), usize::try_from(g[1])) else { return 0 };
    if !(1..=16).contains(&g[2]) || !(1..=64).contains(&g[3]) {
        return 0;
    }
    let geometry = Geometry {
        columns,
        points,
        cell_width: g[2],
        cell_height: g[3],
        first_cell_z: g[4],
        cell_min_y: g[5],
        first_noise_x: g[6],
        first_noise_z: g[7],
        flat_size: g[8],
    };
    let flats = (flats != 0).then(|| unsafe { &*(flats as *const Program) });
    match unsafe { Router::instance(template as *const Program, flats, geometry) } {
        Ok(router) => Box::into_raw(Box::new(router)) as u64,
        Err(_) => 0,
    }
}

unsafe fn parse_raw(ints: *const i32, int_count: i32, doubles: *const f64, double_count: i32, longs: *const i64, long_count: i32) -> Option<Parsed> {
    if ints.is_null() || doubles.is_null() || longs.is_null() || int_count < HEADER as i32 || double_count < 0 || long_count < 0 {
        return None;
    }
    let ints = unsafe { std::slice::from_raw_parts(ints, int_count as usize) };
    let doubles = unsafe { std::slice::from_raw_parts(doubles, double_count as usize) };
    let longs = unsafe { std::slice::from_raw_parts(longs, long_count as usize) };
    parse(ints, doubles, longs)
}

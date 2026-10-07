//! Java bridge for biome searches; see `NativeBiomeSearch`.
use super::{closest, horizontal, Error, Horizontal, Sampler};
use crate::world::level::biome::climate::Node;
use crate::world::level::levelgen::router::Program;

/// Shared argument checks: a live climate program, a validated tree and its
/// per-node predicate, and a previous leaf (or -1).
unsafe fn inputs<'a>(program: u64, nodes: *const Node, node_count: i32, accept: *const u8, previous: i32)
    -> Option<(&'a Program, &'a [Node], &'a [u8])> {
    if program == 0 || nodes.is_null() || accept.is_null() || node_count <= 0 {
        return None;
    }
    let nodes = std::slice::from_raw_parts(nodes, node_count as usize);
    if previous < -1 || previous >= node_count || (previous >= 0 && !nodes[previous as usize].is_leaf()) {
        return None;
    }
    Some((&*(program as *const Program), nodes, std::slice::from_raw_parts(accept, node_count as usize)))
}

fn status(error: Error) -> i32 {
    match error {
        Error::NoLeaf => 1,
        Error::Program => 2,
    }
}

/// `findBiomeHorizontal`: `params` [centre quart X, quart Y, centre quart Z,
/// radius, step]. Writes each match as (quart X, quart Z, leaf)
/// to `out` (room for `cap` matches) and [match count, new previous leaf] to
/// `info`. Returns 0, 1 when a search found no leaf, 2 when sampling failed, 3
/// when `out` is too small (`info[0]` is the count needed), -1 for invalid input.
/// Nothing is written but `info` unless 0 is returned.
/// # Safety
/// `program` came from `mattmc_climate_program_create`; pointers address
/// their stated counts (`params` 5, `info` 2, `out` 3 * `cap`).
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_search_horizontal(program: u64, nodes: *const Node, node_count: i32, accept: *const u8, previous: i32,
    params: *const i32, out: *mut i32, cap: i32, info: *mut i32) -> i32 {
    let Some((program, nodes, accept)) = inputs(program, nodes, node_count, accept, previous) else { return -1 };
    if params.is_null() || info.is_null() || cap < 0 || (cap > 0 && out.is_null()) {
        return -1;
    }
    let p = std::slice::from_raw_parts(params, 5);
    let search = Horizontal { qx: p[0], qy: p[1], qz: p[2], radius: p[3], step: p[4] };
    let mut sampler = Sampler::new(program, nodes, previous);
    let mut matches = Vec::new();
    if let Err(error) = horizontal(&mut sampler, &search, accept, &mut matches) {
        return status(error);
    }
    let info = std::slice::from_raw_parts_mut(info, 2);
    info[0] = matches.len() as i32;
    if matches.len() > cap as usize {
        return 3;
    }
    let out = if cap == 0 { &mut [][..] } else { std::slice::from_raw_parts_mut(out, 3 * cap as usize) };
    for (i, (w, x, leaf)) in matches.iter().enumerate() {
        out[3 * i..3 * i + 3].copy_from_slice(&[*w, *x, *leaf]);
    }
    info[1] = sampler.previous;
    0
}

/// `findClosestBiome3d`: `params` [block X, block Z, spiral radius, step],
/// `ys` the block Ys in visiting order. Writes [found, X, Y, Z, leaf, new
/// previous leaf] to `out`. Returns 0, 1 when a search found no leaf, 2 when
/// sampling failed, -1 for invalid input.
/// # Safety
/// As `mattmc_biome_search_horizontal`; `params` 4, `ys` `ys_len`, `out` 6.
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_search_closest(program: u64, nodes: *const Node, node_count: i32, accept: *const u8, previous: i32,
    params: *const i32, ys: *const i32, ys_len: i32, out: *mut i32) -> i32 {
    let Some((program, nodes, accept)) = inputs(program, nodes, node_count, accept, previous) else { return -1 };
    if params.is_null() || out.is_null() || ys_len < 0 || (ys_len > 0 && ys.is_null()) {
        return -1;
    }
    let p = std::slice::from_raw_parts(params, 4);
    if p[2] < 0 || p[2] > 1 << 20 {
        return -1;
    }
    let ys = if ys_len == 0 { &[][..] } else { std::slice::from_raw_parts(ys, ys_len as usize) };
    let mut sampler = Sampler::new(program, nodes, previous);
    let found = match closest(&mut sampler, p[0], p[1], p[2], p[3], ys, accept) {
        Ok(found) => found,
        Err(error) => return status(error),
    };
    let out = std::slice::from_raw_parts_mut(out, 6);
    let (x, y, z, leaf) = found.unwrap_or((0, 0, 0, -1));
    out.copy_from_slice(&[found.is_some() as i32, x, y, z, leaf, sampler.previous]);
    0
}

use super::{fill, Geometry, Outcome, ENTRIES};
use crate::world::level::biome::climate::Node;
use crate::world::level::levelgen::router::Program;

/// Palette entries a section can report (the 3-bit linear palette's capacity).
pub(crate) const PALETTE_CAP: usize = 8;
/// Packed longs a section can report (64 entries of up to 32 bits).
pub(crate) const RAW_CAP: usize = 32;

/// One chunk's BIOMES stage. `program` is a climate program
/// (`mattmc_climate_program_create`); `nodes` the validated climate tree of
/// `node_count` nodes and `values` each node's biome registry ID (-1 for
/// branches); `previous` the thread's previous leaf or -1. `geometry`: [quart
/// X, quart Z, first section's quart Y, section count, FlatCache grid side,
/// global palette bits]; `initial` each section's old first palette value.
/// Writes per section [requested bits, palette length, raw length] to `info`
/// (then the new previous leaf), palettes at `PALETTE_CAP` and raw storage at
/// `RAW_CAP` per section. Returns 0, 1 when a search found no leaf (nothing
/// written; Java fills the chunk itself), or negative for invalid input.
/// # Safety
/// `program` is live; buffers hold their stated sizes (`info` 3 * sections + 1,
/// `palette` and `raw` their caps per section), are disjoint and borrowed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_biome_fill(
    program: u64,
    nodes: *const Node,
    node_count: i32,
    values: *const i32,
    previous: i32,
    geometry: *const i32,
    initial: *const i32,
    info: *mut i32,
    palette: *mut i32,
    raw: *mut i64,
) -> i32 {
    if program == 0 || nodes.is_null() || values.is_null() || geometry.is_null() || initial.is_null() || info.is_null() || palette.is_null()
        || raw.is_null() || node_count <= 0
    {
        return -1;
    }
    let program = unsafe { &*(program as *const Program) };
    let g = unsafe { std::slice::from_raw_parts(geometry, 6) };
    let (Ok(sections), Ok(global_bits)) = (usize::try_from(g[3]), u32::try_from(g[5])) else { return -1 };
    if !(1..=256).contains(&sections) || !(1..=64).contains(&g[4]) || !(4..=32).contains(&global_bits) {
        return -1;
    }
    let nodes = unsafe { std::slice::from_raw_parts(nodes, node_count as usize) };
    let values = unsafe { std::slice::from_raw_parts(values, node_count as usize) };
    let initial = unsafe { std::slice::from_raw_parts(initial, sections) };
    let fits = |v: i32| v >= 0 && (v as u64) < 1u64 << global_bits;
    if nodes.iter().zip(values).any(|(n, v)| n.is_leaf() && !fits(*v)) || initial.iter().any(|v| !fits(*v))
        || previous < -1 || previous >= node_count || (previous >= 0 && !nodes[previous as usize].is_leaf())
    {
        return -2;
    }
    let geometry = Geometry { qx: g[0], qz: g[1], first_quart_y: g[2], sections, grid: g[4] };
    let Ok(outcome) = fill(program, nodes, values, previous, geometry, initial, global_bits) else { return -3 };
    let Outcome::Filled(containers, last) = outcome else { return 1 };
    let info = unsafe { std::slice::from_raw_parts_mut(info, 3 * sections + 1) };
    let palette = unsafe { std::slice::from_raw_parts_mut(palette, PALETTE_CAP * sections) };
    let raw = unsafe { std::slice::from_raw_parts_mut(raw, RAW_CAP * sections) };
    for (index, container) in containers.iter().enumerate() {
        let (entries, packed) = (container.palette(), container.packed());
        debug_assert!(entries.len() <= PALETTE_CAP && packed.len() <= RAW_CAP && packed.len() <= ENTRIES);
        info[index * 3..index * 3 + 3].copy_from_slice(&[container.requested_bits() as i32, entries.len() as i32, packed.len() as i32]);
        palette[index * PALETTE_CAP..index * PALETTE_CAP + entries.len()].copy_from_slice(entries);
        raw[index * RAW_CAP..index * RAW_CAP + packed.len()].copy_from_slice(&packed);
    }
    info[3 * sections] = last;
    0
}

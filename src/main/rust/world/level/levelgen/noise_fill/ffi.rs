//! C ABI for `NativeNoiseFill`. A handle lives for one `doFill` call on one
//! thread; Java creates it after its eligibility gate, drives every cell in
//! order, reads the results, and releases it in `finally`. The state flag
//! table is a process-lifetime allocation Java never frees. Cell inputs are
//! borrowed heap arrays (critical calls): each call is bounded to one cell.
use super::{Cell, Config, Corners, NoiseFill, OreVeins, Picker, Substance};
use crate::world::level::levelgen::random::Positional;
use crate::world::level::levelgen::synth::State;

struct Handle {
    fill: NoiseFill<'static>,
    cell_size: usize,
}

/// `ints`: [minY, height, minSection, sectionCount, cellWidth, cellHeight, air,
/// defaultBlock, globalBits, substance (1 aquifer batch, 2 disabled), lavaBelow,
/// lavaLevel, lava, fluidLevel, fluid, oreKind (0 none, 1 Xoroshiro, 2 Legacy),
/// 6 ore states, skipSamplingAboveY, fluidIsLava, lavaDefault]. `longs`: ore
/// factory seeds. `doubles`: ridged constant, ridged bound, gap xz scale, gap y
/// scale. Returns 0 for invalid arguments.
/// # Safety
/// `ints` has 25 i32s, `longs` 2 i64s, `doubles` 4 f64s (borrowed); `flags` has
/// `flag_count` bytes that stay valid and unchanged for the process lifetime.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_create(
    ints: *const i32,
    longs: *const i64,
    doubles: *const f64,
    flags: *const u8,
    flag_count: i32,
) -> u64 {
    if ints.is_null() || longs.is_null() || doubles.is_null() || flags.is_null() || flag_count <= 0 {
        return 0;
    }
    let i = unsafe { std::slice::from_raw_parts(ints, 25) };
    let l = unsafe { std::slice::from_raw_parts(longs, 2) };
    let d = unsafe { std::slice::from_raw_parts(doubles, 4) };
    let (width, height) = (i[4], i[5]);
    if !(1..=16).contains(&width) || 16 % width != 0 || !(1..=32).contains(&height) || width * width * height > 2048
        || !(1..=4096).contains(&i[3]) || i[1] <= 0 || !(1..=31).contains(&i[8])
    {
        return 0;
    }
    let picker = Picker { lava_below: i[10], lava_level: i[11], lava: i[12], fluid_level: i[13], fluid: i[14] };
    let substance = match i[9] {
        1 => Substance::AquiferBatch { picker, skip_above: i[22], fluid_is_lava: i[23] != 0, lava_default: i[24] },
        2 => Substance::Disabled(picker),
        _ => return 0,
    };
    let ore = if i[15] == 0 {
        None
    } else {
        let Some(random) = Positional::from_abi(i[15], l[0], l[1]) else { return 0 };
        Some(OreVeins {
            random,
            states: [i[16], i[17], i[18], i[19], i[20], i[21]],
            ridged_constant: d[0],
            ridged_bound: d[1],
            gap_xz_scale: d[2],
            gap_y_scale: d[3],
        })
    };
    let config = Config {
        min_y: i[0],
        height: i[1],
        min_section: i[2],
        section_count: i[3],
        cell_width: width,
        cell_height: height,
        air: i[6],
        default_block: i[7],
        global_bits: i[8] as u32,
        substance,
        ore,
    };
    let flags: &'static [u8] = unsafe { std::slice::from_raw_parts(flags, flag_count as usize) };
    let cell_size = (width * width * height) as usize;
    Box::into_raw(Box::new(Handle { fill: NoiseFill::new(config, flags), cell_size })) as u64
}

/// # Safety
/// A live handle from create, used by one thread at a time.
unsafe fn handle<'a>(id: u64) -> &'a mut Handle {
    unsafe { &mut *(id as *mut Handle) }
}

#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_release(id: u64) {
    if id != 0 {
        drop(unsafe { Box::from_raw(id as *mut Handle) });
    }
}

/// One cell at block (x, y, z): `density` has cellSize doubles, `materials`
/// cellSize*2 ints or null, `corners` 24 doubles (toggle, ridged a, ridged b;
/// null without ore veins), `gap` the vein gap noise state (or null). Returns
/// 0; 1 without writing when the cell needs aquifer batch materials and none
/// were given; -2 for an unknown state, -3 out of chunk.
/// # Safety
/// Pointers are aligned, readable for those lengths, and borrowed for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_cell(
    id: u64,
    x: i32,
    y: i32,
    z: i32,
    density: *const f64,
    materials: *const i32,
    corners: *const f64,
    gap: *const State,
) -> i32 {
    let handle = unsafe { handle(id) };
    let size = handle.cell_size;
    if density.is_null() {
        return -1;
    }
    let corner = |offset: usize| -> Corners {
        if corners.is_null() {
            Corners([0.0; 8])
        } else {
            let mut values = [0.0; 8];
            values.copy_from_slice(unsafe { std::slice::from_raw_parts(corners.add(offset), 8) });
            Corners(values)
        }
    };
    let cell = Cell {
        x,
        y,
        z,
        density: unsafe { std::slice::from_raw_parts(density, size) },
        materials: if materials.is_null() { &[] } else { unsafe { std::slice::from_raw_parts(materials, size * 2) } },
        toggle: corner(0),
        ridged_a: corner(8),
        ridged_b: corner(16),
        gap,
    };
    if cell.materials.is_empty() && handle.fill.needs_materials(&cell) {
        return 1;
    }
    match handle.fill.fill_cell(&cell) {
        Ok(()) => 0,
        Err(super::Error::UnknownState(_)) => -2,
        Err(super::Error::OutOfChunk) => -3,
    }
}

/// Section results: `info` receives [requestedBits, storageBits, paletteLength,
/// rawLength, nonEmpty, ticking, fluid]. Palette ids and raw longs are copied
/// when they fit `palette_cap`/`raw_cap`. Returns 1 when written, 0 when the
/// section was never written, -1 when a buffer is too small.
/// # Safety
/// Buffers are aligned, writable for their capacities and borrowed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_section(
    id: u64,
    index: i32,
    info: *mut i32,
    palette: *mut i32,
    palette_cap: i32,
    raw: *mut i64,
    raw_cap: i32,
) -> i32 {
    let handle = unsafe { handle(id) };
    let Some(section) = handle.fill.section(index.max(0) as usize).filter(|_| index >= 0) else {
        return 0;
    };
    let entries = section.palette();
    let packed = section.packed();
    let out = unsafe { std::slice::from_raw_parts_mut(info, 7) };
    out.copy_from_slice(&[
        section.requested_bits() as i32,
        section.storage_bits() as i32,
        entries.len() as i32,
        packed.len() as i32,
        section.non_empty,
        section.ticking,
        section.fluid,
    ]);
    if entries.len() > palette_cap.max(0) as usize || packed.len() > raw_cap.max(0) as usize {
        return -1;
    }
    unsafe {
        std::ptr::copy_nonoverlapping(entries.as_ptr(), palette, entries.len());
        std::ptr::copy_nonoverlapping(packed.as_ptr(), raw, packed.len());
    }
    1
}

/// Both heightmaps' raw data (OCEAN_FLOOR_WG then WORLD_SURFACE_WG) when each
/// fits `cap` longs; returns the raw length, or -1 when too small.
/// # Safety
/// Both buffers are aligned, writable for `cap` longs and borrowed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_heightmaps(id: u64, ocean_floor: *mut i64, world_surface: *mut i64, cap: i32) -> i32 {
    let handle = unsafe { handle(id) };
    let ocean = handle.fill.heightmap_raw(true);
    let surface = handle.fill.heightmap_raw(false);
    if ocean.len() > cap.max(0) as usize {
        return -1;
    }
    unsafe {
        std::ptr::copy_nonoverlapping(ocean.as_ptr(), ocean_floor, ocean.len());
        std::ptr::copy_nonoverlapping(surface.as_ptr(), world_surface, surface.len());
    }
    ocean.len() as i32
}

/// Post-processing positions as (x, y, z) triples in write order when they fit
/// `cap` triples; returns the count, or the negated count when too small.
/// # Safety
/// `out` is aligned, writable for `cap * 3` ints and borrowed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_post_process(id: u64, out: *mut i32, cap: i32) -> i32 {
    let handle = unsafe { handle(id) };
    let positions = handle.fill.post_process();
    if positions.len() > cap.max(0) as usize {
        return -(positions.len() as i32);
    }
    for (index, position) in positions.iter().enumerate() {
        unsafe { std::ptr::copy_nonoverlapping(position.as_ptr(), out.add(index * 3), 3) };
    }
    positions.len() as i32
}

/// Verification entry: replays `count` (index, state) writes into a fresh
/// section exactly as the fill does and reports it like
/// `mattmc_noise_fill_section` (counters left zero). Returns 1, or -1 when
/// a buffer is too small or an index is out of range.
/// # Safety
/// `writes` has `count * 2` readable i32s; output buffers as for section.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_replay_section(
    air: i32,
    global_bits: i32,
    writes: *const i32,
    count: i32,
    info: *mut i32,
    palette: *mut i32,
    palette_cap: i32,
    raw: *mut i64,
    raw_cap: i32,
) -> i32 {
    if writes.is_null() || count < 0 || !(1..=31).contains(&global_bits) {
        return -1;
    }
    let writes = unsafe { std::slice::from_raw_parts(writes, count as usize * 2) };
    let mut section = super::section::Section::new(air, global_bits as u32);
    for write in writes.chunks_exact(2) {
        if !(0..super::section::ENTRIES as i32).contains(&write[0]) {
            return -1;
        }
        section.set(write[0] as usize, write[1]);
    }
    let entries = section.palette();
    let packed = section.packed();
    let out = unsafe { std::slice::from_raw_parts_mut(info, 7) };
    out.copy_from_slice(&[
        section.requested_bits() as i32,
        section.storage_bits() as i32,
        entries.len() as i32,
        packed.len() as i32,
        0,
        0,
        0,
    ]);
    if entries.len() > palette_cap.max(0) as usize || packed.len() > raw_cap.max(0) as usize {
        return -1;
    }
    unsafe {
        std::ptr::copy_nonoverlapping(entries.as_ptr(), palette, entries.len());
        std::ptr::copy_nonoverlapping(packed.as_ptr(), raw, packed.len());
    }
    1
}

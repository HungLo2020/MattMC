//! C ABI for `NativeNoiseFill`. A handle lives for one `doFill` call on one
//! thread; Java creates it after its eligibility gate, drives every cell in
//! order, reads the results, and releases it in `finally`. The state flag
//! table is a process-lifetime allocation Java never frees. Cell inputs are
//! borrowed heap arrays (critical calls): each call is bounded to one cell.
use super::traversal::{Layout, Step, Traversal};
use super::{Cell, Config, Corners, NoiseFill, OreVeins, Picker, Substance};
use crate::world::level::levelgen::density::validation::density_validate;
use crate::world::level::levelgen::router::Router;
use crate::world::level::levelgen::random::Positional;
use crate::world::level::levelgen::synth::State;

struct Handle {
    fill: NoiseFill<'static>,
    cell_size: usize,
    traversal: Option<Traversal>,
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
    Box::into_raw(Box::new(Handle { fill: NoiseFill::new(config, flags), cell_size, traversal: None })) as u64
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
        Err(super::Error::CellProgram) => -4,
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

/// Hands the cell traversal to Rust. `ints`: [interpolators, columns, points,
/// minCellY, minX, minZ, ore (0/1), toggle, ridgedA, ridgedB, inputs,
/// input interpolator indices...]. `program` is the cell cache's density
/// program (`bytes` long). Returns 0, or negative when rejected (Java keeps
/// its own traversal).
/// # Safety
/// `router` is a live router handle that outlives this fill and is not used
/// elsewhere while bound; `program` stays live and immutable for the fill;
/// `ints` holds `count` i32s, borrowed for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_bind(id: u64, router: u64, program: *const u8, bytes: u64, ints: *const i32, count: i32) -> i32 {
    let handle = unsafe { handle(id) };
    if router == 0 || program.is_null() || ints.is_null() || count < 11 || handle.traversal.is_some() {
        return -1;
    }
    let i = unsafe { std::slice::from_raw_parts(ints, count as usize) };
    let index = |value: i32| usize::try_from(value).ok();
    let (Some(interpolators), Some(columns), Some(points), Some(inputs)) = (index(i[0]), index(i[1]), index(i[2]), index(i[10])) else {
        return -1;
    };
    let config = &handle.fill.config;
    let width = config.cell_width;
    let router_ref = unsafe { &*(router as *const Router) };
    if count as usize != 11 + inputs || inputs == 0 || inputs > 8 || columns as i32 != 16 / width + 1 || points < 2
        || router_ref.output_len() != interpolators * columns * points
    {
        return -2;
    }
    if unsafe { density_validate(program, bytes) } != 0 || unsafe { *program.cast::<u32>().add(2) } as usize != inputs {
        return -3;
    }
    let mut cell_inputs = Vec::with_capacity(inputs);
    for value in &i[11..] {
        match index(*value).filter(|v| *v < interpolators) {
            Some(v) => cell_inputs.push(v),
            None => return -4,
        }
    }
    let ore = match i[6] {
        0 => None,
        _ => match (index(i[7]), index(i[8]), index(i[9])) {
            (Some(t), Some(a), Some(b)) if t < interpolators && a < interpolators && b < interpolators => Some([t, a, b]),
            _ => return -4,
        },
    };
    let layout = Layout { interpolators, columns, points, min_cell_y: i[3], min_x: i[4], min_z: i[5], width, height: config.cell_height };
    handle.traversal = Some(unsafe { Traversal::new(router as *mut Router, program, cell_inputs, ore, layout) });
    0
}

/// Fills the bound traversal's low (`high` 0) or high slice at block X.
/// Returns 0, 1 when Java must upload the slice, -1 when unbound.
/// # Safety
/// A live handle used by one thread.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_slice(id: u64, high: i32, x: i32) -> i32 {
    match unsafe { handle(id) }.traversal.as_mut() {
        Some(traversal) => (!traversal.fill_slice(high != 0, x)) as i32,
        None => -1,
    }
}

/// A slice Java filled itself, in the router's output layout.
/// # Safety
/// `values` holds `count` readable doubles, borrowed for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_upload(id: u64, high: i32, values: *const f64, count: i32) -> i32 {
    let Some(traversal) = unsafe { handle(id) }.traversal.as_mut() else { return -1 };
    if values.is_null() || count < 0 || count as usize != traversal.slice_len() {
        return -2;
    }
    traversal.upload(high != 0, unsafe { std::slice::from_raw_parts(values, count as usize) });
    0
}

/// Runs the column of cells at cell X (0-based within the chunk). Returns 0
/// when the column is done, 1 when the cell whose block position was written
/// to `request` needs aquifer batch materials: its densities are in `density`
/// and the next call passes the materials. Negative on errors.
/// # Safety
/// Off-heap buffers: `materials` null or cellSize*2 i32s; `density` cellSize
/// writable doubles; `request` three writable i32s; `gap` null or a live
/// validated noise state. The bound router and program are live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_cells(id: u64, cell_x: i32, materials: *const i32, gap: *const State, density: *mut f64, request: *mut i32) -> i32 {
    let handle = unsafe { handle(id) };
    let size = handle.cell_size;
    let Some(traversal) = handle.traversal.as_mut() else { return -1 };
    if density.is_null() || request.is_null() {
        return -1;
    }
    let materials = (!materials.is_null()).then(|| unsafe { std::slice::from_raw_parts(materials, size * 2) });
    match traversal.cells(&mut handle.fill, cell_x, materials, gap) {
        Ok(Step::Done) => 0,
        Ok(Step::Materials(position)) => {
            unsafe {
                std::ptr::copy_nonoverlapping(traversal.density().as_ptr(), density, size);
                std::ptr::copy_nonoverlapping(position.as_ptr(), request, 3);
            }
            1
        }
        Err(super::Error::UnknownState(_)) => -2,
        Err(super::Error::OutOfChunk) => -3,
        Err(super::Error::CellProgram) => -4,
    }
}

/// Adds structure terrain adjustment to the bound traversal's cell densities:
/// `geometry` is `NativeBeardifier`'s packed layout (`count` i32s, copied),
/// `kernel` the shared kernel. Returns 0, or -1 when unbound or invalid.
/// # Safety
/// `geometry` holds `count` readable i32s for this call; `kernel` holds the
/// kernel's floats for the process lifetime.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_fill_beardifier(id: u64, geometry: *const i32, count: i32, kernel: *const f32) -> i32 {
    let Some(traversal) = unsafe { handle(id) }.traversal.as_mut() else { return -1 };
    if geometry.is_null() || kernel.is_null() || count < 8 {
        return -1;
    }
    let data = unsafe { std::slice::from_raw_parts(geometry, count as usize) }.to_vec();
    unsafe { traversal.set_beardifier(data, kernel) };
    0
}

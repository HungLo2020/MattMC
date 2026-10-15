//! Stable surface ABI; buffers are borrowed only for each call.
/// # Safety
/// `out` is writable and aligned for `count` i32 values, exclusively borrowed.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_biomes(
    seed: i64,
    x: i32,
    z: i32,
    min_y: i32,
    count: i32,
    out: *mut i32,
) -> i32 {
    unsafe {
        crate::world::level::biome::fiddled_distance::surface_biomes(seed, x, z, min_y, count, out)
    }
}
/// # Safety
/// The program is a readable, aligned array of `len` i32 values. Header word 0
/// is the instruction area length; instructions start at word 8, and all jumps
/// are forward. Trailing data contains biome registry IDs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_validate(program: *const i32, len: i32) -> i32 {
    unsafe { super::program::surface_validate(program, len) }
}
/// # Safety
/// A validated immutable program of `program_len` i32s, readable flags/biomes
/// and exclusively writable output of `len` i32s, 24 writable frame words and
/// `program[0]/8` writable cache words. All pointers are aligned and nonaliasing.
/// Frame: y-index,q,water,bottom,pc,phase,updates,answer,answer-ready,unused,
/// depth-below,surface-depth,min-y,unused,min-surface,min-ready,secondary-ready,
/// request,band-offset,band-offset-ready,below-world-sentinel,unused,unused,unused.
/// Returns 1=done, 2=external request, 0=bounded work yield, negative=bad input.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_step(
    program: *const i32,
    program_len: i32,
    flags: *const i32,
    output: *mut i32,
    biomes: *const i32,
    len: i32,
    frame: *mut i32,
    cache: *mut i32,
    secondary: f64,
) -> i32 {
    unsafe {
        super::evaluator::surface_step(
            program,
            program_len,
            flags,
            output,
            biomes,
            len,
            frame,
            cache,
            secondary,
        )
    }
}

use super::chunk::{Biomes, Inputs, Slot, SurfaceChunk};
use super::scalar::ScalarSurface;
use crate::world::level::levelgen::random::Positional;
use crate::world::level::levelgen::router::Program;
use crate::world::level::levelgen::synth::State;
use crate::world::level::levelgen::proto_chunk::ProtoStorage;

/// The SURFACE stage over a chunk storage (`mattmc_proto_chunk_create`).
/// `ints`: [defaultBlock, usesBiomes, quart origin x, y, z, quart sizes x, y,
/// z, slotCount, then per condition slot (kind: 0 Java, 1 steep, 2 vertical
/// gradient, 3 noise threshold; gradient low, high; positional random kind),
/// then quart biome ids]. Per slot `slot_longs` holds two values (a gradient's
/// seeds, or a noise threshold's state address) and `slot_doubles` two (a
/// noise threshold's min and max). `inputs`: [band noise state, secondary
/// noise state, preliminary surface program], each 0 when Java answers it.
/// `seed` is the biome zoom seed. Returns 0 if invalid.
/// # Safety
/// `ints` holds `int_count` values and the slot arrays `ints[8]` pairs, all
/// borrowed for this call; `storage` is live, outlives the stage and is used
/// only through it while the stage runs; every noise state and program in
/// the slots and `inputs` stays live while the stage runs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_chunk_create(storage: u64, ints: *const i32, int_count: i32, seed: i64, slot_longs: *const i64,
    slot_doubles: *const f64, inputs: *const i64) -> u64 {
    if storage == 0 || ints.is_null() || int_count < 9 || slot_longs.is_null() || slot_doubles.is_null() || inputs.is_null() {
        return 0;
    }
    let i = unsafe { std::slice::from_raw_parts(ints, int_count as usize) };
    let Ok(slots) = usize::try_from(i[8]) else { return 0 };
    if slots > 65536 {
        return 0;
    }
    let l = unsafe { std::slice::from_raw_parts(slot_longs, slots * 2) };
    let d = unsafe { std::slice::from_raw_parts(slot_doubles, slots * 2) };
    let n = unsafe { std::slice::from_raw_parts(inputs, 3) };
    let Some((default_block, biomes, slots)) = parse(i, seed, l, d) else { return 0 };
    let inputs = Inputs { band: n[0] as *const State, secondary: n[1] as *const State, levels: n[2] as *const Program };
    match unsafe { SurfaceChunk::new(storage as *mut ProtoStorage, default_block, biomes, slots, inputs) } {
        Ok(chunk) => Box::into_raw(Box::new(chunk)) as u64,
        Err(_) => 0,
    }
}

fn parse(i: &[i32], seed: i64, l: &[i64], d: &[f64]) -> Option<(i32, Option<Biomes>, Vec<Slot>)> {
    let (default_block, uses_biomes) = (i[0], i[1] != 0);
    let (sizes, count) = ([usize::try_from(i[5]).ok()?, usize::try_from(i[6]).ok()?, usize::try_from(i[7]).ok()?], usize::try_from(i[8]).ok()?);
    if sizes.iter().any(|s| *s > 4096) {
        return None;
    }
    let mut at = 9;
    let slots = parse_slots(i.get(at..at + count * 4)?, l, d, false)?;
    at += count * 4;
    let biomes = if uses_biomes {
        let count = sizes[0] * sizes[1] * sizes[2];
        let ids = i.get(at..at + count)?.to_vec();
        at += count;
        Some(Biomes { seed, qx: i[2], qy: i[3], qz: i[4], size_x: sizes[0], size_y: sizes[1], size_z: sizes[2], ids })
    } else {
        None
    };
    (at == i.len()).then_some((default_block, biomes, slots))
}

/// Condition slots, four ints each: [kind (0 Java, 1 steep, 2 vertical
/// gradient, 3 noise threshold; with `extended` also 4 biome over id range
/// [low, low + high), 5 temperature), gradient low, high, positional random
/// kind]. `l` and `d` hold two values per slot.
fn parse_slots(h: &[i32], l: &[i64], d: &[f64], extended: bool) -> Option<Vec<Slot>> {
    let count = h.len() / 4;
    if h.len() % 4 != 0 || l.len() < count * 2 || d.len() < count * 2 {
        return None;
    }
    (0..count)
        .map(|k| {
            let h = &h[k * 4..k * 4 + 4];
            Some(match h[0] {
                0 => Slot::Java,
                1 => Slot::Steep,
                // Without a band the evaluator's bounds answer every Y.
                2 if h[1] >= h[2] && !extended => Slot::Java,
                2 => Slot::Gradient { low: h[1], high: h[2], random: Positional::from_abi(h[3], l[k * 2], l[k * 2 + 1])? },
                3 if l[k * 2] != 0 => Slot::Noise { state: l[k * 2] as *const State, min: d[k * 2], max: d[k * 2 + 1] },
                4 if extended => Slot::Biome { start: u32::try_from(h[1]).ok()?, len: u32::try_from(h[2]).ok()? },
                5 => Slot::Temperature,
                _ => return None,
            })
        })
        .collect()
}

/// `SurfaceSystem.topMaterial`'s rule for the carvers ([`ScalarSurface`]).
/// `slots` holds four ints per slot (see [`parse_slots`], extended kinds
/// allowed), `slot_longs`/`slot_doubles` two values each; `biome_ids` the biome
/// slots' sorted registry ids. `inputs`: [band noise, secondary noise,
/// preliminary surface program, surface noise], none 0. `depth` is the surface
/// depth's positional random: [kind, a, b]. Returns 0 when the rule cannot be
/// answered natively.
/// # Safety
/// The arrays are readable for their lengths; the states and program stay
/// alive and unchanged while the handle is used.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_scalar_create(program: *const i32, program_len: i32, slots: *const i32, slot_count: i32, slot_longs: *const i64,
    slot_doubles: *const f64, biome_ids: *const i32, biome_count: i32, inputs: *const i64, depth: *const i64, way_below_min_y: i32) -> u64 {
    if program.is_null() || slots.is_null() || slot_longs.is_null() || slot_doubles.is_null() || biome_ids.is_null() || inputs.is_null() || depth.is_null()
        || !(16..=1 << 20).contains(&program_len) || !(0..=65536).contains(&slot_count) || !(0..=1 << 20).contains(&biome_count)
    {
        return 0;
    }
    let count = slot_count as usize;
    let h = unsafe { std::slice::from_raw_parts(slots, count * 4) };
    let l = unsafe { std::slice::from_raw_parts(slot_longs, count * 2) };
    let d = unsafe { std::slice::from_raw_parts(slot_doubles, count * 2) };
    let Some(slots) = parse_slots(h, l, d, true) else { return 0 };
    let ids = unsafe { std::slice::from_raw_parts(biome_ids, biome_count as usize) }.to_vec();
    let n = unsafe { std::slice::from_raw_parts(inputs, 4) };
    let r = unsafe { std::slice::from_raw_parts(depth, 3) };
    let Some(random) = Positional::from_abi(r[0] as i32, r[1], r[2]) else { return 0 };
    let inputs = Inputs { band: n[0] as *const State, secondary: n[1] as *const State, levels: n[2] as *const Program };
    let program = unsafe { std::slice::from_raw_parts(program, program_len as usize) }.to_vec();
    match unsafe { ScalarSurface::new(program, slots, ids, inputs, n[3] as *const State, random, way_below_min_y) } {
        Some(scalar) => Box::into_raw(Box::new(scalar)) as u64,
        None => 0,
    }
}

/// The scalar route's answered requests by kind (see [`super::scalar::REQUESTS`]).
/// # Safety
/// `out` has eight writable i64s.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_scalar_requests(out: *mut i64) {
    if out.is_null() {
        return;
    }
    let out = unsafe { std::slice::from_raw_parts_mut(out, 8) };
    for (k, counter) in super::scalar::REQUESTS.iter().enumerate() {
        out[k] = counter.load(std::sync::atomic::Ordering::Relaxed) as i64;
    }
}

/// # Safety
/// A handle from `mattmc_surface_scalar_create`, not used afterwards.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_scalar_release(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut ScalarSurface) });
    }
}

unsafe fn chunk<'a>(handle: u64) -> &'a mut SurfaceChunk {
    unsafe { &mut *(handle as *mut SurfaceChunk) }
}

/// Starts column (x, z) up to `top`; returns the column length or negative.
/// # Safety
/// A live handle used by one thread.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_chunk_begin(handle: u64, x: i32, z: i32, top: i32, uses_biomes: i32) -> i32 {
    match unsafe { chunk(handle) }.begin(x, z, top, uses_biomes != 0) {
        Ok(count) => count as i32,
        Err(_) => -1,
    }
}

/// Runs the started column: 1 when done, 2 for a Java request, negative on errors.
/// # Safety
/// `program` is a validated surface program of `program_len` words; `frame`
/// 24 words and `cache` `program[0] / 8` words, all borrowed for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_chunk_run(handle: u64, program: *const i32, program_len: i32, frame: *mut i32, cache: *mut i32, secondary: f64) -> i32 {
    if program.is_null() || frame.is_null() || cache.is_null() || program_len < 16 {
        return -1;
    }
    let program = unsafe { std::slice::from_raw_parts(program, program_len as usize) };
    let cache_len = (program[0] / 8).max(0) as usize;
    let frame = unsafe { std::slice::from_raw_parts_mut(frame, 24) };
    let cache = unsafe { std::slice::from_raw_parts_mut(cache, cache_len) };
    match unsafe { chunk(handle) }.run(program, frame, cache, secondary) {
        Ok(status) => status,
        Err(super::chunk::Error::State(_)) => -3,
        Err(super::chunk::Error::Evaluation(status)) => status.min(-4),
        Err(super::chunk::Error::Input) => -2,
    }
}

/// # Safety
/// A handle from create, not used afterwards. The storage stays live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_chunk_release(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut SurfaceChunk) });
    }
}

use super::pass::{Pass, PassInputs, STATE_WORDS};

/// `buildOwnedSurface`'s column loop over a stage (`mattmc_surface_chunk_create`).
/// `ints`: [usesBiomes, chunk min block X, Z, sea level, biome-at-zero,
/// eroded badlands id, frozen ocean id, deep frozen ocean id, default block
/// state, snow block state, packed ice state, WAY_BELOW_MIN_Y,
/// default-state count, water-state count, then the default block's and
/// water's sorted state ids]. `longs`: [surface noise, badlands surface,
/// pillar, pillar roof, iceberg surface, pillar, pillar roof (state
/// addresses), random kind, random a, random b]. Returns 0 if invalid.
/// # Safety
/// `ints`/`longs` are borrowed for this call; `stage` is live, outlives the
/// pass and is used only through it while the pass runs; the noise states stay
/// live and unchanged.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_pass_create(stage: u64, ints: *const i32, int_count: i32, longs: *const i64, long_count: i32) -> u64 {
    if stage == 0 || ints.is_null() || longs.is_null() || int_count < 14 || long_count != 10 {
        return 0;
    }
    let i = unsafe { std::slice::from_raw_parts(ints, int_count as usize) };
    let l = unsafe { std::slice::from_raw_parts(longs, 10) };
    let (Ok(defaults), Ok(waters)) = (usize::try_from(i[12]), usize::try_from(i[13])) else { return 0 };
    if 14 + defaults + waters != i.len() {
        return 0;
    }
    let default_states = i[14..14 + defaults].to_vec();
    let water_states = i[14 + defaults..].to_vec();
    if !default_states.is_sorted() || !water_states.is_sorted() {
        return 0;
    }
    let Some(random) = Positional::from_abi(l[7] as i32, l[8], l[9]) else { return 0 };
    let state = |at: usize| l[at] as *const State;
    let inputs = PassInputs {
        surface: state(0),
        random,
        badlands: [state(1), state(2), state(3)],
        iceberg: [state(4), state(5), state(6)],
        sea_level: i[3],
        biome_at_zero: i[4] != 0,
        eroded_badlands: i[5],
        frozen_ocean: i[6],
        deep_frozen_ocean: i[7],
        default_states,
        water_states,
        default_block: i[8],
        snow_block: i[9],
        packed_ice: i[10],
        way_below_min_y: i[11],
    };
    match unsafe { Pass::new(stage as *mut SurfaceChunk, inputs, i[0] != 0, i[1], i[2]) } {
        Ok(pass) => Box::into_raw(Box::new(pass)) as u64,
        Err(_) => 0,
    }
}

/// Runs the pass: 0 when every column is done, 2 for a rule request (the
/// frame, as `mattmc_surface_chunk_run`), 3 for the frozen ocean's melt check
/// (answer 0/1 in state word 6), negative on errors. `state` holds
/// `STATE_WORDS` words, written on every return.
/// # Safety
/// `program` is a validated surface program of `program_len` words; `frame`
/// 24 words, `cache` `program[0] / 8` words and `state` 8 words, all borrowed
/// for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_pass_run(pass: u64, program: *const i32, program_len: i32, frame: *mut i32, cache: *mut i32, secondary: f64,
    state: *mut i64) -> i32 {
    if pass == 0 || program.is_null() || frame.is_null() || cache.is_null() || state.is_null() || program_len < 16 {
        return -1;
    }
    let program = unsafe { std::slice::from_raw_parts(program, program_len as usize) };
    let cache_len = (program[0] / 8).max(0) as usize;
    let frame = unsafe { std::slice::from_raw_parts_mut(frame, 24) };
    let cache = unsafe { std::slice::from_raw_parts_mut(cache, cache_len) };
    let state = unsafe { std::slice::from_raw_parts_mut(state, STATE_WORDS) };
    let pass = unsafe { &mut *(pass as *mut Pass) };
    match pass.run(program, frame, cache, secondary, state) {
        Ok(status) => status,
        Err(super::chunk::Error::State(_)) => -3,
        Err(super::chunk::Error::Evaluation(status)) => status.min(-4),
        Err(super::chunk::Error::Input) => -2,
    }
}

/// Melt checks the pass asked Java for.
/// # Safety
/// A live pass handle.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_pass_temperature_calls(pass: u64) -> i32 {
    if pass == 0 { 0 } else { unsafe { &*(pass as *const Pass) }.temperature_calls as i32 }
}

/// # Safety
/// A handle from create, not used afterwards. The stage stays live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_pass_release(pass: u64) {
    if pass != 0 {
        drop(unsafe { Box::from_raw(pass as *mut Pass) });
    }
}

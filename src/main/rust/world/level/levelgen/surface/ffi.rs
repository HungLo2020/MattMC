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

use super::chunk::{Biomes, SurfaceChunk};
use crate::world::level::levelgen::proto_chunk::ProtoStorage;

/// The SURFACE stage over a chunk storage (`mattmc_proto_chunk_create`).
/// `ints`: [defaultBlock, usesBiomes, quart origin x, y, z, quart sizes x, y,
/// z, steepSlots, then steep flags per condition slot, then quart biome ids].
/// `seed` is the biome zoom seed. Returns 0 if invalid.
/// # Safety
/// `ints` holds `int_count` values for this call; `storage` is live, outlives
/// the stage and is used only through it while the stage runs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_surface_chunk_create(storage: u64, ints: *const i32, int_count: i32, seed: i64) -> u64 {
    if storage == 0 || ints.is_null() || int_count < 9 {
        return 0;
    }
    let i = unsafe { std::slice::from_raw_parts(ints, int_count as usize) };
    let Some((default_block, biomes, steep)) = parse(i, seed) else { return 0 };
    match unsafe { SurfaceChunk::new(storage as *mut ProtoStorage, default_block, biomes, steep) } {
        Ok(chunk) => Box::into_raw(Box::new(chunk)) as u64,
        Err(_) => 0,
    }
}

fn parse(i: &[i32], seed: i64) -> Option<(i32, Option<Biomes>, Vec<bool>)> {
    let (default_block, uses_biomes) = (i[0], i[1] != 0);
    let (sizes, slots) = ([usize::try_from(i[5]).ok()?, usize::try_from(i[6]).ok()?, usize::try_from(i[7]).ok()?], usize::try_from(i[8]).ok()?);
    if slots > 65536 || sizes.iter().any(|s| *s > 4096) {
        return None;
    }
    let mut at = 9;
    let steep: Vec<bool> = i.get(at..at + slots)?.iter().map(|v| *v != 0).collect();
    at += slots;
    let biomes = if uses_biomes {
        let count = sizes[0] * sizes[1] * sizes[2];
        let ids = i.get(at..at + count)?.to_vec();
        at += count;
        Some(Biomes { seed, qx: i[2], qy: i[3], qz: i[4], size_x: sizes[0], size_y: sizes[1], size_z: sizes[2], ids })
    } else {
        None
    };
    (at == i.len()).then_some((default_block, biomes, steep))
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

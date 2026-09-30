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

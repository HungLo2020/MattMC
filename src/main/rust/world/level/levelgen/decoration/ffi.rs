//! Decoration exports. Programs are owned boxes released by Java; region and
//! output words are borrowed for one call. No Java callbacks.
use super::placement::{heights_resolve, Placement};
use super::program::Program;
use super::random::WorldgenRandom;
use super::region::{Failure, Region};

/// Placement results. Negative values other than `UNAVAILABLE_CHUNK` before
/// any draw (`INVALID`, `JAVA_ROUTE`) leave the world untouched.
pub(crate) const PLACED: i32 = 1;
pub(crate) const NOT_PLACED: i32 = 0;
pub(crate) const INVALID: i32 = -1;
/// A read reached a chunk the region cannot serve; detail words hold its position.
pub(crate) const UNAVAILABLE_CHUNK: i32 = -2;
/// The binding failed mid-placement: the world may hold partial writes.
pub(crate) const BROKEN_BINDING: i32 = -3;
/// The program needs Java in this context (an empty height range); nothing ran.
pub(crate) const JAVA_ROUTE: i32 = -4;
pub(crate) const PANIC: i32 = -5;

/// # Safety
/// `words` is valid for `length` ints for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_program_create(words: *const i32, length: i32) -> *mut Program {
    if words.is_null() || length < 0 {
        return std::ptr::null_mut();
    }
    let words = std::slice::from_raw_parts(words, length as usize);
    let Some(policies) = crate::world::level::chunk::counters::installed_policies() else {
        return std::ptr::null_mut();
    };
    std::panic::catch_unwind(|| Program::decode(words).filter(|program| program.writes_admitted(policies)))
        .ok()
        .flatten()
        .map_or(std::ptr::null_mut(), |program| Box::into_raw(Box::new(program)))
}

/// # Safety
/// `program` came from `mattmc_decoration_program_create` and is released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_program_release(program: *mut Program) {
    if !program.is_null() {
        drop(Box::from_raw(program));
    }
}

/// Compares Rust's `Mth.SIN` with Java's table; 1 when every entry is bit-identical.
///
/// # Safety
/// `table` is valid for 65536 floats for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_sine_matches(table: *const f32) -> i32 {
    if table.is_null() {
        return 0;
    }
    let java = std::slice::from_raw_parts(table, 65536);
    let rust = super::ore::sine_table();
    i32::from(java.iter().zip(rust.iter()).all(|(a, b)| a.to_bits() == b.to_bits()))
}

/// 1 when Rust can prime heightmaps exactly: no custom `BlockState` subclass is registered.
#[no_mangle]
pub extern "C" fn mattmc_decoration_lazy_priming() -> i32 {
    crate::world::level::levelgen::heightmap::installed_masks().map_or(0, |masks| i32::from(!masks.custom))
}

/// One `PlacedFeature.placeWithBiomeCheck` after `setFeatureSeed(decoration, index, step)`.
///
/// # Safety
/// `program` is live; `region`/`out` are valid for their lengths; every owner
/// pointer in the region words stays alive for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_place(
    program: *const Program,
    region: *const i64,
    region_length: i32,
    out: *mut i64,
    out_length: i32,
    decoration_seed: i64,
    index: i32,
    step: i32,
    x: i32,
    y: i32,
    z: i32,
) -> i32 {
    let (Some(program), false, false) = (program.as_ref(), region.is_null(), out.is_null()) else {
        return INVALID;
    };
    if region_length < 0 || out_length < 0 {
        return INVALID;
    }
    let words = std::slice::from_raw_parts(region, region_length as usize);
    let out = std::slice::from_raw_parts_mut(out, out_length as usize);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(mut region) = Region::bind(words, out) else {
            return INVALID;
        };
        if !heights_resolve(program, region.min_gen_y, region.gen_depth) {
            return JAVA_ROUTE;
        }
        let mut random = WorldgenRandom::new(0);
        random.set_feature_seed(decoration_seed, index, step);
        match Placement::new(program, &mut region, random).run([x, y, z]) {
            Ok(true) => PLACED,
            Ok(false) => NOT_PLACED,
            Err(failure) => {
                region.fail_detail(&failure);
                match failure {
                    Failure::UnavailableChunk { .. } => UNAVAILABLE_CHUNK,
                    Failure::Unbound | Failure::Declined => BROKEN_BINDING,
                }
            }
        }
    }));
    result.unwrap_or(PANIC)
}

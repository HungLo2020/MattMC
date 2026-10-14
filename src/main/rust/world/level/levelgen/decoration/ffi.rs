//! Decoration exports. Programs are owned boxes released by Java; region and
//! output words are borrowed for one call. No Java callbacks.
use super::placement::{heights_resolve, Placement, Scratch};
use std::cell::RefCell;

thread_local! {
    /// Vein buffers reused across placements on each worker thread.
    static SCRATCH: RefCell<Scratch> = RefCell::new(Scratch::default());
    /// The last placement's Java-owned effects, until Java copies them.
    static EVENTS: RefCell<Vec<[i64; 5]>> = const { RefCell::new(Vec::new()) };
}

/// Hands events to Java before a sync upcall, which copies them as it would after the call.
pub(crate) fn publish_events(events: Vec<[i64; 5]>) {
    EVENTS.with(|e| *e.borrow_mut() = events);
}

/// Copies the last placement's events (five words each) when `capacity` holds
/// them all, then clears them; returns the event count either way.
///
/// # Safety
/// `output` is valid for `capacity` events for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_events(output: *mut i64, capacity: i32) -> i32 {
    EVENTS.with(|events| {
        let mut events = events.borrow_mut();
        let count = events.len();
        if !output.is_null() && capacity >= 0 && count <= capacity as usize {
            let out = std::slice::from_raw_parts_mut(output, count * 5);
            for (slot, event) in out.chunks_exact_mut(5).zip(events.iter()) {
                slot.copy_from_slice(event);
            }
            events.clear();
        }
        count as i32
    })
}

thread_local! {
    /// The last template placement's placed positions and block-entity loads, until Java copies them.
    static TEMPLATE_RESULTS: RefCell<(Vec<i64>, Vec<i64>)> = const { RefCell::new((Vec::new(), Vec::new())) };
}

/// # Safety
/// `words` is valid for `length` ints for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_template_create(words: *const i32, length: i32) -> *mut super::template::Template {
    if words.is_null() || length < 0 {
        return std::ptr::null_mut();
    }
    let words = std::slice::from_raw_parts(words, length as usize);
    let Some(policies) = crate::world::level::chunk::counters::installed_policies() else {
        return std::ptr::null_mut();
    };
    let admitted = |state: u32| policies.get(state as usize).is_some_and(Option::is_some);
    std::panic::catch_unwind(|| super::template::Template::decode(words).filter(|t| t.states().all(admitted)))
        .ok()
        .flatten()
        .map_or(std::ptr::null_mut(), |t| Box::into_raw(Box::new(t)))
}

/// # Safety
/// `template` came from `mattmc_template_create` and is released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_template_release(template: *mut super::template::Template) {
    if !template.is_null() {
        drop(Box::from_raw(template));
    }
}

/// # Safety
/// `words` is valid for `length` ints for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_template_processors_create(words: *const i32, length: i32) -> *mut super::template::Processors {
    if words.is_null() || length < 0 {
        return std::ptr::null_mut();
    }
    let words = std::slice::from_raw_parts(words, length as usize);
    let Some(policies) = crate::world::level::chunk::counters::installed_policies() else {
        return std::ptr::null_mut();
    };
    let admitted = |state: u32| policies.get(state as usize).is_some_and(Option::is_some);
    std::panic::catch_unwind(|| super::template::Processors::decode(words).filter(|p| p.states().all(admitted)))
        .ok()
        .flatten()
        .map_or(std::ptr::null_mut(), |p| Box::into_raw(Box::new(p)))
}

/// # Safety
/// `processors` came from `mattmc_template_processors_create` and is released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_template_processors_release(processors: *mut super::template::Processors) {
    if !processors.is_null() {
        drop(Box::from_raw(processors));
    }
}

/// `StructureTemplate.placeInWorld`'s processing and write loop (`template.rs`).
/// `args`: `[origin xyz, pivot xyz, mirror, rotation, has bounds, bounds 6, flags,
/// waterlogging, 0, 0, seedLo, seedHi, rotation pivot xyz]`; the random state is written back.
///
/// # Safety
/// As `mattmc_decoration_place`; `args` is valid for 24 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_template_place(
    template: *const super::template::Template,
    processors: *const super::template::Processors,
    region: *const i64,
    region_length: i32,
    out: *mut i64,
    out_length: i32,
    args: *mut i64,
) -> i32 {
    let (Some(template), Some(processors), false, false, false) = (template.as_ref(), processors.as_ref(), region.is_null(), out.is_null(), args.is_null()) else {
        return INVALID;
    };
    if region_length < 0 || out_length < 0 {
        return INVALID;
    }
    let args = std::slice::from_raw_parts_mut(args, 24);
    let words = super::region::Shared::new(region as *mut i64, region_length as usize);
    let out = super::region::Shared::new(out, out_length as usize);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(mut region) = Region::bind(words, out) else {
            return INVALID;
        };
        if !(region.write_path_ready() && super::facts::installed().is_some_and(|f| !f.any_unknown())) {
            return JAVA_ROUTE;
        }
        let a = |i: usize| args[i] as i32;
        let mut random = WorldgenRandom::new(0);
        random.set_state(args[19], args[20]);
        let mut call = super::template::Call {
            origin: [a(0), a(1), a(2)],
            pivot: [a(3), a(4), a(5)],
            rotation_pivot: [a(21), a(22), a(23)],
            mirror: (args[6] as usize).min(2),
            rotation: (args[7] as usize).min(3),
            bounds: (args[8] != 0).then(|| [a(9), a(10), a(11), a(12), a(13), a(14)]),
            flags: a(15),
            waterlogging: args[16] != 0,
            shared_random: args[17] != 0,
            random,
        };
        let result = template.place(processors, &mut region, &mut call);
        let (lo, hi) = call.random.state();
        args[19] = lo;
        args[20] = hi;
        EVENTS.with(|e| *e.borrow_mut() = std::mem::take(&mut region.events));
        match result {
            Ok(placed) => {
                let positions = placed.positions.iter().flat_map(|p| [p[0] as i64, p[1] as i64, p[2] as i64]).collect();
                TEMPLATE_RESULTS.with(|r| *r.borrow_mut() = (positions, placed.loads));
                PLACED
            }
            Err(failure) => failure_status(&mut region, failure),
        }
    }));
    result.unwrap_or(PANIC)
}

/// Copies the last template placement's results (`which` 0: placed positions as
/// x, y, z; 1: block-entity load words) when `capacity` holds them; returns their length.
///
/// # Safety
/// `output` is valid for `capacity` longs for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_template_results(which: i32, output: *mut i64, capacity: i32) -> i32 {
    TEMPLATE_RESULTS.with(|results| {
        let results = results.borrow();
        let words = if which == 0 { &results.0 } else { &results.1 };
        if !output.is_null() && capacity >= 0 && words.len() <= capacity as usize {
            std::slice::from_raw_parts_mut(output, words.len()).copy_from_slice(words);
        }
        words.len() as i32
    })
}

/// Verification only: the `writes`th native block write from now fails (negative disables).
#[no_mangle]
pub extern "C" fn mattmc_decoration_test_fail_after(writes: i64) {
    super::region::inject_failure_after(writes);
}

/// Installs Java's per-state facts (`facts.rs`); 1 when installed and identical.
///
/// # Safety
/// `words` is valid for `length` ints for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_install_facts(words: *const u32, length: i32) -> i32 {
    if words.is_null() || length < 0 {
        return 0;
    }
    i32::from(super::facts::install(std::slice::from_raw_parts(words, length as usize)))
}
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
/// A Java sync upcall threw; Java rethrows its exception.
pub(crate) const CALLBACK_FAILED: i32 = -6;

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

/// The program's outermost feature kind (`Feature::kind`), or 0.
///
/// # Safety
/// `program` is null or live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_program_kind(program: *const Program) -> i32 {
    program.as_ref().map_or(0, |p| p.placed.feature.kind())
}

/// 1 when the program writes through `WorldGenRegion.setBlock`.
///
/// # Safety
/// `program` is null or live.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_program_set_block(program: *const Program) -> i32 {
    program.as_ref().map_or(0, |p| i32::from(p.placed.feature.uses_set_block()))
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
    callback: Option<super::region::Callback>,
    gaussian: *mut i64,
) -> i32 {
    let (Some(program), false, false, false) = (program.as_ref(), region.is_null(), out.is_null(), gaussian.is_null()) else {
        return INVALID;
    };
    let gaussian = std::slice::from_raw_parts_mut(gaussian, 2);
    if region_length < 0 || out_length < 0 {
        return INVALID;
    }
    let words = super::region::Shared::new(region as *mut i64, region_length as usize);
    let out = super::region::Shared::new(out, out_length as usize);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(mut region) = Region::bind(words, out) else {
            return INVALID;
        };
        region.callback = callback;
        let mut cache = super::random::gaussian_from(gaussian[0], gaussian[1]);
        let result = run_program(program, &mut region, decoration_seed, index, step, [x, y, z], &mut cache);
        gaussian.copy_from_slice(&super::random::gaussian_words(cache));
        EVENTS.with(|e| *e.borrow_mut() = std::mem::take(&mut region.events));
        match result {
            Ok(PlaceOutcome::Placed(true)) => PLACED,
            Ok(PlaceOutcome::Placed(false)) => NOT_PLACED,
            Ok(PlaceOutcome::JavaRoute) => JAVA_ROUTE,
            Err(failure) => failure_status(&mut region, failure),
        }
    }));
    result.unwrap_or(PANIC)
}

fn failure_status(region: &mut Region, failure: Failure) -> i32 {
    region.fail_detail(&failure);
    match failure {
        Failure::UnavailableChunk { .. } => UNAVAILABLE_CHUNK,
        Failure::Unbound | Failure::Declined => BROKEN_BINDING,
        Failure::Callback => CALLBACK_FAILED,
    }
}

pub(crate) enum PlaceOutcome {
    /// The placement ran: whether any position placed the feature.
    Placed(bool),
    /// Nothing ran: Java must place it (an empty height range, or a `setBlock`
    /// feature without a ready write path).
    JavaRoute,
}

/// One `placeWithBiomeCheck` over a bound region after `setFeatureSeed(decoration, index, step)`.
pub(crate) fn run_program(
    program: &Program,
    region: &mut Region,
    decoration_seed: i64,
    index: i32,
    step: i32,
    origin: [i32; 3],
    gaussian: &mut Option<f64>,
) -> super::region::Result<PlaceOutcome> {
    if !heights_resolve(program, region.min_gen_y, region.gen_depth) {
        return Ok(PlaceOutcome::JavaRoute);
    }
    // `setBlock` features can write states the world supplies (re-distanced
    // leaves), so every state's post-processing must be known.
    if program.placed.feature.uses_set_block()
        && !(region.write_path_ready() && super::facts::installed().is_some_and(|f| !f.any_unknown()))
    {
        return Ok(PlaceOutcome::JavaRoute);
    }
    let mut random = WorldgenRandom::new(0);
    random.set_feature_seed(decoration_seed, index, step);
    random.gaussian = *gaussian;
    let scratch = SCRATCH.with(|s| std::mem::take(&mut *s.borrow_mut()));
    let (result, scratch, after) = Placement::new(program, region, random, scratch).run(origin);
    SCRATCH.with(|s| *s.borrow_mut() = scratch);
    *gaussian = after;
    result.map(PlaceOutcome::Placed)
}

/// Decoration loop results: done, or Java finishes from the resume point in `info`.
pub(crate) const DECORATED: i32 = 1;
/// `info` words before the kind counters.
pub(crate) const INFO_HEAD: usize = 8;
/// Kind counters after the `info` head.
pub(crate) const INFO_KINDS: usize = 64;
pub(crate) const RESUME: i32 = 2;

/// # Safety
/// `words` is valid for `length` ints and `programs` for `count` addresses of
/// live programs (or 0), all owned by Java for the plan's life.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_plan_create(words: *const i32, length: i32, programs: *const u64, count: i32) -> *mut super::sequence::Plan {
    if words.is_null() || length < 0 || count < 0 || (programs.is_null() && count > 0) {
        return std::ptr::null_mut();
    }
    let words = std::slice::from_raw_parts(words, length as usize);
    let programs = if count == 0 { &[][..] } else { std::slice::from_raw_parts(programs, count as usize) };
    std::panic::catch_unwind(|| super::sequence::Plan::decode(words, programs))
        .ok()
        .flatten()
        .map_or(std::ptr::null_mut(), |plan| Box::into_raw(Box::new(plan)))
}

/// # Safety
/// `plan` came from `mattmc_decoration_plan_create` and is released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_plan_release(plan: *mut super::sequence::Plan) {
    if !plan.is_null() {
        drop(Box::from_raw(plan));
    }
}

/// `applyBiomeDecoration`'s whole step loop over one binding (`sequence.rs`).
/// `info` receives `[resume step, resume index, native placements, Java
/// placements, current step, current index, gaussian have, gaussian bits]`
/// (the gaussian cache is also read on entry), then native placements by
/// program kind (`INFO_KINDS` words).
///
/// # Safety
/// As `mattmc_decoration_place`, plus `plan` live, `biomes` valid for
/// `biome_count` IDs and `info` for `INFO_HEAD + INFO_KINDS` longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_decoration_decorate(
    plan: *const super::sequence::Plan,
    region: *const i64,
    region_length: i32,
    out: *mut i64,
    out_length: i32,
    decoration_seed: i64,
    biomes: *const u32,
    biome_count: i32,
    structures: i64,
    x: i32,
    y: i32,
    z: i32,
    callback: Option<super::region::Callback>,
    info: *mut i64,
) -> i32 {
    let (Some(plan), false, false, false) = (plan.as_ref(), region.is_null(), out.is_null(), info.is_null()) else {
        return INVALID;
    };
    if region_length < 0 || out_length < 0 || biome_count < 0 || (biomes.is_null() && biome_count > 0) {
        return INVALID;
    }
    let biomes = if biome_count == 0 { &[][..] } else { std::slice::from_raw_parts(biomes, biome_count as usize) };
    let info = std::slice::from_raw_parts_mut(info, INFO_HEAD + INFO_KINDS);
    let words = super::region::Shared::new(region as *mut i64, region_length as usize);
    let out = super::region::Shared::new(out, out_length as usize);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(mut region) = Region::bind(words, out) else {
            return INVALID;
        };
        region.callback = callback;
        let mut counts = [0i64; 3];
        let mut current = [0i32; 2];
        let (head, kinds) = info.split_at_mut(INFO_HEAD);
        kinds.fill(0);
        let mut gaussian = super::random::gaussian_from(head[6], head[7]);
        let result = super::sequence::decorate(plan, &mut region, decoration_seed, biomes, structures as u64, [x, y, z], &mut counts, &mut current, kinds, &mut gaussian);
        let info = head;
        info[6..8].copy_from_slice(&super::random::gaussian_words(gaussian));
        EVENTS.with(|e| *e.borrow_mut() = std::mem::take(&mut region.events));
        info[2] = counts[0];
        info[3] = counts[1];
        info[4] = current[0] as i64;
        info[5] = current[1] as i64;
        match result {
            Ok(None) => DECORATED,
            Ok(Some(resume)) => {
                info[0] = resume.step as i64;
                info[1] = resume.index as i64;
                RESUME
            }
            Err(failure) => failure_status(&mut region, failure),
        }
    }));
    result.unwrap_or(PANIC)
}

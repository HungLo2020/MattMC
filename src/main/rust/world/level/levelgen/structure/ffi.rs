//! Structure exports: the global tables Java exports once, and one piece's
//! placement over the bound decoration region per call.
use super::piece::{BoundingBox, Ctx};
use super::Globals;
use crate::world::level::levelgen::decoration::random::{gaussian_from, gaussian_words, WorldgenRandom};
use crate::world::level::levelgen::decoration::region::{Callback, Failure, Region, Shared};
use crate::world::level::levelgen::decoration::{BROKEN_BINDING, CALLBACK_FAILED, INVALID, JAVA_ROUTE, PANIC, PLACED, UNAVAILABLE_CHUNK};

/// Installs the global tables: per state four transforms and a fluid type,
/// twenty state bitsets of `ceil(states / 64)` words each (solid render,
/// replaceable by structures, shape check, liquid, sturdy by direction ×6,
/// centre support down, falling, dynamic shape, collision face full by
/// direction ×6, cannot replace), and loot names joined by `\n`.
/// Returns 1 when installed (or already installed with the same size).
///
/// # Safety
/// Every pointer is valid for its stated length for this call.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_init(
    transforms: *const i32,
    fluids: *const i32,
    states: i32,
    sets: *const i64,
    set_count: i32,
    loot: *const u8,
    loot_length: i32,
) -> i32 {
    const SETS: usize = 20;
    if transforms.is_null() || fluids.is_null() || sets.is_null() || loot.is_null() || states <= 0 || loot_length < 0 || set_count != SETS as i32 {
        return 0;
    }
    let n = states as usize;
    let words = n.div_ceil(64);
    let transforms = std::slice::from_raw_parts(transforms, n * 4);
    let fluids = std::slice::from_raw_parts(fluids, n);
    let sets = std::slice::from_raw_parts(sets, words * SETS);
    let Ok(loot) = std::str::from_utf8(std::slice::from_raw_parts(loot, loot_length as usize)) else {
        return 0;
    };
    let set = |i: usize| sets[i * words..(i + 1) * words].iter().map(|&w| w as u64).collect::<Box<[u64]>>();
    let globals = Globals {
        transforms: transforms.chunks_exact(4).map(|t| [t[0] as u32, t[1] as u32, t[2] as u32, t[3] as u32]).collect(),
        fluid_types: fluids.into(),
        solid_render: set(0),
        replaceable: set(1),
        shape_check: set(2),
        liquid: set(3),
        sturdy: [set(4), set(5), set(6), set(7), set(8), set(9)],
        center_down: set(10),
        falling: set(11),
        dynamic: set(12),
        collision_face: [set(13), set(14), set(15), set(16), set(17), set(18)],
        cannot_replace: set(19),
        loot: loot.split('\n').filter(|s| !s.is_empty()).map(str::to_owned).collect(),
    };
    let installed = super::GLOBALS.get_or_init(|| globals);
    i32::from(installed.transforms.len() == n)
}

/// Places one piece. `words` holds the piece (kind, box, orientation, gen
/// depth, its fields) and receives the fields the placement changed. `args`:
/// `[chunk box (6), chunk x, chunk z, pivot x, y, z, random lo, hi, gaussian have, bits,
/// region random present, lo, hi, level seed, sea level]`; the randoms come back in words 11 to 17
/// (word 15 then says whether Rust advanced the region random).
///
/// # Safety
/// As `mattmc_template_place`; `words` is valid for `length` longs, `args` for 20.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_piece_place(
    region: *const i64,
    region_length: i32,
    out: *mut i64,
    out_length: i32,
    words: *mut i64,
    length: i32,
    args: *mut i64,
    callback: Option<Callback>,
) -> i32 {
    if region.is_null() || out.is_null() || words.is_null() || args.is_null() || region_length < 0 || out_length < 0 || length < 9 {
        return INVALID;
    }
    let Some(globals) = super::globals() else {
        return JAVA_ROUTE;
    };
    let words = std::slice::from_raw_parts_mut(words, length as usize);
    let args = std::slice::from_raw_parts_mut(args, 20);
    let region_words = Shared::new(region as *mut i64, region_length as usize);
    let out = Shared::new(out, out_length as usize);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(mut region) = Region::bind(region_words, out) else {
            return INVALID;
        };
        region.callback = callback;
        region.slot = -1;
        if !(region.write_path_ready() && crate::world::level::levelgen::decoration::facts::installed().is_some_and(|f| !f.any_unknown())) {
            return JAVA_ROUTE;
        }
        let mut random = WorldgenRandom::new(0);
        random.set_state(args[11], args[12]);
        random.gaussian = gaussian_from(args[13], args[14]);
        let chunk_box = BoundingBox::from_words(&args[0..6]);
        let Some(mut ctx) = Ctx::new(&mut region, random, chunk_box, globals) else {
            return JAVA_ROUTE;
        };
        ctx.region_random = (args[15] != 0).then(|| crate::world::level::levelgen::random::Xoroshiro::new(args[16], args[17]));
        ctx.seed = args[18];
        ctx.sea_level = args[19] as i32;
        let Some(piece) = super::Piece::read(words) else {
            return JAVA_ROUTE;
        };
        let pivot = [args[8] as i32, args[9] as i32, args[10] as i32];
        let result = piece.place(&mut ctx, [args[6] as i32, args[7] as i32], pivot, words);
        let (lo, hi) = ctx.random.state();
        let [have, bits] = gaussian_words(ctx.random.gaussian);
        args[11] = lo;
        args[12] = hi;
        args[13] = have;
        args[14] = bits;
        // Hand the region random back only when Rust drew from it: Java steps
        // (entity spawns) may have advanced Java's own copy meanwhile.
        let region_state = ctx.region_random.as_ref().map(|r| r.state()).filter(|&state| state != (args[16], args[17]));
        args[15] = i64::from(region_state.is_some());
        if let Some((lo, hi)) = region_state {
            args[16] = lo;
            args[17] = hi;
        }
        drop(ctx);
        crate::world::level::levelgen::decoration::publish_events(std::mem::take(&mut region.events));
        match result {
            Ok(true) => PLACED,
            Ok(false) => JAVA_ROUTE,
            Err(failure) => {
                region.fail_detail(&failure);
                match failure {
                    Failure::UnavailableChunk { .. } => UNAVAILABLE_CHUNK,
                    Failure::Unbound | Failure::Declined => BROKEN_BINDING,
                    Failure::Callback => CALLBACK_FAILED,
                }
            }
        }
    }));
    result.unwrap_or(PANIC)
}

/// A structure's `afterPlace` for one chunk. `words`: `[kind, …]` (1 desert
/// pyramid, 2 woodland mansion). `args`: `[chunk box (6), pieces' box centre
/// x, y, z, level seed, random lo, hi, gaussian have, bits]`.
///
/// # Safety
/// As `mattmc_structure_piece_place`; `args` is valid for 14 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_after_place(
    region: *const i64,
    region_length: i32,
    out: *mut i64,
    out_length: i32,
    words: *const i64,
    length: i32,
    args: *mut i64,
    callback: Option<Callback>,
) -> i32 {
    if region.is_null() || out.is_null() || words.is_null() || args.is_null() || region_length < 0 || out_length < 0 || length < 2 {
        return INVALID;
    }
    let Some(globals) = super::globals() else {
        return JAVA_ROUTE;
    };
    let words = std::slice::from_raw_parts(words, length as usize);
    let args = std::slice::from_raw_parts_mut(args, 14);
    let region_words = Shared::new(region as *mut i64, region_length as usize);
    let out = Shared::new(out, out_length as usize);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(mut region) = Region::bind(region_words, out) else {
            return INVALID;
        };
        region.callback = callback;
        region.slot = -1;
        if !(region.write_path_ready() && crate::world::level::levelgen::decoration::facts::installed().is_some_and(|f| !f.any_unknown())) {
            return JAVA_ROUTE;
        }
        let mut random = WorldgenRandom::new(0);
        random.set_state(args[10], args[11]);
        random.gaussian = gaussian_from(args[12], args[13]);
        let Some(mut ctx) = Ctx::new(&mut region, random, BoundingBox::from_words(&args[0..6]), globals) else {
            return JAVA_ROUTE;
        };
        ctx.seed = args[9];
        let center = [args[6] as i32, args[7] as i32, args[8] as i32];
        let result = match words[0] {
            1 => super::scattered::pyramid_after_place(&mut ctx, words, center),
            2 => super::mansion_after_place(&mut ctx, words),
            _ => Ok(false),
        };
        let (lo, hi) = ctx.random.state();
        let [have, bits] = gaussian_words(ctx.random.gaussian);
        args[10] = lo;
        args[11] = hi;
        args[12] = have;
        args[13] = bits;
        drop(ctx);
        crate::world::level::levelgen::decoration::publish_events(std::mem::take(&mut region.events));
        match result {
            Ok(true) => PLACED,
            Ok(false) => JAVA_ROUTE,
            Err(failure) => {
                region.fail_detail(&failure);
                match failure {
                    Failure::UnavailableChunk { .. } => UNAVAILABLE_CHUNK,
                    Failure::Unbound | Failure::Declined => BROKEN_BINDING,
                    Failure::Callback => CALLBACK_FAILED,
                }
            }
        }
    }));
    result.unwrap_or(PANIC)
}

/// `TemplateStructurePiece.postProcess` after a native `placeInWorld` of
/// `template` (`template_tail.rs`). `words` as `template_tail::tail` reads them;
/// `args`: `[chunk box (6), origin xyz, mirror, rotation, rotation pivot xyz,
/// random lo, hi, gaussian have, bits, level seed, sea level, water fluid,
/// flowing water fluid]`; the random comes back in words 14 to 17.
///
/// # Safety
/// As `mattmc_structure_piece_place`; `template` is a live template object and
/// `args` is valid for 22 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_template_tail(
    template: *const crate::world::level::levelgen::decoration::template::Template,
    region: *const i64,
    region_length: i32,
    out: *mut i64,
    out_length: i32,
    words: *const i64,
    length: i32,
    args: *mut i64,
    callback: Option<Callback>,
) -> i32 {
    let Some(template) = template.as_ref() else {
        return INVALID;
    };
    if region.is_null() || out.is_null() || words.is_null() || args.is_null() || region_length < 0 || out_length < 0 || length < 1 {
        return INVALID;
    }
    let Some(globals) = super::globals() else {
        return JAVA_ROUTE;
    };
    let words = std::slice::from_raw_parts(words, length as usize);
    let args = std::slice::from_raw_parts_mut(args, 22);
    let region_words = Shared::new(region as *mut i64, region_length as usize);
    let out = Shared::new(out, out_length as usize);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let Some(mut region) = Region::bind(region_words, out) else {
            return INVALID;
        };
        region.callback = callback;
        region.slot = -1;
        if !(region.write_path_ready() && crate::world::level::levelgen::decoration::facts::installed().is_some_and(|f| !f.any_unknown())) {
            return JAVA_ROUTE;
        }
        let mut random = WorldgenRandom::new(0);
        random.set_state(args[14], args[15]);
        random.gaussian = gaussian_from(args[16], args[17]);
        let Some(mut ctx) = Ctx::new(&mut region, random, BoundingBox::from_words(&args[0..6]), globals) else {
            return JAVA_ROUTE;
        };
        ctx.seed = args[18];
        ctx.sea_level = args[19] as i32;
        let a = |i: usize| args[i] as i32;
        let placement = super::template_tail::Placement {
            origin: [a(6), a(7), a(8)],
            mirror: (args[9] as usize).min(2),
            rotation: args[10],
            pivot: [a(11), a(12), a(13)],
            sea_level: a(19),
            water: [a(20), a(21)],
        };
        let result = super::template_tail::tail(&mut ctx, template, words, &placement);
        let (lo, hi) = ctx.random.state();
        let [have, bits] = gaussian_words(ctx.random.gaussian);
        args[14] = lo;
        args[15] = hi;
        args[16] = have;
        args[17] = bits;
        drop(ctx);
        crate::world::level::levelgen::decoration::publish_events(std::mem::take(&mut region.events));
        match result {
            Ok(true) => PLACED,
            Ok(false) => JAVA_ROUTE,
            Err(failure) => {
                region.fail_detail(&failure);
                match failure {
                    Failure::UnavailableChunk { .. } => UNAVAILABLE_CHUNK,
                    Failure::Unbound | Failure::Declined => BROKEN_BINDING,
                    Failure::Callback => CALLBACK_FAILED,
                }
            }
        }
    }));
    result.unwrap_or(PANIC)
}

/// A generator's structure placements (`placement.rs`); null for invalid words.
///
/// # Safety
/// `words` is valid for `length` ints.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_placements_create(seed: i64, words: *const i32, length: i32) -> *mut super::placement::Placements {
    if words.is_null() || length < 1 {
        return std::ptr::null_mut();
    }
    let words = std::slice::from_raw_parts(words, length as usize);
    std::panic::catch_unwind(|| super::placement::Placements::decode(seed, words))
        .ok()
        .flatten()
        .map_or(std::ptr::null_mut(), |p| Box::into_raw(Box::new(p)))
}

/// # Safety
/// `placements` came from `mattmc_structure_placements_create` and is released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_placements_release(placements: *mut super::placement::Placements) {
    if !placements.is_null() {
        drop(Box::from_raw(placements));
    }
}

/// Per placement, whether chunk (x, z) is a structure chunk: 1 yes, 0 no, 2
/// Java must evaluate it. Returns the placement count, or -1.
///
/// # Safety
/// `placements` is live; `out` is valid for the placement count.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_placements_query(placements: *const super::placement::Placements, x: i32, z: i32, out: *mut u8, capacity: i32) -> i32 {
    let Some(placements) = placements.as_ref() else {
        return -1;
    };
    if out.is_null() || capacity < placements.len() as i32 {
        return -1;
    }
    let out = std::slice::from_raw_parts_mut(out, placements.len());
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        for (index, slot) in out.iter_mut().enumerate() {
            *slot = match placements.is_structure_chunk(index, x, z) {
                Some(true) => 1,
                Some(false) => 0,
                None => 2,
            };
        }
    }));
    if result.is_ok() { placements.len() as i32 } else { -1 }
}

/// A jigsaw structure's pools and templates (`jigsaw.rs`); null when invalid.
///
/// # Safety
/// `words` is valid for `length` ints.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_jigsaw_create(words: *const i32, length: i32) -> *mut super::jigsaw::Data {
    if words.is_null() || length < 1 {
        return std::ptr::null_mut();
    }
    let words = std::slice::from_raw_parts(words, length as usize);
    std::panic::catch_unwind(|| super::jigsaw::Data::decode(words)).ok().flatten().map_or(std::ptr::null_mut(), |d| Box::into_raw(Box::new(d)))
}

/// # Safety
/// `data` came from `mattmc_structure_jigsaw_create` and is released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_jigsaw_release(data: *mut super::jigsaw::Data) {
    if !data.is_null() {
        drop(Box::from_raw(data));
    }
}

struct JigsawWorld<'a> {
    columns: &'a crate::world::level::levelgen::noise_fill::column::Columns,
    program: *const u8,
    surface: &'a [i32],
    projection: &'a [i32],
    min_y: i32,
    sampler: Option<crate::world::level::biome::search::Sampler<'a>>,
    accept: &'a [u8],
    fixed_accept: bool,
}

impl super::jigsaw::World for JigsawWorld<'_> {
    fn surface(&mut self, x: i32, z: i32) -> Result<i32, ()> {
        Ok(self.columns.base_height(self.program, x, z, self.surface)?.unwrap_or(self.min_y))
    }
    fn projection(&mut self, x: i32, z: i32) -> Result<i32, ()> {
        Ok(self.columns.base_height(self.program, x, z, self.projection)?.unwrap_or(self.min_y))
    }
    fn valid_biome(&mut self, pos: [i32; 3]) -> Result<bool, ()> {
        match &mut self.sampler {
            Some(sampler) => {
                let leaf = crate::world::level::biome::search::leaf_at(sampler, pos[0] >> 2, pos[1] >> 2, pos[2] >> 2).map_err(|_| ())?;
                Ok(self.accept.get(leaf as usize).copied().unwrap_or(0) != 0)
            }
            None => Ok(self.fixed_accept),
        }
    }
}

/// `JigsawStructure.findGenerationPoint` with the biome check and pieces.
/// `ints`: [chunk x, chunk z, start height kind (0 constant, 1 uniform), a, b,
/// start pool, start name (-1 none), max depth, expansion hack, project,
/// max horizontal, max vertical, padding check, pad bottom, pad top, min Y, max
/// Y, fixed biome accepted (no climate program), surface states, projection
/// states, then both state lists]; `longs`: [seed, climate program, nodes,
/// node count]; `accept` has one byte per node; `previous` the thread's climate
/// leaf, written back. Writes `[0]` (no stub) or `[1, x, y, z, pieces…]` to
/// `out` and returns its length; -1 invalid, -2 Java's path, -3 too small.
///
/// # Safety
/// Pointers are valid for their stated lengths; `columns` is a live column
/// source and `program` its cell program.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_jigsaw_plan(
    data: *const super::jigsaw::Data,
    columns: u64,
    program: *const u8,
    ints: *const i32,
    int_count: i32,
    longs: *const i64,
    accept: *const u8,
    previous: *mut i32,
    out: *mut i64,
    capacity: i32,
) -> i32 {
    let Some(data) = data.as_ref() else { return -1 };
    if columns == 0 || program.is_null() || ints.is_null() || longs.is_null() || previous.is_null() || out.is_null() || int_count < 20 || capacity < 1 {
        return -1;
    }
    let i = std::slice::from_raw_parts(ints, int_count as usize);
    let l = std::slice::from_raw_parts(longs, 4);
    let (Ok(surface_n), Ok(projection_n)) = (usize::try_from(i[18]), usize::try_from(i[19])) else { return -1 };
    if i.len() != 20 + surface_n + projection_n {
        return -1;
    }
    let surface = &i[20..20 + surface_n];
    let projection = &i[20 + surface_n..];
    let columns = &*(columns as *const crate::world::level::levelgen::noise_fill::column::Columns);
    let climate = (l[1] != 0).then(|| &*(l[1] as *const crate::world::level::levelgen::router::Program));
    let node_count = usize::try_from(l[3]).unwrap_or(0);
    if climate.is_some() && (l[2] == 0 || node_count == 0 || accept.is_null()) {
        return -1;
    }
    let nodes: &[crate::world::level::biome::climate::Node] =
        if climate.is_some() { std::slice::from_raw_parts(l[2] as *const crate::world::level::biome::climate::Node, node_count) } else { &[] };
    let accept = if climate.is_some() { std::slice::from_raw_parts(accept, node_count) } else { &[] };
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let mut world = JigsawWorld {
            columns,
            program,
            surface,
            projection,
            min_y: i[15],
            sampler: climate.map(|p| crate::world::level::biome::search::Sampler::new(p, nodes, *previous)),
            accept,
            fixed_accept: i[17] != 0,
        };
        // The generation context's random, then the start height.
        let mut random = crate::world::level::levelgen::random::Legacy::new(0);
        random.set_large_feature_seed(l[0], i[0], i[1]);
        let start_y = match i[2] {
            0 => i[3],
            1 if i[3] > i[4] => i[3],
            1 => random.next_int_bound(i[4] - i[3] + 1) + i[3],
            _ => return Err(-2),
        };
        let settings = super::jigsaw::Settings {
            start_pool: i[5] as u32,
            start_name: (i[6] >= 0).then_some(i[6] as u32),
            max_depth: i[7],
            expansion_hack: i[8] != 0,
            project: i[9] != 0,
            max_horizontal: i[10],
            max_vertical: i[11],
            padding_check: i[12] != 0,
            pad_bottom: i[13],
            pad_top: i[14],
            min_y: i[15],
            max_y: i[16],
            seed: l[0],
        };
        let outcome = super::jigsaw::plan(data, &settings, [i[0], i[1]], start_y, &mut random, &mut world).map_err(|_| -2)?;
        if let Some(sampler) = &world.sampler {
            *previous = sampler.previous;
        }
        let mut words = Vec::new();
        match outcome {
            super::jigsaw::Outcome::None => words.push(0),
            super::jigsaw::Outcome::Stub(stub, pieces) => {
                words.extend([1, stub[0] as i64, stub[1] as i64, stub[2] as i64]);
                super::jigsaw::write(&pieces, &mut words);
            }
        }
        Ok(words)
    }));
    match result {
        Ok(Ok(words)) if words.len() <= capacity as usize => {
            std::slice::from_raw_parts_mut(out, words.len()).copy_from_slice(&words);
            words.len() as i32
        }
        Ok(Ok(_)) => -3,
        Ok(Err(status)) => status,
        Err(_) => -1,
    }
}

struct StubWorld<'a> {
    columns: &'a crate::world::level::levelgen::noise_fill::column::Columns,
    program: *const u8,
    surface: &'a [i32],
    ocean: &'a [i32],
    min_y: i32,
    biomes: StubBiomes<'a>,
}

/// A plain multi-noise source's climate tree, or `TheEndBiomeSource` (leaves 0
/// end, 1 highlands, 2 midlands, 3 islands, 4 barrens) over the sampler's erosion.
enum StubBiomes<'a> {
    Climate(crate::world::level::biome::search::Sampler<'a>),
    End(&'a crate::world::level::levelgen::router::Program),
}

impl super::stubs::Sampling for StubWorld<'_> {
    fn surface(&mut self, x: i32, z: i32) -> Result<i32, ()> {
        Ok(self.columns.base_height(self.program, x, z, self.surface)?.unwrap_or(self.min_y))
    }
    fn ocean_floor(&mut self, x: i32, z: i32) -> Result<i32, ()> {
        Ok(self.columns.base_height(self.program, x, z, self.ocean)?.unwrap_or(self.min_y))
    }
    fn column(&mut self, x: i32, z: i32) -> Result<Vec<i32>, ()> {
        self.columns.column(self.program, x, z)
    }
    fn heightmap(&self, state: i32, ocean: bool) -> bool {
        if ocean { self.ocean } else { self.surface }.contains(&state)
    }
    fn column_min_y(&self) -> i32 {
        self.columns.min_y()
    }
    fn leaf(&mut self, qx: i32, qy: i32, qz: i32) -> Result<usize, ()> {
        match &mut self.biomes {
            StubBiomes::Climate(sampler) => crate::world::level::biome::search::leaf_at(sampler, qx, qy, qz).map(|l| l as usize).map_err(|_| ()),
            StubBiomes::End(program) => {
                let (x, y, z) = (qx.wrapping_shl(2), qy.wrapping_shl(2), qz.wrapping_shl(2));
                let (o, p) = ((x >> 4) as i64, (z >> 4) as i64);
                if o * o + p * p <= 4096 {
                    return Ok(0);
                }
                let (q, r) = (((x >> 4) * 2 + 1) * 8, ((z >> 4) * 2 + 1) * 8);
                let (mut memo, mut present) = (Vec::new(), Vec::new());
                let mut binding = crate::world::level::levelgen::router::Binding { first_x: 0, first_z: 0, size: 0, memo: &mut memo, present: &mut present };
                // The sampler's erosion is the program's fourth root.
                let d = crate::world::level::levelgen::router::with_frame(program, |f| program.point_value(f, 3, q, y, r, &mut binding)).map_err(|_| ())?;
                Ok(if d > 0.25 {
                    1
                } else if d >= -0.0625 {
                    2
                } else if d < -0.21875 {
                    3
                } else {
                    4
                })
            }
        }
    }
}

/// `findValidGenerationPoint` for `stubs.rs`'s families. `ints`: [kind, chunk
/// x, chunk z, sea level, min Y, param a, param b, surface states, ocean floor
/// states, then both lists, then further params]; `longs`: [climate program, nodes, node count,
/// seed]; `accept` and `surround` one byte per node; `previous` the thread's
/// leaf, written back. Writes `[x, y, z, valid, rotation, extra×3]` to `out`;
/// a mineshaft's pieces go to `words` (their count in `extra[0]`, -2 when
/// they do not fit). Returns 1
/// for a stub, 0 for none, -1 invalid, -2 for Java's path.
///
/// # Safety
/// As `mattmc_structure_jigsaw_plan`; `out` holds 8 longs.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_stub(
    columns: u64,
    program: *const u8,
    ints: *const i32,
    int_count: i32,
    longs: *const i64,
    accept: *const u8,
    surround: *const u8,
    previous: *mut i32,
    out: *mut i64,
    words: *mut i32,
    word_capacity: i32,
) -> i32 {
    if columns == 0 || program.is_null() || ints.is_null() || longs.is_null() || accept.is_null() || surround.is_null() || previous.is_null()
        || out.is_null() || int_count < 9
    {
        return -1;
    }
    let i = std::slice::from_raw_parts(ints, int_count as usize);
    let l = std::slice::from_raw_parts(longs, 4);
    let (Ok(surface_n), Ok(ocean_n), Ok(node_count)) = (usize::try_from(i[7]), usize::try_from(i[8]), usize::try_from(l[2])) else { return -1 };
    // No nodes: the End's biome source over the climate program's erosion.
    let end = l[1] == 0 && node_count == 0;
    if i.len() < 9 + surface_n + ocean_n || l[0] == 0 || (!end && (l[1] == 0 || node_count == 0)) {
        return -1;
    }
    let node_count = if end { 5 } else { node_count };
    let columns = &*(columns as *const crate::world::level::levelgen::noise_fill::column::Columns);
    let climate = &*(l[0] as *const crate::world::level::levelgen::router::Program);
    let nodes: &[crate::world::level::biome::climate::Node] =
        if end { &[] } else { std::slice::from_raw_parts(l[1] as *const crate::world::level::biome::climate::Node, node_count) };
    let accept = std::slice::from_raw_parts(accept, node_count);
    let surround = std::slice::from_raw_parts(surround, node_count);
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let mut world = StubWorld {
            columns,
            program,
            surface: &i[9..9 + surface_n],
            ocean: &i[9 + surface_n..9 + surface_n + ocean_n],
            min_y: i[4],
            biomes: if end { StubBiomes::End(climate) } else { StubBiomes::Climate(crate::world::level::biome::search::Sampler::new(climate, nodes, *previous)) },
        };
        let params: Vec<i32> = i[5..7].iter().chain(&i[9 + surface_n + ocean_n..]).copied().collect();
        let found = super::stubs::find(i[0], [i[1], i[2]], i[3], i[4], l[3], &params, &|leaf| accept.get(leaf).copied().unwrap_or(0) != 0,
            &|leaf| surround.get(leaf).copied().unwrap_or(0) != 0, &mut world);
        if let StubBiomes::Climate(sampler) = &world.biomes {
            *previous = sampler.previous;
        }
        found
    }));
    match result {
        Ok(Ok(Some(mut stub))) => {
            if !stub.words.is_empty() {
                if words.is_null() || stub.words.len() > usize::try_from(word_capacity).unwrap_or(0) {
                    return -2;
                }
                std::slice::from_raw_parts_mut(words, stub.words.len()).copy_from_slice(&stub.words);
                stub.extra[0] = stub.words.len() as i32;
            }
            std::slice::from_raw_parts_mut(out, 8).copy_from_slice(&[stub.pos[0] as i64, stub.pos[1] as i64, stub.pos[2] as i64, i64::from(stub.valid),
                stub.rotation as i64, stub.extra[0] as i64, stub.extra[1] as i64, stub.extra[2] as i64]);
            1
        }
        Ok(Ok(None)) => 0,
        Ok(Err(())) => -2,
        Err(_) => -1,
    }
}

/// The largest saved monument origin magnitude the native planner accepts.
const MONUMENT_ORIGIN_LIMIT: u32 = 1 << 30;

/// `OceanMonumentStructure.regeneratePiecesAfterLoad` for a non-empty saved
/// start: `setLargeFeatureSeed(seed, chunk)`, the direction draw, then the
/// building at the saved first piece's box minX/minZ with its saved
/// orientation (2D data value, or -1 for none: the drawn direction). Writes the
/// piece words (`OceanMonumentPieces.addNativePieces`) to `out` and the final
/// Legacy seed to `state[0]`. Returns the word count, or negative (writing
/// nothing) when `out` is too small, the input is invalid or the saved origin
/// is outside the planner's safe envelope: Java then regenerates.
/// # Safety
/// `out` has `capacity` writable i32s; `state` one writable i64.
#[no_mangle]
pub unsafe extern "C" fn mattmc_structure_monument_regenerate(seed: i64, chunk_x: i32, chunk_z: i32, min_x: i32, min_z: i32, orientation: i32,
    out: *mut i32, capacity: i32, state: *mut i64) -> i32 {
    if out.is_null() || state.is_null() || capacity < 0 {
        return -1;
    }
    // Every planned coordinate lies within (-128, +256) of the saved origin (the
    // building's 58-block box, room boxes within -47..+64 moved by a point inside
    // it), so within this envelope neither Java's int arithmetic nor the
    // planner's can wrap. Outside it Java's own body regenerates, wrapping as Java does.
    if min_x.unsigned_abs() > MONUMENT_ORIGIN_LIMIT || min_z.unsigned_abs() > MONUMENT_ORIGIN_LIMIT {
        return -3;
    }
    let planned = std::panic::catch_unwind(|| {
        let mut random = crate::world::level::levelgen::random::Legacy::new(0);
        random.set_large_feature_seed(seed, chunk_x, chunk_z);
        let orientation = if orientation < 0 { None } else { Some(orientation) };
        super::monument_plan::plan_at(&mut random, min_x, min_z, orientation).map(|words| (words, random.state()))
    });
    let Ok(Ok((words, final_state))) = planned else { return -1 };
    if words.len() > capacity as usize {
        return -2;
    }
    unsafe {
        std::ptr::copy_nonoverlapping(words.as_ptr(), out, words.len());
        *state = final_state;
    }
    words.len() as i32
}

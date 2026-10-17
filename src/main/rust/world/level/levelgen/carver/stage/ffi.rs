use super::{CanyonShape, CaveShape, ChunkAquifer, Config, Error, FloatProvider, Ids, Kind, Stage};
use crate::world::level::levelgen::aquifer::substance::Substance;
use crate::world::level::levelgen::proto_chunk::{ffi::storage, ProtoStorage};
use crate::world::level::levelgen::owned_positional::ffi::retain as retain_factory;
use crate::world::level::levelgen::router::{Binding, Program};
use crate::world::level::levelgen::surface::scalar::ScalarSurface;
use crate::world::level::levelgen::synth::State;

/// Top materials the stage answered natively, for the decoration statistics.
pub(crate) static NATIVE_TOP_CALLS: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);

/// The native top-material count since start.
#[no_mangle]
pub extern "C" fn mattmc_carvers_native_top_calls() -> i64 {
    NATIVE_TOP_CALLS.load(std::sync::atomic::Ordering::Relaxed) as i64
}

/// Verification: each native top material also runs Java's upcall from the same
/// previous leaf; the state and the leaf afterwards must agree, and the stage
/// continues with Java's.
static VERIFY_TOP: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);
static TOP_VERIFIED: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
static TOP_MISMATCHED: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(0);
/// The first mismatch: x, y, z, native state, Java state, native leaf, Java leaf.
static FIRST_MISMATCH: std::sync::Mutex<Option<[i32; 7]>> = std::sync::Mutex::new(None);

#[no_mangle]
pub extern "C" fn mattmc_carvers_verify_top(enabled: i32) {
    VERIFY_TOP.store(enabled != 0, std::sync::atomic::Ordering::Relaxed);
}

/// [verified, mismatched] counts, then the first mismatch's seven words (or zeros).
/// # Safety
/// `out` has nine writable i64s.
#[no_mangle]
pub unsafe extern "C" fn mattmc_carvers_top_verification(out: *mut i64) {
    if out.is_null() {
        return;
    }
    let out = unsafe { std::slice::from_raw_parts_mut(out, 9) };
    out[0] = TOP_VERIFIED.load(std::sync::atomic::Ordering::Relaxed) as i64;
    out[1] = TOP_MISMATCHED.load(std::sync::atomic::Ordering::Relaxed) as i64;
    let first = FIRST_MISMATCH.lock().map(|m| m.unwrap_or([0; 7])).unwrap_or([0; 7]);
    for (k, v) in first.iter().enumerate() {
        out[2 + k] = *v as i64;
    }
}

/// Java's top-material callback: (x, y, z, fluid, WORLD_SURFACE_WG raw,
/// OCEAN_FLOOR_WG raw, words, the climate search's previous leaf in and out)
/// to a state id, -1 for none, or -2 on failure.
pub(crate) type TopCallback = unsafe extern "C" fn(i32, i32, i32, i32, *const i64, *const i64, i32, *mut i32) -> i32;

// Pointer block slots.
const MASK: usize = 0;
const MASK_WORDS: usize = 1;
const SIN: usize = 2;
const GRID: usize = 3;
const GRID_LEN: usize = 4;
const CACHE: usize = 5;
const SURFACE: usize = 6;
const SURFACE_LEN: usize = 7;
const SOURCES: usize = 8;
const LEVELS: usize = 9;
const MEMO: usize = 10;
const PRESENT: usize = 11;
const MEMO_LEN: usize = 12;
const BARRIER: usize = 13;
const FACTORY: usize = 14;
const BARRIER_XZ: usize = 16;
const BARRIER_Y: usize = 17;
/// The RandomState sampler's climate point program (0: every neighbour's settings are given).
const CLIMATE: usize = 18;
const NODES: usize = 19;
const NODE_COUNT: usize = 20;
/// Per climate-tree node: its leaf biome's carver settings index (-1 for branches).
const LEAF_SETTINGS: usize = 21;
/// A native top-material binding (`mattmc_surface_scalar_create`), or 0 for Java's upcall.
const TOP: usize = 22;
/// The biome manager's zoom seed, for the native top material's biome.
const ZOOM_SEED: usize = 23;
/// Each climate leaf's biome registry id (with the climate program), or 0.
const LEAF_BIOMES: usize = 24;
/// A fixed biome source's registry id (without a climate program).
const FIXED_BIOME: usize = 25;
/// Java's `coldEnoughToSnow(biome registry id, x, y, z)` for the native top
/// material's temperature condition: 1 cold, 0 warm, negative on failure.
const COLD: usize = 26;
const POINTERS: usize = 27;

type ColdCallback = unsafe extern "C" fn(i32, i32, i32, i32) -> i32;

// Fixed int slots before the configurations and operations.
const HEADER: usize = 39;
const CONFIG_INTS: usize = 6;
const CONFIG_DOUBLES: usize = 23;

/// `FloatProvider` from [kind (0 constant, 1 uniform, 2 trapezoid), a, b, c].
fn provider(v: &[f64]) -> Option<FloatProvider> {
    match v[0] as i32 {
        0 => Some(FloatProvider::Constant(v[1] as f32)),
        1 => Some(FloatProvider::Uniform(v[1] as f32, v[2] as f32)),
        2 => Some(FloatProvider::Trapezoid(v[1] as f32, v[2] as f32, v[3] as f32)),
        _ => None,
    }
}

/// Carves one chunk: `applyCarvers`' loop over the chunk's storage, carving
/// mask and aquifer. `pointers` holds the native buffers (see the slot
/// constants); `ints` [chunkX, chunkZ, minGenY, genDepth, upgrading, air,
/// caveAir, lava, water, configCount, unused, schedule, aquifer (0 none, 1
/// noise-based, 2 disabled), skipY, locationKind, shape (5), surface rect (4),
/// FlatCache grid (3), wayBelow, policy (8), grass block, mycelium and dirt
/// block ids, then per configuration (kind, lava level, height min, height
/// max, width smoothness, replaceable block count), the settings count and per
/// settings (carver count, configuration indices), the neighbour count, each
/// neighbour's settings index in `applyCarvers`' order (-1: sample its carver
/// biome; written back), the climate search's previous leaf (written back),
/// then each configuration's replaceable block ids]; state to block ids come from the
/// installed block registry; `doubles` per configuration (probability, then the Y
/// scale and four shape providers as [kind, a, b, c]: cave horizontal radius,
/// vertical radius, floor level, unused; canyon vertical rotation, thickness,
/// distance factor, horizontal radius; then the canyon's vertical default and
/// center factors); `longs` [world seed]. Writes the aquifer's final
/// `shouldScheduleFluidUpdate` to `ints[11]`. Returns 0, -1 for invalid
/// input, -2 for storage errors, -3 for aquifer errors, -4 when Java's
/// top-material callback failed.
/// # Safety
/// `storage` is a live storage used only by this call; every pointer in
/// `pointers` addresses its stated count of native, aligned, disjoint values
/// for this call; `sources` and `levels` are live programs; `barrier` is null
/// or a live noise state. Nothing is retained.
#[no_mangle]
pub unsafe extern "C" fn mattmc_carvers_run(storage_handle: u64, pointers: *const i64, ints: *mut i32, int_count: i32, doubles: *const f64,
    double_count: i32, longs: *const i64, long_count: i32, top: Option<TopCallback>) -> i32 {
    if storage_handle == 0 || pointers.is_null() || ints.is_null() || doubles.is_null() || longs.is_null() || int_count < HEADER as i32
        || double_count < 0 || long_count != 1
    {
        return -1;
    }
    let Some(top) = top else { return -1 };
    let p = unsafe { std::slice::from_raw_parts(pointers, POINTERS) };
    let i = unsafe { std::slice::from_raw_parts_mut(ints, int_count as usize) };
    let d = unsafe { std::slice::from_raw_parts(doubles, double_count as usize) };
    let seed = unsafe { *longs };
    let Ok(config_count) = usize::try_from(i[9]) else { return -1 };
    if config_count > 30 || i.len() < HEADER + config_count * CONFIG_INTS + 1 || d.len() != config_count * CONFIG_DOUBLES
        || p[MASK] == 0 || p[SIN] == 0 || p[MASK_WORDS] <= 0
    {
        return -1;
    }
    let Some(registry) = crate::content::block::installed() else { return -1 };
    let mut configs = Vec::with_capacity(config_count);
    for k in 0..config_count {
        let c = &i[HEADER + k * CONFIG_INTS..HEADER + (k + 1) * CONFIG_INTS];
        let cd = &d[k * CONFIG_DOUBLES..(k + 1) * CONFIG_DOUBLES];
        let kind = match c[0] {
            0 => Kind::Cave,
            1 => Kind::Nether,
            2 => Kind::Canyon,
            _ => return -1,
        };
        let providers: Option<Vec<FloatProvider>> = (0..5).map(|n| provider(&cd[1 + n * 4..5 + n * 4])).collect();
        let Some(providers) = providers else { return -1 };
        // randomBetweenInclusive needs a positive bound; Java keeps empty ranges.
        if c[2] > c[3] || c[3].wrapping_sub(c[2]).wrapping_add(1) <= 0 {
            return -1;
        }
        let (cave, canyon) = if kind == Kind::Canyon {
            if c[4] <= 0 {
                return -1;
            }
            (None, Some(CanyonShape { vertical_rotation: providers[1], thickness: providers[2], distance_factor: providers[3], width_smoothness: c[4],
                vertical_default: cd[21] as f32, vertical_center: cd[22] as f32, horizontal_radius: providers[4] }))
        } else {
            (Some(CaveShape { horizontal_radius: providers[1], vertical_radius: providers[2], floor_level: providers[3] }), None)
        };
        configs.push(Config { kind, probability: cd[0] as f32, y_min: c[2], y_max: c[3], y_scale: providers[0], lava_level: c[1], bit: k as u32, cave, canyon });
    }
    let mut at = HEADER + config_count * CONFIG_INTS;
    let Ok(settings_count) = usize::try_from(i[at]) else { return -1 };
    at += 1;
    let mut settings = Vec::with_capacity(settings_count);
    for _ in 0..settings_count {
        let Some(Ok(count)) = i.get(at).map(|&c| usize::try_from(c)) else { return -1 };
        let Some(list) = i.get(at + 1..at + 1 + count) else { return -1 };
        if list.iter().any(|k| *k < 0 || *k as usize >= config_count) {
            return -1;
        }
        settings.push(list.iter().map(|k| *k as usize).collect::<Vec<_>>());
        at += 1 + count;
    }
    // applyCarvers' neighbours: x outer, z inner, within 8 of the chunk.
    let Some(Ok(neighbour_count)) = i.get(at).map(|&n| usize::try_from(n)) else { return -1 };
    if neighbour_count != 17 * 17 || i.len() < at + 2 + neighbour_count {
        return -1;
    }
    let neighbour_at = at + 1;
    let mut neighbours = Vec::with_capacity(neighbour_count);
    for (n, &chosen) in i[neighbour_at..neighbour_at + neighbour_count].iter().enumerate() {
        if chosen < -1 || chosen >= settings_count as i32 {
            return -1;
        }
        neighbours.push((i[0].wrapping_add(n as i32 / 17 - 8), i[1].wrapping_add(n as i32 % 17 - 8), chosen));
    }
    let previous_at = neighbour_at + neighbour_count;
    at = previous_at + 1;
    let replaceable: usize = (0..config_count).map(|k| i[HEADER + k * CONFIG_INTS + 5].max(0) as usize).sum();
    if i.len() != at + replaceable {
        return -1;
    }
    // Carver biome sampling: the sampler's climate and the climate tree.
    let climate = if p[CLIMATE] == 0 { None } else { Some(unsafe { &*(p[CLIMATE] as *const Program) }) };
    let (nodes, leaf_settings): (&[crate::world::level::biome::climate::Node], &[i32]) = match climate {
        Some(_) => {
            let Ok(count) = usize::try_from(p[NODE_COUNT]) else { return -1 };
            if p[NODES] == 0 || p[LEAF_SETTINGS] == 0 || count == 0 {
                return -1;
            }
            unsafe {
                (std::slice::from_raw_parts(p[NODES] as *const crate::world::level::biome::climate::Node, count),
                    std::slice::from_raw_parts(p[LEAF_SETTINGS] as *const i32, count))
            }
        }
        None if neighbours.iter().any(|n| n.2 < 0) => return -1,
        None => (&[], &[]),
    };
    if leaf_settings.iter().any(|&l| l < -1 || l >= settings_count as i32) {
        return -1;
    }
    let previous = std::cell::Cell::new(i[previous_at]);
    let block_of = registry.block_column();
    let mut blocks = vec![0u32; registry.blocks().len()];
    let mut set = |block: i32, bit: u32| -> bool {
        match usize::try_from(block).ok().and_then(|b| blocks.get_mut(b)) {
            Some(bits) => {
                *bits |= bit;
                true
            }
            None => false,
        }
    };
    if !set(i[36], super::GRASS_OR_MYCELIUM) || !set(i[37], super::GRASS_OR_MYCELIUM) || !set(i[38], super::DIRT) {
        return -1;
    }
    for k in 0..config_count {
        let count = i[HEADER + k * CONFIG_INTS + 5] as usize;
        for index in at..at + count {
            if !set(i[index], 1u32 << (super::REPLACEABLE + k as u32)) {
                return -1;
            }
        }
        at += count;
    }
    let storage = unsafe { storage(storage_handle) };
    let height_words = storage.heightmap_raw(false).len();
    let mask_words = p[MASK_WORDS] as usize;
    if mask_words * 64 < storage.section_count() * 4096 {
        return -1;
    }
    let mask = unsafe { std::slice::from_raw_parts_mut(p[MASK] as *mut u64, mask_words) };
    let sin = unsafe { std::slice::from_raw_parts(p[SIN] as *const f32, 65536) };
    let aquifer = match i[12] {
        0 => None,
        1 => match unsafe { substance(p, i) } {
            Some(aquifer) => Some(ChunkAquifer::Noise(aquifer)),
            None => return -1,
        },
        2 => {
            let mut policy = [0; 8];
            policy.copy_from_slice(&i[28..36]);
            Some(ChunkAquifer::Disabled(policy))
        }
        _ => return -1,
    };
    let storage_ptr: *mut ProtoStorage = storage;
    let callback = |x: i32, y: i32, z: i32, fluid: bool, storage: &ProtoStorage| -> Result<i32, i32> {
        let (surface, floor) = (storage.heightmap_raw(false), storage.heightmap_raw(true));
        // Java's top material searches the climate tree too: hand it the previous leaf and take it back.
        let mut leaf = previous.get();
        let state = unsafe { top(x, y, z, fluid as i32, surface.as_ptr(), floor.as_ptr(), height_words as i32, &mut leaf) };
        previous.set(leaf);
        if state < -1 { Err(state) } else { Ok(state) }
    };
    let scalar = if p[TOP] == 0 { None } else { Some(unsafe { &*(p[TOP] as *const ScalarSurface) }) };
    if scalar.is_some() && p[COLD] == 0 {
        return -1;
    }
    // SAFETY: Java passes its upcall stub's address with the binding.
    let cold_callback: Option<ColdCallback> = (p[COLD] != 0).then(|| unsafe { std::mem::transmute::<usize, ColdCallback>(p[COLD] as usize) });
    let mut cold = |biome: i32, pos: [i32; 3]| -> Result<bool, ()> {
        let callback = cold_callback.ok_or(())?;
        match unsafe { callback(biome, pos[0], pos[1], pos[2]) } {
            0 => Ok(false),
            1 => Ok(true),
            _ => Err(()),
        }
    };
    let leaf_biomes: &[i32] = match (&scalar, climate) {
        (Some(_), Some(_)) if p[LEAF_BIOMES] == 0 => return -1,
        (Some(_), Some(_)) => unsafe { std::slice::from_raw_parts(p[LEAF_BIOMES] as *const i32, nodes.len()) },
        _ => &[],
    };
    let mut top_sampler = climate.map(|program| crate::world::level::biome::search::Sampler::new(program, nodes, previous.get()));
    let (zoom_seed, fixed_biome) = (p[ZOOM_SEED], p[FIXED_BIOME] as i32);
    // BiomeManager.getBiome over the generator's biome source: the zoomed quart,
    // then the fixed biome or the climate search from the thread's previous leaf.
    let mut biome_at = |x: i32, y: i32, z: i32| -> Result<i32, ()> {
        let mut index = [0i32];
        if unsafe { crate::world::level::biome::fiddled_distance::surface_biomes(zoom_seed, x, z, y, 1, index.as_mut_ptr()) } != 0 {
            return Err(());
        }
        let base_y = (y - 2) >> 2;
        let q_count = ((y.wrapping_add(1).wrapping_sub(3)) >> 2) - base_y + 2;
        let column = index[0] / q_count;
        let (qx, qy, qz) = (((x - 2) >> 2) + (column >> 1), base_y + index[0] % q_count, ((z - 2) >> 2) + (column & 1));
        match top_sampler.as_mut() {
            None => Ok(fixed_biome),
            Some(sampler) => {
                sampler.previous = previous.get();
                let leaf = crate::world::level::biome::search::leaf_at(sampler, qx, qy, qz).map_err(|_| ())?;
                previous.set(sampler.previous);
                leaf_biomes.get(usize::try_from(leaf).map_err(|_| ())?).copied().ok_or(())
            }
        }
    };
    let mut native_top = |x: i32, y: i32, z: i32, fluid: bool, storage: &ProtoStorage| -> Result<i32, i32> {
        match scalar {
            Some(scalar) if VERIFY_TOP.load(std::sync::atomic::Ordering::Relaxed) => {
                let before = previous.get();
                let native = scalar.top_material(storage, x, y, z, fluid, &mut biome_at, &mut cold).map_err(|_| -3);
                let native_leaf = previous.get();
                previous.set(before);
                let java = callback(x, y, z, fluid, storage);
                TOP_VERIFIED.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
                if native != java || native_leaf != previous.get() {
                    TOP_MISMATCHED.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
                    if let Ok(mut first) = FIRST_MISMATCH.lock() {
                        first.get_or_insert([x, y, z, native.unwrap_or_else(|e| e), java.unwrap_or_else(|e| e), native_leaf, previous.get()]);
                    }
                }
                java
            }
            Some(scalar) => {
                NATIVE_TOP_CALLS.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
                scalar.top_material(storage, x, y, z, fluid, &mut biome_at, &mut cold).map_err(|_| -3)
            }
            None => callback(x, y, z, fluid, storage),
        }
    };
    let mut sampler = climate.map(|program| crate::world::level::biome::search::Sampler::new(program, nodes, previous.get()));
    let mut select = |x: i32, z: i32| -> Result<usize, Error> {
        let sampler = sampler.as_mut().ok_or(Error::Selection)?;
        sampler.previous = previous.get();
        // carverBiome: getNoiseBiome(QuartPos.fromBlock(minBlockX), 0, QuartPos.fromBlock(minBlockZ)).
        let leaf = crate::world::level::biome::search::leaf_at(sampler, x.wrapping_mul(4), 0, z.wrapping_mul(4)).map_err(|_| Error::Selection)?;
        previous.set(sampler.previous);
        usize::try_from(leaf_settings[leaf as usize]).map_err(|_| Error::Selection)
    };
    let mut stage = Stage {
        storage: unsafe { &mut *storage_ptr },
        mask,
        chunk_x: i[0],
        chunk_z: i[1],
        min_gen_y: i[2],
        gen_depth: i[3],
        upgrading: i[4] != 0,
        sin,
        block_of,
        blocks: &blocks,
        ids: Ids { air: i[5], cave_air: i[6], lava: i[7] },
        configs: &configs,
        aquifer,
        schedule: i[11] != 0,
        top: &mut native_top,
    };
    let result = stage.run(seed, &mut neighbours, &settings, &mut select);
    let schedule = stage.schedule;
    i[11] = schedule as i32;
    for (n, neighbour) in neighbours.iter().enumerate() {
        i[neighbour_at + n] = neighbour.2;
    }
    i[previous_at] = previous.get();
    match result {
        Ok(()) => 0,
        Err(Error::Storage) => -2,
        Err(Error::Aquifer(_)) => -3,
        Err(Error::TopMaterial(_)) => -4,
        Err(Error::Selection) => -5,
    }
}

/// The aquifer's native substance evaluator over Java's caches.
/// # Safety
/// As `mattmc_carvers_run`.
unsafe fn substance<'a>(p: &[i64], i: &[i32]) -> Option<Substance<'a>> {
    if p[FACTORY] == 0 { return None; }
    let random = unsafe { retain_factory(p[FACTORY] as u64) };
    if random.kind() != i[14] { return None; }
    let (grid_len, surface_len, memo_len) = (usize::try_from(p[GRID_LEN]).ok()?, usize::try_from(p[SURFACE_LEN]).ok()?, usize::try_from(p[MEMO_LEN]).ok()?);
    let shape = [i[15], i[16], i[17], i[18], i[19]];
    let rect = [i[20], i[21], i[22], i[23]];
    let grid = [i[24], i[25], i[26]];
    if p[GRID] == 0 || p[CACHE] == 0 || p[SURFACE] == 0 || p[SOURCES] == 0 || p[LEVELS] == 0 || p[MEMO] == 0 || p[PRESENT] == 0
        || shape[3] <= 0 || shape[4] <= 0 || grid_len % (shape[3] as usize * shape[4] as usize) != 0
        || !(1..=128).contains(&rect[2]) || !(1..=128).contains(&rect[3]) || surface_len != (rect[2] * rect[3] * 2) as usize
        || !(1..=64).contains(&grid[2])
    {
        return None;
    }
    let sources = unsafe { &*(p[SOURCES] as *const Program) };
    if sources.root_count() != 5 || memo_len != sources.point_slots() * (grid[2] * grid[2]) as usize {
        return None;
    }
    let mut policy = [0; 8];
    policy.copy_from_slice(&i[28..36]);
    Some(Substance {
        grid: unsafe { std::slice::from_raw_parts_mut(p[GRID] as *mut i64, grid_len) },
        shape,
        cache: unsafe { std::slice::from_raw_parts_mut(p[CACHE] as *mut i32, grid_len * 3) },
        random,
        skip_y: i[13],
        policy,
        surface_rect: rect,
        surface: unsafe { std::slice::from_raw_parts_mut(p[SURFACE] as *mut i32, surface_len) },
        sources,
        levels: unsafe { &*(p[LEVELS] as *const Program) },
        binding: Binding {
            first_x: grid[0],
            first_z: grid[1],
            size: grid[2],
            memo: unsafe { std::slice::from_raw_parts_mut(p[MEMO] as *mut f64, memo_len) },
            present: unsafe { std::slice::from_raw_parts_mut(p[PRESENT] as *mut u8, memo_len) },
        },
        barrier: p[BARRIER] as *const State,
        barrier_xz: f64::from_bits(p[BARRIER_XZ] as u64),
        barrier_y: f64::from_bits(p[BARRIER_Y] as u64),
        water: i[8],
        lava: i[7],
        way_below: i[27],
    })
}

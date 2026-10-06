use super::{CanyonShape, CaveShape, ChunkAquifer, Config, Error, FloatProvider, Ids, Kind, Stage};
use crate::world::level::levelgen::aquifer::substance::Substance;
use crate::world::level::levelgen::proto_chunk::{ffi::storage, ProtoStorage};
use crate::world::level::levelgen::random::Positional;
use crate::world::level::levelgen::router::{Binding, Program};
use crate::world::level::levelgen::synth::State;

/// Java's top-material callback: (x, y, z, fluid, WORLD_SURFACE_WG raw,
/// OCEAN_FLOOR_WG raw, words) to a state id, -1 for none, or -2 on failure.
pub(crate) type TopCallback = unsafe extern "C" fn(i32, i32, i32, i32, *const i64, *const i64, i32) -> i32;

// Pointer block slots.
const MASK: usize = 0;
const MASK_WORDS: usize = 1;
const SIN: usize = 2;
const BLOCK_OF: usize = 3;
const STATE_COUNT: usize = 4;
const GRID: usize = 5;
const GRID_LEN: usize = 6;
const CACHE: usize = 7;
const SURFACE: usize = 8;
const SURFACE_LEN: usize = 9;
const SOURCES: usize = 10;
const LEVELS: usize = 11;
const MEMO: usize = 12;
const PRESENT: usize = 13;
const MEMO_LEN: usize = 14;
const BARRIER: usize = 15;
const SEED_A: usize = 16;
const SEED_B: usize = 17;
const BARRIER_XZ: usize = 18;
const BARRIER_Y: usize = 19;
const POINTERS: usize = 20;

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
/// max, width smoothness, replaceable block count), the neighbour count and
/// per neighbour (x, z, carver count, configuration indices), then each
/// configuration's replaceable block ids]; `pointers[BLOCK_OF]` maps each state
/// id to its block id; `doubles` per configuration (probability, then the Y
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
        || p[MASK] == 0 || p[SIN] == 0 || p[BLOCK_OF] == 0 || p[MASK_WORDS] <= 0 || p[STATE_COUNT] <= 0
    {
        return -1;
    }
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
    let Ok(neighbour_count) = usize::try_from(i[at]) else { return -1 };
    at += 1;
    let mut neighbours = Vec::with_capacity(neighbour_count);
    for _ in 0..neighbour_count {
        let Some(&[x, z, count]) = i.get(at..at + 3).map(|h| <&[i32; 3]>::try_from(h).unwrap()) else { return -1 };
        let Ok(count) = usize::try_from(count) else { return -1 };
        let Some(list) = i.get(at + 3..at + 3 + count) else { return -1 };
        if list.iter().any(|k| *k < 0 || *k as usize >= config_count) {
            return -1;
        }
        neighbours.push((x, z, list.iter().map(|k| *k as usize).collect::<Vec<_>>()));
        at += 3 + count;
    }
    let replaceable: usize = (0..config_count).map(|k| i[HEADER + k * CONFIG_INTS + 5].max(0) as usize).sum();
    if i.len() != at + replaceable {
        return -1;
    }
    let block_of = unsafe { std::slice::from_raw_parts(p[BLOCK_OF] as *const u32, p[STATE_COUNT] as usize) };
    let block_count = block_of.iter().copied().max().unwrap_or(0) as usize + 1;
    let mut blocks = vec![0u32; block_count];
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
    let mut callback = |x: i32, y: i32, z: i32, fluid: bool, storage: &ProtoStorage| -> Result<i32, i32> {
        let (surface, floor) = (storage.heightmap_raw(false), storage.heightmap_raw(true));
        let state = unsafe { top(x, y, z, fluid as i32, surface.as_ptr(), floor.as_ptr(), height_words as i32) };
        if state < -1 { Err(state) } else { Ok(state) }
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
        top: &mut callback,
    };
    let result = stage.run(seed, &neighbours);
    let schedule = stage.schedule;
    i[11] = schedule as i32;
    match result {
        Ok(()) => 0,
        Err(Error::Storage) => -2,
        Err(Error::Aquifer(_)) => -3,
        Err(Error::TopMaterial(_)) => -4,
    }
}

/// The aquifer's native substance evaluator over Java's caches.
/// # Safety
/// As `mattmc_carvers_run`.
unsafe fn substance<'a>(p: &[i64], i: &[i32]) -> Option<Substance<'a>> {
    let random = Positional::from_abi(i[14], p[SEED_A], p[SEED_B])?;
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

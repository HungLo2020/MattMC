//! `NoiseBasedChunkGenerator.getBaseHeight`: one column of the noise fill,
//! top down, until a state passes the heightmap's predicate. Java evaluates it
//! with a one-cell `NoiseChunk` (the structure Beardifier marker adds zero);
//! here the column's chunk router instance (the same corner slices the NOISE
//! fill uses), the cell density program at each block and the chunk's aquifer
//! substance with that density answer it, all owned by Rust and cached per chunk.
use crate::world::level::levelgen::aquifer::substance::{picked, OwnedAquifer};
use crate::world::level::levelgen::density::cell::density_cell;
use crate::world::level::levelgen::owned_positional::{Factory,ffi::retain as retain_factory};
use crate::world::level::levelgen::router::{with_frame, Geometry, Program, Router};
use crate::world::level::levelgen::synth::State;
use std::sync::{Mutex,Arc};

/// The aquifer inputs a RandomState's chunks share.
pub(crate) struct AquiferConfig {
    pub random: Arc<Factory>,
    /// `AquiferFluidPicker` policy as `OwnedAquifer` reads it.
    pub policy: [i32; 8],
    pub sources: *const Program,
    pub levels: *const Program,
    pub barrier: *const State,
    pub barrier_xz: f64,
    pub barrier_y: f64,
    pub water: i32,
    pub lava: i32,
    pub way_below: i32,
}

/// One RandomState's column source. Java keeps every program and noise state
/// it points at alive while this lives.
pub(crate) struct Columns {
    template: *const Program,
    flats: *const Program,
    /// Interpolators in each slice; the cell program reads the first `inputs`.
    interpolators: usize,
    inputs: usize,
    cell_width: i32,
    cell_height: i32,
    min_y: i32,
    height: i32,
    default_block: i32,
    /// Noise-based aquifer, or the disabled aquifer's picker policy.
    aquifer: Result<AquiferConfig, [i32; 8]>,
    chunks: Mutex<Vec<(i64, Box<ChunkColumns>)>>,
}

// SAFETY: the raw pointers are immutable programs and noise states Java keeps
// alive and never mutates while the source lives; per-chunk state moves out of
// the lock while one thread uses it.
unsafe impl Send for Columns {}
unsafe impl Sync for Columns {}

struct ChunkColumns {
    router: Router,
    /// Corner slices by cell X (0 to cells), filled on first use.
    slices: Vec<Option<Vec<f64>>>,
    aquifer: Option<OwnedAquifer>,
}

const CACHED_CHUNKS: usize = 16;

fn grid_x(x: i32) -> i32 {
    x.div_euclid(16)
}
fn grid_y(y: i32) -> i32 {
    y.div_euclid(12)
}

impl Columns {
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn new(template: *const Program, flats: *const Program, interpolators: usize, inputs: usize, cell_width: i32, cell_height: i32,
        min_y: i32, height: i32, default_block: i32, aquifer: Result<AquiferConfig, [i32; 8]>) -> Option<Self> {
        if template.is_null() || inputs == 0 || inputs > interpolators || !(1..=16).contains(&cell_width) || 16 % cell_width != 0
            || !(1..=64).contains(&cell_height) || height <= 0 || height % cell_height != 0
        {
            return None;
        }
        Some(Self { template, flats, interpolators, inputs, cell_width, cell_height, min_y, height, default_block, aquifer, chunks: Mutex::new(Vec::new()) })
    }

    fn cells_xz(&self) -> i32 {
        16 / self.cell_width
    }

    fn cells_y(&self) -> i32 {
        self.height / self.cell_height
    }

    /// The chunk's router and aquifer: built once, then taken from the cache.
    fn chunk(&self, chunk_x: i32, chunk_z: i32) -> Option<Box<ChunkColumns>> {
        let key = ((chunk_x as i64) << 32) | (chunk_z as u32 as i64);
        {
            let mut chunks = self.chunks.lock().unwrap();
            if let Some(at) = chunks.iter().position(|(k, _)| *k == key) {
                return Some(chunks.swap_remove(at).1);
            }
        }
        let (min_x, min_z) = (chunk_x.wrapping_mul(16), chunk_z.wrapping_mul(16));
        let cells = self.cells_xz();
        let geometry = Geometry {
            columns: (cells + 1) as usize,
            points: (self.cells_y() + 1) as usize,
            cell_width: self.cell_width,
            cell_height: self.cell_height,
            first_cell_z: min_z.div_euclid(self.cell_width),
            cell_min_y: self.min_y.div_euclid(self.cell_height),
            first_noise_x: min_x >> 2,
            first_noise_z: min_z >> 2,
            flat_size: (16 >> 2) + 1,
        };
        let flats = (!self.flats.is_null()).then(|| unsafe { &*self.flats });
        let router = unsafe { Router::instance(self.template, flats, geometry) }.ok()?;
        let aquifer = match &self.aquifer {
            Ok(config) => Some(self.owned_aquifer(config, min_x, min_z)?),
            Err(_) => None,
        };
        Some(Box::new(ChunkColumns { router, slices: vec![None; (cells + 1) as usize], aquifer }))
    }

    fn release(&self, chunk_x: i32, chunk_z: i32, columns: Box<ChunkColumns>) {
        let key = ((chunk_x as i64) << 32) | (chunk_z as u32 as i64);
        let mut chunks = self.chunks.lock().unwrap();
        if chunks.len() >= CACHED_CHUNKS {
            chunks.remove(0);
        }
        chunks.push((key, columns));
    }

    /// `Aquifer.NoiseBasedAquifer`'s grid and skip height for the chunk, with
    /// `NativeAquifer`'s cache shapes (every cache starts empty: they are memos).
    fn owned_aquifer(&self, c: &AquiferConfig, min_x: i32, min_z: i32) -> Option<OwnedAquifer> {
        let (max_x, max_z) = (min_x + 15, min_z + 15);
        let min_grid_x = grid_x(min_x - 5);
        let k = grid_x(max_x - 5) + 1;
        let size_x = k - min_grid_x + 1;
        let min_grid_y = grid_y(self.min_y + 1) - 1;
        let l = grid_y(self.min_y + self.height + 1) + 1;
        let size_y = l - min_grid_y + 1;
        let min_grid_z = grid_x(min_z - 5);
        let n = grid_x(max_z - 5) + 1;
        let size_z = n - min_grid_z + 1;
        let cells = usize::try_from(size_x).ok()? * usize::try_from(size_y).ok()? * usize::try_from(size_z).ok()?;
        // maxPreliminarySurfaceLevel(fromGridX(minGridX, 0), fromGridZ(minGridZ, 0), fromGridX(k, 9), fromGridZ(n, 9)).
        let levels = unsafe { &*c.levels };
        let (i, j, kk, ll) = (min_grid_x * 16, min_grid_z * 16, k * 16 + 9, n * 16 + 9);
        let (mut xs, mut zs) = (Vec::new(), Vec::new());
        let mut zz = j;
        while zz <= ll {
            let mut xx = i;
            while xx <= kk {
                xs.push((xx >> 2) << 2);
                zs.push((zz >> 2) << 2);
                xx += 4;
            }
            zz += 4;
        }
        let mut out = vec![0i32; xs.len()];
        let lanes = crate::world::level::levelgen::router::LANES;
        with_frame(levels, |frame| -> Option<()> {
            for start in (0..xs.len()).step_by(lanes) {
                let end = (start + lanes).min(xs.len());
                levels.surface_levels(frame, &xs[start..end], &zs[start..end], &mut out[start..end]).ok()?;
            }
            Some(())
        })?;
        let p = out.iter().copied().max()? + 8;
        let q = grid_y(p + 12) + 1;
        let skip_y = q * 12 + 11 - 1;
        let shape = [min_grid_x, min_grid_y, min_grid_z, size_x, size_z];
        let mut cache = vec![0i32; cells * 3];
        for entry in cache.chunks_exact_mut(3) {
            entry[1] = -1;
        }
        let surface_rect = [shape[0] * 4 - 12, shape[2] * 4 - 4, shape[3] * 4 + 19, shape[4] * 4 + 11];
        let surface = vec![0i32; (surface_rect[2] * surface_rect[3] * 2) as usize];
        let sources = unsafe { &*c.sources };
        let flat_size = (16 >> 2) + 1;
        let memo = sources.point_slots() * (flat_size * flat_size) as usize;
        Some(OwnedAquifer::new(vec![i64::MAX; cells], shape, cache, c.random.clone(), skip_y, c.policy, surface_rect, surface, c.sources, c.levels,
            [min_x >> 2, min_z >> 2, flat_size], vec![0.0; memo], vec![0u8; memo], c.barrier, c.barrier_xz, c.barrier_y, c.water, c.lava, c.way_below))
    }

    /// `getBaseHeight(x, z, types)`: one above the first state from the top in
    /// `matches` (the heightmap predicate's states), or None for no match
    /// (Java answers `getMinY()`). Err when the column cannot be evaluated here.
    pub(crate) fn base_height(&self, program: *const u8, x: i32, z: i32, matches: &[i32]) -> Result<Option<i32>, ()> {
        let (chunk_x, chunk_z) = (x >> 4, z >> 4);
        let mut chunk = self.chunk(chunk_x, chunk_z).ok_or(())?;
        let result = self.scan(&mut chunk, program, x, z, matches);
        self.release(chunk_x, chunk_z, chunk);
        result
    }

    /// `getBaseColumn(x, z)`'s states from the noise minimum Y upward, as far
    /// as heightmap predicates can tell: the default block where density is
    /// positive (ore veins are solid too), else the aquifer's substance.
    pub(crate) fn column(&self, program: *const u8, x: i32, z: i32) -> Result<Vec<i32>, ()> {
        let (chunk_x, chunk_z) = (x >> 4, z >> 4);
        let mut chunk = self.chunk(chunk_x, chunk_z).ok_or(())?;
        let mut states = Vec::with_capacity(self.height.max(0) as usize);
        let result = self.scan_from(&mut chunk, program, x, z, &[], Some(&mut states));
        self.release(chunk_x, chunk_z, chunk);
        result?;
        states.reverse();
        Ok(states)
    }

    /// The noise range's minimum Y.
    pub(crate) fn min_y(&self) -> i32 {
        self.min_y
    }

    fn scan(&self, chunk: &mut ChunkColumns, program: *const u8, x: i32, z: i32, matches: &[i32]) -> Result<Option<i32>, ()> {
        self.scan_from(chunk, program, x, z, matches, None)
    }

    /// The column from the top until a state in `matches`, or (with `all`) every state top down.
    fn scan_from(&self, chunk: &mut ChunkColumns, program: *const u8, x: i32, z: i32, matches: &[i32], mut all: Option<&mut Vec<i32>>) -> Result<Option<i32>, ()> {
        let w = self.cell_width;
        let (local_x, local_z) = (x & 15, z & 15);
        let (cell_x, cell_z) = ((local_x / w) as usize, (local_z / w) as usize);
        let (fx, fz) = (f64::from(local_x % w) / f64::from(w), f64::from(local_z % w) / f64::from(w));
        let len = chunk.router.output_len();
        for slice in [cell_x, cell_x + 1] {
            if chunk.slices[slice].is_none() {
                let mut out = vec![0.0; len];
                chunk.router.slice(x - local_x + slice as i32 * w, &mut out).map_err(|_| ())?;
                chunk.slices[slice] = Some(out);
            }
        }
        let low = chunk.slices[cell_x].as_ref().ok_or(())?;
        let high = chunk.slices[cell_x + 1].as_ref().ok_or(())?;
        let columns = (self.cells_xz() + 1) as usize;
        let points = (self.cells_y() + 1) as usize;
        if len != self.interpolators * columns * points {
            return Err(());
        }
        let cell_min_y = self.min_y.div_euclid(self.cell_height);
        let mut frame = vec![0.0; self.inputs * 8];
        let at = |slice: &[f64], input: usize, column: usize, y: usize| slice[(input * columns + column) * points + y];
        let mut aquifer = chunk.aquifer.as_mut().map(|a| a.substance());
        for cell_y in (0..points - 1).rev() {
            for input in 0..self.inputs {
                let (j, i) = (cell_z, cell_y);
                frame[input * 8..input * 8 + 8].copy_from_slice(&[
                    at(low, input, j, i),
                    at(high, input, j, i),
                    at(low, input, j, i + 1),
                    at(high, input, j, i + 1),
                    at(low, input, j + 1, i),
                    at(high, input, j + 1, i),
                    at(low, input, j + 1, i + 1),
                    at(high, input, j + 1, i + 1),
                ]);
            }
            for w_y in (0..self.cell_height).rev() {
                let y = (cell_min_y + cell_y as i32) * self.cell_height + w_y;
                let fy = f64::from(w_y) / f64::from(self.cell_height);
                let density = unsafe { density_cell(program, frame.as_ptr(), fx, fy, fz) };
                if density.is_nan() {
                    return Err(());
                }
                // MaterialRuleList: the aquifer's substance (null above zero), then ore
                // veins, whose states are all solid like the default block.
                let state = if density > 0.0 {
                    self.default_block
                } else {
                    match (&mut aquifer, &self.aquifer) {
                        (Some(substance), _) => substance.compute_with_density(x, y, z, density).map_err(|_| ())?.0,
                        (None, Err(policy)) => picked(policy, y),
                        _ => return Err(()),
                    }
                };
                let state = if state < 0 { self.default_block } else { state };
                if let Some(states) = all.as_mut() {
                    states.push(state);
                } else if matches.contains(&state) {
                    return Ok(Some(y + 1));
                }
            }
        }
        Ok(None)
    }
}

/// Creates a column source. `ints`: [interpolators, cell inputs, cell width,
/// cell height, min Y, height, default block, aquifer (0 disabled, 1 noise
/// based), random kind (1 Xoroshiro, 2 Legacy), policy (8), water, lava, way
/// below]; `longs`: [template, flats, actual factory handle, reserved, sources, levels,
/// barrier noise state]; `doubles`: [barrier XZ scale, barrier Y scale].
/// Returns 0 when invalid. Released with `mattmc_noise_columns_release`.
///
/// # Safety
/// `ints` holds 20 i32s, `longs` 7 i64s, `doubles` 2 f64s. Every program and
/// state addressed stays alive and unchanged until release.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_columns_create_owned(ints: *const i32, longs: *const i64, doubles: *const f64) -> u64 {
    if ints.is_null() || longs.is_null() || doubles.is_null() {
        return 0;
    }
    let i = unsafe { std::slice::from_raw_parts(ints, 20) };
    let l = unsafe { std::slice::from_raw_parts(longs, 7) };
    let d = unsafe { std::slice::from_raw_parts(doubles, 2) };
    let mut policy = [0i32; 8];
    policy.copy_from_slice(&i[9..17]);
    let aquifer = match i[7] {
        0 => Err(policy),
        1 => {
            if l[2] == 0 { return 0; }
            let random = unsafe { retain_factory(l[2] as u64) };
            if random.kind() != i[8] { return 0; }
            if l[4] == 0 || l[5] == 0 {
                return 0;
            }
            Ok(AquiferConfig {
                random,
                policy,
                sources: l[4] as *const Program,
                levels: l[5] as *const Program,
                barrier: l[6] as *const State,
                barrier_xz: d[0],
                barrier_y: d[1],
                water: i[17],
                lava: i[18],
                way_below: i[19],
            })
        }
        _ => return 0,
    };
    let (Ok(interpolators), Ok(inputs)) = (usize::try_from(i[0]), usize::try_from(i[1])) else { return 0 };
    match Columns::new(l[0] as *const Program, l[1] as *const Program, interpolators, inputs, i[2], i[3], i[4], i[5], i[6], aquifer) {
        Some(columns) => Box::into_raw(Box::new(columns)) as u64,
        None => 0,
    }
}

/// # Safety
/// `handle` came from `mattmc_noise_columns_create` and is released once.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_columns_release(handle: u64) {
    if handle != 0 {
        drop(unsafe { Box::from_raw(handle as *mut Columns) });
    }
}

/// No state matched: Java answers `getMinY()`.
pub(crate) const NO_MATCH: i32 = i32::MIN;
/// The column could not be evaluated natively: Java evaluates it.
pub(crate) const DECLINED: i32 = i32::MIN + 1;

/// `getBaseHeight(x, z, types)` with the cell program at `program` and the
/// states `matches` (`count` ids) that pass the heightmap's predicate.
///
/// # Safety
/// `handle` is live; `program` is a validated cell program over the source's
/// inputs; `matches` holds `count` i32s.
#[no_mangle]
pub unsafe extern "C" fn mattmc_noise_columns_height(handle: u64, program: *const u8, x: i32, z: i32, matches: *const i32, count: i32) -> i32 {
    if handle == 0 || program.is_null() || matches.is_null() || count < 0 {
        return DECLINED;
    }
    let columns = unsafe { &*(handle as *const Columns) };
    let matches = unsafe { std::slice::from_raw_parts(matches, count as usize) };
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| columns.base_height(program, x, z, matches)));
    match result {
        Ok(Ok(Some(height))) => height,
        Ok(Ok(None)) => NO_MATCH,
        _ => DECLINED,
    }
}

//! `NoiseBasedAquifer.computeSubstance(SinglePointContext, 0.0)` entirely in
//! Rust, for chunks whose aquifer runs on built-in pure sources: the global
//! fluid picker, the skip-sampling height, aquifer centres drawn with native
//! positional randomness, the ordered decision, fluid statuses from the native
//! sources and surface programs, and the barrier noise. The caches it fills
//! are Java's own (centres, statuses, surface levels, FlatCache corners),
//! borrowed for the call; all are pure memos.
use super::super::router::{Binding, Program};
use super::super::synth::{noise_eval, State};
use super::locations::fill_cell_locations;
use super::{cell, decision, fluid, nearest};
use crate::world::level::levelgen::random::Positional;

pub(crate) struct Substance<'a> {
    pub grid: &'a mut [i64],
    /// [minGridX, minGridY, minGridZ, sizeX, sizeZ].
    pub shape: [i32; 5],
    /// Per grid index [fluid level, state id, kind (1 water, 2 lava)]; id -1 when absent.
    pub cache: &'a mut [i32],
    pub random: Positional,
    pub skip_y: i32,
    /// `AquiferFluidPicker`: [lava level, lava id, fluid level, fluid id,
    /// fluid is air, generation disabled, disabled level, air id].
    pub policy: [i32; 8],
    /// Preliminary surface levels: [minX, minZ, width, height] quarts and the cache.
    pub surface_rect: [i32; 4],
    pub surface: &'a mut [i32],
    pub sources: &'a Program,
    pub levels: &'a Program,
    pub binding: Binding<'a>,
    pub barrier: *const State,
    pub barrier_xz: f64,
    pub barrier_y: f64,
    pub water: i32,
    pub lava: i32,
    pub way_below: i32,
}

/// `AquiferFluidPicker.computeFluid(x, y, z).at(y)` for a policy.
pub(crate) fn picked(p: &[i32; 8], y: i32) -> i32 {
    let (level, id) = if p[5] != 0 {
        (p[6], p[7])
    } else if y < p[0].min(p[2]) {
        (p[0], p[1])
    } else {
        (p[2], p[3])
    };
    if y < level { id } else { p[7] }
}

impl Substance<'_> {
    fn picked(&self, y: i32) -> (i32, i32) {
        (0, picked(&self.policy, y))
    }

    /// The fluid status at a grid index's centre (`getAquiferStatus`).
    pub(crate) fn status(&mut self, index: usize) -> Result<(), i32> {
        let pos = self.grid[index];
        let mut f = [0i32; 26];
        f[1] = (pos >> 38) as i32;
        f[2] = ((pos << 52) >> 52) as i32;
        f[3] = ((pos << 26) >> 38) as i32;
        f[20] = self.lava;
        f[21] = self.way_below;
        f[22..26].copy_from_slice(&self.surface_rect);
        let status = fluid::native_step(&mut f, &self.policy, self.surface, self.sources, self.levels, &mut self.binding);
        if status != 0 {
            return Err(status.min(-1));
        }
        let id = f[13];
        self.cache[index * 3..index * 3 + 3].copy_from_slice(&[f[12], id, if id == self.water { 1 } else if id == self.lava { 2 } else { 0 }]);
        Ok(())
    }

    /// `computeSubstance` at (x, y, z) with density 0: the state (-1 for null)
    /// and `shouldScheduleFluidUpdate` after the call.
    pub(crate) fn compute(&mut self, x: i32, y: i32, z: i32) -> Result<(i32, bool), i32> {
        let (_, state) = self.picked(y);
        if y > self.skip_y {
            return Ok((state, false));
        }
        if state == self.lava {
            return Ok((self.lava, false));
        }
        // NativeAquifer.scalar: the twelve centres, ranked; then the decision.
        if !fill_cell_locations(self.grid, &self.shape, self.random, x, y, z, 1, 1) {
            return Err(-3);
        }
        let mut points = [[0; 4]; 12];
        if !cell::centers(x, y, z, self.grid, &self.shape, &mut points) {
            return Err(-3);
        }
        let mut f = [0i32; 20];
        nearest::point(x, y, z, &points, &mut f[..8]);
        f[10] = y;
        f[11] = -1;
        f[14] = self.policy[7];
        let mut values = [0.0, f64::NAN];
        loop {
            match decision::step(&mut f, &mut values, self.cache, false) {
                0 => return Ok((f[12], f[13] != 0)),
                1 => self.status(f[9] as usize)?,
                2 => {
                    let (_, below) = self.picked(y.wrapping_sub(1));
                    f[11] = (below == self.lava) as i32;
                }
                3 => {
                    values[1] = if self.barrier.is_null() {
                        0.0
                    } else {
                        unsafe { noise_eval(self.barrier, x as f64 * self.barrier_xz, y as f64 * self.barrier_y, z as f64 * self.barrier_xz, 0.0, 0.0, 0) }
                    };
                    f[15] = 1;
                }
                other => return Err(other.min(-1)),
            }
        }
    }
}

/// An aquifer's native state owned by Rust for a whole NOISE fill: copies of
/// Java's caches (centres, statuses, surface levels, FlatCache corners) that
/// Java copies back afterwards, and its policy, programs and barrier noise.
pub(crate) struct OwnedAquifer {
    pub grid: Vec<i64>,
    pub shape: [i32; 5],
    pub cache: Vec<i32>,
    pub random: Positional,
    pub skip_y: i32,
    pub policy: [i32; 8],
    pub surface_rect: [i32; 4],
    pub surface: Vec<i32>,
    /// The sources and preliminary surface programs Java keeps alive for the fill.
    pub sources: *const Program,
    pub levels: *const Program,
    /// The chunk's FlatCache grid [first quart X, first quart Z, size] and its memo.
    pub flat: [i32; 3],
    pub memo: Vec<f64>,
    pub present: Vec<u8>,
    pub barrier: *const State,
    pub barrier_xz: f64,
    pub barrier_y: f64,
    pub water: i32,
    pub lava: i32,
    pub way_below: i32,
    // The batch frame and values `NativeAquifer.prepareMaterials` keeps.
    frame: [i32; 32],
    values: [f64; 2],
}

impl OwnedAquifer {
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn new(grid: Vec<i64>, shape: [i32; 5], cache: Vec<i32>, random: Positional, skip_y: i32, policy: [i32; 8], surface_rect: [i32; 4],
        surface: Vec<i32>, sources: *const Program, levels: *const Program, flat: [i32; 3], memo: Vec<f64>, present: Vec<u8>,
        barrier: *const State, barrier_xz: f64, barrier_y: f64, water: i32, lava: i32, way_below: i32) -> Self {
        OwnedAquifer { grid, shape, cache, random, skip_y, policy, surface_rect, surface, sources, levels, flat, memo, present, barrier, barrier_xz,
            barrier_y, water, lava, way_below, frame: [0; 32], values: [0.0; 2] }
    }

    /// A substance evaluator over this state.
    pub(crate) fn substance(&mut self) -> Substance<'_> {
        Substance {
            grid: &mut self.grid,
            shape: self.shape,
            cache: &mut self.cache,
            random: self.random,
            skip_y: self.skip_y,
            policy: self.policy,
            surface_rect: self.surface_rect,
            surface: &mut self.surface,
            sources: unsafe { &*self.sources },
            levels: unsafe { &*self.levels },
            binding: Binding { first_x: self.flat[0], first_z: self.flat[1], size: self.flat[2], memo: &mut self.memo, present: &mut self.present },
            barrier: self.barrier,
            barrier_xz: self.barrier_xz,
            barrier_y: self.barrier_y,
            water: self.water,
            lava: self.lava,
            way_below: self.way_below,
        }
    }

    /// `NativeAquifer.prepareMaterials` for the cell at block (x, y, z) of
    /// `width` x `width` x `height` with its densities: (state, schedule) per
    /// block into `out`, statuses computed natively instead of asked of Java.
    pub(crate) fn cell_materials(&mut self, x: i32, y: i32, z: i32, width: i32, height: i32, density: &[f64], out: &mut [i32]) -> Result<(), i32> {
        if !fill_cell_locations(&mut self.grid, &self.shape, self.random, x, y, z, width, height) {
            return Err(-3);
        }
        let p = self.policy;
        let f = &mut self.frame;
        f[14] = p[7];
        f[16] = 0;
        f[17] = 0;
        f[18] = x;
        f[19] = y;
        f[20] = z;
        f[21] = width;
        f[22] = height;
        f[23] = self.skip_y;
        f[24] = (-54i32).min(p[2]);
        f[25] = p[0];
        f[26] = p[1];
        f[27] = p[2];
        f[28] = p[3];
        f[29] = if p[3] == self.lava { 2 } else if p[3] == self.water { 1 } else { 0 };
        f[30] = p[5];
        f[31] = p[6];
        loop {
            let status = unsafe {
                cell::materials(&mut self.frame, &mut self.values, density, out, &self.grid, &self.shape, &self.cache, self.barrier, self.barrier_xz,
                    self.barrier_y)
            };
            match status {
                0 => return Ok(()),
                1 => {
                    let index = self.frame[9] as usize;
                    self.substance().status(index)?;
                }
                4 => {}
                other => return Err(other.min(-1)),
            }
        }
    }
}

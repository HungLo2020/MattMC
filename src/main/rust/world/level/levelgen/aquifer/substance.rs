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
    fn status(&mut self, index: usize) -> Result<(), i32> {
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

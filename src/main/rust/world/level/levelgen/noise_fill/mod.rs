//! Native-owned NOISE-stage block fill (`NoiseBasedChunkGenerator.doFill`).
//! Java still fills interpolation slices, cell density caches and aquifer
//! cell materials; Rust then runs every block of each cell in Java's exact
//! order: interpolation, the aquifer or disabled-aquifer substance, the ore
//! vein rule, the default block, section palette writes, both world-generation
//! heightmaps and fluid post-processing marks. Java installs the results.
mod ffi;
pub(crate) mod section;
#[cfg(test)]
mod tests;

use super::density::evaluator::density_noise;
use super::density::math::{math, needs_right};
use super::random::Positional;
use super::synth::State;
use section::Section;

/// Per-state flags supplied by Java, indexed by block-state registry id.
pub(crate) const FLAG_AIR: u8 = 1;
pub(crate) const FLAG_BLOCKS_MOTION: u8 = 2;
pub(crate) const FLAG_FLUID: u8 = 4;
pub(crate) const FLAG_RANDOM_TICKS: u8 = 8;

/// The generator's `AquiferFluidPicker`: lava below `min(-54, sea level)`.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) struct Picker {
    pub lava_below: i32,
    pub lava_level: i32,
    pub lava: i32,
    pub fluid_level: i32,
    pub fluid: i32,
}

impl Picker {
    /// `computeFluid(x, y, z).at(y)`, with whether that status is the lava one.
    fn at(&self, y: i32, air: i32) -> (i32, bool) {
        let (level, kind, lava) = if y < self.lava_below {
            (self.lava_level, self.lava, true)
        } else {
            (self.fluid_level, self.fluid, false)
        };
        (if y < level { kind } else { air }, lava && y < level)
    }
}

/// How the first material rule chooses a substance.
#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum Substance {
    /// `NoiseBasedAquifer.computeMaterial`: Java's early returns natively, the
    /// remaining blocks from the cell batch Java prepares on request.
    AquiferBatch {
        picker: Picker,
        skip_above: i32,
        /// Whether `picker.fluid` is a lava-block state (`at(y).is(LAVA)`).
        fluid_is_lava: bool,
        /// `Blocks.LAVA.defaultBlockState()`.
        lava_default: i32,
    },
    /// `Aquifer.createDisabled` with the generator's `AquiferFluidPicker`.
    Disabled(Picker),
}

/// `OreVeinifier` with the vanilla router shape the Java gate matched.
#[derive(Clone, Copy, Debug)]
pub(crate) struct OreVeins {
    pub random: Positional,
    /// copper ore, raw copper block, granite, deepslate iron ore, raw iron block, tuff.
    pub states: [i32; 6],
    /// `MulOrAdd` constant of `veinRidged`.
    pub ridged_constant: f64,
    /// `maxValue()` of `veinRidged`'s second `max` argument (its branch bound).
    pub ridged_bound: f64,
    pub gap_xz_scale: f64,
    pub gap_y_scale: f64,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct Config {
    pub min_y: i32,
    pub height: i32,
    pub min_section: i32,
    pub section_count: i32,
    pub cell_width: i32,
    pub cell_height: i32,
    pub air: i32,
    pub default_block: i32,
    pub global_bits: u32,
    pub substance: Substance,
    pub ore: Option<OreVeins>,
}

#[derive(Debug, PartialEq)]
pub(crate) enum Error {
    /// A state id outside the flag table (Java gates make this unreachable).
    UnknownState(i32),
    /// A write outside the chunk's sections.
    OutOfChunk,
}

/// Interpolated values of one cell, from `NoiseInterpolator.copyCellCorners`
/// order: 000, 100, 010, 110, 001, 101, 011, 111 (x, y, z).
#[derive(Clone, Copy)]
pub(crate) struct Corners(pub [f64; 8]);

impl Corners {
    /// `updateForY`, `updateForX`, `updateForZ` then the deferred final lerp.
    fn value(&self, dy: f64, dx: f64, dz: f64) -> f64 {
        let c = &self.0;
        let xz00 = lerp(dy, c[0], c[2]);
        let xz10 = lerp(dy, c[1], c[3]);
        let xz01 = lerp(dy, c[4], c[6]);
        let xz11 = lerp(dy, c[5], c[7]);
        let z0 = lerp(dx, xz00, xz10);
        let z1 = lerp(dx, xz01, xz11);
        lerp(dz, z0, z1)
    }
}

/// Inputs of one cell; `materials` is the aquifer batch (state, schedule) pairs.
pub(crate) struct Cell<'a> {
    pub x: i32,
    pub y: i32,
    pub z: i32,
    pub density: &'a [f64],
    pub materials: &'a [i32],
    pub toggle: Corners,
    pub ridged_a: Corners,
    pub ridged_b: Corners,
    pub gap: *const State,
}

/// `Mth.lerp(double, double, double)`.
fn lerp(delta: f64, start: f64, end: f64) -> f64 {
    start + delta * (end - start)
}

/// `Mth.clampedMap(double, double, double, double, double)`.
fn clamped_map(value: f64, from: f64, to: f64, start: f64, end: f64) -> f64 {
    let t = (value - from) / (to - from);
    if t < 0.0 {
        start
    } else if t > 1.0 {
        end
    } else {
        lerp(t, start, end)
    }
}

pub(crate) struct NoiseFill<'a> {
    config: Config,
    flags: &'a [u8],
    sections: Vec<Option<Section>>,
    /// Per column, `Heightmap.getFirstAvailable` minus min Y (0 = untouched).
    ocean_floor: [u32; 256],
    world_surface: [u32; 256],
    post_process: Vec<[i32; 3]>,
}

impl<'a> NoiseFill<'a> {
    pub fn new(config: Config, flags: &'a [u8]) -> Self {
        Self {
            config,
            flags,
            sections: (0..config.section_count).map(|_| None).collect(),
            ocean_floor: [0; 256],
            world_surface: [0; 256],
            post_process: Vec::new(),
        }
    }

    fn flags(&self, state: i32) -> Result<u8, Error> {
        self.flags.get(state as usize).copied().filter(|_| state >= 0).ok_or(Error::UnknownState(state))
    }

    /// `NoiseBasedAquifer.compute` before its native batch: the early returns.
    /// `None` means the block needs the cell's batch materials.
    fn aquifer_early(&self, density: f64, y: i32) -> Option<(Option<i32>, bool)> {
        let Substance::AquiferBatch { picker, skip_above, fluid_is_lava, lava_default } = self.config.substance else {
            return None;
        };
        if density > 0.0 {
            return Some((None, false));
        }
        let (state, lava) = picker.at(y, self.config.air);
        if y > skip_above {
            Some((Some(state), false))
        } else if lava || (fluid_is_lava && state == picker.fluid && !(y < picker.lava_below)) {
            Some((Some(lava_default), false))
        } else {
            None
        }
    }

    /// Whether any block of the cell reaches the aquifer batch, as Java's
    /// lazy preparation would. Such a cell needs Java to supply materials.
    pub fn needs_materials(&self, cell: &Cell) -> bool {
        if !matches!(self.config.substance, Substance::AquiferBatch { .. }) {
            return false;
        }
        let (width, height) = (self.config.cell_width, self.config.cell_height);
        (0..height).rev().any(|u| {
            let y = cell.y.wrapping_add(u);
            (0..width * width).any(|column| {
                let index = ((height - 1 - u) * width * width + column) as usize;
                self.aquifer_early(cell.density[index], y).is_none()
            })
        })
    }

    /// The material rule list for one block: substance, then ore veins.
    /// Returns the state (None = default block) and the aquifer's schedule flag.
    fn rule(&self, cell: &Cell, index: usize, x: i32, y: i32, z: i32, d: (f64, f64, f64)) -> (Option<i32>, bool) {
        let (substance, schedule) = match self.config.substance {
            Substance::AquiferBatch { .. } => match self.aquifer_early(cell.density[index], y) {
                Some(early) => early,
                None => {
                    let state = cell.materials[index * 2];
                    ((state != -1).then_some(state), cell.materials[index * 2 + 1] != 0)
                }
            },
            Substance::Disabled(picker) => {
                if cell.density[index] > 0.0 {
                    (None, false)
                } else {
                    (Some(picker.at(y, self.config.air).0), false)
                }
            }
        };
        if substance.is_some() {
            return (substance, schedule);
        }
        let ore = match self.config.ore {
            Some(ore) => self.ore(&ore, cell, x, y, z, d),
            None => None,
        };
        (ore, schedule)
    }

    /// `OreVeinifier.create(...)`'s filler, statement by statement.
    fn ore(&self, ore: &OreVeins, cell: &Cell, x: i32, y: i32, z: i32, (dy, dx, dz): (f64, f64, f64)) -> Option<i32> {
        let toggle = cell.toggle.value(dy, dx, dz);
        // (ore, raw ore block, filler, minY, maxY)
        let (ore_state, raw, filler, min_y, max_y) = if toggle > 0.0 {
            (ore.states[0], ore.states[1], ore.states[2], 0, 50)
        } else {
            (ore.states[3], ore.states[4], ore.states[5], -60, -8)
        };
        let e = toggle.abs();
        let above = max_y - y;
        let below = y - min_y;
        if below < 0 || above < 0 {
            return None;
        }
        let edge = clamped_map(above.min(below) as f64, 0.0, 20.0, -0.2, 0.0);
        if e + edge < 0.4_f32 as f64 {
            return None;
        }
        let mut random = ore.random.at(x, y, z);
        if random.next_float() > 0.7_f32 {
            return None;
        }
        if self.ridged(ore, cell, (dy, dx, dz)) >= 0.0 {
            return None;
        }
        let chance = clamped_map(e, 0.4_f32 as f64, 0.6_f32 as f64, 0.1_f32 as f64, 0.3_f32 as f64);
        if (random.next_float() as f64) < chance && self.gap(ore, cell, x, y, z) > -0.3_f32 as f64 {
            Some(if random.next_float() < 0.02_f32 { raw } else { ore_state })
        } else {
            Some(filler)
        }
    }

    /// `MulOrAdd(ADD, Ap2(MAX, Mapped(ABS, a), Mapped(ABS, b)), c)` through the
    /// same scalar operations Java's density functions call.
    fn ridged(&self, ore: &OreVeins, cell: &Cell, (dy, dx, dz): (f64, f64, f64)) -> f64 {
        let a = math(14, cell.ridged_a.value(dy, dx, dz), 0.0, 0.0, 0.0);
        let max = if !needs_right(11, a, 0.0, ore.ridged_bound) {
            a
        } else {
            let b = math(14, cell.ridged_b.value(dy, dx, dz), 0.0, 0.0, 0.0);
            math(11, a, b, 0.0, ore.ridged_bound)
        };
        math(13, max, 0.0, ore.ridged_constant, 0.0)
    }

    /// `DensityFunctions.Noise.compute`: `noise(op 1, block xyz, xz/y scales)`.
    fn gap(&self, ore: &OreVeins, cell: &Cell, x: i32, y: i32, z: i32) -> f64 {
        unsafe {
            density_noise(cell.gap, 1, x as f64, y as f64, z as f64, 0.0, 0.0, 0.0, ore.gap_xz_scale, ore.gap_y_scale)
        }
    }

    /// Every block of one cell in `doFill`'s order: descending in-cell Y, then
    /// X, then Z. Writes non-air states and their side effects.
    pub fn fill_cell(&mut self, cell: &Cell) -> Result<(), Error> {
        let width = self.config.cell_width;
        let height = self.config.cell_height;
        for u in (0..height).rev() {
            let y = cell.y.wrapping_add(u);
            let dy = u as f64 / height as f64;
            for ix in 0..width {
                let x = cell.x.wrapping_add(ix);
                let dx = ix as f64 / width as f64;
                for iz in 0..width {
                    let z = cell.z.wrapping_add(iz);
                    let dz = iz as f64 / width as f64;
                    let index = (((height - 1 - u) * width + ix) * width + iz) as usize;
                    let (state, schedule) = self.rule(cell, index, x, y, z, (dy, dx, dz));
                    let state = state.unwrap_or(self.config.default_block);
                    if state != self.config.air {
                        self.write(x, y, z, state, schedule)?;
                    }
                }
            }
        }
        Ok(())
    }

    fn write(&mut self, x: i32, y: i32, z: i32, state: i32, schedule: bool) -> Result<(), Error> {
        let flags = self.flags(state)?;
        let section_index = (y >> 4).wrapping_sub(self.config.min_section);
        if section_index < 0 || section_index >= self.config.section_count {
            return Err(Error::OutOfChunk);
        }
        let (air, global_bits) = (self.config.air, self.config.global_bits);
        let section = self.sections[section_index as usize].get_or_insert_with(|| Section::new(air, global_bits));
        section.set((((y & 15) << 8) | ((z & 15) << 4) | (x & 15)) as usize, state);
        // LevelChunkSection.setBlockState counters; the replaced state is air.
        if flags & FLAG_AIR == 0 {
            section.non_empty += 1;
            if flags & FLAG_RANDOM_TICKS != 0 {
                section.ticking += 1;
            }
        }
        if flags & FLAG_FLUID != 0 {
            section.fluid += 1;
        }
        // Heightmap.update: columns are visited top-down and each position is
        // written once, so a predicate match raises the height only if higher.
        let column = ((x & 15) + (z & 15) * 16) as usize;
        let first_available = (y - self.config.min_y + 1) as u32;
        if flags & FLAG_BLOCKS_MOTION != 0 && first_available > self.ocean_floor[column] {
            self.ocean_floor[column] = first_available;
        }
        if flags & FLAG_AIR == 0 && first_available > self.world_surface[column] {
            self.world_surface[column] = first_available;
        }
        if schedule && flags & FLAG_FLUID != 0 {
            self.post_process.push([x, y, z]);
        }
        Ok(())
    }

    pub fn section(&self, index: usize) -> Option<&Section> {
        self.sections.get(index).and_then(Option::as_ref)
    }

    /// `Heightmap` raw data: `ceillog2(height + 1)` bits per column.
    pub fn heightmap_raw(&self, ocean_floor: bool) -> Vec<i64> {
        let bits = ceil_log2(self.config.height as u32 + 1);
        let values = if ocean_floor { &self.ocean_floor } else { &self.world_surface };
        section::pack(values, bits)
    }

    pub fn post_process(&self) -> &[[i32; 3]] {
        &self.post_process
    }
}

/// `Mth.ceillog2`.
pub(crate) fn ceil_log2(value: u32) -> u32 {
    if value <= 1 {
        0
    } else {
        32 - (value - 1).leading_zeros()
    }
}

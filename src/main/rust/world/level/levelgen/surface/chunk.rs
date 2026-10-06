//! The SURFACE stage over Rust-owned chunk storage ([`ProtoStorage`]): columns
//! run the existing evaluator over the storage, and each completed block is
//! written as `BlockColumn.setBlock` writes it (the chunk's `setBlockState`,
//! then a post-processing mark for fluids). The evaluator's requests are
//! answered here when their inputs are native: `steep` from the storage's
//! heightmap (once per column, as its shared lazy Java condition caches it),
//! vertical gradients with their positional random, noise thresholds, the
//! secondary noise, the terracotta band offset and the minimum surface level.
//! Java answers the rest (temperature, extension conditions).
use super::evaluator::surface_step;
use super::frame::*;
use crate::world::level::levelgen::noise_fill::section::ENTRIES;
use crate::world::level::levelgen::noise_fill::{FLAG_AIR, FLAG_FLUID};
use crate::world::level::levelgen::math::{java_round, lerp2};
use crate::world::level::levelgen::proto_chunk::{self, ProtoStorage};
use crate::world::level::levelgen::random::Positional;
use crate::world::level::levelgen::router::{with_frame, Program};
use crate::world::level::levelgen::synth::{noise_eval, State};

/// How the stage answers a rule condition slot.
#[derive(Clone, Copy, Debug)]
pub(crate) enum Slot {
    /// A Java condition (temperature, extensions).
    Java,
    /// `SurfaceRules.Steep`: the context's shared lazy steep condition.
    Steep,
    /// A vertical gradient between its resolved bounds, with its positional random.
    Gradient { low: i32, high: i32, random: Positional },
    /// A noise threshold: `NormalNoise.getValue(x, 0, z)` in [min, max].
    Noise { state: *const State, min: f64, max: f64 },
}

/// `SurfaceSystem.bandOffset` from its noise value: `(int)Math.round(noise * 4.0)`.
pub(crate) fn band_offset(noise: f64) -> i32 {
    java_round(noise * 4.0) as i32
}

/// The surface system's own inputs, each native when Java could provide it.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Inputs {
    /// `clayBandsOffsetNoise` for `bandOffset`, or null.
    pub band: *const State,
    /// `surfaceSecondaryNoise` for `getSurfaceSecondary`, or null.
    pub secondary: *const State,
    /// The noise chunk's preliminary surface level program for
    /// `getMinSurfaceLevel`, or null.
    pub levels: *const Program,
}

pub(crate) struct Biomes {
    pub seed: i64,
    pub qx: i32,
    pub qy: i32,
    pub qz: i32,
    pub size_x: usize,
    pub size_y: usize,
    pub size_z: usize,
    pub ids: Vec<i32>,
}

pub(crate) struct SurfaceChunk {
    /// The chunk's storage, which Java keeps alive while this stage runs.
    storage: *mut ProtoStorage,
    default_block: i32,
    biomes: Option<Biomes>,
    slots: Vec<Slot>,
    inputs: Inputs,
    /// The column's secondary surface noise once answered natively.
    secondary: f64,
    /// The preliminary surface levels at the last surface cell's corners, as
    /// the context's `preliminarySurfaceCache` keeps them across columns.
    corners: Option<((i32, i32), [i32; 4])>,
    // The current column.
    x: i32,
    z: i32,
    count: usize,
    committed: usize,
    column_flags: Vec<i32>,
    output: Vec<i32>,
    column_biomes: Vec<i32>,
    /// Java's `context.steep` is one lazy condition shared by every steep slot.
    steep_answer: i8,
}

#[derive(Debug, PartialEq)]
pub(crate) enum Error {
    Input,
    Evaluation(i32),
    State(i32),
}

impl From<proto_chunk::Error> for Error {
    fn from(error: proto_chunk::Error) -> Self {
        match error {
            proto_chunk::Error::Input => Error::Input,
            proto_chunk::Error::State(state) => Error::State(state),
        }
    }
}

impl SurfaceChunk {
    /// # Safety
    /// `storage` is live and used only through this stage while it runs.
    pub(crate) unsafe fn new(storage: *mut ProtoStorage, default_block: i32, biomes: Option<Biomes>, slots: Vec<Slot>, inputs: Inputs) -> Result<Self, Error> {
        if storage.is_null() {
            return Err(Error::Input);
        }
        if let Some(b) = &biomes {
            if b.ids.len() != b.size_x * b.size_y * b.size_z {
                return Err(Error::Input);
            }
        }
        Ok(Self {
            storage,
            default_block,
            biomes,
            slots,
            inputs,
            secondary: 0.0,
            corners: None,
            x: 0,
            z: 0,
            count: 0,
            committed: 0,
            column_flags: Vec::new(),
            output: Vec::new(),
            column_biomes: Vec::new(),
            steep_answer: -1,
        })
    }

    fn storage(&self) -> &ProtoStorage {
        unsafe { &*self.storage }
    }

    fn storage_mut(&mut self) -> &mut ProtoStorage {
        unsafe { &mut *self.storage }
    }

    /// `BlockColumn.setBlock` of buildSurface: the chunk's setBlockState, then
    /// (inside the build height) a post-processing mark for any fluid.
    fn set_block(&mut self, x: i32, y: i32, z: i32, state: i32) -> Result<(), Error> {
        let storage = self.storage_mut();
        if y < storage.min_y() || y >= storage.min_y() + 16 * storage.section_count() as i32 {
            return Ok(());
        }
        let flags = storage.flag(state)?;
        storage.set_block_state(x, y, z, state)?;
        if flags & FLAG_FLUID != 0 {
            storage.mark(x, y, z);
        }
        Ok(())
    }

    /// Starts a column as `NativeSurface.column` prepares one: flags scanned
    /// from the sections and quart biomes selected from the table.
    pub(crate) fn begin(&mut self, x: i32, z: i32, top: i32, uses_biomes: bool) -> Result<usize, Error> {
        // The storage is separate memory: its borrow does not hold `self`.
        let storage: &ProtoStorage = unsafe { &*self.storage };
        let min_y = storage.min_y();
        let count = top - min_y + 1;
        if count <= 0 {
            self.count = 0;
            return Ok(0);
        }
        let count = count as usize;
        if count > ENTRIES {
            return Err(Error::Input);
        }
        self.x = x;
        self.z = z;
        self.count = count;
        self.committed = count;
        self.column_flags.resize(count, 0);
        self.output.clear();
        self.output.resize(count, -1);
        self.column_biomes.clear();
        self.column_biomes.resize(count, 0);
        self.steep_answer = -1;
        self.secondary = 0.0;
        let default_flags = storage.flag(self.default_block)?;
        let default_flag = if default_flags & FLAG_AIR != 0 { 0 } else if default_flags & FLAG_FLUID != 0 { 1 } else { 3 };
        let mut y = 0;
        while y < count {
            let absolute = min_y + y as i32;
            let end = count.min(y + 16 - (absolute & 15) as usize);
            match storage.section_at(absolute) {
                Some(section) if section.non_empty != 0 => {
                    for at in y..end {
                        let world_y = min_y + at as i32;
                        let state = section.get((((world_y & 15) << 8) | ((z & 15) << 4) | (x & 15)) as usize);
                        let flags = storage.flag(state)?;
                        self.column_flags[at] = if state == self.default_block {
                            default_flag
                        } else if flags & FLAG_AIR != 0 {
                            0
                        } else if flags & FLAG_FLUID != 0 {
                            1
                        } else {
                            2
                        };
                    }
                }
                _ => self.column_flags[y..end].fill(0),
            }
            y = end;
        }
        if uses_biomes {
            let b = self.biomes.as_ref().ok_or(Error::Input)?;
            let mut indices = vec![0i32; count];
            let status = unsafe {
                crate::world::level::biome::fiddled_distance::surface_biomes(b.seed, x, z, min_y, count as i32, indices.as_mut_ptr())
            };
            if status != 0 {
                return Err(Error::Evaluation(status));
            }
            // NativeSurface.prepareBiomes: quart (base + column, baseY + index % qCount).
            let base_y = (min_y - 2) >> 2;
            let q_count = (((min_y + count as i32 - 3) >> 2) - base_y + 2) as usize;
            let (base_x, base_z) = ((x - 2) >> 2, (z - 2) >> 2);
            for (at, index) in indices.iter().enumerate() {
                let index = *index as usize;
                let column = (index / q_count) as i32;
                let qx = base_x + (column >> 1) - b.qx;
                let qy = base_y + (index % q_count) as i32 - b.qy;
                let qz = base_z + (column & 1) - b.qz;
                if qx < 0 || qy < 0 || qz < 0 || qx as usize >= b.size_x || qy as usize >= b.size_y || qz as usize >= b.size_z {
                    return Err(Error::Input);
                }
                self.column_biomes[at] = b.ids[(qx as usize * b.size_z + qz as usize) * b.size_y + qy as usize];
            }
        }
        Ok(count)
    }

    /// Runs the column until it finishes (1) or needs a Java answer (2),
    /// committing completed blocks first, as `NativeSurface.column` does.
    pub(crate) fn run(&mut self, program: &[i32], frame: &mut [i32], cache: &mut [i32], secondary: f64) -> Result<i32, Error> {
        loop {
            // A natively answered secondary noise is the column's; Java's otherwise.
            let secondary = if self.inputs.secondary.is_null() { secondary } else { self.secondary };
            let status = unsafe {
                surface_step(program.as_ptr(), program.len() as i32, self.column_flags.as_ptr(), self.output.as_mut_ptr(),
                    self.column_biomes.as_ptr(), self.count as i32, frame.as_mut_ptr(), cache.as_mut_ptr(), secondary)
            };
            if status < 0 {
                return Err(Error::Evaluation(status));
            }
            // Commit only completed blocks: a request sees prior writes, never
            // the current rule's undecided result.
            let boundary = (frame[Y_INDEX] + 1).max(0) as usize;
            for y in (boundary..self.committed).rev() {
                if self.output[y] >= 0 {
                    let state = self.output[y];
                    let min_y = self.storage().min_y();
                    self.set_block(self.x, min_y + y as i32, self.z, state)?;
                }
            }
            self.committed = boundary;
            match status {
                1 => return Ok(1),
                2 => {
                    if !self.answer(frame)? {
                        return Ok(2);
                    }
                }
                _ => continue,
            }
        }
    }

    /// Answers the evaluator's request natively when its inputs are native;
    /// false leaves it to Java.
    fn answer(&mut self, frame: &mut [i32]) -> Result<bool, Error> {
        let (x, z) = (self.x, self.z);
        let y = frame[MIN_Y].wrapping_add(frame[Y_INDEX]);
        let request = frame[REQUEST];
        let condition = |value: bool, frame: &mut [i32]| {
            frame[ANSWER] = value as i32;
            frame[ANSWER_READY] = 1;
        };
        match request {
            -1 if !self.inputs.band.is_null() => {
                // SurfaceSystem.bandOffset: (int)Math.round(noise * 4.0).
                let noise = unsafe { noise_eval(self.inputs.band, x as f64, 0.0, z as f64, 0.0, 0.0, 0) };
                frame[BAND_OFFSET] = band_offset(noise);
                frame[BAND_OFFSET_READY] = 1;
            }
            -2 if !self.inputs.secondary.is_null() => {
                self.secondary = unsafe { noise_eval(self.inputs.secondary, x as f64, 0.0, z as f64, 0.0, 0.0, 0) };
                frame[SECONDARY_READY] = 1;
            }
            -3 if !self.inputs.levels.is_null() => {
                frame[MIN_SURFACE] = self.min_surface_level(frame[SURFACE_DEPTH])?;
                frame[MIN_SURFACE_READY] = 1;
            }
            slot if slot >= 0 => match self.slots.get(slot as usize).copied().unwrap_or(Slot::Java) {
                Slot::Java => return Ok(false),
                Slot::Steep => {
                    if self.steep_answer < 0 {
                        self.steep_answer = self.steep() as i8;
                    }
                    condition(self.steep_answer != 0, frame);
                }
                Slot::Gradient { low, high, random } => {
                    // The evaluator asks only inside the band: map(y, low, high, 1, 0).
                    let (yd, lo, hi) = (y as f64, low as f64, high as f64);
                    let d = crate::world::level::levelgen::math::lerp((yd - lo) / (hi - lo), 1.0, 0.0);
                    condition(((random.at(x, y, z).next_float()) as f64) < d, frame);
                }
                Slot::Noise { state, min, max } => {
                    let value = unsafe { noise_eval(state, x as f64, 0.0, z as f64, 0.0, 0.0, 0) };
                    condition(value >= min && value <= max, frame);
                }
            },
            _ => return Ok(false),
        }
        Ok(true)
    }

    /// `SurfaceRules.Context.getMinSurfaceLevel`: the preliminary surface levels
    /// at the column's surface cell corners, interpolated, plus surface depth - 8.
    fn min_surface_level(&mut self, surface_depth: i32) -> Result<i32, Error> {
        let (i, j) = (self.x >> 4, self.z >> 4);
        let corners = match self.corners {
            Some((cell, corners)) if cell == (i, j) => corners,
            _ => {
                let levels = unsafe { &*self.inputs.levels };
                let xs = [i << 4, (i + 1) << 4, i << 4, (i + 1) << 4];
                let zs = [j << 4, j << 4, (j + 1) << 4, (j + 1) << 4];
                let mut corners = [0i32; 4];
                with_frame(levels, |frame| levels.surface_levels(frame, &xs, &zs, &mut corners)).map_err(|_| Error::Evaluation(-5))?;
                self.corners = Some(((i, j), corners));
                corners
            }
        };
        let fx = ((self.x & 15) as f32 / 16.0f32) as f64;
        let fz = ((self.z & 15) as f32 / 16.0f32) as f64;
        let level = lerp2(fx, fz, corners[0] as f64, corners[1] as f64, corners[2] as f64, corners[3] as f64);
        // Mth.floor, then + surfaceDepth - 8.
        let k = level as i32;
        let k = if level < k as f64 { k.wrapping_sub(1) } else { k };
        Ok(k.wrapping_add(surface_depth).wrapping_sub(8))
    }

    /// `SurfaceRules.Context.SteepMaterialCondition.compute` on the storage's heightmap.
    fn steep(&self) -> bool {
        let (i, j) = (self.x & 15, self.z & 15);
        let height = |x: i32, z: i32| self.storage().height(x, z);
        let (k, l) = ((j - 1).max(0), (j + 1).min(15));
        let (m, n) = (height(i, k), height(i, l));
        if n >= m + 4 {
            return true;
        }
        let (o, p) = ((i - 1).max(0), (i + 1).min(15));
        height(o, j) >= height(p, j) + 4
    }
}

#[cfg(test)]
mod tests {
    use super::band_offset;

    #[test]
    fn band_offset_rounds_halves_like_java() {
        // Java's Math.round takes halves up: -2.5 -> -2, 2.5 -> 3.
        assert_eq!(band_offset(-0.625), -2);
        assert_eq!(band_offset(0.625), 3);
        assert_eq!(band_offset(f64::NAN), 0);
    }
}

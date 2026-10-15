//! `SurfaceSystem.buildOwnedSurface`'s column loop in Rust: for each column
//! (X outer, Z inner) the height, the column biome, the eroded badlands
//! extension, `Context.updateXZ`'s surface depth, the rule column and the
//! frozen ocean extension, in Java's order. The pass yields to Java only for
//! answers Java owns: a rule request it cannot answer (status 2, as
//! `SurfaceChunk::run`) and the frozen ocean's `shouldMeltFrozenOceanIcebergSlightly`
//! temperature (status 3). Nothing is borrowed across those yields: Java
//! answers into the frame or the state words and calls the pass again.
use super::chunk::{Error, SurfaceChunk};
use super::frame::*;
use crate::world::level::levelgen::noise_fill::FLAG_AIR;
use crate::world::level::levelgen::random::Positional;
use crate::world::level::levelgen::synth::{noise_eval, State};

/// The state words shared with Java on every call.
pub(crate) const STATE_WORDS: usize = 8;
/// Column block X and Z (a temperature request's position X and Z).
pub(crate) const STATE_X: usize = 0;
pub(crate) const STATE_Z: usize = 1;
/// The column's surface depth (`Context.surfaceDepth`).
pub(crate) const STATE_DEPTH: usize = 2;
/// `Context.updateXZ` calls so far, including the current column's.
pub(crate) const STATE_XZ_UPDATES: usize = 3;
/// The current column's `updateBase`: `lastUpdateY` after its `updateXZ`,
/// relative to its value when the pass began.
pub(crate) const STATE_Y_BASE: usize = 4;
/// 1 on the first rule request of a column (Java resynchronizes its context).
pub(crate) const STATE_NEW_COLUMN: usize = 5;
/// A temperature request's biome id out; Java's answer (0 or 1) in.
pub(crate) const STATE_TEMPERATURE: usize = 6;
/// `lastUpdateY` relative to the pass start once the pass completes.
pub(crate) const STATE_Y_FINAL: usize = 7;

/// The surface system's inputs the pass needs beyond the stage's.
pub(crate) struct PassInputs {
    /// `surfaceNoise`.
    pub surface: *const State,
    /// `noiseRandom`: surface depth and the frozen ocean extension.
    pub random: Positional,
    /// `badlandsSurfaceNoise`, `badlandsPillarNoise`, `badlandsPillarRoofNoise`.
    pub badlands: [*const State; 3],
    /// `icebergSurfaceNoise`, `icebergPillarNoise`, `icebergPillarRoofNoise`.
    pub iceberg: [*const State; 3],
    pub sea_level: i32,
    /// buildSurface's `bl`: the column biome is read at Y 0, not above the surface.
    pub biome_at_zero: bool,
    /// Registry ids of eroded badlands, frozen ocean and deep frozen ocean (-1: absent).
    pub eroded_badlands: i32,
    pub frozen_ocean: i32,
    pub deep_frozen_ocean: i32,
    /// Sorted state ids of the default block's block (`is(defaultBlock.getBlock())`).
    pub default_states: Vec<i32>,
    /// Sorted state ids of `Blocks.WATER` (`is(Blocks.WATER)`).
    pub water_states: Vec<i32>,
    pub default_block: i32,
    pub snow_block: i32,
    pub packed_ice: i32,
    /// `DimensionType.WAY_BELOW_MIN_Y`, as Java passes it.
    pub way_below_min_y: i32,
}

enum Phase {
    /// The next column starts.
    Start,
    /// The rule column runs; `base` is its `updateBase`.
    Rules { base: i64 },
    /// The frozen ocean extension waits for Java's temperature answer.
    Melt { min_surface: i32, top: i32, e: f64 },
    Done,
}

pub(crate) struct Pass {
    /// The stage, which Java keeps alive while the pass runs.
    chunk: *mut SurfaceChunk,
    inputs: PassInputs,
    uses_biomes: bool,
    min_x: i32,
    min_z: i32,
    column: usize,
    phase: Phase,
    /// This column's values.
    x: i32,
    z: i32,
    o: i32,
    biome: i32,
    depth: i32,
    xz_updates: i64,
    y_updates: i64,
    new_column: bool,
    /// Temperature requests answered (counted per chunk for verification).
    pub(crate) temperature_calls: u32,
}

/// Java's `Math.min(double, double)`: NaN when either is NaN.
fn java_min(a: f64, b: f64) -> f64 {
    if a.is_nan() || b.is_nan() { f64::NAN } else if a <= b { a } else { b }
}

/// `Mth.floor(double)`.
fn floor(value: f64) -> i32 {
    let i = value as i32;
    if value < i as f64 { i.wrapping_sub(1) } else { i }
}

fn noise(state: *const State, x: f64, z: f64) -> f64 {
    unsafe { noise_eval(state, x, 0.0, z, 0.0, 0.0, 0) }
}

impl Pass {
    /// # Safety
    /// `chunk` is live and used only through this pass while it runs; every
    /// noise state in `inputs` stays live and unchanged.
    pub(crate) unsafe fn new(chunk: *mut SurfaceChunk, inputs: PassInputs, uses_biomes: bool, min_x: i32, min_z: i32) -> Result<Self, Error> {
        if chunk.is_null() || inputs.surface.is_null() || inputs.badlands.iter().chain(&inputs.iceberg).any(|s| s.is_null()) {
            return Err(Error::Input);
        }
        if !unsafe { &*chunk }.has_levels() {
            return Err(Error::Input);
        }
        Ok(Self {
            chunk,
            inputs,
            uses_biomes,
            min_x,
            min_z,
            column: 0,
            phase: Phase::Start,
            x: 0,
            z: 0,
            o: 0,
            biome: -1,
            depth: 0,
            xz_updates: 0,
            y_updates: 0,
            new_column: false,
            temperature_calls: 0,
        })
    }

    fn chunk(&mut self) -> &mut SurfaceChunk {
        unsafe { &mut *self.chunk }
    }

    fn is_default(&self, state: i32) -> bool {
        self.inputs.default_states.binary_search(&state).is_ok()
    }

    fn is_water(&self, state: i32) -> bool {
        self.inputs.water_states.binary_search(&state).is_ok()
    }

    fn publish(&self, state: &mut [i64]) {
        state[STATE_X] = self.x as i64;
        state[STATE_Z] = self.z as i64;
        state[STATE_DEPTH] = self.depth as i64;
        state[STATE_XZ_UPDATES] = self.xz_updates;
        state[STATE_NEW_COLUMN] = self.new_column as i64;
        state[STATE_Y_FINAL] = self.y_updates;
    }

    /// Runs until the pass completes (0), a rule request needs Java (2) or the
    /// frozen ocean extension needs Java's temperature answer (3).
    pub(crate) fn run(&mut self, program: &[i32], frame: &mut [i32], cache: &mut [i32], secondary: f64, state: &mut [i64]) -> Result<i32, Error> {
        loop {
            match self.phase {
                Phase::Done => {
                    self.publish(state);
                    return Ok(0);
                }
                Phase::Start => {
                    if self.column == 256 {
                        self.phase = Phase::Done;
                        continue;
                    }
                    let (k, l) = ((self.column / 16) as i32, (self.column % 16) as i32);
                    self.x = self.min_x + k;
                    self.z = self.min_z + l;
                    let (x, z) = (self.x, self.z);
                    self.o = self.chunk().height(x, z) + 1;
                    let y = if self.inputs.biome_at_zero { 0 } else { self.o };
                    self.biome = self.chunk().biome_at(x, y, z)?;
                    if self.biome >= 0 && self.biome == self.inputs.eroded_badlands {
                        self.eroded_badlands()?;
                    }
                    // Context.updateXZ.
                    self.xz_updates += 1;
                    self.y_updates += 1;
                    self.depth = self.surface_depth(x, z);
                    let base = self.y_updates;
                    state[STATE_Y_BASE] = base;
                    let top = self.chunk().height(x, z) + 1;
                    let uses_biomes = self.uses_biomes;
                    let count = self.chunk().begin(x, z, top, uses_biomes)?;
                    if count == 0 {
                        self.frozen_ocean()?;
                        if matches!(self.phase, Phase::Melt { .. }) {
                            return self.request_temperature(state);
                        }
                        continue;
                    }
                    // NativeSurface.reset.
                    frame.fill(0);
                    cache.fill(-1);
                    frame[Y_INDEX] = count as i32 - 1;
                    frame[WATER_HEIGHT] = i32::MIN;
                    frame[STONE_BOTTOM] = i32::MAX;
                    frame[SURFACE_DEPTH] = self.depth;
                    frame[MIN_Y] = self.chunk().min_y();
                    frame[BELOW_WORLD_SENTINEL] = self.inputs.way_below_min_y;
                    self.new_column = true;
                    self.phase = Phase::Rules { base };
                }
                Phase::Rules { base } => {
                    match self.chunk().run(program, frame, cache, secondary)? {
                        1 => {
                            self.y_updates = base + frame[CONTEXT_UPDATES] as i64;
                            self.frozen_ocean()?;
                            if matches!(self.phase, Phase::Melt { .. }) {
                                return self.request_temperature(state);
                            }
                        }
                        2 => {
                            state[STATE_Y_BASE] = base;
                            self.publish(state);
                            self.new_column = false;
                            return Ok(2);
                        }
                        status => return Err(Error::Evaluation(status)),
                    }
                }
                Phase::Melt { min_surface, top, e } => {
                    let melt = match state[STATE_TEMPERATURE] {
                        0 => false,
                        1 => true,
                        _ => return Err(Error::Input),
                    };
                    self.place_iceberg(min_surface, top, e, melt)?;
                    self.next_column();
                }
            }
        }
    }

    fn next_column(&mut self) {
        self.column += 1;
        self.phase = Phase::Start;
    }

    fn request_temperature(&mut self, state: &mut [i64]) -> Result<i32, Error> {
        self.publish(state);
        state[STATE_TEMPERATURE] = self.biome as i64;
        self.temperature_calls += 1;
        Ok(3)
    }

    /// `SurfaceSystem.getSurfaceDepth`: `(int)(noise * 2.75 + 3.0 + random * 0.25)`.
    fn surface_depth(&self, x: i32, z: i32) -> i32 {
        let d = noise(self.inputs.surface, x as f64, z as f64);
        (d * 2.75 + 3.0 + self.inputs.random.at(x, 0, z).next_double() * 0.25) as i32
    }

    /// `SurfaceSystem.erodedBadlandsExtension`.
    fn eroded_badlands(&mut self) -> Result<(), Error> {
        let (i, j, k) = (self.x, self.z, self.o);
        let [surface, pillar, roof] = self.inputs.badlands;
        let e = java_min((noise(surface, i as f64, j as f64) * 8.25).abs(), noise(pillar, i as f64 * 0.2, j as f64 * 0.2) * 15.0);
        if e <= 0.0 {
            return Ok(());
        }
        let h = (noise(roof, i as f64 * 0.75, j as f64 * 0.75) * 1.5).abs();
        let l = 64.0 + java_min(e * e * 2.5, (h * 50.0).ceil() + 24.0);
        let m = floor(l);
        if k > m {
            return Ok(());
        }
        let min_y = self.chunk().min_y();
        let mut n = m;
        while n >= min_y {
            let (state, _) = self.chunk().block(i, n, j)?;
            if state >= 0 && self.is_default(state) {
                break;
            }
            if state >= 0 && self.is_water(state) {
                return Ok(());
            }
            n -= 1;
        }
        let default = self.inputs.default_block;
        let mut n = m;
        while n >= min_y && self.chunk().block(i, n, j)?.1 & FLAG_AIR != 0 {
            self.chunk().set_column_block(i, n, j, default)?;
            n -= 1;
        }
        Ok(())
    }

    /// `SurfaceSystem.frozenOceanExtension` up to its temperature check; a
    /// column outside the frozen oceans, or below the noise cut, finishes here.
    fn frozen_ocean(&mut self) -> Result<(), Error> {
        let frozen = self.biome >= 0 && (self.biome == self.inputs.frozen_ocean || self.biome == self.inputs.deep_frozen_ocean);
        if !frozen {
            self.next_column();
            return Ok(());
        }
        let (x, z, depth) = (self.x, self.z, self.depth);
        // Context.getMinSurfaceLevel, evaluated where Java's argument is.
        let min_surface = self.chunk().min_surface(x, z, depth)?;
        let [surface, pillar, _] = self.inputs.iceberg;
        let e = java_min((noise(surface, x as f64, z as f64) * 8.25).abs(), noise(pillar, x as f64 * 1.28, z as f64 * 1.28) * 15.0);
        if e <= 1.8 {
            self.next_column();
            return Ok(());
        }
        self.phase = Phase::Melt { min_surface, top: self.o, e };
        Ok(())
    }

    /// The rest of `frozenOceanExtension` once Java answered the melt check.
    fn place_iceberg(&mut self, i: i32, l: i32, e: f64, melt: bool) -> Result<(), Error> {
        let (j, k) = (self.x, self.z);
        let sea = self.inputs.sea_level;
        let roof = self.inputs.iceberg[2];
        let h = (noise(roof, j as f64 * 1.17, k as f64 * 1.17) * 1.5).abs();
        let mut m = java_min(e * e * 1.2, (h * 40.0).ceil() + 14.0);
        if melt {
            m -= 2.0;
        }
        let n;
        if m > 2.0 {
            n = sea as f64 - m - 7.0;
            m += sea as f64;
        } else {
            m = 0.0;
            n = 0.0;
        }
        let o = m;
        let mut random = self.inputs.random.at(j, 0, k);
        let p = 2 + random.next_int_bound(4);
        let q = sea + 18 + random.next_int_bound(10);
        let mut r = 0;
        let mut s = l.max((m as i32).wrapping_add(1));
        while s >= i {
            let (state, flags) = self.chunk().block(j, s, k)?;
            let place = (flags & FLAG_AIR != 0 && s < o as i32 && random.next_double() > 0.01)
                || (state >= 0 && self.is_water(state) && s > n as i32 && s < sea && n != 0.0 && random.next_double() > 0.15);
            if place {
                if r <= p && s > q {
                    let snow = self.inputs.snow_block;
                    self.chunk().set_column_block(j, s, k, snow)?;
                    r += 1;
                } else {
                    let ice = self.inputs.packed_ice;
                    self.chunk().set_column_block(j, s, k, ice)?;
                }
            }
            s -= 1;
        }
        Ok(())
    }
}

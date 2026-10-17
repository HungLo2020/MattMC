//! `SurfaceSystem.topMaterial` for the carvers: the surface rule evaluated at
//! one block, as `NativeSurface.tryApply` evaluates it for a fresh
//! `SurfaceRules.Context` after `updateXZ` and `updateY(1, 1, water, x, y, z)`.
//! Every request is answered here except temperature, which Java answers at
//! the point Java's evaluation would (`Biome.getTemperature` keeps a per-thread
//! cache keyed by position alone, so the call itself is part of the result). A
//! rule needing Java otherwise is rejected when bound.
//!
//! The context's biome is looked up only when a biome or temperature condition
//! first needs it, at most once per call, as Java's memoized supplier does: the
//! lookup's climate search moves the carver's previous leaf.
use super::chunk::{gradient, min_surface_level, noise_threshold, steep, Inputs, Slot};
use super::evaluator::surface_step;
use super::frame::*;
use super::program::surface_validate;
use crate::world::level::levelgen::proto_chunk::ProtoStorage;
use crate::world::level::levelgen::random::Positional;
use crate::world::level::levelgen::router::Program;
use crate::world::level::levelgen::synth::{noise_eval, State};

/// Requests answered, by kind: band offset, secondary noise, minimum surface
/// level, steep, vertical gradient, noise threshold, biome, temperature.
pub(crate) static REQUESTS: [std::sync::atomic::AtomicU64; 8] = [const { std::sync::atomic::AtomicU64::new(0) }; 8];

fn count(kind: usize) {
    REQUESTS[kind].fetch_add(1, std::sync::atomic::Ordering::Relaxed);
}

/// Condition caches up to this length (`program[0] / 8`) live on the stack.
const STACK_CACHE: usize = 2048;

#[derive(Debug, PartialEq)]
pub(crate) enum Error {
    /// The evaluator rejected the program or frame.
    Evaluation(i32),
    /// A request the binding cannot answer.
    Unanswered(i32),
    /// The biome lookup failed.
    Biome,
    /// Java's temperature answer failed.
    Temperature,
}

impl From<super::chunk::Error> for Error {
    fn from(error: super::chunk::Error) -> Self {
        match error {
            super::chunk::Error::Evaluation(status) => Error::Evaluation(status),
            _ => Error::Evaluation(-1),
        }
    }
}

pub(crate) struct ScalarSurface {
    program: Vec<i32>,
    slots: Vec<Slot>,
    /// Sorted registry ids of each biome slot's range.
    biome_ids: Vec<i32>,
    inputs: Inputs,
    /// `SurfaceSystem.surfaceNoise` and `noiseRandom`, for `getSurfaceDepth`.
    surface_noise: *const State,
    depth_random: Positional,
    /// `DimensionType.WAY_BELOW_MIN_Y`, as Java passes it.
    way_below_min_y: i32,
    /// The evaluator's per-call condition cache length (`program[0] / 8`).
    cache_len: usize,
}

impl ScalarSurface {
    /// # Safety
    /// The noise states and the preliminary surface program stay alive and
    /// unchanged while this binding is used.
    pub(crate) unsafe fn new(program: Vec<i32>, slots: Vec<Slot>, biome_ids: Vec<i32>, inputs: Inputs, surface_noise: *const State,
        depth_random: Positional, way_below_min_y: i32) -> Option<Self> {
        if program.len() < 16 || unsafe { surface_validate(program.as_ptr(), program.len() as i32) } != 0 {
            return None;
        }
        if inputs.band.is_null() || inputs.secondary.is_null() || inputs.levels.is_null() || surface_noise.is_null() {
            return None;
        }
        for slot in &slots {
            match *slot {
                Slot::Java => return None,
                Slot::Noise { state, .. } if state.is_null() => return None,
                Slot::Biome { start, len } if start as usize + len as usize > biome_ids.len() => return None,
                _ => {}
            }
        }
        let cache_len = program[0] as usize / 8;
        Some(Self { program, slots, biome_ids, inputs, surface_noise, depth_random, way_below_min_y, cache_len })
    }

    /// `SurfaceSystem.getSurfaceDepth`: `(int)(noise * 2.75 + 3.0 + random * 0.25)`.
    fn surface_depth(&self, x: i32, z: i32) -> i32 {
        let d = unsafe { noise_eval(self.surface_noise, x as f64, 0.0, z as f64, 0.0, 0.0, 0) };
        (d * 2.75 + 3.0 + self.depth_random.at(x, 0, z).next_double() * 0.25) as i32
    }

    /// `topMaterial` at (x, y, z): a state id, or -1 for none. `fluid` is whether
    /// the carved block above holds a fluid (the context's water height is then
    /// y + 1). `biome` looks up the context's biome as a registry id;
    /// `cold` is Java's `coldEnoughToSnow` of a biome at a position.
    /// The binding is immutable: carving threads share it.
    pub(crate) fn top_material(&self, storage: &ProtoStorage, x: i32, y: i32, z: i32, fluid: bool,
        biome: &mut dyn FnMut(i32, i32, i32) -> Result<i32, ()>, cold: &mut dyn FnMut(i32, [i32; 3]) -> Result<bool, ()>) -> Result<i32, Error> {
        // NativeSurface.tryApply after reset(y, 1) on a fresh context.
        let mut frame = [0i32; FRAME_WORDS];
        // The evaluator's condition cache, on the stack for ordinary rules.
        let mut stack = [-1i32; STACK_CACHE];
        let mut heap = Vec::new();
        let cache: &mut [i32] = if self.cache_len <= STACK_CACHE {
            &mut stack[..self.cache_len.max(1)]
        } else {
            heap.resize(self.cache_len, -1);
            &mut heap
        };
        frame[Y_INDEX] = 0;
        frame[WATER_HEIGHT] = if fluid { y.wrapping_add(1) } else { i32::MIN };
        frame[STONE_BOTTOM] = i32::MAX;
        frame[SURFACE_DEPTH] = self.surface_depth(x, z);
        frame[MIN_Y] = y;
        frame[BELOW_WORLD_SENTINEL] = self.way_below_min_y;
        frame[STONE_ABOVE] = 1;
        frame[STONE_BELOW] = 1;
        frame[PROGRAM_COUNTER] = 8;
        frame[PHASE] = 1;
        let (flags, biomes) = ([0i32], [0i32]);
        let mut output = [-1i32];
        let (mut secondary, mut corners, mut steep_answer, mut context_biome) = (0.0, None, None, None);
        loop {
            let status = unsafe {
                surface_step(self.program.as_ptr(), self.program.len() as i32, flags.as_ptr(), output.as_mut_ptr(), biomes.as_ptr(), 1,
                    frame.as_mut_ptr(), cache.as_mut_ptr(), secondary)
            };
            match status {
                1 => return Ok(output[0].max(-1)),
                2 => {}
                0 => continue,
                _ => return Err(Error::Evaluation(status)),
            }
            let request = frame[REQUEST];
            let mut condition = |value: bool| {
                frame[ANSWER] = value as i32;
                frame[ANSWER_READY] = 1;
            };
            match request {
                -1 => {
                    count(0);
                    let noise = unsafe { noise_eval(self.inputs.band, x as f64, 0.0, z as f64, 0.0, 0.0, 0) };
                    frame[BAND_OFFSET] = super::chunk::band_offset(noise);
                    frame[BAND_OFFSET_READY] = 1;
                }
                -2 => {
                    count(1);
                    secondary = unsafe { noise_eval(self.inputs.secondary, x as f64, 0.0, z as f64, 0.0, 0.0, 0) };
                    frame[SECONDARY_READY] = 1;
                }
                -3 => {
                    count(2);
                    let levels: &Program = unsafe { &*self.inputs.levels };
                    frame[MIN_SURFACE] = min_surface_level(levels, &mut corners, x, z, frame[SURFACE_DEPTH])?;
                    frame[MIN_SURFACE_READY] = 1;
                }
                slot if slot >= 0 => match self.slots.get(slot as usize).copied() {
                    // Java's steep is one lazy condition per context.
                    Some(Slot::Steep) => {
                        count(3);
                        condition(*steep_answer.get_or_insert_with(|| steep(storage, x, z)))
                    }
                    Some(Slot::Gradient { low, high, random }) => {
                        count(4);
                        condition(gradient(low, high, random, x, y, z))
                    }
                    Some(Slot::Noise { state, min, max }) => {
                        count(5);
                        condition(noise_threshold(state, min, max, x, z))
                    }
                    Some(Slot::Biome { start, len }) => {
                        count(6);
                        let id = Self::context_biome(&mut context_biome, biome, x, y, z)?;
                        let ids = &self.biome_ids[start as usize..(start + len) as usize];
                        condition(id >= 0 && ids.binary_search(&id).is_ok());
                    }
                    Some(Slot::Temperature) => {
                        count(7);
                        let id = Self::context_biome(&mut context_biome, biome, x, y, z)?;
                        condition(cold(id, [x, y, z]).map_err(|_| Error::Temperature)?);
                    }
                    _ => return Err(Error::Unanswered(request)),
                },
                _ => return Err(Error::Unanswered(request)),
            }
        }
    }

    fn context_biome(memo: &mut Option<i32>, biome: &mut dyn FnMut(i32, i32, i32) -> Result<i32, ()>, x: i32, y: i32, z: i32) -> Result<i32, Error> {
        if let Some(id) = *memo {
            return Ok(id);
        }
        let id = biome(x, y, z).map_err(|_| Error::Biome)?;
        *memo = Some(id);
        Ok(id)
    }
}

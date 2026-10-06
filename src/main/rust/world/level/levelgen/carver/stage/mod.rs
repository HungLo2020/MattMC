//! Rust-owned CARVERS stage for one chunk: `applyCarvers`' loop over the
//! carvers of every chunk within 8 (Java supplies each one's carver list from
//! its carver biome), each carver's seeding, start check and configuration
//! sampling, the cave, Nether cave and canyon carvers with their own Legacy
//! randomness and the `Mth` sine table, the ellipsoid scans with their skip
//! checks, `carveBlock` (or the Nether carver's), over the chunk's
//! [`ProtoStorage`], its carving mask and its aquifer. Java answers only the
//! rare top-material lookup, through a callback.
mod ffi;
#[cfg(test)]
mod tests;

use crate::world::level::levelgen::aquifer::substance::{picked, Substance};
use crate::world::level::levelgen::noise_fill::FLAG_FLUID;
use crate::world::level::levelgen::proto_chunk::{self, ProtoStorage};
use crate::world::level::levelgen::random::Legacy;

/// Per-block bits: grass block or mycelium, dirt; configuration `k`'s
/// replaceable set is bit `REPLACEABLE + k`.
pub(crate) const GRASS_OR_MYCELIUM: u32 = 1;
pub(crate) const DIRT: u32 = 2;
pub(crate) const REPLACEABLE: u32 = 2;

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum Kind {
    Cave,
    Nether,
    Canyon,
}

#[derive(Clone, Copy, Debug, PartialEq)]
pub(crate) enum FloatProvider {
    Constant(f32),
    /// `UniformFloat`: min inclusive, max exclusive.
    Uniform(f32, f32),
    /// `TrapezoidFloat`: min, max, plateau.
    Trapezoid(f32, f32, f32),
}

impl FloatProvider {
    fn sample(self, random: &mut Legacy) -> f32 {
        match self {
            FloatProvider::Constant(value) => value,
            FloatProvider::Uniform(min, max) => random.next_float() * (max - min) + min,
            FloatProvider::Trapezoid(min, max, plateau) => {
                let f = max - min;
                let g = (f - plateau) / 2.0f32;
                let h = f - g;
                min + random.next_float() * h + random.next_float() * g
            }
        }
    }
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct CanyonShape {
    pub vertical_rotation: FloatProvider,
    pub thickness: FloatProvider,
    pub distance_factor: FloatProvider,
    pub width_smoothness: i32,
    pub vertical_default: f32,
    pub vertical_center: f32,
    pub horizontal_radius: FloatProvider,
}

#[derive(Clone, Copy, Debug)]
pub(crate) struct CaveShape {
    pub horizontal_radius: FloatProvider,
    pub vertical_radius: FloatProvider,
    pub floor_level: FloatProvider,
}

/// A configured carver with its anchors resolved for the chunk.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Config {
    pub kind: Kind,
    pub probability: f32,
    /// `UniformHeight` bounds (inclusive, min <= max).
    pub y_min: i32,
    pub y_max: i32,
    pub y_scale: FloatProvider,
    pub lava_level: i32,
    pub bit: u32,
    pub cave: Option<CaveShape>,
    pub canyon: Option<CanyonShape>,
}

#[derive(Clone, Copy)]
enum Skip<'a> {
    Cave(f64),
    Canyon(&'a [f32]),
}

/// `CarvingContext.topMaterial` for the block below a carved surface block:
/// (x, y, z, carved state holds a fluid, current heightmaps) to a state id or
/// -1 for none; `Err` when Java failed.
pub(crate) type TopMaterial<'a> = &'a mut dyn FnMut(i32, i32, i32, bool, &ProtoStorage) -> Result<i32, i32>;

#[derive(Debug, PartialEq)]
pub(crate) enum Error {
    Storage,
    Aquifer(i32),
    TopMaterial(i32),
}

impl From<proto_chunk::Error> for Error {
    fn from(_: proto_chunk::Error) -> Self {
        Error::Storage
    }
}

/// The chunk's aquifer: a noise-based one deciding in Rust, or `Aquifer.Disabled`
/// over its fluid picker (policy as `Substance::policy`), which never schedules.
pub(crate) enum ChunkAquifer<'a> {
    Noise(Substance<'a>),
    Disabled([i32; 8]),
}

impl ChunkAquifer<'_> {
    fn compute(&mut self, x: i32, y: i32, z: i32) -> Result<(i32, bool), i32> {
        match self {
            ChunkAquifer::Noise(substance) => substance.compute(x, y, z),
            ChunkAquifer::Disabled(policy) => Ok((picked(policy, y), false)),
        }
    }
}

/// Block ids the stage writes or compares.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Ids {
    pub air: i32,
    pub cave_air: i32,
    pub lava: i32,
}

pub(crate) struct Stage<'a> {
    pub storage: &'a mut ProtoStorage,
    /// The chunk's `CarvingMask` words: bit (x | z << 4 | (y - minY) << 8).
    pub mask: &'a mut [u64],
    pub chunk_x: i32,
    pub chunk_z: i32,
    pub min_gen_y: i32,
    pub gen_depth: i32,
    pub upgrading: bool,
    pub sin: &'a [f32],
    /// Each block state's block registry id.
    pub block_of: &'a [u32],
    /// Per block id: grass or mycelium, dirt, and each configuration's replaceable bit.
    pub blocks: &'a [u32],
    pub ids: Ids,
    pub configs: &'a [Config],
    pub aquifer: Option<ChunkAquifer<'a>>,
    /// The aquifer's `shouldScheduleFluidUpdate`, carried across calls.
    pub schedule: bool,
    pub top: TopMaterial<'a>,
}

const PI_F: f32 = std::f64::consts::PI as f32;
const HALF_PI_F: f32 = (std::f64::consts::PI / 2.0) as f32;
const TAU_F: f32 = (std::f64::consts::PI * 2.0) as f32;

/// `Mth.floor(double)`: Java's saturating cast, then one down below it.
fn floor(value: f64) -> i32 {
    let i = value as i32;
    if value < i as f64 { i.wrapping_sub(1) } else { i }
}

impl Stage<'_> {
    fn sin(&self, value: f32) -> f32 {
        self.sin[((value * 10430.378f32) as i32 & 65535) as usize]
    }

    fn cos(&self, value: f32) -> f32 {
        self.sin[((value * 10430.378f32 + 16384.0f32) as i32 & 65535) as usize]
    }

    fn block(&self, state: i32) -> u32 {
        let Some(&block) = self.block_of.get(state as usize) else { return 0 };
        self.blocks.get(block as usize).copied().unwrap_or(0)
    }

    /// `ProtoChunk.getBlockState` inside the chunk's sections.
    fn get(&self, x: i32, y: i32, z: i32) -> i32 {
        match self.storage.get(x, y, z) {
            -2 => self.ids.air,
            state => state,
        }
    }

    fn mask_index(&self, x: i32, y: i32, z: i32) -> usize {
        ((x & 15) | ((z & 15) << 4) | ((y - self.storage.min_y()) << 8)) as usize
    }

    /// `applyCarvers`' loop: for each chunk within 8 (in Java's order) and each
    /// of its carvers, `setLargeFeatureSeed(seed + index, x, z)`, the start
    /// check, then the carver.
    pub(crate) fn run(&mut self, seed: i64, neighbours: &[(i32, i32, Vec<usize>)]) -> Result<(), Error> {
        let mut random = Legacy::new(0);
        for (x, z, carvers) in neighbours {
            for (index, &config) in carvers.iter().enumerate() {
                random.set_large_feature_seed(seed.wrapping_add(index as i64), *x, *z);
                let c = self.configs[config];
                if random.next_float() <= c.probability {
                    match c.kind {
                        Kind::Canyon => self.canyon_carve(config, &mut random, *x, *z)?,
                        _ => self.cave_carve(config, &mut random, *x, *z)?,
                    }
                }
            }
        }
        Ok(())
    }

    /// `UniformHeight.sample`: `randomBetweenInclusive(min, max)`.
    fn height(config: &Config, random: &mut Legacy) -> i32 {
        random.next_int_bound(config.y_max.wrapping_sub(config.y_min).wrapping_add(1)).wrapping_add(config.y_min)
    }

    /// `CaveWorldCarver.carve`, with the Nether carver's bound, thickness and Y scale.
    fn cave_carve(&mut self, config: usize, random: &mut Legacy, chunk_x: i32, chunk_z: i32) -> Result<(), Error> {
        let c = self.configs[config];
        let shape = c.cave.ok_or(Error::Storage)?;
        let nether = c.kind == Kind::Nether;
        let i = 112;
        let bound = if nether { 10 } else { 15 };
        // nextInt(nextInt(nextInt(bound) + 1) + 1), innermost first.
        let inner = random.next_int_bound(bound) + 1;
        let middle = random.next_int_bound(inner) + 1;
        let j = random.next_int_bound(middle);
        for _ in 0..j {
            let d = chunk_x.wrapping_shl(4).wrapping_add(random.next_int_bound(16)) as f64;
            let e = Self::height(&c, random) as f64;
            let f = chunk_z.wrapping_shl(4).wrapping_add(random.next_int_bound(16)) as f64;
            let g = shape.horizontal_radius.sample(random) as f64;
            let h = shape.vertical_radius.sample(random) as f64;
            let l = shape.floor_level.sample(random) as f64;
            let mut m = 1;
            if random.next_int_bound(4) == 0 {
                let n = c.y_scale.sample(random) as f64;
                let o = 1.0f32 + random.next_float() * 6.0f32;
                // createRoom: radius 1.5 + Mth.sin(PI / 2) * o.
                let radius = 1.5 + (self.sin(HALF_PI_F) * o) as f64;
                self.ellipsoid(config, d + 1.0, e, f, radius, radius * n, Skip::Cave(l))?;
                m += random.next_int_bound(4);
            }
            for _ in 0..m {
                let q = random.next_float() * TAU_F;
                let o = (random.next_float() - 0.5f32) / 4.0f32;
                let r = if nether {
                    (random.next_float() * 2.0f32 + random.next_float()) * 2.0f32
                } else {
                    let mut thickness = random.next_float() * 2.0f32 + random.next_float();
                    if random.next_int_bound(10) == 0 {
                        thickness *= random.next_float() * random.next_float() * 3.0f32 + 1.0f32;
                    }
                    thickness
                };
                let s = i - random.next_int_bound(i / 4);
                let seed = random.next_long();
                self.tunnel(config, seed, d, e, f, g, h, r, q, o, 0, s, if nether { 5.0 } else { 1.0 }, l)?;
            }
        }
        Ok(())
    }

    /// `CanyonWorldCarver.carve`.
    fn canyon_carve(&mut self, config: usize, random: &mut Legacy, chunk_x: i32, chunk_z: i32) -> Result<(), Error> {
        let c = self.configs[config];
        let shape = c.canyon.ok_or(Error::Storage)?;
        let i = 112;
        let d = chunk_x.wrapping_shl(4).wrapping_add(random.next_int_bound(16)) as f64;
        let j = Self::height(&c, random);
        let e = chunk_z.wrapping_shl(4).wrapping_add(random.next_int_bound(16)) as f64;
        let f = random.next_float() * TAU_F;
        let g = shape.vertical_rotation.sample(random);
        let h = c.y_scale.sample(random) as f64;
        let k = shape.thickness.sample(random);
        let l = (i as f32 * shape.distance_factor.sample(random)) as i32;
        let seed = random.next_long();
        self.canyon(config, seed, d, j as f64, e, k, f, g, 0, l, h)
    }

    /// `WorldCarver.canReach`.
    fn can_reach(&self, x: f64, z: f64, i: i32, j: i32, thickness: f32) -> bool {
        let k = x - (self.chunk_x * 16 + 8) as f64;
        let l = z - (self.chunk_z * 16 + 8) as f64;
        let m = (j - i) as f64;
        let n = (thickness + 2.0f32 + 16.0f32) as f64;
        k * k + l * l - m * m <= n * n
    }

    /// `CaveWorldCarver.createTunnel`.
    #[allow(clippy::too_many_arguments)]
    fn tunnel(&mut self, config: usize, seed: i64, mut d: f64, mut e: f64, mut f: f64, g: f64, h: f64, i: f32, mut j: f32, mut k: f32, m: i32, n: i32,
        o: f64, floor_level: f64) -> Result<(), Error> {
        let mut random = Legacy::new(seed);
        let p = random.next_int_bound(n / 2) + n / 4;
        let bl = random.next_int_bound(6) == 0;
        let mut q = 0.0f32;
        let mut r = 0.0f32;
        for s in m..n {
            let t = 1.5 + (self.sin(PI_F * s as f32 / n as f32) * i) as f64;
            let u = t * o;
            let v = self.cos(k);
            d += (self.cos(j) * v) as f64;
            e += self.sin(k) as f64;
            f += (self.sin(j) * v) as f64;
            k *= if bl { 0.92f32 } else { 0.7f32 };
            k += r * 0.1f32;
            j += q * 0.1f32;
            r *= 0.9f32;
            q *= 0.75f32;
            r += (random.next_float() - random.next_float()) * random.next_float() * 2.0f32;
            q += (random.next_float() - random.next_float()) * random.next_float() * 4.0f32;
            if s == p && i > 1.0f32 {
                let seed = random.next_long();
                let thickness = random.next_float() * 0.5f32 + 0.5f32;
                self.tunnel(config, seed, d, e, f, g, h, thickness, j - HALF_PI_F, k / 3.0f32, s, n, 1.0, floor_level)?;
                let seed = random.next_long();
                let thickness = random.next_float() * 0.5f32 + 0.5f32;
                self.tunnel(config, seed, d, e, f, g, h, thickness, j + HALF_PI_F, k / 3.0f32, s, n, 1.0, floor_level)?;
                return Ok(());
            }
            if random.next_int_bound(4) != 0 {
                if !self.can_reach(d, f, s, n, i) {
                    return Ok(());
                }
                self.ellipsoid(config, d, e, f, t * g, u * h, Skip::Cave(floor_level))?;
            }
        }
        Ok(())
    }

    /// `CanyonWorldCarver.doCarve`.
    #[allow(clippy::too_many_arguments)]
    fn canyon(&mut self, config: usize, seed: i64, mut d: f64, mut e: f64, mut f: f64, g: f32, mut h: f32, mut i: f32, j: i32, k: i32, m: f64)
        -> Result<(), Error> {
        let shape = self.configs[config].canyon.ok_or(Error::Storage)?;
        let mut random = Legacy::new(seed);
        // initWidthFactors.
        let mut factors = vec![0.0f32; self.gen_depth.max(0) as usize];
        let mut factor = 1.0f32;
        for index in 0..factors.len() {
            if index == 0 || random.next_int_bound(shape.width_smoothness) == 0 {
                factor = 1.0f32 + random.next_float() * random.next_float();
            }
            factors[index] = factor * factor;
        }
        let mut n = 0.0f32;
        let mut o = 0.0f32;
        for p in j..k {
            let mut q = 1.5 + (self.sin(p as f32 * PI_F / k as f32) * g) as f64;
            let mut r = q * m;
            q *= shape.horizontal_radius.sample(&mut random) as f64;
            // updateVerticalRadius.
            let height = 1.0f32 - (0.5f32 - p as f32 / k as f32).abs() * 2.0f32;
            let vertical = shape.vertical_default + shape.vertical_center * height;
            r = vertical as f64 * r * (random.next_float() * (1.0f32 - 0.75f32) + 0.75f32) as f64;
            let s = self.cos(i);
            let t = self.sin(i);
            d += (self.cos(h) * s) as f64;
            e += t as f64;
            f += (self.sin(h) * s) as f64;
            i *= 0.7f32;
            i += o * 0.05f32;
            h += n * 0.05f32;
            o *= 0.8f32;
            n *= 0.5f32;
            o += (random.next_float() - random.next_float()) * random.next_float() * 2.0f32;
            n += (random.next_float() - random.next_float()) * random.next_float() * 4.0f32;
            if random.next_int_bound(4) != 0 {
                if !self.can_reach(d, f, p, k, g) {
                    return Ok(());
                }
                self.ellipsoid(config, d, e, f, q, r, Skip::Canyon(&factors))?;
            }
        }
        Ok(())
    }

    fn skip(&self, skip: Skip, d: f64, e: f64, f: f64, y: i32) -> bool {
        match skip {
            // CaveWorldCarver.shouldSkip.
            Skip::Cave(floor_level) => e <= floor_level || d * d + e * e + f * f >= 1.0,
            // CanyonWorldCarver.shouldSkip.
            Skip::Canyon(factors) => (d * d + f * f) * factors[(y - self.min_gen_y - 1) as usize] as f64 + e * e / 6.0 >= 1.0,
        }
    }

    /// `WorldCarver.carveEllipsoid`.
    #[allow(clippy::too_many_arguments)]
    fn ellipsoid(&mut self, config: usize, d: f64, e: f64, f: f64, g: f64, h: f64, skip: Skip) -> Result<bool, Error> {
        let (min_x, min_z) = (self.chunk_x * 16, self.chunk_z * 16);
        let k = 16.0 + g * 2.0;
        if (d - (min_x + 8) as f64).abs() > k || (f - (min_z + 8) as f64).abs() > k {
            return Ok(false);
        }
        let n = (floor(d - g) - min_x - 1).max(0);
        let o = (floor(d + g) - min_x).min(15);
        let p = (floor(e - h) - 1).max(self.min_gen_y + 1);
        let q = if self.upgrading { 0 } else { 7 };
        let r = (floor(e + h) + 1).min(self.min_gen_y + self.gen_depth - 1 - q);
        let s = (floor(f - g) - min_z - 1).max(0);
        let t = (floor(f + g) - min_z).min(15);
        let mut carved = false;
        for u in n..=o {
            let v = min_x + u;
            let w = (v as f64 + 0.5 - d) / g;
            for x in s..=t {
                let y = min_z + x;
                let z = (y as f64 + 0.5 - f) / g;
                if w * w + z * z >= 1.0 {
                    continue;
                }
                let mut surface = false;
                let mut aa = r;
                while aa > p {
                    let ab = (aa as f64 - 0.5 - e) / h;
                    if !self.skip(skip, w, ab, z, aa) {
                        let index = self.mask_index(u, aa, x);
                        if self.mask[index >> 6] & (1u64 << (index & 63)) == 0 {
                            self.mask[index >> 6] |= 1u64 << (index & 63);
                            carved |= match self.configs[config].kind {
                                Kind::Nether => self.carve_nether(config, v, aa, y)?,
                                _ => self.carve_block(config, v, aa, y, &mut surface)?,
                            };
                        }
                    }
                    aa -= 1;
                }
            }
        }
        Ok(carved)
    }

    /// `WorldCarver.carveBlock`.
    fn carve_block(&mut self, config: usize, x: i32, y: i32, z: i32, surface: &mut bool) -> Result<bool, Error> {
        let state = self.get(x, y, z);
        let block = self.block(state);
        if block & GRASS_OR_MYCELIUM != 0 {
            *surface = true;
        }
        if block & (1 << (REPLACEABLE + self.configs[config].bit)) == 0 {
            return Ok(false);
        }
        // getCarveState: lava at or below the lava level, else the aquifer.
        let carved = if y <= self.configs[config].lava_level {
            self.ids.lava
        } else {
            let aquifer = self.aquifer.as_mut().ok_or(Error::Aquifer(-1))?;
            let (state, schedule) = aquifer.compute(x, y, z).map_err(Error::Aquifer)?;
            self.schedule = schedule;
            if state < 0 {
                return Ok(false);
            }
            state
        };
        self.storage.set_block_state(x, y, z, carved)?;
        let fluid = self.storage.flag(carved)? & FLAG_FLUID != 0;
        if self.schedule && fluid {
            self.storage.mark(x, y, z);
        }
        if *surface && self.block(self.get(x, y - 1, z)) & DIRT != 0 {
            let top = (self.top)(x, y - 1, z, fluid, self.storage).map_err(Error::TopMaterial)?;
            if top >= 0 {
                self.storage.set_block_state(x, y - 1, z, top)?;
                if self.storage.flag(top)? & FLAG_FLUID != 0 {
                    self.storage.mark(x, y - 1, z);
                }
            }
        }
        Ok(true)
    }

    /// `NetherWorldCarver.carveBlock`: lava within 31 blocks of the bottom, else cave air.
    fn carve_nether(&mut self, config: usize, x: i32, y: i32, z: i32) -> Result<bool, Error> {
        let state = self.get(x, y, z);
        if self.block(state) & (1 << (REPLACEABLE + self.configs[config].bit)) == 0 {
            return Ok(false);
        }
        let carved = if y <= self.min_gen_y + 31 { self.ids.lava } else { self.ids.cave_air };
        self.storage.set_block_state(x, y, z, carved)?;
        Ok(true)
    }
}

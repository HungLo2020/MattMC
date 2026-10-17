//! `GeodeFeature`: distribution and crack points, the layered density fields
//! (`feature/geode/evaluate.rs`'s exact accumulation) over the level-seeded
//! `NormalNoise`, layer writes through `safeSetBlock`, crack fluid ticks and
//! inner placements.
use super::placement::Placement;
use super::program::{IntProvider, Reader};
use super::region::Result;
use super::tree::Provider;
use super::vegetation::DIRECTIONS;
use crate::world::level::levelgen::feature::geode::evaluate::{accumulate, Point};

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Geode {
    min_offset: i32,
    max_offset: i32,
    distribution: IntProvider,
    outer_wall: IntProvider,
    /// `outerWallDistance.getMaxValue()`.
    outer_wall_max: i32,
    point_offset: IntProvider,
    invalid_threshold: i32,
    /// Air or `invalidBlocks`.
    invalid: usize,
    /// Not `cannotReplace`: `Feature.isReplaceable`.
    replaceable: usize,
    filling: f64,
    inner_layer: f64,
    middle_layer: f64,
    outer_layer: f64,
    crack_chance: f64,
    base_crack_size: f64,
    crack_offset: i32,
    noise: usize,
    multiplier: f64,
    providers: [Provider; 5],
    alternate_chance: f64,
    potential_chance: f64,
    requires_alternate: bool,
    /// Per inner placement: its state facing each direction, not waterlogged then waterlogged.
    placements: Vec<[[u32; 2]; 6]>,
    /// `BuddingAmethystBlock.canClusterGrowAtState`.
    cluster: usize,
    /// States whose fluid is a source.
    source: usize,
    /// Fluid registry IDs with the states holding that fluid.
    fluids: Vec<(u32, usize)>,
    air: u32,
}

impl Geode {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let provider = |reader: &mut Reader| {
            let provider = Provider::read(reader)?;
            let mut states = Vec::new();
            provider.states(&mut states);
            reader.record_writes(states);
            Some(provider)
        };
        let min_offset = reader.next()?;
        let max_offset = reader.next()?;
        if !(-64..=64).contains(&min_offset) || !(min_offset..=64).contains(&max_offset) {
            return None;
        }
        let distribution = IntProvider::read(reader)?;
        let outer_wall = IntProvider::read(reader)?;
        let outer_wall_max = reader.next()?;
        let point_offset = IntProvider::read(reader)?;
        let invalid_threshold = reader.next()?;
        let (invalid, replaceable) = (reader.set()?, reader.set()?);
        let [filling, inner_layer, middle_layer, outer_layer] = [reader.double()?, reader.double()?, reader.double()?, reader.double()?];
        let [crack_chance, base_crack_size] = [reader.double()?, reader.double()?];
        let crack_offset = reader.next()?;
        let noise = reader.count(64)?;
        let noise = reader.noise(noise)?;
        let multiplier = reader.double()?;
        let providers = [provider(reader)?, provider(reader)?, provider(reader)?, provider(reader)?, provider(reader)?];
        let [alternate_chance, potential_chance] = [reader.double()?, reader.double()?];
        let requires_alternate = reader.flag()?;
        let count = reader.count(64).filter(|&n| n > 0)?;
        let mut placements = Vec::with_capacity(count);
        for _ in 0..count {
            let mut facing = [[0; 2]; 6];
            for pair in facing.iter_mut() {
                *pair = [reader.write_state()?, reader.write_state()?];
            }
            placements.push(facing);
        }
        let (cluster, source) = (reader.set()?, reader.set()?);
        let fluid_count = reader.count(64)?;
        let fluids = (0..fluid_count).map(|_| Some((reader.state()?, reader.set()?))).collect::<Option<Vec<_>>>()?;
        let air = reader.write_state()?;
        Some(Self {
            min_offset, max_offset, distribution, outer_wall, outer_wall_max, point_offset, invalid_threshold, invalid, replaceable,
            filling, inner_layer, middle_layer, outer_layer, crack_chance, base_crack_size, crack_offset, noise, multiplier, providers,
            alternate_chance, potential_chance, requires_alternate, placements, cluster, source, fluids, air,
        })
    }

    fn state(placement: &Placement, pos: [i32; 3]) -> Result<u32> {
        placement.region.level_block_state(pos[0], pos[1], pos[2])
    }

    /// `Feature.safeSetBlock(level, pos, state, isReplaceable(cannotReplace))`.
    fn safe_set(&self, placement: &mut Placement, pos: [i32; 3], state: u32) -> Result<()> {
        if placement.program.in_set(self.replaceable, Self::state(placement, pos)?) {
            placement.region.set_block(pos[0], pos[1], pos[2], state, 2)?;
        }
        Ok(())
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let k = self.distribution.sample(placement);
        let d = k as f64 / self.outer_wall_max as f64;
        let e = 1.0 / self.filling.sqrt();
        let f = 1.0 / (self.inner_layer + d).sqrt();
        let g = 1.0 / (self.middle_layer + d).sqrt();
        let h = 1.0 / (self.outer_layer + d).sqrt();
        let l = 1.0 / (self.base_crack_size + placement.random.next_double() / 2.0 + if k > 3 { d } else { 0.0 }).sqrt();
        let crack = (placement.random.next_float() as f64) < self.crack_chance;
        let mut invalid = 0;
        let mut points = Vec::with_capacity(k.max(0) as usize);
        for _ in 0..k {
            let o = self.outer_wall.sample(placement);
            let p = self.outer_wall.sample(placement);
            let q = self.outer_wall.sample(placement);
            let at = [origin[0] + o, origin[1] + p, origin[2] + q];
            if placement.program.in_set(self.invalid, Self::state(placement, at)?) {
                invalid += 1;
                if invalid > self.invalid_threshold {
                    return Ok(false);
                }
            }
            let offset = self.point_offset.sample(placement);
            points.push(Point { x: at[0], y: at[1], z: at[2], offset });
        }
        let mut cracks = Vec::new();
        if crack {
            let n = placement.random.next_int(4);
            let o = k * 2 + 1;
            let (dx, dz) = match n {
                0 => (o, 0),
                1 => (0, o),
                2 => (o, o),
                _ => (0, 0),
            };
            for dy in [7, 5, 1] {
                cracks.push(Point { x: origin[0] + dx, y: origin[1] + dy, z: origin[2] + dz, offset: self.crack_offset });
            }
        }
        let (i, j) = (self.min_offset, self.max_offset);
        let mut potential = Vec::new();
        for z in origin[2] + i..=origin[2] + j {
            for y in origin[1] + i..=origin[1] + j {
                for x in origin[0] + i..=origin[0] + j {
                    let pos = [x, y, z];
                    let (fx, fy, fz) = (x as f64, y as f64, z as f64);
                    let noise = placement.program.noise(self.noise, fx, fy, fz) * self.multiplier;
                    let s = accumulate(&points, fx, fy, fz, noise);
                    let t = accumulate(&cracks, fx, fy, fz, noise);
                    // `!(s < h)`: a NaN density enters, then matches no layer.
                    if s < h {
                        continue;
                    }
                    if crack && t >= l && s < e {
                        self.safe_set(placement, pos, self.air)?;
                        for by in DIRECTIONS {
                            let next = [x + by[0], y + by[1], z + by[2]];
                            let there = Self::state(placement, next)?;
                            if let Some(&(fluid, _)) = self.fluids.iter().find(|(_, set)| placement.program.in_set(*set, there)) {
                                placement.region.schedule_tick(true, next[0], next[1], next[2], fluid, 0);
                            }
                        }
                    } else if s >= e {
                        let state = self.providers[0].state(placement);
                        self.safe_set(placement, pos, state)?;
                    } else if s >= f {
                        let alternate = (placement.random.next_float() as f64) < self.alternate_chance;
                        let state = self.providers[if alternate { 2 } else { 1 }].state(placement);
                        self.safe_set(placement, pos, state)?;
                        if (!self.requires_alternate || alternate) && (placement.random.next_float() as f64) < self.potential_chance {
                            potential.push(pos);
                        }
                    } else if s >= g {
                        let state = self.providers[3].state(placement);
                        self.safe_set(placement, pos, state)?;
                    } else if s >= h {
                        let state = self.providers[4].state(placement);
                        self.safe_set(placement, pos, state)?;
                    }
                }
            }
        }
        for pos in potential {
            let chosen = &self.placements[placement.random.next_int(self.placements.len() as i32) as usize];
            for (direction, by) in DIRECTIONS.iter().enumerate() {
                let next = [pos[0] + by[0], pos[1] + by[1], pos[2] + by[2]];
                let there = Self::state(placement, next)?;
                let state = chosen[direction][usize::from(placement.program.in_set(self.source, there))];
                if placement.program.in_set(self.cluster, there) {
                    self.safe_set(placement, next, state)?;
                    break;
                }
            }
        }
        Ok(true)
    }
}

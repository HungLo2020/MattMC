//! `LakeFeature`. A water lake's freezing pass (biome temperature and light)
//! is a Java step.
use super::placement::Placement;
use super::program::Reader;
use super::region::Result;
use super::tree::Provider;
use super::write::EVENT_POST_PROCESS;
use crate::content::block::{StateFlags, StateId};

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Lake {
    fluid: Provider,
    barrier: Provider,
    /// `CAVE_AIR` and its block registry ID (the tick it schedules).
    air: u32,
    air_block: u32,
    liquid: usize,
    solid: usize,
    /// Not `features_cannot_replace`.
    replaceable: usize,
    /// `lava_pool_stone_cannot_replace`.
    stone_cannot_replace: usize,
    /// The freezing pass when the fluid is water, else -1.
    freeze_step: i64,
}

fn index(s: i32, t: i32, u: i32) -> usize {
    ((s * 16 + t) * 8 + u) as usize
}

impl Lake {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        let provider = |reader: &mut Reader| {
            let provider = Provider::read(reader)?;
            let mut states = Vec::new();
            provider.states(&mut states);
            reader.record_writes(states);
            Some(provider)
        };
        Some(Self {
            fluid: provider(reader)?,
            barrier: provider(reader)?,
            air: reader.write_state()?,
            air_block: reader.state()?,
            liquid: reader.set()?,
            solid: reader.set()?,
            replaceable: reader.set()?,
            stone_cannot_replace: reader.set()?,
            freeze_step: reader.next()? as i64,
        })
    }

    fn state(placement: &Placement, pos: [i32; 3]) -> Result<u32> {
        placement.region.level_block_state(pos[0], pos[1], pos[2])
    }

    fn mark_above(placement: &mut Placement, pos: [i32; 3]) -> Result<()> {
        let mut at = pos;
        for _ in 0..2 {
            at[1] += 1;
            let state = Self::state(placement, at)?;
            if placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR) {
                return Ok(());
            }
            placement.region.event(EVENT_POST_PROCESS, at[0], at[1], at[2], 0);
        }
        Ok(())
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if origin[1] <= placement.region.min_y + 4 {
            return Ok(false);
        }
        let base = [origin[0], origin[1] - 4, origin[2]];
        let at = |s: i32, u: i32, t: i32| [base[0] + s, base[1] + u, base[2] + t];
        let mut shape = [false; 2048];
        let blobs = placement.random.next_int(4) + 4;
        for _ in 0..blobs {
            let random = &mut placement.random;
            let d = random.next_double() * 6.0 + 3.0;
            let e = random.next_double() * 4.0 + 2.0;
            let f = random.next_double() * 6.0 + 3.0;
            let g = random.next_double() * (16.0 - d - 2.0) + 1.0 + d / 2.0;
            let h = random.next_double() * (8.0 - e - 4.0) + 2.0 + e / 2.0;
            let k = random.next_double() * (16.0 - f - 2.0) + 1.0 + f / 2.0;
            for l in 1..15 {
                for m in 1..15 {
                    for n in 1..7 {
                        let o = (l as f64 - g) / (d / 2.0);
                        let p = (n as f64 - h) / (e / 2.0);
                        let q = (m as f64 - k) / (f / 2.0);
                        if o * o + p * p + q * q < 1.0 {
                            shape[index(l, m, n)] = true;
                        }
                    }
                }
            }
        }
        let fluid = self.fluid.state(placement);
        let edge = |s: i32, t: i32, u: i32| {
            !shape[index(s, t, u)]
                && (s < 15 && shape[index(s + 1, t, u)]
                    || s > 0 && shape[index(s - 1, t, u)]
                    || t < 15 && shape[index(s, t + 1, u)]
                    || t > 0 && shape[index(s, t - 1, u)]
                    || u < 7 && shape[index(s, t, u + 1)]
                    || u > 0 && shape[index(s, t, u - 1)])
        };
        for s in 0..16 {
            for t in 0..16 {
                for u in 0..8 {
                    if !edge(s, t, u) {
                        continue;
                    }
                    let state = Self::state(placement, at(s, u, t))?;
                    if u >= 4 && placement.program.in_set(self.liquid, state) {
                        return Ok(false);
                    }
                    if u < 4 && !placement.program.in_set(self.solid, state) && state != fluid {
                        return Ok(false);
                    }
                }
            }
        }
        for s in 0..16 {
            for t in 0..16 {
                for u in 0..8 {
                    if !shape[index(s, t, u)] {
                        continue;
                    }
                    let pos = at(s, u, t);
                    if placement.program.in_set(self.replaceable, Self::state(placement, pos)?) {
                        let open = u >= 4;
                        placement.region.set_block(pos[0], pos[1], pos[2], if open { self.air } else { fluid }, 2)?;
                        if open {
                            placement.region.schedule_tick(false, pos[0], pos[1], pos[2], self.air_block, 0);
                            Self::mark_above(placement, pos)?;
                        }
                    }
                }
            }
        }
        let barrier = self.barrier.state(placement);
        if !placement.region.registry().flags(StateId(barrier as u16)).contains(StateFlags::AIR) {
            for t in 0..16 {
                for w in 0..16 {
                    for v in 0..8 {
                        if edge(t, w, v) && (v < 4 || placement.random.next_int(2) != 0) {
                            let pos = at(t, v, w);
                            let state = Self::state(placement, pos)?;
                            if placement.program.in_set(self.solid, state) && !placement.program.in_set(self.stone_cannot_replace, state) {
                                placement.region.set_block(pos[0], pos[1], pos[2], barrier, 2)?;
                                Self::mark_above(placement, pos)?;
                            }
                        }
                    }
                }
            }
        }
        if self.freeze_step >= 0 {
            placement.java_step(self.freeze_step, base)?;
        }
        Ok(true)
    }
}

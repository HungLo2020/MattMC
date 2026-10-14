//! Nether terrain features: `DeltaFeature` and `BasaltColumnsFeature`.
use super::placement::Placement;
use super::program::{IntProvider, Reader};
use super::region::Result;
use super::simple::Manhattan;
use super::vegetation::DIRECTIONS;
use crate::content::block::{StateFlags, StateId};

/// `Vec3i.distManhattan`: the absolute differences summed as floats.
fn dist_manhattan(a: [i32; 3], b: [i32; 3]) -> i32 {
    ((b[0] - a[0]).abs() as f32 + (b[1] - a[1]).abs() as f32 + (b[2] - a[2]).abs() as f32) as i32
}

fn state(placement: &Placement, pos: [i32; 3]) -> Result<u32> {
    placement.region.level_block_state(pos[0], pos[1], pos[2])
}

fn is_air_state(placement: &Placement, state: u32) -> bool {
    placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR)
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct Delta {
    contents: u32,
    rim: u32,
    size: IntProvider,
    rim_size: IntProvider,
    /// The contents block's states.
    contents_block: usize,
    /// `DeltaFeature.CANNOT_REPLACE`.
    cannot_replace: usize,
}

impl Delta {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            contents: reader.write_state()?,
            rim: reader.write_state()?,
            size: IntProvider::read(reader)?,
            rim_size: IntProvider::read(reader)?,
            contents_block: reader.set()?,
            cannot_replace: reader.set()?,
        })
    }

    /// `DeltaFeature.isClear`: replaceable, open only above.
    fn clear(&self, placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        let here = state(placement, pos)?;
        if placement.program.in_set(self.contents_block, here) || placement.program.in_set(self.cannot_replace, here) {
            return Ok(false);
        }
        for (direction, by) in DIRECTIONS.iter().enumerate() {
            let air = is_air_state(placement, state(placement, [pos[0] + by[0], pos[1] + by[1], pos[2] + by[2]])?);
            // Direction ordinal 1 is up.
            if air != (direction == 1) {
                return Ok(false);
            }
        }
        Ok(true)
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let rimmed = placement.random.next_double() < 0.9;
        let i = if rimmed { self.rim_size.sample(placement) } else { 0 };
        let j = if rimmed { self.rim_size.sample(placement) } else { 0 };
        let rim = rimmed && i != 0 && j != 0;
        let k = self.size.sample(placement);
        let l = self.size.sample(placement);
        let m = k.max(l);
        let mut placed = false;
        for pos in Manhattan::new(origin, k, 0, l) {
            if dist_manhattan(pos, origin) > m {
                break;
            }
            if self.clear(placement, pos)? {
                if rim {
                    placed = true;
                    placement.region.set_block(pos[0], pos[1], pos[2], self.rim, 3)?;
                }
                let shifted = [pos[0] + i, pos[1], pos[2] + j];
                if self.clear(placement, shifted)? {
                    placed = true;
                    placement.region.set_block(shifted[0], shifted[1], shifted[2], self.contents, 3)?;
                }
            }
        }
        Ok(placed)
    }
}

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct BasaltColumns {
    reach: IntProvider,
    height: IntProvider,
    sea_level: i32,
    basalt: u32,
    basalt_block: usize,
    lava_block: usize,
    /// `BasaltColumnsFeature.CANNOT_PLACE_ON`.
    cannot_place_on: usize,
}

impl BasaltColumns {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            reach: IntProvider::read(reader)?,
            height: IntProvider::read(reader)?,
            sea_level: reader.next()?,
            basalt: reader.write_state()?,
            basalt_block: reader.set()?,
            lava_block: reader.set()?,
            cannot_place_on: reader.set()?,
        })
    }

    fn air_or_lava_ocean(&self, placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        let here = state(placement, pos)?;
        Ok(is_air_state(placement, here) || placement.program.in_set(self.lava_block, here) && pos[1] <= self.sea_level)
    }

    fn can_place_at(&self, placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        if !self.air_or_lava_ocean(placement, pos)? {
            return Ok(false);
        }
        let below = state(placement, [pos[0], pos[1] - 1, pos[2]])?;
        Ok(!is_air_state(placement, below) && !placement.program.in_set(self.cannot_place_on, below))
    }

    fn find_surface(&self, placement: &Placement, mut pos: [i32; 3], mut steps: i32) -> Result<Option<[i32; 3]>> {
        while pos[1] > placement.region.min_y + 1 && steps > 0 {
            steps -= 1;
            if self.can_place_at(placement, pos)? {
                return Ok(Some(pos));
            }
            pos[1] -= 1;
        }
        Ok(None)
    }

    fn find_air(&self, placement: &Placement, mut pos: [i32; 3], mut steps: i32) -> Result<Option<[i32; 3]>> {
        let max_y = placement.region.min_y + placement.region.sections as i32 * 16 - 1;
        while pos[1] <= max_y && steps > 0 {
            steps -= 1;
            let here = state(placement, pos)?;
            if placement.program.in_set(self.cannot_place_on, here) {
                return Ok(None);
            }
            if is_air_state(placement, here) {
                return Ok(Some(pos));
            }
            pos[1] += 1;
        }
        Ok(None)
    }

    fn place_column(&self, placement: &mut Placement, center: [i32; 3], height: i32, reach: i32) -> Result<bool> {
        let mut placed = false;
        for z in center[2] - reach..=center[2] + reach {
            for x in center[0] - reach..=center[0] + reach {
                let pos = [x, center[1], z];
                let l = dist_manhattan(pos, center);
                let found = if self.air_or_lava_ocean(placement, pos)? {
                    self.find_surface(placement, pos, l)?
                } else {
                    self.find_air(placement, pos, l)?
                };
                let Some(mut at) = found else {
                    continue;
                };
                let mut m = height - l / 2;
                while m >= 0 {
                    if self.air_or_lava_ocean(placement, at)? {
                        placement.region.set_block(at[0], at[1], at[2], self.basalt, 3)?;
                        at[1] += 1;
                        placed = true;
                    } else {
                        if !placement.program.in_set(self.basalt_block, state(placement, at)?) {
                            break;
                        }
                        at[1] += 1;
                    }
                    m -= 1;
                }
            }
        }
        Ok(placed)
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        if !self.can_place_at(placement, origin)? {
            return Ok(false);
        }
        let j = self.height.sample(placement);
        let clustered = placement.random.next_float() < 0.9f32;
        let k = j.min(if clustered { 5 } else { 8 });
        let count = if clustered { 50 } else { 15 };
        let mut placed = false;
        // `BlockPos.randomBetweenClosed`: each position is drawn as the loop reaches it.
        let (width, depth) = (2 * k + 1, 2 * k + 1);
        for _ in 0..count {
            let x = origin[0] - k + placement.random.next_int(width);
            let y = origin[1] + placement.random.next_int(1);
            let z = origin[2] - k + placement.random.next_int(depth);
            let pos = [x, y, z];
            let m = j - dist_manhattan(pos, origin);
            if m >= 0 {
                let reach = self.reach.sample(placement);
                placed |= self.place_column(placement, pos, m, reach)?;
            }
        }
        Ok(placed)
    }
}

/// `BasaltPillarFeature`.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct BasaltPillar {
    basalt: u32,
}

impl BasaltPillar {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self { basalt: reader.write_state()? })
    }

    fn empty(placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        let state = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
        Ok(is_air_state(placement, state))
    }

    fn set(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<()> {
        placement.region.set_block(pos[0], pos[1], pos[2], self.basalt, 2).map(|_| ())
    }

    /// `placeHangOff`.
    fn hang(&self, placement: &mut Placement, pos: [i32; 3]) -> Result<bool> {
        if placement.random.next_int(10) != 0 {
            self.set(placement, pos)?;
            return Ok(true);
        }
        Ok(false)
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let at = |pos: [i32; 3], by: [i32; 3]| [pos[0] + by[0], pos[1] + by[1], pos[2] + by[2]];
        if !Self::empty(placement, origin)? || Self::empty(placement, at(origin, [0, 1, 0]))? {
            return Ok(false);
        }
        // North, south, west, east.
        const SIDES: [[i32; 3]; 4] = [[0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];
        let mut pos = origin;
        let mut hanging = [true; 4];
        while Self::empty(placement, pos)? {
            if placement.region.outside_build_height(pos[1]) {
                return Ok(true);
            }
            self.set(placement, pos)?;
            for (side, by) in hanging.iter_mut().zip(SIDES) {
                *side = *side && self.hang(placement, at(pos, by))?;
            }
            pos[1] -= 1;
        }
        pos[1] += 1;
        for by in SIDES {
            if placement.random.next_boolean() {
                self.set(placement, at(pos, by))?;
            }
        }
        pos[1] -= 1;
        for i in -3..4i32 {
            for j in -3..4i32 {
                let k = i.abs() * j.abs();
                if placement.random.next_int(10) < 10 - k {
                    let mut column = at(pos, [i, 0, j]);
                    let mut l = 3;
                    while Self::empty(placement, at(column, [0, -1, 0]))? {
                        column[1] -= 1;
                        l -= 1;
                        if l <= 0 {
                            break;
                        }
                    }
                    if !Self::empty(placement, at(column, [0, -1, 0]))? {
                        self.set(placement, column)?;
                    }
                }
            }
        }
        Ok(true)
    }
}

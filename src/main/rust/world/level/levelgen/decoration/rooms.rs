//! `MonsterRoomFeature`. The chest's loot table and the spawner's entity are
//! Java steps (they draw from the decoration random through block entities).
use super::placement::Placement;
use super::program::Reader;
use super::region::Result;
use crate::content::block::{StateFlags, StateId};

/// `Direction.Plane.HORIZONTAL`: north, east, south, west.
const HORIZONTAL: [[i32; 3]; 4] = [[0, 0, -1], [1, 0, 0], [0, 0, 1], [-1, 0, 0]];
const NORTH: usize = 0;

#[derive(Clone, Debug, PartialEq)]
pub(crate) struct MonsterRoom {
    solid: usize,
    chest_block: usize,
    spawner_block: usize,
    /// Not `features_cannot_replace`.
    replaceable: usize,
    solid_render: usize,
    air: u32,
    mossy: u32,
    cobblestone: u32,
    spawner: u32,
    /// The chest facing north, east, south and west.
    chests: [u32; 4],
    loot_step: i64,
    spawner_step: i64,
}

impl MonsterRoom {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            solid: reader.set()?,
            chest_block: reader.set()?,
            spawner_block: reader.set()?,
            replaceable: reader.set()?,
            solid_render: reader.set()?,
            air: reader.write_state()?,
            mossy: reader.write_state()?,
            cobblestone: reader.write_state()?,
            spawner: reader.write_state()?,
            chests: [reader.write_state()?, reader.write_state()?, reader.write_state()?, reader.write_state()?],
            loot_step: reader.count(1 << 16)? as i64,
            spawner_step: reader.count(1 << 16)? as i64,
        })
    }

    fn state(placement: &Placement, pos: [i32; 3]) -> Result<u32> {
        placement.region.level_block_state(pos[0], pos[1], pos[2])
    }

    fn is(placement: &Placement, set: usize, pos: [i32; 3]) -> Result<bool> {
        Ok(placement.program.in_set(set, Self::state(placement, pos)?))
    }

    fn air(placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        let state = Self::state(placement, pos)?;
        Ok(placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR))
    }

    fn safe_set(&self, placement: &mut Placement, pos: [i32; 3], state: u32) -> Result<()> {
        if Self::is(placement, self.replaceable, pos)? {
            placement.region.set_block(pos[0], pos[1], pos[2], state, 2)?;
        }
        Ok(())
    }

    /// `StructurePiece.reorient(level, pos, CHEST.defaultBlockState())` (default facing north).
    fn reorient(&self, placement: &Placement, pos: [i32; 3]) -> Result<u32> {
        let at = |d: usize| [pos[0] + HORIZONTAL[d][0], pos[1], pos[2] + HORIZONTAL[d][2]];
        let mut facing: Option<usize> = None;
        for d in 0..4 {
            let there = Self::state(placement, at(d))?;
            if placement.program.in_set(self.chest_block, there) {
                return Ok(self.chests[NORTH]);
            }
            if placement.program.in_set(self.solid_render, there) {
                if facing.is_some() {
                    facing = None;
                    break;
                }
                facing = Some(d);
            }
        }
        if let Some(d) = facing {
            return Ok(self.chests[(d + 2) % 4]);
        }
        let mut d = NORTH;
        if Self::is(placement, self.solid_render, at(d))? {
            d = (d + 2) % 4;
        }
        if Self::is(placement, self.solid_render, at(d))? {
            d = (d + 1) % 4;
        }
        if Self::is(placement, self.solid_render, at(d))? {
            d = (d + 2) % 4;
        }
        Ok(self.chests[d])
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let at = |s: i32, t: i32, u: i32| [origin[0] + s, origin[1] + t, origin[2] + u];
        let j = placement.random.next_int(2) + 2;
        let (k, l) = (-j - 1, j + 1);
        let o = placement.random.next_int(2) + 2;
        let (p, q) = (-o - 1, o + 1);
        let mut openings = 0;
        for s in k..=l {
            for t in -1..=4 {
                for u in p..=q {
                    let pos = at(s, t, u);
                    let solid = Self::is(placement, self.solid, pos)?;
                    if (t == -1 || t == 4) && !solid {
                        return Ok(false);
                    }
                    if (s == k || s == l || u == p || u == q) && t == 0 && Self::air(placement, pos)? && Self::air(placement, [pos[0], pos[1] + 1, pos[2]])? {
                        openings += 1;
                    }
                }
            }
        }
        if !(1..=5).contains(&openings) {
            return Ok(false);
        }
        for s in k..=l {
            for t in (-1..=3).rev() {
                for u in p..=q {
                    let pos = at(s, t, u);
                    let state = Self::state(placement, pos)?;
                    if s == k || t == -1 || u == p || s == l || t == 4 || u == q {
                        if pos[1] >= placement.region.min_y && !Self::is(placement, self.solid, [pos[0], pos[1] - 1, pos[2]])? {
                            placement.region.set_block(pos[0], pos[1], pos[2], self.air, 2)?;
                        } else if placement.program.in_set(self.solid, state) && !placement.program.in_set(self.chest_block, state) {
                            if t == -1 && placement.random.next_int(4) != 0 {
                                self.safe_set(placement, pos, self.mossy)?;
                            } else {
                                self.safe_set(placement, pos, self.cobblestone)?;
                            }
                        }
                    } else if !placement.program.in_set(self.chest_block, state) && !placement.program.in_set(self.spawner_block, state) {
                        self.safe_set(placement, pos, self.air)?;
                    }
                }
            }
        }
        for _ in 0..2 {
            for _ in 0..3 {
                let x = origin[0] + placement.random.next_int(j * 2 + 1) - j;
                let z = origin[2] + placement.random.next_int(o * 2 + 1) - o;
                let pos = [x, origin[1], z];
                if !Self::air(placement, pos)? {
                    continue;
                }
                let mut solid = 0;
                for by in HORIZONTAL {
                    if Self::is(placement, self.solid, [pos[0] + by[0], pos[1], pos[2] + by[2]])? {
                        solid += 1;
                    }
                }
                if solid == 1 {
                    let chest = self.reorient(placement, pos)?;
                    self.safe_set(placement, pos, chest)?;
                    placement.java_step(self.loot_step, pos)?;
                    break;
                }
            }
        }
        self.safe_set(placement, origin, self.spawner)?;
        placement.java_step(self.spawner_step, origin)?;
        Ok(true)
    }
}

/// `DesertWellFeature`. The suspicious sand's loot table is a Java step.
#[derive(Clone, Debug, PartialEq)]
pub(crate) struct DesertWell {
    sand_block: usize,
    sand: u32,
    slab: u32,
    sandstone: u32,
    water: u32,
    suspicious_sand: u32,
    loot_step: i64,
}

impl DesertWell {
    pub(crate) fn read(reader: &mut Reader) -> Option<Self> {
        Some(Self {
            sand_block: reader.set()?,
            sand: reader.write_state()?,
            slab: reader.write_state()?,
            sandstone: reader.write_state()?,
            water: reader.write_state()?,
            suspicious_sand: reader.write_state()?,
            loot_step: reader.count(1 << 16)? as i64,
        })
    }

    fn empty(placement: &Placement, pos: [i32; 3]) -> Result<bool> {
        let state = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
        Ok(placement.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR))
    }

    fn set(placement: &mut Placement, pos: [i32; 3], state: u32, flags: i32) -> Result<()> {
        placement.region.set_block(pos[0], pos[1], pos[2], state, flags).map(|_| ())
    }

    pub(crate) fn place(&self, placement: &mut Placement, origin: [i32; 3]) -> Result<bool> {
        let at = |pos: [i32; 3], x: i32, y: i32, z: i32| [pos[0] + x, pos[1] + y, pos[2] + z];
        let mut pos = at(origin, 0, 1, 0);
        while Self::empty(placement, pos)? && pos[1] > placement.region.min_y + 2 {
            pos[1] -= 1;
        }
        let state = placement.region.level_block_state(pos[0], pos[1], pos[2])?;
        if !placement.program.in_set(self.sand_block, state) {
            return Ok(false);
        }
        for i in -2..=2 {
            for j in -2..=2 {
                if Self::empty(placement, at(pos, i, -1, j))? && Self::empty(placement, at(pos, i, -2, j))? {
                    return Ok(false);
                }
            }
        }
        for i in -2..=0 {
            for j in -2..=2 {
                for k in -2..=2 {
                    Self::set(placement, at(pos, j, i, k), self.sandstone, 2)?;
                }
            }
        }
        Self::set(placement, pos, self.water, 2)?;
        for [x, y, z] in HORIZONTAL {
            Self::set(placement, at(pos, x, y, z), self.water, 2)?;
        }
        let below = at(pos, 0, -1, 0);
        Self::set(placement, below, self.sand, 2)?;
        for [x, y, z] in HORIZONTAL {
            Self::set(placement, at(below, x, y, z), self.sand, 2)?;
        }
        for j in -2..=2 {
            for k in -2..=2 {
                if j == -2 || j == 2 || k == -2 || k == 2 {
                    Self::set(placement, at(pos, j, 1, k), self.sandstone, 2)?;
                }
            }
        }
        for (x, z) in [(2, 0), (-2, 0), (0, 2), (0, -2)] {
            Self::set(placement, at(pos, x, 1, z), self.slab, 2)?;
        }
        for j in -1..=1 {
            for k in -1..=1 {
                Self::set(placement, at(pos, j, 4, k), if j == 0 && k == 0 { self.sandstone } else { self.slab }, 2)?;
            }
        }
        for y in 1..=3 {
            for (x, z) in [(-1, -1), (-1, 1), (1, -1), (1, 1)] {
                Self::set(placement, at(pos, x, y, z), self.sandstone, 2)?;
            }
        }
        // `List.of(pos, east, south, west, north)`, then `Util.getRandom` twice.
        let choices = [pos, at(pos, 1, 0, 0), at(pos, 0, 0, 1), at(pos, -1, 0, 0), at(pos, 0, 0, -1)];
        for depth in 1..=2 {
            let chosen = choices[placement.random.next_int(5) as usize];
            let sand = at(chosen, 0, -depth, 0);
            Self::set(placement, sand, self.suspicious_sand, 3)?;
            placement.java_step(self.loot_step, sand)?;
        }
        Ok(true)
    }
}

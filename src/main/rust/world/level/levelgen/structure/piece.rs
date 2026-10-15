//! `StructurePiece`'s helpers over the bound decoration region.
use super::Globals;
use crate::content::block::{BlockId, StateFlags, StateId};
use crate::world::level::levelgen::decoration::random::{gaussian_from, gaussian_words, WorldgenRandom};
use crate::world::level::levelgen::decoration::region::{Region, Result, OP_BIOME_TAG, OP_CENTER_SUPPORT, OP_JAVA_STEP, OP_STURDY};

/// `Heightmap.Types` ordinals.
pub(crate) const OCEAN_FLOOR_WG: u32 = 2;
pub(crate) const MOTION_BLOCKING_NO_LEAVES: u32 = 5;
pub(crate) const MOTION_BLOCKING: u32 = 4;
pub(crate) const WORLD_SURFACE_WG: u32 = 0;

/// `Direction.from2DDataValue`: south, west, north, east.
pub(crate) const SOUTH: i32 = 0;
pub(crate) const WEST: i32 = 1;
pub(crate) const NORTH: i32 = 2;
pub(crate) const EAST: i32 = 3;

/// `BoundingBox`, inclusive.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub(crate) struct BoundingBox {
    pub(crate) min_x: i32,
    pub(crate) min_y: i32,
    pub(crate) min_z: i32,
    pub(crate) max_x: i32,
    pub(crate) max_y: i32,
    pub(crate) max_z: i32,
}

impl BoundingBox {
    pub(crate) fn from_words(w: &[i64]) -> Self {
        Self { min_x: w[0] as i32, min_y: w[1] as i32, min_z: w[2] as i32, max_x: w[3] as i32, max_y: w[4] as i32, max_z: w[5] as i32 }
    }
    pub(crate) fn to_words(self, w: &mut [i64]) {
        w[..6].copy_from_slice(&[
            self.min_x as i64, self.min_y as i64, self.min_z as i64, self.max_x as i64, self.max_y as i64, self.max_z as i64,
        ]);
    }
    pub(crate) fn point(x: i32, y: i32, z: i32) -> Self {
        Self { min_x: x, min_y: y, min_z: z, max_x: x, max_y: y, max_z: z }
    }
    pub(crate) fn inside(&self, [x, y, z]: [i32; 3]) -> bool {
        x >= self.min_x && x <= self.max_x && z >= self.min_z && z <= self.max_z && y >= self.min_y && y <= self.max_y
    }
    pub(crate) fn intersects(&self, other: &Self) -> bool {
        self.max_x >= other.min_x && self.min_x <= other.max_x && self.max_z >= other.min_z && self.min_z <= other.max_z
            && self.max_y >= other.min_y && self.min_y <= other.max_y
    }
    pub(crate) fn moved(&mut self, x: i32, y: i32, z: i32) {
        self.min_x += x;
        self.min_y += y;
        self.min_z += z;
        self.max_x += x;
        self.max_y += y;
        self.max_z += z;
    }
}

/// A piece's frame: its box and orientation (`-1` for none).
#[derive(Clone, Copy, Debug)]
pub(crate) struct Frame {
    pub(crate) bbox: BoundingBox,
    pub(crate) orientation: i32,
}

impl Frame {
    pub(crate) fn world_x(&self, x: i32, z: i32) -> i32 {
        match self.orientation {
            NORTH | SOUTH => self.bbox.min_x + x,
            WEST => self.bbox.max_x - z,
            EAST => self.bbox.min_x + z,
            _ => x,
        }
    }
    pub(crate) fn world_y(&self, y: i32) -> i32 {
        if self.orientation < 0 { y } else { y + self.bbox.min_y }
    }
    pub(crate) fn world_z(&self, x: i32, z: i32) -> i32 {
        match self.orientation {
            NORTH => self.bbox.max_z - z,
            SOUTH => self.bbox.min_z + z,
            WEST | EAST => self.bbox.min_z + x,
            _ => z,
        }
    }
    pub(crate) fn world(&self, x: i32, y: i32, z: i32) -> [i32; 3] {
        [self.world_x(x, z), self.world_y(y), self.world_z(x, z)]
    }
    /// The orientation's mirror and rotation as a transform index.
    pub(crate) fn transform(&self) -> usize {
        match self.orientation {
            SOUTH => 1,
            WEST => 2,
            EAST => 3,
            _ => 0,
        }
    }
}

/// One placement call: the region, the placement random and the chunk's box.
pub(crate) struct Ctx<'r, 'a> {
    pub(crate) region: &'r mut Region<'a>,
    pub(crate) random: WorldgenRandom,
    pub(crate) chunk_box: BoundingBox,
    pub(crate) g: &'static Globals,
    pub(crate) names: super::Names,
    /// `WorldGenRegion.getRandom()`, when it is a Xoroshiro source; handed back after the call.
    pub(crate) region_random: Option<crate::world::level::levelgen::random::Xoroshiro>,
    /// The level seed.
    pub(crate) seed: i64,
    /// `getSeaLevel()`.
    pub(crate) sea_level: i32,
    /// A piece's `canBeReplaced`: blocks it never overwrites (empty: always replace).
    pub(crate) keep_blocks: Vec<BlockId>,
    air: u32,
}

impl<'r, 'a> Ctx<'r, 'a> {
    pub(crate) fn new(region: &'r mut Region<'a>, random: WorldgenRandom, chunk_box: BoundingBox, g: &'static Globals) -> Option<Self> {
        let names = super::Names::new()?;
        let air = names.state("minecraft:air", &[])?;
        Some(Self { region, random, chunk_box, g, names, region_random: None, seed: 0, sea_level: 0, keep_blocks: Vec::new(), air })
    }

    pub(crate) fn air(&self) -> u32 {
        self.air
    }

    pub(crate) fn state_at(&self, pos: [i32; 3]) -> Result<u32> {
        self.region.level_block_state(pos[0], pos[1], pos[2])
    }

    pub(crate) fn is_air_state(&self, state: u32) -> bool {
        self.region.registry().flags(StateId(state as u16)).contains(StateFlags::AIR)
    }

    pub(crate) fn block_of(&self, state: u32) -> BlockId {
        self.region.registry().block_of(StateId(state as u16))
    }

    pub(crate) fn min_y(&self) -> i32 {
        self.region.min_y
    }

    /// `WorldGenRegion.setBlock(pos, state, flags)`; false when refused.
    pub(crate) fn set_block(&mut self, pos: [i32; 3], state: u32, flags: i32) -> Result<bool> {
        self.region.set_block(pos[0], pos[1], pos[2], state, flags)
    }

    /// `scheduleTick(pos, fluid, 0)` when the position holds a fluid, as `placeBlock` does.
    fn tick_fluid(&mut self, pos: [i32; 3]) -> Result<()> {
        let fluid = self.g.fluid_type(self.state_at(pos)?);
        if fluid >= 0 {
            self.region.schedule_tick(true, pos[0], pos[1], pos[2], fluid as u32, 0);
        }
        Ok(())
    }

    /// `StructurePiece.placeBlock`: mirror and rotate, write, tick a fluid, mark shape checks.
    pub(crate) fn place_block(&mut self, frame: &Frame, state: u32, x: i32, y: i32, z: i32) -> Result<()> {
        self.place_block_if(frame, state, x, y, z, |_, _| Ok(true))
    }

    /// `placeBlock` with a piece's own `canBeReplaced`.
    pub(crate) fn place_block_if(
        &mut self,
        frame: &Frame,
        state: u32,
        x: i32,
        y: i32,
        z: i32,
        can_replace: impl FnOnce(&mut Self, [i32; 3]) -> Result<bool>,
    ) -> Result<()> {
        let pos = frame.world(x, y, z);
        if !self.chunk_box.inside(pos) || !can_replace(self, pos)? {
            return Ok(());
        }
        if !self.keep_blocks.is_empty() {
            let there = self.block_of(self.state_at(pos)?);
            if self.keep_blocks.contains(&there) {
                return Ok(());
            }
        }
        let state = self.g.transform(state, frame.transform());
        self.set_block(pos, state, 2)?;
        self.tick_fluid(pos)?;
        if self.g.shape_check(state) {
            self.region.event(crate::world::level::levelgen::decoration::write::EVENT_POST_PROCESS, pos[0], pos[1], pos[2], 0);
        }
        Ok(())
    }

    /// `getBlock`: air outside the chunk's box.
    pub(crate) fn get_block(&self, frame: &Frame, x: i32, y: i32, z: i32) -> Result<u32> {
        let pos = frame.world(x, y, z);
        if !self.chunk_box.inside(pos) {
            return Ok(self.air);
        }
        self.state_at(pos)
    }

    /// `isInterior`: below the WG ocean floor one block up.
    pub(crate) fn is_interior(&mut self, frame: &Frame, x: i32, y: i32, z: i32) -> Result<bool> {
        let pos = frame.world(x, y + 1, z);
        if !self.chunk_box.inside(pos) {
            return Ok(false);
        }
        Ok(pos[1] < self.region.height_by_type(OCEAN_FLOOR_WG, pos[0], pos[2])?)
    }

    pub(crate) fn generate_air_box(&mut self, frame: &Frame, x0: i32, y0: i32, z0: i32, x1: i32, y1: i32, z1: i32) -> Result<()> {
        let air = self.air;
        for y in y0..=y1 {
            for x in x0..=x1 {
                for z in z0..=z1 {
                    self.place_block(frame, air, x, y, z)?;
                }
            }
        }
        Ok(())
    }

    /// `generateBox` with an edge and an inner state; `keep_air` skips air positions.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn generate_box(
        &mut self, frame: &Frame, x0: i32, y0: i32, z0: i32, x1: i32, y1: i32, z1: i32, edge: u32, inner: u32, keep_air: bool,
    ) -> Result<()> {
        for y in y0..=y1 {
            for x in x0..=x1 {
                for z in z0..=z1 {
                    if !keep_air || !self.is_air_state(self.get_block(frame, x, y, z)?) {
                        let state = if y != y0 && y != y1 && x != x0 && x != x1 && z != z0 && z != z1 { inner } else { edge };
                        self.place_block(frame, state, x, y, z)?;
                    }
                }
            }
        }
        Ok(())
    }

    /// `generateBox` with a `BlockSelector`.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn generate_box_selector(
        &mut self,
        frame: &Frame,
        x0: i32,
        y0: i32,
        z0: i32,
        x1: i32,
        y1: i32,
        z1: i32,
        keep_air: bool,
        mut selector: impl FnMut(&mut WorldgenRandom, i32, i32, i32, bool) -> u32,
    ) -> Result<()> {
        for y in y0..=y1 {
            for x in x0..=x1 {
                for z in z0..=z1 {
                    if !keep_air || !self.is_air_state(self.get_block(frame, x, y, z)?) {
                        let edge = y == y0 || y == y1 || x == x0 || x == x1 || z == z0 || z == z1;
                        let state = selector(&mut self.random, x, y, z, edge);
                        self.place_block(frame, state, x, y, z)?;
                    }
                }
            }
        }
        Ok(())
    }

    /// `generateMaybeBox`.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn generate_maybe_box(
        &mut self,
        frame: &Frame,
        chance: f32,
        x0: i32,
        y0: i32,
        z0: i32,
        x1: i32,
        y1: i32,
        z1: i32,
        edge: u32,
        inner: u32,
        keep_air: bool,
        interior: bool,
    ) -> Result<()> {
        for y in y0..=y1 {
            for x in x0..=x1 {
                for z in z0..=z1 {
                    if !(self.random.next_float() > chance)
                        && (!keep_air || !self.is_air_state(self.get_block(frame, x, y, z)?))
                        && (!interior || self.is_interior(frame, x, y, z)?)
                    {
                        let state = if y != y0 && y != y1 && x != x0 && x != x1 && z != z0 && z != z1 { inner } else { edge };
                        self.place_block(frame, state, x, y, z)?;
                    }
                }
            }
        }
        Ok(())
    }

    /// `maybeGenerateBlock`.
    pub(crate) fn maybe_generate_block(&mut self, frame: &Frame, chance: f32, x: i32, y: i32, z: i32, state: u32) -> Result<()> {
        if self.random.next_float() < chance {
            self.place_block(frame, state, x, y, z)?;
        }
        Ok(())
    }

    /// `generateUpperHalfSphere`.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn generate_upper_half_sphere(
        &mut self, frame: &Frame, x0: i32, y0: i32, z0: i32, x1: i32, y1: i32, z1: i32, state: u32, keep_air: bool,
    ) -> Result<()> {
        let f = (x1 - x0 + 1) as f32;
        let g = (y1 - y0 + 1) as f32;
        let h = (z1 - z0 + 1) as f32;
        let o = x0 as f32 + f / 2.0;
        let p = z0 as f32 + h / 2.0;
        for y in y0..=y1 {
            let r = (y - y0) as f32 / g;
            for x in x0..=x1 {
                let t = (x as f32 - o) / (f * 0.5);
                for z in z0..=z1 {
                    let v = (z as f32 - p) / (h * 0.5);
                    if !keep_air || !self.is_air_state(self.get_block(frame, x, y, z)?) {
                        let w = t * t + r * r + v * v;
                        if w <= 1.05 {
                            self.place_block(frame, state, x, y, z)?;
                        }
                    }
                }
            }
        }
        Ok(())
    }

    /// `fillColumnDown`: untransformed `setBlock(…, 2)` down through replaceable blocks.
    pub(crate) fn fill_column_down(&mut self, frame: &Frame, state: u32, x: i32, y: i32, z: i32) -> Result<()> {
        let mut pos = frame.world(x, y, z);
        if !self.chunk_box.inside(pos) {
            return Ok(());
        }
        while self.g.replaceable(self.state_at(pos)?) && pos[1] > self.min_y() + 1 {
            self.set_block(pos, state, 2)?;
            pos[1] -= 1;
        }
        Ok(())
    }

    /// `state.isFaceSturdy(level, pos, direction)` by `Direction` ordinal; dynamic shapes ask Java.
    pub(crate) fn sturdy(&self, pos: [i32; 3], direction: usize) -> Result<bool> {
        let state = self.state_at(pos)?;
        if !self.g.dynamic(state) {
            return Ok(self.g.sturdy(state, direction));
        }
        let mut args = [pos[0] as i64, pos[1] as i64, pos[2] as i64, direction as i64];
        self.region.pure_call(OP_STURDY, &mut args)?;
        Ok(args[0] != 0)
    }

    /// `Block.canSupportCenter(level, pos, DOWN)`.
    pub(crate) fn center_support_down(&self, pos: [i32; 3]) -> Result<bool> {
        let state = self.state_at(pos)?;
        if !self.g.dynamic(state) {
            return Ok(self.g.center_down(state));
        }
        let mut args = [pos[0] as i64, pos[1] as i64, pos[2] as i64, 0];
        self.region.pure_call(OP_CENTER_SUPPORT, &mut args)?;
        Ok(args[0] != 0)
    }

    /// `level.getBiome(pos).is(tag)` for a tag index Java knows.
    pub(crate) fn biome_tag(&self, pos: [i32; 3], tag: i64) -> Result<bool> {
        let mut args = [pos[0] as i64, pos[1] as i64, pos[2] as i64, tag];
        self.region.pure_call(OP_BIOME_TAG, &mut args)?;
        Ok(args[0] != 0)
    }

    /// `getMaxY()`.
    pub(crate) fn max_y(&self) -> i32 {
        self.region.min_y + self.region.sections as i32 * 16 - 1
    }

    /// A Java step at a position with this random (loot seeds, entities).
    pub(crate) fn java_step(&mut self, index: i64, pos: [i32; 3]) -> Result<()> {
        let (lo, hi) = self.random.state();
        let [have, bits] = gaussian_words(self.random.gaussian);
        let mut args = [index, pos[0] as i64, pos[1] as i64, pos[2] as i64, lo, hi, -1, have, bits];
        self.region.sync_call(OP_JAVA_STEP, &mut args)?;
        self.random.set_state(args[4], args[5]);
        self.random.gaussian = gaussian_from(args[7], args[8]);
        Ok(())
    }

    fn horizontal_facing(&self, state: u32, facing: [i32; 3]) -> Option<u32> {
        let name = match facing {
            [0, 0, -1] => "north",
            [0, 0, 1] => "south",
            [-1, 0, 0] => "west",
            _ => "east",
        };
        self.names.with(StateId(state as u16), "facing", name).map(|s| s.0 as u32)
    }

    /// `StructurePiece.reorient` for a chest at `pos`.
    pub(crate) fn reorient(&self, pos: [i32; 3], state: u32, chest: BlockId) -> Result<u32> {
        // `Direction.Plane.HORIZONTAL`: north, east, south, west.
        const HORIZONTAL: [[i32; 3]; 4] = [[0, 0, -1], [1, 0, 0], [0, 0, 1], [-1, 0, 0]];
        let at = |d: [i32; 3]| [pos[0] + d[0], pos[1] + d[1], pos[2] + d[2]];
        let mut found: Option<[i32; 3]> = None;
        for d in HORIZONTAL {
            let there = self.state_at(at(d))?;
            if self.block_of(there) == chest {
                return Ok(state);
            }
            if self.g.solid_render(there) {
                if found.is_some() {
                    found = None;
                    break;
                }
                found = Some(d);
            }
        }
        let facing = if let Some(d) = found {
            [-d[0], -d[1], -d[2]]
        } else {
            let current = self.names.value(StateId(state as u16), "facing").unwrap_or("north");
            let mut d = match current {
                "north" => [0, 0, -1],
                "south" => [0, 0, 1],
                "west" => [-1, 0, 0],
                _ => [1, 0, 0],
            };
            if self.g.solid_render(self.state_at(at(d))?) {
                d = [-d[0], -d[1], -d[2]];
            }
            if self.g.solid_render(self.state_at(at(d))?) {
                // `getClockWise`: north -> east -> south -> west.
                d = [-d[2], 0, d[0]];
            }
            if self.g.solid_render(self.state_at(at(d))?) {
                d = [-d[0], -d[1], -d[2]];
            }
            d
        };
        Ok(self.horizontal_facing(state, facing).unwrap_or(state))
    }

    /// `createChest(level, box, random, pos, table, state)` at a world position.
    pub(crate) fn create_chest(&mut self, pos: [i32; 3], table: &str, state: Option<u32>) -> Result<bool> {
        let chest_state = self.names.state("minecraft:chest", &[]).expect("chest");
        let chest = self.block_of(chest_state);
        if !self.chunk_box.inside(pos) || self.block_of(self.state_at(pos)?) == chest {
            return Ok(false);
        }
        let state = match state {
            Some(state) => state,
            None => self.reorient(pos, chest_state, chest)?,
        };
        self.set_block(pos, state, 2)?;
        let step = self.g.chest_step(table).expect("exported loot table");
        self.java_step(step, pos)?;
        Ok(true)
    }

    /// `createDispenser(level, box, random, x, y, z, facing, table)`.
    #[allow(clippy::too_many_arguments)]
    pub(crate) fn create_dispenser(&mut self, frame: &Frame, x: i32, y: i32, z: i32, facing: &str, table: &str) -> Result<bool> {
        let pos = frame.world(x, y, z);
        let dispenser = self.names.state("minecraft:dispenser", &[]).expect("dispenser");
        if !self.chunk_box.inside(pos) || self.block_of(self.state_at(pos)?) == self.block_of(dispenser) {
            return Ok(false);
        }
        let state = self.names.state("minecraft:dispenser", &[("facing", facing)]).expect("dispenser facing");
        self.place_block(frame, state, x, y, z)?;
        let step = self.g.dispenser_step(table).expect("exported loot table");
        self.java_step(step, pos)?;
        Ok(true)
    }
}

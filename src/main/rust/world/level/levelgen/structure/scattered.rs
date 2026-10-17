//! `ScatteredFeaturePiece`s (swamp hut, desert pyramid, jungle temple) and
//! `BuriedTreasurePiece`.
use super::piece::{BoundingBox, Ctx, Frame, MOTION_BLOCKING_NO_LEAVES, OCEAN_FLOOR_WG};
use super::{positional_legacy, resolve, Misc, StateList};
use crate::world::level::levelgen::decoration::random::WorldgenRandom;
use crate::world::level::levelgen::decoration::region::Result;
use crate::world::level::levelgen::random::Xoroshiro;
use std::sync::OnceLock;

/// `ScatteredFeaturePiece`'s fields after the common header.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Scattered {
    pub(crate) frame: Frame,
    pub(crate) width: i32,
    pub(crate) height: i32,
    pub(crate) depth: i32,
    pub(crate) height_position: i32,
}

impl Scattered {
    pub(crate) fn read(frame: Frame, w: &[i64]) -> Option<Self> {
        Some(Self { frame, width: *w.first()? as i32, height: *w.get(1)? as i32, depth: *w.get(2)? as i32, height_position: *w.get(3)? as i32 })
    }

    pub(crate) fn write(&self, w: &mut [i64]) {
        self.frame.bbox.to_words(&mut w[1..7]);
        w[12] = self.height_position as i64;
    }

    /// `updateAverageGroundHeight(level, box, offset)`.
    pub(crate) fn update_average_ground_height(&mut self, ctx: &mut Ctx, offset: i32) -> Result<bool> {
        if self.height_position >= 0 {
            return Ok(true);
        }
        let (mut sum, mut count) = (0i32, 0i32);
        let b = self.frame.bbox;
        for z in b.min_z..=b.max_z {
            for x in b.min_x..=b.max_x {
                if ctx.chunk_box.inside([x, 64, z]) {
                    sum = sum.wrapping_add(ctx.region.height_by_type(MOTION_BLOCKING_NO_LEAVES, x, z)?);
                    count += 1;
                }
            }
        }
        if count == 0 {
            return Ok(false);
        }
        self.height_position = sum / count;
        let dy = self.height_position - self.frame.bbox.min_y + offset;
        self.frame.bbox.moved(0, dy, 0);
        Ok(true)
    }

    /// `updateHeightPositionToLowestGroundHeight(level, offset)`.
    pub(crate) fn update_height_to_lowest_ground(&mut self, ctx: &mut Ctx, offset: i32) -> Result<bool> {
        if self.height_position >= 0 {
            return Ok(true);
        }
        let mut lowest = ctx.region.min_y + ctx.region.sections as i32 * 16 - 1 + 1;
        let mut any = false;
        let b = self.frame.bbox;
        for z in b.min_z..=b.max_z {
            for x in b.min_x..=b.max_x {
                lowest = lowest.min(ctx.region.height_by_type(MOTION_BLOCKING_NO_LEAVES, x, z)?);
                any = true;
            }
        }
        if !any {
            return Ok(false);
        }
        self.height_position = lowest;
        let dy = self.height_position - self.frame.bbox.min_y + offset;
        self.frame.bbox.moved(0, dy, 0);
        Ok(true)
    }
}

/// `SwampHutPiece`: header, width/height/depth/heightPosition, then witch and cat flags.
pub(crate) fn swamp_hut(ctx: &mut Ctx, words: &mut [i64], frame: Frame) -> Result<bool> {
    let n = &ctx.names;
    let (Some(planks), Some(log), Some(fence), Some(potted), Some(table), Some(cauldron)) = (
        n.state("minecraft:spruce_planks", &[]),
        n.state("minecraft:oak_log", &[]),
        n.state("minecraft:oak_fence", &[]),
        n.state("minecraft:potted_red_mushroom", &[]),
        n.state("minecraft:crafting_table", &[]),
        n.state("minecraft:cauldron", &[]),
    ) else {
        return Ok(false);
    };
    let stairs = |facing: &str, shape: &str| n.state("minecraft:spruce_stairs", &[("facing", facing), ("shape", shape)]);
    let (Some(north), Some(east), Some(west), Some(south)) =
        (stairs("north", "straight"), stairs("east", "straight"), stairs("west", "straight"), stairs("south", "straight"))
    else {
        return Ok(false);
    };
    let (Some(north_outer_right), Some(north_outer_left), Some(south_outer_left), Some(south_outer_right)) =
        (stairs("north", "outer_right"), stairs("north", "outer_left"), stairs("south", "outer_left"), stairs("south", "outer_right"))
    else {
        return Ok(false);
    };
    let Some(mut piece) = Scattered::read(frame, &words[9..]) else {
        return Ok(false);
    };
    let (mut witch, mut cat) = (words[13] != 0, words[14] != 0);
    let air = ctx.air();
    if piece.update_average_ground_height(ctx, 0)? {
        let f = &piece.frame;
        ctx.generate_box(f, 1, 1, 1, 5, 1, 7, planks, planks, false)?;
        ctx.generate_box(f, 1, 4, 2, 5, 4, 7, planks, planks, false)?;
        ctx.generate_box(f, 2, 1, 0, 4, 1, 0, planks, planks, false)?;
        ctx.generate_box(f, 2, 2, 2, 3, 3, 2, planks, planks, false)?;
        ctx.generate_box(f, 1, 2, 3, 1, 3, 6, planks, planks, false)?;
        ctx.generate_box(f, 5, 2, 3, 5, 3, 6, planks, planks, false)?;
        ctx.generate_box(f, 2, 2, 7, 4, 3, 7, planks, planks, false)?;
        ctx.generate_box(f, 1, 0, 2, 1, 3, 2, log, log, false)?;
        ctx.generate_box(f, 5, 0, 2, 5, 3, 2, log, log, false)?;
        ctx.generate_box(f, 1, 0, 7, 1, 3, 7, log, log, false)?;
        ctx.generate_box(f, 5, 0, 7, 5, 3, 7, log, log, false)?;
        ctx.place_block(f, fence, 2, 3, 2)?;
        ctx.place_block(f, fence, 3, 3, 7)?;
        ctx.place_block(f, air, 1, 3, 4)?;
        ctx.place_block(f, air, 5, 3, 4)?;
        ctx.place_block(f, air, 5, 3, 5)?;
        ctx.place_block(f, potted, 1, 3, 5)?;
        ctx.place_block(f, table, 3, 2, 6)?;
        ctx.place_block(f, cauldron, 4, 2, 6)?;
        ctx.place_block(f, fence, 1, 2, 1)?;
        ctx.place_block(f, fence, 5, 2, 1)?;
        ctx.generate_box(f, 0, 4, 1, 6, 4, 1, north, north, false)?;
        ctx.generate_box(f, 0, 4, 2, 0, 4, 7, east, east, false)?;
        ctx.generate_box(f, 6, 4, 2, 6, 4, 7, west, west, false)?;
        ctx.generate_box(f, 0, 4, 8, 6, 4, 8, south, south, false)?;
        ctx.place_block(f, north_outer_right, 0, 4, 1)?;
        ctx.place_block(f, north_outer_left, 6, 4, 1)?;
        ctx.place_block(f, south_outer_left, 0, 4, 8)?;
        ctx.place_block(f, south_outer_right, 6, 4, 8)?;
        for i in (2..=7).step_by(5) {
            for j in (1..=5).step_by(4) {
                ctx.fill_column_down(f, log, j, -1, i)?;
            }
        }
        let spawn = f.world(2, 2, 5);
        if !witch && ctx.chunk_box.inside(spawn) {
            witch = true;
            let step = ctx.g.misc_step(Misc::Witch);
            ctx.java_step(step, spawn)?;
        }
        if !cat && ctx.chunk_box.inside(spawn) {
            cat = true;
            let step = ctx.g.misc_step(Misc::Cat);
            ctx.java_step(step, spawn)?;
        }
    }
    piece.write(words);
    words[13] = witch as i64;
    words[14] = cat as i64;
    Ok(true)
}

/// `BuriedTreasurePiece`: the header only; its box becomes the chest's position.
pub(crate) fn buried_treasure(ctx: &mut Ctx, words: &mut [i64], frame: Frame) -> Result<bool> {
    let n = &ctx.names;
    let solids = ["minecraft:sandstone", "minecraft:stone", "minecraft:andesite", "minecraft:granite", "minecraft:diorite"];
    let Some(bases) = solids.iter().map(|s| n.state(s, &[])).collect::<Option<Vec<_>>>() else {
        return Ok(false);
    };
    let (Some(water), Some(lava), Some(sand)) = (n.state("minecraft:water", &[]), n.state("minecraft:lava", &[]), n.state("minecraft:sand", &[])) else {
        return Ok(false);
    };
    let liquid = |s: u32| s == water || s == lava;
    let b = frame.bbox;
    let mut y = ctx.region.height_by_type(OCEAN_FLOOR_WG, b.min_x, b.min_z)?;
    let (x, z) = (b.min_x, b.min_z);
    let mut bbox = b;
    while y > ctx.min_y() {
        let state = ctx.state_at([x, y, z])?;
        let below = ctx.state_at([x, y - 1, z])?;
        if bases.contains(&below) {
            let fill = if !ctx.is_air_state(state) && !liquid(state) { state } else { sand };
            // `Direction.values()`: down, up, north, south, west, east.
            for (index, d) in [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]].into_iter().enumerate() {
                let side = [x + d[0], y + d[1], z + d[2]];
                let there = ctx.state_at(side)?;
                if ctx.is_air_state(there) || liquid(there) {
                    let under = ctx.state_at([side[0], side[1] - 1, side[2]])?;
                    if (ctx.is_air_state(under) || liquid(under)) && index != 1 {
                        ctx.set_block(side, below, 3)?;
                    } else {
                        ctx.set_block(side, fill, 3)?;
                    }
                }
            }
            bbox = BoundingBox::point(x, y, z);
            ctx.create_chest([x, y, z], "minecraft:chests/buried_treasure", None)?;
            break;
        }
        y -= 1;
    }
    bbox.to_words(&mut words[1..7]);
    Ok(true)
}

static JUNGLE_STATES: OnceLock<Option<Box<[u32]>>> = OnceLock::new();
const JUNGLE_STATE_LIST: StateList = &[
    ("minecraft:air", &[]),
    ("minecraft:cobblestone_stairs", &[("facing", "east")]),
    ("minecraft:cobblestone_stairs", &[("facing", "west")]),
    ("minecraft:cobblestone_stairs", &[("facing", "south")]),
    ("minecraft:cobblestone_stairs", &[("facing", "north")]),
    ("minecraft:tripwire_hook", &[("attached", "true"), ("facing", "east")]),
    ("minecraft:tripwire_hook", &[("attached", "true"), ("facing", "west")]),
    ("minecraft:tripwire", &[("attached", "true"), ("east", "true"), ("west", "true")]),
    ("minecraft:redstone_wire", &[("north", "side"), ("south", "side")]),
    ("minecraft:redstone_wire", &[("north", "side"), ("west", "side")]),
    ("minecraft:redstone_wire", &[("east", "side"), ("west", "side")]),
    ("minecraft:mossy_cobblestone", &[]),
    ("minecraft:vine", &[("south", "true")]),
    ("minecraft:tripwire_hook", &[("attached", "true"), ("facing", "north")]),
    ("minecraft:tripwire_hook", &[("attached", "true"), ("facing", "south")]),
    ("minecraft:tripwire", &[("attached", "true"), ("north", "true"), ("south", "true")]),
    ("minecraft:redstone_wire", &[("south", "side"), ("west", "side")]),
    ("minecraft:redstone_wire", &[("north", "side"), ("south", "up")]),
    ("minecraft:vine", &[("east", "true")]),
    ("minecraft:chiseled_stone_bricks", &[]),
    ("minecraft:lever", &[("face", "wall"), ("facing", "north")]),
    ("minecraft:redstone_wire", &[("east", "side"), ("north", "side"), ("south", "side"), ("west", "side")]),
    ("minecraft:sticky_piston", &[("facing", "up")]),
    ("minecraft:sticky_piston", &[("facing", "west")]),
    ("minecraft:repeater", &[("facing", "north")]),
];

/// `JungleTemplePiece`: header, scattered fields, then main chest, hidden chest, trap 1 and trap 2 flags.
pub(crate) fn jungle_temple(ctx: &mut Ctx, words: &mut [i64], frame: Frame) -> Result<bool> {
    let (Some(s), Some(cobblestone), Some(mossy)) = (
        resolve(&JUNGLE_STATES, &ctx.names, JUNGLE_STATE_LIST),
        ctx.names.state("minecraft:cobblestone", &[]),
        ctx.names.state("minecraft:mossy_cobblestone", &[]),
    ) else {
        return Ok(false);
    };
    let Some(mut p) = Scattered::read(frame, &words[9..]) else {
        return Ok(false);
    };
    let (mut main_chest, mut hidden_chest, mut trap1, mut trap2) = (words[13] != 0, words[14] != 0, words[15] != 0, words[16] != 0);
    // `MossStoneSelector`.
    let mut stone_selector = |random: &mut WorldgenRandom, _: i32, _: i32, _: i32, _: bool| if random.next_float() < 0.4 { cobblestone } else { mossy };
    if p.update_average_ground_height(ctx, 0)? {
        let f = &p.frame.clone();
    ctx.generate_box_selector(f, 0, -4, 0, p.width - 1, 0, p.depth - 1, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 2, 1, 2, 9, 2, 2, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 2, 1, 12, 9, 2, 12, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 2, 1, 3, 2, 2, 11, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 9, 1, 3, 9, 2, 11, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 1, 3, 1, 10, 6, 1, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 1, 3, 13, 10, 6, 13, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 1, 3, 2, 1, 6, 12, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 10, 3, 2, 10, 6, 12, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 2, 3, 2, 9, 3, 12, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 2, 6, 2, 9, 6, 12, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 3, 7, 3, 8, 7, 11, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 4, 8, 4, 7, 8, 10, false, &mut stone_selector)?;
    ctx.generate_air_box(f, 3, 1, 3, 8, 2, 11)?;
    ctx.generate_air_box(f, 4, 3, 6, 7, 3, 9)?;
    ctx.generate_air_box(f, 2, 4, 2, 9, 5, 12)?;
    ctx.generate_air_box(f, 4, 6, 5, 7, 6, 9)?;
    ctx.generate_air_box(f, 5, 7, 6, 6, 7, 8)?;
    ctx.generate_air_box(f, 5, 1, 2, 6, 2, 2)?;
    ctx.generate_air_box(f, 5, 2, 12, 6, 2, 12)?;
    ctx.generate_air_box(f, 5, 5, 1, 6, 5, 1)?;
    ctx.generate_air_box(f, 5, 5, 13, 6, 5, 13)?;
    ctx.place_block(f, s[0], 1, 5, 5)?;
    ctx.place_block(f, s[0], 10, 5, 5)?;
    ctx.place_block(f, s[0], 1, 5, 9)?;
    ctx.place_block(f, s[0], 10, 5, 9)?;
    for i in (0..=14).step_by((14) as usize) {
        ctx.generate_box_selector(f, 2, 4, i, 2, 5, i, false, &mut stone_selector)?;
        ctx.generate_box_selector(f, 4, 4, i, 4, 5, i, false, &mut stone_selector)?;
        ctx.generate_box_selector(f, 7, 4, i, 7, 5, i, false, &mut stone_selector)?;
        ctx.generate_box_selector(f, 9, 4, i, 9, 5, i, false, &mut stone_selector)?;
    }
    ctx.generate_box_selector(f, 5, 6, 0, 6, 6, 0, false, &mut stone_selector)?;
    for i in (0..=11).step_by((11) as usize) {
        for j in (2..=12).step_by((2) as usize) {
            ctx.generate_box_selector(f, i, 4, j, i, 5, j, false, &mut stone_selector)?;
        }
        ctx.generate_box_selector(f, i, 6, 5, i, 6, 5, false, &mut stone_selector)?;
        ctx.generate_box_selector(f, i, 6, 9, i, 6, 9, false, &mut stone_selector)?;
    }
    ctx.generate_box_selector(f, 2, 7, 2, 2, 9, 2, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 9, 7, 2, 9, 9, 2, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 2, 7, 12, 2, 9, 12, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 9, 7, 12, 9, 9, 12, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 4, 9, 4, 4, 9, 4, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 7, 9, 4, 7, 9, 4, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 4, 9, 10, 4, 9, 10, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 7, 9, 10, 7, 9, 10, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 5, 9, 7, 6, 9, 7, false, &mut stone_selector)?;
    // blockState = s[1]
    // blockState2 = s[2]
    // blockState3 = s[3]
    // blockState4 = s[4]
    ctx.place_block(f, s[4], 5, 9, 6)?;
    ctx.place_block(f, s[4], 6, 9, 6)?;
    ctx.place_block(f, s[3], 5, 9, 8)?;
    ctx.place_block(f, s[3], 6, 9, 8)?;
    ctx.place_block(f, s[4], 4, 0, 0)?;
    ctx.place_block(f, s[4], 5, 0, 0)?;
    ctx.place_block(f, s[4], 6, 0, 0)?;
    ctx.place_block(f, s[4], 7, 0, 0)?;
    ctx.place_block(f, s[4], 4, 1, 8)?;
    ctx.place_block(f, s[4], 4, 2, 9)?;
    ctx.place_block(f, s[4], 4, 3, 10)?;
    ctx.place_block(f, s[4], 7, 1, 8)?;
    ctx.place_block(f, s[4], 7, 2, 9)?;
    ctx.place_block(f, s[4], 7, 3, 10)?;
    ctx.generate_box_selector(f, 4, 1, 9, 4, 1, 9, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 7, 1, 9, 7, 1, 9, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 4, 1, 10, 7, 2, 10, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 5, 4, 5, 6, 4, 5, false, &mut stone_selector)?;
    ctx.place_block(f, s[1], 4, 4, 5)?;
    ctx.place_block(f, s[2], 7, 4, 5)?;
    for k in 0..4 {
        ctx.place_block(f, s[3], 5, 0 - k, 6 + k)?;
        ctx.place_block(f, s[3], 6, 0 - k, 6 + k)?;
        ctx.generate_air_box(f, 5, 0 - k, 7 + k, 6, 0 - k, 9 + k)?;
    }
    ctx.generate_air_box(f, 1, -3, 12, 10, -1, 13)?;
    ctx.generate_air_box(f, 1, -3, 1, 3, -1, 13)?;
    ctx.generate_air_box(f, 1, -3, 1, 9, -1, 5)?;
    for k in (1..=13).step_by((2) as usize) {
        ctx.generate_box_selector(f, 1, -3, k, 1, -2, k, false, &mut stone_selector)?;
    }
    for k in (2..=12).step_by((2) as usize) {
        ctx.generate_box_selector(f, 1, -1, k, 3, -1, k, false, &mut stone_selector)?;
    }
    ctx.generate_box_selector(f, 2, -2, 1, 5, -2, 1, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 7, -2, 1, 9, -2, 1, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 6, -3, 1, 6, -3, 1, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 6, -1, 1, 6, -1, 1, false, &mut stone_selector)?;
    ctx.place_block(f, s[5], 1, -3, 8)?;
    ctx.place_block(f, s[6], 4, -3, 8)?;
    ctx.place_block(f, s[7], 2, -3, 8)?;
    ctx.place_block(f, s[7], 3, -3, 8)?;
    // blockState5 = s[8]
    ctx.place_block(f, s[8], 5, -3, 7)?;
    ctx.place_block(f, s[8], 5, -3, 6)?;
    ctx.place_block(f, s[8], 5, -3, 5)?;
    ctx.place_block(f, s[8], 5, -3, 4)?;
    ctx.place_block(f, s[8], 5, -3, 3)?;
    ctx.place_block(f, s[8], 5, -3, 2)?;
    ctx.place_block(f, s[9], 5, -3, 1)?;
    ctx.place_block(f, s[10], 4, -3, 1)?;
    ctx.place_block(f, s[11], 3, -3, 1)?;
    if !trap1 {
        trap1 = ctx.create_dispenser(f, 3, -2, 1, "north", "minecraft:chests/jungle_temple_dispenser")?;
    }
    ctx.place_block(f, s[12], 3, -2, 2)?;
    ctx.place_block(f, s[13], 7, -3, 1)?;
    ctx.place_block(f, s[14], 7, -3, 5)?;
    ctx.place_block(f, s[15], 7, -3, 2)?;
    ctx.place_block(f, s[15], 7, -3, 3)?;
    ctx.place_block(f, s[15], 7, -3, 4)?;
    ctx.place_block(f, s[10], 8, -3, 6)?;
    ctx.place_block(f, s[16], 9, -3, 6)?;
    ctx.place_block(f, s[17], 9, -3, 5)?;
    ctx.place_block(f, s[11], 9, -3, 4)?;
    ctx.place_block(f, s[8], 9, -2, 4)?;
    if !trap2 {
        trap2 = ctx.create_dispenser(f, 9, -2, 3, "west", "minecraft:chests/jungle_temple_dispenser")?;
    }
    ctx.place_block(f, s[18], 8, -1, 3)?;
    ctx.place_block(f, s[18], 8, -2, 3)?;
    if !main_chest {
        main_chest = ctx.create_chest(f.world(8, -3, 3), "minecraft:chests/jungle_temple", None)?;
    }
    ctx.place_block(f, s[11], 9, -3, 2)?;
    ctx.place_block(f, s[11], 8, -3, 1)?;
    ctx.place_block(f, s[11], 4, -3, 5)?;
    ctx.place_block(f, s[11], 5, -2, 5)?;
    ctx.place_block(f, s[11], 5, -1, 5)?;
    ctx.place_block(f, s[11], 6, -3, 5)?;
    ctx.place_block(f, s[11], 7, -2, 5)?;
    ctx.place_block(f, s[11], 7, -1, 5)?;
    ctx.place_block(f, s[11], 8, -3, 5)?;
    ctx.generate_box_selector(f, 9, -1, 1, 9, -1, 5, false, &mut stone_selector)?;
    ctx.generate_air_box(f, 8, -3, 8, 10, -1, 10)?;
    ctx.place_block(f, s[19], 8, -2, 11)?;
    ctx.place_block(f, s[19], 9, -2, 11)?;
    ctx.place_block(f, s[19], 10, -2, 11)?;
    // blockState6 = s[20]
    ctx.place_block(f, s[20], 8, -2, 12)?;
    ctx.place_block(f, s[20], 9, -2, 12)?;
    ctx.place_block(f, s[20], 10, -2, 12)?;
    ctx.generate_box_selector(f, 8, -3, 8, 8, -3, 10, false, &mut stone_selector)?;
    ctx.generate_box_selector(f, 10, -3, 8, 10, -3, 10, false, &mut stone_selector)?;
    ctx.place_block(f, s[11], 10, -2, 9)?;
    ctx.place_block(f, s[8], 8, -2, 9)?;
    ctx.place_block(f, s[8], 8, -2, 10)?;
    ctx.place_block(f, s[21], 10, -1, 9)?;
    ctx.place_block(f, s[22], 9, -2, 8)?;
    ctx.place_block(f, s[23], 10, -2, 8)?;
    ctx.place_block(f, s[23], 10, -1, 8)?;
    ctx.place_block(f, s[24], 10, -2, 10)?;
    if !hidden_chest {
        hidden_chest = ctx.create_chest(f.world(9, -3, 10), "minecraft:chests/jungle_temple", None)?;
    }
    }
    p.write(words);
    words[13] = main_chest as i64;
    words[14] = hidden_chest as i64;
    words[15] = trap1 as i64;
    words[16] = trap2 as i64;
    Ok(true)
}

static PYRAMID_STATES: OnceLock<Option<Box<[u32]>>> = OnceLock::new();
const PYRAMID_STATE_LIST: StateList = &[
    ("minecraft:sandstone", &[]),
    ("minecraft:air", &[]),
    ("minecraft:sandstone_stairs", &[("facing", "north")]),
    ("minecraft:sandstone_stairs", &[("facing", "south")]),
    ("minecraft:sandstone_stairs", &[("facing", "east")]),
    ("minecraft:sandstone_stairs", &[("facing", "west")]),
    ("minecraft:cut_sandstone", &[]),
    ("minecraft:sandstone_slab", &[]),
    ("minecraft:chiseled_sandstone", &[]),
    ("minecraft:orange_terracotta", &[]),
    ("minecraft:blue_terracotta", &[]),
    ("minecraft:stone_pressure_plate", &[]),
    ("minecraft:tnt", &[]),
    ("minecraft:sandstone_stairs", &[]),
    ("minecraft:sand", &[]),
];

/// `DesertPyramidPiece`: header, scattered fields, four chest flags, whether
/// the cellar was placed (its suspicious-sand candidates) and the collapsed
/// roof position. `worldGenLevel.getRandom()` is the region's own random.
pub(crate) fn desert_pyramid(ctx: &mut Ctx, words: &mut [i64], frame: Frame) -> Result<bool> {
    let (Some(s), Some(mut region_random)) = (resolve(&PYRAMID_STATES, &ctx.names, PYRAMID_STATE_LIST), ctx.region_random.take()) else {
        return Ok(false);
    };
    let Some(mut p) = Scattered::read(frame, &words[9..]) else {
        return Ok(false);
    };
    let mut chests = [words[13] != 0, words[14] != 0, words[15] != 0, words[16] != 0];
    let mut cellar = false;
    let mut roof = [words[18] as i32, words[19] as i32, words[20] as i32];
    let result = (|| -> Result<()> {
        let offset = -ctx.random.next_int(3);
        if p.update_height_to_lowest_ground(ctx, offset)? {
        let f = &p.frame.clone();
        ctx.generate_box(f, 0, -4, 0, p.width - 1, 0, p.depth - 1, s[0], s[0], false)?;
        for i in 1..=9 {
            ctx.generate_box(f, i, i, i, p.width - 1 - i, i, p.depth - 1 - i, s[0], s[0], false)?;
            ctx.generate_box(f, i + 1, i, i + 1, p.width - 2 - i, i, p.depth - 2 - i, s[1], s[1], false)?;
        }
        for i in 0..p.width {
            for j in 0..p.depth {
                ctx.fill_column_down(f, s[0], i, -5, j)?;
            }
        }
        // blockState = s[2]
        // blockState2 = s[3]
        // blockState3 = s[4]
        // blockState4 = s[5]
        ctx.generate_box(f, 0, 0, 0, 4, 9, 4, s[0], s[1], false)?;
        ctx.generate_box(f, 1, 10, 1, 3, 10, 3, s[0], s[0], false)?;
        ctx.place_block(f, s[2], 2, 10, 0)?;
        ctx.place_block(f, s[3], 2, 10, 4)?;
        ctx.place_block(f, s[4], 0, 10, 2)?;
        ctx.place_block(f, s[5], 4, 10, 2)?;
        ctx.generate_box(f, p.width - 5, 0, 0, p.width - 1, 9, 4, s[0], s[1], false)?;
        ctx.generate_box(f, p.width - 4, 10, 1, p.width - 2, 10, 3, s[0], s[0], false)?;
        ctx.place_block(f, s[2], p.width - 3, 10, 0)?;
        ctx.place_block(f, s[3], p.width - 3, 10, 4)?;
        ctx.place_block(f, s[4], p.width - 5, 10, 2)?;
        ctx.place_block(f, s[5], p.width - 1, 10, 2)?;
        ctx.generate_box(f, 8, 0, 0, 12, 4, 4, s[0], s[1], false)?;
        ctx.generate_box(f, 9, 1, 0, 11, 3, 4, s[1], s[1], false)?;
        ctx.place_block(f, s[6], 9, 1, 1)?;
        ctx.place_block(f, s[6], 9, 2, 1)?;
        ctx.place_block(f, s[6], 9, 3, 1)?;
        ctx.place_block(f, s[6], 10, 3, 1)?;
        ctx.place_block(f, s[6], 11, 3, 1)?;
        ctx.place_block(f, s[6], 11, 2, 1)?;
        ctx.place_block(f, s[6], 11, 1, 1)?;
        ctx.generate_box(f, 4, 1, 1, 8, 3, 3, s[0], s[1], false)?;
        ctx.generate_box(f, 4, 1, 2, 8, 2, 2, s[1], s[1], false)?;
        ctx.generate_box(f, 12, 1, 1, 16, 3, 3, s[0], s[1], false)?;
        ctx.generate_box(f, 12, 1, 2, 16, 2, 2, s[1], s[1], false)?;
        ctx.generate_box(f, 5, 4, 5, p.width - 6, 4, p.depth - 6, s[0], s[0], false)?;
        ctx.generate_box(f, 9, 4, 9, 11, 4, 11, s[1], s[1], false)?;
        ctx.generate_box(f, 8, 1, 8, 8, 3, 8, s[6], s[6], false)?;
        ctx.generate_box(f, 12, 1, 8, 12, 3, 8, s[6], s[6], false)?;
        ctx.generate_box(f, 8, 1, 12, 8, 3, 12, s[6], s[6], false)?;
        ctx.generate_box(f, 12, 1, 12, 12, 3, 12, s[6], s[6], false)?;
        ctx.generate_box(f, 1, 1, 5, 4, 4, 11, s[0], s[0], false)?;
        ctx.generate_box(f, p.width - 5, 1, 5, p.width - 2, 4, 11, s[0], s[0], false)?;
        ctx.generate_box(f, 6, 7, 9, 6, 7, 11, s[0], s[0], false)?;
        ctx.generate_box(f, p.width - 7, 7, 9, p.width - 7, 7, 11, s[0], s[0], false)?;
        ctx.generate_box(f, 5, 5, 9, 5, 7, 11, s[6], s[6], false)?;
        ctx.generate_box(f, p.width - 6, 5, 9, p.width - 6, 7, 11, s[6], s[6], false)?;
        ctx.place_block(f, s[1], 5, 5, 10)?;
        ctx.place_block(f, s[1], 5, 6, 10)?;
        ctx.place_block(f, s[1], 6, 6, 10)?;
        ctx.place_block(f, s[1], p.width - 6, 5, 10)?;
        ctx.place_block(f, s[1], p.width - 6, 6, 10)?;
        ctx.place_block(f, s[1], p.width - 7, 6, 10)?;
        ctx.generate_box(f, 2, 4, 4, 2, 6, 4, s[1], s[1], false)?;
        ctx.generate_box(f, p.width - 3, 4, 4, p.width - 3, 6, 4, s[1], s[1], false)?;
        ctx.place_block(f, s[2], 2, 4, 5)?;
        ctx.place_block(f, s[2], 2, 3, 4)?;
        ctx.place_block(f, s[2], p.width - 3, 4, 5)?;
        ctx.place_block(f, s[2], p.width - 3, 3, 4)?;
        ctx.generate_box(f, 1, 1, 3, 2, 2, 3, s[0], s[0], false)?;
        ctx.generate_box(f, p.width - 3, 1, 3, p.width - 2, 2, 3, s[0], s[0], false)?;
        ctx.place_block(f, s[0], 1, 1, 2)?;
        ctx.place_block(f, s[0], p.width - 2, 1, 2)?;
        ctx.place_block(f, s[7], 1, 2, 2)?;
        ctx.place_block(f, s[7], p.width - 2, 2, 2)?;
        ctx.place_block(f, s[5], 2, 1, 2)?;
        ctx.place_block(f, s[4], p.width - 3, 1, 2)?;
        ctx.generate_box(f, 4, 3, 5, 4, 3, 17, s[0], s[0], false)?;
        ctx.generate_box(f, p.width - 5, 3, 5, p.width - 5, 3, 17, s[0], s[0], false)?;
        ctx.generate_box(f, 3, 1, 5, 4, 2, 16, s[1], s[1], false)?;
        ctx.generate_box(f, p.width - 6, 1, 5, p.width - 5, 2, 16, s[1], s[1], false)?;
        for l in (5..=17).step_by((2) as usize) {
            ctx.place_block(f, s[6], 4, 1, l)?;
            ctx.place_block(f, s[8], 4, 2, l)?;
            ctx.place_block(f, s[6], p.width - 5, 1, l)?;
            ctx.place_block(f, s[8], p.width - 5, 2, l)?;
        }
        ctx.place_block(f, s[9], 10, 0, 7)?;
        ctx.place_block(f, s[9], 10, 0, 8)?;
        ctx.place_block(f, s[9], 9, 0, 9)?;
        ctx.place_block(f, s[9], 11, 0, 9)?;
        ctx.place_block(f, s[9], 8, 0, 10)?;
        ctx.place_block(f, s[9], 12, 0, 10)?;
        ctx.place_block(f, s[9], 7, 0, 10)?;
        ctx.place_block(f, s[9], 13, 0, 10)?;
        ctx.place_block(f, s[9], 9, 0, 11)?;
        ctx.place_block(f, s[9], 11, 0, 11)?;
        ctx.place_block(f, s[9], 10, 0, 12)?;
        ctx.place_block(f, s[9], 10, 0, 13)?;
        ctx.place_block(f, s[10], 10, 0, 10)?;
        for l in (0..=p.width - 1).step_by((p.width - 1) as usize) {
            ctx.place_block(f, s[6], l, 2, 1)?;
            ctx.place_block(f, s[9], l, 2, 2)?;
            ctx.place_block(f, s[6], l, 2, 3)?;
            ctx.place_block(f, s[6], l, 3, 1)?;
            ctx.place_block(f, s[9], l, 3, 2)?;
            ctx.place_block(f, s[6], l, 3, 3)?;
            ctx.place_block(f, s[9], l, 4, 1)?;
            ctx.place_block(f, s[8], l, 4, 2)?;
            ctx.place_block(f, s[9], l, 4, 3)?;
            ctx.place_block(f, s[6], l, 5, 1)?;
            ctx.place_block(f, s[9], l, 5, 2)?;
            ctx.place_block(f, s[6], l, 5, 3)?;
            ctx.place_block(f, s[9], l, 6, 1)?;
            ctx.place_block(f, s[8], l, 6, 2)?;
            ctx.place_block(f, s[9], l, 6, 3)?;
            ctx.place_block(f, s[9], l, 7, 1)?;
            ctx.place_block(f, s[9], l, 7, 2)?;
            ctx.place_block(f, s[9], l, 7, 3)?;
            ctx.place_block(f, s[6], l, 8, 1)?;
            ctx.place_block(f, s[6], l, 8, 2)?;
            ctx.place_block(f, s[6], l, 8, 3)?;
        }
        for l in (2..=p.width - 3).step_by((p.width - 3 - 2) as usize) {
            ctx.place_block(f, s[6], l - 1, 2, 0)?;
            ctx.place_block(f, s[9], l, 2, 0)?;
            ctx.place_block(f, s[6], l + 1, 2, 0)?;
            ctx.place_block(f, s[6], l - 1, 3, 0)?;
            ctx.place_block(f, s[9], l, 3, 0)?;
            ctx.place_block(f, s[6], l + 1, 3, 0)?;
            ctx.place_block(f, s[9], l - 1, 4, 0)?;
            ctx.place_block(f, s[8], l, 4, 0)?;
            ctx.place_block(f, s[9], l + 1, 4, 0)?;
            ctx.place_block(f, s[6], l - 1, 5, 0)?;
            ctx.place_block(f, s[9], l, 5, 0)?;
            ctx.place_block(f, s[6], l + 1, 5, 0)?;
            ctx.place_block(f, s[9], l - 1, 6, 0)?;
            ctx.place_block(f, s[8], l, 6, 0)?;
            ctx.place_block(f, s[9], l + 1, 6, 0)?;
            ctx.place_block(f, s[9], l - 1, 7, 0)?;
            ctx.place_block(f, s[9], l, 7, 0)?;
            ctx.place_block(f, s[9], l + 1, 7, 0)?;
            ctx.place_block(f, s[6], l - 1, 8, 0)?;
            ctx.place_block(f, s[6], l, 8, 0)?;
            ctx.place_block(f, s[6], l + 1, 8, 0)?;
        }
        ctx.generate_box(f, 8, 4, 0, 12, 6, 0, s[6], s[6], false)?;
        ctx.place_block(f, s[1], 8, 6, 0)?;
        ctx.place_block(f, s[1], 12, 6, 0)?;
        ctx.place_block(f, s[9], 9, 5, 0)?;
        ctx.place_block(f, s[8], 10, 5, 0)?;
        ctx.place_block(f, s[9], 11, 5, 0)?;
        ctx.generate_box(f, 8, -14, 8, 12, -11, 12, s[6], s[6], false)?;
        ctx.generate_box(f, 8, -10, 8, 12, -10, 12, s[8], s[8], false)?;
        ctx.generate_box(f, 8, -9, 8, 12, -9, 12, s[6], s[6], false)?;
        ctx.generate_box(f, 8, -8, 8, 12, -1, 12, s[0], s[0], false)?;
        ctx.generate_box(f, 9, -11, 9, 11, -1, 11, s[1], s[1], false)?;
        ctx.place_block(f, s[11], 10, -11, 10)?;
        ctx.generate_box(f, 9, -13, 9, 11, -13, 11, s[12], s[1], false)?;
        ctx.place_block(f, s[1], 8, -11, 10)?;
        ctx.place_block(f, s[1], 8, -10, 10)?;
        ctx.place_block(f, s[8], 7, -10, 10)?;
        ctx.place_block(f, s[6], 7, -11, 10)?;
        ctx.place_block(f, s[1], 12, -11, 10)?;
        ctx.place_block(f, s[1], 12, -10, 10)?;
        ctx.place_block(f, s[8], 13, -10, 10)?;
        ctx.place_block(f, s[6], 13, -11, 10)?;
        ctx.place_block(f, s[1], 10, -11, 8)?;
        ctx.place_block(f, s[1], 10, -10, 8)?;
        ctx.place_block(f, s[8], 10, -10, 7)?;
        ctx.place_block(f, s[6], 10, -11, 7)?;
        ctx.place_block(f, s[1], 10, -11, 12)?;
        ctx.place_block(f, s[1], 10, -10, 12)?;
        ctx.place_block(f, s[8], 10, -10, 13)?;
        ctx.place_block(f, s[6], 10, -11, 13)?;
        // `Direction.Plane.HORIZONTAL` with each direction's 2D data value.
        for (d2, sx, sz) in [(2usize, 0, -1), (3, 1, 0), (0, 0, 1), (1, -1, 0)] {
            if !chests[d2] {
                chests[d2] = ctx.create_chest(f.world(10 + sx * 2, -11, 10 + sz * 2), "minecraft:chests/desert_pyramid", None)?;
            }
        }
        // `addCellar`: the stairs and room around (16, -4, 13).
        let (i, j, k) = (16, -4, 13);
        // blockState = s[13]
        ctx.place_block(f, s[5], 13, -1, 17)?;
        ctx.place_block(f, s[5], 14, -2, 17)?;
        ctx.place_block(f, s[5], 15, -3, 17)?;
        // blockState2 = s[14]
        // blockState3 = s[0]
        let bl = (region_random.next_long() & 1) != 0;
        ctx.place_block(f, s[14], i - 4, j + 4, k + 4)?;
        ctx.place_block(f, s[14], i - 3, j + 4, k + 4)?;
        ctx.place_block(f, s[14], i - 2, j + 4, k + 4)?;
        ctx.place_block(f, s[14], i - 1, j + 4, k + 4)?;
        ctx.place_block(f, s[14], i, j + 4, k + 4)?;
        ctx.place_block(f, s[14], i - 2, j + 3, k + 4)?;
        ctx.place_block(f, if bl { s[14] } else { s[0] }, i - 1, j + 3, k + 4)?;
        ctx.place_block(f, if !bl { s[14] } else { s[0] }, i, j + 3, k + 4)?;
        ctx.place_block(f, s[14], i - 1, j + 2, k + 4)?;
        ctx.place_block(f, s[0], i, j + 2, k + 4)?;
        ctx.place_block(f, s[14], i, j + 1, k + 4)?;
        // blockState = s[6]
        // blockState2 = s[8]
        ctx.generate_box(f, i - 3, j + 1, k - 3, i - 3, j + 1, k + 2, s[6], s[6], true)?;
        ctx.generate_box(f, i + 3, j + 1, k - 3, i + 3, j + 1, k + 2, s[6], s[6], true)?;
        ctx.generate_box(f, i - 3, j + 1, k - 3, i + 3, j + 1, k - 2, s[6], s[6], true)?;
        ctx.generate_box(f, i - 3, j + 1, k + 3, i + 3, j + 1, k + 3, s[6], s[6], true)?;
        ctx.generate_box(f, i - 3, j + 2, k - 3, i - 3, j + 2, k + 2, s[8], s[8], true)?;
        ctx.generate_box(f, i + 3, j + 2, k - 3, i + 3, j + 2, k + 2, s[8], s[8], true)?;
        ctx.generate_box(f, i - 3, j + 2, k - 3, i + 3, j + 2, k - 2, s[8], s[8], true)?;
        ctx.generate_box(f, i - 3, j + 2, k + 3, i + 3, j + 2, k + 3, s[8], s[8], true)?;
        ctx.generate_box(f, i - 3, -1, k - 3, i - 3, -1, k + 2, s[6], s[6], true)?;
        ctx.generate_box(f, i + 3, -1, k - 3, i + 3, -1, k + 2, s[6], s[6], true)?;
        ctx.generate_box(f, i - 3, -1, k - 3, i + 3, -1, k - 2, s[6], s[6], true)?;
        ctx.generate_box(f, i - 3, -1, k + 3, i + 3, -1, k + 3, s[6], s[6], true)?;
        roof = collapsed_roof(ctx, f, s, &mut region_random, i - 2, j + 4, k - 2, i + 2, k + 2)?;
        // blockState3 = s[9]
        // blockState4 = s[10]
        ctx.place_block(f, s[10], i, j, k)?;
        ctx.place_block(f, s[9], i + 1, j, k - 1)?;
        ctx.place_block(f, s[9], i + 1, j, k + 1)?;
        ctx.place_block(f, s[9], i - 1, j, k - 1)?;
        ctx.place_block(f, s[9], i - 1, j, k + 1)?;
        ctx.place_block(f, s[9], i + 2, j, k)?;
        ctx.place_block(f, s[9], i - 2, j, k)?;
        ctx.place_block(f, s[9], i, j, k + 2)?;
        ctx.place_block(f, s[9], i, j, k - 2)?;
        ctx.place_block(f, s[9], i + 3, j, k)?;
        ctx.place_block(f, s[6], i + 4, j + 1, k)?;
        ctx.place_block(f, s[8], i + 4, j + 2, k)?;
        ctx.place_block(f, s[9], i - 3, j, k)?;
        ctx.place_block(f, s[6], i - 4, j + 1, k)?;
        ctx.place_block(f, s[8], i - 4, j + 2, k)?;
        ctx.place_block(f, s[9], i, j, k + 3)?;
        ctx.place_block(f, s[9], i, j, k - 3)?;
        ctx.place_block(f, s[6], i, j + 1, k - 4)?;
        ctx.place_block(f, s[8], i, -2, k - 4)?;
        cellar = true;
        }
        Ok(())
    })();
    ctx.region_random = Some(region_random);
    result?;
    p.write(words);
    for (i, placed) in chests.iter().enumerate() {
        words[13 + i] = *placed as i64;
    }
    words[17] = (words[17] != 0 || cellar) as i64;
    words[18..21].copy_from_slice(&[roof[0] as i64, roof[1] as i64, roof[2] as i64]);
    Ok(true)
}

/// `placeCollapsedRoof`: sandstone or sand from the region random, then the
/// level seed's positional random picks the roof position.
#[allow(clippy::too_many_arguments)]
fn collapsed_roof(ctx: &mut Ctx, f: &Frame, s: &[u32], region_random: &mut Xoroshiro, i: i32, j: i32, k: i32, l: i32, m: i32) -> Result<[i32; 3]> {
    for n in i..=l {
        for o in k..=m {
            let state = if region_random.next_float() < 0.33 { s[0] } else { s[14] };
            ctx.place_block(f, state, n, j, o)?;
        }
    }
    let [x, y, z] = f.world(i, j, k);
    let mut random = positional_legacy(ctx.seed, x, y, z);
    let o = random.next_int_bound(l - i + 1) + i;
    let p = random.next_int_bound(m - k + 1) + k;
    Ok([f.world_x(o, p), f.world_y(j), f.world_z(o, p)])
}

/// `DesertPyramidStructure.afterPlace`: `words` is `[1, count, (box (6),
/// orientation, cellar placed, roof x, y, z) per pyramid piece]`.
pub(crate) fn pyramid_after_place(ctx: &mut Ctx, words: &[i64], center: [i32; 3]) -> Result<bool> {
    let (Some(sand), Some(suspicious)) = (ctx.names.state("minecraft:sand", &[]), ctx.names.state("minecraft:suspicious_sand", &[])) else {
        return Ok(false);
    };
    let Some(step) = ctx.g.brushable_step("minecraft:archaeology/desert_pyramid") else {
        return Ok(false);
    };
    let count = usize::try_from(words[1]).unwrap_or(0);
    if words.len() < 2 + count * 11 {
        return Ok(false);
    }
    // `SortedArraySet` by `Vec3i.compareTo`: Y, then Z, then X.
    let mut set = std::collections::BTreeSet::new();
    let place = |ctx: &mut Ctx, pos: [i32; 3]| -> Result<()> {
        if ctx.chunk_box.inside(pos) {
            ctx.set_block(pos, suspicious, 2)?;
            ctx.java_step(step, pos)?;
        }
        Ok(())
    };
    for piece in words[2..2 + count * 11].chunks_exact(11) {
        let frame = Frame { bbox: BoundingBox::from_words(&piece[0..6]), orientation: piece[6] as i32 };
        if piece[7] != 0 {
            let (i, j, k) = (16, -4, 13);
            let mut add = |x: i32, y: i32, z: i32| {
                let [wx, wy, wz] = frame.world(x, y, z);
                set.insert((wy, wz, wx));
            };
            for o in j + 1..=j + 3 {
                for p in i - 2..=i + 2 {
                    for q in k - 2..=k + 2 {
                        add(p, o, q);
                    }
                }
            }
            for (x, y, z) in [(i + 3, j + 1, k), (i + 3, j + 2, k), (i - 3, j + 1, k), (i - 3, j + 2, k), (i, j + 1, k + 3), (i, j + 2, k + 3),
                (i, j + 1, k - 3), (i, j + 2, k - 3)]
            {
                add(x, y, z);
            }
        }
        place(ctx, [piece[8] as i32, piece[9] as i32, piece[10] as i32])?;
    }
    let mut list: Vec<[i32; 3]> = set.iter().map(|&(y, z, x)| [x, y, z]).collect();
    let mut random = positional_legacy(ctx.seed, center[0], center[1], center[2]);
    // `Util.shuffle`.
    for i in (2..=list.len()).rev() {
        let j = random.next_int_bound(i as i32) as usize;
        list.swap(i - 1, j);
    }
    let mut remaining = (5 + random.next_int_bound(3)).min(list.len() as i32);
    for pos in list {
        if remaining > 0 {
            remaining -= 1;
            place(ctx, pos)?;
        } else if ctx.chunk_box.inside(pos) {
            ctx.set_block(pos, sand, 2)?;
        }
    }
    Ok(true)
}

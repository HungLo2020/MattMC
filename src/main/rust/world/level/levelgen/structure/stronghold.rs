//! `StrongholdPieces`: every piece's `postProcess`, small doors and the smooth stone selector.
use super::piece::{Ctx, Frame, EAST, NORTH};
use super::{resolve, Misc, StateList};
use crate::world::level::levelgen::decoration::random::WorldgenRandom;
use crate::world::level::levelgen::decoration::region::Result;
use std::sync::OnceLock;

/// `StrongholdPiece.SmallDoorType` ordinals.
#[allow(non_snake_case, non_upper_case_globals)]
mod Door {
    pub(super) const OPENING: i32 = 0;
    pub(super) const WOOD_DOOR: i32 = 1;
    pub(super) const GRATES: i32 = 2;
    pub(super) const IRON_DOOR: i32 = 3;
}

/// `SmoothStoneSelector`: edges are cracked, mossy, infested or plain stone bricks; inside is cave air.
#[derive(Clone, Copy)]
struct Smooth {
    cracked: u32,
    mossy: u32,
    infested: u32,
    bricks: u32,
    cave_air: u32,
}

impl Smooth {
    fn pick(&self, random: &mut WorldgenRandom, edge: bool) -> u32 {
        if !edge {
            return self.cave_air;
        }
        let f = random.next_float();
        if f < 0.2 {
            self.cracked
        } else if f < 0.5 {
            self.mossy
        } else if f < 0.55 {
            self.infested
        } else {
            self.bricks
        }
    }
}

/// A piece's fields and the shared states its body reads.
struct Fields {
    door: i32,
    ca: u32,
    smooth: Smooth,
    frames: [u32; 8],
    has_chest: bool,
    has_spawner: bool,
    tall: bool,
    left_low: bool,
    left_high: bool,
    right_low: bool,
    right_high: bool,
    left_child: bool,
    right_child: bool,
    steps: i32,
    room_type: i32,
}

static DOOR_STATES: OnceLock<Option<Box<[u32]>>> = OnceLock::new();
const DOOR_STATE_LIST: StateList = &[
    ("minecraft:cave_air", &[]),
    ("minecraft:stone_bricks", &[]),
    ("minecraft:oak_door", &[]),
    ("minecraft:oak_door", &[("half", "upper")]),
    ("minecraft:iron_bars", &[("west", "true")]),
    ("minecraft:iron_bars", &[("east", "true"), ("west", "true")]),
    ("minecraft:iron_bars", &[("east", "true")]),
    ("minecraft:iron_door", &[]),
    ("minecraft:iron_door", &[("half", "upper")]),
    ("minecraft:stone_button", &[("facing", "north")]),
    ("minecraft:stone_button", &[("facing", "south")]),
    ("minecraft:cracked_stone_bricks", &[]),
    ("minecraft:mossy_stone_bricks", &[]),
    ("minecraft:infested_stone_bricks", &[]),
    ("minecraft:end_portal_frame", &[("facing", "north"), ("eye", "false")]),
    ("minecraft:end_portal_frame", &[("facing", "north"), ("eye", "true")]),
    ("minecraft:end_portal_frame", &[("facing", "south"), ("eye", "false")]),
    ("minecraft:end_portal_frame", &[("facing", "south"), ("eye", "true")]),
    ("minecraft:end_portal_frame", &[("facing", "east"), ("eye", "false")]),
    ("minecraft:end_portal_frame", &[("facing", "east"), ("eye", "true")]),
    ("minecraft:end_portal_frame", &[("facing", "west"), ("eye", "false")]),
    ("minecraft:end_portal_frame", &[("facing", "west"), ("eye", "true")]),
];

/// `generateSmallDoor(level, random, box, type, i, j, k)`.
#[allow(clippy::too_many_arguments)]
fn small_door(ctx: &mut Ctx, f: &Frame, _s: &[u32], door: i32, i: i32, j: i32, k: i32) -> Result<()> {
    let d = resolve(&DOOR_STATES, &ctx.names, DOOR_STATE_LIST).expect("resolved before placement");
    let (ca, bricks) = (d[0], d[1]);
    match door {
        Door::OPENING => ctx.generate_box(f, i, j, k, i + 3 - 1, j + 3 - 1, k, ca, ca, false)?,
        Door::WOOD_DOOR | Door::IRON_DOOR => {
            for (x, y) in [(i, j), (i, j + 1), (i, j + 2), (i + 1, j + 2), (i + 2, j + 2), (i + 2, j + 1), (i + 2, j)] {
                ctx.place_block(f, bricks, x, y, k)?;
            }
            let (lower, upper) = if door == Door::WOOD_DOOR { (d[2], d[3]) } else { (d[7], d[8]) };
            ctx.place_block(f, lower, i + 1, j, k)?;
            ctx.place_block(f, upper, i + 1, j + 1, k)?;
            if door == Door::IRON_DOOR {
                ctx.place_block(f, d[9], i + 2, j + 1, k + 1)?;
                ctx.place_block(f, d[10], i + 2, j + 1, k - 1)?;
            }
        }
        Door::GRATES => {
            ctx.place_block(f, ca, i + 1, j, k)?;
            ctx.place_block(f, ca, i + 1, j + 1, k)?;
            ctx.place_block(f, d[4], i, j, k)?;
            ctx.place_block(f, d[4], i, j + 1, k)?;
            ctx.place_block(f, d[5], i, j + 2, k)?;
            ctx.place_block(f, d[5], i + 1, j + 2, k)?;
            ctx.place_block(f, d[5], i + 2, j + 2, k)?;
            ctx.place_block(f, d[6], i + 2, j + 1, k)?;
            ctx.place_block(f, d[6], i + 2, j, k)?;
        }
        _ => {}
    }
    Ok(())
}

/// Words after the header: the entry door, then each piece's fields (see `NativeStructurePieces` encoders).
pub(crate) fn place(kind: i64, ctx: &mut Ctx, words: &mut [i64], frame: Frame) -> Result<bool> {
    let (Some(s), Some(d)) = (resolve(&STRONGHOLD_STATES, &ctx.names, STRONGHOLD_STATE_LIST), resolve(&DOOR_STATES, &ctx.names, DOOR_STATE_LIST)) else {
        return Ok(false);
    };
    let w = |i: usize| words.get(i).copied().unwrap_or(0);
    let mut st = Fields {
        door: w(9) as i32,
        ca: d[0],
        smooth: Smooth { cracked: d[11], mossy: d[12], infested: d[13], bricks: d[1], cave_air: d[0] },
        frames: [d[14], d[15], d[16], d[17], d[18], d[19], d[20], d[21]],
        has_chest: kind == 10 && w(10) != 0,
        has_spawner: kind == 15 && w(10) != 0,
        tall: kind == 14 && w(10) != 0,
        left_low: w(10) != 0,
        left_high: w(11) != 0,
        right_low: w(12) != 0,
        right_high: w(13) != 0,
        left_child: w(10) != 0,
        right_child: w(11) != 0,
        steps: w(10) as i32,
        room_type: w(10) as i32,
    };
    let f = &frame;
    match kind {
        10 => chest_corridor(ctx, f, s, &mut st)?,
        11 => filler_corridor(ctx, f, s, &mut st)?,
        12 => five_crossing(ctx, f, s, &mut st)?,
        13 => left_turn(ctx, f, s, &mut st)?,
        14 => library(ctx, f, s, &mut st)?,
        15 => portal_room(ctx, f, s, &mut st)?,
        16 => prison_hall(ctx, f, s, &mut st)?,
        17 => right_turn(ctx, f, s, &mut st)?,
        18 => room_crossing(ctx, f, s, &mut st)?,
        19 => stairs_down(ctx, f, s, &mut st)?,
        20 => straight(ctx, f, s, &mut st)?,
        21 => straight_stairs_down(ctx, f, s, &mut st)?,
        _ => return Ok(false),
    }
    if kind == 10 {
        words[10] = st.has_chest as i64;
    }
    if kind == 15 {
        words[10] = st.has_spawner as i64;
    }
    Ok(true)
}

static STRONGHOLD_STATES: OnceLock<Option<Box<[u32]>>> = OnceLock::new();
const STRONGHOLD_STATE_LIST: StateList = &[
    ("minecraft:stone_bricks", &[]),
    ("minecraft:stone_brick_slab", &[]),
    ("minecraft:cave_air", &[]),
    ("minecraft:smooth_stone_slab", &[]),
    ("minecraft:smooth_stone_slab", &[("type", "double")]),
    ("minecraft:wall_torch", &[("facing", "south")]),
    ("minecraft:cobweb", &[]),
    ("minecraft:oak_planks", &[]),
    ("minecraft:wall_torch", &[("facing", "east")]),
    ("minecraft:wall_torch", &[("facing", "west")]),
    ("minecraft:bookshelf", &[]),
    ("minecraft:oak_fence", &[("east", "true"), ("west", "true")]),
    ("minecraft:oak_fence", &[("north", "true"), ("south", "true")]),
    ("minecraft:oak_fence", &[("east", "true"), ("north", "true")]),
    ("minecraft:oak_fence", &[("east", "true"), ("south", "true")]),
    ("minecraft:oak_fence", &[("north", "true"), ("west", "true")]),
    ("minecraft:oak_fence", &[("south", "true"), ("west", "true")]),
    ("minecraft:ladder", &[("facing", "south")]),
    ("minecraft:oak_fence", &[("east", "true")]),
    ("minecraft:oak_fence", &[("west", "true")]),
    ("minecraft:oak_fence", &[("east", "true"), ("north", "true"), ("south", "true"), ("west", "true")]),
    ("minecraft:torch", &[]),
    ("minecraft:lava", &[]),
    ("minecraft:iron_bars", &[("north", "true"), ("south", "true")]),
    ("minecraft:iron_bars", &[("east", "true"), ("west", "true")]),
    ("minecraft:stone_brick_stairs", &[("facing", "north")]),
    ("minecraft:end_portal_frame", &[("facing", "north")]),
    ("minecraft:end_portal_frame", &[("facing", "south")]),
    ("minecraft:end_portal_frame", &[("facing", "east")]),
    ("minecraft:end_portal_frame", &[("facing", "west")]),
    ("minecraft:end_portal", &[]),
    ("minecraft:spawner", &[]),
    ("minecraft:iron_bars", &[("east", "true"), ("north", "true"), ("south", "true")]),
    ("minecraft:iron_door", &[("facing", "west")]),
    ("minecraft:iron_door", &[("facing", "west"), ("half", "upper")]),
    ("minecraft:wall_torch", &[("facing", "north")]),
    ("minecraft:water", &[]),
    ("minecraft:cobblestone", &[]),
    ("minecraft:wall_torch", &[]),
    ("minecraft:ladder", &[("facing", "west")]),
    ("minecraft:cobblestone_stairs", &[("facing", "south")]),
];

#[allow(unused_mut, unused_variables, clippy::all)]
fn chest_corridor(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 4, 4, 6, true, &mut smooth)?;
    small_door(ctx, f, s, door, 1, 1, 0)?;
    small_door(ctx, f, s, Door::OPENING, 1, 1, 6)?;
    ctx.generate_box(f, 3, 1, 2, 3, 1, 4, s[0], s[0], false)?;
    ctx.place_block(f, s[1], 3, 1, 1)?;
    ctx.place_block(f, s[1], 3, 1, 5)?;
    ctx.place_block(f, s[1], 3, 2, 2)?;
    ctx.place_block(f, s[1], 3, 2, 4)?;
    for i in 2..=4 {
        ctx.place_block(f, s[1], 2, 1, i)?;
    }
    if !st.has_chest && ctx.chunk_box.inside(f.world(3, 2, 3)) {
        st.has_chest = true;
        ctx.create_chest(f.world(3, 2, 3), "minecraft:chests/stronghold_corridor", None)?;
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn filler_corridor(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    for i in 0..st.steps {
        ctx.place_block(f, s[0], 0, 0, i)?;
        ctx.place_block(f, s[0], 1, 0, i)?;
        ctx.place_block(f, s[0], 2, 0, i)?;
        ctx.place_block(f, s[0], 3, 0, i)?;
        ctx.place_block(f, s[0], 4, 0, i)?;
        for j in 1..=3 {
            ctx.place_block(f, s[0], 0, j, i)?;
            ctx.place_block(f, s[2], 1, j, i)?;
            ctx.place_block(f, s[2], 2, j, i)?;
            ctx.place_block(f, s[2], 3, j, i)?;
            ctx.place_block(f, s[0], 4, j, i)?;
        }
        ctx.place_block(f, s[0], 0, 4, i)?;
        ctx.place_block(f, s[0], 1, 4, i)?;
        ctx.place_block(f, s[0], 2, 4, i)?;
        ctx.place_block(f, s[0], 3, 4, i)?;
        ctx.place_block(f, s[0], 4, 4, i)?;
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn five_crossing(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 9, 8, 10, true, &mut smooth)?;
    small_door(ctx, f, s, door, 4, 3, 0)?;
    if st.left_low {
        ctx.generate_box(f, 0, 3, 1, 0, 5, 3, ca, ca, false)?;
    }
    if st.right_low {
        ctx.generate_box(f, 9, 3, 1, 9, 5, 3, ca, ca, false)?;
    }
    if st.left_high {
        ctx.generate_box(f, 0, 5, 7, 0, 7, 9, ca, ca, false)?;
    }
    if st.right_high {
        ctx.generate_box(f, 9, 5, 7, 9, 7, 9, ca, ca, false)?;
    }
    ctx.generate_box(f, 5, 1, 10, 7, 3, 10, ca, ca, false)?;
    ctx.generate_box_selector(f, 1, 2, 1, 8, 2, 6, false, &mut smooth)?;
    ctx.generate_box_selector(f, 4, 1, 5, 4, 4, 9, false, &mut smooth)?;
    ctx.generate_box_selector(f, 8, 1, 5, 8, 4, 9, false, &mut smooth)?;
    ctx.generate_box_selector(f, 1, 4, 7, 3, 4, 9, false, &mut smooth)?;
    ctx.generate_box_selector(f, 1, 3, 5, 3, 3, 6, false, &mut smooth)?;
    ctx.generate_box(f, 1, 3, 4, 3, 3, 4, s[3], s[3], false)?;
    ctx.generate_box(f, 1, 4, 6, 3, 4, 6, s[3], s[3], false)?;
    ctx.generate_box_selector(f, 5, 1, 7, 7, 1, 8, false, &mut smooth)?;
    ctx.generate_box(f, 5, 1, 9, 7, 1, 9, s[3], s[3], false)?;
    ctx.generate_box(f, 5, 2, 7, 7, 2, 7, s[3], s[3], false)?;
    ctx.generate_box(f, 4, 5, 7, 4, 5, 9, s[3], s[3], false)?;
    ctx.generate_box(f, 8, 5, 7, 8, 5, 9, s[3], s[3], false)?;
    ctx.generate_box(f, 5, 5, 7, 7, 5, 9, s[4], s[4], false)?;
    ctx.place_block(f, s[5], 6, 5, 6)?;
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn left_turn(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 4, 4, 4, true, &mut smooth)?;
    small_door(ctx, f, s, door, 1, 1, 0)?;
    let direction = f.orientation;
    if direction != NORTH && direction != EAST {
        ctx.generate_box(f, 4, 1, 1, 4, 3, 3, ca, ca, false)?;
    } else {
        ctx.generate_box(f, 0, 1, 1, 0, 3, 3, ca, ca, false)?;
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn library(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    let mut i = 11;
    if !st.tall {
        i = 6;
    }
    ctx.generate_box_selector(f, 0, 0, 0, 13, i - 1, 14, true, &mut smooth)?;
    small_door(ctx, f, s, door, 4, 1, 0)?;
    ctx.generate_maybe_box(f, 0.07, 2, 1, 1, 11, 4, 13, s[6], s[6], false, false)?;
    let mut j = 1;
    let mut k = 12;
    for l in 1..=13 {
        if (l - 1) % 4 == 0 {
            ctx.generate_box(f, 1, 1, l, 1, 4, l, s[7], s[7], false)?;
            ctx.generate_box(f, 12, 1, l, 12, 4, l, s[7], s[7], false)?;
            ctx.place_block(f, s[8], 2, 3, l)?;
            ctx.place_block(f, s[9], 11, 3, l)?;
            if st.tall {
                ctx.generate_box(f, 1, 6, l, 1, 9, l, s[7], s[7], false)?;
                ctx.generate_box(f, 12, 6, l, 12, 9, l, s[7], s[7], false)?;
            }
        } else {
            ctx.generate_box(f, 1, 1, l, 1, 4, l, s[10], s[10], false)?;
            ctx.generate_box(f, 12, 1, l, 12, 4, l, s[10], s[10], false)?;
            if st.tall {
                ctx.generate_box(f, 1, 6, l, 1, 9, l, s[10], s[10], false)?;
                ctx.generate_box(f, 12, 6, l, 12, 9, l, s[10], s[10], false)?;
            }
        }
    }
    for lx in (3..12).step_by((2) as usize) {
        ctx.generate_box(f, 3, 1, lx, 4, 3, lx, s[10], s[10], false)?;
        ctx.generate_box(f, 6, 1, lx, 7, 3, lx, s[10], s[10], false)?;
        ctx.generate_box(f, 9, 1, lx, 10, 3, lx, s[10], s[10], false)?;
    }
    if st.tall {
        ctx.generate_box(f, 1, 5, 1, 3, 5, 13, s[7], s[7], false)?;
        ctx.generate_box(f, 10, 5, 1, 12, 5, 13, s[7], s[7], false)?;
        ctx.generate_box(f, 4, 5, 1, 9, 5, 2, s[7], s[7], false)?;
        ctx.generate_box(f, 4, 5, 12, 9, 5, 13, s[7], s[7], false)?;
        ctx.place_block(f, s[7], 9, 5, 11)?;
        ctx.place_block(f, s[7], 8, 5, 11)?;
        ctx.place_block(f, s[7], 9, 5, 10)?;
        ctx.generate_box(f, 3, 6, 3, 3, 6, 11, s[12], s[12], false)?;
        ctx.generate_box(f, 10, 6, 3, 10, 6, 9, s[12], s[12], false)?;
        ctx.generate_box(f, 4, 6, 2, 9, 6, 2, s[11], s[11], false)?;
        ctx.generate_box(f, 4, 6, 12, 7, 6, 12, s[11], s[11], false)?;
        ctx.place_block(f, s[13], 3, 6, 2)?;
        ctx.place_block(f, s[14], 3, 6, 12)?;
        ctx.place_block(f, s[15], 10, 6, 2)?;
        for m in 0..=2 {
            ctx.place_block(f, s[16], 8 + m, 6, 12 - m)?;
            if m != 2 {
                ctx.place_block(f, s[13], 8 + m, 6, 11 - m)?;
            }
        }
        ctx.place_block(f, s[17], 10, 1, 13)?;
        ctx.place_block(f, s[17], 10, 2, 13)?;
        ctx.place_block(f, s[17], 10, 3, 13)?;
        ctx.place_block(f, s[17], 10, 4, 13)?;
        ctx.place_block(f, s[17], 10, 5, 13)?;
        ctx.place_block(f, s[17], 10, 6, 13)?;
        ctx.place_block(f, s[17], 10, 7, 13)?;
        let mut n = 7;
        let mut o = 7;
        ctx.place_block(f, s[18], 6, 9, 7)?;
        ctx.place_block(f, s[19], 7, 9, 7)?;
        ctx.place_block(f, s[18], 6, 8, 7)?;
        ctx.place_block(f, s[19], 7, 8, 7)?;
        ctx.place_block(f, s[20], 6, 7, 7)?;
        ctx.place_block(f, s[20], 7, 7, 7)?;
        ctx.place_block(f, s[18], 5, 7, 7)?;
        ctx.place_block(f, s[19], 8, 7, 7)?;
        ctx.place_block(f, s[13], 6, 7, 6)?;
        ctx.place_block(f, s[14], 6, 7, 8)?;
        ctx.place_block(f, s[15], 7, 7, 6)?;
        ctx.place_block(f, s[16], 7, 7, 8)?;
        ctx.place_block(f, s[21], 5, 8, 7)?;
        ctx.place_block(f, s[21], 8, 8, 7)?;
        ctx.place_block(f, s[21], 6, 8, 6)?;
        ctx.place_block(f, s[21], 6, 8, 8)?;
        ctx.place_block(f, s[21], 7, 8, 6)?;
        ctx.place_block(f, s[21], 7, 8, 8)?;
    }
    ctx.create_chest(f.world(3, 3, 5), "minecraft:chests/stronghold_library", None)?;
    if st.tall {
        ctx.place_block(f, ca, 12, 9, 1)?;
        ctx.create_chest(f.world(12, 8, 1), "minecraft:chests/stronghold_library", None)?;
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn portal_room(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 10, 7, 15, false, &mut smooth)?;
    small_door(ctx, f, s, Door::GRATES, 4, 1, 0)?;
    let mut i = 6;
    ctx.generate_box_selector(f, 1, 6, 1, 1, 6, 14, false, &mut smooth)?;
    ctx.generate_box_selector(f, 9, 6, 1, 9, 6, 14, false, &mut smooth)?;
    ctx.generate_box_selector(f, 2, 6, 1, 8, 6, 2, false, &mut smooth)?;
    ctx.generate_box_selector(f, 2, 6, 14, 8, 6, 14, false, &mut smooth)?;
    ctx.generate_box_selector(f, 1, 1, 1, 2, 1, 4, false, &mut smooth)?;
    ctx.generate_box_selector(f, 8, 1, 1, 9, 1, 4, false, &mut smooth)?;
    ctx.generate_box(f, 1, 1, 1, 1, 1, 3, s[22], s[22], false)?;
    ctx.generate_box(f, 9, 1, 1, 9, 1, 3, s[22], s[22], false)?;
    ctx.generate_box_selector(f, 3, 1, 8, 7, 1, 12, false, &mut smooth)?;
    ctx.generate_box(f, 4, 1, 9, 6, 1, 11, s[22], s[22], false)?;
    for j in (3..14).step_by((2) as usize) {
        ctx.generate_box(f, 0, 3, j, 0, 4, j, s[23], s[23], false)?;
        ctx.generate_box(f, 10, 3, j, 10, 4, j, s[23], s[23], false)?;
    }
    for j in (2..9).step_by((2) as usize) {
        ctx.generate_box(f, j, 3, 15, j, 4, 15, s[24], s[24], false)?;
    }
    ctx.generate_box_selector(f, 4, 1, 5, 6, 1, 7, false, &mut smooth)?;
    ctx.generate_box_selector(f, 4, 2, 6, 6, 2, 7, false, &mut smooth)?;
    ctx.generate_box_selector(f, 4, 3, 7, 6, 3, 7, false, &mut smooth)?;
    for k in 4..=6 {
        ctx.place_block(f, s[25], k, 1, 4)?;
        ctx.place_block(f, s[25], k, 2, 5)?;
        ctx.place_block(f, s[25], k, 3, 6)?;
    }
    let mut bl = true;
    let mut bls = [false; 12];
    for eye in bls.iter_mut() {
        *eye = ctx.random.next_float() > 0.9;
        bl &= *eye;
    }
    // `END_PORTAL_FRAME` facing north, south, east, west, each without and with an eye.
    for (index, (facing, x, z)) in [(0, 4, 8), (0, 5, 8), (0, 6, 8), (1, 4, 12), (1, 5, 12), (1, 6, 12), (2, 3, 9), (2, 3, 10), (2, 3, 11), (3, 7, 9),
        (3, 7, 10), (3, 7, 11)].into_iter().enumerate()
    {
        ctx.place_block(f, st.frames[facing * 2 + bls[index] as usize], x, 3, z)?;
    }
    if bl {
        ctx.place_block(f, s[30], 4, 3, 9)?;
        ctx.place_block(f, s[30], 5, 3, 9)?;
        ctx.place_block(f, s[30], 6, 3, 9)?;
        ctx.place_block(f, s[30], 4, 3, 10)?;
        ctx.place_block(f, s[30], 5, 3, 10)?;
        ctx.place_block(f, s[30], 6, 3, 10)?;
        ctx.place_block(f, s[30], 4, 3, 11)?;
        ctx.place_block(f, s[30], 5, 3, 11)?;
        ctx.place_block(f, s[30], 6, 3, 11)?;
    }
    if !st.has_spawner {
        let spawner_pos = f.world(5, 3, 6);
        if ctx.chunk_box.inside(spawner_pos) {
            st.has_spawner = true;
            ctx.set_block(spawner_pos, s[31], 2)?;
            let step = ctx.g.misc_step(Misc::SpawnerSilverfish);
            ctx.java_step(step, spawner_pos)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn prison_hall(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 8, 4, 10, true, &mut smooth)?;
    small_door(ctx, f, s, door, 1, 1, 0)?;
    ctx.generate_box(f, 1, 1, 10, 3, 3, 10, ca, ca, false)?;
    ctx.generate_box_selector(f, 4, 1, 1, 4, 3, 1, false, &mut smooth)?;
    ctx.generate_box_selector(f, 4, 1, 3, 4, 3, 3, false, &mut smooth)?;
    ctx.generate_box_selector(f, 4, 1, 7, 4, 3, 7, false, &mut smooth)?;
    ctx.generate_box_selector(f, 4, 1, 9, 4, 3, 9, false, &mut smooth)?;
    for i in 1..=3 {
        ctx.place_block(f, s[23], 4, i, 4)?;
        ctx.place_block(f, s[32], 4, i, 5)?;
        ctx.place_block(f, s[23], 4, i, 6)?;
        ctx.place_block(f, s[24], 5, i, 5)?;
        ctx.place_block(f, s[24], 6, i, 5)?;
        ctx.place_block(f, s[24], 7, i, 5)?;
    }
    ctx.place_block(f, s[23], 4, 3, 2)?;
    ctx.place_block(f, s[23], 4, 3, 8)?;
    ctx.place_block(f, s[33], 4, 1, 2)?;
    ctx.place_block(f, s[34], 4, 2, 2)?;
    ctx.place_block(f, s[33], 4, 1, 8)?;
    ctx.place_block(f, s[34], 4, 2, 8)?;
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn right_turn(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 4, 4, 4, true, &mut smooth)?;
    small_door(ctx, f, s, door, 1, 1, 0)?;
    let direction = f.orientation;
    if direction != NORTH && direction != EAST {
        ctx.generate_box(f, 0, 1, 1, 0, 3, 3, ca, ca, false)?;
    } else {
        ctx.generate_box(f, 4, 1, 1, 4, 3, 3, ca, ca, false)?;
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn room_crossing(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 10, 6, 10, true, &mut smooth)?;
    small_door(ctx, f, s, door, 4, 1, 0)?;
    ctx.generate_box(f, 4, 1, 10, 6, 3, 10, ca, ca, false)?;
    ctx.generate_box(f, 0, 1, 4, 0, 3, 6, ca, ca, false)?;
    ctx.generate_box(f, 10, 1, 4, 10, 3, 6, ca, ca, false)?;
    match st.room_type {
    0 => {
    ctx.place_block(f, s[0], 5, 1, 5)?;
    ctx.place_block(f, s[0], 5, 2, 5)?;
    ctx.place_block(f, s[0], 5, 3, 5)?;
    ctx.place_block(f, s[9], 4, 3, 5)?;
    ctx.place_block(f, s[8], 6, 3, 5)?;
    ctx.place_block(f, s[5], 5, 3, 4)?;
    ctx.place_block(f, s[35], 5, 3, 6)?;
    ctx.place_block(f, s[3], 4, 1, 4)?;
    ctx.place_block(f, s[3], 4, 1, 5)?;
    ctx.place_block(f, s[3], 4, 1, 6)?;
    ctx.place_block(f, s[3], 6, 1, 4)?;
    ctx.place_block(f, s[3], 6, 1, 5)?;
    ctx.place_block(f, s[3], 6, 1, 6)?;
    ctx.place_block(f, s[3], 5, 1, 4)?;
    ctx.place_block(f, s[3], 5, 1, 6)?;
    }
    1 => {
    for i in 0..5 {
    ctx.place_block(f, s[0], 3, 1, 3 + i)?;
    ctx.place_block(f, s[0], 7, 1, 3 + i)?;
    ctx.place_block(f, s[0], 3 + i, 1, 3)?;
    ctx.place_block(f, s[0], 3 + i, 1, 7)?;
    }
    ctx.place_block(f, s[0], 5, 1, 5)?;
    ctx.place_block(f, s[0], 5, 2, 5)?;
    ctx.place_block(f, s[0], 5, 3, 5)?;
    ctx.place_block(f, s[36], 5, 4, 5)?;
    }
    2 => {
    for i in 1..=9 {
    ctx.place_block(f, s[37], 1, 3, i)?;
    ctx.place_block(f, s[37], 9, 3, i)?;
    }
    for i in 1..=9 {
    ctx.place_block(f, s[37], i, 3, 1)?;
    ctx.place_block(f, s[37], i, 3, 9)?;
    }
    ctx.place_block(f, s[37], 5, 1, 4)?;
    ctx.place_block(f, s[37], 5, 1, 6)?;
    ctx.place_block(f, s[37], 5, 3, 4)?;
    ctx.place_block(f, s[37], 5, 3, 6)?;
    ctx.place_block(f, s[37], 4, 1, 5)?;
    ctx.place_block(f, s[37], 6, 1, 5)?;
    ctx.place_block(f, s[37], 4, 3, 5)?;
    ctx.place_block(f, s[37], 6, 3, 5)?;
    for i in 1..=3 {
    ctx.place_block(f, s[37], 4, i, 4)?;
    ctx.place_block(f, s[37], 6, i, 4)?;
    ctx.place_block(f, s[37], 4, i, 6)?;
    ctx.place_block(f, s[37], 6, i, 6)?;
    }
    ctx.place_block(f, s[38], 5, 3, 5)?;
    for i in 2..=8 {
    ctx.place_block(f, s[7], 2, 3, i)?;
    ctx.place_block(f, s[7], 3, 3, i)?;
    if i <= 3 || i >= 7 {
    ctx.place_block(f, s[7], 4, 3, i)?;
    ctx.place_block(f, s[7], 5, 3, i)?;
    ctx.place_block(f, s[7], 6, 3, i)?;
    }
    ctx.place_block(f, s[7], 7, 3, i)?;
    ctx.place_block(f, s[7], 8, 3, i)?;
    }
    ctx.place_block(f, s[39], 9, 1, 3)?;
    ctx.place_block(f, s[39], 9, 2, 3)?;
    ctx.place_block(f, s[39], 9, 3, 3)?;
    ctx.create_chest(f.world(3, 4, 8), "minecraft:chests/stronghold_crossing", None)?;
    }
    _ => {}
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn stairs_down(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 4, 10, 4, true, &mut smooth)?;
    small_door(ctx, f, s, door, 1, 7, 0)?;
    small_door(ctx, f, s, Door::OPENING, 1, 1, 4)?;
    ctx.place_block(f, s[0], 2, 6, 1)?;
    ctx.place_block(f, s[0], 1, 5, 1)?;
    ctx.place_block(f, s[3], 1, 6, 1)?;
    ctx.place_block(f, s[0], 1, 5, 2)?;
    ctx.place_block(f, s[0], 1, 4, 3)?;
    ctx.place_block(f, s[3], 1, 5, 3)?;
    ctx.place_block(f, s[0], 2, 4, 3)?;
    ctx.place_block(f, s[0], 3, 3, 3)?;
    ctx.place_block(f, s[3], 3, 4, 3)?;
    ctx.place_block(f, s[0], 3, 3, 2)?;
    ctx.place_block(f, s[0], 3, 2, 1)?;
    ctx.place_block(f, s[3], 3, 3, 1)?;
    ctx.place_block(f, s[0], 2, 2, 1)?;
    ctx.place_block(f, s[0], 1, 1, 1)?;
    ctx.place_block(f, s[3], 1, 2, 1)?;
    ctx.place_block(f, s[0], 1, 1, 2)?;
    ctx.place_block(f, s[3], 1, 1, 3)?;
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn straight(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 4, 4, 6, true, &mut smooth)?;
    small_door(ctx, f, s, door, 1, 1, 0)?;
    small_door(ctx, f, s, Door::OPENING, 1, 1, 6)?;
    ctx.maybe_generate_block(f, 0.1, 1, 2, 1, s[8])?;
    ctx.maybe_generate_block(f, 0.1, 3, 2, 1, s[9])?;
    ctx.maybe_generate_block(f, 0.1, 1, 2, 5, s[8])?;
    ctx.maybe_generate_block(f, 0.1, 3, 2, 5, s[9])?;
    if st.left_child {
        ctx.generate_box(f, 0, 1, 2, 0, 3, 4, ca, ca, false)?;
    }
    if st.right_child {
        ctx.generate_box(f, 4, 1, 2, 4, 3, 4, ca, ca, false)?;
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, clippy::all)]
fn straight_stairs_down(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let Fields { door, ca, .. } = *st;
    let sm = st.smooth;
    let mut smooth = |r: &mut WorldgenRandom, _: i32, _: i32, _: i32, edge: bool| sm.pick(r, edge);
    ctx.generate_box_selector(f, 0, 0, 0, 4, 10, 7, true, &mut smooth)?;
    small_door(ctx, f, s, door, 1, 7, 0)?;
    small_door(ctx, f, s, Door::OPENING, 1, 1, 7)?;
    for i in 0..6 {
        ctx.place_block(f, s[40], 1, 6 - i, 1 + i)?;
        ctx.place_block(f, s[40], 2, 6 - i, 1 + i)?;
        ctx.place_block(f, s[40], 3, 6 - i, 1 + i)?;
        if i < 5 {
            ctx.place_block(f, s[0], 1, 5 - i, 1 + i)?;
            ctx.place_block(f, s[0], 2, 5 - i, 1 + i)?;
            ctx.place_block(f, s[0], 3, 5 - i, 1 + i)?;
        }
    }
    Ok(())
}

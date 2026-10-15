//! `NetherFortressPieces`: every piece's `postProcess`.
use super::piece::{Ctx, Frame};
use super::{resolve, Misc, StateList};
use crate::world::level::levelgen::decoration::region::Result;
use crate::world::level::levelgen::random::Legacy;
use std::sync::OnceLock;

struct Fields {
    self_seed: i64,
    needs_chest: bool,
    has_spawner: bool,
}

static FORTRESS_STATES: OnceLock<Option<Box<[u32]>>> = OnceLock::new();
const FORTRESS_STATE_LIST: StateList = &[
    ("minecraft:nether_bricks", &[]),
    ("minecraft:air", &[]),
    ("minecraft:nether_brick_fence", &[("north", "true"), ("south", "true")]),
    ("minecraft:nether_brick_fence", &[("east", "true"), ("north", "true"), ("south", "true")]),
    ("minecraft:nether_brick_fence", &[("north", "true"), ("south", "true"), ("west", "true")]),
    ("minecraft:nether_brick_stairs", &[("facing", "south")]),
    ("minecraft:nether_brick_fence", &[("east", "true"), ("west", "true")]),
    ("minecraft:nether_brick_fence", &[("east", "true"), ("south", "true")]),
    ("minecraft:nether_brick_fence", &[("south", "true"), ("west", "true")]),
    ("minecraft:nether_brick_fence", &[]),
    ("minecraft:nether_brick_fence", &[("east", "true"), ("north", "true")]),
    ("minecraft:nether_brick_fence", &[("north", "true"), ("west", "true")]),
    ("minecraft:lava", &[]),
    ("minecraft:nether_brick_stairs", &[("facing", "north")]),
    ("minecraft:nether_brick_stairs", &[("facing", "east")]),
    ("minecraft:nether_brick_stairs", &[("facing", "west")]),
    ("minecraft:soul_sand", &[]),
    ("minecraft:nether_wart", &[]),
    ("minecraft:nether_brick_fence", &[("west", "true")]),
    ("minecraft:nether_brick_fence", &[("east", "true")]),
    ("minecraft:spawner", &[]),
];

/// Words after the header: `BridgeEndFiller`'s seed, the left and right turns' chest flag, or the throne's spawner flag.
pub(crate) fn place(kind: i64, ctx: &mut Ctx, words: &mut [i64], frame: Frame) -> Result<bool> {
    let Some(s) = resolve(&FORTRESS_STATES, &ctx.names, FORTRESS_STATE_LIST) else {
        return Ok(false);
    };
    let field = words.get(9).copied().unwrap_or(0);
    let mut st = Fields { self_seed: field, needs_chest: field != 0, has_spawner: field != 0 };
    let f = &frame;
    match kind {
        30 => bridge_crossing(ctx, f, s, &mut st)?,
        31 => bridge_end_filler(ctx, f, s, &mut st)?,
        32 => bridge_straight(ctx, f, s, &mut st)?,
        33 => corridor_stairs(ctx, f, s, &mut st)?,
        34 => corridor_t_balcony(ctx, f, s, &mut st)?,
        35 => castle_entrance(ctx, f, s, &mut st)?,
        36 => small_corridor_crossing(ctx, f, s, &mut st)?,
        37 => small_corridor_left_turn(ctx, f, s, &mut st)?,
        38 => small_corridor(ctx, f, s, &mut st)?,
        39 => small_corridor_right_turn(ctx, f, s, &mut st)?,
        40 => stalk_room(ctx, f, s, &mut st)?,
        41 => monster_throne(ctx, f, s, &mut st)?,
        42 => room_crossing(ctx, f, s, &mut st)?,
        43 => stairs_room(ctx, f, s, &mut st)?,
        _ => return Ok(false),
    }
    match kind {
        37 | 39 => words[9] = st.needs_chest as i64,
        41 => words[9] = st.has_spawner as i64,
        _ => {}
    }
    Ok(true)
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn bridge_crossing(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 7, 3, 0, 11, 4, 18, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 3, 7, 18, 4, 11, s[0], s[0], false)?;
    ctx.generate_box(f, 8, 5, 0, 10, 7, 18, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 5, 8, 18, 7, 10, s[1], s[1], false)?;
    ctx.generate_box(f, 7, 5, 0, 7, 5, 7, s[0], s[0], false)?;
    ctx.generate_box(f, 7, 5, 11, 7, 5, 18, s[0], s[0], false)?;
    ctx.generate_box(f, 11, 5, 0, 11, 5, 7, s[0], s[0], false)?;
    ctx.generate_box(f, 11, 5, 11, 11, 5, 18, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 5, 7, 7, 5, 7, s[0], s[0], false)?;
    ctx.generate_box(f, 11, 5, 7, 18, 5, 7, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 5, 11, 7, 5, 11, s[0], s[0], false)?;
    ctx.generate_box(f, 11, 5, 11, 18, 5, 11, s[0], s[0], false)?;
    ctx.generate_box(f, 7, 2, 0, 11, 2, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 7, 2, 13, 11, 2, 18, s[0], s[0], false)?;
    ctx.generate_box(f, 7, 0, 0, 11, 1, 3, s[0], s[0], false)?;
    ctx.generate_box(f, 7, 0, 15, 11, 1, 18, s[0], s[0], false)?;
    for i in 7..=11 {
        for j in 0..=2 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
            ctx.fill_column_down(f, s[0], i, -1, 18 - j)?;
        }
    }
    ctx.generate_box(f, 0, 2, 7, 5, 2, 11, s[0], s[0], false)?;
    ctx.generate_box(f, 13, 2, 7, 18, 2, 11, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 0, 7, 3, 1, 11, s[0], s[0], false)?;
    ctx.generate_box(f, 15, 0, 7, 18, 1, 11, s[0], s[0], false)?;
    for i in 0..=2 {
        for j in 7..=11 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
            ctx.fill_column_down(f, s[0], 18 - i, -1, j)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn bridge_end_filler(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    let mut random2 = Legacy::new(st.self_seed);
    for i in 0..=4 {
        for j in 3..=4 {
            let mut k = random2.next_int_bound(8);
            ctx.generate_box(f, i, j, 0, i, j, k, s[0], s[0], false)?;
        }
    }
    let mut i = random2.next_int_bound(8);
    ctx.generate_box(f, 0, 5, 0, 0, 5, i, s[0], s[0], false)?;
    i = random2.next_int_bound(8);
    ctx.generate_box(f, 4, 5, 0, 4, 5, i, s[0], s[0], false)?;
    for ix in 0..=4 {
        let mut j = random2.next_int_bound(5);
        ctx.generate_box(f, ix, 2, 0, ix, 2, j, s[0], s[0], false)?;
    }
    for ix in 0..=4 {
        for j in 0..=1 {
            let mut k = random2.next_int_bound(3);
            ctx.generate_box(f, ix, j, 0, ix, j, k, s[0], s[0], false)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn bridge_straight(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 3, 0, 4, 4, 18, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 5, 0, 3, 7, 18, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 5, 0, 0, 5, 18, s[0], s[0], false)?;
    ctx.generate_box(f, 4, 5, 0, 4, 5, 18, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 4, 2, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 13, 4, 2, 18, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 0, 0, 4, 1, 3, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 0, 15, 4, 1, 18, s[0], s[0], false)?;
    for i in 0..=4 {
        for j in 0..=2 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
            ctx.fill_column_down(f, s[0], i, -1, 18 - j)?;
        }
    }
    ctx.generate_box(f, 0, 1, 1, 0, 4, 1, s[3], s[3], false)?;
    ctx.generate_box(f, 0, 3, 4, 0, 4, 4, s[3], s[3], false)?;
    ctx.generate_box(f, 0, 3, 14, 0, 4, 14, s[3], s[3], false)?;
    ctx.generate_box(f, 0, 1, 17, 0, 4, 17, s[3], s[3], false)?;
    ctx.generate_box(f, 4, 1, 1, 4, 4, 1, s[4], s[4], false)?;
    ctx.generate_box(f, 4, 3, 4, 4, 4, 4, s[4], s[4], false)?;
    ctx.generate_box(f, 4, 3, 14, 4, 4, 14, s[4], s[4], false)?;
    ctx.generate_box(f, 4, 1, 17, 4, 4, 17, s[4], s[4], false)?;
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn corridor_stairs(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    // blockState = s[5]
    for i in 0..=9 {
        let mut j = jmax(1, 7 - i);
        let mut k = jmin(jmax(j + 5, 14 - i), 13);
        let mut l = i;
        ctx.generate_box(f, 0, 0, i, 4, j, i, s[0], s[0], false)?;
        ctx.generate_box(f, 1, j + 1, i, 3, k - 1, i, s[1], s[1], false)?;
        if i <= 6 {
            ctx.place_block(f, s[5], 1, j + 1, i)?;
            ctx.place_block(f, s[5], 2, j + 1, i)?;
            ctx.place_block(f, s[5], 3, j + 1, i)?;
        }
        ctx.generate_box(f, 0, k, i, 4, k, i, s[0], s[0], false)?;
        ctx.generate_box(f, 0, j + 1, i, 0, k - 1, i, s[0], s[0], false)?;
        ctx.generate_box(f, 4, j + 1, i, 4, k - 1, i, s[0], s[0], false)?;
        if (i & 1) == 0 {
            ctx.generate_box(f, 0, j + 2, i, 0, j + 3, i, s[2], s[2], false)?;
            ctx.generate_box(f, 4, j + 2, i, 4, j + 3, i, s[2], s[2], false)?;
        }
        for m in 0..=4 {
            ctx.fill_column_down(f, s[0], m, -1, l)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn corridor_t_balcony(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    // blockState = s[2]
    ctx.generate_box(f, 0, 0, 0, 8, 1, 8, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 8, 5, 8, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 6, 0, 8, 6, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 2, 5, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 6, 2, 0, 8, 5, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 3, 0, 1, 4, 0, s[6], s[6], false)?;
    ctx.generate_box(f, 7, 3, 0, 7, 4, 0, s[6], s[6], false)?;
    ctx.generate_box(f, 0, 2, 4, 8, 2, 8, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 1, 4, 2, 2, 4, s[1], s[1], false)?;
    ctx.generate_box(f, 6, 1, 4, 7, 2, 4, s[1], s[1], false)?;
    ctx.generate_box(f, 1, 3, 8, 7, 3, 8, s[6], s[6], false)?;
    ctx.place_block(f, s[7], 0, 3, 8)?;
    ctx.place_block(f, s[8], 8, 3, 8)?;
    ctx.generate_box(f, 0, 3, 6, 0, 3, 7, s[2], s[2], false)?;
    ctx.generate_box(f, 8, 3, 6, 8, 3, 7, s[2], s[2], false)?;
    ctx.generate_box(f, 0, 3, 4, 0, 5, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 8, 3, 4, 8, 5, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 3, 5, 2, 5, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 6, 3, 5, 7, 5, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 4, 5, 1, 5, 5, s[6], s[6], false)?;
    ctx.generate_box(f, 7, 4, 5, 7, 5, 5, s[6], s[6], false)?;
    for i in 0..=5 {
        for j in 0..=8 {
            ctx.fill_column_down(f, s[0], j, -1, i)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn castle_entrance(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 3, 0, 12, 4, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 5, 0, 12, 13, 12, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 5, 0, 1, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 11, 5, 0, 12, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 5, 11, 4, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 8, 5, 11, 10, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 9, 11, 7, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 5, 0, 4, 12, 1, s[0], s[0], false)?;
    ctx.generate_box(f, 8, 5, 0, 10, 12, 1, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 9, 0, 7, 12, 1, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 11, 2, 10, 12, 10, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 8, 0, 7, 8, 0, s[9], s[9], false)?;
    for i in (1..=11).step_by((2) as usize) {
        ctx.generate_box(f, i, 10, 0, i, 11, 0, s[6], s[6], false)?;
        ctx.generate_box(f, i, 10, 12, i, 11, 12, s[6], s[6], false)?;
        ctx.generate_box(f, 0, 10, i, 0, 11, i, s[2], s[2], false)?;
        ctx.generate_box(f, 12, 10, i, 12, 11, i, s[2], s[2], false)?;
        ctx.place_block(f, s[0], i, 13, 0)?;
        ctx.place_block(f, s[0], i, 13, 12)?;
        ctx.place_block(f, s[0], 0, 13, i)?;
        ctx.place_block(f, s[0], 12, 13, i)?;
        if i != 11 {
            ctx.place_block(f, s[6], i + 1, 13, 0)?;
            ctx.place_block(f, s[6], i + 1, 13, 12)?;
            ctx.place_block(f, s[2], 0, 13, i + 1)?;
            ctx.place_block(f, s[2], 12, 13, i + 1)?;
        }
    }
    ctx.place_block(f, s[10], 0, 13, 0)?;
    ctx.place_block(f, s[7], 0, 13, 12)?;
    ctx.place_block(f, s[8], 12, 13, 12)?;
    ctx.place_block(f, s[11], 12, 13, 0)?;
    for ix in (3..=9).step_by((2) as usize) {
        ctx.generate_box(f, 1, 7, ix, 1, 8, ix, s[4], s[4], false)?;
        ctx.generate_box(f, 11, 7, ix, 11, 8, ix, s[3], s[3], false)?;
    }
    ctx.generate_box(f, 4, 2, 0, 8, 2, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 4, 12, 2, 8, s[0], s[0], false)?;
    ctx.generate_box(f, 4, 0, 0, 8, 1, 3, s[0], s[0], false)?;
    ctx.generate_box(f, 4, 0, 9, 8, 1, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 0, 4, 3, 1, 8, s[0], s[0], false)?;
    ctx.generate_box(f, 9, 0, 4, 12, 1, 8, s[0], s[0], false)?;
    for ix in 4..=8 {
        for j in 0..=2 {
            ctx.fill_column_down(f, s[0], ix, -1, j)?;
            ctx.fill_column_down(f, s[0], ix, -1, 12 - j)?;
        }
    }
    for ix in 0..=2 {
        for j in 4..=8 {
            ctx.fill_column_down(f, s[0], ix, -1, j)?;
            ctx.fill_column_down(f, s[0], 12 - ix, -1, j)?;
        }
    }
    ctx.generate_box(f, 5, 5, 5, 7, 5, 7, s[0], s[0], false)?;
    ctx.generate_box(f, 6, 1, 6, 6, 4, 6, s[1], s[1], false)?;
    ctx.place_block(f, s[0], 6, 0, 6)?;
    ctx.place_block(f, s[12], 6, 5, 6)?;
    let spawn_pos = f.world(6, 5, 6);
    if ctx.chunk_box.inside(spawn_pos) {
        let lava = ctx.g.fluid_type(s[12]);
    ctx.region.schedule_tick(true, spawn_pos[0], spawn_pos[1], spawn_pos[2], lava as u32, 0);
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn small_corridor_crossing(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 0, 0, 4, 1, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 4, 5, 4, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 2, 0, 0, 5, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 4, 2, 0, 4, 5, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 4, 0, 5, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 4, 2, 4, 4, 5, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 6, 0, 4, 6, 4, s[0], s[0], false)?;
    for i in 0..=4 {
        for j in 0..=4 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn small_corridor_left_turn(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 0, 0, 4, 1, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 4, 5, 4, s[1], s[1], false)?;
    ctx.generate_box(f, 4, 2, 0, 4, 5, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 4, 3, 1, 4, 4, 1, s[2], s[2], false)?;
    ctx.generate_box(f, 4, 3, 3, 4, 4, 3, s[2], s[2], false)?;
    ctx.generate_box(f, 0, 2, 0, 0, 5, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 4, 3, 5, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 3, 4, 1, 4, 4, s[6], s[6], false)?;
    ctx.generate_box(f, 3, 3, 4, 3, 4, 4, s[6], s[6], false)?;
    if st.needs_chest && ctx.chunk_box.inside(f.world(3, 2, 3)) {
        st.needs_chest = false;
        ctx.create_chest(f.world(3, 2, 3), "minecraft:chests/nether_bridge", None)?;
    }
    ctx.generate_box(f, 0, 6, 0, 4, 6, 4, s[0], s[0], false)?;
    for i in 0..=4 {
        for j in 0..=4 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn small_corridor(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 0, 0, 4, 1, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 4, 5, 4, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 2, 0, 0, 5, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 4, 2, 0, 4, 5, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 3, 1, 0, 4, 1, s[2], s[2], false)?;
    ctx.generate_box(f, 0, 3, 3, 0, 4, 3, s[2], s[2], false)?;
    ctx.generate_box(f, 4, 3, 1, 4, 4, 1, s[2], s[2], false)?;
    ctx.generate_box(f, 4, 3, 3, 4, 4, 3, s[2], s[2], false)?;
    ctx.generate_box(f, 0, 6, 0, 4, 6, 4, s[0], s[0], false)?;
    for i in 0..=4 {
        for j in 0..=4 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn small_corridor_right_turn(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 0, 0, 4, 1, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 4, 5, 4, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 2, 0, 0, 5, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 3, 1, 0, 4, 1, s[2], s[2], false)?;
    ctx.generate_box(f, 0, 3, 3, 0, 4, 3, s[2], s[2], false)?;
    ctx.generate_box(f, 4, 2, 0, 4, 5, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 2, 4, 4, 5, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 3, 4, 1, 4, 4, s[6], s[6], false)?;
    ctx.generate_box(f, 3, 3, 4, 3, 4, 4, s[6], s[6], false)?;
    if st.needs_chest && ctx.chunk_box.inside(f.world(1, 2, 3)) {
        st.needs_chest = false;
        ctx.create_chest(f.world(1, 2, 3), "minecraft:chests/nether_bridge", None)?;
    }
    ctx.generate_box(f, 0, 6, 0, 4, 6, 4, s[0], s[0], false)?;
    for i in 0..=4 {
        for j in 0..=4 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn stalk_room(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 3, 0, 12, 4, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 5, 0, 12, 13, 12, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 5, 0, 1, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 11, 5, 0, 12, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 5, 11, 4, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 8, 5, 11, 10, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 9, 11, 7, 12, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 5, 0, 4, 12, 1, s[0], s[0], false)?;
    ctx.generate_box(f, 8, 5, 0, 10, 12, 1, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 9, 0, 7, 12, 1, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 11, 2, 10, 12, 10, s[0], s[0], false)?;
    for i in (1..=11).step_by((2) as usize) {
        ctx.generate_box(f, i, 10, 0, i, 11, 0, s[6], s[6], false)?;
        ctx.generate_box(f, i, 10, 12, i, 11, 12, s[6], s[6], false)?;
        ctx.generate_box(f, 0, 10, i, 0, 11, i, s[2], s[2], false)?;
        ctx.generate_box(f, 12, 10, i, 12, 11, i, s[2], s[2], false)?;
        ctx.place_block(f, s[0], i, 13, 0)?;
        ctx.place_block(f, s[0], i, 13, 12)?;
        ctx.place_block(f, s[0], 0, 13, i)?;
        ctx.place_block(f, s[0], 12, 13, i)?;
        if i != 11 {
            ctx.place_block(f, s[6], i + 1, 13, 0)?;
            ctx.place_block(f, s[6], i + 1, 13, 12)?;
            ctx.place_block(f, s[2], 0, 13, i + 1)?;
            ctx.place_block(f, s[2], 12, 13, i + 1)?;
        }
    }
    ctx.place_block(f, s[10], 0, 13, 0)?;
    ctx.place_block(f, s[7], 0, 13, 12)?;
    ctx.place_block(f, s[8], 12, 13, 12)?;
    ctx.place_block(f, s[11], 12, 13, 0)?;
    for ix in (3..=9).step_by((2) as usize) {
        ctx.generate_box(f, 1, 7, ix, 1, 8, ix, s[4], s[4], false)?;
        ctx.generate_box(f, 11, 7, ix, 11, 8, ix, s[3], s[3], false)?;
    }
    for j in 0..=6 {
        let mut k = j + 4;
        for l in 5..=7 {
            ctx.place_block(f, s[13], l, 5 + j, k)?;
        }
        if k >= 5 && k <= 8 {
            ctx.generate_box(f, 5, 5, k, 7, j + 4, k, s[0], s[0], false)?;
        } else if k >= 9 && k <= 10 {
            ctx.generate_box(f, 5, 8, k, 7, j + 4, k, s[0], s[0], false)?;
        }
        if j >= 1 {
            ctx.generate_box(f, 5, 6 + j, k, 7, 9 + j, k, s[1], s[1], false)?;
        }
    }
    for j in 5..=7 {
        ctx.place_block(f, s[13], j, 12, 11)?;
    }
    ctx.generate_box(f, 5, 6, 7, 5, 7, 7, s[3], s[3], false)?;
    ctx.generate_box(f, 7, 6, 7, 7, 7, 7, s[4], s[4], false)?;
    ctx.generate_box(f, 5, 13, 12, 7, 13, 12, s[1], s[1], false)?;
    ctx.generate_box(f, 2, 5, 2, 3, 5, 3, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 5, 9, 3, 5, 10, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 5, 4, 2, 5, 8, s[0], s[0], false)?;
    ctx.generate_box(f, 9, 5, 2, 10, 5, 3, s[0], s[0], false)?;
    ctx.generate_box(f, 9, 5, 9, 10, 5, 10, s[0], s[0], false)?;
    ctx.generate_box(f, 10, 5, 4, 10, 5, 8, s[0], s[0], false)?;
    ctx.place_block(f, s[15], 4, 5, 2)?;
    ctx.place_block(f, s[15], 4, 5, 3)?;
    ctx.place_block(f, s[15], 4, 5, 9)?;
    ctx.place_block(f, s[15], 4, 5, 10)?;
    ctx.place_block(f, s[14], 8, 5, 2)?;
    ctx.place_block(f, s[14], 8, 5, 3)?;
    ctx.place_block(f, s[14], 8, 5, 9)?;
    ctx.place_block(f, s[14], 8, 5, 10)?;
    ctx.generate_box(f, 3, 4, 4, 4, 4, 8, s[16], s[16], false)?;
    ctx.generate_box(f, 8, 4, 4, 9, 4, 8, s[16], s[16], false)?;
    ctx.generate_box(f, 3, 5, 4, 4, 5, 8, s[17], s[17], false)?;
    ctx.generate_box(f, 8, 5, 4, 9, 5, 8, s[17], s[17], false)?;
    ctx.generate_box(f, 4, 2, 0, 8, 2, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 4, 12, 2, 8, s[0], s[0], false)?;
    ctx.generate_box(f, 4, 0, 0, 8, 1, 3, s[0], s[0], false)?;
    ctx.generate_box(f, 4, 0, 9, 8, 1, 12, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 0, 4, 3, 1, 8, s[0], s[0], false)?;
    ctx.generate_box(f, 9, 0, 4, 12, 1, 8, s[0], s[0], false)?;
    for l in 4..=8 {
        for m in 0..=2 {
            ctx.fill_column_down(f, s[0], l, -1, m)?;
            ctx.fill_column_down(f, s[0], l, -1, 12 - m)?;
        }
    }
    for l in 0..=2 {
        for m in 4..=8 {
            ctx.fill_column_down(f, s[0], l, -1, m)?;
            ctx.fill_column_down(f, s[0], 12 - l, -1, m)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn monster_throne(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 2, 0, 6, 7, 7, s[1], s[1], false)?;
    ctx.generate_box(f, 1, 0, 0, 5, 1, 7, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 2, 1, 5, 2, 7, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 3, 2, 5, 3, 7, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 4, 3, 5, 4, 7, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 2, 0, 1, 4, 2, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 2, 0, 5, 4, 2, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 5, 2, 1, 5, 3, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 5, 2, 5, 5, 3, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 5, 3, 0, 5, 8, s[0], s[0], false)?;
    ctx.generate_box(f, 6, 5, 3, 6, 5, 8, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 5, 8, 5, 5, 8, s[0], s[0], false)?;
    ctx.place_block(f, s[18], 1, 6, 3)?;
    ctx.place_block(f, s[19], 5, 6, 3)?;
    ctx.place_block(f, s[10], 0, 6, 3)?;
    ctx.place_block(f, s[11], 6, 6, 3)?;
    ctx.generate_box(f, 0, 6, 4, 0, 6, 7, s[2], s[2], false)?;
    ctx.generate_box(f, 6, 6, 4, 6, 6, 7, s[2], s[2], false)?;
    ctx.place_block(f, s[7], 0, 6, 8)?;
    ctx.place_block(f, s[8], 6, 6, 8)?;
    ctx.generate_box(f, 1, 6, 8, 5, 6, 8, s[6], s[6], false)?;
    ctx.place_block(f, s[19], 1, 7, 8)?;
    ctx.generate_box(f, 2, 7, 8, 4, 7, 8, s[6], s[6], false)?;
    ctx.place_block(f, s[18], 5, 7, 8)?;
    ctx.place_block(f, s[19], 2, 8, 8)?;
    ctx.place_block(f, s[6], 3, 8, 8)?;
    ctx.place_block(f, s[18], 4, 8, 8)?;
    if !st.has_spawner {
        let spawn_pos = f.world(3, 5, 5);
        if ctx.chunk_box.inside(spawn_pos) {
            st.has_spawner = true;
            ctx.set_block(spawn_pos, s[20], 2)?;
            let step = ctx.g.misc_step(Misc::SpawnerBlaze);
            ctx.java_step(step, spawn_pos)?;
        }
    }
    for i in 0..=6 {
        for j in 0..=6 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn room_crossing(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 0, 0, 6, 1, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 6, 7, 6, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 2, 0, 1, 6, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 6, 1, 6, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 2, 0, 6, 6, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 2, 6, 6, 6, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 0, 6, 1, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 5, 0, 6, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 6, 2, 0, 6, 6, 1, s[0], s[0], false)?;
    ctx.generate_box(f, 6, 2, 5, 6, 6, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 6, 0, 4, 6, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 5, 0, 4, 5, 0, s[6], s[6], false)?;
    ctx.generate_box(f, 2, 6, 6, 4, 6, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 5, 6, 4, 5, 6, s[6], s[6], false)?;
    ctx.generate_box(f, 0, 6, 2, 0, 6, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 5, 2, 0, 5, 4, s[2], s[2], false)?;
    ctx.generate_box(f, 6, 6, 2, 6, 6, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 6, 5, 2, 6, 5, 4, s[2], s[2], false)?;
    for i in 0..=6 {
        for j in 0..=6 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
        }
    }
    Ok(())
}

#[allow(unused_mut, unused_variables, non_snake_case, clippy::all)]
fn stairs_room(ctx: &mut Ctx, f: &Frame, s: &[u32], st: &mut Fields) -> Result<()> {
    ctx.generate_box(f, 0, 0, 0, 6, 1, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 0, 6, 10, 6, s[1], s[1], false)?;
    ctx.generate_box(f, 0, 2, 0, 1, 8, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 5, 2, 0, 6, 8, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 2, 1, 0, 8, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 6, 2, 1, 6, 8, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 2, 6, 5, 8, 6, s[0], s[0], false)?;
    ctx.generate_box(f, 0, 3, 2, 0, 5, 4, s[2], s[2], false)?;
    ctx.generate_box(f, 6, 3, 2, 6, 5, 2, s[2], s[2], false)?;
    ctx.generate_box(f, 6, 3, 4, 6, 5, 4, s[2], s[2], false)?;
    ctx.place_block(f, s[0], 5, 2, 5)?;
    ctx.generate_box(f, 4, 2, 5, 4, 3, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 3, 2, 5, 3, 4, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 2, 5, 2, 5, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 2, 5, 1, 6, 5, s[0], s[0], false)?;
    ctx.generate_box(f, 1, 7, 1, 5, 7, 4, s[0], s[0], false)?;
    ctx.generate_box(f, 6, 8, 2, 6, 8, 4, s[1], s[1], false)?;
    ctx.generate_box(f, 2, 6, 0, 4, 8, 0, s[0], s[0], false)?;
    ctx.generate_box(f, 2, 5, 0, 4, 5, 0, s[6], s[6], false)?;
    for i in 0..=6 {
        for j in 0..=6 {
            ctx.fill_column_down(f, s[0], i, -1, j)?;
        }
    }
    Ok(())
}

/// `Math.max(int, int)` / `Math.min(int, int)`.
fn jmax(a: i32, b: i32) -> i32 {
    a.max(b)
}

fn jmin(a: i32, b: i32) -> i32 {
    a.min(b)
}

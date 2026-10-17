//! `OceanMonumentPieces`: the building's `postProcess`, which places its
//! intersecting child rooms in the same call, over a room graph Java hands in.
use super::piece::{BoundingBox, Ctx, Frame};
use super::{resolve, Misc, StateList};
use crate::world::level::levelgen::decoration::region::Result;
use std::sync::OnceLock;

/// `Direction.get3DDataValue()`.
const DOWN: usize = 0;
const UP: usize = 1;
const N3: usize = 2;
const S3: usize = 3;
const W3: usize = 4;
const E3: usize = 5;

static MONUMENT_STATES: OnceLock<Option<Box<[u32]>>> = OnceLock::new();
const MONUMENT_STATE_LIST: StateList = &[
    ("minecraft:prismarine", &[]),
    ("minecraft:prismarine_bricks", &[]),
    ("minecraft:dark_prismarine", &[]),
    ("minecraft:sea_lantern", &[]),
    ("minecraft:water", &[]),
    ("minecraft:gold_block", &[]),
    ("minecraft:wet_sponge", &[]),
    ("minecraft:air", &[]),
];

struct States {
    gray: u32,
    light: u32,
    black: u32,
    lamp: u32,
    water: u32,
    gold: u32,
    sponge: u32,
    air: u32,
    /// `FILL_KEEP`'s blocks: ice, packed ice, blue ice, water.
    keep: [crate::content::block::BlockId; 4],
    sea_level: i32,
}

/// The room graph: per definition its index, openings and connections (slots).
struct Rooms {
    index: Vec<i64>,
    open: Vec<u8>,
    conn: Vec<[i64; 6]>,
}

impl Rooms {
    fn index(&self, room: usize) -> i64 {
        self.index[room]
    }
    fn open(&self, room: usize, d: usize) -> bool {
        self.open[room] >> d & 1 != 0
    }
    fn conn(&self, room: usize, d: usize) -> Option<usize> {
        usize::try_from(self.conn[room][d]).ok()
    }
    /// `connections[d]`, which Java dereferences: present by construction.
    fn to(&self, room: usize, d: usize) -> usize {
        self.conn(room, d).expect("monument room connection")
    }
    fn count(&self, room: usize) -> u32 {
        (self.open[room] & 63).count_ones()
    }
}

/// Words after the header: room count, per room `[index, opening
/// bits, connection slot ×6]`, child count, per child `[kind, box (6),
/// orientation, room slot, main design]`. Child kinds: entry 0, core 1, double
/// X 2, XY 3, Y 4, YZ 5, Z 6, simple 7, simple top 8, wing 9, penthouse 10.
pub(crate) fn place(ctx: &mut Ctx, words: &mut [i64], frame: Frame) -> Result<bool> {
    let Some(s) = resolve(&MONUMENT_STATES, &ctx.names, MONUMENT_STATE_LIST) else {
        return Ok(false);
    };
    let block = |name: &str| ctx.names.block(name);
    let (Some(ice), Some(packed), Some(blue), Some(water)) =
        (block("minecraft:ice"), block("minecraft:packed_ice"), block("minecraft:blue_ice"), block("minecraft:water"))
    else {
        return Ok(false);
    };
    let st = States {
        gray: s[0],
        light: s[1],
        black: s[2],
        lamp: s[3],
        water: s[4],
        gold: s[5],
        sponge: s[6],
        air: s[7],
        keep: [ice, packed, blue, water],
        sea_level: ctx.sea_level,
    };
    let Some(&rooms) = words.get(9) else { return Ok(false) };
    let rooms = usize::try_from(rooms).unwrap_or(usize::MAX);
    let mut at = 10;
    if words.len() < at + rooms.saturating_mul(8) + 1 {
        return Ok(false);
    }
    let mut r = Rooms { index: Vec::with_capacity(rooms), open: Vec::with_capacity(rooms), conn: Vec::with_capacity(rooms) };
    for _ in 0..rooms {
        let w = &words[at..at + 8];
        r.index.push(w[0]);
        r.open.push(w[1] as u8);
        r.conn.push([w[2], w[3], w[4], w[5], w[6], w[7]]);
        at += 8;
    }
    let children = usize::try_from(words[at]).unwrap_or(usize::MAX);
    at += 1;
    if words.len() < at + children.saturating_mul(10) {
        return Ok(false);
    }
    if r.conn.iter().flatten().any(|&c| c >= rooms as i64) || (0..children).any(|c| words[at + c * 10 + 8] >= rooms as i64) {
        return Ok(false);
    }
    building(ctx, &frame, &st)?;
    for c in 0..children {
        let w = &words[at + c * 10..at + c * 10 + 10];
        let child = Frame { bbox: BoundingBox::from_words(&w[1..7]), orientation: w[7] as i32 };
        if !child.bbox.intersects(&ctx.chunk_box) {
            continue;
        }
        let room = usize::try_from(w[8]).unwrap_or(0);
        let main_design = w[9];
        let f = &child;
        match w[0] {
            0 => entry_room(ctx, f, &st, &r, room, main_design)?,
            1 => core_room(ctx, f, &st, &r, room, main_design)?,
            2 => double_x_room(ctx, f, &st, &r, room, main_design)?,
            3 => double_xy_room(ctx, f, &st, &r, room, main_design)?,
            4 => double_y_room(ctx, f, &st, &r, room, main_design)?,
            5 => double_yz_room(ctx, f, &st, &r, room, main_design)?,
            6 => double_z_room(ctx, f, &st, &r, room, main_design)?,
            7 => simple_room(ctx, f, &st, &r, room, main_design)?,
            8 => simple_top_room(ctx, f, &st, &r, room, main_design)?,
            9 => wing_room(ctx, f, &st, &r, room, main_design)?,
            10 => penthouse(ctx, f, &st, &r, room, main_design)?,
            _ => {}
        }
    }
    Ok(true)
}

/// `generateWaterBox`: water, or air at and above sea level unless already
/// water; `FILL_KEEP` blocks stay.
#[allow(clippy::too_many_arguments)]
fn water_box(ctx: &mut Ctx, f: &Frame, st: &States, x0: i32, y0: i32, z0: i32, x1: i32, y1: i32, z1: i32) -> Result<()> {
    for y in y0..=y1 {
        for x in x0..=x1 {
            for z in z0..=z1 {
                let state = ctx.get_block(f, x, y, z)?;
                if !st.keep.contains(&ctx.block_of(state)) {
                    let fill = if f.world_y(y) >= st.sea_level && state != st.water { st.air } else { st.water };
                    ctx.place_block(f, fill, x, y, z)?;
                }
            }
        }
    }
    Ok(())
}

/// `generateDefaultFloor`.
fn default_floor(ctx: &mut Ctx, f: &Frame, st: &States, i: i32, j: i32, bl: bool) -> Result<()> {
    if bl {
        ctx.generate_box(f, i, 0, j, i + 2, 0, j + 7, st.gray, st.gray, false)?;
        ctx.generate_box(f, i + 5, 0, j, i + 7, 0, j + 7, st.gray, st.gray, false)?;
        ctx.generate_box(f, i + 3, 0, j, i + 4, 0, j + 2, st.gray, st.gray, false)?;
        ctx.generate_box(f, i + 3, 0, j + 5, i + 4, 0, j + 7, st.gray, st.gray, false)?;
        ctx.generate_box(f, i + 3, 0, j + 2, i + 4, 0, j + 2, st.light, st.light, false)?;
        ctx.generate_box(f, i + 3, 0, j + 5, i + 4, 0, j + 5, st.light, st.light, false)?;
        ctx.generate_box(f, i + 2, 0, j + 3, i + 2, 0, j + 4, st.light, st.light, false)?;
        ctx.generate_box(f, i + 5, 0, j + 3, i + 5, 0, j + 4, st.light, st.light, false)?;
    } else {
        ctx.generate_box(f, i, 0, j, i + 7, 0, j + 7, st.gray, st.gray, false)?;
    }
    Ok(())
}

/// `generateBoxOnFillOnly`: only where the block is exactly still water.
#[allow(clippy::too_many_arguments)]
fn fill_only(ctx: &mut Ctx, f: &Frame, st: &States, x0: i32, y0: i32, z0: i32, x1: i32, y1: i32, z1: i32, state: u32) -> Result<()> {
    for y in y0..=y1 {
        for x in x0..=x1 {
            for z in z0..=z1 {
                if ctx.get_block(f, x, y, z)? == st.water {
                    ctx.place_block(f, state, x, y, z)?;
                }
            }
        }
    }
    Ok(())
}

/// `chunkIntersects`: the chunk's box against a local XZ rectangle.
fn chunk_intersects(ctx: &Ctx, f: &Frame, x0: i32, z0: i32, x1: i32, z1: i32) -> bool {
    let (m, n, o, p) = (f.world_x(x0, z0), f.world_z(x0, z0), f.world_x(x1, z1), f.world_z(x1, z1));
    let b = ctx.chunk_box;
    b.max_x >= m.min(o) && b.min_x <= m.max(o) && b.max_z >= n.min(p) && b.min_z <= n.max(p)
}

/// `spawnElder`: an elder guardian (a Java step) when inside the chunk's box.
fn spawn_elder(ctx: &mut Ctx, f: &Frame, x: i32, y: i32, z: i32) -> Result<()> {
    let pos = f.world(x, y, z);
    if ctx.chunk_box.inside(pos) {
        let step = ctx.g.misc_step(Misc::ElderGuardian);
        ctx.java_step(step, pos)?;
    }
    Ok(())
}

/// `MonumentBuilding.postProcess` up to its children.
fn building(ctx: &mut Ctx, f: &Frame, st: &States) -> Result<()> {
    let i = st.sea_level.max(64) - f.bbox.min_y;
    water_box(ctx, f, st, 0, 0, 0, 58, i, 58)?;
    generate_wing(ctx, f, st, false, 0)?;
    generate_wing(ctx, f, st, true, 33)?;
    generate_entrance_archs(ctx, f, st)?;
    generate_entrance_wall(ctx, f, st)?;
    generate_roof_piece(ctx, f, st)?;
    generate_lower_wall(ctx, f, st)?;
    generate_middle_wall(ctx, f, st)?;
    generate_upper_wall(ctx, f, st)?;
    for j in 0..7 {
        let mut k = 0;
        while k < 7 {
            if k == 0 && j == 3 {
                k = 6;
            }
            let (l, m) = (j * 9, k * 9);
            for n in 0..4 {
                for o in 0..4 {
                    ctx.place_block(f, st.light, l + n, 0, m + o)?;
                    ctx.fill_column_down(f, st.light, l + n, -1, m + o)?;
                }
            }
            if j != 0 && j != 6 {
                k += 6;
            } else {
                k += 1;
            }
        }
    }
    for j in 0..5 {
        water_box(ctx, f, st, -1 - j, j * 2, -1 - j, -1 - j, 23, 58 + j)?;
        water_box(ctx, f, st, 58 + j, j * 2, -1 - j, 58 + j, 23, 58 + j)?;
        water_box(ctx, f, st, -j, j * 2, -1 - j, 57 + j, 23, -1 - j)?;
        water_box(ctx, f, st, -j, j * 2, 58 + j, 57 + j, 23, 58 + j)?;
    }
    Ok(())
}

/// `MonumentBuilding.generateWing`.
fn generate_wing(ctx: &mut Ctx, f: &Frame, st: &States, bl: bool, i: i32) -> Result<()> {
    if chunk_intersects(ctx, f, i, 0, i + 23, 20) {
        ctx.generate_box(f, i + 0, 0, 0, i + 24, 0, 20, st.gray, st.gray, false)?;
        water_box(ctx, f, st, i + 0, 1, 0, i + 24, 10, 20)?;

        for k in 0..4 {
            ctx.generate_box(f, i + k, k + 1, k, i + k, k + 1, 20, st.light, st.light, false)?;
            ctx.generate_box(f, i + k + 7, k + 5, k + 7, i + k + 7, k + 5, 20, st.light, st.light, false)?;
            ctx.generate_box(f, i + 17 - k, k + 5, k + 7, i + 17 - k, k + 5, 20, st.light, st.light, false)?;
            ctx.generate_box(f, i + 24 - k, k + 1, k, i + 24 - k, k + 1, 20, st.light, st.light, false)?;
            ctx.generate_box(f, i + k + 1, k + 1, k, i + 23 - k, k + 1, k, st.light, st.light, false)?;
            ctx.generate_box(f, i + k + 8, k + 5, k + 7, i + 16 - k, k + 5, k + 7, st.light, st.light, false)?;
        }

        ctx.generate_box(f, i + 4, 4, 4, i + 6, 4, 20, st.gray, st.gray, false)?;
        ctx.generate_box(f, i + 7, 4, 4, i + 17, 4, 6, st.gray, st.gray, false)?;
        ctx.generate_box(f, i + 18, 4, 4, i + 20, 4, 20, st.gray, st.gray, false)?;
        ctx.generate_box(f, i + 11, 8, 11, i + 13, 8, 20, st.gray, st.gray, false)?;
        ctx.place_block(f, st.light, i + 12, 9, 12)?;
        ctx.place_block(f, st.light, i + 12, 9, 15)?;
        ctx.place_block(f, st.light, i + 12, 9, 18)?;
        let k = i + if bl { 19 } else { 5 };
        let l = i + if bl { 5 } else { 19 };

        for m in (5..=20).rev().step_by(3) {
            // CHECK descending
            ctx.place_block(f, st.light, k, 5, m)?;
        }

        for m in (7..=19).rev().step_by(3) {
            // CHECK descending
            ctx.place_block(f, st.light, l, 5, m)?;
        }

        for m in 0..4 {
            let n = if bl { i + 24 - (17 - m * 3) } else { i + 17 - m * 3 };
            ctx.place_block(f, st.light, n, 5, 5)?;
        }

        ctx.place_block(f, st.light, l, 5, 5)?;
        ctx.generate_box(f, i + 11, 1, 12, i + 13, 7, 12, st.gray, st.gray, false)?;
        ctx.generate_box(f, i + 12, 1, 11, i + 12, 7, 13, st.gray, st.gray, false)?;
    }

    Ok(())
}

/// `MonumentBuilding.generateEntranceArchs`.
fn generate_entrance_archs(ctx: &mut Ctx, f: &Frame, st: &States) -> Result<()> {
    if chunk_intersects(ctx, f, 22, 5, 35, 17) {
        water_box(ctx, f, st, 25, 0, 0, 32, 8, 20)?;

        for i in 0..4 {
            ctx.generate_box(f, 24, 2, 5 + i * 4, 24, 4, 5 + i * 4, st.light, st.light, false)?;
            ctx.generate_box(f, 22, 4, 5 + i * 4, 23, 4, 5 + i * 4, st.light, st.light, false)?;
            ctx.place_block(f, st.light, 25, 5, 5 + i * 4)?;
            ctx.place_block(f, st.light, 26, 6, 5 + i * 4)?;
            ctx.place_block(f, st.lamp, 26, 5, 5 + i * 4)?;
            ctx.generate_box(f, 33, 2, 5 + i * 4, 33, 4, 5 + i * 4, st.light, st.light, false)?;
            ctx.generate_box(f, 34, 4, 5 + i * 4, 35, 4, 5 + i * 4, st.light, st.light, false)?;
            ctx.place_block(f, st.light, 32, 5, 5 + i * 4)?;
            ctx.place_block(f, st.light, 31, 6, 5 + i * 4)?;
            ctx.place_block(f, st.lamp, 31, 5, 5 + i * 4)?;
            ctx.generate_box(f, 27, 6, 5 + i * 4, 30, 6, 5 + i * 4, st.gray, st.gray, false)?;
        }
    }

    Ok(())
}

/// `MonumentBuilding.generateEntranceWall`.
fn generate_entrance_wall(ctx: &mut Ctx, f: &Frame, st: &States) -> Result<()> {
    if chunk_intersects(ctx, f, 15, 20, 42, 21) {
        ctx.generate_box(f, 15, 0, 21, 42, 0, 21, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 26, 1, 21, 31, 3, 21)?;
        ctx.generate_box(f, 21, 12, 21, 36, 12, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 17, 11, 21, 40, 11, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 16, 10, 21, 41, 10, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 15, 7, 21, 42, 9, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 16, 6, 21, 41, 6, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 17, 5, 21, 40, 5, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 21, 4, 21, 36, 4, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 22, 3, 21, 26, 3, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 31, 3, 21, 35, 3, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 23, 2, 21, 25, 2, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 32, 2, 21, 34, 2, 21, st.gray, st.gray, false)?;
        ctx.generate_box(f, 28, 4, 20, 29, 4, 21, st.light, st.light, false)?;
        ctx.place_block(f, st.light, 27, 3, 21)?;
        ctx.place_block(f, st.light, 30, 3, 21)?;
        ctx.place_block(f, st.light, 26, 2, 21)?;
        ctx.place_block(f, st.light, 31, 2, 21)?;
        ctx.place_block(f, st.light, 25, 1, 21)?;
        ctx.place_block(f, st.light, 32, 1, 21)?;

        for i in 0..7 {
            ctx.place_block(f, st.black, 28 - i, 6 + i, 21)?;
            ctx.place_block(f, st.black, 29 + i, 6 + i, 21)?;
        }

        for i in 0..4 {
            ctx.place_block(f, st.black, 28 - i, 9 + i, 21)?;
            ctx.place_block(f, st.black, 29 + i, 9 + i, 21)?;
        }

        ctx.place_block(f, st.black, 28, 12, 21)?;
        ctx.place_block(f, st.black, 29, 12, 21)?;

        for i in 0..3 {
            ctx.place_block(f, st.black, 22 - i * 2, 8, 21)?;
            ctx.place_block(f, st.black, 22 - i * 2, 9, 21)?;
            ctx.place_block(f, st.black, 35 + i * 2, 8, 21)?;
            ctx.place_block(f, st.black, 35 + i * 2, 9, 21)?;
        }

        water_box(ctx, f, st, 15, 13, 21, 42, 15, 21)?;
        water_box(ctx, f, st, 15, 1, 21, 15, 6, 21)?;
        water_box(ctx, f, st, 16, 1, 21, 16, 5, 21)?;
        water_box(ctx, f, st, 17, 1, 21, 20, 4, 21)?;
        water_box(ctx, f, st, 21, 1, 21, 21, 3, 21)?;
        water_box(ctx, f, st, 22, 1, 21, 22, 2, 21)?;
        water_box(ctx, f, st, 23, 1, 21, 24, 1, 21)?;
        water_box(ctx, f, st, 42, 1, 21, 42, 6, 21)?;
        water_box(ctx, f, st, 41, 1, 21, 41, 5, 21)?;
        water_box(ctx, f, st, 37, 1, 21, 40, 4, 21)?;
        water_box(ctx, f, st, 36, 1, 21, 36, 3, 21)?;
        water_box(ctx, f, st, 33, 1, 21, 34, 1, 21)?;
        water_box(ctx, f, st, 35, 1, 21, 35, 2, 21)?;
    }

    Ok(())
}

/// `MonumentBuilding.generateRoofPiece`.
fn generate_roof_piece(ctx: &mut Ctx, f: &Frame, st: &States) -> Result<()> {
    if chunk_intersects(ctx, f, 21, 21, 36, 36) {
        ctx.generate_box(f, 21, 0, 22, 36, 0, 36, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 21, 1, 22, 36, 23, 36)?;

        for i in 0..4 {
            ctx.generate_box(f, 21 + i, 13 + i, 21 + i, 36 - i, 13 + i, 21 + i, st.light, st.light, false)?;
            ctx.generate_box(f, 21 + i, 13 + i, 36 - i, 36 - i, 13 + i, 36 - i, st.light, st.light, false)?;
            ctx.generate_box(f, 21 + i, 13 + i, 22 + i, 21 + i, 13 + i, 35 - i, st.light, st.light, false)?;
            ctx.generate_box(f, 36 - i, 13 + i, 22 + i, 36 - i, 13 + i, 35 - i, st.light, st.light, false)?;
        }

        ctx.generate_box(f, 25, 16, 25, 32, 16, 32, st.gray, st.gray, false)?;
        ctx.generate_box(f, 25, 17, 25, 25, 19, 25, st.light, st.light, false)?;
        ctx.generate_box(f, 32, 17, 25, 32, 19, 25, st.light, st.light, false)?;
        ctx.generate_box(f, 25, 17, 32, 25, 19, 32, st.light, st.light, false)?;
        ctx.generate_box(f, 32, 17, 32, 32, 19, 32, st.light, st.light, false)?;
        ctx.place_block(f, st.light, 26, 20, 26)?;
        ctx.place_block(f, st.light, 27, 21, 27)?;
        ctx.place_block(f, st.lamp, 27, 20, 27)?;
        ctx.place_block(f, st.light, 26, 20, 31)?;
        ctx.place_block(f, st.light, 27, 21, 30)?;
        ctx.place_block(f, st.lamp, 27, 20, 30)?;
        ctx.place_block(f, st.light, 31, 20, 31)?;
        ctx.place_block(f, st.light, 30, 21, 30)?;
        ctx.place_block(f, st.lamp, 30, 20, 30)?;
        ctx.place_block(f, st.light, 31, 20, 26)?;
        ctx.place_block(f, st.light, 30, 21, 27)?;
        ctx.place_block(f, st.lamp, 30, 20, 27)?;
        ctx.generate_box(f, 28, 21, 27, 29, 21, 27, st.gray, st.gray, false)?;
        ctx.generate_box(f, 27, 21, 28, 27, 21, 29, st.gray, st.gray, false)?;
        ctx.generate_box(f, 28, 21, 30, 29, 21, 30, st.gray, st.gray, false)?;
        ctx.generate_box(f, 30, 21, 28, 30, 21, 29, st.gray, st.gray, false)?;
    }

    Ok(())
}

/// `MonumentBuilding.generateLowerWall`.
fn generate_lower_wall(ctx: &mut Ctx, f: &Frame, st: &States) -> Result<()> {
    if chunk_intersects(ctx, f, 0, 21, 6, 58) {
        ctx.generate_box(f, 0, 0, 21, 6, 0, 57, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 0, 1, 21, 6, 7, 57)?;
        ctx.generate_box(f, 4, 4, 21, 6, 4, 53, st.gray, st.gray, false)?;

        for i in 0..4 {
            ctx.generate_box(f, i, i + 1, 21, i, i + 1, 57 - i, st.light, st.light, false)?;
        }

        for i in (23..53).step_by(3) {
            ctx.place_block(f, st.light, 5, 5, i)?;
        }

        ctx.place_block(f, st.light, 5, 5, 52)?;

        for i in 0..4 {
            ctx.generate_box(f, i, i + 1, 21, i, i + 1, 57 - i, st.light, st.light, false)?;
        }

        ctx.generate_box(f, 4, 1, 52, 6, 3, 52, st.gray, st.gray, false)?;
        ctx.generate_box(f, 5, 1, 51, 5, 3, 53, st.gray, st.gray, false)?;
    }

    if chunk_intersects(ctx, f, 51, 21, 58, 58) {
        ctx.generate_box(f, 51, 0, 21, 57, 0, 57, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 51, 1, 21, 57, 7, 57)?;
        ctx.generate_box(f, 51, 4, 21, 53, 4, 53, st.gray, st.gray, false)?;

        for i in 0..4 {
            ctx.generate_box(f, 57 - i, i + 1, 21, 57 - i, i + 1, 57 - i, st.light, st.light, false)?;
        }

        for i in (23..53).step_by(3) {
            ctx.place_block(f, st.light, 52, 5, i)?;
        }

        ctx.place_block(f, st.light, 52, 5, 52)?;
        ctx.generate_box(f, 51, 1, 52, 53, 3, 52, st.gray, st.gray, false)?;
        ctx.generate_box(f, 52, 1, 51, 52, 3, 53, st.gray, st.gray, false)?;
    }

    if chunk_intersects(ctx, f, 0, 51, 57, 57) {
        ctx.generate_box(f, 7, 0, 51, 50, 0, 57, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 7, 1, 51, 50, 10, 57)?;

        for i in 0..4 {
            ctx.generate_box(f, i + 1, i + 1, 57 - i, 56 - i, i + 1, 57 - i, st.light, st.light, false)?;
        }
    }

    Ok(())
}

/// `MonumentBuilding.generateMiddleWall`.
fn generate_middle_wall(ctx: &mut Ctx, f: &Frame, st: &States) -> Result<()> {
    if chunk_intersects(ctx, f, 7, 21, 13, 50) {
        ctx.generate_box(f, 7, 0, 21, 13, 0, 50, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 7, 1, 21, 13, 10, 50)?;
        ctx.generate_box(f, 11, 8, 21, 13, 8, 53, st.gray, st.gray, false)?;

        for i in 0..4 {
            ctx.generate_box(f, i + 7, i + 5, 21, i + 7, i + 5, 54, st.light, st.light, false)?;
        }

        for i in (21..=45).step_by(3) {
            ctx.place_block(f, st.light, 12, 9, i)?;
        }
    }

    if chunk_intersects(ctx, f, 44, 21, 50, 54) {
        ctx.generate_box(f, 44, 0, 21, 50, 0, 50, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 44, 1, 21, 50, 10, 50)?;
        ctx.generate_box(f, 44, 8, 21, 46, 8, 53, st.gray, st.gray, false)?;

        for i in 0..4 {
            ctx.generate_box(f, 50 - i, i + 5, 21, 50 - i, i + 5, 54, st.light, st.light, false)?;
        }

        for i in (21..=45).step_by(3) {
            ctx.place_block(f, st.light, 45, 9, i)?;
        }
    }

    if chunk_intersects(ctx, f, 8, 44, 49, 54) {
        ctx.generate_box(f, 14, 0, 44, 43, 0, 50, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 14, 1, 44, 43, 10, 50)?;

        for i in (12..=45).step_by(3) {
            ctx.place_block(f, st.light, i, 9, 45)?;
            ctx.place_block(f, st.light, i, 9, 52)?;
            if i == 12 || i == 18 || i == 24 || i == 33 || i == 39 || i == 45 {
                ctx.place_block(f, st.light, i, 9, 47)?;
                ctx.place_block(f, st.light, i, 9, 50)?;
                ctx.place_block(f, st.light, i, 10, 45)?;
                ctx.place_block(f, st.light, i, 10, 46)?;
                ctx.place_block(f, st.light, i, 10, 51)?;
                ctx.place_block(f, st.light, i, 10, 52)?;
                ctx.place_block(f, st.light, i, 11, 47)?;
                ctx.place_block(f, st.light, i, 11, 50)?;
                ctx.place_block(f, st.light, i, 12, 48)?;
                ctx.place_block(f, st.light, i, 12, 49)?;
            }
        }

        for ix in 0..3 {
            ctx.generate_box(f, 8 + ix, 5 + ix, 54, 49 - ix, 5 + ix, 54, st.gray, st.gray, false)?;
        }

        ctx.generate_box(f, 11, 8, 54, 46, 8, 54, st.light, st.light, false)?;
        ctx.generate_box(f, 14, 8, 44, 43, 8, 53, st.gray, st.gray, false)?;
    }

    Ok(())
}

/// `MonumentBuilding.generateUpperWall`.
fn generate_upper_wall(ctx: &mut Ctx, f: &Frame, st: &States) -> Result<()> {
    if chunk_intersects(ctx, f, 14, 21, 20, 43) {
        ctx.generate_box(f, 14, 0, 21, 20, 0, 43, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 14, 1, 22, 20, 14, 43)?;
        ctx.generate_box(f, 18, 12, 22, 20, 12, 39, st.gray, st.gray, false)?;
        ctx.generate_box(f, 18, 12, 21, 20, 12, 21, st.light, st.light, false)?;

        for i in 0..4 {
            ctx.generate_box(f, i + 14, i + 9, 21, i + 14, i + 9, 43 - i, st.light, st.light, false)?;
        }

        for i in (23..=39).step_by(3) {
            ctx.place_block(f, st.light, 19, 13, i)?;
        }
    }

    if chunk_intersects(ctx, f, 37, 21, 43, 43) {
        ctx.generate_box(f, 37, 0, 21, 43, 0, 43, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 37, 1, 22, 43, 14, 43)?;
        ctx.generate_box(f, 37, 12, 22, 39, 12, 39, st.gray, st.gray, false)?;
        ctx.generate_box(f, 37, 12, 21, 39, 12, 21, st.light, st.light, false)?;

        for i in 0..4 {
            ctx.generate_box(f, 43 - i, i + 9, 21, 43 - i, i + 9, 43 - i, st.light, st.light, false)?;
        }

        for i in (23..=39).step_by(3) {
            ctx.place_block(f, st.light, 38, 13, i)?;
        }
    }

    if chunk_intersects(ctx, f, 15, 37, 42, 43) {
        ctx.generate_box(f, 21, 0, 37, 36, 0, 43, st.gray, st.gray, false)?;
        water_box(ctx, f, st, 21, 1, 37, 36, 14, 43)?;
        ctx.generate_box(f, 21, 12, 37, 36, 12, 39, st.gray, st.gray, false)?;

        for i in 0..4 {
            ctx.generate_box(f, 15 + i, i + 9, 43 - i, 42 - i, i + 9, 43 - i, st.light, st.light, false)?;
        }

        for i in (21..=36).step_by(3) {
            ctx.place_block(f, st.light, i, 13, 38)?;
        }
    }

    Ok(())
}

/// `OceanMonumentCoreRoom.postProcess`.
fn core_room(ctx: &mut Ctx, f: &Frame, st: &States, _r: &Rooms, _room: usize, _main_design: i64) -> Result<()> {
    fill_only(ctx, f, st, 1, 8, 0, 14, 8, 14, st.gray)?;
    let mut block_state = st.light;
    ctx.generate_box(f, 0, 7, 0, 0, 7, 15, block_state, block_state, false)?;
    ctx.generate_box(f, 15, 7, 0, 15, 7, 15, block_state, block_state, false)?;
    ctx.generate_box(f, 1, 7, 0, 15, 7, 0, block_state, block_state, false)?;
    ctx.generate_box(f, 1, 7, 15, 14, 7, 15, block_state, block_state, false)?;

    for ix in 1..=6 {
        block_state = st.light;
        if ix == 2 || ix == 6 {
            block_state = st.gray;
        }

        for j in (0..=15).step_by(15) {
            ctx.generate_box(f, j, ix, 0, j, ix, 1, block_state, block_state, false)?;
            ctx.generate_box(f, j, ix, 6, j, ix, 9, block_state, block_state, false)?;
            ctx.generate_box(f, j, ix, 14, j, ix, 15, block_state, block_state, false)?;
        }

        ctx.generate_box(f, 1, ix, 0, 1, ix, 0, block_state, block_state, false)?;
        ctx.generate_box(f, 6, ix, 0, 9, ix, 0, block_state, block_state, false)?;
        ctx.generate_box(f, 14, ix, 0, 14, ix, 0, block_state, block_state, false)?;
        ctx.generate_box(f, 1, ix, 15, 14, ix, 15, block_state, block_state, false)?;
    }

    ctx.generate_box(f, 6, 3, 6, 9, 6, 9, st.black, st.black, false)?;
    ctx.generate_box(f, 7, 4, 7, 8, 5, 8, st.gold, st.gold, false)?;

    for ix in (3..=6).step_by(3) {
        for k in (6..=9).step_by(3) {
            ctx.place_block(f, st.lamp, k, ix, 6)?;
            ctx.place_block(f, st.lamp, k, ix, 9)?;
        }
    }

    ctx.generate_box(f, 5, 1, 6, 5, 2, 6, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 1, 9, 5, 2, 9, st.light, st.light, false)?;
    ctx.generate_box(f, 10, 1, 6, 10, 2, 6, st.light, st.light, false)?;
    ctx.generate_box(f, 10, 1, 9, 10, 2, 9, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 1, 5, 6, 2, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 9, 1, 5, 9, 2, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 1, 10, 6, 2, 10, st.light, st.light, false)?;
    ctx.generate_box(f, 9, 1, 10, 9, 2, 10, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 2, 5, 5, 6, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 2, 10, 5, 6, 10, st.light, st.light, false)?;
    ctx.generate_box(f, 10, 2, 5, 10, 6, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 10, 2, 10, 10, 6, 10, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 7, 1, 5, 7, 6, st.light, st.light, false)?;
    ctx.generate_box(f, 10, 7, 1, 10, 7, 6, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 7, 9, 5, 7, 14, st.light, st.light, false)?;
    ctx.generate_box(f, 10, 7, 9, 10, 7, 14, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 7, 5, 6, 7, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 7, 10, 6, 7, 10, st.light, st.light, false)?;
    ctx.generate_box(f, 9, 7, 5, 14, 7, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 9, 7, 10, 14, 7, 10, st.light, st.light, false)?;
    ctx.generate_box(f, 2, 1, 2, 2, 1, 3, st.light, st.light, false)?;
    ctx.generate_box(f, 3, 1, 2, 3, 1, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 13, 1, 2, 13, 1, 3, st.light, st.light, false)?;
    ctx.generate_box(f, 12, 1, 2, 12, 1, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 2, 1, 12, 2, 1, 13, st.light, st.light, false)?;
    ctx.generate_box(f, 3, 1, 13, 3, 1, 13, st.light, st.light, false)?;
    ctx.generate_box(f, 13, 1, 12, 13, 1, 13, st.light, st.light, false)?;
    ctx.generate_box(f, 12, 1, 13, 12, 1, 13, st.light, st.light, false)?;

    Ok(())
}

/// `OceanMonumentDoubleXRoom.postProcess`.
fn double_x_room(ctx: &mut Ctx, f: &Frame, st: &States, r: &Rooms, room: usize, _main_design: i64) -> Result<()> {
    let room_definition = r.to(room, E3);
    let room_definition2 = room;
    if r.index(room) / 25 > 0 {
        default_floor(ctx, f, st, 8, 0, r.open(room_definition, DOWN))?;
        default_floor(ctx, f, st, 0, 0, r.open(room_definition2, DOWN))?;
    }

    if r.conn(room_definition2, UP).is_none() {
        fill_only(ctx, f, st, 1, 4, 1, 7, 4, 6, st.gray)?;
    }

    if r.conn(room_definition, UP).is_none() {
        fill_only(ctx, f, st, 8, 4, 1, 14, 4, 6, st.gray)?;
    }

    ctx.generate_box(f, 0, 3, 0, 0, 3, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 15, 3, 0, 15, 3, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 3, 0, 15, 3, 0, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 3, 7, 14, 3, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 0, 2, 0, 0, 2, 7, st.gray, st.gray, false)?;
    ctx.generate_box(f, 15, 2, 0, 15, 2, 7, st.gray, st.gray, false)?;
    ctx.generate_box(f, 1, 2, 0, 15, 2, 0, st.gray, st.gray, false)?;
    ctx.generate_box(f, 1, 2, 7, 14, 2, 7, st.gray, st.gray, false)?;
    ctx.generate_box(f, 0, 1, 0, 0, 1, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 15, 1, 0, 15, 1, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 1, 0, 15, 1, 0, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 1, 7, 14, 1, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 1, 0, 10, 1, 4, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 2, 0, 9, 2, 3, st.gray, st.gray, false)?;
    ctx.generate_box(f, 5, 3, 0, 10, 3, 4, st.light, st.light, false)?;
    ctx.place_block(f, st.lamp, 6, 2, 3)?;
    ctx.place_block(f, st.lamp, 9, 2, 3)?;
    if r.open(room_definition2, S3) {
        water_box(ctx, f, st, 3, 1, 0, 4, 2, 0)?;
    }

    if r.open(room_definition2, N3) {
        water_box(ctx, f, st, 3, 1, 7, 4, 2, 7)?;
    }

    if r.open(room_definition2, W3) {
        water_box(ctx, f, st, 0, 1, 3, 0, 2, 4)?;
    }

    if r.open(room_definition, S3) {
        water_box(ctx, f, st, 11, 1, 0, 12, 2, 0)?;
    }

    if r.open(room_definition, N3) {
        water_box(ctx, f, st, 11, 1, 7, 12, 2, 7)?;
    }

    if r.open(room_definition, E3) {
        water_box(ctx, f, st, 15, 1, 3, 15, 2, 4)?;
    }

    Ok(())
}

/// `OceanMonumentDoubleXYRoom.postProcess`.
fn double_xy_room(ctx: &mut Ctx, f: &Frame, st: &States, r: &Rooms, room: usize, _main_design: i64) -> Result<()> {
    let room_definition = r.to(room, E3);
    let room_definition2 = room;
    let room_definition3 = r.to(room_definition2, UP);
    let room_definition4 = r.to(room_definition, UP);
    if r.index(room) / 25 > 0 {
        default_floor(ctx, f, st, 8, 0, r.open(room_definition, DOWN))?;
        default_floor(ctx, f, st, 0, 0, r.open(room_definition2, DOWN))?;
    }

    if r.conn(room_definition3, UP).is_none() {
        fill_only(ctx, f, st, 1, 8, 1, 7, 8, 6, st.gray)?;
    }

    if r.conn(room_definition4, UP).is_none() {
        fill_only(ctx, f, st, 8, 8, 1, 14, 8, 6, st.gray)?;
    }

    for i in 1..=7 {
        let mut block_state = st.light;
        if i == 2 || i == 6 {
            block_state = st.gray;
        }

        ctx.generate_box(f, 0, i, 0, 0, i, 7, block_state, block_state, false)?;
        ctx.generate_box(f, 15, i, 0, 15, i, 7, block_state, block_state, false)?;
        ctx.generate_box(f, 1, i, 0, 15, i, 0, block_state, block_state, false)?;
        ctx.generate_box(f, 1, i, 7, 14, i, 7, block_state, block_state, false)?;
    }

    ctx.generate_box(f, 2, 1, 3, 2, 7, 4, st.light, st.light, false)?;
    ctx.generate_box(f, 3, 1, 2, 4, 7, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 3, 1, 5, 4, 7, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 13, 1, 3, 13, 7, 4, st.light, st.light, false)?;
    ctx.generate_box(f, 11, 1, 2, 12, 7, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 11, 1, 5, 12, 7, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 1, 3, 5, 3, 4, st.light, st.light, false)?;
    ctx.generate_box(f, 10, 1, 3, 10, 3, 4, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 7, 2, 10, 7, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 5, 2, 5, 7, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 10, 5, 2, 10, 7, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 5, 5, 5, 7, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 10, 5, 5, 10, 7, 5, st.light, st.light, false)?;
    ctx.place_block(f, st.light, 6, 6, 2)?;
    ctx.place_block(f, st.light, 9, 6, 2)?;
    ctx.place_block(f, st.light, 6, 6, 5)?;
    ctx.place_block(f, st.light, 9, 6, 5)?;
    ctx.generate_box(f, 5, 4, 3, 6, 4, 4, st.light, st.light, false)?;
    ctx.generate_box(f, 9, 4, 3, 10, 4, 4, st.light, st.light, false)?;
    ctx.place_block(f, st.lamp, 5, 4, 2)?;
    ctx.place_block(f, st.lamp, 5, 4, 5)?;
    ctx.place_block(f, st.lamp, 10, 4, 2)?;
    ctx.place_block(f, st.lamp, 10, 4, 5)?;
    if r.open(room_definition2, S3) {
        water_box(ctx, f, st, 3, 1, 0, 4, 2, 0)?;
    }

    if r.open(room_definition2, N3) {
        water_box(ctx, f, st, 3, 1, 7, 4, 2, 7)?;
    }

    if r.open(room_definition2, W3) {
        water_box(ctx, f, st, 0, 1, 3, 0, 2, 4)?;
    }

    if r.open(room_definition, S3) {
        water_box(ctx, f, st, 11, 1, 0, 12, 2, 0)?;
    }

    if r.open(room_definition, N3) {
        water_box(ctx, f, st, 11, 1, 7, 12, 2, 7)?;
    }

    if r.open(room_definition, E3) {
        water_box(ctx, f, st, 15, 1, 3, 15, 2, 4)?;
    }

    if r.open(room_definition3, S3) {
        water_box(ctx, f, st, 3, 5, 0, 4, 6, 0)?;
    }

    if r.open(room_definition3, N3) {
        water_box(ctx, f, st, 3, 5, 7, 4, 6, 7)?;
    }

    if r.open(room_definition3, W3) {
        water_box(ctx, f, st, 0, 5, 3, 0, 6, 4)?;
    }

    if r.open(room_definition4, S3) {
        water_box(ctx, f, st, 11, 5, 0, 12, 6, 0)?;
    }

    if r.open(room_definition4, N3) {
        water_box(ctx, f, st, 11, 5, 7, 12, 6, 7)?;
    }

    if r.open(room_definition4, E3) {
        water_box(ctx, f, st, 15, 5, 3, 15, 6, 4)?;
    }

    Ok(())
}

/// `OceanMonumentDoubleYRoom.postProcess`.
fn double_y_room(ctx: &mut Ctx, f: &Frame, st: &States, r: &Rooms, room: usize, _main_design: i64) -> Result<()> {
    if r.index(room) / 25 > 0 {
        default_floor(ctx, f, st, 0, 0, r.open(room, DOWN))?;
    }

    let room_definition = r.to(room, UP);
    if r.conn(room_definition, UP).is_none() {
        fill_only(ctx, f, st, 1, 8, 1, 6, 8, 6, st.gray)?;
    }

    ctx.generate_box(f, 0, 4, 0, 0, 4, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 7, 4, 0, 7, 4, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 4, 0, 6, 4, 0, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 4, 7, 6, 4, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 2, 4, 1, 2, 4, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 4, 2, 1, 4, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 4, 1, 5, 4, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 4, 2, 6, 4, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 2, 4, 5, 2, 4, 6, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 4, 5, 1, 4, 5, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 4, 5, 5, 4, 6, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 4, 5, 6, 4, 5, st.light, st.light, false)?;
    let mut room_definition2 = room;

    for i in (1..=5).step_by(4) {
        let j = 0;
        if r.open(room_definition2, S3) {
            ctx.generate_box(f, 2, i, j, 2, i + 2, j, st.light, st.light, false)?;
            ctx.generate_box(f, 5, i, j, 5, i + 2, j, st.light, st.light, false)?;
            ctx.generate_box(f, 3, i + 2, j, 4, i + 2, j, st.light, st.light, false)?;
        } else {
            ctx.generate_box(f, 0, i, j, 7, i + 2, j, st.light, st.light, false)?;
            ctx.generate_box(f, 0, i + 1, j, 7, i + 1, j, st.gray, st.gray, false)?;
        }

        let var13 = 7;
        if r.open(room_definition2, N3) {
            ctx.generate_box(f, 2, i, var13, 2, i + 2, var13, st.light, st.light, false)?;
            ctx.generate_box(f, 5, i, var13, 5, i + 2, var13, st.light, st.light, false)?;
            ctx.generate_box(f, 3, i + 2, var13, 4, i + 2, var13, st.light, st.light, false)?;
        } else {
            ctx.generate_box(f, 0, i, var13, 7, i + 2, var13, st.light, st.light, false)?;
            ctx.generate_box(f, 0, i + 1, var13, 7, i + 1, var13, st.gray, st.gray, false)?;
        }

        let k = 0;
        if r.open(room_definition2, W3) {
            ctx.generate_box(f, k, i, 2, k, i + 2, 2, st.light, st.light, false)?;
            ctx.generate_box(f, k, i, 5, k, i + 2, 5, st.light, st.light, false)?;
            ctx.generate_box(f, k, i + 2, 3, k, i + 2, 4, st.light, st.light, false)?;
        } else {
            ctx.generate_box(f, k, i, 0, k, i + 2, 7, st.light, st.light, false)?;
            ctx.generate_box(f, k, i + 1, 0, k, i + 1, 7, st.gray, st.gray, false)?;
        }

        let x = 7;
        if r.open(room_definition2, E3) {
            ctx.generate_box(f, x, i, 2, x, i + 2, 2, st.light, st.light, false)?;
            ctx.generate_box(f, x, i, 5, x, i + 2, 5, st.light, st.light, false)?;
            ctx.generate_box(f, x, i + 2, 3, x, i + 2, 4, st.light, st.light, false)?;
        } else {
            ctx.generate_box(f, x, i, 0, x, i + 2, 7, st.light, st.light, false)?;
            ctx.generate_box(f, x, i + 1, 0, x, i + 1, 7, st.gray, st.gray, false)?;
        }

        room_definition2 = room_definition;
    }

    Ok(())
}

/// `OceanMonumentDoubleYZRoom.postProcess`.
fn double_yz_room(ctx: &mut Ctx, f: &Frame, st: &States, r: &Rooms, room: usize, _main_design: i64) -> Result<()> {
    let room_definition = r.to(room, N3);
    let room_definition2 = room;
    let room_definition3 = r.to(room_definition, UP);
    let room_definition4 = r.to(room_definition2, UP);
    if r.index(room) / 25 > 0 {
        default_floor(ctx, f, st, 0, 8, r.open(room_definition, DOWN))?;
        default_floor(ctx, f, st, 0, 0, r.open(room_definition2, DOWN))?;
    }

    if r.conn(room_definition4, UP).is_none() {
        fill_only(ctx, f, st, 1, 8, 1, 6, 8, 7, st.gray)?;
    }

    if r.conn(room_definition3, UP).is_none() {
        fill_only(ctx, f, st, 1, 8, 8, 6, 8, 14, st.gray)?;
    }

    for i in 1..=7 {
        let mut block_state = st.light;
        if i == 2 || i == 6 {
            block_state = st.gray;
        }

        ctx.generate_box(f, 0, i, 0, 0, i, 15, block_state, block_state, false)?;
        ctx.generate_box(f, 7, i, 0, 7, i, 15, block_state, block_state, false)?;
        ctx.generate_box(f, 1, i, 0, 6, i, 0, block_state, block_state, false)?;
        ctx.generate_box(f, 1, i, 15, 6, i, 15, block_state, block_state, false)?;
    }

    for i in 1..=7 {
        let mut block_state = st.black;
        if i == 2 || i == 6 {
            block_state = st.lamp;
        }

        ctx.generate_box(f, 3, i, 7, 4, i, 8, block_state, block_state, false)?;
    }

    if r.open(room_definition2, S3) {
        water_box(ctx, f, st, 3, 1, 0, 4, 2, 0)?;
    }

    if r.open(room_definition2, E3) {
        water_box(ctx, f, st, 7, 1, 3, 7, 2, 4)?;
    }

    if r.open(room_definition2, W3) {
        water_box(ctx, f, st, 0, 1, 3, 0, 2, 4)?;
    }

    if r.open(room_definition, N3) {
        water_box(ctx, f, st, 3, 1, 15, 4, 2, 15)?;
    }

    if r.open(room_definition, W3) {
        water_box(ctx, f, st, 0, 1, 11, 0, 2, 12)?;
    }

    if r.open(room_definition, E3) {
        water_box(ctx, f, st, 7, 1, 11, 7, 2, 12)?;
    }

    if r.open(room_definition4, S3) {
        water_box(ctx, f, st, 3, 5, 0, 4, 6, 0)?;
    }

    if r.open(room_definition4, E3) {
        water_box(ctx, f, st, 7, 5, 3, 7, 6, 4)?;
        ctx.generate_box(f, 5, 4, 2, 6, 4, 5, st.light, st.light, false)?;
        ctx.generate_box(f, 6, 1, 2, 6, 3, 2, st.light, st.light, false)?;
        ctx.generate_box(f, 6, 1, 5, 6, 3, 5, st.light, st.light, false)?;
    }

    if r.open(room_definition4, W3) {
        water_box(ctx, f, st, 0, 5, 3, 0, 6, 4)?;
        ctx.generate_box(f, 1, 4, 2, 2, 4, 5, st.light, st.light, false)?;
        ctx.generate_box(f, 1, 1, 2, 1, 3, 2, st.light, st.light, false)?;
        ctx.generate_box(f, 1, 1, 5, 1, 3, 5, st.light, st.light, false)?;
    }

    if r.open(room_definition3, N3) {
        water_box(ctx, f, st, 3, 5, 15, 4, 6, 15)?;
    }

    if r.open(room_definition3, W3) {
        water_box(ctx, f, st, 0, 5, 11, 0, 6, 12)?;
        ctx.generate_box(f, 1, 4, 10, 2, 4, 13, st.light, st.light, false)?;
        ctx.generate_box(f, 1, 1, 10, 1, 3, 10, st.light, st.light, false)?;
        ctx.generate_box(f, 1, 1, 13, 1, 3, 13, st.light, st.light, false)?;
    }

    if r.open(room_definition3, E3) {
        water_box(ctx, f, st, 7, 5, 11, 7, 6, 12)?;
        ctx.generate_box(f, 5, 4, 10, 6, 4, 13, st.light, st.light, false)?;
        ctx.generate_box(f, 6, 1, 10, 6, 3, 10, st.light, st.light, false)?;
        ctx.generate_box(f, 6, 1, 13, 6, 3, 13, st.light, st.light, false)?;
    }

    Ok(())
}

/// `OceanMonumentDoubleZRoom.postProcess`.
fn double_z_room(ctx: &mut Ctx, f: &Frame, st: &States, r: &Rooms, room: usize, _main_design: i64) -> Result<()> {
    let room_definition = r.to(room, N3);
    let room_definition2 = room;
    if r.index(room) / 25 > 0 {
        default_floor(ctx, f, st, 0, 8, r.open(room_definition, DOWN))?;
        default_floor(ctx, f, st, 0, 0, r.open(room_definition2, DOWN))?;
    }

    if r.conn(room_definition2, UP).is_none() {
        fill_only(ctx, f, st, 1, 4, 1, 6, 4, 7, st.gray)?;
    }

    if r.conn(room_definition, UP).is_none() {
        fill_only(ctx, f, st, 1, 4, 8, 6, 4, 14, st.gray)?;
    }

    ctx.generate_box(f, 0, 3, 0, 0, 3, 15, st.light, st.light, false)?;
    ctx.generate_box(f, 7, 3, 0, 7, 3, 15, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 3, 0, 7, 3, 0, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 3, 15, 6, 3, 15, st.light, st.light, false)?;
    ctx.generate_box(f, 0, 2, 0, 0, 2, 15, st.gray, st.gray, false)?;
    ctx.generate_box(f, 7, 2, 0, 7, 2, 15, st.gray, st.gray, false)?;
    ctx.generate_box(f, 1, 2, 0, 7, 2, 0, st.gray, st.gray, false)?;
    ctx.generate_box(f, 1, 2, 15, 6, 2, 15, st.gray, st.gray, false)?;
    ctx.generate_box(f, 0, 1, 0, 0, 1, 15, st.light, st.light, false)?;
    ctx.generate_box(f, 7, 1, 0, 7, 1, 15, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 1, 0, 7, 1, 0, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 1, 15, 6, 1, 15, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 1, 1, 1, 1, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 1, 1, 6, 1, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 3, 1, 1, 3, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 3, 1, 6, 3, 2, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 1, 13, 1, 1, 14, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 1, 13, 6, 1, 14, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 3, 13, 1, 3, 14, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 3, 13, 6, 3, 14, st.light, st.light, false)?;
    ctx.generate_box(f, 2, 1, 6, 2, 3, 6, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 1, 6, 5, 3, 6, st.light, st.light, false)?;
    ctx.generate_box(f, 2, 1, 9, 2, 3, 9, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 1, 9, 5, 3, 9, st.light, st.light, false)?;
    ctx.generate_box(f, 3, 2, 6, 4, 2, 6, st.light, st.light, false)?;
    ctx.generate_box(f, 3, 2, 9, 4, 2, 9, st.light, st.light, false)?;
    ctx.generate_box(f, 2, 2, 7, 2, 2, 8, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 2, 7, 5, 2, 8, st.light, st.light, false)?;
    ctx.place_block(f, st.lamp, 2, 2, 5)?;
    ctx.place_block(f, st.lamp, 5, 2, 5)?;
    ctx.place_block(f, st.lamp, 2, 2, 10)?;
    ctx.place_block(f, st.lamp, 5, 2, 10)?;
    ctx.place_block(f, st.light, 2, 3, 5)?;
    ctx.place_block(f, st.light, 5, 3, 5)?;
    ctx.place_block(f, st.light, 2, 3, 10)?;
    ctx.place_block(f, st.light, 5, 3, 10)?;
    if r.open(room_definition2, S3) {
        water_box(ctx, f, st, 3, 1, 0, 4, 2, 0)?;
    }

    if r.open(room_definition2, E3) {
        water_box(ctx, f, st, 7, 1, 3, 7, 2, 4)?;
    }

    if r.open(room_definition2, W3) {
        water_box(ctx, f, st, 0, 1, 3, 0, 2, 4)?;
    }

    if r.open(room_definition, N3) {
        water_box(ctx, f, st, 3, 1, 15, 4, 2, 15)?;
    }

    if r.open(room_definition, W3) {
        water_box(ctx, f, st, 0, 1, 11, 0, 2, 12)?;
    }

    if r.open(room_definition, E3) {
        water_box(ctx, f, st, 7, 1, 11, 7, 2, 12)?;
    }

    Ok(())
}

/// `OceanMonumentEntryRoom.postProcess`.
fn entry_room(ctx: &mut Ctx, f: &Frame, st: &States, r: &Rooms, room: usize, _main_design: i64) -> Result<()> {
    ctx.generate_box(f, 0, 3, 0, 2, 3, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 3, 0, 7, 3, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 0, 2, 0, 1, 2, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 6, 2, 0, 7, 2, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 0, 1, 0, 0, 1, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 7, 1, 0, 7, 1, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 0, 1, 7, 7, 3, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 1, 0, 2, 3, 0, st.light, st.light, false)?;
    ctx.generate_box(f, 5, 1, 0, 6, 3, 0, st.light, st.light, false)?;
    if r.open(room, N3) {
        water_box(ctx, f, st, 3, 1, 7, 4, 2, 7)?;
    }

    if r.open(room, W3) {
        water_box(ctx, f, st, 0, 1, 3, 1, 2, 4)?;
    }

    if r.open(room, E3) {
        water_box(ctx, f, st, 6, 1, 3, 7, 2, 4)?;
    }

    Ok(())
}

/// `OceanMonumentPenthouse.postProcess`.
fn penthouse(ctx: &mut Ctx, f: &Frame, st: &States, _r: &Rooms, _room: usize, _main_design: i64) -> Result<()> {
    ctx.generate_box(f, 2, -1, 2, 11, -1, 11, st.light, st.light, false)?;
    ctx.generate_box(f, 0, -1, 0, 1, -1, 11, st.gray, st.gray, false)?;
    ctx.generate_box(f, 12, -1, 0, 13, -1, 11, st.gray, st.gray, false)?;
    ctx.generate_box(f, 2, -1, 0, 11, -1, 1, st.gray, st.gray, false)?;
    ctx.generate_box(f, 2, -1, 12, 11, -1, 13, st.gray, st.gray, false)?;
    ctx.generate_box(f, 0, 0, 0, 0, 0, 13, st.light, st.light, false)?;
    ctx.generate_box(f, 13, 0, 0, 13, 0, 13, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 0, 0, 12, 0, 0, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 0, 13, 12, 0, 13, st.light, st.light, false)?;

    for i in (2..=11).step_by(3) {
        ctx.place_block(f, st.lamp, 0, 0, i)?;
        ctx.place_block(f, st.lamp, 13, 0, i)?;
        ctx.place_block(f, st.lamp, i, 0, 0)?;
    }

    ctx.generate_box(f, 2, 0, 3, 4, 0, 9, st.light, st.light, false)?;
    ctx.generate_box(f, 9, 0, 3, 11, 0, 9, st.light, st.light, false)?;
    ctx.generate_box(f, 4, 0, 9, 9, 0, 11, st.light, st.light, false)?;
    ctx.place_block(f, st.light, 5, 0, 8)?;
    ctx.place_block(f, st.light, 8, 0, 8)?;
    ctx.place_block(f, st.light, 10, 0, 10)?;
    ctx.place_block(f, st.light, 3, 0, 10)?;
    ctx.generate_box(f, 3, 0, 3, 3, 0, 7, st.black, st.black, false)?;
    ctx.generate_box(f, 10, 0, 3, 10, 0, 7, st.black, st.black, false)?;
    ctx.generate_box(f, 6, 0, 10, 7, 0, 10, st.black, st.black, false)?;
    let mut i = 3;

    for _j in 0..2 {
        for k in (2..=8).step_by(3) {
            ctx.generate_box(f, i, 0, k, i, 2, k, st.light, st.light, false)?;
        }

        i = 10;
    }

    ctx.generate_box(f, 5, 0, 10, 5, 2, 10, st.light, st.light, false)?;
    ctx.generate_box(f, 8, 0, 10, 8, 2, 10, st.light, st.light, false)?;
    ctx.generate_box(f, 6, -1, 7, 7, -1, 8, st.black, st.black, false)?;
    water_box(ctx, f, st, 6, -1, 3, 7, -1, 4)?;
    spawn_elder(ctx, f, 6, 1, 6)?;

    Ok(())
}

/// `OceanMonumentSimpleRoom.postProcess`.
fn simple_room(ctx: &mut Ctx, f: &Frame, st: &States, r: &Rooms, room: usize, main_design: i64) -> Result<()> {
    if r.index(room) / 25 > 0 {
        default_floor(ctx, f, st, 0, 0, r.open(room, DOWN))?;
    }

    if r.conn(room, UP).is_none() {
        fill_only(ctx, f, st, 1, 4, 1, 6, 4, 6, st.gray)?;
    }

    let bl = main_design != 0 && ctx.random.next_boolean() && !r.open(room, DOWN) && !r.open(room, UP) && r.count(room) > 1;
    if main_design == 0 {
        ctx.generate_box(f, 0, 1, 0, 2, 1, 2, st.light, st.light, false)?;
        ctx.generate_box(f, 0, 3, 0, 2, 3, 2, st.light, st.light, false)?;
        ctx.generate_box(f, 0, 2, 0, 0, 2, 2, st.gray, st.gray, false)?;
        ctx.generate_box(f, 1, 2, 0, 2, 2, 0, st.gray, st.gray, false)?;
        ctx.place_block(f, st.lamp, 1, 2, 1)?;
        ctx.generate_box(f, 5, 1, 0, 7, 1, 2, st.light, st.light, false)?;
        ctx.generate_box(f, 5, 3, 0, 7, 3, 2, st.light, st.light, false)?;
        ctx.generate_box(f, 7, 2, 0, 7, 2, 2, st.gray, st.gray, false)?;
        ctx.generate_box(f, 5, 2, 0, 6, 2, 0, st.gray, st.gray, false)?;
        ctx.place_block(f, st.lamp, 6, 2, 1)?;
        ctx.generate_box(f, 0, 1, 5, 2, 1, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 0, 3, 5, 2, 3, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 0, 2, 5, 0, 2, 7, st.gray, st.gray, false)?;
        ctx.generate_box(f, 1, 2, 7, 2, 2, 7, st.gray, st.gray, false)?;
        ctx.place_block(f, st.lamp, 1, 2, 6)?;
        ctx.generate_box(f, 5, 1, 5, 7, 1, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 5, 3, 5, 7, 3, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 7, 2, 5, 7, 2, 7, st.gray, st.gray, false)?;
        ctx.generate_box(f, 5, 2, 7, 6, 2, 7, st.gray, st.gray, false)?;
        ctx.place_block(f, st.lamp, 6, 2, 6)?;
        if r.open(room, S3) {
            ctx.generate_box(f, 3, 3, 0, 4, 3, 0, st.light, st.light, false)?;
        } else {
            ctx.generate_box(f, 3, 3, 0, 4, 3, 1, st.light, st.light, false)?;
            ctx.generate_box(f, 3, 2, 0, 4, 2, 0, st.gray, st.gray, false)?;
            ctx.generate_box(f, 3, 1, 0, 4, 1, 1, st.light, st.light, false)?;
        }

        if r.open(room, N3) {
            ctx.generate_box(f, 3, 3, 7, 4, 3, 7, st.light, st.light, false)?;
        } else {
            ctx.generate_box(f, 3, 3, 6, 4, 3, 7, st.light, st.light, false)?;
            ctx.generate_box(f, 3, 2, 7, 4, 2, 7, st.gray, st.gray, false)?;
            ctx.generate_box(f, 3, 1, 6, 4, 1, 7, st.light, st.light, false)?;
        }

        if r.open(room, W3) {
            ctx.generate_box(f, 0, 3, 3, 0, 3, 4, st.light, st.light, false)?;
        } else {
            ctx.generate_box(f, 0, 3, 3, 1, 3, 4, st.light, st.light, false)?;
            ctx.generate_box(f, 0, 2, 3, 0, 2, 4, st.gray, st.gray, false)?;
            ctx.generate_box(f, 0, 1, 3, 1, 1, 4, st.light, st.light, false)?;
        }

        if r.open(room, E3) {
            ctx.generate_box(f, 7, 3, 3, 7, 3, 4, st.light, st.light, false)?;
        } else {
            ctx.generate_box(f, 6, 3, 3, 7, 3, 4, st.light, st.light, false)?;
            ctx.generate_box(f, 7, 2, 3, 7, 2, 4, st.gray, st.gray, false)?;
            ctx.generate_box(f, 6, 1, 3, 7, 1, 4, st.light, st.light, false)?;
        }
    } else if main_design == 1 {
        ctx.generate_box(f, 2, 1, 2, 2, 3, 2, st.light, st.light, false)?;
        ctx.generate_box(f, 2, 1, 5, 2, 3, 5, st.light, st.light, false)?;
        ctx.generate_box(f, 5, 1, 5, 5, 3, 5, st.light, st.light, false)?;
        ctx.generate_box(f, 5, 1, 2, 5, 3, 2, st.light, st.light, false)?;
        ctx.place_block(f, st.lamp, 2, 2, 2)?;
        ctx.place_block(f, st.lamp, 2, 2, 5)?;
        ctx.place_block(f, st.lamp, 5, 2, 5)?;
        ctx.place_block(f, st.lamp, 5, 2, 2)?;
        ctx.generate_box(f, 0, 1, 0, 1, 3, 0, st.light, st.light, false)?;
        ctx.generate_box(f, 0, 1, 1, 0, 3, 1, st.light, st.light, false)?;
        ctx.generate_box(f, 0, 1, 7, 1, 3, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 0, 1, 6, 0, 3, 6, st.light, st.light, false)?;
        ctx.generate_box(f, 6, 1, 7, 7, 3, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 7, 1, 6, 7, 3, 6, st.light, st.light, false)?;
        ctx.generate_box(f, 6, 1, 0, 7, 3, 0, st.light, st.light, false)?;
        ctx.generate_box(f, 7, 1, 1, 7, 3, 1, st.light, st.light, false)?;
        ctx.place_block(f, st.gray, 1, 2, 0)?;
        ctx.place_block(f, st.gray, 0, 2, 1)?;
        ctx.place_block(f, st.gray, 1, 2, 7)?;
        ctx.place_block(f, st.gray, 0, 2, 6)?;
        ctx.place_block(f, st.gray, 6, 2, 7)?;
        ctx.place_block(f, st.gray, 7, 2, 6)?;
        ctx.place_block(f, st.gray, 6, 2, 0)?;
        ctx.place_block(f, st.gray, 7, 2, 1)?;
        if !r.open(room, S3) {
            ctx.generate_box(f, 1, 3, 0, 6, 3, 0, st.light, st.light, false)?;
            ctx.generate_box(f, 1, 2, 0, 6, 2, 0, st.gray, st.gray, false)?;
            ctx.generate_box(f, 1, 1, 0, 6, 1, 0, st.light, st.light, false)?;
        }

        if !r.open(room, N3) {
            ctx.generate_box(f, 1, 3, 7, 6, 3, 7, st.light, st.light, false)?;
            ctx.generate_box(f, 1, 2, 7, 6, 2, 7, st.gray, st.gray, false)?;
            ctx.generate_box(f, 1, 1, 7, 6, 1, 7, st.light, st.light, false)?;
        }

        if !r.open(room, W3) {
            ctx.generate_box(f, 0, 3, 1, 0, 3, 6, st.light, st.light, false)?;
            ctx.generate_box(f, 0, 2, 1, 0, 2, 6, st.gray, st.gray, false)?;
            ctx.generate_box(f, 0, 1, 1, 0, 1, 6, st.light, st.light, false)?;
        }

        if !r.open(room, E3) {
            ctx.generate_box(f, 7, 3, 1, 7, 3, 6, st.light, st.light, false)?;
            ctx.generate_box(f, 7, 2, 1, 7, 2, 6, st.gray, st.gray, false)?;
            ctx.generate_box(f, 7, 1, 1, 7, 1, 6, st.light, st.light, false)?;
        }
    } else if main_design == 2 {
        ctx.generate_box(f, 0, 1, 0, 0, 1, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 7, 1, 0, 7, 1, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 1, 1, 0, 6, 1, 0, st.light, st.light, false)?;
        ctx.generate_box(f, 1, 1, 7, 6, 1, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 0, 2, 0, 0, 2, 7, st.black, st.black, false)?;
        ctx.generate_box(f, 7, 2, 0, 7, 2, 7, st.black, st.black, false)?;
        ctx.generate_box(f, 1, 2, 0, 6, 2, 0, st.black, st.black, false)?;
        ctx.generate_box(f, 1, 2, 7, 6, 2, 7, st.black, st.black, false)?;
        ctx.generate_box(f, 0, 3, 0, 0, 3, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 7, 3, 0, 7, 3, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 1, 3, 0, 6, 3, 0, st.light, st.light, false)?;
        ctx.generate_box(f, 1, 3, 7, 6, 3, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 0, 1, 3, 0, 2, 4, st.black, st.black, false)?;
        ctx.generate_box(f, 7, 1, 3, 7, 2, 4, st.black, st.black, false)?;
        ctx.generate_box(f, 3, 1, 0, 4, 2, 0, st.black, st.black, false)?;
        ctx.generate_box(f, 3, 1, 7, 4, 2, 7, st.black, st.black, false)?;
        if r.open(room, S3) {
            water_box(ctx, f, st, 3, 1, 0, 4, 2, 0)?;
        }

        if r.open(room, N3) {
            water_box(ctx, f, st, 3, 1, 7, 4, 2, 7)?;
        }

        if r.open(room, W3) {
            water_box(ctx, f, st, 0, 1, 3, 0, 2, 4)?;
        }

        if r.open(room, E3) {
            water_box(ctx, f, st, 7, 1, 3, 7, 2, 4)?;
        }
    }

    if bl {
        ctx.generate_box(f, 3, 1, 3, 4, 1, 4, st.light, st.light, false)?;
        ctx.generate_box(f, 3, 2, 3, 4, 2, 4, st.gray, st.gray, false)?;
        ctx.generate_box(f, 3, 3, 3, 4, 3, 4, st.light, st.light, false)?;
    }

    Ok(())
}

/// `OceanMonumentSimpleTopRoom.postProcess`.
fn simple_top_room(ctx: &mut Ctx, f: &Frame, st: &States, r: &Rooms, room: usize, _main_design: i64) -> Result<()> {
    if r.index(room) / 25 > 0 {
        default_floor(ctx, f, st, 0, 0, r.open(room, DOWN))?;
    }

    if r.conn(room, UP).is_none() {
        fill_only(ctx, f, st, 1, 4, 1, 6, 4, 6, st.gray)?;
    }

    for i in 1..=6 {
        for j in 1..=6 {
            if ctx.random.next_int(3) != 0 {
                let k = 2 + if ctx.random.next_int(4) == 0 { 0 } else { 1 };
                let block_state = st.sponge;
                ctx.generate_box(f, i, k, j, i, 3, j, block_state, block_state, false)?;
            }
        }
    }

    ctx.generate_box(f, 0, 1, 0, 0, 1, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 7, 1, 0, 7, 1, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 1, 0, 6, 1, 0, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 1, 7, 6, 1, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 0, 2, 0, 0, 2, 7, st.black, st.black, false)?;
    ctx.generate_box(f, 7, 2, 0, 7, 2, 7, st.black, st.black, false)?;
    ctx.generate_box(f, 1, 2, 0, 6, 2, 0, st.black, st.black, false)?;
    ctx.generate_box(f, 1, 2, 7, 6, 2, 7, st.black, st.black, false)?;
    ctx.generate_box(f, 0, 3, 0, 0, 3, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 7, 3, 0, 7, 3, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 3, 0, 6, 3, 0, st.light, st.light, false)?;
    ctx.generate_box(f, 1, 3, 7, 6, 3, 7, st.light, st.light, false)?;
    ctx.generate_box(f, 0, 1, 3, 0, 2, 4, st.black, st.black, false)?;
    ctx.generate_box(f, 7, 1, 3, 7, 2, 4, st.black, st.black, false)?;
    ctx.generate_box(f, 3, 1, 0, 4, 2, 0, st.black, st.black, false)?;
    ctx.generate_box(f, 3, 1, 7, 4, 2, 7, st.black, st.black, false)?;
    if r.open(room, S3) {
        water_box(ctx, f, st, 3, 1, 0, 4, 2, 0)?;
    }

    Ok(())
}

/// `OceanMonumentWingRoom.postProcess`.
fn wing_room(ctx: &mut Ctx, f: &Frame, st: &States, _r: &Rooms, _room: usize, main_design: i64) -> Result<()> {
    if main_design == 0 {
        for i in 0..4 {
            ctx.generate_box(f, 10 - i, 3 - i, 20 - i, 12 + i, 3 - i, 20, st.light, st.light, false)?;
        }

        ctx.generate_box(f, 7, 0, 6, 15, 0, 16, st.light, st.light, false)?;
        ctx.generate_box(f, 6, 0, 6, 6, 3, 20, st.light, st.light, false)?;
        ctx.generate_box(f, 16, 0, 6, 16, 3, 20, st.light, st.light, false)?;
        ctx.generate_box(f, 7, 1, 7, 7, 1, 20, st.light, st.light, false)?;
        ctx.generate_box(f, 15, 1, 7, 15, 1, 20, st.light, st.light, false)?;
        ctx.generate_box(f, 7, 1, 6, 9, 3, 6, st.light, st.light, false)?;
        ctx.generate_box(f, 13, 1, 6, 15, 3, 6, st.light, st.light, false)?;
        ctx.generate_box(f, 8, 1, 7, 9, 1, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 13, 1, 7, 14, 1, 7, st.light, st.light, false)?;
        ctx.generate_box(f, 9, 0, 5, 13, 0, 5, st.light, st.light, false)?;
        ctx.generate_box(f, 10, 0, 7, 12, 0, 7, st.black, st.black, false)?;
        ctx.generate_box(f, 8, 0, 10, 8, 0, 12, st.black, st.black, false)?;
        ctx.generate_box(f, 14, 0, 10, 14, 0, 12, st.black, st.black, false)?;

        for i in (7..=18).rev().step_by(3) {
            // CHECK descending
            ctx.place_block(f, st.lamp, 6, 3, i)?;
            ctx.place_block(f, st.lamp, 16, 3, i)?;
        }

        ctx.place_block(f, st.lamp, 10, 0, 10)?;
        ctx.place_block(f, st.lamp, 12, 0, 10)?;
        ctx.place_block(f, st.lamp, 10, 0, 12)?;
        ctx.place_block(f, st.lamp, 12, 0, 12)?;
        ctx.place_block(f, st.lamp, 8, 3, 6)?;
        ctx.place_block(f, st.lamp, 14, 3, 6)?;
        ctx.place_block(f, st.light, 4, 2, 4)?;
        ctx.place_block(f, st.lamp, 4, 1, 4)?;
        ctx.place_block(f, st.light, 4, 0, 4)?;
        ctx.place_block(f, st.light, 18, 2, 4)?;
        ctx.place_block(f, st.lamp, 18, 1, 4)?;
        ctx.place_block(f, st.light, 18, 0, 4)?;
        ctx.place_block(f, st.light, 4, 2, 18)?;
        ctx.place_block(f, st.lamp, 4, 1, 18)?;
        ctx.place_block(f, st.light, 4, 0, 18)?;
        ctx.place_block(f, st.light, 18, 2, 18)?;
        ctx.place_block(f, st.lamp, 18, 1, 18)?;
        ctx.place_block(f, st.light, 18, 0, 18)?;
        ctx.place_block(f, st.light, 9, 7, 20)?;
        ctx.place_block(f, st.light, 13, 7, 20)?;
        ctx.generate_box(f, 6, 0, 21, 7, 4, 21, st.light, st.light, false)?;
        ctx.generate_box(f, 15, 0, 21, 16, 4, 21, st.light, st.light, false)?;
        spawn_elder(ctx, f, 11, 2, 16)?;
    } else if main_design == 1 {
        ctx.generate_box(f, 9, 3, 18, 13, 3, 20, st.light, st.light, false)?;
        ctx.generate_box(f, 9, 0, 18, 9, 2, 18, st.light, st.light, false)?;
        ctx.generate_box(f, 13, 0, 18, 13, 2, 18, st.light, st.light, false)?;
        let mut i = 9;

        for _l in 0..2 {
            ctx.place_block(f, st.light, i, 6, 20)?;
            ctx.place_block(f, st.lamp, i, 5, 20)?;
            ctx.place_block(f, st.light, i, 4, 20)?;
            i = 13;
        }

        ctx.generate_box(f, 7, 3, 7, 15, 3, 14, st.light, st.light, false)?;
        let mut x = 10;

        for _l in 0..2 {
            ctx.generate_box(f, x, 0, 10, x, 6, 10, st.light, st.light, false)?;
            ctx.generate_box(f, x, 0, 12, x, 6, 12, st.light, st.light, false)?;
            ctx.place_block(f, st.lamp, x, 0, 10)?;
            ctx.place_block(f, st.lamp, x, 0, 12)?;
            ctx.place_block(f, st.lamp, x, 4, 10)?;
            ctx.place_block(f, st.lamp, x, 4, 12)?;
            x = 12;
        }

        x = 8;

        for _l in 0..2 {
            ctx.generate_box(f, x, 0, 7, x, 2, 7, st.light, st.light, false)?;
            ctx.generate_box(f, x, 0, 14, x, 2, 14, st.light, st.light, false)?;
            x = 14;
        }

        ctx.generate_box(f, 8, 3, 8, 8, 3, 13, st.black, st.black, false)?;
        ctx.generate_box(f, 14, 3, 8, 14, 3, 13, st.black, st.black, false)?;
        spawn_elder(ctx, f, 11, 5, 13)?;
    }

    Ok(())
}

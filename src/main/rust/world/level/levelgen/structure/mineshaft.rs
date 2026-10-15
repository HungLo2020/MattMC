//! `MineshaftPieces`: corridors, crossings, rooms and stairs.
use super::piece::{BoundingBox, Ctx, Frame};
use super::{Misc, TAG_MINESHAFT_BLOCKING};
use crate::content::block::BlockId;
use crate::world::level::levelgen::decoration::region::Result;

/// `Direction` ordinals.
const DOWN: usize = 0;
const UP: usize = 1;
/// `Direction.values()` as steps.
const DIRECTIONS: [[i32; 3]; 6] = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];

/// One mineshaft type's states (`MineshaftStructure.Type`).
struct Kind {
    wood: u32,
    planks: u32,
    fence: u32,
    fence_west: u32,
    fence_east: u32,
    planks_block: BlockId,
    cave_air: u32,
    cobweb: u32,
    chain: u32,
    rail_ns: u32,
    rail_ew: u32,
    torch_south: u32,
    torch_north: u32,
    spawner: u32,
}

fn kind(ctx: &Ctx, mst: i64) -> Option<(Kind, Vec<BlockId>)> {
    let n = &ctx.names;
    let (wood, planks, fence) = if mst == 1 {
        ("minecraft:dark_oak_log", "minecraft:dark_oak_planks", "minecraft:dark_oak_fence")
    } else {
        ("minecraft:oak_log", "minecraft:oak_planks", "minecraft:oak_fence")
    };
    let k = Kind {
        wood: n.state(wood, &[])?,
        planks: n.state(planks, &[])?,
        fence: n.state(fence, &[])?,
        fence_west: n.state(fence, &[("west", "true")])?,
        fence_east: n.state(fence, &[("east", "true")])?,
        planks_block: n.block(planks)?,
        cave_air: n.state("minecraft:cave_air", &[])?,
        cobweb: n.state("minecraft:cobweb", &[])?,
        chain: n.state("minecraft:iron_chain", &[])?,
        rail_ns: n.state("minecraft:rail", &[("shape", "north_south")])?,
        rail_ew: n.state("minecraft:rail", &[("shape", "east_west")])?,
        torch_south: n.state("minecraft:wall_torch", &[("facing", "south")])?,
        torch_north: n.state("minecraft:wall_torch", &[("facing", "north")])?,
        spawner: n.state("minecraft:spawner", &[])?,
    };
    // `MineShaftPiece.canBeReplaced`: never over planks, wood, fence or chain.
    let keep = vec![n.block(planks)?, n.block(wood)?, n.block(fence)?, n.block("minecraft:iron_chain")?];
    Some((k, keep))
}

/// Words after the header: the type (`MST`) first, then each piece's own fields.
pub(crate) fn place(piece_kind: i64, ctx: &mut Ctx, words: &mut [i64], frame: Frame) -> Result<bool> {
    let Some(&mst) = words.get(9) else {
        return Ok(false);
    };
    let Some((k, keep)) = kind(ctx, mst) else {
        return Ok(false);
    };
    ctx.keep_blocks = keep;
    let result = match piece_kind {
        5 if words.len() >= 14 => corridor(ctx, words, &frame, &k),
        6 if words.len() >= 12 => crossing(ctx, words, &frame, &k),
        7 if words.len() >= 11 => room(ctx, words, &frame, &k),
        8 => stairs(ctx, &frame, &k),
        _ => Ok(false),
    };
    ctx.keep_blocks.clear();
    result
}

/// `isInInvalidLocation`: a blocking biome at the centre, or liquid on the box's faces.
fn invalid_location(ctx: &Ctx, frame: &Frame) -> Result<bool> {
    let (b, c) = (frame.bbox, ctx.chunk_box);
    let i = (b.min_x - 1).max(c.min_x);
    let j = (b.min_y - 1).max(c.min_y);
    let k = (b.min_z - 1).max(c.min_z);
    let l = (b.max_x + 1).min(c.max_x);
    let m = (b.max_y + 1).min(c.max_y);
    let n = (b.max_z + 1).min(c.max_z);
    if ctx.biome_tag([(i + l) / 2, (j + m) / 2, (k + n) / 2], TAG_MINESHAFT_BLOCKING)? {
        return Ok(true);
    }
    let liquid = |pos: [i32; 3]| -> Result<bool> { Ok(ctx.g.liquid(ctx.state_at(pos)?)) };
    for o in i..=l {
        for p in k..=n {
            if liquid([o, j, p])? || liquid([o, m, p])? {
                return Ok(true);
            }
        }
    }
    for o in i..=l {
        for p in j..=m {
            if liquid([o, p, k])? || liquid([o, p, n])? {
                return Ok(true);
            }
        }
    }
    for o in k..=n {
        for p in j..=m {
            if liquid([i, p, o])? || liquid([l, p, o])? {
                return Ok(true);
            }
        }
    }
    Ok(false)
}

/// `setPlanksBlock`: below the ocean floor and not sturdy on top, written untransformed.
fn set_planks(ctx: &mut Ctx, f: &Frame, state: u32, x: i32, y: i32, z: i32) -> Result<()> {
    if ctx.is_interior(f, x, y, z)? {
        let pos = f.world(x, y, z);
        if !ctx.sturdy(pos, UP)? {
            ctx.set_block(pos, state, 2)?;
        }
    }
    Ok(())
}

/// `isSupportingBox(i, j, k, l)`: no air above `[i, j]` at height `k + 1`, depth `l`.
fn supporting(ctx: &Ctx, f: &Frame, i: i32, j: i32, k: i32, l: i32) -> Result<bool> {
    for m in i..=j {
        if ctx.is_air_state(ctx.get_block(f, m, k + 1, l)?) {
            return Ok(false);
        }
    }
    Ok(true)
}

fn corridor(ctx: &mut Ctx, words: &mut [i64], f: &Frame, k: &Kind) -> Result<bool> {
    let (has_rails, spider_corridor, mut has_placed_spider, sections) = (words[10] != 0, words[11] != 0, words[12] != 0, words[13] as i32);
    if !invalid_location(ctx, f)? {
        let m = sections * 5 - 1;
        let air = k.cave_air;
        ctx.generate_box(f, 0, 0, 0, 2, 1, m, air, air, false)?;
        ctx.generate_maybe_box(f, 0.8, 0, 2, 0, 2, 2, m, air, air, false, false)?;
        if spider_corridor {
            ctx.generate_maybe_box(f, 0.6, 0, 0, 0, 2, 1, m, k.cobweb, air, false, true)?;
        }
        for n in 0..sections {
            let o = 2 + n * 5;
            support(ctx, f, k, 0, 0, o, 2, 2)?;
            for (chance, x, z) in [(0.1, 0, o - 1), (0.1, 2, o - 1), (0.1, 0, o + 1), (0.1, 2, o + 1), (0.05, 0, o - 2), (0.05, 2, o - 2),
                (0.05, 0, o + 2), (0.05, 2, o + 2)]
            {
                cobweb(ctx, f, k, chance, x, 2, z)?;
            }
            if ctx.random.next_int(100) == 0 {
                minecart_chest(ctx, f, k, 2, 0, o - 1)?;
            }
            if ctx.random.next_int(100) == 0 {
                minecart_chest(ctx, f, k, 0, 0, o + 1)?;
            }
            if spider_corridor && !has_placed_spider {
                let q = o - 1 + ctx.random.next_int(3);
                let pos = f.world(1, 0, q);
                if ctx.chunk_box.inside(pos) && ctx.is_interior(f, 1, 0, q)? {
                    has_placed_spider = true;
                    ctx.set_block(pos, k.spawner, 2)?;
                    let step = ctx.g.misc_step(Misc::SpawnerCaveSpider);
                    ctx.java_step(step, pos)?;
                }
            }
        }
        for n in 0..=2 {
            for o in 0..=m {
                set_planks(ctx, f, k.planks, n, -1, o)?;
            }
        }
        double_support(ctx, f, k, 0, -1, 2)?;
        if sections > 1 {
            double_support(ctx, f, k, 0, -1, m - 2)?;
        }
        if has_rails {
            for p in 0..=m {
                let below = ctx.get_block(f, 1, -1, p)?;
                if !ctx.is_air_state(below) && ctx.g.solid_render(below) {
                    let chance = if ctx.is_interior(f, 1, 0, p)? { 0.7 } else { 0.9 };
                    ctx.maybe_generate_block(f, chance, 1, 0, p, k.rail_ns)?;
                }
            }
        }
    }
    words[12] = has_placed_spider as i64;
    Ok(true)
}

/// `MineShaftCorridor.createChest`: a rail and a chest minecart over air on a floor.
fn minecart_chest(ctx: &mut Ctx, f: &Frame, k: &Kind, x: i32, y: i32, z: i32) -> Result<()> {
    let pos = f.world(x, y, z);
    if ctx.chunk_box.inside(pos) && ctx.is_air_state(ctx.state_at(pos)?) && !ctx.is_air_state(ctx.state_at([pos[0], pos[1] - 1, pos[2]])?) {
        let rail = if ctx.random.next_boolean() { k.rail_ns } else { k.rail_ew };
        ctx.place_block(f, rail, x, y, z)?;
        let step = ctx.g.misc_step(Misc::MineshaftMinecart);
        ctx.java_step(step, pos)?;
    }
    Ok(())
}

/// `placeSupport(i, j, k, l, m)`.
#[allow(clippy::too_many_arguments)]
fn support(ctx: &mut Ctx, f: &Frame, kind: &Kind, i: i32, j: i32, k: i32, l: i32, m: i32) -> Result<()> {
    if supporting(ctx, f, i, m, l, k)? {
        let air = kind.cave_air;
        ctx.generate_box(f, i, j, k, i, l - 1, k, kind.fence_west, air, false)?;
        ctx.generate_box(f, m, j, k, m, l - 1, k, kind.fence_east, air, false)?;
        if ctx.random.next_int(4) == 0 {
            ctx.generate_box(f, i, l, k, i, l, k, kind.planks, air, false)?;
            ctx.generate_box(f, m, l, k, m, l, k, kind.planks, air, false)?;
        } else {
            ctx.generate_box(f, i, l, k, m, l, k, kind.planks, air, false)?;
            ctx.maybe_generate_block(f, 0.05, i + 1, l, k - 1, kind.torch_south)?;
            ctx.maybe_generate_block(f, 0.05, i + 1, l, k + 1, kind.torch_north)?;
        }
    }
    Ok(())
}

/// `maybePlaceCobWeb`.
fn cobweb(ctx: &mut Ctx, f: &Frame, k: &Kind, chance: f32, x: i32, y: i32, z: i32) -> Result<()> {
    if ctx.is_interior(f, x, y, z)? && ctx.random.next_float() < chance && sturdy_neighbours(ctx, f, x, y, z, 2)? {
        ctx.place_block(f, k.cobweb, x, y, z)?;
    }
    Ok(())
}

/// `hasSturdyNeighbours`: at least `needed` neighbours inside the box, sturdy towards the position.
fn sturdy_neighbours(ctx: &Ctx, f: &Frame, x: i32, y: i32, z: i32, needed: i32) -> Result<bool> {
    let pos = f.world(x, y, z);
    let mut count = 0;
    for (index, d) in DIRECTIONS.iter().enumerate() {
        let at = [pos[0] + d[0], pos[1] + d[1], pos[2] + d[2]];
        // `Direction.getOpposite` by ordinal: down/up, north/south, west/east pair up.
        if ctx.chunk_box.inside(at) && ctx.sturdy(at, index ^ 1)? {
            count += 1;
            if count >= needed {
                return Ok(true);
            }
        }
    }
    Ok(false)
}

/// `placeDoubleLowerOrUpperSupport`.
fn double_support(ctx: &mut Ctx, f: &Frame, k: &Kind, i: i32, j: i32, z: i32) -> Result<()> {
    if ctx.block_of(ctx.get_block(f, i, j, z)?) == k.planks_block {
        pillar_or_chain(ctx, f, k, i, j, z)?;
    }
    if ctx.block_of(ctx.get_block(f, i + 2, j, z)?) == k.planks_block {
        pillar_or_chain(ctx, f, k, i + 2, j, z)?;
    }
    Ok(())
}

/// `fillPillarDownOrChainUp(wood, …)`.
fn pillar_or_chain(ctx: &mut Ctx, f: &Frame, k: &Kind, x: i32, y: i32, z: i32) -> Result<()> {
    let mut pos = f.world(x, y, z);
    if !ctx.chunk_box.inside(pos) {
        return Ok(());
    }
    let l = pos[1];
    let (mut down, mut up) = (true, true);
    let mut m = 1;
    let lava = ctx.names.block("minecraft:lava");
    while down || up {
        if down {
            pos[1] = l - m;
            let state = ctx.state_at(pos)?;
            let replaceable = ctx.g.replaceable(state) && Some(ctx.block_of(state)) != lava;
            if !replaceable && ctx.sturdy(pos, UP)? {
                for y in l - m + 1..l {
                    ctx.set_block([pos[0], y, pos[2]], k.wood, 2)?;
                }
                return Ok(());
            }
            down = m <= 20 && replaceable && pos[1] > ctx.min_y() + 1;
        }
        if up {
            pos[1] = l + m;
            let state = ctx.state_at(pos)?;
            let replaceable = ctx.g.replaceable(state);
            if !replaceable && ctx.center_support_down(pos)? && !ctx.g.falling(state) {
                ctx.set_block([pos[0], l + 1, pos[2]], k.fence, 2)?;
                for y in l + 2..l + m {
                    ctx.set_block([pos[0], y, pos[2]], k.chain, 2)?;
                }
                return Ok(());
            }
            up = m <= 50 && replaceable && pos[1] < ctx.max_y();
        }
        m += 1;
    }
    Ok(())
}

/// `MineShaftCrossing`: no orientation, so local coordinates are world ones.
fn crossing(ctx: &mut Ctx, words: &mut [i64], f: &Frame, k: &Kind) -> Result<bool> {
    let two_floored = words[11] != 0;
    if !invalid_location(ctx, f)? {
        let b = f.bbox;
        let air = k.cave_air;
        if two_floored {
            ctx.generate_box(f, b.min_x + 1, b.min_y, b.min_z, b.max_x - 1, b.min_y + 3 - 1, b.max_z, air, air, false)?;
            ctx.generate_box(f, b.min_x, b.min_y, b.min_z + 1, b.max_x, b.min_y + 3 - 1, b.max_z - 1, air, air, false)?;
            ctx.generate_box(f, b.min_x + 1, b.max_y - 2, b.min_z, b.max_x - 1, b.max_y, b.max_z, air, air, false)?;
            ctx.generate_box(f, b.min_x, b.max_y - 2, b.min_z + 1, b.max_x, b.max_y, b.max_z - 1, air, air, false)?;
            ctx.generate_box(f, b.min_x + 1, b.min_y + 3, b.min_z + 1, b.max_x - 1, b.min_y + 3, b.max_z - 1, air, air, false)?;
        } else {
            ctx.generate_box(f, b.min_x + 1, b.min_y, b.min_z, b.max_x - 1, b.max_y, b.max_z, air, air, false)?;
            ctx.generate_box(f, b.min_x, b.min_y, b.min_z + 1, b.max_x, b.max_y, b.max_z - 1, air, air, false)?;
        }
        for (x, z) in [(b.min_x + 1, b.min_z + 1), (b.min_x + 1, b.max_z - 1), (b.max_x - 1, b.min_z + 1), (b.max_x - 1, b.max_z - 1)] {
            if !ctx.is_air_state(ctx.get_block(f, x, b.max_y + 1, z)?) {
                ctx.generate_box(f, x, b.min_y, z, x, b.max_y, z, k.planks, air, false)?;
            }
        }
        let i = b.min_y - 1;
        for j in b.min_x..=b.max_x {
            for z in b.min_z..=b.max_z {
                set_planks(ctx, f, k.planks, j, i, z)?;
            }
        }
    }
    Ok(true)
}

/// `MineShaftRoom`: words `[…, MST, entrance count, entrance boxes (6 each)]`.
fn room(ctx: &mut Ctx, words: &mut [i64], f: &Frame, k: &Kind) -> Result<bool> {
    let count = words[10] as usize;
    if words.len() < 11 + count * 6 {
        return Ok(false);
    }
    if !invalid_location(ctx, f)? {
        let b = f.bbox;
        let air = k.cave_air;
        ctx.generate_box(f, b.min_x, b.min_y + 1, b.min_z, b.max_x, (b.min_y + 3).min(b.max_y), b.max_z, air, air, false)?;
        for entrance in words[11..11 + count * 6].chunks_exact(6) {
            let e = BoundingBox::from_words(entrance);
            ctx.generate_box(f, e.min_x, e.max_y - 2, e.min_z, e.max_x, e.max_y, e.max_z, air, air, false)?;
        }
        ctx.generate_upper_half_sphere(f, b.min_x, b.min_y + 4, b.min_z, b.max_x, b.max_y, b.max_z, air, false)?;
    }
    Ok(true)
}

fn stairs(ctx: &mut Ctx, f: &Frame, k: &Kind) -> Result<bool> {
    if !invalid_location(ctx, f)? {
        let air = k.cave_air;
        ctx.generate_box(f, 0, 5, 0, 2, 7, 1, air, air, false)?;
        ctx.generate_box(f, 0, 0, 7, 2, 2, 8, air, air, false)?;
        for i in 0..5 {
            ctx.generate_box(f, 0, 5 - i - i32::from(i < 4), 2 + i, 2, 7 - i, 2 + i, air, air, false)?;
        }
    }
    Ok(true)
}

#[allow(dead_code)]
const _: usize = DOWN;

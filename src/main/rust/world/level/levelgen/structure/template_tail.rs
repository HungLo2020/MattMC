//! `TemplateStructurePiece.postProcess` after `placeInWorld`: each piece
//! family's `handleDataMarker` over the template's DATA-mode structure blocks,
//! the jigsaw blocks' `final_state`, and `RuinedPortalPiece`'s netherrack,
//! drip columns, vines and leaves. Entities and loot are Java steps.
use super::piece::{BoundingBox, Ctx, OCEAN_FLOOR_WG, WORLD_SURFACE_WG};
use super::Misc;
use crate::content::block::BlockId;
use crate::world::level::levelgen::decoration::region::{Result, OP_COLLISION_FACE};
use crate::world::level::levelgen::decoration::template::{placed_position, Template};

/// The piece families whose data markers do something.
const IGLOO: i64 = 1;
const SHIPWRECK: i64 = 2;
const END_CITY: i64 = 3;
const OCEAN_RUIN: i64 = 4;
const MANSION: i64 = 5;

/// `RuinedPortalPiece.VerticalPlacement` ordinals.
const ON_LAND_SURFACE: i64 = 0;
const ON_OCEAN_FLOOR: i64 = 2;
const IN_NETHER: i64 = 5;

/// `Direction` ordinals and `Direction.Plane.HORIZONTAL`'s faces.
const NORTH: usize = 2;
const SOUTH: usize = 3;
const WEST: usize = 4;
const EAST: usize = 5;
const HORIZONTAL: [usize; 4] = [NORTH, EAST, SOUTH, WEST];
const STEP: [[i32; 3]; 6] = [[0, -1, 0], [0, 1, 0], [0, 0, -1], [0, 0, 1], [-1, 0, 0], [1, 0, 0]];

fn opposite(d: usize) -> usize {
    d ^ 1
}

/// `Rotation.rotate(direction)` for a horizontal direction, by rotation ordinal.
fn rotate(rotation: i64, d: usize) -> usize {
    let index = HORIZONTAL.iter().position(|&h| h == d).unwrap_or(0);
    HORIZONTAL[(index + rotation.rem_euclid(4) as usize) % 4]
}

/// The call: where the template was placed and with what.
pub(crate) struct Placement {
    pub(crate) origin: [i32; 3],
    pub(crate) mirror: usize,
    pub(crate) rotation: i64,
    pub(crate) pivot: [i32; 3],
    pub(crate) sea_level: i32,
    /// `Fluids.WATER` and `Fluids.FLOWING_WATER` registry IDs (`FluidTags.WATER`).
    pub(crate) water: [i32; 2],
}

/// `words`: `[kind, ocean ruin is large, ruined portal (0/1), vertical
/// placement, cold, overgrown, vines, piece box (6)]`.
pub(crate) fn tail(ctx: &mut Ctx, template: &Template, words: &[i64], at: &Placement) -> Result<bool> {
    let Some(tail) = template.tail() else {
        return Ok(false);
    };
    if words.len() < 13 {
        return Ok(false);
    }
    let Some(s) = States::new(ctx) else {
        return Ok(false);
    };
    let bounds = ctx.chunk_box;
    for (pos, marker) in &tail.markers {
        let pos = placed_position(*pos, at.origin, at.mirror, at.rotation as usize, at.pivot);
        if bounds.inside(pos) {
            data_marker(ctx, &s, words, at, marker, pos)?;
        }
    }
    for &(pos, state) in &tail.jigsaws {
        let pos = placed_position(pos, at.origin, at.mirror, at.rotation as usize, at.pivot);
        if bounds.inside(pos) {
            ctx.set_block(pos, state, 3)?;
        }
    }
    if words[2] != 0 {
        let portal = Portal {
            vertical: words[3],
            cold: words[4] != 0,
            overgrown: words[5] != 0,
            vines: words[6] != 0,
            bbox: BoundingBox::from_words(&words[7..13]),
        };
        portal.after(ctx, &s)?;
    }
    Ok(true)
}

struct States {
    air: u32,
    water: u32,
    chest: [u32; 6],
    chest_waterlogged: u32,
    netherrack: u32,
    magma: u32,
    leaves: u32,
    vine: [u32; 6],
    air_block: BlockId,
    obsidian: BlockId,
    lava: BlockId,
    vine_block: BlockId,
    netherrack_block: BlockId,
}

impl States {
    fn new(ctx: &Ctx) -> Option<Self> {
        let n = &ctx.names;
        let chest = |facing: &str| n.state("minecraft:chest", &[("facing", facing)]);
        let vine = |face: &str| if face == "down" { n.state("minecraft:vine", &[]) } else { n.state("minecraft:vine", &[(face, "true")]) };
        Some(Self {
            air: n.state("minecraft:air", &[])?,
            water: n.state("minecraft:water", &[])?,
            chest: [chest("north")?, chest("north")?, chest("north")?, chest("south")?, chest("west")?, chest("east")?],
            chest_waterlogged: n.state("minecraft:chest", &[("waterlogged", "true")])?,
            netherrack: n.state("minecraft:netherrack", &[])?,
            magma: n.state("minecraft:magma_block", &[])?,
            leaves: n.state("minecraft:jungle_leaves", &[("persistent", "true")])?,
            vine: [vine("down")?, vine("up")?, vine("north")?, vine("south")?, vine("west")?, vine("east")?],
            air_block: n.block("minecraft:air")?,
            obsidian: n.block("minecraft:obsidian")?,
            lava: n.block("minecraft:lava")?,
            vine_block: n.block("minecraft:vine")?,
            netherrack_block: n.block("minecraft:netherrack")?,
        })
    }
}

fn below(pos: [i32; 3]) -> [i32; 3] {
    [pos[0], pos[1] - 1, pos[2]]
}

/// `Level.isInSpawnableBounds`.
fn spawnable(pos: [i32; 3]) -> bool {
    (-20_000_000..20_000_000).contains(&pos[1]) && (-30_000_000..30_000_000).contains(&pos[0]) && (-30_000_000..30_000_000).contains(&pos[2])
}

fn step(ctx: &mut Ctx, index: Option<i64>, pos: [i32; 3]) -> Result<()> {
    match index {
        Some(index) => ctx.java_step(index, pos),
        None => Err(crate::world::level::levelgen::decoration::region::Failure::Declined),
    }
}

/// Each family's `handleDataMarker`.
fn data_marker(ctx: &mut Ctx, s: &States, words: &[i64], at: &Placement, marker: &str, pos: [i32; 3]) -> Result<()> {
    match words[0] {
        IGLOO if marker == "chest" => {
            ctx.set_block(pos, s.air, 3)?;
            let index = ctx.g.chest_step("minecraft:chests/igloo_chest");
            step(ctx, index, below(pos))?;
        }
        SHIPWRECK => {
            let table = match marker {
                "map_chest" => "minecraft:chests/shipwreck_map",
                "treasure_chest" => "minecraft:chests/shipwreck_treasure",
                "supply_chest" => "minecraft:chests/shipwreck_supply",
                _ => return Ok(()),
            };
            let index = ctx.g.randomizable_step(table);
            step(ctx, index, below(pos))?;
        }
        END_CITY => {
            if marker.starts_with("Chest") {
                if ctx.chunk_box.inside(below(pos)) {
                    let index = ctx.g.randomizable_step("minecraft:chests/end_city_treasure");
                    step(ctx, index, below(pos))?;
                }
            } else if ctx.chunk_box.inside(pos) && spawnable(pos) {
                if marker.starts_with("Sentry") {
                    let index = ctx.g.misc_step(Misc::Shulker);
                    ctx.java_step(index, pos)?;
                } else if marker.starts_with("Elytra") {
                    let index = ctx.g.misc_step(Misc::ElytraFrame) + at.rotation.rem_euclid(4);
                    ctx.java_step(index, pos)?;
                }
            }
        }
        OCEAN_RUIN => {
            if marker == "chest" {
                let fluid = ctx.g.fluid_type(ctx.state_at(pos)?);
                let state = if at.water.contains(&fluid) { s.chest_waterlogged } else { s.chest[NORTH] };
                ctx.set_block(pos, state, 2)?;
                let table = if words[1] != 0 { "minecraft:chests/underwater_ruin_big" } else { "minecraft:chests/underwater_ruin_small" };
                let index = ctx.g.chest_step(table);
                step(ctx, index, pos)?;
            } else if marker == "drowned" {
                let index = ctx.g.misc_step(Misc::Drowned);
                ctx.java_step(index, pos)?;
                ctx.set_block(pos, if pos[1] > at.sea_level { s.air } else { s.water }, 2)?;
            }
        }
        MANSION => {
            if marker.starts_with("Chest") {
                let facing = match marker {
                    "ChestWest" => Some(WEST),
                    "ChestEast" => Some(EAST),
                    "ChestSouth" => Some(SOUTH),
                    "ChestNorth" => Some(NORTH),
                    _ => None,
                };
                let state = facing.map_or(s.chest[NORTH], |d| s.chest[rotate(at.rotation, d)]);
                ctx.create_chest(pos, "minecraft:chests/woodland_mansion", Some(state))?;
            } else {
                let (misc, count) = match marker {
                    "Mage" => (Misc::Evoker, 1),
                    "Warrior" => (Misc::Vindicator, 1),
                    "Group of Allays" => (Misc::Allays, 1),
                    _ => return Ok(()),
                };
                for _ in 0..count {
                    let index = ctx.g.misc_step(misc);
                    ctx.java_step(index, pos)?;
                }
                ctx.set_block(pos, s.air, 2)?;
            }
        }
        _ => {}
    }
    Ok(())
}

/// `RuinedPortalPiece.postProcess` after the template.
struct Portal {
    vertical: i64,
    cold: bool,
    overgrown: bool,
    vines: bool,
    bbox: BoundingBox,
}

impl Portal {
    fn after(&self, ctx: &mut Ctx, s: &States) -> Result<()> {
        self.spread_netherrack(ctx, s)?;
        self.drip_columns_below(ctx, s)?;
        if self.vines || self.overgrown {
            let b = self.bbox;
            for z in b.min_z..=b.max_z {
                for y in b.min_y..=b.max_y {
                    for x in b.min_x..=b.max_x {
                        if self.vines {
                            self.maybe_add_vines(ctx, s, [x, y, z])?;
                        }
                        if self.overgrown {
                            self.maybe_add_leaves_above(ctx, s, [x, y, z])?;
                        }
                    }
                }
            }
        }
        Ok(())
    }

    fn maybe_add_vines(&self, ctx: &mut Ctx, s: &States, pos: [i32; 3]) -> Result<()> {
        let state = ctx.state_at(pos)?;
        if ctx.is_air_state(state) || ctx.block_of(state) == s.vine_block {
            return Ok(());
        }
        let d = HORIZONTAL[ctx.random.next_int(4) as usize];
        let next = [pos[0] + STEP[d][0], pos[1] + STEP[d][1], pos[2] + STEP[d][2]];
        if ctx.is_air_state(ctx.state_at(next)?) && collision_face(ctx, pos, state, d)? {
            ctx.set_block(next, s.vine[opposite(d)], 3)?;
        }
        Ok(())
    }

    fn maybe_add_leaves_above(&self, ctx: &mut Ctx, s: &States, pos: [i32; 3]) -> Result<()> {
        if ctx.random.next_float() < 0.5 && ctx.block_of(ctx.state_at(pos)?) == s.netherrack_block {
            let above = [pos[0], pos[1] + 1, pos[2]];
            if ctx.is_air_state(ctx.state_at(above)?) {
                ctx.set_block(above, s.leaves, 3)?;
            }
        }
        Ok(())
    }

    fn drip_columns_below(&self, ctx: &mut Ctx, s: &States) -> Result<()> {
        let b = self.bbox;
        for x in b.min_x + 1..b.max_x {
            for z in b.min_z + 1..b.max_z {
                let pos = [x, b.min_y, z];
                if ctx.block_of(ctx.state_at(pos)?) == s.netherrack_block {
                    self.drip_column(ctx, s, below(pos))?;
                }
            }
        }
        Ok(())
    }

    fn drip_column(&self, ctx: &mut Ctx, s: &States, mut pos: [i32; 3]) -> Result<()> {
        self.netherrack_or_magma(ctx, s, pos)?;
        let mut i = 8;
        while i > 0 && ctx.random.next_float() < 0.5 {
            pos[1] -= 1;
            i -= 1;
            self.netherrack_or_magma(ctx, s, pos)?;
        }
        Ok(())
    }

    fn netherrack_or_magma(&self, ctx: &mut Ctx, s: &States, pos: [i32; 3]) -> Result<()> {
        let state = if !self.cold && ctx.random.next_float() < 0.07 { s.magma } else { s.netherrack };
        ctx.set_block(pos, state, 3).map(|_| ())
    }

    fn spread_netherrack(&self, ctx: &mut Ctx, s: &States) -> Result<()> {
        const FS: [f32; 14] = [1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 0.9, 0.9, 0.8, 0.7, 0.6, 0.4, 0.2];
        let surface = self.vertical == ON_LAND_SURFACE || self.vertical == ON_OCEAN_FLOOR;
        let b = self.bbox;
        let (i, j) = (b.min_x + (b.max_x - b.min_x + 1) / 2, b.min_z + (b.max_z - b.min_z + 1) / 2);
        let k = FS.len() as i32;
        let l = ((b.max_x - b.min_x + 1) + (b.max_z - b.min_z + 1)) / 2;
        let m = ctx.random.next_int((8 - l / 2).max(1));
        let heightmap = if self.vertical == ON_OCEAN_FLOOR { OCEAN_FLOOR_WG } else { WORLD_SURFACE_WG };
        for o in i - k..=i + k {
            for p in j - k..=j + k {
                let r = ((o - i).abs() + (p - j).abs() + m).max(0);
                if r < k && ctx.random.next_double() < FS[r as usize] as f64 {
                    let surface_y = ctx.region.height_by_type(heightmap, o, p)? - 1;
                    let t = if surface { surface_y } else { b.min_y.min(surface_y) };
                    let pos = [o, t, p];
                    if (t - b.min_y).abs() <= 3 && self.replaceable(ctx, s, pos)? {
                        self.netherrack_or_magma(ctx, s, pos)?;
                        if self.overgrown {
                            self.maybe_add_leaves_above(ctx, s, pos)?;
                        }
                        self.drip_column(ctx, s, below(pos))?;
                    }
                }
            }
        }
        Ok(())
    }

    /// `canBlockBeReplacedByNetherrackOrMagma`.
    fn replaceable(&self, ctx: &Ctx, s: &States, pos: [i32; 3]) -> Result<bool> {
        let state = ctx.state_at(pos)?;
        let block = ctx.block_of(state);
        Ok(block != s.air_block && block != s.obsidian && !ctx.g.cannot_replace(state) && (self.vertical == IN_NETHER || block != s.lava))
    }
}

/// `Block.isFaceFull(state.getCollisionShape(level, pos), d)`; dynamic shapes ask Java.
fn collision_face(ctx: &Ctx, pos: [i32; 3], state: u32, d: usize) -> Result<bool> {
    if !ctx.g.dynamic(state) {
        return Ok(ctx.g.collision_face(state, d));
    }
    let mut args = [pos[0] as i64, pos[1] as i64, pos[2] as i64, d as i64];
    ctx.region.pure_call(OP_COLLISION_FACE, &mut args)?;
    Ok(args[0] != 0)
}

#[cfg(test)]
mod tests {
    use super::*;

    const NAMES: [&str; 6] = ["down", "up", "north", "south", "west", "east"];

    #[test]
    fn rotation_turns_horizontal_directions_clockwise() {
        assert_eq!(rotate(0, SOUTH), SOUTH);
        assert_eq!(rotate(1, SOUTH), WEST);
        assert_eq!(rotate(2, SOUTH), NORTH);
        assert_eq!(rotate(3, SOUTH), EAST);
        assert_eq!(rotate(1, NORTH), EAST);
        assert_eq!(opposite(NORTH), SOUTH);
        assert_eq!(opposite(WEST), EAST);
        assert_eq!(NAMES[opposite(EAST)], "west");
    }
}

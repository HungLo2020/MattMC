//! Structure pieces placed natively over the bound decoration region: the
//! `StructurePiece` helpers (orientation, boxes, chests, column fills) and each
//! piece family's `postProcess`, with Java's draws, reads and writes in Java's
//! order. Java keeps the piece objects (their saved form) and hands each piece's
//! fields here; the fields a placement changes (ground height, moved boxes,
//! spawn flags) come back. Entities and block-entity contents are Java steps.
pub(crate) mod ffi;
pub(crate) mod fortress;
pub(crate) mod jigsaw;
pub(crate) mod piece;
pub(crate) mod placement;
pub(crate) mod mineshaft;
pub(crate) mod end_city_plan;
pub(crate) mod fortress_plan;
pub(crate) mod mansion_plan;
pub(crate) mod mineshaft_plan;
pub(crate) mod monument_plan;
pub(crate) mod small_plan;
pub(crate) mod stronghold_plan;
pub(crate) mod monument;
pub(crate) mod scattered;
pub(crate) mod stronghold;
pub(crate) mod stubs;
pub(crate) mod template_tail;

use crate::content::block::{BlockId, BlockRegistry, StateId};
use std::sync::OnceLock;

/// Data Java exports once: what block behaviour decides about each state.
pub(crate) struct Globals {
    /// Per state: itself under each piece orientation's mirror and rotation
    /// (none, `LEFT_RIGHT`, `LEFT_RIGHT` then `CLOCKWISE_90`, `CLOCKWISE_90`).
    transforms: Box<[[u32; 4]]>,
    /// Per state: `getFluidState().getType()`'s registry ID, or -1 when empty.
    fluid_types: Box<[i32]>,
    solid_render: Box<[u64]>,
    /// `StructurePiece.isReplaceableByStructures`.
    replaceable: Box<[u64]>,
    /// `StructurePiece.SHAPE_CHECK_BLOCKS`.
    shape_check: Box<[u64]>,
    /// `BlockState.liquid()`.
    liquid: Box<[u64]>,
    /// `isFaceSturdy(empty, origin, d)` by `Direction` ordinal, for states without a dynamic shape.
    sturdy: [Box<[u64]>; 6],
    /// `Block.canSupportCenter(empty, origin, DOWN)` likewise.
    center_down: Box<[u64]>,
    /// `getBlock() instanceof FallingBlock`.
    falling: Box<[u64]>,
    /// `Block.hasDynamicShape()`: shape tests ask Java at the position.
    dynamic: Box<[u64]>,
    /// `Block.isFaceFull(getCollisionShape(empty, origin), d)` by `Direction` ordinal, without a dynamic shape.
    collision_face: [Box<[u64]>; 6],
    /// `BlockTags.FEATURES_CANNOT_REPLACE`.
    cannot_replace: Box<[u64]>,
    /// Loot table names in Java's step-table order.
    loot: Vec<String>,
}

/// Java's step table: chest loot, dispenser loot, brushable loot and
/// randomizable-container loot per table, then the fixed steps below.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
#[repr(i64)]
pub(crate) enum Misc {
    Witch = 0,
    Cat = 1,
    /// A chest minecart with the abandoned mineshaft loot table (draws its seed).
    MineshaftMinecart = 2,
    /// The spawner block entity's entity: a cave spider.
    SpawnerCaveSpider = 3,
    SpawnerSilverfish = 4,
    SpawnerBlaze = 5,
    /// `OceanMonumentPiece.spawnElder`.
    ElderGuardian = 6,
    /// End city sentry.
    Shulker = 7,
    /// End city ship's elytra item frame, facing `rotation.rotate(SOUTH)` by rotation ordinal (8 to 11).
    ElytraFrame = 8,
    Drowned = 12,
    Evoker = 13,
    Vindicator = 14,
    /// One to three allays (the count draws from the region random in Java).
    Allays = 15,
}

/// Biome tags Java tests at a position (`OP_BIOME_TAG`), by index.
pub(crate) const TAG_MINESHAFT_BLOCKING: i64 = 0;

static GLOBALS: OnceLock<Globals> = OnceLock::new();

pub(crate) fn globals() -> Option<&'static Globals> {
    GLOBALS.get()
}

fn bit(set: &[u64], state: u32) -> bool {
    set.get(state as usize >> 6).is_some_and(|w| w >> (state & 63) & 1 != 0)
}

impl Globals {
    pub(crate) fn transform(&self, state: u32, orientation: usize) -> u32 {
        self.transforms.get(state as usize).map_or(state, |t| t[orientation])
    }
    pub(crate) fn fluid_type(&self, state: u32) -> i32 {
        self.fluid_types.get(state as usize).copied().unwrap_or(-1)
    }
    pub(crate) fn solid_render(&self, state: u32) -> bool {
        bit(&self.solid_render, state)
    }
    pub(crate) fn replaceable(&self, state: u32) -> bool {
        bit(&self.replaceable, state)
    }
    pub(crate) fn shape_check(&self, state: u32) -> bool {
        bit(&self.shape_check, state)
    }
    pub(crate) fn liquid(&self, state: u32) -> bool {
        bit(&self.liquid, state)
    }
    pub(crate) fn sturdy(&self, state: u32, direction: usize) -> bool {
        bit(&self.sturdy[direction], state)
    }
    pub(crate) fn center_down(&self, state: u32) -> bool {
        bit(&self.center_down, state)
    }
    pub(crate) fn falling(&self, state: u32) -> bool {
        bit(&self.falling, state)
    }
    pub(crate) fn dynamic(&self, state: u32) -> bool {
        bit(&self.dynamic, state)
    }
    pub(crate) fn collision_face(&self, state: u32, direction: usize) -> bool {
        bit(&self.collision_face[direction], state)
    }
    pub(crate) fn cannot_replace(&self, state: u32) -> bool {
        bit(&self.cannot_replace, state)
    }
    fn loot_index(&self, name: &str) -> Option<i64> {
        self.loot.iter().position(|l| l == name).map(|i| i as i64)
    }
    /// Java step index: a chest's loot table, set if the block entity is a chest.
    pub(crate) fn chest_step(&self, table: &str) -> Option<i64> {
        self.loot_index(table)
    }
    pub(crate) fn dispenser_step(&self, table: &str) -> Option<i64> {
        self.loot_index(table).map(|i| i + self.loot.len() as i64)
    }
    pub(crate) fn brushable_step(&self, table: &str) -> Option<i64> {
        self.loot_index(table).map(|i| i + 2 * self.loot.len() as i64)
    }
    /// `RandomizableContainer.setBlockEntityLootTable`: any randomizable container.
    pub(crate) fn randomizable_step(&self, table: &str) -> Option<i64> {
        self.loot_index(table).map(|i| i + 3 * self.loot.len() as i64)
    }
    pub(crate) fn misc_step(&self, misc: Misc) -> i64 {
        4 * self.loot.len() as i64 + misc as i64
    }
}

/// A piece family's states: block names with property values.
pub(crate) type StateList = &'static [(&'static str, &'static [(&'static str, &'static str)])];

/// Resolves a family's states once per process; None when any is missing.
pub(crate) fn resolve(cache: &'static OnceLock<Option<Box<[u32]>>>, names: &Names, list: StateList) -> Option<&'static [u32]> {
    cache.get_or_init(|| list.iter().map(|(name, properties)| names.state(name, properties)).collect()).as_deref()
}

/// `RandomSource.create(seed).forkPositional().at(x, y, z)`: a Legacy source.
pub(crate) fn positional_legacy(seed: i64, x: i32, y: i32, z: i32) -> crate::world::level::levelgen::random::Legacy {
    use crate::world::level::levelgen::random::{position_seed, Legacy};
    let factory = Legacy::new(seed).next_long();
    Legacy::new(position_seed(x, y, z) ^ factory)
}

/// Block states by name and property values, from the installed registry.
pub(crate) struct Names {
    registry: &'static BlockRegistry,
}

impl Names {
    pub(crate) fn new() -> Option<Self> {
        Some(Self { registry: crate::content::block::installed()? })
    }

    pub(crate) fn block(&self, name: &str) -> Option<BlockId> {
        self.registry.by_name(name)
    }

    /// `name`'s default state with each `(property, value)` set.
    pub(crate) fn state(&self, name: &str, properties: &[(&str, &str)]) -> Option<u32> {
        let block = self.registry.by_name(name)?;
        let mut state = self.registry.block(block).default_state();
        for &(property, value) in properties {
            state = self.with(state, property, value)?;
        }
        Some(state.0 as u32)
    }

    /// `state.setValue(property, value)` by names.
    pub(crate) fn with(&self, state: StateId, property: &str, value: &str) -> Option<StateId> {
        let block = self.registry.block(self.registry.block_of(state));
        let id = block.properties().find(|&p| self.registry.property(p).name() == property)?;
        let index = self.registry.property(id).values().iter().position(|v| v == value)?;
        self.registry.with_value(state, id, index as u16)
    }

    /// The value name of `property` in `state`.
    pub(crate) fn value(&self, state: StateId, property: &str) -> Option<&'static str> {
        let block = self.registry.block(self.registry.block_of(state));
        let id = block.properties().find(|&p| self.registry.property(p).name() == property)?;
        let index = self.registry.value(state, id)?;
        self.registry.property(id).values().get(index as usize).map(String::as_str)
    }

    pub(crate) fn registry(&self) -> &'static BlockRegistry {
        self.registry
    }
}

/// One piece's words: `[kind, box (6), orientation, gen depth, fields…]`.
pub(crate) struct Piece {
    kind: i64,
    frame: piece::Frame,
}

impl Piece {
    pub(crate) fn read(words: &[i64]) -> Option<Self> {
        let bbox = piece::BoundingBox::from_words(words.get(1..7)?);
        Some(Self { kind: words[0], frame: piece::Frame { bbox, orientation: *words.get(7)? as i32 } })
    }

    /// Places the piece; false declines (nothing written) for Java's path.
    pub(crate) fn place(
        &self,
        ctx: &mut piece::Ctx,
        _chunk: [i32; 2],
        _pivot: [i32; 3],
        words: &mut [i64],
    ) -> crate::world::level::levelgen::decoration::region::Result<bool> {
        match self.kind {
            1 if words.len() >= 15 => scattered::swamp_hut(ctx, words, self.frame),
            2 => scattered::buried_treasure(ctx, words, self.frame),
            3 if words.len() >= 21 => scattered::desert_pyramid(ctx, words, self.frame),
            4 if words.len() >= 17 => scattered::jungle_temple(ctx, words, self.frame),
            5..=8 => mineshaft::place(self.kind, ctx, words, self.frame),
            10..=21 if words.len() >= 10 => stronghold::place(self.kind, ctx, words, self.frame),
            30..=43 => fortress::place(self.kind, ctx, words, self.frame),
            50 => monument::place(ctx, words, self.frame),
            _ => Ok(false),
        }
    }
}

/// `WoodlandMansionStructure.afterPlace`: `words` is `[2, count, box (6) per piece]`;
/// cobblestone under the pieces' footprint, down through air and liquids.
pub(crate) fn mansion_after_place(ctx: &mut piece::Ctx, words: &[i64]) -> crate::world::level::levelgen::decoration::region::Result<bool> {
    let Some(cobblestone) = ctx.names.state("minecraft:cobblestone", &[]) else {
        return Ok(false);
    };
    let count = usize::try_from(words[1]).unwrap_or(0);
    if count == 0 || words.len() < 2 + count * 6 {
        return Ok(false);
    }
    let boxes: Vec<piece::BoundingBox> = words[2..2 + count * 6].chunks_exact(6).map(piece::BoundingBox::from_words).collect();
    let all = boxes.iter().skip(1).fold(boxes[0], |a, b| piece::BoundingBox {
        min_x: a.min_x.min(b.min_x),
        min_y: a.min_y.min(b.min_y),
        min_z: a.min_z.min(b.min_z),
        max_x: a.max_x.max(b.max_x),
        max_y: a.max_y.max(b.max_y),
        max_z: a.max_z.max(b.max_z),
    });
    let (i, j) = (ctx.min_y(), all.min_y);
    let chunk_box = ctx.chunk_box;
    for k in chunk_box.min_x..=chunk_box.max_x {
        for l in chunk_box.min_z..=chunk_box.max_z {
            let pos = [k, j, l];
            if !ctx.is_air_state(ctx.state_at(pos)?) && all.inside(pos) && boxes.iter().any(|b| b.inside(pos)) {
                let mut m = j - 1;
                while m > i {
                    let at = [k, m, l];
                    let state = ctx.state_at(at)?;
                    if !ctx.is_air_state(state) && !ctx.g.liquid(state) {
                        break;
                    }
                    ctx.set_block(at, cobblestone, 2)?;
                    m -= 1;
                }
            }
        }
    }
    Ok(true)
}

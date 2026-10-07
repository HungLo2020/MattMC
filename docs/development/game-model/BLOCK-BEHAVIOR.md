# Block behavior (proposal)

> Proposal; not implemented. See the [game model index](index.md).

## What Java's 327 subclasses actually vary by

| Hooks overridden | Block classes |
|---|---|
| 0 (codec or constructor only) | 32 |
| 1–2 | 84 |
| 3–5 | 75 |
| 6–10 | 108 |
| 11+ | 28 (Lectern, SculkSensor, RedStoneWire, Chest, Hopper, …) |

The most common overrides are `createBlockStateDefinition` (172 classes),
`getShape` (153), `getStateForPlacement` (126) and `updateShape` (120). Many
classes are reused heavily: `RotatedPillarBlock` 64 times, `SlabBlock` 60
times, `WallBlock` 27 times, and about 140 blocks are plain `Block`. Most
differences are therefore **parameters and data**, not code.

## Proposal: behavior traits per family, parameters in data

```rust
pub trait BlockBehavior: Send + Sync + 'static {
    // Defaults match vanilla `Block`; implement only what differs.
    fn random_tick(&self, ctx: &mut WorldCtx, at: BlockPos, s: StateId) {}
    fn tick(&self, ctx: &mut WorldCtx, at: BlockPos, s: StateId) {}
    fn update_shape(&self, ctx: &mut ShapeCtx, at: BlockPos, s: StateId, dir: Direction, neighbor: StateId) -> StateId { s }
    fn placement_state(&self, ctx: &PlaceCtx, default: StateId) -> Option<StateId> { Some(default) }
    fn can_survive(&self, ctx: &LevelView, at: BlockPos, s: StateId) -> bool { true }
    fn use_without_item(&self, ctx: &mut WorldCtx, at: BlockPos, s: StateId, player: EntityId) -> InteractionResult { InteractionResult::Pass }
    fn entity_inside(&self, ctx: &mut WorldCtx, at: BlockPos, s: StateId, entity: EntityId) {}
    // … the remaining ~40 hooks, grouped by concern
}
```

- **One implementation per family, configured by fields**:
  `Crop { max_age: u8, seed: ItemId }`, `Slab`, `Stairs { base: StateId }`,
  `Door { set: BlockSetType }`, `Pillar`. The block registry stores a
  `&'static dyn BlockBehavior` per `BlockId`. 1,211 blocks then share roughly
  a hundred behaviors.
- **Shared mix-ins are helper functions, not base classes.** Waterlogging,
  horizontal facing, attachment checks and "falls like sand" become small
  functions that behaviors call. They are not levels of inheritance.
  Behaviors stay flat, one file per family.
- **Traits over enums** because the set is open: new content adds a type in its
  own file without editing a central `match`. An indirect call is cheap next
  to the world access these hooks perform anyway.

## Keep dispatch off hot paths

1. Everything hot paths need is a **column** (see [block states](BLOCK-STATES.md)).
   Lighting, collision, meshing and saving never call behavior code.
2. Each block records **which hooks it overrides** as a bitmask, filled in at
   registration. Systems skip blocks with default behavior without a call:
   random ticks only for the `RANDOM_TICKS` flag, `entity_inside` only for
   blocks that implement it, and so on.
3. Data-only answers are columns or data: drops come from loot tables (only
   6 Java classes override `getDrops`), shapes are per state, and light comes
   from `emission`.

## The world context

Behaviors never hold world references. They receive a context for the call:

- **`LevelView`**: reads (state at a position, neighbors, fluid, light,
  tags).
- **`WorldCtx`**: reads plus effects: setting blocks with update flags,
  scheduling ticks, neighbor updates, level events and sounds, spawning
  entities, and the level's random source.

Java's update semantics are part of parity. `setBlock` flags, the neighbor
updater's order and depth limit, and scheduled-tick ordering must be
reproduced exactly, so `WorldCtx` performs them in Java's order. Random draws
go through Java-compatible RNGs; `levelgen/random.rs` already has the
algorithms.

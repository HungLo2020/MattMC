# Components and entities (proposal)

> Proposal; not implemented. See the [game model index](index.md).

## The model

There are two kinds of world content, stored very differently:

1. **Blocks**: about 98% of the world. A placed block is stored only as its
   state (a palette entry in its section), as in Java; there is no object per
   placed block. Blocks have no per-instance data and are never entities.
2. **Entities**: anything with per-instance data. These live in a
   per-dimension **component store**: mobs, players, items on the ground,
   projectiles, minecarts, **and block entities**.

A **block entity is an entity with a `BlockAnchor` component**:

```rust
struct BlockAnchor { pos: BlockPos, state: StateId }   // where, and the block it belongs to
struct Position { x: f64, y: f64, z: f64 }             // free entities instead
```

Domain data is ordinary components shared by any entity that needs it:

| Component | Block entities | Free entities |
|---|---|---|
| `Inventory` (Java `Container`) | chest, barrel, furnace, hopper, shulker box | chest/hopper minecart, chest boat, player |
| `HopperTransfer` | hopper | hopper minecart (Java already shares `HopperBlockEntity.suckInItems`) |
| `CustomName` | named containers | named mobs |
| `SignText`, `Cooking`, `BrewProgress`, `BeeOccupants` | one owner each | — |

A new content type picks existing components and adds only what is new. The
"inventory transfer" system then works for every entity that has an
`Inventory`, without knowing whether it is anchored to a block.

## Entities lose their inheritance

Java's deepest chain is nine classes (TraderLlama → … → Entity). Each layer
becomes components:

- `LivingEntity` → `Health`, `Attributes`, `Effects`, `Equipment`
- `Mob` → `Ai` (goals or brain), `Navigation`
- `AgeableMob`/`Animal` → `Age`, `Breeding`
- `AbstractHorse` → `Tameable`, `Saddle`, and so on

An entity type is a registry entry: dimensions, category, tracking ranges,
the components to attach at spawn, and its systems. Synchronised entity data
(Java `SynchedEntityData`) becomes a component with dirty tracking per field.

## Ordering is observable: deterministic tick lists

Java ticks block entities in **insertion order** (`Level.blockEntityTickers`)
and entities in `EntityTickList` order. The order changes outcomes: hoppers,
item merging and redstone timing all depend on it. Therefore:

- Gameplay ticks iterate an **explicit ordered list** of entity IDs per level,
  maintained exactly as Java maintains its lists. Component storage order
  never decides gameplay order.
- Unordered queries (render extraction, statistics, saving one chunk) can
  iterate storage directly.

## Chunk scoping

Block entities load, save and unload with their chunk.

- A per-chunk index maps `BlockPos → EntityId`, replacing
  `ChunkAccess.blockEntities`.
- Unloading a chunk despawns its anchored entities after saving them.
- Free entities keep their own section storage, which replaces
  `EntitySectionStorage`.

## Persistence

Each persistent component type has a codec that writes and reads NBT, the way
Java's `saveAdditional`/`loadAdditional` do. Byte-exact output means keeping
Java's key order (the chunk-section slice learned this: it is `HashMap`
iteration order), so codecs declare their keys in insertion order and the
writer reproduces that order.

Block entities also exchange data with item stacks. In Java this is
`applyComponentsFromItemStack` and `collectImplicitComponents`. Here it
becomes copying between an item's data components and the block entity's
components, which can share value types (see [items](ITEMS.md)).

## The store

The store is written in-house: off-the-shelf ECS crates are ruled out, and
new crates need approval. It has sparse sets per component type and
generational `EntityId`s. Requirements:
- chunk-scoped despawn and save
- deterministic order under our control
- cheap single-entity access by ID, because gameplay mostly touches entities
  one at a time

Ticking follows Java's threading model; the store does not need parallel
system scheduling.

## Scope

Entities are kept high-level for now. Blocks, block states and items come
first. AI (goals and brains), synchronised data and entity networking are
designed when entity migration starts.

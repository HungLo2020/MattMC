# Registries and IDs (proposal)

> Proposal; not implemented. See the [game model index](index.md).

## Today

Rust has **no block, block-state, property or item types**. `content/` is
empty, and Java owns all definitions (`Blocks.java`, `BlockState`,
`StateDefinition`, the block classes). Rust only owns parts of the systems
that *read* blocks: lighting, chunk saving and worldgen.

Rust has no shared registry either. Each migrated slice gets its own per-state table
from its own Java bridge class, keyed by raw `i32`/`u16` state IDs, each with a
different lifetime. Examples:
- light types and occlusion faces (lighting)
- skylight descriptors
- heightmap masks
- air/fluid/motion flags (noise fill)
- state→block IDs (carvers)
- canonical encodings (chunk storage)
- meshing state records (rendering)
- a text snapshot of state names (shader packs)

The same facts (air, fluid, opacity, emission, occlusion faces, state identity,
biome identity) are encoded two to five times. Java computes occlusion-face
deduplication twice.

## Proposal

**Typed, dense IDs** in `content/`:

```rust
#[repr(transparent)] pub struct BlockId(u16);      // 1,211 today
#[repr(transparent)] pub struct StateId(u16);      // 31,809 today
#[repr(transparent)] pub struct ItemId(u16);       // 1,687
#[repr(transparent)] pub struct EntityTypeId(u16); // 239
#[repr(transparent)] pub struct BiomeId(u16);      // per server: data-driven
```

### Why `StateId` is 16 bits

Java's state ID is an `int`, but Java code mostly holds 4-byte references to
`BlockState` objects instead. Chunk sections store packed palette indices of
a few bits each. Sections that fall back to the global palette store IDs at
`ceillog2(state count)` bits, 15 today. The network uses the same computed
width, and saves store names.

So the Rust type does not affect saves, the network or packed chunk storage.
It sets the size of every *unpacked* state ID:
- sections unpacked for lighting, meshing, worldgen and saving
- block-update and tick queues
- anything that remembers a block, such as `BlockAnchor` or a falling block

At 16 bits those are half the size of Java's. The cost is a ceiling of 65,536
states, about twice today's count. A startup check fails loudly if
registration exceeds it. Widening means changing `StateId` and fixing what the
compiler reports. (Decided 2026-10-07.)

**Registries are frozen tables**, built once in a fixed order:

```rust
pub struct Registry<T> {
    entries: Vec<T>,                        // index == id
    by_name: HashMap<ResourceLocation, u16>,
    names: Vec<ResourceLocation>,
}
```

- **Built-in registries** (blocks, states, items, entity types, block-entity
  types, data-component types) are built at startup and then exposed as
  `&'static Registries` from a `OnceLock`. Hot code takes the reference once
  and indexes columns directly.
- **Data-driven registries** (biomes, dimension types, damage types,
  enchantments and the other ~43 datapack registries) belong to a server
  session (`WorldRegistries`), not to the process. They are rebuilt when
  datapacks reload.
- **Tags** are frozen bitsets per registry (`TagSet<BlockId>`). A membership
  test is one bit lookup.

## Ordering is part of the contract

Java assigns IDs by registration order. Block IDs follow the `Blocks.java`
declaration order, and state IDs follow block order (each block's states are
contiguous).

Saved worlds store names (palettes hold block-state compounds), so numeric
IDs never reach disk. They still must match between Java and Rust while both
run, and between a MattMC client and server. Keeping Java's order after Java
is gone also keeps the network protocol and any recorded reference data valid.

- Rust registration is driven by an **explicit ordered list** that matches
  Java's order exactly. A startup parity test compares every name, ID and
  state against Java's registries while both run.
- New content is **appended**, so existing IDs never move.
- All content, including the integrated Alex's Caves, Alex's Mobs and TaCZ
  content, uses the `minecraft` namespace, so existing worlds keep loading.

## One source of truth

Every per-state fact a subsystem needs becomes a column of the state table
(see [block states](BLOCK-STATES.md)) or a column owned by that subsystem but
built from the registry. Examples of the latter are render model selectors
and shader-pack material IDs. Subsystems stop receiving their own copies from
Java. During migration the registry itself is first filled from one Java
export, then built natively (see the [migration plan](MIGRATION-PLAN.md)).

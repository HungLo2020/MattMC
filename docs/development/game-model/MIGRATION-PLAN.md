# Migration plan (proposal)

> Proposal; not implemented. See the [game model index](index.md).

Each phase is a normal migration slice: parity tests against Java, a
production-path benchmark, and docs.

## Phase 1: one registry snapshot from Java

- Build `content::Registries` in Rust from **one Java export** at startup:
  - blocks, properties and every state's columns
  - interned shapes and faces
  - items with default components
  - entity types, tags and biomes per server
- Java walks its registries once and sends a compact description.
- **Replace the per-slice tables** with views of this registry, one slice at a
  time:
  - lighting types and faces
  - skylight descriptors
  - heightmap masks
  - noise-fill flags
  - the carvers' `BLOCK_OF`
  - chunk-section labels
  - palette labels
  - rendering's meshing-state columns

  Each Java `Native*` bridge loses its private table builder.
- Parity: a test compares every column against Java's live `BlockState`
  answers.

This is the highest-value first step. It removes the duplication found in the
inventory, gives every later slice typed IDs, and needs no gameplay changes.

## Phase 2: Rust defines the registries

- Rust builds the registries from its own definitions (the builders in
  [adding content](ADDING-CONTENT.md)) in Java's order.
- Java *verifies* against Rust at startup, then later reads IDs from it.
- Per-state functions (light levels, map colors, predicates) are ported block
  by block, with the parity test as the guard.

## Phase 3: behavior and components by family

- Gameplay slices move block behaviors family by family. Likely order: random
  ticks of plants and crops, fluids, falling blocks, then redstone.
- Block entities move with the component store, starting with data-only ones
  (signs, banners), then containers and hoppers (shared with minecarts), then
  ticking machines.
- Entities move last and in layers: physics and collision first, then AI.

## End: Java removed

Once Rust owns definitions, behavior, components and entities, Java's
`Blocks`, `Items`, `BlockState` and block classes are deleted, along with the
`Native*` bridges and the Java side of the parity tests. Parity is then held
by recorded Java reference outputs (saved chunks, tick traces) instead of a
live Java comparison.

## Later: data fixers

Old worlds must keep loading, so Java's data fixers (DataFixerUpper and the
schemas in `net.minecraft.util.datafix`) are eventually ported too. They only
run on load and upgrade, so they come after the live game model.

## Coordination with rendering

Rendering already keys its meshing-state table by state ID and has its own
tint, model-selector and shader-pack material tables. In this plan:
- **Rendering keeps ownership** of render-specific columns: model selectors,
  render types and pack material IDs.
- **Those columns are keyed by the shared `StateId`**, built from
  `content::Registries` instead of raw integers and text snapshots.
- The rendering agent is working on its own tables now. Once this plan is
  final, the two are reconciled before Phase 1 touches render tables.

## Decided

See the [decisions table](index.md#decisions-2026-10-07). For this plan:
- **Component store:** in-house sparse sets with generational IDs. No
  off-the-shelf ECS, and no new crates without approval.
- **Namespace:** all content, including the integrated mods, stays in
  `minecraft:`.
- **Definitions:** content that Java defines in code becomes Rust builders.
  Content in data files stays in the same data files.
- **Client/server:** separate client and server worlds sharing one set of
  definitions, as in vanilla. Client-only components (animation state) exist
  only in the client world.

- **`StateId` width:** 16 bits, with a startup check against the 65,536
  ceiling. See [why 16 bits](REGISTRIES-AND-IDS.md#why-stateid-is-16-bits).

## Open decisions

None right now.

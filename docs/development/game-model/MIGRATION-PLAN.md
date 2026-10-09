# Migration plan (proposal)

> Phase 1 is implemented ([Rust block registry](RUST-BLOCK-REGISTRY.md));
> Phase 2 now has native state graphs, properties, fluids, registered block
> definitions, physical settings and intrinsic state rules. Remaining producers,
> behavior and components are unfinished. Source ownership does not certify
> all acceptance work.
> See the [verification scope](BLOCK-REGISTRY-VERIFICATION.md) and
> [game model index](index.md).

Each phase is a normal migration slice: parity tests against Java, a
production-path benchmark, and docs.

## Phase 1: one block registry from Java (implemented)

The original Phase 1 milestone established the shared registry:

- Rust's [`content::block`](RUST-BLOCK-REGISTRY.md) was built from **one Java
  export**, initialized lazily from frozen Java registries:
  - block/property definitions and every state's columns
  - interned light-occlusion face IDs and Java's truth table

Current construction combines native declarations with a smaller format-8
export of remaining Java state facts; see Phase 2 below.

- These per-slice tables were replaced by views of it, and each Java bridge
  lost its private builder:
  - lighting types and faces
  - skylight descriptors
  - heightmap masks
  - noise-fill flags
  - the carvers' `BLOCK_OF`
  - chunk-section labels and the save vocabulary, which Rust now generates
  - palette-packing labels
- Parity: `NativeBlockRegistryTest` checks every column, property value and
  `setValue` result against Java's live `BlockState`. Each consumer keeps its
  own parity tests. Evidence is in
  [block registry verification](BLOCK-REGISTRY-VERIFICATION.md).

- Rendering's terrain meshing states take their block facts from the
  registry. Rendering keeps its own columns: models, materials, passes,
  shader-pack IDs, tint and sprites.

Not done in Phase 1, by design:
- The shared registry does not yet own items with default components, entity
  types, tags or biomes. Existing native systems can still consume separate
  Java-supplied data, such as [biome-search inputs](../world/biome/RUST-BIOME-SEARCH.md).
  General shared registries remain future work; this phase covers block facts.

## Phase 2: Rust defines the registries

The [native state graph constructor](STATE-GRAPHS.md) now supplies Cartesian
state values and transition IDs for block/fluid definitions and shared
block-registry slot arithmetic. [Fluid definitions](FLUID-DEFINITIONS.md) now
come from Rust, including all five registry entries and their 37 intrinsic
state rows. [Block state definitions](BLOCK-DEFINITIONS.md) now supply the
ordered catalog, property sets and defaults. [Physical settings](BLOCK-PHYSICS.md)
and [intrinsic state rules](BLOCK-INTRINSICS.md) now also originate in Rust.
The latter own map colors, emission and fluid associations.
[Sound definitions](SOUND-DEFINITIONS.md) now own all sound events, profiles and
instruments; [block settings](BLOCK-SOUND-AND-OFFSETS.md) select them and own
model offsets. [Block-family configuration](BLOCK-FAMILY-TYPES.md) now supplies
shared block-set/wood definitions and registered family parameters. Java still
supplies factories, shapes/predicates, blocked light,
codecs, state-object views and world-dependent gameplay. The
[134 property declarations](PROPERTY-DEFINITIONS.md) also originate
in Rust; block registries share their schemas and fluids use their typed
domains. This includes all 11 additional properties for integrated content.
The remaining registry-definition migration below is unfinished.

- Registered blocks now read native names, domains/defaults, physical
  settings and intrinsic state traits. Java retains factories,
  `Registry.register`, state objects, codecs and cache initialization;
  startup checks keep native and Java IDs aligned.
- Broader content registries and the builders proposed in
  [adding content](ADDING-CONTENT.md) remain future work.
- Remaining shape, blocked-light and contextual predicate producers still
  need migration with independent parity checks. Format 7 imports face IDs/truth
  tables, blocked light, offsets and the flags not directly derived from native
  physical/fluid definitions. Map-color identities, emitted light and canonical
  fluid-state associations now originate in native rules.

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
- **The proposed destination is shared typed IDs for all content.** Current
  terrain meshing passes integer state IDs across FFM and validates them
  against `content::block::BlockRegistry`; the generic `content::Registries`
  design is not implemented.
- Done for terrain meshing states: their block facts come from the registry,
  and rendering sends only its own columns (see the
  [registry's consumers](RUST-BLOCK-REGISTRY.md#consumers)). The shader-pack
  state-name snapshot is still separate.

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

- **`StateId` width:** 16 bits; current installation allows 65,535 states,
  reserving `0xffff` as a sentinel. See [why 16 bits](REGISTRIES-AND-IDS.md#why-stateid-is-16-bits).

## Open decisions

None right now.

# Migration plan (proposal)

> Phase 1 is implemented ([Rust block registry](RUST-BLOCK-REGISTRY.md));
> Phase 2 now has native state graphs, properties, fluids, registered block
> definitions, physical settings, intrinsic state rules, sound/offset definitions,
> block-family configuration, map palette/shading and state policy.
> Remaining producers, behavior and components
> are unfinished. Source ownership does not certify
> all acceptance work.
> See the [verification scope](BLOCK-REGISTRY-VERIFICATION.md) and
> [game model index](index.md).

Each phase is a normal migration slice: parity tests against Java, a
production-path benchmark, and docs.

## Current priority (2026-10-09)

The linked guides distinguish current source ownership from author-recorded
acceptance checkpoints. Their historical suite/image/performance results do not
certify every later source revision.

After the local map/state-policy batch, prioritize world-state systems and the
per-frame Java → Rust path ahead of more static catalog migration. Profile
producer work, allocations and transfer costs, then move data storage, producer
logic and lifetime management together. Avoid retaining Java object construction
just to repack data that Rust immediately reconstructs. Existing native chunk,
terrain and DH owners provide starting points; their remaining Java consumers
and orchestration are still unfinished. Preserve world/save behavior and bounded
generation/reload handling, and verify realistic workloads against Frozen.

The first local implementation is the [DH frame transaction](../rendering/RETAINED-SCENE.md#native-dh-visibility-frame-ownership),
which keeps Rust-selected segments in native immutable frame storage. All seven lifecycle cases and the reviewed vanilla/Iris+DH image pairs pass
locally. Four-mode comparisons completed with sixteen clean runs but still fail vanilla/DH
performance floors. The handoff is implemented. Final release image proof and paired diagnostic
profiles pass; the targeted Java allocation/encoding cost is reduced, with no
accepted isolated throughput gain. See the retained-scene evidence for bounds.
[Loaded-section snapshot ownership](../world/chunk/RUST-SECTION-SNAPSHOTS.md)
and bulk rebuild consumers now live in Rust. Lifecycle and paired image checks
pass; performance floors remain unmet. [Live block sections](../world/chunk/RUST-LIVE-SECTIONS.md)
now own canonical packed storage and palette mutation in Rust with CPU read views,
plus native rebuild/light exports and heightmap/skylight handoffs. Enumeration,
save/network and custom generation compatibility exports remain temporary Java
projections. Canonical [stage transfers](../world/levelgen/RUST-STAGE-HANDOFF.md)
now capture/adopt sections within Rust. Move remaining consumers and bulk
producers next; verify performance. The per-frame slice now moves
[DH cloud preparation](../rendering/RUST-DH-CLOUDS.md) and its direct native
consumer together; seven lifecycle cases and settled coast pairs pass, while
whole-renderer performance acceptance remains open.
[Live section mutation/counters](../world/chunk/RUST-SECTION-COUNTERS.md) now have full suite/lifecycle/coast verification; vanilla/DH performance floors
still fail. Move
remaining per-frame world-state extraction next rather than more static catalogs.
The local [item-layer preparation](../rendering/RUST-ITEM-LAYERS.md) slice now
owns authored poses in Rust and feeds block/flat GUI consumers directly;
full CPU/lifecycle checks and reviewed coast pairs pass; vanilla still misses
Frozen performance floors. The measurements precede incoming Java rendering
fixes; combined checks are recorded separately. The local follow-up now composes ordinary world and hand poses in Rust, with
copied parents and pinned CPU owners. Full CPU suites, lifecycle checks and
reviewed coast pairs pass; vanilla p99 still misses Frozen and repeat variance
limits throughput conclusions. See the item-layer guide for evidence scope.
Local [live light storage](../world/lighting/RUST-LIVE-LAYERS.md) now owns
generations, propagation/sky handoffs and independent client packet imports;
full CPU/lifecycle checks and reviewed Frozen image pairs pass. Vanilla FPS/p99
and DH p99 still miss performance floors in that preceding storage release.
Its paired streaming profiles pass their identity/movement checks and sample
about 42 MB of Java terrain-light allocations in eight seconds. The later bulk
consumer profile records no sampled `computeLightWord` allocations, but higher
total Current allocation than that earlier sample. These separate diagnostic
windows do not establish an isolated speedup.
The [bulk terrain-light consumer](../rendering/RUST-TERRAIN-LIGHTING.md) now
reads retained native layers directly. Next move retained scene mesh payloads
and entity preparation. Cached item mesh generation restamps still clone Java
index payloads each frame; migrate that storage and its direct consumer together. These are ownership
migrations; additional static definitions are not the main performance batch.

The pre-integration eight-second allocation profile attributes about 47 MiB to
sky/background preparation, 30 MiB to model submission and 19 MiB to block-entity
scopes, versus about 1 MiB to item generation restamps. These are weighted
diagnostic samples, not isolated speedups. Prioritize live world/biome ownership
and direct background/model consumers alongside retained geometry. Preserve
missing-chunk behavior, height clamping, mutable biome containers, world unload
and resource reload; retaining a stale cache is not an ownership migration.

The current coupled world-state batch has wired
[bulk terrain lighting](../rendering/RUST-TERRAIN-LIGHTING.md): Rust reads
retained light generations and immutable registry facts to prepare 5,832
mesher words. Java still supplies contextual predicates/shade, model admission
and rebuild orchestration. Mutable arrays, custom layers/states/platforms and
appearance diagnostics retain scalar compatibility preparation. Preserve lazy
defaults, missing light types, callback ordering and lease lifetime while
measuring ordinary movement and rebuilds. The committed author record reports
native oracle/halo checks, combined Rust and Java suites, all seven lifecycle
cases and reviewed settled compatibility image pairs passing. The settled
images use scalar diagnostic lighting and do not establish bulk-path pixels.
All sixteen benchmark runs are clean, but
vanilla, shaders and DH still miss the p99 floor. Separate visible-minimap ordinary
observations complete; entry and travel p99 still trail Frozen. Movement reaches
a terrain barrier after about 16.45 blocks, so sustained streaming remains
unverified. Follow this
with retained state updates and direct render consumers, reducing full-frame
Java construction rather than adding more static catalogs.

For world storage, distinguish [native world-generation stage storage](../world/levelgen/RUST-SURFACE-STORAGE.md#shared-chunk-storage)
from authoritative loaded-world ownership: Java still orchestrates stage
installation and chunks, while ordinary canonical live palette mutation now uses
its separate Rust owner. Move bulk producers with storage to reduce write-side
boundary costs.

A concrete remaining producer is terrain rebuild preparation:
[`ClonedChunkSection`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/sodium/client/world/cloned/ClonedChunkSection.java)
retains native captures, `LevelSlice` reads their CPU views, and
[`NativeSectionSnapshot`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/sodium/client/render/chunk/compile/tasks/NativeSectionSnapshot.java)
receives the canonical 18³ state-ID halo in one Rust bulk call. Rust prepares
light words for admitted native slices; Java still admits states to model
metadata and supplies contextual light predicates/shade and biome/custom tint
samples. Compatibility inputs retain scalar light preparation, and unsupported
state containers retain the Java state-grid path.
Historical ordinary-flight profiling identified repeated tint sampling as a
substantial part of Java snapshot preparation (159/383 sampled snapshot CPU frames). The
local [shared section color fields](../world/biome/RUST-SECTION-COLORS.md) slice
moves coordinate planning, overlap deduplication, storage and direct mesher
consumption into Rust. Full suites, seven lifecycle cases and reviewed image
pairs pass; vanilla still misses whole-renderer performance floors. Repeated
ordinary-flight profiles show lower sampled tint/snapshot preparation cost;
that earlier color slice did not migrate authoritative loaded-world storage or
establish an isolated FPS gain. The later live-section owner now handles
canonical storage/mutation. Continue moving remaining bulk producers and
consumers while preserving snapshot timing, contextual rules and reload handling.

## Phase 1: one block registry from Java (implemented)

The original Phase 1 milestone established the shared registry:

- Rust's [`content::block`](RUST-BLOCK-REGISTRY.md) was built from **one Java
  export**, initialized lazily from frozen Java registries:
  - block/property definitions and every state's columns
  - interned light-occlusion face IDs and Java's truth table

Current construction combines native declarations with a smaller format-9
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
shared block-set/wood definitions and registered family parameters. [State policy](STATE-POLICY.md) now owns tick/light-shape eligibility and
leaf/entity markers. Java still
supplies factories, shapes/contextual predicates, blocked light,
codecs, state-object views and world-dependent gameplay. The
[134 property declarations](PROPERTY-DEFINITIONS.md) also originate
in Rust; block registries share their schemas and fluids use their typed
domains. This includes all 11 additional properties for integrated content.
The remaining registry-definition migration below is unfinished.

- Registered blocks now read native names, domains/defaults, physical
  settings, intrinsic state traits, sound/instrument/offset settings and typed
  family parameters. Java retains factories,
  `Registry.register`, state objects, codecs and cache initialization;
  startup checks keep native and Java IDs aligned.
- Broader content registries and the builders proposed in
  [adding content](ADDING-CONTENT.md) remain future work.
- Remaining shape, blocked-light and contextual predicate producers still
  need migration with independent parity checks. Format 9 imports face IDs/truth
  tables, blocked light and motion/solid/custom flags. Physical, fluid and
  state-policy flags and model offsets originate in Rust. Map-color identities,
  emitted light and canonical fluid-state associations originate in native rules.
  Family configuration and state policy do not migrate world callbacks, ticking
  or entity queries; sound definitions do not migrate Java sound policy or resource caches.

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

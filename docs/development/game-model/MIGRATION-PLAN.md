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

<a id="current-priority-2026-10-09"></a>

## Current priority (2026-10-10)

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

Use the [Goal 5 rendering checkpoint](../rendering/GOAL-5-STATUS.md) for
current rendering ownership and evidence limits, and the
[live-biome guide](../world/biome/RUST-LIVE-BIOMES.md) for native sky/fog sampling
and generation-validated color reuse. Its author-recorded comparisons have
different release identities; the
[earlier verification](../world/biome/RUST-LIVE-BIOMES.md#october-9-verification)
also preserves the sky-only and range-fix checkpoints. Performance, ordinary
gameplay, temporal parity and long-session memory acceptance remain open.
The later terrain face-policy/shared-cache, DH height-field and DH lighting
checkpoints have distinct release identities and workload results; follow their
linked guides rather than carrying an earlier pass or failure forward. This
documentation review inspects source and adds no runtime verification.

Follow the owner boundary relevant to the next change:

- **Server scheduling:** [chunk-task queues](../world/chunk-loading/RUST-CHUNK-TASKS.md)
  own priority buckets, ordered chunk membership, task lists and reprioritization
  in Rust. Java temporarily pins callables and retains consecutive dispatch,
  execution throttling, completion futures and level-change callbacks. Remaining
  holders, simulation and task bodies still need migration.
- **World storage and bulk producers:** [live block sections](../world/chunk/RUST-LIVE-SECTIONS.md),
  [section counters](../world/chunk/RUST-SECTION-COUNTERS.md) and
  [generation-stage handoffs](../world/levelgen/RUST-STAGE-HANDOFF.md).
  Live packed storage and generation-stage storage are separate native owners.
  [Retained chunk inputs](../world/chunk/RUST-DH-CHUNK-INPUTS.md) bind mutable
  block/counter owners and an independently admitted shared biome collection;
  consumers observe growth without keeping a stale generation snapshot. Java
  still validates compatibility identities and container replacement.
  Java chunk orchestration, compatibility serialization and remaining bulk
  producers still need migration.
- **Client residency:** [native window bookkeeping](../world/chunk-loading/RUST-CLIENT-RESIDENCY.md)
  owns logical slots, counts, center, position fingerprints and empty membership.
  Ordinary empty scans read retained counters and stop before each Java callback.
  Java keeps atomic chunk references, ring/range reads, radius relocation and
  callback delivery; public empty-set escape transfers membership to Java.
- **Rebuild preparation:** [immutable section snapshots](../world/chunk/RUST-SECTION-SNAPSHOTS.md),
  [section color fields](../world/biome/RUST-SECTION-COLORS.md),
  [live light layers](../world/lighting/RUST-LIVE-LAYERS.md) and the
  [bulk terrain-light consumer](../rendering/RUST-TERRAIN-LIGHTING.md).
  Java still supplies model admission, contextual light predicates/shade and
  biome/custom tint samples; unsupported and diagnostic inputs retain
  compatibility paths. [Canonical face policy](../rendering/RUST-TERRAIN-CULLING.md)
  now consumes retained world IDs directly in Rust. Java exports intrinsic face
  geometry once and keeps contextual/tag/hook callbacks. The shared model,
  selector and state cache permits concurrent builder reads while registration
  and reload remain exclusive.
- **Light publication and scalar reads:** [canonical light maps](../world/lighting/RUST-LIGHT-MAPS.md)
  retain section entries, typed layer owners and sky column metadata in Rust.
  Snapshots share 64-shard roots, and exact canonical block/sky scalar consumers
  sample those owners directly. Java retains light-engine orchestration,
  queued-section precedence, publication sequencing and temporary object-identity
  pins. Preserve supplied-map aliases, custom/escaped-layer callbacks and rebuild
  timing: retain the original layer references, then lease their generations when
  the terrain-light consumer runs. This source ownership does not extend the
  linked guide's recorded verification to later revisions.
- **DH world preparation:** the current [retained-input](../world/chunk/RUST-DH-CHUNK-INPUTS.md),
  [full-data](../world/chunk/RUST-DH-FULL-DATA.md),
  [same-detail update](../world/chunk/RUST-DH-SOURCE-UPDATES.md),
  [material-map](../world/chunk/RUST-DH-MATERIAL-MAPS.md),
  [change-hash](../world/chunk/RUST-DH-CHANGE-HASH.md) and
  [render-reduction](../rendering/RUST-DH-RENDER-COLUMNS.md) owners supersede
  earlier Java column, merge, identity-map and hash loops. Java retains admission,
  compatibility wrappers, orchestration/public metadata, persistence, contextual
  colors/material resolution, faces, greedy mesh assembly and final packets.
  The following release narrative records successive migration and verification
  checkpoints, rather than one current acceptance result: [height fields](../world/chunk/RUST-DH-HEIGHTMAPS.md)
  scan live blocks/counters and publish immutable CPU leases. The
  [lighting and emitter owners](../world/lighting/RUST-DH-LIGHTING.md) perform
  admitted whole passes and serve direct chunk light reads. Java retains strict
  admission, wrapper publication, flags, public mutable projections and fallback
  behavior. The in-progress [column producer](../world/chunk/RUST-DH-COLUMNS.md)
  now owns admitted voxel scans, material IDs, immutable points and source hashes;
  `LongList` consumers read CPU views without a point-array projection. Java
  retains mutable recipient merges, orchestration and contextual hooks. Final CPU
  suites, seven lifecycle cases and both reviewed settled pairs pass. Actual
  travel producer admission and paired allocation checks pass. All20 rotating
  timings are clean, but vanilla p99 and DH median FPS miss Frozen; both shader
  modes pass. The unpaired CPU profile prioritizes work rather than proving an
  isolated throughput gain. These checks belong to release839ff128. The later
  local [DH full-data migration](../world/chunk/RUST-DH-FULL-DATA.md)
  keeps recipient vectors, bulk point operations, complete occlusion and parent
  reduction in Rust. Whole-quadrant production now supplies retained native
  results directly to the ordinary parent update. Release6352edf8 passes218
  Rust world tests and1,860 Java checks (two skipped), including13 focused JNI
  controls; six workers map the exact library and final source guards pass. Earlier full Rust software graphics checks
  fail, with an isolated first pixel mismatch and poisoned-lock failures retained.
  The newer [contributor transport](../world/chunk/RUST-DH-CONTRIBUTORS.md) captures,
  clips and remaps immutable footprints in Rust. The actual transformer reads
  retained views once and the render source shares them. Releaseba39b190 passes
  219 Rust world tests and1,866 Java tests (two skipped), with six exact mapped
  workers and final source/library/Frozen/protected-prompt guards passing. The
  latest local quadrant source also moves uniformity and ordered footprint
  admission/capture into the same Rust producer. Releaseac41d404 passes221 Rust
  world tests and1,870 Java tests (two skipped); six full-task workers map the
  exact image and all source/library/Frozen/prompt guards pass. These CPU checks
  do not accept runtime or performance. The subsequent producer determines signed
  generation/compression metadata and remapped list hashes in the same call.
  Detached readers release their old native producer lease while other readers
  remain valid. Release0cd4b37f passes228 Rust world tests and1,874 Java tests
  (two skipped), including32,768 actual Frozen metadata pairs; six full-task
  workers map that image and source/native/Frozen/prompt guards pass. Gameplay,
  performance and sustained memory remain unverified. Update orchestration,
  material-map mutation, metadata storage and
  transformation policy remain Java, and hardware
  acceptance awaits driver restoration.
  Native live generations now also own saturating mutation revisions for bounded
  [height-field reuse](../world/chunk/RUST-DH-HEIGHTMAPS.md#local-bounded-field-reuse-in-progress).
  Weak keys keep only field values alive; actual ordinary constructor reuse and
  mutation/retained-reader controls pass. Releaseb1611469 passes227 Rust world
  tests and1,871 Java tests (two skipped), six mapped full-task workers and all
  source/native/Frozen/prompt guards. Runtime/performance remain unverified.
  The subsequent source also owns the complete Frozen-ordered64×64 occlusion
  pass over existing vectors/views in one native call, with direct extent/hash
  publication. Release9371c5d7 passes230 Rust world tests and1,875 Java tests
  (two skipped),27 DH controls, six exact mapped workers and all source/native/
  Frozen/prompt guards. All32 saved Frozen cases match both ordered passes;
  ordinary recipient, alias/custom callback and bad-ID controls pass. Metadata
  storage, orchestration and contextual policy remain Java. The latest
  [same-detail merge](../world/chunk/RUST-DH-SOURCE-UPDATES.md) moves eligibility,
  point remapping/hashes/change flags and ordered footprint policy to Rust,
  publishing directly into existing recipient capacity. Committed batches
  release borrowed source leases; unsupported inputs retain exception prefixes
  and callbacks. Releaseefeca492 passes234 Rust world checks and1,880 Java tests
  (two skipped),32 DH controls, six exact mapped workers and all source/native/
  Frozen/prompt guards. Actual Frozen supplies65,536 generation pairs and32
  saved sources with two ordered updates each. These CPU checks do not establish
  gameplay/performance or sustained memory acceptance.
  The next local [material-map migration](../world/chunk/RUST-DH-MATERIAL-MAPS.md)
  moves ordered identity/index storage, complete remapping and wrapped hash policy
  into Rust. Java retains compatibility bindings without a mirrored identity index;
  read-only producers keep original readers. Three native JNI controls and237 Rust
  world checks pass, including the actual Frozen300-operation corpus/imported
  self-merge exception prefixes. Full Java reports1,883 tests (two skipped), zero
  failures/errors;35 column/material controls, six exact mapped workers and all
  source/native/Frozen/prompt guards pass. Hardware/performance and sustained
  memory acceptance remain open; source orchestration, metadata and contextual
  policy still require migration.
  The subsequent local source retains registered state IDs in those identities;
  native intrinsic policy consumes them directly for occlusion and parent reduction,
  avoiding another Java array/wrapper scan. Independent policy leases survive map
  clear/replacement. All20 focused Rust/238 world checks and four JNI controls pass
  on release21991771. Full Java1,884 tests (two skipped),36 column/material controls,
  six exact full-task workers and all source/native/Frozen/prompt guards pass.
  Hardware/performance and sustained memory acceptance remain pending.
  Preserve cached-source timing, mutable-storage detachment and section-local
  exclusion rather than assuming a transactional neighborhood. The
  [stale-counter lighting regression](../world/lighting/RUST-DH-LIGHTING.md#source-review-boundary-2026-10-10)
  compares actual Frozen output and work counts over nine passes; focused Rust
  and JNI checks pass after preserving counter semantics. It is not a runtime
  gameplay acceptance result.
- **Live biomes and colors:** the [admitted palette owner and direct raw sky/fog consumers](../world/biome/RUST-LIVE-BIOMES.md)
  are native. The loaded-world index and DH columns share the same immutable
  primary-owner binding; unchanged publication reuses it after identity checks.
  Rust reuses exact-position colors only after validating the world revision
  and retained source generations. Java retains chunk lifecycle events,
  compatibility sampling, terrain tint/blending and later brightness/weather
  adjustments; hooks still run on every call. Preserve custom-hook/source
  dispatch and the guide's missing-chunk, mutable-container, view-range and
  world-lifetime boundaries.
- **Per-frame scene and entity work:** [DH visibility frames](../rendering/RETAINED-SCENE.md#native-dh-visibility-frame-ownership),
  [built-in DH clouds](../rendering/RUST-DH-CLOUDS.md) and
  [item-layer poses](../rendering/RUST-ITEM-LAYERS.md) have native owners for admitted paths.
  Native [mesh-resource preparation](../rendering/RENDER-ARCHITECTURE.md) now
  reuses resident geometry across section/material keys for the same mesh
  generation and vertex ABI, and creates per-mesh resource sets only when a draw needs them outside shared-page terrain binding.
  The ABI 79 [native terrain selection handoff](../rendering/JAVA-BRIDGE.md#native-terrain-selection-frames)
  retains immutable scene arrays and passes CPU owner/frame identities and the
  exact camera to decoding. The decoder acquires ownership before queueing;
  legacy/diagnostic record encoding remains. Its reported CPU/JNI checkpoint
  does not verify runtime or performance of the combined source.
  Remaining retained mesh payloads, entity preparation, Java animation and
  contextual inputs still need work with their direct consumers.

The cumulative checkpoints below preserve their original wording and individual
source/workload scopes, including the live-biome checkpoint. Words such as
“current” and “next” inside that narrative refer to the recorded slice, not a
new acceptance result or a replacement for the priority above. Follow the
current evidence routes above when comparing releases.

<details markdown="1">
<summary>Cumulative recorded checkpoints (original slice scopes)</summary>

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
and direct background/model consumers alongside retained geometry. The local
[live biome owner and sky consumer](../world/biome/RUST-LIVE-BIOMES.md) is now
wired; full Java/Rust suites, seven lifecycle cases and reviewed settled
Frozen pairs pass. The preceding comparison still misses vanilla p99;
ordinary streaming and broad parity remain open. Preserve
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

</details>

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
  The admitted [collision catalog](../world/chunk/RUST-DH-HEIGHTMAPS.md) and
  [terrain face policy](../rendering/RUST-TERRAIN-CULLING.md) consume cached
  intrinsic geometry with native block facts. Java still produces that geometry
  and retains contextual/custom shape and callback behavior.

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

Current local [DH chunk-input batch](../world/chunk/RUST-DH-CHUNK-INPUTS.md) moves
retained mutable section bindings and all four direct consumers into Rust.
[Client residency](../world/chunk-loading/RUST-CLIENT-RESIDENCY.md) owns window
bookkeeping and scans retained counters without Java boolean packing. Java still
validates compatibility identities, binds objects and orchestrates chunk/callback
transitions. Actual Frozen operation replay covers callback and exception prefixes;
hardware/FPS and long-memory acceptance remain pending. Broader world simulation
and startup/executable migration remain required.

The local [biome publication extension](../world/biome/RUST-LIVE-BIOMES.md) now
shares one retained primary-owner collection with sky/fog and DH columns. It
removes repeated Java pointer-array packing and native owner-row reconstruction
while retaining generation checks and independent consumer admission. The new
CPU gates pass; the remaining runtime, performance and memory gates are unchanged.

The current [DH change-hash migration](../world/chunk/RUST-DH-CHANGE-HASH.md)
owns the full sampled/surface/emitter algorithm in Rust and directly consumes
these retained inputs. Actual Frozen replay covers96 cached-field/edit cases;
the31 focused Java checks and full1,909 reported Java tests pass (two skips,
zero failures/errors), alongside253 Rust world checks. Hardware/FPS and
long-memory verification remain pending. Java retains compatibility
admission and callbacks, not per-voxel packing or the hash loops.

The subsequent [DH render-column reduction](../rendering/RUST-DH-RENDER-COLUMNS.md)
owns the whole compact reduction policy in Rust and borrows existing canonical
CPU storage. It removes Java reducer graph/sort construction; contextual color
callbacks, face construction and final packets remain subsequent migration work.
Actual Frozen3,616-case synthetic CPU replay is supplemental. Releaseee1676ec
passes four focused Rust checks,27 architecture checks, five focused Java controls
and full1,914 reported Java tests (two skips, zero failures/errors). Hardware,
realistic performance and long-memory verification remain pending.

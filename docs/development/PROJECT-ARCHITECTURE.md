# Project Architecture

This document describes the Rust source layout for MattMC. It intentionally does not mirror the Java package tree. Rust code is organized by subsystem ownership so the tree can grow toward the long-term native engine architecture.

The completed runtime target is **one Rust executable** supporting client and dedicated-server modes, **at most one separately loaded Rust library**, and **no Java**. Current Java bridges, configuration and CPU semantic producers describe the incremental migration, not completion of that target. See the [Goal 5 rendering checkpoint](rendering/GOAL-5-STATUS.md) for the current rendering boundary and evidence limits.

The active [Rust migration milestones](RUST-MIGRATION.md) target one executable
with at most one separately loaded Rust library and specify ownership and
acceptance for the whole project.

## Rust Source Layout

```text
src/main/rust/
├── Cargo.toml
├── lib.rs
├── app/
├── assets/
│   ├── models/
│   ├── packs/
│   ├── resources/
│   └── textures/
├── audio/
├── compat/
├── content/
│   ├── block/
│   │   ├── collision/
│   │   ├── definitions/
│   │   └── family/
│   ├── fluid/
│   ├── map_color/
│   ├── property/
│   ├── registry/
│   ├── sound/
│   ├── state/
│   └── tags/
├── core/
├── gameplay/
│   └── tacz/
├── network/
│   └── codec/
│       └── long_array/
├── platform/
├── render/
│   ├── chunk/
│   │   ├── meshing/
│   │   ├── direct_trigger.rs
│   │   ├── gfni_trigger.rs
│   │   ├── index.rs
│   │   ├── occlusion.rs
│   │   ├── render_data.rs
│   │   ├── render_list.rs
│   │   ├── terrain_frames.rs
│   │   └── translucent.rs
│   ├── dh_columns/
│   ├── dh_collector/
│   ├── scene/
│   ├── shared/
│   ├── shaderpack/
│   ├── worldrender/
│   ├── guirender/
│   ├── bridge/
│   └── vulkanic/
│       └── backends/
│           ├── opengl/
│           └── vulkan/
├── storage/
│   ├── chunk/
│   ├── nbt/
│   ├── poi/
│   └── region/
├── tools/
└── world/
    ├── phys/
    │   └── shapes/
    │       ├── boolean_join/
    │       ├── box_extract/
    │       ├── rotation/
    │       ├── closest_point/
    │       └── raycast/
    └── level/
        ├── biome/
        │   ├── climate/
        │   ├── color_fields/
        │   ├── live/
        │   └── search/
        ├── chunk/
        │   ├── biomes/
        │   ├── counters/
        │   ├── dh_columns/
        │   ├── dh_hash/
        │   ├── dh_heightmaps/
        │   ├── live/
        │   ├── palette/
        │   ├── snapshot/
        │   ├── inputs.rs
        │   └── stage_transfer.rs
        ├── lighting/
        │   ├── dh/
        │   ├── layers/
        │   ├── maps/
        │   ├── priority_queue/
        │   ├── propagation/
        │   └── skylight_sources/
        ├── chunk_distance/
        ├── client_residency/
        ├── chunk_tasks/
        ├── color_map_color_util.rs
        └── levelgen/
            ├── synth/
            ├── density/
            ├── router/
            ├── noise_fill/
            ├── biome_fill/
            ├── aquifer/
            ├── surface/
            ├── proto_chunk/
            ├── carver/
            ├── feature/
            ├── blending/
            ├── heightmap/
            ├── math.rs
            └── random.rs
```

## Directory Responsibilities

### `app/`

Application-level orchestration belongs here. This is the intended home for future startup, lifecycle, configuration, and high-level runtime coordination that does not belong to a lower-level engine subsystem.

### `core/`

Shared engine primitives belong here. Use this for low-level types, algorithms, memory utilities, math, identifiers, and cross-subsystem foundations that are not specifically rendering, world, platform, or gameplay code. `core/math.rs` supplies exact coordinate seeding shared by content, world generation and terrain meshing. `core/json.rs` parses JSON with Gson's semantics (key order, duplicate keys, number text) and `core/dfu.rs` decodes it with DataFixerUpper `JsonOps` rules; `core/parallel.rs` runs scoped worker maps whose Java log lines the calling thread emits.

### `world/`

World simulation and world data helpers belong here. Current code includes
`world/level/color_map_color_util.rs` for Java color-map behavior,
`world/level/biome/` for biome corner selection and [climate lookup](world/biome/RUST-CLIMATE.md) (with the
[biome fill](world/biome/RUST-BIOME-FILL.md) in `world/level/levelgen/biome_fill/`),
[eligible biome searches](world/biome/RUST-BIOME-SEARCH.md) in
`world/level/biome/search/` for compiled climate sampling and search traversal
while Java retains acceptance preparation and horizontal random selection,
[skylight-source reconstruction](world/lighting/RUST-SKYLIGHT-SOURCES.md) and
[block/sky light propagation and sky seeding](world/lighting/RUST-LIGHT-PROPAGATION.md)
under `world/level/lighting/`, [block-section save packing](world/chunk/RUST-PALETTE-PACKING.md)
under `world/level/chunk/palette/`, and `world/level/levelgen/` for
noise synthesis, density evaluation, aquifers, surface evaluation with eligible
native conditions and cached rule programs over
[Rust-owned chunk storage](world/levelgen/RUST-SURFACE-STORAGE.md), the
[carvers stage](world/levelgen/carver/RUST-CARVERS.md), worldgen
randomness, the [NOISE fill](world/levelgen/RUST-NOISE-FILL.md) that owns
base-terrain block writes, eligible cell traversal and native-route aquifer
materials, and the
[noise router](world/levelgen/RUST-NOISE-ROUTER.md) that fills interpolation
slices and evaluates shared point programs for
[preliminary surface levels](world/levelgen/RUST-PRELIMINARY-SURFACE.md) and
[aquifer fluid sources](world/levelgen/RUST-AQUIFER.md#native-fluid-sources).
[Per-seed chunk-noise templates](world/levelgen/RUST-CHUNK-NOISE.md) let eligible
fills instantiate their programs without per-chunk Java graph wrapping. See
[Rust World-Generation Organization](world/levelgen/RUST-WORLDGEN-ORGANIZATION.md) for module
ownership, native boundaries, and recorded verification.

Canonical [live block sections](world/chunk/RUST-LIVE-SECTIONS.md) in
`world/level/chunk/live/` own packed storage and palette mutation; Java reads
scoped CPU views. [Section-local counters](world/chunk/RUST-SECTION-COUNTERS.md)
in `world/level/chunk/counters/` fuse eligible canonical writes with count updates
and scan live storage for recounts; aliases retain independent counters.
[Immutable captures and bulk state-ID halos](world/chunk/RUST-SECTION-SNAPSHOTS.md)
live in `chunk/snapshot/`, and `chunk/stage_transfer.rs` supports native
[generation capture/adoption](world/levelgen/RUST-STAGE-HANDOFF.md).
[Shared section color fields](world/biome/RUST-SECTION-COLORS.md) live in
`world/level/biome/color_fields/`. Admitted [live biome palettes](world/biome/RUST-LIVE-BIOMES.md)
live in `world/level/chunk/biomes/`; `world/level/biome/live/` retains the client's
loaded-biome index and samples admitted raw sky and fog colors directly from
those owners. Exact-position color reuse validates the world revision and all
retained source generations on every query. Java supplies registry bindings and
immutable color tables, chunk/packet/range events, compatibility sky/fog sampling,
terrain tint/blending, brightness/weather adjustments, contextual light, model
admission and entity callbacks, and orchestrates chunks and generation stages.
These owners do not complete the world migration.

[Shared chunk inputs](world/chunk/RUST-DH-CHUNK-INPUTS.md) in
`world/level/chunk/inputs.rs` retain mutable block/counter owners, with an
independently admitted biome collection shared by sky/fog and DH columns.
Unchanged bindings avoid rebuilding pointer arrays and native owner rows;
Java still validates section/container identities on each admission. These are
bindings to live owners, not frozen chunk snapshots. Biome admission remains
independent of block/counter admission; registry identity, mutable escape and
custom getter order retain their contracts.

[Client residency](world/chunk-loading/RUST-CLIENT-RESIDENCY.md) in
`world/level/client_residency/` owns logical slots, counts, center, position
fingerprints and empty membership, using retained counters for ordinary scans.
The [range index](world/chunk-loading/RUST-CLIENT-RANGES.md) now selects loaded
stock chunks for block-entity and moving-piston scans through bounded pages;
mutations invalidate unread entries between callbacks.
Java retains atomic chunk references, reference-CAS facts, scalar ring/range reads,
radius relocation and callback delivery. This is partial client-world ownership.

The [visible tracking registry](world/entities/RUST-ENTITY-LOOKUP.md) in
`world/level/entity_lookup/` owns primary visible ID/UUID entries, ordered
enumeration and binding retirement for production client/server consumers.
Java retains behavior references and callbacks. The linked owner separates the
author-recorded CPU, lifecycle and settled checks from the outstanding strict
leash-crop and GUI crosshair differences. The following
[section-membership owner](world/entities/RUST-ENTITY-SECTIONS.md)
stores root and typed arrays in `world/level/entity_sections/`; production
client/server section consumers read native CPU views. Its CPU/Frozen,
seven lifecycle and scoped world/entity comparisons pass. Performance acceptance
remains open: the latest matched matrix passes DH and both shader modes,
but vanilla misses FPS/p99 floors. Broader runtime scope remains open. The
subsequent [section-coordinate index](world/entities/RUST-ENTITY-SECTION-INDEX.md)
owns production coordinates and ordered spatial queries. Its full suites,
Frozen replays and scoped lifecycle/world/entity checks pass; its matched
matrix still misses vanilla/DH FPS, so aggregate performance remains open. The subsequent [entity residency slice](world/entities/RUST-ENTITY-RESIDENCY.md)
owns primary UUID/chunk states and persistence admission in Rust. Full CPU
suites, Frozen replays and scoped lifecycle/world/entity checks pass; every
matched FPS/p99 floor passes in its settled rotating workload. Gameplay callbacks, persistence
I/O and the loading inbox remain Java.
Full parity and Java removal remain open.

The [entity geometry slice](world/entities/RUST-ENTITY-GEOMETRY.md)
retains primary position/bounds components per world and queries them directly
for ordinary camera culling. The [section range-query owner](world/entities/RUST-ENTITY-SECTIONS.md#range-queries)
links membership to those components and scans ordinary range queries in Rust.
The [world-query extension](world/entities/RUST-ENTITY-SECTION-INDEX.md#world-queries)
owns section visibility and joins untyped coordinate traversal with those scans.
Full CPU suites, Frozen replays and scoped runtime checks pass. Rejected-section
CPU improves against the Java-traversal control; Frozen CPU cost and the latest
vanilla p99 floor remain open. Rust removal state retires camera membership
at despawn while keeping primary geometry readable. The
[ticking membership owner](world/entities/RUST-ENTITY-ORDER.md) retains
client/server insertion order and immutable tick snapshots. Java keeps API
projections, object bindings, hooks, behavior callbacks and tick/extraction
orchestration. The [latest visible-tracking checkpoint](rendering/GAMEPLAY-PERFORMANCE.md#local-visible-tracking-checkpoint)
records author-reported CPU/lifecycle and scoped image checks, with passing settled
FPS/p99 floors after Java text-record cleanup. An earlier matrix on the same native
image failed vanilla p99; baseline movement and the combined changes do not
establish an isolated improvement or its cause. General entity
components, complete tick ownership and broad/long-session acceptance remain unfinished.

At the [reviewed source](https://github.com/HungLo2020/MattMC/blob/5de23173ae5b61fb17887589d4dff69a6b17fc77/src/main/rust/world/level/chunk/mod.rs),
DH world CPU ownership also includes columns, mutable full-data recipients,
parent reduction, ordered occlusion, contributor snapshots, material-map
identities/remapping, same-detail merges and change hashes. The separate
`render/dh_columns/` owner reduces compact render columns. At that checkpoint Java supplied
admission/wrapper bindings, update orchestration, metadata list identities and persistence,
contextual colors/material resolution, faces, greedy mesh assembly and final
packets. The following release-specific records describe successive checkpoints;
they do not verify the combined source's runtime or performance.

Ordinary [DH quad builds](rendering/RUST-DH-QUADS.md) now retain directional
quad storage, opacity admission, premerging and greedy policy in Rust. Java
retains source traversal, contextual getter implementations and collector
lifecycle orchestration. [Packet plans](rendering/RUST-DH-PACKETS.md) now own layer selection,
callback admission/order, shading and vertex/companion encoding;
subclass compatibility remains Java. This advances the reviewed checkpoint above.

[Box and vertical face policy](rendering/RUST-DH-VERTICAL-FACES.md) also moves complete
box geometry, fake-ocean policy and stock directional shading into Rust, and owns neighbor
visibility/light intervals and semantic splits in Rust, directly consuming them
into the quad owner. Java lends canonical compact columns and bounded immutable shade facts for
ordinary builds. Source traversal and contextual material resolution remain Java;
custom shade providers retain their ordered compatibility callbacks.

Admitted [DH height fields](world/chunk/RUST-DH-HEIGHTMAPS.md) in
`world/level/chunk/dh_heightmaps/` scan live block owners and section counters
using the bounded intrinsic collision catalog in `content/block/collision/`.
Java's chunk wrappers borrow independent immutable CPU results instead of
constructing two mirrored height arrays. The latest local source also owns native
mutation revisions and bounded immutable field reuse: weak generation identities
avoid retaining chunks, old CPU readers survive replacement/eviction, and scans
run outside the cache lock. Releaseb1611469 passes227 Rust world tests and1,871
Java tests (two skipped); six full-task workers map the image and all source/
native/Frozen/prompt guards match. Runtime, performance and sustained memory
acceptance remain pending. The [DH lighting pass](world/lighting/RUST-DH-LIGHTING.md)
in `world/level/lighting/dh/` owns section-local snapshots, sky seeding,
priority queues, packed block/sky fields and shared emitter indices. Direct
wrapper getters borrow those fields. Java retains exact-input admission,
wrapper publication, correctness flags, mutable emitter-list projections and
compatibility lighting. A mutable light operation detaches to Java storage;
section-local locks do not make a whole chunk or neighborhood transactional.
The ordinary [saved DH lighting reader](world/lighting/RUST-DH-SAVED-LIGHT.md)
also borrows existing NBT nibbles into this owner. Rust determines skipped pairs,
carried skylight, compact bytes and original section counters; generation carries
one field instead of two Java light-storage maps. Imported fields defer emitter
enumeration until its existing consumer boundary. Java retains NBT/file reads,
wrapper admission and public mutable compatibility.
The in-progress [DH column producer](world/chunk/RUST-DH-COLUMNS.md) borrows
block, biome, counter, height and light owners to construct immutable points,
material IDs and source hashes in Rust. Java publishes direct `LongList` CPU
views and resolves unique compatibility identities once. Mutable recipients,
merge orchestration, DTOs and contextual event hooks remain transitional Java;
first mutation detaches a column or mapping. Final CPU suites, seven lifecycle
cases and both reviewed settled pairs pass for this slice. Actual travel producer
admission and paired allocation checks pass. All20 rotating timings are clean,
but vanilla p99 and DH median FPS miss Frozen; both shader modes pass. Mutable lighting escapes preserve
retained counter/constant policy alongside values. Those checks belong to release839ff128.
The later local [DH full-data migration](world/chunk/RUST-DH-FULL-DATA.md)
keeps recipient point vectors, bulk copy/remapping, complete occlusion and parent
reduction in Rust. A whole parent quadrant now retains one native point owner
for direct Java consumers; writes detach into independent native vectors.
The [contributor transport](world/chunk/RUST-DH-CONTRIBUTORS.md) also owns
immutable footprint capture, clipping and signed ID remapping. The ordinary
transformer and render source retain its CPU readers directly; public defensive
exports remain. The quadrant producer owns uniformity, ordered footprint
admission, signed generation/compression metadata and remapped list hashes.
Detached mutable readers release their old producer and arena references;
other immutable readers remain valid. The latest local source additionally
runs the complete Frozen-ordered64×64 occlusion pass in one native call, consuming
existing owners and publishing updated extents/hashes without point export.
Custom sources/lists, aliases and unsupported inputs keep original sequencing.
The [same-detail source update](world/chunk/RUST-DH-SOURCE-UPDATES.md) now also
owns signed-generation eligibility, remapping, wrapped hashes/change flags
and ordered footprints. Publication reuses recipient vector capacity directly;
Java no longer repeats per-column clear/copy/remap/hash calls. Borrowed source
leases end at successful publication; immutable footprint readers retain their
independent batch. Compatibility routes preserve aliases, custom callbacks and
exception mutation prefixes. Releaseefeca492 passes234 Rust world checks and
1,880 Java tests (two skipped), including actual Frozen metadata/reduction/
ordered-pass/same-detail updates and ordinary recipient controls. Six full-task
workers map that image and all source/native/Frozen/prompt guards pass. Java update orchestration, material-map mutation, metadata
storage, persistence and contextual transformation policy remain. At that checkpoint, full Rust
hardware graphics, gameplay, performance and sustained memory acceptance awaited
driver restoration. See the [later author-reported preflight](rendering/RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures)
for driver recovery and full-suite results; new MattMC runtime acceptance remains
pending, and prior839ff128 timings do not verify this source.

The subsequent local [DH material-map migration](world/chunk/RUST-DH-MATERIAL-MAPS.md)
keeps ordered serial identities, deduplication/import indexes, whole-map remapping
and wrapped hash policy in Rust. Java retains compatibility wrapper bindings;
read-only producer maps keep their original readers, and their Java identity index
stays empty. Custom callbacks and persistence remain transitional. The actual
Frozen map corpus and ordinary producer/recipient controls pass on releasec57a1a17;
237 Rust world tests and1,883 Java tests (two skipped) pass;35 column/material
controls, six exact full-task workers and source/native/Frozen/prompt guards pass.
Hardware/gameplay,
performance and sustained memory acceptance remain open.

The latest material identities additionally retain registered block-state facts
beside their serial keys. Native occlusion and parent policy consumes those Rust
entries directly, removing the admitted Java state-ID array and wrapper scan.
Policy outputs own independent facts and borrow the map only during creation;
custom inputs retain compatibility admission. All20 focused Rust/238 world checks
and full Java1,884 tests (two skipped) pass on release21991771. Four material-map
JNI controls,36 column/material controls, six exact full-task workers and all
source/native/Frozen/prompt guards pass. Hardware/performance and sustained
memory remain unverified.
See the [direct intrinsic consumer](world/chunk/RUST-DH-MATERIAL-MAPS.md#direct-intrinsic-material-consumer-local-work-in-progress).

The ABI 79 [native terrain-frame handoff](rendering/JAVA-BRIDGE.md#native-terrain-selection-frames)
uses `render/chunk/terrain_frames.rs` to retain immutable camera-layer/caster
arrays from the shared `render/scene/terrain.rs` vocabulary. Java passes CPU
owner/frame identities and the exact camera; decoding acquires shared ownership
before queueing, avoiding Java pending/request record copies. Each graph retains
at most three selections for lookup; reload/closure invalidates unresolved
references while acquired arrays survive. The author-recorded CPU/JNI checkpoint
uses release `7cf545b2`; it does not establish current runtime/performance acceptance. Legacy/diagnostic
encoding remains.

These world CPU producers are separate from DH render-column collection,
GPU resources and ordinary light-engine publication below.

[Canonical terrain face policy](rendering/RUST-TERRAIN-CULLING.md) now runs in
Rust meshing over retained world IDs, using one bounded intrinsic geometry table
and facts from the native block registry. Java keeps contextual/tag/hook callbacks
and exports source geometry once. Model/state/selector cache reads can overlap
across independent builders; registration and reload remain exclusive. These are
renderer CPU owners, separate from authoritative world storage and GAL resources.

[Live light layers](world/lighting/RUST-LIVE-LAYERS.md) in
`world/level/lighting/layers/` own lazy defaults and nibble generations.
[Canonical block/sky light maps](world/lighting/RUST-LIGHT-MAPS.md) in
`world/level/lighting/maps/` retain typed layer owners and shared 64-shard roots
for section entries and sky column tops. Snapshots share roots; a later mutation
detaches the root and its changed shard. Exact canonical block/sky scalar reads
sample retained owners directly, including sky traversal through missing layers.
Java retains light-engine orchestration, queued-section precedence, publication
sequencing, section callbacks and temporary `DataLayer` identity slots. Supplied
Java maps, custom storage/layers and escaped mutable arrays retain compatibility
behavior. Terrain rebuilds retain layer references before the bulk consumer leases
their current generations. These are CPU owners, separate from renderer resources.

Packed chunk storage also uses [palette histograms](world/chunk/RUST-PALETTE-HISTOGRAM.md)
under `world/level/chunk/palette/histogram/` for general ordered counting and
compatibility section recounts; canonical recounts scan the live owner directly.
[Palette resizing](world/chunk/RUST-PALETTE-RESIZE.md) under
`world/level/chunk/palette/resize/` provides bulk remapping during block palette growth.
[Global palette loading](world/chunk/RUST-PALETTE-UNPACKING.md) uses
`world/level/chunk/palette/unpack/` and the existing encoder for saved-data repacking.
[Direct saved-palette loading](world/chunk/RUST-SAVED-PALETTES.md) now constructs
primary local/global block and biome owners in `palette/saved.rs`. Java supplies
bounded semantic IDs and saved words; it constructs no palette/bit-storage mirror
for admitted imports. [Decoded-section hydration](world/chunk/RUST-DECODED-SECTIONS.md)
creates those owners and saved light layers directly in Rust, eliminating Java
array/list/stream reconstruction on admitted NBT loads.
[Retained region tapes](world/chunk/RUST-REGION-TAPE.md) connect the Rust file
producer and decoder with explicit consumption/discard release. Full-tape Java
copies and output-capacity retries are absent from admitted disk loads; validation
fingerprints are requested only when validation is enabled. Java IO scheduling
and non-section root-tag parsing remain transitional.
[Player chunk distances](world/chunk-loading/RUST-PLAYER-DISTANCE.md) use
`world/level/chunk_distance/` for DistanceManager's natural-spawn, player-ticket,
[simulation](world/chunk-loading/RUST-SIMULATION-DISTANCE.md) and
[loading](world/chunk-loading/RUST-LOADING-DISTANCE.md) distance graphs and
[POI village](world/chunk-loading/RUST-POI-DISTANCE.md) section distances;
Java keeps player sets, tickets, POI records, holders and published level views.
[Server chunk tasks](world/chunk-loading/RUST-CHUNK-TASKS.md) use
`world/level/chunk_tasks/` for priority buckets, ordered chunk membership and
task lists. Java temporarily pins callables and delivers dispatcher/future
callbacks; it keeps no pending priority map or per-chunk task-list owner.
[Cached DH tint neighborhoods](world/biome/RUST-DH-TINT.md) read native full-data
columns and material identity leases. Rust owns color storage and sampling;
Java retains resolver callbacks and projects primary cell handles/generations.
The [retained DH source table](world/chunk/RUST-DH-SOURCE-TABLE.md) now owns live
column leases and feeds this consumer directly, removing repeated Java
neighborhood descriptor preparation. Source/list caller exclusion still applies;
The [metadata owner](world/chunk/RUST-DH-SOURCE-METADATA.md) also retains
generation, compression and uniformity planes; merge and parent consumers read
them directly. Java byte-array mirrors and source phantom-pool registration are
removed. Java identity projections, update orchestration and persistence remain
temporary.
[Owned source occlusion](world/chunk/RUST-DH-SOURCE-OCCLUSION.md) also reads
primary columns directly: Rust admits and detaches centers and preserves the
complete Frozen pass order. Rust publishes new columns and registers escaped
reader identities; Java receives selected extents and creates CPU leases on
demand.
The earlier [tint scenarios](world/biome/RUST-DH-TINT.md) and newer
[source-update/culling comparisons](world/chunk/RUST-DH-SOURCE-OCCLUSION.md#verification-and-cpu-comparison)
have separate author-reported workloads and source scopes. None establishes
realistic renderer/server acceptance; preserve the owner-specific timing limits.
[Ordered palette values](world/chunk/RUST-PALETTE-DISTINCT.md) use
`world/level/chunk/palette/distinct/` for biome/block scans; Java retains palette
resolution and callback delivery.
[Geode density fields](world/levelgen/feature/RUST-GEODE.md) use
`world/level/levelgen/feature/geode/` for one grid batch; Java retains ordered
random draws, placement decisions and world callbacks.
[Canyon ellipsoids](world/levelgen/carver/RUST-CANYON.md) use
`world/level/levelgen/carver/canyon/` for pure canyon candidate geometry on
chunks that retain Java carving. Eligible chunks instead use the
[Rust carvers stage](world/levelgen/carver/RUST-CARVERS.md), including tunnel
randomness, carving masks and block operations over native chunk storage.
[Old-terrain height blending](world/levelgen/blending/RUST-HEIGHT-BLENDING.md) uses
`world/level/levelgen/blending/` for current chunk-grid evaluation; Java retains
direct lookup precedence, sample traversal and scalar/custom compatibility.

World collision geometry also uses [voxel Boolean joins](world/physics/RUST-VOXEL-JOIN.md)
under `world/phys/shapes/boolean_join/`. Java keeps coordinate merging and
shape ownership while Rust evaluates packed occupancy.
Ordered [merged voxel boxes](world/physics/RUST-VOXEL-BOXES.md) use
`world/phys/shapes/box_extract/`; Java retains the original snapshot and callbacks.
[Voxel rotation](world/physics/RUST-VOXEL-ROTATION.md) uses
`world/phys/shapes/rotation/` for packed axis transforms while Java retains
floating coordinates and shape ownership.
[Closest collision points](world/physics/RUST-VOXEL-CLOSEST-POINT.md) use
`world/phys/shapes/closest_point/` to combine the established box traversal
with clamping and ordered distance reduction in one call. Java retains input
ownership, custom compatibility and result construction.
[Ray/shape intersection](world/physics/RUST-VOXEL-RAYCAST.md) uses
`world/phys/shapes/raycast/` for ordered outside-ray hits without exporting a
box list. Java retains public ray prechecks and immediate inside-hit behavior.

### `gameplay/`

Gameplay systems belong here. `gameplay/tacz/function_modifier.rs` already
contains the TaCZ function-modifier kernel. Native player, entity, item,
combat, block-interaction and component systems migrate here by ownership;
the general block-behavior and component systems in the
[game model](game-model/index.md) remain proposals. Its registry, state-graph,
property, fluid, registered block-definition, physical-setting and intrinsic
state-rule owners already live in `content/`.

### `content/`

[`content/state`](game-model/STATE-GRAPHS.md) owns ordered state domains,
Cartesian values and transition graphs. The shared registry and temporary
Java block/fluid views use it. [Block state definitions](game-model/BLOCK-DEFINITIONS.md) now provide the
registered catalog and shared graphs; [physical definitions](game-model/BLOCK-PHYSICS.md)
supply intrinsic block settings. [Intrinsic state rules](game-model/BLOCK-INTRINSICS.md)
compute semantic map colors, emitted light and canonical fluid associations.
[Map palette content](game-model/MAP-COLORS.md) owns palette identities, RGB and
packed brightness tables; rendering owns image residency and GPU resources.
[Sound content](game-model/SOUND-DEFINITIONS.md) owns event identities, profiles
and note instruments; [block sound/offset definitions](game-model/BLOCK-SOUND-AND-OFFSETS.md)
own state sound selection and model offsets. [Block-family configuration](game-model/BLOCK-FAMILY-TYPES.md)
provides shared block sets, wood types and registered family parameters.
[State policy](game-model/STATE-POLICY.md) computes tick/light-shape eligibility
and leaf/entity markers into shared immutable columns.
The [intrinsic collision catalog](world/chunk/RUST-DH-HEIGHTMAPS.md)
retains admitted cached box geometry exported by Java once; Rust derives DH
solidity and opacity using that geometry and existing block-registry facts.
Dynamic/custom shape providers and incompatible policies keep Java callbacks.
The catalog does not migrate shape construction or contextual collision queries.
Playback resources, resolution, playback policy, music, ambience and
subtitles live in `audio/`; Java retains sound instance objects, entity
samples and pack reads. Block-family configuration and state policy do
not migrate world callbacks, scheduling or entity queries. General gameplay remains Java.

[`content/fluid`](game-model/FLUID-DEFINITIONS.md) owns built-in fluid names,
registration order, property declarations, defaults and intrinsic state facts.
Java projects compatibility objects; world-dependent fluid simulation remains
Java. The registry is immutable and independent of its consumers.

[`content/property`](game-model/PROPERTY-DEFINITIONS.md) owns all 134
property declarations and ordered domains. The block registry shares immutable
schemas directly; Java enum/property objects are temporary compatibility views.
The 11 declarations for integrated content retain their distinct identities.

[`content/tags`](resources/TAGS-AND-REGISTRIES.md#tag-loading) decodes tag
files, orders them and resolves their members; Java performs the element
lookups it plans. [`content/registry`](resources/TAGS-AND-REGISTRIES.md#registry-identity-snapshot)
holds identity snapshots of frozen registry sets (network IDs, known packs, tag
ID lists) behind registry and tag synchronization; registry storage and
element codecs remain Java.

Content definitions and registries belong here. Use this for native representations of blocks, items, fluids, models, recipes, data-driven definitions, and other game content metadata. Today it holds the [block registry](game-model/RUST-BLOCK-REGISTRY.md) (`content/block/`): every block, property and block state with per-state columns, built from native identity/domain/default declarations plus a lazy export of remaining Java state facts. World and storage subsystems derive their tables from it, and terrain meshing combines its block facts with render-owned columns. The block registry is immutable for the process lifetime; rendering's cache and resource lifecycle remain separate. Java still owns block behavior factories, shape/blocked-light/contextual-predicate functions, motion/solid/custom flags and contextual gameplay, and Rust item/entity registries and general gameplay components remain proposals. `content` must not depend on its consumers.

### `render/`

Rendering systems belong here. This includes backend-independent render code, native render data structures, chunk rendering helpers, and backend-facing rendering subsystems. For how to work on the renderer, start at the [Rendering](rendering/index.md) developer docs.

Important current subdirectories:

- `render/chunk/`: native chunk-rendering infrastructure, render lists, occlusion, translucent sorting, index generation, rebuild triggers and bounded immutable terrain-selection frames.
- `render/chunk/meshing/`: native chunk mesher implementation, including section scanning, static models, fluids, lighting/AO, tinting, culling, packing, assembly, FFI records, and diagnostics. Its [canonical face-policy owner](rendering/RUST-TERRAIN-CULLING.md) consumes retained world IDs; models, selectors and meshing states share one cache with concurrent read guards and exclusive registration/reload.
- `render/vulkanic/`: the VulkanicGAL graphics abstraction layer (handles, resources, commands, frames, sync, capabilities, metrics) and GAL creation. See its [README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/README.md).
- `render/vulkanic/backends/`: private backend implementation modules. Code outside `render::vulkanic` must not call into backend modules directly.
- `render/dh_collector/`: the Distant Horizons column ledger: generations, column payloads, publication, retirement, owner leases, the prepared frame's visible segments and route receipts ([rendering architecture](rendering/RENDER-ARCHITECTURE.md)). Its Java exports are `render/bridge/dh_collector.rs`. [Retained DH visibility](rendering/RUST-DH-VISIBILITY.md) now receives incremental generation-tree mutations and directly traverses and admits candidates in Rust on the stock camera route. Java retains generation/retrieval orchestration, contextual material provenance, frame parameters and custom CPU callbacks.
- `render/dh_columns/`: complete compact render-column reduction, exact sort ties, water/material companions and bounded worker scratch. Canonical Java consumers borrow existing CPU storage and publish directly; contextual material resolver callbacks remain Java. See [render columns](rendering/RUST-DH-RENDER-COLUMNS.md).
- `render/dh_quads/`: retained ordinary quad storage, complete box visibility/geometry/shading, greedy topology, semantic packet plans and scoped column builds. Rust encodes final buffers and moves them directly into the ledger; Java supplies bounded contextual facts, lifecycle calls and requested diagnostic projections. See [box production](rendering/RUST-DH-VERTICAL-FACES.md), [quad builds](rendering/RUST-DH-QUADS.md), [packets](rendering/RUST-DH-PACKETS.md) and [native handoff](rendering/RUST-DH-COLUMN-HANDOFF.md).
- `render/clouds/`: built-in DH cloud motion, placement, culling and color-change history in CPU owners. Java retains texture/API boxes, world-color queries and callbacks; see [cloud preparation](rendering/RUST-DH-CLOUDS.md).
- `render/items/`: immutable authored item poses and world/hand composition in CPU owners. Java retains models, tints, topology and parent animation; see [item preparation](rendering/RUST-ITEM-LAYERS.md). This does not implement native item registries or gameplay.
- `render/scene/`: wire and data vocabulary shared by Java and the renderers, including immutable native terrain selections consumed directly by frame decoding ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/scene/README.md)).
- `render/shared/`: helpers used by both the world and GUI renderers ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shared/README.md)).
- `render/shaderpack/`: shader-pack parsing, planning and runtime ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/README.md)).
- `render/worldrender/`: the world renderer ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/README.md)). `terrain/staging.rs` retains assembled vertices by mesh key and generation until asset acceptance or discard. `terrain/publication.rs` owns published section-layer identities and changed rows, synchronized directly into the Rust section graph. Java still registers/removes those identities, coordinates asset acceptance and resource-reload swaps, and supplies build/sort/atlas inputs. Ordinary opaque/cutout terrain uses shared page resource sets; per-mesh bindings are created only when needed outside that path, and section/material keys reuse resident geometry for the same mesh generation and vertex ABI. See [render resource preparation](rendering/RENDER-ARCHITECTURE.md), [terrain publication](rendering/JAVA-BRIDGE.md#terrain-publication-registry) and [terrain intake](rendering/JAVA-BRIDGE.md#terrain-layer-intake) for the ownership, copy and lifetime limits.
- `render/guirender/`: the GUI renderer: sprites, quads, item meshes and rasters, the panorama and GUI post effects ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/guirender/README.md)). ABI 80 [GUI item templates](rendering/RUST-GUI-ITEM-TEMPLATES.md) retain cached standard 3D render inputs in Rust and replace per-quad Java frame records; the guide separates reported GUI image passes from unfinished broad runtime and performance acceptance.
- `render/bridge/`: the Java FFI bridge: the `mattmc_vulkanic_gal_*` C ABI, wire records, decoding and the context registry. It is the only code that creates GALs and chooses their backend ([README](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/README.md)).

### `assets/`

Asset loading, decoding, caching, and resource processing belong here.
`assets/packs/` opens and reads directory, ZIP and vanilla packs and owns
`pack.mcmeta` formats, overlays and compatibility; `assets/repository.rs`
discovers packs and orders the selection; `assets/resources/` is the resource
manager (namespace stacks, filters, sidecar metadata). `assets/textures/`
builds texture atlases (sprite sources, PNG decoding, Sodium preprocessing,
stitching, mip levels) and `assets/models/` loads, resolves and bakes block and
item models. Java keeps the `PackResources`/`ResourceManager` API, metadata
section codecs, item definitions and the atlas and baked model objects its
consumers use; see [resource packs and registries](resources/index.md),
[texture atlases](resources/ATLASES.md) and [models](resources/MODELS.md).
`assets/map_image.rs` performs bounded indexed-map PNG encoding for the
transitional world asset transport; map palette data stays in `content/`. Future Rust-side shader and language work should live here when it is not exclusively part of a rendering backend.

### `audio/`

The [native audio backend](https://github.com/HungLo2020/MattMC/blob/ed8dc5714631e0e55d31dbd448789f21a47bedf6/src/main/rust/audio/mod.rs)
owns playback resources, device-local buffers and decoding. Its `policy/`
module owns `sounds.json` resolution, the asset cache, playback policy, the
audio thread that owns (and reopens) the device, the vanilla tickables'
rules, music, ambience, subtitles and the preview; see
[audio development](audio/index.md). Java
[`SoundEngine`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/sounds/SoundEngine.java)
keeps the `SoundInstance` objects gameplay code creates, custom tick callbacks,
entity/biome/light and situational-music inputs, asynchronous resource-read
scheduling and UI drawing. Bundled pack IO now uses the
[resource owners](resources/index.md); Java still requests/copies sound bytes
and returns them to the audio asset cache on its IO pool.
The [shared ambient RNG boundary](audio/CLIENT-SOUNDS.md#shared-level-random)
preserves raw state for the two legacy generators; arbitrary generators use
a separately seeded compatibility sequence. Sound-event, profile and
instrument identities belong to `content/sound/`, independent of those
reloadable resources. The [verification scope](audio/SOUND-ENGINE.md#verification-scope)
keeps policy/FFI tests separate from audible hardware and reload/shutdown
acceptance; the native command thread persists across device cleanup and reopening.

### `network/`

Network protocols and wire codecs belong here. Current
[bulk long-array conversion](network/codec/RUST-LONG-ARRAY.md) uses
`network/codec/long_array/` for exact big-endian conversion. Java retains Netty
buffer ownership, length prefixes, cursor updates and compatibility behavior.

### `storage/`

Persistent encodings and region storage belong here. The
[chunk-section serializer](world/chunk/RUST-CHUNK-SECTIONS.md) uses `storage/chunk/`
to encode eligible section palettes, packed values, light layers and section Y
straight into NBT tape. Rust derives the block-state save vocabulary and
palette storage bits from the shared block registry. Java still orchestrates chunk
capture, supplies per-chunk biome names, builds the remaining root compound
and owns save scheduling. Pending writes retain tape and create Java tags lazily when read;
unsupported section inputs keep the Java encoding route. Eligible current-version
loads use [retained region tapes](world/chunk/RUST-REGION-TAPE.md) and
[decoded-section hydration](world/chunk/RUST-DECODED-SECTIONS.md) to create
primary block, biome and light owners inside Rust. Java wraps their leases and
parses remaining root/chunk state; it no longer reconstructs admitted section
palette/word/light arrays before native import. Non-current/noncanonical input
retains full-tag upgrade and parse compatibility. This boundary does not migrate the complete chunk
lifecycle or establish concurrent-save parity.
The `nbt/`, `poi/` and `region/` modules retain their separate responsibilities.

### `platform/`

Platform abstraction belongs here. Use this for OS, filesystem, threading, native library, windowing-adjacent, and platform-specific integration that should not leak into game or render logic.

### `compat/`

Compatibility and interop layers belong here. Use this for bridge code that exists because Java, Fabric, shader packs, mods, or legacy systems still need adaptation during the incremental migration.

### `tools/`

Developer and offline tooling belongs here. This is for Rust code that supports build-time utilities, diagnostics, conversion tools, generators, or replay/benchmark helpers rather than runtime engine behavior.

## Boundary Notes

Rust `render/vulkanic/backends/` is intentionally private. The architecture tests enforce that:

- `render::vulkanic` owns backend routing.
- Rust code outside `render::vulkanic` cannot reference backend implementation modules.
- `ash` and `shaderc` usage stays inside the Vulkan backend.
- `glow` usage stays inside the OpenGL backend.
- OpenGL and Vulkan backend modules do not depend on each other.

The same tests (`render/vulkanic/architecture_boundary.rs`) enforce the renderer layering: which render layer may depend on which, and that only the bridge creates GALs. See [Render Architecture](rendering/RENDER-ARCHITECTURE.md) for the rules and where new rendering code belongs.

Java package names may still appear in Java source and Java tests. The Rust tree should use subsystem ownership instead of Java-style package paths.

Local in-progress [DH chunk-input ownership](world/chunk/RUST-DH-CHUNK-INPUTS.md)
retains mutable block/counter/biome owners once and directly serves height,
emitter, lighting and column consumers.
[Client residency](world/chunk-loading/RUST-CLIENT-RESIDENCY.md) now uses those
counter owners for callback-bounded empty scans and moves logical slots, counts,
center and empty membership into Rust. Java still binds chunk objects, supplies
reference-CAS facts, projects ring reads, orchestrates relocation, and delivers
compatibility callbacks. Exact Frozen CPU replay is supplemental evidence;
the [release-separated runtime record](rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary)
now includes bounded author-reported lifecycle/images and settled timings.
The earlier linked aggregate timing gate fails; the subsequent
[DH visibility checkpoint](rendering/RUST-DH-VISIBILITY.md) records a passing
settled matrix for its own native image. Broad temporal/streaming and long-memory
acceptance remain open.

The local [DH change-hash producer](world/chunk/RUST-DH-CHANGE-HASH.md) owns
sampling and wrapped identity/hash policy over retained block/biome/counter
owners and cached height/emitter fields. Java admits contextual compatibility
identities and preserves the mutable emitter callback boundary. This is a world
CPU subsystem and introduces no renderer resource or presenter ownership.

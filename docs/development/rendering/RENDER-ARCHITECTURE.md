# Render architecture

The native renderer is split into layers with one-way dependencies. Java hands
semantic frames to the bridge; renderers turn them into GAL command lists; the
GAL executes them on a private backend, with per-submission checks controlled
by the [GAL validation mode](VULKANIC-GAL.md).

This Java/Rust split describes the current migration. The final runtime target is defined in [Project Architecture](../PROJECT-ARCHITECTURE.md); use the [Goal 5 checkpoint](GOAL-5-STATUS.md) for implemented scope, acceptance evidence and remaining work.

```text
render/
├── bridge/       Java C ABI: wire records, decoding, context registry (composition root)
├── worldrender/  world renderer; composes the GUI on the whole-frame route
├── guirender/    GUI renderer: sprites, quads, item meshes, post effects
├── shaderpack/   shader-pack parsing, planning and runtime
├── shared/       helpers used by both renderers
├── scene/        wire and data vocabulary (constants, data types)
├── dh_collector/ DH column state, copied payloads, publication and frame admission
├── clouds/       CPU cloud motion/placement/culling owners; no GPU dependencies
├── items/        authored item poses and world/hand composition; no GPU dependencies
├── vulkanic/     VulkanicGAL: the graphics abstraction layer and its backends
└── chunk/        native chunk meshing, render lists and sorting used by Java's chunk renderer
```

## Dependency rules

Each layer may use the public GAL modules and the layers listed for it. The
rules are enforced by
[`architecture_boundary.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/architecture_boundary.rs),
which runs with the Rust tests.

| Layer | May depend on |
| --- | --- |
| `vulkanic` (GAL and backends) | nothing above it |
| `scene` | GAL value types only (`vulkanic::resources`) |
| `shared` | the public GAL |
| `shaderpack` | `scene` |
| `guirender` | `scene`, `shared`, `shaderpack` |
| `worldrender` | `scene`, `shared`, `shaderpack`, `guirender` |
| `bridge` | everything above, as the composition root |

Further rules:

- Only `vulkanic/backends/` names a backend (Vulkan or OpenGL). Everyone else
  branches on `BackendCapabilities` (features, limits, shader conventions),
  never on which backend is running.
- Only the bridge creates GALs (`VulkanicGal::create*` with `BackendChoice`).
- Code outside `vulkanic` uses the public GAL modules only; tests build GALs
  through `vulkanic::test_support`.
- `guirender` never names `worldrender`. World-owned atlases reach the GUI
  through the `GuiAtlasOwner` trait, which the world renderer implements.
- The GAL carries no game vocabulary and never branches on resource labels.
  Renderer-specific profiling lives in the renderers (`WholeFrameProfile` in
  `worldrender` embeds the GAL's `SubmitProfile`).

Console I/O uses the std-only `core::console` helpers from any layer, keeping
diagnostic failures outside submission and resource state. This adds no renderer
or backend dependency; see [VulkanicGAL](VULKANIC-GAL.md) for the closed-pipe check.

## Where new code goes

| You are adding | Put it in |
| --- | --- |
| A new kind of draw, pass or effect for the world | `worldrender/` (see its README) |
| GUI drawing, item rendering or a GUI post effect | `guirender/` |
| A wire constant or data type shared with Java | `scene/` |
| Shader-pack parsing or pass planning | `shaderpack/` |
| DH column generations, leases or publication/visibility bookkeeping | `dh_collector/`; keep Java wire handling in `bridge/dh_collector.rs` |
| Built-in DH cloud motion, placement and culling policy | `clouds/`; see [cloud preparation](RUST-DH-CLOUDS.md) |
| Authored item-layer transforms and world/hand pose composition | `items/`; see [item preparation](RUST-ITEM-LAYERS.md) |
| A new Java entry point or wire record | `bridge/` (see [Java Bridge](JAVA-BRIDGE.md)) |
| A new GPU capability, resource type or command | `vulkanic/` (see [VulkanicGAL](VULKANIC-GAL.md)) |

A new GPU feature belongs in the GAL only if it is game-agnostic; anything
that knows about blocks, entities, the GUI or shader packs belongs in a
renderer. If a renderer needs a backend difference, add a capability rather
than checking the backend.

The Java CPU boundary may move upward into Rust when gameplay profiles identify
avoidable collection, policy, state rebuilding or boundary traffic. Measure the
producer and transfer separately before moving ownership; preserve equivalent
semantic inputs and generation/lifetime checks. Keep game-specific state in the
renderer or its CPU source, and use VulkanicGAL for all GPU work. This does not
authorize borrowed Java GPU state, a fallback renderer or another presenter.

Current chunk CPU inputs have separate world owners: [live block sections](../world/chunk/RUST-LIVE-SECTIONS.md)
own canonical storage/mutation, [section-local counters](../world/chunk/RUST-SECTION-COUNTERS.md)
fuse eligible writes and recount directly, [immutable rebuild snapshots](../world/chunk/RUST-SECTION-SNAPSHOTS.md)
provide bulk state-ID halos, and [section color owners](../world/biome/RUST-SECTION-COLORS.md)
share resolver lattice samples within a capture. [Live biome owners](../world/biome/RUST-LIVE-BIOMES.md)
retain admitted palettes and the loaded-client index used by direct raw sky
sampling. Java still delivers chunk/packet/range events and retains fog and
terrain-tint consumers. [Live light layers](../world/lighting/RUST-LIVE-LAYERS.md)
own canonical lazy defaults and allocated nibble generations, including native
propagation handoffs. The [terrain-light consumer](RUST-TERRAIN-LIGHTING.md)
borrows these generations and prepares mesher words directly. Java retains
light-engine orchestration, map publication, contextual light predicates/shade,
biome blending, model admission and worker dispatch. Canonical
[generation-stage transfers](../world/levelgen/RUST-STAGE-HANDOFF.md) copy/adopt
inside Rust; stage and live formats remain separate. These CPU owners do not
change GAL resources, completion or presentation.

[Map images](../game-model/MAP-COLORS.md) similarly cross as indexed CPU colors;
Rust expands ordinary RGBA textures and owns native map material policy. The
indexed GUI input arrived with ABI 73; the current whole-frame ABI is 78.
Java retains map revisions, staging and contextual map production.

Raw GUI images use a separate incremental publication contract at ABI 78:
changed CPU payloads accompany the complete live identity manifest, while Rust
retains unchanged pixels and GPU resources. Admission validates the combined
resident bounds before changing the generation. Java retains the resident set
and unaccepted changes for retry, and resends all resident payloads after native
context recreation. VoxelMap consumes its dirty flag before copying and reuses
clean immutable snapshots. See [raw-image generations](JAVA-BRIDGE.md#raw-gui-image-generations)
and the [ordinary gameplay protocol](GAMEPLAY-PERFORMANCE.md); reducing this
traffic does not establish end-to-end performance acceptance.

Selected-source frames carry thousands of mesh instances (mostly off-camera
shadow candidates), and several passes look each one up every frame. Keep
those lookups hashed: `mesh_assets` is a `MeshAssetMap` keyed through
`MeshKeyHasher` (see [`worldrender/mod.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/mod.rs)),
and nothing may depend on its iteration order. Derive per-asset facts once
(for example `has_optical_stencil_sections`) instead of scanning sections per
instance. On the Java side, resource texture bytes requested during frame
extraction go through `TexturePayloadCache`, which resource reload clears.

Java still memoizes raw biome sky and fog samples before later
brightness/weather adjustments. Each `ClientLevel` stores four sky samples in
a ring and one fog sample, keyed by exact quart-position coordinates,
partial-tick bits and game time, without an explicit frame-id reset.
On a sky-memo miss, Sodium's hook first asks the native loaded-biome index for
an exact `ClientLevel`. Rust samples retained biome generations and reuses its
216-color window while source revisions remain current. Native sampling can
decline; the hook then uses Java's fast cubic sampler, and the outer caller
retains its Gaussian fallback if no hook returns a result.

The memo does not round positions or cache later weather/brightness adjustments.
Fog sampling, those adjustments and custom hooks remain Java-owned. Keep the
Java memo lifetime distinct from the native generation/revision checks; the
live-biome migration does not replace both with one cache. See
[`ClientLevel`](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/minecraft/client/multiplayer/ClientLevel.java#L958-L1040),
[`SodiumSkyColorHook`](https://github.com/HungLo2020/MattMC/blob/1b9b103398fd70d5b5152b93a1d0abc581fffc19/src/main/java/net/sodium/fabric/SodiumSkyColorHook.java#L18-L35)
and [live-biome constraints](../world/biome/RUST-LIVE-BIOMES.md#boundaries-to-preserve).

Ordinary frames select camera-visible terrain layers in the Rust section graph.
Solid and cutout layers preserve the graph's BFS visit order rather than
sorting by section key or Euclidean distance. The [selection regression](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/chunk/terrain_selection/tests.rs#L45-L74)
intentionally supplies a far section first and preserves that order. Translucent
layers are sorted back to front, with equal-distance ties retaining visit order.
The author's local shader comparison reports GPU 3.18→2.93 ms; it is not an
independent performance result. The retained Java producer emits ascending
section-key order (`SectionKeyOrder`) for ineligible diagnostic, fault, reload
and readiness-receipt frames, because hash-map iteration order shifted as
sections streamed and identical sets missed Rust's batch-plan cache.
Camera layers and off-camera shadow casters cross as packed compact records
(mesh key, generation, section origin, depth policy; camera layers also carry
flags), rather than per-instance records. The
frontend retains compact entries on the armed shader route. Described resident
sections become scene entries; only entries that cannot use that path expand
into ordinary instances. Frames leaving the shader route, vanilla and Fabulous
expand compact terrain before their ordinary paths. Placement uses the frame's
terrain camera; see [retained scene terrain](RETAINED-SCENE.md#current-scene-terrain-on-the-shader-route)
and [`frame/static_terrain.rs`](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/rust/render/worldrender/frame/static_terrain.rs).
A caster whose newer generation has not crossed yet keeps casting with the
acknowledged one. Java keeps no active-instance state for casters.

Ordinary whole frames use a native FIFO worker by default. The queued bridge
decodes and copies the request on the calling thread before Java releases its
arena; the worker then acquires the swapchain image, prepares, submits and
presents the owned frame. Mesh-asset updates and atlas ticks also decode before
queuing and execute in FIFO order. Java keeps at most one frame queued ahead
of the frame being prepared. Non-queued context-registry operations join all
pending work; standalone visibility/shadow queries remain separate. Captures,
screenshots and RenderDoc frames use the synchronous path.
See [queued frames](JAVA-BRIDGE.md#queued-frames) for completion and retirement.
The older [single in-flight fallback](JAVA-BRIDGE.md#single-in-flight-fallback)
still borrows Java request memory until join when queuing is disabled. Include
caller-side decoding and queue waits when measuring the Java/native split;
worker execution alone is not whole-frame time.

Source terrain draws already use one indirect command each, and consecutive
commands with identical bindings merge into one multi-draw. Opaque and cutout
batches are therefore ordered by material mode and retained geometry page
before draws are built; translucent batches keep their order. Entity mesh
sections with identical state, uniforms and instances whose index ranges are
contiguous draw as one range (`PreparedSourceEntityFrame::section_count`).
Twin shadow draws replay the camera draws' indirect commands, so they stage no
second copy of the instance records. Source resource sets (`TerrainSourceOwnedResourceSet`)
are immutable and `Arc`-shared: per-draw material preparation clones and compares
them, so keep them cheap to clone and do not add mutable state.

Cacheable entity models use ABI 71 rigs: Java supplies local-space part assets
and raw poses after `setupAnim`; Rust composes the hierarchy and expands ordinary
part instances. Java retains animation, texture/material choice and foil clocks.
Armor/trident glint reuses rig parts; per-topology admission and cached upload
proof reduce repeated checks. First-person, uncacheable dynamic textures and
specified diagnostics retain Java-posed geometry for the same native renderer.
See [Java Bridge](JAVA-BRIDGE.md) for the three-semantic-frame retirement delay,
which is not a completion fence, and the separate queued/pipelined lifetimes.
The empty Citadel proxy remains unsupported ([#803](https://github.com/HungLo2020/MattMC/issues/803));
[#819](https://github.com/HungLo2020/MattMC/issues/819) tracks the source-predicted
orb-boundary error when a rig changes the mesh stream length.

The shared mesh instance stream is bound into every mesh resource set, so
growing it rebuilds them all. It grows to at least twice its previous capacity;
growing to the exact requirement while terrain streamed caused ~30 ms frames.

Distant Horizons builds its render list on the render thread without
`LodQuadTree`'s lock while the tick thread can recenter the tree. The node
iterator therefore skips root positions that left the tree after it captured
them; an out-of-bounds root previously crashed the client during world load.

## Resource ownership and retries

Camera-pass terrain visibility is Frozen's Sodium search, ported exactly to
Rust: [`chunk/section_graph.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/chunk/section_graph.rs)
(occlusion BFS waves, angle and outward masks, distance cylinder, ±9.125
frustum boxes, the nearby pass, and tree traversal when the camera section is
unbuilt). The graph also owns the terrain source's bookkeeping
([`section_graph/source.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/chunk/section_graph/source.rs)):
ready columns, which sections need a build, are urgent, in flight or stale,
readiness for the loading gate, block-entity section lists, each section's
animated sprite ids, and the visit set entity culling tests.
[`RustGalWholeFrameTerrainSource`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java)
reports `ChunkTracker` column events (with a non-air section mask), block
edits, dispatched and finished builds, and accepted build flags through a
standalone handle
([`RustSectionGraph`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/RustSectionGraph.java)),
which is outside any bridge context so selecting never joins a pipelined frame.
Java retains meshing worker dispatch, build/sort/atlas inputs, asset publication
and reload staging, and the `BlockEntity` and sprite objects named by Rust's
section keys or IDs. Published layer identities and their graph-sync queue
now live in the native terrain registry described below.
The graph's needs-build/urgent marks are node bits; visible slots use visit
stamps and animated-sprite lists are interned. These bookkeeping changes do not
move world/entity semantics or resource-reload publication into the graph.

The whole-frame drained receipt describes the camera traversal, rather than
every loaded section. An offscreen block update keeps its rebuild mark but
does not invalidate that receipt; when the camera visits it, the ordinary build
queue must drain before readiness can pass. Visible edits still invalidate
readiness immediately, including when the build queue is already busy. The Java
[`TerrainReadinessInvalidationTest`](https://github.com/HungLo2020/MattMC/blob/master/src/test/java/net/vulkanic/world/TerrainReadinessInvalidationTest.java)
exercises both cases against the native graph and checks that an offscreen edit
is requested after camera relocation.

The real-world DH capture observer checks live execution every frame, but
writes the full diagnostic metadata on stage transitions. The settled-work
gate supplies periodic progress and the screenshot supplies the final receipt.
Serializing the retained terrain history every ready DH frame can consume the
capture's time budget; a timeout in another valid save must be investigated,
rather than treated as a requirement to use the historical reference save.

- Visible sections are visited sections that are built with geometry.
- Ordinary frames take their static terrain from the graph
  ([`chunk/terrain_selection.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/chunk/terrain_selection.rs)):
  Java requests one sync from the Rust publication registry into the graph,
  and Rust emits the compact camera layers (graph BFS visit order, translucent
  back to front), shader shadow casters and the frame's animated sprite ids (each
  once) in the frame records' native layout. Java copies these records without
  rebuilding each section's record and does not read the visits. The layer
  fingerprint receipt is computed only when terrain diagnostics are active.
  Diagnostic, fault, reload, explicit per-record and readiness-receipt frames
  keep the Java producer, which asks the search to copy its visits. The
  implementation author's earlier `313e7a8a` report compares byte-identical
  records over 1,800 frames per mode; it predates later ordering/bookkeeping
  changes and was not rerun by this review.
  [Selection eligibility and handoff](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java#L4921-L4979)
- A finished build's layers are decoded and assembled in Rust by one call
  ([`terrain/intake.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/terrain/intake.rs),
  [`terrain/assembly.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/terrain/assembly.rs);
  C export in
  [`bridge/world/terrain_intake.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/world/terrain_intake.rs)).
  - **Decode:** compact positions, colour/AO, light, copied-atlas UVs, segment
    normals, the canonical block identity and mid-block words, plus the
    static-terrain audit's fault injections.
  - **Assembly:** one u16 index range per vertex segment (opaque/cutout). For
    translucent layers, the build sorter's order is made global if
    facing-local, unsupported fluids are omitted, water is classified and its
    material type set, and ranges are split by material/texture.
  - **Identity:** the atlas-scoped `mesh_key` and the content hash
    `mesh_generation` (an identical rebuild keeps it).
  - **Verification:** the author reports bit-for-bit comparisons against the
    former Java code: decoding on 1,500+ layers in vanilla/shaders and assembly
    on 1,500+ shader layers with no mismatches. The temporary comparison was
    removed with the Java assembly. Thirteen checked-in assembly fixtures
    include ported Java translucent contracts; `cargo test --lib terrain::`
    targets the native definitions. This source review did not run those tests
    or inspect the unbundled comparisons.
  - **Vertex staging:** Rust keeps the vertices
    ([`terrain/staging.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/terrain/staging.rs)),
    and Java's asset record holds only their count
    (`VulkanicGalBridge.StagedWorldMeshVertices`). The asset update flags the
    record (`reserved0` bit 0) and Rust copies the staged vertices of that key
    and generation, so a rejected update can retry. Java discards the staged
    entry once the upload is acknowledged, or when the layer is removed or
    rolled back. The map holds one generation per mesh key, capped at 32,768
    keys; it has no separate byte cap. If a new key cannot be staged, Java
    retries assembly with copied vertex output. A missing or superseded
    generation rejects the asset update; it must not silently use another
    generation's vertices. The fully omitted translucent result can leave a staged entry
    when no prior asset exists to remove; [#821](https://github.com/HungLo2020/MattMC/issues/821)
    tracks that source-derived cleanup gap, without an observed runtime leak claim.
    - Diagnostics that read vertices in Java get a copy instead: faults,
      texture probes, the parity appearance trace and detailed terrain
      diagnostics. `-Dmattmc.dev.forceTerrainVertexStaging=true` bypasses only
      the detailed-diagnostics condition; faults, active probes and the appearance
      trace still require copied vertices.
  - **Publication rows:** Rust's registry
    ([`terrain/publication.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/terrain/publication.rs))
    holds each section's published solid, cutout and translucent mesh (key and
    generation, and whether the translucent layer is camera-sorted). It also
    rejects a mesh key that another section layer already publishes.
    - A row switches when Java registers the layer, not when its upload is
      acknowledged; Frozen parity records that timing.
    - The camera section graph takes the rows that changed since its last
      selection in one call
      ([`bridge/world/terrain_publication.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/world/terrain_publication.rs)),
      or every row after a reset, a reload swap or for a new graph. The sync
      holds the publication mutex while applying reset/rows. Its change queue
      is shared; it is not an independent subscription for each graph.
    - Java's off-camera shadow candidates read their rows from the registry
      in one call per frame.
    - Java (`RustTerrainPublication`) still calls the registry when it
      registers, removes or reload-swaps a layer, and retains its asset objects
      and acknowledgement bookkeeping. The registry stores identities, not
      payloads or GPU-completion receipts. See the [standalone contract](JAVA-BRIDGE.md#terrain-publication-registry).
  - Java receives copied index bytes/range records and a receipt, and still
    publishes the asset (residency, upload acknowledgement, reload staging),
    then drops the payload once Rust acknowledges the upload (translucent
    included). It supplies sorter output, atlas identity and water sprite
    rectangles; native assembly performs the water/material classification and
    rewrites. This supersedes the `7a6009f8` Java water/index assembly boundary.
    [Intake handoff](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/src/main/java/net/vulkanic/world/RustTerrainIntake.java).
- Rust lists the build requests in visit order, block-edit rebuilds first and
  sections already in flight skipped. Java dispatches them while in-flight
  builds stay below twice the worker count, and asks Rust whether each
  finished build is stale (edited, reloaded or unloaded meanwhile).
- All-air sections of a ready column are built as empty at once, as in Frozen,
  so the search crosses them immediately.
- Shader shadow casters are built geometry sections the camera did not select.
  The shadow pass never schedules builds; Rust applies the shadow-pass test.
- Entities follow Frozen's Sodium entity culling
  ([`RustGalEntityCullingHook`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/RustGalEntityCullingHook.java)):
  when the Sodium entity-culling option is enabled, a camera-pass entity whose
  expanded culling box touches no section visited this frame is rejected before
  the ordinary frustum test. Rust answers the box test from the search's visit
  stamps. Glowing/name-visible entities, very large boxes, boxes outside level
  height and checks without an active frame search bypass this additional
  rejection. The hook declares that it does not affect the shadow pass; Java
  still extracts retained entities and their geometry.
- Without a shader pack, block entities are extracted as Frozen's Sodium does
  (`RustGalWholeFrameTerrainSource.forEachVisibleBlockEntity`): Rust lists the
  visited built sections with culled block entities, then every built section
  with global ones in first-build order. Moving pistons arrive the same way.
  Shader frames still scan every loaded chunk in range, because the shadow pass
  takes its block entities from that list; unavailable-search cases also keep
  the fallback. The author reports vanilla A/B 455→516 FPS; no dedicated
  block-entity-selection regression was added in this interval. See [the route gate](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/java/net/minecraft/client/renderer/LevelRenderer.java#L2051-L2064).

Keep the graph's behaviour identical to Frozen: its unit tests in
`section_graph/tests.rs` pin each rule, so run
`cargo test --lib section_graph` in `src/main/rust` after any change. Also run
`cargo test --lib terrain_selection` for compact ordering, layer flags,
empty/unbuilt exclusion, animation selection, shadow bounds and mesh-row removal.
These numerical/record tests do not establish image or gameplay acceptance.

The integration remains bounded. The retained Java producer limits camera
sections to 4,096 and throws on overflow; ordinary native selection bypasses
that Java snapshot. Both producers limit off-camera shadow candidates to 12,288
sections, retaining the nearest before restoring key order. Packed terrain
entries share the 65,536-entry whole-frame budget with admitted mesh and orb
instances; this counts layers/instances, not sections.
[Current selection bound](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/src/main/rust/render/chunk/terrain_selection.rs#L203-L227)
· [Combined frame budget](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L18027-L18038)

A port of the selection rules does not establish unbounded or end-to-end Frozen
equivalence. Region draw order, Iris's non-culling frustum and complete scene-owned
visibility remain work in the [retained-scene plan](RETAINED-SCENE.md#phases).
The earlier Java-path [capacity constants](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java#L109-L117)
and [overflow handling](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/java/net/vulkanic/world/RustGalTerrainRenderer.java#L1025-L1067)
remain historical reference for that producer.

Source shadow terrain applies its existing dimension, distance and light-frustum
policy before constructing batches. Validate every shadow candidate's asset
generation, section and sorted topology even when it is culled. Selected batches
retain original frame indices; their cache includes those positions and
identities. Repeated mesh keys cache the complete plan with all ordered instance
identities, including culled draws, then filter a copy. This preserves first-seen
ordering and translucent boundaries without rebuilding unchanged topology.
Camera-dependent complete selections remain uncached. All plans share the
existing four-entry CPU cache and mesh/texture invalidation rules.
The source plan retains its final frustum check. See
[`geometry/batching.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/geometry/batching.rs)
and
[`source/frames/terrain.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/frames/terrain.rs).

Source terrain multi-draw binds the instance storage stream at offset zero;
`firstInstance` addresses 80-byte records within that stream. Pack these records
on their stride, while aligning each absolute uniform descriptor offset and
direct-draw instance descriptor offset to 256 bytes. Equal same-frame uniforms
share immutable packed CPU blocks and their existing upload offsets. The packed
block memo retains at most 64 entries and resets at the next frame; changed
uniform values or texture transforms get separate blocks. Keep each draw’s
instance payload independent and preserve owned bytes at GAL upload boundaries. Keep the conservative descriptor-aligned
per-batch capacity reservation separate from the packed upload size. The
frame-stream allocator in
[`geometry/arenas.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/geometry/arenas.rs)
retains bounded capacity, frame epochs and completion-gated reuse. Changing
record packing must preserve descriptor alignment in mixed direct/multi-draw
allocations as well as exact staged bytes and indirect instance indexing.

Keep upload data and its owner retryable until submission succeeds. Mesh
replacements must validate and submit before publishing the new CPU state or
retiring old assets. Java drops every static-terrain vertex/index payload
(translucent included) once Rust acknowledges its upload; Rust orders
translucent geometry per frame from the build order, so Java sends no sorted
indices (the bridge's sorted-index list is always empty). Atlas recovery replays accepted updates in
order; rejected uploads must not advance animation clocks or lose pending work.
At resource-reload commit, Java also drops the staged layers' CPU payloads after
checking every staged generation was uploaded; earlier acknowledgements could
only release published layers. This does not cover the omitted-layer staging
case tracked by [#821](https://github.com/HungLo2020/MattMC/issues/821).

Java DH preparation retains material and contributor provenance in
[`ColumnRenderSource`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/ColumnRenderSource.java).
Replacing a column clears its dense sidecars and removes sparse entries by that
column's contiguous vertical index range. Keep this work bounded by the column
height: scanning section-wide maps for every column makes fresh section builds
quadratic as metadata accumulates. Other columns and the shared material identity
table must survive replacement. The focused
[`ColumnRenderSourceSemanticMaterialTest`](https://github.com/HungLo2020/MattMC/blob/master/src/test/java/com/seibel/distanthorizons/core/dataObjects/render/ColumnRenderSourceSemanticMaterialTest.java)
covers column boundaries, heights and repeated clearing; it does not establish
gameplay FPS or Frozen visual parity.

The three-axis debug crosshair starts in camera space, unlike ordinary world
lines. Its Java producer in
[`RustGalWorldPrimitiveRenderer`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java)
applies the inverse of the current frame's view before enqueueing its endpoints
in the shared camera-relative world-line stream. Rust then applies that view
once during drawing. Omitting the inverse cancels the axes' camera rotation and
can move the crosshair off-center or behind the camera. Keep the view snapshot
and all six segments under the frame lock. The focused
[`DebugCrosshairCameraTransformTest`](https://github.com/HungLo2020/MattMC/blob/master/src/test/java/net/vulkanic/world/DebugCrosshairCameraTransformTest.java)
checks yaw, pitch, GUI scale and view matrices containing camera effects against
Frozen's camera-space axis directions.

DH asset preflight runs after the real quadtree selects visible generations.
Keep those selected assets resident through submission and presentation. The DH
column ledger excludes their keys from replacement updates while the pending
visible list exists; the coordinator flushes again after presentation. Unrelated
columns still use the bounded publication budget, and repeated rebuilds retain
only the latest pending snapshot. Changing a generation on old segment indices
is unsafe: a replacement can change opaque, transparent and water stream
topology.

The ledger
([`render/dh_collector`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/dh_collector))
owns column state transitions and packed payload storage:
- current, pending, in-flight, published and retiring generations
- each generation's packed vertices, copied once from Java when it is recorded
- owner leases
- lifecycle resets
- the visible segments of the frame being prepared
- route receipts

The coordinator's flush calls `mattmc_vulkanic_gal_world_lod_collector_flush`
([`render/bridge/dh_collector.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/dh_collector.rs)).
It selects the update, builds the frontend assets from the ledger's payloads,
and applies them; Java then acknowledges the update under its collector lock.
A failed apply releases the selection. Updates that carry exact material
provenance (exact-atlas and source-execution diagnostics) still go through
Java's packed `updateWorldLodAssets`, because the provenance needs Java's
model resolution. Native publication shares immutable payloads through `Arc`
while selecting/in-flight tracking, but decodes packed bytes into owned frontend
vertex vectors; diagnostic payload fetches also copy. It removes the ordinary
Java repacking round trip without making publication zero-copy.

Retention targets remain 512 columns and 64 MiB including tracked provenance.
Trimming stops when only protected columns remain, so these are soft targets,
not a bound on live DH geometry. Publication selects at most 16 columns and a
16 MiB target per update; the first oversized column can exceed that byte target.
Pending visible-key and segment lists each have a 16,384-entry bound. Keep these
limits separate from frontend admission and GPU-resource retirement. See
[ledger selection and trimming](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/src/main/rust/render/dh_collector/mod.rs).

Each frame, DH's quadtree walk (`RenderBufferHandler.buildRenderList`)
collects the key and generation of each drawable container it reaches, and
batches their lifecycle, publication and visibility work in one ledger call,
`collectVisibleFrame`. This replaces per-column round trips, not every ledger
call in a frame. That call:
1. drops containers whose generation is no longer current, then requests
   publication of the unpublished columns, in walk order;
2. sorts the keys near to far, keeping walk order for equal distances;
3. records the frame's visibility;
4. admits the visible segments.

The native sort preserves walk order at equal Manhattan distance from the
quadtree center, including Java integer arithmetic. Java still owns the quadtree
walk and frustum decisions. With exact-atlas coverage, `LodRenderer` still
admits each column through `recordVisibleMaterialColumn`; begin/consume,
route selection, generic callbacks and render parameters remain separate.
`consumeVisibleFrame` publishes a native CPU frame identity/lifecycle/count only
when the prepared frame is enabled and its Rust route is selected, then clears
the pending segments and frame together. ABI 74 decode resolves and retains the
immutable list before queueing; explicit readback/legacy inputs still copy.
The collector ring holds at most three resolvable snapshots, but decoded `Arc`
owners can outlive eviction/reset. Changed visible sets allocate new storage;
this is not a total live-memory cap or an allocation-free frame path. See
[native DH ownership](RETAINED-SCENE.md#native-dh-visibility-frame-ownership).
A lifecycle-tagged completion receipt is distinct from this handoff. The [ledger fixtures](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/dh_collector/tests.rs)
cover selection, ordering and stale generations in the batched walk; they do
not exercise the actual native flush export's apply-failure/retry path or prove
visual parity.
Overflow/rejection recovery also needs separate checks that native effects and
Java provenance sidecars stay coherent; source fixtures alone do not establish
rollback for every failed mutation.

[`DistantHorizonsSemanticCollector`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/DistantHorizonsSemanticCollector.java)
keeps the material provenance, the frame's render parameters and the capture
diagnostics. It applies each ledger call's effects to its provenance maps.
Diagnostics and probes read payload copies fetched from the ledger on demand.
Packed vertex admission checks restricted material/normal bytes directly;
unsigned 16-bit position/light fields need no Java vertex reconstruction.
Unpacked inputs retain their field checks. This changes validation allocation,
not topology, material provenance or DH generation policy; see the
[allocation constraints](GAMEPLAY-PERFORMANCE.md#image-and-dh-allocation-constraints).
The ledger reproduces Java's `LinkedHashMap` orders, including the column LRU's
access order, because publication, retirement lists and eviction depend on
them. See also
[`RustGalFrameCoordinator`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java).

Persistent GUI decode-cache hits now share owned vertex/index arrays through
[`SharedVec`](https://github.com/HungLo2020/MattMC/blob/7a6009f84d966263293f864933f4da06b1823dfa/src/main/rust/render/guirender/mesh/model.rs#L178-L235).
Cache misses still copy caller input, shared mutation/consumption can clone,
and preparation can transform/copy geometry. Decode sharing, prepared-geometry
reuse and accepted GPU-range reuse retain separate bounds and lifetimes.

Release descriptor sets and cached pass bindings before their textures,
samplers or residency buffers. Cache eviction must account for prepared
commands as well as submitted work. GUI stream reservations remain owned
through command preparation and submission; frame-local reservations must be
released when preparation fails. Shader reloads retire bindings and pipelines
only after the replacement source generation is accepted.

At `26d6beaa`, queued DH material-route receipts gained the collector lifecycle
captured at consumption; mismatched receipts are dropped and counted after a
reset. The same change drops parked fullscreen plans before runtime inputs.
`7f256b53` broadens consumer-cache release for teardown/runtime replacement and
adds [GAL retirement](VULKANIC-GAL.md): a still-referenced resource waits for its
last dependent, then normal submission retirement protects in-flight use.
This does not bound a cache that never releases its dependents. The
[seven-scenario lifecycle gate](RENDER-VERIFICATION.md#lifecycle-gate) and
[author's report](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/PROGRESS.md) provide scoped transition evidence;
they do not resolve every prior native crash or prove long-run resource bounds.

Item and armor foil extraction reuse bounded immutable CPU texture copies in
[`StandardFoilTextureCache`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/StandardFoilTextureCache.java).
It retains at most two encoded assets (four MiB each), including the selected
resource's blur/clamp metadata and single mip level. World asset reload clears
the cache so changed pixels, metadata or missing resources are read again.
Copies hold no resource-manager, pack, stream or GPU objects. Native texture
publication and acceptance still follow the world asset generation.

DH containers acquire a CPU lifetime lease atomically with semantic publication.
Identical replacements can share a generation: closing one container retires it
only when the last owner closes. Changed generations and world/resource resets
invalidate old lease groups, so late closes cannot erase replacement data.
The collector's snapshot cache targets must not retire a live container merely
because its column is absent from the current visible list; transition siblings
and parents still need readiness. Their working set follows the DH quadtree
lifetime. Check memory during large-radius streaming and repeated transitions;
the cache targets are not a hard cap on live geometry. These leases own CPU
lifetime only; Rust and the GAL retain all native resource ownership.
[`LodBufferContainer.close`](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/LodBufferContainer.java)
now retires only its lease: a never-published container cannot remove a newer
column by position. [`LodRenderSection`](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/java/com/seibel/distanthorizons/core/render/LodRenderSection.java)
serializes close with finished-build installation; a late container is closed
instead of adopted. The lifecycle fixtures cover builds arriving before and
after close, not arbitrary concurrent scheduling or a long-run memory bound.

DH quad layers follow reduced-color opacity. Fully opaque leaf colors stay in
the opaque stream; water keeps its translucent layer even at packed alpha 255.
Preserve this rule in the CPU builder and its semantic packets: moving opaque
foliage into the late `dh_water` writer changes pack lighting and fog, even when
the copied geometry and material category are otherwise correct.

With a shader pack, the DH opaque range (reduced-color, exact-atlas and generic
draws) and the late `dh_water` range are each recorded as one ordered pass
(`append_ordered_batch` in
[`lod/source.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/lod/source.rs)).
The ranges retain draw order and their snapshot boundary; the author reports
shaders+DH 193→215 FPS after replacing about 200 single-draw translucent passes.
The changed LOD regression exercises the existing opaque identical-draw case,
not a new late-translucent-order assertion.

Reduced-color DH column geometry lives in shared device pages
([`lod/residency.rs`](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/worldrender/lod/residency.rs),
using the completion-gated page allocator in `geometry/arenas.rs`):
- **Layout:** a column takes one vertex range and one index range; segments
  address them by `vertex_base` and byte `index_offset`. Exact-atlas residency
  still owns separate per-segment upload buffers.
- **Uploads:** each pending upload transaction uses one staging buffer and
  `CopyBufferRegion`, with a barrier on each side of every written page.
  Confirmed pages transition from their read state; unconfirmed pages use
  `Undefined`. Segment vertices copy directly into the transaction payload,
  which moves into `HostWriteBuffer`; indices still use an assembled payload.
  This removes intermediate copies, not the staging/GPU copy.
- **Releases:** replaced or reconciled columns queue ranges against
  `gal.next_submission_id()`; reclamation waits until that submission id is
  complete. A discarded transaction returns unsubmitted ranges immediately.
  Reclaimed empty pages go to `gal.retire`, so dependent resource sets and
  in-flight submissions can delay actual destruction.
- **Bindings:** with packed uniforms, each built-in pass owner caches one
  geometry/frame set per vertex page, using a dynamic uniform offset per draw.
  Other modes retain per-draw keys. Page sets are pruned after more than 120
  unused owner-pass frames; frames that skip that owner do not advance the
  counter. This delay is not a memory bound or completion fence.
- **Limits:** a column's vertex stream must fit the admitted buffer limit, and
  its `vertex_base` must remain below 2^24 for exact f32 representation. The
  allocator uses 128 MiB pages or larger single-allocation pages; paging does
  not cap total live GPU memory.

The [per-page binding path](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/worldrender/lod/passes.rs)
and [indirect first-instance enablement](VULKANIC-GAL.md#changing-the-gal)
prepare further batching. DH source passes still record individual
`DrawIndexed` commands in draw order; shared pages do not establish DH
multi-draw, zero-copy publication or full retained-scene ownership.

Generic boxes group by four `(SSAO, translucency)` key classes and pack fixed
uniform blocks; Java uses the [packed-buffer transport](JAVA-BRIDGE.md).
DH generic groups (clouds, beacons, API objects) are retained in Rust
([`bridge/world/dh_generic_groups.rs`](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/src/main/rust/render/bridge/world/dh_generic_groups.rs#L91-L143),
introduced in ABI 72, with native cloud fields added in ABI 75):
- `GenericObjectRenderer` registers a group's boxes, in group coordinates,
  only when DH marks it changed (`triggerBoxChange`), its box count changes,
  or it is new. That's the same contract DH's own renderer used for re-uploads.
- Each frame sends one instance per active group (light, shading, SSAO, plus
  an API origin or native cloud owner/epoch). The whole-frame decode resolves
  the origin and expands camera-relative boxes as `(box + origin) - camera`
  in f64. Cloud motion alone no longer resends about 2,600 boxes per frame;
  [native cloud preparation](RUST-DH-CLOUDS.md) also avoids round-tripping origins.
- An instance whose group generation Rust lacks is skipped and raises a resend
  flag. Java then re-registers every group on the next collection. Removed
  groups are released during collection, and clearing the Java renderer releases
  its registered groups.
- The process-wide CPU registry admits at most 4,096 groups and 65,536 retained
  boxes, separately from Java's 10,000-box per-frame producer limit. Registration
  requires nonzero id/generation and valid finite bounds/materials. It copies
  boxes; each frame still allocates/expands camera-relative requests and passes
  ordinary frame validation. The registry is not a per-submission snapshot or a
  GPU completion fence. Preserve registration/release ordering relative to
  decode, especially on the optional worker-decode route.
- The source group allocator starts at zero, which current retained registration
  rejects. [#820](https://github.com/HungLo2020/MattMC/issues/820) applies when
  that first group reaches active collection; inactive or cancelled groups need not
  trigger it. This source mismatch was not reproduced in a client run.

Java retains API callbacks, active/cancelled-group selection, dirty notifications,
ordinary API origins, light and shading. Built-in cloud motion/placement/culling
and color-change history use their separate Rust CPU owner; Java still supplies
world color and shared API boxes. Same-count geometry edits must trigger the existing
change notification. These retained DH boxes do not implement retained entities
or block entities in the [scene plan](RETAINED-SCENE.md#phases).

[Ordinary DH source pack sets](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/worldrender/lod/source.rs#L741-L761)
retire only when their recorded resource generations bind a released role.
Their draw sets, pipelines and frame rings stay intact. The [shared teardown](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/worldrender/source/programs/teardown.rs#L186-L217)
still destroys exact-atlas source resources wholesale, so this is not universal
role-filtered DH retirement.

Built-in DH materials sample the copied skylight coordinate at its original
lightmap texel center, including dark rows for covered or submerged geometry.
Frozen OpenGL is the semantic baseline. Its Java Vulkan-only brightness fold
must not be copied into Rust reduced-color or exact-atlas vertex lighting.

GUI item-target eviction uses every item identity and extent in the ordered
frame, before recording individual items. Evicting against one item at a time
discards cached pixels needed by later items and causes repeated rasterization.
Keep accepted static rasters across frames; identity, extent and asset-generation
changes invalidate them. Pending command uses still prevent eviction.

Flat and standard 3D GUI items both carry a raster identity (`GuiItemCacheRecord`)
from their topology cache key. Static items reuse their raster. Standard-foil
items are marked animated: they re-raster each frame, but their cached quads
keep stable identity (encoded once into persistent native memory), and the
foil clock is refreshed per frame (`withCurrentFoil`) and applied on the GPU as
a per-draw UV transform. Foil and plain variants of one model use distinct
identities, so they never share pixels.

GUI mesh geometry is content-keyed (raster key plus geometry fingerprint) and
stays resident across frames: a draw re-uses a range once an accepted
submission (or the same transaction) wrote it, and a completed range becomes
eligible for release after two idle mesh transactions. A range never proven written that pending commands still
reference is retired rather than rewritten
([`mesh_items/geometry.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/guirender/frontend/mesh_items/geometry.rs)).

The [transaction counter](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/guirender/frontend/recording/target.rs#L206-L235)
does not advance on the no-mesh/no-tile/no-atlas fast path; two displayed frames
alone therefore do not guarantee reclamation. Fixed-capacity pressure can
reclaim completed ranges earlier, while pending commands still protect them.
Explicit non-foil meshes of at least 64 vertices also use a separate
[prepared-geometry memo](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/guirender/mesh/prepare.rs#L34-L110):
at most 256 entries, with age measured in preparation calls; above 128 entries,
it prunes entries unused for more than 64 calls. Placement and ordering are
refreshed from each request. These bounds do not prove whole-renderer memory
stability.

World geometry range release retains bindings to a still-live vertex page.
[Only emptied pages' bindings retire](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/worldrender/assets/stores.rs#L1243-L1266)
before page trimming; this reduces descriptor churn without changing ownership.

Prebuilt GUI commands must keep the `GuiSubmitStats` produced while recording
through route selection: source preparation can arm the selected-source route
for that same frame. At [commit `78e8e04`](https://github.com/HungLo2020/MattMC/commit/78e8e0423084f010bb47e36132550619b37644c2),
[`submit_whole_frame_with_gui_stats`](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/submit.rs#L69-L92)
threads those declarations into the
[armed source submission](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/source/submit.rs#L347-L365).
Replacing them with empty stats would reject legitimate private GUI targets.

[Menu blur](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/guirender/frontend/recording/blur_boundary.rs#L237-L261)
declares its ping-pong scratch render targets in `owned_intermediate_targets`.
[Custom post effects](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/guirender/frontend/post_effects/custom.rs#L368-L406)
report the private intermediate targets their passes write, including the
[color-only execution target](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/guirender/frontend/post_effects/custom.rs#L228-L275)
used for depth-sampling effects when it differs from the main target. External bindings are not reported as GUI-owned
intermediates. The [whole-frame GUI paths](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/submit.rs#L1877-L2165)
merge these declarations with the recorded GUI stats before source validation.
The [validator](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/source/submit.rs#L2043-L2095)
still rejects undeclared targets and a second presenter; this is no general
permission for GUI work to write arbitrary targets. See the scoped coverage
and live-check limits in [Render Verification](RENDER-VERIFICATION.md#1-tests).

Direct DH composition tracks snapshot image usage separately from copied
contents. Its early resolver establishes `ShaderRead` even when far fade is
off and no vanilla pixels are copied. Later vanilla fade snapshots must
transition from that same-frame state. Confirm persistent usage only after
accepted submission; resource replacement and reset discard it. See
[direct recording](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/vanilla/recording.rs).

## Shader controls at startup

Java's [configured-pack disk-state memo](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/java/net/vulkanic/shaderpack/RustShaderPackSourceCollector.java#L168-L198)
reuses a result within the same nonzero frame epoch, or across epochs while its
age is below 250 ms. Same-epoch reuse can exceed that interval, and there is no
end-frame epoch reset. Epoch zero always rechecks; in-process settings writes
invalidate the memo, and the vanilla post-effect lookup is uncached. Treat
250 ms as a polling throttle, not a maximum external-edit detection latency.

Shader selection, pack options and key bindings remain Java-owned configuration.
`Minecraft` initializes that configuration before constructing `Options`, which
must include registered keys before loading saved mappings. Client ticks process
the shader controls; changes request a fresh source snapshot through
`RustGalFrameCoordinator`. Rust still owns shader execution and GPU resources.
Do not remove these configuration hooks when removing Java renderer lifecycle
code. The integrated distribution also needs a version-label fallback when no
separate Iris mod container exists.

World entry and extent changes prepare source prerequisites through a temporary
Rust-owned offscreen GAL target. Its normal graph submission initializes depth
history, lightmaps and voxel resources without writing or presenting the acquired
image. Run that graph even when terrain and LOD streams are still empty: depth
snapshots and initialized shadow attachments are admission prerequisites for
sky/hand-only entry frames too. An empty draw list still executes the graph's
attachment clears, depth copies and forward/composite work.
If source admission rejects contracts or assets and produces no resource
snapshot, keep the source unarmed and retain that rejection. A missing snapshot
cannot participate in depth merging or final-target correlation; treating it
as an admitted transaction can turn an unsupported pack into a world-entry crash.
Admission then rebuilds the exact-frame snapshot for the acquired target;
the selected source graph writes the visible world and GUI through the existing
frame owner. Preparation retires its cached bindings and attachments on success
or failure. Source discovery also covers world frames before terrain appears.
The eager Java atlas publisher stages available normal/specular maps alongside
the base atlas, rather than waiting for a terrain section build. Admission must
include the complete source graph's declared color outputs: their bootstrap and
writers provide them within the selected submission. Live entry validation is
still required; this preparation does not make unsupported frame families
admissible. See
[`source/submit.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/submit.rs).

The selected `gbuffers_textured` material writer applies the resolved Iris
alpha test to its primary output after the pack fragment runs. The default
threshold is 0.1; `alphaTest.gbuffers_textured` can override or disable it.
Opaque particle blending still requires this discard: writing alpha zero does
not preserve the scene or its depth. Keep this source policy in
[`contracts/material.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/contracts/material.rs)
and the prepared shader, outside the game-neutral GAL.

On the direct, non-G-buffer route, material quads tagged
`WORLD_MATERIAL_SOURCE_PARTICLES` draw in a late pass after translucent terrain,
the DH vanilla-fade composites and receiver shadows, following Frozen's separate
particles frame pass
([`vanilla/recording.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/vanilla/recording.rs)).
A fade composite reconstructs distance from the vanilla depth/colour snapshot;
a particle drawn before it is faded as if it were the terrain behind it.
`direct_dh_fade_composites_run_before_particle_draws` checks command order for
single- and double-pass fading, not particle pixels or temporal stability.
Clouds and weather remain in the early material pass. The change does not
relocate model-mesh particles, Fabulous/G-buffer forward materials or selected
shader-source passes; those routes need their own evidence.

Named-source terrain packs one immutable CPU uniform block per used material
pass and one for shadows in each frame. The block borrows its exact Rust source
program, keeping the ABI immutable, and rejects use with another program or
frame. Batches retain independent model transforms and colors. Packing remains
lazy, so an empty world frame does not require unused terrain/shadow uniforms.
These blocks carry no GAL handles or frame-slot identity; stream allocation and
completion still belong to the existing frame transaction. General preparation
helpers continue checking semantic values when callers vary uniforms per batch.
See [terrain frame preparation](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/frames/terrain.rs).

Single-color opaque/translucent terrain, textured material, entity and hand sources retain their
declared pack color slot and original GLSL lighting. Discovery must not require
Complementary helper names or invent auxiliary targets for those programs.
Ordinary terrain resolves `alphaTest.gbuffers_terrain` with its selected stage's
effective defines. Without an override, opaque terrain has no alpha test and
cutout terrain requires primary output alpha greater than 0.1, matching Frozen's
Sodium terrain passes. An explicit disabled or `GREATER x` override applies to
both material classes. The prepared fragment wraps the actual `main` definition,
so early returns still reach the test. Definition parsing preserves signature
comments and spacing and skips prototypes and commented functions; DH depth
insertion uses the same brace-aware parser.
See [`terrain/alpha_test.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/contracts/terrain/alpha_test.rs)
and [`lowering/text.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/lowering/text.rs).
Translucent terrain remains a separate source pass with source-alpha-over
blending. Its selected property branches resolve with that stage's effective
defines. The default alpha test is disabled, matching Frozen's ordinary
translucent terrain; `GREATER x` applies to output zero after the pack runs.
Keep explicit disabled alpha state distinct from a missing raster contract.
The paired lowerer links legacy `varying` declarations to explicit locations,
ignoring declaration comments and reserving one location per matrix column;
scalar uniforms, samplers and the native compiler still gate preparation.
Uniform/sampler discovery and rewriting use the same comment-free lexical view,
so annotations cannot hide an active binding or make a declaration count as a use.
The compact textured stream can remove unused, known terrain-attribute
declarations from shared headers. Actual reads of those attributes still need
an explicit semantic input; substituting zero would change the pack's behavior.
The compact material contract supplies `mc_Entity=(0,0,0,1)`, matching the
disabled generic attribute observed on Frozen's particle draws. This is a fixed
source input, with no new vertex lane or borrowed GL state. Mid-UV, tangent and
mid-block reads remain unsupported; entity/hand/terrain streams keep their own
per-vertex entity semantics.
Terrain, entity and hand source vertices are 64-byte packed records
([`TERRAIN_SOURCE_VERTEX_BYTES`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/programs/lowered/terrain.rs));
the vertex preamble decodes them to the eight semantic vec4 lanes. This is a
Rust-owned source stream, not a change to the Java C ABI or every renderer's
vertex format. Change the packer, GLSL decode, test decoder and direct byte-offset
users (including decal UV and light updates) together.
See [source lowering](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/shaderpack/lowering).

The selected frame orders optional `begin` writers before shadows, then
`prepare` before sky and terrain. These are ordinary Rust-owned fullscreen
passes in the same named-color transaction. Their enabled declarations,
resource bindings, feedback and mip requirements participate in admission;
missing paired sources or lowering failures reject the complete route.
DH frames use separately expanded pre-terrain programs. Before begin, the color
transaction clears every new target and both sides of each clear-enabled warm
target, matching Frozen. Targets declared `clear=false` retain their confirmed
history. The generation owns cached GAL clear passes; warm frames allocate no
clear resources. Fog values remain per-frame inputs and explicit source colors
take precedence. Clears invalidate mip descendants on the affected side, so
sampling them still requires explicit mip generation. Geometry and fullscreen
writers load colors initialized or written earlier this frame. Main depth still
clears. Preserving only slot zero loses packs that write sky elsewhere.
Record opaque outputs immediately
after their pass, before deferred stages snapshot same-frame feedback.
See [fullscreen discovery](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/contracts/fullscreen.rs)
and [frame sequencing](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/plans/terrain.rs).
See [color clears](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/resources/color_targets/clears.rs)
for generation ownership and base-mip transitions. Rejected replacements cannot
advance confirmed history; clear passes retire before their attachment views.

Begin/prepare/deferred/composite/final stages expose unit-quad local positions through the
owned procedural triangle. Their legacy model-view is identity; the projection
maps XY from `[0,1]` to `[-1,1]` and has a zero Z column, matching Frozen's
composite transformer and captured native vertex shader. Local positions do
not inherit the Vulkan sampler-UV row conversion. Normal, color, texture
matrices and unused texture-coordinate sets have explicit composite values;
these constants neither consume world-camera uniforms nor borrow GL state.
The Overworld sky source runs first on an owned octagonal horizon with tiled
Y=±16 planes, then on the expanded vanilla Y=16 disc. The horizon radius is
effective render distance in blocks capped at 256; its vertex color is copied
fog color with alpha 1. The disc receives sky color. Keep these as separate
consumers so self-sampling packs snapshot feedback between draws, and retain
distinct cached pipeline identities for their geometry and uniform contracts.
The horizon remains active when fog hides the disc; the pack's `sky=false`
or `sky=0` directive disables both while leaving celestial discovery separate.
See [horizon geometry](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/lowering/horizon.rs)
for the owned prism and plane construction.
Sun/Moon `gl_Vertex` retains the local unit XZ quad; translation, scale and
Y/X/Z rotations belong to its owned model-view, including fragment-stage
matrix reads. Rotate with the copied vanilla sky clock, independently of
Iris's quarter-shifted `sunAngle`. End sky faces retain their baked local
rotations and camera-only model-view. These inputs use the existing semantic
frame and std140 writer, without Java GPU state. See
[captured input checks](RENDERDOC-INPUTS.md).

Unlit sky and celestial vertex formats expose `(240,240,0,1)` for both legacy
lightmap coordinate sets 1 and 2, matching Frozen's Iris transformer. Both
texture-matrix slots use the canonical lightmap transform; slot 0 retains the
primary texture transform. Lower slot 2 as an alias of the existing owned
lightmap matrix, keeping the two-matrix uniform block and ABI unchanged.

The vanilla lower sky disc carries the `sky_dark_disc` material role and local
bottom-fan vertices. Its Rust shader applies the camera-relative draw transform
and sky fog; a generic black textured material omits that fog and leaves black
regions between cave surfaces. The role has its own fog header and pipeline key;
ordinary material and compact DH-box record offsets stay unchanged. Rebuild
Java and native Rust together when changing this semantic role. See
[the sky material shader](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/vanilla/glsl/sky_material_fragment.glsl).

Sky and celestial fragments derive lower-left screen coordinates from
`gl_FragCoord`; their color/depth target samples use the world source row
conversion, while copied PNGs retain their authored addressing. Composite
varyings already address native target rows. Convert those varyings back to
source screen coordinates only at inverse projection; use the declared `vec2`
interface rather than assuming a spelling such as `texCoord`. Reconstruction
requires this conversion even when the fragment never reads `gl_FragCoord`.
Absolute and viewport-derived integer texel addresses use source rows too,
including aliases in vertex and fragment stages. Convert them at reads of
explicitly bound main/DH depth and named-color targets, using the sampled mip
size. Native fragment texels and image varyings retain their addressing; copied
PNG overrides retain their authored rows. Coordinate/LOD expressions evaluate
once. Keep this policy in
[integer-address lowering](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/lowering/fullscreen_texels.rs);
it adds no frame uniforms or backend resources.
See [coordinate lowering](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/lowering/fullscreen_coordinates.rs).

Each writer selects its own color sampler bindings from the owned targets.
Program-local descriptor ordinals can give opaque, translucent, entity, hand,
textured-material and DH writers different combined handles for the same role.
Replace only the local writer's color subset in its cloned admission table;
retain the original snapshot and all non-color ownership checks.
Feedback history follows the color frame plan
([`frame_plan.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/resources/color_targets/frame_plan.rs)):
self-feedback stages snapshot same-frame writes, and the end-of-frame
current-to-previous copy is skipped for a clear-enabled target whose previous
image is already initialized, because the next frame clears both images first.
Retained `clear=false` history still needs the copy. Mip chains are regenerated
only after a level-zero write, clear or copy invalidates their descendants.
Fullscreen stages additionally choose feedback and mip policy. A geometry
snapshot cannot choose those stages' sampled images. Validate snapshot
generations before excluding the stage-owned color
subset. All other duplicate roles still require exact binding equality.
An absent `DRAWBUFFERS` on `final.fsh` selects its single displayed color via
the owned primary target and normal final-copy path. This default does not
apply to deferred/geometry stages, malformed directives or extra outputs.

Custom `uniform.*` and `variable.*` properties travel with the preprocessed
stage's selected options and source fingerprint. Rust links the active GLSL
uniforms and their dependency closure into a typed expression program, reads
the copied frame semantics and writes the existing std140 block. This supports
scalar arithmetic, comparisons, lazy `if`, selected numeric functions and
`vec2`/`vec3`/`vec4` construction; unsupported active functions remain an admission
failure. It does not borrow Iris evaluator objects or accept an untyped payload.
Keep Frozen's overload order: exact signatures, a final int-to-float cast, then
casts inside arguments. Float-to-int requires `toInt`; `floor`/`ceil` can return
either type. Bound property bytes, parsing, resolution work and graph depth;
reject cycles, missing frame inputs and non-finite results. See
[expression evaluation](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/shaderpack/uniforms/source/expressions).

Built-in celestial positions and `eyeBrightnessSmooth` are derived in Rust from
copied view, sky-time, End-flash and packed-light semantics. Resolve
`sunPathRotation`, `eyeBrightnessHalflife` and `endFlashShadows` from the selected
dimension program set; when an override directory exists, Frozen does not merge
missing base programs into its directives. Cache only the four immutable scope
policies per source generation. Expanded stages remain temporary and bounded.
See [frame directives](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/properties/frame_uniforms.rs).

Update brightness smoothing once per frame, reset it on world/pack generation
changes, and retain both packed-light channels. The derived frame memo must
include pack generation, sky scope, viewport and projection as well as world,
frame, time and view; same-frame reloads or resize preparation must recompute it.
Activation of the shader environment also invalidates that memo. Entity and
hand writers use the derived frame whenever gameplay shader semantics are
enabled, even if the source omits `atlasSize` or render-stage inputs. Keep the
raw-frame path only for disabled fixtures that need no owned derived values.
These values use the existing typed std140 writer and expression inputs, without
borrowing Iris temporal objects. See [frame derivation](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/source/uniforms.rs).

Conventional packs without a MattMC binding manifest use a bounded Rust table of
supported Iris sampler aliases. Geometry samples color targets from slot four;
fullscreen `tex` refers to the first scene color. Resolve conditional custom PNGs
from the selected stage's final defines, keeping sampled assets separate from
color output identities. Geometry and shadow passes share the
`texture.gbuffers.*` group, as Frozen's `GBUFFERS_AND_SHADOW` stage does;
retain each actual shader's defines when selecting conditional properties.
An explicit manifest remains authoritative. See
[protocol bindings](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/resources/bindings/legacy.rs).
Selected `image.` properties can name the existing Rust-owned voxel occupancy,
current/previous voxel light and puddle fields. Resolve both sampler and image
aliases from the actual stage's property branch, including weather, clouds,
entity shadows, glint, outlines, damaged blocks and DH consumers. Validate
format, clearing rules and absolute matching dimensions;
duplicate properties keep only their final value. Unknown image identities and
sampler names alone grant no resource. Resource admission still requires the
owned field; discovery does not create an image or enable a route. `depthtex2`
uses the separate pre-hand depth snapshot. See
[owned image declarations](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/resources/bindings/legacy/images.rs).
The normal pre- and post-terrain chains participate in the asset/admission requirement
closure, alongside terrain and shadow plans. Copied PNG residency uses semantic
role/path identities: the same sampler name in different stages may select
different images. Resolve property branches before checking shared declaration
duplicates; pass-local PNG paths remain authoritative in their lowered bindings.
Raw primary and opaque-only shadow depth remain distinct roles. A late writer
adding raw-depth requirements must rebuild cached wrappers; failure retains the
accepted resources and retires partial creations. Shadow programs may declare
only primary color; do not invent a second output. Legacy combined matrices use
the current draw family's transforms, and fog coordinates retain pack writes
through an explicit interpolated float with Frozen's zero initialization.

Empty selected-source frames still clear owned shadow depth/colors and snapshot
opaque depth before fullscreen consumers. Preparation carries that same opaque
texture and extent; caster geometry controls draw preparation separately.
On source-color rollback, release frontend descriptors and cached pass targets
before the runtime retires sampler wrappers and images. Teardown and runtime
generation replacement follow that order too. GAL retains physical resources
needed by accepted submissions.

Color targets and shadow camera directives use the same bounded, selected
fragment-program traversal as built-in uniforms. Apply declarations in Frozen
ProgramSet order, retaining the last accepted value and protocol defaults for
absent directives. An empty dimension override must not import base libraries.
Compact transported snapshots retain their explicit metadata path; missing
executable includes still prevent admission. See
[directive traversal](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/properties/directives.rs).

Terrain shadows must retain baked faces removed from the camera color ranges.
Turning off GPU face culling cannot restore a CPU-omitted range. Off-camera
source casters use all facings; camera-visible terrain contributes only its
missing ranges to the separate typed shadow stream. The ordinary camera draws
keep their face selection, and their existing shadow faces are not duplicated.
Camera-sorted translucent streams already carry their complete primitive domain.
Keep these decisions in Rust's
[mesh batching](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/geometry/batching.rs)
and source plan; Java supplies the immutable mesh and placement semantics.

Keep each dimension's shadow resolution, matrices, caster properties and cutout
rule together. Cache four immutable shadow policies per source generation;
attachment compatibility includes the selected resolution, and cutout pipeline
identity includes the threshold. Invalid policies reject before attachment
replacement. Perspective shadows remain a camera-policy rejection. The bridge copies
the user's shadow distance in chunks alongside the normal render distance in
blocks (`far_plane`). Rust derives distance-only, advanced and safe-zone terrain
culling from those inputs and the selected pack multipliers, preserving Frozen's
different equality rules and negative sentinels. Camera uniforms remain independent
of caster selection. ABI 68 adds immutable renderer culling bounds, camera origin,
leash-holder bounds, eligibility and player-only extraction roles. Rust selects
entity casters with the pack's entity multiplier, while block-entity directives
remain independent. Java copies bounded off-camera candidates without reading pack
caster flags. Entity distance boxes use Frozen's absolute float casts; advanced
planes subtract the double camera first. Safe-zone entity visibility retains
Frozen's world-AABB behavior, separately from terrain's Sodium entry point.
Model geometry bounds cannot replace gameplay culling bounds. Shadow-only orbs
retain their role through Rust geometry creation; other unported shadow streams
are removed from camera queues and reported as missing ownership during admission.
This transport does not establish broad entity/shadow gameplay parity.
See [scoped shadows](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/properties/shadow/scoped.rs)
and [color declarations](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/resources/color_targets/scoped.rs).
The user-zero shadow-pass lifecycle, default culling with geometry shaders, and
DH-enabled shadow projection selection still need their own admission and runtime
verification; the distance calculator alone does not establish those behaviors.

## Troubleshooting

- **Shader Packs is missing from Video Settings.** Check that the CPU shader
  configuration initialized before `Options`; the menu hides that page when
  `Iris.getIrisConfig()` is null.

- **A boundary test fails after a change.** Its message names the file, line
  and rule. Move the code to the layer allowed to know about it, or expose what
  you need through that layer's public API. Don't add exceptions to the test.
- **"backend identity must not be exposed".** You compared against a backend
  name or added a backend-identifying capability field; add a feature flag or
  limit instead.

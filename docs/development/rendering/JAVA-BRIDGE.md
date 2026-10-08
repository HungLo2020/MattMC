# Java bridge

In the current migration, Java drives the native renderer through a C ABI: the exported
`mattmc_vulkanic_gal_*` functions, which
[`VulkanicGalBridge.java`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java)
binds by name with FFM downcalls. The Rust side is
[`render/bridge/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge) (see its README for the file map). It decodes
and copies what Java sends, calls the GAL or a renderer, and writes a status
back. It makes no rendering decisions.

This is a current compatibility boundary. The [completed runtime target](../PROJECT-ARCHITECTURE.md) has no Java dependency; [Goal 5 status](GOAL-5-STATUS.md) keeps current ownership and verified progress distinct.

## How the ABI stays in sync

- **Records are `#[repr(C)]` structs** in [`bridge/abi/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/abi),
  grouped by family. Every request starts with an `FfiHeader` carrying the ABI
  version and the record's byte size; the bridge rejects unknown versions and
  any size that differs from the Rust layout.
- **Java does not hard-code layouts.** It asks Rust for each record's size,
  alignment and field offsets (`mattmc_vulkanic_gal_abi_struct_layout`, backed
  by the table in [`bridge/layout.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/layout.rs)) by struct id,
  then writes fields by index.
- **Versions:** Java's `ABI_VERSION` must equal Rust's `FFI_ABI_VERSION`
  ([`abi/version.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/abi/version.rs)), which also records what
  each version changed.

## Changing the ABI

1. Add or extend the record in the right `bridge/abi/` family file. Append
   fields; reordering or removing fields breaks every Java writer.
2. Add the record (or new fields) to the layout table in `bridge/layout.rs`,
   with a new struct id for a new record.
3. Mirror it in `VulkanicGalBridge.java`: the `Struct` enum entry (same id) and
   the code writing its fields.
4. Bump `FFI_ABI_VERSION` and Java's `ABI_VERSION` together, with a one-line
   note in `abi/version.rs`.
5. Bound the new input: payload limits live in `abi/limits.rs`, and per-request
   input-byte bounds in the checked readers in `bridge/memory.rs`.
6. Decode into renderer types in the matching module (`gui/`, `world/`, ...)
   and keep it decode-and-copy only; rendering decisions belong to renderers.

Never rename an exported function or change its signature without the matching
Java change: Java binds by name and fails at load if a symbol is missing.

ABI 67 appends `configured_shadow_distance_chunks` to the shader environment
record (struct 73, field 64). Java copies the CPU user setting without clamping
or interpreting negative values; Rust combines it with copied normal render
distance and pack policy. Rebuild the native library before using the new Java
writer. `WorldShaderEnvironmentEncodingTest` verifies the appended field and
adjacent fog range against the exported native layout, including dirty storage.
The layout-query result has a fixed 72-field capacity (312 bytes), mirrored by
Java's query allocation. Each table entry checks its field count at compile time;
the bridge regression queries all exported records, including the 65-field shader
environment. Update the native capacity and Java allocation together when growing it.

ABI 68 appends entity culling mode, flags, absolute double bounds, optional
leash-holder bounds and double camera origin to mesh instances (struct 69,
fields 31–35). Orb instances (struct 110, 248 bytes, fields 8–13) carry the same facts plus
an explicit shadow-only role. Absent metadata actively zeroes every appended
field. The decoder rejects unknown flags, malformed bounds, inconsistent holder
roles and metadata in first-person, terrain or block-entity domains. Nested Java
CPU extraction scopes retain immutable records; an inner null scope masks its
parent and cleanup restores it. Rust owns pack selection and geometry creation.
Rebuild before running `WorldEntityCullingEncodingTest`; its native checks cover
large camera origins, exact double values and dirty storage.

ABI 69 appends `world_static_terrain_shadow_casters` (struct 112, 32-byte
records: mesh key, generation, section origin, depth policy) and the frame's
`static_terrain_camera` to the whole-frame request (struct 53, fields 47–48).
The camera is required whenever casters are present. The current producer packs
caster records before request encoding; Rust validates them and sorts them by key. Eligible
resident casters stay compact for the shader-route scene; other resident
casters expand into shadow-only instances. Rebuild Java and native code together.

ABI 70 appends `world_static_terrain_sections` (struct 113, 40-byte records:
mesh key, newest copied generation, section origin, depth policy, flags) as
field 49. Ordinary frames send every camera-visible section layer this way, in
draw order (translucent back to front); only the camera-sort flag is allowed.
Rust draws the generation it has acknowledged for each key, so the previous
generation keeps drawing while a replacement uploads. Eligible sections stay
compact for the [shader-route scene](RETAINED-SCENE.md#current-scene-terrain-on-the-shader-route).
Leftover sections, and compact terrain on ordinary routes, expand into instances:
camera sections precede the existing instance list and shadow casters follow it.
Keys with no acknowledged generation are skipped until upload. Diagnostic,
fault-injection and resource-reload frames still send per-section instance records;
`-Dmattmc.dev.perRecordStaticTerrain=true` forces that path for A/B checks.

ABI 71 appends `world_model_rig_poses` (struct 115, 40-byte records: raw
`ModelPart` offset, rotation and scale, plus visible/skipDraw flags) as field 50.
A cached entity model registers its part tree once as a rig
(`mattmc_vulkanic_world_model_rig_register`, struct 114 nodes: parent index and
part mesh key/generation) and sends one mesh instance per model each frame,
flagged `0x4000_0000`, with `mesh_key` = rig id and `mesh_generation - 1` = its
first pose. Rust composes the hierarchy like `ModelPart.visitRenderable` and
expands one ordinary instance per drawn part before validation
([`model_rigs.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/world/model_rigs.rs)).
Java retires a rig when its topology is evicted or a part generation changes.
Release uses a [three-semantic-frame delay](https://github.com/HungLo2020/MattMC/blob/7a6009f84d966263293f864933f4da06b1823dfa/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L12578-L12621)
and a three-slot pose ring; that delay is not a GPU-completion fence or per-rig
completion receipt. Queued submission decodes/copies on the caller before
return, while the optional single in-flight route decodes later on its worker
and retains the request arena until join. Preserve these separate contracts
when changing queue depth or lifetime. A rig instance may carry standard item foil: enchanted armor and trident
glint passes are rigs over the same parts with the glint texture and material,
so they no longer bake a new posed mesh asset each frame. First-person frames,
uncacheable dynamic textures and per-part mesh diagnostics keep Java-posed part
instances; `-Dmattmc.dev.modelRigs=false`
forces that path for A/B checks. `ModelRigTransformParityTest` compares Rust's
part transforms with Java's on vanilla models under random poses.

Java still extracts local-space cubes and selects textures/materials. Rig
registration validates nonzero identities, parent-before-child order and known
flags, with 1–1,024 nodes and at most 4,096 registered rigs. Expansion validates
pose spans, skips hidden subtrees, keeps children of `skipDraw` nodes and clears
the transport flag before ordinary instance validation. [Rig implementation](https://github.com/HungLo2020/MattMC/blob/7a6009f84d966263293f864933f4da06b1823dfa/src/main/rust/render/bridge/world/model_rigs.rs).
[Admission and upload proof](https://github.com/HungLo2020/MattMC/blob/7a6009f84d966263293f864933f4da06b1823dfa/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L12623-L12745)
observe a topology once per semantic frame and reuse proof until withdrawal,
readmission or registration invalidates it; changed part generations replace
the registration. These mechanisms do not supply geometry for Citadel's empty
proxy root: both structural extraction and the posed fallback remain empty
([#803](https://github.com/HungLo2020/MattMC/issues/803)).

Typed orb placements name a boundary in the collected mesh stream. When the
shadow-only CPU capture removes foil or outline meshes, map those boundaries
through its kept-mesh prefix before the later source-admission mapping.
Preserve equal-boundary order, camera placements, culling facts and shadow roles;
appearance residency and published immutable records must remain unchanged.
See [the semantic collector](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/ExperienceOrbSemanticCollector.java).
That existing filtering/admission remap does not cover subsequent native rig
expansion: an input rig is one mesh row but may emit zero or many. The unchanged
orb boundary can split the expanded parts or exceed the resulting stream
([#819](https://github.com/HungLo2020/MattMC/issues/819), source-predicted).

## Standalone query handles

Some render-thread questions are answered by handles that share no context
state with a pipelined frame, so asking never joins it. They use standalone pointer-based calls outside the context request protocol.

- **Entity shadow query**
  ([`EntityShadowQuery.java`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/bridge/EntityShadowQuery.java),
  [`world/entity_shadow_query.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/world/entity_shadow_query.rs)).
  It shares only the active pack's overworld shadow policy, refreshed by each
  shader-pack source update. Before extracting off-camera entities for the
  shadow pass, Java sends their copied culling facts with the frame's copied
  environment, matrices and sky type; Rust returns which ones Iris's shadow
  pass admits. The frame plan applies the same admission again. "Undecided"
  (no policy, unresolved hooks) means Java keeps every candidate. The bridge
  owns the handle and destroys it before its context.
- **Section graph** (Frozen's camera terrain search and ordinary compact terrain
  selection): Rust owns readiness/build/urgent/in-flight/stale bookkeeping;
  Java reports column/build events and published solid/cutout/translucent mesh
  rows. After camera search, `select_terrain` returns graph-owned
  buffers for camera layers, optional off-camera shadow candidates and animated
  section positions, plus producer counters. Camera/caster buffers use the same
  ABI 70/69 layouts as the whole-frame request. Their views are valid only until
  the next selection; Java copies them into packed pending storage, takes an
  owned frame copy and copies that block into the request arena. Queued submission
  then decodes/copies before the arena is released. This is a compact handoff,
  not a retained borrow or zero-copy frame. See
  [selection and lifetime](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/src/main/java/net/vulkanic/world/RustSectionGraph.java#L215-L282),
  [packed frame storage](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L511-L591)
  and [selection scope](RENDER-ARCHITECTURE.md#resource-ownership-and-retries).

### Terrain layer intake

At `4740f8fa`, the standalone `mattmc_terrain_assemble_layer` call replaces
the earlier decode-only export. The [bridge export](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/src/main/rust/render/bridge/world/terrain_intake.rs)
keeps wire layout handling outside `worldrender`; native intake/assembly returns
vertices, indices, ranges, identities and accounting receipts. Invalid arguments
or capacity return `-2`; a rejected mesh returns `-3` with a bounded error string.
Java [allocates encoded vertex storage and copies index/range outputs](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/src/main/java/net/vulkanic/world/RustTerrainIntake.java)
before the confined scratch arena closes. It retains build/sort/atlas inputs,
worker dispatch and asset publication. Native assembly now performs water
classification and vertex rewrites previously done through Java's writable
encoded view; keeping encoded vertices through publication is not an end-to-end
zero-copy or worker-ownership claim.

## Rules the boundary tests enforce

- The bridge uses only the public GAL modules and is the only code that creates
  GALs (`VulkanicGal::create*`).
- `gui/` and `world/` only decode and copy records; they never build GAL
  resources or commands themselves.
- Record names extend shared families (GUI, world, material) rather than naming
  individual producers (no `Hotbar`, `BossBar`, ...).

## Verifying an ABI change

Run from the repository root; the subshell leaves the Gradle command there.

```sh
(cd src/main/rust && cargo test --release render::bridge)
./gradlew test --tests net.vulkanic.bridge.VulkanicGalBridgeAbiTest
```

For a refactor that should not change the ABI, compare the exported symbols of
the release library before and after:

```sh
nm -D --defined-only src/main/rust/target/release/libmattmc_rust.so | awk '{print $3}' | sort > symbols.txt
```

## Request memory budget

Checked FFI readers charge all nested reads against a 512 MiB budget per bridge
request. Counts and byte limits are checked before constructing foreign slices;
profiling does not dereference nested pointers ahead of decoding. The byte
counter measures validated reads (including repeated reads), not unique Java
allocation size. Java must supply live memory while native code can read it:
through the call for synchronous and caller-decoded queued requests, and
through the join for the single in-flight pipelined fallback described below.
Failed resource creation batches release successful creates before reporting
failure, and result alignment/capacity are checked before execution.

## Pipelined frames

Ordinary whole frames use the [queued route](#queued-frames) by default when
both pipelining and queuing are enabled. Queued submissions decode and copy on
the calling thread; they do not retain borrowed Java request memory after the
call. `MATTMC_PIPELINED_FRAMES=0` or
`-Dmattmc.rustGal.pipelinedFrames=false` disables both asynchronous routes.

### Single in-flight fallback

When queuing is disabled but pipelining remains enabled, the older
`mattmc_vulkanic_gal_whole_frame_submit_pipelined` route copies and validates the
small present request on the calling thread, but hands the whole-frame request
pointer to a per-context worker. That worker decodes the nested request, then
executes and presents the frame. Java retains the request arena alive and
unmodified until the join; acceptance is not completion or a deep copy of all
request bytes. Java can collect the next frame while native work runs, but
cannot pack another whole-frame request before joining because the requests
also share persistent instance arrays. This was the default at the earlier
`54611cfc` checkpoint; it remains the fallback contract, not the queued route.
[Native handoff and worker decode](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/rust/render/bridge/world/exports.rs#L36-L110)
· [Java retention and release after join](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java#L2378-L2453)
· [Java repacking guard](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java#L1892-L1900)
· [`bridge/pipeline.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/pipeline.rs)

- GUI mesh batches whose vertex and index lists come from a long-lived cache
  (item topology caches, cached TACZ captures) are encoded once into the
  context's persistent arena. `reserved0` bit 0 marks such a batch and the
  upper bits carry the store generation. Rust keys decoded geometry by both
  addresses, both counts and that generation instead of re-decoding it.
  Never rewrite persistent GUI geometry in place. The Java store admits at most
  4,096 topologies; the separate thread-local Rust decode cache clears at 1,024
  entries before inserting another. Cache hits share Rust-owned vertex/index
  arrays through `SharedVec` (`Arc<Vec<T>>`), check each batch's metadata and
  skip per-vertex validation only after
  a successful check under the same block-raster-presence state.
  [Decoder](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/bridge/gui/mesh.rs#L11-L30)
  · [Validation](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/bridge/gui/mesh.rs#L254-L270)
  [Shared arrays](https://github.com/HungLo2020/MattMC/blob/7a6009f84d966263293f864933f4da06b1823dfa/src/main/rust/render/guirender/mesh/model.rs#L178-L235)
  remove repeated deep clones on cache hits. Misses copy Java input; mutable
  access uses copy-on-write, consuming a still-shared vector clones it, and draw
  preparation can create transformed vertices and copy indices. This is not a
  zero-copy path. The generation belongs to each Java bridge instance and advances on its close;
  the native thread-local key contains no new-context identity. Same-thread
  context recreation with address reuse is an unverified lifecycle case, not
  a demonstrated defect or a guarantee supplied by the generation field.
- Generic DH boxes (up to 10,000 per frame, mostly clouds) travel as
  `PackedDhGenericBoxes` primitive arrays rather than a record per box. The
  pending buffer rotates through a ring of three; a consumed frame takes the
  buffer itself, and the encoder writes its arrays into native layout. Reuse
  relies on the coordinator draining queued work down to one prior frame and
  queued submission copying on the caller before return; preserve both when
  changing queue depth. The new packed-box tests assert ABI equality and value
  validation, not ring-wraparound or queue-lifetime behavior.
  Since ABI 72 the same object also carries the frame's retained-group
  instances and camera (`addGroupInstance`, `setCamera`; fields 51 and 52 of
  the whole-frame request). The group boxes themselves are registered once,
  through `VulkanicGalBridge.setDhGenericGroup`, and placed by Rust. See
  [Render Architecture](RENDER-ARCHITECTURE.md).
  [Pending ring](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L467-L483)
  · [Queue drain](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/java/net/vulkanic/gui/RustGalFrameCoordinator.java#L745-L761)
- Non-queued context-registry entry points join pending work first (`with_registry*`),
  preventing concurrent access to a context. Keep context access behind these
  wrappers. Selection through the [standalone query handles](#standalone-query-handles)
  does not join an in-flight frame. Creating the entity-shadow query still
  [uses the context registry](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/rust/render/bridge/world/entity_shadow_query.rs#L17-L25).
- `mattmc_vulkanic_gal_whole_frame_join` returns the frame's submit and present
  results. `RustGalFrameCoordinator.completePendingPipelinedFrame` runs the
  same post-submit work (`completeSubmittedFrame`) when the next frame starts.
- If worker decode or execution returns an error, the worker attempts frame
  cancellation and Java throws the error at join. A retryable selected-source
  failure drops that frame instead of resubmitting it. This does not establish
  complete panic recovery or cancellation semantics.
- Attachment captures, screenshots and RenderDoc captures stay synchronous.
  Atlas pumps during an in-flight frame are deferred to the next frame.
- The worker restricts itself to the highest-frequency CPUs; on hybrid CPUs an
  efficiency core lengthened every frame by ~10%.

### Queued frames

With pipelining enabled, this is the default ordinary whole-frame route
(disable just queuing with `MATTMC_QUEUED_FRAMES=0` or
`-Dmattmc.rustGal.queuedFrames=false`). Java no longer waits for frame N before
handing over frame N+1. The worker runs a FIFO of jobs
([`bridge/pipeline.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/pipeline.rs)):

- `mattmc_vulkanic_gal_whole_frame_submit_queued` decodes the request on the
  calling thread (Java closes the request arena after the call) and queues a
  frame whose job acquires the swapchain image itself, executes the decoded
  frame with that image's frame id and target, and presents. `..._whole_frame_join_queued` returns the
  oldest queued frame (acquire, submit and present results) and waits only for
  the jobs up to it.
- `..._world_mesh_update_assets_queued` and `..._atlas_animation_tick_queued`
  copy and decode on the calling thread and apply in a job, in submission
  order. The first non-frame job failure is reported by the next queued-frame
  join (fail closed); it does not automatically cancel later queued jobs.
- Queued entry points use `with_queue`, which never joins; other
  context-registry entry points still join all queued work first, so rare
  context operations cannot overlap the worker. Standalone query handles
  remain independent.
  A rejected queued call joins and stores its message, so Java's
  `failed with status` errors carry the Rust reason (`lastError`).

`RustGalFrameCoordinator` keeps at most one frame queued ahead of the one it
prepares. This is Java-side backpressure; the native FIFO channel itself is
unbounded. Capture, screenshot and RenderDoc frames drain the queue and run synchronously. While a
frame is queued, completion takes retirement from the present result instead
of querying the bridge. Submission acceptance is not presentation completion.

A queued frame can complete after the world it was prepared for is gone (save
and quit, resource reload). Completion receipts must not assert current
renderer state: the DH material-route receipt carries the collector lifecycle
captured when the frame was consumed (`PrimitiveFrame.distantHorizonsLifecycle`)
and is dropped and counted (`staleRouteExecutionReceipts`, also in capture and
benchmark JSON) when that lifecycle has been reset.
The caller-side decode and arena closure are visible in
[`world/exports.rs`](https://github.com/HungLo2020/MattMC/blob/121ad13c84e45555c34814d54a8199194b37f39c/src/main/rust/render/bridge/world/exports.rs#L200-L239)
and [`VulkanicGalBridge.java`](https://github.com/HungLo2020/MattMC/blob/121ad13c84e45555c34814d54a8199194b37f39c/src/main/java/net/vulkanic/bridge/VulkanicGalBridge.java#L2394-L2437).
Earlier queued worker-decode/double-staging descriptions are superseded by this
caller-decode path; they do not require two live Java request arenas today.

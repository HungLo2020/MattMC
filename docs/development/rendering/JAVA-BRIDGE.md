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

ABI 72 appends retained DH generic-group instances as whole-frame field 51 and
the double camera origin as field 52. Group boxes use struct 116 (56 bytes:
double min/max bounds, color and material); instances use struct 117 (72 bytes:
group id/generation, double origin, packed light, SSAO flag and six shading
multipliers). Java registers changed boxes through `setDhGenericGroup`; Rust
copies them into its CPU registry, then expands each frame's instances into
ordinary camera-relative boxes in draw order. This is retained input data,
not a retained GPU scene or a borrowed Java array. Both id and generation must
be nonzero. Rebuild Java and native code together; see
[registration and expansion](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/src/main/rust/render/bridge/world/dh_generic_groups.rs)
and [group lifetime and bounds](RENDER-ARCHITECTURE.md#resource-ownership-and-retries).
The Java group allocator can supply id 0 to this nonzero-only path;
[#820](https://github.com/HungLo2020/MattMC/issues/820) tracks that admission
mismatch when the first allocated group reaches active rendering. It is not
a claim that every default world fails.

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

ABI 73 adds raw GUI source format 3 for indexed map colors. Input bytes stay
compact; Rust frontend admission counts expanded RGBA residency, then converts
before creating ordinary GAL textures. Resident texture formats remain Alpha8
and RGBA8. Java and Rust reject malformed sizes and excessive expanded totals.
World-map PNG encoding is a bounded standalone CPU asset call, not a GPU resource
route. This map migration also introduces the semantic map-text material with
blended-cutout mode 7; both compact whole-frame decoding and ordinary quad
validation must admit that mode. Rust resolves its depth/lightmap/fog/order
policy; see [map colors](../game-model/MAP-COLORS.md). Rebuild both sides together.

ABI 74 appends native DH CPU frame identity, lifecycle and count as whole-frame
fields 53–55. The collector exports these with layer counts through
`mattmc_dh_collector_consume_retained`; readback is explicit. Production Java
sends no inline LOD stream for a native reference. Decode rejects mixed streams,
missing/oversized metadata, stale identities/lifecycles or changed decisions,
then owns the immutable list before queued execution. Queued input accounting
still charges its declared segment count. This is a bounded semantic CPU
transaction, not a GPU handle or presenter; see
[native frame ownership](RETAINED-SCENE.md#native-dh-visibility-frame-ownership).
Rebuild Java/native together. Capture observers may copy records, but the
renderer consumes the retained native snapshot directly.

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
  Java reports column/build events and requests a native sync from the
  [terrain publication registry](#terrain-publication-registry), replacing its
  earlier per-section mesh-row packing. After camera search, `select_terrain`
  returns graph-owned
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

### Terrain publication registry

[`RustTerrainPublication`](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/java/net/vulkanic/world/RustTerrainPublication.java)
binds standalone `mattmc_terrain_*` calls for publishing/removing a layer,
replacing/clearing all rows, synchronizing a section graph and copying rows for
Java shadow candidates. These render-thread calls use primitive arguments and
long arrays, outside the context request/layout-query protocol. Rebuild Java
and native code together when changing them.

The process-wide, mutex-protected
[registry](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/worldrender/terrain/publication.rs)
stores each section's solid/cutout/translucent key and generation, plus the
translucent camera-sort flag. Duplicate keys belonging to another section layer
reject publication or replacement without changing the registry. A row changes
when Java registers the layer, before upload acknowledgement. Graph sync drains
changed rows directly in Rust; clear/reload replacement or an explicit new-graph
republish sends a full reset under the same lock. This is one shared change
queue, not an independent cursor per graph. Shadow-row reads copy six longs per
requested section into Java storage, with zeros for missing layers.

Java retains the asset objects, upload acknowledgement and reload staging. The
native identity registry does not own vertex/index payloads or GPU completion;
compact frame records still follow the copying contract above. See
[ownership and retries](RENDER-ARCHITECTURE.md#resource-ownership-and-retries).

### Terrain layer intake

At `4740f8fa`, the standalone `mattmc_terrain_assemble_layer` call replaces
the earlier decode-only export. The [bridge export](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/src/main/rust/render/bridge/world/terrain_intake.rs)
keeps wire layout handling outside `worldrender`; native intake/assembly returns
vertices, indices, ranges, identities and accounting receipts. Invalid arguments
or capacity return `-2`; a rejected mesh returns `-3` with a bounded error string.
That checkpoint's encoded-vertex copy is now the fallback. Since `1d609f12`,
ordinary intake leaves assembled vertices in Rust's
[staging map](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/src/main/rust/render/worldrender/terrain/staging.rs),
with one generation per mesh key and at most 32,768 keys. Java receives the
vertex count/key/generation, copied index bytes, draw ranges and receipts.
`StagedWorldMeshVertices` cannot be read as a Java vertex list. Asset records
set `reserved0` bit 0 and carry an empty vertex slice; the native decoder
requires the exact staged generation and clones it, preserving retry data until
Java discards it after acceptance, removal or rollback.

If staging cannot admit a new key, [Java intake](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/src/main/java/net/vulkanic/world/RustTerrainIntake.java)
repeats assembly with copied vertex output. Fault injection, active texture
probes and the appearance trace always require vertex copies. Detailed terrain
diagnostics also copy unless `-Dmattmc.dev.forceTerrainVertexStaging=true` is
set; that switch does not override the other vertex readers. The map's limit
counts layers, not bytes. Java retains build/sort/atlas inputs, worker
dispatch and asset-publication bookkeeping; indices and ranges still cross the
boundary. Native assembly still performs the water/material classification and
rewrites. The staged route removes a vertex round trip, not all copying or Java
ownership. After acceptance, Java drops static-terrain payloads including
translucent layers; per-frame translucent ordering uses native resident geometry.
The resource-reload commit now releases the Java CPU payloads of every staged
layer after all staged generations are uploaded; their earlier acknowledgements
could only find published layers. The fully omitted translucent path still has
a separate cleanup gap when no prior layer asset exists: assembly may stage
vertices before Java returns no asset, and removal
has no asset identity to discard. [#821](https://github.com/HungLo2020/MattMC/issues/821)
tracks that source-derived retention; ordinary acknowledgement cleanup does not
cover it. No runtime growth or exhausted budget was observed by this review.

### DH collector ledger

[`DhCollectorLedger.java`](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/src/main/java/net/vulkanic/world/DhCollectorLedger.java)
binds the standalone `mattmc_dh_collector_*` exports. These calls use the
collector's Java lock and Rust's ledger mutex; variable-length effects, text,
segment and payload results live in thread-local native buffers and must be
taken immediately on the calling thread. They are copied results, not a
persistent native view. Keep segment staging and `record_built` together; the
latter takes the staged payload even when recording fails.

`mattmc_vulkanic_gal_world_lod_collector_flush` also enters the context registry:
it selects and applies a ledger-owned update, then returns identities for
Java's acknowledgement. An apply failure releases its in-flight selection;
a provenance-bearing selection stays on Java's packed asset path. Neither
selection nor asset acceptance is a presentation receipt. The batched visible
query receives `(column key, generation)` pairs and filters stale generations
before requesting publication, sorting and admitting segments. Its count is the
number of pairs, and the result's nine-long header includes a stale-container
count before the sorted keys. This changed standalone buffer contract requires
matching Java/native builds; it is separate from the whole-frame ABI version.
Keys and segment records still cross through Java frame storage. See
[ledger publication and selection](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
for route gating, protection, bounds and the remaining Java producers.

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
- `PackedDhGenericBoxes` carries ABI 72 retained-group instances and camera
  data for the ordinary DH generic producer (up to 10,000 boxes per frame).
  Its primitive per-box arrays remain available for direct box input; unchanged
  groups no longer resend those arrays every frame. The pending buffer rotates
  through a ring of three; a consumed frame takes the buffer itself, and the
  encoder copies its arrays into native layout. Reuse relies on the coordinator
  draining queued work down to one prior frame and queued submission decoding
  on the caller before return; preserve both when changing queue depth.
  Registration copies group-local boxes separately, and whole-frame decode
  expands the matching registry generation into owned frame boxes. The optional
  worker-decode route therefore resolves groups later; registration/release and
  request-arena lifetime are separate concerns. Existing packed-box tests assert
  ABI equality and value validation, not ring-wraparound or queue-lifetime
  behavior. See [Render Architecture](RENDER-ARCHITECTURE.md).
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

## Chunk rebuild color ownership

The separate compact meshing header is version4 (136 bytes), with a final CPU
color-owner identity. Ordinary snapshots use Rust-owned shared world-coordinate
fields; literal tensors remain for compatibility fixtures. Whole-frame ABI74 is
unchanged. See [section color snapshots](../world/biome/RUST-SECTION-COLORS.md)
for construction, sealing, lifetime and provider compatibility.

Ordinary block-state inputs now borrow [Rust loaded-section snapshots](../world/chunk/RUST-SECTION-SNAPSHOTS.md).
Rust fills the state-ID halo directly; Java retains contextual light and model
admission. Unsupported/debug containers retain their original CPU compatibility
path. This does not transfer live chunk mutation or GPU ownership to Java.

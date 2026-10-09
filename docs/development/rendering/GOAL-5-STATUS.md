# Goal 5 rendering checkpoint

**Goal 5 remains incomplete.** The current source review reaches
[`64294324`](https://github.com/HungLo2020/MattMC/commit/642943247003d7d8d756a65180f0872b088c13f0):
native section counters, DH cloud preparation and GUI/world/hand item poses
extend the earlier ownership. The latest author-recorded item workflow passes
all median average-FPS floors in its measured window but **fails overall
performance acceptance on vanilla p99**. Large repeat variance prevents a
robust or isolated speedup claim. Later reload/memory observer corrections have
narrower fresh evidence; they were not followed by another full performance
matrix. See the [current measured workload](#october-9-native-world-and-hand-input-summary).
Source inspection and author reports do not establish broad visual/temporal
parity, complete scene migration, long-run resource bounds or resolution of the
independent native crash.

Rust owns terrain graph bookkeeping, publication identities, ordinary terrain
selection and assembly, rig hierarchy composition, the DH ledger and ordinary
payload publication, retained visibility frames and built-in cloud preparation,
native authored item poses and GPU execution/resources.
Separate native world owners now supply canonical live/rebuild state and section
color fields. Java still supplies
world/entity semantics and animation, meshing dispatch and inputs, full terrain
asset/reload bookkeeping, DH quadtree/frustum candidates, frame parameters and
material-provenance diagnostics. Content definitions now have separate native
owners; Java retains behavior factories, contextual callbacks and compatibility
objects. Terrain staging and DH native publication retain copied/diagnostic
paths; neither is a zero-copy contract.
The final target remains one Rust executable supporting client and dedicated
server, at most one separately loaded Rust library, and no Java/JVM. See the
[whole-project migration plan](../RUST-MIGRATION.md),
[Project Architecture](../PROJECT-ARCHITECTURE.md),
[Render Architecture](RENDER-ARCHITECTURE.md) and [Retained Scene](RETAINED-SCENE.md).

The [current performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6088997781)
keeps the failed tail-latency gate and measured/observer-only windows explicit.

The preceding [generation-handoff source review](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-6086170999)
and [performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6086142300)
retain those open acceptance limits. Earlier checkpoints below preserve their
original source and workload scopes.

The October 9 [content ownership review](https://github.com/HungLo2020/MattMC/issues/771#issuecomment-6073238616)
and [performance/evidence review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6073232463)
retain these source and acceptance boundaries. Content/resource ownership and
performance remain open; #823's bounded tooling closure does not close them.
The earlier [intrinsics ownership review](https://github.com/HungLo2020/MattMC/issues/771#issuecomment-6070919773)
and [intrinsics evidence review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6070924916)
retain their historical checkpoint scope.

The earlier [terrain publication](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6053377956),
[DH lifecycle](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6053379365),
[shared pages](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6053380774)
and [validation/performance](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6053382027)
checkpoints retain their open acceptance work.
[#822 is closed after focused tooling verification](https://github.com/HungLo2020/MattMC/issues/822#issuecomment-6065782881).
Its false PASS when an exception omitted requested background results is now
historical: at `97e30922`, background exceptions become failed steps and the
aggregate rejects missing requested results. The closure covers that defect,
not runtime acceptance; see
[the driver guide](RENDER-VERIFICATION.md#one-command-validation) for the
current checks and their remaining limits.

The [October 8 acceptance/roadmap review](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6065826232)
and [recorded-candidate performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6065805242)
retain the distinction between repaired tooling, proposed ownership and missing
runtime acceptance evidence.

The earlier `f13239e1` [terrain staging](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6051134159),
[DH ledger](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6051203728),
[retained groups](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6051205144)
and [measurement](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6051135359)
checkpoints retain their bounded scopes. The integrated checks and later
interleaved timings below are newer author reports, not independent reruns.

The prior [Goal 5 tracker checkpoint](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6049236504)
and [rendering ownership checkpoint](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-6049217250)
retain their dated scopes; issue status is not runtime acceptance.

The earlier [October 4 tracker checkpoint](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5982772544)
and [October 3 checkpoint](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5972378509)
retain their historical scope and acceptance limits. The older evidence below
is preserved; it is not a measurement of the current implementation.
Those source checkpoints are
[`37817e1`](https://github.com/HungLo2020/MattMC/commit/37817e128b99b07456c0ee22a5d34eaf05c72150)
and [`2fff1ef`](https://github.com/HungLo2020/MattMC/commit/2fff1ef19106350f806ddedd4fb3c3b4fbc44716).

## What changed

### October 9 section counters, clouds and item inputs

The `a908f78c` → `64294324` interval advances three bounded owners:

- [Section counters](../world/chunk/RUST-SECTION-COUNTERS.md) move signed-short lanes and canonical mutation/recount into Rust. Generation input capture reads storage and counters under the storage lock; Java still orchestrates chunk work and publishes results
- [DH cloud preparation](RUST-DH-CLOUDS.md) moves built-in motion, placement, culling and color history into native CPU owners. API callbacks, custom groups and Java DH world orchestration remain
- [Item layers](RUST-ITEM-LAYERS.md) retain authored poses through GUI and world/hand decoding. Java model selection, topology, parent animation and custom paths remain. ABI 75 introduced cloud references, 76 direct GUI poses, and the current ABI 77 adds world/hand inputs

The intervening [readiness/cleanup fixes](https://github.com/HungLo2020/MattMC/commit/111d7a9c48b5d5876c1649e81a3c5608d98fb6f3)
remove column sidecars by their index range, transform debug axes through the
frame view, and preserve offscreen rebuild marks without resetting camera
readiness. These changes do not waive benchmark producer checks or establish
broad visual acceptance. Source-only light-owner draft results in the working
record are not evidence that production light propagation/rebuild consumers
have migrated.

### October 9 native frame and world-input checkpoint

The `87046367` → `a908f78c` interval adds bounded ownership slices:

- [Map colors/images](../game-model/MAP-COLORS.md) and [state policy](../game-model/STATE-POLICY.md) now originate in Rust. ABI 73 introduced indexed GUI source format 3; this historical interval reached ABI 74. Java still supplies contextual map/world behavior and compatibility objects
- [DH visibility frames](RETAINED-SCENE.md#native-dh-visibility-frame-ownership) remain immutable native CPU lists through caller-side queued decode. Java passes identity/lifecycle/counts and still walks the quadtree; three resolvable ring slots do not cap all live decoded owners
- [Section color fields](../world/biome/RUST-SECTION-COLORS.md) share lattice samples per resolver within a section capture. Origin tint calls, Java biome blending/context and literal provider callbacks remain
- [Rebuild snapshots](../world/chunk/RUST-SECTION-SNAPSHOTS.md) and [live block sections](../world/chunk/RUST-LIVE-SECTIONS.md) own distinct immutable/mutable state. Native bulk state-ID halos replace Java halo decoding; model admission, CPU views, callback compatibility and GC lifetime limits remain
- [Generation-stage handoff](../world/levelgen/RUST-STAGE-HANDOFF.md) keeps canonical NOISE output and SURFACE/CARVERS capture/install inside Rust. Inputs are isolated copies and outputs become independent live owners; Java retained orchestration, counters and heightmap publication at that checkpoint; the newer counter guide describes its subsequent native owner

Source inspection and the committed author reports establish these boundaries.
This documentation review did not run clients, Java/Rust suites, captures or
profiles, or inspect the unbundled runtime artifacts. Earlier map, DH, color and
snapshot evidence retains its own release/hardening window; the later table
below does not remeasure each change independently.

### October 8 sound, offset and block-family checkpoint

The `3e1b2a94` → `87046367` interval adds three source boundaries:

- [Sound content](../game-model/SOUND-DEFINITIONS.md) and [block sound/offset policy](../game-model/BLOCK-SOUND-AND-OFFSETS.md) now originate in Rust. Java retains canonical registry/holder views, cached sound/instrument objects and immutable offset vectors; playback resources and contextual gameplay remain separate. Fact-export format 8 derives offset kinds and bounds from native definitions instead of Java-exported values
- [Block-family configuration](../game-model/BLOCK-FAMILY-TYPES.md) now supplies block sets, wood types and typed per-block parameters to Java compatibility constructors. Button timing and weighted-plate limits are data; Java still owns interactions, tick scheduling, redstone and other world-dependent behavior
- [#823 is closed after focused retention verification](https://github.com/HungLo2020/MattMC/issues/823#issuecomment-6073221178). Parent retirement now rechecks workspace eligibility and preserves an old invocation when any workspace must remain. This repairs the demonstrated two-pass defect, not every cleanup risk or runtime acceptance condition. [Storage contract](ARTIFACT-STORAGE.md#verification-driver-retention)

The author reports five Frozen observer pairs matching all seven content
digests after sound/offset integration, then all eight after family integration. The two full runtime workflows
remain **performance FAIL**; their [separate workload records](#october-8-native-block-families-summary)
retain test counts, image scope, repeats and receipt paths. This documentation
review inspected committed source and author records, not the unbundled runtime
receipts, clients or Java/Rust suites. The map-palette/image-processing work in
[the pinned working record](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/PROGRESS.md)
was staged only at that historical checkpoint; `84016f21` subsequently lands
the map/state-policy ownership described above.

`RunDev.py` also gains a Frozen launcher. It skips Frozen's launch-blocking test
task and uses that checkout's ordinary game directory. It does not select the
reference backend or validate source identity; see [launch prerequisites and
write behavior](../tooling/NATIVE-BUILDS.md#launch-current-or-frozen).

### October 8 native content ownership checkpoint

The `97e30922` → `d0141162` interval includes runtime ownership changes, beyond
the earlier verification-only milestone:

- [State graphs](../game-model/STATE-GRAPHS.md), [fluid definitions](../game-model/FLUID-DEFINITIONS.md) and [shared properties](../game-model/PROPERTY-DEFINITIONS.md) now originate in Rust. Java projects compatibility views and retains world-dependent fluid simulation and gameplay
- [Registered block definitions](../game-model/BLOCK-DEFINITIONS.md) own identities, ordered domains/defaults and shared transition graphs. [Physical settings](../game-model/BLOCK-PHYSICS.md) and [intrinsic state rules](../game-model/BLOCK-INTRINSICS.md) also originate in Rust, including map-color identity, emission and canonical fluid association. Export format 7 no longer imports emission or fluid IDs. Java retains shapes, blocked light and contextual behavior; sound/offset ownership remains an isolated draft at this checkpoint
- Capture cleanup at this historical checkpoint finds parent ownership markers and the fixture pass checks that its source directory exists. That still left the two-pass gap recorded in [#823](https://github.com/HungLo2020/MattMC/issues/823); the later family checkpoint repairs it. Follow the current [pinning and storage limits](ARTIFACT-STORAGE.md#verification-driver-retention)

The implementation author's [pinned working record](https://github.com/HungLo2020/MattMC/blob/d0141162d81eee184fa99f0b7a9411d401c306b4/PROGRESS.md)
reports the runtime checks and cleanup counts below. This documentation review
inspected committed source and text records, not the original runtime receipts,
clients, captures, Java/Rust suites or cleanup filesystem. The rejected dense
mesh-slot cache was removed; it is not part of these landed ownership slices.

### October 8 verification and migration checkpoint

The `1216f060` → `97e30922` interval changes acceptance tooling and its
planning/evidence records, not the runtime implementation:

- **Verification:** requested background checks can no longer disappear into a passing aggregate. The combined driver also checks the complete lifecycle scenario set, stricter FPS/parity receipts and paired performance floors. These guards address specific evidence failures; they do not prove all workload equivalence or inspect images. [Canonical workflow](RENDER-VERIFICATION.md#one-command-validation)
- **Feature parity:** `RunFeatureParity.py` adds block-entity, equipment, held-item and hand fixtures. Every requested scenario must pass Frozen; an unchanged historical failure is not absolute parity. The current working record reports chest/chest-shaders/sign/trident passes, but bed/banner/zombie/held-item coverage remains incomplete or failed. Prior shield/hand attempts stopped at disk preflight and zombie equipment-reference processing raised an exception; distinguish those harness failures from renderer defects. [Pinned working record](https://github.com/HungLo2020/MattMC/blob/97e3092269ed29854c8175a480a819fb1896c311/PROGRESS.md)
- **Evidence storage:** completed generated fixtures and superseded marked invocations can retire automatically. Preserve current acceptance, unresolved diagnostics and required input sources using the [storage and pinning rules](ARTIFACT-STORAGE.md#verification-driver-retention); retired evidence is not replayable merely because its summary survives.
- **Migration scope:** the [staged plan](../RUST-MIGRATION.md) extends through content, world ownership, gameplay, presentation/services and Rust application startup. At this historical checkpoint, content definitions and state construction were the next planned ownership slice; the later native content checkpoint above records their landed scope. Existing native rendering and bridge ownership do not complete the application migration.

That checkpoint's root records describe a separate `d7ee0335d` runtime candidate,
not a new runtime result from the verification-only source commit. Its raw
benchmark receipts are unavailable, so recorded health and FPS do not independently re-establish
acceptance. See the [October 8 candidate summary](#october-8-recorded-candidate-summary).
This documentation review inspected source and retained text records, not live
clients, captures, Java/Rust runtime suites or the unbundled benchmark artifacts.

### October 7 source review

#### Integration ownership and validation follow-up

The `bf8a557d` → `697b0a3c` interval adds these boundaries:

- **Terrain publication:** Rust owns each section's three mesh key/generation slots, collision checks and changed-row delivery to the graph. A row switches when registered, before upload acknowledgement; shadow collection reads identities in bulk. Java retains full asset records, upload acknowledgement and reload orchestration. Native row replacement is all-or-nothing on key collisions; it does not make the entire Java/native reload transaction atomic. [Publication source](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/worldrender/terrain/publication.rs) · [Export/graph fixture](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/bridge/world/terrain_publication.rs)
- **DH lifecycle:** the quadtree walk sends key/generation pairs; the ledger filters stale generations and preserves request/LRU walk order before stable distance ordering and visible admission. A container retires only its own lease. A build completing after section close is closed instead of installed. Java still owns the walk, and controlled-order fixtures are not a complete concurrency proof. [Verification](RENDER-VERIFICATION.md#october-7-integration-batch-checks)
- **DH geometry:** ordinary columns share device-local vertex and index pages. Uploads use one staging payload per transaction, accepted writes become resident, rejected ranges return immediately, and replaced ranges wait for submission completion. Packed non-deferred passes reuse one geometry set per vertex page within each pass; unpacked/deferred bindings and exact-atlas geometry retain separate paths. Moving the staging payload avoids an extra clone, not all copying. DH multi-draw indirect remains proposed. [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Vulkan capability:** device creation enables `drawIndirectFirstInstance` only when reported supported, alongside existing `multiDrawIndirect` negotiation. This feature enablement is not implementation of DH multi-draw or proof of a fallback on hardware lacking it. [Device source](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/vulkanic/backends/vulkan/device.rs)
- **Reload payloads:** the committed replacement map now drops uploaded layers' Java CPU payloads, which their earlier acknowledgements could not release while they were staged for reload. This does not address [#821](https://github.com/HungLo2020/MattMC/issues/821): an entirely omitted translucent layer with no previous asset can still leave its native staged generation without that commit/acknowledgement path. [Reload repair](https://github.com/HungLo2020/MattMC/commit/d70fdf1c)
- **Validation driver:** the new orchestration combines selected Java tests, Rust library tests, wiki checking, the seven-scenario gate, two settled parity pairs and moving FPS. Skipped steps and artifact-reader limits constrain a reported pass; visual inspection and workload-specific regressions remain required. [Commands and scope](RENDER-VERIFICATION.md#one-command-validation)

The author reports 2,344 passing Rust tests (three ignored), Java rendering
suites, seven clean lifecycle scenarios and bounded Iris+DH/vanilla parity for
the integration at `72b8cea2`. The later driver record reports Java 792 passed
and one skipped, the same Rust count, seven gate scenarios and fresh settled
parity. The [verification record](RENDER-VERIFICATION.md#october-7-integration-batch-checks)
keeps those reports separate from the inspected fixture definitions and the
[late-evening interleaved timings](#october-7-late-evening-interleaved-summary).
This documentation review reran none of those suites, captures or benchmarks
and did not inspect the unbundled runtime artifacts. The known source gaps
[#820](https://github.com/HungLo2020/MattMC/issues/820) and
[#821](https://github.com/HungLo2020/MattMC/issues/821) are not closed by a
passing aggregate report.

#### Evening terrain staging and DH ownership follow-up

The historical `f75eea5b` → `f13239e1` interval established these boundaries;
the integration follow-up above supersedes the affected ownership details:

- **Terrain vertices:** ordinary assembly stages decoded vertices by mesh key/generation in Rust; Java carries a count, and the mesh update clones the exact staged generation so rejection can retry. Java requests discard after acknowledgement, removal, rollback or an identical already-published rebuild. The 32,768-layer cap falls back to copied output; it is a layer-count bound, not a byte or GPU-memory bound. Faults, texture probes and appearance traces still require Java-readable vertices. Translucent Java payloads now release after upload too; the unused Java sorted-index channel is removed while Rust retains per-frame ordering. [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **DH ledger and payloads:** Rust owns current/pending/in-flight/published/retiring generations, owner leases, lifecycle receipts and column payloads copied at build recording. Ordinary asset publication constructs frontend assets in Rust; Java acknowledges under its lock. Material-provenance updates and on-demand diagnostic payload copies keep Java paths. The collector's 512-column/64 MiB retention targets can remain exceeded when entries are protected; the 16-column/16 MiB publication slice permits a first oversized column. These are not total-memory guarantees. [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **DH visibility:** Java still walks/culls the quadtree. One ledger call requests publication in that walk's order, performs stable near-to-far Manhattan ordering around the quadtree center, records visibility and admits ordinary visible segments. Exact-atlas coverage retains Java per-column admission. Only a selected route hands consumed segments to the frame. This does not move the quadtree or all DH semantics into Rust. [Verification](RENDER-VERIFICATION.md#october-7-evening-staging-and-dh-checks)
- **ABI 72 generic groups:** Java registers boxes when a group is changed or its count changes, then sends group instances/origins per frame. Rust retains local boxes and expands `(box + origin) - camera` in double precision before converting to floats. Missing/stale generations skip that instance and request resend; this is recovery behavior, not proof of gap-free presentation. Rebuild Java and native code together. [Bridge](JAVA-BRIDGE.md)
- **Lifecycle driver:** matching plain or gzip client logs now feed the same failure patterns, and repeatable `--jvm-arg` options can force the staged terrain path in captures. Missing logs, image parity and memory bounds remain outside the gate's asserted contract. [Lifecycle gate](RENDER-VERIFICATION.md#lifecycle-gate)

Two bounded source findings remain open: [#820](https://github.com/HungLo2020/MattMC/issues/820)
reconciles the first allocated DH group ID zero with nonzero retained admission;
[#821](https://github.com/HungLo2020/MattMC/issues/821) covers staged vertices
left behind by a fully omitted translucent layer with no prior published asset.
These are conditional active-path findings, not observed route failures, leaks
or claims about every world. Existing [#803](https://github.com/HungLo2020/MattMC/issues/803)
and [#819](https://github.com/HungLo2020/MattMC/issues/819) remain separate.

The implementation author reports 2,318 Rust/527 Java tests, scoped Iris+DH
and vanilla parity, zero VUIDs and all seven lifecycle scenarios with staging
forced at [`1d609f12`](https://github.com/HungLo2020/MattMC/commit/1d609f121447e42c47b4ff97adc7daa65484bfca).
The [`63584f3a` generic-group report](https://github.com/HungLo2020/MattMC/commit/63584f3a0354fc2c9ac1d9769202f0080e59483b)
records 2,329 Rust/583 Java tests, scoped parity including 2,241 cloud boxes in
nine groups, and three clean unload/reload/resource-reload scenarios. These
results belong to those commits, not a rerun of all later ledger changes.
This documentation review inspected source and test definitions only; it did
not rerun runtime suites, captures or benchmarks, or inspect the unbundled
artifacts. See [fixture limits](RENDER-VERIFICATION.md#october-7-evening-staging-and-dh-checks)
and the [evening timing summary](#october-7-evening-same-session-summary).

#### Native rigs, terrain assembly and lifecycle follow-up

This earlier review through `4740f8fa` retains its original ownership scope;
the evening staging and DH changes above supersede the affected details:

- **Section graph:** Rust owns ready/build/urgent/in-flight/stale state, loading readiness, block-entity section lists, interned animated-sprite IDs and entity-culling visit stamps. Java reports events, dispatches workers, marks the selected sprite objects and publishes meshes. Graph order is BFS visit order, not strict distance order. [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **ABI 71 rigs:** cached models send one flagged instance plus raw poses after Java `setupAnim`; Rust expands their hierarchy. Armor/trident glint reuses rig parts, with Java-supplied foil semantics. Per-topology admission runs once per semantic frame and upload proof is cached per registration. The three-semantic-frame retirement delay is not a completion fence. [Bridge](JAVA-BRIDGE.md)
- **GUI reuse:** persistent decode-cache hits share Rust-owned `SharedVec` arrays; misses copy input, and mutation, consumption or draw preparation can copy again. This is not a zero-copy GUI or removal of Java GUI ownership. [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Terrain intake:** `4740f8fa` extends native decoding to complete layer assembly, including sorter-index normalization, unsupported-fluid omission, water/material classification, ranges and mesh identity. It removes the prior Java assembly and standalone decode export. Java supplies build/sort/atlas inputs and publishes the result; encoded vertices and copied index/range outputs do not establish zero-copy transport. [Retained scene](RETAINED-SCENE.md#current-scene-terrain-on-the-shader-route)
- **Lifecycle:** queued DH receipts carry a collector lifecycle and stale receipts are dropped/countable. Teardown clears cached fullscreen consumers, while GAL retirement waits for dependency release and then honors submission retirement. The new seven-scenario lifecycle gate adds transition evidence, with explicit limits below. [Verification](RENDER-VERIFICATION.md#lifecycle-gate) · [GAL](VULKANIC-GAL.md)

The Citadel empty proxy root still yields no geometry through either structural
traversal or the posed fallback, so [#803](https://github.com/HungLo2020/MattMC/issues/803)
remains open. [#819](https://github.com/HungLo2020/MattMC/issues/819) separately
tracks raw orb mesh boundaries that are not remapped after a rig expands into
zero or many meshes. This is a source-predicted ordering/rejection defect, not
an independently observed gameplay crash or pixel failure.

This documentation review inspected source and test definitions only. The
[author's lifecycle commit](https://github.com/HungLo2020/MattMC/commit/7f256b5354033eff7553f51a10539fac5a68a0bd)
reports all seven transition scenarios clean, 2,304 Rust and 481 Java tests;
[the assembly commit](https://github.com/HungLo2020/MattMC/commit/4740f8fabffd878286850083e2d86ff733c9121e)
reports 2,317 Rust and 448 Java tests, scoped Iris+DH/vanilla parity and clean
world-unload/resource-reload repeats. Its temporary 1,500+ shader-layer assembly
comparison is author-reported and not retained as a checked-in dual-path test.
None of these runtime results or unbundled artifacts was rerun or independently
inspected here. See [bounded checks](RENDER-VERIFICATION.md#october-7-rig-and-terrain-assembly-checks).

#### GUI residency and mode-cost follow-up

The [`20e157ca` source checkpoint](https://github.com/HungLo2020/MattMC/commit/20e157cab7962140b30b83f40374cdeb1e6a8b19) extends the earlier review below:

- **GUI reuse:** flat and standard-foil items keep topology/raster identities; foil pixels remain animated through a per-draw UV transform. Cached TACZ captures can use persistent bridge storage. Native decode, prepared-geometry and accepted GPU-range reuse avoid repeated work, with separate bounds and lifetimes. Geometry idle age counts mesh transactions, not every displayed frame. Same-thread context recreation/address reuse remains a verification gap, not a demonstrated failure. [Bridge](JAVA-BRIDGE.md) · [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Terrain and block entities:** solid/cutout compact selection preserves graph visit order; translucent selection stays back to front. This is not a strict Euclidean near-to-far sort. Shader-disabled block-entity extraction consumes visited built sections plus global block entities when a current search exists; shader frames and unavailable-search cases keep the range-scan path. Java still owns the semantic extraction. [Selection boundary](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **DH and resources:** generic boxes use bounded primitive buffers and a three-slot pending ring; source opaque and late-water ranges each use an ordered pass. Ordinary DH pack-set release filters changed resource roles; exact-atlas teardown remains broader. Empty world geometry pages retire only their dependent bindings. These changes do not establish long-run bounds or cross-route temporal parity. [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Java and diagnostics:** four exact-key sky samples, throttled disk-state checks, single-pass chunk vertex construction, bulk native-profile reads and optional per-item phases reduce collection/instrumentation work. Disk-state reuse is unconditional within a nonzero frame epoch and may outlast 250 ms; the interval applies between epochs. [Profiling](SHADER-TERRAIN-PROFILING.md) · [Verification](RENDER-VERIFICATION.md)

This review inspected pinned source and test definitions only. It did not rerun
Java/native tests, live captures or benchmarks, and did not inspect the author's
unbundled artifacts. The [verification guidance](RENDER-VERIFICATION.md#october-7-residency-and-selection-checks)
distinguishes new assertions from remaining lifecycle and visual checks.

#### Earlier native-selection checkpoint

The following review retains its [`313e7a8a`](https://github.com/HungLo2020/MattMC/commit/313e7a8a82a34dc915c4924a78da77c720af2f7e) scope; later ordering and residency
changes above are not covered by its byte-identical-record report.

- **Ordinary terrain selection:** Rust now forms compact camera layers, optional off-camera shadow candidates and animated-section positions from graph visits and mirrored published mesh rows. Java copies native-layout records without its ordinary visible-list/per-section record construction. It still owns readiness/build scheduling, mesh publication, animated-sprite marking and semantic producers; diagnostics, faults, reloads, explicit per-record mode and identity-receipt frames retain the Java producer. This advances the visibility phase without completing all retained-scene phases. [Selection boundary](RENDER-ARCHITECTURE.md#resource-ownership-and-retries) · [Bridge lifetime](JAVA-BRIDGE.md#standalone-query-handles)
- **Camera entity culling:** the Sodium option gates an additional visited-section rejection before the ordinary frustum check. Glowing/name-visible entities and other documented bypass cases bypass the added rejection; the hook does not affect shadow-pass admission. This avoids some Java extraction work without moving entity semantics or adding Citadel model transport. [Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Direct DH particle order:** particle-source material quads draw after the direct non-G-buffer route's DH vanilla-fade composites, translucent terrain and receiver shadows. The regression checks command order for both fade modes; the fixture supports live tint/mip/rotation inspection. Model-mesh particles, Fabulous/G-buffer and selected shader-source paths remain separate. The earlier [#748 selected-shader alpha review](https://github.com/HungLo2020/MattMC/issues/748#issuecomment-5972310449) is not replaced by this ordering repair. [Particle scope](RENDER-ARCHITECTURE.md#shader-controls-at-startup) · [Verification](RENDER-VERIFICATION.md)

This review inspected source and test definitions at `313e7a8a`; it did not rerun
native/Java tests, capture live gameplay or independently inspect the author's
unbundled comparison/video artifacts. The three terrain-selection tests cover
ordering/flags, empty or unbuilt sections, animation selection, bounded shadow
candidates and duplicate/cleared mesh rows. The entity-culling commit adds no
dedicated camera-hook regression. Neither those definitions nor the particle
command-order check establish broad visual or temporal acceptance.

### October 6 source review

The following bullets preserve the earlier
[`121ad13c` checkpoint](https://github.com/HungLo2020/MattMC/commit/121ad13c84e45555c34814d54a8199194b37f39c). In particular,
its Java-visible-list and camera-limit descriptions predate ordinary native
record selection; use the October 7 scope above for current ownership.

- **Queued whole frames:** the calling thread decodes and copies the queued request before Java releases its arena. The native FIFO then acquires, executes and presents frames, ordered with queued mesh updates and atlas ticks. Java keeps at most one frame queued ahead; non-queued context-registry access joins pending work and standalone queries remain separate. Captures drain to the synchronous route. The earlier worker-decode/borrowed-arena contract remains only for the single in-flight fallback when queuing is disabled. This does not establish all cancellation/failure recovery. [Bridge lifetime](JAVA-BRIDGE.md#pipelined-frames)
- **Compact and retained terrain:** ABI 69/70 carries compact shadow casters and camera sections. Admitted shader frames use current-generation retained records and per-facing scene groups; pending, undescribed or camera-sorted entries retain ordinary expansion. Vanilla, Fabulous and frames leaving that route expand compact terrain. Diagnostic/fault/reload paths still support per-record data. These are partial scene capabilities, not completion of every planned phase. [Current scene route](RETAINED-SCENE.md#current-scene-terrain-on-the-shader-route)
- **Camera and shadow selection:** the Rust section graph follows Frozen-derived search rules, while Java mirrors readiness/build facts, consumes visited sections and schedules work. Shadow candidates use already-built geometry, with the leaf test also applied to camera-visible twins and supplement faces; no shadow-only build sweep is retained. Java's current limits remain 4,096 camera sections and 12,288 shadow-candidate sections, with different overflow handling. Region draw order, Iris's non-culling frustum and a scene-owned visible list remain outstanding. [Selection boundary](RENDER-ARCHITECTURE.md#resource-ownership-and-retries)
- **Entity shadow prefilter:** a standalone Rust query uses the active pack's copied shadow policy before Java extracts off-camera entities; unresolved policy keeps candidates. The frame plan applies admission again. Java still extracts poses/geometry for retained candidates, and this does not repair the Citadel geometry limitation in [#803](https://github.com/HungLo2020/MattMC/issues/803). [Query contract](JAVA-BRIDGE.md#standalone-query-handles)
- **Submission and reuse:** host-buffer writes with safe local ordering move to each command list's start; staging reuses best-fit chunks and keeps smaller idle chunks first. Fullscreen plans park and reuse complete matching inputs with up to four variants per stage path. Source-role, voxel-selection and shared entity-uniform memos avoid repeated work. [GAL](VULKANIC-GAL.md) · [Profiling](SHADER-TERRAIN-PROFILING.md)
- **Source data and color history:** terrain/entity/hand source vertices now pack into 64 bytes and decode to the same eight semantic lanes. Initialized clear-enabled feedback targets skip dead end-of-frame copies, and valid mip chains survive until level-zero changes. Java raw biome sky/fog samples reuse exact position/partial-tick/game-time keys; Java still owns those semantic producers. [Architecture](RENDER-ARCHITECTURE.md)
- **Validation and evidence:** normal release play skips per-frame GAL op/handle/hazard checks; debug builds, tests, watched uploads and explicit GAL-validation runs keep them. Standard validation captures enable the checks. Optional benchmark segment means describe sample-order slices, not broad parity. Source presence and local benchmark changes do not certify acceptance. [Verification](RENDER-VERIFICATION.md) · [Native builds](../tooling/NATIVE-BUILDS.md) · [Artifact storage](ARTIFACT-STORAGE.md)


The earlier `2fff1ef` checkpoint established this scope:

- **Shader sources and inputs:** broader single-color programs, terrain and particle alpha policy, selected custom textures and sampler aliases, typed bounded custom expressions, built-in celestial/light inputs, scoped color/shadow directives, exact R16F, begin/prepare passes, and legacy fullscreen/sky/horizon/celestial transforms. Supported contracts still gate admission; this is not compatibility with every Iris pack. [Implementation](RENDER-ARCHITECTURE.md#shader-controls-at-startup)
- **Entity shadows:** ABI 68 carries immutable entity/leash bounds, camera origin, eligibility and shadow roles for Rust-owned selection. Typed orb ordering survives shadow-only mesh filtering. Rebuild Java and native code together. [Bridge](JAVA-BRIDGE.md) · [Ordering checks](ENTITY-SHADOW-CHECKS.md)
- **DH and terrain:** CPU lifetime leases and selected-column replacement protection, correct snapshot usage through DOUBLE_PASS fading, spectator-in-solid visibility, and fogged vanilla lower-sky geometry. These address separate bounded failure cases. [Resource ownership](RENDER-ARCHITECTURE.md#resource-ownership-and-retries) · [Movement checks](TERRAIN-MOVEMENT-CHECKS.md)
- **Cost and diagnosis:** whole-frame GUI item-cache eviction, packed terrain instances/shared uniforms, earlier shadow selection, reduced hazard bookkeeping allocations, guarded audit formatting, improved timing/resource counters and bounded capture/movement/resize/overlap observers. [GAL](VULKANIC-GAL.md) · [Verification](RENDER-VERIFICATION.md)

Admission remains bounded. The source supports [at most eight color targets](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/rust/render/shaderpack/resources/color_targets/declarations.rs#L5-L7), and custom properties use the [supported typed expression grammar and functions](RENDER-ARCHITECTURE.md#shader-controls-at-startup), including scalar operations and vector construction. Unsupported active functions remain an admission failure. Selected POM, generated-normal and anisotropic-filter settings retain their [unsupported-feature gates](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/rust/render/shaderpack/contracts/terrain.rs#L1562-L1583). These boundaries prevent a blanket shader-pack compatibility claim.

### October 4 follow-up

- **Shader inputs and shadows:** source-derived integer texel addresses now use the sampled target's row convention; manifest-free aliases resolve supported Rust-owned images under the actual stage's properties. Empty selected-source frames initialize and retain owned shadow snapshots, rollback retires frontend consumers before runtime images, and terrain shadow casters retain faces omitted from camera color draws. Admission remains bounded. [Shader architecture](RENDER-ARCHITECTURE.md#shader-controls-at-startup) · [Capture inputs](RENDERDOC-INPUTS.md)
- **DH and terrain:** fully opaque leaf colors stay in the opaque DH stream; built-in DH lighting preserves dark skylight rows. The CPU terrain source combines urgent edits, one nearest-section dispatch in four, current portal-frontier work and immediate loaded-air connectivity. These are separate repairs; nearby geometry can still arrive late in cold first-turn observations. [Ownership and dispatch](RENDER-ARCHITECTURE.md#resource-ownership-and-retries) · [Movement checks](TERRAIN-MOVEMENT-CHECKS.md)
- **Costs:** per-pass immutable terrain uniforms, cached complete repeated-mesh plans, bounded foil copies and reduced snapshot/diagnostic allocations avoid measured work. Java retains the CPU producers described in the architecture. The recorded component reductions do not establish an overall FPS gain. [Shader terrain profiling](SHADER-TERRAIN-PROFILING.md)
- **Diagnosis and termination:** completed capture receipts retain exact frame/submission uniforms and attachment extents; absent optional snapshots are marked unavailable. The harness rejects an observed owned-client core dump even when the wrapper exits 143. Best-effort renderer console writes repair a reproduced closed-pipe abort; a separate disconnect SIGSEGV remains unresolved. [Verification](RENDER-VERIFICATION.md) · [GAL console contract](VULKANIC-GAL.md)

## What the recorded evidence establishes

The following paragraphs retain the earlier October 3–4 evidence. Later retained-scene reports and the October 6–7 speed summaries are separated below; none is a universal runtime certificate.

The [earlier progress log](https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/PROGRESS.md) and [follow-up progress log](https://github.com/HungLo2020/MattMC/blob/37817e128b99b07456c0ee22a5d34eaf05c72150/PROGRESS.md) contain the implementation author's test and capture results. Those Java, Rust, native and gameplay runs were not rerun by this documentation review; their ignored capture artifacts are not bundled with the wiki. The earlier independent review passed 60 Python rendering-tool tests; the follow-up tracker review passed 79 isolated Python rendering-tool tests at `37817e1`. These verify tooling scope only.

The earlier log records passing scoped Complementary static comparisons, hidden/visible leaf-particle cutout comparisons, first selected-source frames for specific Complementary and MakeUp/DH-off workloads, and bounded DH movement repeats after repairing selected-column asset-ack pruning. It also records before/after observations for spectator terrain and dark-sky-disc fog. These results apply to the documented workloads and keep their original tolerances.

MakeUp's latest listed image pair still exceeds tolerance 6. The older Iris+DH V6 far-extension check remains a failed historical result: its per-channel error is 9.686/15.968/11.238 even though the whole-image average is lower. The later coast results below apply to their own recorded workloads and do not erase that verdict. A clean validation log, completed run, compiled shader or low whole-image average cannot replace the required scoped image comparison.

The follow-up's [recorded coast comparisons](RENDER-VERIFICATION.md#4-performance-ab) pass the unchanged tolerance 6 after the individual fixes: shader-disabled DH has whole-image RGB MAE 0.943/1.205/1.420 and DH-region 2.043/1.587/1.473; the later original-pack Iris+DH capture, after correcting optional-snapshot readback, has whole-image 3.671/4.177/3.852 and DH-region 4.951/5.389/4.726. Opaque leaf crowns and the integer-depth light-shaft path have their own retained before/after evidence. These are settled coast poses, not motion, first-frame, broad parity or resource-bound acceptance.

[Cold first-turn observations](TERRAIN-MOVEMENT-CHECKS.md) still show delayed nearby terrain/water. Correlated diagnostics establish missing regular geometry in one cold frame; diagnostic timing does not measure ordinary presentation latency. Historical sessions rejected by later core-record audits remain rejected. Bounded clean console-fix repeats address the closed-pipe abort only; they do not resolve the independent disconnect SIGSEGV.

The [original-pack underground comparison](UNDERGROUND-SHADER-CHECKS.md) still fails (RGB MAE 20.566/15.041/10.029). Null-depth and disabled-volumetric diagnostic comparisons are causal evidence only. Production fog and light shafts remain enabled, and the underground exception decision remains pending.

[Per-pass preparation measurements](RENDER-VERIFICATION.md#4-performance-ab) record reductions of about 18%, while [repeated-mesh batching measurements](SHADER-TERRAIN-PROFILING.md#repeated-mesh-plans) record reductions of 13–15%. Those historical repeated-mesh Current runs were about 34–35 FPS against Frozen about 304–308 FPS; varying readiness and live populations limit comparisons. No overall FPS improvement or broad performance acceptance is established.

### Latest author-recorded workloads

#### October 9 native world and hand input summary

The [summary at `64294324`](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/SUMMARY.md)
records release `0d54a098` on RTX 3080 Ti: moving-camera ABAB, two runs per side
and mode, with exactly 6,000 measured frames each. These are author reports;
this maintenance review did not run the clients or inspect raw runtime receipts.

| Mode | Recorded Current FPS, run 1 / run 2 | Recorded Frozen FPS, run 1 / run 2 | Median run p99, Current / Frozen (ms) |
| --- | --- | --- | --- |
| Vanilla | 1,076.9 / 1,457.3 | 1,178.0 / 1,161.7 | 3.506 / 3.019 |
| Vanilla + DH | 748.1 / 738.2 | 797.4 / 602.7 | 4.862 / 5.604 |
| Shaders | 356.3 / 351.8 | 319.6 / 320.0 | 4.725 / 5.826 |
| Shaders + DH | 257.1 / 246.4 | 228.4 / 226.6 | 6.733 / 7.695 |

**Performance FAIL:** all median average-FPS floors pass, but vanilla p99 fails.
Current vanilla and Frozen DH vary substantially between repeats; differing
streaming work is a lead, not a proven tail cause. The author records 2,432 Rust
tests (three ignored), 1,793 full Java tests (two skipped), seven lifecycle
cases, reviewed coast/HUD pairs and sixteen clean performance rows. Receipt:
`validation/native-world-item-final-20261009/summary.json`.

The [working record](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/PROGRESS.md)
separates this measured source from subsequent observer corrections: 184 affected
Java cases and a fresh strict held-clock foil capture pass after reload-boundary
and managed-memory-path fixes. That short-session image/memory result does not
remeasure the full performance matrix or prove long-run bounds. Ground pairs
closely match live Frozen but fail older fixed probes; the strict ground fixture
remains unaccepted. The prior GUI-item benchmark also predates integration of
`111d7a9c`; its later focused checks and paired images have separate scope.

Sampled source-flag allocation falls from about 194 MB to zero per 15 seconds,
and world-matrix samples also fall, while total Java estimates vary across
profiles. These diagnostic observations do not isolate throughput. Earlier
cloud, counter and GUI-item workflows retain their failed floors and original
release identities in the pinned working record; their results are not additive.

#### October 9 native generation handoff summary

The [summary at `a908f78c`](https://github.com/HungLo2020/MattMC/blob/a908f78cd909200f5f4f4424b124072cef0a17f6/SUMMARY.md)
reports release `c7c95f4a` on RTX 3080 Ti: moving-camera ABAB, two runs per side
and mode, with exactly 6,000 measured frames each. These are author-reported
results, not measurements made by this documentation review.

| Mode | Recorded Current FPS, run 1 / run 2 | Recorded Frozen FPS, run 1 / run 2 | Median average-FPS change | Median run p99, Current / Frozen (ms) |
| --- | --- | --- | --- | --- |
| Vanilla | 1,027.8 / 1,033.3 | 1,101.9 / 1,127.6 | −7.6% | 4.021 / 3.467 |
| Vanilla + DH | 615.2 / 610.6 | 731.2 / 614.0 | −8.9% | 6.649 / 5.731 |
| Shaders | 326.9 / 332.2 | 312.4 / 307.6 | +6.3% | 6.223 / 6.820 |
| Shaders + DH | 244.2 / 249.5 | 220.6 / 221.2 | +11.7% | 7.151 / 9.051 |

**Performance FAIL:** vanilla/DH fail both floors; shader modes pass both in
this workload. Frozen DH repeats vary substantially. The author reports 2,417
Rust tests (three ignored), 1,751 full Java tests (two skipped), 26 focused Java
tests, all seven lifecycle cases, reviewed vanilla/Iris+DH coast pairs and wiki
2,485 pages/43 indexes passing. All sixteen performance rows are clean; VUIDs,
exceptions and owned orphans are zero, source/library/user-edit/Frozen integrity
passes, and 25 generated copies were retired. Receipt:
`artifacts/graphics-captures/validation/native-stage-handoff-final-20261009/summary.json`.
The diagnostic Java handoff allocation estimate falls from about 108 to 1–3
KB/chunk; it includes sampling/observer limits and is not throughput acceptance.

The earlier [map/state summary](https://github.com/HungLo2020/MattMC/blob/84016f210afdf7d5a8c6a61f9440e8f304f6aa74/SUMMARY.md)
preserves its failed original workflow and separate accepted shader+DH repeat.
The [DH frame record](RETAINED-SCENE.md#native-dh-visibility-frame-ownership)
separates the pre-hardening sixteen-row comparison from final-release diagnostic
profiles. [Color](../world/biome/RUST-SECTION-COLORS.md#work-and-verification),
[snapshot](../world/chunk/RUST-SECTION-SNAPSHOTS.md#verification) and
[live-storage](../world/chunk/RUST-LIVE-SECTIONS.md#verification) records retain
later distinct test/profile windows. None proves a migration-specific FPS gain,
broad visual/temporal parity, long-run resource bounds or a Rust-only application.

#### October 8 native block families summary

The [summary at `48a6e051`](https://github.com/HungLo2020/MattMC/blob/48a6e051ecd8bc322fb41118004edb3f13b41932/SUMMARY.md)
records native block-family configuration on RTX 2070: moving camera, ABAB,
two runs per side and mode, and 6,000 measured frames per run.

| Mode | Recorded Current FPS, run 1 / run 2 | Recorded Frozen FPS, run 1 / run 2 | Median average-FPS change | Median run p99, Current / Frozen (ms) |
| --- | --- | --- | --- | --- |
| Vanilla | 1,119.3 / 1,012.5 | 1,132.6 / 1,156.1 | −6.9% | 3.742 / 3.473 |
| Vanilla + DH | 597.1 / 559.9 | 833.0 / 793.2 | −28.9% | 7.973 / 4.275 |
| Shaders | 333.3 / 341.2 | 311.0 / 312.7 | +8.1% | 5.310 / 6.205 |
| Shaders + DH | 247.1 / 240.6 | 223.7 / 223.0 | +9.2% | 7.735 / 8.257 |

**Performance FAIL:** vanilla and vanilla+DH miss both average-FPS and p99
floors; both shader modes pass those floors in this workload. The larger DH gap
needs investigation with equivalent paired inputs and runtime identities;
comparison with an earlier milestone alone does not isolate family overhead.
The author reports all 16 runs clean with exact frame counts and zero VUIDs,
Java/Rust suites passing 1,711/2,366 tests with two skips/three ignores, all seven
lifecycle cases passing and five Frozen observer pairs matching all eight
content digests. Reviewed vanilla/Iris+DH coast RGB errors are
0.304/0.542/0.642 and 3.664/4.182/3.826, with the DH subset passing.

The reported receipts are
`artifacts/graphics-captures/validation/native-block-families-master-20261008/summary.json`
and `build/block-families-master-verification-20261008/results.json`.
Source/native integrity and unchanged Frozen are author-recorded checks;
25 generated fixture copies were reportedly retired. This documentation review
did not inspect those unbundled receipts or rerun runtime, image or cleanup
work. These reports do not establish broad parity, a speedup attributable to
family ownership or Rust-only application completion.

#### October 8 native sound and offsets summary

The [summary at `059c9562`](https://github.com/HungLo2020/MattMC/blob/059c9562134b2e0056b31dfec0982bafa4762abd/SUMMARY.md)
records the preceding sound/offset milestone on RTX 2070 with the same stated
moving-camera ABAB protocol and 6,000 measured frames per run.

| Mode | Recorded Current FPS, run 1 / run 2 | Recorded Frozen FPS, run 1 / run 2 | Median average-FPS change | Median run p99, Current / Frozen (ms) |
| --- | --- | --- | --- | --- |
| Vanilla | 1,152.5 / 1,124.0 | 1,170.6 / 1,175.3 | −3.0% | 3.559 / 3.007 |
| Vanilla + DH | 613.2 / 624.7 | 669.9 / 617.4 | −3.8% | 6.987 / 6.581 |
| Shaders | 348.0 / 345.9 | 320.4 / 318.3 | +8.6% | 5.678 / 5.860 |
| Shaders + DH | 249.8 / 249.0 | 228.2 / 227.1 | +9.6% | 8.001 / 7.816 |

**Performance FAIL:** vanilla and vanilla+DH miss both average-FPS and p99
floors; shaders+DH also misses p99. The author reports all 16 runs clean,
exact frame counts and zero VUIDs; Java/Rust suites pass 1,708/2,363 tests with
two skips/three ignores. Seven lifecycle cases and five observer pairs matching
all seven content digests pass, including 9,250 offset samples. Reviewed
vanilla/Iris+DH coast RGB errors are 0.252/0.401/0.463 and
3.695/4.222/3.867, with the DH subset passing.

The reported receipts are
`artifacts/graphics-captures/validation/native-block-materials-master-20261008/summary.json`
and `build/block-materials-master-verification-20261008/results.json`.
Source/native integrity and unchanged Frozen are author-recorded checks;
25 generated fixture copies were reportedly retired. These runtime, image and
cleanup claims were not independently rerun or checked against their original
artifacts. No isolated sound/offset speedup or broad runtime acceptance follows.

#### October 8 native block intrinsics summary

The [summary at `d0141162`](https://github.com/HungLo2020/MattMC/blob/d0141162d81eee184fa99f0b7a9411d401c306b4/SUMMARY.md)
records native intrinsic state rules before the sound/offset draft: RTX 2070,
moving camera, ABAB and 6,000 measured frames per run.

| Mode | Recorded Current FPS, run 1 / run 2 | Recorded Frozen FPS, run 1 / run 2 | Median average-FPS change | Median run p99, Current / Frozen (ms) |
| --- | --- | --- | --- | --- |
| Vanilla | 1,107.5 / 1,106.4 | 1,149.1 / 1,201.9 | −5.8% | 3.582 / 3.184 |
| Vanilla + DH | 626.0 / 630.0 | 792.3 / 582.2 | −8.6% | 6.233 / 5.789 |
| Shaders | 326.8 / 328.3 | 310.5 / 308.3 | +5.9% | 5.920 / 6.156 |
| Shaders + DH | 245.0 / 244.1 | 221.8 / 221.0 | +10.5% | 7.674 / 8.325 |

**Performance FAIL:** vanilla and vanilla+DH miss both average-FPS and p99
floors; both shader modes pass those floors in this run. The author reports all
16 runs clean with exact frame counts and zero VUIDs, Java/Rust suites passing
1,704/2,359 tests with two skips/three ignores, all seven lifecycle cases passing
and five Frozen observer pairs matching all six content digests. Reviewed
vanilla/Iris+DH coast RGB errors are 0.312/0.515/0.600 and 3.763/4.359/3.997,
with the DH subset passing. The receipt is
`artifacts/graphics-captures/validation/native-block-intrinsics-master-20261008/summary.json`;
25 generated fixture copies were reportedly retired. These runtime, image and
cleanup claims are author records, not independently inspected artifacts or
reruns by this documentation review. No isolated intrinsic-state speedup,
broad parity or Rust-only application completion is established.

#### October 8 native block physics summary

The [summary at `1b183793`](https://github.com/HungLo2020/MattMC/blob/1b1837931a9b25fa12b655e440c6ff77cdd5887d/SUMMARY.md)
records the historical native physical-settings milestone before intrinsic-state
integration:
RTX 2070, moving camera, ABAB and 6,000 measured frames per run.

| Mode | Recorded Current FPS, run 1 / run 2 | Recorded Frozen FPS, run 1 / run 2 | Median average-FPS change | Median run p99, Current / Frozen (ms) |
| --- | --- | --- | --- | --- |
| Vanilla | 1,004.7 / 1,054.4 | 1,111.5 / 1,124.9 | −7.9% | 3.627 / 3.752 |
| Vanilla + DH | 495.7 / 703.1 | 705.4 / 598.2 | −8.0% | 7.310 / 5.991 |
| Shaders | 322.9 / 325.7 | 303.2 / 306.3 | +6.4% | 11.447 / 7.323 |
| Shaders + DH | 238.6 / 242.8 | 217.9 / 216.3 | +10.9% | 14.605 / 9.860 |

**Performance FAIL:** vanilla and vanilla+DH miss the average-FPS floor; only
vanilla passes p99, while the other three modes fail. The author reports all
16 runs clean, exact frame counts and zero VUIDs. Reported Java/Rust suites pass
1,701/2,357 tests with two Java skips/three Rust ignores; all seven lifecycle
cases and five content digests pass. Reviewed vanilla/Iris+DH coast RGB errors
are 0.210/0.355/0.390 and 3.726/4.268/3.871, with the DH subset passing.
These successes do not turn the combined workflow into a pass or establish an
isolated physics speedup, broad parity or Rust-only application completion.
The original checkout's receipt is
`artifacts/graphics-captures/validation/native-block-physics-master-20261008/summary.json`;
25 generated fixture copies were reportedly retired. Those runtime and cleanup
claims were not independently rerun or checked against the unbundled artifacts.

Keep earlier ownership measurements separate. The
[fluid report retained at `df6c6dc7`](https://github.com/HungLo2020/MattMC/blob/df6c6dc77d0e382b13fa8fcd62317f512ebf0b94/PROGRESS.md)
records vanilla/DH FPS deficits of 6.0%/18.6% and four failing p99 comparisons.
The [property summary at that checkpoint](https://github.com/HungLo2020/MattMC/blob/df6c6dc77d0e382b13fa8fcd62317f512ebf0b94/SUMMARY.md)
records deficits of 6.3%/0.5%, with both shader modes passing their FPS and p99
floors in that run. The
[block-definition summary at `2f2158cc`](https://github.com/HungLo2020/MattMC/blob/2f2158cc46f99f7ba3263309e5daefea9874608a/SUMMARY.md)
records vanilla −9.4%, DH +3.7%, shaders +8.8% and shaders+DH +11.5%, with all
four p99 comparisons failing; it uses a separately repeated vanilla set after
earlier attempts were excluded. Each workflow remains a historical performance
FAIL. Differences between these sessions are not isolated migration gains.

#### October 8 recorded candidate summary

The [refreshed summary at `97e30922`](https://github.com/HungLo2020/MattMC/blob/97e3092269ed29854c8175a480a819fb1896c311/SUMMARY.md)
records candidate `d7ee0335d` from a separate original checkout on the RTX 2070:
moving camera, ABAB and 6,000 measured frames per run. The source milestone
itself changes no runtime code.

| Mode | Recorded Rust/Vulkan candidate FPS, run 1 / run 2 | Recorded Frozen Java/OpenGL FPS, run 1 / run 2 |
| --- | --- | --- |
| Vanilla | 1,123.0 / 1,067.9 | 1,200.4 / 1,174.9 |
| Vanilla + DH | 601.7 / 705.0 | 740.2 / 682.4 |
| Shaders | 352.4 / 348.7 | 320.9 / 316.1 |
| Shaders + DH | 252.3 / 243.3 | 227.7 / 225.9 |

Each slash separates runs. The retained record is
`artifacts/graphics-captures/validation/batch2/summary.json` in that original
checkout; raw benchmark receipts are unavailable. Median average FPS is below
Frozen by **7.8% vanilla** and **8.1% vanilla+DH**, and above it by **10.1%
shaders** and **9.3% shaders+DH**. These reported medians do not establish
all-mode performance acceptance, verified per-run health or matching p99.
Fresh complete paired evidence is required under the
[current performance controls](SHADER-TERRAIN-PROFILING.md#october-8-acceptance-controls).
The older tables below retain their own revision/session scope; do not combine
them with this separate candidate record or infer a runtime gain from the
verification-only source change.

#### October 7 late-evening interleaved summary

The [22:36 summary at `697b0a3c`](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/SUMMARY.md) records two moving-camera
runs per side and mode on the RTX 2070 desktop, interleaved current/Frozen/
current/Frozen (ABAB), with 6,000 measured frames each:

| Mode | Rust/Vulkan FPS, run 1 / run 2 | Frozen Java/OpenGL FPS, run 1 / run 2 |
| --- | --- | --- |
| Vanilla | 1,171 / 1,130 | 1,226 / 1,123 |
| Vanilla + DH | 757 / 770 | 736 / 599 |
| Shaders | 343 / 351 | 318 / 316 |
| Shaders + DH | 250 / 252 | 228 / 227 |

These slashes separate runs, not FPS and median frame time. The driver requests
360 settle and 240 warm-up frames before measurement. The author reports all
runs clean (zero VUIDs/exceptions) and up to ±20% desktop run-to-run noise;
the two Frozen vanilla+DH runs alone differ from 736 to 599 FPS. In this
bounded comparison both shader modes and vanilla+DH have higher reported Rust
FPS; vanilla overlaps. The summary supplies no per-run tail statistics or
component isolation, and this review did not rerun or inspect the artifacts.

The earlier [20:32 single-run summary](https://github.com/HungLo2020/MattMC/blob/72b8cea2e63a7186830e6c647a657745aa771969/SUMMARY.md)
also used 6,000 frames, but reported vanilla+DH 617 versus 736 FPS and about
±25% noise. Its other rows and medians remain preserved in that pinned source
and the [integration verification history](RENDER-VERIFICATION.md#october-7-integration-batch-checks).
The later reversal does not isolate a shared-page speedup or establish lasting
all-mode performance parity. DH shared pages are implemented; multi-draw
indirect remains next work. Keep both records separate from the older mixed
windows below and retain visual, temporal and resource-bound acceptance work.

#### October 7 evening same-session summary

The [summary at `f13239e1`](https://github.com/HungLo2020/MattMC/blob/f13239e10d0f66d244c4311c091d0d60819fb391/SUMMARY.md)
preserves the earlier moving-camera measurements on the same desktop and
evening session; it predates the equal-window reports above:

| Reported mode | Rust/Vulkan FPS / median frame | Frozen Java/OpenGL FPS / median frame |
| --- | --- | --- |
| Vanilla | 813–882 / 0.84–0.89 ms; 1,800 frames | 900 / 0.87 ms |
| Vanilla + DH | 631 / 1.50 ms; 6,000 frames | 671 / 1.28 ms |
| Vanilla + DH, shorter runs | 440–549 / 1.53–1.75 ms; 1,800 frames | Same reported Frozen row; duration unspecified |
| Shaders | 311–349 / 2.60–2.93 ms | 304 / 3.04 ms |
| Shaders + DH | 223–241 / 3.99–4.10 ms | 226 / 4.20 ms |

These are author-recorded ranges, not independently rerun measurements. The
brief summary does not state every row's measured duration, warm-up or tail
statistics, and its two Rust vanilla+DH windows must stay separate. It does
not establish equal-window performance parity: vanilla mean FPS remains below
Frozen even where medians are close, and the shader+DH range straddles its
Frozen mean. These values were the fresh evening controls at that checkpoint;
they are not interchangeable with the later equal-window sessions above.

The remaining vanilla+DH cost was attributed to roughly 430 per-column DH
draws and descriptor-set binds. DH column geometry now lives in shared device
pages; packed non-deferred passes reuse a geometry set per vertex page within
each pass ([architecture](RENDER-ARCHITECTURE.md)). Multi-draw indirect is still
proposed. See
[profiling controls](SHADER-TERRAIN-PROFILING.md#october-7-comparison-controls).
Scoped image passes, lifecycle reports and timing ranges establish different
things; none closes the [remaining work](#remaining-work).

#### October 7 midday and later performance reports

The [midday summary](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/SUMMARY.md) retains these author-recorded
moving-camera rows; the source identifies earlier-session controls explicitly:

| Reported workload | Rust/Vulkan FPS / median frame | Frozen Java/OpenGL FPS / median frame |
| --- | --- | --- |
| Shaders, 1,800 frames | 314–316 / 2.98–3.00 ms | 307–309 / 2.99 ms, earlier session |
| Shaders + DH, 1,800 frames | 232 / 4.06 ms; p99 10.3 ms | 232 / 4.11 ms; p99 8.0 ms |
| Vanilla + DH, 1,800 frames | 342–346 / 2.00–2.05 ms | 271 / 2.99 ms; earlier 415 no longer reproduces |
| Vanilla, 1,800 frames | 707–808 / 0.80–0.95 ms | 1000 / 0.84 ms |
| Vanilla, 1,800 after 6,000 warm-up | 1018 / 0.81 ms | 1395 / 0.66 ms, earlier session |
| Long vanilla with profiler | 1590–1630 / 0.51–0.53 ms, 60,000 frames | 1462 / 0.52 ms, 30,000 frames, earlier session |

Equal shader+DH mean FPS still leaves a p99 gap. Long vanilla rows differ in
frame count/session and do not reverse the earlier unequal-warm-up retraction.
The [progress log](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/PROGRESS.md) also retracts attribution of an early
676 FPS graph result: same-build controls spanned 545–676 FPS. Lower C2 thresholds
and later graph/decode/glint/page-sort/DH changes have local attribution reports;
these are not additive gains or a fresh four-mode acceptance matrix. The latest
assembly commit reports shaders 332 and vanilla 882 FPS without fresh paired
Frozen controls in that statement. Preserve the older tables below as dated
evidence, not current equivalent-baseline claims. No benchmark was rerun here.

#### October 7 mode summary

The [source-pinned summary](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/SUMMARY.md) reports moving-camera runs with
matching settings. FPS and median frame time are distinct aggregate measures:

| Reported workload | Rust/Vulkan FPS / median frame | Frozen Java/OpenGL FPS / median frame |
| --- | --- | --- |
| Shaders, 1,800 frames | 314 / 2.98 ms | 307–309 / 2.99 ms |
| Shaders + DH, 1,800 frames | 223 / 4.21 ms | 228 / 4.19 ms |
| Vanilla + DH, 1,800 frames | 417 / 2.01 ms | 415 / 2.08 ms |
| Vanilla, 1,800 frames after 240 warm-up | 595 / 1.37 ms | 937 / 0.84 ms |
| Vanilla, 1,800 frames after 6,000 warm-up (earlier that day) | 912 / 0.94 ms | 1395 / 0.66 ms |
| Vanilla, 30,000 frames after 6,000 warm-up (earlier that day) | 1334 / 0.64 ms | 1462 / 0.52 ms |

These are implementation-author reports, not independent reruns or broad
performance acceptance. The [progress log](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/PROGRESS.md#L13)
records latest short-run ranges of shaders 305–314 and shaders+DH 215–223 FPS,
with shaders+DH p99 about 14 ms against Frozen 9.3 ms. Its earlier unequal-warm-up
vanilla parity claim is explicitly withdrawn; the equal-warm-up rows above
still show a gap. Do not combine these windows, component timings or successive
local optimizations into additive gains. Recorded scoped image passes keep
their original workloads and tolerances; failed underground comparisons,
terrain readiness/flicker, long-run resource bounds and the independent native
crash remain open.

#### Updated October 6 summary reviewed October 7

The [updated source-pinned summary](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/SUMMARY.md)
still describes moving-camera workloads of 1,800 frames:

| Reported workload | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Complementary shader FPS / median frame | 280–294 FPS (latest 294) / 3.28 ms | 307 FPS / 2.99 ms |
| Shader last 600 frames, mean frame time | 3.10–3.24 ms | 3.20 ms |
| Shader GPU frame time | 3.18–3.23 ms | About 3.0 ms |
| Vanilla FPS / median frame | 469–492 FPS / 1.73 ms | 937 FPS / 0.84 ms |

The [same revision's progress log](https://github.com/HungLo2020/MattMC/blob/313e7a8a82a34dc915c4924a78da77c720af2f7e/PROGRESS.md#L13)
reports the native terrain-selection comparison as one pair per mode:
398→469 FPS vanilla and 280→294 FPS shaders, plus byte-identical compact records
over 1,800 frames in each mode. These local pairs and the summary's ranges are
different reports, not repeated independent confirmation or additive gains.
Its entity-culling record explicitly rejects the unusually low vanilla error
0.045 as entity evidence because that capture contains no entities; the later
RGB error 0.202/0.347/0.382 is attributed to the usual water-animation variance.

The falling-leaf fix has an author-reported fixture/window-video before/after
and a source command-order regression. None was rerun by this documentation
review. The last-600-frame slice does not establish full-run parity, and vanilla
worker/GPU timings (1.4/0.69 ms) are component measurements, not independent
costs to add to the frame median. Broad parity, repeated performance acceptance,
long-run resource bounds and the independent crash remain open.

#### October 6 speed summary

The [source-pinned `SUMMARY.md`](https://github.com/HungLo2020/MattMC/blob/121ad13c84e45555c34814d54a8199194b37f39c/SUMMARY.md)
reports moving-camera workloads of 1,800 frames:

| Reported workload | Rust/Vulkan | Frozen Java/OpenGL |
| --- | --- | --- |
| Complementary shader FPS / median frame | 271–288 FPS (latest 283–284) / 3.32–3.38 ms | 307 FPS / 2.99 ms |
| Shader last 600 frames, mean frame time | 3.10–3.24 ms | 3.20 ms |
| Shader GPU frame time | 3.18–3.23 ms | About 3.0 ms |
| Vanilla FPS / median frame | 451–472 FPS / 1.80 ms | 937 FPS / 0.84 ms |

These are implementation-author reports, not reruns by this documentation
review. The last-600 row is a different slice from the full 1,800-frame FPS
and median rows; it does not establish full-run performance parity. The summary
attributes the shader gap to early warm-up/streaming and heavier terrain/shadow
views, and the vanilla gap to CPU work. Its vanilla worker/GPU times (1.40/0.68 ms)
are component timings, not additional independent frame costs.

The [same revision's progress log](https://github.com/HungLo2020/MattMC/blob/121ad13c84e45555c34814d54a8199194b37f39c/PROGRESS.md#L12-L13)
retains author-reported scoped image comparisons and intermediate measurements,
including no measurable gain from mip-chain reuse. Its earlier double-buffered
Java staging description predates the current caller-decode
implementation. Neither these notes nor the speed summary supply new
independently inspected image/profile artifacts, long-run bounds or broad
Goal 5 acceptance.

#### Earlier retained-scene checkpoint

The [source-pinned progress log](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/PROGRESS.md#L4-L11)
records successive profiled moving-shader workloads, including 133 to 188 FPS
after shadow-selection changes. The section-graph step then records a regression
from 187.8 to 175.7 FPS while more translucent sections become visible; the
entity-shadow prefilter records 175.7 to 195.7 FPS. These are separate local
comparisons with changing admitted work, not additive gains or a final
Current-versus-Frozen parity result.

The latest query checkpoint reports Iris+DH RGB error 3.683/4.188/3.866 with its
DH check passing, vanilla 0.176/0.297/0.316, zero validation events, and 2,215
native tests passing with three ignored. Earlier Java renderer checkpoints
record known AtlasAnimation/ShieldAtlas failures and separately retried
instrumentation flakes; they are not a clean independent Java-suite pass.
These are implementation-author reports. This maintenance review did not rerun
the native/Java suites, live comparisons or performance workloads, or inspect
the unbundled image/profile artifacts. The coordinated review independently
ran six focused Python tooling tests; that result verifies tooling only.

Earlier failing image comparisons, cold-readiness observations and the
independent disconnect SIGSEGV remain historical evidence with unresolved
acceptance unless a specific later result addresses them. Atomic native-library
staging prevents an overwritten mapping hazard; it is not demonstrated closure
of that separate crash. A panic/waiter test establishes waiter release, not all
frame-outcome or cancellation semantics. Follow the current
[verification rules](RENDER-VERIFICATION.md) and retain failures alongside passes.

## Remaining work

- Citadel model extraction [#803](https://github.com/HungLo2020/MattMC/issues/803) and source-predicted orb-boundary remapping after rig expansion [#819](https://github.com/HungLo2020/MattMC/issues/819)
- Broad vanilla, DH, Iris and Iris+DH visual and temporal parity, including water, foliage, day/night/weather, entities/layers, hands, GUI and resource packs
- General terrain flicker investigation across all four routes, cold terrain/water readiness and the failed original-pack underground comparison; clean bounded videos do not establish acceptance
- The independent disconnect SIGSEGV, repeated entry/exit and broad native stability; the closed-pipe fix does not close these cases
- First selected-source frames for other packs and real transitions, plus reloads, dimensions, resize/fullscreen and repeated entry/exit
- Matching animated source clocks, repeated performance measurements and long-run/reload/large-radius CPU/GPU resource bounds

Offscreen source preparation helps initialize the selected graph before presentation. The current [admission code](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/rust/render/worldrender/source/admission.rs#L1612-L1667) still disarms incomplete frame coverage or unavailable DH depth and reports Rust-vanilla fallback. A fallback in a required shader frame is a failed Goal 5 workload, not evidence of selected-pack success. Follow [real-config checks](RENDER-VERIFICATION.md#3-real-config-session), capture stdout and stderr, and check actual presentation correlation.

Entity culling does not add Citadel model geometry transport. The conditional empty-model limitation tracked in [#803](https://github.com/HungLo2020/MattMC/issues/803) remains applicable; avoid converting source warnings into either universal crash claims or a declaration that imported mobs now render correctly.

For source-input investigations use [RenderDoc observations](RENDERDOC-INPUTS.md). For acceptance use equivalent Frozen **Java OpenGL** workloads and the unchanged [verification rules](RENDER-VERIFICATION.md); source and numerical tests remain supplemental.

## Tracked follow-up

The later October 7 [GUI residency review](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-6043636919),
[terrain/block-entity review](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6043638924),
[DH transport/batching review](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6043640406)
and [performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6043642312)
cover the `20e157ca` checkpoint without closing those issues. They preserve
lifecycle/test-coverage gaps and author-only runtime provenance; no new
native/Java run or benchmark is established by these source reviews.

The October 7 [terrain-selection review](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6029873623),
[performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6029874156)
and [DH particle-order review](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6029874814)
cover the `313e7a8a` source snapshot without closing those issues. They retain
Java/retained-scene limits, the single-pair performance provenance and the
direct-route particle scope. Source and test-definition inspection does not
establish new runtime, image or temporal acceptance.

The earlier [ownership review](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6027569797)
and [performance review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6027581285)
cover `121ad13c` without closing either issue. They distinguish source/test
inspection from the implementation author's measurements and captures; no
runtime suite or benchmark was rerun during this maintenance review.

The previously verified source-review comments record bounded progress without
closing these issues. They keep their original checkpoint scope; the October 6
source and speed-summary reconciliation and the October 7 source review above
do not themselves update tracker state:

| Topic | Verified comment link |
| --- | --- |
| Current rendering ownership, evidence and remaining Goal 5 acceptance | [#747](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-6010252617) |
| Pipelined/retained-scene performance scope and recorded regressions | [#709](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6010243435) |
| Compact terrain, native section graph and shadow-query boundaries | [#520](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-6010247010) |
| DH iterator repair and continuing integration limits | [#745](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-6010229884) |

Earlier verified tracker updates (preserved for provenance):

| Topic | Verified comment link |
| --- | --- |
| October 4 renderer follow-up and remaining Goal 5 acceptance | [#747](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5982772544) |
| October 4 performance progress and remaining measurements | [#709](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-5982769674) |
| October 4 DH integration and backport progress | [#745](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-5982770594) |
| October 4 terrain/shadow ownership progress | [#520](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-5982771278) |
| October 4 resource/capture evidence | [#758](https://github.com/HungLo2020/MattMC/issues/758#issuecomment-5982771898) |
| Separate player-distance ownership migration; no renderer acceptance claim | [#776](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-5982773396), [scope](../world/chunk-loading/RUST-PLAYER-DISTANCE.md) |

The verified progress comments below preserve the earlier `2fff1ef` checkpoint. They are historical scoped evidence, not acceptance of the October 4 follow-up. No issue closure or completed milestone is implied by this documentation reconciliation.

| Topic | Verified comment link |
| --- | --- |
| Canonical rendering reconciliation | [#747](https://github.com/HungLo2020/MattMC/issues/747#issuecomment-5972378509) |
| ABI 68; Rust shadow distance/frustum/caster selection with Java semantic producers retained | [#520](https://github.com/HungLo2020/MattMC/issues/520#issuecomment-5972365639) |
| Selected-shader particle alpha and author-recorded hidden/visible leaf comparison | [#748](https://github.com/HungLo2020/MattMC/issues/748#issuecomment-5972310449) |
| Scoped color/format lifecycle | [#758](https://github.com/HungLo2020/MattMC/issues/758#issuecomment-5972343949) |
| Single-color output routing | [#761](https://github.com/HungLo2020/MattMC/issues/761#issuecomment-5972344807) |
| Typed custom-uniform expressions | [#762](https://github.com/HungLo2020/MattMC/issues/762#issuecomment-5972364997) |
| Hazard ranges, source packing, shadow selection and GUI cache costs | [#709](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-5972324451) |
| GUI cache/first-frame integration and remaining UI/scene producer ownership | [#772](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-5972325295) |
| DH semantic leases/selected-generation lifetime, migration scope | [#777](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-5972366397) |
| Native DH DOUBLE_PASS/entry integration, continuing backport scope | [#745](https://github.com/HungLo2020/MattMC/issues/745#issuecomment-5972367046) |

# Shader terrain profiling

Use ordinary moving gameplay to measure selected-source terrain against Frozen
Java OpenGL. Static captures, validation, RenderDoc and temporary timers are
correctness or profiling evidence; they do not establish a throughput gain.
See [render verification](RENDER-VERIFICATION.md#4-performance-ab) for the shared
benchmark controls and [architecture](RENDER-ARCHITECTURE.md) for ownership rules.

## Reproduce the workload

Build the release library, pin the same original shader archive on both sides,
and use an owned copied save. Ordinary rows disable DH in both copied runs.
The unlimited FPS slider value is 260; preserve identical camera, render distance,
resource packs and settings. Retain each command, archive/native hashes and the
terminal benchmark artifact.

```sh
./gradlew buildRustNative -PmattmcRustProfile=release
MATTMC_CAPTURE_RUN_SOURCE=/absolute/path/copied/run \
MATTMC_CAPTURE_SHADER_PACK_SOURCE=/absolute/path/original-pack.zip \
MATTMC_CAPTURE_MAX_FPS=260 \
python3 DevUtils/PerfAudit/Gameplay.py --profile extended \
  --mode current-rust-vulkan-shaders-on --mode frozen-opengl-shaders-on \
  --repo-root "$PWD" --frozen-repo ../MattMC_JavaPerfTesting/MattMC \
  --artifact-root artifacts/graphics-captures/shader-moving-profile \
  --artifact-preserve-current-run --rust-profile release \
  --workload-profile moving-camera --capture-camera-pose 150.5,100,530.5,105,10 \
  --settle-frames 360 --warmup-frames 240 --measure-frames 1800 \
  --client-args enableShaders=true
```

Require `status=complete`, all requested measured frames, passing sampler checks,
normal client exits and no orphans. Inspect effective camera and workload counts;
matching copied inputs alone does not guarantee identical live entity populations.
The moving benchmark records its own initial pose in `cameraPath`; do not infer
its yaw or pitch from the deterministic capture's pose argument.
CPU phase entries contain `total` nanoseconds; divide by measured frame count.
For GPU phases, divide by the entry's actual sample `count`: asynchronous
completed-frame timestamps usually provide fewer samples than CPU frames.
Native draw/timeline samples cover a bounded prefix, while cumulative submission
counts also include readiness and warmup. Do not divide cumulative counts by
measured frames or add nested CPU and GPU timers as independent costs.

### Selected-source GPU timing

The world frontend now classifies the runtime's `fullscreen-source.*` pass and
pipeline labels. The existing `gpu-deferred-lighting` counter sums selected
`deferred*` stages, `gpu-composite-0` sums the complete selected `composite*`
family, and `gpu-final-output` measures selected `final`. Built-in graph counters
and DH buckets keep their previous interpretation. Source composite timings
are a family aggregate, not the cost of source `composite0` alone.

Classification parses the stage identity separately from the shader-pack name.
This fixes actual selected-source stages previously reporting zero. It adds no
pass or draw and changes no rendering policy; the GAL/backend sees opaque scope
indices. The generic timestamp allocator sums disjoint spans and rejects query
overflow instead of reusing live queries. Require valid completed-frame samples
and nonzero relevant families before interpreting source GPU costs. Regression,
timestamp and boundary checks pass. The release moving pair completes 1,800
frames per side: Current/Frozen **39.21/302.95 FPS**. Its 1,795 completed GPU
samples average **7.260 ms** total, **0.274 ms** deferred, **0.510 ms** composite
and **0.022 ms** final. Fullscreen families account for **0.805 ms**; do not
attribute the unclassified remainder to these shaders. This metadata fix does
not establish a throughput improvement. A separate 1,800-frame moving run
exercises standard core/synchronization/best practices validation with no API
errors and nonzero source families (1,749 completed GPU samples). All three
clients exit normally, with no retained process or owned crash evidence after
the grace period. Frozen source/worktree state is unchanged. This is scoped
profiling evidence; broad performance, parity and long-run bounds remain open.
Evidence: `artifacts/graphics-captures/goal5/source-fullscreen-gpu-profile/`.

### Mipmap scheduling lead

The bounded Oct 4 semantic probe records 17,408 mip-generation requests and
zero requests for already-valid descendants before a failed warmup. It does not
support an already-valid shortcut in this original-pack workload. The run has
zero measured frames and crashes during concurrent native replacement; exclude
it from throughput and clean-runtime evidence. The probe is removed and no
mipmap optimization is retained. Frame-start generation before later writes
remains a lead requiring clean consumer/cost measurements. The isolated native
staging regression reproduces overwritten mappings and passes after atomic
publication; see [native build constraints](../tooling/NATIVE-BUILDS.md).
Evidence: `artifacts/graphics-captures/goal5/source-mipmap-performance/`.

## Whole-frame sampling profile

Profile the whole render thread before adding component timers. `perf` is
usually unavailable (`perf_event_paranoid`), but async-profiler's `itimer`
mode attaches through the JVM and unwinds the native library with DWARF.
Build the release library with symbols, keep those variables set for the
harness run (otherwise Gradle rebuilds a stripped library), and attach to the
verified client PID during the measured window:

```sh
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only \
  ./gradlew buildRustNative -PmattmcRustProfile=release
asprof -e itimer -i 1ms --cstack dwarf -t -d 20 -o collapsed -f window.collapsed <pid>
```

Keep the profiler binary and its output outside the repository path: the
capture harness terminates processes whose command line names the repository.
Never copy a library over `build/rust/native/`; a running client maps it.

The moving benchmark rotates yaw 0.35° per frame, and each readiness restart
shifts which headings fall inside the measured window. Use
`--measure-frames 2057` (exactly two rotations) and interleave control and
candidate runs (control, candidate, control, candidate) before claiming a gain.
The Oct 4 evening A/B measured 39.26→45.01 FPS (25.53→22.22 ms) for staging
reuse, hashed mesh assets and cached texture payloads; evidence is under
`artifacts/graphics-captures/goal5/ab/` and `goal5/perf-correctness/`.

For Java allocation sites, attach with `-e alloc` instead of `itimer` and
group the render thread's stacks by the first non-JDK frame. Per-frame churn
here usually means a lookup that should be cached or a collection that
shrinks and regrows each frame. The vanilla benchmark finishes in about 60 s,
so attach about 25 s after launch with `--measure-frames 10000`; the shader
route needs about 60 s to settle.

The benchmark's own phase timers are part of every measured frame. Keep its
bookkeeping primitive and cached (phase samples are `long[]`, collector beans
are looked up once). Its final status write sorts every phase's samples, so a
profile window that overlaps completion shows `writePhaseMap`; that is not
frame work.

Native per-instance costs (batch plans, source-frame preparation) are easier
to isolate with a temporary `#[ignore]` release test that times the function
on about 6,000 synthetic terrain instances than with native line profiles,
which async-profiler does not provide. Remove the probe afterwards.
Later changes (canonical section order, shared source resource sets, cached
voxel sample centres, dense voxel material lookup) bring the cumulative
interleaved A/B to 36.88→47.95 FPS (27.12→20.86 ms, p99 35.75→29.23 ms) with the
original-pack Iris+DH coast pair still within tolerance 6
(`goal5/ab/cumulative-1`). About 6,000 mesh instances per frame, mostly
off-camera shadow candidates, still pass through several Rust and Java passes;
that per-instance work is the largest remaining cost.

## Isolate costs before changing batching

Split identity construction, cached camera batches, shadow policy selection,
shadow batch construction and sorted camera batches in a separate bounded probe.
Keep the normal policy and audit gates unchanged. Remove temporary timers before
measuring a candidate. Custom timers and an attached JFR make a run diagnostic
even if the harness does not detect them automatically.

Run `cargo test ... shadow_batch_selection -- --test-threads=1` and
`cargo test ... shadow_facing -- --test-threads=1` after a batching change.
These compare selected ranges with the established reference, including sparse
faces, explicit sections, repeated mesh ordering, culled invalid references and
cache ownership. Follow with normal moving gameplay and a separate validated
Frozen image pair. Avoid reducing caster coverage to improve a timing.

The Oct 4 original archive (`4420b62e…`) control completed 1,800 frames per side:
Current/Frozen **35.31/299.85 FPS**, with Current batching **4.784 ms/frame**.
A separate 6,000-frame diagnostic attributed **3.245 ms** to shadow batch
construction and **1.150 ms** to shadow selection (375 sampled frames each).
Skipping the grouping table for unique selected meshes did not establish a gain:
Current/Frozen **34.31/302.86 FPS**, batching **4.758 ms**. That candidate was
removed; the corrected full shadow face coverage remains. Entity counts and
readiness onset differed, so these runs are scoped evidence, not broad acceptance.
Evidence: `artifacts/graphics-captures/goal5/original-pack-moving-performance/`.

## Repeated mesh plans

Check cache admission before optimizing the selected builder. A later original
pack probe found repeated mesh keys in all 57 measured shadow samples: it built
6,063 batches on average and retained 1,275. The complete build cost 4.275 ms;
filtering cost 0.089 ms. These detailed timers add overhead and are diagnostic.

Repeated meshes now reuse the complete plan under its full ordered instance
identity, then filter a copy. Culled instances still affect ordering and
invalidation; do not key this path with selected identities alone. Camera-sorted
complete selections stay uncached. Reuse shares the existing four-entry CPU
bound and adds no GPU resources. See
[`geometry/arenas.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/geometry/arenas.rs).

A fresh normal control and candidate each complete 1,800 frames per side with
identical Java source/classes and original archive. Batching costs 4.726 versus
4.005 ms, about 15% less; native total costs 19.463 versus 18.538 ms.
Current/Frozen FPS is 34.36/304.22 in the control and 34.87/308.23 in the candidate.
Live entity populations and readiness onsets differ. This establishes a scoped
component reduction, not an overall FPS gain or broad acceptance. Eight selection,
ten batching, two shadow-facing and 26 architecture checks pass. The complete
native suite passes 2,157 tests with three ignored. A normal Current repeat
completes 1,800 frames with zero readiness restarts: batching 4.102 ms (13% less),
FPS 34.60. Both candidate runs are clean. The separate original-pack Iris+DH
coast pair passes RGB MAE 3.718/4.251/3.881; its DH-only region passes
4.954/5.394/4.732 against unchanged tolerance 6. Both clients exit normally;
Current's standard Vulkan validation is exercised and clean, with no API errors
or orphans. The paired images were reviewed. This verifies one settled pose;
motion, broad parity, the large performance gap and long-run bounds remain open.
Evidence: `artifacts/graphics-captures/goal5/shadow-batch-cache-profile/`.

## Per-pass preparation rules

The shader route prepares about 6,600 terrain instances and several hundred
entity draws per frame, so per-item work dominates. Keep these structures when
changing it:

- Resolve pass-invariant state once. Terrain pipelines, pack sets and the
  multi-draw set-zero are memoized in the frame's `SourceTerrainBatchScope`;
  entity/hand pipelines, packs and interface validation use `FrameMemo`
  ([`worldrender/mod.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/worldrender/mod.rs)).
  Code that destroys those resources must clear the memo (see
  `destroy_lowered_entity_source_pack_resources_for_keys`).
- Voxel sources are selected by the volume cull before any asset is touched,
  in mesh-key order, and returned as a shared `Arc` list. Occupancy consumers
  must stay order-independent and may treat an identical list as unchanged.
- A batch plan whose indices admit every eligible instance is keyed by those
  instances only (`mesh_batch_indices_cover_selection`); culled selections with
  repeated meshes still key the full frame.
- Fullscreen stages reuse their frame-invariant GAL objects (color samplers,
  render target and pass, uniform buffers, source-data set) through the stage
  cache in `FullscreenPipelineCache`; only the pack-resources set is created
  per frame. An entry is leased to one live plan, and the runtime releases all
  entries before a color-target promotion or discard retires their views
  ([`fullscreen/pipelines.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/runtime/fullscreen/pipelines.rs)).

Equivalence tests pin each rule (`lazy_mesh_batch_index_*`,
`covering_selection_*`, `terrain_voxel_source_volume_cull_*`,
`occupancy_runtime_trusts_a_shared_source_list_*`,
`stage_cache_reuses_*`). Re-run the Iris+DH and
vanilla parity pairs after changing them.

### Command count

Count ops before optimizing per-op GAL costs: the shader route issued ~3,200
ops per frame with ~1,070 set binds, because each entity mesh section was its
own draw and alternating terrain pages split indirect-draw runs. Merging
contiguous entity sections and ordering opaque/cutout batches by page cut this
to ~1,270 ops, ~330 binds and ~250 draws (moving pair 68→75 FPS). The
`rust-gal.frame.command-ops` and `native-profile.resource-set-binds` counters
track it; keep new per-item work from splitting those runs.

### Per-item lookups

With ~6,700 instances a frame, cold reads of the large inline stores
(`mesh_assets`, retained terrain records) dominate per-item costs. Prefer
compact side indexes: `mesh_asset_drawable_generations` (generation and
optical bit) answers residency and caster checks
(`plain_mesh_instance_is_valid`), and `MeshRangeMemo` serves static-terrain
section ranges to batch plans, which miss whenever the camera moves.
Pass-invariant retained-write facts live in the batch scope
(`RetainedSourceTerrainPass`). On the Java side, model batch checkpoints record
journal positions (`MembershipJournaledMap`, the pending-set undo journal)
instead of copying registries per producer, and off-camera entity candidates
skip extraction when ineligible (`EntityRenderer.rustShadowCullingEligible`).

## Preparation and boundary follow-up

The Oct 4 `cc140840a` probes separate source preparation, entity packing and
batching. Shadow-plan rebuild samples cost about 2.6–3.3 ms, versus about
0.29 ms on cache hits. Later entity samples have median packing costs of
0.073 ms for 150 camera groups and 0.046 ms for 90 shadow groups; these are
different views, not additive costs or the complete measured window. Probes
sample every 16 frames through frame 4,096. The batching probe completes
1,800 measured frames after two readiness restarts. Temporary timers are removed.

A conservative complete-opaque-group shortcut passed reference-order, culled
validation and transparency regressions, but regressed normal moving gameplay:
batching **4.011→4.890 ms/frame**, native total **18.349→19.528 ms**.
Control Current/Frozen is **36.00/296.92 FPS**; candidate is **34.51/308.70 FPS**.
All four runs complete 1,800 frames with the same Java sources/classes, original
archive and copied inputs. Current has one/zero readiness restarts; live workloads
still differ. The shortcut and its experimental tests are removed, and the normal
control library is restored. Preserve this failed result; it establishes no gain.
Both pairs exit normally with no retained clients, JVM crash logs or matching
system core records after the grace period. Current peak RSS is 5.04/5.01 GiB;
this is short-run evidence, not long-run bounds. Throughput runs disable validation.

The preparation diagnostic attributed 1.45 and 2.12 ms to Java terrain semantic
submission and frame consumption/asset flushing. Split these costs before moving
more collection/state ownership into Rust; these measurements alone do not prove
a migration benefit. See the
[CPU boundary rules](RENDER-ARCHITECTURE.md#where-new-code-goes).
Evidence: `artifacts/graphics-captures/goal5/source-prepare-performance/`.

The later bounded lowering diagnostic completes 1,800 frames without readiness
restarts or owned crash evidence. In 104 samples at native frame IDs >=1,000,
median geometry/interface validation costs 0.452 ms, resource lookup/draw
construction 0.451 ms, and stream allocation 0.054 ms. Those sampled IDs do not
identify the exact measurement window, and diagnostic hooks add overhead.
The timers are removed. A hash-based immutable geometry lookup passes the
release build and full 2,174-test native suite, with three ignored. Cold
retirement keys remain sorted, and mesh/generation/ABI, residency and completion
checks remain. Two normal 1,800-frame candidates measure source preparation
**5.118/5.045 ms**, versus **5.621/5.445 ms** in the preceding and fresh
controls. Current measures **37.80/38.26 FPS**, versus **36.54/36.61 FPS**;
Frozen measures **303.39/305.97 FPS**, versus **305.96/304.74 FPS**.
All eight clients exit normally with no retained process or owned crash evidence;
Current peak RSS is 4.97–5.33 GiB. Candidate readiness restarts are three/six,
versus zero/one for controls, and live workloads differ. The preparation decrease
repeats; overall FPS attribution remains tentative. The retained candidate's
original-pack Iris+DH coast pair passes whole-frame RGB MAE 3.723/4.255/3.886
and DH-region 4.951/5.389/4.735 against unchanged tolerance 6. Exact presented
frame ownership and final-output bytes match; standard core/synchronization/best
practices validation is exercised and clean. Both clients exit normally with no
owned crash evidence or retained process. This establishes one settled pose;
broad parity, motion, first-world-frame correctness and long-run bounds remain open.
Evidence: `artifacts/graphics-captures/goal5/native-lower-performance/`.

### Shared source program snapshots

A later bounded diagnostic times cached source-program copies during ordinary
moving gameplay, with extra graphics diagnostics disabled. After the first
4,096 clone ordinals, mean copies cost 22.8 µs for terrain, 42.0 µs for entities,
33.6 µs for hands and 25.7 µs for textured materials. The probe stops at 65,536
copies; ordinals do not identify the measured frame window, so these totals
must not be presented as per-frame costs or throughput evidence.
The timers are removed. The runtime now shares immutable program snapshots
by candidate epoch/material slot, as fullscreen programs already do; see
[the memoized getters](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/shaderpack/runtime/programs.rs).
Owned uncached builders and all pack/resource generation checks remain. The native suite passes
2,175 tests with three ignored, and the release build passes. Its first normal
pair completes 1,800 frames per client: Current/Frozen 36.56/305.34 FPS,
zero Current readiness restarts and clean owned-client exit/crash checks.
That is lower FPS than the preceding geometry control, which had six readiness
restarts. A fresh original-memo control completes with zero restarts and
Current/Frozen 35.45/302.31 FPS. Against that control, candidate native total is
18.637→17.729 ms, preparation 5.450→5.239 ms and shader-plan lookup
0.080→0.001 ms. Both fresh clients exit normally without owned crash evidence.
The gain is scoped to this matched ordinary comparison; workload variability,
broad performance acceptance and long-run bounds remain open. The restored
candidate passes original-pack Iris+DH whole RGB MAE 3.697/4.222/3.859 and
DH-region 4.919/5.350/4.702 against unchanged tolerance 6. Exact presented
frame correlation, source-uniform receipts and final-output/screenshot bytes
match. Standard core/synchronization/best practices validation is exercised
and clean; both clients exit normally without retained processes or owned
crash evidence. The source snapshots are retained as a scoped CPU improvement.
This is one settled pose; broad parity, motion and long-run bounds remain open.

## Java frame collection costs

The ordinary benchmark now splits shadow build requests, candidate collection,
candidate limiting and submission, plus pack refresh, pending publication,
semantic-frame consumption and frozen-asset protection. These fixed scopes use
the existing disabled-by-default benchmark; they do not add per-mesh timers.
For a separate allocation diagnostic, pass
`--jvm-arg=-Dmattmc.dev.graphicsFrameBenchmark.phaseAllocationSamples=true`.
Divide each `java.alloc.phase.<name>-bytes` counter's total by measured frames;
these are render-thread bytes, not sampled JFR weights or all-worker allocation.
Allocation sampling makes the run diagnostic, even when its artifact permits
performance publication.

Frozen-frame generation protection uses a frame-local primitive map. Every world,
hand and orb reference still participates; duplicate conflicting generations
reject before publication, including zero and extreme keys. Map iteration order
does not participate in this policy. The existing upload/retirement locks and
generation checks remain. Twelve targeted Java checks and the complete 2,174-test
native suite pass at this checkpoint, with three native tests ignored.

| Original-pack moving case | Protection time/frame | Current / Frozen FPS |
| --- | --- | --- |
| Boxed-map control | 0.304 ms | 37.46 / 311.79 |
| Primitive-map candidate | 0.088 ms | 36.69 / 303.15 |

Both normal pairs complete 1,800 frames per side with the same native library,
archive, copied inputs and effective moving pose (yaw 0, pitch 9.7, delta 0.35).
Current readiness restarts are two/one and final entity counts are 171/169;
Frozen populations differ too. The protection component costs about 71% less,
but overall FPS does not improve. Separate allocation diagnostics reduce that
component from 539,083 to 262,352 bytes/frame; those runs have zero/two readiness
restarts. All four normal clients exit normally, with no retained owned process,
JVM crash log or matching delayed kernel core. Current peak RSS is 5.12/5.01 GiB,
which does not establish long-run bounds. Throughput validation is off.
Evidence: `artifacts/graphics-captures/goal5/java-boundary-performance/`.

The follow-up uses a primitive insertion-ordered terrain cache, primitive
visibility reconciliation and a reused candidate iterator. Lookup/replacement
preserves admission order; eldest eviction, visibility withdrawal and the existing
24,576-instance bound remain. Twenty-three targeted Java checks pass, including
zero/extreme keys, survivor order, replacement and publication lifetime.
These changes affect CPU collections only; Rust retains source pass selection
and VulkanicGAL retains all GPU ownership.

Separate allocation diagnostics reduce shadow candidate collection from
144,040 to 50,248 bytes/frame and submission from 145,762 to 55,545 bytes/frame.
The normal follow-up completes 1,800 frames per side at **36.54/305.96 FPS**
Current/Frozen, with no Current readiness restarts. Shadow submission costs
**1.007 ms**, versus **1.118 ms** in the preceding primitive-protection candidate.
This is a component reduction; total FPS remains essentially unchanged. Both
clients exit normally with no retained process, JVM crash log or delayed owned
kernel core. Current peak RSS is 4.97 GiB; throughput validation is off.

The separate original-pack Iris+DH coast pair passes whole-image RGB MAE
**3.713/4.249/3.884** and DH-region **4.927/5.361/4.713** against tolerance 6.
The depth mask contains 179,133 pixels and matches the screenshot's exact
frame/submission and acquired/presented image. Final-output bytes match the
screenshot, and all retained source uniform receipts name that submission.
Current's standard Vulkan validation is exercised and clean; both clients exit
normally with no owned crash evidence, and Frozen remains unchanged. The images
were reviewed. This verifies one settled pose, not motion, broad parity or
long-run bounds; the severe performance gap remains open.

## Java allocation follow-up

For cold first-turn delays, separate `Chunk Render Task Executor` samples from
the render thread and retain input wall times. The Oct 4 owned 75-second JFR pair
in `goal5/terrain-first-turn-startup-profile/` identifies repeated position
allocation in `NativeSectionSnapshot.writeTintLattice` and disabled appearance
receipt formatting in `computeLightWord`. Tint extraction now reuses one local
mutable position for all 64 queries; copied values and the -1..2 coordinate
extent remain unchanged. Light extraction formats block names only when the
diagnostic is enabled. Five tint tests include copied native-memory values
against authored grass, oak and dry-foliage providers at negative coordinates;
all 21 related Java checks pass.

First-two-second chunk allocation weights are about 1.760 versus 0.195 GB,
with 247 versus 238 execution/native samples. These are sampled estimates,
not exact allocated bytes, matched completed sections or an FPS gain. Both
original-pack movies still show the early water band. Each side retains three
240-sample videos and unchanged native/pack hashes. Both sessions have exact
owned-client system core records during termination despite wrapper exit 143;
their stability verdicts are rejected. Preserve the post-run abort verdicts
alongside the original receipts. The cleanup core-dump gate and artifact
classification checks pass with 79 rendering-tool tests. Later console fixes
and scoped successful termination repeats are recorded in
[terrain movement checks](TERRAIN-MOVEMENT-CHECKS.md); they do not change those
original rejected verdicts. Readiness and the severe performance gap remain open.

Padded snapshot extraction now memoizes state IDs by `BlockState` identity for
that extraction only. The first occurrence still registers through
`NativeStaticBlockModelRegistry`; repeated cells copy the same integer without
entering its synchronized method again. The memo has at most 5,832 entries and
is discarded before later snapshots or reloads. Preserve the existing generation
rejection in `flushAll`, and keep per-cell light/tint extraction unchanged.
Compare chunk-worker JFR stacks and actual cold-turn recordings before claiming
a gain; fewer registry calls alone do not establish faster terrain presentation.

The Native44 original-pack shader+DH pair in `goal5/terrain-state-id-memo/`
keeps the library, copied inputs and profiler settings fixed. Thirty-three
related Java checks pass. Inclusive worker execution/native samples are:

| Interval after first input | All worker samples, control → candidate | Padded extraction | Registry state ID |
| --- | --- | --- | --- |
| First 2 s | 275 → 138 | 81 → 3 | 53 → 9 |
| Next 8 s | 1,293 → 711 | 413 → 20 | 243 → 46 |

Allocation sample counts during the next eight seconds are similar, 1,197 and
1,196, with estimated weights 1.083 and 1.116 GB. This supports avoided CPU work;
it does not measure exact lock waiting, normalize completed sections or prove
an overall FPS gain. Both owned recordings finish, the clients are reaped, and
post-run kernel audits observe no cores. Each side retains three 240-sample
movies, which still show the cold exposed-seabed band. No exact presentation
latency improvement, registered Frozen image parity or broad stability is proved.

Tint lattice extraction classifies the existing built-in tint path once per
block. On the provider path, absence of a registered color provider proves all
64 copied integers are `-1`, so a bulk fill preserves the packet without 64
table lookups. Registered providers retain every -1..2 coordinate sample; never
infer a uniform lattice from the origin color. The copied-memory regressions
cover untinted blocks and a custom provider whose origin is `-1` while its
neighbors differ, alongside authored grass, oak and dry-foliage values.

The owned 75-second follow-up in `goal5/terrain-untinted-lattice/profile/`
holds Native44, the original archive, copied camera/DH inputs and profiler
settings fixed against the preceding state-ID-memo candidate. All 35 related
Java checks pass. During the next eight seconds after input, inclusive worker
execution/native samples in `writeTintLattice` fall from 376 to 36, and
`BlockColors.getColor` from 371 to 22. All-worker samples are 711 versus 596;
allocation counts are 1,196 versus 1,207, with estimated weights 1.116 versus
0.960 GB. These support avoided extraction work, not exact CPU time, matched
completed sections, an FPS gain or faster presentation. The client is reaped
and its delayed owned-kernel audit observes no crash.

The ordinary Current/Frozen follow-up retains three 240-sample turn movies per
client. Current still briefly exposes the cold seabed strip; the reviewed revisit
is clearer. Both retain distant haze. See
[terrain movement checks](TERRAIN-MOVEMENT-CHECKS.md) for the resource-reload
results and their limits. Terrain readiness and the severe performance gap
remain open.

Attach JFR only to the verified isolated client PID and retain its start identity,
command, binary recording and small render-thread summaries. Separate render
samples from chunk workers. Sample weights are estimates, not exact allocated
bytes; a large weight supported by only one or two events needs further evidence.

The control recording requested 60 seconds but retained 45 before normal client
exit. It found successful mesh encoding constructing the indexed null-error
message on every instance. The bridge now constructs that message only when the
instance is null, preserving rejection and wire bytes. All 20 native ABI/entity/foil
encoding checks pass, covering staging reuse and semantic fields; the complete
Rust suite passes 2,156 tests with three ignored at that checkpoint.
The fresh 60-second normal-gameplay recording has zero allocation samples with
both `encodeWorldMeshInstances` and `StringConcatHelper` in the stack, versus
163 control samples. Encoding itself remains sampled (230 allocation samples).
These recordings differ in duration and live populations; they establish no
overall allocation-rate or FPS gain. The longer 6,000-frame benchmark failed after
seven terrain-readiness restarts; the client exited normally without API errors
or orphans. Retain that failed verdict separately from the allocation diagnostic.
The separate original-pack settled image pair passes RGB MAE 2.336/1.904/2.207
against unchanged tolerance 6. Both clients exit normally; Current's standard
Vulkan validation is exercised and clean, with no API errors or orphans. Both
images were reviewed. This is one shader-only pose; the large moving performance
gap, broad parity, flicker, underground difference and resource bounds remain open.

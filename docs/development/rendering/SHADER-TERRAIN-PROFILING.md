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
Phase entries contain `total` nanoseconds; divide by measured frame count.
Native draw/timeline samples cover a bounded prefix, while cumulative submission
counts also include readiness and warmup. Do not divide cumulative counts by
measured frames or add nested CPU and GPU timers as independent costs.

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

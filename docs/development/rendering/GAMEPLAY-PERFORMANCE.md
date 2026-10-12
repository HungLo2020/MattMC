# Ordinary gameplay performance

Use this alongside the settled renderer benchmark when comparing Current with
Frozen. The settled benchmark hides VoxelMap and its optional camera motion
rotates at a fixed point. Readiness losses can discard partial timing windows.
Those results do not establish startup, streaming, or visible-minimap performance.
The [driver at `ee34f2ad`](https://github.com/HungLo2020/MattMC/blob/ee34f2ad99921848d8fc5d63da93eb6c583786c4/DevUtils/tests/rendering/RunOrdinaryPerformance.py)
records a separate `ordinary-gameplay-v1` protocol. To diagnose a session you
play by hand instead, see [Recording a hand-played session](SESSION-RECORDING.md).
That recorder observes Current only and adds diagnostic overhead; it does not
supply a matched Frozen comparison or acceptance verdict. An ordinary driver's
`complete` result means
the requested observation finished and passed its state/health checks; the
driver does not compare Current/Frozen FPS or p99 against acceptance floors.

## Run the ordinary comparison

Build release first, then run on Linux/X11 with a JDK 25 installation:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/PerfAudit/Gameplay.py --ordinary \
  --run-source /absolute/path/to/shared-copied-run \
  --world Origin --output artifacts/graphics-captures/ordinary-example \
  --jdk /absolute/path/to/jdk-25/bin --dh on --shaders off --seconds 30
```

The source must contain `saves/<world>/level.dat`. Both clients receive separate
copies of this same source through the existing capture engines. A pair rejects
DH cache formats Frozen cannot read; prepare a compatible copied source rather
than editing the original world. Current may upgrade its own isolated cache.
The original saves/configs and Frozen source stay untouched.

`--seconds` sets the length of each of the three windows (30 seconds each by
default), not the total session length. Use a new output directory for every
invocation; the driver rejects an existing one and pins the output with `.keep`.
Review and retire it through the [storage rules](ARTIFACT-STORAGE.md#verification-driver-retention)
when it is no longer needed.

The default runs Current then Frozen at fullscreen 1920×1080, render/simulation
distance 12, an unlimited FPS slider, VSync off, and matched 8 GiB ZGC/JIT
settings. `--jdk` controls both the game/Gradle launch and observer tools by
setting the child `JAVA_HOME` and placing its `bin` first on the child `PATH`.
DH radius and generation settings come from the shared source. These
are explicit comparison controls, not a claim to preserve every RunDev setting.
Use `--side`, `--heap-gb`, `--render-distance`, `--width`, `--height`,
`--no-fullscreen`, `--shaders`, and `--dh` for other controls.

VoxelMap is visible by default. The test agent applies this ordinary HUD setting
once on the render thread after the world opens, compensating for the legacy
capture engine's minimap override. `--minimap hidden` supplies a separate causal
control; keep it distinct from the normal visible-HUD result.

## What is recorded

Three consecutive windows cover initial playable entry, standing still, and
holding ordinary forward movement. Tiny alternating mouse nudges prevent AFK
throttling. There is no deterministic camera or terrain/DH settlement gate.
The driver's PID/window checks prevent input reaching another client.

A bounded JDK 25 agent instruments `Minecraft.runTick` entry/normal return
identically in both clients. It stores primitive frame intervals and method
durations, with no per-frame objects or file writes. Attaching can disturb JIT
state. A separate bootstrap helper exposes the callbacks across Fabric's class
loader boundary; it must stay outside the main agent JAR.
The hooks have overhead; this is a paired diagnostic protocol, not
unprofiled RunDev. An entry window begins when the world is playable; it does
not include the entire loading screen in its aggregate. The raw recording keeps
all completed frames after attachment, including earlier loading and long stalls.
No samples are discarded when readiness changes. Frame intervals are assigned
to windows by completion time, so a boundary-crossing stall can appear in the
following window. Use the combined raw recording for the complete timeline.

`summary.json` records controls, phase times, frame counts, mean/p95/p99/max
intervals, slow-frame counts, displayed FPS, memory, actual movement, HUD/menu
state, and Current's native presentation count. Native presentation and client
loop rates are distinct. A DH-enabled run requires positive native LOD execution
counts on Current and instrumented DH draw calls on Frozen. Inspect both.
`frames.bin` retains every completed
frame (big-endian: u32 count, boolean overflow, then i64 epoch milliseconds,
i64 start-to-start interval nanoseconds, i64 runTick duration nanoseconds).
Overflow, truncated data, menus, F3, throttling, wrong minimap state and failed
movement reject an observation. Process shutdown is bounded owned cleanup,
separate from the normal-exit lifecycle gate. Runtime logs still need review
for rendering/driver errors; successful timing does not establish pixel parity.

Keep system memory pressure and background processes comparable. Repeat and
alternate clients before claiming a small improvement. Comparing an earlier
busy-machine run to a later quiet run does not isolate an implementation change.

## Local section-coordinate checkpoint

Native `168dd924` routes production client/server section-coordinate storage,
ordered keys, chunk relations and coordinate queries through Rust. Java keeps
section behavior bindings and visibility/membership callbacks. See the
[ownership and compatibility guide](../world/entities/RUST-ENTITY-SECTION-INDEX.md).

All twenty alternating exact 6,000-frame runs finish cleanly. All ten live
heap/collector/JIT controls match, final source/native/Frozen/prompt/three
prepared-input guards pass, and no clients remain. The controller exits 1 only
at aggregate performance evaluation. Every scheduled repeat is retained.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen | Floors |
| --- | --- | --- | --- |
| Vanilla | 1324.95 / 1334.95 | 3.410 / 3.629 | FPS fails 0.75%; p99 passes |
| Vanilla + DH | 811.80 / 867.40 | 3.142 / 4.774 | FPS fails 6.41%; p99 passes |
| Shaders | 343.80 / 316.05 | 5.186 / 7.036 | Both pass |
| Shaders + DH | 263.60 / 225.90 | 5.199 / 9.412 | Both pass |

The fixed protocol uses 360 settle and 240 warmup frames, four DH repeats per
side and two for every other mode. Current/Frozen variation, especially in DH,
prevents attribution of an isolated migration gain or regression cause.
Aggregate performance remains **failed**; further evidence-backed migration
and optimization are required. This is a correctness checkpoint, not final
performance or Java-removal acceptance.

Combined suites pass 2,698 Rust tests (three ignored) and 2,022 executed
Java/parity tests (two skipped). Actual unchanged Frozen matches all 6,721
index cases and 4,417 manager cases with source/class/native guards. The first
fixture attempt omitted registry bootstrap; its failure and the preceding
6,693-case replay are retained. Numeric packing, nonfinite values, nulls,
callback mutation and retained live streams are included in the final corpus.

Seven lifecycle cases, two world comparisons and fixed cow body/shadow parity
pass with zero VUIDs. Fourteen original images were individually reviewed and
ten Current correctness native identities match. Small head orientation/shading
differences are outside the body/shadow fixture's acceptance scope. These
settled views do not establish first-frame/first-turn behavior, broad gameplay,
sustained streaming or long-session memory bounds.

Current phase receipts put entity traversal around 0.02–0.03ms median and DH
render-list collection around 0.025–0.028ms, versus 1.09–1.16ms total DH GPU
time. They select costs for further investigation, not a proved FPS cause;
inclusive scopes must not be added as independent costs. Receipts:
`build/native-section-index/candidate/` (`gameplay/performance.json`, mode
repeats, `gameplay/final-guards.json`, `gameplay/effective-control-verification.json`,
original-review records and `runtime-identities.json`), plus
`build/native-section-index/current-phase-costs.json` and CPU replay logs.

## Local entity-section checkpoint

The preceding native `6076262e` includes Rust root/typed section membership, retained
DH accepted-parent culling reuse and count-only admitted frame output. The
benchmark matches actual heap/collector/JIT settings on unchanged Frozen.
All twenty alternating exact 6,000-frame runs are clean; all live controls,
source/native/Frozen/prompt and three prepared-input guards pass. No clients
remain. The controller exits 1 only at aggregate performance evaluation.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen | Floors |
| --- | --- | --- | --- |
| Vanilla | 1263.55 / 1268.40 | 3.674 / 3.460 | FPS fails 0.38%; p99 fails 6.18% |
| Vanilla + DH | 769.85 / 634.30 | 3.281 / 6.727 | Both pass |
| Shaders | 349.80 / 315.70 | 5.034 / 7.169 | Both pass |
| Shaders + DH | 269.00 / 226.60 | 5.276 / 9.146 | Both pass |

The protocol retains 360 settle and 240 warmup frames, four DH repeats per
side and two for every other mode. All scheduled repeats are included.
Frozen's DH result moved substantially from the preceding comparison; this
matrix establishes neither an isolated optimization gain nor a cause for the
vanilla miss. Aggregate performance acceptance remains **failed**, and further
profile-backed Rust migration/optimization is required. This is a correctness
checkpoint, not completion of the performance or Java-removal goals.

Combined suites pass 2,691 Rust tests (three ignored) and 2,018 executed
Java/parity tests (two skipped). Actual Frozen membership 6,123, manager
lookup 4,417 and DH traversal 1,280 cases match with identity guards.
Seven lifecycle cases, two world pairs and the fixed cow body/shadow pair pass
with zero VUIDs. Fourteen original images were individually reviewed and ten
Current correctness runtime identities match. These settled views do not
verify first-turn/first-frame behavior, streaming, broad gameplay or long-session
memory. Receipts: `build/native-entity-sections/dh-summary-candidate/`, including
`gameplay/performance.json`, `gameplay/final-guards.json`,
`gameplay/effective-control-verification.json`, both original-review records
and `runtime-identities.json`.

The following records preserve **earlier candidates**, not verification of
this newer native image.

**Measurement limitation:** paired JFRs subsequently showed Current using G1
with a1GiB initial heap and Frozen using ZGC with4GiB. Their launchers also
select different JIT thresholds. Thus the raw matrix below is retained evidence,
not a controlled renderer performance comparison or isolated regression proof.
The frame-benchmark harness now supplies common `_JAVA_OPTIONS`:1GiB initial,
8GiB maximum, ZGC on Linux/Windows, compact headers, ZUncommitDelay5 and
tier thresholds100/600/300/700. Explicit VM overrides apply to both sides;
gameplay-property precedence and Frozen source remain unchanged. The actual
HotSpot test verifies equal effective settings despite conflicting launcher
defaults. The corrected20-run matrix checks each owned client's live
`VM.flags` checked before the warmup fence. Run
`JAVA_HOME=/path/to/jdk25 python3 -m unittest discover -s DevUtils/tests/rendering -p test_benchmark_jvm_controls.py`
to exercise those controls.

The corrected matrix has now finished: all twenty exact6,000-frame runs are
clean, all ten live VM controls match, final source/native/Frozen/prompt/three
input guards pass and no clients remain at timing completion. The controller
exits1 only at evaluation; vanilla p99 and DH FPS miss their floors.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen | Matched floors |
| --- | --- | --- | --- |
| Vanilla | 1337.60 / 1227.45 | 3.695 / 3.544 | FPS passes; p99 fails4.25% |
| Vanilla + DH | 683.50 / 835.70 | 4.662 / 5.233 | FPS fails18.2%; p99 passes |
| Shaders | 334.95 / 316.05 | 5.349 / 7.016 | Both pass |
| Shaders + DH | 266.65 / 226.05 | 5.684 / 9.959 | Both pass |

All scheduled repeats remain included; the protocol still uses360 settle,
240 warmup and4 DH repeats per side versus2 for the other modes. Separate
matched-control 15s vanilla/DH profiles completed sequentially after timing,
with live flag guards for both clients. They selected retained-DH traversal
and count-only output changes; native `6076262e` passes refreshed combined
suites and Frozen CPU replays. Fresh gameplay and twenty matched timing runs
are in progress. No isolated section-regression cause or accepted speedup
is established yet. Corrected receipts:
`build/native-entity-sections/matched-jvm-v3/gameplay/`, including
`effective-control-verification.json` and the mode/all-run results.

The following table retains the **older unmatched-control matrix**.

The earlier section-membership candidate uses native `d4348edd`
on top of documentation-only `6b858d35b`. Rust stores root/typed member arrays
for production client/server section consumers; Java retains class descriptors,
behavior bindings and callbacks. See the
[ownership and verification guide](../world/entities/RUST-ENTITY-SECTIONS.md).
Coordinate indexing and visibility remain subsequent migration work.

All twenty predeclared alternating timings completed: 6,000 measured frames,
360 settle and 240 warmup, four repeats per side for DH and two for each other
mode. The same prepared worlds/pack and unchanged Frozen OpenGL `7a4d1817`
were used. All runs are clean, all Current runtime native identities match,
and final source/native/Frozen/prompt/three prepared-input guards pass.
No clients remained at timing completion. No profiler or extra game client ran
during timing; separate diagnostic clients start only after its controller ends.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen | Floors |
| --- | --- | --- | --- |
| Vanilla | 1319.75 / 1177.45 | 3.712 / 3.026 | FPS passes; p99 fails22.7% |
| Vanilla + DH | 844.35 / 795.55 | 2.995 / 5.021 | Both pass |
| Shaders | 353.80 / 319.45 | 5.262 / 6.233 | Both pass |
| Shaders + DH | 260.10 / 228.70 | 6.336 / 8.064 | Both pass |

The original controller exits1 at evaluation because vanilla p99 misses the required
floor. Both Current vanilla repeats are retained (3.839 and3.586ms p99), as
are both Frozen repeats (3.151 and2.900ms). This is not a passing performance
checkpoint, an isolated regression attribution, or a reason to stop migration.
Guarded paired15s vanilla JFR diagnostics complete separately with process/source/
native guards. They exposed the GC-control mismatch above; total concurrent GC
cycle duration must not be interpreted as a stop-the-world pause. Their timings
do not replace these measurements or establish the cause of the raw p99 miss.

Combined Rust2,691/three ignored and Java/parity2,016 executed/two skipped pass.
Actual Frozen membership6,123 and manager4,417 cases match with identity guards.
Seven lifecycle cases, two world pairs and a cow/body-shadow pair pass,VUID0;
fourteen original images were individually reviewed and ten Current correctness
runtime identities match. The cow comparison covers five fixed-view images per
side, not general animation or leash acceptance. Initial pre-array-repair
runtime evidence and its deliberate pre-timing controller cancellation remain.
The array-projection compatibility repair was verified before this matrix.

Receipts: `build/native-entity-sections/array-copy-candidate/gameplay/`
(`performance.json`, mode repeats and `final-guards.json`),
`world-original-reviews.json`, `cow-original-reviews.json` and
`runtime-identities.json`. Source is unchanged during measurement/profiling.
First-frame/first-turn defects, sustained streaming, broad gameplay and long
host/GPU memory acceptance remain open. Those receipts describe that earlier source checkpoint.

## Local visible tracking checkpoint

The implementation author reports the October 11 native `9c9f085c` checkpoint,
published in [`d8e6b8f4`](https://github.com/HungLo2020/MattMC/commit/d8e6b8f477b9c6f55aef3030260a3e2af4b10212)
after documentation base `cc929bd06`. This source moves production visible
ID/UUID maps and ordered enumeration into
[Rust entity tracking](../world/entities/RUST-ENTITY-LOOKUP.md). It also removes
profile-backed Java text layer/clipping record copies before the same inputs
reach Rust. Font extraction/layout, behavior callbacks, section membership
and persistence remain Java; no new ownership is claimed for the text cleanup.
The native image is unchanged between the pre-cleanup and final matrices;
`RustGalGuiRenderer` is the executable source delta. The reference remains
unmodified Frozen OpenGL `7a4d181717dc0c7da9086f1a17687dfd522ce417`.
This documentation review inspected pinned source and assertions only; it did
not rerun native/Java suites, replay captures, or inspect untracked originals.
All runtime results and original-image reviews below are author-reported.

All twenty predeclared alternating runs complete cleanly: exactly 6,000 frames,
360 settle/240 warmup, four repeats per side for DH and two for other modes.
No profiler or other game client ran during timing. Every repeat is retained.
Final source/native/Frozen/prompt/three prepared-input guards pass.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
|---|---:|---:|
| Vanilla | 1358.35 / 1155.90 | 3.049 / 3.109 |
| Vanilla + DH | 803.60 / 704.00 | 3.360 / 5.739 |
| Shaders | 346.25 / 316.50 | 5.508 / 6.757 |
| Shaders + DH | 266.75 / 227.85 | 6.118 / 8.369 |

**All four average-FPS and all four p99 floors pass.** Vanilla p99 margin is about 1.9%.
The preceding 5.1% vanilla-tail miss remains below; this does not prove an
isolated gain or root-cause repair, and some absolute medians/baselines moved.
These are settled rotating scenes with hidden minimap, not broad gameplay,
first shader-world-frame/first-turn, streaming or long-memory acceptance.

Fresh full Rust 2,685/three ignored and Java/parity 2,010 executed/two skipped
pass. Frozen lookup/manager 4,417 is rerun after cleanup. Geometry 11,489,
order 4,143 and range 645 pass on the identical native and unchanged consumers.
Seven fresh lifecycles, two world pairs and four GUI item pairs pass, with zero VUIDs;
all twelve originals are individually reviewed and thirteen Current runtime
identities match. The earlier strict body/shadow pair supplements tracking
coverage; its ten originals remain retained.

Equivalent copied-cow inputs expose a thinner near-camera leash in Current,
also seen with the published-registry Current control. This leash comparison
passes whole-image/shadow checks but **fails the strict model crop**; the
body/shadow result does not accept leash parity or establish its cause.
Current crosshair is absent in plain/static/oversized GUI reload fixtures,
also in the older published `9f372451` plain original, but present in the
moving-foil pair. Hotbar item checks do not resolve full GUI/crosshair parity,
and the controls do not isolate a cause. Receipts:
`build/native-entity-lookup/text-copy-candidate/` and the earlier rejected
fixture/profile results under `build/native-entity-lookup/`.

The pinned [capture harness](https://github.com/HungLo2020/MattMC/blob/d8e6b8f477b9c6f55aef3030260a3e2af4b10212/DevUtils/Common/graphics_harness.py)
and [model-identity helper](https://github.com/HungLo2020/MattMC/blob/d8e6b8f477b9c6f55aef3030260a3e2af4b10212/DevUtils/Common/model_identity.py)
accept an exact family identity or that family plus `/part/root` or
`/part/` followed by lowercase hex encoding valid UTF-8.
For split crop aggregation, one frame must contain unique parts matching the
inventory observed across matching captured records, consistent metadata and
finite projected bounds. That inventory is not an independently authored
expected topology, and an exact unsplit model bypasses split-set aggregation.
These guards do not prove every expected model part was captured or establish
blanket topology or visual parity.

## Historical visible tracking before text-copy cleanup

The author-reported pre-cleanup native `9c9f085c` checkpoint on documentation
base `cc929bd06` moves production
client/server visible ID/UUID maps and live ordered enumeration into
[Rust entity tracking](../world/entities/RUST-ENTITY-LOOKUP.md). Java behavior
bindings and callbacks remain; section membership and persistence remain Java.

All twenty predeclared alternating runs completed cleanly with the same
6,000 measured frames, 360 settle/240 warmup and repeat counts as the published
checkpoint below. No profiling or other game client ran during measurement.
Final source/native/Frozen/prompt/three prepared-input guards pass.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
|---|---:|---:|
| Vanilla | 1353.35 / 1174.75 | 3.190 / 3.035 **FAIL** |
| Vanilla + DH | 860.65 / 727.30 | 2.400 / 5.234 |
| Shaders | 344.10 / 318.40 | 4.868 / 6.550 |
| Shaders + DH | 266.45 / 228.35 | 5.299 / 8.614 |

All FPS floors and the other three p99 floors pass; **vanilla p99 misses
5.1%, so the overall matrix fails**. Both Current vanilla repeats have higher
p99 than their paired Frozen runs. Current's median is below its earlier
published measurement, but Frozen's median also moved; neither an isolated
migration regression nor its cause is proved. Do not rerun selectively or
claim full performance acceptance. Paired15s warmed vanilla JFR profiles complete on guarded clients. First
per-thread allocation samples carry large pre-recording weights and are
excluded from scoped analysis. Repeated GUI text layer/clipping copies are
visible in the remaining samples; local preparation now constructs final
metadata once. The later verified cleanup checkpoint above is separate from this failed
matrix. No isolated speedup or vanilla-tail cause is established;
profiled measurements remain diagnostic only.

Full Rust 2,685/three ignored and Java/parity 2,010 executed/two skipped pass;
actual Frozen lookup/manager 4,417, geometry 11,489, order 4,143 and range 645
replays pass with guards. Seven lifecycles, two settled world pairs and a
strict cow body/shadow pair pass with zero VUIDs; fourteen accepted originals
were reviewed. Equivalent leash inputs separately fail strict crop comparison:
Current's near-camera leash is thinner, also seen in the published-registry
Current control. The leash issue remains open; it is not accepted by the
body/shadow or whole-image passes. These pre-cleanup results remain historical.
Receipts: `build/native-entity-lookup/{gameplay,vanilla-jfr}/`.

<a id="current-hardware-evidence-boundary"></a>

## Historical integrated hardware evidence boundary

The implementation author records the October 11 combined checkpoint with
native `9f372451`, published in
[`c1428dbc`](https://github.com/HungLo2020/MattMC/commit/c1428dbc7a1a0b190cb9e7bc2c5c849e99ec3f42)
on atlas/model base `71762d602`. This documentation review inspected source and
assertions without rerunning these workloads or inspecting untracked raw receipts. Rust owns primary entity removal state and immediate
camera-slot retirement, preserving retained views, explicit queries, revival
and transfer. A profile-backed generated-item alpha mask avoids repeated
transparency reads. Java tracking/section admission and broader simulation
remain migration work; see the [entity guide](../world/entities/RUST-ENTITY-GEOMETRY.md)
and [model guide](../resources/MODELS.md).

All 20 predeclared alternating runs finished cleanly: exactly 6,000 measured
frames after 360 settle and 240 warmup frames, four repeats per side for
vanilla + DH and two per side for the other modes. No profiler or other game
client ran during timing. Every repeat remains recorded.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen |
|---|---:|---:|
| Vanilla | 1313.65 / 1142.25 | 3.248 / 3.307 |
| Vanilla + DH | 808.10 / 709.80 | 3.156 / 5.266 |
| Shaders | 341.95 / 312.95 | 4.780 / 7.169 |
| Shaders + DH | 260.60 / 226.05 | 5.275 / 9.203 |

**All four average-FPS and all four p99 floors pass.** Final source/native/
Frozen/prompt/prepared-input guards and ten Current timing identities pass;
no clients remain and the controller exits 0. Vanilla's p99 margin is about
1.8%. These comparisons do not isolate either change's speedup or establish
that the preceding p99 miss was root-caused and fixed. This is a settled
rotating scene with a hidden minimap, not first-world-frame, first-turn,
ordinary-play, sustained-streaming or long-session memory acceptance.

Fresh combined suites pass 2,680 Rust tests (three ignored) and 2,005 executed
Java/parity cases (two skipped). Guarded actual Frozen geometry/removal,
range and ticking comparisons pass 11,489/645/4,143 cases. Seven live
lifecycles, two world pairs and four GUI pairs pass with zero VUIDs; all
twelve original images were reviewed and thirteen Current runtime artifacts
identify this image. GUI coverage includes plain, static/moving foil and
oversized placement after world resource reload. The retained-despawn test
proves camera-slot reuse, not real-session memory bounds.

The model corpus matches 67,433 lines and 4,351 warnings under matched
fixture-only NPE-detail controls. Model-only/full-reload aggregate floors
pass (76.915/79.069 and 128.454/135.543 ms); three individual model pairs
are slower, and Frozen's median moved between runs. Ordinary gameplay
retains normal JVM controls. The separate pre-alpha atlas CPU comparison
matches 8,375 lines and passes 55.014/66.562 ms; it does not establish GPU
upload parity. Neither result is an isolated speedup claim.

Receipts: `build/native-entity-lifecycle-model-integration/alpha-mask-accepted/`
(`gameplay/{performance,final-guards}.json`, `runtime-review.json`,
`gui-review.json`), sibling `alpha-mask-*` CPU receipts and
`model-replay-alpha-mask/`. Preserve every timing repeat and rejected candidate.

### Historical pre-integration lifecycle checkpoint

Native `543a592f` on `f92ea29dd` separately passes its twenty-run matrix:
median FPS Current/Frozen 1366.65/1181.35, 876.60/701.70, 346.40/315.80 and
265.25/226.40; p99 3.121/3.169, 2.307/5.288, 5.128/6.753 and 5.266/8.747 ms
(vanilla/DH/shaders/combined). Its CPU/live checks pass. These measurements
precede atlas/model integration and do not accept the newer image. Receipts:
`build/native-entity-lifecycle/`.

### Historical ticking-owner checkpoint

Preceding native `c487f375`, published in `f92ea29dd`, passes CPU suites,
actual Frozen comparisons and live lifecycle/image checks. Its twenty clean
runs pass every average-FPS floor and three p99 floors, but miss vanilla p99
by 4.8% (3.410/3.253 ms Current/Frozen). These were separately recorded runs;
retain them rather than replacing unfavorable results. Receipts:
`build/native-entity-order-receiver-fix/`.

Subsequent source [`71762d60`](https://github.com/HungLo2020/MattMC/commit/71762d602195e0cc5ca29006a2bed1603b53eab8)
adds native atlas construction and model baking. Its [resource verification](../resources/VERIFICATION.md)
initially recorded preparation/CPU workloads. The historical `9f372451` checkpoint
above later adds combined lifecycle/image/timing evidence; the preceding
`c487f375` runtime pass/failure still does not verify that later source.

### Historical tag-view/GUI checkpoint

Preceding `a99b6f8d` separately passes four GUI pairs/eight original reviews
and its five-pair resource replay. Its 20 clean timings also miss vanilla p99
(3.357/3.123 ms Current/Frozen). Guarded 15-second samples show GUI batch
allocation 184→85.5 MiB and ordinary 3D batch allocation 134→28 MiB; frame
counts/revisions differ, preventing a per-frame or isolated speedup claim.
Those GUI/resource/allocation measurements remain historical evidence for
the unchanged respective code; they are not freshly repeated measurements of
`c487f375`. Receipts: `build/native-registry-tag-views-phase-fix/` and the
[resource verification record](../resources/VERIFICATION.md).

### Historical entity-geometry consumer comparison

The historical [entity-geometry consumer](../world/entities/RUST-ENTITY-GEOMETRY.md)
uses native image `a3c8a7fb` and renderer source `19d7a274`. Its fresh seven
lifecycle cases and two settled Frozen image comparisons pass, with zero
reported validation errors. All four original images were reviewed; nine Current
artifacts and the renderer class pass identity guards. All 20 revised timing runs are clean and source/native/Frozen/prompt/prepared-input
guards pass. Median FPS Current/Frozen is 1315.6/1187.1, 764.2/726.85, 345.0/311.0
and 254.85/218.95 (vanilla/DH/shaders/combined). Only vanilla p99 fails:
3.557/3.047 ms; other p99 medians are 4.094/5.051, 5.694/7.077 and 6.313/9.537 ms.
Every declared repeat remains recorded. No aggregate acceptance or isolated
speedup is claimed. These timings predate the
[GUI template handoff](RUST-GUI-ITEM-TEMPLATES.md); the integrated checks above
supersede them for the newer candidate. CPU suites and both refreshed
Frozen oracles pass. Warmed render-thread culling samples fall from 101 to 2 MiB;
those fixed-window allocation samples have different frame counts and do not
explain p99. Receipts: `build/native-entity-geometry-living-consumer/`.

### Historical client-range/readiness comparison

The historical [client-range/readiness candidate](../world/chunk-loading/RUST-CLIENT-RANGES.md)
uses native image `77126827` and Java `LevelRenderer` source `bfe801db`.
The implementation run on October 11 executes all 20 declared alternating
comparisons on RTX3080Ti/NVIDIA `595.99.02`: 360 settle, 240 warmup and exactly
6,000 measured frames, four repeats per side for vanilla + DH and two for each
other mode. Every run is clean, and source/native/class/Frozen/prompt/prepared-input
guards pass. The aggregate fails only vanilla p99. This was an uncommitted
candidate at measurement time and was later included in published `f92ea29dd`;
the earlier native image's measurements remain distinct from that publication.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen | Gate |
|---|---:|---:|---|
| Vanilla | 1400.8 / 1174.9 | 3.217 / 3.124 | p99 fails |
| Vanilla + DH | 875.0 / 603.05 | 2.394 / 6.884 | Both pass |
| Shaders | 348.9 / 316.6 | 5.284 / 6.463 | Both pass |
| Shaders + DH | 261.15 / 225.6 | 5.608 / 8.705 | Both pass |

Seven lifecycle cases and two settled image comparisons pass, with zero GAL
validation failures; all four originals were reviewed and nine Current artifacts
pin the native image. Paired allocation profiles also pass their guards;
repeated GUI batch records and entity bounds are concrete producer targets.
Sampled allocation totals include observers and different profiled frame counts;
they do not isolate a regression, gain or p99 cause. First-frame/first-turn,
temporal, sustained-streaming, long-memory and broad gameplay acceptance remain open.
Receipts: `build/native-client-ranges-readiness/`. The earlier range-only
vanilla p99 failure remains in `build/native-client-ranges/gameplay/`.

## October 11 published DH visibility checkpoint

The following release-separated matrices and lifecycle/image/profile results
are implementation-author reports reconciled through
[`65457ef8`](https://github.com/HungLo2020/MattMC/commit/65457ef80471487cbdb4ad566e23e9eca07912bc).
This documentation review did not rerun these workloads or inspect the
untracked raw receipts. A settled rotating-view gate is not ordinary-play,
first-frame, streaming, temporal or long-session memory acceptance.

Later master source adds [resource ownership changes](../resources/index.md)
at `398a0078`. Their [resource verification](../resources/VERIFICATION.md#latest-run)
cites an earlier uncommitted build. The later combined author checks above
add client/runtime evidence but do not prove server connection or broad reload
acceptance. The rendering matrices below retain their exact
release identities and do not validate those later changes.

The latest recorded [DH visibility release](RUST-DH-VISIBILITY.md), `ebd3c9a3`, has an
author-reported 20-run matrix on RTX3080Ti/NVIDIA `595.99.02`: 360 settle,
240 warmup and exactly 6,000 measured frames per run, alternating Current and
Frozen. Vanilla + DH has four repeats per side; other modes have two. All declared runs
are retained, with source/native/Frozen/prompt/prepared-world/shader guards and
ten Current fingerprints reported passing.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen | Gate |
|---|---:|---:|---|
| Vanilla | 1431.4 / 1165.9 | 2.963 / 3.293 | Both pass |
| Vanilla + DH | 795.2 / 734.0 | 3.734 / 5.110 | Both pass |
| Shaders | 342.4 / 313.55 | 4.697 / 7.004 | Both pass |
| Shaders + DH | 262.2 / 226.0 | 6.037 / 8.647 | Both pass |

All eight measured floors pass in this settled, rotating, hidden-minimap
workload. The author also reports seven lifecycle cases and two settled Frozen
image pairs with zero GAL validation failures, nine Current fingerprints and
four original images reviewed. These results do not establish first-frame,
first-turn, temporal, sustained-streaming, long-session memory or broad gameplay
parity. They do not isolate the visibility migration's FPS contribution.

The warmed Current profile has 256 native collector samples and no Java node
iterator samples, versus 64 iterator samples in the predecessor. Inclusive
render-list samples are 272/278; compilation remains active, so these counts
are diagnostic rather than an isolated CPU speedup. Receipts remain under
`build/native-dh-visibility/`, including `gameplay/performance.json`,
`gameplay/runtime-review.json` and `warmed-profile-comparison.json`.
Source-author cleanup/archive records do not mean this documentation review
performed cleanup or independently inspected those raw artifacts.

## October 11 region-tape evidence

The preceding [retained region-tape release](../world/chunk/RUST-REGION-TAPE.md)
`06529556` has author-reported seven fresh lifecycle cases and two settled
Frozen image comparisons, but no new FPS matrix. Its three CPU19 comparisons
load/parse forty saved chunks per round: Current 12.20–12.31 ms versus Frozen
13.14–13.53 ms, ratios 0.909–0.928. Three same-CPU previous-Current controls
also pass. The timing driver calls region read/parse directly and excludes
`IOWorker`/`ChunkMap` asynchronous scheduling. Those component timings must not
be spliced into the earlier CPU11
hydration series or attributed to gameplay FPS. The prior hydration and primitive
saved-palette regressions remain in their owner guides. Sampled Java allocation
in the earlier hydration profile excludes native allocation and is not a memory
bound. Source-author artifact retention/pruning records describe their work;
this documentation review performed no artifact cleanup.

## October 11 saved-DH-light matrix

The historical saved-light candidate `a15c0555` on `abbd16d3d` had a 20-run matrix
under the restored NVIDIA `595.99.02` driver. All runs are clean, all source,
native, untouched-Frozen and protected-prompt guards pass, and ten Current
artifacts fingerprint this library. Protocol: alternating isolated JVMs, 360
settle frames, 240 warmup frames and exactly 6,000 measured frames; four DH
repeats per side and two per side in other modes. All repeats are retained.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen | Gate |
|---|---:|---:|---|
| Vanilla | 1362.55 / 1148.50 | 3.365 / 3.337 | p99 fails |
| Vanilla + DH | 877.20 / 709.40 | 2.299 / 5.322 | Both pass |
| Shaders | 344.65 / 315.80 | 4.748 / 6.737 | Both pass |
| Shaders + DH | 261.65 / 226.85 | 5.494 / 8.743 | Both pass |

The aggregate floor gate **fails**: vanilla p99 is slightly above Frozen (about 0.03 ms).
Every FPS floor and the other three p99 floors pass. This is a settled rotating
workload with hidden minimap and readiness restarts; it does not isolate a
saved-light FPS improvement or establish sustained travel, startup, temporal or
long-session memory acceptance. Seven lifecycle cases and both settled Frozen
image pairs pass with zero reported validation errors; nine Current artifacts
fingerprint this library and all four original images were reviewed. Existing
color/lighting differences remain; settled images do not prove temporal parity.
The clean diagnostic DH profile maps this exact library but does not sample
saved-light importer calls; it cannot prove live importer admission.

Receipts: `build/native-dh-saved-light/gameplay/performance.json` and paired
artifacts under `artifacts/graphics-captures/validation/native-dh-saved-light-perf-20261011/`.
Individual saved-light FPS values remain in the
[pinned summary](https://github.com/HungLo2020/MattMC/blob/b32fd91c847f3660bebec4dcc8d268efcdd589c8/SUMMARY.md)
and raw receipts; current root `SUMMARY.md` records the latest tested candidate. Twenty
completed generated world copies were pruned after evaluation; input and result
receipts remain. See [saved-light ownership and component checks](../world/lighting/RUST-DH-SAVED-LIGHT.md).

## October 11 complete DH box matrix

Reboot restored matching NVIDIA kernel/userspace `595.99.02`; NVML and Vulkan
successfully enumerate the RTX3080Ti. Complete [Rust DH box production](RUST-DH-VERTICAL-FACES.md),
native `0133e073` tested on integrated source `abb768873`, passes seven lifecycle
scenarios and two reviewed settled vanilla/Iris+DH Frozen image pairs, with zero
reported validation errors. Nine Current correctness artifacts fingerprint it.

Its predeclared rotating-view matrix contains 20 isolated alternating runs:
360 settle frames, 240 warmup frames and exactly 6,000 measured frames per run.
DH has four repeats per side; other modes have two. Every runtime health,
Current/Frozen source, native and protected-prompt guard passes, all ten Current
artifacts fingerprint the image, and no owned clients remain. Every declared
repeat is retained; none was discarded or replaced.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen | Gate |
|---|---:|---:|---|
| Vanilla | 1427.95 / 1171.55 | 2.905 / 3.024 | Both pass |
| Vanilla + DH | 841.15 / 676.20 | 3.064 / 5.653 | Both pass |
| Shaders | 346.20 / 318.95 | 4.456 / 5.976 | Both pass |
| Shaders + DH | 266.50 / 226.65 | 5.445 / 8.757 | Both pass |

Every measured FPS/p99 floor passes. The workload hides the minimap and can
restart partial timing windows while terrain becomes ready. It does not
establish ordinary visible-HUD, sustained travel, startup, first-frame, temporal
or long-session memory acceptance. Prior matrices remain historical;
these repeats do not isolate the DH producer's contribution to FPS.

Receipts: `build/native-dh-box-gameplay/performance.json` and paired artifacts
under `artifacts/graphics-captures/validation/native-dh-box-perf-20261011/`.
All individual FPS values remain in the raw receipts; root `SUMMARY.md` tracks
the newer candidate above.
See [hardware preflight](RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures).

## October 11 native handoff matrix

Reboot restored matching NVIDIA kernel/userspace `595.99.02`. NVML and
Vulkan enumerate the RTX3080Ti. Native handoff `111c5922` on source `660d19e1`
passes seven Iris+DH lifecycle cases and two reviewed settled Frozen pairs.
Those checks do not prove temporal rendering or sustained streaming.

That predeclared matrix includes 20 isolated alternating runs, each with
360 settle frames, 240 warmup frames and exactly 6,000 measured frames. DH has
four runs per side; the other modes have two. All runtime health, source,
untouched Frozen, native and protected-prompt guards pass. Every Current run
fingerprints the same native image. Every declared run is retained.

| Mode | Median FPS, Current / Frozen | Median p99 ms, Current / Frozen | Gate |
|---|---:|---:|---|
| Vanilla | 1401.55 / 1175.70 | 3.317 / 3.137 | p99 fails |
| Vanilla + DH | 812.35 / 828.20 | 3.387 / 4.094 | FPS fails |
| Shaders | 342.15 / 315.95 | 5.121 / 6.863 | Both pass |
| Shaders + DH | 261.85 / 227.15 | 6.233 / 8.626 | Both pass |

Overall performance acceptance fails. This existing rotating-view workload
hides the minimap and can restart partial timing windows while terrain becomes
ready. It does not establish ordinary visible-HUD, sustained travel, startup,
first-frame, temporal or long-session memory acceptance. The older `839ff128`
matrix remains historical; driver and intervening ownership changes prevent an
isolated before/after speedup claim.

Receipts: `build/native-dh-handoff-gameplay/performance.json` and the paired
artifacts under `artifacts/graphics-captures/validation/native-dh-handoff-perf-20261011/`.
See [handoff verification](RUST-DH-COLUMN-HANDOFF.md) and
[hardware preflight](RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures).

## Image and DH allocation constraints

The [raw-image resolver at `fc1d529d`](https://github.com/HungLo2020/MattMC/blob/fc1d529db2cc6ec80a4ad7a86b15007c17d0e097/src/main/java/net/vulkanic/gui/RustGalGuiRawImageAssets.java)
remembers resource-manager misses, avoiding repeated file probes for dynamic
minimap identities. It clears those misses on resource reload and clears the
set before adding another entry when it already holds 4,096 entries. This is a
bounded miss cache, not permanent suppression: explicit copied assets, atlas
resolution and dynamic-image fallbacks remain separate; decode failures are not
cached as misses. A missing file does not mean a dynamic texture is absent.
The commit adds no dedicated miss-cache
regression; check repeated misses, reload and live minimap updates when changing
this path. See [the diagnostic report](GOAL-5-STATUS.md#october-10-capture-minimap-and-dh-cost-report)
for its author-only sample reductions and unchanged FPS conclusion.

VoxelMap publishes pixels when its dirty flag is consumed, with a cold-cache
snapshot for a new texture identity. Clean frames reuse an immutable snapshot.
The dirty flag is consumed atomically before copying, so a worker update during
the copy remains pending; failed publication restores it. Changing zoom/filter
sources and native cache recreation must still publish the appropriate image.

Raw GUI image ABI 78 carries changed payloads and the complete live identity
manifest. Rust retains unchanged pixel vectors and resources, evicts omitted
identities, and validates the combined resident bounds before committing.
Failures retain the prior native generation and Java's pending changes for retry.
An empty manifest means full replacement, including an empty reset. A recreated
native context receives all Java resident payloads. This prevents a changing
minimap/font from retransmitting a retained title panorama.

The [allocation follow-up](https://github.com/HungLo2020/MattMC/commit/dd8852a5717d0fc4fd2fe75f2aba173614ee8faa)
lets internal raw-image assets adopt freshly produced arrays. Public staging
entry points still copy caller-owned arrays, the public pixel accessor remains
defensive, and the bridge record copies the internal immutable payload. Keep
that ownership boundary when changing VoxelMap or atlas publication; fewer
intermediate copies do not make the path zero-copy.
The same change skips diagnostic identity strings when diagnostics are disabled,
avoids per-block sprite iterators and `Direction.values()` clones, and uses
primitive keys in hot model-sprite/DH provenance maps.

The later [mesh/bridge follow-up](https://github.com/HungLo2020/MattMC/commit/90d31038f246143bc2c4449e6b9db896f83e0d58)
copies or converts only absent geometry, creates per-mesh resource sets lazily
outside the shared-page route, and splits `submitWorldFrame` packing into helpers.
Its author-recorded hand-played measurements reduce GAL creates per new mesh
key from 2.6–3.8 to 2.3 and mesh preparation from 475–534 to 446 µs per new key.
The reported C2 compilation change from 60.6 to 9.3 seconds is also a component
observation. Session FPS varied with terrain throughput; none of these reports
isolates an FPS gain or validates the current head. See
[the ownership constraints](RENDER-ARCHITECTURE.md).

Packed DH admission checks restricted material/normal bytes directly. Position
and light fields are unsigned 16-bit values by representation; validation must
not recreate a ByteBuffer and Java vertex record for each packed vertex.
This removes one measured allocation source without changing DH topology,
reduction, source colors, lighting or generation policy.

Run the regular Java rendering suites and Rust suite, plus:

```sh
python3 -m unittest discover -s DevUtils/tests/rendering -p test_ordinary_performance.py
```

The Java and Rust suites cover dynamic snapshot reuse/replacement/reload,
native patch retention/rejected-update retry and packed DH bounds/allocation.
The [Python fixtures](https://github.com/HungLo2020/MattMC/blob/ee34f2ad99921848d8fc5d63da93eb6c583786c4/DevUtils/tests/rendering/test_ordinary_performance.py)
cover output creation, long-stall accounting, gameplay/motion rejection,
read-only source compatibility and requested-JDK selection despite an inherited
older Java. The launch regression resolves a real Java executable with the
game VM flags but opens no game window; it requires POSIX and JDK 25. The
frame-agent return-path/DH-call test requires a complete JDK 25. Retain either
environment-dependent skip explicitly.
These fixtures do not replace ordinary visible-map gameplay and normal
visual/lifecycle checks.

## Recorded evidence and limits

### DH producer profiles

The [record at `bffd0eef`](https://github.com/HungLo2020/MattMC/blob/bffd0eef886a446a480cf62166da2eba448eb574/PROGRESS.md)
reports paired eight-second DH allocation flights for lighting/emitter release
`3f00217d`. These use the terrain-flight observation protocol, not the separate
three-window ordinary comparison above. All six original-resolution F3 endpoints
were reportedly reviewed and match, crossing seven X chunk columns. Source,
native library, Frozen, protected prompt, prepared save, movement, terminal and
owned-process guards pass. Native lighting-field publication appears in DH
worker stacks, and opaque/translucent/water submissions are positive in both
travel videos. This documentation review inspected committed reports and source,
not their unbundled runtime/profile artifacts; no clients or Java/Rust suites
were rerun here.

Weighted Java allocation is **1.108 GB Current / 4.448 GB Frozen**. Legacy Java
light queues still sample 36.70/34.60 MB; the earlier height-field Current run
sampled 128.97 MB. Native allocation is excluded, subsets overlap, and sparse
sample weights do not measure exact total allocation or isolate throughput.
Current's 4.05 GiB short-window peak RSS is not a long-session memory bound.
Actual producer admission is stronger evidence than merely finding a native
implementation, but these observations do not establish temporal pixel parity.
See [lighting ownership](../world/lighting/RUST-DH-LIGHTING.md).

A separate guarded eight-second Current CPU flight reports 394 native-lighting
samples and 44 emitter-producer samples. Java voxel-column construction remains
at 1,566 samples, including biome reads (694) and block reads (221), among
13,011 total; subsets overlap. Its three F3 endpoints were reviewed, with
positive DH submissions and 0.545-block return drift. There is no paired Frozen
CPU run. Use this to choose further ownership work, not to claim an isolated
speedup. Receipt: `build/native-dh-lighting-migration/current-cpu-travel-review.json`.
The [settled timing matrix for that release](GOAL-5-STATUS.md#october-10-dh-lighting-and-emitter-summary)
meets all measured floors but retains the initial DH miss, four-repeat extension
and narrow DH margin. It predates the `fc1d529d` capture/minimap/DH changes and
does not validate them. Neither that matrix nor these flights establishes entry,
sustained streaming, first shader world frame or absence of pop-in/flicker.

The preceding [height-field release](../world/chunk/RUST-DH-HEIGHTMAPS.md)
`574b68f0` reports matching paired DH travel and native height production:
weighted Java heightmaps 7.34/74.45 MB, total 1.50/4.31 GB Current/Frozen, and
short Current peak RSS 4.36 GiB. These are separate measurements. Earlier
face-policy-only `2f8255f4` profiles report 0.818/2.401 GB ordinary and
1.707/4.453 GB with DH. Shared-cache release `30586383` later reports zero
contended points among 193 meshing samples versus 62/238 before, across eight
workers. Interrupted originals stay rejected; only the explicitly accepted
replacement profiles count. See [cache/profile provenance](RUST-TERRAIN-CULLING.md#concurrent-model-cache-reads).
No cross-release comparison isolates a single change's FPS effect.

Before retiring profile worlds, follow [flight retention](ARTIFACT-STORAGE.md#verification-driver-retention).
Recognized completion and capture-engine exit zero allow workspace retirement
without certifying F3 position review; incomplete enclosing receipts retain the
copy. The author reports that an earlier interrupted allocation world was
already deleted; retained logs/images/profile data do not recover that world.

### Integrated light-map profiles

The [record at `971e0226`](https://github.com/HungLo2020/MattMC/blob/971e0226b5c45b6fb3b2699e6bdb92be27021261/PROGRESS.md#L60-L65)
reports four ordinary eight-second CPU/allocation flights for integrated release
`b7297d06`, after combining native light maps/direct scalar consumers with the
upstream recorder, allocation and lazy-mesh changes. These profiles are separate
from the three-window ordinary-gameplay protocol below. The author reports
terminal, process, source/library/Frozen/protected-edit, movement and cleanup
guards passing, with all twelve F3 images reviewed. Both CPU flights have
identical endpoints; allocation endpoints differ by at most 1.089 blocks, and
both sides cross seven X chunk columns. Four completed copies were retired.
The initial PID/focus rejection remains retained; no failed profile was relabeled
or validator relaxed.

Weighted Java allocation is **0.823 GB Current / 2.398 GB Frozen**. The map-copy
path has zero sampled Current allocation versus 94.37 MB Frozen. This excludes
Rust allocation; zero sampled Java allocation does not make the entire native
or FFM path allocation-free. Canonical map snapshots now share a native sharded
root, while sparse Java pins preserve layer identity. Public Java-map aliases,
custom layers and escaped arrays retain compatibility paths. See
[the ownership contract](../world/lighting/RUST-LIGHT-MAPS.md).

Current section preparation samples 25.17 MB, including 8.39 MB in Java
occlusion-cache tables; those categories are not additive. Direction-array
cloning and legacy packet export have zero samples after the upstream changes,
while `ResourceLocation` strings sample 5.24 MB. CPU leaf samples include
native translucent sorting (962), Java section preparation (319), culling (40)
and scalar light (56). Diagnostics remain enabled. Sparse sampling and combined
changes limit attribution; none of these values isolates a migration FPS gain.
Short RSS windows do not prove long-session memory bounds. Receipt:
`goal5/native-light-map-integrated-flight-profile-v2-20261009/profile-comparison.json`.

The [integrated settled matrix](GOAL-5-STATUS.md#october-9-integrated-light-map-and-scalar-summary)
fails vanilla p99 for that historical release (3.277 ms Current / 3.097 ms Frozen), although its
median average-FPS floors pass in all modes. Clean profiles and diagnostic
settled images do not establish entry, sustained streaming, bulk-input pixel
parity, absence of pop-in/flicker or broad visual acceptance.

### Earlier profile checkpoints

The [pre-sync light-map record](https://github.com/HungLo2020/MattMC/blob/971e0226b5c45b6fb3b2699e6bdb92be27021261/PROGRESS.md#L36-L53)
for `7580a46e` reports four eight-second profiles and twelve reviewed F3 images,
with movement/integrity/cleanup guards passing. Weighted Java allocation is
0.757 / 2.369 GB Current/Frozen; the map-copy path samples 1.05 / 74.45 MB.
The remaining Current copy-path sample is an FFM lease object. Both sides cross
seven X chunk columns with endpoint drift at most 1.089 blocks. Native scalar
light has 46 samples, map-related work 71 and dynamic translucent sorting 1,020;
diagnostics affect attribution and these categories must not be added together.
Native allocations are excluded. The associated settled matrix fails vanilla
FPS and p99. These profiles precede the resource/allocation integration and do
not measure `b7297d06`. Receipt:
`goal5/native-light-map-flight-profile-20261009/profile-comparison.json`.

The earlier [fog/cache record at `3adbe6d5`](https://github.com/HungLo2020/MattMC/blob/3adbe6d5d85ecf82a8b43c58b81c4535cec8007a/PROGRESS.md#L73)
reports four ordinary CPU/allocation profiles for release `f449557e`, separate
from the three-window protocol below. Process, source, library, movement and
cleanup guards pass; twelve actual F3 positions were reviewed, and four verified
copies were retired. Forward/return endpoints differ by at most 0.545 blocks.
Weighted Java allocation over eight seconds is 0.951 GB Current / 2.437 GB
Frozen, with no sampled Current Java sky/fog-grid allocation. This excludes
Rust allocation. Zero sampled Current native color-method CPU stacks do not
prove absence of execution, and substantial Current JIT activity limits
attribution. The 89.13 MB of sampled Current light-map copying identifies further
work, not an accepted speedup or long-session memory result. Receipt:
`goal5/native-live-biome-fog-cache-flight-profile-20261009/profile-comparison.json`.

The `f449557e` profiles and [settled matrix](GOAL-5-STATUS.md#october-9-live-fog-and-validated-color-reuse-summary)
predate the session recorder, allocation/lazy-mesh and native light-map changes.
Vanilla p99 still fails in that matrix; entry, sustained streaming, pop-in/flicker and broad
visual acceptance remain open. The following record retains its earlier
`ee34f2ad` source and ordinary-driver workload scope.

The [record at `ee34f2ad`](https://github.com/HungLo2020/MattMC/blob/ee34f2ad99921848d8fc5d63da93eb6c583786c4/PROGRESS.md#L53-L55)
reports a completed DH-on, shaders-off, visible-minimap comparison after fixing
child `JAVA_HOME` and `PATH`. The first attempt failed before startup because
the inherited older JVM rejected the game flags; that retained fixture has no
usable timing or native-library mapping. Six JDK 25 harness checks are reported
passing after the fix.

The corrected workload uses the same copied world, 30-second entry/standing/travel
windows and Current/Frozen/Frozen/Current order (two observations per side):

| Phase | Median client-loop FPS, Current / Frozen | Median run p99, Current / Frozen (ms) |
| --- | --- | --- |
| Playable entry | 535 / 555 | 6.740 / 5.904 |
| Standing | 619 / 601 | 2.625 / 2.911 |
| Forward movement | 634 / 623 | 3.000 / 2.904 |

Entry and travel performance remain open. Current records 17 frames above
50 ms across entry/travel repeats, versus two on Frozen across all phases.
The author reports source/library/Frozen/original-world guards passing, no
client exceptions or remaining owned clients, twelve reviewed HUD snapshots
and four retired copies. Receipt:
`goal5/native-terrain-light-ordinary-integrated-jdk-fixed-20261009/comparison.json`.
The initial failed JVM fixture remains.

Both versions have partially built entry terrain; the first Current image is
less complete, but captures are not frame/time registered. Standing/travel
terrain broadly agrees while minimap details differ. Forward movement covers
about 16.45 blocks before a terrain barrier: completion does not establish
sustained chunk streaming, bulk-path pixel parity or absence of pop-in/flicker.
The all-frame observer and three screenshots per client add diagnostic overhead.
Keep these ordinary observations separate from the
[integrated settled matrix](GOAL-5-STATUS.md#october-9-bulk-terrain-light-and-ordinary-gameplay-summary)
and the earlier bulk-execution/allocation profiles.

The earlier [ABI 78 commit](https://github.com/HungLo2020/MattMC/commit/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953)
reports short shaders-off/DH-on travel runs near Frozen, but says that result
was not reproduced in manual play and that visual parity captures still timed
out. It claims no performance pass. The [live-light matrix](GOAL-5-STATUS.md#october-9-live-light-and-ordinary-gameplay-summary)
in the retained summary predates this GUI/harness change and fails vanilla
FPS/p99 and DH p99. Keep those separate workloads and source identities.
This documentation review inspected source and committed author records; it
did not run clients, Java/Rust suites or profiles, or inspect the original
runtime receipts.

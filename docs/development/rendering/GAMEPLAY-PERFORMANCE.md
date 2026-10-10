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

## Image and DH allocation constraints

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
still fails vanilla p99 (3.277 ms Current / 3.097 ms Frozen), although its
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

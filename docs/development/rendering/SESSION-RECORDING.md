# Recording a hand-played session

Use this when the game feels slow while you play and the scripted benchmarks
don't show it. You control the game; the recorder buffers frame and tick samples for diagnosis.
Check the dropped-row counters before treating a recording as a complete timeline.

```sh
python3 DevUtils/RunDev.py --record [--record-label vsync-on]
```

Play normally, reproduce the problem for a minute or two, then quit the game.
The launcher prints a summary and saves everything under
`artifacts/recordings/<date-time>[-label]/` (ignored by Git). Re-summarize later
with `python3 DevUtils/PerfAudit/Recording.py summarize <directory>`.
`--record` records Current only. Delete recordings you no longer need; see
[capture storage](ARTIFACT-STORAGE.md).

## Unattended look-around runs

[`RunScriptedLook.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/rendering/RunScriptedLook.py)
records the same data without anyone at the keyboard:

```sh
python3 DevUtils/tests/rendering/RunScriptedLook.py --label baseline [--seconds 150] [--perf-after 30]
```

It copies `run/`'s `options.txt`, `config/`, `voxelmap/`, `resourcepacks/` and
one world (`--world`, default `New World`) into the recording folder, forces
VSync off, `pauseOnLostFocus:false` and windowed 1920×1012, and launches
Current straight into the copy. Every frame,
[`ScriptedCameraSweep`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/dev/ScriptedCameraSweep.java)
sets yaw to ±150° around the initial yaw with a 2.4 s full sine period, and
pitch to ±60° with a 1.6 s period (`--yaw-*`, `--pitch-*`), with the player's
own minimap, DH and video settings. Updates require a player and world with no
screen open. The client
stops by itself after `--seconds` in the world; ordinary end-of-run cleanup deletes the copied world
unless `--keep-game-dir`. `--perf-after N` adds a flat `perf` profile N seconds
into the sweep (needs `kernel.perf_event_paranoid` ≤ 1); no profile is produced
if the client or `perf` cannot be found. `harness.json` records
the sweep, the commit and memory/swap before and after; compare runs only from
similar machine states. This is a Current-only diagnostic run, not a paired
Frozen acceptance test. A zero exit status follows the sweep-completion log
marker; it does not independently require a successful Gradle exit, clean
runtime health or rendering parity. Tests: `python3 DevUtils/tests/rendering/test_scripted_look.py`.

The current recorder PID lookup selects the first JVM matching a recorded
KnotClient, without matching this invocation’s recording directory or process
start identity. Concurrent recorded clients can therefore misattribute samples
or profiling, and the scripted timeout can signal another matching client.
Avoid concurrent recorded sessions; [#824](https://github.com/HungLo2020/MattMC/issues/824)
tracks the invocation-identity defect. Synthetic process-directory fixtures
confirmed the selector mismatch; no real client was signaled or launched in
that review.

The stall sampler’s `blocked_ms` column is elapsed time minus running and
run-queue time, so it includes sleeping and is not proof of blocking on a
particular resource. System PSI and reclaim figures describe the whole machine. Stall sampling
requires Linux `/proc/pressure/cpu`; raw PSI fields are microsecond deltas, with
percentages derived in the summary.

Vary one setting per run with repeatable `--option key:value` (copied
`options.txt`) or `--config 'relative/file:key=value'`, for example
`--config 'config/voxelmap.properties:Hide Minimap=true'` or
`--config 'config/DistantHorizons.toml:numberOfThreads=2'`. Options apply after
the forced defaults and can replace them. Config edits replace only the first
matching key, retaining its separator and simple surrounding double quotes;
they are not TOML-section-aware and reject a missing key. Use existing copied
files and paths relative to the copied game directory, without `..`: the
implementation does not enforce path confinement. `harness.json` retains the
requested overrides so comparisons can identify the changed controls.

The active sweep reports input each frame, and the default copy sets
`inactivityFpsLimit:"minimized"`. With AFK limiting selected, the vanilla client
otherwise caps at 30 FPS after 60 seconds idle. A minimized window still caps
at 10 FPS, so keep it unminimized. The [author's harness notes at `fc1d529d`](https://github.com/HungLo2020/MattMC/blob/fc1d529db2cc6ec80a4ad7a86b15007c17d0e097/docs/development/rendering/SESSION-RECORDING.md)
report early AFK-affected runs at a third of their actual rate and up to 2× FPS
variation with external load. Those observations are not renderer speedups.
Compare render-thread CPU and run-queue time per frame (`stalls.csv`) and the
"everything else" CPU (`cpu.csv` system minus client) alongside FPS.

Independent review at `fc1d529d` passed five synthetic scripted-setup fixtures,
including config separators/quotes and missing-key rejection. They do not run
the client or establish live AFK prevention, profiling correctness or performance.

## What is recorded

| File | Contents |
| --- | --- |
| `summary.md` | Device, settings, FPS, frame-interval percentiles, per-stage breakdown, slowest frames, slowest loop phases, GC, GPU and CPU use |
| `frames.csv` | Buffered presented Rust frames: Java acquire/submit/present timestamps plus every field of the native whole-frame result and profile (Vulkan acquire/present/wait times, present mode, GPU timestamps, draw counts) |
| `ticks.csv`, `phases.csv` | Buffered `Minecraft.runTick` samples: start, start-to-start interval, duration and exclusive time per instrumented phase (`id:ns;…`). Phases named `mc:<section>` are the client's vanilla profiler sections (entities, block entities, particles, VoxelMap tick…), timed on their own stack; they overlap the other phases |
| `seconds.csv` | Once a second: GC, heap, JVM CPU, JIT time, FPS counter, VSync, FPS limit, render distance, window size, current screen |
| `client.jfr` | Java Flight Recorder profile of the client JVM (`settings=profile`) |
| `gpu.csv`, `cpu.csv` | Once a second: `nvidia-smi` utilization/clocks/power, system CPU, iowait and client process CPU |
| `console.log` | The client console, including the `MattMC Vulkan device selection` line |
| `stalls.csv`, `stalls-system.csv` | Once a second: per selected client thread on-CPU time, run-queue wait, residual time and page faults; system-wide PSI deltas, swap and reclaim counters |

Frame/tick timing columns use nanoseconds relative to recording start or
durations as named in their headers. External CPU/stall samplers use epoch
seconds and their stated units. The frame columns follow the bridge records
automatically, so new ABI fields appear without recorder changes.

Read `mc:<section>` as a separate breakdown: time is exclusive within that
profiler stack but overlaps the `GraphicsFrameBenchmark` phases. Do not sum
the two families into total tick time. The recorder's vanilla-profiler adapter
is selected only when the pie, single-tick, metrics and custom profilers are
inactive; enabling one can remove the `mc:` observations. Nested
`world.static-terrain.*` phases separately split tracker events, completed-build
draining, visibility selection, scheduling and cache cleanup. A changed phase
breakdown alone is not a reduction in total frame work.

## Reading a recording

- **Wrong GPU:** the device line marks the `SELECTED` device and whether each
  candidate can present to the window.
- **Presentation-bound:** large `Vulkan present wait` or `acquire` against small
  GPU time. Check the present mode: `FIFO (vsync)` paces to the display.
- **GPU-bound:** `GPU frame (timestamps)` close to the frame interval, with high
  `nvidia-smi` utilization.
- **Java-bound:** slow frames with no native stage over 1 ms. The runTick phase
  tables and `client.jfr` show where the time went; `api.present.fps-limit` is
  the FPS limiter sleeping, not work.
- **Stalls:** the summary's JFR lines give real stop-the-world pauses and ZGC
  allocation stalls (threads blocked waiting for memory). The per-second "GC
  cycle" figure is collector time, which is concurrent under ZGC, not a pause.
  Match slow frames' times to those events and to phases in the slowest 1% of
  iterations.

## Implementation and constraints

- [`GameplaySessionRecorder`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/dev/GameplaySessionRecorder.java)
  is enabled only by `-Dmattmc.dev.recordDir`. The render thread appends
  primitives to bounded buffers; a daemon thread writes CSV every 250 ms, and a
  shutdown hook flushes the rest. If writing falls behind, rows are dropped and
  counted in `seconds.csv`.
- Phase timing reuses the `GraphicsFrameBenchmark.beginPhase`/`endPhase` calls
  already in the client loop, on the render thread only.
- Gradle's `runClient` adds the recorder property and JFR when given
  `-PmattmcRecordDir=<dir>`; `RunDev.py --record` sets it.
- The [Vulkan device selection](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/backends/vulkan/device.rs)
  logs its candidates for every windowed device, recording or not.
- Recording adds a little overhead (JFR sampling and per-frame buffering). Use
  the scripted benchmarks for acceptance numbers.
- Stop the client normally so shutdown can flush pending rows and finalize JFR.
  Shutdown flushing is best effort. Forced termination such as `SIGKILL` can
  lose buffered rows and leave JFR incomplete; the 250 ms writer cadence is
  not a maximum data-loss window.
- Summary tests: `python3 DevUtils/PerfAudit/test_recording.py`.

The summary can be generated from incomplete or missing CSV inputs. Its existence
is not proof of a complete recording or a passing performance gate. The two
summary fixtures exercise synthetic CSV aggregation and missing inputs; they do
not establish live recorder throughput, shutdown reliability or JFR capture.

# Recording a hand-played session

Use this when the game feels slow while you play and the scripted benchmarks
don't show it. You control the game; the recorder captures every frame.

```sh
python3 DevUtils/RunDev.py --record [--record-label vsync-on]
```

Play normally, reproduce the problem for a minute or two, then quit the game.
The launcher prints a summary and saves everything under
`artifacts/recordings/<date-time>[-label]/` (ignored by Git). Re-summarize later
with `python3 DevUtils/PerfAudit/Recording.py summarize <directory>`.
`--record` records Current only. Delete recordings you no longer need; see
[capture storage](ARTIFACT-STORAGE.md).

## What is recorded

| File | Contents |
| --- | --- |
| `summary.md` | Device, settings, FPS, frame-interval percentiles, per-stage breakdown, slowest frames, slowest loop phases, GC, GPU and CPU use |
| `frames.csv` | Every presented Rust frame: Java acquire/submit/present timestamps plus every field of the native whole-frame result and profile (Vulkan acquire/present/wait times, present mode, GPU timestamps, draw counts) |
| `ticks.csv`, `phases.csv` | Every `Minecraft.runTick`: start, start-to-start interval, duration and exclusive time per instrumented phase (`id:ns;…`) |
| `seconds.csv` | Once a second: GC, heap, JVM CPU, JIT time, FPS counter, VSync, FPS limit, render distance, window size, current screen |
| `client.jfr` | Java Flight Recorder profile of the client JVM (`settings=profile`) |
| `gpu.csv`, `cpu.csv` | Once a second: `nvidia-smi` utilization/clocks/power, system CPU, iowait and client process CPU |
| `console.log` | The client console, including the `MattMC Vulkan device selection` line |

Times in the recorder's CSVs are nanoseconds from recording start. The frame
columns follow the bridge records automatically, so new ABI fields appear
without recorder changes.

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
- **Stalls:** match slow frames' times to GC (`seconds.csv`) and to phases in
  the slowest 1% of iterations.

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
- Stop the client normally, or with SIGTERM, so JFR and the final rows are
  written. A killed (`SIGKILL`) client loses its last 250 ms of rows and the JFR
  file.
- Summary tests: `python3 DevUtils/PerfAudit/test_recording.py`.

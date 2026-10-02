# Render verification

What to run before calling a rendering change done. Frozen, the unmodified
Java reference checkout, is the correctness baseline: compare against it,
don't change it.

## 1. Tests

```sh
cd src/main/rust && cargo test --release     # Rust, including boundary tests
./gradlew test                               # Java
```

The architecture boundary tests run with the Rust tests; see
[Render Architecture](RENDER-ARCHITECTURE.md). Java failures reading
"Mockito cannot mock this class" are a known JDK 25 limitation, not
regressions; compare their count with the base commit.

## 2. Frozen image comparison

[`DevUtils/Audit/Capture.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/Audit/Capture.py)
captures the same world pose in Current and in Frozen. Frozen's checkout is
found through `java_perf_repo` in `DevUtils/Common/platform/directory/directories.json`
(or `--frozen-repo`). A shader-pack pair at a fixed pose:

```sh
MATTMC_CAPTURE_SHADER_PACK_SOURCE=$PWD/run/shaderpacks/ComplementaryHungLoIfied.zip \
python3 DevUtils/Audit/Capture.py --profile standard \
  --mode current-rust-vulkan-shaders-on --mode frozen-opengl-shaders-on \
  --world Origin --rust-selected-source-execution --diagnostic \
  --capture-world-time 6000 --capture-camera-pose=150.5,100,530.5,105,10 \
  --artifact-dir artifacts/graphics-captures/my-check
```

Use `--mode current-rust-vulkan-shaders-off --mode frozen-opengl-shaders-off`
without the pack for vanilla. The pair lands in
`paired_visual_static_terrain/pair-01/` (both images, a side-by-side and an
amplified diff). A per-channel mean absolute error, masking the chat area:

```python
import numpy as np; from PIL import Image
d = "artifacts/graphics-captures/my-check/paired_visual_static_terrain/pair-01/"
a, b = (np.asarray(Image.open(d + n).convert("RGB")).astype(float)
        for n in ("current_rust_vulkan_01_initial.png", "frozen_java_opengl_01_initial.png"))
m = np.ones(a.shape[:2], bool); m[570:610, :1000] = False
print(np.abs(a - b)[m].mean(0))
```

- Compare **before and after in the same session.** The error moves between
  sessions with no render change (for example day 2.38 vs 2.64), so a
  stored baseline from another day doesn't prove a regression.
- Check Vulkan validation output: `grep -rho 'VUID-[A-Za-z0-9_-]*'` over the
  Current capture directory should find nothing.
- Distant Horizons pairs vary by about 0.5 between identical runs; repeat them.
- A Current capture can fail in the `measurement` phase with `timed out
  waiting for settled submitted work ... rust-terrain-settled=N/8` and no crash
  report. This also happens on unchanged code; rerun the pair before
  suspecting your change, and compare against a baseline run the same way.

## 3. Real-config session

Set diagnostic environment options before launching. Production renderer
options are snapshotted on first access through `core/environment.rs`; changing
the environment later does not reconfigure that process. Unit fixtures can
use thread-local scoped overrides to exercise different diagnostic modes.

Run the game with your own settings (`python3 DevUtils/RunDev.py` uses the
release native profile) with shaders on and off, join a world, and watch the
log for `panicked`, `Game crashed` or `VUID`. The shader route should report
`shader route active` after warm-up. Capture both stdout and stderr: native
shader diagnostics are not necessarily copied into `run/logs/latest.log`.
An `active` message immediately followed by `vanilla fallback` is a failed
shader frame, not successful shader rendering. Verify that Shader Packs appears
in Video Settings and that toggling the configured pack changes the rendered
world; unit tests alone do not exercise the complete startup and live pass graph.

## 4. Performance A/B

Measure with the moving-camera frame benchmark, enabled through JVM properties,
and always with the release native profile:

```sh
JAVA_TOOL_OPTIONS="-Dmattmc.dev.graphicsFrameBenchmark=true \
 -Dmattmc.dev.graphicsFrameBenchmark.status=/tmp/bench-status.json \
 -Dmattmc.dev.graphicsFrameBenchmark.workloadProfile=moving-camera \
 -Dmattmc.dev.graphicsFrameBenchmark.cameraPathType=moving-camera \
 -Dmattmc.dev.graphicsFrameBenchmark.stopAfterComplete=false" \
./gradlew -PmattmcRustProfile=release runClient -x test \
  "--args=--quickPlaySingleplayer=Origin --width 1280 --height 720"
```

Add `cameraX`, `cameraY`, `cameraZ`, `cameraYaw`, `cameraPitch` and `yawDelta`
(same prefix) to pin the camera path so runs are comparable.

The status file reports `validity.measuredAverageFps`, per-phase CPU times
under `exclusivePhaseNanos` (the native ones are `rust-gal.native-profile.*`:
GAL validation, hazard analysis, backend encode and submit, renderer phases)
and `submittedWorkCounts`.

- Run 5 times per side, with and without Distant Horizons, in the same session.
  Build the baseline by stashing your change, run it, then restore and verify
  the tree.
- Entity counts differ between runs and move fps; compare with entity count as
  a covariate (`submittedWorkCounts.model`), not raw means alone.
- Treat differences within about one standard error as noise. Large
  improvements you didn't design (code-layout effects) aren't wins to claim.

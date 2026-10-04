# Render verification

What to run before calling a rendering change done. Frozen, the unmodified
Java reference checkout, is the correctness baseline: compare against it,
don't change it.

## 1. Tests

Run from the repository root; the subshell preserves the Rust directory configuration without changing the next command's working directory.

```sh
(cd src/main/rust && cargo test --release)   # Rust, including boundary tests
./gradlew test                               # Java
```

The architecture boundary tests run with the Rust tests; see
[Render Architecture](RENDER-ARCHITECTURE.md). Java failures reading
"Mockito cannot mock this class" are a known JDK 25 limitation, not
regressions; compare their count with the base commit.

For GUI target declarations, [commit `78e8e04`](https://github.com/HungLo2020/MattMC/commit/78e8e0423084f010bb47e36132550619b37644c2)
adds two GUI unit tests and extends one existing world-frame test:

- [Blur scratch declarations](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/guirender/frontend/tests.rs#L2565-L2606)
  check that recorded scratch passes are admitted with their owned-target list
  and rejected with an empty list.
- [Custom post-effect declarations](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/guirender/frontend/tests.rs#L2608-L2659)
  check a main → private intermediate → main chain and the same empty-list
  rejection. This fixture uses no external bindings or depth inputs; their
  ownership handling is source-inspected implementation, not new test coverage.
- The added [prebuilt-blur case](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/tests.rs#L2617-L2669)
  in `source_candidate_prepares_matching_png_assets_without_admitting_execution`
  checks that missing stats reject without increasing the submission count,
  then supplying the recorded stats submits that frame exactly once.

The two GUI tests use [mock GAL](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/guirender/frontend/tests.rs#L2661-L2665).
The extended world case uses the
[test-only selected-source coordinator](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/submit.rs#L200-L212);
actual mid-frame [source preparation/arming](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/rust/render/worldrender/submit.rs#L152-L158)
is compiled under `cfg(not(test))`. These assertions cover stats propagation
and zero-versus-one submission, not execution of that production transition.
No dedicated item-raster entry case was added.

To exercise those cases from the repository root:

```sh
(cd src/main/rust && cargo test --release source_frame_gui_validation)
(cd src/main/rust && cargo test --release source_candidate_prepares_matching_png_assets_without_admitting_execution)
```

This documentation review inspected their source only; it did not execute the
tests or reproduce the original menu/world-entry failure in a live client.
After rebuilding native release, repeat the affected blurred-menu/world-entry
and custom post-effect flows, checking source-route continuity and validation
output under [real-config checks](#3-real-config-session). This bounded fix does
not establish the absence of other GUI crashes or broad rendering parity;
[Goal 5 remains incomplete](GOAL-5-STATUS.md#remaining-work).

For DH container lifetime and snapshot-pressure changes, run the real-container
regressions before a normal-overlap gameplay pair:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative -x buildRustNative \
  --tests net.vulkanic.world.DistantHorizonsSemanticCollectorTest
```

Rebuild the native release library first if Rust changed. Keep the gameplay
readiness requirement intact; repeated DH builds in a stationary scene can
indicate premature retirement rather than ordinary streaming. Unit tests alone
do not prove temporal parity or memory stability.

Run `cargo test ... direct_dh_composition` for vanilla/DH snapshot dependencies:
first and repeated frames, no-fade to double-pass changes, resize, rejected
submission, and the opaque/translucent fade boundaries. Use actual terrain
meshes in the fixture so recording reaches the compositor; empty frames can
skip that code. Retry the matching shaders-off DH gameplay workload after a
release rebuild and check both the GAL error stream and Vulkan validation.
Passing these regressions does not establish terrain-flicker or image parity.

For actual gameplay window transitions on X11, run
`python3 DevUtils/tests/rendering/ObserveGameplayResize.py --artifact-root ROOT
--mode current-rust-vulkan-shaders-on --mode frozen-opengl-shaders-on` beside
a fresh paired gameplay run with enough frames to keep both clients alive.
The driver verifies each isolated KnotClient PID and window, requests
960×540 → 1600×900 → 1280×720, checks the benchmark viewport and captured
window extent, and records subsequent frame progress. Use a separate diagnostic
run: these external observations do not prove first-resized-frame correctness,
registered image parity, flicker or performance. Inspect native route/validation
logs and memory along with `MODE-resize/resize.json`.

For source terrain stream packing, run `cargo test ... source_stream_packing`
and `cargo test ... source_terrain_frame_stream`. They check exact staged
records addressed by `firstInstance`, shared uniforms, mixed direct and
multi-draw descriptor alignment, overflow rejection, bounded capacity and
completion-gated reuse. After rebuilding release, repeat actual shader gameplay
against Frozen with validation enabled for correctness and disabled in a separate
performance pair. Compare host-write bytes and source preparation time; packing
tests alone establish neither image parity nor a performance improvement.

On a Linux display, also run the native source-chain conformance explicitly:

```sh
MATTMC_RUN_WINDOWED_CONFORMANCE=1 CARGO_TARGET_DIR=build/rust/target cargo test \
  --manifest-path src/main/rust/Cargo.toml \
  complete_source_chain_executes_once_on_a_native_acquired_vulkan_frame \
  -- --test-threads=1 --nocapture
```

The ordinary suite leaves this windowed fixture disabled. Its native warmup
must use source-entry preparation, then refresh the next frame's exact resource
snapshot before selected execution. Its DH input needs a perspective projection
and matching inverse; an identity placeholder cannot provide valid clip planes.
This test covers source-chain submission/presentation, not Frozen image parity.

For single-color source changes, `cargo test ... single_color` covers paired
discovery, output-slot preservation, particle alpha testing, explicit legacy
varyings and native Vulkan shader-module creation/cleanup. From the repo root:

```sh
CARGO_TARGET_DIR=build/rust/target cargo test \
  --manifest-path src/main/rust/Cargo.toml single_color -- --test-threads=1
```

After changing translucent discovery, also run `cargo test ... translucent`.
For ordinary terrain alpha policy, run `cargo test ... normal_terrain_alpha`;
it checks opaque/cutout defaults, disabled and explicit overrides, selected
property branches, output-slot preservation and native shader-module creation.
Run `cargo test ... source_main_function_parser` after changing alpha wrappers
or DH depth insertion. These fixtures cover signature trivia, prototypes,
commented braces and malformed definitions. Rebuild release and compare foliage
and terrain visible through leaf holes in a fresh equivalent Frozen gameplay
pair; native compilation alone does not establish cutout pixel parity.
Check shader temporal inputs separately from world time and camera equivalence.
Current's deterministic temporal capture overrides do not freeze Frozen's Iris
`SystemTimeUniforms` timer. A fixture-equivalent static pair can therefore contain
different animated cloud/water states. Keep the unchanged image tolerance and
record that limitation; establish matching source-clock inputs before accepting
animated shader parity. Do not change Frozen's renderer to make a pair pass.
Single-color water tests cover disabled/default and explicit `GREATER` alpha
tests and reject undeclared auxiliary writes. The real-pack probe below includes
the separate translucent program. Retry the original gameplay workload after a
release rebuild; source compilation does not prove water pixels or route continuity.

For pre-terrain source changes, run `cargo test ... pre_terrain_fullscreen`.
The regressions cover enabled begin/prepare ordering, native modules, resource
closure, feedback/mip requirements and missing-pair rejection. Warm-frame
checks preserve earlier writers, clear both sides of clear-enabled warm targets
before begin, and record opaque outputs before deferred feedback. A command-recording fixture checks the
begin/shadow/prepare/terrain boundaries; it does not submit its geometry. The
ordinary source-pass regression checks color preservation and depth clearing. Probe the real pack, rebuild release, and capture a fresh
Frozen OpenGL pair: compilation alone cannot prove sky or fog pixels.
For a cold radius-10 static scene, the existing `--workload-profile settled-static
--profile extended` capture allows 900 readiness frames on both clients and
retains the required quiet window. Keep any shorter-deadline failure recorded;
this profile is correctness evidence, not a startup-performance pass.

Run `cargo test ... frame_start_clears` for color lifecycle changes. The native
readback checks current/feedback clears with changing fog, explicit pack colors,
retained `clear=false` pixels and regenerated mip descendants. The lifecycle
regression checks cached-pass reuse, duplicate recording, actual GAL submission
rejection, resize/world replacement and resource retirement. Rebuild release and
retry a real pack with clear-enabled feedback targets; these fixtures do not
establish gameplay parity or the terrain-flicker root cause.

For custom property expressions, run `cargo test ... custom_expression` and
`cargo test ... bounds_custom_property_storage`. These cover type resolution,
dependency cycles, bounded work, option branches, source identity, std140 values
and native shader-module compilation. To check numerical behavior against
Frozen's actual compiled evaluator, use Java 25 with its existing compiled
classes and the fastutil/JOML dependency jars on the classpath:

```sh
java --class-path "$FROZEN_CLASSES:$FASTUTIL_JAR:$JOML_JAR" \
  DevUtils/tests/rendering/FrozenCustomExpressionProbe.java
```

The probe prints typed results and raw float bits, including rejected expressions.
Optional arguments are `type expression` pairs. It only reads Frozen's compiled
classes; it creates no renderer and establishes no gameplay parity. Recorded
Goal 5 cases and class hashes are in
`artifacts/graphics-captures/goal5/custom-uniform-expressions/`.

For built-in celestial/light uniforms, entity/hand propagation and same-frame
activation/reload/resize invalidation:

```sh
CARGO_TARGET_DIR=build/rust/target cargo test \
  --manifest-path src/main/rust/Cargo.toml builtin_ -- --test-threads=1
java --class-path "$FROZEN_CLASSES:$JOML_JAR" \
  DevUtils/tests/rendering/FrozenBuiltinUniformProbe.java
```

The Java 25 probe reads Frozen's compiled classes. It exercises the actual
`SmoothedVec2f` notifier/timer behavior and reproduces the celestial matrix
recipe with Frozen's `Axis` and JOML. It does not invoke a live celestial-uniform
provider or establish gameplay parity. Recorded inputs, outputs and class hashes
are in `artifacts/graphics-captures/goal5/builtin-frame-uniforms/`.
The entity/hand regression uses programs that read smoothed light and celestial
positions without `atlasSize` or a render-stage uniform. The activation case
starts with a valid lightmap-only frame and enables source semantics without
changing the existing frame/world/time memo fields. Rebuild release and retry
the real pack workload after these tests; packing checks do not establish
gameplay admission or visible parity.

Run `cargo test ... legacy_sampler` for protocol alias/phase selection, custom
PNG identity, raw secondary shadow ownership, late-writer cache replacement,
legacy fog/matrix lowering and a single-output shadow program. These tests
include native module compilation and failure cleanup. Supplemental MakeUp
module results are in `artifacts/graphics-captures/goal5/legacy-source-inputs/`;
they do not prove asset admission, shadow policy or particle semantics in gameplay.

For missing legacy particle inputs, use the [RenderDoc observation procedure](RENDERDOC-INPUTS.md)
to inspect the actual Frozen draw before adding a source default. Run
`cargo test ... material` after changing the compact input contract.

For scoped color/shadow directives, run `cargo test ... scoped_directive`.
These regressions cover dimension selection, declaration order, aliases,
conditional properties, empty overrides, bounded memo identity, attachment
replacement and cutout pipeline compatibility. The source probe also reports
the selected pack's shadow and color policies. Re-run built-in uniform and full
Rust tests when changing their shared traversal. Supplemental discovery and
native compilation do not establish live shadow, transition or gameplay parity.

Run `cargo test ... shadow_distance_selection` for copied-distance prerequisites,
multiplier/cap rules and 9,180 visibility decisions captured from Frozen's compiled
advanced, safe-zone, box and non-culling frustums. The numerical driver is
`DevUtils/tests/rendering/FrozenShadowCullingProbe.java`; run it as a Java 25 source
file with Frozen's `build/classes/java/main` and JOML 1.10.5 on the classpath. Its
output is the fixture `DevUtils/tests/rendering/fixtures/frozen_shadow_culling.tsv`.
It reproduces the private selection recipe with explicit inputs and invokes the
actual frustum classes, using an identity camera and upward light. Class hashes
and results are recorded under `artifacts/graphics-captures/goal5/shadow-caster-distances/`.
This is supplemental numerical evidence, not gameplay entity/shadow parity.

Run `cargo test ... entity_shadow_culling` for entity distances, eligibility,
leash unions, player-only roles, independent block-entity flags and strict bridge
transport. `DevUtils/tests/rendering/FrozenEntityBoundsProbe.java` runs with the
same read-only Frozen classpath and produces `fixtures/frozen_entity_bounds.tsv`:
108 actual world-AABB calls at zero, million-block and world-border origins.
Keep these separate from terrain's camera-relative entry point: distance-box
casts and safe-zone results differ. These identity-camera numeric cases remain
supplemental; real moving entity/shadow captures are required for parity.
For typed orb ordering and the copied-world enchanted-item/orb fixture, see
[entity shadow ordering checks](ENTITY-SHADOW-CHECKS.md). CPU boundary and ABI
regressions supplement gameplay captures; they cannot prove shadow pixels.
`ShadowOnlyEntityCaptureTest` checks that unported off-camera streams preserve
camera data and retain an admission receipt rather than disappearing silently.

The 2026-10-02 ABI 68 Current Complementary startup check completed with clean
validation and a settled screenshot. Its first renderable world frame was
91/submission 109, already on `rust-native-selected-source`; no later world
fallback diagnostics appeared. Evidence: `goal5/entity-shadow-bounds/live-complementary`.
This Current-only diagnostic run establishes startup continuity for that workload,
not Frozen image parity, entity-shadow acceptance, flicker or performance.

After an ABI change, rebuild `./gradlew -PmattmcRustProfile=release buildRustNative`
before Java or live checks. Run `WorldShaderEnvironmentEncodingTest` with
`VulkanicGalBridgeAbiTest` to check exported layouts, dirty-storage encoding and
the exact signed configuration value; the Rust bridge tests check decoding.

To diagnose an additional pack before gameplay, extract its `shaders/` contents
into a separate artifact directory, or use an exported semantic source snapshot.
The optional native probe requires a Vulkan device. It prepares the geometry
families, declared sky/celestial writers and the complete post-terrain
deferred/composite/final chain, then
compiles each retained vertex/fragment module. It reports unresolved scalar
semantics and rejects failed preparation or native compilation:

```sh
MATTMC_SHADER_PROBE_SOURCE=/absolute/path/to/extracted/shaders \
CARGO_TARGET_DIR=build/rust/target cargo test \
  --manifest-path src/main/rust/Cargo.toml configured_pack_source_diagnostic \
  -- --ignored --nocapture --test-threads=1
```

The probe does not load binary textures, admit gameplay resources or present a
frame. Without a snapshot's runtime environment it uses the explicit MC 1.21.10
Iris/Linux Overworld test environment. A passing probe is supplemental compiler
evidence only; compare equivalent real gameplay with Frozen before claiming parity.

Run `cargo test ... fullscreen_legacy_transform -- --test-threads=1` for native
16×16 readback checks of combined/separate matrices and `ftransform()`. This
checks viewport coverage, unit local positions, sampler row conversion and
fixed composite inputs without adding world-camera fields. The pre-fix
rejection and Frozen input/native-shader observations are retained under
`artifacts/graphics-captures/goal5/makeup-particle-renderdoc-v9/`.
Run `cargo test ... sky_legacy_transform -- --test-threads=1` for captured
sky-disc and celestial local inputs, clip positions and draw matrix checks.
See [RenderDoc inputs](RENDERDOC-INPUTS.md) for their capture provenance and
matrix layout. These tests cannot replace day/night gameplay comparisons.
Run `cargo test ... sky_lightmap_legacy -- --test-threads=1` for native readback
of both full-bright compatibility coordinates and their aliased lightmap
matrices across the sky disc, horizon and celestial quad. This regression also
checks that the primary matrix remains unchanged. Its baseline is Frozen's
`VanillaCoreTransformer`, including inputs optimized out of a RenderDoc shader;
it proves this input contract, not whole-pack gameplay parity.
Run `cargo test ... horizon_legacy_transform -- --test-threads=1` for native
coverage below the disc, copied horizon color, and capped render distance.
Its disc-only control leaves the lower region at the clear color. Frozen
frame 353 event 888 inputs are retained in `goal5/horizon-input-replay/`;
this supplemental check does not establish shader-pack gameplay parity.
`source_horizon_staging` checks draw order, fog-hidden disc behavior, standard
dimension gates and rejection before allocation when required inputs are absent.
The scoped Complementary result is recorded in
[`PROGRESS.md`](https://github.com/HungLo2020/MattMC/blob/master/PROGRESS.md), with
captured inputs/check logs in `goal5/horizon-input-replay/evidence.json` and
images in `goal5/complementary-horizon-fixed-pair/`. A passing static image
tolerance does not establish matching animated source clocks, terrain-flicker
repair, transitions or long-running memory bounds.
Run `cargo test ... fullscreen_coordinate_domains -- --test-threads=1` for
sky target sampling and inline inverse-projection UV conversion. The native
16×16 regression renders a prepared gradient, samples it through the world
sky writer, and checks upward view rays through a composite using `texcoord`.
It supplements real-pack images; it does not establish clouds or sky parity.
To read one real pre-terrain output, set
`MATTMC_RUST_SELECTED_SOURCE_CAPTURE_STAGE=shader-pack-stage:world0/prepare.fsh:auxiliary_h`
for a fresh Capture run with `--rust-selected-source-execution`. Keep that
diagnostic separate from the paired visual acceptance run.
Run `cargo test ... fullscreen_stage_color_binding` to check that a geometry
snapshot cannot replace a fullscreen stage's feedback image. The regression
also checks foreign pack/world rejection and cleanup after partial preparation.
Run `cargo test ... stage_color_binding` for geometry's program-local tables as
well. The opaque-to-translucent regression stages actual cached samplers with
different descriptor ordinals; scoped replacement must retain non-color inputs,
reject foreign generations, and leave the admission snapshot unchanged.

## 2. Frozen image comparison

After changing source shadow batching, run `cargo test ... shadow_batch_selection
-- --test-threads=1`. These regressions compare early selection with the existing
late frustum cull, including repeated opaque/translucent mesh ordering, original
frame positions, bounded cache eviction and invalid references outside the
frustum. Follow with a real shader-pack moving-camera benchmark and a paired
static image check; CPU batch equality alone does not establish runtime parity
or a performance gain.

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

Selected-source readbacks require a receipt for the exact gameplay frame. The
bounded `selected-source-execution-latest.json` covers steady-state submissions;
per-frame files record activations and may not exist for a later screenshot.
Stage copied producer observations when claiming a readback and retain them
only after its promotion. Deferred retries replace one pending snapshot rather
than consuming the bounded history of acknowledged captures. A missing or stale
receipt must still defer capture; do not relax the frame/producer checks. See
[`GraphicsAuditModelCaptureHistory`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/dev/GraphicsAuditModelCaptureHistory.java).

Real-world Iris+DH captures automatically retain the main and DH opaque depth
snapshots from the acknowledged selected-source submission. Their scope is
`source-dh-depth-coverage`: the pack writes shared color targets, so ordinary DH
private color attachments do not describe that route. The visible-extension
check compares pixels where DH opaque depth is below clear 1 and main opaque
depth is exactly clear 1. It verifies float readback hashes, frame/submission
identity, extent and row origin against the screenshot acknowledgement.
An 8-bit depth preview cannot distinguish distant geometry from clear depth;
missing, stale or resized attachments must fail proof rather than be guessed.
Ordinary DH full captures use their private alpha and float main depth.
Run the evidence regression checks with:

```sh
python3 -m unittest discover -s DevUtils/tests/rendering -p 'test_dh_depth_coverage.py'
```

The two source depth buffers exist only for a claimed diagnostic capture and
are released after completion or rejection before submission. Raw files use
fixed names and are overwritten on retries to bound disk use; the manifest
hashes reject files belonging to a different retry. This proves an opaque DH
extension at that pose, not translucent/water parity or temporal stability.

For particle cutouts, run `--cutout-terrain-particle hidden` first, then
`--cutout-terrain-particle visible --cutout-terrain-reference <hidden-artifact>`
with the same pack and camera. The existing harness checks source equality,
the full particle region including transparent holes, and the visible effect
against the hidden control. An opaque `particle-atlas-static-a` comparison
cannot establish transparency: all its source texels have alpha 255.

## 3. Real-config session

Set diagnostic environment options before launching. Production renderer
options are snapshotted on first access through `core/environment.rs`; changing
the environment later does not reconfigure that process. Unit fixtures can
use thread-local scoped overrides to exercise different diagnostic modes.

Run the game with your own settings (`python3 DevUtils/RunDev.py` uses the
release native profile) with shaders on and off, join a world, and watch the
log for `panicked`, `Game crashed` or `VUID`. The shader route should report
`shader route active` on the first renderable shader-enabled world frame.
Check private preparation and actual presentation correlation when testing entry.
Capture both stdout and stderr: native
shader diagnostics are not necessarily copied into `run/logs/latest.log`.
An `active` message immediately followed by `vanilla fallback` is a failed
shader frame, not successful shader rendering. Verify that Shader Packs appears
in Video Settings and that toggling the configured pack changes the rendered
world; unit tests alone do not exercise the complete startup and live pass graph.

For comparisons across vanilla, shaders and DH, set `MATTMC_CAPTURE_RUN_SOURCE`
to the same source run directory (containing `saves/<world>` and `options.txt`)
for every route. The harness copies that source for both clients, including when
DH is disabled. It fails if the requested world is missing. Without the override,
it uses Current's `run/`. Use fresh artifact roots and check `source_run` and
`source_save_hash` in each canonical `fixture_manifest.json` before comparing
results across routes. World-source regressions run with:

```sh
python3 -m unittest discover -s DevUtils/tests/rendering -p test_canonical_world_source.py
```

For temporal observations on Linux/X11, launch the client through the existing
harness and pass its live Java PID to the bounded window capture driver:

```sh
python3 DevUtils/tests/rendering/CaptureWindowTimeline.py \
  --pid 12345 --output artifacts/graphics-captures/terrain-timeline \
  --frames 120 --interval 0.05 --region 0,420,480,200
```

Use a new output directory each time and choose a region inside the client
window. The driver verifies the window belongs to that PID, captures at most
240 samples, and writes timestamps, adjacent-frame pixel deltas and a temporal
range image. Capture the equivalent scene in Frozen OpenGL. Animation, camera
motion and sampling aliasing can change pixels; this report alone does not
prove flicker or its absence. A stopped client, failed capture or resize fails
the observation rather than silently switching windows.

For shorter frame changes, use the lossless FFV1 video observer (requires
`ffmpeg` and `ffprobe`) on the same live PID and region:

```sh
python3 DevUtils/tests/rendering/CaptureWindowVideo.py \
  --pid 12345 --output artifacts/graphics-captures/terrain-video \
  --fps 60 --seconds 8 --region 0,420,480,200
```

It records up to ten seconds at 30, 60 or 120 samples per second, keeps actual
video timestamps without duplicating frames, and streams decoded pixel deltas
with bounded memory. Inspect the timestamps for gaps: sampling still cannot
prove that every presented frame was observed. The video's overhead makes it
unsuitable for performance acceptance.

For movement across chunk boundaries, use [Terrain movement checks](TERRAIN-MOVEMENT-CHECKS.md).
The benchmark yaw path stays at a fixed position and does not test travel.

For the vanilla lower sky disc, run `cargo test ... sky_dark` and the Java
`SkyProceduralAdmissionTest` after rebuilding native release. The native tests
read actual pixels for local fog distances, camera translation, fog alpha and
disabled fog; the Java check verifies Frozen's bottom fan and copied draw facts.
Repeat the ordinary `--pose buried` comparison against Frozen OpenGL to inspect
uncovered sky between cave surfaces. These checks do not establish shader-pack
sky parity or solve the general terrain-flickering defect.

`ObserveGameplayVideo.py` can trigger that observer alongside an existing
`Gameplay.py` job. Use a fresh artifact root and `--artifact-preserve-current-run`
on the gameplay command so retention cannot remove a video still being written.
The observer waits for a settled camera path, checks the exact live client working
directory and retains the benchmark context before and after recording:

```sh
python3 DevUtils/tests/rendering/ObserveGameplayVideo.py \
  --artifact-root artifacts/graphics-captures/moving-terrain \
  --mode current-rust-vulkan-shaders-off --mode frozen-opengl-shaders-off \
  --minimum-path-frames 1200 --fps 120 --seconds 10
python3 DevUtils/tests/rendering/AnalyzeTerrainVideo.py \
  artifacts/graphics-captures/moving-terrain/current-rust-vulkan-shaders-off-video
```

Keep both clients alive long enough for observation; a frame-count benchmark can
finish quickly on Frozen even when `Options` records a low FPS limit. Check actual
samples and timestamps. The analyzer streams a bounded window, finds pixels
that change then return, and retains three candidate strips. If lossless recording
misses its sample-count requirement, reject it and retry both clients with the
same smaller `--region`; keep the sampling checks intact. Inspect the strips for
motion, water animation and texture aliasing; its thresholds are triage settings,
not parity tolerances. Run its checks with `python3 -m unittest discover -s
DevUtils/tests/rendering -p test_terrain_video_analysis.py`.

Repeat analysis with `--sample-lag 2` and `--sample-lag 4` on both videos:
one rendered frame may appear in several recorder samples, which adjacent-only
triads miss. These runs keep at most nine samples and write separate
`transient-analysis-lag-2/4` directories. Wider intervals also increase motion
false positives; inspect the candidate strips and retain the same thresholds.
Check the tile counts across all rows too. `--rank-by tiles` saves a separate
candidate set so a small coherent patch is not hidden by texture-edge pixels.

For a moving path, queue-drain readiness loss restarts timing and resets the
benchmark camera path. Reject such video as an uninterrupted motion observation.
A deliberately labeled streaming diagnostic can pass
`--jvm-arg=-Dmattmc.dev.graphicsFrameBenchmark.requireTerrainQueueDrain=false`;
retain asynchronous build/pop-in evidence and do not use it for steady-state
performance or settled parity acceptance. Keep the normal readiness gate for
those checks. Compare the actual camera path and restart count on both clients.

Producer readiness timeouts cover one continuous blocked interval. A successful
gate or measurement restart clears the previous deadline; cumulative wait and
restart counters remain available. A short late queue change must not inherit
an expired startup deadline. The harness's overall run limit still applies.
Run `GraphicsFrameBenchmarkReadinessTest` after changes to this lifecycle.

Deterministic correctness captures force GPU retirement after presentation in
`RustGalFrameCoordinator`. They can hide defects caused by overlapping frames.
Also observe normal RunDev sessions or the gameplay frame benchmark, which
polls completion without forcing retirement. Run synchronization validation on
that workload; a clean static capture alone does not establish temporal stability.
Set `MATTMC_TRACE_GPU_OVERLAP=1` before a bounded gameplay launch to retain
`vulkan.submission.ownership` samples from a non-blocking native timeline query.
`gpu_incomplete_images >= 2` establishes unfinished work for distinct present
images at that instant. Offscreen/update submissions are counted separately;
failed samples report `unknown`. `gpu_sample_wall_start_ns` and
`gpu_sample_wall_end_ns` bracket the query in Unix nanoseconds for correlation
with the video observer's wall-clock interval; clock failures report `unknown`.
These intervals do not identify an exact displayed video frame.
This sampling adds diagnostic overhead and
does not establish flicker absence. The profile's `last_images_in_flight` counts
acquired images only; old observations of one acquired image cannot establish
whether GPU submissions overlapped.
Two unfinished present images are sufficient evidence, but a single unfinished
image does not exclude new offscreen work queued behind it. After the run,
`AnalyzeGpuOverlap.py LOG --video VIDEO.json --output RESULT.json` reconstructs
which submissions present from consecutive IDs, sampled completion and the
incomplete-present count. Its `offscreen_queued_while_prior_present_incomplete`
counter identifies that narrower overlap condition; timestamped witnesses are
bounded. Missing/failed samples remain unknown until completion catches up,
and inconsistent counters reject analysis. This does not prove that a shared
resource was reused, that commands execute concurrently on one queue, or that
overlap caused flicker. Run `test_gpu_overlap_analysis.py` and inspect the native
trace and video before interpreting candidates.

For a GPU-heavy real-world diagnostic, set `MATTMC_CAPTURE_WORLD_WIDTH=3840`
and `MATTMC_CAPTURE_WORLD_HEIGHT=2160` before a paired `Gameplay.py` launch.
Both launchers consume these bounded dimensions (320×240 through 3840×2160);
defaults remain 1280×720. Check each benchmark's actual window dimensions and
the fingerprint's resolution before comparing results. Menu fixtures retain
their separate `MATTMC_CAPTURE_MENU_WIDTH/HEIGHT` settings. Higher resolution
alone does not establish GPU overlap: retain the native timeline samples.
The window video observer requests only its crop from X11 using
[FFmpeg's X11 crop options](https://github.com/FFmpeg/FFmpeg/blob/n6.1/libavdevice/xcbgrab.c),
so a larger client does not require copying the entire window for every sample.
Verify the crop fits the actual client and inspect its contents and provenance;
sampling failures and invisible/offscreen regions remain failed observations.
If the window manager clamps a requested extent, retain the failed attempt.
For a separate X11 overlap diagnostic, `ObserveGameplayVideo.py
--unmanaged-extent 3840,2160` temporarily reparents only the verified isolated
game window to the desktop root and bypasses the manager's size clamp. It
requires benchmark acknowledgment of the size before recording, retains
`window-transition.json`, and requests restoration after observation. No display
mode or game context is replaced. Verify the captured crop is visible, both
receipts acknowledge the same extent, restoration succeeds and gameplay
continues. This controlled resize is not steady-state performance, exact-frame
resize correctness or fullscreen acceptance.
The observer reads `client_extent` from X11 geometry and separately records
`screenshot_extent`: a desktop-clipped screenshot cannot establish the full
framebuffer dimensions. The crop must fit both. A valid partial-window crop
does not prove correctness of the unobserved part of a larger framebuffer.

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

Run `cargo test ... whole_frame_resource_profile` after changing whole-frame
resource accounting. The world/GUI counters include frontend preparation and
recording retirement; standalone GAL submit counters have a narrower scope.
Compare creation/destruction alongside deferred destroys when diagnosing churn.
Older submit-only artifacts may report zero despite per-frame preparation.

Check the recorded runtime FPS limit before comparing throughput. For the
capture runner, `MATTMC_CAPTURE_MAX_FPS=260` selects the unlimited slider value;
out-of-range values such as 1000 decode to the default 120. Validation and
graphics diagnostic runs are overhead probes, not performance acceptance.

Profile ordinary gameplay separately when investigating work the benchmark
suppresses. Attach JDK 25's `jcmd CLIENT_PID JFR.start name=render settings=profile
duration=60s filename=/absolute/path/render.jfr` to the verified game process.
Use `jfr view hot-methods`, `allocation-by-site` and `native-methods` on that
recording; separate render-thread stacks from chunk-worker costs. Native samples
include both execution and waiting. Keep the recording and compact aggregates;
expanded JSON repeats class-loader metadata and can be much larger.

The frame coordinator formats its automatic audit line only when
`mattmc.dev.graphicsAuditSliceMetrics=true`, outside measured benchmark frames.
Explicit `currentAuditMetricsLine()` requests still work. Check the logging flag
before building diagnostic strings; testing it inside the sink still allocates
the message during ordinary gameplay.

- Run 5 times per side, with and without Distant Horizons, in the same session.
  Build the baseline by stashing your change, run it, then restore and verify
  the tree.
- Entity and terrain workloads differ between runs and affect FPS.
  `submittedWorkCounts` accumulates during settling, warmup and discarded
  measurement attempts too; it is not a count for the final timing window.
  Compare readiness restarts and frame-correlated workload evidence before
  interpreting a small FPS change. Do not divide that cumulative map by the
  final measured frame count.
- Treat differences within about one standard error as noise. Large
  improvements you didn't design (code-layout effects) aren't wins to claim.

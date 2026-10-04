# Terrain movement checks

Use ordinary player movement when investigating terrain disappearing during
chunk streaming. The frame benchmark's `moving-camera` path rotates yaw at a
fixed position; Frozen also resets position and velocity every frame. That path
does not exercise travel across chunk boundaries.

Abrupt camera turns are a separate case. Use `--pose forest --motion turn` to
make two stationary 180-degree turns (yaw 285→105→285, pitch 25), with the same
shader and DH settings on both clients. The driver types an allowlisted normal
teleport command before starting each full-window 30-fps video, then submits it;
the position stays at 150.5/95/530.5. It records the submission wall time,
server acknowledgment and actual F3 screenshots. `forward` and `back` name the
two turn observations in this mode. Review terrain from the first changed view
through its settled state; an adjacent-frame flicker score alone cannot measure
multi-frame missing chunks. Chat and command processing remain part of this
ordinary-input observation, so it is not an exact frame-correlated latency test.

For a **first-visit** delay, the stationary command fixture can warm unseen
chunks before recording. Copy the source run into a new ignored fixture, add
`.terrain-turn-fixture-owned`, and run `PrepareTerrainTurnFixture.java` with
Current's main runtime classpath and `-Dmattmc.rust.natives.dir=build/rust/native`.
It changes only the copied `Origin/level.dat`, retains its original bytes, and
saves the same forest spectator pose for both clients by default. An optional
`--pose X,Y,Z,YAW,PITCH` saves another starting camera; values must be finite,
inside the fixture's world bounds, with pitch in [-90, 90]. A source already
prepared by this tool rejects another write. Retain it and prepare a new copy.
Starting at the observation pose matters for shader packs whose biome and
eye-brightness smoothing otherwise carries a setup teleport's history.
Then use the default forest copy with
`--pose forest --motion first-turn --seconds 8`. This mode skips setup commands
and settling waits. It verifies the actual F3 world view with `tesseract` before
input; a joined server can still have the loading screen open. It also verifies
F3 has hidden before recording. Three full-window 30-fps videos start before
ordinary mouse input: unseen view, return, and revisit. Input checks the isolated PID and focus
before and after each bounded motion. Review F3 to verify the actual yaw and
unchanged position; raw mouse delivery and sensitivity can affect the result.
Inspect the first changed-view samples in the lossless video, not just its final
image. `--motion travel-turn` also records mouse turns during ordinary travel,
for a separate streaming case.

For a player who stands still before turning, add `--first-turn-idle-seconds 10`
to both runs. The optional wait is bounded to 20 seconds, records its wall-time
interval and checks the isolated process and focus throughout. Its default is
zero to retain the cold-entry case. Review the starting F3 view in either case:
a fixed idle interval does not prove that terrain or shader initialization has
finished. Keep cold-entry and idle-before-turn results distinct.

To distinguish missing geometry from shader inputs, add `--diagnostic-attachments`
to a Current shader-enabled first-turn run. It enables bounded request-driven
readback and audit output under that flight's directory; the driver reserves an
additional GiB of storage. Once the driver has created its output, run in another
terminal:

```sh
python3 DevUtils/tests/rendering/ObserveFlightAttachments.py \
  --flight-output artifacts/graphics-captures/your-current-first-turn
```

The observer verifies the isolated Java client, then arms two native requests:
after the first mouse input and six seconds later. Rust promotes each request
within its existing eight-frame bound. Completed attachment manifests, shader
stage outputs and immutable uniforms identify the actual source frame; the
after-present sidecar must match the same frame, correlation, submission and
presentation image. Inspect the captured final image to confirm its camera and
the symptom. The later sample is not assumed settled. Readback and audit work
change timing, so keep these diagnostics separate from ordinary visual repeats
and performance measurements. A failed observer is not attachment evidence.
Stage outputs, execution and uniform receipts also reject a different submission.
Check terminal runtime results independently: completed readbacks do not make a
subsequently crashed session a clean stability result.

## Run an isolated observation

[`RunTerrainFlight.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/rendering/RunTerrainFlight.py)
launches the repository's existing capture engine with the frame benchmark and
deterministic camera disabled. The engine owns world isolation, bounded runtime,
logs and client termination. Run clients sequentially, from Current:

```sh
python3 DevUtils/tests/rendering/RunTerrainFlight.py \
  --repo "$PWD" --backend rust-vulkan --shaders off \
  --run-source /absolute/path/to/copied/run \
  --output artifacts/graphics-captures/terrain-flight-current
python3 DevUtils/tests/rendering/RunTerrainFlight.py \
  --repo /absolute/path/to/Frozen --backend opengl --shaders off \
  --run-source /absolute/path/to/copied/run \
  --output artifacts/graphics-captures/terrain-flight-frozen
```

The driver checks the shared eight-GiB disk reserve plus a one-GiB run estimate
before launch and records the preflight in `flight.json`. Preserving observations
does not waive that estimate or permit lowering the reserve.

The source must contain an `Origin` singleplayer save with commands enabled;
`--world` can select another source save. Use the same source for both clients.
The source and Frozen renderer are not edited. Output must be a new directory in
Current's ignored graphics capture tree. Both captures use render/simulation
distance 10, `maxFps:260` (the game's unlimited setting) and DH disabled by
default. `--shaders on` requires the configured archive in the copied source.
The driver hashes it before launch and verifies the selected runtime archive
before sending input; matching filenames and enabled flags alone are insufficient.
Retain `shader_pack_sha256_requested` and the effective configuration's hash.
Current's `runClient` copies built shader archives into the isolated shaderpacks
directory and can overwrite a source archive with the same bundled filename.
For an original-pack comparison, give the archive a unique filename in the
owned source copy and select that filename in its `config/iris.properties`
before launching either client. The direct capture-engine route used here does
not stage `MATTMC_CAPTURE_SHADER_PACK_SOURCE`; setting it cannot pin this archive.
Both launcher paths receive explicit
quick-play, window-size and `enableShaders` arguments. The driver verifies the
runtime Iris state and DH renderer mode after the player joins; the legacy
Frozen shell's `--shaders off` alone does not override an enabled source pack.

For DH movement, pass `--dh on` to both commands. Prepare a separate source copy
with DH `rendererMode="DEFAULT"`, a radius of 1–64 chunks and
`enableDistantGeneration=false`. Keep the saved world's nonempty DH database in
Frozen-compatible data format 1 or 2. The driver validates the database read-only
and records its hash before launch; both engines copy it into their isolated
runs. Retain equivalent fog, transparency, water and fade settings. This tests
travel, near-chunk work and existing LOD data; distant generation needs separate
coverage.

Current DH runs enable `mattmc.dev.rustGalDistantHorizons.traceExecution`: at most
240 submission receipts and 240 rejection messages, each limited to one per
second, identify successful native
frame submissions carrying opaque, transparent and water segments. They retain
no geometry or GPU state and do not enable general audit logging. After setup
positions the camera, the driver waits for a DH submission at that pose; the
saved starting camera may show only near terrain. After cleanup, it requires receipts
inside each video's wall-clock interval. These counts do not prove visible pixel
coverage; inspect the DH scene and compare it with Frozen. Frozen uses its
unchanged OpenGL renderer and requires visual review of LOD readiness.

Ordinary singleplayer commands select spectator mode, place the player at
`150.5,125,530.5` with yaw/pitch `105,15` for the default `--pose coast`, disable
daylight/weather progression, clear weather and set time to 6000. The driver waits
for the connected singleplayer window title and, for native shaders, an active
route before sending input. Server join alone can precede the
loading screen closing. Chat opens in a game tick; the input helper pauses two
seconds before typing to allow cold renderer work to finish. Each command requires a new acknowledgment in the
isolated game's log. Input goes only to the verified game window; focus loss or
PID replacement rejects new input. Held keys are released during cleanup and
previous focus is restored only while the game still owns focus.

Use `--pose forest` on both clients for inland travel at `150.5,95,530.5`,
yaw/pitch `285,25`. This reverses the coast route's travel direction and lowers
the camera to exercise foliage, land sections and near-terrain rebuilds. As with
the coast pose, check the actual terrain in your source save and review F3 travel.

Use `--pose buried` on both clients to test a spectator inside terrain: the start
is `167.5,38,553.5`, yaw/pitch `0,0`. Verify the blocks at that position in your
source save; the actor fixture used during investigation contains stone there.
This exercises Frozen's camera-in-solid-block occlusion policy and changes no
blocks. A different save may put the same coordinate in air.

The independent CPU terrain producer must match Frozen's occlusion policy:
disable portal and angle masks when smart culling is disabled, or when the player
is a spectator and the camera's block is solid-rendering. Keep frustum, distance,
outward traversal and build-height/window checks. Include the policy in the
visibility identity so a stationary change rebuilds the frontier and settled
selection. Use the camera's block position, including eye height; player feet
can occupy a different block. See
[`RustGalWholeFrameTerrainSource`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/RustGalWholeFrameTerrainSource.java).

The driver captures F3 screenshots before movement, after holding W, and after
holding S. Each direction holds its key for nine seconds by default and records
eight seconds at 60 samples/second. `--seconds` accepts 1–10 seconds. F3 is hidden
during video. Current enables routine Vulkan validation and nonblocking GPU
timeline diagnostics. Observation overhead prevents throughput comparisons.

To observe a resource reload, start the supplemental observer in another
terminal while the flight runs, using the same output directory:

```sh
python3 DevUtils/tests/rendering/ObserveFlightResourceReload.py \
  --flight-output artifacts/graphics-captures/terrain-flight-current
```

It waits until all movement recordings finish and input is released, verifies
the same client/window, then sends the ordinary F3+T hotkey. The `resource-reload/`
directory contains a reload acknowledgment, eight-second full-window video at
30 samples/second and
screenshots through fifteen seconds. The original engine still owns client
termination. Run the same observation on Frozen, inspect actual completion and
shader/DH state, and check runtime validation after cleanup. The recordings do
not identify the exact first frame after reload or establish broad parity.

The Oct 4 Native44 original-pack shader+DH pair in
`goal5/terrain-untinted-lattice/ordinary-current/` and `ordinary-frozen/`
retains three complete 240-sample turn movies per client. The reviewed first
turn still briefly exposes a broad seabed strip in Current while Frozen shows
water over that area; Current's reviewed revisit is clearer. Distant haze is
present in both settled views. Earlier correlated depth evidence establishes
missing geometry in one diagnostic frame; do not attribute every water-strip
observation to fog or assume the diagnostic measures ordinary latency.

Both clients acknowledge F3+T resource reloads. Their before/after-fifteen-second
screenshots show the shader scene restored without a loading overlay; Current
also shows the completion message. Current's reload video completes with 240
samples. Frozen's recorder rejects repeated timestamps during reload, so its
retained video is excluded from timing/temporal comparison; screenshots and the
acknowledgment remain usable. Both clients are reaped and delayed owned-kernel
audits observe no crash. Current peaks at about 7.46 GiB RSS in this short run;
this does not establish long-run bounds, exact first-reload-frame correctness,
registered parity or a shader-program toggle/reload result.

## Review before drawing conclusions

- Wait for the owning driver to exit and reap it. An observation finishing does
  not mean its game has stopped: the capture engine normally runs to its bounded
  180-second timeout plus diagnostic/cleanup overhead. Do not start another
  client, GPU test or native build while it is still live.
- Inspect `flight.json`, the engine metadata and every recorded F3 screenshot. For
  DH, review `dh_source`, effective settings and `dh_submission_samples_per_video`.
  Check
  actual positions/orientation and chunk coordinates; a rejected command or
  missing movement invalidates the controlled comparison.
- Inspect each `video.json` file for completed sampling, ownership and gaps.
  The driver's `observation_complete_requires_position_review` is a receipt,
  not an acceptance result. Runtime cleanup, crash lists and concrete validation
  findings are checked separately.
- Analyze both videos at lags 1, 2 and 4 using the commands in
  [Render Verification](RENDER-VERIFICATION.md). Inspect candidate strips and
  correlate them with movement, chunk rebuilds and GPU timeline records. Motion,
  foliage aliasing and animation can produce returning pixels without a renderer
  defect. A clean video does not prove flicker absence.

The Oct 4 original-pack first-turn pair retained in
`goal5/terrain-first-turn-post-haze/pinned-original/` has three complete
240-sample videos per client and visually verified stationary poses. Its original
log-only terminal checks passed; later system-core review rejects Current's
termination. Nearby hills and trees are visible during the turn;
a bright exposed-seabed band settles later in Current and also occurs on revisit.
This is an unresolved visual observation, not proof of missing water geometry,
fog or temporal-history failure. Compare early water and land separately and
use geometry/depth or controlled shader-stage evidence to distinguish them.
The preceding pair is excluded from original-pack comparison because Current
overwrote the bundled archive; the new pre-input hash guard rejects that mismatch.

To isolate final TAA blending, use an explicitly diagnostic copy on both clients:
return from `DoTAA` after setting `temp = color`, preserving the pack's jitter,
fog, light shafts, water and other temporal passes. Hash the archive and verify
that only `shaders/lib/antialiasing/taa.glsl` changed. This is not a production fix.
The paired observation in `goal5/terrain-water-temporal-isolation/` still shows
Current's cold first-turn seabed band, although its revisit is clearer. Current's
cold starting view also has terrain holes. Startup readiness differs between
runs, so this rules out the final TAA blend as the sole explanation, not all
temporal effects or a particular geometry cause. All six videos finish with
232–240 samples; missing samples leave gaps up to 67 ms. Use actual video PTS,
and retain those gaps when assessing the earliest view.

The bounded CPU trace in `goal5/terrain-water-publication-trace/` shows regular
translucent meshes arriving long after DH water submissions stabilize. At the
first turn, Current selects 36 translucent meshes; the settled set has 115.
The scheduler now reserves one ordinary choice in four for nearest background
prefetch and prefers the current portal frontier for the other three, preserving
urgent edits and existing worker/publication limits. Two regressions fail before
and 24 related checks pass after. With equivalent tracing, the fully uploaded
settled translucent identity first appears about 21.5 s after input in the
control and 2.3 s in the candidate. This measures CPU selection, not exact GPU
presentation latency or overall FPS. Both original-pack recordings retain three
240-sample videos, but later system-core review rejects both terminations.
Cold startup readiness differs, and the candidate's
early water band remains; untraced repeats must check immediate nearby terrain
as well as eventual completeness. Temporary traces are removed.

The untraced original-pack observations in
`goal5/terrain-water-fair-priority-untraced/` retain a cold Current run and a
Current/Frozen pair with the ten-second idle interval. Each has three complete
240-sample videos and verified stationary poses; Current exercises standard
Vulkan validation and has DH receipts during every video. The original log-only
terminal checks passed, but later system-core review rejects both Current
termination results. See `goal5/terrain-first-turn-startup-profile/retrospective-termination-audit.json`.
The cold Current view still exposes a broad angular seabed strip immediately
after turning, largely settled by video PTS 2 s. With the idle interval, both
clients have nearby terrain and water in the earliest completed-view samples;
Frozen also shows a brief exposed-seabed transition during the turn. Fog and
light shafts remain enabled. This supports a startup/readiness contribution
but does not isolate the exact water pixel cause or prove a visual gain over
the nearest-only scheduler: there is no matched idle control for that policy.
The broad flicker investigation and performance gap remain open. All clients
are gone, the shared fixture and full Frozen state are unchanged, and 71
rendering-tool tests pass, with invalid idle modes/durations rejected before launch.

The diagnostic original-pack run in
`goal5/terrain-water-first-view-attachments/correlated/` retains presented frames
123/submission 165 and 166/submission 251. Both have 14 matching stage readbacks
and eight immutable uniform receipts. Camera, projection, fog color and lighting
inputs match; clocks advance. Early opaque/cutout/translucent execution is
21/16/2 instances, versus 244/226/114 later. In the retained 250×40 near-water
rectangle, regular main and opaque depth are entirely clear early; DH opaque
depth covers it. Later regular water writes nearer depth across the rectangle.
The final images show missing nearby hillsides and water early. This establishes
missing normal geometry in this cold diagnostic frame, beyond a fog-color
difference. It does not establish ordinary-gameplay latency or full settling.
Use the named shader stages and main/before-transparency/DH depths: generic
compatibility attachments can retain an earlier bootstrap producer and must not
be interpreted as the selected pack's current G-buffer.

That session subsequently crashes on the render thread during disconnect,
inside native validation while submitting frame 2164. The driver correctly
rejects it despite completed videos/readbacks and timeout metadata. Retain
`hs_err` and the terminal failure; the stored core is truncated before the
render-thread stack. The cause remains unresolved. Attachment ownership and
storage regression checks pass (75 tool tests); these do not establish runtime
stability or fix the remaining readiness delay.
The subsequent profiling runs also abort during termination without JVM crash
files. The harness now samples the same Linux process's `CoreDumping` flag during
its existing cleanup grace period and treats it as a crash in both flight and
structured artifacts. Review system core records when auditing older sessions;
wrapper exit 143 alone does not establish clean termination.

Private Vulkan backend diagnostics now tolerate failed console writes; see
[VulkanicGAL](VULKANIC-GAL.md). The release C-ABI probe in
`goal5/native-trace-closed-pipe/` aborts on a closed stdout with the old printer
and exits normally with the production helper. Its open/closed stdout/stderr
regression and full release suite pass (2,158 tests, three ignored). The ordinary
original-pack shader+DH repeat retains three 240-sample turn videos, reaps the
client and has neither a cleanup core flag nor a matching kernel core record
when audited 69 seconds after termination. The cold water band remains in early
samples and settles in the reviewed samples by PTS 3 s. This is one scoped
termination result, not a resolution of the separate attachment-mode SIGSEGV,
an FPS gain or broad visual/stability acceptance.
The subsequent attachment-mode repeat retains matching presented frames
120/submission 159 and 167/submission 253, then aborts during termination with
SIGABRT and no JVM crash report. The kernel core flag now makes the driver
reject it. That run still had panicking frontend/GAL console writes; the
best-effort helper now covers production renderer diagnostics across layers.
The earlier independent SIGSEGV remains unresolved; do not merge the verdicts.
The extended-fix repeat in `goal5/native-trace-closed-pipe/frontend-extension/`
passes the full release suite and the diagnostic session: three 240-sample
movies, matching frames 103/submission 145 and 146/submission 231, and no native
crash observed when the kernel is audited 134 seconds after termination.
The client is gone and the fixture/Frozen state are unchanged. This verifies
the console fix in this bounded run; terrain readiness, the independent SIGSEGV
and broader stability still need work.

The CPU source regressions check dispatch priority, immediate loaded-air portal
connectivity, queued cancellation and running-build ownership. After preparing
the release native library, run the focused Java checks without the native GPU
suite:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests net.vulkanic.world.TerrainBuildPriorityTest \
  --tests net.vulkanic.world.TerrainAirFrontierTest
```

Run driver/ownership regression checks without a GPU client:

```sh
python3 -m unittest discover -s DevUtils/tests/rendering -p 'test_*.py'
```

Retain logs, worlds, settings, receipts, screenshots and videos. Remove copied
asset caches only after the engine is terminal and its verified client is gone.
Do not lower the existing capture storage reserve to fit another run.

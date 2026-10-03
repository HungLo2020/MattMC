# Terrain movement checks

Use ordinary player movement when investigating terrain disappearing during
chunk streaming. The frame benchmark's `moving-camera` path rotates yaw at a
fixed position; Frozen also resets position and velocity every frame. That path
does not exercise travel across chunk boundaries.

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
default. `--shaders on` uses the source's configured pack; retain and compare
the effective configuration. Both launcher paths receive explicit
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

## Review before drawing conclusions

- Wait for the owning driver to exit and reap it. An observation finishing does
  not mean its game has stopped: the capture engine normally runs to its bounded
  180-second timeout plus diagnostic/cleanup overhead. Do not start another
  client, GPU test or native build while it is still live.
- Inspect `flight.json`, the engine metadata and all three F3 screenshots. For
  DH, review `dh_source`, effective settings and `dh_submission_samples_per_video`.
  Check
  actual positions/orientation and chunk coordinates; a rejected command or
  missing movement invalidates the controlled comparison.
- Inspect both `video.json` files for completed sampling, ownership and gaps.
  The driver's `observation_complete_requires_position_review` is a receipt,
  not an acceptance result. Runtime cleanup, crash lists and concrete validation
  findings are checked separately.
- Analyze both videos at lags 1, 2 and 4 using the commands in
  [Render Verification](RENDER-VERIFICATION.md). Inspect candidate strips and
  correlate them with movement, chunk rebuilds and GPU timeline records. Motion,
  foliage aliasing and animation can produce returning pixels without a renderer
  defect. A clean video does not prove flicker absence.

Run driver/ownership regression checks without a GPU client:

```sh
python3 -m unittest discover -s DevUtils/tests/rendering -p 'test_*.py'
```

Retain logs, worlds, settings, receipts, screenshots and videos. Remove copied
asset caches only after the engine is terminal and its verified client is gone.
Do not lower the existing capture storage reserve to fit another run.

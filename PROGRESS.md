# Rendering parity, stability, and performance
## Active objective — Goal 5

Complete realistic end-to-end parity for vanilla, DH, Iris, and Iris+DH against
Frozen Java OpenGL. Root-cause terrain flicker across all routes; ensure the
first presented shader-enabled world frame already uses the selected pack.
Profile before optimizing; demonstrate real-world gains and bounded resources.
This checkpoint records work committed in `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on 2026-10-03.
Goal 5 remains **incomplete**; the scoped results below are author-recorded evidence, not broad acceptance.
## Current work (2026-10-03)

- Base `c87803e75`; static RGB MAE 0.236/0.430/0.466 (`goal5/baseline-vanilla-warm`); narrow.
- Sky lightmap aliases fixed; before failed/full 2,073 pass/3 ignored (`goal5/sky-lightmap-contract-fix`).
- Fresh pairs: Complementary RGB 5.051/5.029/5.244 passes; MakeUp 6.860/6.127/3.555 fails6.
- First shader frame: Complementary97/source97, MakeUp87/source87 clean; other packs/transitions pending.
- Shadow-only orb crash: mesh-boundary compaction fixed; before reproduced,
  13 Java tests pass; live pair clean/RGB 5.022/5.043/5.247. Temporary trace removed.
- Shader/entity moving pair: 1,800 frames each clean; Current/Frozen FPS 38.3/285.2,
  p95 30.46/5.58 ms; CPU frontend 13.64 ms (prepare 5.99), GPU 7.24; needs optimization.
- Terrain uniforms share immutable CPU blocks; full 2,073/3 ignored, ABI 68;
  RGB 4.956/4.982/5.208 passes. Moving FPS 39.1/306.6; prepare 6.07 ms; speedup unproven.
- Early shadow frustum selection preserves indices/order/validation; six regressions,
  full Rust 2,079/3 ignored. Real batching 2.489→2.020/2.030 ms (~19%); FPS win unproven:
  Current control/candidates 38.8/39.6/38.1; Frozen 301.7/303.9/307.4, 1,800 frames each.
  Fresh RGB 5.011/5.038/5.235 passes, validation clean; first world184/source184.
  Scoped evidence: `goal5/early-shadow-selection`; flicker and broad parity remain open.
- Normal translation driver: verified travel/setup/route; forest pose/disk preflight, 60 tool tests.
  Vanilla Current v4/Frozen v6: X9→3→9, 480 samples/direction, lag1/2/4 zero tiles.
  Inland vanilla X9→15→9: lag1/2 max0; lag4 2/1 both, motion edges; no parity/flicker acceptance.
  Inland Complementary v1 excluded: user reports manual input ~10:17; clean repeat pending.
- Normal Complementary v2/Frozen v1: 480/direction, lag1/2/4 zero tiles, clean/peak5.53GiB.
- DH ordinary movement: shared v2 DB/radius32/DOUBLE_PASS, 480 samples/direction;
  actual X9→3→9 verified both. Current return has transient rectangular terrain loss:
  lag1/2 max133 tiles, lag4 115; Frozen all six analyses max0. Current repeat reproduces
  max32 tiles; bounded/reaped/validation clean, peak3.95/4.22 GiB. Cause initially open; repair follows.
  `goal5/terrain-translation-dh-source/ordinary-pair-verification.json`; ROI900×350.
  Fade NONE also reproduces (max53 tiles); buffered trace reproduces (max64), 59 Java tests pass.
  Explicit trace links one-frame missing column to asset-ack pruning (365→362→365).
  Fix defers selected-column replacements until presentation; two before-failing regressions,
  61 Java tests pass; traced fixed all six lag analyses max0, 30k records/no prunes.
  Untraced repeat all six max0; clean/reaped, peak5.01GiB. Temporary traces removed.
  Iris+DH Current/Frozen all six max0, clean/reaped; Current peak6.71GiB. General flicker/bounds open.
- Spectator-in-solid terrain omission: missing Frozen portal bypass root-caused.
  CPU policy/cache identity fixed; nine Java/native tests pass; live before/Frozen/after
  confirms restored cave terrain. Clean/reaped, after peak 3.69 GiB; pixel/perf gaps remain.
  `goal5/terrain-occlusion-policy-fix`; general terrain/DH flicker remains unresolved.
- Black cave wedges: generic lower sky disc omitted Frozen sky fog. Typed local fan,
  Rust camera-relative transform/fog/nonwriting depth/back cull fix; ABI 68 unchanged.
  Full Rust 2,083 pass/3 ignored; 11 Java/native and wiki pass. Real vanilla before/
  Frozen/after confirms fogged gaps; peak 3.84 GiB (`goal5/dark-sky-disc-fog`).
  Shader buried pair clean/reaped/active; stronger shafts and severe slowdown remain.
  Lag4 tiles Current 45/21 vs Frozen 11/9 follow cave edges; flicker unproven.
- Disabled audit formatting: normal 60s JFR confirmed discarded strings; gate fixed.
  Nine GUI tests pass; repeat audit allocation samples 5→0; no FPS win claimed.
  `goal5/audit-formatting-guard`; mesh publication/foil reads need further profiling.
- Flicker unresolved: native 269/2 ignored; timestamped timeline diagnostics.
  Controlled 4K vanilla 12k / shader 2.6k pairs clean; videos 480 each/8 s. During
  video, 122/151 offscreen submissions queued behind unfinished prior presents;
  shader candidates sparse foliage. 38 tool tests pass (`goal5/gpu-overlap-unmanaged-4k-*`).
- Fresh uncapped RD10 moving-camera release pair, 1,800 measured frames:
  Current/Frozen average FPS 164.7/975.3; p95 7.89/1.67 ms; p99 10.34/4.25 ms;
  peak RSS 3.20/3.77 GiB; entity counts 181/180. This is one workload, not broad
  performance acceptance. Evidence: `goal5/vanilla-moving-performance`.
- GUI item-cache eviction moved to the whole-frame boundary; reuse/replacement/
  cleanup, 85 GUI tests and 26 boundary tests pass. Live Current/Frozen FPS
  206.5/875.2, entities 182/182, p95 6.55 ms; GUI/lowering 0.37/0.15 ms,
  batches 43→8, draws 52→17, gain ~25%; repeats pending (`goal5/gui-cache-moving-performance`).
- Complementary/DH entry initializes private graph/depth and shadows before meshes.
  V4 world103/source122, clean/RGB 3.826/4.833/4.191; depth proof missing (`goal5/source-entry-dh-v4`).
- Iris+DH coverage verifier: added opt-in readbacks of the actual main/DH opaque
  depth snapshots, with exact screenshot/submission identity and float hashes.
  Exact clear comparisons replace quantized depth; eight Python evidence checks,
  native owner/restore and 42 LOD tests pass; V5 transfer-source usage corrected.
  V6 both captures completed, clean validation, first world
  frame 94 selected source. Mask: 120,552 pixels (13.1%), exact frame 677/submission
  1292. DH extension RGB MAE 9.686/15.968/11.238 exceeds unchanged tolerance 6;
  Whole-image MAE 3.944/4.932/4.242 hides far-water mismatch (V6); parity fails.
- Particle cutouts: Frozen discards alpha ≤0.1; selected textured writer now preserves
  explicit cutoff/off properties. Sixteen focused checks include native module creation.
  Exact-frame receipt lookup and one pending producer snapshot fix capture retries;
  19 Java tests pass. Ordinary flame remained static; no animation parity claim.
  Hidden/visible leaf pairs pass: maximum visible cell RGB MAE 162.99→2.898,
  effect maximum 3.145 within unchanged 6; both clients complete, clean validation.
  Full Rust 1,972/2 ignored; `goal5/particle-leaf-alpha-*-fixed`; flicker unproven.
- DH noise diagnostic: shared PNG texel (17,23), RGBA [124,196,150,255];
  Frozen black/Current tinted, clean (`goal5/dh-noise-texel-probe`). Not parity;
  user decision pending: configured sampling or Frozen's black behavior.
- Iris+DH original/lease-only runs: 8,562/8,640 builds, no eight quiet frames;
  Frozen completed (`goal5/iris-dh-overlap-video`, `goal5/iris-dh-overlap-lifecycle-v2`).
- Reproduced two CPU lifetime errors using real containers: identical replacement
  close retires a shared generation; snapshot trimming retires live quadtree
  columns. Atomic CPU leases and protection during trimming fix both regressions.
  All 82 DH subsystem tests pass. V3/V4 completed both 1,200-frame runs clean;
  Current stopped at 312/309 builds, 98 visible columns. V4 peak RSS
  Current/Frozen 5.68/5.98 GiB. V4 video: 460/480 samples, 8 s, max gap
  34/17 ms; far-island maximum adjacent mean 0.247/0.390 levels/channel.
  Evidence: `goal5/iris-dh-overlap-lifecycle-v4`; narrow case, flicker unproven.
  Large-radius/transition memory bounds pending. MakeUp pair: Frozen completed;
  Current crashed before first world frame (missing source snapshot at correlation).
  Guarded rejected-source preparation and preserved contract rejection diagnostics;
  Three entry tests/full Rust pass: 1,975 passed, 2 ignored; release rebuilt. MakeUp V2
  survives entry with clean validation but rejects `DoLighting` contract; admission unresolved.
- Single-color source discovery preserves original lighting, output slots and
  legacy varyings; used attributes remain gated. Rust custom expressions preserve
  Frozen overloads and typed std140 packing, checked against 32 evaluator cases.
  Evidence: `goal5/custom-uniform-expressions`; MakeUp's nine custom uniforms resolve.
- Built-in uniform caches: 26 Frozen cases/reload/resize pass; MakeUp resolves (`goal5/builtin-frame-uniforms`).
- Standard Iris aliases and stage-selected custom PNGs now resolve in Rust;
  geometry/fullscreen texture domains and sampled-asset/output identities stay separate.
  Fixed raw secondary shadow aliasing and late-writer wrapper cache invalidation;
  partial creations retire on failure. Combined matrices/fog/primary-only shadow fixed.
  Frozen particle draws prove `mc_Entity=(0,0,0,1)`; composite/disc/Sun native pixels pass.
  Sky now uses the Y=16 disc; celestial local vertices/matrices/raw clock match Frozen.
  MakeUp water/LOD bias, output lifecycle and sky UV fixes: full 2,060/3 ignored/release.
  Historical RGB 7.224/6.456/4.248 fails tolerance 6; foliage/water remain, validation clean.
- Normal terrain cutout alpha >0.1 (opaque none); selected overrides/main wrappers
  pass. MakeUp restores leaf holes; RGB MAE 7.576/6.945/3.877 fails
  (`goal5/makeup-settled-terrain-alpha-fixed-pair`; full 2,064/3 ignored/release/wiki).
  Animated clocks differ: Current freezes/Frozen advances; matching clocks required.
- Frame-start clears reset both clear-enabled sides; clear=false history survives.
  Cached GAL passes; native before red/fixed blue, mips/resize/reject/world/order pass.
  Full 2,066/3 ignored/release/wiki; Complementary clean RGB 10.327/9.485/8.576 fails
  (`goal5/complementary-frame-start-clears-pair`); no flicker-root-cause acceptance.
- Iris horizon now precedes disc: owned 2,076 vertices, capped radius/fog alpha 1.
  Captured Frozen event 888/native coverage/wall/gates/rejection checks pass.
  Full Rust 2,070/3 ignored/release/wiki; fresh clean Complementary pair passes
  RGB 5.050/5.069/5.260; band removed (`goal5/complementary-horizon-fixed-pair`).
- Scoped shadow/color directives, bounded caches, cutout identity and exact R16F
  implemented; prior full Rust 2,017 passed/3 ignored and wiki passed. MakeUp:
  eight targets, 840/105/3/-25 camera, ten modules (`goal5/scoped-pack-directives`).
- Shadow distances/entity selection: ABI 68 copies user chunks, immutable world
  renderer/leash bounds, double camera, eligibility and player-only roles. Rust owns
  distance/frustum/caster decisions; block flags are independent. Java adds bounded
  off-camera candidates; orb roles survive Rust geometry creation. Unported shadow
  streams retain admission gaps; six tests/108 Frozen AABBs/23 Java checks pass.
  Terrain: 9,180 cases; release/wiki/full Rust pass (2,028/3 ignored). Live
  Complementary first world frame 91/sub109 selected-source, clean settled capture;
  no world fallback. No Frozen image pair/flicker acceptance.
- Historical moving vanilla: no coherent tiles in narrow triage; GPU overlap unproven
- DH DOUBLE_PASS crash fixed: preserve same-frame/no-copy sampled state before fade.
  Four regressions/full Rust pass (2,032/3 ignored); release/wiki/13 Python pass.
  Both clients completed 16,000 + 8,000 frames clean; narrow videos 961/943 samples.
  Lags 1/2/4: Current 0 coherent tiles, Frozen 1 foreground candidate; flicker unproven.
  Evidence: `goal5/direct-dh-snapshot-state`; paired temporal retry linked there.
- Explicit source ignored with DH off: selection/cache identity fixed across routes;
  3 regressions/17 tool/2 harness/wiki pass (`goal5/canonical-world-source`).
- Corrected shader/DH-off moving pair: both 6,000 frames, 961 samples/8 s;
  no coherent tiles at lags 1/2/4. One acquired image; GPU overlap unproven
  (`goal5/moving-shaders-shared-world-video`). Separate validation-off pair:
  Current/Frozen average FPS 34.6/311.2, median 28.53/3.00 ms; native source
  preparation 6.52 ms, hazards 3.34 ms, GPU 3.90 ms (baseline).
- Fixed stale producer-readiness deadlines carried across ready/restart periods;
  2 regressions failed before, 15 Java checks pass. The initial optimization pair
  failed this gate; retry completed both 1,800-frame runs, no Current restarts.
  Inline-storage experiment was slower (hazards 3.34→4.96 ms) and reverted.
- Destination-vector preservation: nine hazard checks/full Rust 2,033 pass; live
  pair Current/Frozen median 25.25/2.99 ms, FPS 38.1/317.1. Hazards 3.34→0.87 ms,
  reads 19,916→19,899; observed 11.5% frame reduction, broader repeats pending:
  `goal5/hazard-range-allocation/gameplay-destination-preserved`; flicker unproven.
- Preparation/retirement +source packing: full2,036/window/release pass; 80-byte records,
  256-byte descriptors; median22.97/2.99ms, uploads3.54→1.25MB/frame, validation clean.
- Shader resize: both 4,000-frame runs/three X11 resizes pass, validation clean; first-resized frame unproven.

## Required evidence and remaining work

| Area | Status / required proof |
| --- | --- |
| Vanilla terrain, fluids, transparency | Fresh static, moving and temporal pairs pending |
| Entities, items/hands, particles, sky/weather | Motion, day/night/rain gameplay pending |
| GUI, HUD, text, post-processing | Real screens and effect pairs pending |
| Resource packs | Equivalent non-default packs and real reloads pending |
| DH opaque, translucent, water, LOD | Selected-column asset-ack loss fixed in bounded shared-DB repeats; broad water/LOD, transition and resource-bound evidence pending |
| Iris packs | Cutout rule implemented; multiple packs, broad and animated parity pending |
| Iris+DH | Shared DB, shader/DH water and terrain pairs pending |
| Terrain flicker | Reproduce, isolate cause, fix, verify all four routes |
| First shader world frame | Complementary and MakeUp/DH-off initial frames verified; other packs/transitions pending |
| Loading/unloading, dimensions | Repeated real transitions pending |
| Shader toggle/reload, resize/fullscreen | Repeated transitions pending |
| Memory / GPU resources | Long-run and reload/transition bounds pending |
## Baseline and harness constraints
- Sole baseline: `../MattMC_JavaPerfTesting/MattMC`, **Java OpenGL**; never alter Frozen.
- Use Capture.py/Gameplay.py with equivalent copied worlds/camera/time/settings/packs/DH.
- Capture stdout/stderr; Rust diagnostics can bypass `latest.log`. Active→fallback fails.
- One Rust-owned GAL/backend/presenter; Java supplies immutable CPU data, no Iris/DH GPU handles.
- Supplemental tests do not establish gameplay parity; distinguish flicker from animation.
- Deterministic captures force GPU retirement and can hide overlap defects;
  use the gameplay benchmark or RunDev for synchronization/flicker investigation.
## Historical observations to revalidate (not current acceptance)
- Frozen DH DB formats 1–2/Current up to 4; shared Frozen fixture in scratch `g4src`; inspect before reuse.
- Prior gaps: energy swirl/shader fallback/hand-water admission, gun shading, flat foil;
  custom-model/fluid meshing, world-map mips, panorama capture, shadow/color-space controls.
  Revalidate and implement as required.
## Frozen behavior decisions
- User decision: ignore Frozen look-down haze (zero-sized composite shadow maps); preserve Frozen.
- Prior unresolved suspicion: DH hill brightness differs because Frozen DH
  `noisetex` appears black rather than the pack texture. Revalidate evidence;
  ask the user before treating suspected Frozen behavior as a bug.
## Additional audit follow-up (unverified review claims)
- Preparation retries; DRAWBUFFERS/RENDERTARGETS; shadow texture stage; malformed custom uniforms.
- Entity cap/varyings/attributes/shadow quads/archive checksums; other disabled audit strings unverified.

# Rendering parity, stability, and performance
## Active objective — Goal 5
Complete realistic end-to-end parity for vanilla, DH, Iris, and Iris+DH against Frozen Java OpenGL; root-cause terrain flicker across all routes; first shader world frame must use the pack. Profile before optimizing; prove real-world gains and bounded resources. This goal is **not complete**; results below are author-recorded scoped evidence, not broad acceptance. Checkpoints `2fff1ef19` (2026-10-03) and its 2026-10-04 successor were published only by explicit user sync-and-push; otherwise leave all work **uncommitted in the worktree; no commits, pushes or publication**.
## Current work (2026-10-04)
- Base `2fff1ef19`; full Rust2,158 pass/3 ignored,20 Java ABI checks pass; historical vanilla RGB0.236/0.430/0.466; narrow.
- Sky aliases fixed/full2,073 pass; fresh Complementary RGB5.051/5.029/5.244 passes, MakeUp6.860/6.127/3.555 fails6.
- User16:30 haze: sky-check sampled wrong depth rows, driving excess volumetric light; source integer addressing fixed.
  GPU before0→5 sky samples; native/history addressing preserved; family92/full2,146 pass.
  Original4420 Iris+DH RGB13.459/11.926/13.921→3.712/4.221/3.895; DH mask4.915/5.225/4.842 passes6.
  Shader-only RGB2.342/1.918/2.197 passes6; clean, original/light shafts intact; settled only.
- DH opaque leaves incorrectly entered dh_water; alpha rule restored/crowns confirmed, six before-fail/138 tests pass.
  Original pair clean RGB3.721/4.249/3.882/DH4.955/5.390/4.733; vanilla DH8.249/7.553/6.378 fails before/after leaf; fog-off12.311/11.190/9.548 fails6. Harness fog override fixed/7 tests pass (`goal5/dh-tree-silhouette-routing`).
- DH pale seabed: actual Frozen post-VS keeps sky0 dark; built-in Vulkan-only skylight fold removed.
  Native opaque/water×sky0/15 before-fail/after-pass/full2,150/release; normal-fog coast RGB0.943/1.205/1.420, DH2.043/1.587/1.473 passes6/clean (`goal5/dh-water-source-observation`).
- First shader frame: Complementary97/source97, MakeUp87/source87 clean; other packs/transitions pending.
- Shadow-only orb crash: mesh-boundary compaction fixed/before reproduced;13 Java pass; live clean/RGB5.022/5.043/5.247; trace removed.
- Original4420 moving control/candidate/repeat: batching4.726→4.005/4.102 ms (13–15%); FPS34.36→34.87/34.60,
  Frozen304.22/308.23; complete1,800 each/clean; first candidate readiness differs, no overall gain.
  Repeated full-plan cache preserves culled order/filters copy; same four-entry bound; full2,157/3 ignored.
  Iris+DH RGB3.718/4.251/3.881/DH4.954/5.394/4.732 passes6/clean; JFR163→0,6k failed (`goal5/shadow-batch-cache-profile`).
- Per-pass uniforms: four CPU checks/full2,124 pass; exact ownership. 1,800-frame control/candidates: prepare5.87→4.83/4.78 ms (~18%); RGB2.335/1.908/2.196 passes6.
  Current40.2→38.4/41.6 FPS, Frozen302.1/313.5/302.0; no overall FPS win. Probes removed.
- Early shadow selection preserves order/validation; six regressions/full2,079/3 ignored. Batching2.489→2.020/2.030ms (~19%); FPS38.8→39.6/38.1 vs Frozen301.7/303.9/307.4, no gain proved.
  Each1,800 frames; RGB5.011/5.038/5.235 passes/clean; first world184/source184 (`SHADER-TERRAIN-PROFILING.md`).
- First-look shader+DH near chunks: FIFO shadow starvation/underground-first prefetch root-caused;
  urgent edits/3D-near prefetch/portal ties and immediate loaded-air connectivity fixed; 25 Java/64 tool pass.
  Fair dispatch: one nearest/three portal slots;2 before-fail/24 pass; traced CPU settled set21.5→2.3s; traces removed (`goal5/terrain-water-publication-trace`).
  Original4420 untraced: cold water band remains; idle10s Current/Frozen nearby terrain/water present, three240-sample turns each; retrospective cores reject Current teardown, no FPS/broad acceptance (`goal5/terrain-water-fair-priority-untraced`).
  Archive overwrite caught/excluded; driver pins source/runtime SHA before input (`goal5/terrain-first-turn-post-haze`).
- Correlated cold frames123/166: terrain opaque/cutout/water21/16/2→244/226/114; near-water regular depth absent early, DH present; camera/fog match. Geometry readiness still open, diagnostic timing.
  Fourteen stages/eight uniforms each match presentation;75 tool pass (`goal5/terrain-water-first-view-attachments/correlated`).
  Runtime rejected: native SIGSEGV during disconnect/frame2164; core truncated before render stack. Cause unresolved; no stability acceptance.
- Cold workers: retain64 tint coordinates/reuse position; gate disabled light receipt names;21 Java pass. First2s sampled allocation1.760→0.195GB, no FPS/pop-in acceptance.
  Native44 state-ID memo/33 Java pass: padded-grid samples81→3 first2s,413→20 next8s; both reaped/no cores,6×240 movies still show band (`goal5/terrain-state-id-memo`). Untinted bulk fill/35 Java pass: next8s tint samples376→36,color lookup371→22; no FPS/latency gain proved (`goal5/terrain-untinted-lattice`).
  Ordinary Current/Frozen 6×240 turns: cold seabed strip remains Current/revisit clearer; both retain haze. F3+T acknowledged/restored screenshots; Current reload240 samples,Frozen duplicate-PTS video rejected. Both reaped/no cores; Current peak7.46GiB, long-run bounds open.
  Six240-sample movies still show band; native ABRT at both terminations missed by wrapper143. Core grace gate/artifact rejection+79 tool pass (`goal5/terrain-first-turn-startup-profile`).
  Closed-pipe SIGABRT reproduced; std-only best-effort writes cover production renderer diagnostics; stdio/full2,158 pass. Native43 diagnostic abort correctly rejected; Native44 Original4420+DH diagnostic repeat reaped/no core/hs_err, 3×240 samples/2 matching readbacks. Readiness/older SIGSEGV still open (`goal5/native-trace-closed-pipe`).
- Translation driver: travel/setup/route/forest/disk verified,60 tool pass;vanilla v4/Frozen v6 X9→3→9,480/direction,lag1/2/4 zero tiles.
  Inland vanilla X9→15→9: lag1/2 max0; lag4 2/1 both, motion edges; no parity/flicker acceptance.
  Inland Complementary repeat/Frozen: lag4 max5/7 vs3/6, edge candidates; v1 manual-input run excluded.
- Normal Complementary v2/Frozen v1:480/direction,lag1/2/4 zero tiles/clean; peak5.53GiB, first-turn up to6.98GiB; long-run bounds open.
- DH before fix: shared v2 DB/radius32/DOUBLE_PASS, X9→3→9; Current return133/133/115 tiles,
  Frozen all0; repeats32/64, Fade NONE53. Retained ordinary-pair receipts; not the sole flicker cause.
  Explicit trace links one-frame missing column to asset-ack pruning (365→362→365).
  Fix defers selected-column replacements until presentation; two before-failing regressions,
  61 Java tests pass; traced fixed all six lag analyses max0, 30k records/no prunes.
  Untraced repeat all six max0; clean/reaped, peak5.01GiB. Temporary traces removed.
  Iris+DH all six max0/clean; user reports no noticed morning flicker. Broad coverage/long-run bounds open.
- Spectator-in-solid terrain omission: missing Frozen portal bypass root-caused.
  CPU policy/cache identity fixed; nine Java/native tests pass; live before/Frozen/after
  confirms restored cave terrain. Clean/reaped, after peak 3.69 GiB; pixel/perf gaps remain.
- Cave sky fog: owned lower-disc transform/fog/depth fix confirmed live; peak3.84GiB (`goal5/dark-sky-disc-fog`).
- Underground original4420 parity still fails RGB20.566/15.041/10.029; production light shafts remain enabled.
  CPU shadow-facing fix: full2,153 pass/3 ignored; Frozen-only depth251,375→22, shared-depth error0.0106→0.0010.
  Captured-uniform5/33tests/release/live1067 clean; Frozen684 settled fog matches, shadow depths unbound.
  VL-only null-depth diagnostic passes6 (RGB4.354/4.105/4.341); Frozenoriginal change0.477/0.257/0.137.
  Underground exception decision pending. Capture VUID09600 root fixed: absent snapshots marked unavailable;6/34tests/release/live657 clean. Coast RGB3.671/4.177/3.852/DH4.951/5.389/4.726 passes6 (`goal5/shadow-facing-coast-regression`).
- Disabled audit formatting: normal 60s JFR confirmed discarded strings; gate fixed.
  Nine GUI tests pass; repeat audit allocation samples 5→0; no FPS win claimed.
  `goal5/audit-formatting-guard`; mesh publication/foil reads need further profiling.
- Foil resource copies: bounded two-entry CPU cache, cleared by real renderer reload; 27 Java checks pass.
  Historical JFR copy797 samples/~1.45GB weight → new copy0, extraction still sampled; no FPS win claimed.
  Total sampled allocation rose; timing/native differ. Real shader+DH F3+T pair clean/240 samples each; A/B pending.
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
- Empty shadow initialization fixed: both producers failed before, native0→1; discard/replacement balanced/full2,149 pass.
  Original4420 shader RGB2.356/1.924/2.214; Iris+DH3.701/4.194/3.889, DHmask4.929/5.236/4.849; clean, settled only (`goal5/rejected-source-shadow-depth`).
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
  Large-radius/transition memory bounds pending. Historical MakeUp entry crash was guarded;
  source admission now runs, but current image parity remains failed as recorded above.
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
- DH DOUBLE_PASS crash fixed: preserve same-frame/no-copy sampled state before fade.
  Four regressions/full Rust pass (2,032/3 ignored); release/wiki/13 Python pass.
  Both clients completed 16,000 + 8,000 frames clean; narrow videos 961/943 samples.
  Lags 1/2/4: Current 0 coherent tiles, Frozen 1 foreground candidate; flicker unproven.
  Evidence: `goal5/direct-dh-snapshot-state`; paired temporal retry linked there.
- Explicit source/DH-off identity fixed; 3 Rust/17 tool/2 harness/wiki pass (`goal5/canonical-world-source`).
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
| DH opaque, translucent, water, LOD | Selected-column asset-ack/generation pruning fixed in bounded shared-DB repeats and on coast; broad water/LOD movement/parity, transition and resource-bound evidence pending |
| Iris packs | Cutout rule implemented; multiple packs, broad and animated parity pending |
| Iris+DH | Original-pack coast pose passes whole/DH-region tolerance; other scenes and motion pending |
| Terrain flicker | Reproduce, isolate cause, fix, verify all four routes |
| First shader world frame | Complementary and MakeUp/DH-off initial frames verified; other packs/transitions pending |
| Loading/unloading, dimensions | Repeated real transitions pending |
| Shader toggle/reload, resize/fullscreen | Repeated transitions pending |
| Memory / GPU resources | Empty-source shadow init/discard and closed-pipe abort fixed; independent SIGSEGV and long-run/transition bounds pending |
## Baseline and harness constraints
- Sole baseline: `../MattMC_JavaPerfTesting/MattMC`, **Java OpenGL**; never alter Frozen. Use Capture.py/Gameplay.py with equivalent copied worlds/camera/time/settings/packs/DH.
- Capture stdout/stderr; Rust diagnostics can bypass `latest.log`. Active→fallback fails.
- One Rust-owned GAL/backend/presenter; Java supplies immutable CPU data, no Iris/DH GPU handles.
- Supplemental tests do not establish gameplay parity; distinguish flicker from animation.
- Deterministic captures force GPU retirement and can hide overlap defects;
  use the gameplay benchmark or RunDev for synchronization/flicker investigation.
## Historical observations to revalidate (not current acceptance)
- Frozen DH DB1–2/Current up to4; inspect shared scratch `g4src` fixture before reuse.
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

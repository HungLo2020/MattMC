# Rendering parity, stability, and performance
## Active objective — Goal 5
Complete realistic end-to-end parity for vanilla, DH, Iris, and Iris+DH against Frozen Java OpenGL; root-cause terrain flicker across all routes; first shader world frame must use the pack. Profile before optimizing; prove real-world gains and bounded resources. This goal is **not complete**; results below are author-recorded scoped evidence, not broad acceptance. Checkpoints `2fff1ef19` (2026-10-03) and its 2026-10-04 successor were published only by explicit user sync-and-push. Since 2026-10-05 the user allows **local commits at tested milestones; never push**.
## Current work (2026-10-05): retained render scene
- Design and phases: `docs/development/rendering/RETAINED-SCENE.md` (user decisions: replace piece by piece, Frozen-identical culling, Vulkan/RTX 2070+, parity = performance and visuals across vanilla/shaders/DH/moving/settled). Profiles normalized to gameplay time (the profiler window can overlap shutdown): worker ~82% busy, Java waits ~40%.
- Phase 1 (shader route) landed: described resident section meshes (`SceneTerrainGroup` per facing group on the retained record) draw through `prepare_scene_terrain_draws` — one instance block, runs per (kind, cull, winding, page), one indirect draw per run, shadow twins, supplement faces and light-frustum casters as shadow-only runs; undescribed sections keep the batch path. Shadow batch-plan/retained-write work removed for ~6,000 casters/frame. Moving bench (profiled) 115.7→121.2FPS, plan 31.5%→25.8% of gameplay; Iris+DH RGB3.694/4.208/3.856, DH pass, 0 VUIDs (`perf-correctness/scene1c-pair`). Full native 2,201/3. Committed `9f992722e`.
- ABI 70 compact terrain: Java sends camera-visible section layers as 40-byte entries (no per-section records, active-instance replay or reconcile); Rust draws its acknowledged generation and expands them first in the instance list. Diagnostic/fault/reload frames keep per-record terrain (`-Dmattmc.dev.perRecordStaticTerrain=true` forces it). Profiled moving bench 121.2→126.2FPS, p99 14.3→12.2ms; Iris+DH RGB3.699/4.219/3.877, DH pass; vanilla RGB0.133/0.214/0.220 (was 0.219/0.375/0.425; replay ordering gone); 0 VUIDs (`perf-correctness/compact1-*`). Scene partition resolves each record once (`SceneTerrainEntry`). Worker still ~85% busy (~6.6ms): plan 2.1, GAL 1.1, expansion+partition+validation ~1.0, occupancy 0.44. Committed `3f0e3b196`.
- Armed shader frames keep terrain compact: `take_scene_terrain` builds scene entries from compact sections/casters (one record lookup; records dropped when a key changes generation) and expands only the rest; frames leaving the route expand (`expand_static_terrain`); coverage validation and voxel occupancy read compact/scene terrain. Profiled 127→133–137FPS; Iris+DH RGB3.714/4.238/3.876 DH pass, vanilla RGB0.236/0.398/0.449, 0 VUIDs (`nocompact2-*`). Remaining worker: plan 1.9, GAL 1.2, scene entries 0.5 (large records + Arc refcounts: needs dense scene table), occupancy 0.46.
- Frozen shadow casters (`9bcb28f43`): no Java shadow build sweep; Frozen's tree leaf test (±8, no cylinder) for all casters. Profiled 133→188FPS.
- Phase 3 visibility in Rust: `chunk/section_graph.rs` ports Frozen's camera search exactly (BFS waves, masks, cylinder, ±9.125 boxes, nearby pass, tree mode for out-of-graph cameras; 13 unit tests); Java mirrors ChunkTracker readiness and build info via a standalone handle (`RustSectionGraph`), takes visible = visited∧built∧flags≠0, builds in visit order (edits first, 2×workers in flight). Old Java frontier/priority code and its tests removed. Profiled 175.7FPS (was 187.8; Java terrain 0.95→0.75ms but more translucent sections now visible, 25→40 dynamic, worker 4.14→4.38ms); Iris+DH RGB3.684/4.194/3.865 DH pass, vanilla RGB0.134/0.217/0.213, 0 VUIDs (`graph1-*`, `graph2-wall`). Remaining: region draw order, Iris non-culling frustum, visible list into the scene.
- Shadow-entity prefilter: Java extracted ~500 shadow-only entity instances/frame and Rust admitted ~15. A standalone Rust `EntityShadowQuery` (shares the pack's overworld shadow policy, never joins the frame) now answers Iris's admission from copied culling facts before Java extracts; the frame plan re-applies it. Profiled 175.7→195.7FPS (median 4.54ms); Iris+DH RGB3.683/4.188/3.866 DH pass, vanilla RGB0.176/0.297/0.316, 0 VUIDs (`shadowq1-*`); native 2,215/3.
- GPU (2026-10-06): Frozen is GPU-bound (~90-96% util at 311FPS, ~3.0ms GPU); ours 3.37ms with ~0.9ms outside passes (barriers 0.2, texture copies 0.2 — 14 current→previous copies vs Iris's ~5 flip swap-backs, host writes ~0.25, mipmap blit chains 0.25). Host writes (55/frame, each draining via ALL_COMMANDS) are now hoisted to list start and lowered behind one dependency: GPU 3.37→3.23ms; parity unchanged (vanilla water diff is pre-existing animation-phase variance). Staging pool kept largest chunks first (one 23MB upload chunk crowded out the 4MB ones → ~27 vkAllocateMemory/s): now best-fit reuse and smallest-first retention; encode 0.57→0.41ms, profiled 193.8→200.4FPS. Fullscreen plans now park at frame end and are reused whole while inputs are equal: post-terrain staging 0.25→0.02ms, profiled 200→216FPS; parity Iris+DH 3.660/4.169/3.853 DH pass, vanilla 0.128/0.211/0.207, 0 VUIDs (`park1-*`). Parking first missed every frame (voxel-light inputs alternate; sky stages share a path) and blocked the stage cache: variants now 4, grow before evicting; all stages reuse, per-frame GAL creates ~26→~0.2 (lightmap residency remains). Entity uniforms shared (`Arc<[u8]>`). Profiled 216FPS (median 4.36ms); parity Iris+DH 3.702/4.238/3.868 DH pass, vanilla 0.183/0.304/0.323, 0 VUIDs (`variants1-*`). Worker ~3.5ms (submit 1.15, plan 1.14), Java ~2.6ms.
- Queued frames: Java waited for frame N before handing over N+1 (worker idle ~1ms/frame). Worker now runs a FIFO: queued mesh-asset updates and atlas ticks, and frames whose job acquires/executes/presents; Java keeps ≤1 frame queued ahead, double-buffers persistent record staging, takes retirement from present results; captures/screenshots/RenderDoc drain to sync. Profiled 216→263.7FPS (median 4.36→3.64ms, p99 10.5→7.6); parity Iris+DH 3.788/4.358/4.016 DH pass (within Frozen's own run-to-run capture variance), vanilla 0.301/0.538/0.639, 0 VUIDs (`queued1-*`). Release play skips per-frame GAL op/handle/hazard validation (user decision; debug, tests, captures and `MATTMC_GAL_VALIDATION=1` keep it): native 3.66→3.32ms, unprofiled 246/252→273.5FPS (`noval-*`). End-of-frame feedback copies skip clear=true targets (next frame clears both sides first): GPU 3.30→3.12–3.27ms, median 3.42→3.30–3.35ms; parity Iris+DH 3.672/4.181/3.828 DH pass, vanilla 0.287/0.470/0.544, 0 VUIDs (`deadcopy*`). Mip chains skip regeneration while level zero is unchanged: no measurable change (`mipskip*`), parity pass. Per-pass GPU timing (temp diag): terrain 0.79–0.91, shadow 0.68–0.73, translucent 0.28, out-of-pass ~0.55 (7 mip chains 0.25, 12 feedback snapshot copies 0.15, depth copies 0.07, host-write groups 0.10); batch-wide host-write hoist gained nothing (rejected). Deficit vs Frozen is in the first ~600 measured frames (streaming + scene); last 600 frames match Frozen (3.1–3.2ms). Voxel source selection reuses scratch and sorts (key,index); section asset row maps use mixed keys (Long.hashCode treeified bins): parity pass (`voxrow*`). Source vertices packed 128→64B (exact lanes decoded in GLSL): shadow 0.695→0.680ms, FPS 271/281→284/283; parity Iris+DH 3.688/4.209/3.850 DH pass, vanilla 0.203/0.348/0.383, 0 VUIDs (`vtx64*`).
## Earlier work (2026-10-04)
- Perf (evening): async-profiler (itimer/dwarf, release+line tables) whole-frame profiles replaced component timers. Moving bench rotates yaw 0.35°/frame, so A/B uses 2,057 frames (two rotations), interleaved control/candidate ×2.
  Fixed: per-upload VkBuffer/vkAllocateMemory churn (bounded mapped staging pool); BTreeMap mesh-asset lookups (mixed-key HashMap); per-frame zip inflation of item/glint/armor PNGs (reload-cleared cache); per-lookup optical scans; no-orb frame copy; hash-order section/candidate order (canonical key order, plan cache hits); per-draw deep clones/compares of source resource sets (Arc-shared); per-sample voxel transforms and linear material-rule scans (cached centres, lazy dense table).
  Cumulative A/B: 36.88→47.95FPS (frame27.12→20.86ms, p99 35.75→29.23, native18.01→12.51, GPU7.86→6.44). Original4420 Iris+DH RGB3.690/4.213/3.872, DH4.959/5.400/4.738 pass6, clean validation (`goal5/ab/cumulative-1`, `goal5/perf-correctness`).
  Java shadow candidates now enqueue in one producer-lock batch: shadow-submit1.01→0.81ms, A/B 49.18→51.20FPS (`goal5/ab/shadow-batch`); voxel patch box merging made quadratic (randomized equivalence test). Remaining: ~6,000 instances/frame (mostly off-camera shadow candidates) through ~8 Rust passes plus Java admission; Frozen ~3.3ms/frame.
  Vanilla moving bench never settled: frustum-gated cave discovery adds ~1 section per rotation (same in Frozen's Sodium), and Current restarted on any build. Moving rows now start drained and count streaming (`terrainQueueDrainDuringMeasurement=false`, 4 harness tests). Also gated diagnostic layered-geometry upload watches to attachment captures (range-limited scans), fast-hashed vanilla mesh resource maps, XXH3 GUI fingerprints. Vanilla A/B ×2: 162→252FPS (6.15→4.00ms), all 0 restarts; Frozen 983FPS; (`goal5/ab/vanilla-ab`). Spikes root-caused: instance stream regrew to exact size while streaming, rebinding ~670 mesh resource sets (~32ms); geometric growth (before-fail 512→≤7 regrowths): vanilla pair p99 40.7→11.2ms, >20ms frames ~30→2, 250 vs Frozen 990FPS (`goal5/perf-pairs/vanilla-2`). Remaining first-use cost was Shaderc (50–77ms/module, 1.4s/session; driver pipelines ~0.1ms): persistent validated SPIR-V disk cache added (2 tests). Harness timed dumps no longer force full GCs.
  2026-10-05 (user: architecture changes fine, keep parity): shader-route pass work restructured — per-batch pipeline/pack/frame-data/geometry lookups resolved once per pass (batch-scope memos, frame-data results carry their sets), shared uniform blocks matched by Arc identity, entity pipeline/pack/interface memos (`FrameMemo`) and per-group uniform packing shared across sections; GAL hazard validation skips unchanged read-only bindings between consecutive draws (write bindings still re-checked, mutation-tested); voxel sources selected by volume cull before touching assets, keyed by in-volume set in mesh-key order and passed as a shared `Arc` list the occupancy runtime recognizes (was rebuilt every frame: frame order and out-of-volume churn); camera batch plan keyed by its admitted instances (`mesh_batch_indices_cover_selection`). Shader pair 49.1→57.0 vs Frozen 313.6FPS, p99 27.7→22.5ms (`perf-pairs/shaders-10`); parity Iris+DH RGB3.657/4.171/3.853,DH4.963/5.408/4.749 pass6, vanilla RGB0.221/0.350/0.389, 0 validation (`perf-correctness/cumulative-4`,`vanilla-cumulative-2`). Full native2,188/3. Remaining: shadow-supplement plan rebuilds (repeated model meshes force full-frame keys), per-section Java extraction/FFI, GAL submit. Later 10-05 (user: performance AND correctness parity, keep going): retained per-mesh terrain records (one lookup/batch), repeated-mesh plan keyed by selected meshes only, per-binding hazard reuse + per-resource pending destinations; ABI69 shadow casters as compact arrays expanded in Rust (no per-candidate Java records/active state), primitive per-section caster identities, Rust key-order sort; entity sections with identical state/uniforms/instances and contiguous indices draw as one range; opaque/cutout batches ordered by material+page so indirect commands merge (ops 3,177→1,274, set binds 1,071→332, draws 985→247); twin shadow draws stage no duplicate records; compact drawable-generation index; pure-translation voxel culling; precomputed retained section commands. Moving bench 66.3→83.1FPS (12.04ms; `async-profile/shader-cpu14..20`); parity Iris+DH RGB3.685/4.195/3.868, DH pass6, 0 validation (`perf-correctness/cumulative-6`); vanilla RGB0.221/0.386/0.434 pass (`vanilla-cumulative-4`). Full native2,194/3; Java ABI tests updated, 2 AtlasAnimation/ShieldAtlas failures pre-existing. Then: Java checkpoint journals (registry membership + pending-set undo; markModelMeshBatch 1.7%→0.1%), ineligible off-camera entities skip extraction, compact batch range memo + optical-bit residency index (fast caster validation), caster facing mask 0x7f, per-pass retained binding cache. Moving bench 86.8FPS (11.52ms, `shader-cpu27`); Iris+DH RGB3.695/4.213/3.857, DH4.941/5.379/4.724 pass, 0 validation (`cumulative-7`). Remaining native ~7ms is per-item memory latency + GAL; Java ~4ms; parity likely needs pipelined native submit and GPU-driven terrain. Pipelined frames (default; MATTMC_PIPELINED_FRAMES=0 opts out): decoded whole frames execute+present on a per-context native worker (P-core preferred), every entry point joins first, Java completes the previous frame's receipts at the next frame; atlas pumps defer while a frame is in flight. Shader 86.8→110-113FPS (native 7.4ms is now the bound), vanilla 280→363FPS (`shader-pipe3/4`,`vanilla-pipe1`); pipelined parity Iris+DH RGB3.701/4.219/3.870 DH pass, vanilla 0.269/0.429/0.484 pass, 0 validation (`pipelined-2`,`vanilla-pipelined-1`).
- Base `cc140840a`; user10:34 prioritizes performance and permits more Java CPU ownership in Rust; GAL/backend/presenter rules remain. Full native2,175 pass/3 ignored; module-name dependency scan corrected (position_hash false positive); historical vanilla RGB0.236/0.430/0.466 is narrow.
- User16:30 haze: sky-check sampled wrong depth rows, driving excess volumetric light; source integer addressing fixed; GPU before0→5 sky samples; native/history addressing preserved; family92/full2,146 pass.
  Original4420 Iris+DH RGB13.459/11.926/13.921→3.712/4.221/3.895; DH mask4.915/5.225/4.842 passes6.
  Shader-only RGB2.342/1.918/2.197 passes6; clean, original/light shafts intact; settled only.
- DH opaque leaves incorrectly entered dh_water; alpha rule restored/crowns confirmed, six before-fail/138 tests pass.
  Original pair clean RGB3.721/4.249/3.882/DH4.955/5.390/4.733; vanilla DH8.249/7.553/6.378 fails before/after leaf; fog-off12.311/11.190/9.548 fails6. Harness fog override fixed/7 tests pass (`goal5/dh-tree-silhouette-routing`).
- DH pale seabed: actual Frozen post-VS keeps sky0 dark; built-in Vulkan-only skylight fold removed.
  Native opaque/water×sky0/15 before-fail/after-pass/full2,150/release; normal-fog coast RGB0.943/1.205/1.420, DH2.043/1.587/1.473 passes6/clean (`goal5/dh-water-source-observation`).
- First shader frame: Complementary97/source97, MakeUp87/source87 clean; other packs/transitions pending. Shadow-only orb crash: mesh-boundary compaction fixed/before reproduced;13 Java pass; live clean/RGB5.022/5.043/5.247; trace removed.
- Rejected opaque-group shortcut: batching4.011→4.890ms/native18.349→19.528ms; FPS36.00/34.51 vs Frozen296.92/308.70; four1,800-frame runs, restarts1/0, inputs sealed/reaped/no cores; shortcut/tests removed/control restored.
  Java collections: protection0.304→0.088ms; candidate/submission allocation144/146→50/56KB diagnostic, shadow submission1.118→1.007ms;23 Java checks. Normal Current/Frozen36.54/305.96FPS/no overall gain; validated Iris+DH RGB3.713/4.249/3.884,DH4.927/5.361/4.713 pass6; clients reaped/no cores. Shadow rebuild2.6–3.3ms/hit~0.29ms probes removed (`goal5/java-boundary-performance`).
- Native lowering probes removed; Hash geometry-cache release/full2,174 pass/3 ignored. Normal preparation controls5.621/5.445→candidates5.118/5.045ms; Current36.54/36.61→37.80/38.26FPS vs Frozen303–306. All reaped/no cores; restarts0/1 vs3/6 confound overall attribution. Validated Iris+DH RGB3.723/4.255/3.886,DH4.951/5.389/4.735 pass6,clean validation/reaped/no cores; retained scoped win (`goal5/native-lower-performance`).
- Ordinary moving clone diagnostic clean1,800/3 restarts: cached program mean23–42µs/copy,65,536-copy bound; no per-frame claim. Timers removed; Arc memo full2,175/3+release pass; fresh control/candidate35.45→36.56FPS,Frozen302.31/305.34,0 restarts/clean; native18.637→17.729ms,lookup0.080→0.001ms; Iris+DH RGB3.697/4.222/3.859,DH4.919/5.350/4.702 pass6,clean validation/owned exit; retained scoped win (`goal5/native-lower-performance`).
- Source fullscreen GPU labels fixed (before-fail;4 classifier/18 timestamp/27 boundary pass): normal1800 Current/Frozen39.21/302.95FPS;1795 GPU samples total7.260ms,deferred.274/composites.510/final.022ms. Separate moving1800 standard validation clean; all3 clients gone/crash-free. Profiling metadata, not FPS gain (`goal5/source-fullscreen-gpu-profile`).
- Repeated full-plan cache (culled order kept, four-entry bound): batching4.726→4.005/4.102ms, FPS34.36→34.87/34.60 vs Frozen~306, no overall gain; Iris+DH RGB3.718/4.251/3.881/DH4.954/5.394/4.732 pass6 (`goal5/shadow-batch-cache-profile`).
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
  Historical startup pair native ABRT at both terminations missed by wrapper143; core grace gate/artifact rejection+79 tool pass (`goal5/terrain-first-turn-startup-profile`).
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
- Spectator-in-solid terrain omission (missing Frozen portal bypass) fixed; cave terrain restored live (historical).
- Cave sky fog: owned lower-disc transform/fog/depth fix confirmed live; peak3.84GiB (`goal5/dark-sky-disc-fog`).
- Underground original4420 parity still fails RGB20.566/15.041/10.029; production light shafts remain enabled.
  CPU shadow-facing fix: full2,153 pass/3 ignored; Frozen-only depth251,375→22, shared-depth error0.0106→0.0010.
  Captured-uniform5/33tests/release/live1067 clean; Frozen684 settled fog matches, shadow depths unbound.
  VL-only null-depth diagnostic passes6 (RGB4.354/4.105/4.341); Frozenoriginal change0.477/0.257/0.137.
  Underground exception decision pending. Capture VUID09600 root fixed: absent snapshots marked unavailable;6/34tests/release/live657 clean. Coast RGB3.671/4.177/3.852/DH4.951/5.389/4.726 passes6 (`goal5/shadow-facing-coast-regression`).
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
- Frame-start clears reset both clear-enabled sides; clear=false history survives; cached GAL passes (`goal5/complementary-frame-start-clears-pair`, historical).
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
| Performance | Pipelined moving shader bench ~111 FPS vs Frozen ~316 (native worker ~7.4 ms bound; Java ~4.2 ms overlapped; GPU ~3.4 ms); vanilla 363 vs ~975; remaining: native per-item terrain/batch work, GAL submit, fullscreen per-frame objects, serial hand-off |
| Memory / GPU resources | Empty-source shadow init/discard and closed-pipe abort fixed; independent SIGSEGV and long-run/transition bounds pending |
## Baseline and harness constraints
- Sole baseline: `../MattMC_JavaPerfTesting/MattMC`, **Java OpenGL**; never alter Frozen. Use Capture.py/Gameplay.py with equivalent copied worlds/camera/time/settings/packs/DH.
- Capture stdout/stderr; Rust diagnostics can bypass `latest.log`. Active→fallback fails.
- One Rust-owned GAL/backend/presenter; Java supplies immutable CPU data, no Iris/DH GPU handles.
- Supplemental tests do not establish gameplay parity; distinguish flicker from animation.
- Deterministic captures force GPU retirement and can hide overlap defects; use the gameplay benchmark or RunDev for synchronization/flicker investigation.
- Historical observations to revalidate: energy-swirl/shader-fallback/hand-water/gun/foil gaps, custom-model/fluid meshing, world-map mips, panorama and shadow/color-space controls.
## Frozen behavior decisions
- User decision: ignore Frozen look-down haze (zero-sized composite shadow maps); preserve Frozen.
- Prior unresolved suspicion: DH hill brightness differs because Frozen DH
  `noisetex` appears black rather than the pack texture. Revalidate evidence;
  ask the user before treating suspected Frozen behavior as a bug.
## Additional audit follow-up (unverified review claims)
- Unverified: preparation retries, DRAWBUFFERS/RENDERTARGETS, shadow texture stage, custom uniforms, entity cap/varyings/attributes/shadow quads/archive checksums, disabled audit strings.

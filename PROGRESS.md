# Rendering migration handoff

## Active objective

**Goal 2.5 — rough Frozen OpenGL performance parity for current Rust Vulkan.**
Keep Goal 1/2 visual and lifecycle correctness while reducing CPU overhead in the
existing Rust/VulkanicGAL path. Do not start Goal 3 shader-pack implementation.
The target is a fresh, settled and moving workload at no more than 2x Frozen
OpenGL for the same world/options, with no crash, device loss, flicker, or
vanilla/DH regression.

## Architecture constraints

- Java copies immutable render semantics; Rust owns Vulkan resources, passes,
  synchronization, submission, and presentation.
- No Java, Sodium, DH, Iris, OpenGL, or borrowed renderer GPU state crosses the
  VulkanicGAL boundary.
- Preserve explicit semantic pass/material/resource boundaries so Iris and DH can
  add source passes later without rewriting the backend.
- Publication and replacement remain atomic from the presented frame's view.

## Current performance evidence

The uncapped (`maxFps=260`) `Origin` samples use 1280x720, shader-off, release
Rust, matched settle/warmup/measurement windows, and ordinary-DH isolation where selected. The
benchmark honors explicit settled-static and moving-camera selectors, waits for
the Rust source-owned terrain queue to quiesce, and uses the same bounded sample
window and moving-path warmup for Current and Frozen.

| workload | Current Rust Vulkan | Frozen OpenGL | ratio |
| --- | ---: | ---: | ---: |
| latest settled-static (60 frames) | 5.208 ms | 4.737 ms | 1.10x |
| latest moving-camera (60 frames) | 5.924 ms | 3.140 ms | 1.89x |
| DH settled-static radius64 (120 frames) | 5.994 ms | 2.505 ms | 2.39x |
| DH settled-static real-world pair, yaw0 (60 frames) | 7.754 ms | 3.488 ms | 2.22x |
| DH moving radius128 (120 frames, publication churn) | 10.051 ms | 3.033 ms | 3.31x |
| DH moving real-world yaw105 (120 frames) | 8.071 ms | 4.563 ms | 1.77x |

The vanilla settled pair held 513 mesh batches and a visible-cache hit; vanilla moving held about 542 batches with no completed-build churn. In that moving Current sample, semantic world extraction was 2.47 ms, native submit-return 2.42 ms, FFI decode 0.10 ms, Vulkan queue submit 0.015 ms, and GPU frame total 2.93 ms (terrain cutout 1.72 ms). The measured gap is Java semantic production, command preparation, and terrain fragment work; FFI is not the limiting boundary, and no evidence supports weakening Rust/Vulkan ownership or safety.

The queue-gated benchmark change is measurement-only: moving-path warmup is 120 frames, Current rejects any measured frame that leaves the source queue non-drained, and explicit JVM overrides remain supported. This prevents asynchronous section ingestion from being reported as steady-state performance.

The current uncapped inventory sample is 6.734 ms median (3.488 ms GPU,
2.805 ms native, 0.447 ms FFI) versus 4.233 ms for the settled world. It
contains 77 GUI mesh items and 102 indexed draws; the extra cost is GUI
semantic extraction/mesh preparation and transport, with no Vulkan stall,
crash, or device loss. Clearing the selected hand produced 6.719 ms with the
same GUI counts and phase costs, so the glass-pane held-item overlay is not
the inventory slowdown. A short matched empty-hand static control was Current
3.930 ms versus Frozen 3.146 ms (1.25x); the pane-shaped scene geometry
remained after clearing the slot. The strict settled Vulkan run is complete, crash-free, device-loss-free, workload-entered, and has zero concrete VUIDs.

The earlier terrain submission audit found no justified new batching change. The
RTX device reports `multiDrawIndirect=true`; page-addressed terrain remains
grouped into compatible indirect runs. That older high-distance sample attributed
its gap to terrain fragment work; the corrected low-distance pair above instead
shows Java semantic production and coordination as the first CPU targets. FFI is
small, and no unsafe or boundary-weakening optimization is warranted.

Historic cutout culling and compact-fragment probes found no production-safe
win: forcing back-face culling did not lower the cutout region, skipping the
optional animation sample or fog was at most variance-sized, a single-sample
dynamic LOD-bias rewrite raised whole-frame GPU time, and flat-color cutout
retained about 0.8 ms of the roughly 1.0 ms region. The retained static
fragment specialization removes animation varyings and the second sample only
for validated one-frame textures. Paired settled controls held 513 mesh
batches and the same indirect counts; static GPU median was 3.34 ms versus
4.33 ms with the specialization disabled, with cutout 1.63 versus 2.58 ms.
The opt-out is diagnostic only; animated textures and source/Iris/DH pipelines
retain their existing identities. Mesh animation sampling borrows validated
asset tables, with no semantic change. Exact-pose parity shows Rust records
match source records and Current submits fewer terrain indices than Frozen; an
opt-in Vulkan stats probe measured about 1.75M input vertices, 1.16M vertex
invocations, and 4.6M fragment invocations in the settled frame. The validated
pass-local probe attributes about 2.59M fragment invocations to cutout and
0.53M to opaque; it switches at bound pipeline transitions and does not alter
normal submissions. Receipts: `terrain-parity-exact.json`,
`pipeline-stats-settled.json`, `terrain-static-fragment-specialization.json`,
`animation-sample-borrow.json`, and `animation-final-smoke.json`.

A settled follow-up grouped ordinary GUI batches that use distinct immutable
uniform buffers into one render pass. Repeated use of the same offset-zero
buffer remains split so a later upload cannot overwrite an earlier draw. Two
clean runs held the new graph at 23 passes (down from 26) and 101 indexed
draws; frame medians were 4.176 ms and 4.705 ms with different visible mesh
counts, so the small end-to-end change is not credited as a speedup.

## Completed this pass

- Fixed stable model winding identity to use cached model-local vertices and the
  copied local normal. Pose-transformed normals previously changed an immutable
  asset key as animated parts moved.
- Reverted an unproductive whole-stream upload-cache experiment after tracing
  showed first-person rendering overwrites the shared stream each frame. No new
  Rust stream cache or alternate submission path remains.
- Revalidated clean release Rust and matching Frozen runs; both were crash- and
  device-loss-free.
- Reused the render-thread GUI packing list backing arrays across synchronous
  Java-to-FFI submissions. This changes no semantic records or ABI ownership;
  the bridge copies each list before the next frame clears it. This reduced
  transient allocation by about 21 KB/frame and GUI packing from 0.111 ms to
  0.063 ms median; it remains an allocation reduction, not an FPS claim.
- Removed a second `List.copyOf` for admitted world/first-person mesh lists;
  queues still clear only after frame snapshot completion.
- Reserved existing command-list capacity during pass fusion and state
  normalization. This changes only temporary Rust vector growth; operation
  ordering, validation, hazards, and backend lowering are unchanged. The
  follow-up run was clean but noisy, so it remains an uncredited allocation
  optimization.
- Batched compatible GUI mesh composites into one destination pass while
  flushing before an offscreen target is reused. Source-image barriers and
  per-resource-set bindings remain explicit; material, target, and publication
  ownership are unchanged. The controlled inventory run reduced median frame
  time by 4.6%, Java allocation by about 56 KB/frame, and median indexed draw
  operations from 128 to 106.
- Reserved each standard-3D GUI mesh item's scheduler sequence before building
  its immutable records, so frame flush appends the records directly instead of
  cloning every layer just to stamp ordering. Other mesh producers retain the
  original flush-time stamping path. A phase-allocation run reduced submit-call
  allocation by about 89 KB/frame and total Java allocation by about 46.5 KB/frame;
  the clean run remained crash/device-loss free.
- Profiled the standard-3D item seam: scheduler reserve and publish were each
  below 0.1 microseconds per item; native mesh preparation was the larger cost.
  Added a read-only target-cache peek and metadata-only reuse for static accepted
  item rasters. Animated or uncached items retain the full raster path, and the
  Rust suite covers the reused composite metadata. The clean inventory
  follow-up was retained only as summarized evidence.
- Profiled the TACZ special-item seam: each static item previously rebuilt about
  405 KB of Java data and spent about 0.324 ms in capture plus packing. Added a
  bounded render-thread cache of copied semantic quads and prepared packed
  vertices, keyed by gun identity, GUI scale, pixel extent, and exact transform.
  Animated and foil items bypass it; resource reload clears it. The cache is
  semantic-only and retains no renderer, GPU, or FFI object.
- Replaced fragile rich-shader string surgery for the compact DirectTerrain32
  path with an explicit shader. Admission remains limited to shader-off atlas
  terrain; rich vertex/source/Iris/DH ABI is untouched. Release conformance and
  clean runtime captures pass; source-distinct A/B noise receives no speedup credit.
- Enabled Shaderc `Performance` optimization for release SPIR-V while keeping
  debug builds unoptimized for diagnostics; validation is clean and the small
  measured difference remains uncredited release hygiene.
- Changed terrain draw dynamic-offset storage to inline `SmallVec` capacity for
  the common zero-to-three-offset case. Conversion to existing GAL command
  vectors remains at the submission boundary, preserving command semantics and
  resource layouts; focused tests and release-path captures remain clean.
- Grouped compatible ordinary GUI batches before opening the frame pass. The
  grouping is limited by each binding's uniform-buffer identity, preserving
  ordered GUI semantics and offset-zero upload correctness. The two new
  regression tests cover both the grouped and repeated-buffer cases; clean
  Vulkan captures show 23 passes with no change to terrain draw grouping.
- Replayed acknowledged static opaque/cutout terrain records in one producer-lock
  transaction. The normal immutable records, FFI validation, and translucent
  sort remain in place. Two enabled and two same-binary disabled controls reduced
  the opaque phase from about 0.065 to 0.035 ms; follow-up moving runs replayed
  436 records at 4.482 ms/frame with clean validation. This is a CPU bookkeeping reduction,
  not a GPU parity claim.
- Lowered consecutive barriers for distinct buffer/texture handles through one Vulkan dependency call. Repeated handles and all texture-view barriers stay on the original path; A/B backend recording remained about 0.49–0.51 ms, so no end-to-end speedup is credited. Vulkan lowering also converts common dynamic descriptor offsets in stack-backed storage; two same-fixture repeats reduced native medians from 1.778 ms to 1.727 and 1.746 ms with unchanged 98 barriers, 57 descriptor binds, and 42 indexed draws. Barrier-group temporaries now use stack-backed `SmallVec` storage for the common one-to-four-record case; a paired gameplay probe kept the change because backend encode/recording moved from 0.326/0.304 ms to 0.311/0.298 ms, but the workload varied by four entities so no end-to-end speedup credit is assigned.
- Corrected gameplay harness profile selection and made default Current/Frozen performance windows identical; the older 3.896/1.812 ms row remains historical because its terrain readiness was not queue-gated.
- Gated capture-only whole-frame execution receipt construction during measured benchmark frames using the existing submitted-work identity contract. The same settled profile reduced receipt allocation from 50.6 KB to 2.8 KB per frame and total render-thread allocation from about 544 KB to 496 KB; semantic records, native submission, metrics, target auditing, Iris/DH boundaries, and explicit deterministic captures remain unchanged.
- Suppressed two deterministic sky file receipts during measured frames. They remain available during warm-up and explicit captures; this is diagnostic I/O hygiene with no claimed frame-time or allocation credit.
- Scoped the known glass-pane hand overlay workaround to Rust first-person capture only; Java/OpenGL hand, hotbar, and inventory item ownership remain unchanged, with the focused ownership suite passing.
- Made settled and moving performance admission wait for the source-owned terrain queue, applied the moving camera before readiness, rejected post-frame queue churn, and extended moving warmup to 120 frames. Fresh gated pairs are 5.208/4.737 ms settled and 5.924/3.140 ms moving.
- Repaired the paired inventory capture harness: both backends now use the ordinary semantic inventory fixture, and the acknowledged Current/Frozen images are comparable. The fresh pair passed visual parity (mean RGB error below 0.6) with `inventoryOpen=true`; the prior XTest timing path was removed.
- Hardened the resource-reload boundary: transient GUI item semantic collection now defers while the old atlas is staging, so stale `TextureAtlasSprite` payloads cannot cross generations or become fatal unsupported items. A fresh paired reload witness completed on both backends with `futureComplete=true`, `complete=true`, and two post-reload presentations.
- Repaired the dedicated DH parity fixture so Frozen receives the same bounded radius, fade, and fog inputs as Current. The fresh full-attachment DH witness passed semantic and visual parity, proved 17,626 far-world pixels against the private-color/depth boundary, recorded 3,275 submitted LOD instances, and remained crash/device-loss/Vulkan-validation clean. The selected glass-pane hand was removed only from this paired fixture.
- Fixed the F3 crosshair's unseeded Rust line stream and dense-world DH admission: the 512-column and 256-section caps rejected valid city frames. The Rust column bound now matches the 16,384 visible contract, the shared section bound is 4,096, and ordinary DH snapshots and packed uploads are released after admission/submission. The release city client now loads and renders beyond the prior crash points.
- Profiled `Origin Prime City 3` at 854x480. Hash-indexed GAL hazard membership reduced validation from about 687 ms to 15 ms on matched 100k-operation frames. Page-addressed sorted translucent terrain preserved camera order and split indirect runs at the backend's 4,096-draw limit; at the settled 43.7k-batch scene, indexed commands fell from about 44.2k to 1.7k and native frontend time from about 115 to 37 ms. GPU time stayed roughly 76-83 ms, so this is a CPU win only.
- The gameplay benchmark now refreshes the input timer while active, so long unattended settling cannot invoke the 30 FPS AFK cap. The valid release Current baseline on the copied `Origin` world was 29.858 ms mean (median 26.838, p95 50.411) with 120 measured frames and 473 chunks. Ordinary reduced-color immutable DH vertex/index streams now stage through transfer buffers into device-local GPU memory; temporary buffers retire after accepted submission, with generation-bound cleanup. The same fixed-camera workload measured 16.519 ms mean (median 15.556, p95 22.544), a 44.7% Current improvement and 1.91x Frozen's 8.668 ms mean. Broader scene gates remain open. A validated real DH capture submitted ten opaque LOD instances; at the identical camera the DH-off control submitted zero and lost the far ridge above the water.
- With the city's original HIGH DH quality and saved camera restored, old/new pipeline counts are 68,717,754/68,711,820 opaque DH input vertices and about 3.39M/3.38M fragment invocations. Over the last 120 completed GPU frames, device-local residency reduced DH opaque median 60.467 to 2.456 ms and whole-GPU median 76.829 to 20.694 ms; this is a diagnostic GPU comparison, not a wall-clock benchmark. A separate open-view city benchmark selected 134 DH columns, completed 120 frames crash-free at 38.368 ms mean, and remains CPU-heavy (native world frontend about 8.3 ms median, Java world text 5 ms, block-entity traversal 3.5 ms). Frozen cannot read this save's LOD format 4, so city is not a paired goal-gate ratio.
- City profiling exposed a per-glyph defensive font-atlas pixel copy during world-text extraction. Copying only when an atlas identity/generation first enters a collection reduced that phase's median from 4.954 to 0.382 ms and render-thread allocation from 65.5 to 20.2 MiB/frame on matched 193-callback/181-quad city windows; full-frame mean moved from 38.368 to 34.828 ms with one fewer DH column in the latter run. The focused Java test and Current Vulkan-validation text capture pass. Frozen lacks this newer text fixture, so its empty screenshot is rejected as parity evidence.

## Retained parity and correctness evidence

- Fresh current-build DH on/off fixed-camera proof on isolated `Origin` copies at x150.5/y100/z530.5, yaw105/pitch10, RD10/radius64 has same-frame on screenshot and execution receipt: 163 visible columns, 231 opaque/222 transparent/163 water segments, 616 executed instances. The off control has zero visible columns and instances; 103,544 of 194,400 far-view pixels differ by >30 RGB levels and the far islands disappear. Both captures completed cleanly with zero concrete VUIDs. Compact screenshots and receipts are in `mattmc-goal25-current-evidence`; the moving pair has no screenshot proof, so this static visual control is kept separately.
- The city cutout mismatch was 294 invisible `minecraft:light` blocks, each emitted as one zero-area native cutout quad. Light states still remain in the section snapshot for neighboring lighting, but native state admission and replay no longer label them as renderable light-block geometry. The release Current city capture completed with no reported VUIDs; its 476 covered cutout sections match Frozen individually in vertex/index/primitive counts, and both total 488,484 cutout indices. Java compilation passed. Compact cutout receipts are retained; oversized raw captures were pruned.
- The Rust-owned forward reduced-color DH uniform arena now packs aligned 240-byte uniforms into shared bounded uploads by default (`MATTMC_RUST_DH_PACKED_UNIFORMS=0` disables it for diagnostics); exact-atlas and source/Iris layouts remain separate. A 30-frame moving A/B cut median host writes 938→40 and barriers 1,902→102, and GPU time before the first DH draw 2.564→0.080 ms. Completed GPU frame medians were 6.800→4.376 ms, but visible DH columns were 234/213 and the packed run built 15 new columns versus none in the control, so no matched FPS win is credited. A 30-frame packed run with Vulkan validation at 234 columns had zero crash, device loss, or VUID. The full Rust suite passed (1,828, 2 ignored) after the direct-packing change.

## Completion gate

- A real DH publication race caused `world LOD GPU upload references unknown column` before a moving world stabilized: a retired column could re-enter a frame while its retirement still awaited acknowledgment. The collector now excludes that state and clears matching frame instances when retirement is acknowledged. Focused Java regression and the full Rust suite passed; two post-fix real-world runs completed without the crash, one with Vulkan validation. A separate clean-gameplay audit found the harness was forcing `semanticCapture=true` in performance runs, changing ordinary DH asset work. It now confines that property to captures. The Current launcher also disabled DH fog while Frozen kept it; the matched gameplay fixture now retains fog on both sides, and the matrix normalizes only the deliberate DH draw-ownership difference.
- The reduced-color DH direct packer preserves the original GPU bytes across both index widths and all tested micro-offset/color bytes; provenance-backed exact-atlas columns retain the original expansion. In ordinary moving runs, one-column inner admission fell from 3.211 to 0.755 ms and native asset-update median from 3.600 to 1.500 ms despite larger candidate payloads. A 30-frame real-world run passed standard Vulkan validation with no crash, device loss, or concrete VUID. The latest accepted 120-frame moving-DH Current/Frozen pair is 10.051/3.033 ms median (3.31x); Current built 59 columns during measurement, so no settled-frame speedup is claimed. Current native median was 5.134 ms (1.975 world frontend, 2.686 GAL submit) and GPU median 4.117 ms. Earlier static runs with 180 or 600 fixed settle frames still built 32 DH columns during measurement and are rejected as quiescent evidence. The settled-static DH benchmark now requires stable semantic publication and restarts if a column appears during measurement. At radius64 it restarted three times, then completed a 120-frame Current/Frozen pair with zero new columns, 200 visible columns, and 765 executed instances: 5.994/2.505 ms median (2.39x), Current GPU 4.547 ms including 2.404 ms terrain cutout. A whole-frame mesh batch-cache probe cut internal batch time to 0.06 ms, but a same-work radius16 run remained about 4.1 ms/frame, so the probe was reverted. A separate restored-renderer pass-statistics run saw about 2.84M cutout fragment invocations from 1.11M input vertices; its timing is diagnostic because the visible workload differed. A restored-renderer moving reprofile at 213 visible DH columns and 55 new columns found 9.485 ms frame interval, 4.867 ms native work (1.895 world frontend, 2.467 GAL submit), 3.967 ms GPU, and only 0.107 ms FFI present; there is no large presentation wait in this sample. Goal 2.5 remains open. Compact evidence is in `mattmc-goal25-current-evidence`; raw runs were pruned. The direct DH pass now omits repeated pipeline/lightmap binds at emission while retaining each segment's geometry bind and draw. A 120-frame moving run emitted 3,474 operations before normalization versus 4,547 in the prior control, but its 243 visible columns/959 instances versus 211/791 make the similar 9.577/9.579 ms frame medians incomparable as a speedup. The focused ordering test and full Rust suite pass (1,829 passed, 2 ignored); a 30-frame real-world standard Vulkan run completed with zero crash, device loss, or VUID. Next: reduce per-segment DH resource and draw overhead through Rust-owned page addressing, then remeasure matched moving and settled pairs; static cutout remains a separate GPU cost.
- Coarse DH column residency now packs index ranges into one Rust-owned device buffer and one upload buffer per column, preserving U16/U32 alignment and source-path draw offsets. The full Rust suite passed (1,830, 2 ignored), a 30-frame real-world validation run had zero VUID/crash/device loss, and a moving run built 50 columns with an 8.933 ms median at 203 visible columns; its workload differs from the prior control, so no FPS gain is credited. Both extended radius32 Current/Frozen screenshots completed, but visual parity failed (mean RGB deltas 3.058/4.836/6.598; DH full-attachment witness missing); the large gray Frozen-only region is an unresolved baseline mismatch, not an accepted behavior change. Compact images and receipts are retained. Goal 2.5 remains open; next work must address steady draw/GPU cost as well as publication cost. A same-binary depth-bucket A/B at exactly 163 DH columns and 616 instances slightly regressed (4.427 versus 4.323 ms); it was reverted. A vertex-fog probe was also reverted because its runs had 200 versus 163 visible columns. The settled benchmark now requires unchanged visible DH column/segment counts throughout quiescence and measured frames, and accepts an optional exact visible-column gate for controlled comparisons. These changes prevent population drift from being credited as renderer speed.
- The ordinary 60-frame `Origin` DH pair at fixed yaw0 completed with Current 7.754 ms and Frozen 3.488 ms median (2.22x); Current executed 1,273 DH segments across 263 columns with no measurement restart. Its GPU median was 4.561 ms (1.571 terrain cutout, 1.158 DH opaque), and native median 4.293 ms. The private DH vertex shaders no longer calculate an unused fade/distance varying; fragment coverage still computes the source fade. Focused release shader tests (46/46) and full Rust suite (1,830 passed, 2 ignored) pass; the ordinary pair is crash/device-loss free. This pair used a different camera and DH population from the prior control, so no isolated shader speedup is claimed; it has no same-frame visual proof. Compact receipt retained; raw game copies pruned. Goal 2.5 remains open.
- Shared-column DH vertex residency now uses one device-local vertex buffer and staging upload per column when its combined stream fits backend/f32-exact limits; oversized columns retain segment buffers. Each draw keeps original typed indices and source order, with its private base vertex carried by an existing reserved uniform lane in direct and lowered-source shaders. Focused residency/fallback and shader suites pass. A real radius32 Current/Frozen capture completed; the Current image differs from its prechange control by only 0.234 average RGB levels, with no new large geometry defect. The paired visual gate still fails on the existing Frozen-only gray region/full-attachment witness (new mean RGB deltas 2.964/4.620/6.303), so visual parity is not claimed. A 120-frame moving `Origin` pair at yaw105/pitch10 was Current 8.071 ms versus Frozen 4.563 ms median (1.77x) and completed crash/device-loss free; Current ended at 243 DH columns/982 segments, with 3.994 ms GPU and 4.498 ms native medians. This scene passes 2x, but differs from the older moving control and cannot isolate the vertex change's speedup. Compact receipts/images kept, raw game copies pruned. Other scenes and the visual gate remain open. The dense-city audit found and fixed a harness error: non-`Origin` vanilla runs now explicitly disable the Current DH route and reject any reported DH execution. A corrected 120-frame, 473-chunk, 1280x720 city pair had zero DH draws: Current 11.388 ms versus Frozen 2.125 ms mean (5.36x), with Current GPU median 6.582 ms. An isolated Frozen visibility probe found the same 709 selected geometry sections as Current, so excess section selection is not the cause. The Rust mesh geometry arena had kept Vulkan vertex pages in host Upload memory; a bounded staging-to-device-local page copy now preserves reusable geometry and completion-gated retirement. On the same 60-frame city workload, opaque GPU median fell 4.595→0.690 ms, cutout 1.692→0.309 ms, and GPU frame 6.657→1.574 ms. Frame mean moved only 12.124→11.016 ms; CPU production is now the measured blocker. A fresh RD10 city profile with identical 4,672,836/488,484/571,296 terrain indices shows 10.235 ms frame median, 4.393 ms Java semantic extraction (3.422 ms indexed-mesh enqueue), 4.719 ms coordinator, 2.150 ms Rust world frontend (0.908 ms mesh group expansion), and 1.380 ms GPU frame. A temporary per-family probe found 34 banners account for 0.719 ms median of the 1.29 ms model block-entity traversal; signs, beds, chests, and skulls each cost 0.13–0.17 ms. The probe was removed after retaining a compact receipt. A second temporary probe found 308 generic model submits per city frame, most from banner layers: generic Rust enqueue totals about 0.58 ms/frame and Java pose extraction 0.23 ms/frame. A banner-only shortcut cannot close the gap; prioritize shared model submission and world batching while preserving authored material order. Both temporary probes were removed. A projection grouping probe made no GPU improvement and was reverted. The final release Rust suite passed 1,832 tests (2 ignored), including bounded copy-proof and equipment tests. A Current-only 30-frame moving `Origin` DH gameplay run completed crash/device-loss free at 238 visible columns and 1,158 executed instances (8.151 ms median); it has no matched Frozen or visual claim. The default 180-frame city screenshot gate expired while terrain was still building; the harness had silently overwritten an explicit settle-frame override, now fixed. With a 2,400-frame allowance, the Rust terrain queue drained and the capture settled after 360 frames at 709 geometry sections. Matched 1280x720 city Current/Frozen screenshots completed crash-free and differ by 0.527/0.556/0.570 mean RGB levels. The Current screenshot also completed under routine Vulkan validation with zero concrete VUIDs and no device loss. Compact receipts are retained and raw game copies pruned. Goal 2.5 remains open.

- Rust-owned camera-sorted translucent terrain now keeps one bounded direct-path batch/index template with its immutable mesh generation and exact camera position. It waits for a stable camera before building the template; moving positions use the original path, and source/Iris G-buffer batches are excluded. An RD10 city off–on–off same-binary 60-frame comparison kept 4,672,836/488,484/571,296 terrain indices and zero DH draws: mesh expand/group median 0.871→0.412→0.856 ms, world frontend 2.065→1.603→2.086 ms, and frame median 9.810→9.258→10.059 ms. A routine Vulkan city capture completed with zero concrete VUIDs; its image differs from the prior Current view by less than 0.001 mean RGB per channel and from Frozen by 0.527/0.556/0.570. The full release Rust suite passed (1,832, 2 ignored). A Current-only moving `Origin` DH smoke completed crash/device-loss free at 238 visible columns and 1,158 executed instances; no matched DH speedup is credited. Compact evidence retained, raw runs pruned. Goal 2.5 remains open because city frame time is still above the 2x Frozen budget and broad DH parity remains unproven.

- A game-process JFR city profile identified repeated model rollback-set copies and boxed visible block-entity positions. Membership-aware immutable checkpoint views and primitive position deduplication pass Java compilation and the focused rollback suite (6/6); a Current-only `Origin` smoke completed 60 measured frames at 4.039 ms median. A respawn exposed retained static-terrain replay without a seeded camera; replay now waits for the next camera while preserving residency and the invariant on newly submitted terrain. The boat's textureless `WATER_MASK` model also exposed invalid generic textured-model admission. Its source position-only, depth-write/no-color-write contract now has an explicit Rust semantic material and backend depth-mask state; only this material normalizes its unused zero-atlas UVs. City gameplay and a standard Vulkan run complete without the former boat crash or concrete VUIDs. A fresh exact-camera, RD10, 60-frame city pair is Current 8.553 ms versus Frozen 3.585 ms (2.39x), with the same 4,672,836/488,484/571,296 Current terrain indices as the prior city profile. Current semantic extraction is 3.677 ms, indexed-mesh enqueue 2.809 ms, Rust world frontend 1.586 ms, and GPU 1.328 ms median. The full release Rust suite passes (1,833, 2 ignored). No isolated speedup is credited without a same-binary A/B; the 2x target and boat-local visual comparison remain open. Compact evidence retained; raw probes pruned.

Goal 2.5 is complete only when fresh settled and moving comparisons meet the
2x target, GPU timestamps correspond to completed presentation submissions, and
vanilla terrain, DH continuity, inventory, reload, and device-loss checks remain clean. This file stays under 200 lines and records current evidence only.

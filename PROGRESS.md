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
Rust, matched settle/warmup/measurement windows, and ordinary-DH isolation. The
benchmark honors explicit settled-static and moving-camera selectors, waits for
the Rust source-owned terrain queue to quiesce, and uses the same bounded sample
window and moving-path warmup for Current and Frozen.

| workload | Current Rust Vulkan | Frozen OpenGL | ratio |
| --- | ---: | ---: | ---: |
| latest settled-static (60 frames) | 5.208 ms | 4.737 ms | 1.10x |
| latest moving-camera (60 frames) | 5.924 ms | 3.140 ms | 1.89x |

These are bounded fixture results, not evidence of broad 2x parity. A real `Origin Prime City 3` launch exposed a 1–2 FPS path and a DH admission crash after 515 columns; Goal 2.5 remains open. The moving fixture discarded one partial window and measured 60 frames with zero completed terrain builds; an earlier 2.17x comparison used an under-populated Frozen path and is rejected.

The settled pair held 513 mesh batches and a visible-cache hit; moving held about 542 batches with no completed-build churn. In the moving Current sample, semantic world extraction was 2.47 ms, native submit-return 2.42 ms, FFI decode 0.10 ms, Vulkan queue submit 0.015 ms, and GPU frame total 2.93 ms (terrain cutout 1.72 ms). The measured gap is Java semantic production, command preparation, and terrain fragment work; FFI is not the limiting boundary, and no evidence supports weakening Rust/Vulkan ownership or safety.

The queue-gated benchmark change is measurement-only: moving-path warmup is 120 frames, Current rejects any measured frame that leaves the source queue non-drained, and explicit JVM overrides remain supported. This prevents asynchronous section ingestion from being reported as steady-state performance.

Captured game directories are pruned after extraction; compact matched-control,
terrain-isolation, and barrier A/B evidence remains under
`logs/graphics-audit/gameplay/20260921-2330-profile-barrier`.

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
- Completed-pass GPU timestamps put about 60 ms of the roughly 76 ms city frame in DH opaque. Pipeline statistics show about 68.7M input vertices, 45.8M vertex invocations, and 3.4M fragment invocations; FFI and Vulkan safety checks are not the cause. Frozen rejects the city cache's LOD format 4, so the city is invalid for paired comparison. On a copied, format-2-only `Origin` save with matching DH config and camera, both reach 140 visible sections; Current submits 13.40M DH opaque indices and Frozen 17.63M. An earlier 2.9M-index Frozen sample was still loading and is rejected. At 854x480/473 chunks, Frozen's fixed-camera mean was 8.668 ms; Current release with the isolated benchmark copy's AFK cap disabled and 120 stable terrain-drain frames measured 27.773 ms (3.20x, p95 50.255 ms). The earlier 34.008 ms Current row was confounded by Minecraft's 30 FPS AFK cap after 60 seconds; direct Gradle's default debug-Rust run falsely showed 0.5–0.75 s DH asset updates. Release preparation was roughly 2–19 ms with sub-0.1 ms reconciliation. Goal parity remains open.
- The gameplay benchmark now refreshes the input timer while active, so long unattended settling cannot invoke the 30 FPS AFK cap. The valid release Current baseline on the copied `Origin` world was 29.858 ms mean (median 26.838, p95 50.411) with 120 measured frames and 473 chunks. Ordinary reduced-color immutable DH vertex/index streams now stage through transfer buffers into device-local GPU memory; temporary buffers retire after accepted submission, with generation-bound cleanup. The same fixed-camera workload measured 16.519 ms mean (median 15.556, p95 22.544), a 44.7% Current improvement and 1.91x Frozen's 8.668 ms mean. Broader scene gates remain open. A validated real DH capture submitted ten opaque LOD instances; at the identical camera the DH-off control submitted zero and lost the far ridge above the water.
- The old 16.736/8.806 ms `Origin` moving ratio was capped at 120 FPS and is rejected. A fresh uncapped 180-frame 1-degree/turn pair was Current 17.442 ms versus Frozen 3.694 ms mean (4.72x), with Current executing 142 visible DH columns. Frozen lacks per-frame LOD receipts, so exact paired workload parity remains provisional. Profiling found roughly 18,700 material quads/frame, including 16,434 faces expanded from compact DH generic boxes; a diagnostic-only box omission lowered 60-frame Current mean 18.839 to 12.859 ms. The retained Rust path now keeps each validated box compact, groups it by existing material/SSAO phase, and derives all six shaded faces in a Rust-owned vertex shader. The later 180-frame Current moving run completed at 10.670 ms mean/10.456 median with 146 visible DH columns, 211 opaque/283 transparent/146 water segments, and comparable vanilla terrain counts: about 39% lower Current mean but still 2.89x the earlier Frozen mean. Native world frontend fell 6.678 to 3.022 ms and validation 2.058 to 0.125 ms. A same-camera DH-on screenshot retained far islands absent in the DH-off control; compact versus legacy far-view pixels differed by at most 3 RGB levels in the 102,200-pixel ridge region. The corrected positive DH generic fixture, placed beyond the private near clip, rendered the same 10,745 magenta pixels and bounds in compact and legacy captures, with zero >3 RGB differences in its 200x170 region. Both captures and the long moving run were crash-free; the compact capture had zero VUIDs, and the full Rust suite passed (1,824, 2 ignored). The temporary legacy switch and near-clipped failed fixture captures were removed. The moving load also exposed tinted stonecutter quads without a `BlockColors` provider; native admission uses vanilla constant-white semantics for that class, with focused Java coverage.
- With the city's original HIGH DH quality and saved camera restored, old/new pipeline counts are 68,717,754/68,711,820 opaque DH input vertices and about 3.39M/3.38M fragment invocations. Over the last 120 completed GPU frames, device-local residency reduced DH opaque median 60.467 to 2.456 ms and whole-GPU median 76.829 to 20.694 ms; this is a diagnostic GPU comparison, not a wall-clock benchmark. A separate open-view city benchmark selected 134 DH columns, completed 120 frames crash-free at 38.368 ms mean, and remains CPU-heavy (native world frontend about 8.3 ms median, Java world text 5 ms, block-entity traversal 3.5 ms). Frozen cannot read this save's LOD format 4, so city is not a paired goal-gate ratio.
- City profiling exposed a per-glyph defensive font-atlas pixel copy during world-text extraction. Copying only when an atlas identity/generation first enters a collection reduced that phase's median from 4.954 to 0.382 ms and render-thread allocation from 65.5 to 20.2 MiB/frame on matched 193-callback/181-quad city windows; full-frame mean moved from 38.368 to 34.828 ms with one fewer DH column in the latter run. The focused Java test and Current Vulkan-validation text capture pass. Frozen lacks this newer text fixture, so its empty screenshot is rejected as parity evidence.
- Dense vanilla-only `Origin Prime City 3` at 854x480/473 chunks remains far outside parity. A reset-save static pair was Current 14.420 ms versus Frozen 2.562 ms mean (5.63x). Sodium-style directional face selection keeps immutable sections intact while filtering ordinary camera draw ranges; on a fixed camera, opaque input fell from 3.56M to 1.95M vertices and opaque GPU pass median from 6.02 to 3.02 ms. A queue-gated 1-degree/frame turn completed 180 frames after 659 drained frames and zero readiness restarts: Current was 26.567 ms versus Frozen 2.452 ms mean before a checkpoint fix. Repeated full world-asset key snapshots in model rollback checkpoints cost about 4.3 ms/frame; sharing immutable key snapshots until registry mutation cut block-entity traversal from 5.95 to 2.10 ms/frame and the moving mean to 21.407 ms (8.73x Frozen), with matched median terrain indices and mesh instances. The terrain face mask is now part of the Rust batch-plan cache key, preventing stale directional draws after camera rotation; a focused test covers this. Stack-backed per-batch dynamic offsets avoid temporary heap vectors but show no robust frame-time gain. These city tests disabled DH. A fresh daylight `Origin` DH-on Vulkan-validation capture on the latest build shows far islands/coast beyond the vanilla shoreline, with 244 visible DH columns, 399 opaque segments, 1,020 executed instances, and zero reported VUIDs. An exact-copy, same-camera control with DH `rendererMode=DISABLED` shows zero executed instances and the far islands/coast disappear; 68,021 of 102,200 pixels in a far-view region differ by >30 RGB levels. Both runs completed cleanly. A real-save LOD-only capture with radius 128 also executed 1,020 DH instances across 244 visible columns (near clip 0.057 blocks); its screenshot and compact receipt are retained as `mattmc-dh-lod-only-real-proof`. A radius-8 harness attempt captured only 6 columns and was rejected as visual evidence. Disposable game copies and oversized raw receipts were pruned after retaining screenshots and compact receipts. The isolated city save is reset from one snapshot for each new A/B run. A moving-city trace showed whole-frame mesh-plan cache misses as visible terrain identities and face masks change. Two draw-record shortcuts were reverted after a full 180-frame repeat left draw-record time at 2.437 versus 2.439 ms mean; earlier 60-frame gains tracked broad Java timing variation and receive no speedup credit. An isolated normal-DH capture executed 1,020 LOD instances across 244 columns with zero VUIDs and a far-view region within one RGB level of the earlier compact-box capture. The source game `lodOnlyMode` was restored to false after an accidental direct diagnostic run; future captures must omit `--game-dir` to copy the world. Goal 2.5 remains open.

Validation: Java compile, Rust check, diff check, focused suites, and full native
suite (1,825 passed, 2 ignored) passed; DH/city captures had zero VUIDs.

## Next bounded work

- City, DH off (1280x720/473 chunks, same save/camera): Current after lazy
  tracing 16.192 ms vs prior GC-free Frozen 3.095 ms (5.23x); GPU 10.031 ms,
  backend encode 0.604 ms. The prior page-binding change cut draw-record
  2.102 to 1.939 ms; late visual is correct (`mattmc-city-draw-record-shortcut-latest`).
- DH on/off proof: 244/0 columns, 1,020/0 instances; far islands only with DH (`mattmc-dh-latest-proof`).
- Ordinary DH, 1280x720/473 chunks: static Current/Frozen 7.830/3.255 ms
  (2.406x), 244 columns/1,020 instances; moving 11.305/3.134 ms (3.607x),
  254 columns/1,118 instances. Frozen exact LOD counts remain unavailable.
  Trace formatting consumed 2.34 ms/frame diagnostically; same-binary lazy
  trace cut moving 13.707 to 11.606 ms and backend encode 3.993 to 1.738 ms.
  Final validation: 1,103 LOD instances, zero VUIDs (`mattmc-dh-lazy-trace-latest`).
- Thin DH probes are rejected by the visibility gate. Moving still records
  2,317 GAL barriers/frame; packed DH uniforms and GPU cost remain targets.

## Completion gate

Goal 2.5 is complete only when fresh settled and moving comparisons meet the
2x target, GPU timestamps correspond to completed presentation submissions, and
vanilla terrain, DH continuity, inventory, reload, and device-loss checks remain
clean. This file stays under 200 lines and records current evidence only.

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

A repeated opaque early-fragment-tests shader A/B (4.065 ms then 5.020 ms versus 4.229 ms controls) was run and rejected as noise; no shader semantic change remains from that experiment. The static DirectTerrain32 vertex branch for per-instance light is now removed under an explicit terrain admission invariant; reverse-order A/Bs were 3.434 versus 3.476 ms, so no speedup credit is assigned. Whole-mesh animation classification now stays per section, so a static section beside animated water can use the static fragment identity; matched 868-batch A/Bs were 8.585 versus 8.442 ms with indistinguishable GPU totals, so no speedup credit is assigned. A translation-only vertex A/B moved in opposite directions across reverse order and was reverted. A 113-mesh material trace found cutout bits 0/2/5 and six mixed meshes, so no global mip/cutoff specialization is safe without repartitioning draws. A bounded indexed-section variant was measured and rejected: it raised an 837-layer fixture from 858 to 1,196 world draws and from 8.26 to 8.79 ms GPU; no variant code remains.

Captured game directories are pruned after extraction; compact matched-control,
terrain-isolation, and barrier A/B evidence remains under
`logs/graphics-audit/gameplay/20260921-2330-profile-barrier`.

GUI pass-grouping repeats and routine Vulkan validation completed cleanly; large capture trees were pruned after the results were recorded.

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
- Fixed the F3 crosshair's unseeded Rust line stream. Dense-world profiling found 11.8–14.6 GiB RSS, about 1–2 FPS, and a reproducible crash at 515 DH columns because Rust allowed only 512 while the visible contract permits 16,384. Ordinary DH columns now release Java raw snapshots after admission, skip unused material provenance, retain one Rust packed upload copy before residency, and release it after successful GPU submission; retired generations use the authoritative packed-asset map. Rust's column bound now matches the visible contract. Dense sorted translucent sections may contain more than 256 material runs, so the shared Java/Rust section bound is 4,096. Release quick-play `Origin Prime City 3` passed the old crash point and reached frame 416 without a world-frame crash or translucent section rejection. The first complete profile found the principal frame bottleneck in GAL hazard analysis: a linear duplicate-read scan over a roughly 100,000-operation submission. Hash-indexed read membership preserves deterministic access order and all conflict checks; matched city frames at about 100,670 operations reduced median hazard validation from 687 to 15 ms and GAL submission from 724 to 52 ms. At about 133,000 operations, hazard validation is near 19 ms, but total frontend time remains about 210 ms/frame, graph construction about 129 ms, and RSS about 13 GiB. Full goal parity remains unresolved.

Validation: `./gradlew compileJava`, `cargo check --manifest-path src/main/rust/Cargo.toml`,
`git diff --check`, the native Rust suite, and focused terrain/hand/inventory suites pass.
The full native suite is 1,819 passed, 2 ignored; deep Vulkan smoke `20260922-090931`
completed 60 frames with zero concrete VUIDs. Fresh vanilla `20260922-100417` and DH
`20260922-100034` witnesses completed on Current and Frozen; no speedup is credited.

## Next bounded work

1. Keep the measured CPU/GPU boundaries explicit; do not optimize FFI or merge
   Iris/DH source/material passes without a new profile showing a real cost.
2. Finish the dense saved-world repeat, profile its remaining native memory and
   frame time, then rerun matching Frozen/Current city and queue-gated fixtures.
3. Keep only result-bearing diagnostics and prune one-off traces.

## Completion gate

Goal 2.5 is complete only when fresh settled and moving comparisons meet the
2x target, GPU timestamps correspond to completed presentation submissions, and
vanilla terrain, DH continuity, inventory, reload, and device-loss checks remain
clean. This file stays under 200 lines and records current evidence only.

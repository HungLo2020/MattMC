# Rendering migration handoff

Updated: 2026-09-19
This is a working handoff and must remain at or below 200 lines.

## Active objective

**Goal 3 — stabilize and optimize shader-off Rust Vulkan terrain.** Vanilla
terrain and Distant Horizons terrain must remain visible and stable during
normal RunDev gameplay. Improve CPU/GPU frame cost without weakening the
Rust-owned VulkanicGAL boundary or coupling the direct path to future Iris
shader integration.

The reported missing-vanilla regression is fixed in current visual controls,
but remains a completion gate across movement and lifecycle coverage.

## Architectural constraints

- Java may copy immutable CPU semantics. Rust owns Vulkan resources, pipelines,
  synchronization, render passes, and presentation.
- No Sodium, DH, or Iris renderer object, GPU handle, command state, or GL state
  may cross the VulkanicGAL boundary.
- Frozen Java OpenGL is the visual and lifecycle correctness baseline.
- Shader-off direct rendering and future Iris/deferred source rendering retain
  explicit separate policies where their pass/depth contracts differ.
- Visibility, mesh replacement, and asset publication must be atomic from the
  presented frame's perspective. A new build must not erase the last valid
  visible result while replacement data is incomplete.

## Accepted prior baseline

- r485 literal RunDev rendered near vanilla terrain plus far DH for 1,657 DH
  frames. Twenty-four stable captures had no terrain holes or fatal errors.
- r486 disabled the DH renderer at the same camera/radius and removed the far
  field, proving that r485's extension was actual DH LOD terrain.
- Resource reload, resize, world reload, revisit, and soak lifecycles passed
  without Java Vulkan execution, fallback presentation, or borrowed GPU state.
- All 1,800 runnable Rust tests (2 ignored) and 468 focused Java tests passed at
  the end of Goal 2.

## Performance baseline

- The older render-distance-10 comparison remains Frozen 1.87 ms/frame,
  Rust Vulkan vanilla 15.67 ms, and vanilla plus DH 22.20 ms. Do not compare it
  numerically with the current render-distance-4 optimization fixture.
- Current exact steady-world runs are 5.78–8.64 ms/frame versus Frozen OpenGL
  0.70–0.78 ms/frame. Packed GUI/TACZ staging and immutable atlas animation
  keys reduced the 60-frame allocation window from 245.5 MB to 69–70 MB,
  about 72%. The large same-workload gap remains open.
- Completed GPU timestamps put the representative total at 2.69 ms: opaque
  terrain 1.37 ms and cutout terrain 1.05 ms. CPU present/update-display is
  about 0.07 ms; the old 14–16 ms presentation block is not the current limit.

## Current investigation

- The independent terrain source retains immutable section meshes across dirty
  rebuilds, which is the correct ownership/lifetime model.
- Vanilla visibility publication now retains the last presented resident domain
  while a replacement portal graph is incomplete. Section-boundary movement
  fully traverses the already resident graph in the same frame instead of
  reconciling Rust against one breadth-first wave.
- DH had a second atomicity bug: its quadtree considered a CPU-built child LOD
  renderable before Rust acknowledged the bounded asset upload, disabled the
  covering parent, and presented holes. Child readiness now requests native
  publication and becomes renderable only after acknowledgement; an older
  acknowledged generation remains valid during replacement.
- JFR found `GuiRawImageAssetRecord.pixels()` defensive copies responsible for
  34.8% of sampled startup/loading allocation. Internal equality and byte-size
  checks now inspect the record's already immutable private bytes; construction
  and public access still copy. The identical steady benchmark showed no frame
  allocation improvement, so this is a loading/GUI staging fix only.
- Queue diagnostics now maintain the retained-geometry count incrementally and
  only construct/publish the long progress summary when diagnostics or capture
  explicitly request it.
- Standard validation exposed the new terrain indirect batches using draw counts
  above one without enabling Vulkan's core `multiDrawIndirect` feature. Device
  creation now negotiates and enables it when supported; lowering preserves the
  same GAL command with allocation-free single-record draws on other hardware.
- The empty inventory benchmark is a heavy mixed workload. Packed GUI/TACZ
  staging reduced its median from 10.80 to 8.12 ms (25%) and render-thread
  allocation from 9.15 to 2.35 MB/frame (74%). Primitive atlas-reference
  retention then reduced allocation another 2.8% to 2.24 MB/frame; timing was
  noisy at 8.53 ms with GPU time also higher. The real `InventoryScreen` was
  active for all 60 measured frames.
- A live steady-world JFR (r512) attributed about 75% of sampled render-thread
  allocation to entity interpolation collision work in the 102-entity fixture.
  Disabled terrain diagnostics no longer build per-instance events each frame;
  r513 reduced allocation 4.67→4.46 MB/frame. r518 then removed the unconditional
  whole semantic-frame clone from direct vanilla/DH graph construction while
  retaining exact snapshots for Iris/source admission, Fabulous, and capture.
  Against r514, median frame time improved 6.323→6.014 ms (4.9%) and native world
  frontend median reached 1.726 ms; command recording fell 0.508→0.499 ms.
- The earlier DH generic-pass crash came from a submission path that lacked
  explicit pre/post-SSAO separation. The current Rust frontend partitions those
  draws around the private SSAO pass; the exact combined r516 run and literal
  release quick-play run complete without that exception.
- Terrain replacement had two coupled generation holes. A visible replacement
  was initially withheld until upload, then the first fix queued it but replaced
  Java's active instance and dependency record before Rust accepted it. Under a
  radius-10 stream this could withdraw many old generations at once. Registered,
  acknowledged, pending, and active states are now distinct: the old accepted
  generation and texture dependencies remain drawable until `consumeFrame()`
  observes the accepted replacement and swaps the active instance atomically.
- A release relocation capture reached `replacement-visible` after four wait
  frames and executed 8,192 checked geometry events without a crash or Vulkan
  validation error after the generation-handoff fix.
- JFR attributed 48 MB of a 60-frame sample to regex-based metric formatting
  and 66 MB to boxed TACZ Bedrock staging. Metric values now preserve safe
  strings directly, armor material selection uses typed pipeline identity, and
  TACZ uses bounded primitive staging. Allocation fell about 30%; remaining
  GUI mesh-record construction is the next architectural packing target.
- An enchanted armored zombie exposed a stale development-property guard even
  though the native armor-glint contract was complete. Ordinary armor glint is
  now admitted by semantic validation without that runtime property.
- GUI mesh transport now recognizes immutable primitive-backed list views and
  encodes them directly without expanding each vertex or index into Java
  records. TACZ GUI and Bedrock capture use reusable bounded CPU staging whose
  completed semantics are still copied before reuse.
- Rust-owned atlas animation no longer rescans the immutable Java sprite map
  each frame to derive a key; reload generations still invalidate it, while
  animation ticks remain owned by the Rust atlas resource.
- Swapchain acquisition now signals a bounded binary semaphore consumed by the
  presentation submission instead of creating and CPU-waiting a fence every
  frame. Timeline completion gates normal reuse; canceled signaled semaphores
  are destroyed after device quiescence. Median acquire cost fell 0.705→0.054
  ms (92%); the exact validation-off frame median was 5.98 ms and allocation
  was 70.3 MB/60 frames. Standard validation and deterministic capture passed.
- World entry exposed a bounded-upload race: after a semantic frame retained an
  accepted terrain generation, the post-consume asset flush could replace that
  mesh before submission. Post-consume publication now protects every mesh
  generation referenced by the frozen frame and defers conflicting replacement
  or retirement to the next pre-consume transaction.
- Cold world entry exposed a second native transaction race. A larger hand
  batch could grow the shared mesh stream after world commands had captured its
  ResourceSets, destroying those sets before the combined submit validated
  them. Superseded descriptor sets and stream/indirect buffers now retire only
  after submission, with dependent sets destroyed before their buffers.

## Work queue

1. Validate longer cold start, steady view, camera movement, light/chunk invalidation,
   reload, and vanilla-plus-DH composition in the real client.
2. Continue profiling the remaining Java allocation and Rust frontend hot paths.
3. Reduce terrain pass and command-recording cost using the completed GPU and
   native CPU timestamps; retain explicit passes needed by future Iris.
4. Profile the remaining inventory batch-record construction and Rust GUI/world
   frontend cost, then compare the same screen with Frozen OpenGL.

## Current evidence and repository hygiene

- r520 revalidated detailed vanilla foreground plus DH opaque, transparent,
  water, generic objects, and double-pass composition after the frame-clone
  removal. It executed 1,064 DH opaque instances with no terrain failure,
  device loss, or concrete Vulkan VUID.
- The post-acquire full Rust suite passes 1,803 tests with 2 ignored. The
  release steady benchmark and deterministic standard-validation capture had
  no crash, device loss, or concrete Vulkan VUID.
- The generation and stream-lifetime fixes pass focused lifecycle regressions
  and the complete Rust suite: 1,804 passed, 2 ignored. A release `New`-world
  cold start with a visible first-person hand completed 60 measured gameplay
  frames under standard validation with no crash, device loss, or concrete
  VUID. Its separate settled-capture row timed out while the terrain queue was
  still active, but remained crash-free with 1,273 retained vanilla sections.
- Generated captures, build products, profiler output, runtime logs, and Python
  caches are not retained as source evidence. The September cleanup removed
  about 94 GB of reproducible output from the workspace.
- Audit runs now share one repository retention root even when `--artifact-dir`
  names a run. Default quotas retain one successful and one failed run per
  group; `--artifact-preserve-current-run` releases the prior automatic marker.
- Obsolete milestone/source-text suites were removed. Behavioral ABI, Vulkan
  backend, terrain/DH lifecycle, and focused audit-helper coverage remain. The
  cleanup removed 2,615 implementation-mirroring tests and 63,657 source lines;
  1,731 Java `@Test`s, 452 collected Python tests, and the Rust suite remain.

## Completion gate

- Vanilla terrain never disappears or flickers during cold start, movement,
  invalidation, view-distance changes, reload, or settled gameplay.
- DH remains visibly distinct beyond the vanilla radius and shares one Rust
  frame/presenter with vanilla terrain.
- Fresh benchmarks show the effect of each optimization and reach quiescence;
  GPU timestamps are tied to completed presentation submissions.
- Architecture/callsite tests continue to reject Java GPU execution, borrowed
  renderer state, legacy fallback presentation, and accidental Iris coupling.
- PROGRESS.md remains a concise current handoff under 200 lines.

## Repository state

- Branch: `master`; initial Goal 3 investigation began from clean commit
  `be9b7328f` (`distant horizons mostly working`).
- Goal 3 correctness fixes are uncommitted. No commit or push has been made.

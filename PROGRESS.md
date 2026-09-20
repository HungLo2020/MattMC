# Rendering migration handoff

Updated: 2026-09-20
This is a working handoff and must remain at or below 200 lines.

## Active objective

**Goal 3 — paused after release world-load smoke.** Vanilla
terrain and Distant Horizons terrain must remain visible and stable during
normal RunDev gameplay. Improve CPU/GPU frame cost without weakening the
Rust-owned VulkanicGAL boundary or coupling the direct path to future Iris
shader integration.

Ordinary release quick-play enters the saved world and visibly renders vanilla
terrain. Pre-publication runtime atlas ticks rebase the declaration clock instead
of exhausting the live-event FIFO; movement and broader lifecycle remain gates.

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

## Performance baseline

- The current exact fixed-camera release comparison is Rust Vulkan 5.59 ms
  median/14.10 ms p95 versus Frozen OpenGL 4.71/8.01 ms. Rust's completed GPU
  median is 6.10 ms; its remaining gap is dominated by CPU/native tail latency,
  especially submit/encoding, rather than a large steady median deficit.
- The old radius-12 moving baseline is invalid for gameplay comparison: its
  test rotated player body/head state and visibly disturbed held-item and other
  player-relative rendering. Moving results require a fresh camera-only run.

## Current investigation

- The independent terrain source retains immutable section meshes across dirty
  rebuilds, which is the correct ownership/lifetime model.
- Vanilla visibility publication retains the last presented resident domain
  while a replacement portal graph is incomplete. Its empty-domain debounce now
  forces the promised second-frame decision, and a bounded transition ring keeps
  still-visible overhang sections resident across section-boundary movement.
- DH had a second atomicity bug: its quadtree considered a CPU-built child LOD
  renderable before Rust acknowledged the bounded asset upload, disabled the
  covering parent, and presented holes. Child readiness now requests native
  publication and becomes renderable only after acknowledgement; an older
  acknowledged generation remains valid during replacement.
- Queue diagnostics maintain retained geometry incrementally; ordinary frames
  neither construct its summary, clone the portal-visible key set, nor update
  per-layer atomic receipts used only by benchmarks and captures.
- Standard validation exposed the new terrain indirect batches using draw counts
  above one without enabling Vulkan's core `multiDrawIndirect` feature. Device
  creation now negotiates and enables it when supported; lowering preserves the
  same GAL command with allocation-free single-record draws on other hardware.
- Static translucent terrain now preserves static orders; only `DYNAMIC` geometry
  enters Rust's camera sorter. Reusable scratch carries resolved assets, and
  concurrent asset reads reuse a thread-local value probe instead of allocating
  three temporary keys per visible section; publication keys remain immutable.
- The empty inventory benchmark is a heavy mixed workload. Packed GUI/TACZ
  staging reduced its median from 10.80 to 8.12 ms (25%) and render-thread
  allocation from 9.15 to 2.35 MB/frame (74%). Primitive atlas-reference
  retention then reduced allocation another 2.8% to 2.24 MB/frame; timing was
  noisy at 8.53 ms with GPU time also higher. The real `InventoryScreen` was
  active for all 60 measured frames.
- The moving benchmark first rotated the player, injected a camera after setup,
  and rewrote player position, rotation, input, and velocity every render frame.
  It now seeds one interpolation-safe view; a source guard rejects recurring
  player writes. Prior visual/performance results are invalid.
- Programmatic view-distance scenarios now broadcast `ClientInformation` to the
  integrated server. Before this fix the client selected radius 12 while the
  server retained radius 10, leaving 147 impossible terrain sections pending.
  The corrected run loaded 637 chunks, reached `unavailable=0`/`drained=true`,
  and sustained 239 quiescent-cache frames while view fingerprints changed.
- The independent portal source now matches Sodium above and below build height
  by seeding the nearest world boundary plane instead of publishing an empty
  terrain domain. Portal math is batched once per BFS wave through reusable
  buffers, and resident edge admission no longer allocates temporary positions.
- Terrain instances now retain only immutable mesh and section-origin semantics.
  One full-precision camera record is frozen per frame; moving frames reuse the
  same Java instance identities and native staging, patching only three camera
  doubles before synchronous submission. Rust still validates the unchanged ABI
  and exclusively lowers camera-relative transforms and Vulkan resources. Its
  topology-cache lookup also reuses the full mesh-identity staging allocation.
- Quiescent Java OpenGL capture exposed a Rust-OpenGL BlockDisplay crash (invalid stratum 71), so its moving control produced no usable sample.
- Completed worker builds are consumed two per frame instead of in one unbounded
  render-thread burst. Streaming p95 fell 34.88→19.19 ms, completion p95
  13.49→3.72 ms, and allocation 5.14→4.08 MB/frame. Resident portal visibility
  refreshes during outstanding builds while retaining last-published meshes.
- Terrain publication now protects both per-mesh edits and whole-atlas reloads.
  Registered, acknowledged, pending, and active mesh states keep an accepted
  edit drawable until `consumeFrame()` swaps its replacement. Reloads rebuild
  generation-scoped mesh keys off-screen while the old atlas/UV set stays live;
  after every mesh upload is acknowledged, one transaction publishes the new
  atlas, visibility domain, instances, and old-generation retirements.
- Fixed-camera audits synchronize the integrated-server player so packet chunks
  follow the tested camera. Release relocation, view-distance changes, and a
  real boundary edit reached `replacement-visible` in nine wait frames; dirty
  published sections rebuild even outside the instantaneous raw frustum.
- Swapchain acquisition now uses a bounded binary semaphore consumed by the
  presentation submission. Timeline completion gates reuse, and performance
  capture no longer forces each new token to retire: radius-12 median fell
  25.17→22.20 ms and retirement 6.11→0.043 ms at one image in flight.
  Light-state advance is negligible (0.0015 ms median, no work).
- World entry exposed a bounded-upload race: after a semantic frame retained an
  accepted terrain generation, the post-consume asset flush could replace that
  mesh before submission. Post-consume publication now protects every mesh
  generation referenced by the frozen frame and defers conflicting replacement
  or retirement to the next pre-consume transaction.
- The reported whole-frame crash was ResourceSet slot reuse while commands were
  still assembling. A nestable GAL command-recording lifetime keeps ordered
  destroys live through submit or abandonment. Low-level, nested GUI/world, and
  streamed mesh-growth regressions reproduce the old generation-reuse path.
- Corrected profiling now separates the whole native call from the Rust world
  frontend and command-lifetime cleanup. Empty Vulkan descriptor-pool pages are
  retained for reuse, and synchronous glibc `malloc_trim` is reserved for
  backend teardown or explicit diagnostics. Cleanup fell 2.18→0.033 ms median;
  its 2.20 ms tail fell to 0.046 ms p95 with 26 deferred destroys/frame. The
  valid 20-frame release run rendered every frame with clean crash/device logs.
- Real release gameplay then exposed a DH close-before-preflight race: native
  retirement could overtake a visible instance queued by the same traversal.
  Closing a column now removes its pending instance and route counts in the
  same transaction; consumed frames still retain assets through presentation.
- The terrain source used `ClientLevel.hasChunk()`, which intentionally always
  returns true, and cached unloaded placeholder chunks as permanent empty air.
  It now queries the packet-backed cache. Portal traversal also stopped masking
  unloaded neighbors before admission: they remain unavailable until their real
  packet chunks arrive, so readiness cannot accept a two-section startup frame.
- DH generic boxes are copied before route admission. A rejected startup frame
  previously sent those faces without their private DH target and crashed.
  Frame consumption now pairs the boxes with the exact selected route decision.
- Disabled DH now prevents background columns from entering semantic builds;
  the same admission predicate controls collection and build diversion. The
  benchmark restarts after late readiness loss and preserves its exact cause.
  A repeat reached quiescence with no restart or continuing rebuild loop.

## Work queue

1. Validate movement through ordinary gameplay or passive capture before
   accepting more moving performance or flicker evidence.
2. Continue cold-start invalidation checks; exact reload, relocation, live time,
   chunk-light, and vanilla-plus-DH composition now pass.
3. Audit cutout geometry, overdraw, mip policy, and depth/cull state without
   changing the explicit passes future Iris needs.
4. Diagnose the Rust-OpenGL BlockDisplay stratum-71 control-route crash, then
   capture a matched quiescent Java OpenGL moving baseline.

## Current evidence and repository hygiene

- Rust passes 1,814 tests with 2 ignored; atlas lifecycle is 27/27, passive-camera Java is 12/12, terrain renderer is 36/36, and portal batching is 4/4.
- The stale-ResourceSet release control now streams 795 retained sections and
  measures 808 mesh instances/788 batches for 60 frames with 102 deferred
  destroys, zero stale handles, zero failed terrain submits, and clean crash
  output. The benchmark retained 770 terrain layers instead of falsely settling
  on two; its frame median is 5.60 ms and completed GPU median is 5.56 ms.
- An exact combined release capture selected nine DH opaque segments while the
  same frame retained 33 vanilla layers; relocation retained all nine frames.
  Midnight→noon changed sky factor 0.24→1.0; an invisible light changed block
  light 0→15 and mesh content while all nine frames retained 548 instances.
- At the same radius-10 spawn workload, exact sort classification cut batches
  3,379→793, draws 2,810→226, Rust frontend 12.79→2.62 ms, command recording
  4.10→0.51 ms, and frame time 21.79→16.36 ms; GPU stayed near 16 ms.
- Pass routing is not duplicating terrain: no section has equal solid/cutout
  index counts. Preserving strict near-to-far section order increased indirect
  runs 14→210 without reducing GPU time, so page grouping remains preferable.
- Shader probes isolated vertex/raster cost, so direct shader-off opaque/cutout
  terrain uses a lossless 48-byte GPU ABI; translucent, entity, G-buffer,
  layered, and future Iris/source routes retain the 80-byte semantic ABI.
  Static mesh plans are cached while only camera-sorted subsets rebuild; the
  valid 808-instance run measures expansion at 0.122 ms median.
- Generated output is not retained as source evidence; September cleanup removed
  about 94 GB. Audit runs share one bounded retention root and fail unsupported
  direct profiles instead of succeeding without launching Minecraft.
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

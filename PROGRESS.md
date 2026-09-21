# Rendering migration handoff
Updated: 2026-09-21
This is a working handoff and must remain at or below 200 lines.
## Active objective
**Goal 2.5 — active correctness and performance work.** Vanilla
terrain and Distant Horizons terrain must remain visible and stable during
normal RunDev gameplay. Improve CPU/GPU frame cost without weakening the
Rust-owned VulkanicGAL boundary or coupling the direct path to future Iris
shader integration.
Strict combined capture publishes all visible DH candidates without erasing
the foreground. Restarting traversal for first-time resident sections removes
the vanilla isolation's section-shaped startup holes. Radius-8 capture now
visibly exercises DH beyond the vanilla foreground and reaches quiescence.
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
- Quiescent radius-10 vanilla release `20260920-215626` measured 4.33/6.04 ms
  frame and 2.93/3.53 ms completed GPU median/p95; cutout was 1.88/2.54 ms.
  Its fixed 1,291,998 cutout and 437,514 solid indices match combined gameplay.
  Combined vanilla+DH measured 5.62/7.93 ms frame and 3.92/4.58 ms GPU; DH adds
  about 1.29 ms frame and 0.99 ms GPU. The direct-only 32-byte GPU ABI retains the authoritative Rich80 asset for future Iris/source routes.
## Current investigation
- Vanilla visibility now reconverges when asynchronous builds add a later valid
  portal input to an already-propagated section. This removed worker-order terrain
  omissions while retaining the portal graph, immutable mesh cache, last-domain
  publication, empty-domain debounce, and bounded transition residency ring.
- DH had a second atomicity bug: its quadtree considered a CPU-built child LOD
  renderable before Rust acknowledged the bounded asset upload, disabled the
  covering parent, and presented holes. Child readiness now requests native
  publication and becomes renderable only after acknowledgement; an older
  acknowledged generation remains valid during replacement.
- DH semantic generation no longer passes through a redundant global one-task
  semaphore. Its existing bounded loader runs four workers; bounded native
  admission now accepts 16 columns or 16 MiB per update. Publication demand
  protects transition children from retention trimming, and retirement keeps
  an acknowledged asset valid until Rust consumes the explicit removal.
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
- Packed GUI/TACZ staging previously cut inventory allocation 9.15→2.35 MB/frame; JFR
  then found GUI asset publication cloning an immutable raw atlas at the bridge accessor (449 MB sampled during startup). Trusted bridge packing now copies the record-owned
  defensive snapshot directly into confined FFI memory;
  public access remains defensive and Rust still validates the native copy;
  post-fix JFR found zero accessor samples. Combined inventory ran 8.67/11.13 ms with
  `InventoryScreen` active, 2.54 MB/frame steady allocation, and no resource churn.
  JFR attributed 904 MB of sampled GUI batch allocation to per-frame records/sequence
  copies. A validated trusted-owned batch path removes redundant immutable copies; the
  same inventory run fell 1.11→1.03 MB/frame and remained complete/crash-free at 25 batches.
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
- Disabled DH prevents background columns from entering semantic builds; one
  predicate controls collection and build diversion.
- A closed generation-zero DH container could erase a newer published column.
  Generation-zero Rust lifecycle markers now leave later generations intact.
- The single-pass fade mistook private depth for visible DH color and replaced
  valid vanilla terrain with the private clear color. Fade coverage now uses
  the resolved alpha marker, matching the preceding resolve stage.
- Empty DH builds remain render-ready without allocating zero-byte Vulkan
  assets, and they retire an older acknowledged generation only when the new
  lifecycle container is installed. Null or completed-empty candidates no
  longer keep strict capture permanently unpublished.
- Fixed-camera isolation found every resident vanilla mesh was submitted, but
  startup produced different resident portal domains. First-time asynchronous
  section completions now restart traversal from the camera, matching Sodium's
  complete-graph frame search instead of making worker order final.
- Ordinary release RunDev exposed black DH side and underside faces. Raw-color
  and raw-lightmap isolation proved the geometry and material color were valid:
  low-sky faces alone sampled black. Both Rust-owned DH vertex paths now decode
  the dedicated block-light byte and reproduce `standard.vert`'s Vulkan sky-UV
  fold; reduced-color fallback and exact-atlas replacement share one contract.
## Work queue
1. Continue Goal 2.5 with evidenced vanilla/DH transition defects and costs.
2. Keep direct-route changes compatible with later Iris source-pass work.
## Current evidence and repository hygiene
- Rust passes 1,814 tests with 2 ignored; focused DH Java coverage is 54/54.
  Release RunDev loaded `New`, opened inventory, changed night to day, settled DH,
  and survived yaw without flicker; standard validation reported zero VUIDs.
- Radius-8 LOD_ONLY `20260920-190538` visibly renders coarse DH terrain with 12
  candidates, 19 opaque segments, zero unpublished columns, and settled queues.
  Combined SINGLE_PASS `20260920-192435` preserves the vanilla foreground with
  zero unpublished columns. Opaque-item moving capture `20260920-213506` passes
  initial, right, left, and return views with 548 vanilla submissions, six visible
  DH columns, and zero unpublished columns or retirements.
  Translucent moving-camera capture `20260920-231508` now exercises all seven
  front/lateral/orbit/cross/above/below/return poses; source sort, Rust-copy,
  final-order, and combined DH/vanilla gates pass with no crash or device loss.
  Rust-Vulkan capture commands now enable the existing opt-in GPU timestamp
  path; a partial standard probe reduced unavailable timestamp frames to 3
  (the prior correctness artifact had 748). A full post-change performance
  capture completed with DH continuity and seven screenshots; its retained
  sort receipts were sparse, so the audit now relies on explicit stale-submit
  faults plus the image-level final-order gate.
  The Java metrics summary now publishes the decoded GPU pass totals and
  submission identity, allowing the audit artifact to retain device timings
  without moving any rendering or timing work out of Rust/Vulkan; decoded
  samples are accumulated once per completed submission for valid per-frame
  averages.
  Interior edit `20260920-193417` retained 640 vanilla layers and nine DH instances
  through nine continuity frames. Resource reload `20260920-195327` replaced all
  nine old DH columns without a blank frame. View-distance decrease `20260920-195538`
  and increase `20260920-195925` retained vanilla terrain on every sampled frame
  with zero unpublished DH columns. Vanilla isolation has no holes.
- An exact combined release capture selected nine DH opaque segments while the
  same frame retained 33 vanilla layers; relocation retained all nine frames.
  Midnight→noon changed sky factor 0.24→1.0; an invisible light changed block
  light 0→15 and mesh content while all nine frames retained 548 instances.
- At the same radius-10 spawn workload, exact sort classification cut batches
  3,379→793, draws 2,810→226, Rust frontend 12.79→2.62 ms, command recording
  4.10→0.51 ms, and frame time 21.79→16.36 ms; GPU stayed near 16 ms.
- Latest combined capture measured 31.77 s frontend work over 748 frames, with 25.27 s in command generation and a 0.435 ms decoded GPU frame. Retained clean DH gameplay (`20260921-000328`) measured Rust 120.1 median FPS versus Frozen OpenGL 120.4 (`20260920-235556`); Rust frontend 6.94 ms/frame, command generation 1.18 ms, resource preparation 5.60 ms, GPU 2.58 ms. The earlier 42 ms cost was audit-only.
- Vanilla-only camera relocation passed geometry and replacement lifecycle gates (`20260920-235941`): 3,824 visible-submit events, 747 retained instances, no lifecycle failures. Its later strict process-scan failure was harness-only; the generated run was pruned.
- Generated output is not source evidence; September cleanup removed about 94 GB. Retained gameplay baselines keep only manifests/artifacts (fixture copies are pruned), and audit runs reject unsupported direct profiles.
- Obsolete implementation-mirroring suites were removed. Behavioral ABI, backend,
  terrain/DH lifecycle, focused audit-helper, Java, Python, and Rust coverage remain.
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
- Branch: `master`; this investigation began from clean commit `be9b7328f` (`distant horizons mostly working`). Correctness fixes are uncommitted; no commit or push has been made.

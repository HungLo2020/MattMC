# Rendering migration handoff
## Active objective
**Goal 3 — Iris shader packs on Rust Vulkan.** Continue from the completed
vanilla/DH Rust Vulkan path with a Rust-owned shader-pack vertical slice. Java
may provide copied immutable source/assets/config semantics; Rust owns
preprocessing, lowering, pass/resource policy, synchronization, Vulkan
execution, and the single presenter. Distant Horizons shader integration is
out of scope until the non-DH path is complete.
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
- The selected-source path now completes a real Rust Vulkan frame for the
  Complementary fixture. Capture `20260921-095656` is crash/device-loss free,
  reaches `rust-native-selected-source`, and records source coverage for opaque,
  cutout, translucent, entity, and GUI work.
- Stage probing narrowed the prior pale/washed output to the post-deferred
  fullscreen chain. The fix keeps Rust-owned target images in native texel
  address space while explicitly converting only legacy source reconstruction
  vectors (`screenPos`/`screenPosDH`) to the source pack's lower-left domain.
  This preserves target/depth alignment and removes the vertically mirrored
  reconstruction without weakening the Rust-owned pass/resource contract.
- A bounded `composite5-fog-inputs` probe and the depth probe are unit-tested and
  opt-in only; they are diagnostic aids, not a second rendering path.
- Source admission, output attachments, feedback history, barriers, mipmaps,
  entity packed-light variants, water material lanes, GUI prebuild, and Rust
  fullscreen execution remain semantic Rust/Vulkan policy. Java supplies only
  copied source/assets/configuration data.
- Frozen Java OpenGL shader-on capture `20260921-100003` did not reach a paired
  deterministic frame: it joined the world and loaded the shader pipeline but
  stayed at `LevelLoadingScreen` until the harness deadline, with DH isolation
  unrecorded. This is a readiness/isolation blocker, not a crash, and means no
  parity claim is published yet.
- Vanilla/DH lifecycle, publication atomicity, queue boundedness, packed staging,
  indirect submission negotiation, inventory allocation, and GPU timestamp work
  remain in the existing Rust-owned architecture. No Java Vulkan or Iris GPU
  object crosses the boundary.

## Work queue
1. Finish the selected-source terrain + deferred/fullscreen slice by obtaining
   a readiness-complete Frozen Iris OpenGL baseline and paired semantic capture;
   the Rust Vulkan frame and source execution are now real and complete.
2. Add entities/hand/particles/sky/cloud/weather/shadow coverage through the
   same semantic callsites; keep unsupported stages unadmitted.
3. Add paired cross-repo capture evidence and prune stale diagnostics while
   preserving the Rust/VulkanicGAL boundary.
## Current evidence and repository hygiene
- Rust focused frontend validation passes 473/473 tests after the current
  shader-coordinate changes; lowering tests pass 83/83 and `cargo check` passes.
  Release RunDev loaded `New`, opened inventory, changed night to day, settled DH,
  and survived yaw without flicker; standard validation reported zero VUIDs.
- The latest selected-source real-world capture (`20260921-095656`) completed its
  deterministic Rust Vulkan frame with source execution, GUI execution, strict
  GL scan, and Vulkan validation all passing. Its image is visually detailed and
  upright; shader-on Frozen parity still awaits a readiness-complete baseline.
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
- Combined release captures retained vanilla and DH through relocation, lighting,
  and time-of-day transitions; exact translucent sorting remained stable.
- Exact sorting cut batches 3,379→793 and frame time 21.79→16.36 ms. Clean DH
  gameplay measured Rust 120.1 median FPS versus Frozen OpenGL 120.4; Rust
  frontend 6.94 ms/frame, command generation 1.18 ms, GPU 2.58 ms.
- Vanilla-only camera relocation passed geometry and replacement lifecycle gates (`20260920-235941`): 3,824 visible-submit events, 747 retained instances, no lifecycle failures. Its later strict process-scan failure was harness-only; the generated run was pruned.
- Generated output is not source evidence; September cleanup removed about 94 GB. Retained gameplay baselines keep only manifests/artifacts (fixture copies are pruned), and audit runs reject unsupported direct profiles.
- Obsolete implementation-mirroring suites were removed. Behavioral ABI, backend,
  terrain/DH lifecycle, focused audit-helper, Java, Python, and Rust coverage remain.
## Completion gate
- A supported Iris pack renders a complete non-DH frame through Rust Vulkan;
  no Java Vulkan or Iris GPU runtime participates, and unsupported packs stay
  explicitly unadmitted.
- Frozen Java OpenGL with the identical pack/options/resource pack/world/camera
  remains the correctness baseline with paired semantic evidence.
- Vanilla terrain never disappears or flickers during cold start, movement,
  invalidation, view-distance changes, reload, or settled gameplay.
- DH remains visibly distinct beyond the vanilla radius and shares one Rust
  frame/presenter with vanilla terrain.
- Fresh benchmarks show the effect of each optimization and reach quiescence;
  GPU timestamps are tied to completed presentation submissions.
- Architecture/callsite tests continue to reject Java GPU execution, borrowed
  renderer state, legacy fallback presentation, and accidental Iris coupling.
- PROGRESS.md remains a concise current handoff under 200 lines.

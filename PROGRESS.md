# Rendering migration handoff

Updated: 2026-09-19
This is a working handoff and must remain at or below 200 lines.

## Active objective

**Goal 2 — Distant Horizons on Vanilla Rust Vulkan.** Normal DH gameplay must
render in the same Rust-owned Vulkan frame as vanilla terrain using copied,
immutable CPU semantics. Java, DH, and Iris may not pass renderer objects, GPU
resources, native handles, GL state, or a second presenter across the boundary.
Frozen Java OpenGL remains the unchanged correctness baseline.

Goal 2 is complete. Literal RunDev visibly renders vanilla and DH together;
the generic-object/SSAO crash and vanilla-terrain regression do not recur.

## Implemented architecture

- Java copies real quadtree-visible opaque, transparent-side, transparent-up,
  and water CPU semantics. Rust owns their pipelines, buffers, attachments,
  lightmap, fog/fades, depth/blend state, and presentation.
- Java DH exits before legacy draw, fog, fade, deferred, apply, and presentation
  work when Rust is selected. Receipts reject Java Vulkan frame execution.
- Generic boxes, clouds, SSAO inputs, terrain-atlas identities, generation
  changes, and retirements have bounded semantic contracts. Unsupported work
  remains fail-closed.
- Shader-off DH uses Frozen's reduced-color contract. Exact-atlas overlays are
  admitted only for proven identities; unresolved faces remain explicit.
- World meshes keep immutable topology/material assets separate from frame
  transforms and packed light. `RunDev.py` uses the Rust release profile while
  retaining Java diagnostics.

## Accepted checkpoints

- r229-r233 established the real-world route. A 600-frame comparison measured
  Current near 69.9 FPS versus Frozen near 120.3 FPS; work was split across Java
  packing, Rust frontend/submit, and about 9 ms GPU time.
- r264 exposed a rebuild loop and 12 GB RSS failure. Lifecycle containers now
  retain CPU-semantic readiness, close retirement is generation-checked, and
  the collector protects active quadtree candidates under the 512-column cap.
- r272 preserved Frozen's greedy coarse topology for the ordinary route. On the
  same seven columns, copied vertices fell 36.2% and segments fell 64 to 48.
- The direct route owns private DH render, fog/SSAO/far-fade resolve, sparse
  apply before vanilla opaque, and later vanilla fade stages. No Java/DH GPU
  object or state enters these stages.
- r331 copied DH's vanilla-fog cancellation policy. r340 then passed a settled
  radius-8 comparison at mean RGB error about 0.005/channel, although a
  10-chunk vanilla radius obscured almost all DH geometry.
- r343 used vanilla radius 4/DH radius 8. Private alpha covered 424,014 pixels,
  including 368,204 without vanilla depth; normal error was about 0.61-0.74 per
  channel. r344 cut asset updates 767 to 190 and bytes 83.4 to 14.7 MB.
- r459 validates resource reload; r460 validates two resizes without DH reset;
  r461/r464 validate same- and different-world reloads with empty menu state.
  All streams republish and each DH-only mask passes near `[2.29,2.21,2.67]`.
- r467 revisit and r468 four-leg soak end with 13 visible columns, 55 instances,
  no unpublished columns/retirements, and less than 64 MiB retained.
- r366 suppresses vanilla terrain and proves that 13 real DH columns, 55 LOD
  instances, and nine DH cloud groups remain in the Rust-owned frame.

## Current accepted implementation

- The user's concern was correct: the earlier Current "LOD-only" images did
  not isolate DH. Java copied the configured vanilla-fade mode but omitted
  DH's independent `lodOnlyMode`, so vanilla terrain could overwrite the DH
  result and make an apparent proof image look like ordinary terrain.
- The existing two fade bits now encode LOD-only when both are set. Rust maps
  that value to Frozen's `uOnlyRenderLods` behavior and replaces vanilla with
  the private DH image at both fade boundaries without changing ABI size.
- Frozen still clears/applies its private DH target and invokes the fade hooks
  on an active sparse frame with no staged LOD draw. Rust now preserves that
  transaction instead of exposing a complete vanilla frame intermittently.
- Source inspection found a real baseline mismatch. Frozen OpenGL renders
  transparent-side, transparent-up, and water-up once under the inherited
  TRANSPARENT state. Rust had copied the Java Vulkan no-shader compatibility
  plan: water in the opaque phase, detail sides without depth writes, then a
  water replay. That plan is not the required Frozen baseline.
- The ordinary Rust route now submits the three transparent buckets once in
  Frozen order with alpha blending, back-face culling, LESS depth, and depth
  writes. Deferred/source rendering retains its separate explicit water plan,
  preserving the Iris/DH graph boundary.
- DH VBO segment ordinals restart inside each bucket. The shared forward cache
  therefore includes the immutable layer in its key; equal side/up/water
  ordinals cannot bind another bucket's storage buffer. A focused regression
  test covers this collision.
- Frozen's `uOnlyRenderLods` returns the private DH texture before its ordinary
  unwritten-pixel fallback. Rust did the fallback first, leaking vanilla through
  DH coverage holes. The corrected branch returns the resolved private target
  directly and preserves translucent water when DH fog is disabled.
- r398-r400 are fresh matched visible-extension pairs with vanilla radius 4 and
  DH radius 8. Each executes 55 real LOD segments with one Rust presenter and
  no Java Vulkan frame. NONE, SINGLE_PASS, and DOUBLE_PASS emit exactly one,
  two, and three compositor passes and pass Frozen visual parity at mean RGB
  errors `[0.69,0.92,1.07]`, `[1.25,1.53,1.87]`, and `[1.24,1.31,1.51]`.
- The harness now exposes `--dh-composition-mode` for NONE, SINGLE_PASS,
  DOUBLE_PASS, and LOD_ONLY. It writes the shared copied config, records the
  policy in the canonical fixture identity/manifest, and propagates it to the
  child capture. The paired r402 LOD_ONLY row executes 55 instances and three
  passes with one Rust presenter/no Java Vulkan frame; whole-frame error is
  `[1.25,1.32,1.52]` and its masked DH-extension error is `[2.30,2.23,2.69]`.
- Paired visual reports now gate the DH-visible extension separately: pixels
  require nonzero private-DH alpha and clear reversed vanilla depth. The
  r398-r400 mask contains 336,740 pixels (36.5% of the frame); masked mean RGB
  errors are `[0.77,1.01,1.22]`, `[2.31,2.63,3.39]`, and `[2.28,2.20,2.66]`.
  This prevents vanilla foreground or sky agreement from hiding a broken LOD
  field and retains a reviewable mask image with every qualifying pair.
- The palette gate no longer requires an Iris-style exact-atlas draw from the
  ordinary shader-off route. Its screenshot acknowledgement instead proves
  that the executed, spatially matching DH column retains all four requested
  material identities under Frozen's reduced-color contract. Exact-atlas
  validation remains required only for a source program that declares it.
- Palette readiness also exposed two capture deadlocks: a stopped DH generation
  module is quiescent after the publication/execution checks pass, and mixed
  reduced-color quads cannot satisfy exact-atlas stability. Both gates now use
  the contract of the active route rather than weakening renderer admission.
- r407 exposed a fixture mismatch: Current transitioned its copied vanilla
  radius after retaining the DH source column, while Frozen remained at the
  initial radius 10 and photographed ordinary terrain over the palette.
- r408 is the authoritative material proof: explicit LOD_ONLY forces both
  renderers to show DH's private output. The consumed-column receipt passes for
  all four materials, Current executes ten opaque LOD segments with one Rust
  presenter, and the 291,989-pixel DH-only mask (31.7% of the frame) passes at
  mean RGB error `[5.02,3.42,0.70]` under the 6.0/channel threshold.
- The ordinary palette fixture now performs the same staged transition on both
  sides. Frozen first observes a real opaque DH pass, reloads normal Sodium at
  radius 2, drains its build queue, and observes a post-reload DH pass; Current
  first consumes the exact semantic palette column and then remains at radius
  2. The receipt rejects incomplete or unequal transitions.
- r411 passes ordinary DOUBLE_PASS composition. Both rows finish at vanilla
  radius 2; Current consumes two real palette segments, Frozen renders six DH
  columns after its reload, whole-frame mean error is `[0.38,0.37,0.40]`, and
  the 291,989-pixel DH-only extension passes at `[0.85,0.79,0.82]`.
- r413 and r415 pass paired vanilla view-distance decrease (10 to 4) and
  increase (10 to 12). Each side proves a real DH draw before the change and a
  newer nonempty draw after the normal terrain rebuild. Current has zero
  unpublished columns and pending retirements at both capture boundaries.
- The decrease DH-only mask covers 336,873 pixels and passes at mean RGB error
  `[2.43,2.32,2.66]`; the increase mask covers 257,831 pixels and passes at
  `[2.65,2.80,3.18]`. The ordinary draw-count gate remains on ordinary terrain
  rows but is inapplicable to these intentional dynamic-radius DH lifecycles.
- r417/r418 extend the decrease lifecycle across the transparent and explicit
  water gates. The captured Rust frame has 20 opaque, 22 transparent, and 13
  water segments; its 336,740-pixel DH-only region passes Frozen parity at
  `[2.37,2.31,2.77]`, with no unpublished columns or pending retirements.
- Settled timing isolates the remaining cost: Frozen is 1.87 ms/frame,
  Current vanilla is 15.67 ms, and Current with DH is 22.20 ms. Disabling DH
  opaque cuts most of the incremental GPU cost; transparent and water do not.
- A dedicated Vulkan timestamp now measures the direct DH opaque span. r440
  measured 3.96 ms of 18.90 ms total GPU time; r447 repeated at 3.93 ms.
  Noise, fog, fade, draw order, and composition mode were individually ruled
  out. Opaque depth writes are now enabled for direct shader-off DH, while the
  deferred Iris path keeps its separate depth policy.
- r449 validates that change with full attachments: 336,740 pixels (36.5% of
  the frame) are real DH beyond vanilla depth, masked error is
  `[0.61,0.62,0.64]`, and visual, draw-coverage, and lifecycle gates all pass.
- Generic DH objects now cross the ABI as one bounded 64-byte semantic box
  record; Rust validates and expands its six faces into cached quad topology.
  r472's enforced mixed-phase gate proves one pre-SSAO and 2,241 post-SSAO
  boxes in one Rust frame and passes Frozen's 410,703-pixel DH-only mask. r471
  loads the user's crash save with Rust SSAO active; r473 runs 311 DH frames
  through quiescence. r485 runs literal RunDev for 1,657 DH frames; 24 stable
  captures show near vanilla plus far DH. r486 disabled control removes far LODs.
- The normal-callsite audit follows the registered level, chunk, light-texture,
  and world hooks through the real quadtree and shared generic object registry.
  Legacy LOD, deferred, fade, custom-render, and Java GPU paths fail closed.
- Coarse DH vertices now retain the same position, micro-offset, color, light,
  material, and face semantics in a 16-byte Rust-owned GPU record instead of
  32 bytes; eligible segment indices are U16. Built-in and lowered Iris/DH
  shaders decode the same layout. r451 passes the real Vulkan fixture.
- Repeated settled runs r452/r453 reduce the direct opaque DH span from about
  4.30 ms to 2.51 ms (42%) and valid total GPU time from 20.53 ms to about
  17.4 ms. The layout remains an owned semantic format, not a legacy DH VBO.
- Whole-frame tracing found the inactive source route destroying direct-DH
  lightmap bindings every frame. Cleanup now occurs once on a real source-state
  transition. r455 cuts frame median 20.46 to 18.74 ms and Rust frontend median
  5.27 to 4.71 ms under the same 21-column, 102-instance workload.
- GPU timestamps now publish only completed presentation submissions and carry
  their submission identity, so upload spans and repeated stale samples cannot
  pollute frame aggregates. r456 records total GPU median 17.44 ms and DH opaque
  2.38 ms; matching no-DH r457 is 14.27 ms GPU/14.16 ms frame, isolating DH's
  incremental cost at about 3.17 ms GPU and 3.64 ms frame.

## Remaining work
Goal 2 has no remaining acceptance item. Start the next rendering objective only
from a new explicit goal; the completed evidence remains retained in r472, r473,
r483, r485, and r486.

## Validation baseline

- Current checks: all 1,800 runnable Rust tests pass (2 ignored), plus 468/468
  focused Java architecture/callsite tests. r408 passes explicit DH materials;
  r411 passes ordinary composition; r413/r415 pass paired rebuilds; r459-r468
  pass reload, recreation, world, revisit, and soak lifecycles without crash,
  device loss, VUID, Java Vulkan execution, or fallback presenter.
- No commit or push has been made. Incomplete capabilities remain gated.

## Completion gate

Goal 2 passes: r485 visibly combines near vanilla terrain with far DH under
SSAO/generic rendering; r486 proves the far field is DH, and ownership tests
exclude Java Vulkan, fallback presentation, and borrowed GPU state.

# Vulkan Structural Plan

## Purpose

This document captures the current Vulkan performance mandate, the structural problems identified in the Vulkanic abstraction layer and Vulkan backend, the non-regression requirements, and a methodical plan for fixing the architecture without damaging correctness or OpenGL performance.

This is intended to keep future work aligned, measurable, and reversible when experiments fail.

## Primary Goal

Make the Vulkan backend first-class, correct, and materially faster while preserving the shared abstraction layer and keeping OpenGL performance and correctness high.

In practice, that means:

- Vulkan should no longer behave like a compatibility backend under an OpenGL-shaped hot path.
- The Vulkan backend should move toward explicit frame-owned command recording and earlier pass preparation.
- OpenGL must remain correct, performant, and stable throughout the refactor.

## Hard Requirements

These requirements are non-negotiable for any accepted change.

### Correctness

- The project must build successfully.
- The client must launch successfully.
- Vulkan correctness must not regress.
- OpenGL correctness must not regress.
- Any broken command ownership, render pass lifecycle, descriptor binding, or presentation behavior must be treated as a failed experiment.

### Performance Guardrails

- No more than a 2% regression in OpenGL average FPS.
- No more than a 2% regression in OpenGL 1% low.

If a change violates the guardrail, it must be fixed or reverted.

### Vulkan Performance Objective

The standing objective is a major Vulkan uplift.

- Minimum strategic goal: at least 50fps for vulkan on average

## Trusted Baseline

Reference artifact:

- `logs/auto-profile/20260402_123345/summary.txt`

Trusted aggregate baseline:

- OpenGL: `118.6175 avg / 92.2385 1% low`
- Vulkan: there is no truly trustworthy vulkan baseline

This is the baseline all future comparisons should be judged against unless it is explicitly replaced with a better trusted baseline.

## Standard Validation Commands

### Focused Validation

```bash
./gradlew test --tests 'net.vulkanic.*' --tests 'net.vulkanic.backends.vulkan.*' --tests 'net.blaze3d.platform.WindowVulkanSwapchainResizeTest' --tests 'net.blaze3d.opengl.GlDeviceShaderSourceFallbackTest'
```

### Full Validation

```bash
./gradlew test
```

### Standard Performance Harness

```bash
python3 DevUtils/PerfAudit/Matrix.py --profile standard --modes current-opengl-shaders-off current-java-vulkan-shaders-off --world Origin
```

## Summary of the Structural Problem

The core issue is not that no hotspots can be found. The issue is that the remaining costs are structural and concentrated in correctness-critical seams.

### High-Level Diagnosis

Vulkan is still being driven through an abstraction layer and render flow that are shaped more like OpenGL than Vulkan.

The hot path still tends to do too much work too late:

- resolve pipeline state during draw submission
- resolve descriptor expectations during draw submission
- route through compatibility-heavy command and state seams
- mix frame-owned work and compatibility-style immediate work
- allow texture maintenance and render submission to meet at the same critical synchronization boundaries

That shape is survivable for correctness, but it is not a strong foundation for first-class Vulkan throughput or pacing stability.

### Why Local Optimizations Keep Failing

Many of the obvious hotspots are also the least safe places to make aggressive changes.

These include:

- command buffer submission and fence waits
- render pass begin/end behavior
- descriptor binding and descriptor reuse
- pipeline resolution and compatibility matching
- texture upload and mip generation
- present composition and swapchain transitions

When a change touches those seams, it often changes not just CPU cost but also ordering, ownership, pacing, or synchronization behavior.

That is why multiple technically plausible optimizations have validated cleanly but still regressed the harness.

## Structural Issues by Category

### 1. Command Ownership Is Still Too Mixed

Relevant files:

- `src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java`
- `src/main/java/net/vulkanic/VulkanicAPI.java`

Problem:

- Work can still arrive through both frame-owned and compatibility-style command paths.
- Submission behavior is still too dependent on how work entered the system rather than what category of work it is.
- This makes synchronization hard to simplify safely.

Why it matters:

- Fence waits, submit timing, and command lifetime remain one of the most plausible structural throughput limiters.
- It also creates pacing instability.

### 2. Too Much Pipeline and Descriptor Work Happens Near Draw Time

Relevant files:

- `src/main/java/net/blaze3d/opengl/GlCommandEncoder.java`
- `src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java`
- `src/main/java/net/vulkanic/PipelineDescriptor.java`

Problem:

- The render path still performs too much matching, validation, translation, and setup close to draw submission.
- The architecture remains compatibility-oriented instead of pass-prepared.

Why it matters:

- Even when local costs are reduced, the architecture still asks the hot path to repeatedly prove compatibility.
- This keeps Vulkan draw execution heavier than it should be.

### 3. Descriptor Planning and Descriptor Submission Are Not Separated Cleanly Enough

Relevant files:

- `src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java`

Problem:

- The code still tends to combine "figure out what should be bound" with "materialize and bind it now".
- That limits cache boundaries and makes invalidation logic harder to reason about.

Why it matters:

- Descriptor churn remains one of the persistent hot seams.
- This is likely costing both throughput and consistency.

### 4. Texture Maintenance Still Crosses Critical Render Ownership Boundaries

Relevant files:

- `src/main/java/net/minecraft/client/renderer/texture/TextureAtlas.java`
- `src/main/java/net/minecraft/client/renderer/texture/SpriteContents.java`
- `src/main/java/net/vulkanic/backends/vulkan/VulkanBackend.java`

Problem:

- Animated texture uploads, atlas writes, and mip generation still interact with command ownership and render submission.
- Attempts to move this work without fully clarifying ownership have already caused crashes or major regressions.

Why it matters:

- Texture maintenance must be separated from render submission in a controlled way, not just rerouted.

### 5. The Proxy and Abstraction Seams Still Cost Real CPU in Hot Vulkan Paths

Relevant files:

- `src/main/java/net/vulkanic/VulkanicAPI.java`
- `src/main/java/net/vulkanic/GraphicsBackend.java`

Problem:

- Some high-frequency Vulkan paths still cross compatibility-heavy dispatch.
- Earlier broad direct-dispatch attempts were too blunt and regressed performance, but the JFR evidence still shows that some remaining routes are hot.

Why it matters:

- This is not the deepest structural issue, but it remains real overhead.
- It should be attacked narrowly and only after larger ownership and pass-preparation problems are better shaped.

## Measurement Problem: Vulkan Variance

There is a second issue beyond raw throughput: Vulkan measurements are materially noisier than OpenGL measurements.

### What This Means

- Small improvements may be masked by run-to-run variance.
- Small regressions may also be hard to identify from a single compare.
- 1% lows vary more than averages.
- Pacing-sensitive changes can look inconsistent even when they are directionally real.

### Practical Consequence

The harness is still necessary, but it is best at catching:

- large wins
- large regressions
- invalid runs

It is weaker at distinguishing small Vulkan deltas.

### Required Measurement Discipline

For any change that lands close to baseline:

- run the normal 3-run harness first
- if the result is within roughly 5% either way, rerun a second compare before deciding
- track per-run spread, not just aggregate numbers
- do not keep or reject borderline changes from one noisy batch alone

## Methodical Structural Plan

The work should proceed in phases. The order matters.

### Phase 1: Tighten Measurement and Decision Rules

Goal:

- prevent noisy Vulkan variance from producing bad keep/revert decisions

Tasks:

- always compare against the trusted baseline
- record per-run spread as well as aggregate metrics
- rerun borderline results
- distinguish throughput changes from pacing changes

Exit criteria:

- a clear measurement protocol exists for accepting or rejecting structural work

### Phase 2: Freeze Public Contracts, Refactor Internals Behind Them

Goal:

- preserve OpenGL correctness and behavior while Vulkan internals are reworked

Tasks:

- treat `GraphicsBackend` as the stability contract
- keep `VulkanicAPI` focused on routing, not more hot-path logic
- rely on tests to protect typed and untyped API equivalence

Exit criteria:

- structural refactors happen behind the existing public seam

### Phase 3: Make Vulkan Command Ownership Explicit and Frame-Centric

Goal:

- clearly separate frame work from non-frame work

Tasks:

- classify all Vulkan command sources into frame render work, frame-adjacent maintenance work, and immediate non-frame work
- remove implicit ownership decisions where possible
- tighten submit behavior around those explicit categories

Exit criteria:

- fewer ambiguous command paths
- fewer ownership-dependent surprises
- no invalid runs from submission changes

### Phase 4: Move Pipeline Preparation Earlier Than the Draw Hot Path

Goal:

- stop repeatedly discovering pipeline compatibility in active draw submission

Tasks:

- split `GlCommandEncoder.trySetup` responsibilities into preparation vs bind/execute
- introduce a Vulkan-only prepared pass or pipeline package concept
- build pass-local immutable state earlier

Package contents should eventually include:

- resolved pipeline handle
- resolved resource layout expectations
- descriptor submission plan
- immutable pass-local metadata needed during draw recording

Exit criteria:

- repeated draws perform less compatibility-oriented decision work

### Phase 5: Split Descriptor Planning from Descriptor Materialization

Goal:

- create a clean cache boundary for descriptor work

Tasks:

- separate descriptor plan generation from actual descriptor update/bind execution
- define explicit invalidation triggers
- keep command-buffer binding suppression separate from planning reuse

Exit criteria:

- descriptor logic becomes easier to cache, reason about, and benchmark independently

### Phase 6: Separate Texture Maintenance from Render Submission

Goal:

- stop texture maintenance work from accidentally controlling render scheduling

Tasks:

- classify maintenance work by when it must run
- define whether it is frame-critical, frame-adjacent, or safely amortizable
- only move or batch it after command ownership is explicit

Exit criteria:

- atlas and sprite maintenance no longer create hidden command ownership coupling

### Phase 7: Narrowly Remove Remaining Hot Compatibility Dispatch

Goal:

- trim remaining proven proxy overhead without destabilizing the abstraction

Likely candidates based on prior profiling:

- beginRenderPass paths
- specific uniform setters heavily used by Iris
- resolveTextureHandle-related paths

Rules:

- optimize only one or a few measured hot routes at a time
- validate and benchmark each route independently
- avoid another broad dispatch rewrite

Exit criteria:

- smaller, attributable wins with low structural risk

## Keep/Revert Gates for Every Phase

Each phase must pass four gates.

### 1. Correctness Gate

- focused Vulkan tests pass
- full tests pass

### 2. Stability Gate

- no invalid benchmark runs
- no launch failures
- no render pass or command-context ownership breakage

### 3. Performance Gate

- OpenGL remains within guardrail
- Vulkan remains within guardrail
- borderline changes get rerun because of variance

### 4. Decision Gate

- keep only if the behavior and performance story are both coherent
- otherwise revert immediately and record the lesson

## What Should Be Avoided

These patterns should not be repeated without a much stronger justification.

### Avoid Broad Caching Sweeps

Reason:

- they repeatedly looked reasonable in profiles but failed the harness

### Avoid Broad Submission Batching Sweeps

Reason:

- batching changes have already shown correctness and harness-validity risk

### Avoid Broad VulkanicAPI Direct-Dispatch Rewrites

Reason:

- they are too hard to attribute and too easy to regress

### Avoid Texture-Maintenance Rerouting Before Ownership Is Explicit

Reason:

- that seam has already produced crashes and large regressions when changed too early

## Practical Interpretation of Success

Success is not a micro-optimization that moves a single Java hotspot.

Success is a render architecture where:

- Vulkan work is more frame-centric
- pass preparation happens earlier
- descriptor planning is cleaner and more reusable
- texture maintenance is explicitly scheduled
- the hottest compatibility seams are pushed out of the critical path
- OpenGL remains stable throughout

## Recommended Next Structural Focus

If only one structural program should be pursued next, it should be this:

1. clarify Vulkan command ownership completely
2. then move pipeline and descriptor preparation earlier in the pass lifecycle

This is the most credible route to improving both:

- Vulkan throughput
- Vulkan frame pacing consistency

## Working Principle

Do not optimize the hottest-looking line in isolation.

First ask:

- what category of work is this?
- who owns it?
- when should it be decided?
- when should it be recorded?
- when should it be submitted?

If those answers are still ambiguous, the architecture is not ready for safe performance work yet.

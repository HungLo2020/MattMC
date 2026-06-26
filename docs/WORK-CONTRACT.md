# Work Contract

## Problem

The recurring failure mode in this repo is not one bad fix. It is broad experimental batches that mix multiple layers of correctness at once and silently degrade a stable baseline.

The overall goal is correctness.

The usual mistake is mixing these different targets in one batch:

- stable baseline behavior
- Vulkan fallback correctness when shaders are off or fail open
- shader-enabled fail-open behavior
- actual shader-pack correctness

When those are changed together, regressions pile up faster than they can be understood.

## Contract

1. Correctness is the goal.
   - Stable baseline behavior is not the final target. It is the floor that all new work must preserve.
   - Every patch must improve or protect correctness in a clearly stated way.

2. Protect the stable baseline at all times.
   - Baseline means: the game loads into a world, terrain is visible, and it does not crash.
   - If a change breaks that baseline, discard that slice immediately instead of layering more fixes on top.

3. Only one correctness target per patch.
   - Do not mix baseline preservation, fail-open work, and shader-pack correctness work in the same experiment.
   - Do not mix terrain, GUI, frame lifecycle, and Iris custom-pass changes in one experiment unless they are strictly inseparable.

4. Keep patches narrow.
   - Prefer one subsystem, one hypothesis, one validation target.
   - If a patch spreads across many files or multiple rendering systems, split it before continuing.

5. Validate against behavior, not just source tests.
   - Use bounded runtime validation and artifact capture after each meaningful change.
   - Do not treat new source-guard tests as proof that the runtime behavior is correct.

6. Improve correctness in layers.
   - Stable vanilla terrain and no crash is the minimum acceptable baseline.
   - Shader-enabled fail-open behavior is a later layer.
   - Full shader-pack correctness is a later layer still.

7. No speculative patch stacking.
   - If the current hypothesis is not proven, do not add adjacent fixes "while we are here".
   - A regression should shrink the patch, not widen it.

8. Use DevUtils/RunDevCapture.sh EXCLUSIVELY to run tests. after each run of this script ensure the game itself has successfully exited before running a new test. this test helps collect diagnostics and ensure the world loads. use the "origin" world and parameter for this script. 

## Working Order

1. Start from the stable baseline.
2. State the exact correctness gap being targeted.
3. Choose one narrow hypothesis.
4. Make the smallest possible change.
5. Run bounded validation.
6. Keep the change only if it measurably improves correctness without regressing the baseline.

If that loop is not being followed, stop and reset before doing more work.
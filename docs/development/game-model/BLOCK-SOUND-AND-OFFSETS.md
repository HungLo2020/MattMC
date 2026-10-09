# Block sounds, instruments and model offsets

> Current source ownership; runtime results below are author-recorded
> sound/offset milestone evidence. This review did not rerun them or inspect
> their local receipts. The overall performance target remains unmet.

## Native definitions and consumers

[`content/block/definitions/material.rs`](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/src/main/rust/content/block/definitions/material.rs)
selects shared immutable sound,
instrument and model-offset settings. The catalog uses 159 profiles for its
1,235 blocks. These are semantic content settings; GPU materials and rendering
resources remain render-owned. State sound rules preserve cracked decorated
pots and pine-bearing pewen branches. The author’s recorded comparison
matched all 31,809 native state results to Frozen; see the evidence below.

[Sound definitions](SOUND-DEFINITIONS.md) supply typed identities.
[`content/block/offset.rs`](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/src/main/rust/content/block/offset.rs)
owns the offset arithmetic, with eight finite
configurations. It preserves Java's float division followed by double
subtraction/multiplication, including the exact horizontal limits. Coordinate
seeding moves to `core/math.rs` for shared use by content, world generation and
terrain meshing. The x product wraps at 32 bits before widening.

The remaining-fact packet uses format 8: native definitions now supply
maximum offsets and offset kinds, so Java stops exporting them. Blocked light,
remaining state flags and light-occlusion faces still await migration.

## Compatibility and allocation

Native state sounds use a typed 16-bit column, borrowed directly by the
[`NativeBlockDefinitions` projection](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/src/main/java/net/minecraft/world/level/block/state/NativeBlockDefinitions.java)
without an extra integer copy. Native-admitted Java states cache
their canonical sound view and share an
offset-table view. The finite table has 256 entries for XZ or 4,096 for XYZ;
NONE returns `Vec3.ZERO`. The six active configurations contain 9,216 entries.
Queries select an immutable vector instead of calculating and allocating one.
There is no world-position cache or per-query FFM call.

Copied `Properties` retain the generic offset function, which may be applied
to a different block class with different maximum offsets. Manually constructed
states keep that path and the existing state-dependent sound callback.
This synthetic/unregistered path is not fallback for a missing registered
definition. Registered state views use the native tables directly. Their instrument and
base sound settings are applied before constructor caches are assigned.

## Editing and verification

Edit catalog bindings and shared material/offset declarations. Keep sound
rules dependent only on declared native properties. Changes to a shared
configuration affect every selecting block.

Run native content/core tests, `NativeBlockMaterialsTest`, existing meshing
and chunk-save tests, and current v8 `VerifyStateGraphs.py` (which retains v7
sound/offset coverage). Its offset check exhausts
every finite table index through Frozen's real state API and includes extreme
coordinates. The draft native evaluator matches all 9,250 exact samples from
eight configurations, including full coordinate seeds and double bits.

The following results and the arithmetic comparison are
[author-recorded sound/offset evidence](https://github.com/HungLo2020/MattMC/blob/87046367cdf0a4a427f10066a9010dd6d39fd422/PROGRESS.md#milestone-1h--sound-content-and-block-offsets-published-059c95621-performance-target-unmet),
not a fresh acceptance run of the source reviewed here.

The native declarations/evaluator, content/core tests (24), and wider
content/world tests (167) pass. The Java views, four new regressions and v7
observer compile; the integrated release build, 32 Java checks and 24 native
content/core tests pass. Five integrated v7 Frozen pairs match all seven
semantic digests, including every offset sample; see
`build/block-materials-master-verification-20261008/results.json`. Full client verification passes its seven lifecycle and two visual comparisons. Shared
vectors remove a source-level allocation; no measured gameplay gain is claimed.

An arithmetic comparison found 2,562 sampled XYZ y-coordinates where the old
renderer’s intermediate float rounding differs from Frozen’s result cast once
to float. The shared calculation preserves Frozen’s arithmetic. This is a
precision finding, not evidence of a visible defect or its cause.

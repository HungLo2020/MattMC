# Native intrinsic block-state rules

> Current implementation at
> [`d0141162`](https://github.com/HungLo2020/MattMC/commit/d0141162d81eee184fa99f0b7a9411d401c306b4).
> The author's focused/full-client correctness results below passed;
> the overall performance target remains unmet.

## Ownership

The [intrinsic rule owner](https://github.com/HungLo2020/MattMC/blob/d0141162d81eee184fa99f0b7a9411d401c306b4/src/main/rust/content/block/definitions/intrinsic.rs)
evaluates emitted light, semantic map color and canonical fluid association
once for every registered state. The
catalog selects 205 shared rule sets in `intrinsic/declarations.rs`. Rules use
typed native properties: lit lights, candle/pickle counts, log axes, bed parts,
growth stages, spawner/vault states and waterlogging. These are executable
content rules, not a checked-in dump of 31,809 state results.

Native consumers read immutable `StateTraits` by `StateId`. The remaining-fact
packet uses format 7 and no longer imports emitted light or fluid IDs from
Java. Shapes, blocked light and contextual gameplay still await migration.
Map palette identities are native; palette RGB values/shading remain separate
work. Render-owned textures, materials and tints are unaffected by these rules.

Fluid selection goes through the native fluid owner's semantic lookup. It
preserves both source/falling variants, including copper grates' falling source
water. Do not collapse those variants into a generic “waterlogged” fluid ID.

## Temporary Java views

The indexed state factory passes each native state's packed traits to its
Java constructor. Cached getters stay local and do not call FFM. A per-view
admission flag distinguishes native intrinsic data from manually constructed
compatibility views; the latter still initialize their fluid through the
existing callback.

Copied `Properties` functions use bounded lookup tables generated from the
native rules. Tables include only the properties each function needs, so a
constant color works with any state, and a copied log-color function needs
only its axis. Registered state construction reads native columns directly.
No Java callback independently declares the registered color/light policy.

The bridge borrows process-lifetime CPU buffers: packed state rows and finite,
deduplicated scalar-rule descriptors, property IDs and values. Missing or
inconsistent domains fail initialization. There is no runtime-input cache,
GPU resource ownership or per-frame native call in this projection.

## Editing and verification

Edit the shared rule set selected in `catalog.rs`; add a typed rule in
`intrinsic.rs` when existing rules cannot express the behavior. Declare its
minimal property dependencies too. Startup checks that each projection covers
its domain and gives one answer for every combination of those dependencies.
Do not add Java declarations for registered map colors, emission or fluid IDs.

Run [the content and state checks](BLOCK-DEFINITIONS.md#working-on-this-slice),
including `NativeBlockIntrinsicTest`. Its regressions compare copied functions
with every native state, exercise a copied rule on a different block sharing
only its required properties, and check manually constructed fluid states.
Version 6 of `DevUtils/tests/content/VerifyStateGraphs.py` adds cached/default
map colors and copied color/emission functions to the retained graph, fluid,
property, physical and block-state digests. Use a new output directory and
run without competing builds, games or profilers. The map-color probes use
`EmptyBlockGetter` at `BlockPos.ZERO`; see the
[observer scope and integrity limits](STATE-GRAPHS.md#verification). A matching
receipt does not establish general contextual behavior or a performance gate.

The following results are author-recorded. This documentation review did not
rerun their suites or inspect the local receipts. They describe this milestone,
before the sound/offset follow-up mentioned in the source summary.

Before integration, the native evaluator matched all 31,809 Frozen state IDs,
map colors, light levels and fluid IDs. Twenty standalone content tests and
compilation of six production Java classes plus three regression tests passed.
Frozen remained unchanged; evidence: `build/block-intrinsic-migration-draft/`.
The integrated release build, 20 native content tests and 27 Java ownership,
registry, graph, meshing and save tests pass. Five v6 Frozen pairs match all
six semantic digests, with sources/native hashes verified and Frozen unchanged:
`build/block-intrinsics-master-verification-20261008/results.json`. Bootstrap
medians were Current 2.288/Frozen 2.314 s; JVM main-thread allocation
956.74/1069.62 MB
(−10.6% for the combined content migration). No isolated speedup is established.

Full `validation/native-block-intrinsics-master-20261008/` verification passed
1,704 Java tests (2 skipped), 2,359 Rust tests (3 ignored), all seven lifecycle
scenarios and both reviewed static coast pairs. Vanilla RGB differences were
0.312/0.515/0.600; Iris+DH 3.763/4.359/3.997, with its DH subset passing. No
Vulkan validation errors or owned process orphans were found.

All 16 paired 6,000-frame runs were clean. Vanilla/DH average FPS and p99
remain below Frozen; both shader modes pass this run. Root `SUMMARY.md` records
both repeats and tail values. Sources/native hashes match the observer and
Frozen remains unchanged; 25 generated fixture copies were retired. These
static/lifecycle checks do not prove every gameplay case or complete migration.

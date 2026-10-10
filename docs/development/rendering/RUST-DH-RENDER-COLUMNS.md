# Rust DH render-column reduction

Rust owns the admitted complete compact render-column reduction.
Ordinary `RenderDataPointUtil.mergeMultiData` calls borrow existing canonical
column storage and publish directly into the destination; Java does not pack a
point buffer, construct reducer nodes, sort links or repeat reduction policy.
Full-data conversion still performs contextual block-color callbacks in Java.
[Cached DH tint neighborhoods](../world/biome/RUST-DH-TINT.md) use retained
native column/material inputs and Rust cache storage, with recorded Frozen CPU
replay and scoped timing verification. Face construction, greedy merging and
final packet construction also remain.
This is backend-independent CPU render policy, with no GPU handles or presenter.

Source reviewed at [`5de23173a`](https://github.com/HungLo2020/MattMC/commit/5de23173ae5b61fb17887589d4dff69a6b17fc77) on 2026-10-10; the release results below are
implementation-author reports, and this review did not rerun suites or clients.
See the [current CPU checkpoint](GOAL-5-STATUS.md#october-10-retained-world-and-dh-reduction-cpu-checkpoint)
for remaining runtime, performance and sustained-memory acceptance gates.

Implementation: [native reduction](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/dh_columns),
[storage admission](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/NativeDhRenderReduction.java)
and [ordinary consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/util/RenderDataPointUtil.java).

## Constraints

- Preserve packed alpha visibility, omitted zero-height/invisible entries,
  synthetic air gaps and overlap rejection. Keep the original unstable
  fastutil8.5.12 sort permutation: equal-size ties affect surviving colors.
- Preserve the two small-segment passes, adjacent equal-alpha merging,
  middle-segment erasure, forced bottom merging and highest-to-lowest output.
  Water/nonwater separation and highest water-surface restoration follow
  Frozen, including its single-output fast path and opaque material categories.
- Exact-material companions follow the current semantic extension: merging
  different visible identities produces mixed identity; restored water keeps
  its original companion. The semantic one-output path runs normal reduction,
  while the ordinary path uses Frozen's separate `reduceToOne` rule.
- Sources and destinations may alias. Complete native preparation precedes
  writes; invalid geometry declines without native partial publication, then
  the original Java path delivers its callbacks and exceptions. Existing caller
  exclusion is still required; this does not create a concurrent transaction.
- Admit the final `ColumnArrayView` class backed by exact `LongArrayList`
  storage and bounded extents. The bridge uses `instanceof ColumnArrayView`
  and an exact-class check for its backing list. Custom `IColumnDataView`
  implementations/lists, malformed extents and oversized input retain the original
  sequence. Ordinary zero-target output does not inspect the source. Semantic
  array validation remains before admission. Empty-view edge cases retain
  compatibility behavior rather than inventing a new write contract.
- Up to4,096 source/output entries admit. One native thread retains scratch
  capacity bounded to8,192 nodes,8,192 sort indices and4,096 output pairs.
  It contains numeric values only, retains no source pointer/world/Java object,
  and is reclaimed with that thread. Repeated normal calls reuse these vectors;
  no global world or resource cache is added. These per-thread limits are not
  a process-wide memory bound; long-session memory still needs verification.

## Verify

Use JDK25 and consistent release settings:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib render::dh_columns::
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.vulkanic.world.NativeDhRenderReductionTest
python3 DevUtils/RunWiki.py check
```

[`GenerateFrozenDhRenderReductionOracle.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/GenerateFrozenDhRenderReductionOracle.java)
executes the untouched Frozen reducer with six compiled-class pins. Its3,616
synthetic CPU cases cover empty/all packed material IDs, water, gapped/shuffled
inputs, equal-size ties, sort thresholds, the4,096-entry bound and overlapping
geometry. It records actual outputs, exception classes and surrounding sentinel
storage; the expected reduction algorithm is not copied. This is not saved-world
rendering, admission profiling, performance or gameplay evidence.

Release `ee1676ec` passes all four focused Rust controls and27 architecture
checks. The five JNI controls replay3,616 Frozen cases through native admission
and the ordinary consumer, and576 supplemental current semantic cases through
the actual compatibility reducer. Aliasing, custom getter/exception prefixes,
zero-target reads and oversized inputs pass controls. Independent worker tests
confirm scratch reuse and bounded capacities.

Full Java reports1,755 ordinary tests (two skipped) and159 parity tests, zero
failures/errors across343 suites. Six final workers map the exact release. All
39,492 Current/38,176 Frozen source pins, source inventories, native image and
protected prompt match; Frozen HEAD remains unchanged. Receipts:
`build/native-dh-render-reduction/`. The initial fixture-include compilation
rejection is retained; the corrected, formatted sources pass the final gates.
GPU runtime, realistic Frozen comparisons and long-session memory remain pending
a matched NVIDIA driver; older renderer timings do not accept this image.

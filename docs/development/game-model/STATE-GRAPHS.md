# Native state graphs

> Current work: state construction and registered block state definitions are
> native; Java state objects, property codecs and most gameplay remain. Fluid declarations
> and intrinsic state facts now come from [Rust](FLUID-DEFINITIONS.md). This is a step
> toward Phase 2, not complete Rust content ownership.

[`content/state`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/state)
owns ordered Cartesian state domains and immutable transition graphs. The
last property varies fastest. The shared block registry uses the same
`StateSlot` arithmetic; Java no longer expands streams of property/value pairs
or constructs neighbour targets by copying and searching value maps.

## Temporary Java views

[`StateDefinition`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/block/state/StateDefinition.java)
projects native value indices into its existing state factory. Registered
blocks borrow graphs from the [native block definitions](BLOCK-DEFINITIONS.md),
which share them by domain sizes. Other definitions supply sorted domain sizes
to the native constructor. `StateHolder` projects transition IDs into reference
arrays; hot property updates perform no native call.

Definitions and states retain `NativeStateGraph`. Built-in block graphs borrow
immutable process-lifetime buffers and never release them. Dynamically created
graphs use an automatic arena that releases their owner after all views become
unreachable. These semantic CPU buffers are unrelated to GPU resources or GAL
handles.

Native layouts reserve `0xffff`: at most 65,535 states per domain. The bridge
bounds each materialized values/transition buffer to 16,777,216 entries.
Invalid or oversized domains fail construction; no Java graph fallback exists.
Pure Rust `StateLayout` supplies compact slot arithmetic without materializing
the graph. Future native definitions should build through this owner.

## Verification

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests 'net.minecraft.world.level.block.state.NativeStateGraphTest' \
  --tests 'net.minecraft.world.level.block.NativeBlockRegistryTest' \
  --tests 'net.sodium.client.render.chunk.compile.pipeline.NativeMeshingStateViewTest'

python3 DevUtils/tests/content/VerifyStateGraphs.py \
  --java-home /path/to/jdk-25 --output build/state-graph-verification
```

The [driver at this review](https://github.com/HungLo2020/MattMC/blob/a908f78cd909200f5f4f4424b124072cef0a17f6/DevUtils/tests/content/VerifyStateGraphs.py)
requires a new directory under this checkout's `build/`. It builds Current,
reads Frozen's existing main classpath, compiles one observer against Frozen
and alternates five fresh JVM pairs by default (`--pairs` permits at least
three). Use `--frozen-repo` for a separate reference checkout whose main
classes were built beforehand. Both sides use identical JVM flags without a
profiler.

Receipts record semantic digests, bootstrap time and JVM main-thread allocated
bytes. Allocation excludes other threads and native memory; it is not total
process memory. Semantic mismatch, incomplete/invalid receipts, failed
processes or changed measured sources/native library reject the run. The
driver reports timing/allocation ratios but enforces no speedup threshold.
These measurements cover bootstrap, not gameplay FPS.

Integrity checks hash Current's enumerated sources and native library, plus
the observer source, driver and compiled observer class; Frozen is identified
by its path, Git HEAD/status and existing classpath. The driver rechecks Frozen's
Git identity, but does not individually hash its precompiled class bytes.
Treat that reference-build provenance limit separately from semantic equality.

The [reference observer](https://github.com/HungLo2020/MattMC/blob/a908f78cd909200f5f4f4424b124072cef0a17f6/DevUtils/tests/content/StateGraphReference.java)
uses identical public APIs on Current and Frozen. It hashes every definition,
property domain, ordered state, default and single-property transition across
blocks and fluids. Version 2 also emits an independent digest for fluid IDs,
intrinsic facts, legacy block IDs and codec outputs/identity round trips.
Version 3 adds all property declarations/codecs; version 4 adds block-state
flags, lighting, fluid associations, two sampled offsets and light-occlusion
boxes. Version 5 adds 18 physical settings, matching superclass caches where
present, and seven cached physical facts per block state. Version 6
adds default/cached map-color IDs, copied color/emission functions and canonical
fluid associations. Version 7 also checks sound-event identities/ranges,
profile/instrument references and per-state sound/instrument bindings. Its
offset coverage exhausts finite indices and extreme coordinates; see
[sounds and offsets](BLOCK-SOUND-AND-OFFSETS.md). Version 8 adds block-set/wood
definitions, codec/alias identities and registered family parameters; see
[block families](BLOCK-FAMILY-TYPES.md). Current version 10 also includes
[map palette](MAP-COLORS.md) and [state-policy](STATE-POLICY.md) digests, for ten
in total. The observer/receipt version, definition schema 5, registry format-9
remaining-fact packet and rendering ABI 74 are separate contracts.

The policy digest's leaf bit still uses Java `instanceof LeavesBlock` on both
sides. It does not directly verify Rust's leaf column; `NativeBlockPolicyTest`
checks the native registry flag against the Frozen fixture separately.
Other contextual probes use `EmptyBlockGetter` at `BlockPos.ZERO`; they do not cover arbitrary worlds.

The observer's graph count combines block and fluid states; the recorded
31,846 total comprises 31,809 block states plus 37 fluid states. Matching
digests cover the inspected semantics and samples, not all contextual
collision/gameplay, rendering or save-lifecycle behavior. Use repeated
unprofiled measurements and the
[full rendering workflow](../rendering/RENDER-VERIFICATION.md) for runtime
acceptance. This documentation review at `a908f78c` inspected source only,
without rerunning suites, reading local runtime receipts or reviewing images.
The author records five v7 pairs for the
[sound/offset milestone](SOUND-DEFINITIONS.md#editing-and-verification) and
five v8 pairs for the [family milestone](BLOCK-FAMILY-TYPES.md#editing-and-verification),
alongside their full-client workflows. Later v10 map/state-policy results are
recorded on [their own pages](MAP-COLORS.md#editing-and-verification). Those
separate records remain scoped evidence; overall performance acceptance still
fails, and full gameplay and Rust-only application acceptance remain incomplete.

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

The [driver](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/VerifyStateGraphs.py)
requires an unused output directory. It builds Current, reads the existing
Frozen main classpath, compiles the same observer against Frozen and alternates
five fresh JVM pairs. Use `--frozen-repo` for a different reference location;
build that checkout beforehand through its normal workflow. Both sides use
identical JVM flags without a profiler. Receipts include every graph's combined
semantic digest, bootstrap time/main-thread allocation, source/native hashes
and reference identity. A mismatch, incomplete receipt, failed process or
changed measured source rejects the result. Bootstrap measurements are scoped
to registry startup; they are not gameplay FPS.

The [reference observer](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/content/StateGraphReference.java)
uses identical public APIs on Current and Frozen. It hashes every definition,
property domain, ordered state, default and single-property transition across
blocks and fluids. Version 2 also emits an independent digest for fluid IDs,
intrinsic facts, legacy block IDs and codec outputs/identity round trips.
Version 3 adds all property declarations/codecs; version 4 adds block-state
flags, lighting, fluid associations, sampled offsets and light-occlusion boxes.
Matching hashes establish those semantics, not complete gameplay, rendering
or save-lifecycle parity. Profiled bootstrap allocation
samples are diagnostic; require repeated unprofiled measurements and the
[full rendering workflow](../rendering/RENDER-VERIFICATION.md) before promoting
runtime changes. Run work from `master` in the MattMC checkout.

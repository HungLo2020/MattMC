# Native fluid definitions

> Current implementation: Rust owns all five built-in fluid definitions and
> the intrinsic facts of their 37 states. World-dependent flow, ticks,
> replacement, sounds, buckets and block interactions still use Java adapters.
> This is part of Phase 2; fluid simulation has not migrated yet.

## Ownership and compatibility

[`content/fluid`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/fluid)
builds the ordered process-lifetime registry without Java declaration input.
It owns names, family/source selection, property declarations, default IDs,
explosion resistance and state amounts, source flags, heights and legacy block
levels. It uses the shared [state layout](STATE-GRAPHS.md) and exposes typed
`FluidId` and `FluidStateId` access for later native gameplay consumers.

Keep order `empty`, `flowing_water`, `water`, `flowing_lava`, `lava`, with first
state IDs 0, 1, 17, 19, 35. `falling` enumerates **true before false**; the
flowing level domain is 1–8, varying fastest. Defaults are local state zero,
including `falling=true`. Preserve this ordering even when it seems surprising.

[`Fluids`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/material/Fluids.java)
registers compatibility objects in native order and checks every fluid/state
ID. Its public constants are named views. Adapter selection binds remaining
Java callbacks to a native family; adapters do not declare state properties or
calculate intrinsic facts. `FluidState` reads immutable projected facts without
FFM calls on its hot getters. Java still supplies property objects and codecs.

The versioned bridge borrows immutable CPU buffers from Rust's `OnceLock`.
They live for the process and require no release. Java copies small metadata
and trait records once, checks schema/bounds, and has no declaration fallback.
These buffers contain no rendering resources or GPU handles. `content` must
remain independent of world/rendering consumers. Change the schema and Java
decoder together when adding columns; append new definitions to preserve IDs.

## Verification

```sh
CARGO_TARGET_DIR="$PWD/build/rust/target-tests" cargo test \
  --manifest-path src/main/rust/Cargo.toml --locked --lib content::fluid

./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests 'net.minecraft.world.level.material.NativeFluidDefinitionsTest' \
  --tests 'net.minecraft.world.level.block.NativeBlockRegistryTest' \
  --tests 'net.minecraft.world.level.block.state.NativeStateGraphTest'

python3 DevUtils/tests/content/VerifyStateGraphs.py \
  --java-home /path/to/jdk-25 --output build/fluid-definitions-verification
```

The independent observer compiles against Frozen's existing classes and runs
the same public API on both sides, without modifying Frozen. Its graph digest
covers all block/fluid domains, ordered states, defaults and transitions. A
separate fluid digest covers all names/IDs, default IDs, intrinsic traits,
legacy block IDs and encoded codec outputs; every codec round trip must return
the canonical state. Version 2 receipts add that fluid digest while retaining
the original graph digest. Use a new output directory for each run.

These checks establish definition/state/codec parity and fresh-JVM bootstrap
measurements, not world-dependent fluid simulation or complete gameplay
performance. Continue migration while tracking the remaining performance gap;
use [real client verification](../rendering/RENDER-VERIFICATION.md) to measure
each production slice and diagnose regressions.

## Current evidence (2026-10-08)

The five fresh-JVM pairs in the original checkout's
`build/fluid-definitions-master-verification-20261008/results.json` agree with
Frozen: 1,235 blocks, five fluids, 31,846 states and 491,395 graph transitions.
The unchanged graph digest is
`989fd061714809653c436cae3e4931044af50fa0f4c4ade82c28b82140540e23`;
the fluid/codec digest is
`88ec5a63f0f0f999c863525582a4616c0772edf7fa41707e285ca1bfef7c05b6`.
Source/native hashes matched and Frozen remained unchanged.

Median bootstrap time was Current 2.267 s versus Frozen 2.233 s (+1.5%);
main-thread allocation was 958.99 versus 1066.39 MB (−10.1%). This includes
the earlier native state-graph migration, so it is not an isolated fluid
speedup. Two native fluid tests and ten focused Java projection/registry/graph/
meshing tests pass. The full workflow at
`artifacts/graphics-captures/validation/native-fluid-definitions-master-20261008/`
completed: Java 1,697 passed/two skipped, Rust 2,351 passed/three ignored, all
seven lifecycle scenarios passed, and the reviewed vanilla/shaders+DH coast
comparisons passed with zero VUIDs. All 16 ABAB FPS receipts were complete and
clean; 25 generated fixture copies were retired.

Performance acceptance **failed**: median average FPS was 6.0%/18.6% below
Frozen for vanilla/vanilla+DH and 8.3%/9.5% above for shaders/shaders+DH. All
four p99 comparisons failed; shader-only p99 was worse than earlier captures,
without an established cause. The root `SUMMARY.md` retains both repeats;
this is a verified ownership slice with an open performance gap, not full
renderer or migration acceptance.

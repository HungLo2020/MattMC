# Rust loaded-section snapshots

Chunk rebuilds now capture canonical block states once into immutable Rust
storage. The existing 512-entry, five-second cloned-section cache retains these
captures. Rebuild slices borrow them directly; ordinary sections no longer clone
a Java palette and expand it into 4,096 Java object references per slice.
Rust also builds the mesher's 18³ state-ID neighbourhood in one bulk call.
Java still computes contextual light and admits distinct states to model metadata.

Snapshots are immutable rebuild state. [Live block sections](RUST-LIVE-SECTIONS.md)
now own ordinary canonical mutation separately and produce captures directly in
Rust. Compatibility containers still copy packed words and palette identities at
capture. Scheduling, biome/light snapshots, chunk orchestration and remaining
gameplay callbacks use Java; world-generation stage owners remain separate.

## Working on the path

- [`NativeBlockSectionSnapshot`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeBlockSectionSnapshot.java)
  accepts standard canonical block containers, captures native live owners
  directly, and exposes a read-only CPU view of immutable native states.
- [`chunk/snapshot/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/snapshot)
  owns decoding, immutable storage and neighbourhood reads, without rendering
  dependencies. Every capture owns 8 KiB; inputs and Java objects are not retained.
- [`LevelSlice`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/sodium/client/world/LevelSlice.java)
  retains captures through rebuild execution. Java callbacks resolve canonical
  objects from the CPU view without downcalls. Compatibility arrays are allocated
  only for unsupported containers and debug-world substitutes.

Custom palette/storage/registry/strategy implementations keep their original
clone/unpack path without extra callback probes. Preserve object identity,
coordinate order, air for absent sections, bounds and original provider behavior.
An invalid native capture declines before publication; malformed compatibility
inputs retain the original Java access behavior. Native padding requires the
exact centre and an entirely native/empty neighbourhood within the slice volume.

## Lifetime and bounds

A capture's automatic FFM arena releases its Rust allocation only after the CPU
view becomes unreachable. Cache eviction does not invalidate queued rebuilds;
those rebuilds retain their captures. The bulk call fences its owner array so GC
cannot reclaim borrowed pointers during native access. No mutable native view is
exposed. Explicitly scoped arenas are used only by tests.

Reset clears all **27** section slots, including captures, model data, lights and
block-entity references. The former three-slot loop retained unrelated sections
between tasks. Native memory is bounded by the existing cache and outstanding
rebuild owners; there is no native global snapshot registry or duplicate cache.
Automatic reclamation follows GC rather than occurring immediately at eviction.
GPU ownership and compact mesh header4/whole-frame ABI74 are unchanged.

## Verification

Use JDK25 and the release native library for Java tests:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml world::level::chunk::snapshot
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests net.minecraft.world.level.chunk.NativeBlockSectionSnapshotTest
python3 DevUtils/RunWiki.py check
```

Tests compare every position against original container reads at every palette
boundary and across 324 recorded world sections. They cover later writes/resizes,
aliases, custom callbacks, all halo faces/edges/corners, missing sections,
invalid inputs, arena expiration and concurrent GC readers. These are supplemental;
real streaming, lifecycle, Frozen image and performance evidence is required
before this slice is accepted as a performance improvement.

On 2026-10-09, native2409/Java1739 checks passed (3 ignored/2 skipped), including
seven new Java snapshot tests. Final Java view-adoption ordering hardening
passes38 affected checks;full Java and benchmarks precede this hardening.
The native library is unchanged. The release SHA`15c7ba5e` passed all seven lifecycle
cases and reviewed vanilla/Iris+DH image pairs with VUID0. All16 moving-camera
ABAB runs were clean and exactly6,000 frames, but vanilla FPS/p99 and both shader
p99 floors **failed**. DH repeats vary substantially. No isolated throughput
improvement is established. Results: `validation/native-chunk-state-snapshots-20261009/summary.json`.

Paired normal-flight CPU profiles and F3 positions pass in
`goal5/chunk-state-snapshots-flight-profile-v3-20261009/`. Both sides start at
150.5/95/530.5 and cross seven X chunk columns to block266/95/561, with about
half-block terminal drift. Capture+slice preparation fell35→24 sampled stacks
in8s; downstream snapshot work rose313→334. Small diagnostic samples and the
observer overhead prevent a speedup claim. Two rejected Current recordings are
preserved; the observer now retains microsecond timestamps in FFV1/NUT, without
relaxing its validator. Before recordings used Matroska. Comparison:
`build/map-policy-performance-profile/chunk-state-profile-comparison.json`.

Final hardened source passes a fresh reviewed Iris+DH pair (RGB3.746/4.309/3.992,
VUID0, source/library/Frozen integrity and owned orphans0). Receipt:
`goal5/chunk-state-snapshots-final-readonly-proof-20261009/receipt.json`.

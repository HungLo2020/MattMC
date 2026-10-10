# Rust loaded-section snapshots

Chunk rebuilds now capture canonical block states once into immutable Rust
storage. The existing 512-entry, five-second cloned-section cache retains these
captures. Rebuild slices borrow them directly; ordinary sections no longer clone
a Java palette and expand it into 4,096 Java object references per slice.
Rust also builds the mesher's 18³ state-ID neighbourhood in one bulk call.
Rust now also prepares canonical [terrain light words](../../rendering/RUST-TERRAIN-LIGHTING.md)
from retained generations. Java supplies contextual predicates/shade and admits
distinct states to model metadata; compatibility inputs retain scalar preparation.

Snapshots are immutable rebuild state. [Live block sections](RUST-LIVE-SECTIONS.md)
now own ordinary canonical mutation separately and produce captures directly in
Rust. Compatibility containers still copy packed words and palette identities at
capture. Java orchestrates scheduling, biome/light captures, chunks and remaining
gameplay callbacks; admitted [biome containers](../biome/RUST-LIVE-BIOMES.md) and
[light layers](../lighting/RUST-LIVE-LAYERS.md) retain separate native owners.
World-generation stage owners remain separate Rust
representations, with [native capture/adoption](../levelgen/RUST-STAGE-HANDOFF.md)
for canonical NOISE/SURFACE/CARVERS transfers.

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

Unsupported palette/storage/registry/strategy implementations keep the original
clone/unpack path. The compatibility capture checks `getSize()` and, when the
size matches, `getBits()` before checking the exact storage class; custom storage
can therefore observe these additional probes. Do not promise callback-free
admission. [Capture guard order](https://github.com/HungLo2020/MattMC/blob/a908f78cd909200f5f4f4424b124072cef0a17f6/src/main/java/net/minecraft/world/level/chunk/NativeBlockSectionSnapshot.java#L64-L80).
Preserve object identity,
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
between tasks. The 512-entry cache bounds its own retained entries; outstanding rebuild owners
and GC-delayed reclamation also retain memory. This is not a fixed total-memory
cap. There is no native global snapshot registry or duplicate cache.
Automatic reclamation follows GC rather than occurring immediately at eviction.
That snapshot milestone used compact header4/whole-frame ABI74. The later
[native face policy](../../rendering/RUST-TERRAIN-CULLING.md) uses private compact
header5 and consumes these same retained IDs; GPU ownership is unchanged.

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

The author reports that on 2026-10-09, native2409/Java1739 checks passed (3 ignored/2 skipped), including
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

These are author-recorded results from the [snapshot checkpoint](https://github.com/HungLo2020/MattMC/blob/9afdb7d2cbca29a298e3ca0a4024a78eba93ed64/PROGRESS.md);
this documentation review did not rerun suites, profiles or captures or inspect
the unbundled receipts.

Final hardened source passes a fresh reviewed Iris+DH pair (RGB3.746/4.309/3.992,
VUID0, source/library/Frozen integrity and owned orphans0). Receipt:
`goal5/chunk-state-snapshots-final-readonly-proof-20261009/receipt.json`.

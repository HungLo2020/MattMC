# Rust live light layers

At [`4246f4e7`](https://github.com/HungLo2020/MattMC/commit/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953),
canonical `DataLayer` objects have a Rust owner. Lazy raw
integer defaults and allocated 2,048-byte nibble generations live in
[`lighting/layers/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/lighting/layers).
Java reads a leased CPU view; mutations, independent copies and sky-layer
repetition run in Rust. This subsystem has no rendering or GPU dependencies.

## Handoffs

[`NativeLightBlocks`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeLightBlocks.java)
passes a pinned CPU owner to propagation. Rust takes an independent snapshot
without a Java byte-array projection. This still copies native bytes into a
pass-local snapshot; it is not zero-copy propagation. Results export ordered section keys and
affected sections only. After Java performs the existing map copy-on-write,
Rust installs each result directly into its target owner. Sky seeding and first
row repetition also keep canonical light bytes native. Client packet application uses
`DataLayer.copyOf` for its already independent import: Rust copies the packet
payload directly instead of first allocating a Java clone. The public array
constructor retains its existing alias contract.

Java still owns light-engine orchestration, map publication and callbacks.
The [bulk terrain-light consumer](../../rendering/RUST-TERRAIN-LIGHTING.md)
now borrows retained generations directly; its latest verification is recorded
separately. Compatibility slices still use scalar CPU views. No whole-game
speedup is established.
Follow the [Java handoff](https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/java/net/minecraft/world/level/lighting/NativeLightPropagation.java#L204-L259)
and [native installation](https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/src/main/rust/world/level/lighting/propagation/ffi.rs#L230-L242)
when changing publication: result-transfer errors throw and do not roll back
sections already installed. Unsupported input/callback rejection replays Java
before result installation, as described in [propagation](RUST-LIGHT-PROPAGATION.md#how-a-pass-works).

## Compatibility and lifetime

- Preserve raw defaults outside 0..15. Lazy reads ignore invalid indices;
  invalid writes materialize before the original array exception.
- Existing allocated-generation views observe writes. `fill` detaches; old
  leased views stay valid. Copies are independent and allocated copies reset
  the raw default to zero, matching the original byte-array constructor.
- The public byte-array constructor preserves its caller's mutable alias.
  `getData()` explicitly transfers an owned layer into one persistent Java
  array; it is an ownership escape, not a temporary read-only export.
  Later writes use that array until a fill or independent copy adopts
  native ownership again. Subclasses retain their original Java callbacks.
- CPU owner and view leases have separate automatic arenas. A view retains its
  generation after owner release. Synchronous callbacks pin their source until
  Rust finishes reading; no borrowed Java or owner pointer survives the pass.
- Preserve original light-storage caller exclusion and map copy-on-write.
  Atomic native cells do not replace the world publication transaction.

## Verification

The commands below are the current focused checks. The results that follow are
the implementation author's [committed live-light record](https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/PROGRESS.md#L81-L87)
and [release summary](https://github.com/HungLo2020/MattMC/blob/4246f4e7bfc1f3ab7862272ebba5f1f38aa16953/SUMMARY.md),
not independent runtime verification by this documentation review. Its
release `31c8c8cc` measurements precede the ABI 78 GUI/harness changes.

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml world::level::lighting
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeLightLayerTest' --tests '*NativeLightPropagationTest' --tests '*NativeLiveBlockSectionTest' --tests '*NativeBlockSectionSnapshotTest'
python3 DevUtils/RunWiki.py check
```

The retained Frozen oracle covers 108 representations and all 4,096 cells per
representation, including unusual defaults, copies, writes, allocation and
fill. `frozen-data-layer.rle` losslessly stores those original outputs; it is not
a native-generated expectation. Native lifetime and invalid-access checks pass.
The full Rust suite passes 2,436 cases (3 ignored), and full Java passes
1,800 (2 skipped). The focused integrated
lighting suite passes 18 Rust cases and 29 Java cases,
including the existing saved/generated terrain propagation and storage checks.
The client import checks also pass. Release `31c8c8cc` passes all seven lifecycle
cases and inspected vanilla/Iris+DH coast pairs, including DH coverage. All
sixteen ABAB runs are clean and exactly 6,000 frames, with VUID/exception/orphan
counts zero. Performance acceptance **fails** vanilla FPS/p99 and DH p99:
vanilla median FPS is 1,096 versus Frozen 1,153; p99 is 3.781/3.439 ms. DH
p99 is 5.524/5.519 ms. Shader and shader+DH FPS/p99 floors pass this set.
These results do not isolate this storage change or establish a speedup.
Final source, native-library, Frozen and protected-user-edit integrity checks
pass. Twenty-five generated copies were retired. The original runtime invocation
has since been retired by the existing retention policy. Historical results and
integrity remain in `build/native-light-migration/runtime-final.log` and
`production-verification.json`; the retained flight comparison below is separate.
Long-session memory remains work. The later
[bulk consumer](../../rendering/RUST-TERRAIN-LIGHTING.md) has its own verification.

Paired ordinary-flight CPU/allocation profiles pass source/library identity,
movement-window, cleanup and reviewed F3 position checks. Both allocation runs
start at 150.5/95/530.5 and finish at 266.692/95/561.626, crossing seven X chunk
columns. Weighted Java allocation is 0.934 GB Current versus 2.186 GB Frozen
in one eight-second window each; this is a diagnostic estimate, not an isolated
storage benefit. Current `computeLightWord` still samples 41.94 MB, largely
positions, and light-layer stacks include 84.93 MB; the native owner/view binding
accounts for 8.39 MB. Categories overlap. Current CPU contains substantial JIT
compilation, so the pair cannot establish steady-state throughput. Four generated
copies were retired. Comparison: `goal5/native-live-light-flight-profile-20261009/profile-comparison.json`.

Regenerate the recorded oracle against the untouched Frozen build with JDK25:

```sh
FROZEN_ROOT=/home/matt/Documents/Repos/MattMC_JavaPerfTesting/MattMC
mkdir -p build/native-light-migration
javac -cp "$FROZEN_ROOT/build/classes/java/main" -d build/native-light-migration DevUtils/tests/lighting/GenerateFrozenLightLayerOracle.java
java -cp "build/native-light-migration:$FROZEN_ROOT/build/classes/java/main" GenerateFrozenLightLayerOracle build/native-light-migration/frozen-layer-regenerated.rle
cmp build/native-light-migration/frozen-layer-regenerated.rle src/main/rust/world/level/lighting/layers/frozen-data-layer.rle
```

The generator refuses a class other than the recorded Frozen `DataLayer`
(SHA256 `fa57ffc1904d3844c390aeccff1cdfe8f88f7f3b1fa62b15a1d1e59b31b3f795`).
Regeneration matches the retained fixture byte for byte. Use the existing Frozen
build; do not change its implementation to produce expectations.

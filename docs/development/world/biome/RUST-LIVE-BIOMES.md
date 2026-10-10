# Live biome ownership and sky sampling

The migration moves canonical 4³ biome palettes and packed storage into
Rust, together with a loaded-chunk index and its direct sky-color consumer.
Java retains scoped CPU views for ordinary biome reads. Chunk load, packet
replacement, section replacement, view-center changes and unload update the
native index. Rust retains typed section owners, resolves biome IDs against a
world's immutable color table and reuses its cubic window while source
generations remain current. Java does not build a 216-color array each frame.

## Boundaries to preserve

- Only the standard factory's frozen `MappedRegistry` binding is admitted.
  Registry IDs and holder identities are captured once. Custom strategies,
  palettes, storage, unsealed registries and unsupported widths retain Java.
- Preserve imported padding, unused palette entries, first-use growth order and
  requested global width. Zero-width copies share their single-value generation;
  a successful single-value network read changes every alias. Growth detaches
  the growing owner. Invalid imports must not mutate the old owner.
- Native views have independent generation leases. Canonical Java reads use
  atomic CPU spans rather than one native call per cell. Compatibility mutation
  invalidates native residency before returning ownership to Java; retained
  old views remain alive. Single-value compatibility escape transfers every
  still-shared copy to one Java palette; growth has already detached its owner.
  Repeated network reads and later copies must preserve that shared palette.
  Serialization and generic enumeration still use
  temporary compatibility projections.
- Native residency follows the existing client storage, including hidden
  out-of-range chunks. Sampling applies the current view range and height clamp;
  missing chunks use the world's Plains biome. A loaded compatibility chunk
  causes native sampling to decline. Do not confuse that with an absent chunk.
- Section replacement listeners and packet batching preserve native identity
  without rebuilding the index for every section in a packet. Mutable `getSections()` access permanently transfers that chunk index to
  compatibility. Trusted synchronous readers use `getSectionsForRead()`; custom
  overrides retain their original dispatch. Test raw-array replacement, custom
  mutation and partial packets before broad acceptance.
- The native index and cache have bounded residency and world epochs. These
  are CPU world data; no GAL resource, GPU handle or presentation owner changes.
  If bounded index admission fails, the bridge disables native sampling and
  immediately clears residency/cache pins instead of retaining them until GC.

## Working and checking

Storage: [`chunk/biomes/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/biomes).
Direct consumer: [`biome/live/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/biome/live).
Transitional bridges: [`NativeLiveBiomeSection`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeLiveBiomeSection.java)
and [`NativeBiomeWorld`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeBiomeWorld.java).

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml world::level::chunk::biomes
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml world::level::biome::live
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeLiveBiomeSectionTest' --tests '*NativeLiveBlockSectionTest'
./gradlew -PmattmcRustProfile=release test parityTest -x testRustNative
python3 DevUtils/RunWiki.py check
```

The recorded fixtures come from actual unchanged Frozen palette and cubic
sampler classes, guarded by class SHA256 in the generators under
`DevUtils/tests/world/biome/`. They contain 1,600 palette import/growth/copy/alias
operations and 256 exact double-bit sampler cases. Gzip only compresses recorded
expectations; it never creates them from the migrated implementation. Regenerate
using Frozen's full runtime classpath and JDK25, then compress with
`CompressFrozenBiomeOracles.py --biomes BIOME_RECORD --sky SKY_RECORD
--view-range RANGE_RECORD --output-root OUTPUT`.
`GenerateFrozenViewRangeOracle.java` records actual frozen client storage range
decisions, guarded by its class SHA. Preserve Java integer wrapping rather than
widening its subtraction/absolute-value semantics.
These fixtures are supplemental CPU evidence, not runtime or throughput proof.

## October 9 verification

The final range fix preserves Frozen's integer subtraction/absolute-value
wrapping. Its independently recorded 12,288-case storage oracle reproduced the
earlier widened-integer failure. Release `c3aa5fed` passes 2,455 Rust checks
(three ignored) and 1,812 Java tests (two skipped), with zero failures. All six
native-enabled Java executors map that library. Source/Frozen/protected-edit
guards pass; all three fixtures regenerate byte for byte. All seven lifecycle
cases and both newly reviewed vanilla/Iris+DH settled pairs pass, with VUID0,
DH coverage passing and no owned orphan clients. Nine verified generated
copies were retired. No FPS repeat followed this integer-range-only fix;
performance and profile measurements below retain their earlier build identity.
Receipts:
`build/native-biome-migration/final-suite-verification.json`.
`validation/native-live-biomes-range-final-20261009/summary.json`.

The preceding release `bc2207fb` passes all seven shader+DH lifecycle cases
and manually reviewed settled vanilla/Iris+DH diagnostic pairs, including DH
coverage. VUIDs, exceptions, panics and GAL dependency violations are zero.
All 16 ABAB/6,000-frame runs are clean; vanilla p99 fails (3.498ms Current,
3.075ms Frozen). These metrics predate the arithmetic fix. Receipt:
`validation/native-live-biomes-20261009/summary.json`.

Four ordinary streaming profiles validate source, process, library and
movement guards; all 12 F3 images were reviewed and four generated copies
retired. Current's CPU profile samples native sky 13 times and records no Java
sky-grid samples. Weighted Java allocation over eight seconds is 0.974GB
Current/2.319GB Frozen, with sky-grid samples 0/168MB. Rust allocations are
excluded; this does not establish an isolated speedup or total memory gain.
CPU endpoints differ by up to 1.09 blocks; allocation endpoints match. Receipt:
`goal5/native-live-biomes-flight-profile-20261009/profile-comparison.json`.

Ordinary world entry, sustained streaming, temporal rendering defects,
long-session memory and full performance acceptance remain open. Settled
diagnostic images alone cannot establish production-path or broad parity.

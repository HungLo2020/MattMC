# Live biome ownership and color sampling

The migration moves admitted canonical 4³ biome palettes and packed storage into
Rust, together with a loaded-chunk index and admitted sky/fog-color consumers.
Java retains scoped CPU views for ordinary biome reads. Chunk load, packet
replacement, section-biome replacement, view-center changes and chunk unload update the
native index. Rust retains typed section owners, resolves biome IDs against a
world's immutable color table and reuses its cubic window while source
generations remain current. The admitted native path avoids Java's 216-color grid; compatibility
sampling retains its original callbacks.

All suite, runtime and profile outcomes reported below are committed author
records. Their external receipts were not independently inspected or rerun by
this documentation review.

The fog extension published in `3adbe6d5` reuses the same residency and generation capture for
both color fields. The author reports seven focused Rust checks and nine Java boundary checks
passing, including 256 recorded Frozen color cases through live storage.
A world-context fog-hook overload delegates existing hook
implementations to their original method. Native fog admission requires the
exact Sodium hook, client level, client cache and biome manager, with that
manager's immutable source matching the level. Custom hooks and sources keep
their original callbacks. Registries containing biome-effect subclasses also
retain Java rather than capturing virtual color providers as constants.

The same published milestone includes the cache migration, which removes Java's camera/tick-only color
memos. A regression reproduced a stale fog-hook result at an unchanged camera
and tick. Rust retains one exact-position result per field only after validating
the world revision and every captured owner/generation/revision. Replacing or
mutating a biome source invalidates reuse; Java hook, brightness and weather
callbacks execute on every call. The `29e3fd54` results below precede this change.
The author reports seven focused Rust and seven Java cache/boundary checks passing, including live
source mutations, fractional movement within one window and per-call hook
changes. Build `f449557e` passes all 1,818 Java tests (two skipped), and six
native-enabled executors map that exact library. Current/Frozen source and
protected-prompt guards pass. The full Rust suite also passes 2,456 checks (three ignored), and all seven
lifecycle cases pass. Both newly captured vanilla/Iris+DH settled pairs were manually reviewed;
DH coverage and VUID0 pass. All sixteen ABAB/6,000-frame runs are clean.
Vanilla p99 still fails (3.394ms Current/3.322ms Frozen); the other three
modes meet the measured FPS/tail floors. Repeat variance remains substantial,
and no isolated speedup is established. Source/library/Frozen/prompt guards
pass, and 25 verified generated copies were retired. All four ordinary
CPU/allocation profiles validate process, source, library, movement and cleanup
guards. All twelve actual F3 positions were reviewed; forward/return endpoints
differ by at most 0.545 blocks between sides, crossing seven X chunk columns.
Four additional generated copies were retired. Weighted Java allocation is
0.951 GB Current versus 2.437 GB Frozen in eight seconds. Java sky/fog grid
allocation samples are zero Current versus 208.67/12.58 MB Frozen. Current
CPU sampling misses the native sky/fog methods in this window; zero samples
do not establish absence of execution. Current JIT work is substantial, and
Java allocation profiling excludes Rust. No isolated speedup or long-memory
acceptance is established. Lighting-map copies still sample 89.13 MB Current,
guiding the next world-state migration. Broad gameplay and temporal acceptance
remain open. Profile receipt:
`goal5/native-live-biome-fog-cache-flight-profile-20261009/profile-comparison.json`. Runtime receipt:
`build/native-biome-fog-cache-migration/runtime-verification.json`. This finding is not a confirmed cause of
the reported terrain pop-in or flickering.

The `f449557e` results above belong to the fog/cache milestone. They predate
the later manual recorder, Java allocation and lazy mesh-binding changes through
[`90d31038`](https://github.com/HungLo2020/MattMC/commit/90d31038f246143bc2c4449e6b9db896f83e0d58);
those changes have not been remeasured by this documentation review.

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
- Biome-container replacement listeners and packet batching preserve native identity
  without rebuilding the index for every section in a packet. Mutable `getSections()` access permanently transfers that chunk index to
  compatibility. Trusted synchronous readers use `getSectionsForRead()`, which
  returns the same array under a borrow contract, not an immutable copy; custom
  overrides retain their original dispatch. Test raw-array replacement, custom
  mutation and partial packets before broad acceptance.
- The native index and cache have bounded residency and world epochs. These
  are CPU world data; no GAL resource, GPU handle or presentation owner changes.
  If bounded index admission fails, the bridge disables native sampling and
  immediately clears residency/cache pins instead of retaining them until GC.
  Normal world-owner reclamation uses an automatic FFM arena; a new world gets
  a new epoch. This is not an explicit immediate disconnect/reset teardown.

Java still orchestrates chunks, packets, generation and world ticks, and supplies
the registry binding and immutable sky/fog color tables. The native sky path is
admitted through Sodium's hook for an exact `ClientLevel`; unsupported inputs
retain Java sampling. Canonical fog sampling now uses the same native index;
terrain tint/blending and later brightness/weather adjustments remain Java consumers.

## Working and checking

Storage: [`chunk/biomes/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/biomes).
Direct consumer: [`biome/live/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/biome/live).
Transitional bridges: [`NativeLiveBiomeSection`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeLiveBiomeSection.java)
and [`NativeBiomeWorld`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeBiomeWorld.java).

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml world::level::chunk::biomes
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml world::level::biome::live
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeLiveBiomeSectionTest' --tests '*NativeLiveBlockSectionTest'
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeBiomeFogTest' --tests '*NativeBiomeWorldTest'
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

The following suite, runtime and profile results are author reports. Their
receipt files are not tracked at this source revision; this documentation review
did not independently rerun them or inspect those artifacts.

**Local fog build `29e3fd54`:** full Rust passes 2,456 checks (three ignored),
and both Java tasks pass 1,816 tests (two skipped). Six native-enabled Java
workers map that exact library. All seven lifecycle cases and both manually
reviewed vanilla/Iris+DH settled pairs pass, including DH coverage and VUID0.
All 16 ABAB/6,000-frame runs are clean; vanilla p99 fails (3.609ms Current,
3.271ms Frozen). Median FPS exceeds Frozen in all four modes, with substantial
repeat variance. Source/native/Frozen/protected-edit guards pass and 25
verified generated copies were retired. No isolated speedup is established.
All four ordinary profiles validate source/process/library/movement guards;
all twelve F3 captures were reviewed and four generated copies retired.
Native fog appears in the Current CPU profile; Java sky/fog-grid samples
are zero. Weighted Java allocation is 0.955GB Current/2.443GB Frozen over
eight seconds, excluding Rust allocation. Forward endpoints differ by
0.526 blocks for CPU and match for allocation; returns can differ by
0.526 blocks. These are diagnostic measurements, not an isolated speedup.
Profile receipt: `goal5/native-live-biome-fog-flight-profile-20261009/profile-comparison.json`.
Receipts: `build/native-biome-fog-migration/runtime-verification.json` and
`validation/native-live-biome-fog-20261009/summary.json`.

The following results describe the preceding sky-only milestone:

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

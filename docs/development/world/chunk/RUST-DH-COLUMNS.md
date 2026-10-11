# Rust DH column producer

Rust owns the admitted chunk-to-DH column builder, material ID assignment and
immutable CPU point storage. The [full-data owner](RUST-DH-FULL-DATA.md) also owns
mutable recipient storage, parent reduction and direct bulk operations;
[material maps](RUST-DH-MATERIAL-MAPS.md) and
[same-detail updates](RUST-DH-SOURCE-UPDATES.md) own their admitted CPU policies.
Java publishes compatibility views and retains update orchestration, persistence
and contextual hooks. [Compact render-column reduction](../../rendering/RUST-DH-RENDER-COLUMNS.md)
is Rust-owned, as are admitted [cached tint neighborhoods](../biome/RUST-DH-TINT.md).
The ordinary [quad owner](../../rendering/RUST-DH-QUADS.md) now retains
directional lists and greedy topology in Rust. Contextual resolver callbacks,
collector lifecycle orchestration remain Java, with subclass compatibility.
[Complete box production](../../rendering/RUST-DH-VERTICAL-FACES.md) now owns
ordinary visibility, stock shading, light intervals and semantic splits.
[Packet planning/encoding](../../rendering/RUST-DH-PACKETS.md) and
[final column handoff](../../rendering/RUST-DH-COLUMN-HANDOFF.md) now retain
ordinary packet bytes in Rust through ledger publication. Complete Java removal
and broad runtime acceptance remain open.

The producer borrows existing [block owners](RUST-LIVE-SECTIONS.md),
[biome owners](../biome/RUST-LIVE-BIOMES.md),
[height fields](RUST-DH-HEIGHTMAPS.md) and
[lighting fields](../lighting/RUST-DH-LIGHTING.md). Rust captures sections under
their existing locks. The [shared input owner](RUST-DH-CHUNK-INPUTS.md)
passes a retained chunk owner rather than rebuilding Java block/counter/biome
arrays and a pointer triplet buffer on every build. Java passes chunk metadata; it does
not construct dense voxel inputs or output point arrays. Outputs retain no live
chunk, light owner or world. A bounded compatibility table resolves each unique
block/biome wrapper once; the native material IDs remain authoritative.

The current [saved-light migration](../lighting/RUST-DH-SAVED-LIGHT.md) lets
generation supply persisted lighting through the same Rust field owner, without
Java voxel setters or a dense reconstruction for this consumer. Its new checks
are recorded separately from the historical results below.

Source reviewed at [`5de23173a`](https://github.com/HungLo2020/MattMC/commit/5de23173ae5b61fb17887589d4dff69a6b17fc77) on 2026-10-10; the release results below are
implementation-author reports, and this review did not rerun suites or clients.
See the [current CPU checkpoint](../../rendering/GOAL-5-STATUS.md#october-10-retained-world-and-dh-reduction-cpu-checkpoint)
for remaining runtime, performance and sustained-memory acceptance gates.

Implementation: [whole producer](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/dh_columns),
[CPU bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhColumns.java),
[list view](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhColumnList.java),
[admission](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/transformers/LodDataBuilder.java).

## Working constraints

- Preserve Frozen's x/z column order, first material encounter order, air
  normalization, biome clamping, point bit layout, minimum row and exclusive
  world top. Light changes alone do not split points. The previous material's
  color policy controls single-block forcing; fluids include waterlogged states.
- Preserve the original `isEmpty=false` and `applyToParent=true` behavior,
  LIGHT generation metadata, compression metadata and wrapped integer hashes.
- Chunk block reads respect stale empty-section counters. Raw emitter
  enumeration deliberately reads palette states independently of those counters.
  Section captures are not a transactional world or neighborhood snapshot.
- Admit stock factories/wrappers, ready biome stages and canonical registered
  owners only. Event subscribers, public wrapper-cache overrides, custom worlds,
  unsupported states and mutable lighting storage keep the original callbacks.
- Internal full-data columns use `LongList`, including direct native views.
  They must not subclass `LongArrayList`: fastutil's equality/comparison methods
  access another concrete list's backing array directly. Constructors, DTOs,
  transforms and mutable consumers accept the interface. Explicit legacy arrays
  remain `LongArrayList`; list values and mutation behavior remain compatible.
- The later [mutable column migration](RUST-DH-FULL-DATA.md) transfers a first
  mutation into independent Rust storage; the earlier release839ff128 transferred
  it to Java. Other views keep immutable native values. Mapping mutation detaches its compatibility
  table once; ordinary reads, serialization and recipient merges use native IDs.
  Public point-array replacements and metadata/list mutations must decline the
  immutable native source hash before the original hash cache is populated.
- Closing a pooled full-data source must not invalidate retained native columns.
  Keep GPU handles, render policy and presentation outside this world CPU owner.

## Verify and diagnose

Historical immutable-producer checkpoint, as reported by the implementation
author:

The previous lighting release's runtime and timing results do not verify this
implementation. The pre-handoff-fix build passed 2,485 Rust tests, 1,851 Java tests, all seven
lifecycle cases and both reviewed settled image pairs. A subsequent actual
Frozen mutable-lighting escape probe exposed a retained-counter mismatch; that
handoff is now corrected. The final release (`839ff128`) passes 2,485 Rust
tests (three ignored) and 1,852 Java tests (two skipped), including all 14 DH
column/lighting JNI checks. Six observed workers map that release. All seven final lifecycle cases and both manually reviewed settled vanilla
and shader+DH pairs pass, with VUID0 and DH visible-extension coverage28.84%.
Source/native/Frozen/prompt fingerprints match. Paired allocation and Current CPU travel profiles pass all integrity/process
guards, with all nine original F3 images reviewed and successful native source
publication observed. All 20 predeclared timing runs are clean, but performance acceptance fails
vanilla p99 and DH median FPS. Both shader modes pass.

From the repository root, with JDK 25:

```sh
CARGO_TARGET_DIR=build/rust/target cargo test --manifest-path src/main/rust/Cargo.toml dh_columns
CARGO_TARGET_DIR=build/rust/target cargo test --manifest-path src/main/rust/Cargo.toml world::level::lighting::dh::
./gradlew parityTest -PmattmcRustProfile=release --tests net.minecraft.world.level.levelgen.NativeDhColumnsTest --tests net.minecraft.world.level.levelgen.NativeDhLightingTest
python3 DevUtils/RunWiki.py check
```

Use the standard release environment (`CARGO_PROFILE_RELEASE_DEBUG=line-tables-only`,
`CARGO_PROFILE_RELEASE_STRIP=none`) for runtime measurements.
`build/native-dh-column-migration/` retains current development logs and oracle
commands; acceptance receipts must pin the final source and native release.

The supplemental column oracle runs untouched Frozen's actual builder and
lighting over all 16 saved block chunks and two declared vanilla biome patterns,
yielding 32 cases. It pins 11 production classes and records every point,
material identity/order, metadata and source/mapping hash. The JNI handoff asserts 256 actual native
columns per case and verifies retained lifetimes. Saved biome data and
real gameplay are not represented. The recorder's registry setup logs known
missing external-mod tag references; its successful CPU output is not a clean
runtime log-health pass. The Rust column comparison passes all 32 cases.

The separate stale-counter recorder pins six Frozen classes and records three
cases across three lighting passes. All cells and work counts match the focused
Rust regression, including palette edits and retained emitter caches. Frozen's
light-section level counters survive clear and affect later constant conversion;
native fields retain that policy state alongside compact values. This preserves
the baseline rather than correcting Frozen behavior. Previous suites did not
exercise this repeated clear/edit case. A separate eight-class Frozen oracle
records three ten-pass cases with a public mutable escape after the second
pass. The old escape reconstructed values but lost retained policy counters
(8,198 versus 12,289 work iterations in one later pass). The handoff now copies
section allocation, constant and signed-counter state alongside values; retained
native views remain immutable. All 30 actual Frozen passes now match through
the production-path JNI test: the first two passes per case assert native
admission, then the remaining eight assert Java-owned storage after escape.
Final lifecycle and settled pairs pass; ordinary
travel profiles pass their bounded checks; performance misses below and
long-session verification remain open.

For a native admission failure, check the exact wrapper/factory class, event
subscribers, persisted BIOMES status, canonical biome domain, public cache
replacements and the presence of a native light field. A CPU test must assert
that all 256 populated columns are native views; matching values alone can hide
an unintended compatibility path. Real travel profiles must separately prove
producer admission and matching camera/world/settings before claiming a gain.

## Current travel evidence

These are historical release `839ff128` measurements, retained as migration
evidence. They do not verify the newer owners reviewed above.

The guarded eight-second allocation pair uses the same prepared radius32 cached
DH source, shaders off. All six original F3 endpoints exactly match across
Current/Frozen, crossing seven X chunk columns. Native source publication occurs
on 18 sampled worker stacks and native column views on three. Both Current
videos contain positive opaque, translucent and water submissions. Weighted Java
allocation is 0.828/4.226 GB C/F; event lookup subsets are 11.53/1,383.07 MB.
These exclude native allocations and subsets overlap. Short Current peak RSS is
4.684 GiB; this is not long-session memory acceptance.

A separate guarded Current-only eight-second CPU profile directly samples the
native column kernel (102/11,881 ticks). All three F3 endpoints are reviewed;
it crosses seven X columns and returns exactly to its starting position.
Neighbor lookup, wrapper/height-field preparation and recipient updates remain
visible work. This prioritizes migration; it is not a paired Frozen CPU or
isolated throughput result. Receipts: `build/native-dh-column-migration/`.

## October 10 timing results

Release `839ff128` has 20 clean ABAB runs of exactly 6,000 frames, with source,
native, Frozen and protected-prompt guards passing. Before any measurements,
the protocol declared four DH repeats per side and two in every other mode.
All runs are included; no profiler or other game ran during measurement.

| Mode | Median FPS Current/Frozen | Median p99 ms Current/Frozen | Targets |
| --- | --- | --- | --- |
| Vanilla | 1331.80 / 1153.55 | 3.421 / 3.110 | p99 fails |
| Vanilla + DH | 725.85 / 733.70 | 4.865 / 5.321 | FPS fails |
| Shaders | 347.30 / 316.35 | 4.967 / 5.979 | Both pass |
| Shaders + DH | 254.15 / 225.05 | 6.811 / 8.034 | Both pass |

These measure the complete rotating real-world workload, not an isolated
producer improvement or broad rendering acceptance. The vanilla p99 miss is
about 10%; the DH FPS miss about 1%. Keep both failures visible while migrating
remaining ownership and diagnosing frame costs. Receipt:
`build/native-dh-column-migration/performance.json`; all per-run FPS values
are in root `SUMMARY.md`. Separate profiler runs do not replace these results.

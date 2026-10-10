# Rust DH full-data columns

This local migration is **in progress**. Mutable full-data columns use Rust
point vectors with direct CPU readers. Native producer columns detach on their
first mutation into independent Rust storage. Bulk list copies and ID remapping
consume existing point views; Java does not reconstruct another point array.
Canonical material identity enables Rust's complete column occlusion scan and
2×2 child-to-parent reduction. One producer call consumes the source’s retained
CPU point views and remaps all1,024 parent columns into one native quadrant
owner. The ordinary parent update reads those results directly; subsequent
column mutations detach into independent Rust vectors. The later
[contributor transport](RUST-DH-CONTRIBUTORS.md) also retains immutable footprints
in Rust. The quadrant producer also determines semantic uniformity and ordered
footprint admission/capture from source facts in the same call. It now also
selects signed generation/compression metadata and computes parent list hashes
while appending remapped points. Java stores the resulting bytes and reads seeded
hashes; it does not repeat these policies per group. Java still owns source update
orchestration, custom material-map mutation, metadata storage, contextual callbacks,
persistence and render-data transformation.
Legacy or explicitly replaced Java lists remain compatibility inputs.

The subsequent [material-map migration](RUST-DH-MATERIAL-MAPS.md) moves admitted
ordered material identities/indexes, whole-map remapping and hash policy to Rust.
Java retains wrapper bindings, custom callbacks and persistence. Its separate
guide records duplicate/import and exception-prefix constraints and current checks.

The subsequent [same-detail source update](RUST-DH-SOURCE-UPDATES.md) moves
generation eligibility, remapping, change detection and ordered footprint
admission into one Rust preparation/publication operation. Existing recipient
vectors reuse their capacity; public metadata storage and orchestration remain
Java. Its guide records the source-borrowing and failure-publication constraints.

The subsequent local source also performs the complete 64×64 occlusion pass in
one Rust call over existing owners. It follows Frozen's x/z and neighbor order,
so later centers observe earlier mutations. Java stages bounded owner/view
descriptors and consumes updated extents/hashes without exporting point values.
CPU checks for this newer pass are recorded below; hardware/gameplay and
performance acceptance remain pending.

Implementation: [point owner and occlusion](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/dh_columns),
[CPU list](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhColumnList.java)
and [source consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/fullData/sources/FullDataSourceV2.java).
The immutable producer's earlier results belong to [DH columns](RUST-DH-COLUMNS.md).

## Constraints when changing this subsystem

- Preserve list object identity during mutation, iterator/sublist behavior,
  symmetric `LongList` equality, wrapped hashes and the successful mutation
  prefix when a later material ID is invalid. Self-insertion must read the old
  range before vector growth. Explicit Java serialization exports values.
- Empty compatibility columns allocate no native vector until needed. Owners
  are released through CPU leases; retained lists survive source close. There
  is no global point cache. Existing caller exclusion remains required: this
  does not turn DH's mutable source into a concurrent transactional world.
- CPU point views expire on mutation and remain private to the list/bridge.
  Refresh pointer/length after growth or removal. Pin every borrowed owner
  through its native call. No GPU handle or GAL resource crosses this bridge.
- Occlusion receives semantic registered block-state identities once per
  update; Rust derives opacity from its intrinsic catalog. Custom material maps,
  wrappers, unsupported policy and center/neighbor aliases keep the Java path.
  Preserve Frozen x/z order, adjacent-index advancement, top/bottom protection
  and exact packed-field setters, including suspected baseline behavior.
- Parent reduction shares that material lease across the whole source update.
  Preserve transition order, midpoint sampling, first-tied non-air selection,
  first-liquid override, light averages and coalescing. Invalid IDs/geometry or
  unencodable output decline before publishing an owner; Java retains its
  original corrupt-point warnings and skip behavior. Empty output allocates no
  mutable vector. Parent results remain independent of source/material lifetimes.
  Native generation steps use the signed byte minimum; compression uses the
  signed byte maximum, including imported noncanonical values. Raw bytes are
  facts; do not send Java policy answers or normalize unknown enum values.
  Parent hashes use wrapped Java long-list arithmetic over remapped outputs.
  They are seeded before publication. List writes invalidate them before mutation;
  the complete occlusion pass publishes updated hashes before returning under
  the caller's exclusion rules.
  The whole-quadrant path borrows bounded point descriptors and a semantic ID
  remap table; it exports no point values. Custom/oversized inputs or enabled
  developer order validation retain per-column compatibility sequencing.
- Detached mutable columns release both their former producer reference and
  arena-backed point segment after copying; either could otherwise retain a
  whole producer. Pin the previous owner/span through the copy. Other readers
  retain their own leases, and list identity remains stable. Clearing a nonempty
  immutable column also releases its old lease; empty no-op behavior remains.
- Public list/metadata replacement must decline the original immutable source
  hash. Hash caches must observe every mutation, including native occlusion.
- Whole-pass admission requires the exact stock source, supported material
  identities, unique native lists and bounded columns. Null and exact empty
  Java lists remain open neighbors. Custom/nonempty Java lists, aliases,
  oversized columns or malformed material IDs decline to original per-column
  sequencing. Validate every input before changing points, including read-only
  centers and overlapping mutable/immutable spans. Java may detach immutable
  centers during preparation, preserving their values and list identity.
- Pass scratch storage is bounded per calling thread and its identity map is
  cleared in `finally`; it must not retain sources between calls. Pin all
  native point owners through descriptor staging, mutation and view refresh.

## Verification

With JDK25 and the standard release flags:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib dh_columns
./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.world.level.levelgen.NativeDhColumnsTest
./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.world.level.levelgen.NativeDhReductionTest
python3 DevUtils/RunWiki.py check
```

The subsequent whole-pass release `9371c5d7` passes all12 focused Rust checks,
230 Rust world tests and1,875 reported Java tests (two skipped). Its27 DH controls
include both ordered passes across all32 saved Frozen cases, comparing every
point and list hash with the original per-column route and recorded Frozen
output. Ordinary native producer-to-recipient updates preserve list identity
and the source snapshot. Aliases, custom callbacks, nonempty Java lists and bad
IDs decline without point changes. Six full-task workers map the exact library;
Current/Frozen source, native and protected-prompt guards match. Receipts:
`build/native-dh-occlusion-pass/`. These CPU results do not accept full Rust
hardware graphics, gameplay, FPS or sustained memory. The larger per-thread
scratch allocation and preparation cost still require real workload measurement.

The parent-metadata checkpoint passes all ten focused Rust checks and
228 Rust world tests. Twenty-six DH JNI controls pass, including all32,768
actual Frozen metadata pairs, seeded/remapped hash equivalence, mutation
invalidation, detached producer/parent leases, ordinary metadata publication
and resized-generation-list exception prefixes. Full Java verification passes:
1,874 reported tests (two skipped), six full-task workers mapping release
`0cd4b37f`, and unchanged Current/Frozen source, native-library and protected
prompt guards. Receipts: `build/native-dh-parent-metadata/`. Hardware, full Rust
graphics, gameplay, performance and sustained memory acceptance remain pending.

The [metadata recorder](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/GenerateFrozenDhParentMetadataOracle.java)
checks Frozen's compiled `FullDataSourceV2` identity before invoking its actual
private minimum-generation/maximum-compression helpers. DHP1 has32 canonical,
extreme and signed-byte CPU patterns, each with1,024 groups. Raw SHA256:
`e22e591361b6fab7213427af54064d9da2367b84b820ead6f45a2e97122fd7b5`.
This is exact CPU policy evidence, not gameplay/performance. Preserve culling
and repeat-update behavior: a visual-compression update may report changes on
repetition because the merged points are culled afterward. The initial new
control assumed `false`; its rejection is retained. Comparing the original
per-group Java route's returned flag and full resulting columns passes, without
changing production culling. Existing merge-only repeat checks still pass.

Earlier reduction/occlusion verification follows; it does not accept later source.

The [Frozen recorder](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/GenerateFrozenDhOcclusionOracle.java)
uses the prior DHC1 production-column fixture and the untouched Frozen culler,
with six checked class identities. It records 32 saved-column cases and two
complete ordered passes each. Unique fixture biome identities preserve material
ID positions; this does not establish saved-biome or gameplay parity. Raw DHO1
SHA256: `3419a7da318caf78ddec3df594f83841b78a5307033577d7d3073df50b9b01eb`.
The [reduction recorder](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/GenerateFrozenDhReductionOracle.java)
invokes untouched Frozen's private production reducer for every2×2 group in
those32 cases, checking five production class identities. DHR1 raw SHA256:
`8cb928a2dcf1259b76868c64c556bb56edf745254e157e18d7c9f4cae6e79d19`.
All32,768 reductions match in Rust and through the actual native producer →
ordinary parent consumer. Checks cover direct reducer admission, retained Rust
ownership, exact points/hashes, unchanged source and mutable results surviving
source/parent/material close. Release6352edf8 passes all six focused Rust checks,218 Rust world tests and
13 focused JNI controls. Single/whole-quadrant outputs and remapped results
match the oracle, and ordinary parent updates retain native views. Additional
controls cover nonidentity parent mapping, unchanged repeated updates, detached
writes and compatibility decline before publication. Full Java verification:
1,737 ordinary tests (two skipped) and123 parity tests, with six observed workers
mapping that exact release. All39,438 Current and38,176 Frozen source pins,
the native library and protected prompt match. Receipts:
`build/native-dh-reduction/quadrant/`. The earlier per-column release68dede8b
checkpoint remains in the parent directory. These are CPU fixtures; they do
not establish ordinary gameplay admission, pixel parity or throughput.

The first JNI invocation used source edited during native compilation and
failed on a missing scalar-add export. Its log/XML are retained; a frozen-input
rebuild passes. A separate wrong exception-type expectation was corrected
against the original Java helper; its initial failure is retained. Receipts:
`build/native-dh-mutable-columns/`.

The earlier full Rust software attempt fails (2,353pass/138fail/three ignored): its first
GUI raster pixel differs by one green value on llvmpipe, reproduced in isolation,
and later graphics-lock failures cascade. The earlier GLX-aborted invocation
is also retained. No full Rust or hardware acceptance follows.

Final hardware suites, ordinary runtime admission, paired profiles,
equivalent Frozen timing and memory checks remain required. An NVIDIA
kernel/library mismatch currently prevents hardware captures; see
[render verification](../../rendering/RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures).
Earlier release839ff128 misses vanilla p99 and DH median FPS. Neither old
timings nor the supplemental oracle establish a speedup for this change.

# Rust DH full-data columns

The subsequent [retained source table](RUST-DH-SOURCE-TABLE.md) owns the live
column references in Rust and feeds cached tint directly. Rust retains point
ownership while Java list identity survives writes; growth and detachment may
change the backing storage. The [metadata owner](RUST-DH-SOURCE-METADATA.md)
also owns generation/compression/uniformity planes and feeds parent reduction
directly. Source orchestration and persistence remain transitional Java work.
The later [owned source occlusion](RUST-DH-SOURCE-OCCLUSION.md) reads that table
directly and projects only escaped center readers; the descriptor route below remains
for compatibility inputs and its measurements are historical.
At [`52664da4`](https://github.com/HungLo2020/MattMC/commit/52664da430edab005b09f64e2df29eaa79db95a1),
`FullDataSourceV2.close()` closed the table in a `finally` path after its
metadata-pool close. The metadata migration removes that pool registration;
source close now materializes any unescaped native values as compatibility
readers before releasing the table. Those readers and previously escaped ones retain independent
leases and grouped allocations still have the separate GC lifetime described
below. Source close does not guarantee that every column allocation is freed.

Mutable full-data columns use Rust
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
while appending remapped points. Java publishes those results through native
metadata identity views and reads seeded hashes; it does not repeat these
policies per group. The ordinary parent route borrows the retained source
table and metadata directly; descriptor-based overloads remain compatibility
routes. Java still owns source update
orchestration, custom material-map callbacks, temporary metadata identity views, contextual color callbacks,
persistence and render geometry construction.
Legacy or explicitly replaced Java lists remain compatibility inputs. The subsequent
[render-column reducer](../../rendering/RUST-DH-RENDER-COLUMNS.md) owns compact
reduction policy in Rust. [Cached tint neighborhoods](../biome/RUST-DH-TINT.md)
now use native colors and admitted sampling; resolver callbacks and final render
geometry remain Java.

The subsequent [material-map migration](RUST-DH-MATERIAL-MAPS.md) moves admitted
ordered material identities/indexes, whole-map remapping and hash policy to Rust.
Java retains wrapper bindings, custom callbacks and persistence. Its separate
guide records duplicate/import and exception-prefix constraints and current checks.

The subsequent [same-detail source update](RUST-DH-SOURCE-UPDATES.md) moves
generation eligibility, remapping, change detection and ordered footprint
admission into one Rust preparation/publication operation. Existing recipient
vectors reuse their capacity; metadata storage is now native, while orchestration
remains Java. Its guide records the source-borrowing and failure-publication constraints.

The current source also performs the complete 64×64 occlusion pass in
one Rust call over existing owners. It follows Frozen's x/z and neighbor order,
so later centers observe earlier mutations. The ordinary route reads the
retained source directly and Java refreshes escaped CPU extents; the
descriptor adapter remains a compatibility route. Neither exports point values.
CPU checks for this newer pass are recorded below; hardware/gameplay and
performance acceptance remain pending for those earlier checkpoints.

Historical grouped-lifetime source reviewed at [`5dcd2501`](https://github.com/HungLo2020/MattMC/commit/5dcd25017197443df269f27193441a5580c6479a) on 2026-10-11. Earlier release evidence retains its own source scope; the results below are
implementation-author reports, and this review did not rerun suites or clients.
See the [current evidence checkpoint](../../rendering/GOAL-5-STATUS.md)
for remaining runtime, performance and sustained-memory acceptance gates.

Implementation: [point owner and occlusion](https://github.com/HungLo2020/MattMC/tree/52664da430edab005b09f64e2df29eaa79db95a1/src/main/rust/world/level/chunk/dh_columns),
[CPU list](https://github.com/HungLo2020/MattMC/blob/52664da430edab005b09f64e2df29eaa79db95a1/src/main/java/net/minecraft/world/level/chunk/NativeDhColumnList.java)
and [source consumer](https://github.com/HungLo2020/MattMC/blob/52664da430edab005b09f64e2df29eaa79db95a1/src/main/java/com/seibel/distanthorizons/core/dataObjects/fullData/sources/FullDataSourceV2.java).
The immutable producer's earlier results belong to [DH columns](RUST-DH-COLUMNS.md).

## Constraints when changing this subsystem

`NativeDhColumnList.size(int)` now hands positive resizing directly to Rust.
It preserves existing points, zero-fills growth and truncates in one operation.
Saved-source hydration through `ListUtil.clearAndSetSize` keeps list identities
and its clear-before-resize order. Negative sizes retain the inherited Java
removal/exception prefix. This removes per-point zero appends and repeated
FFM view creation; it does not move compressed stream decoding out of Java.

The local block-light producer batch exposed an intermittent DH RSS guard
failure at about 12.4 GiB before measuring frames. Two diagnostic reruns
completed below the guard; the game-only allocation profile identifies DH
column hydration as the largest sampled FFM source, including about 922 MB
weighted allocation below `NativeDhMutableColumn.add`/view refresh. These are
allocation samples, not retained-byte totals or proof of a leak. Combined
candidate `3c8e2d3d` passes full Rust and Java/parity suites, including resize
identity/value/zero-fill/truncation controls. Two completed DH diagnostic
sessions peak at 8.51/9.07 GiB below the guard. The game-only JFR weighted
allocation below add/view refresh drops from 922 to 158 MB; variable background
DH work makes this directional allocation evidence rather than a normalized
speedup or retained-memory bound. The failed run and diagnostics remain in
`build/native-light-seeds/`. The final twenty matched timing runs complete cleanly.
Four DH sessions peak at 8.52–9.12 GiB RSS and two shader+DH sessions at
9.53/9.92 GiB, below the guard. DH FPS remains 8.2% below Frozen; other FPS
and all p99 floors pass. These bounded workloads do not prove sustained-memory
acceptance. See the [complete matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#block-light-sources-and-bulk-dh-resize).

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
  the owned occlusion pass invalidates primary hashes while mutating points,
  then hash reads recompute on demand. Its output refreshes escaped extents;
  the older descriptor adapter also returns changed/hash rows. Keep caller
  exclusion through either path.
  The ordinary whole-quadrant path borrows the retained source table, native
  metadata and semantic ID remap; the compatibility overload stages bounded
  point descriptors. Neither exports point values. Custom/oversized inputs or enabled
  developer order validation retain per-column compatibility sequencing.
- Demand-created empty lists from a full-data source share a
  [`NativeDhColumnGroup`](https://github.com/HungLo2020/MattMC/blob/52664da430edab005b09f64e2df29eaa79db95a1/src/main/java/net/minecraft/world/level/chunk/NativeDhColumnGroup.java).
  Their mutable vectors register pointers under one automatic arena instead
  of creating one cleanup per vector. The source, grouped lists and retained
  segments keep that lifetime reachable; collection of the shared arena
  triggers the group's release callback. This is GC-driven release, not an
  explicit group-close operation or a bounded release deadline. The newer
  source-table close releases its own leases separately.
- Grouping follows `empty(group)` initialization and newly adopted vectors
  in the retained-source merge. Producer views and externally replaced lists
  do not automatically join a source's group;
  ungrouped mutable vectors keep independent arenas. Clearing a grouped list
  empties its contents without unregistering its pointer. A retained grouped
  list can therefore keep other registered vectors alive until group release.
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

The [metadata recorder](https://github.com/HungLo2020/MattMC/blob/5dcd25017197443df269f27193441a5580c6479a/DevUtils/tests/world/chunk/GenerateFrozenDhParentMetadataOracle.java)
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

The [Frozen recorder](https://github.com/HungLo2020/MattMC/blob/5dcd25017197443df269f27193441a5580c6479a/DevUtils/tests/world/chunk/GenerateFrozenDhOcclusionOracle.java)
uses the prior DHC1 production-column fixture and the untouched Frozen culler,
with six checked class identities. It records 32 saved-column cases and two
complete ordered passes each. Unique fixture biome identities preserve material
ID positions; this does not establish saved-biome or gameplay parity. Raw DHO1
SHA256: `3419a7da318caf78ddec3df594f83841b78a5307033577d7d3073df50b9b01eb`.
The [reduction recorder](https://github.com/HungLo2020/MattMC/blob/5dcd25017197443df269f27193441a5580c6479a/DevUtils/tests/world/chunk/GenerateFrozenDhReductionOracle.java)
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
kernel/library mismatch previously prevented hardware captures and was cleared
by the user's October 10 reboot; see
[render verification](../../rendering/RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures).
Earlier release839ff128 misses vanilla p99 and DH median FPS. Neither old
timings nor the supplemental oracle establish a speedup for this change.

## Grouped-column evidence

This subsection records the earlier `5dcd2501` group-only change. The current
source-table close and newly adopted merge-vector grouping are subsequent
changes; they do not retroactively turn these measurements or two tests into
verification of the newer lifetime paths.

The [implementation commit](https://github.com/HungLo2020/MattMC/commit/5dcd25017197443df269f27193441a5580c6479a)
reports 13.4 million phantom cleanables and about 2.2 GB of bookkeeping at
a forced crash. Its still-camera forced-crash comparison reports
crash-report delay falling from 6.5 minutes to 20 seconds, with exit at 22
seconds. Two interleaved moving-camera runs per version report FPS 41.0/50.2
before and 57.8/54.9 after, with fewer long frames, lower runqueue wait and
lower median post-GC heap. These are author-reported observations from that
workload, not an independently rerun benchmark or broad GC/lifetime acceptance.

The two [new Java fixtures](https://github.com/HungLo2020/MattMC/blob/5dcd25017197443df269f27193441a5580c6479a/src/test/java/net/minecraft/world/level/chunk/NativeDhColumnGroupTest.java)
check shared versus independent arena scopes and list add/set/remove/clear/hash
semantics. They do not observe collection or native release, source reuse,
retained-reader teardown, exception cleanup, or crash-report timing. This
review inspected their assertions but ran no Java/Rust/client checks. Preserve
those lifetime checks and sustained memory measurements as separate acceptance
work; see [forced-crash recording](../../rendering/SESSION-RECORDING.md).

The [group-lifetime review](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6103618931)
and [measurement review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6103620939)
retain those boundaries. The before/after moving-camera comparison is not a
Frozen baseline comparison; both issues remain open.

# Rust DH source updates

This local migration is in progress. The same-detail recipient merge now runs
through Rust preparation and publication over existing native point owners.
Rust selects generation eligibility, remaps IDs, computes wrapped hashes and
the global change flag, and admits/clips/remaps contributor footprints in x/z
order. Publication copies directly into existing recipient vector capacity;
Java does not clear/copy/remap/hash each column through separate native calls.

Admitted [material maps](RUST-DH-MATERIAL-MAPS.md) now also merge identities in
Rust. Java orchestrates update/propagation flags,
stores public generation/compression bytes and publishes compatibility readers.
The parent producer and complete occlusion pass are documented in
[full-data columns](RUST-DH-FULL-DATA.md). Complete Java removal remains open.

Implementation: [merge policy and publication](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/same_detail.rs),
[CPU bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhSameDetailMerge.java),
[ordinary source consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/fullData/sources/FullDataSourceV2.java).

## Lifetime and compatibility rules

- Keep caller exclusion across preparation and publication. Prepared source
  spans are borrowed native CPU data, not a source snapshot or a transaction.
  The bridge pins the original source list array until publication succeeds;
  Rust then clears borrowed spans and Java clears that lease. Contributor
  snapshots own independent values and may survive source mutation/unload.
- Preserve existing recipient list identity and vector capacity. Null recipients
  are adopted separately; unadopted vectors remain owned by the batch. Updating
  a native producer view detaches it before publication. There is no point-array
  export or Java point mirror and no GPU handle crosses this boundary.
- Validate recipient aliases and source/recipient allocation overlap before
  values change. Reserve all needed capacity before copying. A later reserve
  failure can move an earlier allocation: return fresh views for **all** existing
  recipients before throwing, even though values remain unchanged. Hashes must
  be seeded/refreshed before the operation returns to Java.
- Generation values are signed bytes. Preserve the downsample/empty special
  case and comparisons for unknown values, without enum normalization. A
  metadata-only update does not set Frozen's global `dataChanged` flag. Wrapped
  hash collisions remain hash comparisons; do not substitute value equality.
- Preserve remove-before-admit footprint order, capacity1,024, per-column
  prefix256 and null versus present-empty/four-null footprints. Batch readers
  retain the immutable footprint owner after publication, not the source lease.
- Custom sources/lists, aliased metadata backing arrays, resized metadata,
  oversized columns, unsupported footprints or invalid remap IDs decline to
  the original per-column route before point publication. That route retains
  custom callbacks and the successful prefix when a later update throws.
  Developer order validation also keeps its original sequencing.
- Scratch buffers and identity tables are bounded per calling thread; tables
  are cleared in `finally`. The prepared owner retains bounded metadata and
  footprints while readers exist. Validate total retention in sustained runtime
  memory checks before claiming resource acceptance.

## Verification

Use JDK25 and a consistently configured release library:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib same_detail
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.world.level.levelgen.NativeDhSameDetailMergeTest
python3 DevUtils/RunWiki.py check
```

The [Frozen recorder](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/GenerateFrozenDhSameDetailOracle.java)
checks Frozen's compiled source identity and invokes its actual private
same-detail update. DHM1 covers all65,536 signed generation pairs and32 saved
point-source cases, each with two updates. It records full points/metadata,
null columns, change flags and emptiness. Raw SHA256:
`0d0b71c986010984edcb419edf0536d80577e6117e1c71b032fcecc6d9ad4b31`.
This is supplemental CPU evidence, not gameplay or performance proof. Frozen
does not implement Current's contributor sidecar: contributor controls compare
Current's original route, separately from the actual Frozen base-state oracle.

Local release `efeca492` passes all16 focused Rust column checks and234 Rust
world checks. Full Java reports1,880 tests (two skipped), zero failures/errors;
32 DH controls include31 native consumers and one bootstrap regression. Six
full-task workers map the exact release; all39,453 Current/38,176 Frozen source
pins, native and protected-prompt guards match. Controls cover the complete
Frozen corpus, ordinary saved-source update and native footprint ownership,
source-lease release, capacity reuse, reserve-failure view refresh, and exact
compatibility exception/mutation prefixes. Receipts:
`build/native-dh-same-detail/`. Hardware, full Rust graphics, realistic performance
and sustained memory acceptance remain pending driver restoration. The earlier
recipient-update CPU/allocation profile motivates this migration; it does not
measure this implementation's benefit or extra preparation/scratch cost.

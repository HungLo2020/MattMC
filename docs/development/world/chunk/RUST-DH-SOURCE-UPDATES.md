# Rust DH source updates

The same-detail recipient merge runs
through Rust preparation and publication over existing native point owners.
Preparation reads the [retained source table](RUST-DH-SOURCE-TABLE.md) directly.
Rust selects generation eligibility, remaps IDs, computes wrapped hashes and
the global change flag, and admits/clips/remaps contributor footprints in x/z
order. Publication copies directly into existing recipient vector capacity;
Java does not clear/copy/remap/hash each column through separate native calls.

Admitted [material maps](RUST-DH-MATERIAL-MAPS.md) now also merge identities in
Rust. Java orchestrates update/propagation flags and projects compatibility readers.
The [metadata owner](RUST-DH-SOURCE-METADATA.md) retains generation, compression
and uniformity in Rust. Owned merges consume and publish these planes directly.
Rust also publishes newly created vectors directly into the primary table
and owns bounded registration/selection of escaped CPU readers.
Java projects selected contributor-map actions and changed extents for escaped
readers only; exact copy proofs leave existing list readers unchanged.
Base-state hashing also reads the retained Rust table.
The parent producer and complete occlusion pass are documented in
[full-data columns](RUST-DH-FULL-DATA.md). Complete Java removal remains open.

Current direct-source path reviewed at [`7cf8b68c`](https://github.com/HungLo2020/MattMC/commit/7cf8b68cc4fab2f01ba142eaa48092399ec53edc).
The earlier `5de23173a` release results below remain historical
implementation-author reports; this review reran no suites or clients.
See the [current evidence checkpoint](../../rendering/GOAL-5-STATUS.md)
for remaining runtime, performance and sustained-memory acceptance gates.

Implementation: [merge policy and publication](https://github.com/HungLo2020/MattMC/blob/7cf8b68cc4fab2f01ba142eaa48092399ec53edc/src/main/rust/world/level/chunk/dh_columns/same_detail.rs),
[CPU bridge](https://github.com/HungLo2020/MattMC/blob/7cf8b68cc4fab2f01ba142eaa48092399ec53edc/src/main/java/net/minecraft/world/level/chunk/NativeDhSameDetailMerge.java),
[ordinary source consumer](https://github.com/HungLo2020/MattMC/blob/7cf8b68cc4fab2f01ba142eaa48092399ec53edc/src/main/java/com/seibel/distanthorizons/core/dataObjects/fullData/sources/FullDataSourceV2.java).

## Lifetime and compatibility rules

With empty source and recipient contributor maps, the current direct route
uses a scoped native merge released in `finally`, plus a fixed per-thread
remap buffer. Other prepared merge batches retain the shared lifetime below;
see [metadata ownership](RUST-DH-SOURCE-METADATA.md#ownership-and-lifetime).

Prepared merge batches have explicit `close()`. The source caller closes
the batch in `finally` when it has published no non-null contributor footprint;
this is a conservative publication flag, not a reachability test. Contributor
readers keep the batch lifetime. This does not establish
a GC release deadline or permit closing a batch while those readers are live.

- Keep caller exclusion across preparation and publication. Prepared source
  spans are borrowed native CPU data, not a source snapshot or a transaction.
  The direct bridge pins both source tables until publication succeeds; the
  compatibility adapter pins the original source list array.
  Rust then clears borrowed spans and Java clears that lease. Contributor
  snapshots own independent values and may survive source mutation/unload.
- The direct route reuses source-owned identity proofs until structural binding
  changes. Point growth cannot change primary identity. Preparation rejects
  duplicate recipient identities and any source/recipient primary alias.
  Direct commit rechecks source/recipient structural versions and the target
  remap stamp before mutation;
  distinct Rust-owned vectors cannot overlap. Compatibility descriptor commits
  retain their explicit allocation-overlap and identity checks.
- Primary columns also own a scalar maximum-ID cache, invalidated by point
  writes. Identity remaps validate their range against it and use the original
  column hash as a hint; exact-copy reuse still needs the separate copy proof.
  Admitted identity copies use a bulk span copy. Capacity-only reserve changes
  neither values nor hash/ID/copy proofs. Verification and performance limits
  are recorded with [source occlusion](RUST-DH-SOURCE-OCCLUSION.md).
- Preserve existing recipient list identity and vector capacity. Direct source
  commits transfer new vectors to the source without Java adoption. Unescaped
  readers stay native until demand; escaped empty readers acquire independent
  leases without changing list identity. Descriptor compatibility commits still
  transfer new vectors from the batch to their Java recipients. Updating
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
- Custom sources/lists, aliased native metadata planes or Java backing arrays,
  resized metadata,
  oversized columns, unsupported footprints or invalid remap IDs decline to
  the original per-column route before point publication. That route retains
  custom callbacks and the successful prefix when a later update throws.
  Developer order validation also keeps its original sequencing.
- Compatibility scratch buffers and identity maps are bounded per calling
  thread; the maps clear in `finally`. Retained-source identity proofs instead
  live with the source until structural invalidation or close, with at most
  4,096 identities. The prepared owner retains bounded metadata and
  footprints while readers exist. Validate total retention in sustained runtime
  memory checks before claiming resource acceptance.

## Verification

Use JDK25 and a consistently configured release library:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib same_detail
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.world.level.levelgen.NativeDhSameDetailMergeTest
python3 DevUtils/RunWiki.py check
```

The [Frozen recorder](https://github.com/HungLo2020/MattMC/blob/52664da430edab005b09f64e2df29eaa79db95a1/DevUtils/tests/world/chunk/GenerateFrozenDhSameDetailOracle.java)
checks Frozen's compiled source identity and invokes its actual private
same-detail update. DHM1 records 48 cases: 16 synthetic cases cover all 65,536
signed generation pairs, and 32 use saved point sources. Each has two updates. It records full points/metadata,
null columns, change flags and emptiness. Raw SHA256:
`0d0b71c986010984edcb419edf0536d80577e6117e1c71b032fcecc6d9ad4b31`.
This is supplemental CPU evidence, not gameplay or performance proof. Frozen
does not implement Current's contributor sidecar: contributor controls compare
Current's original route, separately from the actual Frozen base-state oracle.

The author reports that release `efeca492` passes all16 focused Rust column checks and234 Rust
world checks. Full Java reports1,880 tests (two skipped), zero failures/errors;
32 DH controls include31 native consumers and one bootstrap regression. Six
full-task workers map the exact release; all39,453 Current/38,176 Frozen source
pins, native and protected-prompt guards match. Controls cover the complete
Frozen corpus, ordinary saved-source update and native footprint ownership,
source-lease release, capacity reuse, reserve-failure view refresh, and exact
compatibility exception/mutation prefixes. Receipts:
`build/native-dh-same-detail/`. That checkpoint awaited driver restoration;
see the [later reported preflight](../../rendering/RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures)
for recovery and full-suite results. New MattMC runtime, realistic performance
and sustained-memory acceptance remain pending. The earlier
recipient-update CPU/allocation profile motivates this migration; it does not
measure this implementation's benefit or extra preparation/scratch cost.

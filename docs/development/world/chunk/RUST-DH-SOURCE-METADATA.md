# Rust DH source metadata

> **Current state:** the [Rust-owned DH full-data source](RUST-DH-SOURCE.md)
> supersedes this page's descriptions of Java fallback, compatibility,
> descriptor and decline routes, Java-held lists, resizable metadata and the
> Java footprint map; those no longer exist. Ownership details and historical
> results below remain as recorded.

Saved generation/compression/uniformity payloads now use the
[direct native metadata codecs](RUST-DH-METADATA-CODEC.md). The measurements below
remain historical for the earlier storage/merge ownership slice.

Source reviewed at [`64db2b31`](https://github.com/HungLo2020/MattMC/commit/64db2b31690fdd62ca1b1968d1cf076f0fa09108).
Suite counts, timings and untracked receipt descriptions below are
implementation-author reports. This documentation review inspected source and
test assertions; it reran no Java/Rust suites, clients or benchmarks.

The later [source-occlusion checkpoint](RUST-DH-SOURCE-OCCLUSION.md) adds
native reader registration and direct primary publication; the metadata-stage
timings below remain historical and do not verify that later implementation.

This ownership slice moves generation, compression and horizontal-uniformity
storage into Rust beside the retained source table. Ordinary same-detail merge
preparation reads those planes directly, and publication updates them in Rust.
Java no longer copies four byte planes or converts 4,096 booleans for that path.
Parent reduction also reads primary points and metadata directly, without a
Java descriptor grid or metadata snapshots. Source commits return only changed
CPU extents to Java; exact-copy reuse requires no dense compatibility projection. Rust prepares the
point-write indices; owned commits detach, reserve and publish only those
columns. Metadata eligibility still runs for every source column. One source
pair proof caches structural nonaliasing by monotonic source identity and both
table versions; replacement invalidates it, while point growth does not. Exact
copy proofs also avoid reading a second point backing solely for its length. Metadata publication also folds the immediate base-data hash during the same
grid visit. The public caller uses it only when no later culling occurs and
Frozen reports changed data. Metadata-only updates preserve Frozen's cached
hash; a bounded hash decline still completes metadata publication and lets the
caller hash through its compatibility path. Selected columns reuse the completed merge's exact hashes and lengths during
that immediate publication; unselected columns still read the primary table.
Bounded per-column scalar lengths preserve the hash budget after borrowed
point spans are cleared; merge metadata, hashes, remaps and footprint owners
also survive as needed. Both paths enforce the same logical point budget. Do not use a prepared result
after subsequent point edits or culling. The common preparation stream carries
compact column facts and loads rare
contributor arrays only after ordered admission. The latest CPU verification is recorded below; it does not establish gameplay
acceptance.

When both contributor maps are empty, the temporary bridge uses a scoped native
merge and releases it in `finally`. A fixed per-thread remap buffer replaces
per-update confined allocation; that merge registers no shared arena or
Cleaner. Bounded per-thread automatic-arena scratch remains. Both routes use the same Rust admission/commit policy.
New column readers still retain their source group. Updates publishing
contributor readers keep the shared merge lifetime; do not close it while those
readers remain live.

Implementation: [metadata owner](https://github.com/HungLo2020/MattMC/blob/64db2b31690fdd62ca1b1968d1cf076f0fa09108/src/main/rust/world/level/chunk/dh_columns/metadata.rs),
[merge policy](https://github.com/HungLo2020/MattMC/blob/64db2b31690fdd62ca1b1968d1cf076f0fa09108/src/main/rust/world/level/chunk/dh_columns/same_detail.rs),
[parent consumer](https://github.com/HungLo2020/MattMC/blob/64db2b31690fdd62ca1b1968d1cf076f0fa09108/src/main/rust/world/level/chunk/dh_columns/parent.rs)
and [temporary list views](https://github.com/HungLo2020/MattMC/blob/64db2b31690fdd62ca1b1968d1cf076f0fa09108/src/main/java/net/minecraft/world/level/chunk/NativeDhMetadataList.java).

## Ownership and lifetime

- Generation and compression are native byte lists. Java fields expose `ByteList`
  identities with private borrowed CPU views; there is no Java byte-array mirror.
  Rust performs resizing, insertion and removal. Scalar Java setters deliver
  semantic writes directly into the owned CPU storage.
- Uniformity is a fixed 4,096-byte native plane. Its Java API accepts booleans;
  merge and parent consumers read it directly. Explicit compatibility snapshots
  are separate from the ordinary owned route.
- The source table retains the metadata owner. Java metadata views hold an
  independent typed native lease and one shared automatic arena, so table close
  cannot invalidate retained metadata readers. That lease contains metadata,
  not the source's primary column table or point vectors. One retained list
  can keep all three planes alive until automatic-arena collection; no GC
  release deadline follows.
- Keep the original DH caller exclusion through reads, edits, serialization and
  native consumers. Resizing invalidates borrowed extents; the list refreshes
  its private view before returning. Shared ownership adds lifetime protection,
  not concurrent mutation. Uniformity's fixed extent never resizes.
- Full-data sources no longer extend or register with the Java phantom array
  pool: their former two pooled metadata arrays are gone. Other subsystems still
  use that pool and remain separate migration work.

## Compatibility and working on callers

Use `getByte`, `set`, `size`, `getElements` and `setElements` through `ByteList`.
`toByteArray` is an explicit value export for codecs or compatibility controls,
not a synchronization mechanism for native consumers. Java serialization
exports an independent `ByteArrayList`. Source metadata has no public backing
array; the old fields' concrete Java type has changed.

Preserve signed byte comparisons and wrapped hashes. Imported lists can have
noncanonical lengths; owned merge/parent admission requires all three planes
at exactly 4,096 entries. The compatibility update must retain its successful
mutation and callback prefix before a later failure. Hash collisions still
follow Frozen's global change-flag policy; they do not authorize exact copy
reuse. Point copy proofs remain tied to column identity/revision and complete
remap contents as described in the [source table guide](RUST-DH-SOURCE-TABLE.md).

Persistence remains Java orchestration. Decoder writes must preserve bytes
successfully read before EOF, and callers must not silently turn a live view
into an independent array. Sparse contributor-map transport, contextual colors,
geometry remain subsequent migration work. The later
[source occlusion migration](RUST-DH-SOURCE-OCCLUSION.md) removes ordinary Java
descriptor-grid construction and center selection.
There are no GPU handles or presenter changes in this CPU migration.

## Verification

Use JDK25 and a consistent release configuration:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib world::level::chunk::dh_columns -- --test-threads=1
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative compileTestJava
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests 'net.minecraft.world.level.chunk.NativeDhMetadataListTest' --tests 'net.minecraft.world.level.levelgen.NativeDh*Test'
python3 DevUtils/RunWiki.py check
```

The author reports that the combined tree on upstream `2edce1f66`, including
the incoming audio migration, uses native image `ef31dac8` and passes serial
full Rust (2,593 passed, three ignored) and normal full Java tasks (1,943
reported, 1,941 executed and two skipped, zero failures/errors across 350
suites). The reported six Java workers map the exact native image. Controls include all 48 saved Frozen merge
cases and both updates through the shared and scoped paths, signed bytes,
null/empty columns, hashes, metadata-only cached-hash behavior, both compression
modes, EOF prefixes, serialization and retained-reader lifetimes. A fresh-process
registry control checks false before bootstrap and true afterward in one
fresh JVM. This focused sequence does not establish arbitrary concurrent or
reentrant bootstrap behavior; see the [registry owner](../../game-model/RUST-BLOCK-REGISTRY.md).

The author reports six fresh alternating pairs, five 1,000-call warmups and
1,000 measured calls per JVM with matching checksums and
source/native/compiled-class/Frozen/protected-prompt pins. CPU medians:

| Workload | Current ms | Frozen ms | Current/Frozen |
|---|---:|---:|---:|
| Create, hash and close 1,000 sources | 10.299 | 13.879 | 0.742 |
| Change one input point, update and hash 1,000 times | 55.868 | 71.545 | 0.781 |

Both author-reported medians pass this scoped synthetic comparison against Frozen. Creation costs more than the
earlier source-table-only checkpoint; these results do not claim a creation
speedup over that version or a whole-game performance gain.

Historical rejected candidates remain in the receipts (before the audio integration):

| Candidate | Update Current/Frozen ms per 1,000 | Status |
|---|---:|---|
| Initial metadata `ef416d37` | 111.049 / 71.238 | Six-pair failure |
| Sparse projection `7c761ee7` | 98.561 / 70.637 | Two-pair screen failure |
| Native write indices `d56bc9ee` | 86.787 / 70.052 | Two-pair screen failure |
| Structural pair proof `776582f5` | 75.812 / 70.354 | Six-pair failure |
| Fused hash `22d8b092` | 77.532 / 70.680 | Six-pair failure |
| Compact stream `2a03b3a2` | 72.466 / 70.472 | Six-pair failure |
| Prepared hashes with shared bridge `81848bd4` | 73.931 / 70.749 | Six-pair failure |

Profiles motivated sparse publication, compact inputs, immediate hash reuse and
scoped bridge lifetime. The budget control caught reading source lengths after
commit cleared borrowed spans; scalar counts fixed it, and the abort log is
preserved. Earlier full Java failures exposed an obsolete private-array test and
premature registry readiness, both corrected. A separate diagnostic combining
both tag groups in one worker exposed transformer initialization ordering; the
normal full tasks above pass, and the diagnostic failure remains retained.

Use the identical compiled source driver through
`DevUtils/tests/world/chunk/VerifyDhSource.py`, six alternating pairs, five
warmups at the measured count, and fresh output directories. Its metadata field
binding uses reflection outside timing to accommodate Frozen's original concrete
list type. Check source/native/compiled-class/Frozen/protected-prompt pins and
keep builds, tests, observers, hashing and profiling outside timing windows.
Receipts: `build/native-dh-metadata/`. Synthetic CPU results do not prove
ordinary gameplay, FPS, crash cleanup or sustained host/GPU memory acceptance.


The unchanged workload uses 4,096 columns with four points each. Create mode
includes hash and close; update mode changes the first input point, merges and
hashes the target with MERGE_SAME_BLOCKS and empty contributor maps. The
0.781× result therefore measures the scoped empty-footprint route, not
contributor-reader performance. The driver reports ratios without a speed threshold in its
exit status; its native/compiled-driver guards do not establish every broader
source/classpath guard stated in the author's receipts. See the
[driver boundary](RUST-DH-SOURCE-TABLE.md#verification). The earlier 1.439×
update failure stays historical; the newer comparison does not establish an
FPS, streaming or long-memory gain. Serial suite success does not resolve the
retained parallel X11/GLX `BadAccess` failure.


The [metadata-list tests](https://github.com/HungLo2020/MattMC/blob/64db2b31690fdd62ca1b1968d1cf076f0fa09108/src/test/java/net/minecraft/world/level/chunk/NativeDhMetadataListTest.java)
inspect signed values, resize/iteration/self-add, retained mutation after source
close and independent serialized values. Their truncated-blob checks use only
UNCOMPRESSED inputs of 0, 137 and 4,096 bytes; they do not establish every codec
failure path. The 48-case merge corpus has no contributor facts; separate
controls cover contributors. Frozen parent policy fixtures and compatibility
quadrant controls are distinct from the ordinary public-parent controls for
the new direct route. None is an exhaustive malformed-input, concurrent-access
or GC-release matrix.

The [#777 ownership review](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6105286352)
and [#709 evidence review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6105286909)
retain these boundaries. Both issues remain open.

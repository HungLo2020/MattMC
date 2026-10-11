# Retained Rust DH source columns

Rust retains the live 64×64 full-data column table and cached tint reads it
directly. Present-empty columns initially need no Java readers; list identity
projections materialize on demand. Java keeps source orchestration during
the migration. Stock tint calls no longer stage 9, 25 or 49 point descriptors,
copy them across the boundary, or repeat the neighborhood plan in Java.
Material IDs, point vectors and the table remain ordinary CPU data.

Implementation: [source table](https://github.com/HungLo2020/MattMC/blob/52664da430edab005b09f64e2df29eaa79db95a1/src/main/rust/world/level/chunk/dh_columns/source.rs),
[column leases](https://github.com/HungLo2020/MattMC/blob/52664da430edab005b09f64e2df29eaa79db95a1/src/main/rust/world/level/chunk/dh_columns/mutable.rs),
[direct tint consumer](https://github.com/HungLo2020/MattMC/blob/52664da430edab005b09f64e2df29eaa79db95a1/src/main/rust/world/level/chunk/dh_columns/tint.rs)
and [temporary Java bindings](https://github.com/HungLo2020/MattMC/blob/52664da430edab005b09f64e2df29eaa79db95a1/src/main/java/net/minecraft/world/level/chunk/NativeDhSource.java).

Source reviewed at [`52664da4`](https://github.com/HungLo2020/MattMC/commit/52664da430edab005b09f64e2df29eaa79db95a1).
The suite counts and timings below are implementation-author reports. This
documentation review inspected source/test assertions, but reran no Java/Rust,
client or benchmark checks and did not inspect untracked execution receipts.

## Ownership and mutation

- Source entries retain primary Rust column allocations, including aliases.
  Growth and removals change that allocation's vector, so native consumers read
  its current extent. There is no copied point table or per-frame synchronization
  of pointer/length descriptors. An untouched source represents all present-empty slots without a dense table,
  point vectors or leases. The first structural binding change materializes the
  bounded dense table. Binding an empty
  list to another slot materializes its primary identity for alias checks.
- Primary columns own atomic cached wrapped list hashes. Concurrent hash
  readers do not require a new lock. Point writes invalidate the cache,
  including failed remaps with a successful mutation prefix. Merge publication
  seeds the exact remapped hash; later source and mutable Java list hashing
  reuse that primary state. Immutable detachment preserves its unchanged value.
  All writable vector access stays inside the invalidating native accessor.
- The current copy-proof extension tracks primary column identity/mutation and
  the complete remap contents (up to4,096 IDs; larger maps always copy).
  Unchanged copies reuse points and extents while
  still applying generation, compression, uniformity and contributor policy.
  Point writes, recipient edits and remap changes invalidate the proof. Native
  identities are never recycled; prepared commits reject structural version or
  remap changes. Hash collisions alone never authorize reuse. Checked descriptor
  publication always copies because its recipient can differ from the source
  table that was prepared.
- Immutable producer and parent points use typed Rust shared ownership. Binding
  a source retains its existing allocation; it does not copy points. Writes
  detach immutable spans without changing Java list identity. Clearing a
  nonempty immutable list can install a new empty primary allocation; it need
  not preserve the old allocation identity. Java readers refresh CPU views
  after growth, detachment or replacement.
- Lazy source lists and newly adopted retained-source merge vectors share the source's
  `NativeDhColumnGroup` automatic arena. First materialization of a grouped
  empty list retains that group. Immutable producer/parent spans retain their
  independent leases; replacement lists retain their original lifetime. One
  retained grouped reader can keep every registered vector alive until the
  group is collected. Grouping reduces cleanup records; it does not establish
  prompt native release or sustained-memory acceptance.
- Source closure releases the Rust table and its primary leases immediately.
  Retained Java column readers keep their own native leases and remain usable.
  A closed source preserves a 512-byte presence snapshot for lazy empty/null
  compatibility readers; it retains no copied points or live native table.
  Its native consumers decline to compatibility readers after closure.
- Reference counts protect allocation lifetime, including arena cleanup on
  another thread. They do **not** permit simultaneous point reads and writes.
  Keep DH's existing caller exclusion throughout native calls, mutation, source
  replacement and serialization. Borrowed slices cannot survive a mutation.
  Source leases contain raw owned handles without persistent Rust references;
  mutation borrows vector/backing fields separately from the atomic count.
- Java retains weak source attachments for empty lists. Their first materialized
  vector publishes its lease once to every still-bound source slot. Replacing a
  slot removes its attachment. Retaining a list cannot retain its former Java
  source and all neighboring columns through that attachment.
- `FullDataSourceV2.getColumn(index)` retains the list's identity.
  `setColumn(index, list)` replaces its identity and publishes a retained native
  lease or an unsupported-input marker. The old public `dataPoints` field is now
  private; internal structural writes must use this method.
  `columnReferences()` explicitly copies references for compatibility helpers;
  editing that returned array cannot replace source slots. Its lists remain live.
  Persistence accesses columns directly without cloning the reference array.

## Consumer and compatibility constraints

The direct tint call plans and samples the retained table in Rust. Its bounded
critical entrypoint allocates nothing and never waits for locks. It limits work
to 4,096 points, 49 samples, 16 collisions per key and 128 compared UTF16 units.
Larger workloads retry through the ordinary native call. Declines leave the
color and position output untouched. Both routes share the
[Frozen tint policy](../biome/RUST-DH-TINT.md), including signed coordinate
behavior, first misses and mutation prefixes.

Custom or Java-backed lists remain explicit compatibility inputs. A table
marker declines before sampling them; the existing borrowed-span admission and
original Java traversal preserve empty legacy lists and custom callbacks.
Resolver callbacks never run under native locks. Same-detail preparation and publication also read retained source/recipient
columns in Rust. Source-owned identity proofs rebuild only after structural
binding changes; preparation rejects aliases and direct publication rejects a
stale recipient structural version. First writes detach producer spans in Rust;
Java preserves list identity and refreshes its CPU projection. A bounded native
call copies numeric addresses/counts/hashes/flags into reused heap arrays;
Java does not perform thousands of checked FFM reads for that publication. Generation and
compression policy publishes into the existing semantic byte arrays, with Java
projecting uniformity booleans and sparse contributor actions. Immutable producer-hash admission checks typed native spans and signed metadata
without constructing absent Java projections. Base-state hashes
read primary points and borrowed signed metadata directly, with a bounded
65,536-point/4,096-points-per-column critical call. Oversized or custom inputs
keep their original hashing route. Preparation streams columns directly; absent contributor descriptors use one
immutable default table rather than an allocation per merge. Source orchestration, byte storage,
persistence codecs, contextual colors and geometry remain migration work.
The subsequent [source metadata migration](RUST-DH-SOURCE-METADATA.md) moves
generation/compression/uniformity storage and direct parent transport into Rust.
Its fresh verification is separate from the historical results below. Occlusion
transport and source orchestration remain work.

## Verification

Use JDK25 and the standard release profile:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib world::level::chunk::dh_columns
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests 'net.minecraft.world.level.levelgen.NativeDh*Test'
python3 DevUtils/RunWiki.py check
```

The author reports that the combined tree based on upstream `d4400d33e`,
including audio policy and grouped lifetimes, passes the serial full Rust suite: 2,568 passed,
three ignored, zero failures. Full Java reports 1,933 tests, two skips and zero
failures/errors across 346 suites (1,931 executed). All six workers mapped release
`86f20f9b`. Source/native/compiled-class/Frozen/fixture/protected-prompt guards
match before and after timing. Frozen remains unchanged.

Controls replay actual Frozen tint and same-detail corpora and cover signed
metadata, hash collisions, mutation/remap invalidation, stale structural
versions, producer detachment, aliases and closed readers. The integration
regression checks shared arena scopes for lazy, existing-empty, missing and
unprojected merge recipients; retained readers remain writable after table
close. It does not prove GC release, crash latency or sustained memory.
The earlier parallel Rust invocation failed on X11/GLX `BadAccess`; its log
remains. A serial pass does not fix that race. An obsolete Java private
hash-field assertion initially failed; its corrected control checks public
hash behavior and preserves detached-owner checks. Initial failures are retained.

The author’s synthetic CPU measurements use six alternating AB/BA pairs per workload,
identical compiled drivers, matching checksums and five warmup rounds at the
measured operation count in every JVM. Builds, tests, observers, profiling and
source hashing run outside timing windows. Medians:

| Workload | Operations | Current ms | Frozen ms | C/F |
| --- | --- | --- | --- | --- |
| Create and close source | 1,000 | 5.96 | 15.06 | 0.396 |
| Repeated same-detail update | 1,000 | 102.18 | 71.02 | 1.439 |
| Tint radius 0, detail 6 | 500,000 | 2.93 | 3.04 | 0.964 |
| Tint radius 1, detail 6 | 500,000 | 55.99 | 84.09 | 0.666 |
| Tint radius 2, detail 6 | 100,000 | 22.85 | 44.68 | 0.512 |
| Tint radius 3, detail 6 | 50,000 | 20.81 | 42.12 | 0.494 |
| Tint radius 3, detail 7 | 50,000 | 20.34 | 41.84 | 0.486 |
| Tint radius 3, detail 8 | 500,000 | 3.03 | 3.32 | 0.914 |

Creation and all six tint cases have author-reported Current medians below
Frozen in these synthetic scenarios; detail 8 uses the unsmoothed tint-cache
route. These ratios are not isolated renderer or server gains. Repeated
same-detail updates **fail** scoped performance parity: 1.439× Frozen. Primary
hash reuse, exact copy proofs and fused traversal improve earlier candidates,
but that gap still needs profiling and further migration. This intermediate
ownership batch does not establish whole-game performance acceptance.
At that checkpoint, generation/compression storage, uniformity, orchestration,
persistence and parent/occlusion transport remained work. The subsequent
[metadata migration](RUST-DH-SOURCE-METADATA.md) records newer ownership and
verification separately.

Use [`VerifyDhSource.py`](https://github.com/HungLo2020/MattMC/blob/52664da430edab005b09f64e2df29eaa79db95a1/DevUtils/tests/world/chunk/VerifyDhSource.py) with JDK25, the isolated
Frozen/current classpath files, `--native-dir build/rust/native`, a fresh
`--output` directory and `--mode create` or `merge`. The identical compiled
workload runs five warmup rounds per JVM and alternates AB/BA; use six pairs for
acceptance measurements. Preserve source/native/compiled-class guards and keep
builds, tests, profiling and source hashing outside timing windows.
Receipts are retained in `build/native-dh-source-owner/`; failed initial builds
remain there. Supplemental CPU fixtures and timing do not establish gameplay,
FPS, sustained streaming or long-running host/GPU memory acceptance.

The source benchmark uses a synthetic 4,096-column input with four points per
column. Create mode includes hashing and close; merge mode changes the first
input point, updates the target and hashes it each operation. The script checks
exit/result shape, matching checksums and the native image before each run,
but only records a timing ratio: no speed threshold controls its exit status.
It initially hashes the compiled driver inputs, not every Frozen/current
classpath entry. Warmup and measured rounds are separate CLI options; setting
them equal is protocol guidance, not a constraint enforced by the script.
Broader before/after source and Frozen integrity checks are
author-reported receipt evidence, not additional checks implied by this command.

The [ownership/lifetime review](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6104462433)
and [reported CPU evidence review](https://github.com/HungLo2020/MattMC/issues/709#issuecomment-6104463092)
retain these compatibility, grouped-adoption and acceptance limits. Both issues
remain open; this documentation review ran no suites or runtime workloads.

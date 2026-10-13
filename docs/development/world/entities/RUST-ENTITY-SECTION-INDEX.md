# Rust entity-section coordinates and spatial index

Current source routes production `EntitySectionStorage` through the existing
Rust library for its primary coordinate map, ordered key index, chunk relations
and spatial coordinate search. Both client and server section managers use
that storage. Java retains section behavior bindings, initial visibility
callbacks and consumer callbacks. Primary section visibility and combined
untyped/stock-class traversal and member scanning reside in Rust. See the
[member-query constraints](RUST-ENTITY-SECTIONS.md#range-queries) and the current
stock typed traversal results below.
The subsequent [residency owner](RUST-ENTITY-RESIDENCY.md) moves primary chunk
residency and persistence admission into Rust with scoped verification.
Transition orchestration, behavior callbacks and persistence I/O remain further
migration work.

Implementation: [native owner](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/index_ffi.rs),
[physical map](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/section_map.rs),
[threaded ordered index](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_sections/section_tree.rs)
and [behavior bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/entity/NativeEntitySectionIndex.java).
This complements [native section membership](RUST-ENTITY-SECTIONS.md).
There is no Java primary section map or sorted-key mirror, no per-query Java
candidate array, and no new crate or GPU handle crossing this boundary.

## Mutation and lifetime rules

- Preserve Frozen's captured hash insertion slot across the initial visibility
  callback. Reentrant mutation can replace/shrink the table or overwrite that
  slot. Preserve committed prefixes and failures; do not re-find the key.
- Position streams capture live threaded nodes, including removed prefetched
  nodes. They are neither immutable snapshots nor newly fail-fast iterators.
- Spatial traversal supplies both key and current binding in one native call;
  position streams preserve their separate live lookup behavior.
- Spatial searches leave the next raw node and future x-ranges unopened until
  the next call. A consumer may mutate future sections before traversal resumes.
- Native bindings are generational and retired promptly. Cursor leases retain
  the owner; explicit close removes their registrations. Automatic arenas
  release abandoned leases without a Java callback under the native mutex.
- Closed spatial cursors return to a small per-index pool (up to 8), and
  `mattmc_section_index_spatial_reopen` restarts one with new bounds, so entity
  queries allocate no lease or arena per search. Each cursor also retains one
  bounded match buffer without entity references. Reopening an open lease fails.
  Nested searches take another pooled cursor or allocate one.
- Thread links use an arena with reachability-based retirement rather than
  owning reference cycles. Unreachable retired slots are reused. Follow the
  existing world-thread mutation contract; this does not add concurrent APIs.
- Java chunk sets are required API snapshots projected from native key order;
  they do not serve as a second primary coordinate map.

## World queries

The [native result-collection slice](RUST-ENTITY-COLLECTION.md) extends stock
non-spectator queries across sections; see its scoped verification and remaining
Frozen component CPU gaps.

Untyped `EntitySectionStorage.getEntities` now joins spatial traversal,
section visibility and root-member bounds scans inside Rust. Empty, hidden
and nonmatching sections are skipped without a Java round trip. Matching
sections return their first bounded batch directly to the existing member
consumer, avoiding a second scan. Visibility is primary native state; Java
projects the enum and requests transitions.

Native index bindings retain typed member owners. Removing a binding releases
that lease while independently referenced sections remain usable. Dense
binding slots retain their generation checks. Cursor slots remain reserved
while closed leases are pooled or otherwise reachable, and are reused only
after lease release. Neither slot table retains Java objects or GPU handles.

Preserve these callback boundaries:

- Finish the current section's callbacks before opening future sections.
  Do not prefetch future section decisions across a callback without a valid
  rewind mechanism that retains the original live cursor nodes.
- Check member modification and geometry revisions before consuming cached
  matches. Keep iterator end/failure ordering, including failed mutations.
- Stock `forClass` queries select admitted typed groups in Rust. Java assigns
  a stable CPU class identity once and registers each group after successful
  lazy construction. Unknown classes yield at the original section boundary;
  empty roots/hidden sections still skip class admission, and null visibility
  still fails before type selection. No Java class object is stored in Rust.
- Custom casts retain ordered Java dispatch because they may move entities
  before their bounds check. Custom section/storage behavior also retains its
  callback boundaries. A typed batch carries its own group's modification
  count and geometry revision, so callbacks invalidate prefetched matches.
- Custom section/storage overrides and null visibility retain their Java
  behavior. No native lock spans a Java callback.

### Stock typed traversal: scoped verification, 2026-10-13

Combined traversal now consumes stock class groups, including
empty typed groups in otherwise nonempty sections. The native section cursor
returns the first typed match batch directly to its consumer; Java no longer
re-enters each admitted nonmatching section. Existing typed member arrays and
primary geometry are consumed in place. Java retains class descriptors, lazy
membership construction and entity behavior; full type-system/simulation
migration remains open. The pre-change gameplay profile at `efe94ff71` identifies
section-query boundary work; it does not measure this migration's improvement.

Verification adds derived-class cold/warm queries, nested callbacks, over-128
match batches and future-section mutation to the actual Frozen observer.
Final native `a9928d75` passes 2,764 Rust tests (3 ignored), 2,102 Java tests (2
skipped), and all 6,735 actual Frozen observer cases. The first timing attempt
was rejected for measured JIT activity. Subsequent attempts keep
output outside sample loops and use matching `-Xbatch` controls with 32
warmups and eight compiler-quiet samples in nine alternating JVMs.

Per 4,096 production typed queries over 256 one-entity sections:

| Workload | Current ms | Prior Java traversal ms | Frozen ms |
| --- | ---: | ---: | ---: |
| Empty typed groups | 23.37 | 45.04 | 25.16 |
| No bounds hits | 35.00 | 73.83 | 34.22 |
| All bounds hits | 63.71 | 79.01 | 35.88 |

Warmed Current Java allocation is zero, versus 16.88 MB for the traversal
control and 16.88–50.43 MB for Frozen. These are component results, not total
memory or gameplay acceptance. Small-section CPU and all-hit Frozen gaps
remain. Source/class/native/Frozen guards pass; final receipts are in
`build/native-typed-section/oracle-3/`. The rejected timing is in `oracle-1/`;
`oracle-2/` records the previous candidate at 33.61 ms for empty groups.
Skipping their unnecessary geometry lock reduces that cost to 23.37 ms,
about 7% below Frozen. Bounds misses remain about 2% above Frozen and all-hit
queries remain substantially slower. These gaps guide further migration.

One fresh 6,000-frame Current/Frozen gameplay pair per mode passes the scoped
FPS and p99 floors:

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1241.4 / 1095.0 | 3.522 / 4.213 |
| Shaders | 354.9 / 300.0 | 5.356 / 7.122 |
| DH | 801.0 / 693.5 | 4.060 / 6.067 |
| Shaders + DH | 278.9 / 227.5 | 5.769 / 8.859 |

Inputs and JVM controls match; live entity counts are respectively 190/190,
180/178, 165/166 and 164/166. These single pairs do not isolate this migration's
throughput gain or establish sustained performance acceptance. Vanilla and
shaders+DH settled comparisons and world unload/reload, resource reload and
view-distance decrease pass with zero VUIDs. Seven original images, five
Current presentation correlations and nine native identities were checked.
Hazy lifecycle images establish structural behavior only; first-turn continuity,
the first shader world frame, broad visual coverage and long-running memory remain open.
Thirteen completed copied worlds were retired with originals unchanged.

Receipts: `build/native-typed-section/{runtime,gameplay-comparison,final-integrity}.json`.
Originals: `artifacts/graphics-captures/validation/native-typed-section-20261013/`.
Reproduce the component comparison:

```sh
python3 DevUtils/tests/world/entity/VerifySectionMembers.py \
  --java-home /path/to/jdk25 --output build/typed-world-query \
  --typed-world-benchmark --java-scan-control-ref e0cf94984
```

### Historical combined untyped traversal

Native `a554bfbc` passes 2,746 Rust tests (three ignored), 2,070 Java tests
(two skipped), 6,663 exact Frozen membership/world-query cases and 6,721 index
cases, with source/class/native guards.
The expanded oracle covers nested queries, future-section creation/removal,
visibility changes, batches over 128 matches and failures after removal
callbacks. Initial exception-text and failed-mutation exception-type mismatches
were corrected; the failed receipts remain.

The enclosing-query benchmark uses nine alternating JVMs, five warmups and
eight samples per workload. Each sample executes 4,096 production world
queries with one primary entity per section. The Java-traversal control uses
published `04e2c47f0` Java adapters on the candidate native image; it is not a
historical whole-game binary. Current's warmed Java allocation is zero; Frozen
allocates 96 KiB–3.22 MiB per sample in these workloads. This does not measure
total host/native memory or retention.

| Workload | Current ms | Java-traversal control ms | Frozen ms |
| --- | ---: | ---: | ---: |
| 256 sections, no hits | 34.94 | 57.20 | 21.88 |
| 256 sections, all hits | 60.29 | 60.31 | 22.78 |
| One section, one hit | 0.693 | 0.736 | 0.406 |

No-hit queries improve 38.9% against the control; all-hit queries match it.
Frozen remains faster overall in this workload. The first candidate regressed
all-hit CPU. A diagnostic wall profile placed 93.4% of query samples in the
native cursor; dense reusable slots replaced its hash lookups and direct batch
metadata removed extra foreign-memory reads. The earlier candidates and
profile remain in `build/native-world-entity-query/`. These CPU measurements
exclude gameplay, GPU work and long-session memory acceptance.

Four clean 6,000-frame vanilla windows in Current/Frozen/Frozen/Current order
produce medians of **1,235.60/1,206.95 FPS** and **3.606/3.487 ms p99**.
Individual FPS runs are 1,218.2/1,233.7 and 1,253.0/1,180.2. FPS passes;
p99 misses Frozen by 3.4%. Saved inputs, settings, camera and heap/GC/JIT
controls match. Live entity counts are Current 188/186 versus Frozen 180/175.
This is scoped vanilla evidence, not an isolated gameplay gain or all-mode
performance acceptance.

Two settled image pairs (vanilla and Iris+DH) and world unload/reload, resource
reload and view-distance decrease pass with zero VUIDs. Seven original images,
five Current presenter identities and seven native image identities were
reviewed. Lifecycle views are heavily hazy and establish structural behavior
only; they do not resolve first-turn terrain gaps or long-session memory.
Nine completed copied worlds were retired; original captures, measurements,
profiles and failed attempts remain in `build/native-world-entity-query/` and
`artifacts/graphics-captures/validation/native-world-entity-query-iteration-20261012/`.

```sh
python3 DevUtils/tests/world/entity/VerifySectionMembers.py \
  --java-home /path/to/jdk25 --output build/world-entity-query-reference \
  --world-benchmark --java-scan-control-ref 04e2c47f0
```

## Historical coordinate-index verification

Native `168dd924` passes thirteen focused controls,
including 20,000 randomized AVL mutations, physical hash resize/shrink,
callback slot capture, detached-node retention and owner/cursor lifetimes.
The expanded actual Frozen corpus matches all 6,721 cases with source/class/
native guards; manager lookup matches all 4,417 cases. Full suites pass 2,698
Rust tests (three ignored) and 2,022 executed Java/parity tests (two skipped).
The initial fixture failure omitted registry bootstrap; that failed attempt
and the preceding 6,693-case replay remain in `build/native-section-index/`.

The fixed-source controller passes seven lifecycle cases, two world comparisons
and cow body/shadow parity with zero VUIDs. Fourteen original images were
individually reviewed and ten Current native identities match. Small head
orientation/shading differences are outside the body/shadow fixture scope;
these checks do not accept broad animation, leash or HUD behavior. All twenty matched timing runs finish cleanly, with identical live heap/GC/
JIT controls and final source/native/input guards. Both shader modes pass;
vanilla and DH FPS miss by 0.75% and 6.41%, while every p99 floor passes.
Aggregate performance remains unaccepted; no isolated gain/regression cause
is claimed. See the [full matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#local-section-coordinate-checkpoint).
These CPU and scoped settled-runtime checks do not establish broad runtime
parity, temporal behavior, performance or long-session memory acceptance.
`SUMMARY.md` records the latest published measurements; this index matrix is
historical. Candidate receipts:
`build/native-section-index/candidate/`.

Build with JDK25 and a release native library, then run:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/world/entity/VerifySectionIndex.py \
  --java-home /path/to/jdk25 --output build/section-index-reference
CARGO_TARGET_DIR=build/rust/target cargo test --profile suite \
  --manifest-path src/main/rust/Cargo.toml world::level::entity_sections \
  -- --test-threads=1
```

The observer compiles separately against actual Current and unchanged Frozen
production classes. Its fixtures exercise mutation/query order, lazy streams,
removal/reinsertion/rotations after cursor creation, callback exceptions and
reentrant physical-map insertion. Source, compiled class and loaded native
identities must remain unchanged throughout replay. CPU replays supplement
real gameplay, performance and long-session resource checks.

# Retained-section collision traversal

The collision traversal moves ordered world-cell traversal and static-shape
intersection decisions into Rust, reading retained primary block/counter owners.
Java receives a candidate state or a resolved hit and projects the existing
collision shape for the result callback. Java builds the query voxel shape lazily
only when a contextual collision needs its remaining Java join. Contextual
providers keep their original calls. General entity movement remains migration work.

Implementation: [native traversal](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/collision_scan),
[chunk adapter](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeBlockCollisionScan.java)
and [production iterator](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/BlockCollisions.java).
The adapter shares the [retained chunk inputs](../chunk/RUST-DH-CHUNK-INPUTS.md);
it does not export cells or create another world cache.

## Constraints

- Preserve X/Y/Z cursor order. Stop before changing chunks so the original
  chunk getter, including missing results, stays ordered. Bound native work to
  4,096 cells per call and decline wrapping or oversized cursor extents.
- Static cached geometry alone does not prove that a contextual collision call
  is constant. The semantic catalog admits audited context-free providers
  separately. Fluids, powder snow, moving pistons, custom providers and custom
  collision contexts keep their original callbacks. Stock face/edge exclusion
  precedes contextual lookup in Rust, just as in the original iterator; excluded
  contextual cells require no Java return. Suffocation queries retain their
  predicate order.
- Full-block identity uses the original strict AABB comparison. Static voxel
  intersection retains the original shape/query grids, including empty coordinate
  intervals, and reproduces query-shape snapping and ordered epsilon merging.
  Merged boxes alone are insufficient: dropping a leading empty interval changes
  the epsilon merge anchor at a top slab's lower edge.
  In particular, the zero-bit query shortcut can return a full block even when
  near-zero endpoints both round to zero; do not simplify that behavior.
- Re-read primary owners after callbacks. Do not precompute a result batch across
  Java calls: a result provider can mutate the next cell or replace storage.
  Unescaped structural chunk bindings are reused. Array escape revalidates
  section/container identities; Rust detects retired palettes and requests
  re-admission. A scoped reader is reused within a section and dropped before
  switching owners or returning to Java.
- The iterator's mutable result position may escape. After skipping a prefix,
  publish its last visited non-corner position, including when exhaustion yields
  no additional collision. Skipped corners and missing chunks do not change it.
- No Java callback runs under a Rust owner lock. Existing world mutation
  exclusion still applies; this does not create an atomic concurrent snapshot.

## Verification

The final combined version includes upstream `8e7c5e496`, which moves DH
full-data sources and updates into Rust. Integration preserved all 22 collision
paths without conflicts. Native `2ad8899a` passes 2,759 Rust tests (3 ignored),
2,095 Java tests (2 skipped) and all 231,212 Frozen comparisons. Query CPU is
80.61/79.93 ms Current/Frozen per 65,536 queries (within 1%), with
34.25/75.23 MB Java allocation (54.5% less). Source/class/native/Frozen guards
pass. The incoming [DH source guide](../chunk/RUST-DH-SOURCE.md) records its
intentional differences from Frozen; these are not collision parity claims.

Fresh single 6,000-frame Current/Frozen pairs on that combined version:

| Mode | FPS | p99 ms | Live entities |
| --- | --- | --- | --- |
| Vanilla | 1331.2 / 1262.8 | 3.347 / 4.006 | 186 / 192 |
| Vanilla + DH | 794.2 / 663.6 | 4.334 / 7.003 | 167 / 166 |

Saved workload inputs/settings and live JVM controls match within each pair.
Both scoped FPS/p99 floors pass, but single pairs and differing live entity
counts do not establish a sustained or isolated collision-migration gain.
The shader+DH settled pair and world unload/reload, resource reload and
view-distance reduction pass, with zero reported exceptions, panics, ordering
violations or GAL validation errors. Five originals, four presenter correlations
and six Current native-image identities were reviewed. Lifecycle originals are
heavily hazed: structural evidence only. First-turn terrain gaps, first shader
world-frame behavior and long-running memory acceptance remain open.
Eight completed copied worlds were retired; originals and failures remain.
Receipts: `build/native-collision-scan/integrated-*` and
`artifacts/graphics-captures/validation/native-collision-scan-integrated-20261013/`.

### Earlier iterations (before upstream integration)

Earlier scan-only versions
passed 8,192 actual Frozen comparisons but failed CPU acceptance: 455.2/82.2 ms,
122.0/80.3 ms and 117.1/80.3 ms Current/Frozen per 65,536 queries. The Current
Java-iterator control measured 86.9 ms. Profiling found allocation in Java's
remaining shape moves and voxel joins. Their receipts remain under
`build/native-collision-scan/`; these measurements do not verify the current
static-intersection implementation. Its first expanded replay found a top-slab
mismatch at Y=0.4999999–0.5000001 after all 8,192 mixed queries passed. The native
catalog now retains original coordinate grids: all 231,212 expanded cases pass
(native708facd0). Ten Java and five native boundary tests pass. That run
rejected measured compilation activity. A subsequent compiler-quiet run
(native283df04d) passes the same comparisons but still fails CPU acceptance:
119.75/81.22 ms, with Java allocation 38.27/75.23 MB. A fresh profile places
369/397 query samples at the native scan call site. The next candidate also
owns face/edge exclusion for contextual cells: native48d8c6ad passes all 231,212
comparisons and 11 Java tests, taking 83.33/80.06 ms Current/Frozen with the same
allocation. This removes most of the regression, but the remaining 4.1% CPU
miss was not accepted. Fixed-width primary reads and lazy Java query shapes
bring the latest source (native2669a647, oracle-10) to 80.41/80.11 ms, with
34.25/75.23 MB Java allocation per 65,536 queries. CPU is comparable (+0.4%),
with 54.5% less Java allocation; this is not a measured gameplay gain. All
231,212 comparisons and full `check` pass: 2,757 Rust tests (3 ignored) and
2,098 Java tests (2 skipped), including eleven collision boundary tests.
Four clean 6,000-frame vanilla windows measure median Current/Frozen FPS
1,274.4/1,347.7 and p99 3.310/3.498 ms. The overall FPS floor still misses 5.4%;
p99 is better. Saved inputs/settings and JVM controls match; live entity counts
were 190/189/185/188 in Current/Frozen/Frozen/Current order. This does not isolate
a gameplay gain or establish all-mode performance acceptance.

Vanilla and shader+DH settled image pairs pass. World unload/reload, resource
reload and view-distance reduction pass with no reported exceptions, panics,
ordering violations or GAL validation errors. Seven original images, five
presenter correlations and seven native identities were reviewed. Hazy lifecycle
views provide structural evidence only; first-turn terrain gaps, first shader
world-frame behavior and long-running memory acceptance remain open. Source,
class, native and Frozen guards pass. Nine completed copied worlds were removed;
originals, profiles, failed attempts and measurements remain under
`build/native-collision-scan/` and the marked capture root.

The expanded observer adds every state of representative slabs, stairs, walls
and snow, probing epsilon contacts and query snapping. Timings use forty warmup
rounds, collect samples before logging, and reject compiler activity during
measured samples. The query loop is separate from fixture/bootstrap work. Earlier protocols
had two visibly warming samples after five warmups, another reason to retain
rather than reuse those results. The gameplay evidence above is separate from
these focused query measurements.

With JDK 25 and the matching release library:

```sh
./gradlew -PmattmcRustProfile=release test --tests '*NativeBlockCollisionScanTest'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib \
  world::level::collision_scan -- --test-threads=1
python3 DevUtils/tests/world/chunk/VerifyBlockCollisionScan.py \
  --java-home /path/to/jdk25 --output build/collision-oracle --benchmark
```

The observer uses actual Frozen and Current collision iterators, client worlds,
chunks and palettes in disconnected client shells. It covers 2,048 seeded
AABBs, two fluid contexts and suffocation filtering with full-height chunks.
It does not establish real gameplay or rendering parity. Run timings with
builds, tests, profilers and other clients stopped. Keep failed receipts and
report the complete world-query cost, including binding/admission work.

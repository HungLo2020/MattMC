# Rust entity-section membership

`ClassInstanceMultiMap` now stores its root and cached typed member arrays in
the existing Rust library. Both client and server `EntitySection` producers
and queries consume those arrays. Java retains class descriptors, behavior
object bindings, and custom equality/type/bounds/consumer callbacks.
Ordinary untyped and stock `forClass` range queries now scan retained primary
geometry in Rust. The subsequent [coordinate/index slice](RUST-ENTITY-SECTION-INDEX.md) owns production keys and coordinate
queries with scoped verification and open performance limits. The subsequent [residency slice](RUST-ENTITY-RESIDENCY.md) has scoped verification.
Rust also owns each section's visibility value. Java still requests transitions,
dispatches behavior callbacks and performs persistence I/O.

Implementation: [native member owner](https://github.com/HungLo2020/MattMC/tree/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/rust/world/level/entity_sections),
[Java behavior bridge](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/util/NativeClassInstanceStorage.java),
and [production section consumer](https://github.com/HungLo2020/MattMC/blob/0d23141bdaf918ce7314fdf3cc2f846df35d04a3/src/main/java/net/minecraft/world/level/entity/EntitySection.java).
Quiet traversal reads retained, read-only CPU views; it does not reconstruct
a Java member list or send that list back to Rust. No GPU handles cross this
boundary. This is membership ownership, not complete entity simulation.
The current ownership description is a source review at `0d23141b`; historical
results below retain their original checkpoints. No runtime suites or receipts
were independently rerun or validated for this reconciliation.

## Compatibility and lifetime

- Preserve insertion order, duplicate members, lazy typed views and the explicit
  immutable snapshots required by the collection API. Captured backing arrays
  are live storage, not immutable snapshots: mutations without growth remain
  observable through a capture; growth leaves the captured old array alive.
  The Java class-descriptor map preserves class identity and callback ordering;
  its values refer to native groups rather than Java member arrays.
- Iterators and typed spliterators keep ArrayList's mutation/failure timing.
  Root streams still use the enclosing collection's iterator-based spliterator.
- Typed array projections preserve ArrayList's VM bounds/type failures and the
  destination's successfully copied prefix. Required caller snapshots project
  native bindings directly; they do not introduce a Java primary member list.
- Equality callbacks can mutate or throw. Removal keeps its captured backing
  array, even if a callback grows the active array, and uses the current size
  when performing the removal. Preserve committed state before a bounds failure.
- Native array leases retain generational behavior bindings through growth and
  callback traversal. Slots become reusable only after their final array/pending
  reference disappears. Java clears matching retired generations promptly.
- Never hold a native mutex across a Java callback. Follow the existing world
  thread contract; atomic views do not introduce concurrent mutation support.
  Automatic arenas keep the owner alive while collection wrappers, typed views
  or their iterators remain reachable. Scoped captures release their leases on
  exit, including exceptions; automatic owner cleanup has no deterministic GC
  deadline. Active member arrays and binding tables can retain high-water
  capacity. Reusing retired slots proves reuse, not shrink-to-live-size storage
  or a measured long-session memory bound.

## Range queries

Section insertion attaches a typed CPU geometry lease to each ordinary entity's
native membership binding. Queries scan current bounds in insertion order;
Java receives only matches or members requiring custom bounds behavior. No
per-query entity/bounds array is constructed. World transfer preserves the
retained geometry identity; the last membership reference releases its lease.

Rust produces bounded batches of candidate bindings. Bounds revisions and
membership modifications invalidate prefetched results after callbacks, so
geometry changes, early aborts and iterator failure timing remain observable.
Nested queries borrow separate scratch output; ordinary queries reuse one
bounded buffer per Java thread.
Custom type tests still run before bounds checks, since their casts may mutate
or throw. Custom/null entity bounds invalidate native admission immediately;
restoring stock bounds permits native scanning again. Arbitrary `EntityAccess`
implementations retain their bounds callback. The existing world-thread
contract applies; no native mutex crosses a Java callback.

Native `066c6679`, on upstream `5f7b28a97`, passes 2,743 Rust tests (three
ignored), 2,068 Java tests (two skipped), and 6,403 actual-Frozen oracle cases. Final review gave fixture entities distinct IDs and reran the
12 affected Java checks plus the oracle/benchmark; production code and runtime
identities were unchanged.
The CPU comparison uses nine alternating JVMs, five warmups and eight samples
per workload; each sample makes 32,768 production section queries. Java heap
allocation is zero in the warmed samples. The control overlays the published
Java scan on the candidate native image; it is not a preceding whole-game binary.

| Query workload | Current ms | Java-scan control ms | Frozen ms |
| --- | ---: | ---: | ---: |
| 256 members, no hits | 15.57 | 46.37 | 6.70 |
| 256 members, all hits | 57.44 | 58.46 | 14.68 |
| One member, one hit | 0.868 | 0.276 | 0.148 |

Large rejected scans improve 66.4% against the Java control; small sections
still regress, and Frozen remains faster. The initial per-member crossing and
first batched candidates were rejected and replaced; their measurements and
the default-hash oracle failure remain in `build/native-entity-range/`.

Four clean 6,000-frame vanilla runs give Current/Frozen medians of
**1,318.95/1,338.80 FPS** and **3.335/3.605 ms p99**. The first pair was
1,280.8/1,326.8 FPS; the fixed second pair was 1,357.1/1,350.8. Both are retained.
Average FPS misses the floor by 1.5%; p99 passes. Settings, saved-world inputs,
camera, heap/GC/JIT flags match; live entity counts vary slightly. This is
scoped vanilla evidence, not a new all-mode acceptance or an isolated speedup.

Two settled image pairs (vanilla, Iris+DH) and world unload/reload, resource
reload and view-distance decrease pass with zero VUIDs. Seven original images
were reviewed and five Current presenter identities checked. The lifecycle
views are heavily hazy and establish structural evidence only; they do not
close transient terrain defects or long-session memory bounds. Nine completed
copied worlds were retired, preserving original images, observations and failures.

Enclosing world queries now join retained coordinate traversal, visibility and
root-member scans in Rust. See the [world-query owner and current measurements](RUST-ENTITY-SECTION-INDEX.md#world-queries).
The table above remains historical member-query evidence. Receipts:
`build/native-entity-range/`; add `--benchmark` to the command below to repeat
that member-query workload.

## Verification

Use JDK25 on Linux x64, a built release library and unchanged Frozen at
`../MattMC_JavaPerfTesting/MattMC`:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/world/entity/VerifySectionMembers.py \
  --java-home /path/to/jdk25 --output build/section-members-reference
CARGO_TARGET_DIR=build/rust/target cargo test --profile suite \
  --manifest-path src/main/rust/Cargo.toml world::level::entity_sections \
  -- --test-threads=1
./gradlew test --tests net.minecraft.util.ClassInstanceMultiMapNativeTest \
  --tests net.minecraft.world.level.entity.EntityLookupIntegrationTest
```

The observer compiles separately against actual Current and Frozen production
classes. It compares root/typed membership, equality-driven growth/removal,
live views, iterator/stream/spliterator callbacks and actual section queries.
It pins source, compiled classes, loaded native image, Frozen HEAD and the
protected prompt. Both JVMs use `-XX:-OmitStackTraceInFastThrow` so repeated VM
bounds exceptions retain their full messages; output is not normalized.
The callback oracle additionally uses matched `-XX:+UnlockExperimentalVMOptions
-XX:hashCode=2` to hold class-key map order constant between processes. A prior
default-hash run matched all 280 spatial queries but differed in unrelated
class-map traversal order; that failed receipt remains. Benchmarks and gameplay
use the normal identity-hash policy and unchanged Frozen source.

The historical candidate (`6076262e`, including the DH visibility change) passes
2,691 Rust tests (three ignored) and 2,018 executed Java/parity tests (two
skipped). Six native controls include 10,000 retired-slot reuse cycles. The
actual Frozen membership corpus matches all 6,123 cases; the production
manager lookup replay matches all 4,417, with source/class/native guards.
Review found and repaired a typed-array VM-error and destination-prefix
mismatch before these checks. The initial 5,691-case corpus, full-message JVM
control failure and pre-repair results remain in `build/native-entity-sections/`.

The [original membership observer](https://github.com/HungLo2020/MattMC/blob/0cea5de1d07fc0f569576a8938be37416e86eb66/DevUtils/tests/world/entity/VerifySectionMembers.java)
and [guard wrapper](https://github.com/HungLo2020/MattMC/blob/0cea5de1d07fc0f569576a8938be37416e86eb66/DevUtils/tests/world/entity/VerifySectionMembers.py)
bound that 6,123-case record: the wrapper requires matching counts of at least
5,500, not exactly 6,123. Its section fixtures use intersecting bounds and
custom `tryCast` returns the supplied member. They cover bounds/cast/consumer
ordering, callback mutation, aborts and exceptions, but do not establish
rejected spatial filtering, a rejecting cast or manager lifecycle behavior.
The 4,417 manager replay is separate evidence. Later range and world-query
corpora extend this scope; do not backdate those checks to this checkpoint.

The preceding `d4348edd` candidate passes seven lifecycle checks, two world
comparisons and the cow/body-shadow pair with zero VUIDs. Fourteen original
images were individually reviewed and ten Current native identities match.
Its complete matched-JVM twenty-run matrix misses vanilla p99 by 4.25% and DH
FPS by 18.2%; both shader modes pass. Earlier unmatched-GC results are retained
as confounded evidence. See the [full measurements and protocol](../../rendering/GAMEPLAY-PERFORMANCE.md#local-entity-section-checkpoint).
Matched profiles selected a subsequent retained-DH traversal/output change;
fresh gameplay checks pass with zero VUIDs, all fourteen originals reviewed
and ten native identities matched. Twenty matched timings finish with clean guards: DH and both shader modes
pass both floors, while vanilla misses FPS by 0.38% and p99 by 6.18%. Aggregate
performance remains unaccepted; no regression or improvement is attributed
to this slice in isolation.
These CPU and scoped runtime results do not establish broad gameplay,
long-session memory bounds or an isolated section-storage speedup.

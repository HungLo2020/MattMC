# Rust entity-section membership

`ClassInstanceMultiMap` now stores its root and cached typed member arrays in
the existing Rust library. Both client and server `EntitySection` producers
and queries consume those arrays. Java retains class descriptors, behavior
object bindings, and equality/type/bounds/consumer callbacks. The subsequent [coordinate/index slice](RUST-ENTITY-SECTION-INDEX.md) owns production keys and coordinate
queries with scoped verification and open performance limits. Visibility and persistence
orchestration remain Java and are subsequent migration work.

Implementation: [native member owner](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/entity_sections),
[Java behavior bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/util/NativeClassInstanceStorage.java),
and [production section consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/entity/EntitySection.java).
Quiet traversal reads retained, read-only CPU views; it does not reconstruct
a Java member list or send that list back to Rust. No GPU handles cross this
boundary. This is membership ownership, not complete entity simulation.

## Compatibility and lifetime

- Preserve insertion order, duplicate members, lazy typed views and immutable
  snapshots. The Java class-descriptor map preserves class identity and callback
  ordering; its values refer to native groups rather than Java member arrays.
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
  Automatic arenas keep the owner alive while collection wrappers are reachable.

## Verification

Use JDK25 on Linux x64, a built release library and unchanged Frozen at
`../MattMC_JavaPerfTesting/MattMC`:

```sh
./gradlew -PmattmcRustProfile=release buildRustNative classes
python3 DevUtils/tests/world/entity/VerifySectionMembers.py \
  --java-home /path/to/jdk25 --output build/section-members-reference
CARGO_TARGET_DIR=build/rust/target-tests cargo test --profile suite \
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

The latest candidate (`6076262e`, including the DH visibility change) passes
2,691 Rust tests (three ignored) and 2,018 executed Java/parity tests (two
skipped). Six native controls include 10,000 retired-slot reuse cycles. The
actual Frozen membership corpus matches all 6,123 cases; the production
manager lookup replay matches all 4,417, with source/class/native guards.
Review found and repaired a typed-array VM-error and destination-prefix
mismatch before these checks. The initial 5,691-case corpus, full-message JVM
control failure and pre-repair results remain in `build/native-entity-sections/`.

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

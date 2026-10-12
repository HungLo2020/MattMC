# Rust entity geometry

Rust owns primary free-entity geometry, removal state and ordinary camera
queries. Removal retires camera membership immediately while retaining readable
geometry and callback behavior. AI,
physics integration and networking remain in Java. The subsequent
[ticking membership slice](RUST-ENTITY-ORDER.md) moves snapshot/order storage
into Rust; Java still invokes simulation callbacks.
The [current combined checkpoint](../../rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary)
records fresh runtime/performance results; measurements below retain their
historical native identities.

## Ownership and consumers

Each `Level` lazily owns a Rust geometry store. Entity construction attaches
primary position and physical bounds through a generational slot. Rust applies
movement equality, floor and block/chunk-coordinate changes, and prepares the
ordinary camera bounds. Java keeps immutable `Vec3`, `BlockPos`, `ChunkPos` and
`AABB` compatibility projections and publishes them before movement callbacks.
An entity constructed without a level has a separate owner until transferred.

The stock camera frustum submits its matrix and camera origin once per world
revision. Rust scans its retained dense components and writes visibility flags
into their read-only CPU views; Java sends no entity/bounds array. Another
frustum's query, a mutation or a camera offset invalidates cached results.
Storage order does not replace Java's gameplay or extraction order.

See source `c1428dbc7`: the [Rust owner](https://github.com/HungLo2020/MattMC/blob/c1428dbc7a1a0b190cb9e7bc2c5c849e99ec3f42/src/main/rust/world/level/entity_geometry/mod.rs),
[CPU bridge](https://github.com/HungLo2020/MattMC/blob/c1428dbc7a1a0b190cb9e7bc2c5c849e99ec3f42/src/main/java/net/minecraft/world/entity/NativeEntityGeometry.java)
and [camera consumer](https://github.com/HungLo2020/MattMC/blob/c1428dbc7a1a0b190cb9e7bc2c5c849e99ec3f42/src/main/java/net/minecraft/client/renderer/entity/EntityRenderer.java).
These are CPU resources; GPU ownership remains entirely in Rust/GAL.

## Compatibility and lifetime

- Rust preserves Java `!=`, signed-zero/NaN behavior, saturating conversion and
  wrapping floor edges. Position mutation alone does not translate physical bounds.
- Capture prepared bounds before hooks. Reuse their immutable projection until
  the prepared coordinates change. If a hook mutates geometry, test the captured
  bounds against the resulting frustum; the next query observes the new owner.
- Always invoke renderer culling-bound callbacks before admission, including
  living-entity equipment queries. A callback returning the current primary
  immutable box can use the owner when the box and frustum have exact stock
  classes and hooks are empty or consist solely of `RustGalEntityCullingHook`.
  Changed captures, derived/custom boxes, custom hooks and frustum subclasses
  retain their original preparation and
  callbacks. Leash-holder callbacks still occur after frustum rejection;
  an earlier hook veto returns immediately, as in the compatibility route.
- Nested and throwing movement callbacks observe the committed position and
  compatibility projections. World transfer preserves the primary CPU view.
- Removal reason storage and first-reason selection now belong to Rust.
  Java's `RemovalReason` enum is a compatibility projection; its declaration
  order maps to native codes 1–5, with 0 representing null. Internal reads
  preserve private-field semantics when a subclass overrides the public getter.
- The first nonnull removal retires the generational camera slot immediately.
  Retained leases keep primary geometry readable without remaining in the dense
  scan. Detached movement does not invalidate live camera results. Explicit
  queries of detached bodies still execute Rust's ordinary culling policy:
  `Frustum.isVisibleEntity` checks `indexed()` and uses `queryOne`, rather than
  the retained dense-query visibility flag. This does not restore membership.
- Revival reindexes the same view in its current world. World transfer preserves
  removal state and detached status; transferring a removed body does not insert
  it into either world's scan. Reindexing may reuse a slot with a new generation,
  while the retained view identity stays unchanged.
- Java's [removal callbacks](https://github.com/HungLo2020/MattMC/blob/c1428dbc7a1a0b190cb9e7bc2c5c849e99ec3f42/src/main/java/net/minecraft/world/entity/Entity.java)
  run after the native transition: the retained reason controls `stopRiding`,
  then passenger dismounts, `levelCallback.onRemove` and `onRemoval` run in order.
  The last two receive the requested reason, even on a repeated removal that
  retains an earlier reason. A thrown callback leaves the completed prefix in
  place; callbacks may revive or remove again. No native lock spans callbacks.
  With no retained reason, `setRemoved(null)` still throws before callbacks and
  leaves camera membership intact. With a retained reason, a null request keeps
  it and follows the same callback path with null as the requested argument.
- Native leases retain their world independently of Java level identity.
  Scoped lease closure removes its slot. Production lease collection follows
  Java reachability, but dense membership no longer waits for GC after removal.
  Unspawned, nonremoved bodies still acquire slots at construction; tracking
  admission, section associations and other gameplay components remain work.
  Camera membership is separate from the ordered ticking store and does not
  itself migrate gameplay despawn behavior.
- Queries and compatibility projections follow the existing world-thread
  contract. Atomic CPU fields do not make arbitrary concurrent Java entity
  mutation a supported snapshot API.

## Verification

The prior review at `f92ea29d` inspected source without rerunning workloads.
This reconciliation inspects source and verification drivers at `c1428dbc7`;
it does not rerun workloads or independently inspect the runtime receipts.
The results below are author-recorded, including the fresh combined
implementation checks for native `9f372451` and the historical measurements.
Use Linux x64, JDK25 and the release native library:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests net.minecraft.world.entity.NativeEntityGeometryTest \
  --tests net.minecraft.world.entity.EntityGeometryIntegrationTest
python3 DevUtils/tests/world/entity/VerifyEntityGeometry.py
```

The oracle compiles the same driver separately against actual Frozen and
Current classes. It records 11,489 movement/removal/callback and renderer culling cases,
then checks Current against that output with source, class and native-image
guards. It does not establish realistic runtime or performance parity.
The [driver](https://github.com/HungLo2020/MattMC/blob/c1428dbc7a1a0b190cb9e7bc2c5c849e99ec3f42/DevUtils/tests/world/entity/VerifyEntityGeometry.java)
uses allocated entity fixtures and an explicit confined geometry scope. It
does not exercise production automatic-scope reclamation; real unbound
construction, transfer to a mocked `Level` and explicit closure have separate
integration controls.
Its wrapper requires Frozen at `../MattMC_JavaPerfTesting/MattMC`, checks the
recorded Frozen HEAD and protected prompt hash, and verifies the loaded image
through `/proc/self/maps`.
The 432 added removal sequences cover all first/repeated reasons, nulls,
callback exceptions, reset during callbacks, overridden public reason getters,
and explicit culling before and after revival. They use empty passenger lists
and overridden dismount callbacks; they do not exercise a full mounted-entity
lifecycle. Separate [native controls](https://github.com/HungLo2020/MattMC/blob/c1428dbc7a1a0b190cb9e7bc2c5c849e99ec3f42/src/main/rust/world/level/entity_geometry/tests.rs)
inspect dense counts, slot reuse and stable view identity. The
[Java integration controls](https://github.com/HungLo2020/MattMC/blob/c1428dbc7a1a0b190cb9e7bc2c5c849e99ec3f42/src/test/java/net/minecraft/world/entity/EntityGeometryIntegrationTest.java)
also cover removal, detached mutation/query, transfer and revival using real
unbound construction and a mocked destination level. These fixture checks do
not establish production automatic-scope reclamation or a long-session memory
bound.
Earlier 11,057-case receipts do not verify the new lifecycle behavior.
Full Rust tests run serially because the existing suite shares native driver
state. Receipts belong in `build/native-entity-geometry/`; retain rejected checks.

Current combined native `9f372451` on atlas/model base `71762d602` passes
fresh full serial Rust (2,680 tests, three ignored) and Java/parity (2,005
executed, two skipped), plus guarded actual Frozen geometry/removal, order
and range comparisons (11,489/4,143/645 cases). Seven live lifecycles, two
world pairs and four GUI pairs pass with zero VUIDs. All twelve original
images were reviewed; thirteen Current runtime artifacts identify this image.
The fresh twenty-run comparison passes every average-FPS/p99 floor with final
source/native/Frozen/prompt/prepared-input guards. See the
[current matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary).
No isolated lifecycle speedup or real-session memory bound is established. Receipts:
`build/native-entity-lifecycle-model-integration/alpha-mask-{cpu-results,geometry-replay,order-replay,range-replay}`
and `alpha-mask-accepted/`.

Historical pre-atlas/model lifecycle native `543a592f` passes fresh full serial Rust (2,659 tests,
three ignored) and Java/parity (2,005 executed, two skipped), zero failures.
All seven native geometry/lifetime controls pass, including 10,000 retained
removed bodies sharing one reusable camera slot rather than accumulating dense
entries. The actual Frozen geometry/removal replay passes 11,489 cases with
five Current/three Frozen compiled-class guards and the loaded native image.
Refreshed order/range comparisons also pass 4,143/645 cases. These CPU results
do not establish real-world performance or long-session memory acceptance;
seven live lifecycles and two settled image pairs pass with zero VUIDs. All
four originals were reviewed; nine Current artifacts identify this native image.
All twenty timing runs are clean and all four average-FPS/p99 floors pass, with
source/native/Frozen/prompt/prepared-input guards. The narrow vanilla p99 margin
does not isolate a speedup or resolve the preceding miss's cause. See the
[current matrix](../../rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary).
Receipts: `build/native-entity-lifecycle/{cpu-results,geometry-replay-v2,order-replay,range-replay}`.

Historical initial-consumer release `a3c8a7fb` passes 2,631 Rust tests (three ignored), 1,992 Java tests
(two skipped), and both the entity oracle and 645-case client-range oracle.
Eight new Java controls cover camera reuse, transfer/closure, numeric edges,
real unbound construction and mutation inside movement, culling and leash
callbacks. Deep-stubbed gameplay fixtures must use the real geometry owner;
mocking that owner suppresses actual movement. Five initial teleport failures
and their correction remain recorded. Seven lifecycle scenarios and two settled
Frozen image pairs pass with zero VUIDs; all four original images were reviewed
and nine Current artifacts pin this native library. All 20 timing runs are
clean; acceptance fails only the vanilla p99 floor:
Current/Frozen median 3.590/3.019 ms, despite higher average FPS (1352.35/1170.95).
This does not identify a cause or establish a speedup from the migration.
All four paired warmed CPU/allocation diagnostics pass. They still sample
80.5 MiB of Java bounds inflation and 199 MiB of GUI batch work in Current;
absolute sampled bytes are not per-frame rates or a p99 root cause. Review
found the first consumer excluded normal `LivingEntityRenderer` overrides,
which usually return the primary box. The revised consumer now invokes the
callback and admits its returned identity, preserving equipment/bounds mutation
prefixes. Eleven focused controls and 1,995 full Java tests pass (two skipped), as do
the refreshed 11,057-case entity and 645-case range Frozen oracles. All 1,155
Rust source paths and the native library remain unchanged; the serial Rust
suite result above is reused for this Java consumer revision. A fresh warmed
Current allocation profile passes its runtime and source guards: render-thread
`EntityRenderer.shouldRender` samples fall from 101 to 2 MiB, and total bounds
inflation from 80.5 to 4 MiB. These are weighted samples from fixed 15-second
windows with different frame counts, not per-frame rates or a p99 explanation.
The revised runtime passes seven lifecycle cases and both settled image pairs
with zero reported validation errors. All four original images were reviewed;
nine Current artifacts and the compiled renderer class pass identity guards.
All 20 revised timing runs are clean, and source/native/Frozen/prompt/prepared-input
guards and ten Current fingerprints pass. Performance acceptance fails only vanilla
p99: Current/Frozen 3.557/3.047 ms. Median FPS is 1315.6/1187.1, 764.2/726.85,
345.0/311.0 and 254.85/218.95 for vanilla, DH, shaders and combined; the other
p99 floors pass. Every declared repeat is retained. This matrix predates the
[new GUI template core](../../rendering/RUST-GUI-ITEM-TEMPLATES.md).
Receipts: `build/native-entity-geometry-living-consumer/`.

Allocation profiles identified ordinary Java inflation as avoidable frame work.
The earlier range/readiness timing matrix predates this implementation and
missed vanilla p99. Do not use it to claim this slice's speed or acceptance.

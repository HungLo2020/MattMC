# Rust entity geometry

Rust owns primary free-entity geometry and ordinary camera queries. AI,
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

See the [Rust owner](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/entity_geometry/mod.rs),
[CPU bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/entity/NativeEntityGeometry.java)
and [camera consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/renderer/entity/EntityRenderer.java).
These are CPU resources; GPU ownership remains entirely in Rust/GAL.

## Compatibility and lifetime

- Rust preserves Java `!=`, signed-zero/NaN behavior, saturating conversion and
  wrapping floor edges. Position mutation alone does not translate physical bounds.
- Capture prepared bounds before hooks. Reuse their immutable projection until
  the prepared coordinates change. If a hook mutates geometry, test the captured
  bounds against the resulting frustum; the next query observes the new owner.
- Always invoke renderer culling-bound callbacks before admission, including
  living-entity equipment queries. A callback returning the current primary
  immutable box can use the owner. Changed captures, derived/custom boxes,
  custom hooks and frustum subclasses retain their original preparation and
  callbacks. Leash-holder callbacks still occur after rejection.
- Nested and throwing movement callbacks observe the committed position and
  compatibility projections. World transfer preserves the primary CPU view.
- Native leases retain their world independently of Java level identity.
  Scoped lease closure removes its generational slot; production automatic
  scopes follow Java entity reachability. Removed entities can remain in this
  store until their leases are collected. Explicit active/despawn membership
  and other gameplay components remain future work.
- Queries and compatibility projections follow the existing world-thread
  contract. Atomic CPU fields do not make arbitrary concurrent Java entity
  mutation a supported snapshot API.

## Verification

Use JDK25 and the release native library:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests net.minecraft.world.entity.NativeEntityGeometryTest \
  --tests net.minecraft.world.entity.EntityGeometryIntegrationTest
python3 DevUtils/tests/world/entity/VerifyEntityGeometry.py
```

The oracle compiles the same driver separately against actual Frozen and
Current classes. It records 11,057 movement/callback and renderer culling cases,
then checks Current against that output with source, class and native-image
guards. It does not establish realistic runtime or performance parity.
Full Rust tests run serially because the existing suite shares native driver
state. Receipts belong in `build/native-entity-geometry/`; retain rejected checks.

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

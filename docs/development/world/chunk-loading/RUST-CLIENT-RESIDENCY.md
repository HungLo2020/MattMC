# Rust client chunk residency

At [source `f92ea29d`](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/main/java/net/minecraft/client/multiplayer/ClientChunkCache.java),
supported client windows route through
[Rust residency policy](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/client_residency/mod.rs).
Rust owns logical slot publication, counts, center, the diagnostic position
fingerprint and empty-section membership. The
[CPU adapter](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeClientResidency.java)
reads retained [chunk inputs](../chunk/RUST-DH-CHUNK-INPUTS.md) for ordinary empty
scans; it does not pack Java booleans or rebuild Rust section arrays.

## Working constraints

- Java still binds atomic `LevelChunk` references, supplies reference-CAS facts,
  projects scalar ring/range reads, orchestrates radius relocation, and delivers
  world/biome/hook callbacks. This is not a completed client-world migration.
- [Loaded-range consumers](RUST-CLIENT-RANGES.md) now select stock chunks through
  an incremental Rust coordinate index. Block-entity and moving-piston scans
  revalidate bounded pages between callbacks; custom bindings keep live getters.
  Coordinate publication requires an exact `LevelChunk`; indexed traversal
  also requires an exact `ClientChunkCache`. This is separate from the stricter
  world, height-binding and section checks for retained empty-counter scans in
  the [adapter](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/main/java/net/minecraft/world/level/chunk/NativeClientResidency.java).
- Count/header reads use a read-only native CPU view, without a downcall or world
  lock on each chunk lookup. These pointers are semantic owners, never GPU handles.
- Slot publication and old-count removal precede unload. New admission follows
  successful unload. Exceptions preserve the completed prefix. A failed reference
  CAS still invokes unload and drop hooks, as Frozen does.
- A refresh stops immediately after each nonempty removal. Run the Java callback
  outside native locks, then revalidate bindings and sample subsequent counters.
  Preserve the captured array when a callback changes an entry or escapes it.
- Custom chunks/sections/worlds or height-accessor bindings use their original
  getters at the original row boundary. Admit bulk geometry only for exact client
  worlds with the original chunk/world height binding.
  Native creation admits radii 0 through 127; unsupported radii retain the
  existing compatibility storage behavior. Moving the center alone does not
  evict old physical slots; radius changes still relocate them in Java order.
- `getLoadedEmptySections()` transfers the exact fastutil table once, including
  zero, capacity, collisions and iteration order. Later public mutation remains
  authoritative in that compatibility set; native empty membership is retired.
  Do not reconstruct the table by inserting an unordered membership list.
- Native bindings retain mutable primary owners, so growth remains observable.
  Replacing/dropping a slot releases its previous native binding. Independent
  snapshots remain valid. Additional strong leases require sustained memory tests.

## Verification

This documentation review inspected source `f92ea29d` and recorded tests; it did
not run game, native or Java suites. The author-recorded CPU checkpoint below
predates later combined-source changes and is not current runtime acceptance.

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib world::
./gradlew -PmattmcRustProfile=release buildRustNative
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeClientResidency*'
python3 DevUtils/RunWiki.py check
```

[The recorder](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/GenerateFrozenClientResidencyOracle.java)
executes untouched Frozen's actual private storage and outer `updateViewRadius`.
The checked-in CRS1 corpus contains 600 operations: physical slots, count, exact
empty iteration, range/index results, callback order and exception prefixes.
Its raw SHA256 is
`e6a9c21a774ff315d699f53ed619e57679dc8af7a0fc5b25fdb3f0ad800148bd`.
The [600-operation Java replay](https://github.com/HungLo2020/MattMC/blob/5de23173ae5b61fb17887589d4dff69a6b17fc77/src/test/java/net/minecraft/client/multiplayer/NativeClientResidencyParityTest.java)
uses custom chunk, section and world classes. It checks actual `Storage` and
outer radius transitions while section emptiness follows compatibility getters;
it does not establish ordinary retained-counter admission. Separate
[adapter controls](https://github.com/HungLo2020/MattMC/blob/5de23173ae5b61fb17887589d4dff69a6b17fc77/src/test/java/net/minecraft/world/level/chunk/NativeClientResidencyTest.java)
exercise retained counters, callback mutation, mutable escape and exception
prefixes. Fixture callbacks supply immutable coordinates and mutable empty facts;
the recorder does not copy Frozen's residency algorithm. Regeneration requires the
pinned **Frozen-only** classpath/class manifest and normal registry bootstrap.
Current Java Vulkan is not an oracle.

Local receipts live in `build/native-client-residency/`, including class pins,
recorder commands and the original compile/bootstrap rejection logs. CPU tests
for combined release `a1561418` pass 248 Rust world checks and 1,900 reported
Java tests (two skipped), including six focused client adapter controls. Six
final Java workers load that exact library; Current/Frozen source and protected
prompt checks pass. The combined DH semantic test's initial rejection and
reconciliation are retained separately. These are supplemental contract results.
Later [range-consumer reports](RUST-CLIENT-RANGES.md#verify) and the
[combined gameplay checkpoint](../../rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary)
record scoped hardware entry, rendering and timing checks. They do not turn
this older CPU checkpoint into current performance or full-parity acceptance;
broad streaming and long-session memory acceptance remain open.

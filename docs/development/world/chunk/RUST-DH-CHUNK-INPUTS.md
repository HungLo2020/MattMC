# Retained Rust DH chunk inputs

Local migration in progress: height fields, emitter enumeration, the whole
lighting pass and the column producer now consume one retained Rust chunk input
owner. Java caches compatibility identities on `ChunkAccess` and admits current
containers; it no longer rebuilds section pointer tables for each consumer.
This is CPU world ownership, with no rendering/GPU handle or global chunk cache.

The local [DH change-hash producer](RUST-DH-CHANGE-HASH.md) also reads these
bindings directly, retaining cached height/emitter semantics and current counters.

Implementation: [input owner](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/inputs.rs),
[transitional binding](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhChunkInputs.java)
and [chunk admission](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/ChunkAccess.java).
The existing save/load codec bridge `NativeChunkSections` is a separate,
unchanged component; this input owner also admits canonical proto chunks.

## Ownership and compatibility

- Retain the authoritative mutable block and section-counter owners through
  `Arc`. Retaining only a palette generation would hide later growth. Live
  creation, copies and stage-result adoption all use the same ownership protocol.
  Biome owners already use `Arc` and are attached only when columns need them.
- The local biome extension uses a shared native collection of primary biome
  owners. [Loaded-world sky/fog](../biome/RUST-LIVE-BIOMES.md) and column inputs
  retain that same allocation. Java caches compatibility identities once per
  binding change; color publication does not reconstruct pointer/owner arrays.
  Biome admission is independent of block storage and checks each consumer's
  registry contract. This producer also avoids a second biome-owner collection
  when columns attach the already retained input.
- Consumers index the retained bindings directly; native consumers do not rebuild
  borrowed owner vectors. Lighting passes at most nine chunk identities instead
  of a nine-chunk section pointer table. Column preparation no longer allocates
  three Java owner/pin arrays or the triplet input buffer per invocation.
- Java still validates container/counter identity and public state-wrapper
  overrides on admission. It keeps bounded compatibility references, not block,
  biome or counter value mirrors. An escaped section-array slot, replaced
  container, compatibility transfer or biome escape cannot reuse stale bindings.
- Binding replacement releases the chunk cache's previous lease after its readers
  disappear. Independently retained inputs remain valid; immutable height,
  emitter, light and column outputs own their values independently of inputs.
  An obsolete cached binding can retain a replaced container until the next
  admission or chunk unload. Reclamation still depends on Java arena/GC lifetimes
  during transition; sustained memory behavior remains unverified.
  Immutable bindings publish through a volatile cache; concurrent cold admission
  may create duplicate temporary leases. No cache monitor encloses escape or
  compatibility callbacks.
- Counters remain section-local even when block storage is shared. Stale-empty
  counts mask height/block reads, while emitter enumeration reads raw palettes.
  Preserve existing section mutation locks and caller exclusion: these bindings
  do not make a concurrent chunk/neighborhood into an atomic snapshot.
- Keep custom chunk/section/container callbacks, debug-world handling, public
  wrapper overrides, unready biome stages and mutable-light escape on their
  existing compatibility routes. Height/emitter admission must not force biome
  compatibility materialization.

## Verification

Use JDK 25 and one consistently configured release library:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib world::
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.world.level.levelgen.NativeDhHeightmapsTest --tests net.minecraft.world.level.levelgen.NativeDhLightingTest --tests net.minecraft.world.level.levelgen.NativeDhColumnsTest
python3 DevUtils/RunWiki.py check
```

Historical block-input CPU checkpoint: all 241 Rust world tests pass, including primary-owner
release/growth, independent aliased counters and retained biome growth controls.
Release97737bac builds successfully; all27 focused Java integration checks
pass (seven height, seven lighting, thirteen column). FullJava reports1,886
tests/two skips/zero failures or errors across337 suites (1,884 executed).
All37 column/material controls pass (36 native plus bootstrap regression),
six full-task workers map the exact image, and39,462 Current/38,176 Frozen
source/native/protected-prompt guards match. The complete task also checks that
escape callbacks execute outside a new chunk monitor. These are CPU checks;
fullRust/hardware/runtime/performance/long-memory acceptance remains pending. Receipts: `build/native-chunk-inputs/`.
Existing actual untouched Frozen height, lighting/work-count and column corpora
remain the compatibility baseline; the saved biome patterns are fixture inputs,
not broad gameplay parity. The new JNI controls exercise ordinary consumers,
reuse, raw stale-empty emitters, escaped-slot replacement and immutable outputs.

The newer shared biome-input checkpoint is release70cbb053, recorded under
`build/native-biome-inputs/`. Its250 Rust world checks,29 focused Java controls
and fullJava1,905 reported/two skips/zero failures or errors pass. Six full-task
workers load that image and source/native/Frozen/protected-prompt guards match.
Both sky/fog and columns retain the exact same native biome collection; unchanged
publication reuses the color window while primary mutation/growth still
invalidates it. See the [live-biome guide](../biome/RUST-LIVE-BIOMES.md) for
admission boundaries and scoped verification. Obsolete Java binding caches can
retain replaced owners until readmission, unload or collection; sustained memory
verification is required before accepting this image.

This follows the retained chunk/neighborhood preparation observed in the earlier
Current travel profile; it is not an isolated speedup claim. Measure initial
binding cost, admission loops, allocation, GC/native retention and sustained
streaming against Frozen after hardware restoration. Additional strong owner
leases and per-chunk compatibility references need long-session memory checks.
Java orchestration, general world simulation and the executable remain migration
work. Historical renderer timings do not verify this release.

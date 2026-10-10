# Rust DH chunk change hashes

Admitted stock DH chunk wrappers use Rust to
produce the block/biome change hash used by pre-update checks and update
publication. The producer directly reads [retained chunk inputs](RUST-DH-CHUNK-INPUTS.md),
cached [height fields](RUST-DH-HEIGHTMAPS.md) and native emitter positions.
Java does not create sampled block/biome arrays or repeat the hash loops.
This is CPU world policy; it does not change GPU ownership or presentation.

Implementation: [producer](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/dh_hash),
[compatibility admission](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhChunkHash.java)
and [ordinary consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/common/wrappers/chunk/ChunkWrapper.java).

## Ownership and constraints

- Rust owns UTF-16 identity hashing, wrapped arithmetic, sampled x/z/y order,
  surface sampling and the final emitter fold. A registry-bound native table
  receives immutable serialized identities once per wrapper binding. Java keeps
  compatibility references and validates public wrapper-cache replacements.
  Hash readers share a native read lock; cold identity registration takes a
  write lock. No chunk, world or level is retained by this table.
- Section readers use the existing primary-owner mutation locks. Per-section
  affine contributions restore the original global sampling order without a
  dense voxel copy. Current section counters mask block reads even when raw
  palettes contain blocks; biome reads remain independent of those counters.
- Preserve Frozen's cached height and first-enumeration emitter values after
  subsequent palette edits. When solid and light-blocking surface heights differ,
  both block reads still use the light-blocking height. Preserve this original
  rule rather than interpreting it as a baseline bug.
- Sampling completes before the original emitter callback boundary. Rust drops
  its locks before Java enumerates or exposes positions. The normal path retains
  native emitters without allocating Java positions. A previously exposed
  mutable list supplies bounded CPU positions in its current order, including
  duplicates and edits. Custom position subclasses and unsupported coordinates
  decline to the original callbacks and exceptions.
- Canonical chunks, biome domains, stock level bindings and supported height
  accessors admit. Custom wrappers/worlds, unready biomes, compatibility escape
  and public wrapper overrides retain the original producer. This does not
  make a concurrently changing chunk an atomic world snapshot; preserve caller
  exclusion. Registry-domain warming can populate additional stock wrapper
  identities before their first sampled encounter; custom bindings decline.
- Native tables are bounded by admitted registry sizes. Per-thread text/position
  scratch grows geometrically within fixed admission bounds. These leases and
  Java cache reclamation still require sustained memory verification.

## Verification

Use JDK25 and the same release settings for the build and Java tests:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib world::
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release buildRustNative
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.world.level.levelgen.NativeDhChunkHashTest
python3 DevUtils/RunWiki.py check
```

The supplemental recorder
[`GenerateFrozenDhChunkHashOracle.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/GenerateFrozenDhChunkHashOracle.java)
executes untouched Frozen's actual hash producer with15 pinned classes. Its96
cases use16 saved block chunks, two declared vanilla biome patterns and three
phases: initial cached fields, raw block/biome edits, then stale-empty counters
with reordered/duplicated emitter positions and an exclusive-top position.
Saved biome data and real gameplay are not represented. The expected hash
algorithm is not copied into the recorder. Its known missing external-mod tag
messages are fixture setup evidence, not a clean runtime log-health result.

Release `628cfd97` passes all253 Rust world checks,132 Rust bridge checks,
155 Rust chunk checks and two terrain-expansion checks. The31 focused Java
controls pass; all96 Frozen hashes match through native retained inputs and
the ordinary wrapper consumer. Public wrapper replacement, mutable emitter
callbacks, invalid coordinates, escaped biomes and exception prefixes retain
compatibility behavior. An initial wrapper-replacement fixture wrote into a
stale-empty section and correctly observed air; the corrected fixture edits
through the counter-owning setter, with the original failure retained.

Full Java reports1,755 ordinary tests (two skipped) and154 parity tests, zero
failures/errors across342 suites. Six final workers map the exact release; all
39,481 Current and38,176 Frozen source pins, native image and protected prompt
match, with Frozen HEAD unchanged. Receipts: `build/native-dh-chunk-hash/`.
Older renderer timings do not verify this image. Hardware/runtime/FPS and
long-session memory remain pending driver restoration. Measure cold identity
registration, repeated admission, concurrent worker throughput, allocation and
real streaming against Frozen before claiming a performance gain.

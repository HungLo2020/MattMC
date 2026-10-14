# Native biome decoration

`ChunkGenerator.applyBiomeDecoration` keeps Java's step loop, structure
placement and feature seeds. For each placed feature, it first offers the
feature to [`NativeDecoration`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeDecoration.java).
A supported feature then runs entirely in
[`levelgen/decoration/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/decoration)
in one call: `setFeatureSeed`, every placement modifier, the configured
feature's draws, region reads and section writes. Every other feature keeps
Java's path, in the same order and with the same seeds.

## What runs in Rust

| Piece | Supported now | Rust file |
| --- | --- | --- |
| Random | `WorldgenRandom` over Xoroshiro, through `BitRandomSource`'s derivations | `random.rs` |
| Modifiers | `count` (constant, uniform), `in_square`, `height_range` (uniform, trapezoid, biased to bottom), `biome`, `rarity_filter` | `placement.rs` |
| Features | `minecraft:ore` (`OreFeature`, exact class and `Feature.ORE`) | `ore.rs` |
| Rules | tag, block, block-state and always-true tests; random block and random block-state tests | `program.rs` |
| Region | 3×3 proto chunks: live block/counter/biome owners, WG heightmaps | `region.rs` |

`ScatteredOreFeature`, trees, vegetation, nested selectors and structures are
Java. Custom feature classes, custom rule tests and other modifiers make the
whole placed feature Java.

## Contracts to preserve

- **Draw order.** Java's placement streams are lazy: each modifier's
  `getPositions` runs as its `flatMap` reaches it, so draws interleave depth
  first with the feature's own draws. `placement.rs` walks modifiers
  recursively in that order. `count` draws its count before any element;
  `in_square` draws X then Z.
- **JVM trigonometry.** `OreFeature` calls `Math.sin/cos`. HotSpot's x86_64
  stubs differ from the platform libm in the last bit for 66,193 of the
  16,777,216 possible ore angles. [`core/java_trig.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/java_trig.rs)
  transcribes the stubs; its test checks every ore angle against a HotSpot
  25.0.1 hash. Rust builds `Mth.SIN` from that port. At startup Java compares
  it with its own table; any difference disables native decoration.
- **Region semantics.** Reads outside the 3×3 neighbourhood raise the same
  `WorldGenRegion.getChunk` crash. Far writes are refused and logged by
  Java's own `ensureCanWrite`. Ore writes use `LevelChunkSection.setBlockState(…, false)`
  semantics: section and native counters only, no heightmap or block-entity work.
- **Views.** When a native write grows a palette, Java reloads that section's
  CPU view after the call.
- **No partial fallback.** A program declines (`JAVA_ROUTE`) only before its
  first draw. Program creation requires counter policies for every state a
  target can replace or write, so section writes cannot decline mid-placement.
  A binding failure after writes throws; it never reruns the feature in Java.

## Gates

A decoration call binds a region only when the level is a plain
`WorldGenRegion`, the center is a non-upgrading plain `ProtoChunk`, all nine
chunks are plain `ProtoChunk`s with the region's height, every section has
canonical native block, counter and biome owners sharing one biome binding,
and the native block registry is ready. Before each placement, Java re-checks
those owners and copies the current `OCEAN_FLOOR_WG` heightmaps; Java features
run in between and may change them.

A chunk restored from disk at CARVERS reloads only its final heightmaps, so its
WG heightmaps are unprimed. `ChunkAccess.getHeight` primes one at its first
read and later writes never update it. Rust does the same: it primes the map
from current blocks at the first native read (`region.rs`, using the shared
heightmap masks) and Java installs it after the call. This needs a registry
without custom `BlockState` subclasses; otherwise an unprimed map keeps that
placement in Java.

Programs are cached per generator and placed feature, keyed by the tag
generation, biome binding and generation context (`minGenY`, `genDepth`).
A height range that is empty in the context stays in Java, which warns.
`-Dmattmc.worldgen.javaDecoration=true` or `DEBUG_FEATURE_COUNT` keeps every
feature in Java. `NativeDecoration.statistics()` reports bound regions,
native/Java placements and why bound placements fell back.

## Verify

```sh
CARGO_TARGET_DIR=build/rust/target cargo test --locked --manifest-path src/main/rust/Cargo.toml \
  --profile suite --lib -- world::level::levelgen::decoration core::java_trig
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> \
  --sides current --compare-to build/full-generation/<pre-change>/current-0
```

The [FULL-chunk driver](../RUST-FULL-GENERATION.md) is the acceptance check.
Compare against pre-change Current in every dimension, and against Frozen
where Frozen is reproducible. Check `native_decoration` in `regions.json` to
confirm the native route ran. See [recorded results](RUST-DECORATION-RESULTS.md).

For workloads that are not repeatable even on Java's route (parallel
generation, chunks restored from disk), run with
`--jvm-arg=-Dmattmc.worldgen.verifyDecoration=true`. Every native placement
is then undone, replayed on Java's route with the same seeded random, and
compared state by state; `verified`/`mismatched` appear in the statistics and
mismatches are logged. The mode is slow and leaves palette order unlike a
normal run, so never use its worlds as parity output.

## Adding a feature family

1. Read the Java feature and every helper it calls; list each draw, read and write.
2. Add the configured feature to `program.rs` and its encoder in
   `NativeDecoration.encode`, declining exact classes it cannot reproduce.
3. Implement it beside `ore.rs`. Writes through `WorldGenRegion.setBlock`
   need heightmap, block-entity, post-processing and POI behaviour, which
   `region.rs` does not provide yet.
4. Run the FULL-chunk driver against pre-change Current and Frozen, then time it.

# Native biome decoration

`ChunkGenerator.applyBiomeDecoration` hands its whole step loop to
[`NativeDecoration.decorate`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeDecoration.java).
[`decoration/sequence.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/decoration/sequence.rs)
then runs every generation step in one call per chunk:

1. the step's structures, through a Java upcall (`Sequence.structures`);
2. the union of the present biomes' placed features, in index order, each
   after `setFeatureSeed(decoration, index, step)`;
3. each feature's compiled program in
   [`levelgen/decoration/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/decoration),
   or Java's `placeWithBiomeCheck` through an upcall (`Sequence.feature`).

Java still gathers the present biome set (from section palettes) and owns
structure placement and every feature without a program. When the loop
declines before running, Java's own loop runs, offering each placed feature
to `NativeDecoration.place` one at a time.

## What runs in Rust

| Piece | Supported now | Rust file |
| --- | --- | --- |
| Loop | steps, structure slots, biome union, feature seeds, Java fallbacks | `sequence.rs` |
| Random | `WorldgenRandom` over Xoroshiro, through `BitRandomSource`'s derivations | `random.rs` |
| Modifiers | `count`, `in_square`, `height_range` (uniform, trapezoid, biased, very biased), `biome`, `rarity_filter`, `block_predicate_filter`, `heightmap`, `surface_water_depth_filter`, `count_on_every_layer`, `environment_scan`, `random_offset`, `noise_threshold_count`, `noise_based_count`, `surface_relative_threshold_filter` | `placement.rs` |
| Int providers | constant, uniform, biased-to-bottom, weighted list, clamped, clamped normal | `program.rs`, `simple.rs` |
| Float providers | constant, uniform, clamped normal, trapezoid | `program.rs`, `simple.rs` |
| State providers | simple, weighted, rule-based, randomized int | `tree/mod.rs`, `vegetation.rs` |
| Predicates | true/all/any/not, matching blocks/tags/fluids, solid, replaceable, inside world bounds, has sturdy face, would-survive (saplings); anything else asks Java | `program.rs`, `placement.rs` |
| Features | ore, replace blobs, glowstone, springs, the three selectors, trees (every vanilla trunk, foliage, root placer and decorator, pale moss), random patch and flower patches, simple block, block column, seagrass, sea pickle, kelp, disk, vines, forest rock, underwater magma, multiface growth (glow lichen, sculk vein), geode, monster room, delta, basalt columns, coral claw/tree/mushroom, lake, sculk patch, pointed and large dripstone, dripstone cluster, blue ice, ice spike, iceberg, `freeze_top_layer`, bamboo, huge mushrooms, huge fungi, weeping and twisting vines, basalt pillar, scattered ore, chorus plant, End island, vegetation patches (moss, pale moss, clay pools), desert well | `ore.rs`, `simple.rs`, `tree/`, `vegetation.rs`, `geode.rs`, `rooms.rs`, `nether.rs`, `coral.rs`, `lakes.rs`, `sculk.rs`, `dripstone.rs`, `ice.rs`, `freeze.rs`, `plants.rs`, `end.rs`, `patch.rs`, `rooms.rs` |
| Templates | `StructureTemplate.placeInWorld` for structure pieces ([guide](../structure/RUST-STRUCTURE-TEMPLATES.md)) | `template.rs` |
| Region | 3×3 proto chunks: live block/counter/biome owners, WG and final heightmaps, sky-light sources | `region.rs` |
| `setBlock` | `WorldGenRegion.setBlock` → `ProtoChunk.setBlockState` | `write.rs`, `facts.rs` |

Any other configured feature inside a supported selector, patch or placement
runs as a **Java leaf**: Rust hands Java the random's state through an upcall,
Java places it and returns the state. Top-level programs use Java leaves only
under `count_on_every_layer` or `environment_scan`, where the eager scans pay
for the upcall. **Java steps** run small pieces that need block entities or
registries (monster-room loot and spawner mob, desert-well suspicious sand,
pale moss patches, a water lake's freezing pass) the same way.

Still Java: End spikes (crystal entities), the End gateway and platform,
fossils (their two template placements run natively), planted huge fungi, and
features without an encoder. Structure starts and legacy (non-template)
structure pieces such as mineshafts also stay Java; at about 3% of the
overworld feature step, mineshafts are the largest of those. They run through the step
loop's upcall in Java's order. `freeze_top_layer` reads block light from Java
(a pure upcall); its biome temperature is a pure function of position, so
Java's per-thread temperature cache never changes a result.

## Contracts to preserve

- **Draw order.** Java's placement streams are lazy: each modifier's
  `getPositions` runs as its `flatMap` reaches it, so draws interleave depth
  first with the feature's own draws. `placement.rs` walks modifiers
  recursively in that order. `count_on_every_layer` is eager and re-samples
  its count at every loop check; Rust memoises each column's ground layers
  because nothing writes while positions are collected.
- **JVM arithmetic.** HotSpot's x86_64 `sin`/`cos` stubs are transcribed in
  [`core/java_trig.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/java_trig.rs)
  and checked over every ore and fancy-branch angle. Rust builds `Mth.SIN`
  from that port; Java compares it at startup and any difference disables
  native decoration. Float expressions keep Java's `float`/`double` widening.
- **Hash-set order.** Trees record logs and leaves, and vegetation patches
  their ground blocks, in `java.util.HashSet` order;
  [`java_set.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/decoration/java_set.rs)
  emulates it, including `HashMap`'s tree bins: a bucket of nine keys in a
  table of 64 or more becomes a red-black tree whose new keys follow their
  tree parent and whose root moves to the front. That order outlives a later
  split, and a cave moss patch reaches it.
- **Survival rules.** `canSurvive` checks compile in `NativeDecorationEncoder.survival`
  to predicates over state sets: always, `mayPlaceOn` for vegetation whose
  rule reads only the state below, seagrass, tall seagrass, sea pickles,
  growing plants (kelp), corals and double plants. Shape-dependent sets
  (sturdy faces, support shapes) are built without a world; a dynamic-shape
  state found at run time asks Java's exact test instead. Any other block
  asks Java at the position.
- **Gaussian cache.** `WorldgenRandom.setSeed` never resets its
  `MarsagliaPolarGaussian`, so a cached gaussian carries from one placed feature
  to the next. Rust keeps it with the random and hands it across every upcall
  and between programs. `Math.log` in the draw comes from Java (a pure upcall):
  HotSpot's intrinsic need not match libm bit for bit. So do `Math.pow` with
  non-integer exponents and `sin`/`cos` off the transcribed stub's main path;
  HotSpot computes `Math.pow(x, 2.0)` as `x * x` (checked on the bundled JDK).
- **Region semantics.** Rust binds the 3×3 chunks it may write. Reads beyond
  them (sculk cursors wander that far) go to `WorldGenRegion` through pure
  upcalls (block state, height, noise biome), so they see and fail exactly as
  Java's reads do. Far writes are refused by Java's own `ensureCanWrite`. Ore
  writes use `LevelChunkSection.setBlockState(…, false)`.
- **`setBlock`.** `write.rs` follows `WorldGenRegion.setBlock` exactly: far
  writes refused, void air outside the world, section and counter writes,
  light notifications for lit chunks with `ChunkSkyLightSources.update` in
  place, final heightmaps primed and updated. POI, block-entity stubs,
  post-processing marks, light-engine calls and ticks are recorded in call
  order and replayed by Java after the call or before any upcall.
- **Upcalls.** A sync upcall first gives Java everything Rust holds (views,
  heightmaps, sky sources, primed maps, events), runs the Java step, then
  refreshes the binding Rust reads. Operations: `updateShapeAtEdge`, beehive
  checks and bees, Java leaves, Java predicates, a step's structures and a
  Java-route feature. Pure upcalls (no sync): `Math` functions, block light,
  and reads beyond the bound 3×3.
- **No partial fallback.** A program declines (`JAVA_ROUTE`) only before its
  first draw; inside the loop that feature is placed by Java instead. Program
  creation requires counter policies for every state it can write. A binding
  failure after writes throws the same "Feature placement" crash Java raises.
  If the binding no longer holds after a Java feature, the loop stops and
  Java's loop continues from the next feature.

## Gates and switches

A region binds only when the level is a plain `WorldGenRegion`, all nine
chunks are plain `ProtoChunk`s with the region's height and canonical native
owners, and the native block registry is ready. The native loop also needs
the write path (every chunk at CARVERS or later with the final heightmap set,
and each lit chunk's light engine and sky sources) and a plan for the region
context. Programs and the plan are cached per generator, keyed by the tag
generation, biome binding and `minGenY`/`genDepth`.

Unprimed WG heightmaps (chunks restored from disk at CARVERS) are primed by
Rust at the first read, exactly as `ChunkAccess.getHeight` primes them,
unless a custom `BlockState` subclass is registered.

| Property | Effect |
| --- | --- |
| `mattmc.worldgen.javaDecoration` | every feature on Java's path (the pre-change code) |
| `mattmc.worldgen.javaSequence` | Java's step loop, one native call per placed feature |
| `mattmc.worldgen.decorationJavaKinds=10,11` | keep these outermost program kinds in Java (bisecting) |
| `mattmc.worldgen.verifyDecoration` | shadow-verify every native placement (Java's loop) |
| `mattmc.worldgen.decorationProfile` | per-feature and per-structure time by route (Java's loop) |
| `mattmc.worldgen.decorationDebug` | log encoder declines, rejected programs and loop declines |
| `mattmc.worldgen.decorationTest` | verification: `fail:N` fails the Nth native write, `javafail:N` the Nth Java `setBlock` |

`NativeDecoration.statistics()` (the audit's `native_decoration`) reports
bound regions, native/Java placements, sequenced and declined loops, native
placements by kind and replayed events by kind.

## Verify

```sh
CARGO_TARGET_DIR=build/rust/target cargo test --locked --manifest-path src/main/rust/Cargo.toml \
  --profile suite --lib -j 8 -- world::level::levelgen::decoration core::java_trig
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> \
  --rounds 2 --sides current --compare-to build/full-generation/<pre-change>/reference0-0
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --timing \
  --radius 4 --rounds 2 --sides current --dimensions minecraft:overworld,minecraft:primordial_caves,minecraft:the_nether \
  --jvm-arg=-Dmattmc.worldgen.verifyDecoration=true
```

The [FULL-chunk driver](../RUST-FULL-GENERATION.md) is the acceptance check.
Compare against pre-change Current in every dimension and against Frozen
where Frozen is reproducible. Check `native_decoration` in `regions.json` to
confirm the native route ran.

Shadow verification undoes each native placement, replays it on Java's route
with the same seeded random and compares blocks, heightmaps, sky sources,
ticks and post-processing. It needs Java's loop, so it never exercises
`sequence.rs`; FULL parity does. Its worlds keep extra post-processing marks
and block-entity stubs from the undone placement: never use them as parity
output. Profile with `decorationProfile` to choose the next feature by cost.
See [recorded results](RUST-DECORATION-RESULTS.md).

## Adding a feature family

1. Read the Java feature and every helper it calls; list each draw, read and write.
2. Add it to `vegetation.rs` (or its own file) with a decoder that reads
   states it writes through `Reader::write_state` and sets through `Reader::set`,
   and its encoder in `NativeDecorationEncoder` matching the exact feature
   instance and configuration class. Compile `canSurvive` with `survival`.
3. Read through `level_block_state` (void air outside the world) and write
   through `set_block`; predicates through `Placement::test`.
4. Run FULL parity, shadow verification and a profile, then time it.

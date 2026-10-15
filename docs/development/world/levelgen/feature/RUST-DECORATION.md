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

Rust also gathers the present biome set: the distinct values stored in the
bound 3×3's native biome owners, mapped through a per-plan table to plan biome
IDs (values outside `possibleBiomes()` drop out). When a value is not in the
plan, Java gathers the set from section palettes as before
(`javaBiomeSets=` counts those loops). Java owns the structure-step dispatch
(each start's pieces then place natively, see
[native structure pieces](../structure/RUST-STRUCTURE-PIECES.md)) and every
feature without a program. When the loop declines before running, Java's own
loop runs, offering each placed feature to `NativeDecoration.place` one at a
time.

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
features without an encoder. Java retains structure-step dispatch in the original
order and the piece objects, but supported starts and legacy pieces, including
mineshafts, now use [native structure planning and placement](../structure/RUST-STRUCTURE-PIECES.md).
Unsupported paths retain Java fallbacks. The earlier mineshaft estimate, roughly 3% of the Overworld feature step,
describes the preceding Java route, not its current Java-only cost. This boundary
was source-reviewed at `e1c35961991261469c934bf07f724b99a418ba55` on 2026-10-15;
no generation or performance test was rerun. [Start dispatch][current-start-dispatch] ·
[Piece dispatch and fallback][current-piece-dispatch]

`freeze_top_layer` reads block light and biome temperature from Java. Java keeps
a **per-Biome, per-thread cache keyed only by packed position**, although a cache
miss calculates temperature using the supplied **sea level**, so the cache
history is part of the result. Rust therefore asks Java's own
`Biome.warmEnoughToRain(pos, level.getSeaLevel())` (`OP_WARM`) on the calling
thread, at the points `shouldFreeze` and `shouldSnow` call it (a synchronous callback that updates the cache, never memoized or reordered): the position
below for ice first, then the surface position for snow, only when the biome has
precipitation. Java's cache sees the same sequence as Java's feature would make.
This is retained Java cache ownership, not a native temperature. With
`-Dmattmc.worldgen.verifyDecoration=true` each native freeze placement checks
that the caches (entries and FIFO order) and call counts equal Java's replay
from the same history (`freezeHistoryVerified=`/`freezeHistoryMismatched=`);
`-Dmattmc.worldgen.verifyFreezeSeed=true` additionally seeds conflicting
sea-level entries and fills each cache to its eviction boundary (not a parity
run). [Java cache scope][temperature-cache] · [Calculation, lookup and sea-level callers][temperature-lookup] ·
[Native freeze path][native-temperature]

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

Binding is per-chunk work over every section, so two shortcuts keep sparse
chunks (the End's islands) cheap:

- Each `ProtoChunk` keeps its last capture (section, block, counter and biome
  owners and their words) while its section array is unchanged; the next
  region reuses it, and `refresh` re-checks every owner before native work.
  A failed check drops the capture so the next bind recaptures.
- A structure step calls back only when the chunk references one of the
  step's structures: `startsForStructure` finds nothing otherwise, and each
  feature reseeds the random the step would have set.

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
placements by kind, replayed events by kind, native templates and pieces with
Java's remaining structure work (`javaTemplates=`, `javaPieces=`), carver
counters and `javaBiomeSets=`, upcalls by operation (`upcalls={op=count}`)
and one-time setup times by phase (`setupMs=`: facts, piece tables, plan and
program encoding per biome source, template compiles).

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

[current-start-dispatch]: https://github.com/HungLo2020/MattMC/blob/e1c35961991261469c934bf07f724b99a418ba55/src/main/java/net/minecraft/world/level/levelgen/structure/Structure.java#L97-L115
[current-piece-dispatch]: https://github.com/HungLo2020/MattMC/blob/e1c35961991261469c934bf07f724b99a418ba55/src/main/java/net/minecraft/world/level/levelgen/structure/StructureStart.java#L89-L108
[temperature-cache]: https://github.com/HungLo2020/MattMC/blob/e1c35961991261469c934bf07f724b99a418ba55/src/main/java/net/minecraft/world/level/biome/Biome.java#L64-L72
[temperature-lookup]: https://github.com/HungLo2020/MattMC/blob/e1c35961991261469c934bf07f724b99a418ba55/src/main/java/net/minecraft/world/level/biome/Biome.java#L113-L185
[native-temperature]: https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/decoration/freeze.rs

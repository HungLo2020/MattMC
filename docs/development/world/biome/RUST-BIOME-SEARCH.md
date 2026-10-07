# Rust biome searches

A plain `MultiNoiseBiomeSource` runs its biome searches in
[`biome/search/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/biome/search).
The searches are `findBiomeHorizontal` (concentric-ring structure placement,
such as strongholds) and `findClosestBiome3d` (`/locate biome`). Before, every
sampled position walked the sampler's Java density functions and then made a
separate native climate-tree call. Now one call per search does the sampling,
the climate searches and the visiting order. The Java bridge is
[`NativeBiomeSearch`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeBiomeSearch.java).

## What runs where

- Rust samples each position with the `RandomState` sampler's six functions,
  compiled once per sampler as a point program. Those functions have no cache
  markers, so it evaluates them directly. It quantizes climate as
  `Climate.target` does: a float cast, then ×10000.
- Rust searches the climate tree in visiting order from the thread's previous
  leaf. A single-leaf list returns its leaf without searching, as Java does.
  Java installs the new previous leaf afterwards.
- `findBiomeHorizontal` returns every accepted sample in visiting order. Java
  then replays the original's random choice among them
  (`pair == null || random.nextInt(r + 1) == 0`), so the result and the
  `RandomSource` end in the same state.
- `findClosestBiome3d` returns the first accepted sample. The spiral is
  `BlockPos.spiralAround(ZERO, radius / step, EAST, SOUTH)` ported exactly. Java
  still computes the accepted set and the Ys (`Mth.outFromOrigin`).

## Constraints when changing this code

- Searches test a precomputed acceptance flag per climate leaf, not a Java
  predicate per sample.
  - Ring placement now passes its `HolderSet` to a new `findBiomeHorizontal`
    overload. A plain source evaluates that set's `contains` once per leaf.
  - `findClosestBiome3d` already evaluated its predicate once per possible
    biome. That set and the Ys now go to a protected method that sources may
    override.

  Arbitrary `Predicate` searches and the perimeter-only `findBiomeHorizontal`
  variant keep the Java loop.
- Only an exact `MultiNoiseBiomeSource` with an exact `Climate.ParameterList`
  is eligible, and only when every sampler function compiles.
- The debug flags `DEBUG_ONLY_GENERATE_HALF_THE_WORLD` and
  `debugGenerateSquareTerrainWithoutNoise` keep Java.
- If a sample fails or a climate search finds no leaf, Rust writes nothing
  back and Java runs the whole search itself, reproducing the original
  outcome. Invalid ABI input throws instead.
- Sampling is pure, so Rust may sample a column's remaining Ys after the match
  that ends a 3D search. Only searched samples move the previous leaf.
- `-Dmattmc.worldgen.javaBiomeSearch=true` keeps Java searching.
  `NativeBiomeSearch.setEnabled` toggles this in tests.

## Verify a change

```sh
python3 DevUtils/tests/worldgen/VerifyRustBiomeSearch.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustBiomeSearch.py --forks 3
```

`NativeBiomeSearchTest` runs both routes on one thread from the same previous
leaf and `RandomSource` seed. It compares the result, the next random value
and the thread's previous leaf afterwards. Worlds:

- the overworld and nether presets over three seeds
- large biomes
- custom single-leaf and three-point lists

Searches use random centres, radii, steps, vertical steps and accepted sets,
including empty sets. The `HolderSet` overload is checked against the original
predicate search. All ring positions of two overworld seeds are compared
through `ChunkGeneratorStructureState`.

The ring placement runs on the background executor. Each thread's previous
leaf, which can break exact ties, therefore depends on scheduling, on both
routes alike. The single-threaded comparisons fix that state.

The benchmark (`NativeBiomeSearchVerification`) has two cases:

- `rings`: an overworld's concentric-ring positions through
  `ChunkGeneratorStructureState`, wall time on three CPUs
- `locate`: twelve `/locate biome`-style spiral searches for rare biomes

Three independent JVM pairs alternate the route order on the same CPUs. Each
case must save at least 5% in every pair and at the upper 95% bootstrap bound.
Results are written to `build/biome-search-migration/acceptance/`. Inspect
`results.json` → `performance` → each case's `passes` value: the driver
records a failed performance gate without failing its process.

## Status

The implementation author recorded release acceptance on 2026-10-06: Ryzen 5
5600G, Linux x86_64, OpenJDK 25, with `passes: true` for both cases.

Parity: three Java tests passed with identical outcomes for 216 horizontal
searches, 108 closest searches and 256 ring positions. Eight Rust biome tests
also passed.

Mutation checks: eight semantic mutations were all caught.
- Spiral direction order, horizontal axes and closest-search Y quarts.
- Previous-leaf carrying, the single-leaf previous leaf and Java's install of
  the previous leaf.
- The reservoir bound.
- Float quantization. This one survived the parity corpus, since a double
  product rarely changes the integer, and is now caught by a unit test pinned
  to a value where it does.

Median time per round over three JVM pairs. The last column is the
conservative saving at the upper 95% bound of the time ratio:

| Case | Java | Rust including boundary | Time saved | Conservative saving |
|---|---:|---:|---:|---:|
| `rings` | 1952 ms | 902 ms | 53.8% | 52.9% |
| `locate` | 423.9 ms | 46.7 ms | 89.0% | 88.5% |

Every pair passed. The smallest individual-pair savings were 53.1% (`rings`)
and 88.6% (`locate`). `rings` is wall time across the background executor's
threads. JIT compilation on those threads overlapped 51 of its 180 measured
samples, which are included; `locate` had none. These are warmed timings on
this machine, not whole-server claims.

# Rust biome fill

The BIOMES stage (`NoiseBasedChunkGenerator.doCreateBiomes`) used to sample the
chunk's climate in Java at every quart (six density functions through the
chunk's caches), call the [native climate search](RUST-CLIMATE.md) once per
section, and write 64 holders into each section's biome container. For plain
multi-noise chunks the whole stage now runs in one Rust call.

[`NativeBiomeFill`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeBiomeFill.java)
compiles, once per `RandomState`, the router's temperature, vegetation,
continents, erosion, depth and ridges as a point program for the
[noise router](../levelgen/RUST-NOISE-ROUTER.md), from the router rebuilt the
way chunk wrapping rebuilds it (as [chunk noise](../levelgen/RUST-CHUNK-NOISE.md)
does). FlatCache markers keep the chunk's semantics: inside its quart grid the
input at the quart corner with Y 0, outside it the input at the point. Per chunk,
[`biome_fill/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/biome_fill)
then:

1. evaluates the six functions over each quart column, all quart Ys as lanes;
2. quantizes them as `Climate.target` does (`(long)((float)value * 10000F)`);
3. searches the climate tree section by section in Java's (x, y, z) order,
   starting from the calling thread's previous leaf;
4. replays each section's `recreate()` and 64 `getAndSetUnchecked` writes under
   the biome strategy (single value, 1–3 bit linear, global).

Java installs each container (`LevelChunkSection.installGeneratedBiomes`) and
sets the thread's previous leaf to the last one Rust found.

## Gate

`NativeBiomeFill.fill` returns false, keeping Java's fill, unless:

- the chunk is a plain `ProtoChunk` and its noise chunk a plain `NoiseChunk`
  with the empty blender, whose quart grid starts at the chunk;
- the resolver is a plain `MultiNoiseBiomeSource` (so no blending resolver or
  below-zero retrogen) with an exact `Climate.ParameterList`;
- every section's biome container is a plain `PalettedContainer` with the
  biome strategy, one ID map and a global palette of 4–32 bits;
- every leaf value and container seed is its ID map's own holder (palettes
  compare holders by identity, Rust compares registry IDs).

A search that finds no leaf makes Rust return without writing anything; Java's
fill then runs from the same previous leaf and fails as it always did.
`-Dmattmc.worldgen.javaBiomeFill=true` keeps Java's fill for comparisons.

## Preserve these contracts

- The climate program must compute what `NoiseChunk.cachedClimateSampler`
  computes: the normalized router, markers transparent except FlatCache.
- Search order and the previous leaf are observable: equal distances can depend
  on it. Sections run bottom to top, quarts in (x, y, z) order; the thread's
  previous leaf after the fill is the last leaf found. A one-node tree skips the
  search, as Java's single-leaf shortcut does (sampling is pure).
- Container replay keeps `recreate()`'s first value in the palette and rebuilds
  palettes by first occurrence in storage order on resize, as `copyFrom` does.

## Verify and measure

```sh
python3 DevUtils/tests/worldgen/VerifyRustBiomeFill.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustBiomeFill.py --forks 3 --cpu 5 --background-cpus 0,1
```

`NativeBiomeFillTest` runs the production `doCreateBiomes` with both fills for
every vanilla noise setting with the overworld and nether presets (two seeds,
five positions, run as one sequence per route so the previous leaf carries from
chunk to chunk). It compares every section's biome container (network bytes,
saved form, holders) and the previous leaf after each chunk. It also covers a
one-leaf list, a list fine enough to need the global palette, a router whose
temperature reads a Y-dependent FlatCache (chunk table semantics are observable
only then), and the gates.
Rust tests in `biome_fill/tests.rs` pin the container replay and quantization
(including a value whose float rounding crosses an integer). Mutations that
quantize in double, swap the storage index or search order, drop the previous
leaf between sections or after a one-leaf fill, ignore `recreate()`'s first
value, or treat FlatCache as transparent each fail a test.

## Measurements

The implementation author recorded release runs on 2026-10-05 on an i7-10750H
laptop (CPU 5 measured, CPUs 0/1 for JVM workers). The driver ran three
alternating JVM pairs per case, at least 15 s of warmup and 30 samples per JVM,
each sample creating the noise chunk and filling biomes for eight fresh chunks
from a cleared previous leaf; route checksums matched in every JVM, no measured
sample saw JIT activity, and the native JVMs filled their chunks natively.

| Case | Java fill | Rust fill | Paired ratios | 95% ratio interval |
|---|---:|---:|---|---:|
| Overworld | 1.54 ms | 0.81 ms | 0.53, 0.52, 0.57 | 0.52–0.58 |
| Amplified | 1.53 ms | 0.79 ms | 0.52, 0.54, 0.51 | 0.51–0.54 |
| Nether | 0.20 ms | 0.05 ms | 0.22, 0.25, 0.22 | 0.22–0.25 |

Times are per chunk and include the noise chunk the stage creates (the same in
both routes), the boundary and the container installs. Not structure lookups,
later stages or whole-game measurements. Raw rounds and hashes were recorded
under `build/biome-fill-migration/` (not bundled with the wiki).

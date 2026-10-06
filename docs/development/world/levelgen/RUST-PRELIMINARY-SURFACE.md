# Rust preliminary surface level

`NoiseChunk.preliminarySurfaceLevel(x, z)` estimates a column's terrain height
from the router's `preliminarySurfaceLevel` function (in vanilla, a
`FindTopSurface` search). The aquifer requests a grid of about 144 columns
while each `NoiseChunk` is built; the surface stage, aquifer fluid sources and
debug views read single columns. Every level lands in the chunk's
`preliminarySurfaceLevelCache`.

[`NativeSurfaceLevel`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeSurfaceLevel.java)
moves that computation to Rust. Each `RandomState` compiles its unwrapped
`preliminarySurfaceLevel` once, with the [noise router](RUST-NOISE-ROUTER.md)
compiler in point mode, into a shared program in
[`router/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/router).
`NoiseChunk` asks Rust for every uncached level: a grid in batched calls,
single columns in one call each. Java still owns the cache and the callers.
A `Cleaner` releases the program when its `RandomState` is collected.

## Why one program serves every chunk

Java evaluates the chunk-wrapped function with a point context at a
quart-aligned column. In that context every chunk cache reads through:
interpolators, `CacheOnce` and cell caches evaluate their input, and
`FlatCache` and `Cache2D` return their input's value at the column, which equals
the point's value whenever that input cannot depend on Y. Rust rejects programs
where a FlatCache or Cache2D input can depend on Y. `BlendDensity` is
transparent for point contexts. The equivalence needs the empty blender (a
blender replaces `blendAlpha`/`blendOffset` with chunk data), so blended chunks,
`NoiseChunk` subclasses and unsupported functions keep the Java path.

## Evaluation

A batch puts up to 64 columns in lanes, so the coordinate noises behind each
column's Y-independent values run in the batch noise sampler. For a
`FindTopSurface` root, Rust evaluates every column's upper bound and the
density's Y-independent inputs this way, then scans each column down from its
bound one cell height at a time, as Java does, a few Ys at a time. Results go
through Java's `Mth.floor`, including its saturating cast and wrap at
`Integer.MIN_VALUE`. A program that is one constant is evaluated once.

## Preserve these contracts

- `maxPreliminarySurfaceLevel` caches the grid's missing columns in the loop's
  order and returns the same maximum; grids over 65,536 columns use the loop.
- `FindTopSurface` is accepted only as the root; its scan order, integer
  arithmetic and `> 0.0` test follow Java.
- Changes to chunk-cache semantics for point contexts, or to which markers the
  router accepts, must keep the equivalence above or leave the gate.

## Verify and measure

The driver audits every production edit since `da1109de6`, shared with the
[cell traversal](RUST-NOISE-FILL.md#cell-traversal) driver.

```sh
python3 DevUtils/tests/worldgen/VerifyRustSurfaceLevel.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustSurfaceLevel.py --forks 3 --cpu 5 --background-cpus 0,1
```

`NativeSurfaceLevelTest` builds Java and native `NoiseChunk`s for every vanilla
setting (three seeds, six positions out to the world border) and compares the
cache the constructor fills, 48 further columns up to 4,000 blocks away and a
second grid. 50 synthetic roots cover every router node and cache marker under
`FindTopSurface` and as plain roots, searches longer than one lane frame, NaN
and huge bounds. Gate tests cover Y-dependent caches, misplaced
`FindTopSurface`, unsupported functions, subclasses and the property.
`NativeNoiseFillTest` keeps the Java route fully Java. Rust tests compare the
scan with Java's loop, batches with single columns and `Mth.floor` edge cases.

Benchmarks (`SurfaceLevelVerification`) time building fresh `NoiseChunk`s and
filling them through `fillFromNoise`, Java against native levels, in separate
JVMs.

## Measurements

The implementation author recorded release runs on 2026-10-04 on an i7-10750H
laptop (CPU 5 measured, CPUs 0/1 for JVM workers).

The surface-level work alone, timed in one JVM with alternating routes: a fresh
144-column grid (the aquifer's) next to a just-built chunk took 698 µs in Java
and 288 µs natively (overworld), 950 µs and 397 µs (amplified). Nether's
constant function took 8 µs and 13 µs before the constant shortcut; its
aquifers are disabled, so production never requests that grid.

`NoiseChunk` construction plus `fillFromNoise`, same-JVM interleaved (2,000 measured
chunks per route and setting), two runs each:

| Setting | Java levels | Native levels | Change |
|---|---:|---:|---:|
| Overworld | 7.71 / 8.07 ms | 7.31 / 7.65 ms | −5.1% / −5.2% |
| Amplified | 6.98 / 7.26 ms | 6.67 / 6.94 ms | −4.4% / −4.4% |
| Nether | 1.13 / 1.08 ms | 1.12 / 1.09 ms | no change |

The driver's separate-JVM benchmark (three alternating pairs, 15 s warmup, 30
samples of eight chunks each, identical checksums) measured overworld medians
of 57.1 ms and 52.7 ms (paired ratios 1.05, 0.92, 0.86) and did not resolve the
effect: amplified and nether ratios ranged 0.97–1.23, and per-JVM variation was
up to 21%, larger than the effect. Not surface-stage, carving, feature or
whole-game measurements. Raw rounds and hashes were recorded under
`build/surface-level-migration/` (not bundled with the wiki).

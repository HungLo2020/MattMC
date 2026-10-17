# Rust chunk noise instantiation

Every `NoiseChunk` used to rebuild its `RandomState`'s whole density graph in
Java (`noiseRouter.mapAll(wrap)`): new chunk caches, interpolators and
optimizer wrappers for every node, about 1.7 ms of a 2.1 ms overworld
constructor. The native NOISE fill no longer reads that graph.

[`NativeChunkNoise`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeChunkNoise.java)
compiles, once per `RandomState`, what the fill needs from the unwrapped router:

- a slice template for [`router/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/router):
  the functions of the interpolated markers the cell cache and ore veins read,
  with FlatCache markers reading per-chunk tables;
- a point program of those FlatCaches' inputs (nested FlatCaches keep their
  chunk semantics through FLAT_POINT slots);
- the cell cache's density program, built by `NativeCellDensity`'s builder
  over interpolated markers instead of chunk interpolators;
- the ore vein roots and constants and the aquifer barrier noise.

The router is first rebuilt with chunk wrapping's normalization (holders
resolved, two-argument functions re-created, so a constant operand becomes
`MulOrAdd`) but markers kept. An eligible `NoiseChunk` (plain class, empty
blender, `BeardifierMarker`, `Beardifier.EMPTY` or a structure Beardifier
with native cells, 16-block cell rows, native surface levels) skips wrapping. Its fill instantiates the template: Rust
computes each FlatCache table at the chunk's quart corners with Y 0, as
`FlatCache` does, then the [cell traversal](RUST-NOISE-FILL.md#cell-traversal)
runs over the instance.

## Structure terrain adjustment

Chunks beside terrain-adjusting structures (villages, outposts, ancient
cities, trial chambers, trail ruins) get a structure `Beardifier`. Their Java
cell cache is `Ap2(ADD, finalDensity, beardifier)`: the final density's
`NativeCellDensity` cell, then `NativeBeardifier`'s cell, added point by point.
The template also compiles the final density's own cell program, and the fill
binds it with the Beardifier's packed geometry (copied once per fill; the
structure-owned geometry cannot change during a fill). The Rust traversal adds
the [Beardifier](RUST-BEARDIFIER.md) cell to each cell's densities, keeping
`NativeBeardifier.fillCell`'s exact-zero result for cells outside the affected
box and Java's `a + b` order. Beardifiers built through the public constructor
(live lists), subclasses and custom providers keep constructor wrapping.

The chunk still builds its Java graph, with the constructor's exact steps,
when a Java path asks (`ensureWrapped`): the Java fill loop, `NativeNoiseRouter.create`,
a Java preliminary surface level, or an aquifer source evaluated in Java (the
aquifer resolves its router lazily). The biome stage's Java climate sampler,
when it is still used (see [biome fill](../biome/RUST-BIOME-FILL.md)), wraps only
its own six functions, as before.

## Preserve these contracts

- Template roots must compute what the chunk's interpolators would: compile
  the normalized router with the same marker rules as chunk wrapping (CacheOnce
  transparent, Cache2D over Y-independent input, FlatCache as its table).
- The cell program must be the program `NativeCellDensity` builds for the
  wrapped cell cache; its inputs map to template roots in builder order.
- FlatCache tables are the input at (corner, 0, corner) for the chunk grid; a
  template slice never leaves that grid for 16-block chunks.
- Lazy wrapping must not change results: wrapped functions are pure, and a
  native chunk's caches are fresh when built.

## Verify and measure

```sh
python3 DevUtils/tests/worldgen/VerifyRustChunkNoise.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustChunkNoise.py --forks 3 --cpu 5 --background-cpus 0,1
```

`NativeChunkNoiseTest` compares every slice value of every template root, bit
for bit, with the slices the same chunk's wrapped Java interpolators fill, for
every bundled setting (three seeds, six positions). It also compares the
climate sampler a native chunk builds with a wrapped chunk's, and checks the
gates and lazy wrapping. `NativeNoiseFillTest` requires fills of every bundled setting to
instantiate natively and match the Java fill loop exactly, and fills chunks
with the recorded structure corpus (`structures.json`: villages, outposts,
ancient cities, trial chambers) for overworld and amplified the same way. Mutations of
FlatCache corner order, ore root order, cell input order, FlatCache handling
in templates (caught by a router whose FlatCache input depends on Y) and the
aquifer barrier each fail a test, according to the implementation author.
The Java comparator disables native fill, preliminary levels, fluid sources
and chunk-noise instantiation; earlier native noise/density/aquifer helpers
remain shared. See the [verification limits](RUST-WORLDGEN-ORGANIZATION.md#verification).

## Measurements

The implementation author recorded release runs on 2026-10-05 on an i7-10750H
laptop (CPU 5 measured, CPUs 0/1 for JVM workers). The driver ran three
alternating JVM pairs per setting, at least 15 s of warmup and 30 samples per
JVM, each sample building and filling eight fresh chunks; route checksums
matched in every JVM.

| Setting | Wrapped graph median | Native template median | Paired ratios | 95% ratio interval |
|---|---:|---:|---|---:|
| Overworld | 62.6 ms | 48.8 ms | 0.78, 0.73, 0.79 | 0.72–0.80 |
| Amplified | 59.0 ms | 46.0 ms | 0.83, 0.78, 0.74 | 0.74–0.84 |
| Nether | 9.8 ms | 9.3 ms | 0.96, 0.88, 0.91 | 0.88–0.97 |
| Structures (overworld) | 112.0 ms | 92.6 ms | 0.83, 0.81, 0.83 | 0.80–0.84 |

The structures case builds overworld chunks at eight corpus structure positions
with their structure Beardifiers; its baseline is the previous production path
for such chunks (constructor wrapping and Java's per-cell loop).

Overworld, amplified and structure chunks improved in every pair; nether's
smaller constructor (about 50 µs) gives a smaller gain. A same-JVM
interleaved probe of 2,000 chunks per route measured 7–8% less time per nether
constructor and fill, and 22–23% for overworld and amplified. Times include
the biome stage's climate sampler (built and sampled at every quart), which a
native chunk now builds without the constructor's wrapping. Not surface,
carving, feature or whole-game measurements. Raw rounds and hashes were
recorded under `build/chunk-noise-migration/` (not bundled with the wiki).

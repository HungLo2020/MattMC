# Rust noise router

During the NOISE fill, each `NoiseChunk` fills interpolation slices: for every
cell column of the next X slice, every interpolator's density function is
evaluated at each cell-corner Y. Slice filling dominated the remaining fill time
once the block loop moved to Rust ([NOISE fill](RUST-NOISE-FILL.md)).

[`NativeNoiseRouter`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeNoiseRouter.java)
compiles all of a chunk's interpolators into one program for
[`router/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/router).
Rust then fills a whole slice — every column, interpolator and Y — in one
ordinary downcall into off-heap memory, and Java copies the values into the
interpolators' slice arrays. Cell caches, aquifer materials and the block loop
read those arrays as before. Java still owns the `NoiseChunk`, its caches and
the density graphs; the router exists for one `NativeNoiseFill.run`.

## Program

The compiler walks each interpolator's chunk-wrapped graph (`wrapped()`) by
identity, so a shared `CacheOnce` becomes one shared node:

| Java function | Router node |
|---|---|
| `Constant`, `BlendAlpha` (1.0), `BlendOffset` (0.0) | constant |
| `Noise`, `Shift`, `ShiftA`, `ShiftB`, `ShiftedNoise`, `WeirdScaledSampler` | noise operations 1–7 shared with density programs |
| `Ap2`, `MulOrAdd`, `Mapped`, `Clamp`, `RangeChoice` | density `math` operations 8–22 |
| `YClampedGradient`, `BlendedNoise`, `EndIslandDensityFunction` | gradient, blended noise, End islands |
| `Spline` | a spline table (existing spline nodes and knots) over up to four coordinate nodes |
| `FlatCache` | the chunk's values, copied once per router |
| `Cache2D` | a copy of its input, accepted only if that input cannot depend on Y |
| `CacheOnce`, holders, optimizer wrappers, `BlendDensity` | transparent |

Every operation calls the production Rust implementation that Java's
`compute` already uses: density arithmetic and branch predicates, noise
samplers, spline evaluation (float coordinates) and the End island height.
Noise states are referenced, not copied; Java keeps them reachable until
release.

Rust evaluates each column as up to 64 lanes (one per corner Y). A node
computes only the lanes its parent needs: `MUL`, `MIN` and `MAX` evaluate their
right input only where Java's short circuit would, and `RangeChoice` evaluates
each branch only for its own lanes. Nodes that cannot depend on Y (constants,
FlatCache, `ShiftA`/`ShiftB`, End islands, noises with zero Y scale and anything
built only from those) run once per column.

## Eligibility

`NativeNoiseRouter.create` returns null, and Java fills the slices, unless:

- the chunk is a plain `NoiseChunk` with the empty `Blender`, not yet
  interpolating, with 1–64 plain `NoiseInterpolator`s it owns;
- every node is one of the functions above (nested interpolators, cell caches,
  markers and other functions are rejected), with bounded size and depth;
- every `Cache2D` input is Y-independent (Rust validates this);
- no cell cache can read, with the chunk as context, a `CacheOnce` that the
  router replaced. Java's slice fill leaves such a cache's last value behind;
  the router does not, so sharing it would be observable.

A slice whose FlatCache lookup would fall outside the chunk's quart grid
(impossible for 16-block chunks) returns status 1 and Java fills that slice.
All vanilla noise settings take the router.

## Preserve these contracts

- Slices hold the same points as `fillSlice`: block X of the slice, Z at each
  cell column, Y at `(i + cellNoiseMinY) * cellHeight`.
- After a native slice, `NoiseChunk` sets the fields the Java column loop
  leaves behind and advances both interpolation counters. Their exact steps are
  unobservable: every counter comparison is against a value recorded earlier.
- The Y-independence rule for noises relies on samplers adding a non-negative
  octave offset before reading Y, so `y * 0.0`'s sign cannot reach the result.
  A sampler change that breaks this must also change the rule.
- One point uses the single-sample noise path Java's `compute` uses; more use
  the batch path Java's array fills use.

## Verify and measure

```sh
python3 DevUtils/tests/worldgen/VerifyRustNoiseRouter.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustNoiseRouter.py --forks 3 --cpu 5 --background-cpus 0,1
```

The driver rebuilds every edited production file from the reference commit
with its exact audited rewrites and requires a byte-for-byte match.
`NativeNoiseRouterTest` fills chunks of every vanilla noise setting (three seeds,
six positions out to the world border) with both slice fills and compares every
slice value bit for bit after each step. A synthetic router exercises every node
type, short circuit, shared `CacheOnce`, spline over Y-dependent coordinates and
`Cache2D` over a FlatCache; further cases check each gate and the per-slice
fallback. `NativeNoiseFillTest` additionally requires every vanilla fill to take
native slices while matching the Java loop's sections, heightmaps and
post-processing exactly. Rust tests cover lane blocks, short circuits, FlatCache
indexing, validation, Y-independence and splines.

Benchmarks (`NoiseFillVerification`) time `fillFromNoise` on fresh chunks
through the native fill in separate JVMs: `javaslices` (the previous production
route) against `native`. Router compile and release are inside the timing.

## Measurements

The implementation author recorded release runs on 2026-10-04 on an i7-10750H
laptop (CPU 5 measured, CPUs 0/1 for JVM workers), alternating JVM pairs, at
least 15 s of warmup and 30 samples per JVM, each sample filling eight fresh
chunks. Both routes produced identical checksums in every JVM.

| Setting | Java slices median | Native slices median | Paired ratios | 95% ratio interval |
|---|---:|---:|---|---:|
| Overworld (3 pairs) | 95.4 ms | 53.5 ms | 0.53, 0.61, 0.50 | 0.48–0.65 |
| Amplified (3 pairs) | 80.1 ms | 61.5 ms | 0.73, 0.69, 0.80 | 0.65–0.84 |
| Nether (8 pairs) | 12.0 ms | 11.9 ms | 0.86–1.22, median 0.89 | 0.86–1.10 |

Overworld and amplified improved in every pair. Nether is not established
across JVMs: its per-JVM variation (11–23%) exceeds the effect, because its
single interpolator is dominated by `BlendedNoise`, which both routes sample
with the same batch sampler. A same-JVM interleaved probe of 4,000 nether fills
measured 9% less time per chunk (about 1.52 ms to 1.38 ms). Times cover the
whole fill stage. Not surface, carving, feature or whole-game measurements.
Raw rounds and hashes were recorded under `build/noise-router-migration/`
(not bundled with the wiki).

# Rust noise router

During the NOISE fill, each `NoiseChunk` fills interpolation slices: for every
cell column of the next X slice, every interpolator's density function is
evaluated at each cell-corner Y. Slice filling dominated the remaining fill time
once the block loop moved to Rust ([NOISE fill](RUST-NOISE-FILL.md)).

[`NativeNoiseRouter`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeNoiseRouter.java)
compiles all of a chunk's interpolators into one program for
[`router/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/router).
Rust fills a whole slice — every column, interpolator and Y — in one ordinary
downcall. With [native cell traversal](RUST-NOISE-FILL.md#cell-traversal), both
slices stay in Rust and feed the native cell program; Java prepares requested
aquifer materials and installs the fill results. The Java per-cell route
instead copies slice values into the interpolators' arrays. Java retains the
`NoiseChunk`, graph compilation and compatibility paths; each router instance
exists for one `NativeNoiseFill.run`. The same
compiler builds the shared [preliminary surface level](RUST-PRELIMINARY-SURFACE.md)
and [aquifer fluid source](RUST-AQUIFER.md#native-fluid-sources) programs in point mode,
and per-seed [chunk noise templates](RUST-CHUNK-NOISE.md) that eligible chunks
instantiate instead of compiling their wrapped graphs.

## Program

For the per-chunk compilation route, the compiler walks each interpolator's
chunk-wrapped graph (`wrapped()`) by identity, so a shared `CacheOnce` becomes
one shared node. [Chunk-noise templates](RUST-CHUNK-NOISE.md) compile the
normalized unwrapped graph once per `RandomState` instead:

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

Rust evaluates each column in blocks of up to 64 lanes (one per corner Y). A node
computes only the lanes its parent needs: `MUL`, `MIN` and `MAX` evaluate their
right input only where Java's short circuit would, and `RangeChoice` evaluates
each branch only for its own lanes. Nodes that cannot depend on Y (constants,
FlatCache, `ShiftA`/`ShiftB`, End islands, noises with zero Y scale and anything
built only from those) run once per column per lane block.

## Eligibility

The following gates apply to `NativeNoiseRouter.create`, the per-chunk
wrapped-graph route. [Template instantiation](RUST-CHUNK-NOISE.md) has its own
gates. `create` returns null, leaving Java to fill slices within an otherwise
eligible native fill, unless:

- the router is enabled (`-Dmattmc.worldgen.javaNoiseRouter=true` disables it);
- the chunk is a plain `NoiseChunk` with the empty `Blender`, not yet
  interpolating, with 1–64 plain `NoiseInterpolator`s it owns;
- every node is one of the functions above (nested interpolators, cell caches,
  markers and other functions are rejected), with bounded size and depth;
- every `Cache2D` input is Y-independent (Rust validates this);
- no cell cache can read, with the chunk as context, a `CacheOnce` that the
  router replaced. Java's slice fill leaves such a cache's last value behind;
  the router does not, so sharing it would be observable.

A needed FlatCache lookup outside the chunk's quart grid returns status 1;
Java copies no native output into its slice arrays and fills that slice itself.
Normal 16-block chunk traversal stays inside the grid; the fallback test
explicitly requests an out-of-grid slice. Other nonzero slice statuses or
failed downcalls throw, rather than restarting the whole fill in Java. See the
[Java-traversal slice handoff](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/java/net/minecraft/world/level/levelgen/NativeNoiseRouter.java#L148-L168).
On the [native traversal](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/java/net/minecraft/world/level/levelgen/NativeNoiseFill.java#L466-L485),
a declined wrapped-router slice is filled in Java and uploaded to Rust; a
template instance declining an in-grid slice throws.
The bundled noise settings are expected to take the router in the fresh,
empty-blender test fixture; custom graphs and other chunk contexts must still
pass every gate.

## Preserve these contracts

- Slices hold the same points as `fillSlice`: block X of the slice, Z at each
  cell column, Y at `(i + cellNoiseMinY) * cellHeight`.
- On the Java per-cell route, after a native slice `NoiseChunk` sets the fields the Java column loop
  leaves behind and advances both interpolation counters. Their exact steps are
  unobservable: every counter comparison is against a value recorded earlier.
- The Y-independence rule for noises relies on samplers adding a non-negative
  octave offset before reading Y, so `y * 0.0`'s sign cannot reach the result.
  A sampler change that breaks this must also change the rule.
- One point uses the single-sample noise path Java's `compute` uses; more use
  the batch path Java's array fills use.

## Verify and measure

For current router and fill integration, use the
[cell traversal driver](RUST-NOISE-FILL.md#cell-traversal-verification), which
also runs `NativeNoiseRouterTest`. See the [shared verification
limits](RUST-WORLDGEN-ORGANIZATION.md#verification).

The commands below reproduce the router-only migration in a separate checkout
of `da1109de6`; they are not current-tree verification commands. Its exact
rewrite audit rejects the later production changes before building. Preserve
that audit rather than bypassing it.

```sh
python3 DevUtils/tests/worldgen/VerifyRustNoiseRouter.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustNoiseRouter.py --forks 3 --cpu 5 --background-cpus 0,1
```

The router's [driver](https://github.com/HungLo2020/MattMC/blob/da1109de6fe84592bf75e32cffef0cb5506d2651/DevUtils/tests/worldgen/VerifyRustNoiseRouter.py)
audits modified production Java/Rust files against `858476969` with exact
rewrites before building. New router files and test files are hashed; they are
not reconstructed from an earlier Java oracle. The driver uses Linux affinity
and native-library paths. Select available, distinct CPU IDs for `--cpu` and
`--background-cpus` (at least two worker IDs); this validation also runs with
`--parity-only`. Later edits to these files are audited by
[the surface level driver](RUST-PRELIMINARY-SURFACE.md) and the drivers after it.

At that historical snapshot, [`NativeNoiseRouterTest`](https://github.com/HungLo2020/MattMC/blob/da1109de6fe84592bf75e32cffef0cb5506d2651/src/test/java/net/minecraft/world/level/levelgen/NativeNoiseRouterTest.java)
has four methods. Its bundled-setting fixture compares each slice value bit
for bit after each step across three seeds and six positions out to the world
border. The synthetic fixture covers the supported operations, short circuits, shared `CacheOnce`,
Y-dependent spline coordinates and `Cache2D` over a FlatCache. Other cases check
selected graph-rejection gates, the disabled switch and per-slice fallback;
they do not exhaust every size, ownership or lifecycle gate.
[`NativeNoiseFillTest`](https://github.com/HungLo2020/MattMC/blob/da1109de6fe84592bf75e32cffef0cb5506d2651/src/test/java/net/minecraft/world/level/levelgen/NativeNoiseFillTest.java#L148-L173)
also requires every bundled fixture fill and slice to take the native route
while comparing sections, heightmaps and post-processing with the Java loop.
The [seven added Rust router tests](https://github.com/HungLo2020/MattMC/blob/da1109de6fe84592bf75e32cffef0cb5506d2651/src/main/rust/world/level/levelgen/router/tests.rs)
cover lane blocks, short circuits, FlatCache indexing, validation,
Y-independence and splines. The driver's Rust filter runs the broader levelgen
suite, not only these seven tests.

Benchmarks (`NoiseFillVerification`) time `fillFromNoise` on fresh chunks
through the native fill in separate JVMs: `javaslices` (the previous production
route) against `native`. Both modes retain the native block fill and previously
native worldgen helpers. Router compile and release are inside the timing;
chunk and `NoiseChunk` construction are excluded equally. Each sample times
eight fills plus the workload's checksum reads. The checksum covers heightmap
words and section air/non-air flags, a narrower comparison than the parity
assertions. The [benchmark route check](https://github.com/HungLo2020/MattMC/blob/da1109de6fe84592bf75e32cffef0cb5506d2651/src/test/java/net/minecraft/world/level/levelgen/NoiseFillVerification.java#L52-L83)
requires nonzero native fill/slice counts in native mode, not native execution
of every measured slice.

## Measurements

The implementation author recorded release runs on 2026-10-04 on an i7-10750H
laptop (CPU 5 measured, CPUs 0/1 for JVM workers), alternating JVM pairs, at
least 15 s of warmup and 30 samples per JVM, each sample filling eight fresh
chunks. Both routes produced identical checksums in every JVM.

| Setting | Java slices median per eight fills | Native slices median per eight fills | Paired ratios | 95% ratio interval |
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

The CPU assignments above are the author's recorded setup. The checked-in
driver applies one process-wide `taskset` CPU mask; it does not itself prove
separate measured-thread and JVM-worker affinity. Its `cpu_ns` records only the
calling thread's CPU time while `fillFromNoise` dispatches background work;
use the elapsed-time samples for the stated workload comparison.

## Maintenance review and remaining acceptance

The [2026-10-05 static review for #775](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-5988872723) inspected
[`da1109de6fe84592bf75e32cffef0cb5506d2651`](https://github.com/HungLo2020/MattMC/commit/da1109de6fe84592bf75e32cffef0cb5506d2651).
Static reconstruction matched ten modified production Java/Rust files from
24 exact rewrites against `858476969`; this checks the recorded edit boundary,
not runtime parity. The tests and measurements above are source-defined checks
and author-recorded results, not reruns by this review. No Java/Rust suite,
mutation test, benchmark or live world was run, and the unbundled measurement
files were not verified.

At that reviewed snapshot, Java compiled graphs, copied slice output, traversed
cells, prepared cell caches and requested aquifer materials, and installed the
fill results. Current [cell traversal](RUST-NOISE-FILL.md#cell-traversal) and
[chunk-noise templates](RUST-CHUNK-NOISE.md) move more of that work into Rust,
while Java retains compilation, requested aquifer-material preparation and
result installation. These bounded migrations do not establish native
ownership of the complete world-generation pipeline. Retain explicit acceptance work for custom/blended
contexts, failure and cancellation recovery, concurrency, native memory bounds
and FULL-chunk generation before extending scope. The migration does not close
[#775](https://github.com/HungLo2020/MattMC/issues/775).

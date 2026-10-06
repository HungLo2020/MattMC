# Rust aquifer evaluation

## Ownership and boundary

`levelgen/aquifer/` owns nearest-center selection, pressure/material decisions,
fluid-level calculations, and fluid-update decisions. For the built-in Xoroshiro
and Legacy positional factories it also draws each grid cell's random centre
(`aquifer/locations.rs` over [`levelgen/random.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/random.rs));
custom factories keep Java's `location()`. Java owns density-graph bindings and
block-state identity. The [native NOISE fill](RUST-NOISE-FILL.md) owns chunk
writes for eligible chunks; the remaining Java loop keeps them otherwise.

For the built-in fluid picker and known pure density sources, Rust consumes the
already-filled density cell and returns block choices plus update flags. Material calls
process at most 128 positions before yielding. Missing aquifer statuses are
resolved lazily; repeated preliminary-surface lookups stay in a native cache,
and misses are computed by the [native surface program](RUST-PRELIMINARY-SURFACE.md).
Barrier noise uses the existing native synthesis kernel directly.
Native buffers are created only when a query needs aquifer sampling. Single-column
height queries retain ordered requests instead of evaluating an entire cell.

### Native fluid sources

With pure sources and native [preliminary surface levels](RUST-PRELIMINARY-SURFACE.md),
a fluid status is one native call: Rust also evaluates the aquifer's noise
sources (erosion, depth, fluid level floodedness, fluid level spread, lava)
instead of yielding each request to Java.
[`NativeFluidSources`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeFluidSources.java)
compiles them once per `RandomState` with the [noise router](RUST-NOISE-ROUTER.md)
compiler. Java evaluates the chunk-wrapped sources at point contexts, where a
`FlatCache` returns its quart-corner value (input at Y 0) inside the chunk's
grid and its input at the point outside it; the router's FLAT_POINT node does
the same through a per-chunk binding that `NativeAquifer` owns, computing each
corner once. Before the first native status, `NativeAquifer` copies the
chunk's cached surface levels into its surface window. On this per-status
request path Java owns the status cache and its callers. Eligible
[NOISE traversals](RUST-NOISE-FILL.md#rust-owned-aquifer-materials) and
[carvers](carver/RUST-CARVERS.md) instead copy the aquifer caches into a
Rust-owned binding for the stage and copy the resulting memos back afterwards. `NativeFluidSourcesTest` compares every status
of the fill's aquifer centres and points across the chunk grid's edge with
Java's request path for every aquifer-enabled vanilla setting.

A verification-only entry point also compares each source's value bit for bit
(43,200 values across the grid edge); mutations of corner position, memo
layout, the grid test, source order, surface quantization and the surface
window seed each fail it.

```sh
python3 DevUtils/tests/worldgen/VerifyRustFluidSources.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustFluidSources.py --forks 3 --cpu 5 --background-cpus 0,1
```

The driver audits every production edit since `da1109de6` and benchmarks
`NoiseFillVerification` in `javasources` against `native` mode. Measured
2026-10-05 (laptop as below): overworld 41.9 → 41.1 ms and amplified 40.5 →
40.8 ms per eight fills (paired ratios 0.89–1.10, intervals spanning 1);
same-JVM interleaved fills also measured no change (5.80 vs 5.81 ms, 5.32 vs
5.33 ms). Rust computes FlatCache corners that Java reads from tables the chunk
already holds. The move replaces about 180 Java request round trips per
overworld chunk with one call per status.

Custom sources, nonstandard contexts, and blended chunks use the ordered native
request path. Java answers requests in the original order. Unknown block states
have chunk-owned identity tokens rather than being collapsed to registry air.
Buffers belong to one chunk and are borrowed only for a foreign call.

`NoiseChunk` also defers unused final interpolation steps. It retains the original
Y/X/Z arithmetic and preserves reads between staging calls. Interpolator overrides
and `NoiseChunk` subclasses retain eager updates.

## Compatibility constraints

- Preserve last-visited-wins distance ties, Java integer overflow, float constants,
  floating-point operation order, NaN behavior, and signed zero.
- Preserve conditional noise sampling, fluid-source calls, random draws, and
  fluid-update flags. Matching block states alone is insufficient.
- Batch eligibility must reject unknown callbacks, including ore callbacks that
  interleave with aquifer decisions. Cell results expire at the next cell epoch.
- Keep chunk writes, heightmap changes, and postprocessing entries in their
  existing order (the native fill reproduces it). Rendering is outside this subsystem.
- Native centre draws must stay bit-identical to `location()`: `Mth.getSeed`,
  the factory seeds and `nextInt(10)`, `nextInt(9)`, `nextInt(10)` in that order.

## Verification

```sh
# Focused integration and Rust world-generation tests:
./gradlew test -PmattmcRustProfile=release -x testRustNative \
  --tests 'net.minecraft.world.level.levelgen.*'
rustc --edition=2021 --test src/test/rust/worldgen.rs -o build/worldgen-tests
build/worldgen-tests

# Original Java oracle, individual decisions/callback traces, and terrain hashes:
python3 DevUtils/tests/worldgen/VerifyRustAquifer.py --parity-only

# Five warmed JVM pairs for Amplified, Large Biomes, and Overworld:
python3 DevUtils/tests/worldgen/VerifyRustAquifer.py --forks 5
```

The driver extracts the original Java aquifer and interpolation implementation
from Git into ignored build output. The renamed oracle changes only class naming
and record-field access syntax. Production has no reference selector.
`VerifyRustWorld.py --parity-only` also extracts the original aquifer, preserving
its comparison against the original Java noise/density stack.

The timing gate requires every JVM pair and the upper 95% time-ratio bound to be
at most 0.95. These timings cover the terrain/material stage, not complete chunk
generation. `NoiseChunk` setup, interpolation, ore decisions, callbacks, and all
native calls are included; chunk storage writes and later generation stages are
outside this timer. Use `--world overworld` to narrow the timing workload or `--seed`
to change its seed. Fingerprints always cover five seeds and eight settings.
Full-world comparisons use the isolated server workflow in
[surface verification](RUST-SURFACE.md) and compare block states, heightmaps, and
ordered postprocessing entries.

## Recorded verification — 2026-09-30

- 2,520,000 original-Java comparisons matched exact block-state identities,
  update flags, and ordered source callbacks. Cases include both RNG families,
  extreme seeds, NaNs/infinities, stateful contexts, and reentrant callbacks.
- All 40 terrain fingerprints matched: eight settings, five seeds, 240 chunks,
  and 17,203,200 blocks plus router/climate/update observations. The separate
  comparison against the original Java noise/density/aquifer stack also matched.
- All 44 focused Java tests and 21 Rust world-generation tests passed.
- Fresh worlds for seeds `42` and `-123456789` matched 400 FULL chunks,
  1,296 surface checkpoints, and 1,296 carver checkpoints across Overworld,
  Primordial Caves, Nether, and End. These runs used one worker and fixed
  request order; blocks, heightmaps, and ordered postprocessing all matched.
  Reports are under `build/aquifer-migration/full/*-native/comparison.json`.

Ryzen 5 5600G, JDK 25.0.4.1, Rust 1.93.1, full Cargo release library. Seed 42,
five chunk positions, five independent JVM pairs, 11 measured rounds per setting
after stable warmup. Times are medians of fork medians in milliseconds per chunk;
the improvement includes the aquifer migration and deferred interpolation.

| Setting | Java | Native integration | Less time | Conservative 95% ratio interval |
| --- | ---: | ---: | ---: | --- |
| Amplified | 8.656 | 8.068 | 6.8% | 0.9242–0.9477 |
| Large Biomes | 9.950 | 9.063 | 8.9% | 0.9073–0.9235 |
| Overworld | 8.937 | 8.228 | 7.9% | 0.9124–0.9338 |

Every paired median and upper confidence bound passed the 5% terrain-time gate.
The initial three-pair Amplified upper bound was 0.9517; two additional pairs
were collected, retaining every original round. All measurements used unchanged
sources and the same native binary. Raw evidence is in
`build/aquifer-migration/five-pair-report.json`; the initial and additional logs
are in `acceptance/` and `additional-pairs/` beneath that directory.

### Full-generation timing

Normal parallel Overworld generation through FULL used three fresh-world JVM
pairs, three workers, six warmup regions, and three measured regions totaling
675 requested chunks per process. Explicit save/drain was outside the timer.

| Pair | Java total | Native total | Native elapsed-time change |
| --- | ---: | ---: | ---: |
| 1 | 19.791 s | 19.691 s | −0.5% |
| 2 | 19.956 s | 20.130 s | +0.9% |
| 3 | 20.092 s | 20.340 s | +1.2% |

The median paired change was **0.9% slower**, with 0.4% less process CPU time
and 0.6% more allocated bytes. Measured regions still recorded 2.1–2.8 seconds
of JVM compilation activity. These observations do **not** establish a 5%
complete-generation gain or a reliable small regression. Raw samples, JIT
counters, commands, and process exit records are under
`build/aquifer-migration/full-timing/`; `report.json` summarizes the pairs.

A finite parity corpus does not prove equivalence for every possible input.
The 5% acceptance result applies to the measured terrain/material evaluator,
including its boundary costs, not complete generation, custom callbacks, or
other machines.

The repository-wide Rust suite encountered 80 renderer failures, beginning with
an OpenGL shader compilation error and followed by poisoned graphics locks.
Rendering was not changed; the focused world-generation suite passed separately.

## Native stage decisions

The NOISE fill's cell traversal prepares cell materials with the same path
([Rust-owned aquifer materials](RUST-NOISE-FILL.md#rust-owned-aquifer-materials)).

### Carver substance decisions

The [Rust carvers stage](carver/RUST-CARVERS.md) decides
`computeSubstance(SinglePointContext, 0.0)` entirely in Rust
([`aquifer/substance.rs`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/aquifer/substance.rs))
for aquifers on the fully native route: native centres, ranking and decision,
fluid statuses from the native sources and surface programs, and the barrier
noise, over the aquifer's own caches (`NativeAquifer.nativeBinding`).

# Rust NOISE fill

`NoiseBasedChunkGenerator.doFill` writes a chunk's base terrain. For eligible
chunks [`NativeNoiseFill`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeNoiseFill.java)
hands every block to [`noise_fill/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/noise_fill):
interpolation, the substance rule (aquifer batch or disabled aquifer), the ore
vein rule with native positional randomness, the default block, section palette
writes and counters, both world-generation heightmaps and fluid post-processing
marks. Java installs the sections, heightmaps and marks once.

The [noise router](RUST-NOISE-ROUTER.md) fills eligible interpolation slices;
Java fills slices that its separate router gate declines. Java still traverses
cells, fills cell density caches and, on request, aquifer cell materials
(including fluid-status requests), and owns the generator, the `NoiseChunk`
and every chunk object. Chunks ineligible for native fill run the unchanged
Java block loop.

## Eligibility

All must hold; otherwise the Java loop runs:

- No `DEBUG_ORE_VEINS`, `DEBUG_AQUIFERS`, `DEBUG_DISABLE_FLUID_GENERATION` or void-terrain debugging.
- A plain `NoiseChunk`, empty `Blender`, the finalDensity cell cache, 16-block cell rows.
- Every section the fill can write is still untouched air; both heightmaps unwritten.
- Substance: a `NoiseBasedAquifer` whose native batch is ready (built-in factory,
  `AquiferFluidPicker`, pure sources, critical barrier noise), or `Aquifer.Disabled`
  with an `AquiferFluidPicker`.
- Ore veins (if enabled): the vanilla shape — `veinToggle` a `NoiseInterpolator`,
  `veinRidged` `MulOrAdd(ADD, c, Ap2(MAX, Mapped(ABS, interp), Mapped(ABS, interp)))`,
  `veinGap` a `Noise` — and an Xoroshiro or Legacy ore factory.

The bundled noise settings can qualify when these chunk-state and
context checks also pass. Once native fill is selected, a failure throws rather
than silently restarting the Java block loop. Router rejection or its explicit
per-slice fallback can still select Java slice evaluation inside that native
fill; see the [separate router contract](RUST-NOISE-ROUTER.md#eligibility).

## Preserve these contracts

- Blocks run in `doFill`'s order (cell X, cell Z, cell Y down, then Y down, X, Z).
  The palette replay depends on it: a resize rebuilds by first occurrence in
  storage index order, so write order changes in-memory palette order (network
  bytes) even when saved data re-packs it.
- Interpolation repeats `NoiseInterpolator`'s lerp chain; ore arithmetic keeps
  Java's float constants, `clampedMap` and draw order; `veinRidged` evaluates
  through the same Rust `math` operations and `max` branch Java calls.
- Each position is written once, top-down per column, into fresh sections, so
  heightmaps reduce to the highest matching `y + 1` and counters to tallies.
  Any change that writes twice or into non-fresh state must leave the gate.
- Rust repeats `NoiseBasedAquifer.compute`'s early returns (solid, above the
  sampling ceiling, lava) and asks Java for a cell's batch materials (status 1,
  before any write) only when some block reaches the batch — exactly the cells
  the Java loop prepares, so fluid-status requests and caches match too.
- Section installs go through `LevelChunkSection.installGenerated`.

## Verify and measure

For the current implementation, use the
[router verification driver](RUST-NOISE-ROUTER.md#verify-and-measure), which runs
both router and fill parity classes. The following commands reproduce the
historical fill migration in a separate checkout of `858476969`; they are not
current-tree verification commands:

```sh
python3 DevUtils/tests/worldgen/VerifyRustNoiseFill.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustNoiseFill.py --forks 3 --cpu 5 --background-cpus 0,1
```

This historical driver reconstructs the `858476969` production files from
reference commit `5218ac875`. Its exact-rewrite audit rejects the later router
changes to `NoiseChunk.java` before tests run. Use the current driver above on
`da1109de6` rather than bypassing this audit.

At that historical snapshot, the driver rebuilds every edited production Java
file from the reference commit with its exact audited rewrites and requires a
byte-for-byte match. `NativeNoiseFillTest` fills each bundled noise setting at
three seeds and five chunk positions through `fillFromNoise`
with both routes and compares section network bytes, saved packs, counters,
heightmaps, post-processing lists and later aquifer reads; every candidate fill
must take the native route, and the aggregate fixture must exercise ore and
raw-ore blocks. It does not require ore in each individual chunk. It also
compares native aquifer centres with Java's `location()` (Xoroshiro and Legacy) and replays up
to 400-state write sequences against real `PalettedContainer` resizes. The
implementation author reported that a raw-ore chance mutation and a write-order palette mutation each failed a test.

Benchmarks time `fillFromNoise` on fresh chunks per mode in separate JVMs;
chunk and `NoiseChunk` construction are excluded equally.

## Measurements

For the pre-router fill implementation at `858476969`, the implementation
author recorded a full release run on 2026-10-04: three
alternating JVM pairs per setting on an i7-10750H laptop (CPU 5 measured, CPUs
0/1 for JVM workers), at least 15 s of warmup and 30 samples per JVM, each
sample filling eight fresh chunks. Both routes produced identical checksums in
every JVM.

| Setting | Java loop median per eight fills | Native fill median per eight fills | Paired median reduction | 95% ratio interval |
|---|---:|---:|---:|---:|
| Overworld (aquifers, ore veins) | 88.9 ms | 71.0 ms | 20% | 0.689–0.822 |
| Amplified | 101.9 ms | 68.0 ms | 33% | 0.604–0.824 |
| Nether (disabled aquifer) | 17.4 ms | 10.5 ms | 40% | 0.563–0.716 |

Every pair improved by more than 5%. Variation was 5–18% per JVM, as each fill
hands off to the world-generation executor; 13 of 540 samples overlapped JIT
activity. Times cover the whole fill stage, including the unchanged Java slice
and cell-cache work. Not surface, carving, feature or whole-game measurements.
Raw rounds and hashes were recorded at
`build/noise-fill-migration/acceptance/results.json` (not bundled with the wiki).

## Maintenance review and remaining acceptance

The [2026-10-05 source review for #775](https://github.com/HungLo2020/MattMC/issues/775#issuecomment-5987249254)
inspected this implementation at `858476969d6500b2a9e26d10c5d8c5967222ccfe`.
The [three Java test methods](https://github.com/HungLo2020/MattMC/blob/858476969d6500b2a9e26d10c5d8c5967222ccfe/src/test/java/net/minecraft/world/level/levelgen/NativeNoiseFillTest.java#L148-L241)
and [eight added native noise-fill tests](https://github.com/HungLo2020/MattMC/blob/858476969d6500b2a9e26d10c5d8c5967222ccfe/src/main/rust/world/level/levelgen/noise_fill/tests.rs)
were read, not rerun. The driver's Rust filter covers the broader levelgen suite;
eight is the added noise-fill test count, not that whole suite's size. Static
reconstruction matched eight modified production Java files from twenty-one
exact rewrites; new bridge/Rust files were hashed rather than reconstructed
from an original-Java oracle. The distance driver's three converted files also
matched fourteen rewrites, an audit-format change rather than another runtime
migration. No Java/Rust suite, mutation test, benchmark or live world was run in
this maintenance review.

The comparison is the **Java fill loop versus native fill**, with previously
native worldgen helpers still present in the Java-loop route. It is not a wholly
Java historical worldgen baseline. The [benchmark workload](https://github.com/HungLo2020/MattMC/blob/858476969d6500b2a9e26d10c5d8c5967222ccfe/src/test/java/net/minecraft/world/level/levelgen/NoiseFillVerification.java#L47-L80)
times eight fills per sample, including Java cell/slice work and native result
installation. Its checksum covers heightmap words and section air/non-air flags;
it is narrower than the detailed parity assertions. The recorded reductions are
fill-workload elapsed times, not per-chunk full-generation or whole-game gains.

Java still owns cell traversal/cache preparation, generator and NoiseChunk
lifetime, registry identity, chunk-object installation and later stage
orchestration. Built-in ore/aquifer positional randomness moving here does not
move every worldgen random owner. Keep custom/blended/prewritten-state fallback,
cancellation/concurrency/failure recovery, native memory bounds and FULL-chunk
acceptance explicit before extending the supported scope. This bounded migration
does not close #775 or complete world-generation ownership. The later
[router review](RUST-NOISE-ROUTER.md#maintenance-review-and-remaining-acceptance)
records current slice ownership and evidence limits at `da1109de6`; the
historical fill measurements above do not verify that later implementation.

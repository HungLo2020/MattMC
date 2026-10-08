# Rust NOISE fill

`NoiseBasedChunkGenerator.doFill` writes a chunk's base terrain. For eligible
chunks [`NativeNoiseFill`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeNoiseFill.java)
hands every block to [`noise_fill/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/noise_fill):
interpolation, the substance rule (aquifer batch or disabled aquifer), the ore
vein rule with native positional randomness, the default block, section palette
writes and counters, both world-generation heightmaps and fluid post-processing
marks. Java installs the sections, heightmaps and marks once.

The [noise router](RUST-NOISE-ROUTER.md) fills the interpolation slices and,
when the cell traversal is native (below), Rust also walks every cell and, for
aquifers on the native route, prepares their cell materials too. Java owns the
generator, the `NoiseChunk` and every chunk object, and prepares materials
only for other aquifers. Ineligible chunks run the unchanged Java loop.

## Cell traversal

With a router (a chunk's compiled router, or an instance of its seed's
[chunk noise template](RUST-CHUNK-NOISE.md)), `NativeNoiseFill` binds it and
the cell cache's program to the fill ([`traversal.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/noise_fill/traversal.rs)).
Rust then keeps both slices, builds each cell's interpolator corners as
`selectCellYZ` and `copyCellCorners` do, evaluates the cell density cache with
the same validated program `NativeCellDensity` runs, copies the ore vein
corners and fills the cell: one ordinary downcall per slice and one per column
of cells. When a cell needs aquifer materials, the
[Rust-owned aquifer](#rust-owned-aquifer-materials) prepares them; otherwise
Rust stops before writing the cell, Java copies its densities into the cell
cache, prepares the materials and resumes the call. A slice the router
declines is filled by Java and uploaded. For a router compiled from a wrapped
chunk, the traversal needs the finalDensity cell cache to be the chunk's only
cell cache and a `NativeCellDensity` over this chunk's interpolators;
otherwise Java's per-cell loop runs with Rust slices. A native chunk-noise
template supplies its own cell program and can also bind [structure Beardifier
cells](RUST-CHUNK-NOISE.md#structure-terrain-adjustment). Afterwards `NoiseChunk`
has stopped interpolating; its interpolator corners and cell counters are not
read again.

## Rust-owned aquifer materials

When the chunk's aquifer is on the fully native route
(`NativeAquifer.nativeBinding`: built-in pure sources and positional
randomness, a water, lava or air fluid picker, a native barrier noise),
`NativeNoiseFill` hands its state to the traversal once: copies of the
centre, fluid status, preliminary surface and FlatCache caches, its policy and
programs. A cell needing batch materials then gets them from
[`aquifer/substance.rs`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/aquifer/substance.rs)
(`OwnedAquifer::cell_materials`): `NativeAquifer.prepareMaterials`' batch
decision with every fluid status computed natively instead of asked of Java.
The caches are pure memos; Java copies them back after the traversal. The
aquifer's `shouldScheduleFluidUpdate` is not touched, as with Java-prepared
batches. `-Dmattmc.worldgen.javaFillMaterials=true` keeps Java preparing
materials, for comparisons.

The same aquifer path decides substances for the
[carvers stage](carver/RUST-CARVERS.md).

```sh
python3 DevUtils/tests/worldgen/VerifyRustFillAquifer.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustFillAquifer.py --forks 3 --cpu 5 --background-cpus 0,1
```

`NativeNoiseFillTest.rustAquiferMaterialsMatchJavaPreparedMaterials` fills
every vanilla setting (three seeds, four positions) with Rust-owned materials
and with Java-prepared ones and compares sections, heightmaps,
post-processing and later aquifer reads, plus an overworld variant with a lava
sea; every aquifer-enabled fill must take the Rust route. The other fill tests
compare the default route with Java's pure loop. Mutations of the batch's
fluid-kind code or air id fail them; using the fluid level instead of
`min(-54, fluid level)` for the batch's lava boundary survives, because the
fill's own substance rule answers every block below a sea before the batch. The driver benchmarks `NoiseFillVerification` in `javamaterials`
against `native` mode.

Author-recorded measurements (2026-10-05, the same laptop and method as the cell traversal):
4.96 → 4.97 ms per overworld fill (paired ratios 0.96, 0.93, 1.02), 4.78 →
4.74 ms amplified (0.96, 0.99, 1.03) and 0.93 → 0.95 ms nether, which has no
aquifer (1.03, 0.99, 0.95). No 95% interval excludes 1: the material
decisions were already native, so the move removes the per-cell handoff and
Java's status requests (Java↔Rust chatter) rather than compute. Raw rounds
were recorded under `build/fill-aquifer-migration/` (not bundled with the wiki).

## Eligibility

All must hold; otherwise the Java loop runs:

- The [block registry](../../game-model/RUST-BLOCK-REGISTRY.md) is installed.
  Rust derives each state's flags from it (air, blocks motion, fluid, random
  ticks, the AIR block), as do the proto-chunk storage and the surface stage.
  The [registry verification record](../../game-model/BLOCK-REGISTRY-VERIFICATION.md)
  reports the author's all-state flag and consumer parity checks; it does not
  provide a new noise-fill hot-path measurement for that table migration.
- No `DEBUG_ORE_VEINS`, `DEBUG_AQUIFERS`, `DEBUG_DISABLE_FLUID_GENERATION` or void-terrain debugging.
- A plain `NoiseChunk`, empty `Blender`, 16-block cell rows, and either a
  [native chunk-noise template](RUST-CHUNK-NOISE.md) or the wrapped finalDensity
  cell cache.
- Every section the fill can write is still untouched air; both heightmaps unwritten.
- Substance: a `NoiseBasedAquifer` whose native batch is ready (built-in factory,
  `AquiferFluidPicker`, pure sources, critical barrier noise), or `Aquifer.Disabled`
  with an `AquiferFluidPicker`.
- Ore veins (if enabled): an Xoroshiro or Legacy ore factory, and the vanilla
  ore shape validated either by the template on the unwrapped router or by
  the wrapped-chunk gate (`veinToggle` a `NoiseInterpolator`, `veinRidged`
  `MulOrAdd(ADD, c, Ap2(MAX, Mapped(ABS, interp), Mapped(ABS, interp)))`,
  and `veinGap` a `Noise`).

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
  sampling ceiling, lava) and prepares batch materials only when some block
  reaches the batch. A traversal with a native aquifer binding computes these
  materials in Rust; otherwise it requests them from Java (status 1, before
  writing that cell). Preserve the material-request order and cache results
  on each route.
- Section installs go through `LevelChunkSection.installGenerated`.
- The native traversal visits cells in the same order as Java's (X, then Z,
  then Y down) and requests materials for exactly the cells Java's loop would.

## Verify and measure

For the current implementation, use the
[cell traversal driver](#cell-traversal-verification), whose parity-only path
runs the router, preliminary-surface and fill parity classes. The following commands reproduce the
historical fill migration in a separate checkout of `858476969`; they are not
current-tree verification commands:

```sh
python3 DevUtils/tests/worldgen/VerifyRustNoiseFill.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustNoiseFill.py --forks 3 --cpu 5 --background-cpus 0,1
```

This historical driver reconstructs the `858476969` production files from
reference commit `5218ac875`. Its exact-rewrite audit rejects the later router
changes to `NoiseChunk.java` before tests run. Use the current driver below
rather than bypassing this audit.

### Cell traversal verification

The current driver's parity-only invocation is:

```sh
python3 DevUtils/tests/worldgen/VerifyRustCellTraversal.py --parity-only
```

It audits the recorded production rewrites since `da1109de6` and runs the
router, preliminary-surface and fill parity suites. The shared
[verification limits](RUST-WORLDGEN-ORGANIZATION.md#verification) apply.

The timing invocation below was used for the author-recorded comparison; it
is not a working current-tree timing recommendation:

```sh
python3 DevUtils/tests/worldgen/VerifyRustCellTraversal.py --forks 3 --cpu 5 --background-cpus 0,1
```

At `54611cfc`, [`NoiseFillVerification`](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/test/java/net/minecraft/world/level/levelgen/NoiseFillVerification.java#L59-L92)
disables the ordinary native-traversal switch in `javacells` mode but leaves
chunk-noise templates enabled. An eligible template [runs Rust traversal
unconditionally](https://github.com/HungLo2020/MattMC/blob/54611cfc25dbdf60ae4b11dc17557d2bec77469d/src/main/java/net/minecraft/world/level/levelgen/NativeNoiseFill.java#L290-L300),
so the benchmark's route assertion is expected to fail instead of comparing
Java cells with native cells. This is a static source finding, not a rerun;
it does not establish a failure of the separate parity-only path. The
[`5c02fd82` benchmark update](https://github.com/HungLo2020/MattMC/blob/5c02fd8215f4c1dde624dbe3d21a476d38b16708/src/test/java/net/minecraft/world/level/levelgen/NoiseFillVerification.java#L61-L100)
still leaves chunk-noise templates enabled in `javacells` mode, and the
[template fill still traverses natively](https://github.com/HungLo2020/MattMC/blob/5c02fd8215f4c1dde624dbe3d21a476d38b16708/src/main/java/net/minecraft/world/level/levelgen/NativeNoiseFill.java#L300-L310),
so this source-identified limitation remains current.

`NativeNoiseFillTest` requires fills of every bundled setting to take the Rust traversal,
and `cellTraversalsMatchJavaLoop` also compares fills whose slices Java fills
and uploads, and fills by Java's per-cell loop, with the Java loop. The
implementation author reports that mutations of corner order, Y order, the
material request position, the slice swap, the cell cache copy and the uploaded
slice each fail a test.

Author-recorded cell traversal measurements (2026-10-05, same laptop and
method as below):
the separate-JVM driver run measured medians per eight fills of 42.2 → 41.3 ms
(overworld, paired ratios 1.03, 0.88, 0.94), 40.1 → 39.1 ms (amplified, 0.95,
0.98, 1.02) and 8.1 → 7.8 ms (nether, 1.03, 0.97, 0.94). No 95% interval
excludes 1. A same-JVM interleaved probe of 2,000 fills per route measured
1–3% less time per overworld and amplified fill. The Java per-cell loop already
evaluated cell densities with the same Rust kernel, so the move removes
Java↔Rust chatter (about 768 `selectCellYZ` and cell calls and every slice copy
per chunk) rather than compute. Raw rounds were recorded under
`build/cell-traversal-migration/` (not bundled with the wiki).

At the historical fill snapshot `858476969`, `VerifyRustNoiseFill.py` rebuilds
its edited production Java files from the reference commit with exact audited
rewrites and requires a byte-for-byte match. `NativeNoiseFillTest` fills each bundled noise setting at
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

At the reviewed `858476969` snapshot, Java still owned cell traversal and
cache preparation. The later [native traversal](#cell-traversal) moves those
cell operations into Rust for eligible fills. Java retains the generator and
`NoiseChunk` lifetime, aquifer-material preparation on compatibility routes,
registry identity, chunk-object installation and later-stage orchestration.
The current [native aquifer binding](#rust-owned-aquifer-materials) moves
material preparation into Rust for eligible traversals. Built-in ore/aquifer positional randomness moving here does not
move every worldgen random owner. Keep custom/blended/prewritten-state fallback,
cancellation/concurrency/failure recovery, native memory bounds and FULL-chunk
acceptance explicit before extending the supported scope. This bounded migration
does not close #775 or complete world-generation ownership. The later
[router review](RUST-NOISE-ROUTER.md#maintenance-review-and-remaining-acceptance)
records slice ownership and evidence limits at `da1109de6`; the historical
fill measurements above do not verify that later implementation or the
[current native-stage changes](RUST-WORLDGEN-ORGANIZATION.md#verification).

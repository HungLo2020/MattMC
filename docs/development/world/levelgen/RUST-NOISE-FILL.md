# Rust NOISE fill

`NoiseBasedChunkGenerator.doFill` writes a chunk's base terrain. For eligible
chunks [`NativeNoiseFill`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeNoiseFill.java)
hands every block to [`noise_fill/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/noise_fill):
interpolation, the substance rule (aquifer batch or disabled aquifer), the ore
vein rule with native positional randomness, the default block, section palette
writes and counters, both world-generation heightmaps and fluid post-processing
marks. Java installs the sections, heightmaps and marks once.

Java still fills interpolation slices, cell density caches and, on request,
aquifer cell materials (including fluid-status requests), and owns the generator, the
`NoiseChunk` and every chunk object. Ineligible chunks run the unchanged Java loop.

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

All vanilla noise settings qualify.

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

```sh
python3 DevUtils/tests/worldgen/VerifyRustNoiseFill.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustNoiseFill.py --forks 3 --cpu 5 --background-cpus 0,1
```

The driver rebuilds every edited production Java file from the reference
commit with its exact audited rewrites and requires a byte-for-byte match. `NativeNoiseFillTest` fills each vanilla
noise setting at three seeds and five chunk positions through `fillFromNoise`
with both routes and compares section network bytes, saved packs, counters,
heightmaps, post-processing lists and later aquifer reads; every pair must take
the native route and produce ore and raw-ore blocks. It also compares native
aquifer centres with Java's `location()` (Xoroshiro and Legacy) and replays up
to 400-state write sequences against real `PalettedContainer` resizes. A raw-ore
chance mutation and a write-order palette mutation each fail a test.

Benchmarks time `fillFromNoise` on fresh chunks per mode in separate JVMs;
chunk and `NoiseChunk` construction are excluded equally.

## Measurements

The implementation author recorded a full release run on 2026-10-04: three
alternating JVM pairs per setting on an i7-10750H laptop (CPU 5 measured, CPUs
0/1 for JVM workers), at least 15 s of warmup and 30 samples per JVM, each
sample filling eight fresh chunks. Both routes produced identical checksums in
every JVM.

| Setting | Java loop median | Native fill median | Paired median reduction | 95% ratio interval |
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

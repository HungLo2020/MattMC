# Rust carvers stage

The CARVERS stage (`NoiseBasedChunkGenerator.applyCarvers`) runs the built-in
cave, Nether cave and canyon carvers of every chunk within 8 of the carved one.
On eligible chunks the carving itself now runs in Rust, in one call per chunk.

## How it is split

Java still gives each chunk within 8 its carvers: `carveChunk` reads every
neighbour's carver biome (`ChunkAccess.carverBiome`, cached per chunk) and
passes the configured carvers in applyCarvers' order.
[`NativeCarvers`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeCarvers.java)
resolves each carver's anchors for the chunk and encodes its providers, then
[`carver/stage/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/carver/stage)
runs the rest in one call:

- applyCarvers' loop: `setLargeFeatureSeed(seed + index, x, z)` on a Legacy
  random, `isStartChunk`, then `CaveWorldCarver.carve` (and the Nether
  carver's bound, thickness and Y scale) or `CanyonWorldCarver.carve`, with
  their uniform heights and constant, uniform or trapezoid floats;
- rooms, tunnels with their own Legacy randomness and branching, canyons, the
  `Mth` sine table (copied from Java), `canReach`, the ellipsoid scans with
  their skip checks, `carveBlock` and the Nether carver's `carveBlock`;

over:

- the chunk's [Rust-owned storage](../RUST-SURFACE-STORAGE.md#shared-chunk-storage)
  (`ProtoChunk.setBlockState` semantics, both world-generation heightmaps);
- the chunk's carving mask, as words;
- the chunk's aquifer: a noise-based aquifer's substance decision runs in
  [`aquifer/substance.rs`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/aquifer/substance.rs)
  (centres, ranking, decision, native fluid statuses and barrier noise, over
  Java's own caches); a disabled aquifer answers its fluid picker.

The only call back into Java is `CarvingContext.topMaterial`, for dirt below a
carved grass or mycelium block. It is an FFM upcall that first copies the
storage's current heightmaps into the Java chunk, because the rule may test
steep. The storage, the mask words, the aquifer's caches and its schedule flag
install once at the end.

## Gate

The chunk keeps Java's carvers when any of these fail:

- `NativeCarvers.eligible`: plain `ProtoChunk`, unblended noise chunk, a
  carving mask without a blending mask, no carver debugging, a plain
  `SurfaceSystem`, and a surface rule whose conditions read the chunk only
  through heightmaps (`NativeSurface.readsOnlyHeightmaps`: built-in rules and
  conditions);
- every configured carver in range passes `NativeCarvers.supports`: a
  built-in carver class with its own configuration class, `UniformHeight`
  Y, constant, uniform or trapezoid floats, no debug settings; and its height
  range resolves non-empty (Java logs an empty one);
- the aquifer is disabled with a plain fluid picker, or noise-based on its fully
  native route (`NativeAquifer.carverBinding`);
- the storage accepts the chunk ([`NativeProtoChunk`](../RUST-SURFACE-STORAGE.md#shared-chunk-storage)).

Java's loop reseeds its random per carver, so a fallback draws what Rust would
have. A top-material exception aborts the stage and is rethrown.
`-Dmattmc.worldgen.javaCarvers=true` keeps Java's carvers for comparisons.

## Preserve these contracts

- Keep each carver's draw order: arguments are drawn left to right as Java
  evaluates them (for example `nextInt(nextInt(nextInt(bound) + 1) + 1)`
  innermost first, and the tunnel seed after its thickness and length).
- Keep Java's float/double promotions in tunnels and canyons, `Mth.floor`'s
  saturating cast, and the ellipsoid's loop order (X, Z, descending Y), skip
  check before mask check.
- `shouldScheduleFluidUpdate` is stale after a lava-level block: the flag
  carries across the stage from the aquifer's value at its start.
- Replaceable sets use `carveBlock`'s own predicate (`blockState.is(set)`) per
  block. Named and direct sets are cached until any holder's tags are rebound
  (`Holder.TAG_GENERATION`).

## Verify and measure

```sh
python3 DevUtils/tests/worldgen/VerifyRustCarvers.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustCarvers.py --forks 3 --cpu 5 --background-cpus 0,1
```

`NativeCarversTest` prepares chunks through the production NOISE and SURFACE
stages for every bundled noise setting, using the Nether multi-noise preset
for the Nether setting and the Overworld preset for every other setting, then
runs `carveChunk` both ways. This fixture does not reproduce each dimension's
actual generator and biome-source wiring. Sections (network and saved forms, counters),
heightmaps, post-processing, later aquifer reads, the carving mask and the
aquifer's schedule flag must match. It prints the top-material upcall count
and checks selected gates; the test does not require that count to be nonzero.
The implementation author reports that mutations that reset the schedule flag at the lava level, mark
fluids regardless of it, move rooms, turn the second branch the wrong way,
change the thickness draw, the Nether lava height, the carver seed index or
the upgrade margin, skip top materials, misclassify water statuses or reorder
neighbours each fail it. See the [shared verification
limits](../RUST-WORLDGEN-ORGANIZATION.md#verification) before extending that
fixture result to retrogen, custom generators or complete-world acceptance.

## Measurements

The implementation author recorded release runs on 2026-10-05 on an i7-10750H
laptop (CPU 5 measured, CPUs 0/1 for JVM workers). The driver ran three
alternating JVM pairs per setting, at least 15 s of warmup and 30 samples per
JVM, each sample carving eight chunks prepared through NOISE and SURFACE
outside the timer; route checksums matched in every JVM, and the native JVMs
carved their chunks natively.

| Setting | Java carvers | Rust stage | Paired ratios | 95% ratio interval |
|---|---:|---:|---|---:|
| Overworld | 1.04 ms | 0.72 ms | 0.71, 0.79, 0.66 | 0.63–0.81 |
| Amplified | 0.95 ms | 0.71 ms | 0.73, 0.75, 0.73 | 0.69–0.78 |
| Nether | 0.53 ms | 0.45 ms | 0.86, 0.86, 0.76 | 0.72–0.88 |

Times are per chunk: `carveChunk` with the 17×17 carver loop, neighbour carver
biomes cached as on a server, storage creation, the boundary and the installs.
Many overworld and amplified samples saw JIT activity (the untimed preparation
between samples keeps the compiler busy); the ratios held in every pair. Not
whole-game measurements. Raw rounds and hashes were recorded under
`build/carvers-migration/` (not bundled with the wiki).

Parity at the time: 96 chunk pairs (every vanilla setting, three seeds, four
positions), all carved natively, with 721 top-material upcalls.

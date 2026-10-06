# Rust surface chunk storage

The SURFACE stage used to write every block through `ProtoChunk.setBlockState`:
a palette lookup, section counters and two heightmap updates per write, behind
a Java callback per committed block. On ordinary chunks those writes now happen
in Rust, which owns the chunk's block storage for the whole stage.

[`NativeSurfaceChunk`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeSurfaceChunk.java)
moves the chunk to [shared chunk storage](#shared-chunk-storage) when
`SurfaceSystem.buildSurface` starts, and gives
[`surface/chunk.rs`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/surface/chunk.rs):

- a quart biome table (registry IDs) covering every quart a column's biome
  corners can select, filled once from the stage's `BiomeManager`;
- native condition slots for steepness, eligible vertical gradients and noise
  thresholds, plus available band-offset, secondary-noise and preliminary-level
  inputs; unsupported inputs retain Java answers.

Rust then scans each column, selects its rule-evaluation biomes from the
table, runs the [surface evaluator](RUST-SURFACE.md), commits completed blocks
and answers the evaluator's requests itself:

- `steep` from its own heightmap, once per column (one lazy condition shared by
  every steep slot, as in Java);
- vertical gradients inside their band: `Mth.map(y, low, high, 1, 0)` against
  `nextFloat()` of the condition's positional random (Xoroshiro or Legacy) at
  the block;
- noise thresholds, the secondary surface noise and the terracotta band offset
  (`(int)Math.round(noise * 4.0)`, Java's rounding ported bit for bit) from
  the noises' native states;
- the minimum surface level from the noise chunk's native preliminary surface
  program at the surface cell's corners (kept across columns, as the context's
  cache keeps them), `Mth.lerp2`, `Mth.floor`, plus surface depth − 8.

Java still drives the X/Z column loop, updates the context and looks up each
column's biome for the extensions. It answers only temperature and extension
conditions, and any of the above whose noise, random factory or noise chunk is
not the built-in kind. The eroded badlands and frozen ocean extensions read and write
through a block column backed by the Rust storage. At the end Java installs
the modified sections (`installGenerated`), both heightmaps and the fluid
post-processing marks, in write order.

## Shared chunk storage

[`NativeProtoChunk`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeProtoChunk.java)
and [`proto_chunk/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/proto_chunk)
own a chunk's blocks while a stage runs on them (SURFACE and the
[carvers](carver/RUST-CARVERS.md)): every section's exact palette, storage and
counters (`LevelChunkSection.exportGenerated`) and the `WORLD_SURFACE_WG` and
`OCEAN_FLOOR_WG` heightmaps. A section unpacks on its first block access;
counters answer only-air checks without unpacking, so untouched sections are
never unpacked or reinstalled. Stages borrow the storage's handle; Java
installs modified sections, both heightmaps and the stage's post-processing
marks once. `NativeProtoChunk.create` returns null, keeping `setBlockState`
writes, unless:

- the chunk is a plain `ProtoChunk` whose persisted status is before
  `INITIALIZE_LIGHT` (no light updates) and whose heightmaps to update are
  exactly the primed world-generation pair;
- every section is a plain `LevelChunkSection` with a modelled palette
  (single, linear, hash map or global) and storage.

## Compiled rule programs

A batched rule program depends only on the rule, the surface system's bands,
the biome registry, whether biomes are native and the generation height range.
`NativeSurface` caches eligible validated programs per surface system (one per
`RandomState`, which also fixes the bands). Its key contains the rule and
registry identities, the native-biome flag, minimum generation Y and generation
depth; later contexts apply the cached condition sources. Concurrent first
users can still compile the same key before it is cached. Programs with extension rules or
continuations are compiled per context as before.
`-Dmattmc.worldgen.surfaceCompileEachChunk=true` compiles every context's
program, and `-Dmattmc.worldgen.javaSurfaceConditions=true` keeps Java
answering the newly moved gradient/noise/minimum-level requests, for
comparisons. Steepness remains native on Rust-owned storage with either setting.

```sh
python3 DevUtils/tests/worldgen/VerifyRustSurfaceConditions.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustSurfaceConditions.py --forks 3 --cpu 5 --background-cpus 0,1
```

`NativeSurfaceChunkTest` counts the requests Java answers by kind: on its
corpus Java's route answers thousands of gradients, noise thresholds, band
offsets, secondary noises, minimum surface levels and steep checks, and Rust
storage answers none of them in Java. `cachedRuleProgramsMatchFreshCompilation`
surfaces chunks of one `RandomState` (cached programs) against chunks compiled
fresh with Java's answers. Mutations of the gradient's orientation, the
minimum surface offset and the secondary noise's coordinates fail it; Rust's
rounding in place of Java's fails `band_offset_rounds_halves_like_java`
(`surface/chunk.rs`); `<=` in place of the gradient's `<` survives (equal only
when `nextFloat()` hits the probability exactly). These mutation outcomes are
implementation-author reports. The author measured on 2026-10-05/06 (same laptop and driver
method as below), per chunk, Java answers and per-chunk compilation versus Rust
answers and cached programs: overworld 4.08 → 3.55 ms (paired ratios 0.87,
0.89, 0.85; 95% interval 0.83–0.91), amplified 4.90 → 4.41 ms (0.89, 0.90,
0.92; 0.87–0.93), nether 3.85 → 3.53 ms (0.92, 0.93, 0.89; 0.88–0.95, its
upper bound 0.953 just above the driver's 0.95 gate). Raw rounds are under
`build/surface-conditions-migration/` (not bundled with the wiki). This combined
comparison changes both condition execution and program caching; it does not
isolate either contribution. The documentation review did not rerun these
checks or independently inspect the raw measurement artifacts.

## Gate

`NativeSurfaceChunk.create` also requires a batched rule
(`NativeSurface.canBatch`: known rules and conditions only, plain
`SurfaceSystem`, `BiomeManager` and `ProtoChunk`).

`-Dmattmc.worldgen.javaSurfaceStorage=true` keeps Java storage, for comparison runs.

## Preserve these contracts

- Writes are `ProtoChunk.setBlockState` before light: build-height check, AIR
  into an only-air section skipped, the `PalettedContainer` replay and resize
  order, section counters, then `Heightmap.update` for `WORLD_SURFACE_WG` and
  `OCEAN_FLOOR_WG`, then the block column's fluid post-processing mark.
- Reads are `ProtoChunk.getBlockState`: AIR in an only-air section (even if it
  stores cave air), VOID_AIR outside the sections. Heightmap down-scans treat
  only-air sections as air.
- `steep` is one lazy condition per context: every steep slot in a column
  shares the first answer, computed from the heightmap at that moment.
- Commit order is the evaluator's: completed blocks below a request are written
  before Java answers it.

## Verify and measure

```sh
python3 DevUtils/tests/worldgen/VerifyRustSurfaceChunk.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustSurfaceChunk.py --forks 3 --cpu 5 --background-cpus 0,1
```

`NativeSurfaceChunkTest` runs the stage with both storages over NOISE-filled
chunks for every vanilla setting with quart biomes cycling through every biome
(biome conditions, steep and both extensions), and compares section network and
saved bytes, counters, heightmaps and post-processing lists. It also compares
fingerprints of the `NativeSurfaceVerification` fixtures with the vanilla rule,
its custom rules and a rule whose two steep slots must share one answer (also
run over real overworld and amplified terrain, whose small slopes flip a
recomputed answer), and checks the gates. Rust tests in `surface/chunk.rs` cover
what vanilla writes cannot reach: AIR into a cave-air-only section, replacing a
randomly ticking block, and down-scans through only-air sections (now in
`proto_chunk/tests.rs`). Mutations
that recompute steep per request, drop fluid marks, update `OCEAN_FLOOR_WG` with
the wrong predicate, scan only-air sections as solid, swap the installed
heightmaps, remove the AIR shortcut or keep ticking counts each fail a test. The driver audits production edits against their exact
rewrites of the reference commit, runs the parity suites and Rust tests, and
benchmarks `NativeSurfaceChunkVerification` in alternating JVM pairs.

## Measurements

The implementation author recorded release runs on 2026-10-05 on an i7-10750H
laptop (CPU 5 measured, CPUs 0/1 for JVM workers). The driver ran three
alternating JVM pairs per setting, at least 15 s of warmup and 30 samples per
JVM, each sample running the stage on eight NOISE-filled chunks with the
dimension's multi-noise biomes; route checksums matched in every JVM, and the
native JVMs ran their chunks on Rust storage.

| Setting | Java storage | Rust storage | Paired ratios | 95% ratio interval |
|---|---:|---:|---|---:|
| Overworld | 5.34 ms | 3.94 ms | 0.74, 0.73, 0.75 | 0.72–0.76 |
| Amplified | 6.20 ms | 4.67 ms | 0.79, 0.75, 0.75 | 0.74–0.80 |
| Nether | 5.59 ms | 3.79 ms | 0.67, 0.72, 0.68 | 0.67–0.72 |

Times are per chunk and include creating the Rust storage (exporting sections
and heightmaps, the quart biome table), every boundary crossing and the
install. Biome lookups read a precomputed quart table, as production reads
chunk biome containers, so biome sampling cost is excluded from both routes.
Not carving, feature or whole-game measurements. Raw rounds and hashes were
recorded under `build/surface-chunk-migration/` (not bundled with the wiki).

Parity at the time: 64 NOISE-filled chunk pairs and 320 synthetic fixture
chunks (288 on Rust storage; the rest use an extension condition that mutates
the chunk and keep Java storage) matched exactly.

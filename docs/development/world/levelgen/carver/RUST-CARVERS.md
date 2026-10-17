# Rust carvers stage

The CARVERS stage (`NoiseBasedChunkGenerator.applyCarvers`) runs the built-in
cave, Nether cave and canyon carvers of every chunk within 8 of the carved one.
On eligible chunks the carving itself now runs in Rust, in one call per chunk.

## How it is split

[`NativeCarvers`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeCarvers.java)
keeps per-generator tables: the distinct carver settings of the biome source's
biomes, the union of their carvers (encoded once per chunk), and for a plain
multi-noise source each climate-tree leaf's settings. Per chunk Java passes
each neighbour's cached carver settings (`ChunkAccess.cachedCarverBiome`), or
-1 when the neighbour has not cached one yet;
[`carver/stage/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/carver/stage)
then runs the rest in one call:

- carver selection: an uncached neighbour's carver biome is sampled at
  `(QuartPos.fromBlock(minBlockX), 0, QuartPos.fromBlock(minBlockZ))` with the
  RandomState sampler's climate program and searched from the thread's previous
  leaf, interleaved with carving in applyCarvers' order (as Java's lazy
  `carverBiome` does). A fixed biome source fills every neighbour in Java;
  other biome sources keep Java's loop. Rust's choices fill the neighbours'
  caches and its previous leaf becomes the thread's afterwards;
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
- each state's block, from the [block registry](../../../game-model/RUST-BLOCK-REGISTRY.md)
  (the stage needs it installed);
- the chunk's aquifer: a noise-based aquifer's substance decision runs in
  [`aquifer/substance.rs`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/aquifer/substance.rs)
  (centres, ranking, decision, native fluid statuses and barrier noise, over
  Java's own caches); a disabled aquifer answers its fluid picker.

`CarvingContext.topMaterial`, for dirt below a carved grass or mycelium
block, also runs in Rust
([`surface/scalar.rs`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/levelgen/surface/scalar.rs),
bound per chunk by
[`NativeTopMaterial`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/levelgen/NativeTopMaterial.java)).
It evaluates the surface rule as `NativeSurface.tryApply` does on a fresh
context: surface depth from the surface noise and positional random, then
every request in Rust (band offset, secondary noise, preliminary surface,
steep on the storage's heightmap, gradients, noise thresholds and biome
membership). A temperature condition calls `coldEnoughToSnow` through Java at
the original evaluation point. That adapter reaches the [shared native temperature/cache service](../../biome/RUST-TEMPERATURE.md)
([ordering](../RUST-SURFACE.md#temperature)). The context's biome is looked up lazily, at most
once per call, as Java's memoized supplier does: the biome manager's zoom, then
the fixed biome or the climate search from the thread's previous leaf, which
the lookup moves. A rule needing Java (an extension, a non-native noise or
random, a biome without a key) binds to null and the stage keeps Java's upcall,
which copies the storage's heightmaps into the Java chunk first and carries
the previous leaf in and out.
`-Dmattmc.worldgen.javaCarverTopMaterial=true` keeps the upcall for every chunk.
The storage, the mask words, the aquifer's caches and its schedule flag
install once at the end. Canonical sections use the
[native handoff](../RUST-STAGE-HANDOFF.md), preserving isolated inputs and
modified-only installation without Java palette/word reconstruction.

## Gate

The chunk keeps Java's carvers when any of these fail:

- `NativeCarvers.eligible`: plain `ProtoChunk`, unblended noise chunk, a
  carving mask without a blending mask, no carver debugging, a plain
  `SurfaceSystem`, and a surface rule whose conditions read the chunk only
  through heightmaps (`NativeSurface.readsOnlyHeightmaps`: built-in rules and
  conditions);
- the biome source is a plain multi-noise source whose sampler compiles to a
  native climate program, or a fixed source (other sources keep Java's loop);
- every carver of the biome source's biomes passes `NativeCarvers.supports`: a
  built-in carver class with its own configuration class, `UniformHeight`
  Y, constant, uniform or trapezoid floats, no debug settings; and its height
  range resolves non-empty (Java logs an empty one);
- the aquifer is disabled with a plain fluid picker, or noise-based on its fully
  native route (`NativeAquifer.nativeBinding`);
- the storage accepts the chunk ([`NativeProtoChunk`](../RUST-SURFACE-STORAGE.md#shared-chunk-storage)).

Java's loop reseeds its random per carver, so a fallback draws what Rust would
have. A top-material exception aborts the stage and is rethrown; a failed
carver-biome sample (`-5`) throws.

The decoration statistics line reports `carvedChunks=`, `carverBiomesSampled=`
(neighbours Rust sampled), `carverTopCalls=` (Java upcalls), `carverTopNative=`,
`carverTopDeclined=` (chunks whose rule kept the upcall), `carverTopCold=`
(temperature conditions Java answered), and the verification counts below.

## Known regression: the End

The End's `TheEndBiomeSource` is neither a multi-noise nor a fixed source, so
its chunks never reach the native stage: Current runs Java's own carver loop.
Even so, the audit agent's matched `generateCarvers` timing (same workload,
serial) shows that loop at about 150–170 µs per chunk in Current against
about 46–53 µs in Frozen. The earliest saved Current baseline
(`94e748593`) was 391 µs, so the gap predates the native top material and
the FEATURES transaction. Its cause is **not established**: Java sampling
attributes Current's time mostly to `WorldGenRegion.getChunk` under
`carverBiome` and to native end-island noise, but it cannot see native frames.
This workload is not within the performance budget; it needs its own
investigation (native-frame profiles of Current and Frozen on an End-only
run).

## Verify the top material

`-Dmattmc.worldgen.verifyCarverTop=true` (or `NativeTopMaterial.setVerify`)
runs every native top material and then Java's upcall from the same storage
state and previous leaf. The stage keeps Java's answer and counts a mismatch
when the state or the leaf afterwards differs (`carverTopVerified=`,
`carverTopMismatched=`).
[`NativeTopMaterialTest`](https://github.com/HungLo2020/MattMC/blob/master/src/test/java/net/minecraft/world/level/levelgen/NativeTopMaterialTest.java)
does this over every vanilla setting, three seeds and biome-edge chunks. It
also uses a floor rule that reaches every request kind, because vanilla
rules reach only biomes and the preliminary surface from the carvers. It
checks that the native route makes no Java upcall and that disabling the
binding keeps the upcall.
The top-material cache keeps the newest eight identity keys per surface system.
Construction runs outside its publication lock. Concurrent first users recheck
the key under that lock and receive the canonical binding; an unpublished
loser's native allocation is released immediately through an idempotent
Cleaner action. Declined (null) entries follow the same identity and eviction
rules. Canonical bindings retain their existing system/in-flight lifetimes.
The tests force four simultaneous first users behind a barrier, check immediate
loser cleanup while the Java objects are still retained, repeat cleanup, and
check equal-valued distinct keys, null caching and eviction. This correction
addresses a duplicate-publication failure reproduced on unchanged `683ab835`;
it does not change carving geometry or investigate the separate End timings.
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

The historical results below predate the shared block registry. Carvers now
read state-to-block ownership from that registry; the [registry verification
record](../../../game-model/BLOCK-REGISTRY-VERIFICATION.md) reports the author's
later parity checks, but no new carver hot-path timing for that change.

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

# Rust-only DH generation

Every generated or loaded chunk becomes DH data through Rust only:

1. height fields (`NativeDhHeightmaps`);
2. block and sky lighting (`NativeDhLighting`);
3. chunk columns (`NativeDhColumns`);
4. the merge into its full-data source (see [Rust-owned DH source](RUST-DH-SOURCE.md));
5. data-source sky light (`NativeDhSkyLight`).

None of these steps has a Java fallback any more. The source
updates they feed are described in [Rust-owned DH source](RUST-DH-SOURCE.md).

- **Java entry points:**
  - [`DhLightingEngine`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/generation/DhLightingEngine.java)
    (chunk lighting, data-source sky light);
  - [`LodDataBuilder`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/transformers/LodDataBuilder.java)
    (chunk columns);
  - [`ChunkCompoundTagParser.readLightingForGeneration`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/common/wrappers/worldGeneration/chunkFileHandling/ChunkCompoundTagParser.java)
    (saved light).
- **Block facts:** [`NativeCollisionGeometry.dhSolid` / `dhOpacity`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/block/NativeCollisionGeometry.java),
  installed into Rust by `mattmc_collision_dh_install`.

## Why the Java paths ran, and what fixed them

A route survey counted every route and decline reason in a DH gameplay run on
the small DH fixture. Before these changes, the Java lighting pass handled 73%
of chunks (1,488 Java, 560 native), and the Java chunk builder ran just as often.
There were three causes:

- **Missing block facts.** Rust's DH facts (solid, opacity) came from the
  collision-shape catalog, so states without cached geometry (dynamic or
  non-canonical shapes) had none, and the height-field builder declined (2,079
  times). Java now installs facts for every state, using the same context-free
  policy that `BlockStateWrapper` uses: `NativeCollisionGeometry.dhSolid` and
  `dhOpacity` serve both.
- **Wrappers without height fields.** Chunks loaded from disk construct their
  wrapper with `recreateHeightmaps=false`, which skipped the native height
  fields too. Any neighborhood containing one declined native lighting. The
  Java pass then wrote Java light storage into all of its neighbors, which in
  turn declined every later native pass. Those wrappers now always get Rust
  height fields; `false` skips only the Java height scan.
- **Separate biome bindings.** A chunk's sections can come from different
  container factories, each with its own binding over the same biome registry.
  The column builder compared bindings by identity and declined. It now accepts
  bindings that hold the same holders in the same order (cached per pair).

After the fixes, and again after the Java paths were deleted, every chunk took
the native route: 2,495 lighting passes, 2,465 column builds and 93,065
height-field builds, with no decline.

## Rules when changing this subsystem

- **There is no Java light storage.** Wrappers read light from their Rust light
  owner (`getDhBlockLight`, `getDhSkyLight`). Before a chunk is lit, these
  return 0, except sky light above the world, which is 15. Only the Rust pass
  writes light, so `IChunkWrapper` has no setters.
- **A decline is logged once and leaves the chunk without that step.** The chunk
  stays unlit (it is still marked lit, as before, so it isn't retried) or gives
  no LOD data. With registered block states, stock wrappers and stock levels,
  nothing declines.
- **Saved chunks use DH's own heights.** A `recreateHeightmaps=false` wrapper
  used to read Minecraft's saved `WORLD_SURFACE`/`MOTION_BLOCKING` heightmaps
  (empty when the chunk wasn't primed). It now gets the same Rust height fields
  as a fresh chunk, so a saved chunk and the same chunk generated fresh give
  identical LOD data. In the generation benchmark, current saved-chunk output
  equals Frozen's fresh-chunk output for all 16 recorded chunks; Frozen's
  saved-chunk output differs on 14 of them.
- **Saved light that can't be imported natively isn't imported.** DH lights
  that chunk instead. Frozen's Java reader imported a partial copy of malformed
  data, or threw.
- **Keep block facts in one place.** Change `dhSolid`/`dhOpacity`, never one
  side alone. Rust installs exactly what Java computes; a different table is
  rejected.
- **Wrapper-replacement checks are gone.** `WRAPPER_BY_BLOCK_STATE` and
  `WRAPPER_BY_BIOME` hold only stock wrappers in this project. The checks that
  scanned every palette for replaced wrappers before each native call were
  removed.

## Removed

- **Lighting:** the Java chunk-lighting pass (queues, propagation, debug
  wireframes) and the Java sky-light bake. The bake is now the test reference
  `JavaSkyBakeReference`.
- **Chunk builder:** the Java chunk builder. It is now the test reference
  `JavaLodBuilderReference`.
- **Light storage:** `ChunkLightStorage`, the wrappers' Java light storage and
  its setters, `Field.copyToMutableStorage`, and the Java saved-light reader.
- **Switches:** the `-Dmattmc.dh.javaSkyBake` switch.

The Java height scan in `ChunkWrapper.createDhHeightMaps` remains. It is used
only for chunks the native builder can't take (debug worlds, unsupported chunk
classes), which can't be lit or converted natively either.

## Measured results (2026-10-13)

Measured on the laptop against a build of the previous commit (`d6e1e3f68`,
"baseline").

**CPU, per-chunk generation** (`VerifyDhSource.py --mode generation`, 16
recorded chunks × 20 rounds, six alternating pairs, medians):

| Wrappers | Baseline ms | Current ms | Current / baseline | Output |
| --- | --- | --- | --- | --- |
| Fresh (`--recreate-heightmaps`) | 763.7 | 716.7 | 0.94 | equal |
| Saved | 1,002.2 | 712.2 | 0.71 | intentionally changed |

**Gameplay** (small DH fixture, moving camera, 6,000 frames, five runs per
side). Current had a median of 95 FPS and 8.26 ms frames; baseline had 99 FPS
and 7.57 ms. p99 was lower on current (42.2 ms against 44.6 ms). This is not a
like-for-like comparison:

- By measurement, current showed all 41 visible DH columns in every run, while
  baseline showed 27–38. The extra LOD terrain is visible in GPU DH draw time
  (about +55 µs per frame).
- Render-thread CPU work was lower on current (whole-frame native total
  7.46 ms against 7.62 ms).
- The laptop swapped 1.3–3.9 GB in every run, and GC took 23–33 s per run.

An attempt to measure both sides only after DH goes quiet (`settled-static`)
didn't fit the harness's 300-second per-mode limit on this machine.

## Tests

```bash
./gradlew -PmattmcRustProfile=release test --tests 'net.minecraft.world.level.levelgen.NativeDh*' --tests '*Heightmap*'
```

- `NativeDhLightingTest`: the recorded Frozen cases, including all ten
  clear/edit phases that previously switched to Java storage, now run natively.
- `NativeDhSkyLightTest` and the saved-light column test compare against the
  Java reference copies.
- `NativeDhHeightmapsTest` checks that states without cached geometry get the
  shared facts.

The generation benchmark times the per-chunk path (wrapper, lighting, columns)
against Frozen:

```bash
python3 DevUtils/tests/world/chunk/VerifyDhSource.py --mode generation --recreate-heightmaps ...
python3 DevUtils/tests/world/chunk/VerifyDhSource.py --mode generation --per-side-checksums ...
```

`--recreate-heightmaps` times fresh chunks; both builds must give equal output.
Without it, the benchmark times saved chunks, whose output intentionally changed
(see above), so `--per-side-checksums` only requires each side to be
consistent.

To repeat the route survey, temporarily count the decline sites and run one
gameplay pass, as in the phase-2 survey (`phase2-survey*` under
`artifacts/performance/realworld-20261011/`).

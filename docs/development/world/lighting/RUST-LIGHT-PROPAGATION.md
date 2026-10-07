# Rust light propagation

[`propagation/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/lighting/propagation)
runs the decrease and increase queues of `LightEngine.runLightUpdates()` for
`BlockLightEngine` and `SkyLightEngine`. It also runs the per-section loop of
`SkyLightEngine.propagateLightSources()`, which fills skylight above the
lowest sources and seeds the increase queue. The bridge is
`NativeLightPropagation` (lighting package) with `NativeLightBlocks` (chunk
package, for palette data). Java still owns everything else: section statuses,
`checkNode`, queued and retained data, `markNewInconsistencies`,
`swapSectionMap` and light storage.

## How a pass works

1. Java drains both queues into native memory. Rust replays the original
   loops in the same order: every decrease, then every increase.
2. One callback function supplies two separately cached snapshots per section:
   the updating `DataLayer` (bytes, or the default value of a lazy layer) and
   `lightOnInSection` on the first layer request, then block states only when
   first needed. The block snapshot contains palette state IDs plus packed
   words, or one state ID per block for chunk kinds the bridge does not model;
   Rust maps state IDs to light types in place. A section needing both
   snapshots makes two callback invocations.
3. Rust writes levels into its own copies. At the end Java installs each
   written section with the original copy-on-write: the first write in a pass
   copies the layer. Java then adds the `sectionsAffectedByLightUpdates`
   entries.
4. Unsupported input or a failed section callback returns before propagation
   writes are installed. Java restores both drained queues in their original
   order and runs the original loops, which may complete or throw. This is a
   boundary around propagation writes, not a rollback of earlier `checkNode`
   work or callback side effects. Invalid ABI input (`-1`), downcall failures
   and result-transfer failures throw instead of replaying in Java; see the
   [bridge status handling](https://github.com/HungLo2020/MattMC/blob/5c02fd8215f4c1dde624dbe3d21a476d38b16708/src/main/java/net/minecraft/world/level/lighting/NativeLightPropagation.java#L274-L325).

Sky seeding runs one critical downcall per section. It needs no allocations
or callbacks. Java keeps the section iteration, `getDataLayerToWrite` and the
`enqueueIncrease` calls, in the original order. A rejected sky-seeding call
throws; it does not use the propagation pass's Java replay path.

## Constraints when changing this code

- Keep FIFO order, the returned work count, entry bits
  (`LightEngine.QueueEntry`) and every `setStoredLevel`, including writes of an
  unchanged value. A lazy layer must allocate exactly when the original would.
- Block properties are light types that Rust derives from the
  [block registry](../../game-model/RUST-BLOCK-REGISTRY.md):
  `max(1, getLightBlock())`, emission, `isEmptyShape` and each direction's
  occlusion face, numbered in state order. Faces are merged only by exact box
  lists. Occlusion is a truth table of `Shapes.faceShapeOccludes`, so never
  approximate geometry.
- Only exact `BlockLightEngine`/`SkyLightEngine` with their vanilla storages
  use Rust queue propagation. `ProtoChunk`, `LevelChunk` and `ImposterProtoChunk` (non-debug)
  sections are read packed. Other `LightChunk`s answer per block through
  `getBlockState`. A null chunk reads as bedrock and blocks outside the build
  height read as air, as in `LightEngine.getState`.
- Rust keeps no pointers into the propagation pass's Java inputs between calls.
  Each engine has its own Rust handle and
  callback buffer, and the engine's thread owns the pass.
- `-Dmattmc.lighting.javaPropagation=true` keeps the Java loops.
  `NativeLightPropagation.setEnabled` toggles this in tests.

## Verify a change

```sh
python3 DevUtils/tests/lighting/VerifyRustLightPropagation.py --parity-only
python3 DevUtils/tests/lighting/VerifyRustLightPropagation.py --forks 3
```

The driver audits the edits to `LightEngine`, `SkyLightEngine` and `DataLayer`
against Git `54611cfc2`. It then runs `NativeLightPropagationTest` and the Rust
lighting tests. The parity tests light worlds on both routes over the same
chunks and compare all light storage after every pass:

- updating, visible and queued layers, including whether each layer is lazy or
  allocated and which layers are shared between the two maps
- section states, changed and affected sections, sky top sections
- light-update notification membership (sets, not order or duplicate counts)
  and work counts

Worlds:

- block states from 36 saved FULL overworld chunks
  (`src/test/resources/lighting/terrain.json.gz`, extracted by
  `DevUtils/tests/lighting/ExtractLightTerrainCorpus.py`),
  [reconstructed as `ProtoChunk` fixtures](https://github.com/HungLo2020/MattMC/blob/5c02fd8215f4c1dde624dbe3d21a476d38b16708/src/test/java/net/minecraft/world/level/lighting/LightPropagationFixtures.java#L48-L65)
  and relit; the corpus contains no saved light and does not test a region-file
  save/load roundtrip
- noise-filled overworld, amplified, nether and End terrain (sky light over islands above the void)
- sparse empty chunks with floating blocks and pillars, so skylight fills
  unstored sections below stored ones across every chunk border
- chunks served through a generic `LightChunk`
- world limits with missing neighbours

Edits cover torches, glowstone, lava, slabs, stairs, shafts, roofs, emptied
sections, queued section data and chunks with light switched off. Every pass
also checks that layers published before it are unchanged (copy-on-write). Another test checks every registered state
against its table type, and checks every merged face against every other face.

These fixtures exercise the light engines over saved/generated blocks, not a
live loaded-`LevelChunk`/`ImposterProtoChunk` lifecycle or full-server world
generation. They do not establish concurrent/reentrant pass or handle-cleanup
coverage. The benchmark times production calls only. Terrain loading and engine
construction are untimed.

| Case | Workload |
|---|---|
| `terrain` | Initial lighting of the saved corpus in shuffled batches |
| `edits` | 96 self-reverting edits on that lit terrain |
| `nether` | Initial block lighting of 36 noise-filled nether chunks |

Three independent JVM pairs alternate the route order on the same CPUs. Each
case must save at least 5% in every pair and at the upper 95% bootstrap
bound. Results are written to `build/light-propagation-migration/acceptance/`.
Inspect `results.json` → `performance` → each case's `passes` value: the
[driver records a failed performance gate without failing its process](https://github.com/HungLo2020/MattMC/blob/5c02fd8215f4c1dde624dbe3d21a476d38b16708/DevUtils/tests/lighting/VerifyRustLightPropagation.py#L163-L186),
so exit success alone is not performance acceptance.

## Status

The results below are historical: they measured the original slice, when Java
built the light types. The move to the block registry was checked against the
slice's previous code by
[block registry verification](../../game-model/BLOCK-REGISTRY-VERIFICATION.md).

The implementation author recorded release acceptance on 2026-10-06:
Ryzen 5 5600G, Linux x86_64, OpenJDK 25. The author reported eight Java tests
and eleven Rust lighting tests passing with zero differences (1,801 Rust
passes compared). Four of those Rust tests are newly added propagation tests;
eleven is the broader lighting-suite count. The author also reported that
thirteen deliberate semantic mutations were all caught. Each mutation broke one
of: entry bits, comparisons, edge crossings in
each direction, the lowest data section, affected sections, `lightOnInSection`,
occlusion direction, copy-on-write, lazy-layer allocation or seeding.

Median time per workload over three JVM pairs. The last column is the
conservative saving at the upper 95% bound of the time ratio:

| Case | Java | Rust including boundary | Time saved | Conservative saving |
|---|---:|---:|---:|---:|
| `terrain` | 60.1 ms | 38.3 ms | 36.2% | 33.6% |
| `edits` | 32.8 ms | 17.6 ms | 46.3% | 45.1% |
| `nether` | 23.7 ms | 13.5 ms | 43.0% | 32.2% |

Every pair passed: the smallest individual-pair saving was 33.1% (nether).
A few measured rounds overlapped JIT compilation (10, 5 and 2 of the 180
samples per case); medians and bootstrap bounds include them. These are
warmed subsystem timings on this machine and corpus, not whole-game frame or
chunk-generation claims. Finite parity tests cannot enumerate every input.
Raw samples, checksums and source/library hashes are in
`build/light-propagation-migration/acceptance/results.json` (not bundled with
the wiki). The [2026-10-06 maintenance review for #776](https://github.com/HungLo2020/MattMC/issues/776#issuecomment-6025144528)
inspected source and committed fixtures;
it did not rerun the Java/Rust suites, mutations, benchmarks or a live world,
or independently verify those raw acceptance results.

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
2. The first time a pass touches a section, Rust calls back into Java once.
   The callback returns the section's updating `DataLayer` (bytes, or the
   default value of a lazy layer) and `lightOnInSection`. It returns block
   states only when the pass first reads one: palette types plus packed words,
   or one type per block for chunk kinds the bridge does not model.
3. Rust writes levels into its own copies. At the end Java installs each
   written section with the original copy-on-write: the first write in a pass
   copies the layer. Java then adds the `sectionsAffectedByLightUpdates`
   entries.
4. If Rust rejects a pass, nothing in Java has changed, so Java refills the
   queues and runs the original loops. Rust rejects input the original would
   throw on (reading an unstored section, missing palette entries, unknown
   states) and any failed callback. Java then reproduces the original
   exception.

Sky seeding runs one critical downcall per section. It needs no allocations
or callbacks. Java keeps the section iteration, `getDataLayerToWrite` and the
`enqueueIncrease` calls, in the original order.

## Constraints when changing this code

- Keep FIFO order, the returned work count, entry bits
  (`LightEngine.QueueEntry`) and every `setStoredLevel`, including writes of an
  unchanged value. A lazy layer must allocate exactly when the original would.
- Block properties come from immutable tables built from the original methods:
  `max(1, getLightBlock())`, emission, `isEmptyShape` and each direction's
  occlusion face. Faces are merged only by exact box lists. Occlusion is a
  truth table of `Shapes.faceShapeOccludes`, so never approximate geometry.
- Only exact `BlockLightEngine`/`SkyLightEngine` with their vanilla storages
  use Rust. `ProtoChunk`, `LevelChunk` and `ImposterProtoChunk` (non-debug)
  sections are read packed. Other `LightChunk`s answer per block through
  `getBlockState`. A null chunk reads as bedrock and blocks outside the build
  height read as air, as in `LightEngine.getState`.
- Rust keeps no pointers between calls. Each engine has its own Rust handle and
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
- light-update notifications and work counts

Worlds:

- 36 saved FULL overworld chunks (`src/test/resources/lighting/terrain.json.gz`,
  extracted by `DevUtils/tests/lighting/ExtractLightTerrainCorpus.py`)
- noise-filled overworld, amplified, nether and End terrain (sky light over islands above the void)
- sparse empty chunks with floating blocks and pillars, so skylight fills
  unstored sections below stored ones across every chunk border
- chunks served through a generic `LightChunk`
- world limits with missing neighbours

Edits cover torches, glowstone, lava, slabs, stairs, shafts, roofs, emptied
sections, queued section data and chunks with light switched off. Every pass
also checks that layers published before it are unchanged (copy-on-write). Another test checks every registered state
against its table type, and checks every merged face against every other face.

The benchmark times production calls only. Terrain loading and engine
construction are untimed.

| Case | Workload |
|---|---|
| `terrain` | Initial lighting of the saved corpus in shuffled batches |
| `edits` | 96 self-reverting edits on that lit terrain |
| `nether` | Initial block lighting of 36 noise-filled nether chunks |

Three independent JVM pairs alternate the route order on the same CPUs. Each
case must save at least 5% in every pair and at the upper 95% bootstrap
bound. Results are written to `build/light-propagation-migration/acceptance/`.

## Status

Release acceptance passed on 2026-10-06: Ryzen 5 5600G, Linux x86_64, OpenJDK 25.
Eight Java tests and eleven Rust lighting tests passed with zero differences
(1,801 Rust passes compared). Thirteen deliberate semantic mutations were all
caught. Each mutation broke one of: entry bits, comparisons, edge crossings in
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
`build/light-propagation-migration/acceptance/results.json`.

# FULL-chunk generation verification

[`VerifyFullGeneration.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/worldgen/VerifyFullGeneration.py)
runs dedicated servers from Frozen and Current with a small audit agent
([`full_generation/`](https://github.com/HungLo2020/MattMC/tree/master/DevUtils/tests/worldgen/full_generation)),
generates fixed regions to FULL in every dimension, and compares them. Use it
for any change to world generation's stages, features, structures or chunk
publication.

## What it compares

Parity runs request chunks one at a time on one worker thread
(`max.bg.threads=1`). Each FULL chunk is fingerprinted from its serialized save
form (`SerializableChunkData.copyOf(...).write()`): every section's block
states and palette order, biomes and light, heightmaps, block entities,
structure starts/references, block/fluid ticks, post-processing and status.
`LastUpdate` is ignored. Generation entities (type, position, rotation; no
UUID) are compared too. `chunks.txt.gz` holds the canonical dump of every chunk.

## Commands

All sides use the bundled JDK in `libraries/jdk/linux-x64`. Outputs must be
under `build/`.

```sh
# Frozen vs Current, all dimensions, two 81-chunk regions each
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --rounds 2
# Candidate vs a retained pre-change run
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> \
  --sides current --compare-to build/full-generation/<before>/current-0
# Repeatability controls
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --sides frozen,frozen
# Interrupted generation: carve, save, unload, then finish to FULL from disk
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --resume \
  --sides current --compare-to build/full-generation/<before>/current-0
# Parallel timing: Current, the same build with native decoration off, and Frozen
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --timing \
  --sides current,current-java,frozen --pairs 3 --radius 6 --rounds 3 --warmups 2
# Which blocks differ
python3 DevUtils/tests/worldgen/full_generation/diff_chunks.py A/chunks.txt.gz B/chunks.txt.gz \
  --dimension minecraft:overworld --label measure-1
```

`--skip-build` reuses existing classes; never edit `src/` while a run is in
flight. Timing runs report per-region wall time, process CPU and synchronous
time per `ChunkStatusTasks` step. Asynchronous NOISE work is not in those step
totals.

## Known differences (2026-10-13)

- **Frozen is not fully repeatable.** Two Frozen runs differed in 13
  overworld chunks near the origin and 3 chunks of a moss patch elsewhere.
  Treat a Current/Frozen difference as real only where Frozen agrees with itself.
- **The origin region is not a strict oracle on either side.** Its spawn
  chunks generate during startup. In 1 of 9 Current runs, 9 trees near the
  region's west edge differed; immediate reruns of that build, with native
  decoration on and off, matched the baseline. Repeat a run before treating an
  origin-region difference as a regression; the other regions repeated exactly.
- **Primordial Caves** differs from Frozen in every chunk: commit `62becada6`
  made custom ore targets resolve their bundled host tags, so Current places
  ores Frozen never placed. Compare Primordial Caves against pre-change Current.
- End, Nether and the overworld otherwise matched Frozen exactly.
- **`--resume` is not repeatable, even on Java's route.** Restored chunks
  prime their WG heightmaps lazily and neighbours decorate in a different
  order, so ore blobs overlapping chunk borders resolve differently between
  runs. Use it with the decoration shadow mode, not as an exact oracle.

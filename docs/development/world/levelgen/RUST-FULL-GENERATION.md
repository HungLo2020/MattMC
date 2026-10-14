# FULL-chunk generation verification

[`VerifyFullGeneration.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/worldgen/VerifyFullGeneration.py)
runs dedicated servers from Frozen and Current with a small audit agent
([`full_generation/`](https://github.com/HungLo2020/MattMC/tree/master/DevUtils/tests/worldgen/full_generation)),
generates fixed regions to FULL in every dimension, and compares them. Use it
for any change to world generation's stages, features, structures or chunk
publication.

## Regions

Each dimension measures regions centred at chunk (64·(i+1), −48·(i+1)), away
from spawn. Startup prepares spawn chunks in parallel and unloads them; a
region overlapping them resumes from disk and is not repeatable on either side.

`--biomes a,b,…` adds a region around the nearest of each biome (searched from
block 4096, 4096). Regions are kept 2·radius + 24 chunks apart, so the
candidate depends on the earlier biomes: always pass the same list to runs you
compare. The overworld suite used for decoration is warm and lukewarm ocean,
deep dark, lush and dripstone caves, mushroom fields, jungle, bamboo jungle,
badlands, ice spikes, frozen ocean, dark forest, mangrove swamp, cherry grove,
snowy taiga, desert and pale garden (`--rounds 0 --radius 3`).

## What it compares

Parity runs request chunks one at a time on one worker thread
(`max.bg.threads=1`). First every chunk of the region and a one-chunk margin is
decorated to FEATURES in row order, then each region chunk goes to FULL. A
chunk's blocks come from its own and its neighbours' decorations, so this fixes
their order; without it, which neighbour decorates first depends on scheduler
timing and border chunks (post-processing lists, overlapping plants) differ
now and then on both routes. `-Daudit.orderedFeatures=false` restores the
unordered requests.

Each FULL chunk is fingerprinted from its serialized save form
(`SerializableChunkData.copyOf(...).write()`): every section's block states and
palette order, biomes and light, heightmaps, block entities, structure
starts/references, block/fluid ticks, post-processing and status. `LastUpdate`
is ignored. Generation entities (type, position, rotation; no UUID) are
compared too. `chunks.txt.gz` holds the canonical dump of every chunk.

## Commands

All sides use the bundled JDK in `libraries/jdk/linux-x64`. Outputs must be
under `build/`. Only one driver runs at a time: a second waits for
`build/full-generation/.driver.lock`, and each server launch waits until
`--heap` (default 8G) plus 4 GiB is available. Do not build (cargo or Gradle)
while a run is in flight.

```sh
# Frozen vs Current, all dimensions, two 81-chunk regions each
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --rounds 2
# Candidate vs a retained pre-change run (the same audit options)
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> \
  --sides current --compare-to build/full-generation/<before>/reference-0
# Repeatability controls
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --sides frozen,frozen
# Another checkout, such as the commit before a change, built under build/
git worktree add --detach build/reference-<commit> <commit>   # link libraries/jdk, then build it
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> \
  --sides reference,current --reference-root build/reference-<commit>
# Biome-targeted regions
python3 DevUtils/tests/worldgen/VerifyFullGeneration.py --output build/full-generation/<label> --rounds 0 --radius 3 \
  --dimensions minecraft:overworld --biomes minecraft:warm_ocean,minecraft:deep_dark,...
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

`--skip-build` reuses existing classes. Timing runs report per-region wall
time, process CPU and synchronous time per `ChunkStatusTasks` step.
Asynchronous NOISE work is not in those step totals.

## Reading the result safely

Inspect `comparison.json` and every requested region rather than treating exit
status zero as a parity verdict: the driver records and prints differences but
does not exit nonzero merely because `identical` is false. Timing summaries
also have no performance-threshold exit gate. Non-timing comparison uses the
first launch per side; additional pairs are not automatically folded into a
repeatability verdict. Preserve each attempt when investigating variance.

The driver checks Frozen HEAD and selected source cleanliness before running,
and records Current HEAD/dirty state and a selected native-file hash initially.
It does not finally reseal source/native/class images or verify the library
mapped by each process. Record those controls separately when required by the
[acceptance requirements](../../RUST-MIGRATION.md); `--skip-build` is not proof
that compiled classes match source. No runtime replay was performed by this
documentation review.

## Interrupted and failed generation

`--resume` carves each region with a two-chunk margin, saves and unloads it,
then finishes it to FULL from disk. With the ordered pre-pass it repeats
exactly on both routes, so compare it between sides (not with straight runs:
restored chunks prime WG heightmaps lazily, as vanilla does).

[`VerifyGenerationFailure.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/worldgen/VerifyGenerationFailure.py)
injects a decoration failure and checks nothing past it is published. On the
native route the Nth native block write fails mid-placement; on Java's route
(`javaDecoration`) the Nth `WorldGenRegion.setBlock` throws. `--at-chunk K`
arms it just before the Kth chunk of the ordered pass, so both routes fail in
the same chunk. The audit then records in-memory chunk statuses, saves without
flushing (a failed task keeps the chunk map busy, so `stopServer` never saves),
and halts; a second launch reads every persisted status around the region.

```sh
python3 DevUtils/tests/worldgen/VerifyGenerationFailure.py --output build/generation-failure/<label> --writes 50 --at-chunk 30
```

It accepts when both routes raise the "Feature placement" crash for the same
decorating chunk and that chunk is neither held nor persisted at FEATURES or
later.

## Known differences (2026-10-14)

- Off spawn, Frozen and Current each repeat exactly (648 chunks, all four
  dimensions) and match each other in the overworld, End and Nether.
- **Primordial Caves** differs from Frozen in every chunk: commit `62becada6`
  made custom ore targets resolve their bundled host tags, so Current places
  ores Frozen never placed. Compare Primordial Caves against pre-change Current.
- **Spawn regions are not repeatable on either side**: their spawn chunks
  generate during startup. Two Frozen runs differed in 13 origin chunks (and 3
  chunks of a moss patch elsewhere), and Current varied in 2 of about 12 runs
  (once in 9 trees near the region's west edge; immediate reruns with native
  decoration on and off matched). Repeat a run before treating an
  origin-region difference as a regression.
- **Unordered serial runs flake rarely on both routes** (one region in about
  ten, a few border chunks): neighbour decoration order follows the scheduler.
  The ordered pre-pass removes this; compare runs made with the same setting.
- **End-city sentry shulkers** get an unseeded random yaw on both routes:
  two reference runs differ in those entities' rotation.
- **Jigsaw piece bounding boxes in saved structure starts** depend on whether
  the start's chunk was unloaded and reloaded during the run:
  `PoolElementStructurePiece` recomputes its box from the template on load,
  dropping the expansion-hack height. Under load (two servers at once, or the
  full structure list) a village start occasionally flips; blocks and every
  other field match. Classify such a diff by comparing starts with piece boxes
  removed before calling it a regression.
- **Unordered `--resume` runs are not repeatable, even on Java's route**:
  neighbours decorate in a different order. With the ordered pre-pass they are.

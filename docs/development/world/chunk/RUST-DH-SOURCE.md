# Rust-owned DH full-data source

A DH full-data source (`FullDataSourceV2`, one 64×64-column section) is a
handle over a Rust source table. Rust owns all of the source's data and every
update. The source has no Java fallback routes and no addon compatibility paths.

- **Rust:** [`dh_columns/source.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/source.rs)
  (table, footprints, hashes),
  [`parent.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/parent.rs)
  (parent update and downsample),
  [`same_detail.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/same_detail.rs),
  [`reduction.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/reduction.rs),
  [`occlusion_pass.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/occlusion_pass.rs).
- **Java:** [`NativeDhSource`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhSource.java),
  [`FullDataSourceV2`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/fullData/sources/FullDataSourceV2.java).

## What Rust owns

| Data | Java sees |
| --- | --- |
| Columns (points) | `NativeDhColumnList` views, created on demand |
| Generation and compression planes | Fixed-size `ByteList` views (`columnGenerationSteps`, `columnWorldCompressionMode`) |
| Uniformity plane | `hasSemanticHorizontalUniformity` / `setSemanticHorizontalUniformity` |
| Semantic footprints (up to 1,024 cells, 256 points per column) | Read-only views, plus a presence plane read without a downcall |

Java keeps the material mapping (`FullDataPointIdMap`), the `isEmpty`,
`applyToParent` and `applyToChildren` flags, and the timestamps.

## Updates

Each of these runs as one Rust call per source update:

| Operation | Java entry | Rust |
| --- | --- | --- |
| Same-detail merge | `updateFromDataSource` | `mattmc_dh_same_detail_source_owned`, then commit, metadata/hash and footprint publication |
| Generated chunk merge | `updateFromNativeColumns` | `mattmc_dh_same_detail_columns` |
| Child to parent (2×2 reduction) | `updateFromDataSource` | `mattmc_dh_source_update_parent` |
| Parent to child (downsample) | `updateFromDataSource` | `mattmc_dh_source_downsample` |
| Occlusion culling | `finishUpdate` | `mattmc_dh_source_cull_readers` |
| Base-data hash | `hashCode` | `mattmc_dh_source_hash_full` |
| Saved footprint sidecar | DTO load | `mattmc_dh_source_footprints_decode` |

Material facts (`NativeDhMaterials`) are always available. They come from Rust's
block catalog for registered block states, or otherwise from each mapping
entry's wrapper (`getOpacity`, `isAir`, `isLiquid`).

## Rules when changing this subsystem

- **Every bound column is native and owned by one cell.** `setColumn` binds a
  `NativeDhColumnList` directly. It binds any other list, or a list already
  bound to a cell, as a native copy. No source contains Java-held lists or
  aliased cells, so Rust never declines an update for those reasons.
- **Metadata planes have exactly one value per column.** Structural edits
  (`size`, `add`, `remove`, `clear`) throw `UnsupportedOperationException`.
  Use `set`, `setElements` or `NativeDhMetadataList.setAll`.
- **Corrupt input is an error, not a hand-off.** These raise exceptions instead
  of falling back to a Java route:
  - a point ID outside the remap or the mapping;
  - an invalid (negative) logical column size in an input;
  - a column bound to two cells (the culler reports this).

  A rejected same-detail merge changes nothing. The parent update and downsample
  keep the cells written before the failing one, as Java's loops did.
- **Parent and downsample results replace columns.** Escaped readers of a
  replaced column keep their old lease; Java drops the binding (`unbind`).
- **Footprint views are cached per source.** `NativeDhSource.footprint` caches
  the views it creates. Every operation that can change footprints calls
  `footprintsChanged()`. Add that call to any new footprint mutation.
- **Closed sources are hashed in Java.** A reader that escaped before close can
  still write, and its writes don't reach the Rust closed projection, so the
  closed hash folds over the readers.

## Behavior that differs from Frozen

- **Downsampling remaps once and leaves the input unchanged.** Frozen bound the
  parent's column object into four child cells and remapped it in place for
  each one, so it remapped up to four times and rewrote the parent's data.
  Each child cell now gets its own once-remapped copy.
- **The parent reducer never skips a point.** Java's release-build `encode`
  never validates, so it masks height and bottom to 12 bits. Rust does the same.
  The old Rust reducer declined those cases to Java instead.
- **A missing input column in a downsample becomes an empty column.** Frozen
  threw a `NullPointerException`.

## Removed compatibility paths

None of these were used in this project. DH API generators returning API chunks
or data sources, the chunk-processing event, the `IDhApiFullDataSource`
data-point API, the Java reducer, culler, same-detail loop and hash fallbacks,
the Java-staged merge and parent routes (`NativeDhParentColumns`), and resizable
metadata are gone. The DH API interface files remain as definitions.

## Tests

```bash
(cd src/main/rust && cargo test --locked --profile suite --lib dh_columns)
./gradlew -PmattmcRustProfile=release test --tests 'net.minecraft.world.level.levelgen.NativeDh*' \
  --tests '*DistantHorizonsLodWaterReductionTest' --tests '*FullDataSourceV2*' --tests 'net.minecraft.world.level.chunk.NativeDh*'
```

The production routes replay the recorded Frozen corpora:
- 48 same-detail cases (both passes, hashes);
- 32,768 parent reductions;
- 32,768 parent metadata pairs;
- both occlusion passes over 32 saved cases.

A reference copy of Frozen's uniformity predicate is kept in the contributors
test.

## Measured results (2026-10-12)

These were measured on the laptop against a build of the previous commit
(`de7d3572f`, "baseline").

CPU timing ran through
[`VerifyDhSource.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/VerifyDhSource.py)
in its baseline-versus-current form: `--frozen-classpath-file` and
`--frozen-native-dir` point at the baseline. Each mode used six alternating
pairs, one shared compiled driver and matching checksums. Values are medians:

| Mode | Baseline ms | Current ms | Current / baseline |
| --- | --- | --- | --- |
| Same-detail merge (1,000) | 108.4 | 103.6 | 0.96 |
| Merge with culling (300) | 135.0 | 130.9 | 0.97 |
| Child-to-parent update (300) | 241.4 | 86.5 | 0.36 |
| Downsample into a fresh child (300) | 4,828 | 575 | 0.12 |
| Create and close (1,000) | 15.9 | 16.6 | 1.04, within run spread |

Gameplay ran as series `dhab2`: the small DH fixture, moving camera, shaders
off, with baseline and current runs interleaved. The machine swapped, so treat
the timings as indicative:

| Build | Peak memory (RSS + swap) | FPS | p99 |
| --- | --- | --- | --- |
| Baseline | 11.3, 12.3 GB | 112, 122 | 39.9, 42.5 ms |
| Current | 11.4, 11.0 GB | 149, 137 | 28.4, 32.8 ms |


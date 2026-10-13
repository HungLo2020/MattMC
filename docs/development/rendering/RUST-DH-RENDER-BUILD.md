# Rust-only DH render build

The ordinary DH render path moves source conversion, section construction and
packet encoding through three Rust stages. Diagnostic sidecar and probe paths
still contain Java loops, as described below:

1. **Conversion**, once per full-data source (the section and its four
   neighbors): `mattmc_dh_render_convert_source` in
   [`render/dh_columns/conversion.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/dh_columns/conversion.rs)
   walks all 4,096 columns, reads the Rust full-data source directly and
   writes the Rust render source. Columns taller than the render source convert
   into a scratch window and are reduced in Rust.
2. **Section build**: `mattmc_dh_section_build` in
   [`render/dh_quads/section.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/dh_quads/section.rs)
   walks every column and point, selects neighbor columns, applies debug colors
   and the saturation/brightness adjustment, resolves semantic provenance and
   produces every box, then the greedy merge runs.
3. **Packets**: the four packet layers are encoded into a native column and
   published to the collector ledger (unchanged; see
   [Native DH column handoff](RUST-DH-COLUMN-HANDOFF.md)).

Java entry points:
[`FullDataToRenderDataTransformer`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/transformers/FullDataToRenderDataTransformer.java)
(conversion settings and the semantic column hook),
[`RenderColumnConversionContext`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/transformers/RenderColumnConversionContext.java)
(contextual queries) and
[`ColumnRenderBufferBuilder`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/ColumnRenderBufferBuilder.java)
(per-build settings).

Source reviewed at [`c357b63c`](https://github.com/HungLo2020/MattMC/commit/c357b63cd5afcc0951344c33ad2f4568d5c77e4f). The measurements below are implementation-author reports;
this documentation reconciliation inspected source and test assertions without
rerunning suites, benchmarks or clients. The earlier
[particle runtime matrix](RUST-PARTICLE-FRAMES.md#runtime-evidence) predates this
section-build replacement and does not verify the integrated source.

## What Java still does

- **Contextual queries during conversion.** Rust calls back for a block's
  facts the first time a mapping id appears in a source, and for colors it
  cannot resolve itself. Untinted colors are cached per id. Tinted blocks
  (grass, leaves, fluids) are colored in Rust as well; see
  [Tinted colors](#tinted-colors). Java sees a column only once it queries:
  `COLUMN_BEGIN` sets the column's world position first.
- **Semantic diagnostic builds** (`exactMaterialTopology`, capture, or
  `forceSemanticPreservation`) intern block/biome identity strings in Java, so
  each converted column also gets `COLUMN_BEGIN`/`COLUMN_END` hooks that record
  its material spans, uniformity and contributor footprints
  (`FullDataToRenderDataTransformer.finishSemanticColumn`). The section build
  receives those sparse sidecars as two flat tables and handles them in Rust.
- **Settings and lifecycle.** Java reads the configuration once per source or
  section, owns the asynchronous scheduling in `LodRenderSection`, and supplies
  grass dirt colors and shades for the packet layers.

Debug source-flag colors and capture-only water probes also retain Java scans.
Semantic span/contributor construction can walk points in Java; the removal of
the ordinary build loops is not a claim that every diagnostic or contextual
callback has migrated. These paths are visible in
[`ColumnRenderBufferBuilder`](https://github.com/HungLo2020/MattMC/blob/c357b63cd5afcc0951344c33ad2f4568d5c77e4f/src/main/java/com/seibel/distanthorizons/core/dataObjects/render/bufferBuilding/ColumnRenderBufferBuilder.java)
and [`finishSemanticColumn`](https://github.com/HungLo2020/MattMC/blob/c357b63cd5afcc0951344c33ad2f4568d5c77e4f/src/main/java/com/seibel/distanthorizons/core/dataObjects/transformers/FullDataToRenderDataTransformer.java).

## Tinted colors

`ClientBlockStateColorCache.getColor` colors a tinted block as its base color
times a biome tint. The tint is the cached tint for the point's biome or, with
biome blending, the average over the neighboring columns' cached tints. Render
conversion does exactly that in Rust
([`TintContext`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/dh_columns/conversion.rs)):

- Java reports each mapping id's route once per color generation (`TINT`):
  native, with the base color and the block's tint cache and biome identity, or
  Java, for untinted, broken or level-dependent states. Unresolved colors are
  asked again later.
- Rust reads the cached tint, or blends neighbors over the Rust full-data source
  ([`tint::source_blend`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/tint.rs)),
  and multiplies. A miss (no cached tint, Frozen's invalid sentinel, or input
  outside the native route) asks Java for that point's color. Java then runs
  Minecraft's color resolver, which fills the cache, so later points stay native.
- Anything that changes a state's route bumps the color generation
  (`NativeDhConversionScope.colorsChanged`): color-cache clears, and states
  joining `ClientBlockStateColorCache`'s broken or needs-level sets.
- Tests set `RenderColumnConversionContext.nativeTint` to false to compare with
  Java's per-point colors.

## Rules when changing this subsystem

- **One implementation.** There is no Java box geometry, quad list, greedy
  merge, packet encoder, reducer or column traversal in production. The old
  Java versions survive only as test oracles (see Tests). Change the Rust code.
- **Keep the section parameter block in step.** `section.rs` and
  `ColumnRenderBufferBuilder` share its index layout (`P_*`, `F_*`).
- **Semantic identities are builder-local.** The section build interns the
  center source's identities in first-use order and returns that order;
  `LodQuadBuilder.buildSection` checks Java assigns the same ids.
- **Builds need a consumer.** `LodRenderSection` only builds while the Rust
  whole-frame renderer is selected; otherwise a build would be discarded.
- **A failed neighbor load counts as missing.** The load future always
  completes, so a section never stays "upload in progress" after an error.
- **Overlapping full-data points** in a tall column are rejected with an
  `IllegalArgumentException` and the destination left untouched, as Frozen's
  reducer did.

## Removed

- Java geometry and topology: `ColumnBox`, `BufferQuad`,
  `BufferMergeDirectionEnum`, the Java quad lists, merge and packet encoder in
  `LodQuadBuilder` (now `final`), and `ColumnQuadView`.
- Java fallbacks: `RenderDataPointReducingList`, the heap-list conversion
  scratch, the per-column conversion export and `NativeDhConversionReader`,
  class-identity admission in the conversion scope (multiplayer levels now get
  the Rust color cache), and the shade-facts admission gate.
- Unused API: `NativeDhPacketPlan.build` and its Rust encoder export,
  `ColumnArrayView.copyFrom`/`mergeWith`/`changeVerticalSizeFrom`, and
  `LodBufferContainer`'s Java upload state.
- Retired drivers (last in commit `1490391f9`): `VerifyDhQuads`,
  `VerifyDhFaces`, `VerifyDhPackets`, `VerifyDhColumnPublication`,
  `VerifyDhRenderSource`, `VerifyDhConversionPolicy`,
  `VerifyDhPrimaryConversion` and `VerifyDhConversionReader`. Their recorded
  Frozen fixtures are still replayed by the tests below.

## Tests

```bash
./gradlew -PmattmcRustProfile=release test parityTest --tests '*NativeDh*' --tests '*LodQuadBuilder*' --tests '*ColumnRenderBufferBuilder*'
```

- `NativeDhSectionBuildTest` (parity) builds 240 random sections per mode in
  Rust and with the Java reference (`JavaSectionBuildReference`,
  `JavaColumnBoxReference`, `JavaQuadListReference`: the HEAD `1490391f9`
  Java code), comparing quad rows, identity tables and packet bytes. It covers
  neighbors, every debug mode, color adjustment, transparency modes, cave
  culling, semantic planes, spans and contributor boxes.
- `NativeDhQuadBuilderTest`, `NativeDhFacesTest`, `NativeDhPacketPlanTest`
  and `NativeDhRenderReductionTest` replay the recorded Frozen fixtures.
- `NativeDhRenderConversionTest` and `NativeDhConversionScopeTest` cover the
  whole-source conversion bridge and its material cache.
- `NativeDhTintConversionTest` (parity) converts recorded terrain with warm tint
  caches through the native tint route and through Java's per-point colors, at
  blend radii 0, 1 and 3, and requires equal packets. `NativeDhTintTest` replays
  Frozen's cached-neighborhood fixture through the Rust source blend.

**Whole pipeline, across builds.** `VerifyDhSource.py --mode render` converts,
builds and encodes recorded terrain (a 3 x 3 grid of block-detail sections
plus two parent levels) with the identical driver on a baseline build and on
Current, requires equal packet checksums and times both:

```bash
python3 DevUtils/tests/world/chunk/VerifyDhSource.py --mode render --java "$JAVA" \
  --frozen-classpath-file baseline.cp --current-classpath-file current.cp \
  --native-dir build/rust/native --frozen-native-dir ../baseline/build/rust/native \
  --output build/dh-render-check --pairs 6 --rounds 6 --warmup-rounds 2
```

The runner checks the selected native file before each launch and rejects
nonmatching checksums by default. `--per-side-checksums` deliberately relaxes
cross-side equality; it must not be used as parity evidence. The runner records
a ratio but has no slower-than-baseline exit gate. Its initial driver hashes
and native checks do not by themselves establish final source/class-image or
mapped-library identity. Preserve any additional author-run provenance checks
separately from what this script enforces.

Add `--jvm-arg=-Ddh.verify.renderConfig=1` or `=2` for other settings presets,
`--jvm-arg=-Ddh.verify.tint=all|partial|none` for warm, partly missing or empty
tint caches (default `all`), `--jvm-arg=-Ddh.verify.blend=N` for the biome
blend radius,
or a semantic launch switch such as
`--jvm-arg=-Dmattmc.dev.rustGalDistantHorizons.exactMaterialTopology=true`.

## Measured results (2026-10-13)

Against a build of the preceding Current implementation (`1490391f9`,
"baseline") on the laptop. This baseline is not unchanged Frozen, despite the
driver option names. These scoped CPU and moving-camera comparisons are
separate from the preceding particle migration’s Current/Frozen matrix.

**CPU, whole pipeline** (`--mode render`, medians; every pair's packet
checksums matched):

| Setting | Baseline ms | Current ms | Current / baseline |
| --- | --- | --- | --- |
| Defaults (6 pairs x 6 rounds) | 2,767.2 | 2,107.2 | 0.76 |
| Preset 1: culling, fake ocean, color adjustment | 1,428.0 | 1,119.0 | 0.78 |
| Preset 2: debug colors, no transparency | 1,418.0 | 1,110.4 | 0.78 |
| Semantic preservation (diagnostic) | 4,847.8 | 4,877.4 | 1.01 |
| Exact material topology (diagnostic) | 5,100.8 | 4,943.4 | 0.97 |

The presets and diagnostic modes ran two pairs of three rounds each. Java heap
allocation per default round (the driver prints `DH_ALLOCATED_PER_ROUND`;
native storage is not counted) fell from 33.1 MB to 15.8 MB.

**Gameplay** (small DH fixture, moving camera, 6,000 frames, four alternating
runs per side, medians):

| Side | FPS | Median frame | p99 | DH columns built during the run |
| --- | --- | --- | --- | --- |
| Baseline | 119.8 | 6.38 ms | 31.5 ms | ~414 |
| Current | 116.7 | 6.46 ms | 34.4 ms | ~518 |

The 1.3% median-frame difference is inside the run spread (5.98–6.86 ms on
both sides). Current published about 25% more DH columns within the same window,
and its extra frame time sits in GC and DH asset preflight rather than in the
build. The laptop swapped 1.6–3.9 GB in every run, so GC times vary widely
between runs. Runs: `artifacts/performance/realworld-20261011/dhab5-*`.

The moving runs process different numbers of columns under substantial swap
pressure. Their CPU attribution and timing spread are author observations, not
a controlled isolated throughput gain or broad frame-time, streaming or
long-session memory acceptance. The diagnostic semantic CPU ratio of 1.01
remains slower than the preceding implementation.

## Measured results: tinted colors (2026-10-13)

Against a build of the preceding Current implementation (`c357b63cd`,
"baseline"), not unchanged Frozen.

**CPU, whole pipeline** (`--mode render`; every pair's packet checksums matched):

| Tint caches, blend radius | Baseline ms | Current ms | Current / baseline |
| --- | --- | --- | --- |
| Warm, 3 (default; 6 pairs x 4 rounds) | 2,633.9 | 2,420.7 | 0.92 |
| Warm, 0 | 723 | 709 | 0.98 |
| Warm, 1 | 854 | 773 | 0.91 |
| Warm, 2 | 1,025 | 991 | 0.97 |
| Partly missing, 3 (misses fall back to Java) | 1,385 | 1,161 | 0.84 |
| Empty, 3 (every tinted point asks Java) | 699 | 701 | 1.00 |

The rows after the first ran two pairs of two rounds each.

**Gameplay** (small DH fixture, moving camera, 6,000 frames, four alternating
runs per side, medians): baseline 110.9 FPS, 6.39 ms median frame, 42.2 ms
p99; Current 111.6 FPS, 6.05 ms, 45.8 ms. Both sides spread widely (median frame
5.64–8.65 ms) with 1.5–4.4 GB of swap in every run, so this shows no regression
rather than a measured gain. Runs: `artifacts/performance/realworld-20261011/dhab6-*`.

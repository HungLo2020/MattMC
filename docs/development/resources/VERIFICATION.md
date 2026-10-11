# Verification

Three drivers check this area: resource stacks, tags and registry sync
against Frozen (this section through [Limits](#limits)); [atlases](#atlases)
and [models](#models) against Reference for records and warnings, and Frozen
for timing acceptance.

[`VerifyResourceStacks.py`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/VerifyResourceStacks.py)
runs the same observer,
[`ResourceStackReference.java`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/ResourceStackReference.java),
against Frozen's classes and this checkout's in alternating fresh JVMs. Every
record text must match exactly. For each gated metric, the driver divides
the median of Current's per-JVM timings by the median of Frozen's and requires
that ratio to be at most `--max-ratio` (default `1.10`). Each JVM reports an
iteration median; individual paired ratios are recorded but are not gates.
See the [aggregation and failure conditions](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/VerifyResourceStacks.py#L139-L165).

The original driver/observer references retain the `398a0078` source pin;
those two files are unchanged at the reviewed `f92ea29d` source. The member-view
and profiler references below use `f92ea29d`. Each reported run belongs to its
named worktree/native image, not to a fresh execution of the reviewed source.
No resource tests, native/game builds, profiling or game runs were repeated,
and the ignored `build/` receipts were not inspected during this documentation
review.

```sh
# Frozen: a separate checkout with compiled main classes (default
# ../MattMC_JavaPerfTesting/MattMC). The driver builds this checkout's release library.
python3 DevUtils/tests/resources/VerifyResourceStacks.py \
  --output build/resource-stack --pairs 5 --iterations 9
```

`--output` must be a new directory under `build/`. Records, logs, a
`mismatch.diff` on failure, and `results.json` (timings, paired ratios,
library, observer and driver hashes, and both checkout identities) are written
there. The driver requires at least three pairs and JDK 25; the example requests
nine iterations rather than the default seven. It builds Current's classes and
release native library and uses Frozen's precompiled classes through the
[shared classpath helper](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/content/VerifyStateGraphs.py#L50-L67).

## What it covers

- **Packs and stacks**: this checkout's resources as a directory pack, a
  vanilla-builder pack and a jar subset, plus fixtures for overlays, filters,
  sidecars, odd names, archives with directory entries, a directory pack
  inside an archive, and broken or metadata-less packs. It records lookups,
  listings, stacks, namespaces, root files, `pack.mcmeta` sections and sidecar
  metadata for both pack types.
- **Repository**: available packs, compatibility, positions and selected order
  over the folders and the jar; 200 randomized `rebuildSelected` and
  `Position.insert` trials.
- **Tags**: every tag directory in the checkout's data plus fixture directories,
  loaded through `TagLoader` with a synthetic, order-sensitive element lookup;
  each tag's members, the map's iteration order, the element lookups in call
  order, and the self-reference/dependent overflow fixture are recorded. This
  is not a registry-backed lookup for every directory; see the
  [tag observer](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/ResourceStackReference.java#L403-L481).
- **Registry sync**: server registries loaded as `WorldLoader` does, from the
  vanilla data (a known pack) plus an unknown fixture pack. For three client
  known-pack sets it records the encoded registry-data entries and the
  decoded update-tags payloads, before and after static tag rebinds from a
  reload fixture. Payload wire order is checked against Java's algorithm in
  the same JVM, because `ResourceKey` identity hashes make it vary between runs.

Timings: list+read, lookup and manager construction per pack type; tag
loading; registry packing for three known-pack sets plus tag payload creation;
and a retag (apply static tag rebinds, then perform that sync work).
[Retag timing](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/ResourceStackReference.java#L523-L551)
starts after loading the pending tags. The
[timed sync path](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/ResourceStackReference.java#L574-L624)
skips packet byte encoding and hashing; those are used while recording. Neither
timing measures network transport, client receipt or a complete in-game reload.

Focused source tests also cover
[snapshot IDs, known packs, Java payload order, deferred unbound-tag errors and unchanged/changed rebind caching](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/test/java/net/minecraft/core/NativeRegistrySnapshotTest.java#L64-L109),
[generation retirement](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/content/registry/mod.rs#L172-L196),
and [retag name/member references and malformed records](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/src/main/rust/content/registry/ffi.rs#L273-L300).
These describe inspected assertions, not additional test-run receipts.

## Latest run

Historical author report, preserved from the
[incoming verification page](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/docs/development/resources/VERIFICATION.md#L46-L69):
uncommitted work on `437e2bc1` against Frozen `7a4d1817`, five pairs, nine
timed iterations (sync and retag after nine untimed warm-up rounds), release
library `05bde869`. The author reported a pass: 89,630 record lines were
identical in every run, and every gated median was within 10%. Times are
medians in milliseconds. The numbers and identifiers below are retained as
reported; no resource tests, benchmark, native/game build or game run was repeated for this
documentation review.

This heading preserves the original published URL. It records the older
`05bde869` run; later reported checkpoints follow in the next section.

| Timing | Current | Frozen | Ratio | Paired ratios |
| --- | --- | --- | --- | --- |
| Client list + read | 617.81 | 673.18 | 0.918 | 0.87–0.97 |
| Client lookups | 92.45 | 128.75 | 0.718 | 0.64–0.85 |
| Server list + read | 124.48 | 199.55 | 0.624 | 0.61–0.70 |
| Server lookups | 39.77 | 62.24 | 0.639 | 0.61–0.80 |
| Client manager construction | 0.99 | 1.29 | 0.773 | 0.74–0.82 |
| Server manager construction | 0.63 | 0.93 | 0.682 | 0.66–0.80 |
| Tag loading | 36.10 | 51.52 | 0.701 | 0.67–0.80 |
| Registry sync (3 clients) | 6.68 | 7.80 | 0.856 | 0.69–0.93 |
| Retag + sync | 7.42 | 6.81 | 1.091 | 1.04–1.27 |
| First retag (cold, not gated) | 11.37 | 11.27 | 1.008 | — |

Retag has the highest reported gated ratio, `1.091`, and passed with little
margin under the aggregate `1.10` gate (one pair reached
1.27). A per-pair gate would not have passed that pair. It still walks every
rebound registry, as Frozen does, and then
publishes the changed tags to Rust and decodes them. The first retag after
publishing is one cold sample per run; it is reported, not gated.

## Local integrated checkpoint and profiling

The following sequence is retained from the author-reported local work;
pending checks apply to their individual checkpoints. The local `6673fac82`
worktree with client-range/entity/GUI migrations builds
release `e81fc916`. Serial Rust passes 2,649 tests (three ignored); Java executes
2,001 tests (two skipped). Refreshed Frozen entity/range replays and all
Current/native/Frozen/protected-prompt source guards pass. The fresh resource
comparison uses five pairs, nine iterations and `--max-ratio 1.0`: all ten runs
produce identical 89,630-line records, but performance acceptance fails only
retagging (Current/Frozen 3.506/3.461 ms, ratio 1.013). The other eight warm
median floors pass. Cold retagging is 6.084/4.712 ms and remains reported,
ungated evidence. Receipts: `build/native-gui-item-templates-integrated/`.
This is not combined client/server, GUI, gameplay/FPS or reload acceptance.

For Linux CPU diagnostics, reuse that completed corpus with the paired
[retag profiler](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/DevUtils/tests/resources/ProfileResourceRetags.py):

```sh
python3 DevUtils/tests/resources/ProfileResourceRetags.py \
  --verification build/native-gui-item-templates-integrated/resource-replay \
  --output build/native-gui-item-templates-integrated/retag-profiles-v3 \
  --java-home /path/to/jdk25 --profiler /path/to/asprof
```

The helper repeats the shared observer's actual registry loading, pending-tag
loading, rebind and sync calls in fresh Current/Frozen JVMs. The default sample
is 15 seconds; `--duration` permits 1–30 seconds. It preserves collapsed stacks,
native mappings, process identities and source/native guards. The observer’s existing `-- reload`
label marks the sampling start, excluding initial registry setup and sync
warm-up. Separate pending-tag loading from retag/sync stacks; both remain in
the sampled phase. The earlier whole-call profiles include setup/sync and remain
retained separately. The corrected phase-marker pair passes with clean process,
source and native guards. Current snapshot publication/scanning accounts for
401/289 overlapping samples of 15,436, and decoding for 76. These counts do not
normalize per operation or prove the 1.3% cause. They identify the Java
scan/native republish/Java decode boundary for further ownership work.
Profiler timings cannot replace the five-pair gate.

Before sampling, the profiler requires identical records from the completed
corpus, matching Frozen Git identity/status and the same Current library hash.
It hashes file contents under `src`, `DevUtils` and `build.gradle` in both trees,
the native libraries and the protected prompt before/after, and checks the live
PID/start time and mapped library during sampling. It reuses the corpus's
observer classes and classpaths without resealing their compiled bytecode or
content fixtures. These are bounded diagnostic guards, not complete artifact
attestation or a new parity run.

The next local candidate replaces copied network member lists with
[immutable native views](TAGS-AND-REGISTRIES.md#registry-identity-snapshot).
Release `a99b6f8d` builds; four focused Rust controls and two Java integration
tests pass, including pointer identity, retirement, last-owner release,
immutable duplicate/empty members, retained maps/lists through 32 reloads and
a GC request, and primitive packet wire ordering. The
[Java fixture](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/test/java/net/minecraft/core/NativeRegistrySnapshotTest.java)
keeps the old map and parent list reachable when checking its sublist, so it
does not establish sublist-only lifetime or eventual automatic-arena cleanup.
The [Rust lease fixture](https://github.com/HungLo2020/MattMC/blob/f92ea29dde0199a9ea65006e216ac92570b3b02b/src/main/rust/content/registry/ffi.rs)
separately checks access after retag/context release and storage release after
the explicit last lease is dropped. The fresh full serial Rust suite also
passes 2,651 tests (three ignored, zero failures). Full Java executes 2,002
tests with two skips and no failures; Frozen entity/range replays pass, as do
all source/native/Frozen/prompt guards and the wiki check.

The fresh five-pair, nine-iteration resource replay passes the unchanged
aggregate ratio-of-medians gate at `--max-ratio 1.0`; all ten runs produce
identical 89,630-line records. Warm timings in milliseconds:

| Timing | Current | Frozen | Ratio |
| --- | --- | --- | --- |
| Client list + read | 410.944 | 435.161 | 0.944 |
| Client lookups | 58.285 | 73.045 | 0.798 |
| Server list + read | 75.216 | 109.824 | 0.685 |
| Server lookups | 25.639 | 34.682 | 0.739 |
| Client manager | 0.875 | 1.086 | 0.806 |
| Server manager | 0.413 | 0.549 | 0.752 |
| Tag loading | 19.769 | 26.028 | 0.760 |
| Registry sync | 2.621 | 2.970 | 0.882 |
| Retag + sync | 3.751 | 5.603 | 0.670 |

Retag paired ratios are 0.621, 1.153, 0.686, 0.664 and 0.688; one pair is
slower, although the predeclared aggregate gate passes. Cold retag remains
ungated and slower: 5.952/4.532 ms (1.313×). Current warm retag is also higher
than the preceding candidate's 3.506 ms, while Frozen's median changed
substantially. These separate fresh-JVM comparisons do not establish an
isolated speedup or resolve cold-path cost. Keep both completed candidates;
do not discard unfavorable pairs. Further holder/publication ownership and
real reload/network/gameplay evaluation remain required.
Receipts: `build/native-registry-tag-views/`. Corrected GUI reload comparisons pass plain items and static foil, but moving
foil initially selected the wrong animation phase. The repaired route now passes
all four fresh GUI pairs with source/native guards; exact capture evidence is in [GUI templates](../rendering/RUST-GUI-ITEM-TEMPLATES.md#current-integrated-gui-capture-boundary).
At native `a99b6f8d`, the integrated runtime passes seven lifecycles and two settled world pairs,
with zero VUIDs and source/native/Frozen/prepared-input guards passing.
The allocation diagnostic also passes its guards. All 20 gameplay timing runs
finished cleanly; all median FPS floors and three p99 floors pass, but vanilla
p99 fails at 3.357/3.123 ms Current/Frozen. See the
[historical tag-view/GUI checkpoint summary](../rendering/GAMEPLAY-PERFORMANCE.md#historical-tag-viewgui-checkpoint).
The newer combined `c487f375` checkpoint refreshes the full CPU suites, actual
Frozen geometry/range/ticking replays and live lifecycle/world checks. Its
20-run gameplay matrix still misses only vanilla p99; it does not repeat
these resource timings, the GUI-specific pairs, the resource retag CPU profiles
or the GUI allocation diagnostic. These checks do not establish first-frame,
sustained-streaming or long-memory acceptance.

## Limits

The [final integrity check](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/VerifyResourceStacks.py#L159-L162)
rechecks the native library file hash and Frozen Git identity/status. It does not
recheck Current source/resource hashes or compiled class images. Observer and
driver hashes are recorded initially, rather than all being resealed at the end.
These guards should not be described as complete end-to-end artifact attestation.

The driver does not start a client or server, join a world, run `/reload`, or
exercise arbitrary custom `PackResources` implementations, mods' packs, server
resource-pack downloads or the pack selection UI. Matching fixture records
does not establish broad pack compatibility, gameplay/FPS parity or integrated
runtime acceptance. The [tag ordering helper](TAGS-AND-REGISTRIES.md#tag-loading)
also has an explicit collision-tree limit.
Check those in game: use the pack screen, run `/reload` and join a world, and
look for resource, tag or registry errors in the log.

## Reference checkouts

The atlas and model source references below are pinned to
[`71762d602`](https://github.com/HungLo2020/MattMC/commit/71762d602195e0cc5ca29006a2bed1603b53eab8).
Their **Reference** is intended to retain the Java pipelines being replaced.
The caller selects that checkout, builds its classes and release native
library independently, and passes `--reference` (default
`build/reference-head`). For this migration, parent `f92ea29d` retains those
pipelines; the drivers do not select or verify the Reference commit's ancestry.
For example, create a worktree at the chosen commit, run
`./gradlew -PmattmcRustProfile=release classes buildRustNative` there, and remove
that worktree when finished. Frozen also needs precompiled main classes and
its appropriate native library. The shared classpath helper builds Current's
classes; each JVM's `mattmc.rust.natives.dir` points to its own tree's
`build/rust/native`. Use JDK 25 for these runs.

Both drivers compile the same observer separately against all three trees,
use Current's resources plus generated fixtures, and reverse the run order
on alternate fresh-JVM rounds. Defaults are five rounds and five timed
iterations; at least three rounds and a new output directory under `build/`
are required. Each observer reports the sorted middle iteration sample
(the upper middle for an even count). The driver takes the median of those
per-JVM samples and gates **Current/Frozen**, at `--max-ratio 1.10` by default.
Reference timing ratios and individual round ratios are reported, not gated.

Both explicitly skip Frozen before comparing records or warnings:
[atlas comparison](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/textures/VerifyAtlases.py#L280-L317)
and [model comparison](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/models/VerifyModels.py#L332-L370).
`records_identical` therefore covers only the Reference and Current record
texts. Frozen must still finish and emit one valid timing/hash receipt.
Nonzero exits or command timeouts, missing/ambiguous receipts, compared record
or warning mismatches, and aggregate timing ratios above the threshold fail
the run. Logs and records remain in the output directory; mismatches add
`mismatch.diff` or `warnings.diff`, and runtime failures are saved in
`results.json` with `passed: false`.

These new drivers record initial Git HEAD/status for each tree and hashes of
the observer and driver sources. They do not record native-library hashes or
verify the loaded native image, and perform no final source, resource,
compiled-class or native-library integrity recheck. The stronger
resource/profiler guards described earlier do not apply to these drivers.
The historical numbers below are author
reports, not fresh executions of `71762d602`; their ignored `build/` receipts
were not inspected and no resource tests, native/game builds or benchmarks
were repeated here.

## Atlases

[`VerifyAtlases.py`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/textures/VerifyAtlases.py)
runs [`AtlasReference.java`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/textures/AtlasReference.java)
on Reference, Current and Frozen in alternating fresh JVMs. Every known atlas
is built by `SpriteLoader.loadAndStitch` over this checkout's resources: alone,
with an edge-case fixture pack (PNG encodings, animation metadata errors,
unstitch and paletted sources, filters, broken definitions, GUI metadata), and
from a pack whose sprites allow four mip levels. Records hold sizes, mip
levels, map order, placements, UVs, frames, flags, per-level pixel hashes and
uploaded first-frame snapshots. All Reference/Current record texts must match
the first Reference record. Each Current run's sorted WARN/ERROR first lines
must match the first Reference warning list; later Reference warning lists
are not separately checked. Stack traces and continuation lines are excluded.

The [snapshot path](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/textures/AtlasReference.java#L220-L247)
calls `TextureAtlas.upload` through a CPU semantic device, not a real GPU.
Frozen lacks that device and skips snapshots; its entire record and warning
list are excluded from parity checks. Reflective device installation failure
also disables snapshots, while a null snapshot or reflective invocation
failure can produce `snapshot none` or `snapshot failed` records. Matching
record text alone does not separately require successful snapshots. Pixel
and snapshot summaries use truncated hashes, and animation ticking is not
observed.

The [timed region](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/textures/AtlasReference.java#L85-L104)
loads all known atlases in parallel over the plain resource stack and waits
for their `readyForUpload` futures. It runs after the three recording passes,
without a separate warm-up loop. Resource-manager setup, recording, semantic
uploads/snapshots and closing sprite contents are outside this timing;
fixture-pack and mip-pack loads are not timed.

**Latest run (historical author report):** the
[incoming page](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/docs/development/resources/VERIFICATION.md#atlases)
reports a pass for uncommitted work on `398a0078`, Frozen `7a4d1817`, five
rounds and five warm iterations. It reports 8,414 record lines matching
Reference in every round and parallel atlas loading at 82.4 ms against
Frozen's 115.5 ms (0.713; rounds 0.65–0.73) and Reference's 105.1 ms.
The report does not identify Reference's commit or the native-library hashes.

## Models

[`VerifyModels.py`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/models/VerifyModels.py)
runs [`ModelReference.java`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/models/ModelReference.java)
the same way. It stitches every atlas (no upload), runs
`ModelManager.reload` up to its preparation barrier, and records, alone and
with two fixture packs: per block state the model class, particle,
render-group partition and the parts collected for four seeds (AO, every quad's
vertices, tint, face, sprite, shade, light); per item model id the baked item
model tree and properties; and the missing models. Fixtures cover model JSON
errors and number forms, parent and texture-reference cycles, element and face
rotations, display transforms, generated items over animated and partly
transparent sprites, weighted and multipart blockstates, overlaps and errors,
a blockstate split across packs, special-model bases and item definitions
over missing models. Record lines contain labels and truncated hashes of the
observed details; an optional detail file exposes the full dumps. Item-tree
dumps inspect selected model fields, but represent other objects by class or
lambda markers, so they do not establish all special-renderer behavior.

The observer [constructs atlas objects with `Unsafe`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/models/ModelReference.java#L197-L259),
installs stitched sprites and completed pending-stitch futures, and captures
the prepared model state. Its preparation barrier returns an unresolved
future, deliberately preventing application. This exercises preparation and
baking through the real `ModelManager.reload` entry point, with synthetic
atlas setup and no texture-manager lifecycle or upload.

Normalized, because Java itself varies per JVM: generated items' SOUTH/NORTH
front faces, unresolved texture reference lines, and group numbering (the
partition is compared). WARN entries compare with their continuation lines;
ERROR entries compare their header only, since Java's cause is a Gson or DFU
exception with a stack trace. Every Reference/Current run's normalized
warnings and record text must match the first Reference run; Frozen's are
excluded from both comparisons.

The [two timed regions](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/DevUtils/tests/models/ModelReference.java#L111-L132)
use only the plain resource stack, after plain/fixture recording passes and
an untimed stitch of all atlases. `models_ns` times preparation with those
precomputed atlases; `reload_ns` times fresh stitching of all atlases plus
the same preparation. Both include the synthetic atlas/model-manager setup
inside `reload`, stop at the preparation barrier and exclude sprite cleanup.
Neither includes reload application, GPU upload, a complete client reload or
a separate warm-up loop.

**Latest run (historical author report):** the
[incoming page](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/docs/development/resources/VERIFICATION.md#models)
reports a pass for uncommitted work on `398a0078`, Frozen `7a4d1817`, five
rounds and five warm iterations: 67,433 record lines identical across all
15 runs including Frozen, and 4,351 warning entries matching Reference.
The all-15 claim is additional author-reported evidence; the inspected driver
does not enforce or establish Frozen record equality. Reference's commit and
the native-library hashes are not identified in that report. The historical
timings are retained below.

| Timing | Current | Frozen | Reference | Current/Frozen | Rounds |
| --- | --- | --- | --- | --- | --- |
| Model reload (atlases precomputed) | 138.0 ms | 150.2 ms | 152.9 ms | 0.919 | 0.73–1.06 |
| Atlas + model reload | 224.2 ms | 285.2 ms | 252.8 ms | 0.786 | 0.76–0.91 |

**Limits:** neither driver uploads through a real GPU, applies the reload to a
running client, renders, or exercises resource-pack switching in game. Check
those by joining a world, switching resource packs and comparing the log's
model and texture warnings with Frozen's.

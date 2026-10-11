# Verification

Three drivers check this area: resource stacks, tags and registry sync
against Frozen (this section through [Limits](#limits)); [atlases](#atlases)
and [models](#models) against Frozen and a Reference checkout.

[`VerifyResourceStacks.py`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/VerifyResourceStacks.py)
runs the same observer,
[`ResourceStackReference.java`](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/ResourceStackReference.java),
against Frozen's classes and this checkout's in alternating fresh JVMs. Every
record text must match exactly. For each gated metric, the driver divides
the median of Current's per-JVM timings by the median of Frozen's and requires
that ratio to be at most `--max-ratio` (default `1.10`). Each JVM reports an
iteration median; individual paired ratios are recorded but are not gates.
See the [aggregation and failure conditions](https://github.com/HungLo2020/MattMC/blob/398a00782007587c779264aaa62222036b8a9361/DevUtils/tests/resources/VerifyResourceStacks.py#L139-L165).

The source and test references on this page are pinned to the integrated
`398a0078` implementation. The reported run below belongs to an earlier
uncommitted worktree; it is not a runtime verification of that integrated commit.

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

The local `6673fac82` worktree with client-range/entity/GUI migrations builds
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
[retag profiler](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/resources/ProfileResourceRetags.py):

```sh
python3 DevUtils/tests/resources/ProfileResourceRetags.py \
  --verification build/native-gui-item-templates-integrated/resource-replay \
  --output build/native-gui-item-templates-integrated/retag-profiles-v3 \
  --java-home /path/to/jdk25 --profiler /path/to/asprof
```

The helper repeats the shared observer's actual registry loading, pending-tag
loading, rebind and sync calls in fresh Current/Frozen JVMs. It samples each
live process for 15 seconds and preserves collapsed stacks, native mappings,
process identities and source/native guards. The observer’s existing `-- reload`
label marks the sampling start, excluding initial registry setup and sync
warm-up. Separate pending-tag loading from retag/sync stacks; both remain in
the sampled phase. The earlier whole-call profiles include setup/sync and remain
retained separately. The corrected phase-marker pair passes with clean process,
source and native guards. Current snapshot publication/scanning accounts for
401/289 overlapping samples of 15,436, and decoding for 76. These counts do not
normalize per operation or prove the 1.3% cause. They identify the Java
scan/native republish/Java decode boundary for further ownership work.
Profiler timings cannot replace the five-pair gate.

The next local candidate replaces copied network member lists with
[immutable native views](TAGS-AND-REGISTRIES.md#registry-identity-snapshot).
Release `a99b6f8d` builds; four focused Rust controls and two Java integration
tests pass, including pointer identity, retirement, last-owner release,
immutable duplicate/empty members, queued maps/sublists through 32 reloads and
GC, and primitive packet wire ordering. The fresh full serial Rust suite also
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
[complete integrated matrix](../rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary).
The newer combined `c487f375` checkpoint refreshes the full CPU suites, actual
Frozen geometry/range/ticking replays and live lifecycle/world checks. Its
20-run gameplay matrix still misses only vanilla p99; it does not repeat
these resource timings. These checks do not establish first-frame,
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

The atlas and model drivers compare records with **Reference**: the last
commit whose Java pipeline they replace, checked out and built on its own
(`git worktree add build/reference-head <commit>`, then
`./gradlew -PmattmcRustProfile=release classes buildRustNative` there).
Frozen is timed and, where its observer can see the same state, compared too.
Remove the worktree with `git worktree remove` when done.

## Atlases

[`VerifyAtlases.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/textures/VerifyAtlases.py)
runs [`AtlasReference.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/textures/AtlasReference.java)
on Reference, Current and Frozen in alternating fresh JVMs. Every known atlas
is built by `SpriteLoader.loadAndStitch` over this checkout's resources: alone,
with an edge-case fixture pack (PNG encodings, animation metadata errors,
unstitch and paletted sources, filters, broken definitions, GUI metadata), and
from a pack whose sprites allow four mip levels. Records hold sizes, mip
levels, map order, placements, UVs, frames, flags, per-level pixel hashes and
uploaded first-frame snapshots; Current must match Reference, and sorted
WARN/ERROR first lines must match. Frozen has no semantic device, so it skips
snapshots and is timed only.

**Latest run** (uncommitted work on `398a0078`, Frozen `7a4d1817`, five
rounds, five warm iterations): passed. 8,414 record lines matched Reference in
every round. Loading all atlases in parallel took 82.4 ms against Frozen's
115.5 ms (0.713; rounds 0.65–0.73) and Reference's 105.1 ms.

## Models

[`VerifyModels.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/models/VerifyModels.py)
runs [`ModelReference.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/models/ModelReference.java)
the same way. It stitches every atlas (no upload), runs
`ModelManager.reload` up to its preparation barrier, and records, alone and
with two fixture packs: per block state the model class, particle,
render-group partition and the parts collected for four seeds (every quad's
vertices, tint, face, sprite, shade, light); per item model id the baked item
model tree and properties; and the missing models. Fixtures cover model JSON
errors and number forms, parent and texture-reference cycles, element and face
rotations, display transforms, generated items over animated and partly
transparent sprites, weighted and multipart blockstates, overlaps and errors,
a blockstate split across packs, special-model bases and item definitions
over missing models.

Normalized, because Java itself varies per JVM: generated items' SOUTH/NORTH
front faces, unresolved texture reference lines, and group numbering (the
partition is compared). WARN entries compare with their continuation lines;
ERROR entries compare their header only, since Java's cause is a Gson or DFU
exception with a stack trace.

**Latest run** (uncommitted work on `398a0078`, Frozen `7a4d1817`, five
rounds, five warm iterations): passed. 67,433 record lines were identical in
all 15 runs, Frozen's included, and the 4,351 warning entries matched
Reference.

| Timing | Current | Frozen | Reference | Current/Frozen | Rounds |
| --- | --- | --- | --- | --- | --- |
| Model reload (atlases precomputed) | 138.0 ms | 150.2 ms | 152.9 ms | 0.919 | 0.73–1.06 |
| Atlas + model reload | 224.2 ms | 285.2 ms | 252.8 ms | 0.786 | 0.76–0.91 |

**Limits:** neither driver uploads through a real GPU, applies the reload to a
running client, renders, or exercises resource-pack switching in game. Check
those by joining a world, switching resource packs and comparing the log's
model and texture warnings with Frozen's.

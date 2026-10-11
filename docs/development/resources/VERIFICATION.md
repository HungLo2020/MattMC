# Verification

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

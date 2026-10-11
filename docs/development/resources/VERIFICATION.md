# Verification

[`VerifyResourceStacks.py`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/resources/VerifyResourceStacks.py)
runs the same observer,
[`ResourceStackReference.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/resources/ResourceStackReference.java),
against Frozen's classes and this checkout's in alternating fresh JVMs. Every
record must match byte for byte, and each warm timing median must be within
10% of Frozen (`--max-ratio`).

```sh
# Frozen: a separate checkout with compiled main classes (default
# ../MattMC_JavaPerfTesting/MattMC). The driver builds this checkout's release library.
python3 DevUtils/tests/resources/VerifyResourceStacks.py \
  --output build/resource-stack --pairs 5 --iterations 9
```

`--output` must be a new directory under `build/`. Records, logs, a
`mismatch.diff` on failure, and `results.json` (timings, paired ratios,
library and observer hashes) are written there.

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
- **Tags**: every tag directory loaded through `TagLoader`, with each tag's
  members, the map's iteration order, the element lookups in call order, and
  the self-reference overflow fixture.
- **Registry sync**: server registries loaded as `WorldLoader` does, from the
  vanilla data (a known pack) plus an unknown fixture pack. For three client
  known-pack sets it records the encoded registry-data entries and the
  decoded update-tags payloads, before and after static tag rebinds from a
  reload fixture. Payload wire order is checked against Java's algorithm in
  the same JVM, because `ResourceKey` identity hashes make it vary between runs.

Timings: list+read, lookup and manager construction per pack type; tag
loading; a full sync for three clients; and a retag (rebind static tags, then
sync).

## Latest run

Uncommitted work on `437e2bc1` against Frozen `7a4d1817`, five pairs, nine
timed iterations (sync and retag after nine untimed warm-up rounds), release
library `05bde869`. It passed: 89,630 record lines were identical in every run,
and every gated median was within 10%. Times are medians in milliseconds.

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

Retag is the slowest case and passed with little margin (one pair reached
1.27). It still walks every rebound registry, as Frozen does, and then
publishes the changed tags to Rust and decodes them. The first retag after
publishing is one cold sample per run; it is reported, not gated.

## Limits

The driver does not start a client or server, join a world, run `/reload`, or
exercise mods' packs, server resource-pack downloads or the pack selection UI.
Check those in game: use the pack screen, run `/reload` and join a world, and
look for resource, tag or registry errors in the log.

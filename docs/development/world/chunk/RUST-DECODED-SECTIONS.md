# Rust decoded-section ownership

Current-version saved chunks create their block, biome and light owners directly
inside Rust. Section palettes, words and light bytes no longer travel through
Java arrays, lists and streams before being imported back into Rust.

[`storage/chunk/hydrate.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/storage/chunk/hydrate.rs)
uses the existing [saved-palette policy](RUST-SAVED-PALETTES.md) and live light
owners. Java still schedules reads, binds registries, wraps section objects and
parses the remaining root tag. This is an intermediate ownership boundary;
[Retained region tapes](RUST-REGION-TAPE.md) now connect the Rust file producer
and section consumer without full-tape Java copies. Java scheduling and the rest
of chunk construction still need migration.

## Admission and lifetime

- Only stock factories created by `PalettedContainerFactory.create` with an
  admitted frozen biome registry use this route. A caller-created factory keeps
  its original codec callbacks, even when its strategies match stock strategies.
- `parseLoaded` reads the root's `DataVersion` before creating section owners.
  Older versions use the complete original tag and upgrade callback.
- A confined decode scope owns temporary native vectors and the remaining root
  tape. Hydration returns independent block, biome and light leases. Closing the
  decode scope leaves those primary owners and their CPU views valid. The scope
  retains its original factory, so later hydration cannot reinterpret decoded IDs
  with a different registry or factory.
- Rust preserves section order, missing-container defaults, out-of-height light
  rows, local palette padding and unused entries. Global palettes use the same
  remapping and packing policy as the ordinary saved-palette loader.
- A rejected row declines the complete hydration without publishing partial
  owners. The original full-tag route supplies malformed-input diagnostics and
  partial codec behavior. ABI and ownership-transfer failures still throw.
- Saved light layers enter the native light map directly. Public array-backed
  `DataLayer` inputs keep their mutable alias contract; `getData()` remains an
  explicit ownership escape. See [live light layers](../lighting/RUST-LIVE-LAYERS.md).

## Verify and measure

Use JDK25 and the matching release flags:

```sh
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeChunkSectionsTest' --tests '*NativeSavedPaletteLoadingTest' --tests '*NativeLightLayerTest'
CARGO_TARGET_DIR=build/rust/target-tests cargo test --profile suite --manifest-path src/main/rust/Cargo.toml storage::chunk::hydrate -- --test-threads=1
python3 DevUtils/tests/chunk/VerifyDecodedSections.py --java-home "$JAVA_HOME"
python3 DevUtils/tests/chunk/VerifyDecodedSections.py --java-home "$JAVA_HOME" --benchmark --pairs 3
python3 DevUtils/tests/chunk/VerifyDecodedSections.py --java-home "$JAVA_HOME" --benchmark --pairs 3 --reference predecessor
python3 DevUtils/tests/chunk/VerifyDecodedSections.py --java-home "$JAVA_HOME" --profile current
python3 DevUtils/tests/chunk/VerifyDecodedSections.py --java-home "$JAVA_HOME" --profile frozen
python3 DevUtils/RunWiki.py check
```

The standalone driver needs prepared runtime classpaths in
`build/native-chunk-tasks/{current,frozen}-classpath.txt`; the predecessor
comparison also needs the preserved classes and native image in
`build/native-saved-palettes/`. It compiles against untouched Frozen and checks
the loaded class locations and hashes. The checked-in oracle records 40 real
saved chunks through Frozen's region reader and public parser: every block,
biome and light value, palette/storage representation and re-serialized NBT.
The production test also checks primary ownership, independent copies after
decode-scope closure and custom codec callbacks.

Timing includes reading and parsing all 40 chunks per round. Region-file setup,
registry initialization, final flush and full fingerprints are outside timing.
Two hundred equal warmup rounds precede eight measured rounds in each independent JVM.
Pairs alternate order on one CPU with a 512 MiB heap and synchronous C2.
Compiler activity invalidates timing; failures and all rounds remain in receipts.
Source, fixture, compiled-class, native-image and Frozen guards must pass.

Receipts live in `build/native-saved-palettes/chunk-loader/`. Release `d7107c13`
passes 33 focused Java controls, the complete Rust suite (2,623 passed, three
ignored), and both Java suites (1,969 reported, two skipped, zero failures).
The real-chunk Frozen oracle and primary-owner/custom-codec controls pass.
Three compiler-quiet guarded pairs measure13.39–13.46ms Current against
8.29–8.32ms Frozen per40 chunks (about1.61–1.62×). The scoped performance
floor fails. Earlier40-round warmup
pilots had measured compilation and remain retained; output is now buffered
until all measured rounds finish. These are historical pre-optimization timings; later controls and runtime
evidence are recorded below. Fixture throughput does not prove gameplay speed, streaming,
temporal rendering correctness or long-running memory bounds.

The warmed `d7107c13` JFR pair covers40,000 chunk loads per side. Current's
native samples often end in FFM stubs; their unique signatures associate about
30% of main-thread samples with hydration and48% with region tape reading.
These are signature-based attribution, not native symbol profiles or exact CPU
percentages. Weighted Java allocation is about7.01GB Current versus18.23GB
Frozen; one sampled pair does not establish memory bounds or an isolated gain.
Raw recordings, sampled allocations and input guards remain in
`profile-jfr-{current,frozen}/` beneath the receipt directory.

The `453df088` candidate validates packed owner indices once per word instead of
recomputing division and loading an atomic word for every cell. It validates
before allocating the generation and ignores final-word padding exactly as
before. This applies to both block and biome admission. Full Rust passes2,623 tests
with three ignored; Java reports1,969 with two skips and no failures. Frozen
replay and ownership checks pass. Three quiet guarded loading pairs now measure
10.22–10.30ms Current versus8.31ms Frozen
per40 chunks: about1.23–1.24× Frozen and23–24% below the earlier d710 observations.
All three direct control pairs pass:22–23% lower cost than the preserved
hydration checkpoint,26–27% lower than the previous published build. Every pair
has zero measured compilation and passing source/class/native/Frozen guards.
The Frozen floor still fails and guides the next tape-ownership migration.

The exact453df088 runtime passes seven lifecycle cases and two settled Frozen
OpenGL pairs (vanilla and shaders+DH), with zero VUIDs. Nine Current artifacts
map this native image. Four original deterministic1280×720 gameplay captures
were visually reviewed; terrain, water, fog and sky are coherent. A timed Frozen
vanilla screenshot captured startup and is excluded; the comparison uses the
completed deterministic gameplay capture. Source/native/prompt and untouched
Frozen guards pass. Receipts: `gameplay/runtime-review.json` and
`artifacts/graphics-captures/validation/native-saved-palettes-correctness-20261011/summary.json`.
This is settled correctness evidence, not new whole-game FPS, first-turn terrain
admission, temporal parity or sustained-memory acceptance.

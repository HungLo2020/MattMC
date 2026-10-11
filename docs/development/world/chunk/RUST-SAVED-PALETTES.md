# Direct saved-palette owners

Canonical `PalettedContainer.unpack` now installs a primary Rust block or biome
owner directly from saved words and bounded registry IDs. Local imports preserve
raw words, padding, palette order and unused entries. Global imports remap saved
indices to registry IDs and pack the final storage inside Rust. The loader avoids
constructing Java palettes, bit storage and decoded cell arrays before adopting
native storage. Existing live reads, mutations, copies and downstream native
consumers use the resulting owner.

The policy is in
[`palette/saved.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/palette/saved.rs).
[`NativeSavedPaletteLoading`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeSavedPaletteLoading.java)
is a temporary CPU bridge: it resolves canonical object identities and borrows
heap ID/word spans synchronously. Rust copies into independent owned storage and
retains no Java pointers. There is no additional crate or resource registry.

## Scope and constraints

- Both ordinary codec loading and DH codec loading reach this shared loader.
  [NBT section hydration](RUST-DECODED-SECTIONS.md) uses the same native policy
  directly, without exporting Java palette/word arrays or constructing streams.
  File scheduling, registry binding and chunk orchestration still remain Java.
- Blocks require the standard strategy and canonical block registry. Biomes
  require the standard strategy and existing frozen-registry binding described
  in [live biome ownership](../biome/RUST-LIVE-BIOMES.md). Only exact known list
  implementations are read during admission; custom lists retain original callbacks.
- Saved palettes have at most 65,536 entries; registries at most 65,535 IDs.
  Local widths include zero, block 4–8 and biome 1–3; global widths preserve
  the requested saved width separately from registry storage width.
- Declared-bit validation precedes storage consumption. Zero-width loading
  never consumes its optional stream. Nonzero streams materialize once, before
  native admission, and declined inputs reuse those same words.
- Malformed lengths/indices, null or unsupported values and local duplicate
  palettes preserve existing compatibility behavior. Global aliases map directly.
  No partial owner is published on decline. The original caller supplies its
  errors and first missing-ID exception.
- Local padding is retained exactly. Global repacking ignores source padding
  and zeros output padding. Zero-width copies retain their shared single-value
  behavior; growing copies detach according to the existing live-owner policy.
- Thread-local ID scratch is bounded at 256 KiB and retains no Java objects.
  Owners and generation views use existing independent FFM cleanup leases.

## Build and verify

Use JDK25 and matching release flags for the native image:

```sh
CARGO_PROFILE_RELEASE_DEBUG=line-tables-only CARGO_PROFILE_RELEASE_STRIP=none ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests 'net.minecraft.world.level.chunk.NativeSavedPaletteLoadingTest' --tests 'net.minecraft.world.level.chunk.NativePaletteUnpackingTest' --tests 'net.minecraft.world.level.chunk.NativeLiveBlockSectionTest' --tests 'net.minecraft.world.level.chunk.NativeLiveBiomeSectionTest'
CARGO_TARGET_DIR=build/rust/target-tests cargo test --profile suite --manifest-path src/main/rust/Cargo.toml world::level::chunk::palette::saved -- --test-threads=1
python3 DevUtils/RunWiki.py check
```

`SavedPaletteVerification` compiles against untouched Frozen and invokes its
actual public loader. Its checked-in compressed oracle has 82 cases: local and
global block/biome boundaries, raw padding, unused entries, aliases, later copies,
edits, reads and saving again. Production-boundary tests also require canonical
owners with no Java storage mirror, and check stream and malformed-input behavior.

The standalone driver uses the prepared Current/Frozen runtime classpaths in
`build/native-chunk-tasks/{current,frozen}-classpath.txt`. With those builds ready:

```sh
python3 DevUtils/tests/chunk/VerifySavedPaletteLoading.py --java-home "$JAVA_HOME"
# Isolated complete caller timings, including imports and sampled value reads:
python3 DevUtils/tests/chunk/VerifySavedPaletteLoading.py --java-home "$JAVA_HOME" --benchmark --pairs 3
# Compare with the preserved pre-migration classes and native image:
python3 DevUtils/tests/chunk/VerifySavedPaletteLoading.py --java-home "$JAVA_HOME" --benchmark --pairs 3 --reference predecessor
```

Receipts live in `build/native-saved-palettes/`. At the earlier saved-owner
checkpoint `e5c83a3a`, the 24 focused Java and three
focused Rust tests pass, including the 82-case replay. Full Java validation
reports1,967 tests, two skipped and no failures (1,965 executed); full Rust passes
2,621 tests with three ignored. Timing and gameplay acceptance are pending.
The first timing pilot included measured JIT work and remains under
`caller-pilot-with-jit/`; it does not establish a gain. Corrected timing uses
25 equal warmup rounds, a512MiB heap and synchronous C2 with threshold1,000,
requiring zero measured compiler activity. These are supplemental steady-state
caller measurements, distinct from normal JVM gameplay. The driver pins one
allowed CPU and records compiler
activity, input/class/native hashes and native mappings; inspect those receipts
before attributing an isolated gain. Source fixtures are supplemental;
historical saved-light/gameplay results in `SUMMARY.md` do not accept this new
native image. Fixture timing does not establish gameplay speed, temporal
rendering correctness, streaming or long-running memory behavior.

Three compiler-quiet pairs per width cost about3.17–3.20× Frozen for zero-width
blocks and8.55–8.64× for two-entry blocks. These scoped regressions are retained;
the following 17-entry pair failed the measured-JIT check and the matrix stopped.
No predecessor attribution or whole-game performance follows from those results.
The next comparison measures real region-read/chunk-parse workloads through the
[direct hydration route](RUST-DECODED-SECTIONS.md).

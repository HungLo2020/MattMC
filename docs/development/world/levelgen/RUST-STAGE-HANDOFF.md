# Native generation section handoff

Canonical NOISE results now become independently owned live sections directly
inside Rust. SURFACE and CARVERS capture those live sections into isolated Rust
stage inputs and install only modified results through the same native handoff.
Java no longer exports canonical palettes/packed words, constructs state lists,
or rebuilds a second container for these transfers.

The transitional bridge is
[`NativeGenerationSections`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeGenerationSections.java).
The ownership boundary lives in
[`stage_transfer.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/stage_transfer.rs),
with import/export entries beside each stage. See
[live storage](../chunk/RUST-LIVE-SECTIONS.md) and
[stage semantics](RUST-SURFACE-STORAGE.md#shared-chunk-storage).

## Ownership and compatibility

- Import copies packed inputs under each native owner's lock. Stages retain no
  live-owner pointers: later live mutations cannot alter their input snapshots.
  Stage storage still unpacks sections lazily on first access.
- Install packs a modified dense stage section once in Rust and creates a unique
  live owner. Java registers its cleanup before allocating the CPU projection,
  then publishes it and the original three counters. Releasing the stage cannot
  invalidate installed sections or retained rebuild captures.
- Preserve the section/container objects and untouched owners. Preserve palette
  insertion/growth order, words, network bytes and counter casts. This change
  does not combine generation stages or change callback/order semantics.
- The direct route accepts exact built-in section/container classes and their
  canonical live owners. Custom classes, alias palettes and unsupported metadata
  retain the original compatibility import/install. Check class identities before
  invoking custom methods; do not narrow the existing generation eligibility gate.
- Pointer arrays, counters and small heightmaps use confined native scratch for
  ordinary FFM calls. Pin all input owners until import returns. A successful
  result transfers one owned pointer; consume it once. No native registry/cache,
  GPU handle, frame ownership or renderer ABI changes are involved.

Java still orchestrates chunks, biome inputs, marks and heightmap publication.
The generation-stage dense representation and live packed representation remain
separate Rust formats. General save/network enumeration still uses temporary
Java compatibility projections; further producer/consumer migration remains work.

## Verification and profiling

Use JDK25 and the same release profile for tests and runtime checks:

```sh
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeStageHandoffTest' --tests '*NativeNoiseFillTest' --tests '*NativeSurfaceChunkTest' --tests '*NativeCarversTest' --tests '*NativeLiveBlockSectionTest'
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml -- --test-threads=4
python3 DevUtils/tests/rendering/RunValidation.py --label <new-label> --all-java-tests --perf
python3 DevUtils/RunWiki.py check
```

The focused Java suite currently passes all 26 tests. Handoff cases verify
snapshot isolation, stage-release/GC lifetimes, unchanged-owner preservation,
alias compatibility and every palette growth boundary through global storage.
A custom NOISE chunk with extra untouched sections verifies that installation
never invokes their accessors. Existing stage cases cover every vanilla noise
setting and custom-rule gates.
Rust tests separately cover transferred palette/word history and rejected alias
outputs. These are supplemental correctness checks, not whole-game acceptance.

Allocation profiling uses `NativeSurfaceChunkVerification native overworld`
with the test runtime classpath and JFR `profile` settings. Each process prepares
and surfaces NOISE-filled chunks; retain command, source/library identities,
route count and checksum. The recording includes startup/setup/warmup and JFR
observer overhead. Weighted allocation samples can demonstrate removal of the
Java transfer work, but cannot establish Frozen-equivalent FPS or isolated timing
improvements. Existing pinned-source worldgen helper drivers describe historical
migrations and require integration updates before accepting this owner boundary.

The before/after diagnostic has identical checksums: 680/520 surfaced chunks
with adaptive warmup. Estimated Java allocation in stage-handoff stacks falls
from 107.9 to 1.0 KB per surfaced chunk (270/2 samples); legacy palette/word
projection stacks disappear. The initial after profile sampled 3.36 KB/chunk;
sparse after samples limit precision. This is a scoped allocation estimate, not a timing
or whole-game speedup. Retained receipt:
`goal5/native-stage-handoff-profile-20261009/allocation-comparison.json`.

Final release `c7c95f4a` passes 2,417 Rust tests (3 ignored), the full Java
suite (1,751 passed, 2 skipped), all seven lifecycle cases, reviewed vanilla
and Iris+DH coast pairs, and the wiki check (2,485 pages/43 indexes). VUIDs,
exceptions, dependency failures and owned orphan clients are zero. All sixteen
ABAB runs are clean with exactly 6,000 measured frames. Source/library/user-edit
and untouched Frozen identity checks pass; 25 generated copies were retired.

Whole-renderer performance floors still fail: vanilla averages 7.6% below Frozen
and vanilla+DH 8.9% below, both with worse p99. Shader modes pass both floors.
Frozen DH repeats vary substantially; this does not establish an isolated
transfer speedup or its regression cause. Retained final receipt:
`validation/native-stage-handoff-final-20261009/summary.json`. See `SUMMARY.md`
for repeats/p99 and `PROGRESS.md` for subsequent producer/consumer migration.

# Native generation section handoff

Canonical NOISE results now become independently owned live sections directly
inside Rust. SURFACE and CARVERS capture those live sections into isolated Rust
stage inputs and install only modified results through the same native handoff.
Java no longer exports canonical palettes/packed words, constructs state lists,
or rebuilds a second container for these transfers.

The transitional bridge is
[`NativeGenerationSections`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeGenerationSections.java).
The ownership boundary lives in
[`stage_transfer.rs`](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/src/main/rust/world/level/chunk/stage_transfer.rs),
with [retained capture](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/levelgen/proto_chunk/ffi.rs)
and import/export entries beside each stage. See
[live storage](../chunk/RUST-LIVE-SECTIONS.md) and
[stage semantics](RUST-SURFACE-STORAGE.md#shared-chunk-storage).

## Ownership and compatibility

- Import copies packed inputs under each native owner's lock. Stages retain no
  live-owner pointers: later live mutations cannot alter their input snapshots.
  Native [section counters](../chunk/RUST-SECTION-COUNTERS.md) are sampled under
  the same lock; Java no longer projects a three-counter input array. This is
  per-owner capture, not a whole-chunk atomic snapshot. Stage
  storage still unpacks sections lazily on first access.
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
- Canonical chunks pass the shared [retained chunk owner](../chunk/RUST-DH-CHUNK-INPUTS.md)
  directly to Rust for stage capture. No per-stage Java storage/counter pointer
  or pin arrays are constructed. Small heightmaps use confined native scratch;
  custom height providers, custom chunks and unsupported inputs retain the
  original pointer-array compatibility route and getter order.
  Pin the admitted owner until import returns. A successful
  result transfers one owned pointer; consume it once. No native registry/cache,
  GPU handle, frame ownership or renderer ABI changes are involved.

Java still orchestrates chunks, biome inputs, marks and heightmap publication.
The generation-stage dense representation and live packed representation remain
separate Rust formats. Input copying and output packing/allocation remain; this
is native-only capture/adoption, not zero-copy transfer. Output counters are published
through the Java bridge separately after live-owner adoption; whole-chunk
failure atomicity is not established. General save/network enumeration still uses temporary
Java compatibility projections; further producer/consumer migration remains work.

## Current retained-input follow-up

The verified 2026-10-13 implementation removes the canonical pointer-table construction and
uses read borrows for generation capture/install, surface reads, decoration and
proto-to-live transfer. An exact `ImposterProtoChunk` forwards read borrows to
its wrapped live chunk; subclasses retain the original getter dispatch. Reading
biomes during decoration no longer marks ordinary live sections as escaped,
allowing collision and random-tick consumers to retain structural authority.
Explicit mutable access still retires that authority before returning the array.
Palette replacement is re-admitted before each stage capture, and captures remain
isolated snapshots with section-local counters even when block owners alias.

The paired vanilla JFR at `13b42a901` recorded 48 server-thread samples through
`NativeChunkInputs.bind`; 46 explicitly include collision traversal, while two
truncated stacks do not identify the consumer. This identifies repeated
admission work; it is not a rate, isolated speedup or proof of the candidate.
Evidence: `build/native-world-consumer-profile/paired/`. Native `03026814` passes the full Rust suite (2,784 tests, three ignored) and
Java suite (2,130 tests, two skipped), plus 24 actual
Frozen NOISE/SURFACE comparisons (three settings, two seeds, four positions).
The observer hashes every block, section counter predicates, both generation
heightmaps and ordered post-processing marks; it also checks native surface
admission and absence of mutable-array escape. These are scoped checks. The final run preserves prior failure receipts: an
old test expected read admission to escape, and a mocked surface fixture needed
its section data supplied through the read-borrow API. Production custom-height
providers retain their original getter order, checked independently.

The affected live consumers also match 231,212 actual Frozen collision cases
and 600 random-tick cases. Six interleaved JVMs with 40 warmups and eight
compiler-quiet samples each measure collision CPU at **83.32/79.52 ms**
Current/Frozen per 65,536 whole-world queries, with Java allocation
**34.25/75.23 MB**. The 4.8% CPU gap remains open; this does not establish an
isolated migration speedup or a native/total memory bound. Receipts:
`build/native-retained-stage/{oracle-2,collisions-1,random-ticks-1}/`.

The after-change 30-second gameplay JFR contains one server binding sample,
from DH chunk preparation, and none identified as collision re-admission.
The old random-tick section path has 20 before samples and none after. This
supports the removal of repeated preparation; the recordings have different
sample totals and do not measure an isolated CPU speedup. Original JFRs and
losslessly compressed event exports remain under
`build/native-{world-consumer,retained-stage}-profile/paired/`.

### Scoped runtime checkpoint

One fresh 6,000-frame Current/Frozen pair per mode on native `03026814`:

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen | Peak RSS GiB, Current / Frozen |
| --- | --- | --- | --- |
| Vanilla | 1280.5 / 1241.0 | 3.180 / 3.675 | 5.18 / 4.36 |
| Vanilla + DH | 815.4 / 684.4 | 3.793 / 6.812 | 6.12 / 5.76 |
| Shaders | 352.3 / 313.8 | 5.221 / 7.619 | 5.78 / 6.74 |
| Shaders + DH | 277.8 / 226.2 | 6.003 / 9.620 | 8.46 / 6.35 |

All scoped FPS/p99 floors pass. Saved inputs and live JVM controls match;
live entity counts are 182/181, 167/167, 188/190 and 165/164 in table order.
Single pairs do not establish sustained acceptance or an isolated gain. No RSS
guard fired, but higher combined-mode RSS and long host/GPU memory bounds remain
open; these traces measure short client sessions, not GPU allocation.

Vanilla and shader+DH settled pairs, world unload/reload, resource reload and
view-distance reduction pass with zero Vulkan validation errors. All seven
originals were viewed; five presenter correlations and nine native identities
match. The paired coast views closely align. Heavy blue haze limits the three
lifecycle initial images to structural evidence. These checks do not resolve
first-turn pop-in/flicker, first shader-world presentation or broad entity visuals.
Final source/class/native/Frozen/input/JVM guards pass. Seventeen completed world
copies were retired across the runtime and before/after profiles. Lossless JSON
compression reclaimed 1,202,127,619 bytes; original images, JFRs, failures and
measurement receipts remain. Details: `build/native-retained-stage/` and the
profile directories above. Subsequent results below are historical.

```sh
python3 DevUtils/tests/worldgen/VerifyRetainedGeneration.py \
  --java-home /path/to/jdk25 --output build/retained-generation/oracle
```

## Verification and profiling

Use JDK25 and the same release profile for tests and runtime checks:

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeStageHandoffTest' --tests '*NativeNoiseFillTest' --tests '*NativeSurfaceChunkTest' --tests '*NativeCarversTest' --tests '*NativeLiveBlockSectionTest'
CARGO_TARGET_DIR=build/rust/target cargo test --locked --manifest-path src/main/rust/Cargo.toml --profile suite --lib world::level::levelgen::proto_chunk -- --test-threads=1
python3 DevUtils/tests/rendering/RunValidation.py --label <new-label> --all-java-tests --perf
python3 DevUtils/RunWiki.py check
```

The preceding handoff milestone passed 26 focused Java tests at `a908f78c`.
The author reports that the section-counter follow-up passes the full
1,762-test Java suite (2 skipped),
including the direct counter-owner input route. Handoff cases verify
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

The author reports final release `c7c95f4a` passing 2,417 Rust tests (3 ignored), the full Java
suite (1,751 passed, 2 skipped), all seven lifecycle cases, reviewed vanilla
and Iris+DH coast pairs, and the wiki check (2,485 pages/43 indexes). VUIDs,
exceptions, dependency failures and owned orphan clients are zero. All sixteen
ABAB runs are clean with exactly 6,000 measured frames. Source/library/user-edit
and untouched Frozen identity checks pass; 25 generated copies were retired.

Whole-renderer performance floors still fail: vanilla averages 7.6% below Frozen
and vanilla+DH 8.9% below, both with worse p99. Shader modes pass both floors.
Frozen DH repeats vary substantially; this does not establish an isolated
transfer speedup or its regression cause. Retained final receipt:
`validation/native-stage-handoff-final-20261009/summary.json`. The [pinned summary](https://github.com/HungLo2020/MattMC/blob/a908f78cd909200f5f4f4424b124072cef0a17f6/SUMMARY.md)
records repeats/p99. This documentation review inspected source and committed
author reports, not the unbundled receipts, profiles or images, and reran no
Java/Rust suites or runtime workloads. Broader producer/consumer migration remains open.

The newer [section-counter verification](../chunk/RUST-SECTION-COUNTERS.md#verification)
includes this handoff and DH cloud ownership, release `59171b74`. All seven
lifecycle cases and reviewed coast pairs pass. Vanilla/DH performance floors
still fail; shader modes pass. These scoped checks do not establish broad
parity or an isolated transfer speedup.

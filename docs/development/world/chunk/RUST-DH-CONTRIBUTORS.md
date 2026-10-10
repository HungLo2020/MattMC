# Rust DH semantic contributor transport

Rust captures immutable four-column
footprints, clips source contributors and remaps their packed material IDs.
Source updates and DTO loading retain direct CPU readers. With semantic
preservation enabled, the full-data transformer obtains one footprint and the
render source retains the same immutable readers; it does not construct another
Java point array. Ordinary packed-only DH builds skip that transformer read and
publication. Exact-material topology/capture and the explicit developer switch
enable preservation; see [render architecture](../../rendering/RENDER-ARCHITECTURE.md).
The ordinary native parent producer now determines horizontal uniformity and
ordered contributor admission while reducing the whole quadrant. It captures
all admitted footprints in that same call; Java reads immutable descriptors
without per-cell native capture calls. Parent generation/compression selection
and seeded hashes also belong to Rust; see [full-data columns](RUST-DH-FULL-DATA.md).
Java maintains metadata storage and
resolves render material spans and contextual colors. This is world CPU data, with no
GPU handles or renderer resource ownership in the bridge.

Source reviewed at [`5de23173a`](https://github.com/HungLo2020/MattMC/commit/5de23173ae5b61fb17887589d4dff69a6b17fc77) on 2026-10-10; the release results below are
implementation-author reports, and this review did not rerun suites or clients.
See the [current CPU checkpoint](../../rendering/GOAL-5-STATUS.md#october-10-retained-world-and-dh-reduction-cpu-checkpoint)
for remaining runtime, performance and sustained-memory acceptance gates.

Implementation: [native producer](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/chunk/dh_columns/contributors.rs),
[CPU lease](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhContributors.java),
[ordinary transformer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/dataObjects/transformers/FullDataToRenderDataTransformer.java).
The related [full-data columns](RUST-DH-FULL-DATA.md) guide covers mutable points,
parent reduction and occlusion; its older verification does not accept this source.

## Working constraints

- Distinguish null from an empty, present column. Preserve column order, raw
  point bits, truncation and Java's signed integer OR when remapping IDs.
  A failed remap declines before publishing an owner; the original Java helper
  retains its callback sequence and exception behavior.
- Parent updates provide compression bytes, prior uniformity proofs and existing
  target membership as facts. A resized public compression list retains the
  Java compatibility path. Rust compares geometry and remapped IDs, ignoring
  lighting. `MERGE_SAME_BLOCKS` is value0; null and present-empty are distinct.
  Process cells in x/z order, remove each old entry before admission, and preserve
  the effect of uniform replacements releasing capacity for later cells.
- Source updates admit footprints while the retained cell count is below1,024,
  clipping each source column to256 points. The generic bridge supports4,096 points
  per column; larger render-source inputs keep their Java compatibility copy.
- Borrow native views or exact `LongArrayList` primitive storage for one bounded
  capture. Custom list subclasses keep original Java reads. Heap arguments are
  confined to the bounded critical native call; native output retains no heap,
  chunk or world references. Pin every borrowed list through the call.
- Native snapshots are immutable. A retained internal getter clones only the
  four-reference array. Public `getSemanticHorizontalContributors` still returns
  independent mutable `LongArrayList` copies, and Java serialization remains an
  explicit value export. Never share a view of mutable source storage instead
  of capturing independent values.
- Source replacement, world close or clearing a render column must not alter
  readers already retained elsewhere. CPU leases release the native owner;
  there is no global footprint cache. A reader from the batched producer retains
  the whole quadrant's immutable point/footprint owner; include this retention
  in runtime memory checks. Existing source exclusion remains required.

## Verification

With JDK25 and the standard release flags:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml --locked --profile suite --lib dh_columns
./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests net.minecraft.world.level.levelgen.NativeDhContributorsTest
python3 DevUtils/RunWiki.py check
```

Releaseac41d404 passes nine focused Rust checks and221 Rust world tests. Full
Java/DTO verification passes1,737 ordinary tests (two skipped) and133 parity
tests. The final task includes22 native DH controls plus the shared-fixture
bootstrap regression. Six observed full-task workers map the exact native
image; all39,441 Current/38,176 Frozen source pins, native-library and protected
prompt guards match. Receipts: `build/native-dh-parent-semantics/`.

All32,768 saved-native groups match the existing Java semantic predicate, every
admitted footprint and actual Frozen parent-reduction values. Further controls
cover capacity/replacement order, null versus empty, clipping, lighting-independent
comparison, aliased remapped IDs, malformed facts, lifetimes and defensive
exports. An ordinary parent update proves its readers retain the quadrant owner,
with no per-cell native capture. A resized public compression list preserves the
original Java exception and successful update prefix. The actual transformer
makes one retained-footprint read, zero array-export reads and shares its
immutable readers when semantic preservation is enabled. The combined integration
test also checks that disabled preservation never reads or publishes a footprint.
The initial integration rejection and test-scope reconciliation are retained
under `build/native-client-residency/initial-combined-java-failure/`.

The semantic predicate is an existing Current CPU contract; Frozen has no
contributor sidecar implementation. Exact remaps use `FullDataPointUtil`, whose
Current and untouched Frozen source SHA256 is
`cc71e97919a15b94627dc33be95310c78ace8db33c6b9cf41e5ca6ce79acb63a`.
These controls do not establish Frozen pixel parity, saved-biome equivalence,
ordinary runtime admission, a speedup or long-session memory acceptance. Earlier
ba39b190/6352edf8/839ff128 checkpoints do not accept this source. Earlier fixture
bootstrap rejection and final rerun remain in `build/native-dh-contributors/`.
Hardware verification requires restoration of the NVIDIA kernel/library match;
see [render verification](../../rendering/RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures).

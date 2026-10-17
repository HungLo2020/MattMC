# Rust DH lighting pass

> **Current state:** [Rust-only DH generation](../chunk/RUST-DH-GENERATION.md) supersedes this page's
> descriptions of Java fallback, compatibility and decline routes, Java light
> storage and wrapper-replacement checks; those no longer exist. Details and
> historical results below remain as recorded.

Rust owns the admitted whole DH lighting pass and its immutable CPU outputs.
Java retains exact-input admission, wrapper publication, correctness flags,
error handling and compatibility lighting. Broad gameplay and long-session
resource acceptance remain open.

The current [saved-light migration](RUST-DH-SAVED-LIGHT.md) imports ordinary
persisted lighting directly into this Rust field owner. Generation carries the
owner into native passes and column production; externally supplied mutable
stores still retain compatibility behavior. Its verification is separate from
the historical checkpoints below.

Source reviewed at [`5de23173a`](https://github.com/HungLo2020/MattMC/commit/5de23173ae5b61fb17887589d4dff69a6b17fc77) on 2026-10-10; the release results below are
implementation-author reports, and this review did not rerun suites or clients.
See the [current CPU checkpoint](../../rendering/GOAL-5-STATUS.md#october-10-retained-world-and-dh-reduction-cpu-checkpoint)
for remaining runtime, performance and sustained-memory acceptance gates.

The [earlier lighting review](https://github.com/HungLo2020/MattMC/commit/bffd0eef886a446a480cf62166da2eba448eb574)
and release `3f00217d` reports below remain historical; earlier height-field
results are separate.

The pass borrows authoritative live block owners and retained
[DH height fields](../chunk/RUST-DH-HEIGHTMAPS.md). Rust takes section-local
snapshots, enumerates emitters, seeds sky light and runs both priority queues.
Uniform output sections retain encoded constants; varying sections own 2,048
packed bytes per channel. Java borrows those exact tables and bytes.
The [shared chunk-input owner](../chunk/RUST-DH-CHUNK-INPUTS.md)
retains section bindings in Rust and passes one CPU owner per neighbor; initial
admission and public-wrapper checks remain Java. Java supplies neighbor order; it does not build
dense block/policy arrays or execute per-cell boundary calls. Outputs own their
light values and cached emitter positions independently. Chunk light getters
read CPU leases directly. Rust/GAL GPU ownership and presentation are unchanged.

Implementation: [native pass](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/lighting/dh),
[CPU bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhLighting.java),
[emitter bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhSources.java),
[producer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/core/generation/DhLightingEngine.java),
[direct consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/common/wrappers/chunk/ChunkWrapper.java).

## Compatibility and constraints

- Preserve neighbor enumeration and first-match deduplication, priority 15→0,
  LIFO within each priority and direction order up/down/west/east/north/south.
- Preserve Frozen's center clear **after** initial neighbor/center seeding,
  nonempty minimum propagation bound, exclusive world maximum, sky outside-world
  defaults and cached emitter positions across later block edits.
- The first emitter enumeration owns a native cache, including when hashing
  or beacon consumers request it before lighting. A public list is admitted only
  while exact positions/order still match that owner; edited/subclass positions
  retain original callbacks. Lighting borrows the native cache directly.
- Outputs are immutable. Repeated passes publish new fields; retained CPU views
  remain valid. Preserve the original caller exclusion rules: section-local
  snapshots do not establish a concurrent whole-world transaction.
- Custom wrappers/list subclasses, incompatible states/shapes, replaced public
  state wrappers, incompatible public emitter caches and externally injected
  mutable light storage retain the original callbacks. A mutable operation on
  a native field detaches into the original Java storage and retains the emitter
  enumeration. Never retain two mutable authorities for one wrapper.
- Bound the neighborhood to nine chunks and each chunk to 256 sections. Queue
  capacity follows actual work, with an explicit monotonic-update bound. Release
  pass snapshots and queues after the call; keep no global queue cache.

<a id="source-review-boundary-2026-10-10"></a>

## Stale-counter compatibility (2026-10-10)

The source-only concern at `bffd0eef8` is superseded by counter-aware code, an
actual [Frozen CPU recorder](https://github.com/HungLo2020/MattMC/blob/5de23173ae5b61fb17887589d4dff69a6b17fc77/DevUtils/tests/world/lighting/GenerateFrozenDhStaleCountersOracle.java)
and [executable production-bridge regressions](https://github.com/HungLo2020/MattMC/blob/5de23173ae5b61fb17887589d4dff69a6b17fc77/src/test/java/net/minecraft/world/level/levelgen/NativeDhLightingTest.java): three cases, nine passes, every cell and exact
work counts. Direct palette writes can leave `hasOnlyAir` stale. Native lighting
borrows section counters and returns air for stale-empty block reads, while
emitter enumeration deliberately retains raw palette positions. The original
[source review](https://github.com/HungLo2020/MattMC/issues/777#issuecomment-6096976812)
and [dedicated #826](https://github.com/HungLo2020/MattMC/issues/826)
preserve the original concern; the [current repair review](https://github.com/HungLo2020/MattMC/issues/826#issuecomment-6101723839)
records the executable repair and remaining acceptance work. The source/CPU
repair is present: the stale-counter control asserts native admission, and the
mutable-escape control checks exact cell values, work counts and retained-view
immutability. #826 remains open for fresh producer-admission, lifecycle and
manually reviewed comparison evidence on the current retained-input/ABI 79
image. The reported driver mismatch blocks that runtime verification; neither
older `839ff128` passes nor this source review closes the current acceptance gate. The
upstream `fc1d529d` provenance/merge change did not resolve that case; the
subsequent retained-counter implementation and actual Frozen oracle do.

Repeated clear/edit passes exposed another compatibility requirement: Frozen
light-section level counters survive value clears and affect later constant
conversion. Native fields now retain that bounded policy state alongside
compact values. An initial development JNI run reproduced a work-count mismatch
(12,289 versus 8,199); the retained-counter implementation passes the focused
Rust regression and replacement JNI checks. Frozen behavior is unchanged.
A further actual Frozen mutable-escape probe found later work counts diverging
(8,198 versus 12,289) after Java reconstructed a native field. The corrected
handoff copies retained section allocation, constant and signed-counter policy
with the values. Its three-case/ten-pass oracle pins eight Frozen classes;
all 30 production-path passes in the JNI test match in release `839ff128`.
The first two passes per case assert native admission; the remaining eight
assert Java-owned public storage after the mutable escape. Retained native views
stay immutable across both paths. Full Rust/Java checks pass; the final seven lifecycle cases and both reviewed settled pairs pass.
Later travel admission and timing results belong to the
[column milestone](../chunk/RUST-DH-COLUMNS.md). They do not accept the newer
retained-input implementation, whose hardware and long-session verification
remain pending. The earlier release's results
below remain separate.


## Verify

The author reports five JNI checks passing for the `3f00217d` cache owner;
full Rust passes 2,483 tests (3 ignored), and full Java passes 1,843 tests
(2 skipped, no failures/errors) across 330 suites. Two observed JNI workers
map that release. All seven lifecycle scenarios and both manually reviewed
settled vanilla/Iris+DH pairs pass with zero validation messages; the DH
extension covers 28.81% of pixels. Moving producer admission is reported in
paired eight-second DH travel profiles, with all six F3 positions reviewed
and matching. The twenty clean production timing runs meet the measured
FPS/p99 floors, including the expanded DH repeat set described below.

To rerun focused CPU checks, use JDK 25 and the standard release profile:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml world::level::lighting::dh::
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests 'net.minecraft.world.level.levelgen.NativeDhLightingTest'
python3 DevUtils/RunWiki.py check
```

[`GenerateFrozenDhLightingOracle.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/lighting/GenerateFrozenDhLightingOracle.java)
runs against the untouched Frozen classpath. Pass output path, saved chunk JSON
and a JSON mapping of the seven named production classes to SHA-256 identities.
Add `cold` as a fourth argument to record the separate cold-neighborhood fixture.
The recorder refuses mismatched class identities. It invokes the actual private
production pass to record work counts; no expected lighting algorithm is copied.

The deterministic gzip fixture contains 20 cases, each with full lighting,
block-only and no-sky passes: all 16 saved chunks, nine neighbors in shuffled
order, missing center seeding/duplicate/null neighbors, a stale emitter cache
and an entirely empty chunk. Initial existing light values, every final cell,
correctness flags and work counts come from Frozen. Four Rust checks pass for all
60 passes, rejected input/queue bounds, compact fields and independent
CABI output lifetimes. Two additional cold cases cover the complete JNI neighborhood handoff. The
fixture does not establish runtime admission, visual parity, performance or bounded
long-session memory. Receipts: `build/native-dh-lighting-migration/`.

The paired allocation profiles report weighted Java allocation of 1.11/4.45 GB
Current/Frozen. Native lighting-field publication appears in actual DH builder
stacks. Legacy light queues still allocate 36.70/34.60 MB; the earlier Current
height-field run sampled 128.97 MB. Sampling excludes native allocations and
does not attribute a throughput gain. Current short-run peak RSS is 4.05 GiB;
this is not long-session acceptance. All source/native/Frozen/protected-prompt,
prepared-source, movement and owned-process guards pass. Both travel videos
contain positive native opaque/translucent/water submissions.

The timing protocol uses two Current/Frozen ABAB repeats per mode, each exactly
6,000 frames. The initial DH-only two-repeat FPS median misses by 0.22%; that
result remains recorded. Two additional repeats per side were declared before
running, and all four DH repeats are included. The resulting median is
749.6/744.65 FPS and 4.142/5.161 ms p99 Current/Frozen. Vanilla, shaders and
Iris+DH also pass both measured floors. The DH margin is narrow and repeats
vary substantially; no isolated lighting speedup is established. Timing receipt:
`build/native-dh-lighting-migration/performance.json`.

The initial compact-only variant declined normal pre-hash emitter caches. Its
runtime was deliberately stopped and retained as superseded; it is not used
for acceptance of the complete cache-owner implementation.

An additional guarded eight-second Current CPU profile directly samples the
Rust lighting pass (394 samples) and first emitter producer (44). Java voxel-column
construction still has 1,566 samples, including biome reads (694) and block
reads (221), out of 13,011 total samples. Subsets overlap and there is no
paired Frozen CPU run. This evidence guides the next whole-producer migration;
it does not establish an isolated speedup. All three F3 endpoints are reviewed,
with positive native DH submissions in both videos and process/integrity guards
passing. Receipt: `build/native-dh-lighting-migration/current-cpu-travel-review.json`.

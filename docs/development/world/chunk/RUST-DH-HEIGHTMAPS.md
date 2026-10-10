# Rust DH chunk height fields

Admitted `ChunkWrapper` instances use a Rust-owned pair
of height fields. Rust scans the existing live block and section-counter owners;
Java does not construct dense block inputs, computed masks or two mirrored
`int[16][16]` maps. Getters read an immutable CPU lease. Broad gameplay and
long-session resource behavior remain open.

Source reviewed at [`5de23173a`](https://github.com/HungLo2020/MattMC/commit/5de23173ae5b61fb17887589d4dff69a6b17fc77) on 2026-10-10; the release results below are
implementation-author reports, and this review did not rerun suites or clients.
See the [current CPU checkpoint](../../rendering/GOAL-5-STATUS.md#october-10-retained-world-and-dh-reduction-cpu-checkpoint)
for remaining runtime, performance and sustained-memory acceptance gates.

The [earlier height-field review](https://github.com/HungLo2020/MattMC/commit/bffd0eef886a446a480cf62166da2eba448eb574)
and the original height-field release reports below remain historical. Later
[DH lighting results](../lighting/RUST-DH-LIGHTING.md) belong to their own release.

Implementation:
[height-field owner](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/dh_heightmaps),
[intrinsic collision catalog](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/content/block/collision),
[CPU bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhHeightmaps.java)
and [DH consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/com/seibel/distanthorizons/common/wrappers/chunk/ChunkWrapper.java).

## Ownership and compatibility

- Cached intrinsic collision boxes are exported once into a bounded immutable
  native catalog. Solidity comes from geometry; opacity comes from the existing
  native block registry. No Java DH per-block policy answers are exported.
- Exact ordinary chunks/sections/containers use their existing
  [live storage](RUST-LIVE-SECTIONS.md) and [counter owners](RUST-SECTION-COUNTERS.md).
  A scan holds each section's mutation lock while reading it. It does not make
  the whole concurrently mutable chunk transactional. Preserve existing caller
  exclusion rules and section-local, potentially stale `hasOnlyAir` counters.
- Dynamic collision shapes, custom shape/grid classes, custom readers, debug
  worlds and incompatible state policy retain the original Java path. A
  mismatched entry in DH's public wrapper cache also retains that path. Subclass
  callbacks are not replaced by sampled answers.
- The [shared chunk-input owner](RUST-DH-CHUNK-INPUTS.md) retains mutable
  block/counter bindings; scans borrow that one pinned owner. Each result owns its
  values independently; an automatic arena releases it once after CPU readers
  finish. Rebuilding a wrapper replaces its lease without mutating retained
  older results. There is no owning global chunk cache or GPU handle. The bounded
  reuse implementation below retains only immutable fields and weak generation identities.
- `recreateHeightmaps=false` still uses Minecraft's existing heightmaps. Getter
  bounds checks and the original compatibility callback sequence remain.

Frozen DH's full opacity is **16**. Its scan starts at the exclusive top of the
highest nonempty section and does **not** evaluate the minimum row. An entirely
empty chunk reports maximum `minY + 16`. Preserve these details; changing
suspected Frozen behavior requires the user's decision.

<a id="local-bounded-field-reuse-in-progress"></a>

## Bounded field reuse

The current source reuses immutable fields inside Rust when authoritative
storage and the sampled empty-section mask are unchanged. This follows the
Current-only travel profile's repeated chunk-wrapper preparation/native height
scans (288 of11,881 ticks); it is a candidate optimization, not a measured gain.
The ordinary Java bridge still runs its public-wrapper and custom-reader
admission checks on every call. Consumers keep the existing read-only lease and
2,056-byte layout; independent leases may now share one field allocation.

- [Live storage](RUST-LIVE-SECTIONS.md) owns mutation revisions. Packed writes,
  palette additions and shared single-value reads invalidate reuse. Growth
  changes generation identity. A saturated revision declines reuse permanently.
- Keys contain weak generation identity, revision, the actual shared single value
  when relevant, minY and sampled emptiness. They neither retain chunks/sections
  nor use reusable raw addresses as identity. Stale-empty counters still mask
  raw values, including contextual states, exactly as the existing producer does.
- Use the same empty mask for the key and scan; independent resampling could
  cache a result under the wrong mask after counter ABA. Recheck inputs before
  storing a missed result. This preserves section-local reads and existing
  exclusion; it does not make a concurrently mutable chunk transactional.
- The cache holds at most64 field pairs (131,584 field bytes, plus bounded keys
  and allocation overhead). Entries are pruned on insertion when generation
  leases expire. Eviction releases only the cache lease; outside readers remain
  valid. Scans run outside the cache mutex, allowing independent requests.

Releaseb1611469 passes all eight focused Rust height checks and227 Rust world
tests, including16 actual Frozen saved field pairs, alias/writes/masks/contextual
rejection, weak lifetime, bounded eviction and eight parallel readers. Six actual
height JNI controls pass, including all31,532 cached Frozen state cases and proof
that two ordinary chunk-wrapper constructors share the same immutable allocation.
A subsequent block write creates new fields; retained old values remain stable.
Public wrapper overrides still decline before cache reuse.

Full final-source Java verification passes1,737 ordinary tests (two skipped) and
134 parity tests, including seven live-storage Java controls. Six observed
full-task workers map the exact native image; all39,443 Current/38,176 Frozen,
native-library and protected-prompt pins match. Receipts:
`build/native-dh-height-reuse/`.
Later source does not inherit the historical runtime acceptance below. Check
both reuse benefit and mutation bookkeeping cost in fresh equivalent gameplay,
then run hardware correctness, all predeclared timings and sustained memory
checks after [driver restoration](../../rendering/RENDER-VERIFICATION.md#check-the-hardware-driver-before-runtime-captures).

## Verification

Use JDK 25 and the normal release configuration:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml -- --test-threads=4
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only ./gradlew -PmattmcRustProfile=release parityTest -x testRustNative --tests 'net.minecraft.world.level.levelgen.NativeDhHeightmapsTest'
python3 DevUtils/RunWiki.py check
```

The following runtime measurements are historical and belong to the original
height-field release. The retained-input and reuse source above requires fresh acceptance.

The actual Frozen recorder is
[`GenerateFrozenDhHeightmapOracle.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/world/chunk/GenerateFrozenDhHeightmapOracle.java).
Compile/run it against the untouched Frozen classpath, passing output path,
`src/test/resources/worldgen/heightmap/chunks.json`, and SHA-256 identities of
Frozen `BlockStateWrapper`, `ChunkWrapper` and `ProtoChunk`. It rejects other
class identities. A registry-only fixture level services production wrapper
serialization; unexpected world callbacks throw. Expected fields are obtained
from actual production wrappers, not a copied heightmap algorithm.

The deterministic gzip fixture records 31,809 states, 31,532 cached states,
323 collision geometries and 16 saved chunks across four dimensions/two seeds.
Raw SHA-256: `a760bba8f0114ca2728c6d3dcaa70266346dea30fb84b1f8f9b8fb410a315afd`.
The author's recorded initial five Rust checks pass: cached solidity/opacity, all saved field pairs,
stale-empty counters, minimum-row behavior, malformed/contextual rejection and
retained output lifetime. Five actual JNI tests pass on release `574b68f0`: all 31,532 cached states,
16 saved chunks, direct DH getters, rebuild/retained-view behavior, original
custom callbacks/public-cache overrides and eight parallel readers. The final
Rust suite passes 2,479 tests (3 ignored), including a zero-width palette alias
changed during a borrowed scan. The full Java suite passes 1,838 tests (2 skipped,
0 failures/errors) across 329 suites; three observed JNI workers map the exact
release. Both manually reviewed settled vanilla/Iris+DH pairs pass (VUID 0);
DH visible extension covers 28.81%. Six original lifecycle cases pass. The
different-world case reports one closed-channel line during requested teardown
(two exception-pattern matches); the original strict failure is retained. A
correct same-source strict rerun passes, but the shutdown race is unresolved.
`correctness-combined.json` distinguishes these cases; the classifier is unchanged.
All 16 production ABAB timing runs complete cleanly at exactly 6,000 frames.
Median FPS Current/Frozen: vanilla 1,372.4/1,179.4; DH 720.6/602.7;
shaders 340.6/317.5; combined 253.3/224.5. Median p99 milliseconds:
3.125/3.176, 5.471/6.706, 4.417/6.141 and 5.795/7.754 respectively.
Every mode passes this batch's FPS/p99 floors; repeats vary and these results
cannot isolate the height-field change.

Paired eight-second DH travel allocation profiles have matching start/forward
poses, seven crossed X chunk columns and manually reviewed F3 endpoints.
Source, native library, Frozen, protected prompt, source save and process guards
pass. Native DH opaque/translucent/water submissions remain positive in both
Current travel videos. Actual builder allocation stacks demonstrate native
height-field admission. Weighted Java heightmap allocation is 7.34/74.45 MB
Current/Frozen (95.42 MB in the earlier Current profile); total weighted Java
allocation is 1.50/4.31 GB. Sampling weights overlap and exclude native allocations.
Current's short-window peak RSS is 4.36 GiB; this does not establish bounded
long-session memory. The bounded clients exit 143 after the driver reaps them;
both drivers and profilers exit 0.

Receipts: `build/native-dh-heightmap-migration/{correctness-combined,performance,allocation-combined}.json`.
Raw travel profiles: `artifacts/graphics-captures/goal5/native-dh-heightmaps-dh-profile-20261010/`.

The CPU corpus is supplemental. Runtime and profiling receipts support only
the recorded workloads; temporal pop-in/flicker, first shader world frame,
broad gameplay and long-session host/GPU memory still require verification.

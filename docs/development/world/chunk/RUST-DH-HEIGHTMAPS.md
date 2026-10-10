# Rust DH chunk height fields

Admitted `ChunkWrapper` instances use a Rust-owned pair
of height fields. Rust scans the existing live block and section-counter owners;
Java does not construct dense block inputs, computed masks or two mirrored
`int[16][16]` maps. Getters read an immutable CPU lease. Broad gameplay and
long-session resource behavior remain open.

Source ownership was reviewed at
[`bffd0eef8`](https://github.com/HungLo2020/MattMC/commit/bffd0eef886a446a480cf62166da2eba448eb574).
The verification below preserves the implementation author's reports for the
height-field release; this documentation review did not rerun Rust/Java suites
or clients. Later [DH lighting results](../lighting/RUST-DH-LIGHTING.md) belong
to their own release and do not replace the height-field evidence.

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
- Inputs are borrowed and pinned only for the native call. Each result owns its
  values independently; an automatic arena releases it once after CPU readers
  finish. Rebuilding a wrapper replaces its lease without mutating retained
  older results. There is no global chunk cache or GPU handle.
- `recreateHeightmaps=false` still uses Minecraft's existing heightmaps. Getter
  bounds checks and the original compatibility callback sequence remain.

Frozen DH's full opacity is **16**. Its scan starts at the exclusive top of the
highest nonempty section and does **not** evaluate the minimum row. An entirely
empty chunk reports maximum `minY + 16`. Preserve these details; changing
suspected Frozen behavior requires the user's decision.

## Verification

Use JDK 25 and the normal release configuration:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml -- --test-threads=4
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests 'net.minecraft.world.level.levelgen.NativeDhHeightmapsTest'
python3 DevUtils/RunWiki.py check
```

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

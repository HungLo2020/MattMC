# Rust DH lighting pass

Rust owns the admitted whole DH lighting pass and its immutable CPU outputs.
The release `3f00217d` cache owner passes five JNI checks;
full Rust passes 2,483 tests (3 ignored), and full Java passes 1,843 tests
(2 skipped, no failures/errors) across 330 suites. Two observed JNI workers
map that release. All seven lifecycle scenarios and both manually reviewed
settled vanilla/Iris+DH pairs pass with zero validation messages; the DH
extension covers 28.81% of pixels. Moving producer admission is observed in
paired eight-second DH travel profiles, with all six F3 positions reviewed
and matching. The twenty clean production timing runs meet the measured
FPS/p99 floors.
Broad gameplay and long-session resource acceptance remain open. This evidence
belongs to the lighting release; earlier height-field results are separate.

The pass borrows authoritative live block owners and retained
[DH height fields](../chunk/RUST-DH-HEIGHTMAPS.md). Rust takes section-local
snapshots, enumerates emitters, seeds sky light and runs both priority queues.
Uniform output sections retain encoded constants; varying sections own 2,048
packed bytes per channel. Java borrows those exact tables and bytes.
Java supplies bounded owner references and neighbor order; it does not build
dense block/policy arrays or execute per-cell boundary calls. Outputs own their
light values and cached emitter positions independently. Chunk light getters
read CPU leases directly. Rust/GAL GPU ownership and presentation are unchanged.

Implementation: [native pass](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/lighting/dh),
[CPU bridge](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeDhLighting.java),
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

## Verify

Use JDK 25 and the standard release profile:

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

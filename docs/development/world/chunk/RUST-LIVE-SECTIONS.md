# Rust live block sections

Ordinary canonical block containers now keep their authoritative palette and
packed words in Rust. Rust admits state IDs, grows palettes in first-use order,
updates packed words and owns their lifetimes. Java's container keeps scoped CPU
views for block reads; it retains no Java palette/word mirror and performs no
native call per read. Changing Java writes currently make one fused downcall;
unchanged positions need no native mutation.

The implementation is in
[`chunk/live/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/live).
[`NativeLiveBlockSection`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeLiveBlockSection.java)
is its transitional CPU bridge. This covers fresh containers, valid saved/network
imports, generated-section installation and independent copies. Biomes, chunk
orchestration, scheduling and section block/fluid counters still use Java.

## Consumers and remaining work

Rebuild captures decode directly from the live owner in Rust. Light propagation
receives its existing packed section payload directly from that owner. Heightmap
and skylight scans export a coherent native word/ID pair into reusable native
scratch; they avoid constructing Java palettes/arrays for the handoff. Their
existing Rust kernels and publication rules remain unchanged.

Rust also counts live storage into the existing ordered callback records.
Generic enumeration, codec/network serialization and generation-stage
exports currently materialize temporary compatibility data. It is never retained
beside the authoritative owner. Moving those consumers and bulk write producers
onto the owner is remaining work: native allocation alone does not prove a speed
improvement. A native executable and complete Java removal remain unfinished.

## Compatibility and lifetime rules

- Accept only the exact standard block strategy, canonical registry, built-in
  storage/palettes and canonical ordinary block states. Aliases, malformed data,
  custom callbacks and unsupported widths retain the original owner. Do not
  normalize imported palettes or probe custom implementations during admission.
- Preserve packed padding, palette order, unused entries and requested global
  configuration bits. A resize uses fresh zero padding and first occurrence in
  storage order. Hash palettes append the overflow entry before growing.
- Keep caller exclusion and threading rules. A per-owner native mutex serializes
  mutation and bulk exports; it never calls Java. A generation's atomic words
  and palette entries support CPU readers while writes are admitted. Publishing
  a palette entry precedes publishing its packed index.
- A retained view pins its generation with a separate `Arc` lease and automatic
  FFM arena. Growth publishes a new view; old readers stay valid. No global
  section registry/cache or extra GPU handles are introduced. Reclamation follows
  GC; retained generations consume memory until their readers disappear.
- Preserve the original single-palette alias: zero-width copies share their
  palette, and a successful zero-width network read replaces it for all copies.
  Growth detaches only the growing owner. Different-width reads that fail must
  leave the old shared generation attached. Capture that single value once when
  making an immutable rebuild snapshot.
- An unsupported write or explicit compatibility mutation materializes the
  original Java representation once and transfers ownership back. Invalid writes
  preserve the original palette-admission-before-index-error behavior. Counting
  callbacks continue to resolve the current palette after a callback mutates it.

## Verification

Use JDK25 and a consistently configured release library for Java/runtime checks:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml -- --test-threads=4
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests 'net.minecraft.world.level.chunk.*'
python3 DevUtils/RunWiki.py check
python3 DevUtils/tests/rendering/RunValidation.py --label <new-label> --all-java-tests --perf
```

Focused tests compare original Java writes, configurations, words, palette order,
old returned objects and serialization across growth boundaries, every registered
state and 324 recorded sections. They cover malformed imports, aliases, padding,
custom callbacks, copies, retained views, GC readers and rebuild/light exports.
Real gameplay, lifecycle and Frozen comparisons are still required; source tests
and fixture timing alone do not establish performance acceptance.

Final release `526af413` passes 2,415 Rust tests (3 ignored), 113 focused Java
tests and the full Java suite (1,746 passed, 2 skipped). All seven lifecycle cases
and both reviewed vanilla/Iris+DH coast pairs pass; VUIDs are zero. All 16
ABAB runs contain exactly 6,000 frames and have clean runtime/cleanup receipts.
Source, library and untouched Frozen identity checks pass.

Whole-renderer performance acceptance **fails**: vanilla averages 5.1% below
Frozen and vanilla+DH 24.3% below, with worse p99 times. Both shader modes beat
Frozen on average FPS and p99. These results do not establish an isolated
storage speedup or identify the cause of the DH gap. See `SUMMARY.md` and
`validation/native-live-block-sections-alias-final-20261009/summary.json`.

Earlier diagnostic profiles used release `4a8f5d18`, before final alias hardening:
`goal5/live-block-sections-flight-profile-20261009/` and
`goal5/live-sections-dh-benchmark-profile-20261009/`. Their source, timing,
camera-position and cleanup receipts pass. Short CPU windows, observer overhead
and some inconsistent inline stack labels limit attribution. Allocation samples
and native-worker costs guide further migration; they do not prove throughput
acceptance. Settled images and bounded profiles cannot certify unseen flicker,
broad gameplay coverage or long-running memory behavior.

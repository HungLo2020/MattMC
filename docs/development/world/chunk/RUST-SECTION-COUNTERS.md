# Rust section mutation and counters

Canonical `LevelChunkSection` writes update packed blocks and section counters
in one Rust call, under the live-storage mutation lock. Recounts scan owned
storage directly; they do not build Java histogram records or block callbacks.
Java status queries read an eight-byte, read-only CPU view without downcalls.
The three mutable Java short fields have been removed.

Implementation:
[`counters/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/chunk/counters)
and the [live-storage owner](RUST-LIVE-SECTIONS.md).
[`NativeSectionCounters`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/chunk/NativeSectionCounters.java)
is a temporary CPU bridge, not a second authority.

## Constraints

- Keep counters section-local. Two sections can share the same block container
  and still have independent, stale counts. A section copy copies its current
  counts; it must not recount. Zero-width palette aliases never share counters.
- Preserve signed-short wrapping, including imported negative counts. Incremental
  writes count nonair blocks, ticking nonair blocks and every nonempty fluid.
  Recounts additionally add nonempty fluids to the nonempty count and count only
  randomly ticking fluids in the fluid lane. Built-in lava ticks; water does not.
  An all-water recount therefore retains the original nonempty count of 8,192.
- Admit only exact standard sections/containers, canonical state objects and
  installed native policy. Invalid indices and unsupported/custom state policy
  use the compatibility path without native mutation. Preserve palette admission
  and exceptions, caller exclusion and ordinary/unchecked write behavior.
- Custom callbacks remain in their original Java order. Compatibility updates
  publish each short separately so later exceptions keep their original prefix.
  Failed recounts publish no counts. Network reads replace only the nonempty
  lane, before decoding blocks; failed block reads retain that changed lane.
- Each counter owner has one automatic arena cleanup. Retained CPU views pin
  it; mutation and generation downcalls fence their section/owner references.
  No global registry of sections or GPU handles is introduced.

[Generation handoff](../levelgen/RUST-STAGE-HANDOFF.md) borrows both storage and
counter owners. Rust samples counters while holding the storage mutation lock
and copies isolated stage inputs; Java no longer builds three-counter input
arrays. Stage result installation still returns three small counter values and
publishes them after adopting the block owner. Compatibility serialization,
biomes, chunk orchestration and broader world simulation remain migration work.

## Verification

Use JDK 25 and the same release configuration for all Java/runtime checks:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml -- --test-threads=4
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests 'net.minecraft.world.level.chunk.*' --tests 'net.minecraft.world.level.levelgen.NativeStageHandoffTest' --tests 'net.minecraft.world.level.levelgen.NativeNoiseFillTest'
python3 DevUtils/RunWiki.py check
```

`JavaSectionCounts` retains the original implementation as an independent oracle.
Tests compare all registered states, saved terrain, seeded mutations through
palette growth, independent aliases, signed wrapping, failed network reads and
retained views. These checks establish CPU semantics only. Runtime parity,
bounded resources and realistic Frozen-equivalent performance remain required;
no section-transaction speedup is claimed before production measurements.

Release `59171b74` passes 2,426 Rust tests (3 ignored), 121 focused Java tests,
the full Java suite (1,762 passed/2 skipped), all seven lifecycle transitions
and Wiki checks (2,487 pages/43 indexes). The direct-entry regression asserts
native admission after registry readiness, alongside the original Java count
oracle. Reviewed vanilla/Iris+DH coast pairs pass with VUID 0; those settled
views do not establish broader gameplay or temporal parity.

All 16 ABAB runs contain exactly 6,000 frames and have clean runtime/cleanup
receipts. Vanilla averages 9.0% below Frozen and vanilla+DH 12.9% below, with
worse median p99 times; both shader modes pass both performance floors. The DH
repeats vary substantially. Source/library/Frozen identity checks pass and 25
generated copies are retired. No isolated transaction speedup is proven.
Receipt: `validation/native-section-counters-final-20261009/summary.json`.

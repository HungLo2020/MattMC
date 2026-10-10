# Rust light-map publication

Canonical block/sky storage now uses the Rust owner in
[`lighting/maps/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/lighting/maps).
Publication retains a shared 64-shard root. A subsequent update detaches the
root and only its modified shard, instead of cloning the complete Java section
map. Sky column tops and their defaults use the same ownership policy. Java
still orchestrates the light engine and section callbacks.

The temporary Java bridge holds sparse CPU object-identity slots, preserving
`DataLayer` identity across snapshots and cache hits. Rust retains the typed
light owners. Slots become reusable only after their last native map/cache pin
retires and Java clears the corresponding object reference. Removal resolves its returned identity
under the shared retirement lock; another snapshot cannot clear or reuse that
slot during the return handoff. Normal snapshots
and reads do not enumerate or reconstruct a Java map. Exact canonical block/sky
scalar consumers also sample their retained owners directly in Rust, including
missing-layer sky traversal. Custom storages, virtual layers and escaped arrays
retain Java callbacks. Normal reads drain at most 64 retired identities,
including idle scalar reads. Visible-map reads share a native read lock; updating
cache reads retain the original caller exclusion. Package-private
verification projections are separate from gameplay.

## Contracts

- Public constructors supplied with Java maps retain their original aliases,
  custom map behavior and Java copies. Only owned canonical factories migrate.
- Snapshot copies reset the two-entry cache and enable it, including copies of
  a disabled cache. Preserve Frozen's `Long.MAX_VALUE` empty-cache sentinel.
  Set/remove do not clear cached identities. Explicit layer copying uses a raw
  lookup and retains virtual `DataLayer.copy()` dispatch.
- Null values retain membership. Snapshot layer identities are shallow-shared;
  explicit layer copies detach. Do not replace these rules with byte snapshots.
- Sky copies reset the top map's default to the current lowest Y, even if the
  source's default was customized independently.
- Mutable light-array escape invalidates the old typed owner. Previously leased
  generations remain alive; subsequent Java reads observe the escaped array.
  Re-adoption creates a fresh owner. Retained map entries containing an invalid
  owner must decline direct native reads until replaced.
- Queued light still precedes visible storage. Terrain rebuild captures retain
  the original layer references, then the existing
  [bulk consumer](../../rendering/RUST-TERRAIN-LIGHTING.md) leases their current
  generations. Capturing generation bytes earlier would change fill semantics.
- All handles are CPU owners in the existing Rust library. Preserve original
  caller exclusion and publication sequencing; this adds no GPU ownership path.

## Verification and current limits

The suite, runtime, profile and artifact-retirement outcomes below are committed
author reports for the named builds. This documentation review inspected source
and these reports, without rerunning Java/Rust suites, clients or benchmarks or
independently inspecting the unbundled runtime receipts. Keep the earlier
`7580a46e` and integrated `b7297d06` evidence separate.

The focused production suites pass 29 Rust and 26 Java checks, including
retained generations, queue/publication ordering, the bulk terrain consumer
and both scalar consumers. The 4,096-operation actual Frozen map fixture
preserves identity/cache/default behavior. Another actual Frozen fixture covers
1,024 scalar block/sky samples, including extreme packed positions, missing
layers, sky enablement, allocated nibbles and arbitrary lazy defaults.
These scalar fixtures use a chunk getter that returns null and an
`EmptyBlockGetter` level view; they do not exercise a complete loaded-world lighting
lifecycle. The concurrent retirement test covers distinct snapshots sharing
identity pins, while mutation of the same map still requires caller exclusion.

A concurrent-snapshot regression reproduced premature CPU-slot retirement.
Removal now holds the shared retirement lock through result resolution; the
20,000-cycle Java regression passes. Native tests cover 100,000 retirement
cycles with immediate retirement acknowledgements and bounded slot tables in
that fixture; this is not a process-wide or long-session memory bound. The full suites pass 2,467 Rust tests (three ignored) and 1,826 Java tests
(two skipped), with six JNI executors mapping release `7580a46e` and all
source/library/Frozen/prompt guards passing. Validation was intentionally
interrupted after reproducing an external `super(null)` constructor ambiguity.
The new native constructor is now package-private; an external-subclass
regression preserves the original protected API. Final Java validation passes
1,827 tests (two skipped), with six actual JNI executors matching the library.
Rust source/library are unchanged, so the preserved full Rust result applies.

For pre-sync release `7580a46e`, all seven lifecycle cases and both manually reviewed vanilla/Iris+DH settled
pairs pass, with zero VUIDs, exceptions and owned clients left running. All 16
ABAB timing runs contain exactly 6,000 frames and pass workload/integrity checks.
Median Current/Frozen FPS: vanilla 1,099.8/1,158.0, DH 737.2/661.9,
shaders 348.3/318.0, shaders+DH 253.8/228.25. Vanilla fails both average FPS
and p99 (4.090/3.114 ms); the other three modes pass both measures.
This is not overall performance acceptance or an isolated migration speedup.
Diagnostic settled pairs do not prove ordinary bulk-input pixels or temporal
terrain correctness.

For pre-sync release `7580a46e`, four ordinary eight-second flight profiles pass movement, process, cleanup and
source/library/Frozen/prompt checks; all twelve F3 position captures were
reviewed. Both sides cross seven chunk columns, with forward/return endpoints
differing by at most 1.089 blocks. Weighted Java allocation is 0.757/2.369 GB
Current/Frozen. The map-copy path samples 1.05/74.45 MB; Current's remaining
sample is an FFM lease, while the preceding Current fog build sampled 89.13 MB.
These observations exclude native allocation and do not isolate an FPS gain.
Native scalar reads appear in the CPU profile; larger diagnostic stacks include
translucent sorting and validation. Short RSS windows are not long-session
memory proof. Four completed profile copies and 25 completed validation copies
were retired; the interrupted fixture remains preserved.

Upstream `81440bf19` subsequently changed renderer resources, allocation and
recording tools. The combined release `b7297d06` passes a fresh full Rust suite
(2,467 passed, three ignored) and full Java suites (1,829 tests, two skipped),
with six actual JNI workers loading the exact library. Both new settled pairs
were manually reviewed and pass their diagnostic checks; Iris+DH coverage
passes. All 16 ABAB/6,000-frame runs are clean. Median Current/Frozen FPS:
vanilla 1,327.95/1,171.55, DH 769.6/645.15, shaders 347.4/315.0,
shaders+DH 258.95/227.15. Vanilla p99 still fails (3.277/3.097 ms); the other
modes pass (DH 3.868/6.245, shaders 4.590/6.411, shaders+DH 6.004/8.191 ms).
Combined changes prevent attribution to this migration alone.

The original combined lifecycle report passes six cases and misclassifies
one INFO connection close after orderly shutdown. The shared strict classifier
now separates that exact ordered message in both lifecycle and FPS checks;
all seven retained logs pass replay. The original failed receipt remains
unchanged. The fresh affected transition passes, with the exact release library and all
source/Frozen/prompt/process-cleanup guards verified. One additional completed
runtime copy was retired.
Source, library, Frozen and protected-prompt integrity guards pass. The 25
completed runtime copies were retired; compact receipts, logs and images remain.

Four fresh ordinary eight-second profiles of integrated `b7297d06` also pass
all movement, source/library/Frozen/prompt and process-cleanup checks. All twelve
F3 images were reviewed: both CPU flights have identical endpoints; allocation
endpoints differ by at most 1.089 blocks, with both sides crossing seven chunk
columns. Weighted Java allocation is 0.823/2.398 GB Current/Frozen. The map-copy
path has no sampled Current allocation versus 94.37 MB Frozen; this does not
mean every native or FFM allocation is zero. Current section preparation samples
25.17 MB, including 8.39 MB Java occlusion-cache tables. Upstream removed the
sampled direction-array cloning and legacy packet export; native face-policy
consumption is the next candidate. Profiles exclude Rust allocations and do not
isolate an FPS gain. Four completed copies were retired. The initial PID/focus
rejection remains retained; no failed profile was relabeled or validator relaxed.
Receipt: `goal5/native-light-map-integrated-flight-profile-v2-20261009/profile-comparison.json`.

The standalone candidate's 10,000-snapshot allocation check is supplemental.
Realistic performance and long-memory acceptance remain open. Evidence:
`build/native-light-map-migration/final/`, `build/native-light-map-migration/integrated/` and
`goal5/native-light-map-flight-profile-20261009/profile-comparison.json`.

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml world::level::lighting
./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeLightMapTest' --tests '*NativeLightLayerTest' --tests '*NativeLightPropagationTest' --tests '*NativeTerrainLightingTest'
python3 DevUtils/RunWiki.py check
```

[`GenerateFrozenLightMapOracle.java`](https://github.com/HungLo2020/MattMC/blob/master/DevUtils/tests/lighting/GenerateFrozenLightMapOracle.java)
records the actual unchanged Frozen classes and rejects different class hashes.
Compile it with the existing Frozen runtime classpath; its output is raw
`frozen-light-maps.bin`. The Java and Rust fixtures losslessly gzip each recorded stream
with `mtime=0`. The adjacent `GenerateFrozenLightSampleOracle.java` records the
scalar-consumer fixture and guards its actual Frozen classes too. Do not
regenerate expectations using Current's map classes.

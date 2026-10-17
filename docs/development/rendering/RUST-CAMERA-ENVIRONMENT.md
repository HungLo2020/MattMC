# Camera immersion from retained world sections

Ordinary camera water/lava/powder-snow
queries run in Rust. The query consumes the loaded client residency owner's retained
block palettes and counters. Java supplies the camera pose, projection scalars
and fluid-tag membership; it no longer constructs the five near-plane vectors,
point positions and block positions on the admitted path.

Implementation: [immersion policy](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/camera_environment.rs),
[world consumer](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/world/level/client_residency/ffi.rs)
and [camera adapter](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/Camera.java).
This extends [client residency](../world/chunk-loading/RUST-CLIENT-RESIDENCY.md);
it does not introduce a second world-cell cache or a rendering resource owner.

## Constraints when changing this path

- Only exact stock cameras, client worlds, chunk caches, chunks and sections
  are admitted. Debug worlds, mutable section-array escape, unsupported
  palettes and partially applied packets retain the original ordered getters.
  A custom binding must decline; it must never masquerade as a missing chunk.
- Palette growth and ordinary cell/counter changes remain visible through the
  retained primary owners. Replacing a palette retires its old world-query
  binding. Scoped old readers remain valid, and copied owners remain independent.
  Packet publication admits the replacement binding. Array escape retires the
  binding before returning the mutable array to its caller.
- Preserve water's strict height comparison, lava's inclusive comparison,
  water-first admission and the original five-point ordering. Java adds a block
  Y integer to a float fluid height before promoting the result to double.
  Keep vector operation order and the float near-plane constant unchanged.
- Same-fluid checks above a cell use fluid identity/family, independently of
  tag membership. Tag bindings can change on reload; the bounded semantic mask
  is reusable only while its binding generation is unchanged.
- Stock projection inputs are reusable only for identical FOV and framebuffer
  dimensions. Custom or uninitialized projection providers retain lazy getters.
- Query-local duplicate-cell reuse ends at the native return. Do not cache
  results by frame, tick or camera pose without validating every world input.
  No world lock crosses a Java callback. Use the original client-thread world
  mutation contract; this is not a new concurrent-world snapshot guarantee.

## Verification and current limits

The October 12 paired gameplay JFRs at published `04cde9ff5` attribute 248 MB of
sampled render-thread allocation weight to `Camera.getFluidInCamera`. The
recordings include loading work: this is neither an allocation rate nor retained
memory. Original recordings and losslessly compressed event exports remain in
`build/native-world-state-profile/paired/`.

Upstream `de7d3572f` was integrated before publication, preserving its scoped
and batched native release alongside camera binding retirement. Combined native
`971d56d8` passes 2,750 Rust tests (three ignored), 2,087 Java tests (two skipped),
the five affected harness tests and all 49,152+4 Frozen camera cases. A fresh
six-JVM benchmark measures **121.58/127.65 ms Current/Frozen** (about **4.8% less
CPU**) and **0/319.29 MB Java allocation** per 1,048,576 queries. Receipts:
`integrated-rust.log`, `integrated-java-results.json` and
`integrated-oracle/results.json`. One fresh 6,000-frame vanilla pair gives
**1,273.0/1,330.2 FPS** and **3.395/3.298 ms p99**: Current misses Frozen by
4.3% FPS and 2.9% p99. This small sample does not isolate a regression or a
gameplay benefit; performance acceptance remains open. The broader gameplay
results below retain their pre-integration identity. The integrated shader+DH
world unload/reload also passes without validation errors. Its reviewed original
is heavily hazed; source/class/native, presenter and matched-workload guards
pass. Three completed copied worlds were retired. See `integrated-runtime.json`
and `integrated-integrity.json`.

Before integrating upstream `de7d3572f`, native `d885e723` passed **49,152 actual Frozen camera/world queries**,
including six projection settings and four tag bindings, plus four checks for
lazy custom/missing projection providers, mutable camera block position and
reset. The observer verifies native admission separately so compatibility
fallback cannot produce a false native pass. Its full suites passed: 2,750 Rust tests (three ignored) and 2,080 Java tests
(two skipped), including ten camera boundary checks.

Six interleaved JVMs, each with five warmup and eight measured samples, run
1,048,576 production camera queries per sample. Medians are **121.10/124.87 ms
Current/Frozen**, about **3.0% less CPU time**, with **0/319.29 MB Java heap
allocation**. This is a focused CPU result, not an isolated gameplay improvement
or a bound on total/native memory. Pre-integration receipts: `final-oracle-2/results.json`, `full-rust.log`
and `full-java-results.json` under `build/native-camera-environment/`.

Four clean 6,000-frame vanilla runs in Current/Frozen/Frozen/Current order give
median **1,298.60/1,264.80 FPS** and **3.543/3.679 ms p99**. Saved inputs and
live JVM controls match; both scoped floors pass. Two pairs with repeat variance
do not establish an isolated gameplay gain or all-mode acceptance. Two settled
image pairs and world unload/reload, resource reload and view-distance decrease
pass without validation errors. Seven originals and five Rust presenter
correlations were reviewed; the lifecycle views are heavily hazed and establish
structural evidence only. First-frame, first-turn and long-session concerns
remain open. Receipts: `iteration.json`, `image-review.json` and
`final-integrity.json`. Nine completed copied worlds were retired; originals
and failure evidence remain.

A separate 30-second Current gameplay JFR samples the new camera adapter and
native query path, with no allocation samples beneath `Camera.getFluidInCamera`
after excluding each thread's first allocation event. This supports real-game
usage; sampling cannot prove zero allocation. Its profiled frame times are
excluded from acceptance. Original JFR, analysis and losslessly compressed
events remain under `build/native-camera-environment/profile/`.

Earlier attempts remain available in that directory. The first observer launch
failed bootstrap ordering; a subsequent fixture had zero framebuffer size and
is excluded. Corrected initial native `cfa7ee79` passed behavior but measured
187.57/126.09 ms. Tag/cell reuse reached 166.56/126.04 ms, the combined query
134.84/126.23 ms, and immutable projection input reuse 120.82/126.02 ms before
removing the superseded two-call API. These records explain the boundary change;
they do not verify the final source. No failed adapter implementation remains.

With JDK 25 and the release native library:

```sh
./gradlew -PmattmcRustProfile=release test --tests '*NativeCameraEnvironmentTest'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib \
  world::level::camera_environment -- --test-threads=1
python3 DevUtils/tests/world/chunk/VerifyCameraEnvironment.py \
  --java-home /path/to/jdk25 --output build/camera-oracle --benchmark
```

Run timings with builds, profilers, tests and other clients stopped. The CPU
observer constructs a disconnected client shell but calls the actual camera,
client-world, chunk and palette implementations on both sides. It does not
replace Frozen query logic or establish real gameplay/visual parity.

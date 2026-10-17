# Shared biome temperature

`Biome.warmEnoughToRain`, `coldEnoughToSnow` and the frozen-ocean melt check use
one Rust temperature service. The public `TemperatureModifier` methods use that
same implementation. The [Java adapter](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/level/biome/NativeBiomeTemperature.java)
and [native service](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/level/biome/temperature)
keep the boundary private. Java binds actual position getters, the base temperature,
and immutable synthesis states; Rust owns temperature arithmetic, frozen-modifier
branching, thresholds, cache lookup, insertion and eviction.

## History and callbacks

Each actual `Biome` retains one cache facade per actual calling thread. Cache
identity is not derived from a registry ID, and world-generation stages do not
create another cache. All callers, including surface rules, carvers, freezing,
precipitation and rendering, observe the same history on that biome and thread.

The key is the original packed position alone. A hit skips the height and noise
queries even when the new caller supplies another sea level. The 1,024-entry
cache evicts in insertion order; hits do not refresh it. NaN entries remain
misses. Insertion rereads the live capacity after callbacks, including reentrant
calls that insert other entries. Replacing an existing NaN at capacity still
evicts the first entry, matching the displaced Java implementation.

Preserve genuine getter calls and their order: frozen modification can reread
X/Z for three noise queries, and height adjustment reads Y again after its noise
query. Do not flatten these into an eager coordinate snapshot. Native frames
hold their actual cache through the invocation. No cache lock spans a Java
callback, and a throwing callback leaves the completed prefix without inserting
the failed result. The Java arena cleanup owns only the native handle and
releases it idempotently; the cache does not retain its enclosing biome.

## Verification and remaining work

Use JDK 25 and a coherent release Java/native build:

```sh
./gradlew -PmattmcRustProfile=release test \
  --tests '*BiomeTemperatureContractTest' \
  --tests '*NativeBiomeTemperatureTest' \
  --tests '*NativeSurfacePassTest' -x testRustNative
python3 DevUtils/tests/worldgen/biome_temperature/VerifyTemperature.py \
  --pre-runtime build/worldgen/temperature/pre-ccd-runtime \
  --output build/worldgen/temperature/portable-review --screen
```

The portable driver requires the original pinned Frozen build, a complete sealed
pre-change runtime and the recorded classpaths described in the
[chunk-generation guide](../chunk-loading/RUST-CHUNK-GENERATION.md). It verifies
baseline files before and after, records actual mapped native images and class
origins, and compares public answers, raw modifier bits and cache FIFO history.
Its bounded cost screen is intermediate evidence, not completed-worldgen
performance acceptance.

The opt-in surface/freezing verifier snapshots and restores the actual native
cache in FIFO order without making temperature calls. It must restore the native
post-state even if the Java replay fails. These copies exist only for explicit
verification, not as a second production cache.

The shared noise evaluator and [leaf permutation construction](../levelgen/RUST-NOISE.md)
and [Perlin graph/factory policy](../levelgen/RUST-PERLIN.md) are native. Other
composite construction and random-source state remain Java-owned. Higher-level precipitation, freezing and snow
now use the [native weather policy](RUST-WEATHER.md) with an embedded actual
temperature child and ordered world observations. Remaining world-generation
work includes noise construction, compatibility paths and the separately
reserved region/effect handoff. See the [surface constraints](../levelgen/RUST-SURFACE.md#temperature)
and [decoration ordering](../levelgen/feature/RUST-DECORATION.md#what-runs-in-rust).

## Intermediate verification — October 16, 2026

On HungLoDE (i7-12700K, JDK 25, Rust/Cargo 1.94.0), release native image
`7ac75c26…` passed 48 focused Java checks, including six native-owner checks
and the carver cache-history comparison.
The same 11 portable contracts passed on original Frozen `7a4d1817…`, sealed
published pre-change `ccd393a9…`/`c701b5fd…`, and Current. Each runtime produced
the same 710,520-observation corpus fingerprint, covering public answers,
modifier raw bits, and final FIFO entries. Four native module checks passed;
three omitted native operations and an omitted Java frame release were caught
by their intended assertions. These are intermediate results, not completion
of world generation. Receipts: `build/worldgen/temperature/{portable-second,native-controls,java-controls}/`.

A pinned CPU 4–7 screen measured cache hits at 32.49 ns (Frozen 2.05 ns),
altitude misses at 163.85 ns (37.53 ns), and frozen modifiers at 184.93 ns
(102.30 ns). Steady samples reported zero Java allocation; that does not measure
native allocation. Cache-hit evidence has only one compilation-quiet sample
per runtime. These isolated costs remain temporary boundary/cache debt owned
by the world-generation worker, to remove as actual consumers move into the
shared native runtime; they are not a final performance pass.

A paired actual Overworld surface screen used 16 warmup and 12 measured batches
of 24 passes, identical observer bytecode, and coherent pre-change/Current
runtimes. The last batch's complete block, heightmap and ordered postprocessing
fingerprint matched. Native temperature recorded 1,171 hits and 33 misses, with
no live invocation frames afterwards. Quiet medians were 3.781 ms before and
3.876 ms Current (1.025×); sampled peak RSS was 859.9/881.1 MB, with zero JVM
swap. This bounded screen does not attribute the entire timing or RSS delta
to temperature or establish final Frozen acceptance. The first shorter surface
run had insufficient quiet samples and remains inconclusive.
Receipts: `build/worldgen/temperature/surface-screen2/result.json`.

The stable checkpoint then passed 2,554 ordinary Java tests plus six expected
skips, 193 parity tests, 11 slow tests, and 3,102 Rust tests plus nine ignored.
Fresh and CARVERS-save/unload/resume campaigns each produced 648 FULL chunks
per runtime across four dimensions. Current matched sealed pre-change in all
chunks; Overworld, Nether and End matched original Frozen. The pre-existing
162 Primordial Caves differences per phase remain explicit. All six servers
saved and exited successfully with runtime/mapping guards intact.

Injected native and Java feature failures restored all nine touched chunks
exactly and kept the failed target absent on disk. Cancellation preserved the
matched status results and released 660 tracked holders. Four-dimension stock
fresh/reopen and player pending-shutdown/own-save-reopen checks also passed.
Receipts: `build/worldgen/temperature/{stable-tiers2,full-straight,full-resume,failure-cancel,stock-binding,player-shutdown}/`.
These results do not certify gameplay/FPS, fix the separately owned lighting
issue, or complete the world-generation migration.

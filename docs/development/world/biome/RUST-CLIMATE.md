# Native climate lookup

Ordinary multi-noise biome selection searches the Java-built climate tree in
Rust. The BIOMES stage of plain multi-noise chunks also samples and searches in
Rust, in one call per chunk; see [biome fill](RUST-BIOME-FILL.md). Section generation batches its 64 queries when sampling uses known built-in
density functions, packing each sample immediately without retaining a batch of
temporary sample objects. Blending, retrogen, and extension callbacks keep their original
call order; individual default climate searches still use Rust. Single-leaf trees
return their sole Java value directly because there is no search to perform.

## Changing the evaluator

Keep native code under `src/main/rust/world/level/biome/climate/`: traversal in
`search.rs`, optional AVX2 distances in `simd.rs`, and the boundary in `ffi.rs`.
The Java bridge is `NativeClimateTree` in `world.level.biome`; the sampling
eligibility check lives beside the density types in `levelgen/BiomeSamplerSafety`.

- Preserve tree construction, child order, strict distance comparisons, and the
  per-thread previous leaf. Equal distances can depend on earlier calls. IDs refer
  to original leaves, so duplicate biome values cannot hide a selection mismatch.
- Match Java `long` overflow exactly, including extreme-input failures. Do not
  replace arithmetic with saturation or reorder/prune based on real-number rules.
- Native snapshots are immutable and validated once. Calls borrow automatic-arena
  buffers; Rust retains no pointers. Scratch buffers are thread-local. Only bounded
  small searches use critical downcalls, with no pinned Java heap.
  Changing Java tree bounds or children requires rebuilding the snapshot.
- Keep the Java callback traversal for custom distance metrics and parameter-list
  subclasses. Unknown sampling callbacks must be rejected before invoking them;
  batching them could change observable order or previous-leaf history.

## Verify a change

```sh
python3 DevUtils/tests/worldgen/VerifyRustBiome.py
./gradlew test -PmattmcRustProfile=release -x testRustNative \
  --tests 'net.minecraft.world.level.biome.*' \
  --tests 'net.minecraft.world.level.levelgen.*'
```

The driver extracts original Java from commit `5a231206da15336ed8f5e9d954ed7cca45a92274`
into ignored `build/` directories. It compares scalar and batch leaf selection
against a separately named, otherwise unchanged Java oracle, then compares stored
section biome keys using real seeded samplers in separate JVMs. No production
fallback switch or alternate checkout is used.

Timings measure direct climate lookup, including batch eligibility checks,
additional input/output arrays, packing, native calls, result mapping, and consuming
results. They exclude fixture/tree construction and unrelated density evaluation.
The benchmark conservatively charges an input-array copy that section generation
no longer needs. Workloads include nearby, random, and real seeded climate inputs.
Warmup requires stable rounds and no recent compilation; measured rounds reject
significant JIT activity. Three alternating process pairs must each be at least 5%
faster, as must the conservative bootstrap confidence bound. Raw rounds, hashes,
reference revision, JVM options, and CPU affinity are saved in `results.json`.

For full generation, the shared world driver accepts `--biomes` to include stored
biome keys alongside block, heightmap, and postprocessing hashes:

```sh
python3 DevUtils/tests/worldgen/VerifyRustSurfaceWorld.py java \
  --reference --hash --biomes --serial --workers 1 --radius 2 --rounds 2 \
  --warmups 1 --output build/biome-world \
  --classpath build/biome-migration/verification/classpath.txt
python3 DevUtils/tests/worldgen/VerifyRustSurfaceWorld.py native \
  --compare java --hash --biomes --serial --workers 1 --radius 2 --rounds 2 \
  --warmups 1 --output build/biome-world \
  --classpath build/biome-migration/verification/classpath.txt
```

Use fresh labels for additional seeds. These runs create isolated temporary worlds
and stop their servers. Add `--biome-oracle` to a native run without `--compare`
to check every scalar and section lookup against original Java in the same thread
and query order. Its `biome-oracle.json` must report zero mismatches and nonzero
section queries. This test agent never participates in performance measurements.
The oracle instruments Java climate-sampling/search entry points. The later
[native BIOMES fill](RUST-BIOME-FILL.md) performs those operations inside Rust
and bypasses those hooks, so `--biome-oracle` alone does not directly verify
its internal samples or searches. Use `NativeBiomeFillTest` for focused stage-output
and previous-leaf parity; stored-biome full-world hashes are a separate
observable.
See the [world-generation guide](../levelgen/RUST-WORLDGEN-ORGANIZATION.md)
for standalone Rust tests without renderer dependencies. Finite test corpora establish
exact agreement for tested cases, not a proof covering every possible world seed,
extension, machine, or thread schedule.

## Recorded acceptance: 2026-09-30

AMD Ryzen 5 5600G, CPU 2, OpenJDK 25.0.4.1, ZGC and compact object headers,
release Rust with AVX2. Median nanoseconds per lookup across three independent
process pairs (11 measured rounds per workload in each process):

| Tree / inputs | Original Java | Native route, including boundary | Less time |
| --- | ---: | ---: | ---: |
| Nether / nearby | 66.05 | 31.20 | 52.8% |
| Nether / random | 95.94 | 39.40 | 58.9% |
| Nether / seeded | 63.92 | 27.35 | 57.2% |
| Overworld / nearby | 606.79 | 150.65 | 75.2% |
| Overworld / random | 2972.16 | 617.41 | 79.2% |
| Overworld / seeded | 861.59 | 175.16 | 79.7% |
| Primordial / nearby | 15.26 | 4.65 | 69.5% |
| Primordial / random | 13.91 | 4.63 | 66.7% |
| Primordial / seeded | 14.05 | 4.63 | 67.0% |

Primordial uses the single-leaf shortcut, with no Rust search. All nine workloads
passed the 5% gate. The smallest conservative improvement in any process pair was
52.4%; the least favorable 95% confidence bound still showed 52.2% less time.
3 series with significant late JIT activity were discarded and warmed again.
Initialization is excluded from these steady-state measurements.

Exact comparisons passed for 3,519,000 queries in both scalar and batch mode,
plus 806,400 stored biome entries across 120 setting/preset/seed cases. Rust's
focused world-generation suite passed 27 tests; the focused Java suite passed 51.
Final timing and parity evidence is under `build/biome-migration/final/`; full-world
results, including the initial discrepancies, are in `build/biome-migration/world-results.json`.

Full generation additionally checked 3,701,823 production lookup results against
the same-process Java oracle with zero mismatches, covering both seeds and all four
dimensions. All 400 measured chunks had identical stored biomes; surface and carver
fingerprints matched throughout. Initial separate-process FULL comparisons differed
in one feature-stage chunk per seed. The first difference also occurred between two
original-Java runs; both shadow runs matched Java controls completely. Keep this
feature-stage variability visible: stable biome selection does not make the existing
feature pipeline deterministic. The broad Rust suite also reported 77 renderer-test
failures (including poisoned graphics locks); it is not claimed green by these checks.

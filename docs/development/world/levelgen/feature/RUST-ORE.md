# Rust ore geometry

## Boundary and compatibility

`levelgen/feature/ore/` owns sphere construction, containment pruning and ordered
rasterization for `OreFeature`. Production has no Java evaluator for these loops.
Java retains placement endpoints, original RNG draws, cached `Mth` float-table
factors, build-height checks, visited bits, target rules, block writes and cleanup.
`ScatteredOreFeature` and `OreVeinifier` are separate algorithms and are unchanged.

One call handles a vein and returns ordered `(x, y, firstZ, lastZ)` spans. Spans
preserve duplicate candidates and traversal order: build-height callbacks must
still run before deduplication. Output buffers grow and retry using the same
random samples, without consuming more RNG values. Each placement leases reusable
buffers until callbacks and section cleanup finish. Nested calls lease another buffer; RNG failures also release the lease.

- Keep float fractions/table factors and double operation order exact. Do not
  substitute reciprocals, fused multiply-add or approximate trigonometry.
- Axis-square reuse preserves individually rounded values and the original sums.
  A sphere marked dead cannot remove later positive-radius spheres, so its
  remaining containment comparisons can be skipped.
- Native pointers are borrowed only for the call. Java owns aligned native
  scratch buffers and immutable shape tables; Rust does not retain pointers.
- Small calls borrow Java arrays through [FFM critical access](https://docs.oracle.com/en/java/javase/25/docs/api/java.base/java/lang/foreign/Linker.Option.html#critical(boolean)).
  Rust validates at most 16 spheres with bounded coordinates and radii before
  scanning; there are no allocations, blocking operations or Java callbacks.
  Rejected inputs and larger veins use the ordinary call with native buffers.
- AVX2 paths preserve each lane's original arithmetic. Tiny spheres consider
  their two nearest block centers per axis; larger spheres return exact occupied
  spans. SIMD is runtime-dispatched, with a scalar Rust path for other CPUs or
  inputs outside the vector bounds.
- Codec sizes are 0–64; larger programmatically constructed configurations use
  dynamically sized native storage. Empty veins do not cross the boundary.

## Configured target tags

The custom `ore/...` configurations used by Dry Midlands and Primordial Ocean
reference `stone_ore_replaceables` and `deepslate_ore_replaceables`. Keep these
IDs aligned with the bundled block tags: native geometry only supplies candidate
coordinates, and Java still rejects any block that does not match the target.
An unresolved tag can therefore yield no ore even when the geometry is correct.
Do not widen the host sets to unrelated custom terrain while repairing an ID.

Run the registry/resource regression separately from geometry parity:

```sh
./gradlew test --tests net.minecraft.world.level.levelgen.feature.CustomOreTargetTagsTest
```

It follows biome → placed feature → configured feature references and exercises
native placement in deterministic, fresh in-memory chunk sections. The actual
configured heights, counts, biome filters, air-exposure rules and palette writes
run, but the fixture supplies solid host terrain; it does not run a server's
complete noise/carver/decoration pipeline. Geodes and other target families are
separate routes. Resource changes do not retrofit existing generated chunks.

## Verify this subsystem

```sh
python3 DevUtils/tests/worldgen/VerifyRustOre.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustOre.py --forks 3
```

The driver builds release Rust, checks the renamed Java oracle against Git
`594da3f15e9cab3284567af65330aad376e7a871`, and runs only ore-specific tests.
It compares ordered candidates, complete placement traces, final written block
states and subsequent RNG state. Coverage includes every codec size, world-border
coordinates, strict sphere boundaries, overlapping spheres, buffer growth,
custom larger sizes, Legacy/Xoroshiro RNGs and the game’s `WorldgenRandom` wrapper,
air checks, height limits, denied writes, concurrent GC and nested placement/RNG
callbacks.

The performance gate compares the **migrated geometry operation**, including
endpoint/RNG work, native transitions, input transfer, output copying where needed,
and consumption of every candidate coordinate. Its Java oracle uses the original
streaming loops with no added result array; the driver checks those loops against
Git. No prebuilt sphere corpus or native-only input preparation is timed away.

Separate measurements cover complete `OreFeature.place()` calls, including
rules, real chunk palettes, reads/writes, air checks and section cleanup. The
in-memory world adapter excludes server scheduling and unrelated generation.
Repeated placements write STONE back to STONE to keep equal inputs while executing
writes; correctness tests separately verify changed blocks and palette growth.
Solid and sparse-air terrain are reported separately. These are subsystem timings,
not an in-game frame-rate or full-chunk speedup claim.

Benchmarks use `WorldgenRandom` over Xoroshiro, 128 origins and 32 seeds per origin,
and all 12 sizes registered for the built-in `OreFeature` configurations.
Each process uses `-Xbatch`, at least three seconds of warmup, stable recent
rounds, and zero measured JIT-compilation time. Three process pairs alternate
Java/native order on the same CPU. For every measured geometry size, each pair and
the upper 95% bootstrap time-ratio bound must be at most 0.95. Complete-placement
results are reported separately. Random-input frequencies are identical even when
timing repetition counts differ. Raw samples, environment and source/library
hashes are under `build/ore-migration/acceptance/`.

## Verified results (2026-10-01)

Release acceptance passed on Ryzen 5 5600G (AVX2), Linux x64, OpenJDK
25.0.4.1. Eight Java tests and four Rust tests passed: 16,640 seeded geometry
fixtures / 11,064,419 ordered candidate points, and 585 complete placement
comparisons / 946,592 recorded events, with zero differences. Edge, larger-size,
concurrency and reentry cases are additional to those totals. This is tested
parity coverage, not exhaustive enumeration of every possible input.

Times are median process medians in nanoseconds per vein. Rust includes the
Java caller, RNG, transfers, downcall and output consumption. The final column
uses the upper 95% time-ratio bound, giving the conservative measured saving.

| Size | Java ns | Rust + boundary ns | Time saved | Conservative saving |
| ---: | ---: | ---: | ---: | ---: |
| 3 | 183.5 | 139.4 | 24.0% | 18.7% |
| 4 | 237.1 | 172.8 | 27.1% | 21.7% |
| 7 | 450.7 | 337.0 | 25.2% | 24.5% |
| 8 | 509.2 | 364.3 | 28.4% | 27.5% |
| 9 | 602.5 | 476.3 | 20.9% | 20.0% |
| 10 | 682.9 | 595.2 | 12.8% | 10.0% |
| 12 | 876.3 | 792.8 | 9.5% | 9.5% |
| 14 | 1071.9 | 950.1 | 11.4% | 10.6% |
| 17 | 1416.9 | 1261.6 | 11.0% | 9.7% |
| 20 | 1833.8 | 1499.3 | 18.2% | 15.6% |
| 33 | 4166.4 | 3161.7 | 24.1% | 20.1% |
| 64 | 16702.1 | 11805.3 | 29.3% | 28.8% |

All 12 geometry cases passed the 5% gate in all three pairs and their confidence
bounds; the smallest individual-pair saving was 9.5%. Complete-placement median
time reductions were 6.7–23.6% across the 24 fixtures. **Size 4 / solid terrain did
not consistently clear 5% for complete placement**: its three reductions were
10.4%, 8.7% and 3.7% (8.7% aggregate median reduction). The other 23 placement
fixtures passed the same statistical gate. This does not change the geometry
result; keep the two scopes explicit when citing performance.

The final source and release-library hashes matched the acceptance record.
Scalar fallback correctness is tested; speed on other CPUs is unmeasured.
Raw samples and all placement timings are in `build/ore-migration/acceptance/results.json`;
`REPORT.md` in that directory provides a readable summary. No renderer or
unrelated project tests are part of this acceptance.

# Rust geode density fields

## Ownership and compatibility

`world/level/levelgen/feature/geode/` evaluates the body and crack densities for
`GeodeFeature`. One ordinary FFM call covers the bounded grid, replacing a noise
transition per candidate block. Java retains the original random draws, points,
thresholds, providers, replacement predicates, block writes, fluid ticks and bud
placement. This migrates the numeric evaluation slice of geode generation.

- Preserve X-fastest traversal, point order and double operation association.
  Distance subtracts coordinates **as doubles**, then squares and sums them.
  Inverse square root is `1.0 / sqrt(distance + offset)`. No approximate math,
  reassociation or fused multiply-add is permitted.
- Reuse the existing noise evaluator and its immutable Java-created seed state.
  Precomputation must consume no randomness and perform no world/provider calls.
- Java owns aligned buffers. Rust validates bounded lengths and borrows pointers
  only for the call. It retains no pointers and makes no Java callbacks.
- Per-thread scratch grows to at most 65,536 cells (about 2 MiB for native and heap
  output combined). Its lease spans Java placement callbacks. Reentrant calls
  use the original streaming compatibility path; failures release the lease.
- The native path accepts ordinary immutable derived points, at most 64 body and
  64 crack points, nonnegative offsets, a finite noise multiplier in `[0,1]`, and
  grids with each dimension at most 64 and 512–65,536 cells. Smaller custom grids
  keep the streaming path to avoid fixed boundary costs. Every registered
  geode fits these limits. Custom coordinate classes, oversized grids, wrapped
  endpoints producing huge ranges and out-of-range numeric inputs use the
  original Java numeric loop. Keep that compatibility behavior intact.

## Verify this slice

```sh
python3 DevUtils/tests/worldgen/VerifyRustGeode.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustGeode.py --forks 3
```

The driver builds release Rust, checks the renamed Java placement oracle and
streaming numeric oracle against Git `7af3a1594956f41ed57c3bf67d11ce006e61530f`,
and checks that production changes only the numeric batch hook. It runs focused
geode tests: raw density bits, ordered placement reads/writes/ticks, final blocks,
subsequent RNG draws, valid codec point counts/offsets, coordinate extremes,
compatibility cases, exceptions, reentry and concurrent GC. The placement adapter
uses real chunk palettes and explicit tag membership. No renderer is involved.

Performance compares the original streaming numeric loop with the complete Rust
caller: eligibility checks, point packing, noise-state validation, FFM transition,
result copying and ordered consumption are timed. Both sides use the **existing
Rust-backed NormalNoise**, which predates this migration. RNG and world operations
remain Java and are covered by placement parity rather than this timing gate.
Do not describe the numeric result as a full-feature, chunk-generation or FPS gain.

All 15 registered configurations have three numeric parameter families. Five
benchmark cases cover those families and both cracked/uncracked inputs where
applicable. Two additional cases cover the smallest supported cube with one
body point and the largest cube with 20 body points and cracks. All cases use
the same 32 seeded grids on both sides. Each case/mode uses
a fresh JVM, at least three seconds of stable warmup, 11 measured samples and
zero measured JIT compilation. Three pairs alternate process order on one CPU.
Every pair and the upper 95% bootstrap time-ratio bound must be at most `0.95`.
Raw measurements, parity counts, environment and source/library hashes are saved
under `build/geode-migration/acceptance/`.

## Verified results (2026-10-01)

Release acceptance passed on Ryzen 5 5600G, Linux x64 and OpenJDK 25.0.4.1.
Seven Java and three Rust tests passed. Registered inputs matched 34,499,520 raw
density doubles; 180 complete placements matched traces, blocks and RNG state.
Another 300 edge fixtures and concurrency/compatibility cases also passed.

Times are median process medians per numeric grid, including the Rust caller
and boundary costs. Conservative savings use the upper 95% time-ratio bound.

| Workload | Java ms | Rust + boundary ms | Time saved | Conservative saving |
| --- | ---: | ---: | ---: | ---: |
| Amethyst, cracks | 3.3121 | 2.6816 | 19.0% | 18.6% |
| Amethyst, no cracks | 2.9684 | 2.3764 | 19.9% | 17.5% |
| Ore, cracks | 3.3128 | 2.6849 | 19.0% | 18.2% |
| Ore, no cracks | 2.9176 | 2.3661 | 18.9% | 18.0% |
| Coral / red sand | 2.8204 | 2.2853 | 19.0% | 17.3% |
| 512 cells, 1 point | 0.0364 | 0.0304 | 16.6% | 16.3% |
| 64,000 cells, 20 points + cracks | 10.1937 | 8.2363 | 19.2% | 18.9% |

All seven cases pass in all three pairs and their confidence bounds. Worst individual paired saving: **16.59%**. Minimum conservative saving: **16.28%**.

Raw evidence and the readable report are in
`build/geode-migration/acceptance/results.json` and `REPORT.md`. Finite test
coverage supplements the preserved arithmetic and order; it is not exhaustive
enumeration of every input. Compatibility paths and full-feature performance
are outside this numeric speedup claim. Speed on other CPUs is unmeasured.

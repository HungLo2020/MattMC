# Rust canyon ellipsoid evaluation

## Ownership and correctness

`world/level/levelgen/carver/canyon/` evaluates the built-in canyon
shape predicate for one chunk-clipped ellipsoid. On chunks the
[Rust carvers stage](RUST-CARVERS.md) takes, canyons are carved there instead,
whole; this path serves the chunks that keep Java's carvers. Java retains tunnel creation,
random draws, bounds, live carving masks, biome/aquifer calls, block writes and
postprocessing. Output columns preserve X/Z/descending-Y traversal.

- Preserve Java double operation order: `(X² + Z²) * floatWidth + Y² / 6.0`. Float factors are
  promoted exactly. No reciprocal approximation, reassociation or FMA is allowed.
- Scalar and runtime-selected AVX2 paths use the same comparisons. Canyon masks
  can contain gaps; consume their individual runs in descending Y order.
- Precompute only the pure shape. Probe and update the live `CarvingMask` immediately
  before each original `carveBlock` callback. Preserve its per-column mutable flag
  and aggregate return value. Mask callbacks may affect later mask decisions.
- Only private built-in checker metadata and the standard immutable context use
  native evaluation for batches of at least 512 cells and 16 Y rows. Custom checkers/contexts, invalid numeric inputs, oversized
  spans, tiny batches and reentry retain the literal original streaming loop.
- Java owns bounded per-thread scratch and its callback-spanning lease. Rust checks
  lengths, indices and alignment, borrows disjoint buffers, and retains no pointers.
  Exceptions release the lease. Maximum height is 2,048; output is at most 8,192
  words, about 128 KiB for native and heap output together.
- Batches with at most 64 Y rows and 8,192 geometric decisions use a bounded
  critical FFM entry borrowing Java arrays. Rust independently enforces these limits
  and performs no allocation, blocking or callbacks. Larger calls use ordinary
  FFM with copied buffers, allowing GC during evaluation. Copy only the needed
  factor rows and rebase their index. A separate startup call initializes CPU
  dispatch before short calls borrow arrays; the bounded Rust entry performs no CPU probing.

## Verify this slice

```sh
python3 DevUtils/tests/worldgen/VerifyRustCanyon.py --parity-only
python3 DevUtils/tests/worldgen/VerifyRustCanyon.py --forks 3
```

The driver builds release Rust and audits frozen Java oracles against Git
`7af3a1594956f41ed57c3bf67d11ce006e61530f`. Only registration boilerplate is removed
from the original carver test classes. Production tunnel/RNG producers are checked
for changes beyond attaching pure checker metadata.

Focused tests compare packed decisions, ordered callbacks, complete original
carving traces, final masks/blocks, postprocessing, subsequent RNG draws, word
boundaries, coordinate extremes, custom checkers, exceptions, nested calls and GC.
The complete-carve adapter uses real chunk palettes, explicit replacement-tag
membership and a deterministic aquifer. It exercises this subsystem without a
renderer or full-game test suite. Finite coverage supplements preserved arithmetic
and ordering; it does not enumerate every possible input.

Performance includes the actual ellipsoid caller: bounds, eligibility, metadata
packing, factors, native transitions/copies, mask checks/updates and ordered
coordinate/column-state consumption. Only the terminal world operation is replaced
by the same checksum consumer on both sides. The registered canyon profile includes
small compatibility calls; 16/32-row solid and gapped profiles, tall batches and
occupied masks add boundary cases. Each mode/case runs in a fresh JVM with at
least six seconds of stable warmup, zero measured JIT
compilation and 11 samples. Three process pairs alternate order on one CPU.
Every paired ratio and the upper 95% bootstrap ratio must be at most `0.95`.
Raw logs, source/library hashes and results are under `build/canyon-migration/acceptance/`.

## Verified results (2026-10-02)

Release acceptance passed on Ryzen 5 5600G, Linux x64, OpenJDK 25.0.4.1 and
rustc 1.93.1. Seven Java and four Rust tests passed with zero mismatches:
2,178 numeric fixtures / 1,995,015 decisions, plus 512 complete carves (128
canyon and 384 cave/Nether controls), matching masks, block traces and RNG state.

Times are median process medians per ellipsoid caller, including the boundary.

| Workload | Original Java µs | Rust + boundary µs | Time saved | Conservative saving |
| --- | ---: | ---: | ---: | ---: |
| Registered canyon, mixed sizes | 1.8603 | 1.6644 | 10.53% | 9.39% |
| 32 rows, dense (2,048 cells) | 9.0339 | 6.2764 | 30.52% | 20.15% |
| 32 rows, gapped (2,048 cells) | 5.5872 | 5.0563 | 9.50% | 7.56% |
| 16 rows, dense (1,024 cells) | 3.9461 | 3.5332 | 10.46% | 8.07% |
| 16 rows, gapped (1,024 cells) | 2.8558 | 2.6516 | 7.15% | 5.57% |
| Tall, 66,048 cells | 583.8412 | 485.4715 | 16.85% | 14.30% |
| Registered canyon, occupied mask | 0.8967 | 0.7376 | 17.74% | 17.07% |

Every case passes all three pairs and its confidence bound. Worst individual
paired saving: **5.81%**; minimum conservative saving: **5.57%**.
Raw evidence and the detailed report are in
`build/canyon-migration/acceptance/results.json` and `REPORT.md`.
This historical result measures an intersecting-ellipsoid caller; it does not
measure complete carving/chunk generation, FPS or performance on other CPUs.
Cave predicates remained Java in that migration. The later
[Rust carvers stage](RUST-CARVERS.md) now owns eligible cave, Nether-cave and
canyon carving, with its own scoped measurements.

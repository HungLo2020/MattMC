# Structure terrain adjustment

`density/beardifier/` evaluates complete Beardifier terrain cells in Rust: box
distances, BURY, BEARD_THIN, BEARD_BOX, ENCAPSULATE, and ordered junction sums.
`layout.rs` validates packed geometry, `evaluate.rs` owns arithmetic, and `ffi.rs`
owns the borrowed-buffer boundary.

## Boundary and compatibility

- The built-in structure factory binds `NativeBeardifier`. An exact `NoiseChunk`
  filling a complete cell uses one ordinary downcall, with native input/output
  buffers and a copy back into the Java result array. Cells completely outside the
  affected box are cleared without a call. No Java heap is pinned during evaluation.
- Geometry is packed on every fill. Structure-box mutations between fills remain
  visible. Scratch buffers belong to the calling thread; Rust retains no pointers.
- Preserve Java integer wrapping, double operation order, contribution order and
  the original inverse-square-root formula. Do not reassociate sums or add FMA.
  The kernel table comes from the original Java initialization, preserving its
  exact float bits across math-library implementations.
- Java retains structure discovery and scalar/custom-provider evaluation. The
  public constructor keeps live-collection behavior. Subclasses, custom boxes or
  junctions, non-cell providers, and irregular arrays retain their original path.
  This migration covers the production cell-fill workload; it is not a scalar
  cutover. Final provider coordinates and the postincremented array index match
  the original traversal.

## Verify a change

```sh
python3 DevUtils/tests/worldgen/VerifyRustBeardifier.py
# Correctness only:
python3 DevUtils/tests/worldgen/VerifyRustBeardifier.py --parity-only
```

The test-only `JavaBeardifier` is the original class from revision
`594da3f15e9cab3284567af65330aad376e7a871`, with only its name changed. The driver
checks that fact before testing. Comparisons use raw double bits and check final
provider state. Randomized geometry also covers every adjustment, integer
extremes, kernel edges, moved boxes and concurrent readers.

The committed corpus contains geometry from ten seed-generated villages,
outposts, ancient cities and trial chambers from the earlier profiling worlds.
It preserves piece/junction order and records source-region hashes. Each
structure is replayed independently at two chunk positions, using the original
chunk-distance filtering. It is a component replay, not a complete-world test.
Regenerate from saved profiling regions with:

```sh
python3 DevUtils/tests/worldgen/ExtractBeardifierCorpus.py \
  --profile build/worldgen-profile-20260930 \
  --output src/test/resources/worldgen/beardifier/structures.json
```

Timings use the production cell-fill API and include per-chunk evaluator setup,
geometry marshalling, downcalls, output copies, provider-state updates and result
consumption. Both active cells and full chunk height (including inactive cells)
are measured. File/registry loading and initial shared kernel allocation are
outside warmed timings. Every one of three alternating process pairs and the
upper 95% bootstrap ratio bound must show at least 5% less time. Accepted rounds
must report zero JIT compilation. Raw evidence is under
`build/beardifier-migration/acceptance/`.

## Why this target

The existing Overworld profile recorded Beardifier work inside terrain-cell
fills and substantial iterator allocation there. Cell batching provides a bounded
native boundary without moving block storage or rendering. That historical profile
identified the candidate; the focused comparison above measures this migration.

## Recorded acceptance — 2026-09-30

Release Rust, OpenJDK 25.0.4.1, Ryzen 5 5600G, pinned CPU 2. Median-of-process-
medians, ns per output, with the costs listed above included:

| Adjustment / cells | Original Java | Native path | Less time |
| --- | ---: | ---: | ---: |
| BEARD_THIN / active | 296.83 | 103.96 | 65.0% |
| BEARD_THIN / full height | 65.93 | 22.44 | 66.0% |
| BEARD_BOX / active | 84.71 | 36.09 | 57.4% |
| BEARD_BOX / full height | 18.32 | 7.03 | 61.7% |
| ENCAPSULATE / active | 848.59 | 431.55 | 49.1% |
| ENCAPSULATE / full height | 198.83 | 98.89 | 50.3% |

All three process pairs and all six confidence-bound gates passed. The smallest
paired reduction was 47.0%; the largest upper 95% native/Java time-ratio bound
was 0.5303, below the required 0.95. All accepted rounds reported zero JIT
compilation. This measures the cell-fill subsystem on this machine.

All 3,108,880 raw-bit comparisons passed: 1,966,080 saved-geometry outputs plus
1,142,800 randomized, boundary, mutation, concurrent and provider-state cases.
These tests include BURY and NONE; their performance is not represented by a
separate saved-geometry workload. This is measured coverage, not an exhaustive
proof for every possible input. The results artifact records source, corpus,
native-library and original-oracle hashes.

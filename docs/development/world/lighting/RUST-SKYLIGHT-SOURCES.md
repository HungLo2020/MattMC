# Rust skylight-source reconstruction

`ChunkSkyLightSources.fillFrom()` now uses `world/level/lighting/skylight_sources/`
for normal packed chunks. This reconstructs the lowest unblocked skylight entry
in each column. Light propagation queues and incremental `update()` stay Java.
The bridge lives beside Java chunk storage to borrow its package-private data.

## Constraints when changing this code

- Preserve `getLightBlock() != 0` and the original downward/upward face-union
  decision. The bridge builds a truth table with the original Java shape operation
  from immutable cached block-state faces. Exact box lists share face IDs;
  coordinates are never quantized. Rust uses the table, not approximate geometry.
- Carry the previous block's downward face across nonempty section boundaries.
  An all-air section resets that face without testing an edge, matching the
  original shortcut. A fully open column resets to the below-world sentinel;
  unlike heightmap priming, it does not keep its old value.
- Preserve every packed padding bit, including padding in complete words.
  Java publishes ordinary scan results only after successful evaluation.
- Use ordinary FFM calls for section scans, with copied words, refreshed local
  palette mappings, immutable global descriptors and thread-owned scratch.
  Rust borrows buffers, allocates nothing and retains no pointers.
- Empty chunks use a separate Rust bulk clear through critical heap access.
  This call validates at most 128 words and performs no allocations, blocking
  operations or callbacks. Do not extend it with unbounded work.
- Follow existing chunk ownership: sections/destination must not be mutated
  concurrently with a reconstruction. Independent readers have separate scratch.
- Standard `ProtoChunk` and `LevelChunk` sections use Rust. Custom readers,
  palettes, storage/state subclasses, unsupported dimensions, late registry
  additions and invalid packed IDs retain the original compatibility path.
  Fallback precedes publication, preserving exceptions and partial writes.

The palette snapshot accessors shared with heightmap priming are named
`dataForNativeScan()` and `registryForNativeScan()`. Keep them package-private.

## Verify a change

```sh
python3 DevUtils/tests/lighting/VerifyRustSkyLightSources.py --parity-only
python3 DevUtils/tests/lighting/VerifyRustSkyLightSources.py --forks 3
```

The driver pins the original `fillFrom()` and every shared helper to Git
`3b709a204`. Tests compare raw packed outputs, all visible columns and their
maximum, then check incremental updates after priming. Coverage includes every
registered state and distinct vertical shape pair, fluids, slabs/stairs, air
variants, section edges/gaps, sentinel behavior, padding, dimension limits,
malformed IDs, custom callback traces, real/debug LevelChunks, parallel readers
and GC during critical clears. The saved-terrain corpus reuses the unchanged
16 FULL chunks from two seeds and four dimensions in the heightmap fixtures.
No server or renderer is required.

Benchmarks time the complete `fillFrom()` caller: eligibility, palette mapping,
input transfers, downcalls, publication and consuming packed output. Source
construction, registry/immutable-table initialization and fixture loading occur
before timing on both sides. The native route is asserted; there is no output
cache or silent Java fallback in timings.

Three independent JVM pairs alternate order, use the same CPU and warm until
compilation stops and recent timings stabilize. Measured rounds require zero
JIT-compilation time. Every workload must use at least 5% less time in every pair
and at its upper 95% bootstrap ratio bound. Raw samples, checksums, source/library
hashes and a report live under `build/skylight-sources-migration/acceptance/`.
Results measure this reconstruction slice, not full lighting or chunk generation.

## Status

Release acceptance passed on 2026-10-01: Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1. Ten Java tests and three Rust tests passed with zero differences.
The catalog covers all 31,809 registered states and all 2,256 pairs of original
vertical face objects (28 compact faces). Additional tests include 256 seeded
fixtures / 65,536 columns, 125 all-state fixtures and 64 saved-terrain comparisons.

All 12 timing cases passed in every independent JVM pair and their confidence
bounds. The smallest individual-pair reduction was **45.9%**, with a conservative
95% bound of **45.8%**. Saved-terrain median process timings per complete call:

| Dimension | Original Java | Rust including boundary | Less time |
|---|---:|---:|---:|
| Overworld | 43.02 µs | 10.28 µs | 76.1% |
| Nether | 4.23 µs | 2.09 µs | 50.5% |
| End | 22.15 µs | 4.84 µs | 78.1% |
| Primordial | 4.31 µs | 2.04 µs | 52.8% |

The initial general empty-section path was rejected for an empty-chunk regression.
The final bounded clear takes 85 ns versus 372 ns for Java, including the call
boundary (77.1% less time). Shape-heavy stress results are larger because the
truth table avoids repeated shape-union work; they are not normal terrain averages.

`build/skylight-sources-migration/acceptance/REPORT.md` gives all 12 workloads;
`results.json` preserves raw rounds, checksums and hashes. Final source/library
hashes matched the acceptance record. These are warmed measurements on this
machine and corpus; finite parity tests cannot enumerate every possible input.

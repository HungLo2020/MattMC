# Rust closest collision point

`VoxelShape.closestPointTo()` now moves a complete numeric query to Rust for
supported complex collision grids. Free-position searches use this query when
placing entities around obstacles. This is another step toward native world
physics; it does not change renderer code.

The kernel is in
[`world/phys/shapes/closest_point/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/phys/shapes/closest_point).
[`NativeVoxelClosestPoint`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/phys/shapes/NativeVoxelClosestPoint.java)
copies current occupancy and coordinates, makes one ordinary FFM call, and
constructs an independently owned Java `Optional<Vec3>`. Rust visits the
established greedy boxes and immediately clamps and reduces each ordered box
to select the nearest point. Proven full occupancy or a verified partial cuboid uses a native single-box
entry point with current endpoint values, preserving the original snapshot
without exporting unnecessary arrays. No result or shape data is cached.

## Compatibility and ownership

- Preserve the public empty-shape check, including empty shapes with a null query.
  Nonempty null queries still throw through the original caller.
- Native inputs are exact `ArrayVoxelShape`/`CubeVoxelShape`, exact bitset grids,
  and exact `Vec3` queries. Axes must be 1–256, with 512–65,536 cells. Custom
  shapes, grids, query subclasses and unsupported sizes retain virtual Java calls.
- Only exact `DoubleArrayList` and `CubePointRange` coordinates qualify.
  Current array values are copied each call; cube coordinates use the same
  double `index / parts` operation. Custom and shifted lists retain their
  original reads, exceptions and reentry behavior.
- Nonfinite query or consumed coordinate values use the original Java path to preserve
  NaN payloads. Finite signed zeros, reversed bounds, subnormals and overflowed
  squared distances are supported. Clamp follows Java's conditional and
  `Math.min` rules, including negative zero.
- Keep the original BitSet snapshot constructor and bounds reads. Ignore logical
  padding and stale bounds during extraction. Bits outside supported storage
  select compatibility. A grid with only out-of-dimension bits preserves the
  original nonempty caller's exception rather than returning an empty point.
- Keep greedy Y/X/Z box order and use strict `<` distance comparisons. Equal
  distances, including infinity, keep the first box. Preserve double operation
  order without FMA or reordered sums. Cached best distance is safe because
  supported inputs have no virtual query callbacks or mutable result objects.
- A projection shortcut requires increasing coordinates, an occupied incident
  voxel, and a unique **IEEE double** distance/point. Compare the nearest distinct
  coordinate alternatives using the same squared-distance association; reject
  rounding ties, underflow, overflow and negative-zero ambiguity. If it declines,
  Rust retains the ordered greedy traversal. Returning a real-number nearest
  point alone would not preserve the original floating-point tie behavior.
- Stored cuboid bounds are hints: check their ranges, exact occupancy count and
  every covered row (at most 256) before using the single-box entry point.
  Full-Z rows are contiguous, so one range check per X proves their whole Y block.
- Buffers are disjoint and aligned; the ordinary downcall permits GC, allocates
  nothing, invokes no Java callbacks and retains no pointers. Only private
  numeric workspace is mutated. Scratch is guarded against overlapping use
  and retains about 21 KiB of numeric buffers per thread. Concurrent mutation of a live
  input remains unsupported under the original unsynchronized ownership rules.

## Verify

```sh
python3 DevUtils/tests/physics/VerifyRustVoxelClosestPoint.py --parity-only
python3 DevUtils/tests/physics/VerifyRustVoxelClosestPoint.py --forks 3
# Fresh JVMs for a selected workload:
python3 DevUtils/tests/physics/VerifyRustVoxelClosestPoint.py --forks 3 \
  --case full_8 --output build/voxel-closest-point-migration/isolated-full
```

The Linux driver pins the complete pre-migration public query to Git `8b9173b39`
and permits only the exact closest-point and separately pinned
[ray-intersection dispatch](RUST-VOXEL-RAYCAST.md). It checks the existing Java geometry and
numeric helpers, and pins the mechanical visitor extraction from the previous Rust traversal:
only endpoint delivery changes; scanning and greedy merging stay identical. Focused tests include affected box, join and rotation paths;
no graphics context or whole-game tests are needed.

Parity compares raw result bits against both the pre-migration query (which
already uses Rust box extraction) and fully Java box extraction. Tests cover
exhaustive small grids, complex patterns, all registered collision states,
derived joins, ties, signed zeros, reversed coordinates, nonfinite fallback,
padding, changing inputs, custom callbacks/exceptions/reentry and concurrent GC.
Finite tests supplement the equivalence argument: the established extractor
emits the same ordered endpoints; each clamp and distance has the same operands
and association, so induction over strict comparisons selects the same point.

Performance includes the complete public query: eligibility, clone, current
input copies (or proven full-grid endpoints), native boundary, box traversal, nearest-point reduction, output
reads, Java result allocation and consumption. Fixture/library/initial scratch
setup is excluded equally. Both baselines must pass: fully Java extraction and
the faster existing native-box caller. Eighteen workloads include full/random/
dense/sparse/checker/shell grids, cube coordinates, irregular/long rows, maximum
capacity, single occupied cells, partial cuboids, unaltered registered states
and explicitly derived registered joins.
Three independent warmed JVM comparisons use rotating execution order, six
seconds minimum warmup, eleven measured rounds, zero reported compilation,
equal checksums and a 5% saving in every comparison and its upper 95% ratio bound.

## Recorded verification

Release acceptance passed on 2026-10-02 (Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1). All 32 focused Java tests and 14 Rust physics tests passed
with zero differences. Coverage includes 4,096 exhaustive small grids,
192 complex fixtures, all 31,809 registered collision states, 543 derived joins,
floating-point edge cases and custom compatibility behavior.

All 18 workloads passed three independent warmed comparisons against **both**
baselines. Against the faster pre-migration caller with Rust box extraction,
the weakest observed saving was **16.3%**, conservative 95% bound **15.9%**.
Against fully Java, both minima were **73.3%**. Median complete caller timings:

| Workload | Pre-migration caller | Rust + caller/boundary | Less time |
|---|---:|---:|---:|
| Sparse 8³ | 0.639 µs | 0.453 µs | 29.2% |
| Random 16³ | 21.176 µs | 9.173 µs | 56.7% |
| Shell 16³ | 2.355 µs | 1.392 µs | 40.9% |
| All 93 qualifying registered states | 0.479 µs | 0.385 µs | 19.6% |
| Derived registered joins, 16³ | 2.601 µs | 1.041 µs | 60.0% |
| Partial cuboid in 16³ | 0.853 µs | 0.122 µs | 85.7% |

Registered shapes and both partial cuboid sizes also passed three comparisons
each in fresh single-workload JVMs: minimum **33.8%** observed saving,
conservative bound **14.6%** against the pre-migration caller. All measured
rounds, including slower ones, are retained in the confidence calculation.

`build/voxel-closest-point-migration/acceptance/REPORT.md` holds the parity
argument, all timings, raw evidence locations and scope. `results.json`
retains rounds, checksums, compiler counters and matching source/library hashes.
Earlier failed or incomplete timings are separately labeled historical.
These measurements cover warmed supported queries; small and custom inputs
retain Java compatibility and are not credited with a measured speedup.

The later [ray/shape migration](RUST-VOXEL-RAYCAST.md) adds a shared full-grid/
interior-cavity traversal shortcut. Its ordered outputs pass the independent
per-cell oracle and focused Java checks. The measurements above are historical
and precede that shared optimization.

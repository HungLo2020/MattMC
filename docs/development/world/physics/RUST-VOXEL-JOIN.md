# Rust voxel Boolean joins

Complex occupancy grids use Rust inside `BitSetDiscreteVoxelShape.join()`.
Matching grids and consecutive-Z row segments combine packed words; other
grids use the original Java mergers' numeric mappings. Uniform inputs are
short-circuited only when the truth table makes the result constant. Coordinate merging and its epsilon rules remain in
Java. This is an occupancy-join migration, not whole collision processing or
shape optimization. Renderer source and backend behavior are untouched.

The kernel is in
[`world/phys/shapes/boolean_join/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/phys/shapes/boolean_join).
[`NativeVoxelJoin`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/phys/shapes/NativeVoxelJoin.java)
owns the ordinary FFM call, thread-local scratch, snapshots and Java result.
There are no native allocations, retained pointers or joined-result caches.

## Constraints

- Native joins require exact bitset shapes, built-in Boolean operators, trusted
  built-in mergers, and 512–262,144 output cells. Axes are limited to 256 cells;
  inputs are bounded to 262,144 cells/bits. Smaller/custom/unsupported cases
  retain the original Java callback loop. The original result is allocated
  before the size gate; small joins never inspect or initialize the bridge.
- Treat negative and out-of-range mapped coordinates as empty, exactly like
  `isFullWide()`. Preserve Java merger output indices; never recompute or round
  the floating coordinates in Rust.
- Preserve all 16 truth tables, dimensions, raw occupancy and padding, and the
  original empty bounds (`Integer.MAX_VALUE` minima and `Integer.MIN_VALUE+1`
  maxima). Do not normalize empty results or replace their coordinates.
- Custom operators, shapes and callback-bearing coordinate lists must fall back
  before any extra user callbacks. Null arguments retain original exceptions.
- Source grids and coordinate lists must stay stable under the original caller's
  ownership rules. Copy current bitsets every call. No shape/block references
  escape to native code; scratch retains only numeric buffers.
- Package-visible bitset/bounds fields support the bridge and literal original
  oracle. Keep input storage read-only and result capacity/initialization equal
  to the original constructor. The production bridge fills only the fresh target
  allocated by that caller, whose dimensions match the original mergers.
  Scratch is protected against reentry and output
  is copied into independently owned Java storage before publication.

## Verification

```bash
python3 DevUtils/tests/physics/VerifyRustVoxelJoin.py --parity-only
python3 DevUtils/tests/physics/VerifyRustVoxelJoin.py --forks 3
# Additional fresh JVM per selected workload, after main acceptance:
python3 DevUtils/tests/physics/VerifyRustVoxelJoinIsolated.py
```

The Linux driver builds release Rust and pins both the complete original grid
join and public `Shapes.joinUnoptimized()` caller to Git `b81c01943`. The public
oracle redirects only the kernel call to the literal original Java body; it uses
the same real bitset shape class and allocation. Shape/merger implementations
remain pinned except the specific native dispatch, field visibility and pure
nonoverlap compatibility predicate, plus the separately pinned
[merged-box and box-list dispatches](RUST-VOXEL-BOXES.md) and strip-helper visibility.
The original discrete-grid source audit also removes only the separately pinned
[rotation dispatch](RUST-VOXEL-ROTATION.md).
No reflection is timed. Use `--cpu N` for
an available logical CPU. Focused tests require no graphics context.

Tests compare every cell, raw words/capacity, all bounds and floating-coordinate
bits. They cover all truth tables and merger families, boundary sizes, sparse/
dense/checker/shell/empty grids, padding, registered collision shapes, epsilon
edges, original shortcut identity, custom callback order, exceptions, box
extraction, optimization, collision distances and concurrent readers during GC.

Acceptance measures the **complete public unoptimized join caller** including
coordinate construction, eligibility, source bitset snapshots, mapping export,
every heap/native copy, one ordinary FFM call, Java result allocation and all
output-word/coordinate/bound consumption. Initial fixture/library/scratch setup
is excluded; every result is recomputed. Six warmed JVMs form three pairs with
alternating execution order, zero reported compilation in measured rounds and
matching checksums. Every workload must take at least 5% less time in each pair
and at the upper 95% bootstrap ratio bound.

Synthetic pairs exercise complex grids deliberately; two workloads combine
32 geometrically distinct registered collision shapes with a complex probe. Small ordinary block
joins may not qualify. Results cannot be interpreted as the same improvement
in total collision processing, shape optimization or frame rate. Raw evidence
belongs in `build/voxel-join-migration/acceptance/`.

## Recorded verification

Release acceptance passed on 2026-10-01 (Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1). Seven Java tests and two focused Rust tests passed with zero
output differences. Coverage includes all 16 truth tables in 3,600 fixtures,
25,679 nonempty registered bitset collision shapes (25,678 native joins),
raw coordinate/bound/bit equality and affected box/collision behavior.

All 24 main workloads passed three independently warmed pairs and their
confidence bounds: minimum **51.9% less time**, conservative bound **51.7%**.
Three small/registered cases also passed in fresh JVMs for each workload:
minimum **43.4% less time**, conservative bound **41.4%**. Narrow-profile Java
specialization therefore still clears the 5% requirement. Representative
single-workload JVM median timings, including the complete caller and boundary:

| Complex join | Original Java | Rust + boundary | Less time |
|---|---:|---:|---:|
| Matching 8³ grids | 3.02 µs | 0.72 µs | 76.3% |
| Differing cube grids | 3.13 µs | 1.34 µs | 57.2% |
| 32 distinct registered shapes + 8³ probe | 5.24 µs | 2.77 µs | 47.2% |

`build/voxel-join-migration/acceptance/REPORT.md` contains the equivalence
argument, scope and every workload. `results.json`, `isolated-results.json`
and logs retain raw rounds, matching output checksums, zero measured compiler
time and final source/library hashes. Finite tests do not enumerate every
possible grid; the proof also relies on the stated stable-input/type contract.
These numbers describe this migration after warmup, not later optimization,
small compatibility calls, startup or whole-game performance.

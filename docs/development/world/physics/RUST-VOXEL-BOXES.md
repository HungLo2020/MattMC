# Rust merged voxel boxes

Complex grids use Rust inside `BitSetDiscreteVoxelShape.forAllBoxes()` when
merging is requested. This operation feeds collision box lists, shape
optimization and ray clipping. Java still owns floating coordinates, AABB
objects and every consumer call. Unmerged enumeration and small or unsupported
grids retain the original loop.

The kernel lives in
[`world/phys/shapes/box_extract/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/phys/shapes/box_extract).
[`NativeVoxelBoxes`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/phys/shapes/NativeVoxelBoxes.java)
owns its ordinary FFM call and thread-local numeric buffers. Rust allocates
nothing and retains no pointers. Workspace holds no shape references and never
reuses a previous extraction result. Built-in
`ArrayVoxelShape`/`CubeVoxelShape` box lists map the native endpoints directly
into Java AABBs, avoiding two intermediate callback layers. Custom shape
overrides retain the original virtual dispatch.

## Compatibility rules

- Keep the original snapshot constructor **before** native dispatch, including
  custom-grid cell/bounds reads and Java BitSet clone behavior. Consumer mutations
  of the live grid must not affect this enumeration.
- Preserve ordered boxes, rather than merely equivalent occupied volume.
  Traverse Y, then X, then Z; find a consecutive Z run, expand it through X,
  then through Y, clearing the same cells. Box order affects ray ties and
  shape optimization.
- Word scans must stop at each row boundary. Ignore padding and bits outside
  logical dimensions. Stale shape bounds do not restrict scanning.
- Supported snapshots have axes 1–256 and 512–65,536 cells. Oversized storage,
  null consumers and unsupported dimensions use the original path. The small
  size/merge gate must stay an inlined constant and avoid bridge initialization.
- Keep coordinate reads and user callbacks in Java, in the original order.
  Propagate the same consumer exception without wrapping it; release scratch in
  `finally`. Hold its lease through callbacks. Reentrant extraction falls back
  to the original loop so a nested call cannot overwrite pending endpoints.
- Rust mutates only the private native word snapshot. Its buffers are aligned,
  valid and disjoint for the complete downcall. Output capacity is bounded by
  `x*y*ceil(z/2)` boxes, the maximum number of original Z runs. Empty compressed
  words must clear stale workspace on every call.

Scratch grows to the largest supported endpoint capacity seen by its thread.
Heap/native endpoint arrays together retain at most 4 MiB plus 8 KiB of words;
ordinary smaller grids need substantially less. Old arenas are automatically
reclaimed. This is workspace reuse, not a shape/result cache.

## Why the output is identical

The native word searches skip only cells that the original loop would read as
empty. A cached mask for rows up to 64 cells tracks that same private snapshot;
X/Y expansion cannot change another run in the current row.
The next set/clear positions delimit the same Z run. X and Y expansion
test precisely the same strips, and clear precisely the same bits in a private
snapshot. Induction over successive emitted boxes gives the same snapshot and
next box at every step. Numeric endpoints are copied back without conversion;
the unchanged Java caller performs the same coordinate lookups and callbacks.
Custom-grid snapshot reads and original compatibility paths remain intact.
If an exact adjacency check proves there are no face-adjacent occupied cells,
the original algorithm can emit only unit boxes. A specialized path enumerates
those cells in the same Y/X/Z order. Other occupancy uses the normal greedy path.

## Verify

```sh
python3 DevUtils/tests/physics/VerifyRustVoxelBoxes.py --parity-only
python3 DevUtils/tests/physics/VerifyRustVoxelBoxes.py --forks 3
# Fresh JVMs for a single workload; use a separate artifact directory:
python3 DevUtils/tests/physics/VerifyRustVoxelBoxes.py --case checker_8 \
  --output build/voxel-box-migration/isolated-checker --forks 3
```

The Linux driver builds release Rust and pins the original extraction loop,
helpers and complete public `VoxelShape.toAabbs()` caller to Git `3e85592c4`.
Only the exact native dispatches and helper visibility are permitted by the
source audit.
The discrete-grid source audit also allows only the separately pinned
[rotation dispatch](RUST-VOXEL-ROTATION.md).
The test oracle uses the same real snapshot class and strip helpers; only the
kernel call is redirected. No reflection is timed. Use `--cpu N` to select an
available logical CPU. These focused tests need no graphics context.

Parity compares ordered integer boxes, every occupied cell and raw floating
coordinate bits. It covers exhaustive small grids, random/dense/sparse/shell/
checker/slab inputs, word and size boundaries, padding, registered states,
joined shapes, custom callbacks, exceptions, mutations, reentry and concurrent GC.
Finite tests are supplemented by the equivalence argument above.

Acceptance measures the **complete `toAabbs()` caller**: original snapshot and
bounds reads, allocations, eligibility, word snapshot/copy, one ordinary native
call, endpoint copy, Java coordinate lookups, list growth, AABB
allocation and consumption of all six coordinates of every emitted box. The
built-in list path removes intermediate callbacks; arbitrary consumers remain
separate and their runtime is not credited as a measured speedup. Every
call recomputes the result. Fixture/library/initial scratch setup is excluded
equally. It does not measure full shape optimization, clipping, physics or FPS.

Twenty-one workloads cover eight patterns at 8³/16³, odd/long rows, all qualifying
unaltered registered collision shapes, and two explicitly derived collections
of 32 distinct registered-shape/probe joins. Each must pass three independently
warmed JVM pairs, identical checksums, zero reported compilation in measured
rounds, and at least 5% less time in every pair and at the upper 95% bootstrap
ratio bound. Raw evidence goes in `build/voxel-box-migration/acceptance/`.

## Recorded verification

Release acceptance passed on 2026-10-01 (Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1). Eight new Java tests, seven affected join tests and four
focused Rust tests passed with zero differences. Coverage includes 266,496
exhaustive Rust grids, 4,096 exhaustive Java grids, 256 complex fixtures, all
31,809 registered states and 543 sampled joined grids. All 93 qualifying
original registered grids also form an unmodified performance workload.

All 21 workloads passed three warmed JVM pairs: minimum **15.8% less time**,
conservative 95% bound **13.7%**. Four cases also passed three pairs of fresh
single-workload JVMs: minimum **15.4%**, conservative bound **12.8%**.
Median complete caller timings (including the native boundary):

| Workload | Original Java | Rust + caller/boundary | Less time |
|---|---:|---:|---:|
| Random 8³ | 3.97 µs | 3.26 µs | 18.1% |
| Random 16³ | 49.07 µs | 25.11 µs | 48.8% |
| Checker 8³ | 6.67 µs | 4.99 µs | 25.1% |
| Checker 16³ | 55.05 µs | 37.11 µs | 32.6% |
| Random 8×1×64 | 3.80 µs | 3.15 µs | 16.9% |
| All 93 qualifying registered states | 1.60 µs | 0.48 µs | 70.2% |

`build/voxel-box-migration/acceptance/REPORT.md` contains the parity argument,
all workloads, raw-artifact locations and scope. `results.json` and the four
`isolated-*/results.json` files retain rounds, checksums, compiler counters and
matching final source/library hashes. Early failed timings were corrected and
are separately labeled historical. These results describe this caller after
warmup, not arbitrary user callbacks, cold startup or whole-game performance.

The source audit also permits the separately pinned
[closest-point dispatch](RUST-VOXEL-CLOSEST-POINT.md). Its Java reference caller
and the established extraction/rotation algorithms remain unchanged.
It also permits the separately pinned
[outside-ray dispatch](RUST-VOXEL-RAYCAST.md).

The ray/shape migration adds an occupancy-derived shortcut for full grids and
single strictly interior rectangular cavities. It proves every word and emits
the original one/six ordered boxes. Arbitrary occupancy keeps the established
visitor; the Rust per-cell oracle verifies shortcuts and changed-cell rejection.
Earlier timings above precede this shared optimization.

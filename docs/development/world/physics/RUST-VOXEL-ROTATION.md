# Rust voxel rotation

Complex bitset grids use one packed Rust transform inside
`DiscreteVoxelShape.rotate()`. `Shapes.rotate()` keeps the original floating
coordinate transformations, center handling and shape construction in Java.
Block shape factories use this caller to construct rotations and reflections;
this migration does not change renderer code or claim a whole-game speedup.

The kernel is in
[`world/phys/shapes/rotation/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/phys/shapes/rotation).
[`NativeVoxelRotation`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/phys/shapes/NativeVoxelRotation.java)
owns the ordinary FFM call and numeric thread-local scratch. Rust allocates
nothing and retains no pointers. Each call copies current input and creates
an independently owned result; there is no transformed-shape cache.

## Compatibility constraints

- Identity returns the original object before bridge initialization. Grids below
  512 cells retain the original loop and avoid bridge initialization too.
- Native inputs are exact bitset shapes with axes 1–256, 512–65,536 logical
  cells, and no stored bit above the supported range. Custom and unsupported
  grids retain original virtual reads and exceptions.
  Reject negative BitSet lengths too: the highest legal bit can overflow that
  Java metadata value. Fall back before exporting its oversized word array.
- Java exports the original permutation and reflection decisions. Rust uses
  those decisions exactly; do not substitute a conventionally defined rotation
  for the existing Minecraft mapping.
- Ignore input bounds and padding when rotating. Rebuild output occupancy and
  bounds from logical cells. Empty results keep dimension minima and zero maxima.
  Preserve the original result class and allocated BitSet capacity.
  Empty native results reuse the fresh constructor's empty storage; keep the
  native call while avoiding redundant Java zero arrays and temporary BitSets.
- Floating coordinates, unusual IEEE values, custom lists, exceptions and
  coordinate callbacks stay in the unchanged public Java caller. Release native
  scratch before it reads any user coordinates, so reentry has its own result.
- Inputs must be stable during rotation, as required by the unsynchronized
  original. Never write source words. Scratch contains only numeric data and
  consumes about 16 KiB per thread; it is guarded against overlapping use.
- FFI buffers must be valid, aligned and disjoint for the entire call. Metadata
  validation precedes writes, output padding is cleared, and each output bit
  must lie inside the permuted dimensions.

## Why the mapping is identical

For each occupied source cell, the original chooses three coordinates through
its axis permutation and replaces reflected coordinates with `size - 1 - value`.
The native kernel expands that same mapping into signed linear strides plus an
offset. It sets precisely the resulting packed index. The permutation and
reflections form a bijection, so no occupied cell is lost or added. Word scans
skip only logical empty cells. Bounds are minima and exclusive maxima of the
same transformed set, with the original empty-case initialization. The unchanged
Java caller then performs the same floating coordinate operations.

## Verify

```sh
python3 DevUtils/tests/physics/VerifyRustVoxelRotation.py --parity-only
python3 DevUtils/tests/physics/VerifyRustVoxelRotation.py --forks 3
# Narrow group/workload profile in fresh JVMs:
python3 DevUtils/tests/physics/VerifyRustVoxelRotation.py --forks 3 \
  --case random_8 --group INVERT_Z \
  --output build/voxel-rotation-migration/isolated-invert-z
```

The Linux driver builds release Rust and pins the literal original grid loop
and complete `Shapes.rotate(input, group, center)` caller to Git `6fe3f1e87`.
Only the exact production dispatch is allowed by its source audit. `--cpu N`
selects an available CPU. `--skip-build` requires an already matching release
library. Graphics contexts and unrelated project tests are unnecessary.

Parity checks every packed word, dimensions, bounds, storage capacity, result
class and raw coordinate bits. Tests cover all 48 groups, exhaustive small grids,
complex patterns, padding, stale bounds, every registered state, derived joined
grids, custom callbacks, exceptions, reentry, changing inputs and concurrent GC.
Finite tests supplement the mapping argument; they do not enumerate all grids.

Performance measures the complete public caller: eligibility, current input
snapshot, all heap/native copies, one ordinary downcall, independently allocated
BitSet/result, coordinate transforms and consumption of every result word,
bound and coordinate. Both paths compute fresh outputs from equal inputs.
Library/fixture/initial scratch setup is excluded equally. Fifteen workloads
include eight occupancy patterns, asymmetric/long-row grids, cube coordinates,
shifted centers and all qualifying unmodified registered states. Each normally
uses all 47 nonidentity groups; narrow checks select a single group explicitly.

Acceptance requires three independently warmed JVM pairs with alternating order,
matching checksums, zero reported compiler time in measured rounds, at least
5% less time in every pair, and an upper 95% bootstrap ratio bound at or below
0.95. Raw evidence belongs in `build/voxel-rotation-migration/`.

## Recorded verification

Release acceptance passed on 2026-10-01 (Ryzen 5 5600G, Linux x86_64,
OpenJDK 25.0.4.1). Seven new Java tests, fifteen affected Java tests and seven
focused Rust tests passed with zero differences. Coverage includes all 31,809
registered states under every group, 1,495,023 nonidentity registered-state
comparisons, 144 complex fixtures, 64 joined grids and 196,608 exhaustive Rust
transformations through both kernel and FFI. The real BitSet length-overflow
case also retains the original Java output without exporting oversized storage.

All fifteen main workloads and seven fresh single-group checks passed three
warmed JVM pairs each. Minimum **34.8% less time**, conservative 95% bound
**34.3%**, including the complete public caller and native boundary. Narrow
checks cover all six axis permutations; their minimum was **38.0%**.

| Representative caller | Original Java | Rust + caller/boundary |
|---|---:|---:|
| Random 8³, all nonidentity groups | 6.13 µs | 0.84 µs |
| Random 16³, all nonidentity groups | 48.37 µs | 3.62 µs |
| All 93 qualifying registered states | 2.68 µs | 0.64 µs |

`build/voxel-rotation-migration/acceptance/REPORT.md` contains the mapping
argument, all timings and artifact paths. Final main and narrow reports have
matching source/library hashes, checksums and zero measured compiler time.
Earlier failed confidence checks and interrupted runs are labeled historical
and excluded. These numbers describe the warmed migration caller, not cold
initialization, compatibility paths, total world generation or FPS.

The source audit also permits the separately pinned
[closest-point dispatch](RUST-VOXEL-CLOSEST-POINT.md). Its Java reference caller
and the established extraction/rotation algorithms remain unchanged.
[Ray-intersection verification](RUST-VOXEL-RAYCAST.md) also permits only its
exact public dispatch when auditing shared shape sources.

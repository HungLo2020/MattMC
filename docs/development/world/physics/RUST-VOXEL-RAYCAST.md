# Rust ray/shape intersection

For supported grids of 512–65,536 cells, the outside branch of `VoxelShape.clip()`
combines ordered box extraction and ray intersection in Rust. Block/fluid raycasts use it for interaction and
line-of-sight queries. This moves another complete numeric stage of world
physics to Rust while Java owns inputs and the public result.

The kernel lives in
[`world/phys/shapes/raycast/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/world/phys/shapes/raycast).
[`NativeVoxelRaycast`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/world/phys/shapes/NativeVoxelRaycast.java)
makes one ordinary FFM call and constructs an independently owned hit point.
It exports no box list and invokes no Java callbacks from Rust. Separate scalar
and grid execution methods keep the adapter from sharing one compilation
profile across both paths. Empty/short rays and inside hits keep their original
Java arithmetic. For
supported shapes, proving the probe lies outside one entire coordinate axis
skips the remaining pure index searches; otherwise the original inside test
selects either the Java inside hit or the Rust outside stage. Custom paths keep
their original calls and order.

## Constraints

- Preserve ordered greedy boxes and X/Y/Z plane order. Strict `0 < t < best`
  keeps the first face/box on ties and excludes hits exactly at the ray end.
- Match both AABB constructors: normalize endpoints, add the block position,
  then normalize again. Preserve signed-zero min/max and every double operation's
  association, including the `1e-7` plane and transverse tolerances. No FMA.
  Current finite nondecreasing lists prove normalization cannot change the
  translated endpoints (including signed zero); only this case skips swaps.
- Only exact built-in Array/Cube shapes, bitset grids, Vec3 values and standard
  BlockPos/MutableBlockPos qualify. Axes must be 1–256 with 512–65,536 cells;
  coordinates must be exact DoubleArrayList/CubePointRange instances.
- Full grids and proven single interior cuboid voids emit the exact original
  one/six-box traversal without walking cleared rows. This shared optimization
  also applies to the current-caller baseline; both sides get the improvement.
- Large grids extract endpoints into private native scratch before intersection.
  With at least 64 boxes, shared plane intermediates are calculated once per
  endpoint within that call, with the original sums and division. Box and face
  comparisons keep their original order.
- A whole-grid miss shortcut requires a separating-axis proof for both the
  original transverse intervals and that axis's plane parameters. It uses the
  original rounded delta and arithmetic; being inside the grid or merely
  missing its outer entry plane is insufficient. Reversed lists decline it.
- Read current values every call. Keep BitSet.clone's source-capacity trimming and a fresh occupancy snapshot
  on every outside call. There is no input, geometry or result cache.
  Proven full grids or exact
  partial cuboids use a single-box native entry point; bounds alone are not proof.
- Unsupported/custom/nonfinite inputs retain original Java behavior. A native
  handled miss is distinct from declining the query. Custom getters, overrides,
  exceptions and reentry keep their original order.
- Native buffers are aligned, disjoint, private and live for the call. Rust
  retains no caller pointers. Endpoint scratch grows on first use, bounded by
  65,536 boxes (1.5 MiB per native thread); only this call's freshly extracted
  prefix is read. Guard Java scratch until the result is copied; about 21 KiB
  of numeric storage per Java thread. Scratch capacity is reused, never inputs
  or results.
  Concurrent input mutation retains the original unsynchronized contract.

## Verify

```sh
python3 DevUtils/tests/physics/VerifyRustVoxelRaycast.py --parity-only
python3 DevUtils/tests/physics/VerifyRustVoxelRaycast.py --forks 3
python3 DevUtils/tests/physics/VerifyRustVoxelRaycast.py --forks 3 \
  --case joined_registered_16 --output build/voxel-raycast-migration/isolated-joined
```

The Linux driver pins the original public clip method and AABB arithmetic to
Git `8b9173b39`. Both oracles use the same input shapes and rays: one retains
existing production Rust box extraction, the other uses original Java boxes.
The mechanical shared visitor, prechecks and custom compatibility are audited.
Parity compares raw point bits, face, inside/miss flags and block-position
values/ownership. Finite tests supplement the equivalence argument: identical
ordered boxes feed identical ordered strict comparisons and arithmetic.
All native outputs and finite Java results compare raw bits. Nonfinite rays
can produce NaNs in the unchanged Java inside precheck; those Java-only results
compare NaN values rather than unspecified sign/payload bits, as permitted by
[Java's floating-point specification](https://docs.oracle.com/javase/specs/jls/se25/html/jls-4.html#jls-4.2.3).

Performance times the complete public outside query: Java prechecks, eligibility,
clone/proof, current-input copies, FFM call, extraction/intersection, output
reads, Java result allocation and consuming every result field. Fixtures and
initial library/scratch setup are excluded equally. Twenty-three workloads include
all qualifying registered shapes (including a separate oblique-ray workload), derived joins, sparse/dense/irregular grids,
single cells, cuboids, translated rays, misses and rays starting in empty
cells inside the grid. Every workload must save 5%
against both baselines in each of three warmed comparisons and its upper 95%
ratio bound. Fifteen-second minimum warmup, at least two observed GC collections, eleven
rounds targeting 500 ms and zero reported compilation are required. The
mutator uses CPU 2 and JVM workers use CPUs 3/4 by default; all modes use the
same affinity and heap/GC flags. The complete three-comparison run takes about
80–90 minutes on the recorded system. Override affinity with `--cpu` and
`--background-cpus`. This avoids making GC share the sole measured core. Inside and unsupported paths are covered by parity;
they are not credited with measured migration gains.

## Verification status

Verified on AMD Ryzen 5 5600G / Linux x86_64 / OpenJDK 25.0.4.1:
**43 Java tests and 24 focused Rust tests passed**, including the directly
shared box/closest-point/join/rotation code. All 23 workloads passed three
fresh-JVM comparisons, every individual median and every upper 95% ratio gate.

| Baseline | Smallest paired time saving, full suite | Smallest lower 95% saving bound |
|---|---:|---:|
| Current Java caller with production Rust boxes | 18.75% | 18.56% |
| Original all-Java traversal and ray caller | 36.25% | 36.22% |

A separate nine-JVM repeat of the previously sensitive joined-grid case also
passed: minimum saving **15.08%** against current, lower 95% bound **14.83%**.
Both runs used identical source/library hashes, matching output checksums and
zero reported timed compilation. These are warmed eligible query results;
inside/custom/small paths retain Java compatibility. No rendering or unrelated
project tests were run.

Full evidence and the 23-workload table are in
`build/voxel-raycast-migration/REPORT.md`, `acceptance-split/results.json` and
`isolated-split/results.json`. Raw artifacts stay under `build/`.

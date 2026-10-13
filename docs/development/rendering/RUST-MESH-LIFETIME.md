# Dynamic mesh lifetime

**Implemented; scoped verification completed on 2026-10-13.**

Rust now owns dynamic model/item mesh usage, insertion order, generation
retirement and rollback history in
[`render/mesh_lifetime`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/mesh_lifetime).
The Java `DynamicWorldMeshLifetime` adapter passes scalar identities and projects
retirement results. It no longer keeps a second usage map or undo journal.
Model rigs touch their part identities directly from Rust's existing rig table,
with one call after ordered Java admission. If admission or registration fails,
Java applies the successful prefix before the producer rolls back.

This is semantic asset residency, not GPU lifetime. The existing world asset
update applies returned retirements; VulkanicGAL still owns GPU destruction
and completion. Static terrain uses its separate residency policy. Java still
owns asset wrappers, upload bookkeeping, pending retirement delivery and model
animation/submission. This slice does not complete retained entity rendering.

The subsequent [asset registry migration](RUST-ASSET-REGISTRY.md) moves numeric
mesh/texture membership and journals into Rust, with native dependency checks
for stable rig admission. Its verification is separate from the historical
lifetime checkpoint below.

## Constraints

- Retain the current and previous semantic frame. Retirement preserves first
  insertion order and identifies the exact mesh generation. Re-observing a key
  keeps its position; removing and re-adding moves it to the end.
- Keys and generations are signed Java longs: negative values are valid; zero
  is not. Frames must be positive. Repeated identical observations do not
  append undo history.
- Checkpoints belong to one semantic frame. A new frame clears history;
  rollback consumes only changes after its mark. Removals and same-frame
  retirement preserve ordered snapshots, including observations from other
  frame numbers that deliberately bypass ordinary undo recording.
- Each Java owner serializes its native calls. An automatic arena retains and
  releases its one Rust owner. Every call fences the Java owner. Native output
  is borrowed only while projecting that call's independently owned result;
  later calls cannot mutate an earlier Java retirement list.
- Native state is CPU-only. There are no GPU handles, alternate presenters or
  Java callbacks inside the native owner. No new crate was introduced.

## Verification

A fresh 30-second Current/Frozen gameplay profile at `efe94ff71` identifies
model admission as a remaining Java allocation source. Dynamic lifetime's
boxed keys, usage records and change records account for about 73 MB sampled
allocation weight in the Current model-submission stacks. This is diagnostic
sampling, not an allocation rate or an isolated gameplay measurement.
Receipts: `build/native-model-submission-profile/paired/`.

Full checks pass on native `ec10875b`: 2,763 Rust tests (3 ignored), 2,100
Java tests (2 skipped), and a 200,000-operation policy replay. Affected tests
cover ordered rollback, signed identities, independent retirement results,
registered rig touches and production admission/registration failure prefixes.

The final component benchmark compares the full Java/native boundary with the
previous Java tracker, per 1,024 simulated frames:

| Workload | CPU ms, Current / previous Java | Java heap MB, Current / previous Java |
| --- | --- | --- |
| Stable rigs | 3.49 / 4.42 | 0 / 31.46 |
| Changing rigs | 8.72 / 7.14 | 9.56 / 55.01 |
| Stable scalar touches | 8.46 / 4.42 | 0.033 / 44.04 |
| Changing scalar touches | 13.86 / 7.02 | 9.56 / 67.59 |

Stable rigs improve CPU by 21%; changing rigs remain 22% slower and scalar
paths regress. These gaps remain optimization work. Java allocation reductions
do not establish a total-memory bound. Rejected initial scalar and intermediate
slot implementations remain in `build/native-mesh-lifetime/oracle-1/` and
`oracle-2/`; final results and guards are in `oracle-3/`.

One fresh 6,000-frame gameplay pair per mode against unchanged Frozen OpenGL:

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1269.9 / 1191.4 | 3.691 / 3.755 |
| Shaders | 359.2 / 315.8 | 4.752 / 7.753 |
| DH | 808.6 / 621.8 | 3.405 / 6.030 |
| Shaders + DH | 282.1 / 226.2 | 5.856 / 9.185 |

All four scoped FPS/p99 floors pass. Inputs and JVM controls match; live entity
counts are respectively 188/182, 188/185, 167/167 and 167/166. Single pairs do
not establish an isolated migration gain or sustained performance acceptance.
The vanilla and shaders+DH settled comparisons pass, as do world unload/reload,
resource reload and view-distance decrease, with zero Vulkan validation errors.
Seven original images, five Current presenter correlations and nine Current
native identities were checked. Hazy lifecycle views provide structural evidence
only. First-turn pop-in, the first shader world frame, broad visual coverage and
long-running host/GPU memory remain open. Receipts are in
`build/native-mesh-lifetime/{runtime,gameplay-comparison,final-integrity}.json`;
originals remain in
`artifacts/graphics-captures/validation/native-mesh-lifetime-20261013/`.
Thirteen completed copied worlds were retired after the clients exited.

Ordinary gameplay enables rigs. Existing correctness captures force
`graphicsAuditSliceMetrics=true`, which disables rigs and selects Java part
poses. Those captures therefore exercise the scalar lifetime consumer; do not
claim they visually verify rig-wide touches. The actual rig transform test and
producer failure-prefix tests provide separate supplemental evidence. The replay below pins the prior
internal tracker at
`efe94ff71`; it is supplemental policy evidence, not the Frozen rendering
baseline. Real gameplay comparisons must use unchanged Frozen OpenGL.

```sh
# JDK 25; run CPU timings without builds, clients or other profilers active.
./gradlew -PmattmcRustProfile=release test --tests '*DynamicWorldMeshLifetimeTest'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib \
  render::mesh_lifetime -- --test-threads=1
python3 DevUtils/tests/rendering/VerifyMeshLifetime.py \
  --java-home /path/to/jdk25 --output build/mesh-lifetime/replay --benchmark
```

The replay compares 200,000 ordered operations, exceptions, marks, sizes and
retirement sequences, including invalid/stale marks and other-frame updates.
CPU workloads cover repeated stable asset touches and changing keys over
1,024 frames per sample. Six interleaved JVMs per workload each warm up for
32 samples before eight measured samples. Both scalar and rig-wide caller
workloads are retained, so batching cannot hide the scalar path's cost.
Measured compilation is rejected;
source, class and native hashes are checked. These cases do not establish
whole-game performance, lifecycle safety or long-running memory acceptance.

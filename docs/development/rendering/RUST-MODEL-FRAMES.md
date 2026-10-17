# Native model frames

**Implemented; scoped verification completed on 2026-10-13. CPU and vanilla
FPS gaps remain. This is not whole-renderer or whole-migration acceptance.**

Cacheable world model rigs now write animated `ModelPart` facts directly into
Rust-owned pose storage. Rust constructs their instance records and immutable
viewport, shadow, lighting, order and foil variants. Java retains animation,
material selection and frame orchestration; this does not migrate simulation or
all model producers. First-person and diagnostic Java-posed paths remain.

The producer is
[`NativeModelFrame`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/NativeModelFrame.java),
backed by
[`render/model_frame`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/model_frame).
It replaces the Java pose ring, root-matrix arrays and full copied rig-instance
records in ordinary rig extraction. Mixed submissions copy already-produced
instance bytes into existing staging; poses are borrowed directly. Existing
Rust decoding composes the rig hierarchy. Whole-frame ABI layout is unchanged;
all resources, GPU synchronization and presentation still belong to VulkanicGAL.

## Lifetime and failure rules

- Extraction, Java projection and completion use the render thread; Java views
  use a confined arena. Rust workers read raw CPU pointers only until join. Pose reservations are bounded to 1,024
  nodes per model. Interrupted extraction leaves no committed reservation;
  invalid instance semantics preserve the already-committed pose prefix.
- A batch checkpoint records instance and pose counts. Rollback truncates both;
  monotonically increasing versions prevent a reused slot reviving an old view.
- Successful frame consumption seals poses and transfers the owner to the frame.
  Immutable instance variants can still be appended for viewport projection.
  Original instances remain unchanged. New extraction uses a separate owner.
- Pose views are available only after sealing, so later growth cannot invalidate
  a published pose pointer. Java instance views expire on completion or abort. Copied records keep their
  independent lifetime. Staging caches only copied records, never native views.
- Queued submission decodes on its caller; pipelined submission may decode on a
  worker. Retain model storage through join and Java completion receipts. Shutdown
  joins/destroys native work before releasing abandoned model owners. Reset closes
  pending extraction; failed consumption and dropped frames also release it.
- These are CPU ownership rules. They neither replace rig-registration retirement
  nor introduce a GPU handle, presenter or crate.

## Verification and measurement

The paired 30-second gameplay profiles at `002f04262` are retained in
`build/native-frame-producer-profile/paired/`. Model enqueue stacks contain
159 MB sampled allocation weight in copied instance records and 80 MB in float
arrays. These are sampled weights, not rates or measured migration gains.

Full checks pass on native `d5edf850`: Rust 2,779 tests (3 ignored) and
Java 2,121 tests (2 skipped). Subsequent shutdown ordering, confined views and sealed-pose guards pass
24 focused Java tests after recompilation. Coverage includes actual enqueue and
batch rollback, stale views after slot reuse/close, exact instance-field encoding,
direct pose-buffer submission, completion-error cleanup, and real cow/wolf/
humanoid hierarchy transforms using the new producer. Fresh Frozen runtime results and limits are below. Source tests do not establish broad
rendering parity or long-running memory behavior.

Two completed profile world copies were retired and verified lossless JSON
compression reclaimed 599,025,323 bytes; original JFRs and failure logs remain.

```sh
# JDK 25
./gradlew -PmattmcRustProfile=release test \
  --tests '*NativeModelFrameTest' --tests '*ModelRig*Test'
CARGO_TARGET_DIR=build/rust/target cargo test --locked \
  --manifest-path src/main/rust/Cargo.toml --profile suite --lib \
  render::model_frame -- --test-threads=1
# After building; no clients, builds or profilers during timings.
python3 DevUtils/tests/rendering/VerifyModelFrames.py \
  --java-home /path/to/jdk25 --output build/model-frames/measurement
```

The component driver includes production, variants, encoding, frame release and
a sparse consumption checksum across 256 frames of 16 models with 12 nodes each. Its control
reproduces the previous Java pose-ring and record-construction sequence; it is
supplemental internal evidence, not the Frozen correctness baseline. It records
three interleaved JVMs per side, compiler-quiet samples and source/class/native
guards. Full field/pose checksums compare both paths before timing. Java allocation measurements do not bound native or total memory.

## Component results and limits

Final `build/native-model-frame/oracle-3/` passes full field/pose equality,
compiler-quiet sampling and source/class/native guards. Medians per 256 frames:

| Workload | CPU ms, Current / prior Java sequence | Java heap MB, Current / prior Java sequence |
| --- | --- | --- |
| Ordinary models | 0.838 / 0.770 | 0.662 / 2.064 |
| Foil, viewport and order variants | 1.235 / 0.987 | 1.174 / 4.424 |

Java allocation falls 68%/73%; CPU remains 8.8%/25.1% slower than this internal
control. This does not establish a gameplay performance gain. Fresh Frozen
mode timings and completion/image/lifecycle results are recorded below.

Failed/intermediate evidence is retained. `oracle-1` included full Java
checksum readback in timing (4.06/1.88 ms ordinary); `profile-1` identifies that
observer as the dominant sampled work. `oracle-2` moves full equality readback
before timing and uses confined Java scopes, but still measures 1.19/0.78 ms
ordinary. `profile-2` identifies bounded scalar writes and indexed matrix reads.
Checked 40-byte node slices, direct matrix components and selective view refresh
produce the final results above without removing validity or scope checks.
Original JFRs and both rejected measurements remain available.

## Current runtime checkpoint

One fresh 6,000-frame Current/Frozen OpenGL pair per mode on native `d5edf850`:

| Mode | FPS, Current / Frozen | p99 ms, Current / Frozen |
| --- | --- | --- |
| Vanilla | 1161.5 / 1211.8 | 3.665 / 3.702 |
| Shaders | 329.9 / 303.4 | 6.397 / 8.207 |
| DH | 762.8 / 576.6 | 3.856 / 7.267 |
| Shaders + DH | 278.5 / 219.5 | 6.156 / 9.753 |

Vanilla FPS remains 4.2% behind Frozen; the other FPS and all four p99 floors
pass. Single pairs do not isolate this migration's effect or establish sustained
performance acceptance. The moving workload enables ordinary model rigs. Input/JVM/source/class/native guards pass; live entity counts differ:
184/189 vanilla, 188/185 shaders, 166/166 DH and 167/164 shaders+DH.
Explicit pipelined and synchronous 6,000-frame runs pass with ordinary rigs
active. Two settled image pairs and world unload/reload, resource reload and
view-distance decrease pass, with zero Vulkan validation errors. Seven original
images, five Current presenter correlations and eleven native identities were
checked. Fifteen completed copied worlds were retired; originals and failures
remain in `artifacts/graphics-captures/validation/native-model-frame-20261013/`.
Receipts: `build/native-model-frame/{runtime,completion-modes,gameplay-comparison,
image-review,final-integrity}.json`.

The settled shader+DH coast aligns closely. Vanilla's distant aquatic detail
differs, and the three lifecycle images are heavily blue-fogged; those images
provide structural evidence only. Standard diagnostic captures disable rigs, so
their images cannot establish native rig visual parity. Broad gameplay, first
shader world frame, first-turn terrain gaps and long memory remain open.

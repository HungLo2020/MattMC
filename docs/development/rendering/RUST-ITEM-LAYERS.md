# Native item-layer preparation

> Current ownership is source-inspected at [`64294324`](https://github.com/HungLo2020/MattMC/commit/642943247003d7d8d756a65180f0872b088c13f0).
> Runtime results below are author-recorded checkpoints; this documentation
> review did not rerun suites or inspect the unbundled receipts, profiles or images.

Canonical authored item transforms now have immutable Rust CPU owners. Semantic
layer extraction captures that owner instead of creating a Java pose, quaternion
and matrix arrays. GUI, ordinary world-item and first-person collectors pass CPU owners to native
decoding. ABI 76 introduced direct GUI consumption; ABI 77 adds world/hand
parent poses and operation modes. Rust resolves the final pose once and copies
it into its owned request before rendering. GPU resources still belong to GAL.

Implementation:
[`render/items/`](https://github.com/HungLo2020/MattMC/tree/642943247003d7d8d756a65180f0872b088c13f0/src/main/rust/render/items),
[`NativeItemLayerTransform`](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/src/main/java/net/vulkanic/world/NativeItemLayerTransform.java)
and [GUI decoding](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/src/main/rust/render/bridge/gui/mesh.rs).
[World/hand decoding](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/src/main/rust/render/bridge/world/item_poses.rs#L5-L93)
resolves CPU poses before ordinary semantic validation and clears their owner fields.

## Constraints

- Preserve JOML float evaluation, left-hand mirroring, centering, signed scale
  and normal-pose rules. `NO_TRANSFORM` ignores its mutable vectors. Custom
  `Vector3fc` implementations, fastmath/FMA configurations and nonfinite native
  results retain the original Java pose path and exception behavior.
- The temporary Java cache holds at most 256 transform identities. Check current
  scalar bits before reuse. Each changed transform creates an independent owner;
  existing captures remain immutable and valid after cache eviction or reload.
  The cache limit does not bound all retained owners: captures pin evicted owners
  until their automatic arenas become reclaimable.
- A scoped read-only CPU view pins each owner. Synchronous and queued submission
  fence batch references through decode. The older pipelined route separately
  pins captures until join or context destruction, independently of mutable lists.
- Keep owner references through GUI topology, foil and sequencing copies. Reject
  conflicting inline/native poses and invalid hand modes before dereferencing.
  No native pointer survives into GPU resources or the renderer's owned frame.
- Mutable FRAPI meshes are created only when their public getter is called.
  Repeated calls return the same mesh, including after clear. Built-in extent
  traversal inspects absent meshes directly; subclass getter callbacks retain
  their existing invocation. Emitted/custom meshes keep their original path.

## World and first-person consumers

Ordinary world items apply authored TRS to a copied parent; hands compose a
prepared local pose. `world_pose.rs` preserves the distinct JOML float ordering
and the independent normal matrix. The immutable owner is 340 bytes, including
the existing 208-byte right/left pose prefix and cached authored operations.
Canonical layers bypass Java local-pose construction and multiplication.

Each request captures the parent model, normal and trust flag once. Base and
foil copies retain that capture. Native special foil resolves from the same
pose instead of constructing another Java matrix projection. Public compatibility
getters and enabled CPU diagnostics can still request a native projection.
Synchronous/queued calls fence request lists; worker-decoded submissions keep
independent owner pins until join, failure or context destruction.

Custom layers, emitted meshes, special renderers, custom quad/sprite callbacks,
non-affine parents, unusual numeric inputs and fastmath/FMA retain the existing
Java CPU semantics. They still feed the Rust renderer. Java continues to resolve
models, tints, quads and parent animation; entity scene preparation and contextual
world inputs remain migration work. Moving a local transform alone does not
establish complete item ownership or a whole-game speedup.

Related tracking: [#772](https://github.com/HungLo2020/MattMC/issues/772#issuecomment-6089031512).

## Verification and profiling

Use JDK 25 and the same release profile for Java and runtime checks:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml -- --test-threads=4
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeWorldItemPose*' --tests '*NativeItemLayerTransformTest' --tests '*ItemLayerLazyMeshTest' --tests '*ItemStackRenderStateSemanticLayerTest' --tests 'net.vulkanic.gui.*' --tests 'net.vulkanic.bridge.*'
python3 DevUtils/RunWiki.py check
python3 DevUtils/tests/rendering/RunValidation.py --label <new-label> --all-java-tests --perf
```

The preceding published GUI-only checkpoint passed 2,429 Rust tests (3 ignored), 153 focused Java
cases, 1,768 full Java cases (2 skipped), all seven lifecycle cases and
Wiki checks (2,488 pages/43 indexes). Reviewed vanilla/Iris+DH coast and HUD
pairs pass. These settled views do not certify broad gameplay or temporal parity.
The preceding cloud-source profile sampled 264.2 MB of Java mutable-mesh
constructor allocation over 15 seconds (hotbar 130.0 MB, entities 114.3 MB,
hands 19.9 MB). Receipt: `goal5/native-dh-cloud-owner-profile-20261009/current/item-preparation-diagnostic.json`.
Those weighted samples identify a target; they are not an isolated speedup or
measurements of this new source.

The new release `0570723b` has a valid Current DH runtime profile: unchanged
source/library, exact profile timestamps inside measurement and clean retirement.
Mutable-mesh constructor samples fall from 264.2 MB to zero; semantic-layer
samples from 34.6 to 21.0 MB. Total sampled allocation rises from 2.620 to
2.736 GB, including 194.0 MB in repeated source-flag environment queries.
The comparison spans different source checkpoints and is diagnostic only.
Receipt: `goal5/native-item-layer-profile-20261009/current/allocation-comparison.json`.
The completed `validation/native-item-layer-final-20261009/` comparison has
sixteen clean ABAB runs of 6,000 frames, with VUIDs/exceptions/orphans zero.
Vanilla remains 9.2% below Frozen and misses the p99 floor. DH and both shader
modes pass their FPS/p99 floors in this comparison; DH repeat variance prevents
attributing an isolated gain. All 25 generated run copies were retired.
These measurements use Java source before incoming `111d7a9c4` was integrated;
they do not measure its DH cleanup or capture/readiness changes. Keep the
combined-source checks separate from that performance evidence. After integration,
179 focused Java cases and fresh reviewed vanilla/Iris+DH coast pairs pass;
RGB differences are 0.203/0.348/0.382 and 3.712/4.266/3.901, DH coverage passes
and VUIDs are zero. Receipt: `validation/native-item-layer-upstream-pairs-20261009/reviewed-integration.json`.

The author reports that the world/hand checkpoint matches all 12,000 Frozen CPU
oracle cases exactly, including model/normal bits and trust flags. The committed
[seeded Java checks](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/src/test/java/net/vulkanic/world/NativeWorldItemPoseTest.java#L13-L58)
exercise 800 transforms across both hands and composition orders, plus immutable
parent capture. The [wire tests](https://github.com/HungLo2020/MattMC/blob/642943247003d7d8d756a65180f0872b088c13f0/src/test/java/net/vulkanic/bridge/NativeWorldItemPoseEncodingTest.java#L18-L57)
cover dirty storage and copies; the worker-pin case invokes the pinning helper
directly and does not execute submission, join, failure or context destruction.

For that recorded checkpoint, full Rust checks pass (2,432 cases, 3 ignored),
as does full Java (1,793 cases,
2 skipped). The final release also passes 89 affected Java cases after a
diagnostic-only change to use best-effort console writes. The decoder and
closed-pipe checks pass on that final source. Final release `0d54a098` passes all seven lifecycle cases and reviewed
vanilla/Iris+DH coast/HUD pairs (RGB mean differences 0.300/0.538/0.638 and
3.650/4.151/3.848; DH coverage passes; VUIDs zero). These are settled views.
Receipt: `validation/native-world-item-final-20261009/summary.json`.

All sixteen ABAB runs contain exactly 6,000 frames with no exceptions, VUIDs,
terrain failures or owned orphans. Median average FPS passes every mode here,
but vanilla p99 is 3.506 ms versus Frozen 3.019 ms, so the overall performance
gate fails. Current vanilla repeats are 1,076.9/1,457.3 FPS and Frozen DH
797.4/602.7 FPS; this variance prevents a robust or isolated speedup claim.
Current vanilla streaming spans 68 versus 2 measured frames, which identifies
a work-phase difference without establishing a tail root cause. Source, native
library, Frozen and protected-user-edit integrity checks pass; 25 generated
run copies were retired.

The final Current moving-DH profile proves native world-owner consumption and
zero sampled source-flag environment allocation, previously 194 MB/15 seconds.
Sampled old world-layer matrix allocation falls from 2.10 MB to zero, while
parent capture adds 5.24 MB and total Java allocation rises from 2.228 to
2.576 GB. Sparse weighted samples and different workload phases limit
attribution; Java asset/topology preparation remains substantial. Receipt:
`goal5/native-world-item-profile-20261009/current/item-allocation-comparison.json`.
Additional actual world and hand captures exercise modes 1 and 3, including
native special foil, on the worker-decoded route. A common frame boundary now
keeps GUI/hand observers aligned through resource reloads; 184 affected Java
cases pass after that diagnostic correction. The final held-clock fixture
passes complete normal-route admission: inspected Frozen pixels, native pose
inputs, reload, routine Vulkan validation and actual process-memory observation.
Receipt: `goal5/native-world-item-foil-memory-20261009/held-clock/`; one generated
copy retired, no owned orphans. Short-session peak RSS is 4,845,148 KB Current
and 8,413,100 KB Frozen; this does not establish long-session memory behavior.
Ground images match the live Frozen capture, but both disagree with the older
fixed pixel probes: the strict ground fixture remains unaccepted. Frozen is
unchanged; no Frozen behavior is classified as a bug. Earlier performance
measurements precede these observer-only changes.

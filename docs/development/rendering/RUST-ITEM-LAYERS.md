# Native item-layer preparation

Canonical authored item transforms now have immutable Rust CPU owners. Semantic
layer extraction captures that owner instead of creating a Java pose, quaternion
and matrix arrays. The block/flat-item GUI collectors and their native decoder consume
the owner directly. ABI 76 appends the CPU address and hand selection to GUI
mesh batches; inline model lanes are zero for that route. Rust copies the pose
into its owned request before rendering. GPU resources still belong to GAL.

Implementation:
[`render/items/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render/items),
[`NativeItemLayerTransform`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/vulkanic/world/NativeItemLayerTransform.java)
and [GUI decoding](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/bridge/gui/mesh.rs).

## Constraints

- Preserve JOML float evaluation, left-hand mirroring, centering, signed scale
  and normal-pose rules. `NO_TRANSFORM` ignores its mutable vectors. Custom
  `Vector3fc` implementations, fastmath/FMA configurations and nonfinite native
  results retain the original Java pose path and exception behavior.
- The temporary Java cache holds at most 256 transform identities. Check current
  scalar bits before reuse. Each changed transform creates an independent owner;
  existing captures remain immutable and valid after cache eviction or reload.
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

World/hand consumers still request compatibility
matrix projections. Their proposed direct lowering must preserve two operations:
ordinary world items apply authored TRS to the parent pose; hands multiply a
prepared local pose. JOML rotation and matrix multiplication associate float sums
differently, including the independent normal matrix. Java still resolves model layers, tints, quads and special
renderers. Moving those producers and consumers together remains work; this
slice does not claim complete item rendering or world-state ownership.

## Verification and profiling

Use JDK 25 and the same release profile for Java and runtime checks:

```sh
CARGO_TARGET_DIR=build/rust/target-tests cargo test --manifest-path src/main/rust/Cargo.toml -- --test-threads=4
CARGO_PROFILE_RELEASE_STRIP=none CARGO_PROFILE_RELEASE_DEBUG=line-tables-only ./gradlew -PmattmcRustProfile=release test -x testRustNative --tests '*NativeItemLayerTransformTest' --tests '*ItemLayerLazyMeshTest' --tests '*ItemStackRenderStateSemanticLayerTest' --tests 'net.vulkanic.gui.*' --tests 'net.vulkanic.bridge.*'
python3 DevUtils/RunWiki.py check
python3 DevUtils/tests/rendering/RunValidation.py --label <new-label> --all-java-tests --perf
```

The local implementation passes 2,429 Rust tests (3 ignored), 153 focused Java
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

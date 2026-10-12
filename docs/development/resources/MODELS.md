# Block and item models

Rust loads, resolves and bakes block and item models
([`assets/models/`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/models/mod.rs)):
model JSON, blockstate files, discovery, parent and texture resolution, and
baking elements and generated items into quads. Java keeps item definitions
(`ClientItem` codecs and the item model tree), the baked object graph
(`BakedQuad`, `QuadCollection`, `SingleVariant`, `WeightedVariants`,
`MultiPartModel`), render groups (`ModelGroupCollector` with `BlockColors`),
the state dispatch and special renderers.

Source-reviewed at
[`71762d60`](https://github.com/HungLo2020/MattMC/commit/71762d602195e0cc5ca29006a2bed1603b53eab8).
See the [model verification record](VERIFICATION.md#models) for reported
parity and timing evidence and its limits; CPU model preparation is not
GPU rendering or live-reload acceptance.

## Flow

[`ModelManager.reload`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/resources/model/ModelManager.java)
calls [`NativeModels`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/resources/model/NativeModels.java):

1. **Load** (`mattmc_models_load`): model files (top pack per id) and
   blockstate stacks, instantiated for every known state
   ([`model.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/models/model.rs),
   [`blockstate.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/models/blockstate.rs)).
   Java exports its state definitions once per process.
2. **Groups** (`mattmc_models_groups`): each state's visual equality group.
3. **Discover** (`mattmc_models_discover`): the models blockstates and item
   roots reach ([`resolve.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/models/resolve.rs));
   item roots are the model ids `ClientItem` trees reference.
4. **Bake** (`mattmc_models_bake`): Java sends the block atlas's sprites
   (sheets by pointer) and JOML matrices; Rust bakes every needed geometry in
   parallel ([`bake.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/models/bake.rs),
   [`job.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/models/job.rs))
   and returns parts, roots, per-state selections, item properties and
   missing-sprite reports. Java constructs quads in parallel from the result
   buffer, then assembles the model graph before freeing that buffer.
5. **Items**: `ModelBakery.bakeModels` bakes item models in Java against
   `NativeModels`' item baker. Its geometry and particle calls add their
   missing-sprite reports; Java logs them all once item baking finishes.

## Admission and lifetime

Use the [native resource-manager admission path](RESOURCE-MANAGER.md#native-atlas-and-model-admission).
Rust reads the selected model/blockstate bytes directly. In
[`job::load`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/models/job.rs#L41),
listings that can invoke Java pack filters finish on the calling Java thread;
file reads and parsing then run on native workers. Worker logs are deferred
until those workers join.

[`NativeModels`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/resources/model/NativeModels.java#L238)
caches the Java state-definition export, assuming block registries are frozen
before resource loading. This is not a dynamic registration interface. Each
native reload has a separate mutex-protected session for loaded files and
discovery. The manager must stay reachable during load; the bridge fences it
through that call. The bake borrows `SpriteContents.originalImage` pointers
only for the synchronous native call and fences the preparations; callers must
not close those images concurrently. This consumes the already copied
level-zero sheets; `AtlasManager` separately waits for mip completion before
applying the atlas.

On the successful path, `ModelManager` closes the native session after
converting its output, before Java item-model baking begins. That explicit
close sits downstream of the preparation `allOf`: a sibling preparation
failure can skip it, leaving the session's Cleaner as fallback. Prompt
reclamation and failed-reload cleanup have not been runtime-verified here.

Returned quads, transforms, state selections and
[`NativeResolvedModel` wrappers](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/java/net/minecraft/client/resources/model/NativeModels.java#L711)
are Java objects, not lazy readers of that session. Item wrappers expose
prebaked `X0_Y0` geometry and `TextureSlots.EMPTY`; `bakeTopGeometry` rejects
any other `ModelState`. Custom item trees must report model roots through
`resolveDependencies` and cannot use this interface as an arbitrary rebaker.
The wrappers still refer to atlas sprites, whose image lifetime follows the
[atlas owner](ATLASES.md#admission-threads-and-ownership).

The reload barrier and apply executor still belong to Java. Per-session locks
and once-only release protect the normal staged flow; they do not authorize
close/call races or establish a shared model/atlas/mesh generation for
arbitrary overlapping reloads.

## Constraints

- **Matrices come from Java.** Model rotation, UV-lock and element rotation
  matrices (and `1/cos` rescale factors) are computed by JOML in Java and
  passed in. Rust's vector code mirrors JOML 1.10.5 without FMA: each
  `a*b + c` rounds twice, in JOML's association order
  ([`geometry.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/assets/models/geometry.rs)).
- **Gson and DFU semantics.** Model files read the first JSON value and
  ignore trailing text (`parse_value`), with Gson's key order, duplicate
  keys, number text and `LazilyParsedNumber.intValue`; blockstates use DFU
  `JsonOps` rules
  ([`core/dfu.rs`](https://github.com/HungLo2020/MattMC/blob/71762d602195e0cc5ca29006a2bed1603b53eab8/src/main/rust/core/dfu.rs)).
- **Evaluation order matters for logs.** Java bakes a root's parts in order
  (particle, then geometry) and stops at the first failure; item models bake
  geometry, then the particle; special models only resolve the particle.
  Missing-sprite reports follow that order. A JSON `null` element or face
  fails where Java's loop reached it, with Java's NPE text.
- **Java's own nondeterminism.** Generated items order the front faces from
  `Map.of` (random per JVM; Rust emits SOUTH then NORTH). Unresolved texture
  references list in `Map.copyOf` order, and group ids follow identity-hash
  `HashMap` order. Verification normalizes these; nothing consumes the order.

## Performance

The [verification driver](VERIFICATION.md#models) defaults to the historical
last-Java Reference for semantic comparison and Frozen for timing. For the
migration goal, use unchanged Frozen for both roles as shown below. The
combined `9f372451` results below are fresh measurements; earlier reports
retain their original source and protocol identities.
The important performance constraints are:

- Generated items over large sprites (TACZ's 929×929 gun textures) dominate
  baking. `spans` indexes spans by facing and anchor instead of searching
  the list as Java does; keep it linear in pixels.
- Geometry bakes, first texture resolutions and Java's quad construction
  run in parallel. The bake output carries per-geometry offsets, so
  `NativeModels.build` can read geometries concurrently.
- `core/parallel.rs` hands out small chunks, since reload work is uneven.

## Working on it

```sh
(cd src/main/rust && cargo test --lib assets::models)
(cd src/main/rust && cargo test --lib core::dfu)
(cd src/main/rust && cargo test --lib core::json)
python3 DevUtils/tests/models/VerifyModels.py --output build/model-verification
```

The observer can write full detail for chosen labels (records hold hashes):
`ModelReference content packs record iterations detail.txt '<label regex>'`.
Run it on both trees and diff the details to find a mismatch.

**Troubleshooting:** "Requested a model that was not discovered previously"
means an item tree asks for a model its `resolveDependencies` did not
report. `IllegalStateException` from `NativeResolvedModel.bakeTopGeometry`
carries the geometry failure Rust recorded.

## Frozen comparison for the migration goal

Use unchanged Frozen for both baseline roles. Repeated invalid-fixture exceptions
can trigger JVM fast-throw message elision: one observed Frozen run emitted 200
helpful NPE messages and 1,132 bare NPEs, while Current emitted all 1,332 details.
The complete model records matched. Retain exception details with the same JVM
control on every side, rather than dropping warning comparisons:

```sh
python3 DevUtils/tests/models/VerifyModels.py \
  --reference ../MattMC_JavaPerfTesting/MattMC \
  --frozen-repo ../MattMC_JavaPerfTesting/MattMC \
  --output build/models-frozen-details --max-ratio 1.0 --pairs 5 --iterations 5 \
  --jvm-arg=-XX:-OmitStackTraceInFastThrow
```

This is a diagnostic protocol for the fixture reloads; it changes no Frozen
source. Record the JVM arguments with timings. Ordinary gameplay comparisons
keep their existing production JVM controls. The separate Java pipeline is a
historical diagnostic, not this goal's correctness baseline.

## Diagnose model-only reload cost

Use a completed identical-record Frozen corpus with the paired CPU profiler:

```sh
python3 DevUtils/tests/models/ProfileModels.py \
  --verification build/models-frozen-details --output build/models-cpu-profile \
  --java-home /path/to/jdk25 --profiler /path/to/asprof
```

It samples fifteen seconds after twenty warm model-only reloads with atlases
precomputed, records source/native/process guards and waits for each owned
process to finish. These profiled runs do not replace the timing gate.

The integrated `a48b6a65` corpus matches all 67,433 record lines and 4,351
warnings under matched NPE-detail controls. Model-only cost misses the strict
Frozen floor (83.237/71.367 ms), while full atlas/model reload passes
(129.458/134.165 ms). Paired profiles pass their guards; Current includes 1,486
of 26,984 samples in repeated generated-item transparency reads. The bounded
Rust alpha mask passes a fresh five-round comparison: all records/warnings
match, model-only 76.915/79.069 ms and full reload 128.454/135.543 ms pass
the strict aggregate floors. Three individual model-only paired ratios exceed
1.0; keep them. Frozen's median moved from 71.367 to 79.069 ms, so these
separate runs do not isolate a speedup. Invalid frames retain per-access errors
and first-seen span order remains unchanged. Seven client lifecycles, two reviewed world pairs and four GUI pairs/eight
original reviews pass on combined native `9f372451`, with zero VUIDs.
The fresh twenty-run gameplay matrix passes every FPS/p99 floor; see the
[current matrix](../rendering/GAMEPLAY-PERFORMANCE.md#current-hardware-evidence-boundary)
for controls and limits. Receipts: `build/native-entity-lifecycle-model-integration/`.

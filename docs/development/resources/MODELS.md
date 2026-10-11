# Block and item models

Rust loads, resolves and bakes block and item models
([`assets/models/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/models/mod.rs)):
model JSON, blockstate files, discovery, parent and texture resolution, and
baking elements and generated items into quads. Java keeps item definitions
(`ClientItem` codecs and the item model tree), the baked object graph
(`BakedQuad`, `QuadCollection`, `SingleVariant`, `WeightedVariants`,
`MultiPartModel`), render groups (`ModelGroupCollector` with `BlockColors`),
the state dispatch and special renderers.

## Flow

[`ModelManager.reload`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/resources/model/ModelManager.java)
calls [`NativeModels`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/resources/model/NativeModels.java):

1. **Load** (`mattmc_models_load`): model files (top pack per id) and
   blockstate stacks, instantiated for every known state
   ([`model.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/models/model.rs),
   [`blockstate.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/models/blockstate.rs)).
   Java exports its state definitions once per process.
2. **Groups** (`mattmc_models_groups`): each state's visual equality group.
3. **Discover** (`mattmc_models_discover`): the models blockstates and item
   roots reach ([`resolve.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/models/resolve.rs));
   item roots are the model ids `ClientItem` trees reference.
4. **Bake** (`mattmc_models_bake`): Java sends the block atlas's sprites
   (sheets by pointer) and JOML matrices; Rust bakes every needed geometry in
   parallel ([`bake.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/models/bake.rs),
   [`job.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/models/job.rs))
   and returns parts, roots, per-state selections, item properties and
   missing-sprite reports. Java builds the model objects in one pass.
5. **Items**: `ModelBakery.bakeModels` bakes item models in Java against
   `NativeModels`' item baker. Its geometry and particle calls add their
   missing-sprite reports; Java logs them all once item baking finishes.

## Constraints

- **Matrices come from Java.** Model rotation, UV-lock and element rotation
  matrices (and `1/cos` rescale factors) are computed by JOML in Java and
  passed in. Rust's vector code mirrors JOML 1.10.5 without FMA: each
  `a*b + c` rounds twice, in JOML's association order
  ([`geometry.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/assets/models/geometry.rs)).
- **Gson and DFU semantics.** Model files read the first JSON value and
  ignore trailing text (`parse_value`), with Gson's key order, duplicate
  keys, number text and `LazilyParsedNumber.intValue`; blockstates use DFU
  `JsonOps` rules
  ([`core/dfu.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/dfu.rs)).
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

The model reload is gated against Frozen. What keeps it there:

- Generated items over large sprites (TACZ's 929×929 gun textures) dominate
  baking. `spans` indexes spans by facing and anchor instead of searching
  the list as Java does; keep it linear in pixels.
- Geometry bakes, first texture resolutions and Java's quad construction
  run in parallel. The bake output carries per-geometry offsets, so
  `NativeModels.build` can read geometries concurrently.
- `core/parallel.rs` hands out small chunks, since reload work is uneven.

## Working on it

```sh
(cd src/main/rust && cargo test --lib -- assets::models core::dfu core::json)
python3 DevUtils/tests/models/VerifyModels.py --output build/model-verification
```

The observer can write full detail for chosen labels (records hold hashes):
`ModelReference content packs record iterations detail.txt '<label regex>'`.
Run it on both trees and diff the details to find a mismatch.

**Troubleshooting:** "Requested a model that was not discovered previously"
means an item tree asks for a model its `resolveDependencies` did not
report. `IllegalStateException` from `NativeResolvedModel.bakeTopGeometry`
carries the geometry failure Rust recorded.

# TaCZ gun rendering

**Current behavior (2026-10-17).** Every TaCZ gun is drawn by one shared
renderer,
[`TaczGlock17SpecialRenderer`](https://github.com/HungLo2020/MattMC/blob/master/src/main/java/net/minecraft/client/renderer/special/TaczGlock17SpecialRenderer.java)
(the name is historical), with one instance per gun id.

## Ownership

- A gun's or attachment's Bedrock geometry (`geo_models/gun`, `geo_models/attachment`)
  is converted once into a `ModelPart` tree (`BedrockGunModel`). One Bedrock node
  becomes one part, and its cubes become that part's local mesh.
- Each frame Java samples the animation and writes node poses into the parts.
  The gun then goes through the ordinary direct-texture model route
  (`submitModelSemanticTexture` with `Unit.INSTANCE`). Rust caches the
  per-node meshes once and composes the hierarchy as a
  [model rig](RUST-MODEL-FRAMES.md). First-person hands use Java part poses
  over the same cached meshes. No Java code transforms gun vertices per frame.
- Model-rig instances are entity meshes, so held guns also become shader shadow
  casters. The previous material-quad path could not be converted to casters.
  With shaders enabled, a gun in the shadow pass crashed the frame with
  `unadmitted semantic families: custom-geometry=1`.
- Nodes named `*_illuminated` and their subtrees draw full-bright. A gun with such
  nodes is submitted twice, with complementary `skipDraw` masks.
- Still on copied Java quads: first-person optical scope/sight attachments
  (their stencil roles have no model-route equivalent) and the muzzle flash.

## Constraints

- Count-only collectors (`isSemanticCoverageOnly()`) must get no TaCZ
  submissions. Those replays revisit item layers that the real pass already
  queued, and any submission is counted as unadmitted work.
- GUI items: the bounds passed to Rust GUI meshes are pose-local, because the
  composite shader applies the item pose. Use `GuiItemRenderState.oversizedItemBounds()`
  or the 16×16 slot, never `bounds()`, which is already transformed. Otherwise a
  container screen's `leftPos/topPos` is applied twice.
- The GUI capture is limited to Rust's 65,536-vertex GUI mesh bound
  (16,384 quads). The largest bundled gun has about 6,600.

## Verification

```sh
./gradlew -PmattmcRustProfile=release test -x testRustNative \
  --tests '*TaczBedrockModelRigTest'
```

`TaczBedrockModelRigTest` poses every bundled gun and attachment geometry
randomly. It requires the rig's emitted positions and UVs to match the original
per-node Java walk within 1e-4, per light pass. It covers CPU geometry only.
Check on-screen placement in a client: inventory, hotbar, first person, third
person and dropped items, each with shaders on and off.

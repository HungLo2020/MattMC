# Rendering

How the native (Rust) renderer is organized and how to change it safely. The
renderer lives in [`src/main/rust/render/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render);
each module there has a short README with its own file map.

- [Goal 5 rendering checkpoint](GOAL-5-STATUS.md): current source scope, bounded evidence, open tracker work and remaining parity/performance limits.
- [Render Architecture](RENDER-ARCHITECTURE.md): the layers, what each may
  depend on, and where new code belongs.
- [Retained render scene](RETAINED-SCENE.md): the proposed persistent scene
  model and its phased rollout toward Frozen parity.
- [Rust DH cloud preparation](RUST-DH-CLOUDS.md): native motion/culling ownership,
  API overrides and direct pose consumption with bounded lifetime.
- [Native item-layer preparation](RUST-ITEM-LAYERS.md): authored CPU poses,
  direct GUI/world/hand consumption and lazy mutable-mesh allocation.
- [VulkanicGAL](VULKANIC-GAL.md): working with the graphics abstraction layer:
  handles, validation, submission, hazards and common errors.
- [Java Bridge](JAVA-BRIDGE.md): the C ABI Java calls, and how to change it
  without breaking Java.
- [Render Verification](RENDER-VERIFICATION.md): tests, Frozen image
  comparisons, real-config sessions and A/B performance checks.
- [Ordinary gameplay performance](GAMEPLAY-PERFORMANCE.md): visible minimap,
  continuous frame timing and actual travel alongside settled renderer tests.
- [Capture storage and recovery](ARTIFACT-STORAGE.md): reclaim reproducible
  caches and restore losslessly archived historical capture data.
- [Shader terrain profiling](SHADER-TERRAIN-PROFILING.md): compare moving shader
  workloads and isolate batching and Java allocation costs.
- [RenderDoc input observations](RENDERDOC-INPUTS.md): capture Frozen's actual
  OpenGL vertex inputs without changing its renderer or source files.
- [Underground shader lighting checks](UNDERGROUND-SHADER-CHECKS.md): prepare
  equivalent cave captures and distinguish fog, shadow and volumetric inputs.
- [Entity shadow ordering checks](ENTITY-SHADOW-CHECKS.md): exercise typed orb
  placement boundaries through CPU capture and copied-world shader sessions.
- [Terrain movement checks](TERRAIN-MOVEMENT-CHECKS.md): use ordinary isolated
  gameplay to exercise chunk streaming while observing presented terrain.

# Rendering

How the native (Rust) renderer is organized and how to change it safely. The
renderer lives in [`src/main/rust/render/`](https://github.com/HungLo2020/MattMC/tree/master/src/main/rust/render);
each module there has a short README with its own file map.

- [Goal 5 rendering checkpoint](GOAL-5-STATUS.md): current source scope, bounded evidence, open tracker work and remaining parity/performance limits.
- [Render Architecture](RENDER-ARCHITECTURE.md): the layers, what each may
  depend on, and where new code belongs.
- [Rust dynamic mesh lifetime](RUST-MESH-LIFETIME.md): semantic asset usage,
  generation retirement, rollback ownership and scoped verification.
- [Rust world asset registry](RUST-ASSET-REGISTRY.md): primary numeric membership,
  rollback history, stable rig dependency checks and scoped verification.
- [Retained render scene](RETAINED-SCENE.md): the proposed persistent scene
  model and its phased rollout toward Frozen parity.
- [Rust DH cloud preparation](RUST-DH-CLOUDS.md): native motion/culling ownership,
  API overrides and direct pose consumption with bounded lifetime.
- [Rust DH render columns](RUST-DH-RENDER-COLUMNS.md): compact-column reduction,
  water/material policy, borrowed storage and actual Frozen CPU replay.
- [Rust DH full-data conversion](RUST-DH-CONVERSION.md): conversion-policy
  migration, ordered callbacks and ongoing performance evaluation.
- [Rust DH render-source storage](RUST-DH-RENDER-SOURCE.md): primary CPU points,
  lazy semantic planes, direct reducer/box consumers and source lifetime.
- [Rust DH quad builds](RUST-DH-QUADS.md): build-local quad storage, premerging,
  greedy topology, semantic outcomes and Frozen verification.
- [Rust DH semantic packets](RUST-DH-PACKETS.md): native layer selection,
  contextual callback plans, vertex packing and transport ownership.
- [Retained Rust DH visibility](RUST-DH-VISIBILITY.md): incremental topology/lifecycle, direct native traversal and callback compatibility.
- [Native DH column handoff](RUST-DH-COLUMN-HANDOFF.md): scoped packet storage,
  direct ledger ownership transfer and diagnostic projections.
- [Rust DH box production](RUST-DH-VERTICAL-FACES.md): complete visibility,
  shade/fake-ocean policy, borrowed neighbors and direct native consumption.
- [Native terrain face policy](RUST-TERRAIN-CULLING.md): direct retained-state
  culling, immutable geometry ownership and callback compatibility.
- [Bulk Rust terrain lighting](RUST-TERRAIN-LIGHTING.md): direct retained light
  reads, contextual callback compatibility and local verification scope.
- [Live biome color sampling](../world/biome/RUST-LIVE-BIOMES.md): direct raw sky/fog
  colors and generation-validated reuse, Java lifecycle events and bounded evidence.
- [Rust fog policy](RUST-FOG-POLICY.md): candidate color/range policy, native
  water/rain histories and ordered callback boundaries.
- [Rust camera immersion](RUST-CAMERA-ENVIRONMENT.md): direct retained-world
  fluid queries, palette retirement and current verification limits.
- [Native item-layer preparation](RUST-ITEM-LAYERS.md): authored CPU poses,
  direct GUI/world/hand consumption and lazy mutable-mesh allocation.
- [Rust GUI item templates](RUST-GUI-ITEM-TEMPLATES.md): retained semantic
  layers, ABI 80 frame instances, lifetime rules and pending acceptance.
- [VulkanicGAL](VULKANIC-GAL.md): working with the graphics abstraction layer:
  handles, validation, submission, hazards and common errors.
- [Java Bridge](JAVA-BRIDGE.md): the C ABI Java calls, and how to change it
  without breaking Java.
- [Render Verification](RENDER-VERIFICATION.md): tests, Frozen image
  comparisons, real-config sessions and A/B performance checks.
- [Ordinary gameplay performance](GAMEPLAY-PERFORMANCE.md): visible minimap,
  continuous frame timing and actual travel alongside settled renderer tests.
- [Recording a hand-played session](SESSION-RECORDING.md): `RunDev.py --record`
  per-frame timings, JFR and GPU/CPU samples while you play.
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

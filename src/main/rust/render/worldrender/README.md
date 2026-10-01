# worldrender

The world renderer: `WorldPrimitiveFrontend` turns each world frame decoded by
the bridge into GAL command lists. See `mod.rs` for the module overview.

```
mod.rs        WorldPrimitiveFrontend state, module map
submit.rs     whole-frame / partial submission entry points
frame/        requests, limits, frame/quad/mesh/LOD validation
vanilla/      built-in route: recording, resources/ (records, meshes, materials,
              overlays, sky, outline), shaders + glsl/
source/       selected shader-pack route: admission, uniforms, programs/ (keys,
              terrain, materials, entities, teardown), frames/, plans/ (terrain,
              DH, fullscreen, submission), resources/, receipts/, coverage,
              submit, mesh
lod/          Distant Horizons: geometry, packing, residency, passes, contract,
              uniforms, exact_atlas, source, composition, staging
fabulous.rs   Fabulous transparency route
post_effects.rs vanilla post effects
assets/       stores, atlas animation, animation upload, material registry
geometry/     arenas/streams, batching, translucent order
passes/       targets, pipelines, oriented target
features/     outline, world text, particles, experience orbs, decal foil
terrain/      static chunk-terrain boundary
diagnostics/  capture, traces, decal/equipment capture, vertex observation,
              GPU profile scopes
teardown.rs, util.rs
```

Rules (enforced by `vulkanic/architecture_boundary.rs`): depend on
`render::{scene, shared, shaderpack, guirender}` and the public GAL modules
only; never name a backend or the bridge. The whole-frame submit composes the
GUI renderer, and `WorldPrimitiveFrontend` implements `GuiAtlasOwner`
(`assets/atlas_animation.rs`) so GUI quads can sample world-owned atlases.

Known large functions kept intact (behaviour-preserving move): the vanilla
recording loop (`vanilla/recording.rs`), named-source terrain/DH plans,
complete named-source submission, whole-frame submission and the Fabulous
material frame. Splitting them is logic refactoring, tracked separately.

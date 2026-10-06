# shaderpack

Data-driven shader programs: Iris/OptiFine shader packs and Minecraft's own
(vanilla and resource-pack) shaders. See `mod.rs` for the module overview.

```
source/      copied source snapshots, manifest, preprocess, dialect, binary assets
properties/  pack properties: entity/item ids, custom uniforms, held light, wetness, shadow
contracts/   per-family semantic contracts (terrain, entity, hand, material, weather,
             cloud, line, damaged block, DH, fullscreen, vertex interface)
lowering/    source -> explicit GLSL: stages, pairs, fragment/vertex/fullscreen surfaces,
             fullscreen_vertex/celestial owned geometry and transforms, varyings,
             fullscreen_texels source/native/PNG integer addressing,
             opaque resources, uniforms, text utilities, diagnostic probes
programs/    model/ (identity, stages, material programs), lowered/ (per family),
             builtin/ (MattMC's own programs; GLSL in builtin/glsl/)
uniforms/    terrain lighting environment, source uniform catalog, temporal values
plan/        resource manifest, runtime plan, pass graph
resources/   GAL residency: color_targets/, pack assets, semantic bindings
voxels/      light volume, material map, emission table, occupancy/ (sampling,
             snapshots, runtime, colored light, flood fill, puddles, regions)
vanilla/     post_effect/ (contract, executor), imports, namespaces, engine Globals,
             lightmap, fabulous/
runtime/     ShaderPackRuntimeExecutor: candidates/, programs, color targets, source
             resources, lightmap, voxels, fullscreen stages, fullscreen/, recording
             (graph, source_passes, draw), per-frame inputs (frame)
```

Rules (enforced by `vulkanic/architecture_boundary.rs`): depend only on
`render::scene` and the public GAL modules (`gal`, `resources`, `commands`,
`handles`, `error`, `frame`, `sync`); never name a backend or a renderer.
Tests build GALs through `vulkanic::test_support`. Large test modules live in
`tests.rs` beside the code they test.

The runtime's cached source-program getters return immutable `Arc` snapshots,
keyed by candidate epoch and terrain material slot. Keep per-frame uniforms and
GPU resources outside these snapshots. Reloads replace the memoized CPU value;
callers must still reject mismatched pack/resource generations before using it.
Run the runtime and world-renderer native tests after changing this contract,
including `prepared_program_epoch_replacement` for replacement and failed retry.

The built-in programs serve the world renderer's own graph (which the runtime
executor also records); they may move to `render/worldrender` with it.

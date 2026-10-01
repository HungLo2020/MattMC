# guirender

The GUI renderer: sprites, raw images, affine and tiled quads, 3D item meshes,
full item rasters, the panorama and GUI post effects, recorded into GAL command
lists. See `mod.rs` for the overview.

```
mod.rs              layering and module map
atlas_reference.rs  references into world-owned atlases; GuiAtlasOwner
tiling.rs           tiled quads lowered to affine quads
items/              item raster layout, flat item materials, raster targets
frontend/           GuiFrontend
  mod.rs            persistent state, reset/teardown
  limits, requests, ordering      bounds, request types, stratum ordering
  submit, recording/              entry points; target, blur boundary, batches
  resources/, pipelines           texture groups and keys, shared pipelines
  sprites, sprite_table, assets   bundled sprites, overrides, raw images
  atlas, item_rasters             world-atlas quads, item raster quads
  mesh_items/                     geometry, composite, recording
  post_effects/                   custom (+resources), blur, invert, creeper, spider
  commands, shaders + glsl/       command helpers, GLSL
mesh/               semantic 3D GUI meshes
  model, validation, prepare      request/draw types, checks, lowering
  pass, program, raster_state     per-draw passes, shared programs, winding math
  composite, offscreen, uniforms  raster blits, offscreen targets, packing
  limits, shaders + glsl/, fixtures/ (test captures)
```

Rules (enforced by `vulkanic/architecture_boundary.rs`): depend on
`render::{scene, shared, shaderpack}` and the public GAL modules only; never
name a backend, `render::worldrender` or the bridge. The world renderer may
call into this module (it composes the GUI on the whole-frame route) and
implements `GuiAtlasOwner` so the GUI can sample its atlases.

Known large functions kept intact (behaviour-preserving move): custom
post-effect recording, the owned-atlas and blur-boundary recording paths,
mesh item recording, blur resources and resource creation. Splitting them is
logic refactoring, tracked separately.

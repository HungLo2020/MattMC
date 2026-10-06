# bridge

The Java boundary: the C ABI Java calls through FFM downcalls
(`mattmc_vulkanic_gal_*`). It reads versioned `#[repr(C)]` records, bounds
and copies what Java sent, decodes wire values, calls the GAL or a renderer,
and writes a status back. It makes no rendering decisions. It owns the context
registry and is the only code that creates GALs and chooses their backend.
See `mod.rs` for the overview.

```
mod.rs            overview, module map
abi/              wire records by family (common, context, frame, resources,
                  submission, gui, world, world_assets, lod, whole_frame,
                  shader_pack), ABI versions, payload limits
layout.rs         struct-layout table Java checks at load
memory.rs         reads/writes of Java memory: headers, bounded slices, labels
accounting.rs     input-byte bounds per request
wire.rs           wire enums, flags and handles -> GAL values
capabilities.rs   capability negotiation and per-resource capability checks
status.rs         status, last error, metrics and submit results
context.rs        context registry and lifetime, GAL creation, timestamps
resources.rs      resource batches
submission.rs     command submission, completion, retirement, readback
frame.rs          surface, acquire, resize, present, capture
gui/              atlas, quads, mesh, frame, assets
world/            exports, whole_frame, meshes, mesh_assets, first_person,
                  lod, dh_boxes, environment, assets, background,
                  entity_shadow_query (standalone shadow-entity prefilter)
shader_pack.rs, sprite_animation.rs
canonical.rs      (tests) canonical encoding of decoded batches
tests/            end-to-end ABI tests; gal_abi.rs for records and batches
```

Rules (enforced by `vulkanic/architecture_boundary.rs`): use the public GAL
modules only and never name a backend; the semantic `gui/` and `world/`
modules only decode and copy records and call renderers; schemas extend shared
record families rather than naming producers. Exported symbol names and
`#[repr(C)]` layouts are what Java binds to (`VulkanicGalBridge.java`):
changing either needs the matching Java change.

Known large functions kept intact (behaviour-preserving move): whole-frame
decoding (`world/whole_frame.rs`), the whole-frame entry point, LOD asset
decoding and the struct-layout table.

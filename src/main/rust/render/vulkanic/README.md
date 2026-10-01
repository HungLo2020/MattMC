# vulkanic (VulkanicGAL)

The graphics abstraction layer and nothing else. Developer guide:
`docs/development/rendering/VULKANIC-GAL.md`; API reference: `cargo doc`
(every public item is documented, enforced by `#![warn(missing_docs)]`).

```
mod.rs          overview, public re-exports, missing-docs lint
create.rs       creating a GAL and choosing its backend (bridge only)
gal/            VulkanicGal, one module per concern:
  mod.rs          state, constructor, limits
  buffers_textures, pipelines, passes, frame_targets   resource creation
  bindings        resource-set validation
  lifetime        destroy and the dependency graph
  recording       command-recording scopes, command lists
  command_validation, hazards, normalization, submission   submit path
  frames          presentation surface
  profiling, capture, test_hooks, arena
resources.rs    descriptors, formats, usages, BackendCapabilities
commands.rs     CommandOp, command lists, submission batches
frame.rs, handles.rs, sync.rs, error.rs, metrics.rs
backends/       private Vulkan and OpenGL backends (+ mock for tests)
tests/          GAL tests by concern; test_support.rs builds GALs for others
```

Rules, enforced by `architecture_boundary.rs`:
- `ash`/`shaderc` stay in `backends/vulkan`, `glow` in `backends/opengl`.
- Nothing outside `backends/` names a backend. A backend is chosen once, at
  creation (`VulkanicGal::create*` with `BackendChoice`), and only the bridge
  creates GALs; afterwards behavior differences are capabilities
  (`BackendFeature`, `BackendLimits`, `ShaderConventions`).
- The core GAL and backends carry no game vocabulary, never branch on label
  text, and import nothing above them (scene, shared, renderers, bridge).
- An item is `pub` only if code outside `vulkanic` uses it, and is
  documented; test hooks are `#[cfg(test)]` `*_for_test`.
- Code outside `vulkanic` uses the public GAL modules only; tests there build
  GALs through `test_support` (test builds only).

Known large functions kept intact: the command-op validator
(`gal/command_validation.rs`) and the hazard analysis (`gal/hazards.rs`).

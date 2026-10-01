# vulkanic (VulkanicGAL)

The graphics abstraction layer: handles, resources, commands, frames, sync,
capabilities and metrics (`gal.rs`, `resources.rs`, `commands.rs`, `frame.rs`,
`sync.rs`, `handles.rs`, `error.rs`), plus the private backends under
`backends/` (`vulkan/`, `opengl/`).

Rules, enforced by `architecture_boundary.rs`:
- `ash`/`shaderc` stay in `backends/vulkan`, `glow` in `backends/opengl`.
- Nothing outside `backends/` names a backend; behavior differences are
  capabilities (`BackendFeature`, `BackendLimits`, `ShaderConventions`).
- The core GAL and backends carry no game vocabulary, never branch on label
  text, and never import renderers (world, GUI, shader pack).
- GPU profiling scopes are opaque indices assigned by renderers.
- Code outside `vulkanic` uses the public GAL modules only; tests there build
  GALs through `test_support` (test builds only).

Everything else in this directory (`ffi/`) is the FFI bridge, awaiting its
move to `render/bridge`. The shader pack, world renderer and GUI renderer have
moved to `render/shaderpack`, `render/worldrender` and `render/guirender`.

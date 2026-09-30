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

Everything else in this directory is a renderer awaiting its move to
`render/worldrender`, `render/guirender`, `render/shaderpack`,
`render/shared` or `render/bridge`.

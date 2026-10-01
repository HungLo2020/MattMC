# vulkanic (VulkanicGAL)

The graphics abstraction layer and nothing else: handles, resources,
commands, frames, sync, capabilities and metrics (`gal.rs`, `resources.rs`,
`commands.rs`, `frame.rs`, `sync.rs`, `handles.rs`, `error.rs`,
`metrics.rs`), GAL creation (`create.rs`), and the private backends under
`backends/` (`vulkan/`, `opengl/`).

Rules, enforced by `architecture_boundary.rs`:
- `ash`/`shaderc` stay in `backends/vulkan`, `glow` in `backends/opengl`.
- Nothing outside `backends/` names a backend. A backend is chosen once, at
  creation (`VulkanicGal::create*` with `BackendChoice`), and only the bridge
  creates GALs; afterwards behavior differences are capabilities
  (`BackendFeature`, `BackendLimits`, `ShaderConventions`).
- The core GAL and backends carry no game vocabulary, never branch on label
  text, and import nothing above them (scene, shared, renderers, bridge).
  Renderers keep their own profiles and embed the GAL's `SubmitProfile`.
- GPU profiling scopes are opaque indices assigned by renderers.
- Code outside `vulkanic` uses the public GAL modules only; tests there build
  GALs through `test_support` (test builds only).

The Java FFI bridge lives in `render/bridge`; the shader pack, world renderer
and GUI renderer in `render/shaderpack`, `render/worldrender` and
`render/guirender`.

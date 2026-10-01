# Render architecture

The native renderer is split into layers with one-way dependencies. Java hands
semantic frames to the bridge; renderers turn them into GAL command lists; the
GAL validates and executes them on a private backend.

```text
render/
├── bridge/       Java C ABI: wire records, decoding, context registry (composition root)
├── worldrender/  world renderer; composes the GUI on the whole-frame route
├── guirender/    GUI renderer: sprites, quads, item meshes, post effects
├── shaderpack/   shader-pack parsing, planning and runtime
├── shared/       helpers used by both renderers
├── scene/        wire and data vocabulary (constants, data types)
├── vulkanic/     VulkanicGAL: the graphics abstraction layer and its backends
└── chunk/        native chunk meshing, render lists and sorting used by Java's chunk renderer
```

## Dependency rules

Each layer may use the public GAL modules and the layers listed for it. The
rules are enforced by
[`architecture_boundary.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/architecture_boundary.rs),
which runs with the Rust tests.

| Layer | May depend on |
| --- | --- |
| `vulkanic` (GAL and backends) | nothing above it |
| `scene` | GAL value types only (`vulkanic::resources`) |
| `shared` | the public GAL |
| `shaderpack` | `scene` |
| `guirender` | `scene`, `shared`, `shaderpack` |
| `worldrender` | `scene`, `shared`, `shaderpack`, `guirender` |
| `bridge` | everything above, as the composition root |

Further rules:

- Only `vulkanic/backends/` names a backend (Vulkan or OpenGL). Everyone else
  branches on `BackendCapabilities` (features, limits, shader conventions),
  never on which backend is running.
- Only the bridge creates GALs (`VulkanicGal::create*` with `BackendChoice`).
- Code outside `vulkanic` uses the public GAL modules only; tests build GALs
  through `vulkanic::test_support`.
- `guirender` never names `worldrender`. World-owned atlases reach the GUI
  through the `GuiAtlasOwner` trait, which the world renderer implements.
- The GAL carries no game vocabulary and never branches on resource labels.
  Renderer-specific profiling lives in the renderers (`WholeFrameProfile` in
  `worldrender` embeds the GAL's `SubmitProfile`).

## Where new code goes

| You are adding | Put it in |
| --- | --- |
| A new kind of draw, pass or effect for the world | `worldrender/` (see its README) |
| GUI drawing, item rendering or a GUI post effect | `guirender/` |
| A wire constant or data type shared with Java | `scene/` |
| Shader-pack parsing or pass planning | `shaderpack/` |
| A new Java entry point or wire record | `bridge/` (see [Java Bridge](JAVA-BRIDGE.md)) |
| A new GPU capability, resource type or command | `vulkanic/` (see [VulkanicGAL](VULKANIC-GAL.md)) |

A new GPU feature belongs in the GAL only if it is game-agnostic; anything
that knows about blocks, entities, the GUI or shader packs belongs in a
renderer. If a renderer needs a backend difference, add a capability rather
than checking the backend.

## Troubleshooting

- **A boundary test fails after a change.** Its message names the file, line
  and rule. Move the code to the layer allowed to know about it, or expose what
  you need through that layer's public API. Don't add exceptions to the test.
- **"backend identity must not be exposed".** You compared against a backend
  name or added a backend-identifying capability field; add a feature flag or
  limit instead.

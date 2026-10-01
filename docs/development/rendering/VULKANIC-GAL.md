# VulkanicGAL

VulkanicGAL is the graphics abstraction layer every renderer records into. It
owns one device through a private backend (Vulkan or OpenGL), validates every
request, and executes command lists. It knows nothing about the game. Source:
[`render/vulkanic/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic), with the `VulkanicGal` API in
[`vulkanic/gal/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/gal) (one module per concern).

The API reference is the rustdoc: every public item is documented. Build and
open it with:

```sh
cd src/main/rust
cargo doc --no-deps --open    # then open mattmc_rust::render::vulkanic
```

## The model in five rules

1. **Handles are generational.** Every resource is a `Handle` (kind, slot,
   generation). Using a destroyed handle fails with `StaleHandle`, never
   aliases a newer resource.
2. **Creation is validated against capabilities.** Descriptors (`BufferDesc`,
   `TextureDesc`, `GraphicsPipelineDesc`, ...) are checked against
   `BackendCapabilities` features and limits before the backend sees them.
   Check `capabilities().supports(...)` instead of assuming a backend.
3. **Work is explicit command lists.** Record `CommandOp`s, wrap them with
   `create_command_list`, and submit a `SubmissionBatch`. Submission validates
   ops and handles, removes redundant binds, and runs hazard analysis.
4. **Hazards need explicit barriers.** The GAL tracks every access in a
   batch. Overlapping accesses that conflict (a write and any other access
   to the same range) must be separated by a `CommandOp::Barrier`, or the
   submit is rejected. The GAL does not insert barriers for you.
5. **Destroy is safe while in flight.** `destroy` fails while other
   resources depend on the handle. A resource still used by an incomplete
   submission is destroyed when that submission retires (`retire_completed`
   or `retire_through`). Inside a command-recording scope
   (`begin_command_recording`/`finish_command_recording`), destroys queue
   until the outermost scope closes.

Presentation follows its own cycle: `configure_frame_surface`, then per frame
`acquire_frame`, `create_frame_target` for the acquired image, render, submit,
and `present_frame` (or `cancel_frame`). Check `AcquiredFrame::status`:
`Resized` and `Minimized` mean there is no image to render this time.

## Changing the GAL

- **Keep it game-agnostic.** If code mentions blocks, entities, the GUI or
  shader packs, it belongs in a renderer
  ([Render Architecture](RENDER-ARCHITECTURE.md)).
- **Add capabilities, not backend checks.** A behavior difference between
  backends becomes a `BackendFeature` flag or a `BackendLimits` field
  reported by each backend.
- **Make an item `pub` only if code outside `vulkanic` uses it**, and
  document it: `render/vulkanic/mod.rs` sets `#![warn(missing_docs)]`, so an
  undocumented public item is a build warning. Internal items use
  `pub(in crate::render::vulkanic)` or narrower.
- **Test-only access** goes in `gal/test_hooks.rs` as `#[cfg(test)]`
  `pub(crate)` methods named `*_for_test`; read-only diagnostics for capture
  tooling are named `*_for_capture`.
- **New command op:** add the `CommandOp` variant, validate it in
  `gal/command_validation.rs`, record its accesses in `gal/hazards.rs`, lower
  it in each backend, and add tests in `vulkanic/tests/`.

## Testing

```sh
cd src/main/rust
cargo test --release render::vulkanic   # GAL, backend and boundary tests
cargo test --release                     # everything (about 90 s)
```

GAL tests live in [`vulkanic/tests/`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/tests), one file per
concern. Tests elsewhere build GALs with `vulkanic::test_support` (mock,
Vulkan or OpenGL). Backend and pixel tests need a working Vulkan and OpenGL
device.

## Common errors

| Error | Usual cause |
| --- | --- |
| `StaleHandle` | The handle was destroyed, or is null. |
| `WrongHandleType` | A handle of another kind was passed (for example a texture where a view is expected). |
| `DependencyViolation` | Destroying a resource that a view, set, pipeline or target still references; destroy dependents first. |
| `overlapping ... access ... conflicts with prior ... access` | Two accesses to the same range in one batch with no barrier between them. |
| `attachment load depends on a prior pass that did not store` | A pass loads an attachment that an earlier pass ended with `DontCare`. |
| `UnsupportedFeature` | The backend lacks a feature or limit the request needs; check capabilities first. |
| `GAL command-recording scope is not active` | `finish_command_recording` without a matching `begin_command_recording`. |

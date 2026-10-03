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
   submit is rejected. The barrier's `before` must cover the preceding access
   and `after` must cover the next use. Clearing an attachment does not remove
   a preceding sampled-read dependency: transition from `ShaderRead`, not
   `Undefined`, when an earlier stage sampled it. The GAL does not insert
   barriers for you.
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

Resource bindings describe state; draws and dispatches consume them. Hazard
tracking must record each use even when the resource set was not rebound after
a barrier. Pass fusion may combine attachment load/store passes, but must
preserve repeated clears and discard boundaries.

For CPU changes to hazard tracking, compare the gameplay benchmark's
`gal-hazard-analysis` time alongside its read/write-event and barrier counters.
Keep validation enabled for correctness runs and use a separate validation-off
workload for timing. Accesses to unrelated ranges preserve the pending destination
vector; overlapping accesses still validate its state and subtract their covered
range. Preserve every untouched range and destination-state check when changing
this hot path.

The standalone GAL `SubmitProfile.resource_creates_delta` and
`resource_destroys_delta` cover submission only. The world/GUI coordinator
replaces these two embedded counters with deltas over the accepted whole-frame
attempt, including frontend preparation and recording-scope retirement. Check
`gal-command-recording-deferred-destroys` too. Older benchmark artifacts with
submit-only counters cannot establish absence of per-frame resource churn;
Completion retirement outside this attempt is outside its destruction delta.
Compare aggregate GAL counters after completion for leak checks; per-attempt
creation/destruction imbalance alone is not proof of growth. These are resource
counts, not GPU memory byte measurements.

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

Color formats retain their exact channel count and precision. `R16Float`
uses two bytes per texel for upload/readback and an exact single-channel
half-float attachment in both private backends. Its appended wire value is 12;
all prior texture-format values and request layouts remain unchanged. The
shared color-format conformance test clears it to 0.5 and checks half-float
readback on both backends, alongside exact resource creation for other formats.

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

GPU timestamp profiling is asynchronous. The Vulkan backend reserves separate
query pairs for each disjoint timing span, then aggregates spans by scope when
the submission retires. Repeated passes must not rewrite a scope's old query
pair without a reset. Each submission supports 128 spans; exceeding that bound
marks its timing sample unavailable, without changing rendering. Query sets
remain reserved until their submissions retire. See
[`lowering.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/backends/vulkan/lowering.rs)
for allocation and decoding.

Swapchain acquisition metrics describe receipts held by the CPU, not GPU
completion. For overlap diagnosis, `MATTMC_TRACE_GPU_OVERLAP=1` logs a freshly
queried timeline and counts incomplete present images separately from resource
updates. It performs no wait or retirement. See the
[gameplay verification procedure](RENDER-VERIFICATION.md#4-performance-ab).

| Error | Usual cause |
| --- | --- |
| `StaleHandle` | The handle was destroyed, or is null. |
| `WrongHandleType` | A handle of another kind was passed (for example a texture where a view is expected). |
| `DependencyViolation` | Destroying a resource that a view, set, pipeline or target still references; destroy dependents first. |
| `overlapping ... access ... conflicts with prior ... access` | Two accesses to the same range in one batch with no barrier between them. |
| `barrier before ... does not cover prior ... access` | The declared source state omits an earlier access. The error includes the resource label and command list; trace their stage order and correct the producer's barrier. |
| `attachment load depends on a prior pass that did not store` | A pass loads an attachment that an earlier pass ended with `DontCare`. |
| `UnsupportedFeature` | The backend lacks a feature or limit the request needs; check capabilities first. |
| `GAL command-recording scope is not active` | `finish_command_recording` without a matching `begin_command_recording`. |

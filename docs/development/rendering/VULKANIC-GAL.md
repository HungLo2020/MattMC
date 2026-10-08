# VulkanicGAL

VulkanicGAL is the graphics abstraction layer every renderer records into. It
owns one device through a private backend (Vulkan or OpenGL), validates resource
creation, and executes command lists with optional per-submission checks. It knows nothing about the game. Source:
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
   `create_command_list`, and submit a `SubmissionBatch`. Submission removes
   redundant binds and hoists eligible host-buffer writes. Op, handle and
   hazard checks run in validation mode; release play skips those checks by
   default. See the validation notes below.
4. **Hazards need explicit barriers.** With validation enabled, the GAL
   tracks accesses in a batch and rejects conflicting overlaps (a write and
   any other access to the same range) without a `CommandOp::Barrier`.
   Producers must satisfy this contract even when validation is disabled. The barrier's `before` must cover the preceding access
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
6. **Owners retire; they don't force.** A resource whose consumers live in
   other caches (sampled targets, depth/shadow samplers, material textures)
   is released with `retire`: destroyed now when nothing binds it, otherwise
   destroyed when its last dependent is destroyed (a view's release then
   releases its texture). Teardown code that ignores the result
   (`let _ = gal.retire(..)`) uses `retire`; `destroy` keeps the strict
   `DependencyViolation` for code that must know. Teardown also drops cached
   consumer sets first (`release_cached_source_consumers`). Every validation
   failure is logged as `rust_gal_validation_failure` (rate-limited); the
   [lifecycle gate](RENDER-VERIFICATION.md#lifecycle-gate) fails on a logged
   dependency violation.

Dependency retirement and submission retirement are separate. At
[`7f256b53`](https://github.com/HungLo2020/MattMC/commit/7f256b5354033eff7553f51a10539fac5a68a0bd),
[`retire`](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/src/main/rust/render/vulkanic/gal/lifetime.rs#L33-L52)
marks a referenced resource without destroying it; releasing its last dependent
re-enters ordinary destruction, which still defers backend release through the
last incomplete submission. A retained dependent can therefore keep a retired
resource live indefinitely; this is not a memory bound or a completion fence.
The [new fixture](https://github.com/HungLo2020/MattMC/blob/4740f8fabffd878286850083e2d86ff733c9121e/src/main/rust/render/vulkanic/tests/handles.rs#L50-L80)
covers two views of one texture and an unreferenced texture, not the full
consumer-cache/recording/submission matrix. This documentation review inspected
the definition only. Preserve the author's bounded lifecycle report separately
from broad leak or native-crash acceptance.

Presentation follows its own cycle: `configure_frame_surface`, then per frame
`acquire_frame`, `create_frame_target` for the acquired image, render, submit,
and `present_frame` (or `cancel_frame`). Check `AcquiredFrame::status`:
`Resized` and `Minimized` mean there is no image to render this time.

Renderer console diagnostics use the std-only helpers in
[`core/console.rs`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/core/console.rs)
with `format_args!`; the Vulkan backend reexports them privately through
`trace::stdout` and `trace::stderr`. Keep their existing enablement gates
before constructing messages. These writes preserve the stream and newline,
but ignore console I/O errors: a launcher closing its pipe must not abort a
submission or affect resource retirement. Rust's ordinary
[`println!`](https://doc.rust-lang.org/std/macro.println.html) and
[`eprintln!`](https://doc.rust-lang.org/std/macro.eprintln.html) panic on write
failure, so do not use them for production renderer diagnostics. Actual backend errors
still return through `GalResult`; logging is not an error-handling substitute.
Run `cargo test --release diagnostic_stdio_does_not_abort_on_closed_pipe --
--test-threads=1` from `src/main/rust` after changing this path. Its bounded child
checks open and closed stdout/stderr and exact line bytes without a GPU; it does
not establish gameplay shutdown correctness.

Resource bindings describe state; draws and dispatches consume them. Hazard
tracking must record each use even when the resource set was not rebound after
a barrier. Pass fusion may combine attachment load/store passes, but must
preserve repeated clears and discard boundaries.

Consecutive draws reuse each bound binding's recorded read accesses until that
binding changes: a bind clears only its own entry, any other non-draw command
clears all of them, and write bindings are always re-recorded so repeated writes
are still rejected (`consecutive_draws_reuse_read_bindings_*`,
`rebinding_one_set_still_checks_unchanged_reads_against_new_writes`). Access
events of one resource set and dynamic-offset combination are built once per
submission. Pending barrier destinations are kept per resource key, so an access
only examines destinations of its own resource.

Release builds skip the per-frame command-op, handle and hazard checks during
normal play. Debug builds, `cargo test`, buffer-upload captures and any run
with `MATTMC_GAL_VALIDATION=1` keep them; the capture harness sets that
variable whenever `--validation standard` is active
([`per_frame_validation`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/gal/submission.rs)).
Creation-time descriptor checks, batch/list limits, normalization, backend
submission and in-flight lifetime tracking still run. The environment setting
is read once per process (`1`, `true` and `on` enable it), so set it before
launch. GAL checks are separate from Vulkan's validation layers; enable the
appropriate checks for the run.

Vulkan command labels (`gal.batch.*`, `gal.command-list.*`, `gal.pass.*`) are
emitted only with Vulkan debug-utils available and at least one enabling
condition: validation, a requested RenderDoc capture (`MATTMC_RENDERDOC_CAPTURE`
or RenderDoc's `ENABLE_VULKAN_RENDERDOC_CAPTURE`), or
`MATTMC_VULKAN_DEBUG_LABELS=1`. Set the last switch before attaching another
tool. Object naming remains independent of the command-label switch and uses
debug-utils when available
([`command_labels_requested`](https://github.com/HungLo2020/MattMC/blob/master/src/main/rust/render/vulkanic/backends/vulkan/device.rs)). Run new rendering work under a validation
capture before trusting it.

[Submission trace messages](https://github.com/HungLo2020/MattMC/blob/20e157cab7962140b30b83f40374cdeb1e6a8b19/src/main/rust/render/vulkanic/gal/submission.rs#L5-L12)
are now formatted only when `MATTMC_TRACE_WHOLE_FRAME` is present. This switch is
read once; even a value of `0` is present, so unset it for an untraced timing
run. It is separate from the command-label switches and validation mode.

For CPU changes to hazard tracking, set `MATTMC_GAL_VALIDATION=1` and compare
the gameplay benchmark's `gal-hazard-analysis` time alongside its
read/write-event and barrier counters. Keep validation enabled for correctness
runs and use a separate validation-off workload for timing. Accesses to unrelated ranges preserve the pending destination
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

The Vulkan lowerer copies large or unaligned `HostWriteBuffer` data through
persistently mapped staging chunks (`backends/vulkan/lowering/staging.rs`).
A submission owns its chunks until its timeline value retires; they then return
to a bounded idle list (32 MiB). Allocation takes the smallest idle chunk large
enough for the request; retirement retains smaller chunks first so a rare large
upload cannot crowd out the usual 4 MiB chunks. The 32 MiB limit covers idle
storage, not chunks owned by in-flight submissions. Do not reintroduce per-upload
buffer and memory allocation: the earlier workload attributed about 2 ms per
shader frame to driver allocation churn.

Submission hoists a `HostWriteBuffer` with its adjacent, same-queue
`TransferDst` entry/exit barriers only if no earlier command in that list
references its buffer, including through a resource set. The Vulkan lowerer
groups consecutive host writes to distinct buffers behind shared dependencies.
Keep writes
self-contained and preserve earlier-use ordering; this is a command-list
transformation, not a batch-wide move across lists. See
[`host_write_hoist.rs`](https://github.com/HungLo2020/MattMC/blob/121ad13c84e45555c34814d54a8199194b37f39c/src/main/rust/render/vulkanic/gal/host_write_hoist.rs).

Vulkan device creation enables `drawIndirectFirstInstance` when the physical
device reports it, alongside the separately queried `multiDrawIndirect`
([`device.rs`](https://github.com/HungLo2020/MattMC/blob/697b0a3c6200151830a565c73aaee88d323eb484/src/main/rust/render/vulkanic/backends/vulkan/device.rs)).
Nonzero indirect `firstInstance` values require that enabled feature; do not
infer it from multi-draw support alone. This device setup change supports the
terrain instance addressing described in [Render Architecture](RENDER-ARCHITECTURE.md#resource-ownership-and-retries).
DH's new shared pages and per-page bindings still use individual indexed draws;
feature enablement does not implement DH multi-draw.

GLSL modules compile through Shaderc at 50–80 ms each, so the Vulkan backend
keeps compiled SPIR-V on disk (`backends/vulkan/spirv_disk_cache.rs`), keyed by
a schema/Shaderc tag, build profile, stage, entry point and full source, and
validated on load. It defaults to `~/.cache/mattmc/spirv-v1`;
`MATTMC_SPIRV_CACHE_DIR` overrides it (`off` disables it), and a shader dump
directory disables it. Bump `SCHEMA` when compile options change.
`MATTMC_TRACE_VK_SHADER_COMPILE=1` prints compile and pipeline creation times.

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
[gameplay verification procedure](RENDER-VERIFICATION.md#3-real-config-session).

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

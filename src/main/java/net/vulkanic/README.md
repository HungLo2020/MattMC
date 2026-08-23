# Vulkanic Graphics Abstraction Layer

## Scope (Current)

Vulkanic is the rendering abstraction boundary for MattMC.

Current migration policy is explicit:

- OpenGL remains the authoritative correctness baseline and private compatibility backend.
- The user-facing Vulkan option is admitted only when startup selects the Rust-owned
  whole-frame route; incomplete semantic capabilities remain unavailable rather than
  falling back to Java Vulkan or borrowed Iris state.
- Rust Vulkan presentation and the explicit VulkanicGAL own the selected Vulkan frame;
  the Java GPU device is limited to CPU-only startup metadata and fail-closed guards.
- Continue expanding Rust-owned semantic coverage and parity evidence until every
  supported rendering callsite is represented by the explicit route.

For authoritative migration status and historical audit notes, see `MIGRATION-PROGRESS.md`.

## Architecture Rules

1. **Frontend-only access for game/mod code**
   - Non-Vulkanic code should call `net.vulkanic.*` frontend APIs.
   - Non-Vulkanic code must not import `net.vulkanic.backends.*`.

2. **Backend API isolation**
   - OpenGL imports stay inside `backends/opengl/`.
   - Vulkan imports stay inside `backends/vulkan/`.

3. **Context-aware rendering APIs**
   - Rendering operations should flow through `CommandContext` signatures.
   - `getImmediateContext()` remains a temporary compatibility seam only.

## Readiness Seams (Implemented)

The codebase already has backend-agnostic seams needed for pre-implementation readiness:

- Backend identity/readiness reporting (`getActiveBackendType()`, `isNativeVulkanBackendReady()`, readiness report).
- Scoped command-context helper (`withCommandContext(...)`) for safe context lifetimes.
- Portable pipeline descriptor metadata (`PipelineDescriptor.PortableState`, stable cache key).
- Descriptor-style resource binding metadata (`PipelineResourceBindings`, layout validation).
- Render-pass metadata (`VulkanicRenderPassDescriptor`).
- Resource barrier metadata (`VulkanicResourceBarriers`).

These seams support the active migration route and its validation; they do not grant
legacy Java rendering permission during a Rust-owned Vulkan frame.

## Contributor Guidance

- Prefer concrete Rust-owned semantic feature expansion with bounded validation.
- Keep backend-selection UX tied to the Rust whole-frame admission and fail closed when
  a capability is not yet coherent.
- Use scoped context handling (`withCommandContext`) instead of manual push/pop where possible.
- Keep architecture/boundary tests passing and extend guardrails when adding new migration surfaces.

## Guardrails

Architectural and migration guardrails live in `src/test/java/net/vulkanic/` and enforce:

- import boundaries,
- fail-fast backend routing expectations,
- command-context migration constraints,
- readiness report behavior.

If a change weakens these boundaries, update the design first and then update tests intentionally.

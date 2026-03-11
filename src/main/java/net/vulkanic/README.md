# Vulkanic Graphics Abstraction Layer

## Scope (Current)

Vulkanic is the rendering abstraction boundary for MattMC.

Current migration policy is explicit:

- Keep OpenGL as the active runtime backend.
- Do **not** add user-facing Vulkan backend selection yet.
- Do **not** treat existing Vulkan bootstrap classes as production-rendering readiness.
- Continue readiness hardening so Vulkan implementation can happen later without large upstream churn.

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

These seams are for compatibility preparation and validation; they do not imply Vulkan rendering is production-enabled.

## Contributor Guidance

- Prefer readiness hardening over feature expansion.
- Avoid adding backend-selection UX, flags, or config paths for Vulkan at this stage.
- Use scoped context handling (`withCommandContext`) instead of manual push/pop where possible.
- Keep architecture/boundary tests passing and extend guardrails when adding new migration surfaces.

## Guardrails

Architectural and migration guardrails live in `src/test/java/net/vulkanic/` and enforce:

- import boundaries,
- fail-fast backend routing expectations,
- command-context migration constraints,
- readiness report behavior.

If a change weakens these boundaries, update the design first and then update tests intentionally.

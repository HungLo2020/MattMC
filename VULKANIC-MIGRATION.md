# Vulkanic Graphics Abstraction Layer — Migration Guide

**Last Updated:** 2026-02-23  
**Status:** Phase 1 & 2 complete · Phase 2.5 deprecated-method migration complete · Phase 3 in progress (3a–3b done, draw path wired, one bypass migrated)

---

## 1 · Goal

**Vulkanic is the backend.** Its job is to own both the OpenGL implementation and the future Vulkan implementation. Everything — game code, mod code, and Blaze3D — must flow through Vulkanic so that swapping in the Vulkan backend requires zero changes above the Vulkanic boundary.

The Vulkan backend lives in `net.vulkanic.backends.vulkan`. It does not live in Blaze3D.

---

## 2 · The Architectural Problem

### 2.1 · Two Competing Abstraction Layers

The codebase currently has two separate abstraction layers that serve overlapping roles:

**Layer A — Blaze3D** (`net.blaze3d.systems`, `net.blaze3d.opengl`, `net.blaze3d.pipeline`)
```
GpuDevice → CommandEncoder → RenderPass → RenderPipeline
```
Blaze3D defined its own device/command/pipeline model. `GlDevice`, `GlCommandEncoder`, and `GlRenderPass` are the OpenGL implementations. These are used by core Minecraft rendering (LevelRenderer, GuiRenderer, SkyRenderer, etc.).

**Layer B — Vulkanic** (`net.vulkanic`)
```
VulkanicAPI → GraphicsBackend → OpenGLBackend
```
Vulkanic is the intended backend abstraction. All 570 VulkanicAPI methods dispatch through `GraphicsBackend` to `OpenGLBackend`. `OpenGLBackend` is the only file that may import `org.lwjgl.opengl.*`.

### 2.2 · How They Interact Today

Currently, Blaze3D's OpenGL backend classes (`GlCommandEncoder`, `GlStateManager`, `GlDevice`) call Vulkanic internally:

```
Game code → RenderSystem.getDevice() → GlDevice
                                           ↓ calls GlCommandEncoder
                                              ↓ calls GlStateManager
                                                 ↓ calls VulkanicAPI.*(getImmediateContext(), ...)
                                                    ↓
                                                  OpenGLBackend → LWJGL
```

This is backwards from the goal. Blaze3D is calling Vulkanic — meaning Blaze3D is a consumer of Vulkanic, not a peer. When it comes time to add a Vulkan backend, **the backend needs to be in Vulkanic**, but the current code would require implementing `VkDevice implements GpuDevice` in Blaze3D, which is not owned by this project and cannot be cleanly controlled.

### 2.3 · What Needs to Change

The Blaze3D concepts of `GpuDevice`, `CommandEncoder`, `RenderPass`, `RenderPipeline`, and `FrameGraphBuilder` are sound abstractions — they model the Vulkan execution model well. The problem is where they live.

**The correct direction:**
- The `GpuDevice / CommandEncoder / RenderPass / RenderPipeline` abstraction layer moves **into Vulkanic**.
- Blaze3D's `GlDevice`, `GlCommandEncoder`, `GlRenderPass` either:
  - Become thin delegating facades that call Vulkanic (not the other way around), or
  - Are replaced entirely by Vulkanic's own implementations.
- The Vulkan backend is then added in `net.vulkanic.backends.vulkan` as a full implementation of the same Vulkanic interfaces.
- `VulkanicAPI.initialize(BackendType.VULKAN)` selects the Vulkan path. Everything above it is unchanged.

---

## 3 · The Target Architecture

```
┌────────────────────────────────────────────────────────────────────┐
│  Game / mod rendering code (Minecraft, Iris, Sodium, DH, etc.)   │
│  Imports from net.vulkanic.* ONLY                                  │
└───────────────────────────────┬────────────────────────────────────┘
                                │
                                ▼
┌────────────────────────────────────────────────────────────────────┐
│  Vulkanic Frontend API  (net.vulkanic)                            │
│  VulkanicAPI  — 570+ static methods                                │
│  GraphicsBackend — the complete interface (device + pipeline +     │
│                    command buffer + render pass + resource mgmt)   │
│  CommandContext — wraps VkCommandBuffer or OpenGL immediate ctx    │
└──────────────────────────┬────────────────────────────────────────┘
              ┌────────────┴────────────┐
              ▼                          ▼
┌─────────────────────────┐  ┌──────────────────────────────┐
│  OpenGLBackend          │  │  VulkanBackend  (future)     │
│  net.vulkanic.backends  │  │  net.vulkanic.backends       │
│  .opengl.OpenGLBackend  │  │  .vulkan.VulkanBackend       │
│                         │  │                              │
│  Only file that may     │  │  Only file that may          │
│  import lwjgl.opengl.*  │  │  import lwjgl.vulkan.*       │
└─────────────────────────┘  └──────────────────────────────┘
```

Blaze3D (`GlDevice`, `GlCommandEncoder`, `GlRenderPass`) in this final state is either:
- A thin facade layer that calls `VulkanicAPI` for everything (delegating upward instead of downward), or
- Phased out as game code is migrated to call `VulkanicAPI` directly.

---

## 4 · What Was Accomplished

### Phase 1: Blaze3D Uses VulkanicAPI ✅ Complete

All rendering operations in `GlStateManager`, `GlCommandEncoder`, `GlDevice`, `GlDebugLabel`, `DirectStateAccess`, and related Blaze3D classes call `VulkanicAPI`. No `org.lwjgl.opengl.*` imports exist outside `OpenGLBackend`. The `ArchitecturalBoundaryTest` enforces this.

### Phase 2: Mods Use VulkanicAPI ✅ Complete

Sodium, Iris, and Distant Horizons all call VulkanicAPI. None import `org.lwjgl.opengl.*` directly.

### Phase 2.5: CommandContext Migration ✅ Complete

All 283 previously-deprecated VulkanicAPI methods replaced with `CommandContext`-aware signatures. `GraphicsBackend` now has 212 rendering methods that all accept `CommandContext`. Zero `@Deprecated` annotations remain in VulkanicAPI, GraphicsBackend, or OpenGLBackend. The `CommandContext.getHandle()` method is designed to carry a `VkCommandBuffer` handle when the Vulkan backend is active.

### Phase 3a: Resource Types & Device Lifecycle ✅ Complete

New abstract types in `net.vulkanic`: `VulkanicBuffer` / `VulkanicBufferSlice`, `VulkanicTexture` / `VulkanicTextureView` / `VulkanicTextureFormat`, `PipelineHandle` / `PipelineDescriptor`. All have `net.vulkanic.backends.opengl` implementations. `GraphicsBackend` now has `createManagedBuffer`, `createManagedTexture`, `createManagedTextureView`, `createPipeline`, `beginCommandBuffer`, `submitCommandBuffer`. `GlDevice` registers with `VulkanicAPI` on construction.

### Phase 3b: Render-Pass Abstraction ✅ Complete

`VulkanicRenderPass` interface (`setPipeline`, `setVertexBuffer`, `setIndexBuffer`, `drawIndexed`, `draw`, `close`) added to `net.vulkanic`. `GraphicsBackend.beginRenderPass(ctx, label, colorTarget, clearColor[, depthTarget, clearDepth])` is the interface entry point. `OpenGLBackend.beginRenderPass` creates an FBO, attaches textures, clears, sets viewport, and returns an `OpenGLRenderPass`.

`GlCommandEncoder.createRenderPass` now delegates FBO lifecycle to `VulkanicAPI.beginRenderPass` for the normal rendering path (Iris shadow/safeMultiply paths keep their own FBO handling).

`createTextureViewFromGlHandle` is a **live transitional bridge** used every frame by `GlCommandEncoder.createRenderPass`. It cannot be removed until `GlDevice.createTexture` returns `VulkanicTexture` directly. The Javadoc explains exactly what step would allow its removal.

### Phase 3 — Draw Path Wiring ✅ Complete

`GlCommandEncoder.drawFromBuffers` now routes ALL draw calls explicitly through `VulkanicAPI` rather than through `GlStateManager` wrappers:

- `GlStateManager._glBindBuffer(34963, ...)` → `VulkanicAPI.bindBuffer(ctx, GL_ELEMENT_ARRAY_BUFFER, ...)`
- `GlStateManager._drawElements(...)` → `VulkanicAPI.drawElements(ctx, ...)` (with Iris tessellation override preserved inline)
- `GlStateManager._drawArrays(...)` → `VulkanicAPI.drawArrays(ctx, ...)`

The method now obtains a single `CommandContext` at entry and shares it across all VulkanicAPI calls, matching Vulkan's command-buffer recording model.

`GlCommandEncoder.getActiveVulkanicRenderPass()` accessor added, making the active `VulkanicRenderPass` available for future code paths without casting to `GlCommandEncoder`.

### Phase 3 — First Bypass Migration ✅ Complete

`VoxelMap CompressibleGLBufferedImage.uploadToTexture` was using a bind-then-generate pattern:
```java
// Before — mutates global GL texture bind state
VulkanicAPI.bindTexture2D(ctx, glId);
VulkanicAPI.generateTextureMipmap(ctx, GL_TEXTURE_2D);
```
Migrated to the DSA (Direct State Access) form:
```java
// After — no global state mutation, one call
VulkanicAPI.generateTextureMipmapDSA(ctx, glId);
```
This reduces VoxelMap's bypass call count from **1** to **0**, bringing the total non-Blaze3D bypass count from 480 to **479**.

---

## 5 · What Needs to Happen Next

### Phase 3: Elevate Vulkanic to Own the Full Abstraction

**Goal:** Grow `GraphicsBackend` to encompass the `GpuDevice / CommandEncoder / RenderPass / RenderPipeline` concepts that currently live in Blaze3D. Then make Blaze3D delegate *to* Vulkanic rather than vice versa.

This is the step that puts Vulkanic in the position where adding a Vulkan backend requires only implementing a new `GraphicsBackend`.

#### 3a — Migrate the Device-Level Concepts into GraphicsBackend

The `GpuDevice` interface in Blaze3D defines:
- `createCommandEncoder()` — returns a `CommandEncoder`
- `createTexture(...)` — allocates a GPU texture
- `createBuffer(...)` — allocates a GPU buffer
- `precompilePipeline(RenderPipeline)` — compiles shaders and bakes pipeline state

These all belong in `GraphicsBackend`. The goal is that `VulkanicAPI.createTexture(...)`, `VulkanicAPI.createBuffer(...)`, `VulkanicAPI.beginCommandBuffer()`, etc. dispatch through `GraphicsBackend` to either `OpenGLBackend` or `VulkanBackend`.

For the OpenGL backend, these methods delegate to the existing `GlDevice` / `GlCommandEncoder` implementations. Nothing changes in behavior; the ownership of the interface shifts.

#### 3b — Migrate the Command-Encoder / Render-Pass Concepts

The `CommandEncoder` and `RenderPass` interfaces define:
- `createRenderPass(textureView, clearColor, ...)` → begin rendering to a target
- `setPipeline(RenderPipeline)` → bind a compiled pipeline
- `setVertexBuffer / setIndexBuffer` → bind geometry
- `drawIndexed / draw` → issue a draw call
- `close()` → end the render pass

These map directly to Vulkan:
- `vkCmdBeginRenderPass`
- `vkCmdBindPipeline`
- `vkCmdBindVertexBuffers / vkCmdBindIndexBuffer`
- `vkCmdDrawIndexed`
- `vkCmdEndRenderPass`

The Vulkanic `CommandContext` already carries the `getHandle()` method intended to wrap a `VkCommandBuffer`. Extending `GraphicsBackend` with `beginRenderPass(ctx, target, ...)`, `setPipeline(ctx, pipeline)`, `drawIndexed(ctx, ...)`, `endRenderPass(ctx)` gives Vulkanic complete ownership of this model.

`OpenGLBackend` implements these by calling `GlCommandEncoder` (as it currently does). `VulkanBackend` implements them natively with Vulkan API calls.

#### 3c — Migrate the Pipeline / Shader Concepts

`RenderPipeline` in Blaze3D is a descriptor-style object (vertex shader, fragment shader, blend function, depth test, cull mode, vertex format, etc.). `GpuDevice.precompilePipeline()` compiles it into a `CompiledRenderPipeline` (`GlRenderPipeline` today; a `VkPipeline` future).

This pipeline concept belongs in Vulkanic as:
- `VulkanicAPI.createPipeline(PipelineDescriptor)` → returns an opaque `PipelineHandle`
- `OpenGLBackend.createPipeline(...)` → compiles shaders, links programs, caches state
- `VulkanBackend.createPipeline(...)` → compiles SPIR-V, creates `VkPipeline`

#### 3d — Migrate the Resource Types

`GpuBuffer` and `GpuTexture`/`GpuTextureView` are currently Blaze3D types. They need Vulkanic equivalents:
- `VulkanicBuffer` — wraps `GlBuffer` (OpenGL) or `VkBuffer` (Vulkan)
- `VulkanicTexture` / `VulkanicTextureView` — wraps `GlTexture` (OpenGL) or `VkImage`/`VkImageView` (Vulkan)

Once these types live in Vulkanic, game code that currently holds `GpuBuffer` references would hold `VulkanicBuffer` references instead.

#### 3e — Migrate the FrameGraph

`FrameGraphBuilder` and `FramePass` define the per-frame render graph (sky pass, main pass, particle pass, etc.) and manage resource lifetimes. This is a Vulkan-compatible design (render passes, resource barriers, dependency ordering). It belongs in Vulkanic as a portable scheduler above the `CommandEncoder`/`RenderPass` model.

Once in Vulkanic, `LevelRenderer` would use `VulkanicAPI.beginFrame()` → register `FramePass` entries → `VulkanicAPI.executeFrame()`, and the backend manages command buffer submission, synchronization, and resource lifetime.

---

## 6 · Ordering and Backward Compatibility

**Every step must leave the OpenGL backend 100% functional.**

The migration proceeds by expanding `GraphicsBackend` one concept at a time, implementing each new method in `OpenGLBackend` by delegating to existing `GlDevice`/`GlCommandEncoder` code, then updating Blaze3D to call `VulkanicAPI` for that concept instead of using its own implementation.

Recommended order (each step builds on the previous):

1. **Buffer lifecycle** — `createBuffer` / `deleteBuffer` / `mapBuffer` / `unmapBuffer` into `GraphicsBackend`. These have the most bypass callers (DH, Sodium) and are self-contained.

2. **Texture lifecycle** — `createTexture` / `deleteTexture` / `uploadTexture` into `GraphicsBackend`. Needed before render pass attachments can be expressed in Vulkanic.

3. **Pipeline objects** — `createPipeline(PipelineDescriptor)` into `GraphicsBackend`. Replaces the scattered state-setting calls with a compiled, opaque handle.

4. **Render pass** — `beginRenderPass(ctx, target, pipeline, ...)` / `endRenderPass(ctx)` into `GraphicsBackend`. This is the key command-buffer recording step.

5. **Draw calls** — `draw(ctx, ...)` / `drawIndexed(ctx, ...)` into `GraphicsBackend` render-pass scope. These already exist as standalone `VulkanicAPI` methods; they gain render-pass context here.

6. **Frame graph** — Migrate `FrameGraphBuilder`/`FramePass` into Vulkanic to manage per-frame submission, synchronization, and resource lifetime.

At any point in this sequence, the OpenGL backend works exactly as before (it delegates to the same `GlDevice`/`GlCommandEncoder` implementations), and the Vulkan backend gains a new foothold.

---

## 7 · Current State vs Target: Bypass Call Inventory

These are the call sites that currently bypass the intended path and need to move as part of Phase 3:

| Subsystem | Bypass `getImmediateContext()` calls | Category |
|---|---|---|
| **Distant Horizons** | 252 | Buffer, texture, shader, draw, state |
| **Iris Shaders** | 161 | Shader setup, compute, image bindings, state |
| **Sodium** | 66 | Buffer, VAO, shader, uniform, fence |
| **VoxelMap** | 1 | Mipmap generation |
| **Blaze3D opengl** | 146 | Intentional — correct internal backend calls |
| **Blaze3D systems** | 9 | Intentional — timer query etc. |

The 480 non-Blaze3D bypass calls are the concrete migration target. Each call eliminated either:
- Moves to use a higher-level `VulkanicAPI` device/pipeline/renderpass method (Phase 3 work above), or
- Is already served by an existing `VulkanicAPI` method with a proper `CommandContext` parameter.

---

## 8 · What Adding the Vulkan Backend Looks Like

Once `GraphicsBackend` owns the full abstraction (device + pipeline + renderpass + resources + framegraph), adding the Vulkan backend is:

```java
// net.vulkanic.backends.vulkan.VulkanBackend
public class VulkanBackend implements GraphicsBackend {

    // Device
    @Override
    public VulkanicBuffer createBuffer(int usage, long size) {
        // vkCreateBuffer + vkAllocateMemory + vkBindBufferMemory
    }

    // Pipeline
    @Override
    public PipelineHandle createPipeline(PipelineDescriptor desc) {
        // Compile SPIR-V shaders, vkCreateGraphicsPipeline
    }

    // Command buffer / render pass
    @Override
    public CommandContext beginCommandBuffer() {
        // vkBeginCommandBuffer → return CommandContext wrapping VkCommandBuffer handle
    }

    @Override
    public void beginRenderPass(CommandContext ctx, VulkanicTexture colorTarget, ...) {
        // vkCmdBeginRenderPass
    }

    @Override
    public void drawIndexed(CommandContext ctx, int count, int firstIndex, int baseVertex) {
        // vkCmdDrawIndexed
    }

    @Override
    public void endRenderPass(CommandContext ctx) {
        // vkCmdEndRenderPass
    }

    @Override
    public void submitCommandBuffer(CommandContext ctx) {
        // vkQueueSubmit
    }
}
```

Selecting the backend:
```java
VulkanicAPI.initialize(BackendType.VULKAN);
// Every VulkanicAPI call now dispatches to VulkanBackend
```

No game code, mod code, or Blaze3D facade code changes. The backend switches transparently.

---

## 9 · Key Metrics (Current State)

| Metric | Value |
|---|---|
| `@Deprecated` annotations remaining in Vulkanic layer | **0** |
| `GraphicsBackend` methods with `CommandContext` param | **212** |
| Total `VulkanicAPI` static methods | **570** |
| Bypass `getImmediateContext()` calls to migrate (non-Blaze3D) | **479** (was 480; VoxelMap migrated) |
| VoxelMap bypass calls | **0** (migrated to DSA in Phase 3) |
| `drawFromBuffers` calls routing through `GlStateManager` for draws | **0** (all now via `VulkanicAPI` directly) |
| Blaze3D `GpuDevice/CommandEncoder` concepts still in Blaze3D | **Must move to Vulkanic** |
| `ArchitecturalBoundaryTest` | ✅ Passing |
| `createTextureViewFromGlHandle` transitional bridge | **Live — removal requires `GlDevice.createTexture` → `VulkanicTexture`** |

---

## 10 · Rules at Every Step

1. **OpenGL backend stays 100% functional.** Every sub-step must pass all tests.

2. **`org.lwjgl.opengl.*` imports stay in `OpenGLBackend` only.**

3. **`org.lwjgl.vulkan.*` imports stay in `VulkanBackend` only** (future).

4. **The Vulkan backend is implemented in `net.vulkanic.backends.vulkan`, not in Blaze3D.**

5. **Blaze3D delegates to Vulkanic — not the other way around.** After each migration step, Blaze3D's GL classes should be calling `VulkanicAPI` for the migrated concept, not providing their own independent implementation.

6. **Concepts migrate from Blaze3D into Vulkanic, not the reverse.** When Blaze3D has a useful abstraction (GpuBuffer, RenderPipeline, FrameGraph), the move is to bring the concept into `GraphicsBackend` and then make `GlDevice`/`GlCommandEncoder` delegate to `VulkanicAPI` for it.

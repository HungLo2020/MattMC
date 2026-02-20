# Vulkanic Graphics Abstraction Layer — Migration Guide

**Last Updated:** 2026-02-20  
**Status:** Phase 1 & 2 complete · Phase 2.5 deprecated-method migration complete · Phase 3 not yet started

---

## 1 · Executive Summary

Vulkanic is MattMC's graphics abstraction layer. Its goal is to decouple all game and mod code from OpenGL so that a Vulkan backend can be added later without changing any rendering logic above the abstraction boundary.

**What has been achieved so far:**

- No code outside `net/vulkanic/backends/opengl/` imports `org.lwjgl.opengl.*` — enforced by `ArchitecturalBoundaryTest`.
- All 283 previously-deprecated VulkanicAPI methods have been replaced with `CommandContext`-aware signatures. Zero `@Deprecated` annotations remain.
- Blaze3D (`GlStateManager`, `GlCommandEncoder`, `GlDevice`, etc.) calls VulkanicAPI exclusively — no direct LWJGL OpenGL calls anywhere outside the backend.
- Sodium, Iris, and Distant Horizons all call VulkanicAPI; none import `org.lwjgl.opengl.*` directly.

**What the previous Migration Guide got wrong:**

The old document claimed Phase 2.5 required 270-400 hours to design "command buffer infrastructure, pipeline management, descriptor sets" etc. from scratch. That analysis was incorrect because it overlooked a critical fact: **Blaze3D already has a modern, Vulkan-shaped rendering architecture built in.** The work required is not to design those abstractions — they already exist. The work is to route the remaining bypass call sites through them.

---

## 2 · The Real Architecture (As It Exists Today)

Understanding the actual call chain is essential before planning any further work.

### 2.1 · The Modern Path (Vulkan-Ready)

```
Game / mod rendering code (LevelRenderer, GuiRenderer, SkyRenderer, etc.)
    ↓  calls
RenderSystem.getDevice()                     [net.blaze3d.systems.GpuDevice interface]
    ↓  returns
GlDevice                                     [net.blaze3d.opengl.GlDevice implements GpuDevice]
    ↓  .createCommandEncoder() returns
GlCommandEncoder                             [implements CommandEncoder]
    ↓  .createRenderPass(...) returns
GlRenderPass                                 [implements RenderPass]
    ↓  .setPipeline() / .drawIndexed() etc.
GlCommandEncoder.executeDraw()               ← calls VulkanicAPI.drawIndexedInstanced(getImmediateContext(), ...)
GlCommandEncoder.applyPipelineState()        ← calls GlStateManager._enableDepthTest() etc.
    ↓
GlStateManager                               ← calls VulkanicAPI.setCapabilityEnabled(getImmediateContext(), ...)
    ↓
VulkanicAPI.*(getImmediateContext(), ...)     [net.vulkanic.VulkanicAPI]
    ↓
OpenGLBackend                                [net.vulkanic.backends.opengl.OpenGLBackend]
    ↓
LWJGL GL11/GL20/GL30/etc.                   [only here, in the backend]
```

**Key insight:** The `GpuDevice → CommandEncoder → RenderPass → RenderPipeline` stack in Blaze3D is already the Vulkan-compatible architecture. For the majority of Minecraft's core rendering (world, chunks, particles, sky, weather, GUI, post-processing) this path is already in use today.

Adding a Vulkan backend at the Blaze3D level means:

1. Implementing `VkDevice implements GpuDevice`
2. Implementing `VkCommandEncoder implements CommandEncoder`
3. Implementing `VkRenderPass implements RenderPass`
4. `GlDevice` → `VkDevice` selected at startup based on config

The `GlCommandEncoder` and `GlStateManager` VulkanicAPI calls are **backend implementation details** — they are correct, intentional, and will stay calling VulkanicAPI even in a Vulkan world (just through a different backend).

### 2.2 · The Bypass Paths (Work Remaining)

Three subsystems still call `VulkanicAPI.getImmediateContext()` directly from rendering logic instead of going through the `GpuDevice → CommandEncoder → RenderPass` stack:

| Subsystem | Bypass call sites | Notes |
|---|---|---|
| **Iris Shaders** | 161 | `IrisRenderSystem`, `GLDebug`, `IrisGenericRenderProgram`, `IrisLodRenderProgram`, Iris pipeline renderers |
| **Distant Horizons** | 252 | `GLState`, `GLBuffer`, `LodRenderer`, `ShaderProgram`, texture/framebuffer wrappers, all renderers |
| **Sodium** | 66 | `GLRenderDevice`, `GlProgram`, `GlShader`, uniform wrappers, `GlFence`, `GlVertexArray`, `GlBuffer` |
| **VoxelMap** | 1 | `CompressibleGLBufferedImage` (mipmap generation) |

**Total bypass calls outside Blaze3D and Vulkanic layer:** 480

These calls are safe and correct on the OpenGL backend today. The problem is that when a Vulkan backend exists, these bypass calls will not be routed through it — they will either break or require each subsystem to be updated individually to speak Vulkan.

The Blaze3D `getDevice()` path was designed precisely so subsystems do not need to know which backend is running.

### 2.3 · The Vulkanic Layer's Role

`VulkanicAPI` and `GraphicsBackend` sit between Blaze3D's OpenGL backend classes and the LWJGL bindings. They serve two purposes:

1. **Isolation**: Keep raw `org.lwjgl.opengl.*` imports confined to `OpenGLBackend.java`.
2. **Runtime dispatch**: Allow a future Vulkan backend to be swapped in by changing which `GraphicsBackend` implementation is registered.

`VulkanicAPI` is **not** the user-facing high-level API for game/mod rendering. That role belongs to the Blaze3D `GpuDevice / CommandEncoder / RenderPass / RenderPipeline` stack. VulkanicAPI is the **low-level bridge** that Blaze3D's OpenGL implementation calls internally.

---

## 3 · What Was Actually Accomplished

### Phase 1: Blaze3D / GlStateManager ✅ Complete

All methods in `GlStateManager`, `GlCommandEncoder`, `GlDevice`, `GlDebugLabel`, `DirectStateAccess`, `VertexArrayCache`, and related Blaze3D classes were migrated from direct LWJGL calls to VulkanicAPI calls. The `ArchitecturalBoundaryTest` enforces no regressions.

### Phase 2: Mod Integration ✅ Complete

Sodium, Iris, and Distant Horizons no longer import `org.lwjgl.opengl.*`. All their rendering calls flow through VulkanicAPI. The architectural boundary test passes.

### Phase 2.5: CommandContext Migration ✅ Complete

All 283 previously-deprecated VulkanicAPI methods have been replaced with `CommandContext`-aware signatures. `GraphicsBackend` now has 212 rendering methods that all accept a `CommandContext` parameter, following the design intent where a Vulkan backend would pass a `VkCommandBuffer` handle through the `getHandle()` method. Zero `@Deprecated` annotations remain in VulkanicAPI, GraphicsBackend, or OpenGLBackend.

---

## 4 · What Actually Needs to Happen Next

The old roadmap described building new abstractions. The correct roadmap is about **routing existing code through existing abstractions**.

### Phase 3: Route Bypass Callers Through GpuDevice (Current Priority)

**Goal:** Eliminate the 480 bypass `getImmediateContext()` calls in Iris, DH, Sodium, and VoxelMap by making them use the `GpuDevice → CommandEncoder → RenderPass` path that Blaze3D already provides.

**Why this is the right next step:**
- OpenGL backend stays 100% functional at every sub-step (GlCommandEncoder just calls GlStateManager as before).
- Each subsystem migration is isolated — one subsystem at a time, one feature category at a time.
- When done, a Vulkan backend only requires implementing `GpuDevice / CommandEncoder / RenderPass` interfaces — no game or mod code changes needed.
- The architectural boundary test already validates the import rules; adding a "no raw getImmediateContext() in mod code" test rule would allow automated regression prevention.

**Ordering (least risky first):**

#### 3a · VoxelMap (1 call) — Trivial

`CompressibleGLBufferedImage` calls `generateTextureMipmap` and `bindTexture2D` directly. This is one call site and can be replaced with a Blaze3D `CommandEncoder` call or moved to be triggered from the proper Blaze3D texture update path.

#### 3b · Sodium (66 calls) — Straightforward

Sodium already partially uses the Blaze3D path:
- `ShaderChunkRenderer` already calls `RenderSystem.getDevice().createCommandEncoder().applyPipelineState()`.
- `SodiumGameOptionPages` already uses `RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures()`.

The remaining 66 bypass calls are in:
- `GlBuffer` / `GlVertexArray` — buffer management that maps to `GpuBuffer` / `CommandEncoder.mapBuffer()`.
- `GlProgram` / `GlShader` / uniform wrappers — shader compilation and uniform upload; map to `GpuDevice.precompilePipeline()` and `RenderPass.setUniform()`.
- `GlFence` — GPU synchronization; maps to `CommandEncoder.createFence()` and `GpuFence`.
- `GLRenderDevice` — Sodium's own `RenderDevice` abstraction; its `CommandList` and `DrawCommandList` wrappers could be re-implemented using the Blaze3D `CommandEncoder`/`RenderPass` APIs.

#### 3c · Iris Shaders (161 calls) — Medium

Iris already uses `RenderSystem.getDevice().createCommandEncoder().createRenderPass()` in several places (`CompositeRenderer`, `FinalPassRenderer`, `ShadowCompositeRenderer`, `HorizonRenderer`, `CenterDepthSampler`). The remaining 161 bypass calls are concentrated in:
- `IrisRenderSystem` (95 calls) — Iris's own GL abstraction layer; most of these are thin wrappers that delegate to `VulkanicAPI` and should instead delegate to a `CommandEncoder` obtained from `RenderSystem.getDevice()`.
- `IrisLodRenderProgram` / `IrisGenericRenderProgram` (45 calls) — DH-Iris compatibility shaders; these mirror the DH `ShaderProgram` pattern and should use `GpuDevice.precompilePipeline()`.
- `GLDebug` / `FinalPassRenderer` / uniform classes — scattered uses of compute, image bindings, and state manipulation.

The Iris `CustomPass` interface already extends `RenderPassInterface` which hooks into `GlRenderPass`. This is the integration point for Iris's custom rendering — extending it rather than bypassing it is the correct direction.

#### 3d · Distant Horizons (252 calls) — Largest effort

DH maintains its own parallel rendering stack (`GLBuffer`, `DhFramebuffer`, `DhColorTexture`, `ShaderProgram`, vertex attribute objects, multiple renderers). This stack maps cleanly onto the Blaze3D model:

| DH class | Blaze3D equivalent |
|---|---|
| `GLBuffer` | `GpuBuffer` (via `GpuDevice.createBuffer()`) |
| `DhFramebuffer` / `DhColorTexture` / `DHDepthTexture` | `GpuTexture` / `GpuTextureView` (via `GpuDevice.createTexture()`) |
| `ShaderProgram` / `Shader` | `RenderPipeline` (via `GpuDevice.precompilePipeline()`) |
| `AbstractVertexAttribute` | `VertexFormat` / `VertexArrayCache` (inside GlCommandEncoder) |
| `LodRenderer` / `FogRenderer` / `SSAORenderer` etc. | `FramePass` in a `FrameGraphBuilder` |
| `GLState` (state save/restore) | Managed by `GlCommandEncoder.applyPipelineState()` per `RenderPipeline` |
| `GenericObjectRenderer` | `RenderPass.drawMultipleIndexed()` |

The recommended approach for DH is to have its renderers participate in the Blaze3D `FrameGraphBuilder` — registering their work as `FramePass` entries alongside Minecraft's sky/main/particle passes. `LodRenderer.renderLod()` would become a `FramePass` lambda that obtains a `CommandEncoder` from `RenderSystem.getDevice()` and creates a `RenderPass` for each draw.

---

## 5 · What a Vulkan Backend Looks Like from Here

Once all bypass callers are routed through `GpuDevice → CommandEncoder → RenderPass`, the Vulkan backend becomes a clean, isolated implementation task:

```
VkDevice implements GpuDevice
    → createCommandEncoder() returns VkCommandEncoder
    → createTexture() allocates VkImage + VkImageView
    → createBuffer() allocates VkBuffer

VkCommandEncoder implements CommandEncoder
    → createRenderPass() begins a VkRenderPass via vkCmdBeginRenderPass
    → clearColorTexture() maps to vkCmdClearColorImage
    → writeToBuffer() maps to vkCmdUpdateBuffer or staging buffer copy

VkRenderPass implements RenderPass
    → setPipeline() binds VkPipeline via vkCmdBindPipeline
    → bindSampler() updates a descriptor set
    → setVertexBuffer() calls vkCmdBindVertexBuffers
    → setIndexBuffer() calls vkCmdBindIndexBuffer
    → drawIndexed() calls vkCmdDrawIndexed
    → close() calls vkCmdEndRenderPass
```

The `FrameGraphBuilder.execute()` loop already enforces resource acquisition and release ordering — this maps naturally to Vulkan's render pass attachments and image layout transitions. The `FramePass` dependency graph maps to Vulkan's subpass dependencies or explicit pipeline barriers between render passes.

`VulkanicAPI.initialize(BackendType.VULKAN)` would register a `VkDevice` instead of `GlDevice`, and every call that flows through `RenderSystem.getDevice()` would then go to Vulkan natively. The Blaze3D interface layer is already Vulkan-compatible by design.

---

## 6 · Architecture Diagram (Current State)

```
┌────────────────────────────────────────────────────────────────┐
│  Core Minecraft Rendering (LevelRenderer, GuiRenderer, etc.)  │
│  FrameGraphBuilder + FramePass                                  │
│                    ↓ RenderSystem.getDevice()                   │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  GpuDevice / CommandEncoder / RenderPass / RenderPipeline│  │
│  │  (Blaze3D - net.blaze3d.systems / net.blaze3d.pipeline)  │  │
│  │  Implemented by: GlDevice / GlCommandEncoder / GlRenderPass│ │
│  │               ↓ via GlStateManager / VulkanicAPI calls   │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                ↓ (correct path)                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  VulkanicAPI / GraphicsBackend / CommandContext          │  │
│  │  (net.vulkanic)                                          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                ↓                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │  OpenGLBackend  (net.vulkanic.backends.opengl)           │  │
│  │  ONLY file allowed to import org.lwjgl.opengl.*          │  │
│  └──────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ⚠️  Bypass paths still exist (480 calls total):               │
│     Iris (161) ──┐                                              │
│     DH    (252) ─┼──→ VulkanicAPI directly (skips GpuDevice)  │
│     Sodium (66) ─┘                                              │
│     VoxelMap (1)                                                │
└────────────────────────────────────────────────────────────────┘
```

---

## 7 · Key Metrics

| Metric | Value |
|---|---|
| `@Deprecated` annotations remaining in Vulkanic layer | **0** |
| GraphicsBackend rendering methods with `CommandContext` param | **212** |
| Total VulkanicAPI static methods | **570** |
| `getImmediateContext()` calls — Blaze3D backend (intentional) | **146** |
| `getImmediateContext()` calls — Iris, DH, Sodium, VoxelMap (bypass, to eliminate) | **480** |
| Files using `RenderSystem.getDevice()` (correct path) | **62** |
| Files using `createRenderPass()` (fully modern path) | **23** |
| Architectural boundary test | ✅ Passing |

---

## 8 · Rules That Must Be Maintained at Every Step

1. **OpenGL backend stays 100% functional.** Every sub-step must be testable and releasable. Never break the OpenGL path to make progress on abstraction.

2. **`org.lwjgl.opengl.*` imports stay confined to `net/vulkanic/backends/opengl/`.** The `ArchitecturalBoundaryTest` enforces this and must continue to pass.

3. **`net.vulkanic.backends.*` imports stay confined to `net/vulkanic/`.** Same test.

4. **Game and mod code should use `RenderSystem.getDevice()` to get a `CommandEncoder`, not call `VulkanicAPI.getImmediateContext()` directly.** Each bypass call eliminated is a direct step toward Vulkan compatibility.

5. **`VulkanicAPI` and `GraphicsBackend` are the low-level backend bridge, not the game-facing rendering API.** The game-facing API is the Blaze3D `GpuDevice / CommandEncoder / RenderPass / RenderPipeline` stack.

---

## 9 · Non-Goals (Clarified)

- **Designing new pipeline/descriptor/renderpass abstractions** — Blaze3D already has these. We adopt and route through them; we do not duplicate them.
- **Changing `VulkanicAPI` method signatures** — The 570 static methods are stable. Future changes are additions, not removals.
- **Removing OpenGL support** — OpenGL remains a supported, first-class backend indefinitely.
- **Performance optimization** — Correctness and backend-independence come first; optimization is Phase 4.
- **Immediate Vulkan implementation** — A Vulkan backend cannot be added safely until the bypass path calls are eliminated.

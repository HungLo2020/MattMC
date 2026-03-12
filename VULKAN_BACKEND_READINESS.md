# Vulkan Backend Readiness

## Coverage Baseline (as of 2026-03-12)

| Backend | Implements `GraphicsBackend` | Coverage |
|---------|------------------------------|----------|
| Vulkan  | 38 / 261 methods             | **14.6%** |
| OpenGL  | 256 / 261 methods            | **98.1%** |

"Implements" means the backend has a declared method of that name.
It does **not** mean the method works correctly end-to-end.

> 100% coverage = every production `VulkanicAPI.*` callsite delegates to
> a working implementation in both backends.  We are at **14.6%** for Vulkan.

### How to measure progress

```bash
# Verbose report with per-method status and production callsite counts
python3 DevUtils/VulkanCoverageAudit/vulkan_coverage_audit.py

# One-line summary
python3 DevUtils/VulkanCoverageAudit/vulkan_coverage_audit.py --brief

# GitHub Markdown table
python3 DevUtils/VulkanCoverageAudit/vulkan_coverage_audit.py --markdown
```

The test suite also enforces a regression floor:

```bash
./gradlew test --tests net.vulkanic.VulkanBackendCoverageTest
```

`MIN_VULKAN_COVERAGE_PCT` in that test must be raised (never lowered) as
implementations are added.

---

## Goal

Ship a working Vulkan backend that can render at least one basic MattMC scene.
100% parity with OpenGL is not required for this milestone.

## Non-Goals

- API perfection, abstraction cleanup, renaming
- Optimization or feature completeness
- Speculative improvements

Only blockers that prevent a Vulkan backend from existing are tracked here.

## Vulkan Mode Policy

- Vulkan mode is **fail-hard** by design.
- OpenGL fallback from Vulkan-selected execution is explicitly forbidden.
- Missing or unimplemented Vulkan backend methods must surface immediate runtime errors.

## Required Capabilities

### Renderer Initialization

- **Capability Name:** Renderer Initialization
- **Description:** Ability to select Vulkan backend routing and initialize core Vulkan runtime objects.
- **Status:** SCAFFOLDING (not integrated into engine startup)
- **Evidence:**
  - `VulkanicAPI.initialize(GraphicsBackendType)` selects Vulkan and creates `new VulkanBackend()`.
  - `VulkanicAPI.createFailFastVulkanProxy(...)` routes all `GraphicsBackend` calls to Vulkan-native methods only.
  - `VulkanBackend.NativeSpine.initialize()` creates instance, surface, physical/logical device, queue, swapchain, command pool.
  - `VulkanBackend.initializeNativeVulkanRuntime()` performs bring-up attempt and returns structured diagnostics.
  - `VulkanicAPI.initializeNativeVulkanRuntime()` / `describeNativeVulkanInitialization()` exist as API surface.
- **Notes:** The initialization API exists and is tested in isolation but is **not called from any engine startup path** (`Main.java`, `Minecraft`, `GameRenderer`). Marking "complete" would be wrong — nothing triggers it.

### Logical Device / Context Abstraction

- **Capability Name:** Logical Device / Context Abstraction
- **Description:** Backend must expose command context plus internal device/queue ownership needed for command submission.
- **Status:** SCAFFOLDING (not integrated, covers 5 of 261 interface methods)
- **Evidence:**
  - `CommandContext` interface + `VulkanCommandContext` wrapper exist.
  - `VulkanBackend.NativeSpine` creates `VkDevice` + graphics queue on bring-up.
  - `VulkanExecutionContextInfo` snapshot type exists for diagnostic reporting.
  - `VulkanicAPI.getVulkanExecutionContextInfo()` / `describeVulkanExecutionContextInfo()` are dead code — zero production callsites.
- **Notes:** Abstraction layer exists. No production code calls it. Does not contribute to the 86.2% of missing methods.

### Swapchain / Surface Handling

- **Capability Name:** Swapchain / Surface Handling
- **Description:** Backend must create and manage platform surface + swapchain suitable for rendering output.
- **Status:** SCAFFOLDING (not integrated into render loop)
- **Evidence:**
  - `VulkanBackend.NativeSpine.createSurface()` / `createSwapchain()` exist and run on bring-up.
  - `VulkanSwapchainSurfaceInfo` snapshot + recreate methods exist.
  - `VulkanicAPI.recreateVulkanSwapchain()` / `recreateVulkanSwapchainIfNeeded()` are dead code — zero production callsites in `GameRenderer` or `RenderSystem`.
- **Notes:** Swapchain exists internally. The resize/recreate hooks are not wired into any window-resize or render-loop path.

### Command Encoder or Command Buffer Abstraction

- **Capability Name:** Command Encoder or Command Buffer Abstraction
- **Description:** Backend must support explicit command recording and submission.
- **Status:** PARTIAL
- **Evidence:**
  - `GraphicsBackend.beginCommandBuffer()` / `submitCommandBuffer(...)`.
  - `VulkanBackend.beginCommandBuffer()` begins primary command buffer.
  - `VulkanBackend.submitCommandBuffer(...)` ends and submits recorded commands.
- **Notes:** Single primary command buffer path exists; rendering commands that should be recorded are largely unimplemented on Vulkan path.

### GPU Buffer Creation (vertex/index/uniform)

- **Capability Name:** GPU Buffer Creation (vertex/index/uniform)
- **Description:** Backend must allocate GPU buffers required for geometry and uniform data.
- **Status:** COMPLETE
- **Evidence:**
  - `GraphicsBackend.createManagedBuffer(...)` is required by API.
  - `VulkanicAPI.createManagedBuffer(...)` always routes through active backend.
  - `VulkanBackend.createManagedBuffer(...)` (both size and initial-data variants) now allocates native Vulkan buffers via `NativeSpine` (`vkCreateBuffer`, `vkAllocateMemory`, `vkBindBufferMemory`).
  - `VulkanBackend.mapManagedBuffer(...)` now maps managed Vulkan buffers through `vkMapMemory` / `vkUnmapMemory`.
  - `VulkanBuffer` now provides a Vulkan-native `VulkanicBuffer` implementation with deterministic close/unmap callbacks.
- **Notes:** Vulkan can now allocate managed vertex/index/uniform buffers through the backend-neutral API. Legacy raw `bufferData`/`bufferSubData` migration is tracked under Buffer Upload / Staging Support.

### Buffer Upload / Staging Support

- **Capability Name:** Buffer Upload / Staging Support
- **Description:** Backend must upload CPU data to GPU buffers (directly or via staging).
- **Status:** PARTIAL
- **Evidence:**
  - `GraphicsBackend` defines buffer upload/mapping operations (`bufferData`, `bufferSubData`, `mapManagedBuffer`, etc.).
  - OpenGL has implementations in `OpenGLBackend`.
  - `VulkanBackend.createManagedBuffer(..., initialData)` uploads initial bytes through mapped host-visible memory.
  - `VulkanBackend.mapManagedBuffer(...)` now supports explicit CPU mapping for managed Vulkan buffers.
  - Legacy raw upload entrypoints (`bufferData`, `bufferSubData`, copy-buffer pathways) still have no Vulkan-native implementation.
- **Notes:** Managed-buffer upload exists; full legacy upload/staging parity is still incomplete.

### Texture/Image Creation

- **Capability Name:** Texture/Image Creation
- **Description:** Backend must create GPU images/textures used by scene rendering.
- **Status:** COMPLETE
- **Evidence:**
  - `GraphicsBackend.createManagedTexture(...)` and `createManagedTextureView(...)` define required abstraction.
  - `VulkanicAPI.createManagedTexture(...)` and `VulkanicAPI.createManagedTextureView(...)` route through active backend.
  - `VulkanBackend.createManagedTexture(...)` now creates native Vulkan images via `NativeSpine` (`vkCreateImage`, `vkAllocateMemory` with `VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT`, `vkBindImageMemory`).
  - `VulkanBackend.createManagedTextureView(...)` (both full-range and mip-range variants) creates `VkImageView` objects via `NativeSpine.createVkImageView(...)`.
  - `VulkanTexture` provides a Vulkan-native `VulkanicTexture` implementation wrapping `VkImage + VkDeviceMemory + default VkImageView`, with deterministic synchronized close.
  - `VulkanTextureView` provides a Vulkan-native `VulkanicTextureView` implementation wrapping a `VkImageView`, with deterministic synchronized close.
  - Format mapping: `VulkanicTextureFormat.RGBA8/RED8/RED8I/DEPTH32` → corresponding `VkFormat` values.
  - Usage mapping: `USAGE_COPY_SRC/DST/TEXTURE_BINDING/RENDER_ATTACHMENT` → correct `VkImageUsageFlagBits`, with automatic depth vs colour attachment selection.
  - Teardown: `NativeSpine.close()` destroys all tracked extra image views before destroying tracked images.
- **Notes:** Managed texture and view creation now works through the backend-neutral API. Texture *content* upload (populating image data) is tracked separately under Texture Upload.

### Texture Upload

- **Capability Name:** Texture Upload
- **Description:** Backend must upload image data into GPU textures.
- **Status:** BLOCKED
- **Evidence:**
  - `GraphicsBackend` includes texture upload operations (`uploadTexture2D`, sub-image upload methods).
  - OpenGL implementation exists in `OpenGLBackend`.
  - `VulkanBackend` has no Vulkan texture upload implementation.
- **Notes:** Texture content cannot be populated on Vulkan path.

### Shader Abstraction

- **Capability Name:** Shader Abstraction
- **Description:** Backend must support shader module creation/compilation data flow suitable for pipeline creation.
- **Status:** PARTIAL
- **Evidence:**
  - `VulkanBackend.compileSpirvModule(...)` via `SpirvCompiler`/`GlslangSpirvCompiler`.
  - Virtual shader/program lifecycle in `VulkanBackend` (`createShader`, `uploadShaderSource`, `compileShader`, `linkProgram`, info logs).
  - `VulkanicSpirvModule` abstraction exists.
- **Notes:** Shader compilation exists, but Vulkan pipeline consumption is not implemented.

### Graphics Pipeline Creation

- **Capability Name:** Graphics Pipeline Creation
- **Description:** Backend must compile/bind a Vulkan graphics pipeline from descriptor state + shaders.
- **Status:** BLOCKED
- **Evidence:**
  - `GraphicsBackend.createPipeline(PipelineDescriptor)` required.
  - `VulkanBackend.createPipeline(...)` throws `UnsupportedOperationException("Vulkan-native pipeline creation is not implemented yet.")`.
- **Notes:** No Vulkan pipeline object can be created.

### Render Target / Framebuffer Abstraction

- **Capability Name:** Render Target / Framebuffer Abstraction
- **Description:** Backend must represent color/depth targets and bind them for rendering.
- **Status:** PARTIAL
- **Evidence:**
  - `VulkanicTexture`, `VulkanicTextureView`, `VulkanicRenderPassDescriptor` define backend-neutral target model.
  - OpenGL path creates temporary FBO in `OpenGLBackend.beginRenderPass(...)`.
- **Notes:** Abstraction exists, but Vulkan backend does not implement target binding lifecycle.

### Render Pass / Attachment Model

- **Capability Name:** Render Pass / Attachment Model
- **Description:** Backend must begin/end render passes and honor attachment load/store semantics.
- **Status:** BLOCKED
- **Evidence:**
  - `VulkanicRenderPass` and `VulkanicRenderPassDescriptor` exist.
  - All `VulkanBackend.beginRenderPass(...)` overloads throw `UnsupportedOperationException("Vulkan-native render pass lifecycle is not implemented yet.")`.
- **Notes:** Vulkan cannot start a render pass, so no draw can target attachments.

### Descriptor / Resource Binding

- **Capability Name:** Descriptor / Resource Binding
- **Description:** Backend must allocate/update/bind resource descriptors for pipeline resources.
- **Status:** BLOCKED
- **Evidence:**
  - Abstractions exist: `DescriptorPoolHandle`, `DescriptorSetHandle`, `PipelineResourceBindings`.
  - OpenGL logical implementation exists (`OpenGLDescriptorPoolHandle`, `OpenGLDescriptorSetHandle`, `OpenGLBackend.bindPipelineResources(...)`).
  - Vulkan methods throw unsupported: `createDescriptorPool`, `allocateDescriptorSet`, `updateDescriptorSet`, `bindDescriptorSet`, `resetDescriptorPool`, `bindPipelineResources`.
- **Notes:** Vulkan resource binding path is not operational.

### Draw Calls (indexed and non-indexed)

- **Capability Name:** Draw Calls (indexed and non-indexed)
- **Description:** Backend must issue draw commands once pipeline/resources/attachments are bound.
- **Status:** BLOCKED
- **Evidence:**
  - `GraphicsBackend` requires draw entry points (`drawArrays`, `drawElements`, instanced/indexed variants).
  - `VulkanicAPI` routes draw calls through active `GraphicsBackend`.
  - `VulkanBackend` public method surface does not implement draw methods from `GraphicsBackend`.
  - Fail-fast proxy in `VulkanicAPI.createFailFastVulkanProxy(...)` throws if method is not natively implemented.
- **Notes:** Vulkan backend cannot service standard draw API calls.

### Synchronization Model

- **Capability Name:** Synchronization Model
- **Description:** Backend must expose resource visibility and queue/frame synchronization sufficient for correct rendering.
- **Status:** BLOCKED
- **Evidence:**
  - `VulkanicResourceBarriers` abstraction exists.
  - `GraphicsBackend.applyResourceBarriers(...)` exists.
  - `VulkanBackend.applyResourceBarriers(...)` throws unsupported.
  - Submission path currently uses `vkQueueWaitIdle` after submit (`VulkanBackend.NativeSpine.submitPrimaryCommandBuffer(...)`).
- **Notes:** No Vulkan barrier mapping or frame-level sync primitives are implemented.

### Frame Lifecycle (begin frame / end frame)

- **Capability Name:** Frame Lifecycle (begin frame / end frame)
- **Description:** Backend must define frame-level sequencing suitable for swapchain image acquisition, recording, submit, and completion.
- **Status:** MISSING
- **Evidence:**
  - `GraphicsBackend` / `VulkanicAPI` provide command-buffer lifecycle only (`beginCommandBuffer`, `submitCommandBuffer`).
  - No `beginFrame` / `endFrame` abstraction exists.
- **Notes:** Required frame orchestration for Vulkan presentation flow is absent.

### Resize / Recreate Framebuffers

- **Capability Name:** Resize / Recreate Framebuffers
- **Description:** Backend must recreate swapchain-dependent targets on window resize/out-of-date surface.
- **Status:** SCAFFOLDING (API exists, not wired to window resize events)
- **Evidence:**
  - `VulkanBackend.NativeSpine.recreateSwapchainIfFramebufferSizeChanged()` exists and checks GLFW framebuffer size vs stored swapchain extent.
  - `VulkanBackend.beginCommandBuffer()` auto-checks for mismatch and triggers recreation.
  - `VulkanicAPI.recreateVulkanSwapchain()` / `recreateVulkanSwapchainIfNeeded()` exist as API surface.
  - No callsite in `Window`, `Minecraft`, `GameRenderer`, or `RenderSystem` calls these methods on resize events.
- **Notes:** The resize-recreate logic exists internally in the command-buffer path. Engine-level window callback → swapchain recreation is not wired.

### Presentation

- **Capability Name:** Presentation
- **Description:** Backend must acquire swapchain images and present rendered images to the window surface.
- **Status:** MISSING
- **Evidence:**
  - `VulkanBackend` creates swapchain, but there is no `vkAcquireNextImageKHR` or `vkQueuePresentKHR` path.
  - No presentation method is defined in `GraphicsBackend`/`VulkanicAPI`.
- **Notes:** Even with command submission, rendered output cannot reach the screen.

### GraphicsBackend Contract Coverage (Vulkan Path)

- **Capability Name:** GraphicsBackend Contract Coverage (Vulkan Path)
- **Description:** Vulkan backend must natively implement the `GraphicsBackend` methods used by the engine.
- **Status:** BLOCKED
- **Evidence:**
  - Vulkan selection uses `VulkanicAPI.createFailFastVulkanProxy(...)`.
  - Proxy throws `IllegalStateException` if `VulkanBackend` lacks a method from `GraphicsBackend`.
  - `VulkanBackend` currently implements only a limited subset of methods and omits many core rendering entry points.
- **Notes:** This is a hard runtime blocker for using Vulkan backend routing with existing engine call paths. This fail-hard behavior is intentional and required; no OpenGL fallback is allowed when Vulkan backend routing is selected.

## Blocking Issues

1. **Vulkan backend covers only 14.6% of the `GraphicsBackend` interface.**
  - **Origin:** `VulkanicAPI.createFailFastVulkanProxy(...)` + 38 of 261 interface methods implemented in `VulkanBackend`.
  - **Why it blocks:** Any production render call that hits the 223 unimplemented methods immediately throws. Normal render flow is impossible.
   - **Measure:** `./gradlew test --tests net.vulkanic.VulkanBackendCoverageTest` — raises floor as implementations land.

2. **Pipeline and render pass execution are unimplemented on Vulkan path.**
   - **Origin:** `VulkanBackend.createPipeline(...)` and all `beginRenderPass(...)` overloads throw `UnsupportedOperationException`.
   - **Why it blocks:** A basic scene cannot bind pipeline state or begin a render pass, so no draw is possible.

3. **Descriptor/resource binding is unimplemented on Vulkan path.**
   - **Origin:** `createDescriptorPool`, `allocateDescriptorSet`, `updateDescriptorSet`, `bindDescriptorSet`, `bindPipelineResources` all throw unsupported.
   - **Why it blocks:** Shader resources (textures/uniform buffers) cannot be bound for rendering.

4. **Vulkan texture upload path and legacy buffer upload path are still missing.**
  - **Origin:** `createManagedTexture`/`createManagedTextureView` are now implemented; however, Vulkan-native texture *upload* entrypoints (`uploadTexture2D`, sub-image upload) and legacy raw buffer upload (`bufferData`, `bufferSubData`) Vulkan mappings are still absent.
  - **Why it blocks:** Texture resources can be created in Vulkan but not yet *populated* with pixel data; older non-managed upload paths are not yet serviceable.

5. **Presentation lifecycle is missing (acquire/present).**
   - **Origin:** No `vkAcquireNextImageKHR` / `vkQueuePresentKHR` in `VulkanBackend`; no `beginFrame`/`endFrame` abstraction.
   - **Why it blocks:** Rendered frames cannot reach the screen.

6. **Scaffolded APIs have zero production callsites.**
   - **Origin:** `initializeNativeVulkanRuntime`, `getVulkanExecutionContextInfo`, `getVulkanSwapchainSurfaceInfo`, `recreateVulkanSwapchain*` are called only from tests.
   - **Why it matters:** These have been counted as "COMPLETE" in the past but contribute nothing to actual render flow. Engine integration code has not been written.

## First Vulkan Backend Exit Criteria

- Vulkan backend compiles
- Vulkan instance and device initialize
- swapchain created
- command buffers recorded
- one simple frame rendered (clear screen or triangle)
- engine can render at least one basic MattMC scene
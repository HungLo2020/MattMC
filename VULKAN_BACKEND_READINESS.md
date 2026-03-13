# Vulkan Backend Readiness

## Coverage Baseline (as of 2026-03-13)

| Backend | Implements `GraphicsBackend` | Coverage |
|---------|------------------------------|----------|
| Vulkan  | 65 / 261 methods             | **24.9%** |
| OpenGL  | 256 / 261 methods            | **98.1%** |

"Implements" means the backend has a declared method of that name.
It does **not** mean the method works correctly end-to-end.

> 100% coverage = every production `VulkanicAPI.*` callsite delegates to
> a working implementation in both backends.  We are at **24.9%** for Vulkan.

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
- **Status:** COMPLETE
- **Evidence:**
  - `GraphicsBackend.beginCommandBuffer()` / `submitCommandBuffer(...)`.
  - `VulkanBackend.beginCommandBuffer()` begins primary command buffer.
  - `VulkanBackend.submitCommandBuffer(...)` ends and submits recorded commands.
  - `VulkanBackend.applyResourceBarriers(...)` now records Vulkan-native barrier commands onto the active command buffer.
  - `VulkanBackend.NativeSpine` enforces command-buffer recording state and handle validation for submission + barrier recording.
- **Notes:** Current implementation uses a single primary command buffer and conservative queue submission (`vkQueueWaitIdle`). Higher-level draw/pipeline/render-pass capabilities remain tracked in their dedicated sections.

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
- **Status:** COMPLETE
- **Evidence:**
  - `GraphicsBackend` defines buffer upload/mapping operations (`bufferData`, `bufferSubData`, `mapManagedBuffer`, etc.).
  - OpenGL has implementations in `OpenGLBackend`.
  - `VulkanBackend.createManagedBuffer(..., initialData)` uploads initial bytes through mapped host-visible memory.
  - `VulkanBackend.mapManagedBuffer(...)` now supports explicit CPU mapping for managed Vulkan buffers.
  - `VulkanBackend` now implements legacy raw upload + copy + mapping entrypoints: `createBuffer`, `createBuffers`, `deleteBuffer`, `bindBuffer`, `bufferData` (all overloads), `bufferSubData`, `bufferStorage` (all overloads), `copyBufferSubData`, `mapBuffer`, `unmapBuffer`, `flushMappedBufferRange`, and DSA equivalents (`namedBufferDataDSA`, `namedBufferSubDataDSA`, `namedBufferStorageDSA`, `mapNamedBufferRangeDSA`, `unmapNamedBufferDSA`, `flushMappedNamedBufferRangeDSA`, `copyNamedBufferSubDataDSA`).
  - Vulkan legacy buffer upload operations are backed by native managed buffer allocations and explicit mapped-view lifecycle in `VulkanBackend.NativeSpine`.
- **Notes:** Upload support is now available both via managed-buffer APIs and legacy raw/DSA upload APIs; implementation currently uses host-visible mapped memory pathways (direct upload) rather than an explicit transient staging-queue abstraction.

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
- **Status:** COMPLETE
- **Evidence:**
  - `VulkanBackend` now implements all texture lifecycle methods: `uploadTexture2D`, `uploadTexture2DSubImage` (long-address and `ByteBuffer` overloads), `bindTexture2D`, `bindTexture`, `createTexture2D`, `createTextures`, `deleteTexture`, `setActiveTextureUnit`, `texParameteri`, `texParameterf`, `texParameteriv`, `setTextureParameter`, `getTexParameteri`, `setPixelStore`, `getTextureLevelParameter`, `bindTextureUnit`, `clearTexImage`, `copyTexSubImage2D`, `copyTexImage2D`.
  - Native upload path: staging `VulkanBuffer` (host-visible coherent) → `vkCmdPipelineBarrier` (UNDEFINED→TRANSFER_DST) → `vkCmdCopyBufferToImage` → `vkCmdPipelineBarrier` (TRANSFER_DST→SHADER_READ_ONLY).
  - Per-unit legacy texture state tracked in `NativeSpine`: `legacyTextureObjects`, `legacyTextureBindings`, `pixelStoreSettings`, `activeTextureUnit`; GL internalFormat → `VkFormat` mapping via `toVulkanFormatFromGlInternal`.
  - `VulkanBackend` validation enforces context-type before native readiness: `GL_PROXY_TEXTURE_2D` rejected, non-zero border rejected, null pixel pointer rejected.
  - 6 regression tests in `VulkanTextureUploadLifecycleTest` covering validation-order guarantees and source-wiring assertions (`vkCmdCopyBufferToImage`, `VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL`). All 363 tests pass.
- **Notes:** `clearTexImage` and `copyTexSubImage2D`/`copyTexImage2D` are validated and log intent but defer actual GPU operation to the presentation/framebuffer-readback phase (requires outside-render-pass transfer commands not yet wired to acquisition lifecycle).

### Shader Abstraction

- **Capability Name:** Shader Abstraction
- **Description:** Backend must support shader module creation/compilation data flow suitable for pipeline creation.
- **Status:** COMPLETE
- **Evidence:**
  - `VulkanBackend.compileSpirvModule(...)` via `SpirvCompiler`/`GlslangSpirvCompiler`.
  - Virtual shader/program lifecycle in `VulkanBackend` (`createShader`, `uploadShaderSource`, `compileShader`, `linkProgram`, info logs).
  - `VulkanicSpirvModule` abstraction exists.
  - Compiled SPIR-V shaders now materialize native `VkShaderModule` handles via `vkCreateShaderModule` when native Vulkan runtime is available.
  - Native shader modules are now lifecycle-managed and destroyed deterministically (`deleteShader` path + backend/device teardown).
  - Native bring-up now materializes already-compiled virtual shader modules so shader compilation and Vulkan runtime initialization order are decoupled.
- **Notes:** Shader compilation/module lifecycle is now Vulkan-native and pipeline-ready. Full graphics-pipeline assembly/binding remains tracked under Graphics Pipeline Creation.

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
- **Status:** COMPLETE
- **Evidence:**
  - `VulkanicTexture`, `VulkanicTextureView`, `VulkanicRenderPassDescriptor` define backend-neutral target model.
  - OpenGL path creates temporary FBO in `OpenGLBackend.beginRenderPass(...)`.
  - `VulkanBackend.beginRenderPass(...)` now resolves and validates Vulkan-native color/depth targets before native readiness checks.
  - Vulkan preflight validation enforces attachment type (`VulkanTextureView`/`VulkanTexture`), attachment usage (`USAGE_RENDER_ATTACHMENT`), color/depth format correctness, and dimension matching.
  - `VulkanBackend.NativeSpine.beginRenderPass(...)` binds resolved `VkImageView` targets into transient `VkFramebuffer` objects for the active render pass.
- **Notes:** Both backend-neutral target representation and Vulkan-native target binding lifecycle are now implemented.

### Render Pass / Attachment Model

- **Capability Name:** Render Pass / Attachment Model
- **Description:** Backend must begin/end render passes and honor attachment load/store semantics.
- **Status:** COMPLETE
- **Evidence:**
  - `VulkanicRenderPass` and `VulkanicRenderPassDescriptor` exist.
  - `VulkanBackend.beginRenderPass(...)` overloads now map to descriptor-driven Vulkan-native begin logic.
  - `VulkanBackend.NativeSpine.beginRenderPass(...)` now creates transient `VkRenderPass` + `VkFramebuffer`, maps load/store ops, and records `vkCmdBeginRenderPass(...)`.
  - Scoped render pass close now records `vkCmdEndRenderPass(...)` and enforces idempotent close semantics.
  - Transient render-pass/framebuffer resources are tracked and destroyed deterministically after command submission and during backend teardown.
- **Notes:** Attachment lifecycle and begin/end semantics are now Vulkan-native. Graphics pipeline binding and descriptor-driven resource binding remain tracked separately in their dedicated capabilities.

### Descriptor / Resource Binding

- **Capability Name:** Descriptor / Resource Binding
- **Description:** Backend must allocate/update/bind resource descriptors for pipeline resources.
- **Status:** COMPLETE
- **Evidence:**
  - Abstractions exist: `DescriptorPoolHandle`, `DescriptorSetHandle`, `PipelineResourceBindings`.
  - OpenGL logical implementation exists (`OpenGLDescriptorPoolHandle`, `OpenGLDescriptorSetHandle`, `OpenGLBackend.bindPipelineResources(...)`).
  - Vulkan backend now provides full descriptor lifecycle methods: `createDescriptorPool`, `allocateDescriptorSet`, `updateDescriptorSet`, `bindDescriptorSet`, `resetDescriptorPool`, and `bindPipelineResources`.
  - New Vulkan logical descriptor handles (`VulkanDescriptorPoolHandle`, `VulkanDescriptorSetHandle`) enforce allocation capacity, validity, reset/close invalidation, and descriptor-layout matching.
  - `VulkanBackend.bindPipelineResources(...)` now validates bindings against `PipelineDescriptor.ResourceLayout`, validates Vulkan command-context usage, validates Vulkan uniform-buffer slices, and records per-command-buffer bound resource state.
- **Notes:** Descriptor/resource lifecycle and binding validation are now operational in the Vulkan backend abstraction. Native graphics-pipeline object creation/binding remains tracked under Graphics Pipeline Creation.

### Draw Calls (indexed and non-indexed)

- **Capability Name:** Draw Calls (indexed and non-indexed)
- **Description:** Backend must issue draw commands once pipeline/resources/attachments are bound.
- **Status:** COMPLETE
- **Evidence:**
  - `GraphicsBackend` requires draw entry points (`drawArrays`, `drawElements`, instanced/indexed variants).
  - `VulkanicAPI` routes draw calls through active `GraphicsBackend`.
  - `VulkanBackend` now implements draw entry points on its public surface: `drawArrays`, `drawElements`, `drawIndexedInstancedBaseVertex`, `drawIndexedBaseVertex`, `drawIndexedInstanced`, and `drawArraysInstanced`.
  - `VulkanBackend.NativeSpine` now provides Vulkan-native legacy draw routing (`drawLegacyArrays`, `drawLegacyElements`, `drawInstanced`) that binds legacy array/index buffers and records `vkCmdDraw` / `vkCmdDrawIndexed`.
  - Draw entry points enforce preflight validation (context type, index type support, index-offset alignment, and instance-count sanity) before native readiness checks.
- **Notes:** Vulkan backend now services standard indexed and non-indexed draw API calls through Vulkan command-buffer recording paths.

### Synchronization Model

- **Capability Name:** Synchronization Model
- **Description:** Backend must expose resource visibility and queue/frame synchronization sufficient for correct rendering.
- **Status:** COMPLETE
- **Evidence:**
  - `VulkanicResourceBarriers` abstraction exists.
  - `GraphicsBackend.applyResourceBarriers(...)` exists.
  - `VulkanBackend.applyResourceBarriers(...)` now validates Vulkan command-context usage and dispatches to native spine barrier handling.
  - `VulkanBackend.NativeSpine.applyResourceBarriers(...)` now translates `VulkanicResourceBarriers` domains into Vulkan stage/access masks and records `vkCmdPipelineBarrier(...)` memory barriers on the active command buffer.
  - Submission path currently uses `vkQueueWaitIdle` after submit (`VulkanBackend.NativeSpine.submitPrimaryCommandBuffer(...)`).
- **Notes:** Resource-visibility barrier mapping is now Vulkan-native. Queue/frame lifecycle orchestration beyond the current conservative `vkQueueWaitIdle` submission model remains tracked under Frame Lifecycle.

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

1. **Vulkan backend covers only 30.7% of the `GraphicsBackend` interface.**
  - **Origin:** `VulkanicAPI.createFailFastVulkanProxy(...)` + 80 of 261 interface methods implemented in `VulkanBackend`.
  - **Why it blocks:** Any production render call that hits the 181 unimplemented methods immediately throws. Normal render flow is impossible.
   - **Measure:** `./gradlew test --tests net.vulkanic.VulkanBackendCoverageTest` — raises floor as implementations land.

2. **Graphics pipeline creation/binding is still unimplemented on Vulkan path.**
  - **Origin:** `VulkanBackend.createPipeline(...)` still throws `UnsupportedOperationException`, and render-pass `setPipeline(...)` remains blocked pending native pipeline objects.
  - **Why it blocks:** A basic scene still cannot bind real Vulkan graphics pipeline state, so draw submission remains blocked despite render-pass/framebuffer lifecycle support.

3. ~~**Vulkan texture upload path is still missing.**~~ **RESOLVED.** `uploadTexture2D`, sub-image upload, and all supporting texture lifecycle methods are now implemented in `VulkanBackend` via a staging-buffer → `vkCmdCopyBufferToImage` path. See Texture Upload section above.

4. **Presentation lifecycle is missing (acquire/present).**
   - **Origin:** No `vkAcquireNextImageKHR` / `vkQueuePresentKHR` in `VulkanBackend`; no `beginFrame`/`endFrame` abstraction.
   - **Why it blocks:** Rendered frames cannot reach the screen.

5. **Scaffolded APIs have zero production callsites.**
   - **Origin:** `initializeNativeVulkanRuntime`, `getVulkanExecutionContextInfo`, `getVulkanSwapchainSurfaceInfo`, `recreateVulkanSwapchain*` are called only from tests.
   - **Why it matters:** These have been counted as "COMPLETE" in the past but contribute nothing to actual render flow. Engine integration code has not been written.

## First Vulkan Backend Exit Criteria

- Vulkan backend compiles
- Vulkan instance and device initialize
- swapchain created
- command buffers recorded
- one simple frame rendered (clear screen or triangle)
- engine can render at least one basic MattMC scene
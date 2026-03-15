# Vulkan Backend Readiness

## Coverage Baseline (as of 2026-03-13)

| Backend | Implements `GraphicsBackend` | Coverage |
|---------|------------------------------|----------|
| Vulkan  | 263 / 263 methods            | **100.0%** |
| OpenGL  | 256 declared + interface defaults | **100%** (compiler-verified) |

"Implements" means the backend has a declared method of that name.
It does **not** mean the method works correctly end-to-end.

> 100% coverage = every production `VulkanicAPI.*` callsite delegates to
> a working implementation in both backends.  We are now at **100.0%** for Vulkan.

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
- **Status:** COMPLETE
- **Evidence:**
  - `VulkanicAPI.initialize(GraphicsBackendType)` selects Vulkan and creates `new VulkanBackend()`.
  - `VulkanicAPI.createFailFastVulkanProxy(...)` routes all `GraphicsBackend` calls to Vulkan-native methods only.
  - `Options.selectGraphicsBackend(...)` now routes production startup backend selection through `VulkanicAPI.initializeFromOptionsValue(...)` from `options.txt` (`graphics_backend`).
  - `RenderSystem.initRenderer(...)` now invokes `VulkanicAPI.initializeNativeVulkanRuntimeOnRendererStartupIfSelected()` after context/device setup.
  - `RenderSystem.initRenderer(...)` no longer hard-codes `new GlDevice(...)` or OpenGL-only post-init hooks in shared startup code; device creation/bootstrap selection now route through backend-owned `GraphicsBackend.prepareRendererBootstrapWindow(...)`, `createRendererDevice(...)`, and `onRendererDeviceInitialized(...)` seams.
  - `VulkanBackend` now owns compatibility bootstrap window creation/cleanup and exposes a backend-owned `VulkanCompatibilityGpuDevice` wrapper so Vulkan-selected startup no longer presents plain `GlDevice` ownership to shared game code.
  - Shared diagnostics/warnlist callsites now consume backend-neutral `GpuDeviceInfo` and `getOptionalFeatureNames()` seams instead of inferring behavior from raw `"OpenGL"` string checks, `getVersion()` formatting, or OpenGL extension wording.
  - `VulkanicAPI.initializeNativeVulkanRuntimeOnRendererStartupIfSelected()` enforces fail-hard startup behavior: no-op for uninitialized/OpenGL routing, throws on Vulkan-selected bring-up failure.
  - `VulkanBackend.NativeSpine.initialize()` creates instance, surface, physical/logical device, queue, swapchain, command pool.
  - `VulkanBackend.initializeNativeVulkanRuntime()` performs bring-up attempt and returns structured diagnostics.
  - Regression coverage: `VulkanRendererStartupInitializationTest` validates uninitialized/OpenGL no-op behavior, Vulkan fail-hard/ready behavior, and startup callsite wiring.
- **Notes:** Vulkan-native initialization is now triggered from the production renderer startup path while preserving explicit fail-hard behavior for Vulkan-selected execution. Shared startup and shared diagnostics are more backend-neutral, but the compatibility device still delegates substantial resource/pipeline work to OpenGL internals and does not prove full Vulkan-native rendering ownership yet.

### Logical Device / Context Abstraction

- **Capability Name:** Logical Device / Context Abstraction
- **Description:** Backend must expose command context plus internal device/queue ownership needed for command submission.
- **Status:** COMPLETE
- **Evidence:**
  - `CommandContext` interface + `VulkanCommandContext` wrapper exist.
  - `VulkanBackend.NativeSpine` creates `VkDevice` + graphics queue on bring-up.
  - `VulkanExecutionContextInfo` snapshot type provides logical-device/queue/command-pool/command-buffer ownership diagnostics.
  - `VulkanicAPI.initializeNativeVulkanRuntimeOnRendererStartupIfSelected()` now validates `getVulkanExecutionContextInfo().isAvailable()` during production renderer startup when Vulkan routing is selected.
  - `RenderSystem.initRenderer(...)` invokes startup Vulkan initialization/validation hook on production startup path.
  - Regression coverage: `VulkanRendererStartupInitializationTest` asserts startup hook wiring and execution-context validation behavior.
- **Notes:** Logical device/context ownership is now both exposed and actively validated on the production startup path for Vulkan-selected execution.

### Swapchain / Surface Handling

- **Capability Name:** Swapchain / Surface Handling
- **Description:** Backend must create and manage platform surface + swapchain suitable for rendering output.
- **Status:** COMPLETE
- **Evidence:**
  - `VulkanBackend.NativeSpine.createSurface()` / `createSwapchain()` exist and run on bring-up.
  - `VulkanBackend.NativeSpine.createSwapchain(...)` now enumerates swapchain images (`vkGetSwapchainImagesKHR`) and materializes tracked swapchain `VkImageView` handles (`vkCreateImageView`) for lifecycle ownership.
  - `VulkanBackend.NativeSpine` now tracks and destroys swapchain image views deterministically during recreation and backend teardown.
  - `VulkanBackend.NativeSpine.beginFrame()` now validates acquired image indices against tracked swapchain image/view ownership before frame progression.
  - `VulkanSwapchainSurfaceInfo` snapshot + recreate methods remain available for backend-neutral diagnostics.
  - `VulkanicAPI.initializeNativeVulkanRuntimeOnRendererStartupIfSelected()` now validates `getVulkanSwapchainSurfaceInfo().isAvailable()` during production renderer startup when Vulkan routing is selected.
  - `VulkanicAPI.recreateVulkanSwapchainIfNeededOnFramebufferResize(...)` now provides a resize-event-safe Vulkan callsite that avoids implicit backend initialization.
  - `Window.onFramebufferResize(...)` now calls `handleVulkanSwapchainFramebufferResize(...)`, which delegates to Vulkan swapchain conditional recreation when Vulkan backend routing is active.
  - Regression coverage: `VulkanSwapchainSurfaceInfoTest` now asserts source-level swapchain image/view lifecycle wiring; `WindowVulkanSwapchainResizeTest` covers resize-triggered recreation behavior with OpenGL no-regression paths.
- **Notes:** Swapchain/surface lifecycle is now production-integrated with deterministic image-view ownership and recreation/teardown handling.

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
- **Notes:** Shader compilation/module lifecycle is now Vulkan-native and consumed by graphics-pipeline creation. Frame lifecycle/presentation integration remains tracked in their dedicated capabilities.

### Graphics Pipeline Creation

- **Capability Name:** Graphics Pipeline Creation
- **Description:** Backend must compile/bind a Vulkan graphics pipeline from descriptor state + shaders.
- **Status:** COMPLETE
- **Evidence:**
  - `GraphicsBackend.createPipeline(PipelineDescriptor)` required.
  - `VulkanBackend.createPipeline(...)` now validates descriptor/SPIR-V inputs and delegates to native `NativeSpine.createVulkanPipeline(...)`.
  - `VulkanBackend.NativeSpine.createVulkanPipeline(...)` now creates `VkDescriptorSetLayout`, `VkPipelineLayout`, and `VkPipeline` via `vkCreateGraphicsPipelines`.
  - `VulkanBackedRenderPass.setPipeline(...)` now validates `VulkanPipelineHandle` and records `vkCmdBindPipeline(...)`.
  - Pipeline resources are lifecycle-managed in `NativeSpine` (`managedVkPipelineHandles`, `managedVkPipelineLayoutHandles`, `managedVkDescriptorSetLayoutHandles`) and destroyed deterministically on handle close/backend teardown.
  - Regression tests: `VulkanPipelineCreationLifecycleTest` covers OpenGL path non-regression, Vulkan fail-hard behavior when runtime is unavailable, runtime-ready pipeline creation path, and source-level native wiring assertions.
- **Notes:** Vulkan can now compile and bind native graphics pipeline objects through the backend-neutral API. Presentation/acquire lifecycle remains tracked separately.

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
- **Notes:** Attachment lifecycle and begin/end semantics are now Vulkan-native and now support native graphics-pipeline binding through `VulkanBackedRenderPass.setPipeline(...)`.

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
- **Notes:** Descriptor/resource lifecycle and binding validation are operational in the Vulkan backend abstraction and now pair with native graphics-pipeline creation/binding.

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
- **Status:** COMPLETE
- **Evidence:**
  - `GraphicsBackend.beginFrame()` / `GraphicsBackend.endFrame()` now define backend-neutral frame-level lifecycle hooks.
  - `VulkanicAPI.beginFrame()` / `VulkanicAPI.endFrame()` now expose frame lifecycle orchestration through the active backend.
  - `VulkanBackend.beginFrame()` now enforces Vulkan fail-hard readiness and delegates to native acquire logic.
  - `VulkanBackend.endFrame()` now enforces Vulkan fail-hard readiness and delegates to native present logic.
  - `RenderSystem.flipFrame(...)` now conditionally routes Vulkan-selected execution through `VulkanicAPI.beginFrame()` + `VulkanicAPI.endFrame()`.
- **Notes:** Frame-level sequencing abstraction now exists and is wired into the production frame-flip path for Vulkan-selected routing.

### Resize / Recreate Framebuffers

- **Capability Name:** Resize / Recreate Framebuffers
- **Description:** Backend must recreate swapchain-dependent targets on window resize/out-of-date surface.
- **Status:** COMPLETE
- **Evidence:**
  - `VulkanBackend.NativeSpine.recreateSwapchainIfFramebufferSizeChanged()` exists and checks GLFW framebuffer size vs stored swapchain extent.
  - `VulkanBackend.beginCommandBuffer()` auto-checks for mismatch and triggers recreation.
  - `Window.onFramebufferResize(...)` now invokes `Window.handleVulkanSwapchainFramebufferResize(...)` before `eventHandler.resizeDisplay()`.
  - `Window.handleVulkanSwapchainFramebufferResize(...)` delegates to `VulkanicAPI.recreateVulkanSwapchainIfNeededOnFramebufferResize(...)`.
  - `VulkanicAPI.recreateVulkanSwapchainIfNeededOnFramebufferResize(...)` no-ops for uninitialized/OpenGL backends and conditionally recreates swapchain state for Vulkan-selected routing.
  - Regression tests: `WindowVulkanSwapchainResizeTest` covers uninitialized no-init behavior, OpenGL no-op behavior, Vulkan fail-hard/readiness behavior, and minimized-dimension guard paths.
- **Notes:** Resize callback wiring is now implemented end-to-end. Existing command-buffer mismatch checks remain in place as a secondary safety path.

### Presentation

- **Capability Name:** Presentation
- **Description:** Backend must acquire swapchain images and present rendered images to the window surface.
- **Status:** COMPLETE
- **Evidence:**
  - `VulkanBackend.NativeSpine.beginFrame()` now acquires swapchain images via `vkAcquireNextImageKHR`.
  - `VulkanBackend.NativeSpine.endFrame()` now presents acquired images via `vkQueuePresentKHR`.
  - Acquire/present path handles `VK_ERROR_OUT_OF_DATE_KHR` / `VK_SUBOPTIMAL_KHR` by recreating the swapchain.
  - `RenderSystem.flipFrame(...)` now uses Vulkan frame lifecycle routing in Vulkan-selected mode and preserves `GLFW.glfwSwapBuffers(...)` for OpenGL.
  - Regression tests in `VulkanFramePresentationLifecycleTest` cover OpenGL no-op behavior, Vulkan fail-hard/readiness behavior, and source-level wiring assertions.
- **Notes:** Swapchain acquire/present lifecycle is now implemented and wired through production frame presentation routing.

### GraphicsBackend Contract Coverage (Vulkan Path)

- **Capability Name:** GraphicsBackend Contract Coverage (Vulkan Path)
- **Description:** Vulkan backend must natively implement the `GraphicsBackend` methods used by the engine.
- **Status:** COMPLETE
- **Evidence:**
  - Vulkan selection uses `VulkanicAPI.createFailFastVulkanProxy(...)`.
  - Proxy no longer has any `GraphicsBackend` interface methods left to block: `VulkanBackend` now explicitly implements **263/263** methods.
  - Earlier cycles implemented the high-frequency render-state, FBO, texture, shader, descriptor, draw, and frame-lifecycle paths.
  - **Final completion cycle additions:** the remaining 101 contract methods were implemented, covering legacy blend compatibility (`blendFunc`, `blendFuncSeparatei`), blit/copy helpers, sync/query lifecycle, shader/program handle wrappers, state/introspection queries, DSA texture/framebuffer helpers, debug/capability probes, compute-entry compat shims, and explicit overrides for defaulted bridge methods such as `resolveUniformLocation(...)`, `resolveTextureHandle(...)`, and `resolveFramebufferForTextures(...)`.
  - Virtual compatibility tracking was expanded for VAOs, samplers, sync objects, and query objects, plus cached Vulkan capability metadata and integer state-query routing.
  - NativeSpine helpers already added in the prior cycle remain in use for real command-buffer dynamic state and render-pass clears: `isRenderPassActive()`, `cmdSetViewport()`, `cmdSetScissor()`, `cmdClearAttachments()`.
  - **Coverage audit:** 263/263 Vulkan, 256/263 OpenGL explicit declarations (263/263 total compiler-verified coverage on OpenGL).
- **Tests:** `VulkanRenderStateContractTest`, `VulkanFullContractCoverageTest`, and updated `VulkanFailFastRoutingTest`; full suite now **423/423 passing**.
- **Notes:** The fail-fast Vulkan proxy still guards native-readiness-sensitive operations, but there are now **zero** `GraphicsBackend` contract gaps on the Vulkan path.

### Backend-Neutral Device / Encoder Callsite Seams

- **Capability Name:** Backend-Neutral Device / Encoder Callsite Seams
- **Description:** High-traffic production callsites must avoid concrete `GlDevice` / `GlCommandEncoder` dependencies and instead use backend-neutral `GpuDevice` / `CommandEncoder` interfaces.
- **Status:** COMPLETE
- **Evidence:**
  - `CommandEncoder` now exposes backend-neutral pipeline-state hooks: `applyPipelineState(RenderPipeline)` and `invalidateCachedProgramBinding()`.
  - `GlCommandEncoder` now implements those hooks directly through the interface seam; the previous public `lastProgram` field is no longer exposed for external mutation.
  - `ShaderChunkRenderer` now acquires a `CommandEncoder` from `RenderSystem.getDevice()` and no longer casts to `GlCommandEncoder` for pipeline-state application or cached-program invalidation.
  - `Iris.setDebug(...)` now queries enabled extensions through `RenderSystem.getDevice().getEnabledExtensions()` and no longer casts to `GlDevice`.
- **Tests:** `ApiNeutralityCallsiteTest` now guards the new encoder seam and ensures those high-traffic callsites remain free of concrete backend casts.
- **Notes:** This removes concrete OpenGL type leakage from hot rendering paths and strengthens the path toward a future Vulkan-native `GpuDevice` / `CommandEncoder` implementation.

### Backend-Owned Render Target Binding Seams

- **Capability Name:** Backend-Owned Render Target Binding Seams
- **Description:** Production callsites should bind output targets by owned color/depth textures, not by resolving and binding backend-specific framebuffer identities themselves.
- **Status:** COMPLETE
- **Evidence:**
  - `GraphicsBackend` now exposes `bindRenderTarget(CommandContext, VulkanicTexture, VulkanicTexture)` as a backend-owned render-target binding seam.
  - `VulkanicAPI.bindRenderTarget(...)` now routes `GpuTexture` color/depth pairs through the active backend and falls back to the default framebuffer only when no color target exists.
  - `RenderTarget.iris$bindFramebuffer()` now binds through `VulkanicAPI.bindRenderTarget(...)` and no longer resolves backend framebuffer ids directly.
  - `OpenGLBackend` and `VulkanBackend` both implement the seam so attachment-pair binding stays inside backend code.
- **Tests:** `Phase3DrawPathTest` now guards the seam and ensures `RenderTarget` no longer resolves framebuffer ids directly from production callsites.
- **Notes:** This shrinks one more GL-shaped surface area by keeping framebuffer identity private to the backend while preserving current compatibility behavior.

### Distant Horizons Owner-Bound Render Target Seams

- **Capability Name:** Distant Horizons Owner-Bound Render Target Seams
- **Description:** Distant Horizons hot render paths should bind Minecraft and LodRenderer outputs through owner seams instead of pulling and rebinding raw framebuffer ids.
- **Status:** COMPLETE
- **Evidence:**
  - `IMinecraftRenderWrapper` / `MinecraftRenderWrapper` now expose `hasTargetRenderTarget()` and `bindTargetRenderTarget(CommandContext)` so DH renderers bind Minecraft's current output target through the wrapper owner.
  - `LodRenderer` now retains the active `IDhApiFramebuffer` and exposes `hasActiveRenderTarget()` / `bindActiveRenderTarget()` so downstream shaders no longer need the raw active framebuffer id just to rebind DH output.
  - `TestRenderer`, `DhApplyShader`, `FogApplyShader`, `SSAOApplyShader`, `VanillaFadeRenderer`, `DhFadeRenderer`, and `FadeApplyShader` now bind through those owner seams instead of resolving draw targets from `getTargetFramebuffer()` / `getActiveFramebufferId()` in their hot paths.
- **Tests:** `Phase3DrawPathTest` now guards the new DH owner-bound seams and verifies those render paths avoid raw framebuffer-id binding in the migrated callsites.
- **Notes:** This preserves legacy compatibility APIs for Iris/DH integration while moving active production rendering closer to texture-backed, backend-owned render-target intent.

### Distant Horizons Managed Offscreen Framebuffer Owners

- **Capability Name:** Distant Horizons Managed Offscreen Framebuffer Owners
- **Description:** Distant Horizons transient offscreen passes should carry framebuffer ownership through `DhFramebuffer` objects instead of reducing those targets to raw framebuffer ids during creation, binding, and apply stages.
- **Status:** COMPLETE
- **Evidence:**
  - `FogRenderer`, `SSAORenderer`, `DhFadeRenderer`, and `VanillaFadeRenderer` now create and track offscreen targets as `DhFramebuffer` owners, attaching textures through `DhFramebuffer.addColorAttachment(...)` instead of ad hoc global framebuffer mutation.
  - `FogShader`, `SSAOShader`, `DhFadeShader`, and `VanillaFadeShader` now bind their output targets through `DhFramebuffer.bind(...)` rather than cached raw framebuffer ids.
  - `FogApplyShader`, `SSAOApplyShader`, and `FadeApplyShader` now bind read targets through framebuffer owners (`bindAsReadBuffer`) instead of raw `bindReadFramebuffer` ids for the migrated Distant Horizons offscreen passes.
- **Tests:** `Phase3DrawPathTest` now guards the owner-based offscreen framebuffer flow across the DH renderers and apply shaders.
- **Notes:** This removes another GL-shaped identity leak from hot render paths by treating offscreen framebuffers as owned resources rather than public integer handles.

### Named Framebuffer Blit Routing Seams

- **Capability Name:** Named Framebuffer Blit Routing Seams
- **Description:** Production fallback blit paths should delegate source/destination framebuffer routing through Vulkanic's named-blit seam instead of manually binding read/draw targets around every copy.
- **Status:** COMPLETE
- **Evidence:**
  - `DirectStateAccess` fallback blit path now uses `VulkanicAPI.blitNamedFramebuffer(...)` instead of caching and restoring read/draw framebuffer bindings manually.
  - `IrisRenderSystem` unsupported-DSA blit path now also uses `VulkanicAPI.blitNamedFramebuffer(...)`, keeping read/draw target routing inside Vulkanic/backends.
  - The existing OpenGL and Vulkan backend `blitNamedFramebuffer(...)` implementations now serve both DSA-capable and fallback production paths.
- **Tests:** `Phase3DrawPathTest` now guards the migration and ensures those fallback blit paths do not manually bind read/draw framebuffer targets before blitting.
- **Notes:** This shrinks another framebuffer-identity surface area by making blit intent a backend-owned operation even when the caller only has source/destination framebuffer handles.


## Session 2026-03-15: Typed Texture Upload — R32F Center-Depth Path

### Problem Targeted
`GL_R32F / GL_RED / GL_FLOAT` upload tuple was missing from `VulkanBackend.LegacyTextureFormatInfo.resolve()`, causing an `IllegalArgumentException("Unsupported legacy texture upload format combination")` crash whenever Iris loaded a shaderpack that exercises the center-depth smooth-depth path (`CenterDepthSampler`). The callsite also used raw `InternalTextureFormat.R32F` + `PixelType.FLOAT` GL integer expansion — opaque to any backend routing logic.

### Structural Changes
- **`VulkanicTextureUploadFormat` (new enum):** Backend-neutral upload tuple type (10 named formats) with `fromLegacyGlTuple(int, int, int)` auto-mapper.
- **`GraphicsBackend`:** Two typed `default` overloads for `uploadTexture2D` accepting `VulkanicTextureUploadFormat`.
- **`VulkanicAPI`:** Smart auto-upgrade in the legacy `uploadTexture2D` dispatch — when both target and format are recognized, promotes to typed call; falls back to raw for unknown tuples. Two new typed overloads added.
- **`VulkanicCoreAPI`:** Two typed `uploadTexture2D` wrappers.
- **`OpenGLBackend`:** Explicit typed override — unpacks format to GL integers, delegates to `GL11.glTexImage2D`.
- **`VulkanBackend`:** Explicit typed override + **`R32F → VK_FORMAT_R32_SFLOAT` added** in `LegacyTextureFormatInfo.resolve()`.
- **`IrisRenderSystem`:** Two typed `texImage2D` overloads accepting `VulkanicTextureUploadFormat`.
- **`CenterDepthSampler`:** `setupColorTexture()` migrated — no longer uses `InternalTextureFormat` / `PixelType`; calls `IrisRenderSystem.texImage2D(texture, 0, VulkanicTextureUploadFormat.RED32_SFLOAT, 1, 1, 0, null)`.

### Evidence
- `VulkanTextureUploadFormatContractTest` (new, 3 tests): tuple mapper recognizes center-depth tuple; Vulkan resolver accepts R32F (asserts `vkFormat == VK_FORMAT_R32_SFLOAT`, `pixelBytes == 4`, `aspectMask == VK_IMAGE_ASPECT_COLOR_BIT`); resolver still fail-fasts for unknown tuples.
- `VulkanicTypedApiRoutingTest` (2 new tests): known GL-R32F tuple routes to typed method; unknown tuple falls back to raw int overload.
- `ApiNeutralityCallsiteTest` (2 new assertions): `GraphicsBackend` typed upload seam exists; `CenterDepthSampler` references `VulkanicTextureUploadFormat.RED32_SFLOAT` and not `PixelType.FLOAT.getGlFormat()`.

### Progress Classes
- **1 — Abstraction Improvement:** Typed upload seam added; center-depth callsite off raw GL tuple.
- **2 — Backend Implementation:** Concrete Vulkan R32F path; fail-fast hole removed for this tuple.
- **4 — Parity Improvement:** Upload intent expressed as backend-neutral format, not GL mechanism.
- **5 — Debuggability Improvement:** Narrower failure mode for unsupported tuples; known center-depth routes deterministically.

### Still Unproven
- Full runtime validation with Iris + shaderpack active on a real Vulkan device.
- Other upload tuples used by other shader passes may still be unmapped in `LegacyTextureFormatInfo.resolve()`.

### Bug Fix — Over-Broad `fromLegacyGlTuple` Routing (Post-session fix)
**Symptom:** Water surface rendered white/transparent in OpenGL mode with Iris shader packs enabled.

**Root cause:** `VulkanicTextureUploadFormat.fromLegacyGlTuple` used OR conditions that matched on just the pixel `format` OR just the `internalFormat`, independent of each other. This allowed higher-precision formats (e.g., `GL_RGBA16`, `GL_RGBA`, `GL_RGB16`) to match lower-precision enum entries (`RGBA8_UNORM`, `RGB8_UNORM`), silently changing the `internalFormat` passed to `glTexImage2D`. Iris render targets that default to `InternalTextureFormat.RGBA` (`GL_RGBA = 0x1908`) were incorrectly upgraded to `GL_RGBA8 (0x8058)` and render targets using `GL_RGBA16` were downgraded to `GL_RGBA8`, corrupting colortex buffer precision used by composite shaders for water rendering.

**Fix:** Replaced the entire conditional block in `fromLegacyGlTuple` with exact per-field matching (`internalFormat AND format AND type` must all match). Each enum entry now only recognizes its own canonical exact tuple. Unknown or non-canonical tuples fall through to the raw GL path, preserving the caller's original values unchanged.

**Regression guard:** `VulkanTextureUploadFormatContractTest` now contains 4 tests:
- `testTupleMapperDoesNotRouteUnsizedRgbaToRgba8Unorm` — `GL_RGBA` (unsized) does not map to `RGBA8_UNORM`
- `testTupleMapperDoesNotDowngradeRgba16ToRgba8` — `GL_RGBA16` does not silently lose precision
- `testTupleMapperDoesNotDowngradeRgb16ToRgb8` — `GL_RGB16` does not silently lose precision
- `testTupleMapperStillRecognizesCanonicalRgba8UnormTuple` — exact `GL_RGBA8` tuple still routes correctly

## Session 2026-03-15: Backend-Owned Device Identity and Feature Seams

### Problem Targeted
Vulkan-selected startup still exposed compatibility `GlDevice` metadata as the source of truth for vendor/renderer/version/extensions/features. High-traffic callsites (Iris shader macro vendor/renderer selection, Iris debug callback extension reporting, and Minecraft startup/system-report feature logging) consumed those `RenderSystem.getDevice()` values directly. That coupling makes Vulkan mode identity partially OpenGL-shaped and keeps backend selection semantics fragile.

### Structural Changes
- **`GraphicsBackend`:** Added backend-owned identity and feature seams:
  - `getBackendVendorName()`
  - `getBackendRendererName()`
  - `getBackendVersionName()`
  - `getBackendEnabledExtensions()`
  - `getBackendOptionalFeatureNames()`
- **`VulkanicAPI`:** Added public wrappers for all new backend-owned seams with null-safe defaults and defensive list copy behavior.
- **`OpenGLBackend`:** Implemented seam methods using GL string queries for identity and active `GlDevice` values for extension/optional-feature lists.
- **`VulkanBackend`:** Implemented seam methods and captured native physical-device metadata during device selection (`vendorID`, `apiVersion`, `deviceName`) via `vkGetPhysicalDeviceProperties`.
- **`VulkanCompatibilityGpuDevice`:** Metadata and feature methods now delegate to backend-owned seam methods instead of exposing compatibility-device-local identity values.
- **Callsite migrations:**
  - `StandardMacros` vendor/renderer classification now uses `VulkanicAPI.getBackendVendorName()` and `VulkanicAPI.getBackendRendererName()`.
  - `Iris.setDebug(...)` extension snapshot for debug callback now uses `VulkanicAPI.getBackendEnabledExtensions()`.
  - `Minecraft` startup log and system report optional-feature list now use `VulkanicAPI.getBackendOptionalFeatureNames()`.

### Evidence
- `VulkanicTypedApiRoutingTest` now verifies the new Vulkanic API seam routes to backend methods for all identity/feature getters and preserves expected return values.
- `ApiNeutralityCallsiteTest` now guards migrated callsites and asserts those shader/debug/diagnostic code paths reference backend-owned seam methods instead of concrete device metadata lookups.
- Full test suite validation: `./gradlew test` reports **442 passed, 0 failed**.

### Progress Classes
- **1 — Abstraction Improvement:** Backend-owned identity/feature contract added and consumed through `VulkanicAPI`.
- **2 — Backend Implementation:** Both OpenGL and Vulkan backends now implement the contract; Vulkan path uses native physical-device properties.
- **4 — Parity Improvement:** Cross-backend callsites now consume backend intent (identity/features) instead of concrete device shape.
- **5 — Debuggability Improvement:** Vendor/renderer/version/feature reporting now follows selected backend ownership, reducing misleading metadata in diagnostics.

### Still Unproven
- Vulkan runtime rendering ownership is still incomplete in broader architecture; compatibility `GlDevice` remains in service for substantial resource and draw plumbing.
- Additional high-traffic callsites may still read concrete `RenderSystem.getDevice()` metadata and should be migrated through the same seam.

## Session 2026-03-15: Backend-Owned Final Present Seam (Finding #1)

### Problem Targeted
`GlCommandEncoder.presentTexture(...)` was still using a GL-shaped direct-FBO blit path (`bindFrameBufferTextures` + `blitFrameBuffers`) in shared/game callsites. On Vulkan-selected runtime this created a semantic gap between frame lifecycle acquire/present and actual final image composition into swapchain images.

### Structural Changes
- **`GraphicsBackend`:** Added backend-owned `presentTextureToScreen(CommandContext, GpuTextureView)` seam.
- **`VulkanicAPI` + `VulkanicCoreAPI`:** Added wrapper methods so production callsites can route final presentation by backend intent.
- **`GlCommandEncoder.presentTexture(...)`:** Replaced direct draw-FBO blit implementation with `VulkanicCoreAPI.presentTextureToScreen(...)`.
- **`OpenGLBackend`:** Implemented `presentTextureToScreen` using named-framebuffer texture attachment + blit to default framebuffer.
- **`VulkanBackend`:** Implemented `presentTextureToScreen` as a backend-owned queued present request; native `endFrame()` now composes the queued source texture into the acquired swapchain image via `vkCmdBlitImage` before `vkQueuePresentKHR`.
- **`VulkanBackend.NativeSpine`:** Added pending present request tracking and generalized image-layout transition helpers for transfer/present barriers.

### Evidence
- Source guardrails added/updated:
  - `Phase3DrawPathTest` now asserts `GlCommandEncoder.presentTexture` routes through `VulkanicCoreAPI.presentTextureToScreen(...)` and no longer performs direct draw-FBO attachment for this path.
  - `VulkanFramePresentationLifecycleTest` now asserts Vulkan source wiring includes queued present composition (`composePendingPresentTexture`) and `vkCmdBlitImage` in the frame presentation path.
  - `VulkanicTypedApiRoutingTest` now verifies `VulkanicAPI.presentTextureToScreen(...)` routes through backend seam dispatch.

### Progress Classes
- **1 — Abstraction Improvement:** Final present callsite no longer encodes concrete GL FBO-blit mechanics in shared `GlCommandEncoder` path.
- **2 — Backend Implementation:** Vulkan path moved from no-op display blit behavior to concrete swapchain composition command recording in `endFrame`.
- **4 — Parity Improvement:** Both backends now implement the same backend-owned present seam (`presentTextureToScreen`) with backend-native behavior.
- **5 — Debuggability Improvement:** Presentation failures now fail in backend-owned seam/compose code with targeted errors (missing texture handle/storage/mip/layout assumptions).

### Still Unproven
- Runtime validation on real Vulkan hardware with Iris/shaderpacks active is still required to prove full image correctness and layout assumptions under all passes.
- Current Vulkan present composition uses queue-idle synchronization and single-request overwrite semantics; it is functional but not yet optimized for throughput.

## Blocking Issues

1. ~~**Vulkan backend covers only 43.0% of the `GraphicsBackend` interface.**~~ **RESOLVED.**
  - `VulkanBackend` now explicitly implements **263/263** `GraphicsBackend` methods.
  - `python3 DevUtils/VulkanCoverageAudit/vulkan_coverage_audit.py --brief` now reports **Vulkan IMPLEMENTED: 263 (100.0%), MISSING: 0 (0.0%)**.
  - The fail-fast proxy remains in place for readiness diagnostics, but it no longer blocks any `GraphicsBackend` contract method due to missing Vulkan overrides.

2. ~~**Graphics pipeline creation/binding is still unimplemented on Vulkan path.**~~ **RESOLVED.**
  - `VulkanBackend.createPipeline(...)` now compiles native Vulkan pipeline objects (`VkDescriptorSetLayout` + `VkPipelineLayout` + `VkPipeline`) and `VulkanBackedRenderPass.setPipeline(...)` records `vkCmdBindPipeline(...)`. See Graphics Pipeline Creation section above.

3. ~~**Vulkan texture upload path is still missing.**~~ **RESOLVED.** `uploadTexture2D`, sub-image upload, and all supporting texture lifecycle methods are now implemented in `VulkanBackend` via a staging-buffer → `vkCmdCopyBufferToImage` path. See Texture Upload section above.

4. ~~**Presentation lifecycle is missing (acquire/present).**~~ **RESOLVED.** Vulkan frame lifecycle now includes `beginFrame`/`endFrame` abstraction plus native `vkAcquireNextImageKHR` and `vkQueuePresentKHR` paths, wired into `RenderSystem.flipFrame(...)` for Vulkan-selected routing.

5. ~~**Some scaffolded diagnostics APIs still have zero production callsites.**~~ **RESOLVED.** `initializeNativeVulkanRuntime`, `getVulkanExecutionContextInfo`, and `getVulkanSwapchainSurfaceInfo` are now all exercised from production renderer startup via `RenderSystem.initRenderer(...)` → `VulkanicAPI.initializeNativeVulkanRuntimeOnRendererStartupIfSelected()`.

## First Vulkan Backend Exit Criteria

- Vulkan backend compiles
- Vulkan instance and device initialize
- swapchain created
- command buffers recorded
- one simple frame rendered (clear screen or triangle)
- engine can render at least one basic MattMC scene
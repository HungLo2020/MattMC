# Vulkan Backend Readiness

## Goal

Determine whether Vulkanic currently provides the minimum abstractions necessary to implement a first working Vulkan backend capable of rendering a basic scene.

## Non-Goals

Out of scope for this document:

- API perfection
- abstraction cleanup
- renaming or stylistic changes
- optimization
- feature completeness
- parity with OpenGL backend
- speculative improvements

Only blockers that prevent a Vulkan backend from existing are listed.

## Required Capabilities

### Renderer Initialization

- **Capability Name:** Renderer Initialization
- **Description:** Ability to select Vulkan backend routing and initialize core Vulkan runtime objects.
- **Status:** PARTIAL
- **Evidence:**
  - `VulkanicAPI.initialize(GraphicsBackendType)` selects Vulkan and creates `new VulkanBackend()`.
  - `VulkanicAPI.createFailFastVulkanProxy(...)` routes all `GraphicsBackend` calls to Vulkan-native methods only.
  - `VulkanBackend.NativeSpine.initialize()` creates instance, surface, physical/logical device, queue, swapchain, command pool.
- **Notes:** Initialization exists, but this does not imply render-capable Vulkan execution.

### Logical Device / Context Abstraction

- **Capability Name:** Logical Device / Context Abstraction
- **Description:** Backend must expose command context plus internal device/queue ownership needed for command submission.
- **Status:** PARTIAL
- **Evidence:**
  - `CommandContext` interface (`isImmediate()`, `getHandle()`, `getDebugName()`).
  - `VulkanCommandContext` wraps a Vulkan command buffer handle.
  - `VulkanBackend.NativeSpine.createLogicalDeviceAndQueue()` creates `VkDevice` + graphics queue.
- **Notes:** Device abstraction is internal-only; no complete Vulkan execution surface is exposed through implemented backend methods.

### Swapchain / Surface Handling

- **Capability Name:** Swapchain / Surface Handling
- **Description:** Backend must create and manage platform surface + swapchain suitable for rendering output.
- **Status:** PARTIAL
- **Evidence:**
  - `VulkanBackend.NativeSpine.createSurface()` uses `glfwCreateWindowSurface`.
  - `VulkanBackend.NativeSpine.createSwapchain()` uses `vkCreateSwapchainKHR` and selects format/present mode/extent.
- **Notes:** Creation exists, but acquire/present/recreate paths are missing.

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
- **Status:** BLOCKED
- **Evidence:**
  - `GraphicsBackend.createManagedBuffer(...)` is required by API.
  - `VulkanicAPI.createManagedBuffer(...)` always routes through active backend.
  - `VulkanBackend` provides no `createManagedBuffer(...)` implementation.
  - `VulkanicAPI.createFailFastVulkanProxy(...)` throws when a `GraphicsBackend` method is missing in `VulkanBackend`.
- **Notes:** Vulkan backend cannot create core rendering buffers.

### Buffer Upload / Staging Support

- **Capability Name:** Buffer Upload / Staging Support
- **Description:** Backend must upload CPU data to GPU buffers (directly or via staging).
- **Status:** BLOCKED
- **Evidence:**
  - `GraphicsBackend` defines buffer upload/mapping operations (`bufferData`, `bufferSubData`, `mapManagedBuffer`, etc.).
  - OpenGL has implementations in `OpenGLBackend`.
  - `VulkanBackend` does not implement equivalent Vulkan data upload/staging methods.
- **Notes:** Vertex/index/uniform data cannot be populated on Vulkan path.

### Texture/Image Creation

- **Capability Name:** Texture/Image Creation
- **Description:** Backend must create GPU images/textures used by scene rendering.
- **Status:** BLOCKED
- **Evidence:**
  - `GraphicsBackend.createManagedTexture(...)` and `createManagedTextureView(...)` define required abstraction.
  - OpenGL implementation exists (`OpenGLBackend`, `OpenGLTexture`, `OpenGLTextureView`).
  - `VulkanBackend` does not implement managed texture creation/view methods.
- **Notes:** Vulkan path cannot create renderable or sampleable textures through current abstraction.

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
- **Status:** MISSING
- **Evidence:**
  - No resize/recreate swapchain API in `GraphicsBackend` or `VulkanicAPI`.
  - `VulkanBackend` has swapchain creation but no recreate path.
- **Notes:** Vulkan backend cannot recover from resize/out-of-date swapchain events.

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
- **Notes:** This is a hard runtime blocker for using Vulkan backend routing with existing engine call paths.

## Blocking Issues

1. **Vulkan backend does not cover the required `GraphicsBackend` contract.**
   - **Origin:** `VulkanicAPI.createFailFastVulkanProxy(...)` + limited method surface in `VulkanBackend`.
   - **Why it blocks Vulkan implementation:** Any engine call into a missing backend method fails immediately, preventing normal render flow.

2. **Pipeline and render pass execution are unimplemented on Vulkan path.**
   - **Origin:** `VulkanBackend.createPipeline(...)` and all `VulkanBackend.beginRenderPass(...)` overloads throw `UnsupportedOperationException`.
   - **Why it blocks Vulkan implementation:** A basic scene cannot bind pipeline state or begin a render pass, so no draw is possible.

3. **Descriptor/resource binding is unimplemented on Vulkan path.**
   - **Origin:** `VulkanBackend.createDescriptorPool(...)`, `allocateDescriptorSet(...)`, `updateDescriptorSet(...)`, `bindDescriptorSet(...)`, `bindPipelineResources(...)` all throw unsupported.
   - **Why it blocks Vulkan implementation:** Shader resources (textures/uniform buffers) cannot be bound for rendering.

4. **Vulkan resource creation/upload path for buffers and textures is missing.**
   - **Origin:** `GraphicsBackend` requires managed buffer/texture creation and upload methods; `VulkanBackend` does not implement these runtime entry points.
   - **Why it blocks Vulkan implementation:** Geometry, uniform data, and textures cannot be created/populated on Vulkan path.

5. **Presentation lifecycle is missing (acquire/present/resize-recreate).**
   - **Origin:** No presentation or frame lifecycle API in `GraphicsBackend`/`VulkanicAPI`; `VulkanBackend` contains swapchain creation only.
   - **Why it blocks Vulkan implementation:** Rendered frames cannot be acquired/presented or recovered after resize, so on-screen Vulkan rendering cannot complete.

## First Vulkan Backend Exit Criteria

- Vulkan backend compiles
- Vulkan instance and device initialize
- swapchain created
- command buffers recorded
- one simple frame rendered (clear screen or triangle)
- engine can render at least one basic MattMC scene
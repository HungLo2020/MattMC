# Vulkan API Migration Guide

## Overview

This document tracks the migration from OpenGL-style API methods to a proper Vulkan-native API design. The goal is to create a rendering API that works naturally with Vulkan's explicit, low-overhead model while maintaining full OpenGL backward compatibility through translation layers.

## Philosophy

### The Problem with "OpenGL + Context"
Previously, we attempted to add `CommandContext` parameters to existing OpenGL-style methods. While this made methods context-aware, it did NOT create a Vulkan-compatible API because:

1. **OpenGL methods don't map to Vulkan operations** - Methods like `glBindTexture()` or `glUniform*()` have no direct Vulkan equivalents
2. **Performance overhead remains** - Translating OpenGL-style calls to Vulkan creates the same overhead we're trying to avoid
3. **Double migration required** - Game code would need to migrate twice: once to CommandContext, then again to proper Vulkan APIs

### The New Approach: Vulkan-Native API Design

Instead, we design the API **from Vulkan's perspective first**:

1. **Vulkan-native abstractions** - Pipeline State Objects, Descriptor Sets, Render Passes, Command Buffers
2. **OpenGL translates Vulkan calls** - The OpenGL backend converts these high-level Vulkan concepts into OpenGL operations
3. **Single migration** - Game code migrates once to the final API
4. **Future-proof** - When Vulkan backend is ready, it's a straightforward implementation

## Core Vulkan Concepts vs OpenGL

### State Management
- **OpenGL**: Mutable global state machine (bind-to-edit model)
- **Vulkan**: Immutable Pipeline State Objects (PSOs) created upfront
- **Our API**: Pipeline creation/binding abstractions that OpenGL translates to state changes

### Textures & Samplers
- **OpenGL**: Texture units, bind textures, set sampler parameters
- **Vulkan**: Descriptor Sets with combined image-samplers or separate images/samplers
- **Our API**: Descriptor set abstractions that OpenGL translates to texture binding

### Uniforms
- **OpenGL**: `glUniform*()` calls per-draw
- **Vulkan**: Uniform Buffers in Descriptor Sets or Push Constants
- **Our API**: Uniform buffer + descriptor set APIs that OpenGL translates to glUniform calls

### Drawing
- **OpenGL**: Immediate draw calls with current state
- **Vulkan**: Record draw commands into command buffers with explicit dependencies
- **Our API**: Command buffer recording that OpenGL executes immediately

### Render Targets
- **OpenGL**: Framebuffer Objects (FBOs) with attachments
- **Vulkan**: Render Passes with subpasses and explicit load/store operations
- **Our API**: Render pass abstractions that OpenGL translates to FBO binding

## Migration Process

### Step-by-Step Method Migration

For each deprecated OpenGL-style method:

#### 1. Research Phase
- Study how the operation works in Vulkan (vkspec, tutorials, real engines)
- Understand the OpenGL equivalent and its limitations
- Identify the conceptual gap between the two approaches

#### 2. API Design Phase
- Design Vulkan-native API method(s) from scratch
- Consider:
  - What objects need to be created? (PSO, descriptor set layout, etc.)
  - What state is immutable vs dynamic?
  - How does this fit into Vulkan's command buffer model?
  - What validation is needed?
- **Prioritize Vulkan's model** - Design for minimal overhead in Vulkan
- Create new method signatures in `GraphicsBackend` interface

#### 3. OpenGL Implementation Phase
- Implement in `OpenGLBackend`
- **Translate Vulkan concepts to OpenGL**:
  - PSO creation → State machine configuration
  - Descriptor sets → Texture binding + uniform setting
  - Render passes → FBO binding + glClear operations
  - Command buffers → Immediate execution
- Ensure behavior matches exactly

#### 4. Public API Phase
- Add public wrapper methods in `VulkanicAPI`
- Provide comprehensive documentation with examples
- Explain the Vulkan → OpenGL mapping

#### 5. Migration Phase
- Find all call sites of the deprecated method
- Replace with new API calls
- Verify behavior is identical (visual testing, performance profiling)
- **Test incrementally** - Each method migration should compile and run

#### 6. Cleanup Phase
- Once all call sites are migrated, remove the deprecated method
- Update this document's progress tracking

### Example Migration

**Old OpenGL-style method:**
```java
@Deprecated
void glUniform1i(int location, int value);
```

**New Vulkan-style API:**
```java
// Create a uniform buffer (maps to Vulkan's VkBuffer with VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT)
UniformBufferHandle createUniformBuffer(long size, BufferUsage usage);

// Update uniform buffer data (maps to mapped memory write or vkCmdUpdateBuffer)
void updateUniformBuffer(UniformBufferHandle buffer, long offset, ByteBuffer data);

// Bind uniform buffer to descriptor set binding (maps to vkCmdBindDescriptorSets)
void bindUniformBuffer(int binding, UniformBufferHandle buffer, long offset, long range);
```

**OpenGL Backend Translation:**
```java
// In OpenGLBackend:
@Override
public UniformBufferHandle createUniformBuffer(long size, BufferUsage usage) {
    int glBuffer = GL15.glGenBuffers();
    GL15.glBindBuffer(GL31.GL_UNIFORM_BUFFER, glBuffer);
    GL15.glBufferData(GL31.GL_UNIFORM_BUFFER, size, GL15.GL_DYNAMIC_DRAW);
    return new UniformBufferHandle(glBuffer);
}

@Override
public void bindUniformBuffer(int binding, UniformBufferHandle buffer, long offset, long range) {
    GL30.glBindBufferRange(GL31.GL_UNIFORM_BUFFER, binding, buffer.id, offset, range);
}
```

## Working Document - Migration Tracking

### Priority Categories

Methods are categorized by importance for initial Vulkan support:

1. **Critical Path** - Required for basic rendering (shaders, buffers, drawing)
2. **High Priority** - Commonly used, significant performance impact
3. **Medium Priority** - Used in specific scenarios
4. **Low Priority** - Rarely used, can be deferred

### Migration Status Legend

- ⬜ Not Started - OpenGL-style deprecated method exists
- 🔄 In Progress - Vulkan-style API designed, implementation underway
- ✅ Complete - Fully migrated, deprecated method removed
- ⏸️ Deferred - Low priority, will migrate later

### Current Progress

**Total Methods**: 874 (all marked @Deprecated)
**Migrated**: 0 (CommandContext additions were the wrong approach - those don't count)
**Remaining**: 874

### Phase 1: Pipeline State Objects (Critical Path)

Vulkan uses Pipeline State Objects (PSOs) which bundle:
- Shader stages
- Vertex input state
- Input assembly
- Viewport/scissor
- Rasterization state
- Multisample state
- Depth/stencil state
- Color blend state

#### Shader Management
- ⬜ Shader compilation and linking → Pipeline creation
- ⬜ Shader source upload → SPIR-V module creation (OpenGL: source compilation)
- ⬜ Shader attribute binding → Vertex input layout specification

#### Vertex Input State
- ⬜ Vertex attribute configuration → VkVertexInputAttributeDescription
- ⬜ Vertex buffer binding → VkVertexInputBindingDescription

#### Rasterization State
- ⬜ Polygon mode, cull face, front face → VkPipelineRasterizationStateCreateInfo
- ⬜ Depth bias (polygon offset) → depthBias* parameters

#### Depth/Stencil State
- ⬜ Depth test, depth write, depth compare → VkPipelineDepthStencilStateCreateInfo
- ⬜ Stencil operations → VkStencilOpState

#### Color Blend State
- ⬜ Blend equations, blend functions → VkPipelineColorBlendAttachmentState
- ⬜ Color write mask → colorWriteMask

### Phase 2: Descriptor Sets & Resources (Critical Path)

#### Uniform Buffers
- ⬜ Uniform buffer creation and updates
- ⬜ Uniform buffer binding to descriptor sets

#### Texture & Sampler Management
- ⬜ Image creation and upload → VkImage creation
- ⬜ Sampler creation → VkSampler with immutable state
- ⬜ Descriptor set layout creation
- ⬜ Descriptor set allocation and updates

### Phase 3: Render Passes & Framebuffers (Critical Path)

#### Render Pass Design
- ⬜ Render pass creation with attachments
- ⬜ Subpass dependencies
- ⬜ Load/store operations

#### Framebuffer Management
- ⬜ Framebuffer creation linked to render pass
- ⬜ Framebuffer attachment binding

### Phase 4: Command Buffer Recording (Critical Path)

#### Command Buffer Management
- ⬜ Command buffer allocation
- ⬜ Begin/end recording
- ⬜ Command buffer submission

#### Drawing Commands
- ⬜ Draw calls (indexed, non-indexed, instanced)
- ⬜ Draw indirect
- ⬜ Multi-draw

#### Dynamic State
- ⬜ Viewport and scissor (if dynamic)
- ⬜ Blend constants (if dynamic)
- ⬜ Stencil reference (if dynamic)

### Phase 5: Memory & Synchronization (High Priority)

#### Buffer Management
- ⬜ Buffer creation with usage flags
- ⬜ Buffer mapping and unmapping
- ⬜ Buffer copies

#### Memory Barriers
- ⬜ Pipeline barriers for image layout transitions
- ⬜ Buffer/image memory barriers
- ⬜ Execution dependencies

#### Synchronization Objects
- ⬜ Fences for CPU-GPU sync
- ⬜ Semaphores for GPU-GPU sync
- ⬜ Events for fine-grained sync

### Phase 6: Advanced Features (Medium Priority)

#### Compute Shaders
- ⬜ Compute pipeline creation
- ⬜ Compute shader dispatch

#### Query Objects
- ⬜ Query pool creation
- ⬜ Timestamp queries
- ⬜ Occlusion queries

#### Debug & Validation
- ⬜ Debug labels and markers
- ⬜ Debug callbacks
- ⬜ Object naming

### Phase 7: Deferred Features (Low Priority)

Features that can wait until core functionality is complete.

## Implementation Guidelines

### Designing Vulkan-Native APIs

When designing new API methods, follow these principles:

1. **Explicit is better than implicit** - No hidden state changes
2. **Immutable when possible** - Match Vulkan's immutable objects (PSOs, samplers, etc.)
3. **Batch operations** - Group related operations (descriptor set updates, etc.)
4. **Clear ownership** - Who creates/destroys objects?
5. **Resource lifetimes** - Make object dependencies explicit
6. **Validation-friendly** - Design for validation layer support

### OpenGL Backend Translation Strategy

The OpenGL backend must:

1. **Maintain state tracking** - OpenGL is stateful, track what's bound
2. **Minimize state changes** - Cache and deduplicate state changes
3. **Handle PSO → state conversion** - Break PSOs into individual state calls
4. **Emulate descriptor sets** - Map to texture units + uniform bindings
5. **Immediate execution** - Execute "command buffers" immediately
6. **Match Vulkan behavior** - Ensure identical rendering results

### Testing Strategy

For each migrated method:

1. **Unit tests** - Test the new API in isolation
2. **Visual regression** - Screenshots before/after migration
3. **Performance profiling** - Ensure no regression
4. **Edge cases** - Test boundary conditions
5. **Integration tests** - Test interaction with other subsystems

## Future: Vulkan Backend Implementation

Once the API is fully migrated and stable:

1. **Vulkan backend scaffold** - Create VulkanBackend class
2. **Instance & device** - Initialize Vulkan instance and logical device
3. **Memory management** - Implement VMA or custom allocator
4. **Swapchain** - Present queue and image acquisition
5. **Core features** - Implement PSOs, descriptor sets, command buffers
6. **Validation** - Enable validation layers for development
7. **Optimization** - Minimize CPU overhead, maximize parallelism

## Notes & Lessons Learned

### What Didn't Work: CommandContext Approach

**The Problem**: Adding `CommandContext` to OpenGL methods like `glBindTexture(ctx, tex)` seemed logical but:
- Still required expensive Vulkan-to-OpenGL translation
- Didn't actually model Vulkan's architecture
- Required game code to migrate twice
- Created a "fake Vulkan" API that didn't perform well

**The Learning**: Vulkan isn't "OpenGL with explicit state" - it's a fundamentally different model. You can't retrofit OpenGL methods to work efficiently with Vulkan.

### What Works: Vulkan-First Design

**The Solution**: Design APIs that match Vulkan's model, then make OpenGL adapt:
- PSO creation → OpenGL state configuration
- Descriptor sets → Texture binding + uniforms
- Command buffers → Immediate execution
- Game code uses Vulkan-style APIs from day one
- When Vulkan backend is ready, it's a natural fit

## References

- [Vulkan Specification](https://www.khronos.org/registry/vulkan/specs/1.3/html/)
- [Vulkan Guide](https://github.com/KhronosGroup/Vulkan-Guide)
- [Vulkan Tutorial](https://vulkan-tutorial.com/)
- [OpenGL to Vulkan Migration](https://developer.nvidia.com/transitioning-opengl-vulkan)
- [Approaching Zero Driver Overhead (AZDO)](https://www.gdcvault.com/play/1020791/)

## Progress Updates

### 2026-02-12: Strategic Pivot
- **Decision**: Abandon CommandContext approach, switch to Vulkan-native API design
- **Status**: All 874 methods marked @Deprecated
- **Next**: Begin Phase 1 with shader and pipeline state migration
- **Goal**: Create proper Vulkan abstractions that OpenGL translates efficiently

---

**Last Updated**: 2026-02-12  
**Maintainer**: Development Team  
**Status**: Strategic planning phase - preparing for proper Vulkan-native API migration

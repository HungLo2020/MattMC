# Vulkanic API Migration Guide

**Document Purpose:** This guide explains the paradigm for migrating deprecated OpenGL-specific methods to a Vulkan-compatible graphics abstraction API.

**Status:** Active migration in progress (2 of 285 methods migrated)  
**Last Updated:** 2026-02-08

---

## Table of Contents

1. [Overview](#overview)
2. [Critical Architectural Constraints](#critical-architectural-constraints)
3. [The Problem We're Solving](#the-problem-were-solving)
4. [Migration Paradigm](#migration-paradigm)
5. [Core Design Principles](#core-design-principles)
6. [The Six-Step Workflow](#the-six-step-workflow)
7. [Method Selection Strategy](#method-selection-strategy)
8. [API Design Guidelines](#api-design-guidelines)
9. [Special Considerations](#special-considerations)
10. [Related Documentation](#related-documentation)

---

## Overview

Vulkanic is a graphics abstraction layer designed to sit between game/mod code and underlying graphics APIs (OpenGL and Vulkan). The primary goal is to migrate from an OpenGL-flavored API to a truly backend-agnostic API that will support both OpenGL (currently) and Vulkan (future).

**Current State:**
- 874 deprecated methods marked for replacement
- All deprecated methods are thin OpenGL wrappers (1:1 OpenGL bindings)
- Approximately 25-30% compatible with Vulkan's architectural principles
- Migration is incremental and method-by-method

**Target State:**
- Backend-agnostic API compatible with both OpenGL and Vulkan
- Clean separation between frontend API and backend implementations
- Proper abstractions for command buffers, pipelines, and resource management

---

## Critical Architectural Constraints

**These rules are ABSOLUTE and must NEVER be violated:**

### Rule 1: External Code Can Only Call VulkanicAPI
**Code outside the `vulkanic/` directory can ONLY call methods from `VulkanicAPI`.**

This means:
- Game code, mod code, Blaze3D, Sodium, Iris, Distant Horizons - all must use VulkanicAPI
- Direct imports of `GraphicsBackend` interface from external code are forbidden
- Direct imports of backend implementations (OpenGLBackend, VulkanBackend) from external code are forbidden
- The VulkanicAPI facade is the ONLY public interface to the graphics system

### Rule 2: Backends Directory is Strictly Private
**Nothing outside `vulkanic/` can directly call ANYTHING within the `backends/` directory.**

This means:
- No direct calls to `OpenGLBackend` from external code
- No direct calls to `VulkanBackend` (future) from external code
- No direct imports of classes within `backends/opengl/` or `backends/vulkan/` from external code
- Backend implementations are internal implementation details, never public APIs

### Rule 3: Backends Cannot Call Each Other
**Backend implementations are completely isolated from each other.**

This means:
- OpenGL backend cannot call Vulkan backend
- Vulkan backend cannot call OpenGL backend
- Each backend is a self-contained implementation of the GraphicsBackend interface
- No shared code between backends except through the GraphicsBackend interface contract

### Enforcement

These constraints ensure:
- **Clean abstraction:** Backends can be swapped, replaced, or extended without affecting external code
- **Future flexibility:** New backends can be added without changing external code
- **Testability:** Backends can be tested independently
- **Clear responsibilities:** Frontend defines "what" to do, backends define "how" to do it

**Violation of these rules breaks the entire architecture.** During migration, ensure all code changes respect these boundaries.

---

## The Problem We're Solving

The original Vulkanic API was a direct 1:1 mapping to OpenGL calls. While this successfully abstracted OpenGL behind an interface, the API design is fundamentally incompatible with Vulkan's architecture:

**OpenGL Paradigm (Current - Being Phased Out):**
- Immediate-mode rendering with global state machine
- Implicit state binding (texture units, buffer targets)
- Synchronous operations
- State changes scattered throughout rendering code

**Vulkan Paradigm (Target - Being Migrated To):**
- Explicit, command-buffer-based rendering
- No global state - everything is explicit
- Descriptor sets instead of bind points
- Pipeline state objects (PSOs) instead of dynamic state changes
- Render passes with clear begin/end boundaries

The migration bridges these two paradigms by creating an API that can be implemented efficiently in both OpenGL (via emulation) and Vulkan (natively).

---

## Migration Paradigm

### Incremental Replacement Strategy

Instead of attempting a "big bang" rewrite, we migrate one deprecated method at a time:

1. All existing methods are marked as deprecated
2. For each deprecated method, design a new Vulkan-compatible version
3. The new method must work with BOTH OpenGL AND Vulkan backends
4. Migrate all call sites to use the new method
5. Once a deprecated method has zero call sites, remove it entirely
6. Only after all methods are migrated will the actual Vulkan backend be implemented

**Benefits:**
- Incremental testing prevents regressions
- Validates new API design with real usage before Vulkan implementation
- Deprecation warnings guide developers away from legacy patterns
- Clear separation between legacy and modern code
- No "big bang" refactoring risk

---

## Core Design Principles

All new replacement methods must adhere to these principles:

### 0. Respect API Boundaries (Critical)
**Before anything else, ensure architectural constraints are maintained:**
- All external code must call VulkanicAPI only - never GraphicsBackend or backend implementations
- Backend implementations (in `backends/` directory) are strictly internal
- Backends are isolated from each other - no cross-backend calls
- See [Critical Architectural Constraints](#critical-architectural-constraints) for details

### 1. Backend Agnostic Design
- Must work identically with both OpenGL and Vulkan backends
- Avoid OpenGL-specific concepts like texture units, bind targets, global state
- Use semantic abstractions that map naturally to both APIs
- Think in terms of what the operation does, not how OpenGL does it

### 2. Explicit Resource Management
- Resources have clear creation and destruction lifecycle
- No hidden global state or implicit bindings
- Use descriptor sets instead of bind points
- Make resource dependencies explicit in the API

### 3. Command Buffer Based
- Rendering commands should conceptually be recorded into command buffers
- Enables deferred execution and multi-threading
- Compatible with Vulkan's command recording model
- Note: Full CommandBuffer parameter may be added in future evolution

### 4. Pipeline State Objects
- Rendering state should be baked into immutable pipeline objects
- Dynamic state is limited to viewport, scissor, and a few other operations
- Clear separation between setup time (pipeline creation) and runtime (rendering)
- Most state changes become pipeline switches, not individual state mutations

### 5. Render Pass Awareness
- Framebuffer operations occur within render pass context
- Clear begin/end boundaries for rendering operations
- Optimized for tile-based GPUs (modern mobile hardware)
- Attachments and clear values defined at render pass creation

---

## The Six-Step Workflow

For each deprecated method being migrated, follow this workflow:

### Step 1: Select Deprecated Method
- Review usage patterns across the codebase
- Prioritize high-frequency methods or those with few call sites
- Consider migration complexity and Vulkan compatibility
- Check VULKAN-COMPAT.md for method analysis and priority

### Step 2: Design New Abstracted Method
- Define a Vulkan-compatible API signature
- Ensure the OpenGL implementation is straightforward
- Document both OpenGL and future Vulkan semantics
- Name the method to reflect intent, not implementation
- Consider future evolution (e.g., CommandBuffer parameters)

### Step 3: Implement New Method (OpenGL Backend Only)
- Add method to GraphicsBackend interface WITHOUT @Deprecated annotation
- Implement in OpenGLBackend WITHOUT @Deprecated annotation
- Add to VulkanicAPI facade WITHOUT @Deprecated annotation
- Include comprehensive documentation for both current and future implementations

### Step 4: Migrate Call Sites
- Replace deprecated method calls with new method
- Update one file or component at a time
- Test after each file migration to catch issues early
- Maintain identical behavior during migration

### Step 5: Verify Zero Usage
- Search codebase for any remaining calls to deprecated method
- Ensure only the @Deprecated declaration remains
- Verify compilation succeeds with new API

### Step 6: Remove Deprecated Method
- Delete from VulkanicAPI.java
- Delete from GraphicsBackend.java
- Delete from OpenGLBackend.java
- The method is now fully migrated

**Repeat for the next method.**

---

## Method Selection Strategy

### Priority Categories

Methods should be migrated in this order:

**Priority 1: Dynamic State Operations**
- These are simplest to migrate
- Include viewport, scissor, line width, stencil values
- Map cleanly to both OpenGL and Vulkan dynamic state
- Ideal for establishing migration patterns

**Priority 2: High-Frequency State Changes**
- Methods called many times per frame
- Often related to blending, depth testing, rasterization
- Will eventually become pipeline state objects
- Significant performance impact when optimized

**Priority 3: Resource Operations**
- Texture, buffer, and shader operations
- Require descriptor set abstractions
- More complex than state operations
- Foundation for other migrations

**Priority 4: Framebuffer and Render Pass Operations**
- Require render pass abstraction infrastructure
- Complex interaction with pipeline state
- Migrate after basic infrastructure is in place

**Priority 5: Specialized and Low-Frequency Operations**
- Debug callbacks, queries, synchronization
- Complex or niche functionality
- Migrate last to avoid blocking common patterns

### Selection Criteria

When choosing the next method to migrate:
- Fewer call sites = easier migration
- More Vulkan-compatible = simpler design
- Higher usage frequency = bigger impact
- Related to already-migrated methods = consistent patterns

---

## API Design Guidelines

### Naming Conventions

**Dynamic State Methods:**
- Use prefix "setDynamic" to indicate per-frame state changes
- Example: `setDynamicViewport()`, `setDynamicScissor()`
- These map to vkCmdSet* functions in Vulkan

**Pipeline State Methods:**
- Use builder pattern for creating pipeline state objects
- Example: `PipelineBuilder.setDepthTest()`, `PipelineBuilder.build()`
- These map to VkGraphicsPipelineCreateInfo in Vulkan

**Resource Methods:**
- Use descriptive verbs: create, update, bind, destroy
- Make resource type explicit in name
- Example: `createTexture2D()`, `updateDescriptorSet()`

**Render Pass Methods:**
- Use begin/end pairs for clear boundaries
- Example: `beginRenderPass()`, `endRenderPass()`
- These map to vkCmdBeginRenderPass/vkCmdEndRenderPass

### Documentation Requirements

Every new non-deprecated method must include:
- Purpose and semantic meaning of the operation
- OpenGL implementation details (what GL call it maps to)
- Future Vulkan implementation details (what Vulkan call it will map to)
- Parameter descriptions with explicit units and coordinate systems
- Notes on future evolution (e.g., CommandBuffer parameter addition)

### Evolution Path

Current implementations are simplified for incremental migration. Future evolution includes:

**Phase 1 (Current):** Simple method signatures for easy migration
- Example: `setDynamicViewport(int x, int y, int width, int height)`

**Phase 2 (Future):** Add CommandBuffer parameter for Vulkan compatibility
- Example: `setDynamicViewport(CommandBuffer cmd, int x, int y, int width, int height)`

**Phase 3 (Later):** Full Vulkan backend implementation
- OpenGL backend emulates Vulkan concepts
- Vulkan backend uses native implementation

---

## Special Considerations

### Simple vs. Complex Methods

**Simple Methods (like viewport, scissor):**
- Direct mapping to both OpenGL and Vulkan
- Minimal abstraction needed
- Can be migrated with straightforward API design
- Good starting points for establishing patterns

**Complex Methods (like texture binding, framebuffer setup):**
- Require new abstraction infrastructure
- May need descriptor sets, pipeline objects, or render passes
- Design must account for both immediate needs and future architecture
- May require multiple related methods to be migrated together
- Consider deferring until infrastructure is in place

### Infrastructure Dependencies

Some methods cannot be migrated until supporting infrastructure exists:

**Descriptor Sets:** Required for texture and buffer binding operations  
**Pipeline State Objects:** Required for state management methods  
**Render Passes:** Required for framebuffer operations  
**Command Buffers:** Future addition for all rendering commands

Build infrastructure incrementally as needed by migrations, but plan ahead to avoid rework.

### Maintaining Compatibility

During migration:
- Game and mod code must continue to work
- Performance should not regress
- Behavior must remain identical
- Deprecated methods can coexist with new methods during transition
- Only remove deprecated methods when completely unused

---

## Related Documentation

For detailed information, consult these documents:

**[VULKAN-COMPAT.md](VULKAN-COMPAT.md)**  
Comprehensive analysis of all 874 deprecated methods, Vulkan compatibility ratings, migration priorities, and detailed per-method migration guides. Start here for understanding the full scope.

**[src/main/java/net/vulkanic/README.md](src/main/java/net/vulkanic/README.md)**  
Architecture overview of the Vulkanic abstraction layer, directory structure, design principles, and implementation phases. Essential for understanding the system architecture.

**[VULKANIC_PROGRESS.md](VULKANIC_PROGRESS.md)**  
Current migration status tracking for Blaze3D, Sodium, Iris Shaders, and other components. Shows which files have been migrated from direct OpenGL usage to Vulkanic.

---

## Summary

The Vulkanic API migration is a careful, incremental transformation from an OpenGL-specific API to a backend-agnostic graphics abstraction that supports both OpenGL and Vulkan. By following the six-step workflow and adhering to the core design principles, each method is migrated safely with full testing, ensuring the codebase remains stable while evolving toward a modern, efficient graphics architecture.

**Key Takeaway:** Think in terms of graphics concepts (viewport, scissor, pipelines, render passes) rather than OpenGL implementation details. Design APIs that express what you want to happen, not how OpenGL does it. The result will work efficiently with both graphics APIs.

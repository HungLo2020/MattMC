# OpenGL Backend Implementation

This directory contains MattMC's OpenGL-specific rendering backend implementation. All OpenGL API calls are isolated within this directory to maintain the rendering abstraction.

## Overview

The OpenGL backend implements the `RenderBackend` interface using LWJGL (Lightweight Java Game Library) to make OpenGL and GLFW calls. This is currently the primary (and only) graphics backend for MattMC.

**Key Principle**: All OpenGL-specific code belongs here. Code outside this directory must remain graphics API-agnostic.

## Directory Contents

### Core Backend Files

| File | Purpose |
|------|---------|
| `OpenGLRenderBackend.java` | Main `RenderBackend` implementation - translates DrawCommands to GL calls |
| `OpenGLBackendFactory.java` | Factory for creating OpenGL backend and window instances |
| `Window.java` | GLFW window creation and OpenGL context management |
| `Shader.java` | GLSL shader program loading and management |
| `Texture.java` | OpenGL texture loading and binding |
| `TextureManager.java` | Texture caching with LRU eviction |
| `TextureAtlas.java` | Runtime texture atlas generation |
| `Framebuffer.java` | OpenGL framebuffer objects for render-to-texture |
| `CubeMap.java` | Cubemap texture loading for skyboxes |
| `ChunkVAO.java` | VAO/VBO management for chunk meshes |

### Rendering Components

| File | Purpose |
|------|---------|
| `OpenGLChunkRenderer.java` | Chunk mesh rendering with VAO caching |
| `OpenGLChunkMeshManager.java` | Manages chunk mesh upload and lifecycle |
| `OpenGLItemRenderer.java` | Item rendering for UI (hotbar, inventory) |
| `OpenGLPanoramaRenderer.java` | Rotating cubemap backgrounds for menus |
| `OpenGLFrustum.java` | Frustum culling using OpenGL matrix state |

### Effects and Utilities

| File | Purpose |
|------|---------|
| `BlurEffect.java` | Gaussian blur post-processing shader |
| `BlurRenderer.java` | Blur effect helper for UI backgrounds |
| `AbstractBlurBox.java` | Blurred rectangle region rendering |
| `UIRenderHelper.java` | UI drawing utilities (text, shapes, projection) |
| `OpenGLColorHelper.java` | OpenGL color state management |
| `OpenGLSystemInfo.java` | System information queries via OpenGL |

### GUI Components (`gui/` subdirectory)

Contains OpenGL-specific GUI rendering:
- `components/` - Button renderer, text renderer, TrueType font handling
- `screens/` - Menu screens (title, pause, inventory, options, etc.)

## Code Organization Rules

### ✅ Code That Belongs Here

- Classes that import `org.lwjgl.opengl.*`
- Classes that make direct `gl*()` function calls
- OpenGL resource management (VAOs, VBOs, textures, shaders, FBOs)
- GLFW window and input handling
- OpenGL-specific rendering techniques

### ❌ Code That Does NOT Belong Here

- Pure mathematical utilities (color math, vector operations)
- Game logic (block states, entity behavior)
- Abstract data structures that don't require OpenGL
- System utilities that don't query OpenGL state

**If a class doesn't import anything from `org.lwjgl.opengl.*` or make `gl*()` calls, it probably belongs elsewhere.**

## The Abstraction Boundary

### Critical Rule

**Code outside `mattmc.client.renderer.backend` must NEVER directly import classes from this `opengl/` directory.**

```java
// ❌ WRONG - Direct OpenGL import outside backend/
import mattmc.client.renderer.backend.opengl.Texture;
import mattmc.client.renderer.backend.opengl.Window;

// ✅ CORRECT - Use the RenderBackend interface
import mattmc.client.renderer.backend.RenderBackend;
backend.loadTexture("/path/to/texture.png");
backend.drawTexture(textureId, x, y, width, height);
```

### Why This Matters

1. **Backend Portability**: Direct OpenGL imports make code impossible to use with Vulkan
2. **Testability**: OpenGL dependencies require a GL context, preventing unit testing
3. **Architecture**: The backend is an implementation detail that should be hidden
4. **Future-Proofing**: Adding new backends requires zero changes to game code

### If You Need OpenGL Functionality Outside

**Don't import OpenGL classes.** Instead:

1. Define the needed functionality as a method on `RenderBackend`
2. Implement it in `OpenGLRenderBackend`
3. Call it through the backend interface

## OpenGLRenderBackend Implementation

The `OpenGLRenderBackend` class is the heart of this directory. It:

1. **Maintains resource registries** mapping abstract IDs to OpenGL resources:
   - Mesh IDs → ChunkVAO objects
   - Material IDs → Shader + texture combinations
   - Transform IDs → Translation/transformation data

2. **Translates DrawCommands** into OpenGL calls:
   - Looks up resources by ID
   - Binds appropriate shaders and textures
   - Applies transformations
   - Issues draw calls

3. **Manages OpenGL state** for different render passes:
   - OPAQUE: Depth testing enabled, no blending
   - TRANSPARENT: Alpha blending enabled
   - UI: 2D orthographic projection, blending enabled

## Usage Pattern

```java
// The backend is created through the factory (outside this directory)
RenderBackendFactory factory = RenderBackendFactory.createOpenGL();
RenderBackend backend = factory.createBackend();

// All rendering goes through the interface
backend.beginFrame();
backend.clearBuffers();

// Submit draw commands
backend.submit(new DrawCommand(meshId, materialId, transformId, RenderPass.OPAQUE));

// 2D UI rendering
backend.setup2DProjection(screenWidth, screenHeight);
backend.drawText("Hello World", 10, 10, 1.5f);
backend.restore2DProjection();

backend.endFrame();
```

## Current State

- ✅ Full `RenderBackend` interface implementation
- ✅ Window/context management via GLFW
- ✅ Shader compilation and management
- ✅ Texture loading with caching
- ✅ Chunk mesh rendering with VAO pooling
- ✅ UI rendering (text, buttons, textures)
- ✅ Post-processing effects (blur)
- ✅ Input callback abstraction
- 🔄 Ongoing: Migrating remaining direct GL usages from outside backend/

## Technical Notes

### OpenGL Version
The backend targets OpenGL 2.1+ with compatibility profile for maximum hardware support. Modern features (VAOs, shaders) are used where available.

### Thread Safety
OpenGL operations are NOT thread-safe. All rendering must occur on the main thread that owns the GL context.

### Resource Cleanup
OpenGL resources (textures, shaders, VAOs, FBOs) must be explicitly deleted. Use appropriate cleanup methods when resources are no longer needed.

## Related Documentation

- See `../README.md` for the abstraction layer overview
- See `docs/RENDERING-SYSTEM.md` for comprehensive system documentation

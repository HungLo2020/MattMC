# Rendering Backend Abstraction Layer

This directory contains MattMC's rendering backend abstraction layer, which provides a clean separation between game logic and graphics API implementations.

## Overview

The rendering system is designed around a simple but powerful principle: **the rest of the codebase should be completely agnostic to which graphics API is being used**. Whether the game runs on OpenGL, Vulkan, or a future graphics API, the game logic remains unchanged.

This architecture provides:
- **Backend Independence**: Game logic never directly calls graphics APIs
- **Testability**: Rendering logic can be tested without a graphics context
- **Extensibility**: New graphics backends can be added without modifying game code
- **Maintainability**: Clear separation of concerns between layers

## Directory Structure

```
backend/
├── RenderBackend.java        # Core interface for all backends
├── RenderBackendFactory.java # Factory for creating backends and windows
├── DrawCommand.java          # API-agnostic draw command structure
├── RenderPass.java           # Render pass definitions (OPAQUE, TRANSPARENT, UI, etc.)
├── README.md                 # This file
└── opengl/                   # OpenGL-specific implementation
    ├── OpenGLRenderBackend.java
    ├── OpenGLBackendFactory.java
    ├── Shader.java
    ├── Texture.java
    ├── Window.java
    └── ... (other OpenGL resources)
```

## Core Components

### RenderBackend Interface

The `RenderBackend` interface defines the complete contract for rendering operations. It includes:

- **Frame management**: `beginFrame()`, `endFrame()`
- **Draw command submission**: `submit(DrawCommand cmd)`
- **2D/UI rendering**: `setup2DProjection()`, `restore2DProjection()`, `drawText()`, `fillRect()`, etc.
- **3D rendering**: `setupPerspectiveProjection()`, `enableDepthTest()`, `enableLighting()`, etc.
- **Resource management**: `loadTexture()`, `releaseTexture()`
- **Input handling**: Callback setup methods for keyboard, mouse, and window events
- **System queries**: `getGPUName()`, `getDisplayResolution()`, etc.

### DrawCommand

A `DrawCommand` represents a single API-agnostic draw operation:

```java
public final class DrawCommand {
    public final int meshId;        // Reference to mesh data
    public final int materialId;    // Reference to material/shader
    public final int transformIndex; // Reference to transformation data
    public final RenderPass pass;   // Which render pass (OPAQUE, UI, etc.)
}
```

The backend maps these abstract IDs to its internal resources (VAOs, shaders, textures in OpenGL; buffers and pipelines in Vulkan).

### RenderPass

Defines the logical rendering order:
- `OPAQUE` - Solid geometry rendered first with depth testing
- `TRANSPARENT` - Alpha-blended geometry rendered back-to-front
- `SHADOW` - (Reserved) Shadow map generation
- `UI` - User interface elements rendered last on top

### RenderBackendFactory

Creates backends and associated resources without exposing implementation details:

```java
RenderBackendFactory factory = RenderBackendFactory.createDefault();
WindowHandle window = factory.createWindow(1280, 720, "MattMC");
RenderBackend backend = factory.createBackend();
```

## Architecture Principles

### The Abstraction Boundary

**Code outside the `backend/` directory must never directly import or use classes from backend implementation directories.**

This is the foundational rule that enables backend independence:

| ❌ FORBIDDEN | ✅ CORRECT |
|-------------|-----------|
| `import mattmc.client.renderer.backend.opengl.Texture;` | `backend.loadTexture("/path/to/texture.png");` |
| `import mattmc.client.renderer.backend.opengl.Shader;` | Use `DrawCommand` with material IDs |
| `new OpenGLRenderBackend()` | `RenderBackendFactory.createDefault().createBackend();` |

### Three-Layer Architecture

1. **Game/World Layer** (`mattmc.world`, `mattmc.client`)
   - Blocks, chunks, entities, game logic
   - No knowledge of graphics APIs
   - Uses abstract rendering interfaces only

2. **Rendering Front-End** (`mattmc.client.renderer` outside `backend/`)
   - Determines WHAT to draw
   - Builds `DrawCommand` objects using logic classes (`ChunkRenderLogic`, `UIRenderLogic`, etc.)
   - No direct graphics API calls

3. **Rendering Back-End** (`mattmc.client.renderer.backend`)
   - Determines HOW to draw
   - Translates `DrawCommand` objects into graphics API calls
   - All graphics API code is isolated here

## Adding New Backend Implementations

To add a new graphics backend (e.g., Vulkan):

1. Create a new directory: `backend/vulkan/`
2. Implement `RenderBackend` interface in `VulkanRenderBackend.java`
3. Implement `RenderBackendFactory` in `VulkanBackendFactory.java`
4. Map `DrawCommand` meshId/materialId to Vulkan resources
5. Add factory method: `RenderBackendFactory.createVulkan()`

The game code automatically works with the new backend - no changes needed outside the `backend/` directory.

## Usage Example

```java
// Game initialization - backend-agnostic
RenderBackendFactory factory = RenderBackendFactory.createDefault();
WindowHandle window = factory.createWindow(1280, 720, "MattMC");
RenderBackend backend = factory.createBackend();

// Render loop - works with any backend
backend.beginFrame();
backend.setClearColor(0.5f, 0.7f, 1.0f, 1.0f);
backend.clearBuffers();

// Submit draw commands built by front-end logic
for (DrawCommand cmd : commandBuffer.getCommands()) {
    backend.submit(cmd);
}

backend.endFrame();
```

## Current Status

- ✅ `RenderBackend` interface fully defined
- ✅ `OpenGLRenderBackend` implementation complete
- ✅ Factory pattern for backend creation
- ✅ DrawCommand/RenderPass abstraction working
- ✅ UI rendering through backend abstraction
- ✅ 3D world rendering through backend abstraction
- 🔄 Ongoing: Refactoring remaining direct OpenGL usages
- 📋 Future: Vulkan backend (architecture ready, not yet implemented)

## Related Documentation

- See `docs/RENDERING-SYSTEM.md` for comprehensive system documentation
- See `opengl/README.md` for OpenGL-specific implementation details

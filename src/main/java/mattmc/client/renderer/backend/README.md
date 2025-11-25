# Backend Directory

This directory contains the rendering backend abstraction layer and implementations for MattMC.

## Purpose

The backend directory serves as the rendering abstraction layer, allowing the game to support multiple rendering APIs while maintaining a consistent interface for the rest of the codebase.

## Structure

- **RenderBackend.java** - The core interface that defines the contract for all rendering backend implementations
- **RenderPass.java** - Defines the different rendering passes (OPAQUE, TRANSPARENT, UI, etc.)
- **DrawCommand.java** - Encapsulates rendering commands that can be submitted to any backend
- **opengl/** - OpenGL-specific implementation of the rendering backend

## Adding New Backend Implementations

To add support for a new rendering API (e.g., Vulkan, DirectX):

1. Create a new directory under `backend/` (e.g., `vulkan/`)
2. Implement the `RenderBackend` interface for your API
3. Ensure your implementation can execute `DrawCommand` objects
4. Follow the same pattern as the OpenGL implementation

## Important Guidelines

- Backend implementations should be self-contained within their respective directories
- Only rendering API-specific code belongs in backend implementation directories
- Shared rendering logic should be placed in the parent `renderer/` directory
- All backend implementations must implement the `RenderBackend` interface

## ⚠️ CRITICAL: Abstraction Boundary

**CODE OUTSIDE THE `backend/` DIRECTORY MUST NOT DIRECTLY IMPORT OR USE CLASSES FROM BACKEND IMPLEMENTATION DIRECTORIES!**

This is a fundamental architectural principle:

### ❌ FORBIDDEN
- Code in `mattmc.client.renderer` (outside `backend/`) importing from `mattmc.client.renderer.backend.opengl`
- Code in `mattmc.client` importing OpenGL-specific classes like `Window`, `Texture`, `Shader`, etc.
- Code in `mattmc.world` importing rendering backend classes
- Direct instantiation of backend-specific renderers (e.g., `new CrosshairRenderer()`)

### ✅ CORRECT
- Code outside `backend/` should ONLY interact with:
  - `RenderBackend` interface
  - `DrawCommand` objects
  - `RenderPass` enum
  - Abstract/shared classes in the `renderer/` directory
- Backend-specific implementations are instantiated and managed within the backend
- Communication happens through the `RenderBackend` interface

### Why This Matters

1. **Portability**: If code directly imports OpenGL classes, switching to Vulkan/DirectX becomes impossible
2. **Testability**: Code tied to specific implementations cannot be easily unit tested
3. **Maintainability**: Changes to backend implementations shouldn't break application code
4. **Clean Architecture**: The backend is an implementation detail that should be hidden

### Migration Path

If you find code that violates this boundary:
1. Identify what functionality is being accessed
2. Add appropriate methods to the `RenderBackend` interface
3. Have the OpenGL backend implement those methods
4. Update calling code to use the interface instead of direct implementation

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

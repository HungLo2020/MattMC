# OpenGL Backend Directory

This directory contains the OpenGL-specific implementation of the MattMC rendering backend.

## ⚠️ IMPORTANT: OpenGL-Only Code

**ALL CLASSES IN THIS DIRECTORY MUST BE OPENGL-SPECIFIC!**

This means:
- ✅ **DO** put classes here that import `org.lwjgl.opengl.*`
- ✅ **DO** put classes here that make OpenGL API calls (glVertex, glColor, glBindTexture, etc.)
- ✅ **DO** put classes here that manage OpenGL resources (VAOs, VBOs, shaders, textures, framebuffers)
- ❌ **DO NOT** put pure utility classes here (color math, system info, etc.)
- ❌ **DO NOT** put generic rendering logic here that doesn't use OpenGL

## Code Organization Rules

If a class in this directory does NOT use OpenGL:
1. Move it to an appropriate utility package (e.g., `mattmc.util.*` or `mattmc.client.util.*`)
2. Update all imports in files that reference it
3. Ensure the class has no OpenGL dependencies

If a class has BOTH OpenGL and non-OpenGL code:
1. Split the class into two parts:
   - OpenGL-specific code stays here
   - Pure utility/math code moves to appropriate util package
2. Update all imports accordingly

## Examples

### ✅ Belongs Here
- `OpenGLRenderBackend.java` - Implements OpenGL rendering backend
- `Shader.java` - Manages OpenGL shaders
- `Texture.java` - Manages OpenGL textures
- `ChunkVAO.java` - Manages OpenGL Vertex Array Objects
- `OpenGLColorHelper.java` - Sets OpenGL color state using `glColor4f()`

### ❌ Does NOT Belong Here
- Pure color math utilities (extractRed, packRGB, lerp colors) → Move to `mattmc.util.ColorUtils`
- System information gathering (CPU, memory, GPU info) → Move to `mattmc.client.util.SystemInfo`
- Mathematical calculations without OpenGL calls → Move to `mattmc.util.MathUtils`

## Purpose

This directory implements the OpenGL rendering backend for MattMC. It:
- Implements the `RenderBackend` interface using OpenGL
- Manages all OpenGL state and resources
- Provides OpenGL-specific rendering utilities
- Handles shader compilation and management
- Manages textures, framebuffers, and other OpenGL objects

## Key Classes

- **OpenGLRenderBackend** - Main backend implementation
- **Shader** - OpenGL shader management
- **Texture** - Texture loading and management
- **Window** - GLFW window and OpenGL context management
- **ChunkRenderer** - Chunk mesh rendering using OpenGL
- **UIRenderHelper** - UI rendering utilities using OpenGL
- **Framebuffer** - OpenGL framebuffer management

## Guidelines for Contributors

1. **Check imports**: If your class doesn't import anything from `org.lwjgl.opengl.*`, it probably doesn't belong here
2. **Check API calls**: If your class doesn't call any `gl*()` functions, it probably doesn't belong here
3. **Separate concerns**: Keep pure logic/math separate from OpenGL rendering code
4. **Follow the pattern**: Look at existing classes as examples of proper organization

## Related Documentation

- See `/src/main/java/mattmc/client/renderer/backend/README.md` for backend abstraction information
- See `OPENGL-REFACTOR.md` in the project root for refactoring guidelines

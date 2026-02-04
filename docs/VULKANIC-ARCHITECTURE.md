# Vulkanic Architecture - The Correct Way

## Overview

This document explains the proper architecture of the Vulkanic rendering abstraction layer and the strict rules that must be followed.

## Architectural Rules (CRITICAL)

### Rule 1: OpenGL Isolation
**ONLY** code in `src/main/java/net/vulkanic/backends/opengl/` can make OpenGL calls.

- ✅ Allowed: `OpenGLCommandBuffer.java` calling `GL11.glClear()`
- ❌ Forbidden: `GlCommandEncoder.java` calling `GL11.glClear()`
- ❌ Forbidden: `Minecraft.java` calling `GL11.glClear()`

### Rule 2: Backend Isolation
**ONLY** code in `src/main/java/net/vulkanic/` can interact with backends.

- ✅ Allowed: `Vulkanic.java` creating `OpenGLDevice`
- ❌ Forbidden: `Blaze3D` creating `OpenGLDevice`
- ❌ Forbidden: `Minecraft.java` calling `OpenGLCommandBuffer`

### Rule 3: Public API Only
Code outside `net/vulkanic/` can **ONLY** call the Vulkanic public API.

- ✅ Allowed: `GlCommandEncoder.java` calling `Vulkanic.getDevice()`
- ✅ Allowed: `GlCommandEncoder.java` calling `VulkanicCommandBuffer.clear()`
- ❌ Forbidden: `GlCommandEncoder.java` calling `OpenGLCommandBuffer.clear()`
- ❌ Forbidden: `GlCommandEncoder.java` calling `GL11.glClear()`

## Correct Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Game Code (Minecraft, GameRenderer, LevelRenderer, etc.)   │
│  - Cannot call Vulkanic directly                             │
│  - Cannot call OpenGL directly                               │
│  - Can only call Blaze3D                                     │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Blaze3D (RenderSystem, GlCommandEncoder, GlDevice)         │
│  - Calls Vulkanic API for rendering operations              │
│  - Cannot call OpenGL directly (except legacy code)          │
│  - Cannot call backends directly                             │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Vulkanic API (Public Interface)                             │
│  - Vulkanic.initialize()                                     │
│  - Vulkanic.getDevice()                                      │
│  - VulkanicDevice, VulkanicCommandBuffer, etc.               │
│  - Backend-agnostic                                          │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  Vulkanic Backend Selection                                  │
│  - BackendType.OPENGL → OpenGLDevice                         │
│  - BackendType.VULKAN → VulkanDevice (future)                │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  OpenGL Backend (backends/opengl/)                           │
│  - OpenGLDevice, OpenGLCommandBuffer, OpenGLShader, etc.     │
│  - ONLY place that can call OpenGL                           │
│  - Uses LWJGL directly (GL11, GL20, GL30, etc.)              │
│  - No Blaze3D dependencies                                   │
└──────────────────────────┬──────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│  OpenGL (LWJGL)                                               │
│  - GL11, GL20, GL30, etc.                                    │
│  - Only called from backends/opengl/                         │
└─────────────────────────────────────────────────────────────┘
```

## Call Chain Example: Clear Screen

### Current Implementation (Correct)

```
1. Minecraft.java
   └─> [No Vulkanic calls - uses RenderSystem]

2. GlCommandEncoder.clearColorAndDepthTextures()
   ├─> Vulkanic.isInitialized()
   ├─> Vulkanic.getDevice()
   ├─> device.createCommandBuffer()
   ├─> cmd.clearColorAndDepth(r, g, b, a, depth)
   └─> cmd.submit()

3. OpenGLCommandBuffer.clearColorAndDepth()
   ├─> GL11.glClearColor(r, g, b, a)
   ├─> GL11.glClearDepth(depth)
   └─> GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT)
```

## Backend Implementation Requirements

### OpenGL Backend (`backends/opengl/`)

All classes in this package:
- ✅ MUST use LWJGL OpenGL directly (GL11, GL20, GL30, etc.)
- ❌ MUST NOT use Blaze3D (GlStateManager, RenderSystem, etc.)
- ❌ MUST NOT be called directly from outside `net/vulkanic/`

**Files:**
- `OpenGLDevice.java` - Device queries using GL11
- `OpenGLCommandBuffer.java` - Rendering using GL11/GL13/GL20
- `OpenGLShader.java` - Shader compilation using GL20
- `OpenGLBuffer.java` - Buffer management using GL15
- `OpenGLTexture.java` - Texture management using GL11/GL12
- `OpenGLFramebuffer.java` - FBO management using GL30

### Future Vulkan Backend (`backends/vulkan/`)

Will follow the same pattern:
- ✅ MUST use LWJGL Vulkan directly
- ❌ MUST NOT use Blaze3D
- ❌ MUST NOT be called directly from outside `net/vulkanic/`

## Benefits of This Architecture

### 1. Clean Separation of Concerns
- Game code doesn't know about rendering backends
- Blaze3D doesn't know about OpenGL/Vulkan details
- Backends are self-contained

### 2. Easy Backend Switching
```java
// Switch from OpenGL to Vulkan
Vulkanic.initialize(BackendType.VULKAN);
```
No game code changes needed!

### 3. No Circular Dependencies
- Blaze3D → Vulkanic (one direction)
- Vulkanic → Backend (one direction)
- Backend → OpenGL/Vulkan (one direction)

### 4. Testable
- Can test backends in isolation
- Can mock Vulkanic for testing Blaze3D
- Can test without full game context

## Migration Progress

### Phase 1: Infrastructure ✅ COMPLETE
- Vulkanic API defined
- OpenGL backend implemented
- Initialization integrated into RenderSystem

### Phase 2: Blaze3D Integration (In Progress)
- ✅ `GlCommandEncoder.clearColorAndDepthTextures()` routed through Vulkanic
- ✅ `GlCommandEncoder.clearDepthTexture()` routed through Vulkanic
- ⏳ More operations to be routed...

### Phase 3: Full Migration (Future)
- Route all Blaze3D rendering through Vulkanic
- Migrate third-party mods (Sodium, Iris)
- Add Vulkan backend

## Enforcement

### Build-Time Checks
Consider adding to build:
```java
// Ensure backends don't import Blaze3D
if (file.path.contains("backends/opengl/")) {
    assertNoImport("net.blaze3d.*");
}

// Ensure game code doesn't import backends
if (!file.path.contains("net/vulkanic/")) {
    assertNoImport("net.vulkanic.backends.*");
}
```

### Code Review Checklist
- [ ] Does backend code import Blaze3D? → ❌ REJECT
- [ ] Does game code import backend classes? → ❌ REJECT
- [ ] Does Blaze3D call OpenGL directly for new code? → ❌ REJECT
- [ ] Does new code route through Vulkanic? → ✅ APPROVE

## Summary

**The Golden Rule:**
```
Game → Blaze3D → Vulkanic API → Backend → OpenGL/Vulkan
```

**Never:**
```
Game → OpenGL ❌
Blaze3D → OpenGL ❌ (legacy code being migrated)
Backend → Blaze3D ❌
Game → Backend ❌
```

This architecture enables:
- Multiple rendering backends (OpenGL, Vulkan, Metal, etc.)
- Clean separation of concerns
- Easy testing and maintenance
- Future-proof design

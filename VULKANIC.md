# VULKANIC - OpenGL Migration Research & Implementation Plan

**Date**: 2026-02-04  
**Purpose**: Research document identifying all OpenGL usage in MattMC and defining migration strategy to the Vulkanic rendering abstraction layer.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [What is Blaze3D?](#what-is-blaze3d)
3. [OpenGL Call Inventory](#opengl-call-inventory)
4. [Current Architecture Analysis](#current-architecture-analysis)
5. [Vulkanic Architecture Plan](#vulkanic-architecture-plan)
6. [Migration Strategy](#migration-strategy)
7. [Implementation Roadmap](#implementation-roadmap)

---

## Executive Summary

### Key Findings

This research document catalogs **all OpenGL usage** within the MattMC codebase and provides a comprehensive plan for migrating to the **Vulkanic rendering abstraction layer** as specified in `/src/main/java/net/vulkanic/README.md`.

**Critical Discovery**: The Minecraft core codebase (`net/minecraft/`) makes **ZERO direct OpenGL calls**. All rendering is already abstracted through **Blaze3D**, which serves as the existing rendering abstraction layer.

**OpenGL Call Locations**:
1. **Blaze3D** (`net/blaze3d/`) - Mojang's rendering abstraction (primary layer)
2. **Sodium** (`net/sodium/`) - Performance optimization mod
3. **Iris** (`net/irisshaders/`) - Shader pack support mod  
4. **Distant Horizons** (`com/seibel/distanthorizons/`) - LOD rendering mod
5. **VoxelMap** (`net/voxelmap/`) - Minimap mod (minimal GL usage)

### Strategic Recommendation

**Two-Phase Approach**:
1. **Phase 1**: Create Vulkanic as a **wrapper around Blaze3D** initially (minimal disruption)
2. **Phase 2**: Gradually move Blaze3D internals into Vulkanic backends, eventually replacing Blaze3D entirely

This allows incremental migration while maintaining stability.

---

## What is Blaze3D?

### Overview

**Blaze3D** is Mojang's proprietary low-level graphics rendering abstraction layer that sits between the Minecraft game engine and OpenGL. It's located in `/src/main/java/net/blaze3d/` and serves as the **foundational rendering API** for Minecraft Java Edition.

### Purpose & Role

Blaze3D provides:
- **Platform-independent graphics API** abstracting OpenGL details
- **Shader-based modern rendering pipeline** (GLSL shaders)
- **State management** to minimize redundant OpenGL calls
- **Buffer and resource management** (VBOs, FBOs, textures)
- **Thread-safe rendering** with render thread enforcement

### Architecture

```
Minecraft Game Code
       ↓
  Blaze3D API
       ↓
  OpenGL (via LWJGL)
       ↓
    GPU Driver
```

### Key Components

#### 1. **GPU Device Management** (`systems/`, `opengl/`)
- `GpuDevice` - Abstract GPU interface
- `GlDevice` - OpenGL implementation of GpuDevice  
- `RenderSystem` - Central rendering orchestrator
- `CommandEncoder` - GPU command execution

#### 2. **Graphics Pipeline** (`pipeline/`)
- `RenderPipeline` - Rendering configuration (shaders, formats, blending)
- `CompiledRenderPipeline` - Compiled shader programs
- `RenderTarget`/`TextureTarget` - Framebuffer management
- `BlendFunction` - Blending modes

#### 3. **OpenGL State Management** (`opengl/`)
- `GlStateManager` - Tracks OpenGL state with caching
- `GlProgram` - Shader program wrapper
- `GlShaderModule` - Shader compilation/linking
- `GlBuffer` - GPU buffer abstraction
- `GlTexture`/`GlTextureView` - Texture management

#### 4. **Vertex & Mesh Construction** (`vertex/`)
- `Tesselator` - Singleton vertex buffer manager
- `BufferBuilder` - Builds vertex data into buffers
- `VertexFormat` - Vertex layout (positions, normals, UVs, colors)
- `MeshData` - Completed mesh data for GPU upload

#### 5. **GPU Resources** (`buffers/`, `textures/`)
- `GpuBuffer` - GPU buffer objects (VBO, UBO, etc.)
- `GpuBufferSlice` - Portions of larger GPU buffers
- `GpuTexture`/`GpuTextureView` - Texture resources
- Std140 layout calculator for uniform buffers

#### 6. **Platform Abstraction** (`platform/`)
- `Window` - Window management via LWJGL/GLFW
- `GLX` - OpenGL extension handling
- `DepthTestFunction`, `LogicOp`, `PolygonMode` - GPU enums

### Design Patterns

- **State-based rendering**: Maintains GPU state to minimize redundant calls
- **Resource caching**: Pipelines, shaders, and vertex arrays are cached
- **Thread safety**: Enforces render thread execution (`assertOnRenderThread()`)
- **Abstraction layers**: Separates high-level pipeline from low-level OpenGL
- **Extension detection**: Adaptively uses available OpenGL extensions

### Modern OpenGL Features Used

- Vertex Array Objects (VAO)
- Framebuffer Objects (FBO)
- Shader programs (GLSL)
- Buffer storage extensions (ARB_buffer_storage)
- Direct State Access (ARB_direct_state_access)
- Uniform Buffer Objects (UBO)
- Fence sync (GL_ARB_sync)

---

## OpenGL Call Inventory

### 1. Blaze3D (`net/blaze3d/`)

**Location**: Core rendering abstraction layer  
**Purpose**: Mojang's rendering API wrapping OpenGL

#### Files Making OpenGL Calls

##### `GlStateManager.java` - Primary State Management
**Thread Safety**: All calls wrapped with `RenderSystem.assertOnRenderThread()`  
**Caching**: Tracks state to avoid redundant GL calls  
**Iris Integration**: Conditional override points for shader mods

| OpenGL Call | Purpose | Category |
|-------------|---------|----------|
| `GL20.glScissor()` | Set scissor box | State |
| `GL11.glDepthFunc()`, `GL11.glDepthMask()` | Depth testing | State |
| `GL20.glCreateShader()`, `GL20.glCompileShader()` | Shader compilation | Shader |
| `GL20.glCreateProgram()`, `GL20.glLinkProgram()` | Program linking | Shader |
| `GL20.glAttachShader()`, `GL20.glDeleteShader()` | Shader lifecycle | Shader |
| `GL20.glUseProgram()`, `GL20.glDeleteProgram()` | Program activation | Shader |
| `GL20.glGetUniformLocation()` | Uniform location query | Shader |
| `GL20.glUniform1i()` | Set integer uniform | Shader |
| `GL20.glBindAttribLocation()` | Vertex attribute binding | Vertex |
| `GL15.glGenBuffers()`, `GL15.glDeleteBuffers()` | Buffer lifecycle | Buffer |
| `GL15.glBindBuffer()` | Buffer binding | Buffer |
| `GL15.glBufferData()`, `GL15.glBufferSubData()` | Upload buffer data | Buffer |
| `GL30.glMapBufferRange()`, `GL15.glUnmapBuffer()` | Map GPU memory | Buffer |
| `GL30.glGenVertexArrays()`, `GL30.glBindVertexArray()` | VAO management | Vertex |
| `GL30.glGenFramebuffers()`, `GL30.glDeleteFramebuffers()` | FBO lifecycle | Framebuffer |
| `GL30.glBindFramebuffer()` | FBO binding | Framebuffer |
| `GL30.glFramebufferTexture2D()` | Attach texture to FBO | Framebuffer |
| `GL30.glBlitFramebuffer()` | Copy between framebuffers | Framebuffer |
| `GL14.glBlendFuncSeparate()` | Blending function | State |
| `GL11.glGenTextures()`, `GL11.glDeleteTextures()` | Texture lifecycle | Texture |
| `GL11.glBindTexture()` | Texture binding | Texture |
| `GL11.glTexParameteri()` | Texture parameters | Texture |
| `GL11.glTexImage2D()`, `GL11.glTexSubImage2D()` | Upload texture data | Texture |
| `GL13.glActiveTexture()` | Select texture unit | Texture |
| `GL20.glVertexAttribPointer()` | Vertex attribute format | Vertex |
| `GL20.glEnableVertexAttribArray()` | Enable vertex attribute | Vertex |
| `GL30.glVertexAttribIPointer()` | Integer vertex attribute | Vertex |
| `GL11.glDrawArrays()`, `GL43C.glDrawElements()` | Draw commands | Draw |
| `GL11.glPixelStorei()` | Pixel pack/unpack | Texture |
| `GL11.glReadPixels()` | Read framebuffer | Framebuffer |
| `GL11.glEnable()`, `GL11.glDisable()` | Enable/disable state | State |
| `GL11.glViewport()` | Set viewport | State |
| `GL11.glColorMask()` | Color write mask | State |
| `GL11.glClear()` | Clear buffers | State |
| `GL11.glPolygonMode()`, `GL11.glPolygonOffset()` | Polygon rendering | State |
| `GL11.glLogicOp()` | Logical operations | State |
| `GL32.glFenceSync()`, `GL32.glClientWaitSync()` | GPU synchronization | Sync |
| `GL32.glDeleteSync()` | Delete sync object | Sync |
| `GL11.glGetError()` | Error checking | Debug |
| `GL11.glGetString()`, `GL11.glGetInteger()` | Query capabilities | Info |
| `GL20.glGetShaderi()`, `GL20.glGetProgrami()` | Shader/program queries | Shader |
| `GL20.glGetShaderInfoLog()`, `GL20.glGetProgramInfoLog()` | Compilation logs | Shader |
| `GL11.glGetTexLevelParameteri()` | Texture parameter query | Texture |

**Total OpenGL Functions**: ~60 distinct functions

---

##### `GlDevice.java` - Device Initialization
**Purpose**: GPU device wrapper, capability detection, pipeline compilation

| OpenGL Call | Purpose |
|-------------|---------|
| `GL.createCapabilities()` | Initialize LWJGL GL context |
| `GL11.glGetInteger(GL_UNIFORM_BUFFER_OFFSET_ALIGNMENT)` | Query UBO alignment |
| `GL11.glEnable(GL_TEXTURE_CUBE_MAP_SEAMLESS)` | Enable seamless cubemaps |
| `GL11.glBindTexture(GL_TEXTURE_CUBE_MAP, ...)` | Probe max texture size |
| `GlStateManager._texImage2D()` | Texture creation for probing |
| `GL11.glGetTexLevelParameteri()` | Query texture dimensions |

**Extension Detection**:
- ARB_direct_state_access
- KHR_debug
- ARB_buffer_storage
- ARB_sync

---

##### `GlCommandEncoder.java` - Command Encoding
**Purpose**: Primary graphics command API for render passes

| OpenGL Call | Purpose |
|-------------|---------|
| `GL11.glClearColor()`, `GL11.glClearDepth()` | Set clear values |
| `GlStateManager._glBindFramebuffer()` | Bind draw framebuffer |
| `GlStateManager._scissorBox()` | Scissor test region |
| `GlStateManager._viewport()` | Rendering viewport |
| `GL11.glBindTexture(GL_TEXTURE_CUBE_MAP, ...)` | Cubemap binding |
| `GlStateManager._texSubImage2D()` | Texture upload |
| `GlStateManager._readPixels()` | Pixel readback |
| `GlStateManager._glBindBuffer(GL_PIXEL_PACK_BUFFER, ...)` | Async pixel transfer |
| `GL11.glDrawBuffer()` | Select color attachment |

**Uses DirectStateAccess abstraction** for buffer/framebuffer ops

---

##### `GlProgram.java` - Shader Programs
**Purpose**: Compiled shader program management

| OpenGL Call | Purpose |
|-------------|---------|
| `GL20.glLinkProgram()` | Link shader program |
| `GL20.glCreateProgram()` | Create program handle |
| `GL20.glAttachShader()` | Attach shader to program |
| `GL20.glBindAttribLocation()` | Bind vertex attributes |
| `GL31.glGetUniformBlockIndex()` | Get UBO index |
| `GL31.glUniformBlockBinding()` | Bind UBO to slot |
| `GL31.glGetActiveUniformBlockName()` | Query UBO names |
| `GL20.glGetProgrami()` | Get program info |
| `GL20.glGetProgramInfoLog()` | Get link errors |

---

##### `DirectStateAccess.java` - Modern GL Abstraction
**Purpose**: Provides ARB_direct_state_access OR legacy fallback

**ARB_direct_state_access Path (Modern)**:
| OpenGL Call | Purpose |
|-------------|---------|
| `ARBDirectStateAccess.glCreateBuffers()` | Create buffer handles |
| `ARBDirectStateAccess.glNamedBufferData()` | Upload data without binding |
| `ARBDirectStateAccess.glNamedBufferSubData()` | Update data without binding |
| `ARBDirectStateAccess.glNamedBufferStorage()` | Immutable buffer storage |
| `ARBDirectStateAccess.glMapNamedBufferRange()` | Map buffer without binding |
| `ARBDirectStateAccess.glUnmapNamedBuffer()` | Unmap buffer |
| `ARBDirectStateAccess.glCreateFramebuffers()` | Create FBO handles |
| `ARBDirectStateAccess.glNamedFramebufferTexture()` | Attach texture to FBO |
| `ARBDirectStateAccess.glBlitNamedFramebuffer()` | Blit between FBOs |
| `ARBDirectStateAccess.glCopyNamedBufferSubData()` | Copy buffer data |
| `ARBDirectStateAccess.glFlushMappedNamedBufferRange()` | Flush mapped range |

**Legacy Fallback Path (Bind-Then-Operate)**:
| OpenGL Call | Purpose |
|-------------|---------|
| `GL15.glBindBuffer()` | Bind buffer target |
| `GL15.glBufferData()` | Upload after binding |
| `GL30.glBindFramebuffer()` | Bind FBO |
| `GL30.glFramebufferTexture2D()` | Attach after binding |
| `GL30.glBlitFramebuffer()` | Blit after binding |
| `GL31.glCopyBufferSubData()` | Copy after binding |
| `GL30.glFlushMappedBufferRange()` | Flush after binding |
| `ARBBufferStorage.glBufferStorage()` | Immutable storage (bind-based) |

---

##### Other Blaze3D Files

| File | OpenGL Usage | Purpose |
|------|--------------|---------|
| `TimerQuery.java` | `GL33.glGenQueries()`, `GL33.glBeginQuery()`, `GL33.glEndQuery()`, `GL33.glGetQueryObjecti64v()` | GPU timing queries |
| `VertexArrayCache.java` | `GL30.glGenVertexArrays()`, `GL30.glDeleteVertexArrays()` | VAO pooling |
| `BufferStorage.java` | `ARBBufferStorage.glBufferStorage()` | Persistent buffer mapping |
| `GlDebug.java` | `GL43.glDebugMessageControl()`, `KHRDebug.glDebugMessageCallback()` | Debug output |
| `GlDebugLabel.java` | `KHRDebug.glObjectLabel()` | Debug labels for GPU objects |

---

### 2. Sodium (`net/sodium/`)

**Location**: Performance optimization mod  
**Purpose**: Highly optimized chunk rendering system

#### Philosophy
- **Direct GL calls** via LWJGL bindings (GL20C, GL30C, GL32C)
- **State tracking** through `GlStateTracker` to minimize redundant calls
- **Device abstraction** via `RenderDevice` interface
- **Fence-based sync** for efficient GPU coordination

#### Files Making OpenGL Calls

##### `RenderDevice.java` - Core Rendering Device
| OpenGL Call | Purpose |
|-------------|---------|
| `GL20C.glUseProgram()` | Activate shader programs |
| `GL30C.glBindVertexArray()` | Bind vertex geometry |
| `GL15C.glBindBuffer()` | Bind GPU buffers |
| `GL20C.glDrawElementsBaseVertex()` | Indexed draw calls |
| `GL11C.glDrawArrays()` | Array draw calls |

##### `GlBuffer.java` - GPU Buffer Management
| OpenGL Call | Purpose |
|-------------|---------|
| `GL15.glGenBuffers()` | Create buffer handle |
| `GL15.glDeleteBuffers()` | Destroy buffer |
| `GL15.glBindBuffer()` | Bind buffer target |
| `GL15.glBufferData()` | Allocate buffer storage |
| `GL30.glMapBufferRange()` | Map for CPU access |
| `GL15.glUnmapBuffer()` | Unmap buffer |
| `GL44.glBufferStorage()` | Persistent mapping (GL 4.4+) |

##### `GlVertexArray.java` - Vertex Array Objects
| OpenGL Call | Purpose |
|-------------|---------|
| `GL30.glGenVertexArrays()` | Create VAO |
| `GL30.glDeleteVertexArrays()` | Delete VAO |
| `GL30.glBindVertexArray()` | Bind VAO |
| `GL20.glEnableVertexAttribArray()` | Enable attribute |
| `GL20.glVertexAttribPointer()` | Configure attribute |
| `GL33.glVertexAttribDivisor()` | Instancing divisor |

##### `GlProgram.java` & `GlShader.java` - Shaders
| OpenGL Call | Purpose |
|-------------|---------|
| `GL20.glCreateShader()` | Create shader |
| `GL20.glShaderSource()` | Set shader source |
| `GL20.glCompileShader()` | Compile shader |
| `GL20.glCreateProgram()` | Create program |
| `GL20.glAttachShader()` | Attach shader to program |
| `GL20.glLinkProgram()` | Link program |
| `GL20.glGetUniformLocation()` | Get uniform location |
| `GL31.glGetUniformBlockIndex()` | Get UBO index |
| `GL31.glUniformBlockBinding()` | Bind UBO |

##### `GlUniform*.java` - Uniform Updates
| OpenGL Call | Purpose |
|-------------|---------|
| `GL20.glUniform1f()`, `GL20.glUniform1i()` | Set scalar uniforms |
| `GL20.glUniform3fv()`, `GL20.glUniform4fv()` | Set vector uniforms |
| `GL20.glUniformMatrix4fv()` | Set matrix uniforms |
| `GL30.glBindBufferBase()` | Bind UBO to binding point |

##### `GlFence.java` - GPU Synchronization
| OpenGL Call | Purpose |
|-------------|---------|
| `GL32.glFenceSync()` | Create fence |
| `GL32.glClientWaitSync()` | Wait for fence |
| `GL32.glDeleteSync()` | Delete fence |

##### `GlContextInfo.java` - Capability Detection
| OpenGL Call | Purpose |
|-------------|---------|
| `GL11.glGetString(GL_VENDOR)` | GPU vendor |
| `GL11.glGetString(GL_RENDERER)` | GPU model |
| `GL11.glGetString(GL_VERSION)` | GL version |
| `GL30.glGetStringi(GL_EXTENSIONS, i)` | Extension list |

**Total Files**: ~25 files with OpenGL calls

---

### 3. Iris (`net/irisshaders/`)

**Location**: Shader pack support (OptiFine compatibility)  
**Purpose**: Advanced shader pipelines with deferred/forward+ rendering

#### Philosophy
- **Abstraction via IrisRenderSystem** - Wraps GL with DSA support
- **Advanced shader features** - Compute, tessellation, geometry shaders
- **Multi-pass rendering** - Shadow maps, compositing, deferred
- **Capability detection** - GL 4.0+, ARB extensions required
- **Render thread safety** - All GL calls validated

#### Files Making OpenGL Calls

##### `IrisRenderSystem.java` - Core GL Abstraction
**Purpose**: Main entry point for all Iris GL operations

| OpenGL Call | Purpose |
|-------------|---------|
| `GL20.glUseProgram()` | Activate shader programs |
| `GL11.glBindTexture()` | Bind textures |
| `GL13.glActiveTexture()` | Select texture unit |
| `GL30.glBindFramebuffer()` | Bind framebuffers |
| `GL11.glViewport()` | Set viewport |
| `GL11.glClear()` | Clear buffers |
| `GL30.glDrawBuffers()` | Set MRT targets |
| `GL43.glDispatchCompute()` | Compute shader dispatch |
| `GL42.glMemoryBarrier()` | Synchronization barrier |

##### `Program.java` - Enhanced Shader Programs
| OpenGL Call | Purpose |
|-------------|---------|
| `GL20.glCreateProgram()` | Create program |
| `GL20.glAttachShader()` | Attach shaders |
| `GL20.glLinkProgram()` | Link program |
| `GL20.glGetUniformLocation()` | Query uniform |
| `GL31.glGetUniformBlockIndex()` | Query UBO |
| `GL31.glUniformBlockBinding()` | Bind UBO |
| `GL42.glGetProgramResourceIndex()` | Query SSBO index |
| `GL43.glShaderStorageBlockBinding()` | Bind SSBO |

##### `GlFramebuffer.java` - Multi-Target Framebuffers
| OpenGL Call | Purpose |
|-------------|---------|
| `GL30.glGenFramebuffers()` | Create FBO |
| `GL30.glBindFramebuffer()` | Bind FBO |
| `GL30.glFramebufferTexture2D()` | Attach color/depth |
| `GL30.glDrawBuffers()` | Configure MRT |
| `GL30.glCheckFramebufferStatus()` | Validate FBO |
| `GL30.glDeleteFramebuffers()` | Delete FBO |

##### `CompositeRenderer.java` - Post-Processing
| OpenGL Call | Purpose |
|-------------|---------|
| `GL20.glUseProgram()` | Composite shader |
| `GL30.glBindFramebuffer()` | Bind render target |
| `GL11.glDrawArrays()` | Full-screen quad |
| `GL43.glDispatchCompute()` | Compute-based compositing |

##### `GlTexture.java` - Texture Management
| OpenGL Call | Purpose |
|-------------|---------|
| `GL11.glGenTextures()` | Create texture |
| `GL11.glBindTexture()` | Bind texture |
| `GL11.glTexImage2D()` | Allocate texture |
| `GL11.glTexParameteri()` | Set parameters |
| `GL30.glGenerateMipmap()` | Generate mipmaps |
| `GL11.glDeleteTextures()` | Delete texture |

##### `ShaderStorageBuffer.java` - SSBO Management
| OpenGL Call | Purpose |
|-------------|---------|
| `GL15.glGenBuffers()` | Create buffer |
| `GL15.glBindBuffer(GL_SHADER_STORAGE_BUFFER, ...)` | Bind SSBO |
| `GL15.glBufferData()` | Allocate storage |
| `GL30.glBindBufferBase()` | Bind to binding point |
| `GL15.glDeleteBuffers()` | Delete buffer |

##### `GlImage.java` - Image Load/Store
| OpenGL Call | Purpose |
|-------------|---------|
| `GL42.glBindImageTexture()` | Bind image unit |
| `GL42.glMemoryBarrier()` | Synchronize image access |

##### `GlSampler.java` - Sampler Objects
| OpenGL Call | Purpose |
|-------------|---------|
| `GL33.glGenSamplers()` | Create sampler |
| `GL33.glSamplerParameteri()` | Set sampler state |
| `GL33.glBindSampler()` | Bind sampler to unit |
| `GL33.glDeleteSamplers()` | Delete sampler |

##### Shadow Rendering (`ShadowRenderer.java`, `ShadowRenderTargets.java`)
| OpenGL Call | Purpose |
|-------------|---------|
| `GL30.glBindFramebuffer()` | Bind shadow FBO |
| `GL11.glViewport()` | Shadow map viewport |
| `GL11.glClear(GL_DEPTH_BUFFER_BIT)` | Clear shadow depth |
| Rendering with shadow camera transformation |

##### Capability Detection
| OpenGL Call | Purpose |
|-------------|---------|
| `GL11.glGetString(GL_VERSION)` | GL version |
| `GL11.glGetInteger(GL_MAX_TEXTURE_IMAGE_UNITS)` | Texture units |
| `GL43.glGetInteger(GL_MAX_COMPUTE_WORK_GROUP_COUNT)` | Compute limits |

**Total Files**: ~60+ files with OpenGL calls

---

### 4. Distant Horizons (`com/seibel/distanthorizons/`)

**Location**: LOD (Level of Detail) rendering mod  
**Purpose**: Render distant terrain beyond normal render distance

#### Philosophy
- **Minimal abstraction** - Direct GL32 calls
- **Capability detection** - Validates GL 3.2+ minimum
- **LOD-specific rendering** - Separate framebuffers/shaders
- **Iris integration** - Can use Iris FBOs when shader packs loaded

#### Files Making OpenGL Calls

##### `GLProxy.java` - GPU Capabilities
| OpenGL Call | Purpose |
|-------------|---------|
| `GL11.glGetString(GL_VERSION)` | Detect GL version |
| `GL11.glGetString(GL_VENDOR)` | GPU vendor |
| `GL11.glGetInteger(GL_MAX_TEXTURE_SIZE)` | Texture limits |

##### `LodRenderer.java` - Main LOD Rendering
| OpenGL Call | Purpose |
|-------------|---------|
| `GL20.glUseProgram()` | Activate LOD shaders |
| `GL30.glBindFramebuffer()` | Bind LOD FBO |
| `GL11.glDrawArrays()` | Draw LOD geometry |
| `GL11.glEnable/glDisable()` | State changes |

##### `DhFramebuffer.java` - LOD Framebuffers
| OpenGL Call | Purpose |
|-------------|---------|
| `GL30.glGenFramebuffers()` | Create FBO |
| `GL30.glBindFramebuffer()` | Bind FBO |
| `GL30.glFramebufferTexture2D()` | Attach textures |
| `GL30.glDrawBuffers()` | Configure outputs |
| `GL11.glReadBuffer()`, `GL11.glDrawBuffer()` | Read/write buffers |

##### `DhColorTexture.java`, `DHDepthTexture.java` - Textures
| OpenGL Call | Purpose |
|-------------|---------|
| `GL11.glGenTextures()` | Create texture |
| `GL11.glBindTexture()` | Bind texture |
| `GL11.glTexImage2D()` | Allocate texture |
| `GL11.glTexParameteri()` | Set parameters |

##### `GLBuffer.java`, `GLVertexBuffer.java` - Buffers
| OpenGL Call | Purpose |
|-------------|---------|
| `GL15.glGenBuffers()` | Create buffer |
| `GL15.glBindBuffer()` | Bind buffer |
| `GL15.glBufferData()` | Upload data |
| `GL15.glDeleteBuffers()` | Delete buffer |

##### `ShaderProgram.java` - LOD Shaders
| OpenGL Call | Purpose |
|-------------|---------|
| `GL20.glCreateProgram()` | Create program |
| `GL20.glCreateShader()` | Create shader |
| `GL20.glShaderSource()` | Set shader source |
| `GL20.glCompileShader()` | Compile shader |
| `GL20.glLinkProgram()` | Link program |
| `GL20.glGetUniformLocation()` | Query uniforms |

##### SSAO & Effects (`SSAORenderer.java`, `FogRenderer.java`)
| OpenGL Call | Purpose |
|-------------|---------|
| `GL20.glUseProgram()` | Effect shaders |
| `GL30.glBindFramebuffer()` | Effect FBO |
| `GL11.glDrawArrays()` | Full-screen effects |

**Total Files**: ~30 files with OpenGL calls

---

### 5. VoxelMap (`net/voxelmap/`)

**Location**: Minimap mod  
**Purpose**: Render minimap overlay

#### OpenGL Usage (Minimal)

##### `CompressibleGLBufferedImage.java`
| OpenGL Call | Purpose |
|-------------|---------|
| `GL11.glGenTextures()` | Create map texture |
| `GL11.glBindTexture()` | Bind texture |
| `GL11.glTexImage2D()` | Upload map image |

**Total Files**: ~1 file with minimal GL calls

---

### 6. Minecraft Core (`net/minecraft/`)

**CRITICAL FINDING**: **ZERO OpenGL calls** in core Minecraft code.

All rendering is abstracted through Blaze3D APIs. This is excellent for the Vulkanic migration plan.

---

## Current Architecture Analysis

### Rendering Call Flow

```
Minecraft Game Logic (net/minecraft/)
    ↓ (Blaze3D API calls only)
Blaze3D Rendering Layer (net/blaze3d/)
    ↓ (OpenGL calls)
OpenGL (via LWJGL)
    ↓
GPU Driver
```

### Third-Party Mod Integration

```
Vanilla Rendering (Blaze3D)
    ↓
Sodium (optimized chunk rendering)
    ↓
Iris (shader pipeline)
    ↓
Distant Horizons (LOD rendering)
```

**Integration Points**:
1. **Iris-DH**: DH can render through Iris FBOs for shader compatibility
2. **Sodium-Iris**: Iris redirects Sodium chunk programs to extended shaders
3. **State Management**: All mods track GL state to minimize redundant calls

### OpenGL API Distribution

| Component | GL Calls | Percentage | Abstraction Level |
|-----------|----------|------------|-------------------|
| **Blaze3D** | ~60 functions | 100% | High (full abstraction) |
| **Sodium** | ~30 functions | 50% | Medium (device wrapper) |
| **Iris** | ~45 functions | 75% | Medium (IrisRenderSystem) |
| **Distant Horizons** | ~25 functions | 42% | Low (direct GL32 calls) |
| **VoxelMap** | ~3 functions | 5% | None (direct calls) |
| **Minecraft Core** | **0 functions** | **0%** | **Perfect (Blaze3D only)** |

### State Management Strategies

| Component | Strategy | Cache |
|-----------|----------|-------|
| **Blaze3D** | `GlStateManager` with full state tracking | Yes |
| **Sodium** | `GlStateTracker` for device state | Yes |
| **Iris** | Delegates to `IrisRenderSystem` | Partial |
| **Distant Horizons** | `GLState` minimal tracking | Basic |

---

## Vulkanic Architecture Plan

### Design Philosophy

Based on the Vulkanic README requirements and current architecture analysis, we propose:

**Core Principles**:
1. ✅ **Abstraction**: Decouple game rendering from specific graphics APIs
2. ✅ **Flexibility**: Support multiple backends (OpenGL, Vulkan)
3. ✅ **Maintainability**: Centralized rendering in `vulkanic` package
4. ⛔ **Enforcement**: NO direct OpenGL calls outside `vulkanic/backends`

### Proposed Architecture

```
Game Code (net/minecraft/)
    ↓
Vulkanic Public API (net/vulkanic/)
    ↓
Backend Selection Layer
    ↓
Backend Implementations (net/vulkanic/backends/)
    ├── OpenGL Backend (initial - wraps/replaces Blaze3D)
    └── Vulkan Backend (future)
    ↓
Graphics API (OpenGL/Vulkan)
    ↓
GPU Driver
```

### Directory Structure

```
net/vulkanic/
├── VulkanicRenderer.java       # Main renderer interface
├── VulkanicDevice.java         # Device abstraction
├── VulkanicCommandBuffer.java  # Command recording
├── VulkanicShader.java         # Shader program abstraction
├── VulkanicBuffer.java         # GPU buffer abstraction
├── VulkanicTexture.java        # Texture abstraction
├── VulkanicFramebuffer.java    # Render target abstraction
├── VulkanicPipeline.java       # Graphics pipeline state
├── backends/
│   ├── BackendFactory.java     # Backend selection/creation
│   ├── opengl/
│   │   ├── OpenGLRenderer.java
│   │   ├── OpenGLDevice.java
│   │   ├── OpenGLCommandBuffer.java
│   │   ├── OpenGLShader.java
│   │   ├── OpenGLBuffer.java
│   │   ├── OpenGLTexture.java
│   │   ├── OpenGLFramebuffer.java
│   │   └── OpenGLPipeline.java
│   └── vulkan/ (future)
│       ├── VulkanRenderer.java
│       └── ...
└── util/
    ├── VertexFormat.java       # Platform-independent vertex formats
    └── ShaderCompiler.java     # Shader compilation abstraction
```

### API Design Examples

#### Example 1: Drawing (Public API)
```java
// ✅ CORRECT: Using Vulkanic API (game code)
VulkanicRenderer renderer = Vulkanic.getRenderer();
VulkanicCommandBuffer cmd = renderer.createCommandBuffer();

cmd.beginRenderPass(framebuffer);
cmd.bindPipeline(shader);
cmd.bindVertexBuffer(vertexBuffer);
cmd.draw(vertexCount);
cmd.endRenderPass();
cmd.submit();

// ❌ WRONG: Direct OpenGL call (violates abstraction)
GL11.glDrawArrays(GL_TRIANGLES, 0, vertexCount);

// ❌ WRONG: Direct Blaze3D call (violates abstraction)
RenderSystem.drawElements(...);
```

#### Example 2: Shader Creation (Public API)
```java
// ✅ CORRECT: Using Vulkanic API
VulkanicShader shader = Vulkanic.createShader()
    .vertexShader(vertexSource)
    .fragmentShader(fragmentSource)
    .compile();

// ❌ WRONG: Direct OpenGL
int shaderId = GL20.glCreateShader(GL_VERTEX_SHADER);
GL20.glShaderSource(shaderId, vertexSource);
GL20.glCompileShader(shaderId);
```

#### Example 3: Backend Implementation (Internal)
```java
// OpenGL backend implementation (net/vulkanic/backends/opengl/)
public class OpenGLRenderer implements VulkanicRenderer {
    @Override
    public void draw(int vertexCount) {
        // ✅ CORRECT: OpenGL call inside backend
        GL11.glDrawArrays(GL_TRIANGLES, 0, vertexCount);
    }
}

// Vulkan backend implementation (net/vulkanic/backends/vulkan/)
public class VulkanRenderer implements VulkanicRenderer {
    @Override
    public void draw(int vertexCount) {
        // ✅ CORRECT: Vulkan call inside backend
        vkCmdDraw(commandBuffer, vertexCount, 1, 0, 0);
    }
}
```

### Backend Selection Mechanism

```java
public class Vulkanic {
    private static VulkanicRenderer renderer;
    
    public static void initialize(BackendType type) {
        switch (type) {
            case OPENGL:
                renderer = new OpenGLRenderer();
                break;
            case VULKAN:
                renderer = new VulkanRenderer();
                break;
            default:
                throw new IllegalArgumentException("Unknown backend: " + type);
        }
    }
    
    public static VulkanicRenderer getRenderer() {
        return renderer;
    }
}
```

Configuration could be:
- **Command-line argument**: `--rendering-backend=vulkan`
- **Config file**: `options.txt` entry
- **Auto-detection**: Fall back to OpenGL if Vulkan unavailable

---

## Migration Strategy

### Phase 1: Create Vulkanic Wrapper (Low Risk)

**Goal**: Establish Vulkanic as a thin wrapper around Blaze3D without breaking anything.

**Steps**:
1. Create `net/vulkanic/` package structure
2. Define public API interfaces matching Blaze3D concepts
3. Implement OpenGL backend as **pass-through to Blaze3D**
4. Add backend selection mechanism (default to OpenGL)
5. **No changes to game code yet** - just infrastructure

**Timeline**: 2-3 weeks  
**Risk**: Very Low (no game code changes)

**Example**:
```java
// OpenGL backend delegates to existing Blaze3D
public class OpenGLRenderer implements VulkanicRenderer {
    @Override
    public void draw(int vertexCount) {
        // Delegate to existing Blaze3D
        RenderSystem.drawArrays(vertexCount);
    }
}
```

### Phase 2: Migrate Third-Party Mods (High Risk)

**Goal**: Migrate Sodium, Iris, Distant Horizons to use Vulkanic APIs.

**Challenges**:
- These mods make **direct OpenGL calls** for performance
- Iris requires advanced GL features (compute, SSBO, image load/store)
- Sodium has highly optimized GL code paths
- Distant Horizons has GL 3.2+ requirements

**Strategy**:
1. Extend Vulkanic API to support advanced features (compute, SSBO, etc.)
2. Create "performance backend" for Sodium with zero-overhead wrappers
3. Provide Iris-specific extensions for shader pack compatibility
4. DH migration to generic Vulkanic buffer/framebuffer APIs

**Timeline**: 3-6 months  
**Risk**: High (performance regression possible, compatibility issues)

### Phase 3: Move Blaze3D Internals to Vulkanic (Medium Risk)

**Goal**: Replace Blaze3D with Vulkanic, moving GL code into OpenGL backend.

**Steps**:
1. Copy `GlStateManager`, `GlDevice`, `GlCommandEncoder` into `vulkanic/backends/opengl/`
2. Refactor to implement Vulkanic interfaces
3. Update Blaze3D to delegate to Vulkanic (legacy compatibility)
4. Eventually deprecate Blaze3D entirely

**Timeline**: 2-4 months  
**Risk**: Medium (potential state management bugs)

### Phase 4: Implement Vulkan Backend (High Complexity)

**Goal**: Add Vulkan backend for modern GPUs.

**Requirements**:
- Vulkan loader (LWJGL Vulkan bindings)
- SPIR-V shader compilation from GLSL
- Descriptor set management (uniforms/textures)
- Command buffer recording
- Swapchain management
- Synchronization (semaphores/fences)

**Steps**:
1. Implement `VulkanDevice`, `VulkanCommandBuffer`, etc.
2. GLSL → SPIR-V shader translation
3. Map OpenGL state to Vulkan pipelines
4. Performance validation vs. OpenGL backend

**Timeline**: 6-12 months  
**Risk**: Very High (complex API, cross-platform issues)

### Phase 5: Testing & Optimization

**Goal**: Ensure stability and performance across backends.

**Testing Matrix**:
- ✅ OpenGL 3.2 (minimum)
- ✅ OpenGL 4.3 (modern)
- ✅ OpenGL 4.6 (ARB_direct_state_access)
- ✅ Vulkan 1.0
- ✅ Vulkan 1.1+
- Platforms: Windows, Linux, macOS (OpenGL/MoltenVK)

**Benchmarks**:
- Frame time comparison (OpenGL vs. Vulkan)
- CPU overhead (driver calls)
- Memory usage (descriptor sets, staging buffers)
- Shader compilation time

**Timeline**: Ongoing  
**Risk**: Medium (performance regressions possible)

---

## Implementation Roadmap

### Milestone 1: Infrastructure Setup (Week 1-2)
- [ ] Create `net/vulkanic/` package
- [ ] Define core interfaces (`VulkanicRenderer`, `VulkanicDevice`, etc.)
- [ ] Set up backend factory pattern
- [ ] Add backend selection configuration
- [ ] Create build scripts for Vulkan dependencies (LWJGL)

### Milestone 2: OpenGL Backend Foundation (Week 3-5)
- [ ] Implement `OpenGLRenderer` as Blaze3D pass-through
- [ ] Implement `OpenGLDevice` wrapping `GlDevice`
- [ ] Implement `OpenGLCommandBuffer` wrapping `GlCommandEncoder`
- [ ] Implement `OpenGLShader` wrapping `GlProgram`
- [ ] Implement `OpenGLBuffer` wrapping `GpuBuffer`
- [ ] Implement `OpenGLTexture` wrapping `GpuTexture`
- [ ] Implement `OpenGLFramebuffer` wrapping `RenderTarget`

### Milestone 3: Testing & Validation (Week 6)
- [ ] Unit tests for Vulkanic API
- [ ] Integration tests with Blaze3D
- [ ] Performance benchmarks (ensure zero overhead)
- [ ] Smoke tests on all platforms

### Milestone 4: Mod Migration Planning (Week 7-8)
- [ ] Audit Sodium GL usage patterns
- [ ] Audit Iris GL usage patterns
- [ ] Audit Distant Horizons GL usage patterns
- [ ] Design mod-specific Vulkanic extensions
- [ ] Create migration guides for mod developers

### Milestone 5: Sodium Migration (Month 3-4)
- [ ] Extend Vulkanic for Sodium's performance needs
- [ ] Migrate `RenderDevice` to `VulkanicDevice`
- [ ] Migrate chunk rendering to Vulkanic APIs
- [ ] Performance validation (ensure no regression)
- [ ] Compatibility testing with Iris

### Milestone 6: Iris Migration (Month 4-5)
- [ ] Add compute shader support to Vulkanic
- [ ] Add SSBO support to Vulkanic
- [ ] Add image load/store to Vulkanic
- [ ] Migrate Iris pipelines to Vulkanic
- [ ] Shader pack compatibility validation

### Milestone 7: Distant Horizons Migration (Month 5-6)
- [ ] Migrate DH framebuffers to Vulkanic
- [ ] Migrate DH buffers to Vulkanic
- [ ] Migrate DH shaders to Vulkanic
- [ ] Iris integration testing

### Milestone 8: Blaze3D Deprecation (Month 7-8)
- [ ] Move Blaze3D internals to OpenGL backend
- [ ] Update game code to use Vulkanic directly
- [ ] Mark Blaze3D as deprecated
- [ ] Final compatibility testing

### Milestone 9: Vulkan Backend (Month 9-15)
- [ ] Set up Vulkan initialization
- [ ] Implement Vulkan device & queue management
- [ ] Implement Vulkan command buffer recording
- [ ] Implement Vulkan pipeline state objects
- [ ] Implement Vulkan descriptor sets
- [ ] Implement GLSL → SPIR-V compilation
- [ ] Implement Vulkan swapchain
- [ ] Implement Vulkan synchronization
- [ ] Performance optimization
- [ ] Cross-platform validation

### Milestone 10: Production Release (Month 16)
- [ ] Backend selection UI in game settings
- [ ] Auto-fallback from Vulkan to OpenGL
- [ ] Comprehensive documentation
- [ ] Performance comparison documentation
- [ ] Release announcement

---

## Critical Migration Rules

### ⛔ DO NOT (Violations of Abstraction)

1. **DO NOT** make direct OpenGL calls outside `net/vulkanic/backends/opengl/`
2. **DO NOT** make direct Vulkan calls outside `net/vulkanic/backends/vulkan/`
3. **DO NOT** import classes from `net/vulkanic/backends/` in game code
4. **DO NOT** bypass Vulkanic for "performance" without benchmarking
5. **DO NOT** assume OpenGL semantics in public Vulkanic API

### ✅ DO (Correct Practices)

1. **DO** use only public Vulkanic API classes for all rendering
2. **DO** implement new rendering features as Vulkanic extensions
3. **DO** ensure all backends implement same behavior
4. **DO** write backend-agnostic code in game logic
5. **DO** performance test both backends equally

### Code Review Checklist

- [ ] No OpenGL imports in `net/minecraft/`
- [ ] No Vulkan imports in `net/minecraft/`
- [ ] No backend imports outside `net/vulkanic/`
- [ ] All rendering uses `Vulkanic.*` APIs
- [ ] New features available in **all** backends
- [ ] Backend-specific code **only** in `backends/`
- [ ] Documentation updated for new APIs

---

## Technical Considerations

### OpenGL → Vulkan Mapping Challenges

| OpenGL Concept | Vulkan Equivalent | Complexity |
|----------------|-------------------|------------|
| State machine | Pipeline state objects | High |
| Immediate context | Command buffers | Medium |
| Automatic sync | Manual barriers | High |
| Driver-managed memory | Explicit allocation | High |
| GLSL shaders | SPIR-V shaders | Medium |
| Texture units | Descriptor sets | High |
| glDrawElements | vkCmdDrawIndexed | Low |
| Framebuffer binding | Render passes | Medium |

### Performance Considerations

**OpenGL Backend**:
- ✅ Zero overhead vs. current Blaze3D (if done correctly)
- ✅ Driver handles optimization
- ❌ State change overhead
- ❌ Driver CPU overhead

**Vulkan Backend**:
- ✅ Explicit control over resources
- ✅ Multi-threaded command recording
- ✅ Lower CPU overhead
- ❌ More complex code
- ❌ More memory management responsibility

### Cross-Platform Compatibility

| Platform | OpenGL Support | Vulkan Support | Notes |
|----------|----------------|----------------|-------|
| **Windows** | ✅ Native | ✅ Native | Best support |
| **Linux** | ✅ Native | ✅ Native | Excellent support |
| **macOS** | ✅ 4.1 (deprecated) | ⚠️ MoltenVK | OpenGL deprecated, MoltenVK recommended |

**macOS Strategy**: Prioritize Vulkan (via MoltenVK), maintain OpenGL for compatibility.

---

## Appendix: Complete File Inventory

### Files with OpenGL Calls

#### Blaze3D (net/blaze3d/)
1. `opengl/GlStateManager.java` - State management (60+ GL functions)
2. `opengl/GlDevice.java` - Device initialization
3. `opengl/GlCommandEncoder.java` - Command encoding
4. `opengl/GlProgram.java` - Shader programs
5. `opengl/DirectStateAccess.java` - Modern/legacy GL paths
6. `opengl/GlDebug.java` - Debug output
7. `opengl/GlDebugLabel.java` - Object labeling
8. `opengl/VertexArrayCache.java` - VAO pooling
9. `opengl/BufferStorage.java` - Persistent mapping
10. `systems/TimerQuery.java` - GPU timing

#### Sodium (net/sodium/)
11. `client/gl/device/RenderDevice.java`
12. `client/gl/buffer/GlBuffer.java`
13. `client/gl/buffer/GlBufferUsage.java`
14. `client/gl/buffer/GlBufferTarget.java`
15. `client/gl/buffer/GlBufferStorageFlags.java`
16. `client/gl/buffer/GlBufferMapFlags.java`
17. `client/gl/array/GlVertexArray.java`
18. `client/gl/shader/GlProgram.java`
19. `client/gl/shader/GlShader.java`
20. `client/gl/shader/ShaderType.java`
21. `client/gl/shader/ShaderWorkarounds.java`
22. `client/gl/shader/uniform/*.java` (multiple files)
23. `client/gl/tessellation/GlPrimitiveType.java`
24. `client/gl/tessellation/GlIndexType.java`
25. `client/gl/sync/GlFence.java`
26. `client/gl/attribute/GlVertexAttributeFormat.java`
27. `client/gl/functions/BufferStorageFunctions.java`
28. `client/compatibility/environment/GlContextInfo.java`
29. `client/compatibility/workarounds/nvidia/NvidiaWorkarounds.java`
30. `fabric/SodiumGpuSyncHelper.java`
31. `client/gui/SodiumGameOptionPages.java`

#### Iris (net/irisshaders/)
32. `gl/IrisRenderSystem.java`
33. `gl/program/Program.java`
34. `gl/program/ComputeProgram.java`
35. `gl/program/ProgramUniforms.java`
36. `gl/program/ProgramSamplers.java`
37. `gl/shader/GlShader.java`
38. `gl/shader/ProgramCreator.java`
39. `gl/shader/ShaderType.java`
40. `gl/shader/ShaderWorkarounds.java`
41. `gl/shader/StandardMacros.java`
42. `gl/framebuffer/GlFramebuffer.java`
43. `gl/texture/GlTexture.java`
44. `gl/texture/TextureType.java`
45. `gl/texture/PixelFormat.java`
46. `gl/texture/PixelType.java`
47. `gl/texture/DepthBufferFormat.java`
48. `gl/texture/DepthCopyStrategy.java`
49. `gl/texture/InternalTextureFormat.java`
50. `gl/texture/TextureUploadHelper.java`
51. `gl/buffer/ShaderStorageBuffer.java`
52. `gl/buffer/ShaderStorageBufferHolder.java`
53. `gl/image/GlImage.java`
54. `gl/image/ImageBinding.java`
55. `gl/image/ImageClearPass.java`
56. `gl/sampler/GlSampler.java`
57. `gl/sampler/SamplerLimits.java`
58. `gl/blending/AlphaTestFunction.java`
59. `gl/blending/BlendModeFunction.java`
60. `gl/uniform/MatrixUniform.java`
61. `gl/uniform/MatrixFromFloatArrayUniform.java`
62. `pipeline/*.java` (multiple files)
63. `shadows/*.java` (shadow rendering)
64. `targets/*.java` (render targets)
65. `uniforms/*.java` (custom uniforms)
66. `compat/dh/*.java` (DH integration)
67. `pathways/colorspace/*.java`
68. `pbr/util/TextureManipulationUtil.java`
69. `pbr/TextureInfoCache.java`
70. `Iris.java`
71. `GLDebug.java`

#### Distant Horizons (com/seibel/distanthorizons/)
72. `core/render/glObject/GLProxy.java`
73. `core/render/glObject/GLState.java`
74. `core/render/glObject/GLEnums.java`
75. `core/render/glObject/buffer/GLBuffer.java`
76. `core/render/glObject/buffer/GLVertexBuffer.java`
77. `core/render/glObject/buffer/GLElementBuffer.java`
78. `core/render/glObject/buffer/QuadElementBuffer.java`
79. `core/render/glObject/texture/DhFramebuffer.java`
80. `core/render/glObject/texture/DhColorTexture.java`
81. `core/render/glObject/texture/DHDepthTexture.java`
82. `core/render/glObject/texture/EDhPixelFormat.java`
83. `core/render/glObject/texture/EDhPixelType.java`
84. `core/render/glObject/texture/EDhInternalTextureFormat.java`
85. `core/render/glObject/texture/EDhDepthBufferFormat.java`
86. `core/render/glObject/shader/Shader.java`
87. `core/render/glObject/shader/ShaderProgram.java`
88. `core/render/glObject/vertexAttribute/*.java`
89. `core/render/renderer/LodRenderer.java`
90. `core/render/renderer/TestRenderer.java`
91. `core/render/renderer/ScreenQuad.java`
92. `core/render/renderer/SSAORenderer.java`
93. `core/render/renderer/FogRenderer.java`
94. `core/render/renderer/DebugRenderer.java`
95. `core/render/renderer/DhFadeRenderer.java`
96. `core/render/renderer/VanillaFadeRenderer.java`
97. `core/render/renderer/generic/GenericObjectRenderer.java`
98. `core/render/renderer/generic/RenderableBoxGroup.java`
99. `core/render/renderer/shaders/*.java` (all shader classes)
100. `core/render/DhApiRenderProxy.java`
101. `core/render/vertexFormat/LodVertexFormatElement.java`
102. `common/wrappers/minecraft/MinecraftGLWrapper.java`
103. `common/wrappers/misc/LightMapWrapper.java`
104. `wrapperInterfaces/minecraft/IMinecraftGLWrapper.java`

#### VoxelMap (net/voxelmap/)
105. `persistent/CompressibleGLBufferedImage.java`

**Total**: ~105 files contain OpenGL calls

---

## Summary Statistics

| Metric | Value |
|--------|-------|
| **Total files with GL calls** | ~105 |
| **GL calls in Minecraft core** | **0** ✅ |
| **GL calls in Blaze3D** | ~60 functions |
| **GL calls in Sodium** | ~30 functions |
| **GL calls in Iris** | ~45 functions |
| **GL calls in Distant Horizons** | ~25 functions |
| **GL calls in VoxelMap** | ~3 functions |
| **Total unique GL functions** | ~80+ |
| **Lines of GL-related code** | ~15,000+ |

---

## Conclusion

### Key Takeaways

1. ✅ **Excellent foundation**: Minecraft core makes ZERO direct OpenGL calls
2. ✅ **Clear abstraction**: Blaze3D already provides rendering abstraction
3. ⚠️ **Third-party challenge**: Mods make extensive direct GL calls
4. ✅ **Phased approach**: Can migrate incrementally without breaking changes
5. ✅ **Long-term vision**: Vulkan backend achievable with proper planning

### Recommended Next Steps

1. **Immediate**: Create Vulkanic infrastructure (Milestone 1-2)
2. **Short-term**: Test with Blaze3D wrapper (Milestone 3)
3. **Medium-term**: Migrate mods (Milestone 4-7)
4. **Long-term**: Vulkan backend (Milestone 9-10)

### Success Criteria

- ✅ Zero OpenGL calls outside `vulkanic/backends/opengl/`
- ✅ All rendering through Vulkanic public API
- ✅ OpenGL and Vulkan backends functionally equivalent
- ✅ Performance parity or improvement vs. current Blaze3D
- ✅ Cross-platform compatibility maintained
- ✅ Shader pack compatibility preserved (Iris)
- ✅ LOD rendering compatibility preserved (DH)

---

**Document Version**: 1.0  
**Last Updated**: 2026-02-04  
**Author**: MattMC Development Team  
**Status**: Research Complete - Ready for Implementation

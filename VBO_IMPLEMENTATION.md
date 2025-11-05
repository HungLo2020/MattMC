# VBO/VAO Implementation Guide

## Overview

This document describes the VBO (Vertex Buffer Object) and VAO (Vertex Array Object) rendering optimization that has been implemented for MattMC chunk rendering.

## What Changed?

Previously, chunk rendering used OpenGL display lists:
- Display lists precompile geometry but still use immediate mode internally
- Each chunk rebuild requires compilation on the render thread
- Cannot separate CPU mesh building from GPU upload

Now, chunk rendering supports VBO/VAO:
- Mesh geometry is built on worker threads (CPU-side)
- Only small GPU uploads happen on render thread
- Single `glDrawElements` call per chunk
- Upload budget prevents frame hitches

## Architecture

### CPU-Side (Worker Threads)

1. **BlockFaceCollector** collects visible block faces with culling
2. **MeshBuilder** converts faces into vertex/index arrays
3. **ChunkMeshBuffer** stores the CPU-side mesh data

```
Vertex format (9 floats per vertex, interleaved):
- Position: x, y, z (3 floats)
- TexCoord: u, v (2 floats) 
- Color: r, g, b, a (4 floats)
```

### GPU-Side (Render Thread)

1. **ChunkVAO** manages VBO and EBO (Element Buffer Object)
2. Single `glDrawElements` call renders entire chunk
3. Fixed-function pipeline compatibility mode

## Usage

### Enable/Disable VBO Rendering

```java
// Display list rendering is used by default (supports textures)
// Enable VBO rendering for better performance (no texture support yet)
chunkRenderer.setUseVBORendering(true);

// Disable VBO rendering (use display lists with texture support)
chunkRenderer.setUseVBORendering(false);
```

### Upload Budget

Mesh uploads are throttled to prevent frame hitches:
```java
// Maximum mesh uploads per frame (default: 2)
AsyncChunkLoader.MAX_MESH_UPLOADS_PER_FRAME = 2;
```

## Current Limitations

### No Texture Support in VBO Mode

**Display lists (default mode) fully support textures.**

VBO rendering currently uses fallback colors only:
- Each block renders with its fallback color
- Textures are disabled during VBO rendering
- Display list path (default) supports textures normally

**Why?** The original code groups faces by texture and renders each group separately. VBO rendering builds a single mesh for the entire chunk, so we can't switch textures mid-render.

**Solution:** Implement a texture atlas (future enhancement)

**Current Status:** Display lists are used by default to maintain texture support.

### Fixed-Function Pipeline

Currently uses OpenGL fixed-function pipeline for compatibility:
- `glEnableClientState(GL_VERTEX_ARRAY)`
- `glEnableClientState(GL_COLOR_ARRAY)`
- `glVertexPointer`, `glColorPointer`

**Future:** Migrate to core OpenGL with shaders

## Performance Benefits

### Before (Display Lists)
- Chunks compiled on render thread (CPU work)
- ~50,000 triangles per chunk
- ~7 texture groups × glBegin/glEnd per chunk
- Compilation hitches during chunk loading

### After (VBO)
- Mesh building on worker threads (parallelized)
- ~50,000 triangles per chunk (same geometry)
- 1 glDrawElements per chunk
- Small, bounded GPU uploads on render thread
- No compilation hitches

## Code Structure

```
src/main/java/mattmc/client/renderer/chunk/
├── ChunkMeshBuffer.java    # CPU-side mesh data
├── ChunkVAO.java           # GPU-side VBO/EBO manager
├── MeshBuilder.java        # Face → vertex/index converter
├── ChunkRenderer.java      # Dual-path rendering
└── ChunkMeshData.java      # Old display list mesh (kept for compatibility)

src/main/java/mattmc/world/level/chunk/
└── AsyncChunkLoader.java   # Builds both mesh formats in parallel
```

## Migration Path

Both rendering paths are maintained for smooth migration:

1. **Current State**: Display lists used by default (full texture support), VBO available as optional optimization
2. **Testing Phase**: Users can opt-in to VBO rendering to test performance (no textures)
3. **Future**: Once texture atlas is implemented, VBO can become the default

## Future Enhancements

### High Priority
- [ ] Implement texture atlas for multi-texture VBO support
- [ ] Add metrics/logging to track VBO usage

### Medium Priority  
- [ ] Separate upload budgets for display lists vs VBOs
- [ ] Cache texture state instead of querying per frame
- [ ] Migrate to core OpenGL VAOs with shaders

### Low Priority
- [ ] Implement greedy meshing (combine adjacent faces)
- [ ] Occlusion culling between chunks
- [ ] Instanced rendering for repeated structures

## Debugging

### Check Rendering Mode
```java
boolean usingVBO = chunkRenderer.isUsingVBORendering();
System.out.println("VBO rendering: " + usingVBO);
```

### Common Issues

**Chunks not appearing:**
- VAOs are uploaded asynchronously
- Check that mesh buffers are being built: `collectCompletedMeshBuffers()`
- Verify upload budget isn't too low

**Performance regression:**
- VBO path may be slower initially (no textures = ugly)
- Compare with display lists: `setUseVBORendering(false)`
- Check upload budget (too high = hitching)

**Missing features:**
- Textures: Not supported yet in VBO path
- Overlays: Not supported yet in VBO path
- Use display list path if these are critical

## References

- Original issue: [Problem statement about VBO/VAO optimization]
- OpenGL VBO tutorial: https://www.opengl.org/wiki/Vertex_Specification
- Minecraft-like chunk rendering: Uses similar approach with texture atlases

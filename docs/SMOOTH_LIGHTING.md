# Smooth Lighting System

## Overview

MattMC implements mesh-time light sampling (smooth lighting) to create smooth, realistic lighting across block faces. This document describes the implementation, vertex format, and technical details of the smooth lighting system.

## Features

- **Per-vertex light sampling**: Each vertex samples light from 8 nearby voxels
- **Smooth light gradients**: Light values are interpolated across block faces
- **Ambient occlusion (AO)**: Corners and edges darken based on nearby solid blocks
- **Runtime toggle**: Can be enabled/disabled via `smooth_lighting` setting
- **Cross-chunk support**: Properly handles lighting across chunk boundaries

## Vertex Format

### Extended Vertex Attributes (12 floats per vertex)

| Offset | Attribute    | Type  | Description                          |
|--------|-------------|-------|--------------------------------------|
| 0-2    | Position    | vec3  | Vertex position (x, y, z)            |
| 3-4    | TexCoord    | vec2  | Texture coordinates (u, v)           |
| 5-8    | Color       | vec4  | Vertex color with light applied (r, g, b, a) |
| 9      | SkyLight    | float | Sky light level (0-15), stored for future use |
| 10     | BlockLight  | float | Block light level (0-15), stored for future use |
| 11     | AO          | float | Ambient occlusion (0-3), stored for future use |

**Total**: 12 floats × 4 bytes = 48 bytes per vertex

### Legacy Format (for comparison)

The previous format used 9 floats per vertex (36 bytes):
- Position (3 floats)
- TexCoord (2 floats)
- Color (4 floats)

The new format adds 3 floats for light data, increasing memory usage by 33%.

## Light Sampling

### Algorithm

For each vertex:

1. **Determine vertex position** based on face and corner index
2. **Sample nearby voxels**: Check 4-8 blocks around the vertex
3. **Average light values**: Compute mean sky and block light
4. **Calculate AO**: Use 3-block corner rule
5. **Apply to color**: Modulate vertex color by light and AO

### Light Calculation

```
lightFactor = max(skyLight, blockLight) / 15.0
aoFactor = [1.0, 0.8, 0.6, 0.45][aoCount]
finalColor = baseColor * lightFactor * aoFactor
```

### Ambient Occlusion (3-Block Corner Rule)

For each vertex corner, check 3 adjacent blocks:
- **side1**: First adjacent block
- **side2**: Second adjacent block  
- **corner**: Diagonal corner block

AO count calculation:
- If both sides solid: **3** (maximum darkening)
- If one side + corner solid: **2**
- If one side solid (no corner): **1**
- If all air: **0** (no darkening)

AO factors:
- 0 occluders → 1.0 (full brightness)
- 1 occluder  → 0.8 (slight darkening)
- 2 occluders → 0.6 (medium darkening)
- 3 occluders → 0.45 (strong darkening)

## Implementation Components

### VertexLightSampler

**Location**: `mattmc.client.renderer.chunk.VertexLightSampler`

Responsible for:
- Sampling light from nearby blocks
- Calculating ambient occlusion
- Handling chunk boundaries via `BlockAccessor` interface

Key methods:
- `sampleVertex(chunk, x, y, z, normal, cornerIndex)` - Sample light for a vertex
- `calculateAO(...)` - Compute AO using 3-block corner rule

### MeshBuilder

**Location**: `mattmc.client.renderer.chunk.MeshBuilder`

Responsible for:
- Building mesh vertex data from collected faces
- Applying light sampling when enabled
- Modulating vertex colors by light and AO

Key methods:
- `setLightSampler(sampler)` - Enable smooth lighting
- `sampleVertexLight(face, normal, cornerIndex)` - Helper to sample light
- `addVertex(..., skyLight, blockLight, ao)` - Add vertex with light modulation

### BlockAccessor

**Location**: `mattmc.world.level.chunk.AsyncChunkLoader.ChunkBlockAccessor`

Responsible for:
- Accessing blocks across chunk boundaries
- Reading light data from chunks
- Providing default values when out of bounds

### Settings

**Location**: `mattmc.client.settings.OptionsManager`

Setting: `smooth_lighting` (boolean)
- Default: `true`
- Saved in: `Options.txt`
- Methods: `isSmoothLightingEnabled()`, `setSmoothLightingEnabled(boolean)`, `toggleSmoothLighting()`

## Performance Considerations

### Memory Impact

- **Vertex size**: +12 bytes per vertex (33% increase)
- **Typical chunk**: ~10,000 vertices → +120 KB per chunk
- **256 loaded chunks**: +30 MB total

### CPU Impact

- Light sampling adds ~4-8 block lookups per vertex
- AO calculation adds 3 block lookups per vertex
- Caching in mesh builder eliminates per-frame overhead
- Meshing happens asynchronously on worker threads

### Optimization Opportunities

1. **Pack light data into fewer bytes**: Store light as bytes instead of floats
2. **Simplified sampling**: Use fewer sample points (4 instead of 8)
3. **AO-only mode**: Skip light sampling, only calculate AO
4. **Chunk-level caching**: Cache light sampler per chunk

## Usage

### Enable/Disable

Via options file (`Options.txt`):
```
smooth_lighting=true
```

Via code:
```java
OptionsManager.setSmoothLightingEnabled(true);
```

### Testing

The system can be tested by:
1. Toggle smooth lighting on/off in settings
2. Compare visual appearance of block faces
3. Check for seams at chunk boundaries
4. Verify AO darkening in corners and edges

### Expected Visual Results

**With smooth lighting OFF**:
- Flat shading on each face
- Hard transitions at block boundaries
- Uniform brightness across faces

**With smooth lighting ON**:
- Smooth gradients across faces
- Gradual transitions between blocks
- Darkening in corners and edges (AO)
- More realistic, Minecraft-like appearance

## Limitations

### Current Limitations

1. **Cross-chunk light queries**: Currently return defaults for out-of-bounds queries
2. **No shader support**: Light is baked into vertex colors (fixed-function pipeline)
3. **Memory overhead**: Stores light data that isn't currently used by GPU

### Future Improvements

1. **Cross-chunk light access**: Implement proper neighbor chunk queries
2. **Shader-based lighting**: Use GLSL shaders for more advanced lighting
3. **Compressed light data**: Pack light into bytes instead of floats
4. **Dynamic relighting**: Update smooth lighting when blocks change
5. **Configurable AO factors**: Allow users to customize AO darkness

## Technical Notes

### Vertex Corner Indices

Each face has 4 corners, indexed 0-3:

**Top face (Y+)**:
```
  0---3
  |   |
  1---2
```

**Bottom face (Y-)**:
```
  0---1
  |   |
  3---2
```

Similar patterns for North, South, East, West faces following right-hand winding.

### Light Storage

Light is stored per-voxel in chunks:
- **Format**: 1 byte per block
- **Sky light**: High nibble (bits 4-7)
- **Block light**: Low nibble (bits 0-3)
- **Range**: 0-15 for each channel
- **Location**: `LevelChunk.lightSections[]`

### Coordinate Systems

- **World coordinates**: Absolute position in world
- **Chunk coordinates**: Chunk position (chunkX, chunkZ)
- **Chunk-local coordinates**: Block position within chunk (0-15, 0-383, 0-15)
- **Vertex coordinates**: Float position for rendering

## Related Documentation

- [CHUNK_SYSTEM.md](CHUNK_SYSTEM.md) - Chunk architecture and rendering
- [WORLD_SAVE_FORMAT.md](WORLD_SAVE_FORMAT.md) - Light storage format
- README.md - Project overview

## References

- Minecraft smooth lighting implementation
- Ambient occlusion techniques in voxel engines
- Per-vertex lighting in OpenGL fixed-function pipeline

# 3D Light Grid Implementation

## Overview

This document describes the implementation of a 3D light propagation system for MattMC, similar to Minecraft's lighting engine. The system uses a BFS (Breadth-First Search) algorithm to propagate both skylight and block light throughout the voxel world.

## System Architecture

### Light Storage

Light data is stored in `LevelChunk` as byte arrays (`lightData[x][y][z]`):
- **High nibble (4 bits)**: Skylight (0-15)
- **Low nibble (4 bits)**: Block light (0-15)
- **Lazy initialization**: Arrays are only created when first used to save memory

### Light Types

1. **Skylight (0-15)**
   - Represents natural light from the sky
   - Propagates from top-down through air columns
   - Special behavior: Does NOT attenuate when going straight down through air blocks
   - Blocked by opaque blocks

2. **Block Light (0-15)**
   - Represents light emitted by blocks (e.g., torches)
   - Torches emit light level 14
   - Attenuates by 1 per block in all directions
   - Blocked by opaque blocks

## Light Propagation Algorithm

### Initialization

1. **Skylight Initialization** (`initializeSkylight`)
   - Scans each column from top to bottom
   - Finds the topmost non-air block
   - Sets skylight to 15 (max) for the topmost air block above it
   - BFS propagation handles the rest

2. **Block Light Initialization** (`initializeBlockLight`)
   - Scans all blocks in the chunk
   - For each light-emitting block (e.g., torch with emission=14):
     - Sets block light to the emission level
     - Adds to propagation queue

### BFS Propagation

The `propagateLight` method uses a queue-based BFS algorithm:

```
while queue is not empty:
    node = queue.poll()
    
    for each of 6 neighbors (up, down, north, south, west, east):
        calculate neighbor_light:
            if skylight AND going down AND light==15:
                neighbor_light = 15  // Special case: no attenuation
            else:
                neighbor_light = current_light - 1
        
        if neighbor_light > current_neighbor_light:
            set neighbor light = neighbor_light
            add neighbor to queue
```

### Cross-Chunk Propagation

The system supports light propagating across chunk boundaries:
- Helper methods (`getBlockAt`, `getLightAt`, `setLightAt`) handle coordinate translation
- If a neighbor chunk doesn't exist, propagation stops at the boundary
- Returns `false` from `setLightAt` to prevent infinite re-queuing

### Block Updates

When a block is placed or broken (`Level.setBlock`):
1. Calls `LightEngine.updateLightAt` with the changed position
2. Clears light at that position
3. Recalculates:
   - If new block emits light, sets block light and propagates
   - If new block is air, checks for skylight from above
4. BFS propagates the changes to neighbors

## Integration Points

### 1. Chunk Generation (`AsyncChunkLoader.generateChunk`)
```java
// After terrain generation:
LightEngine.initializeChunkLighting(chunk, chunkAccess);
```

### 2. Block Changes (`Level.setBlock`)
```java
// After setting block:
if (oldBlock != block) {
    updateLightAtPosition(worldX, chunkY, worldZ);
}
```

### 3. Mesh Building (`MeshBuilder.sampleVertexLight`)
```java
// Sample light for each vertex:
int skyLight = face.chunk.getSkyLight(face.cx, face.cy, face.cz);
int blockLight = face.chunk.getBlockLight(face.cx, face.cy, face.cz);
return new float[] {(float)skyLight, (float)blockLight, ao};
```

## Expected In-Game Results

### Visual Behavior

1. **Exposed Areas**
   - Open terrain will be fully lit (skylight level 15)
   - Clear day-night distinction (skylight affects brightness)

2. **Caves and Enclosed Spaces**
   - Completely dark without light sources (skylight=0, blockLight=0)
   - Creates natural darkness in underground areas

3. **Torches**
   - Emit warm light (level 14) that propagates outward
   - Light attenuates smoothly: 14 → 13 → 12 → ... → 1 → 0
   - Reaches up to 14 blocks away from the source

4. **Mixed Lighting**
   - Areas can have both skylight and block light
   - Final brightness = max(skylight, blockLight)
   - Creates interesting lighting gradients

### Example Scenarios

**Scenario 1: Underground Tunnel**
```
Surface (y=70): Skylight=15, bright
Tunnel entrance (y=65): Skylight=15, still bright
Tunnel deeper (y=60): Skylight=0, dark (needs torches)
Torch placed (y=60): BlockLight=14 at torch, 13 nearby, gradually fading
```

**Scenario 2: Building Interior**
```
Roof blocks skylight
Interior is dark (skylight=0)
Place torches every ~10 blocks for illumination
Light spreads evenly from each torch
```

**Scenario 3: Cave System**
```
Cave ceiling blocks skylight
All areas naturally dark
Torches create pools of light
Unexplored areas remain pitch black
```

### Performance Characteristics

- **Chunk Generation**: O(WIDTH × HEIGHT × DEPTH) for initialization + O(V + E) for BFS
- **Block Update**: O(L³) where L is light level (max 15, so bounded)
- **Memory**: 1 byte per block (2 nibbles for sky + block light)
- **Cross-Chunk**: Only propagates to loaded neighbors

## Testing

Comprehensive tests verify:
- ✓ Skylight propagates down through air columns
- ✓ Opaque blocks stop skylight vertically
- ✓ Torches emit correct light level (14)
- ✓ Block light propagates in all 6 directions
- ✓ Light attenuates correctly (1 per block)
- ✓ Opaque walls completely block light transmission

## Future Enhancements

Potential improvements:
1. **Light Removal**: Currently uses simplified update; could implement proper flood-fill removal
2. **Colored Light**: Support RGB values instead of single intensity
3. **Smooth Lighting**: Per-vertex interpolation (already has AO slot in vertex format)
4. **Day/Night Cycle**: Modulate skylight intensity based on time
5. **Weather**: Rain/clouds could reduce skylight
6. **Emissive Materials**: More blocks with light emission (lava, glowstone, etc.)

## Verification Checklist

To verify the implementation works correctly in-game:

- [ ] Open terrain is bright during the day
- [ ] Caves are completely dark
- [ ] Placing a torch lights up the surrounding area
- [ ] Breaking a torch makes the area dark again
- [ ] Light doesn't leak through solid walls
- [ ] Enclosed buildings are dark inside
- [ ] Light smoothly fades with distance
- [ ] No performance issues during chunk generation
- [ ] No visual glitches or flickering

## Technical Notes

- Skylight special case prevents performance issues with tall air columns
- Cross-chunk boundary handling prevents infinite loops
- Lazy light array initialization reduces memory usage
- BFS ensures correct flood-fill behavior
- Light values stored in vertex data for GPU-side shading

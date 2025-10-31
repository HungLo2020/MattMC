# Chunk-Based Voxel Rendering System

## Overview
This implementation provides a Minecraft-like chunk-based voxel rendering system for MattMC. The system has been integrated into the DevplayScreen for testing and development.

## Architecture

### Chunk System
The chunk system follows Minecraft 1.18+ specifications:
- **Dimensions**: 16 blocks (X) × 384 blocks (Y) × 16 blocks (Z)
- **Y-Coordinate Range**: -64 to 319 (384 blocks total)
- **Storage**: Full 3D array for simplicity (can be optimized with sections later)

### Block System
- **Block Class**: Simple wrapper around BlockType
- **BlockType Enum**: Defines block properties (color, solidity)
  - AIR: Transparent, non-solid
  - GRASS: Green, solid (surface block)
  - DIRT: Brown, solid (subsurface)
  - STONE: Gray, solid (deep underground)

### Terrain Generation
- **Surface Level**: y=64 (configurable)
- **Flat Terrain**: Generates horizontal layers from bedrock to surface
- **Layer Structure**:
  - y=64: Grass (1 layer, 256 blocks)
  - y=61-63: Dirt (3 layers, 768 blocks)
  - y=-64 to y=60: Stone (125 layers, 32,000 blocks)
  - Total solid blocks: 33,024

### Rendering Optimizations
1. **Face Culling**: Only renders block faces adjacent to air
2. **Air Block Skipping**: Skips rendering of air blocks entirely
3. **Static AIR Instance**: Reuses single AIR block to avoid allocations
4. **Depth Testing**: Proper 3D rendering with z-buffer
5. **Backface Culling**: OpenGL backface culling enabled

### Lighting/Shading
- **Directional Shading**: Different brightness for each face
  - Top faces: 100% brightness
  - Bottom faces: 50% brightness
  - North/South faces: 80% brightness
  - East/West faces: 60% brightness

## Controls

### Camera Movement
- **W**: Move forward (decrease Z)
- **S**: Move backward (increase Z)
- **A**: Move left (decrease X)
- **D**: Move right (increase X)
- **Space**: Move up (increase Y)
- **Left Shift**: Move down (decrease Y)

### Camera Rotation
- **Left Arrow**: Rotate left (decrease yaw)
- **Right Arrow**: Rotate right (increase yaw)
- **Up Arrow**: Look up (decrease pitch)
- **Down Arrow**: Look down (increase pitch)

### Other
- **ESC**: Return to Singleplayer screen

## Usage

### Running the DevplayScreen
1. Start MattMC
2. Navigate to Singleplayer screen
3. Select "DevPlay" option
4. View the rendered chunk with camera controls

### Creating and Rendering Chunks
```java
// Create a chunk at world position (0, 0)
Chunk chunk = new Chunk(0, 0);

// Generate flat terrain at y=64
chunk.generateFlatTerrain(64);

// Get a specific block
Block block = chunk.getBlock(x, y, z);

// Set a specific block
chunk.setBlock(x, y, z, new Block(BlockType.GRASS));
```

### Coordinate Systems
- **World Coordinates**: Absolute position in the world (y=-64 to y=319)
- **Chunk Coordinates**: Position within a chunk (x=0-15, y=0-383, z=0-15)

Conversion methods:
```java
int worldY = Chunk.chunkYToWorldY(chunkY);  // chunkY + MIN_Y
int chunkY = Chunk.worldYToChunkY(worldY);  // worldY - MIN_Y
```

## Performance Considerations

### Current Implementation
- Renders a single 16×16×384 chunk
- Face culling reduces rendering to only visible faces
- With flat terrain at y=64: ~33,024 solid blocks, but many internal faces are culled

### Potential Optimizations for Future
1. **Chunk Sections**: Divide chunks into 16×16×16 sections (like Minecraft)
2. **Mesh Generation**: Pre-build vertex buffers instead of immediate mode
3. **Frustum Culling**: Don't render chunks outside camera view
4. **Multiple Chunks**: Implement chunk loading/unloading system
5. **VBOs/VAOs**: Use modern OpenGL for better performance
6. **Greedy Meshing**: Combine adjacent faces of same block type

## File Structure
```
src/main/java/MattMC/
├── world/
│   ├── Block.java          - Block wrapper class
│   ├── BlockType.java      - Block type definitions
│   └── Chunk.java          - Chunk storage and generation
└── screens/
    └── DevplayScreen.java  - Rendering and camera controls
```

## Comparison to Minecraft

| Feature | MattMC | Minecraft |
|---------|---------|-----------|
| Chunk Width | 16 blocks | 16 blocks |
| Chunk Depth | 16 blocks | 16 blocks |
| Chunk Height | 384 blocks | 384 blocks (1.18+) |
| Y Range | -64 to 319 | -64 to 319 (1.18+) |
| Face Culling | ✓ | ✓ |
| Chunk Sections | ✗ (future) | ✓ (16×16×16) |
| Block IDs | Enum | Numeric IDs |
| Rendering | Immediate mode | VBOs/VAOs |

## Future Enhancements
- [ ] Multiple chunk rendering
- [ ] Chunk loading/unloading
- [ ] Procedural terrain generation (noise-based)
- [ ] Block breaking/placing
- [ ] Different biomes
- [ ] Cave systems
- [ ] Lighting system
- [ ] Texture mapping
- [ ] Modern OpenGL (VBOs/VAOs/shaders)

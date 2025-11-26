# MattMC vs Minecraft Java Edition: Architecture Comparison

This document provides a comprehensive comparison of MattMC's architecture against Minecraft Java Edition, highlighting similarities, differences, and MattMC-specific innovations.

---

## Table of Contents

1. [Executive Summary](#executive-summary)
2. [World Structure](#world-structure)
3. [Chunk System](#chunk-system)
4. [Block System](#block-system)
5. [Lighting System](#lighting-system)
6. [Rendering Architecture](#rendering-architecture)
7. [Physics & Collision](#physics--collision)
8. [Entity System](#entity-system)
9. [Storage & Persistence](#storage--persistence)
10. [Resource System](#resource-system)
11. [MattMC Innovations](#mattmc-innovations)
12. [Key Differences Summary](#key-differences-summary)

---

## Executive Summary

MattMC is a Minecraft-inspired voxel engine that reimagines Minecraft's architecture with modern design principles. While sharing many concepts with Minecraft Java Edition, MattMC introduces:

- **Backend Abstraction**: Complete separation of rendering logic from graphics API (OpenGL/Vulkan-ready)
- **RGBI Lighting**: Per-channel RGB colored lighting with intensity, beyond Minecraft's single-channel system
- **Modern Java**: Uses Java 21 features and modern libraries (LWJGL 3.3.4)
- **Clean Architecture**: Clear separation of concerns with well-documented interfaces

| Aspect | Minecraft Java | MattMC |
|--------|---------------|--------|
| Chunk Height | 384 blocks (-64 to 319) | 384 blocks (-64 to 319) ✓ |
| Light Channels | 1 (grayscale) | 4 (RGBI) |
| Rendering API | OpenGL (fixed) | Abstracted (OpenGL impl) |
| Storage Format | Anvil/Region | Anvil/Region compatible |
| Block States | Complex state system | Simplified state system |

---

## World Structure

### Minecraft Java Edition
- **World Dimensions**: Overworld, Nether, End (each dimension is a separate world)
- **World Border**: ±29,999,984 blocks from origin
- **Coordinate System**: Block coordinates (integers), sub-block precision for entities
- **Spawn Point**: World spawn + player spawn points

### MattMC
- **World Dimensions**: Single dimension (expandable architecture)
- **World Size**: Effectively infinite (limited by storage)
- **Coordinate System**: Same as Minecraft (block integers, entity floats)
- **Level Class**: Central `Level.java` manages the world

```java
// MattMC's Level structure
public class Level implements LevelAccessor {
    private final ChunkManager chunkManager;
    private final WorldBlockAccess blockAccess;
    private final WorldLightManager worldLightManager;
    private final AsyncChunkLoader asyncLoader;
    private final DayCycle dayCycle;
    // ...
}
```

---

## Chunk System

### Minecraft Java Edition

| Property | Value |
|----------|-------|
| Chunk Size | 16×384×16 blocks |
| Section Size | 16×16×16 blocks |
| Sections per Chunk | 24 (Y: -64 to 319) |
| Y Range | -64 to 319 |
| Chunk Storage | Anvil format with NBT |

**Key Classes (obfuscated):**
- `LevelChunk` - Full chunk with block/entity data
- `ChunkSection` - 16³ section of blocks
- `ChunkStatus` - Chunk generation stages
- `ChunkSource` - Chunk loading/generation

### MattMC

| Property | Value |
|----------|-------|
| Chunk Size | 16×384×16 blocks ✓ |
| Section Size | 16×16×16 blocks ✓ |
| Sections per Chunk | 24 ✓ |
| Y Range | -64 to 319 ✓ |
| Chunk Storage | Compatible Anvil format |

**Key Classes:**
```
mattmc.world.level.chunk/
├── LevelChunk.java        # Main chunk class
├── ChunkManager.java      # Loaded chunks tracking
├── AsyncChunkLoader.java  # Background loading
├── AsyncChunkSaver.java   # Background saving
├── ChunkNBT.java          # Serialization
├── LightStorage.java      # Per-section light data
├── ColumnHeightmap.java   # Height tracking
├── Region.java            # Region file handling
├── RegionFile.java        # .mca file I/O
└── RegionFileCache.java   # Region caching
```

**LevelChunk Implementation:**
```java
public final class LevelChunk {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 384;
    public static final int DEPTH = 16;
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    public static final int SECTION_HEIGHT = 16;
    public static final int NUM_SECTIONS = 24;
    
    private final Block[][][] blocks;
    private final LightStorage[] lightSections; // 24 sections
    private final ColumnHeightmap heightmap;
    // ...
}
```

### Comparison Notes

| Feature | Minecraft | MattMC | Status |
|---------|-----------|--------|--------|
| Chunk Dimensions | 16×384×16 | 16×384×16 | ✓ Identical |
| Section System | Yes | Yes | ✓ Identical |
| Async Loading | Yes | Yes | ✓ Similar |
| Region Files | 32×32 chunks | 32×32 chunks | ✓ Compatible |
| Heightmaps | Multiple types | Single type | ~ Simplified |

---

## Block System

### Minecraft Java Edition

**Block Registry:**
- Static registry of all block types
- Each block has a unique `ResourceLocation`
- Complex blockstate system with properties

**Block States:**
- Properties like `facing`, `half`, `shape`, `waterlogged`
- States are immutable and pre-computed
- Blockstate → Model mapping via JSON

**Block Classes:**
```
net.minecraft.world.level.block/
├── Block.java              # Base block
├── Blocks.java             # Registry
├── state/
│   ├── BlockState.java     # Immutable state
│   └── properties/         # State properties
└── [specific blocks]       # StairsBlock, etc.
```

### MattMC

**Block Registry:**
```java
public class Blocks {
    public static final Block AIR = register("air", new Block(false));
    public static final Block STONE = register("stone", new Block(true));
    public static final Block GRASS_BLOCK = register("grass_block", new Block(true));
    public static final Block TORCH = register("torch", new TorchBlock(false, 14, 11, 0));
    // ... 50+ blocks registered
}
```

**Block States:**
```java
// Simplified state system
public class BlockState {
    private final Map<String, String> properties;
    
    public String getProperty(String key);
    public BlockState withProperty(String key, String value);
}
```

**Block Classes:**
```
mattmc.world.level.block/
├── Block.java              # Base block with RGB lighting
├── Blocks.java             # Static registry
├── StairsBlock.java        # Stairs with facing/half/shape
├── TorchBlock.java         # Light-emitting torch
├── WallTorchBlock.java     # Wall-mounted variant
├── RotatedPillarBlock.java # Log/pillar rotation
└── state/
    └── BlockState.java     # Property map
```

**RGB Light Emission:**
```java
public class Block {
    private final int lightEmissionR; // Red 0-15
    private final int lightEmissionG; // Green 0-15
    private final int lightEmissionB; // Blue 0-15
    
    // Torch example: warm orange light (14, 11, 0)
    public static final Block TORCH = new TorchBlock(false, 14, 11, 0);
}
```

### Comparison Notes

| Feature | Minecraft | MattMC | Status |
|---------|-----------|--------|--------|
| Block Registry | ResourceLocation keys | String identifiers | ~ Similar |
| State System | Complex properties | Simplified Map<String,String> | ~ Simpler |
| Light Emission | Single value 0-15 | RGBI (4 channels) | ★ Enhanced |
| Collision Shapes | VoxelShapes | VoxelShape | ✓ Similar |
| JSON Models | Yes | Yes | ✓ Compatible |

---

## Lighting System

### Minecraft Java Edition

**Light Types:**
- **Sky Light**: From the sun/moon (15 at top, diminishes through blocks)
- **Block Light**: From emissive blocks (torches, lava, etc.)
- **Combined**: max(skyLight × skyDarkness, blockLight)

**Light Propagation:**
- BFS flood-fill algorithm
- Light decreases by 1 per block (air) or opacity value
- 15 light levels (0-15)
- Updates trigger chunk remeshing

**Storage:**
- 4 bits per block per light type
- Nibble arrays in chunk sections
- ~2KB per section for lighting

### MattMC

**Light Types:**
- **Sky Light**: Identical to Minecraft (15 at top, BFS propagation)
- **Block Light RGBI**: 4-channel colored lighting
  - R: Red channel (0-15)
  - G: Green channel (0-15)
  - B: Blue channel (0-15)
  - I: Intensity for propagation (0-15)

**Light Propagation:**
```java
public class LightPropagator {
    // RGBI propagation: color stays constant, intensity decrements
    public void addBlockLightRGB(LevelChunk chunk, int x, int y, int z, int r, int g, int b) {
        int intensity = Math.max(r, Math.max(g, b));
        chunk.setBlockLightRGBI(x, y, z, r, g, b, intensity);
        // BFS propagation with intensity-1 per step
        // Color (RGB) remains constant during propagation
    }
}
```

**Cross-Chunk Propagation:**
```java
public class CrossChunkLightPropagator {
    // Handles light crossing chunk boundaries
    // Defers updates for unloaded chunks
    // Processes deferred updates when chunks load
}
```

**Storage:**
```java
public class LightStorage {
    // Per 16×16×16 section
    private final byte[] skyLight;      // 4 bits per block (2KB)
    private final byte[] blockLightR;   // 4 bits per block
    private final byte[] blockLightG;   // 4 bits per block
    private final byte[] blockLightB;   // 4 bits per block
    private final byte[] blockLightI;   // 4 bits per block
    // Total: ~10KB per section vs Minecraft's ~4KB
}
```

**Smooth Lighting:**
- Per-vertex light sampling (8 neighbors)
- Ambient occlusion (3-block corner rule)
- Runtime toggle via settings

### Comparison Notes

| Feature | Minecraft | MattMC | Status |
|---------|-----------|--------|--------|
| Sky Light | 15 levels | 15 levels | ✓ Identical |
| Block Light | 15 levels (gray) | RGBI (4×15 levels) | ★ Enhanced |
| Propagation | BFS flood-fill | BFS flood-fill | ✓ Similar |
| Cross-chunk | Yes | Yes | ✓ Similar |
| Smooth Lighting | Yes | Yes | ✓ Similar |
| Colored Light | No (shaders only) | Native RGBI | ★ MattMC Innovation |

---

## Rendering Architecture

### Minecraft Java Edition

**Architecture:**
- Tightly coupled to OpenGL
- `WorldRenderer` directly uses GL calls
- Shader system via GLSL
- Chunk rendering via `ChunkRenderDispatcher`

**Chunk Rendering:**
- Vertex buffer objects (VBOs)
- Render layers: solid, cutout, translucent
- Frustum culling
- Section culling (cave culling)

### MattMC

**Backend Abstraction:**
```
Game/World Layer (no graphics knowledge)
       │
       ▼
Rendering Front-End (ChunkRenderLogic, UIRenderLogic)
       │ DrawCommand
       ▼
Rendering Back-End (OpenGLRenderBackend, future Vulkan)
```

**Key Interfaces:**
```java
public interface RenderBackend {
    void beginFrame();
    void endFrame();
    void submit(DrawCommand cmd);
    void setup2DProjection(int width, int height);
    void setupPerspectiveProjection(float fov, float aspect, float near, float far);
    // ... 90+ methods
}

public class DrawCommand {
    public final int meshId;        // Abstract mesh reference
    public final int materialId;    // Abstract material reference
    public final int transformIndex;
    public final RenderPass pass;   // OPAQUE, TRANSPARENT, UI
}
```

**Render Logic Separation:**
```java
// Frontend: decides WHAT to render (no GL imports allowed)
public class ChunkRenderLogic {
    public void buildCommands(Level world, CommandBuffer buffer) {
        for (LevelChunk chunk : world.getLoadedChunks()) {
            if (!frustum.isChunkVisible(chunk)) continue;
            buffer.add(new DrawCommand(meshId, materialId, transformId, RenderPass.OPAQUE));
        }
    }
}

// Backend: decides HOW to render (OpenGL implementation)
public class OpenGLRenderBackend implements RenderBackend {
    public void submit(DrawCommand cmd) {
        ChunkVAO vao = meshRegistry.get(cmd.meshId);
        material.shader.use();
        vao.render();
    }
}
```

**Texture Atlas:**
- Runtime-built texture atlas for all block textures
- Enables single draw call for multiple textures
- String path → Integer ID mapping for performance

### Comparison Notes

| Feature | Minecraft | MattMC | Status |
|---------|-----------|--------|--------|
| Graphics API | OpenGL (fixed) | Abstracted | ★ Better |
| Chunk VBOs | Yes | Yes | ✓ Similar |
| Frustum Culling | Yes | Yes | ✓ Similar |
| Render Passes | Yes | Yes | ✓ Similar |
| Backend Swap | No | Yes (Vulkan-ready) | ★ MattMC |
| Testability | Requires GL | Mockable backend | ★ Better |

---

## Physics & Collision

### Minecraft Java Edition

**Collision System:**
- AABB (Axis-Aligned Bounding Box) based
- VoxelShapes for complex collision
- Per-block collision shapes
- Entity-block collision detection

**Player Physics:**
- Gravity: -0.08 blocks/tick
- Jump velocity: 0.42 blocks/tick
- Movement per tick with collision resolution
- Swimming, flying, creative mode physics

### MattMC

**Collision System:**
```
mattmc.world.phys/
├── shapes/
│   └── VoxelShape.java    # Block collision shapes
└── (AABB system)
```

**Player Physics:**
```
mattmc.world.entity.player/
├── LocalPlayer.java       # Player entity
├── PlayerController.java  # Movement input
├── PlayerPhysics.java     # Physics simulation
├── PlayerInput.java       # Input handling
├── CollisionDetector.java # Collision checks
└── BlockInteraction.java  # Block breaking/placing
```

**VoxelShape Implementation:**
```java
public class VoxelShape {
    public static VoxelShape empty();
    public static VoxelShape block();      // Full 1×1×1
    public static VoxelShape fromAABB(...); // Custom shape
    
    public boolean collidesWith(VoxelShape other);
    public List<AABB> toAABBs();
}
```

### Comparison Notes

| Feature | Minecraft | MattMC | Status |
|---------|-----------|--------|--------|
| AABB Collision | Yes | Yes | ✓ Similar |
| VoxelShapes | Yes | Yes | ✓ Similar |
| Gravity | 0.08/tick | Configurable | ~ Similar |
| Swimming | Yes | In progress | ~ Partial |
| Flying | Creative mode | Yes | ✓ Similar |

---

## Entity System

### Minecraft Java Edition

**Entity Hierarchy:**
```
Entity
├── LivingEntity
│   ├── Player
│   ├── Mob
│   └── ...
├── ItemEntity
├── Projectile
└── ...
```

**Entity Features:**
- UUID identification
- Position/motion vectors
- NBT serialization
- Tick-based updates
- Riding/passengers

### MattMC

**Current Entity System:**
```
mattmc.world.entity/
└── player/
    ├── LocalPlayer.java      # Client-side player
    ├── PlayerController.java
    ├── PlayerPhysics.java
    └── ...
```

**LocalPlayer:**
```java
public class LocalPlayer {
    private double x, y, z;
    private float yaw, pitch;
    private Inventory inventory;
    private Gamemode gamemode;
    // Physics, movement, interaction...
}
```

**Status:** MattMC currently focuses on the player entity. Other entities (mobs, items) are planned for future development.

---

## Storage & Persistence

### Minecraft Java Edition

**World Save Format:**
```
world/
├── level.dat              # World metadata
├── region/                # Overworld chunks
│   └── r.X.Z.mca         # Region files (32×32 chunks)
├── DIM-1/region/         # Nether
├── DIM1/region/          # End
├── data/                 # Additional data
├── playerdata/           # Per-player data
└── ...
```

**NBT (Named Binary Tag):**
- Hierarchical data format
- Types: byte, short, int, long, float, double, string, list, compound, arrays
- Used for all game data serialization

### MattMC

**World Save Format:**
```
world/
├── level.dat              # World seed, spawn, etc.
├── region/
│   └── r.X.Z.mca         # Compatible region format
└── playerdata/
    └── player.dat        # Player data
```

**NBT Implementation:**
```
mattmc.nbt/
├── NBTTag.java           # Base tag class
├── NBTCompound.java      # Compound tag (map)
├── NBTList.java          # List tag
├── NBTString.java        # String tag
├── NBTInt.java           # Integer tag
├── NBTByteArray.java     # Byte array
└── ...                   # All NBT types
```

**ChunkNBT Serialization:**
```java
public class ChunkNBT {
    // Serialize chunk to NBT compound
    public static NBTCompound serialize(LevelChunk chunk) {
        NBTCompound root = new NBTCompound();
        root.putInt("DataVersion", 3953);
        
        // Block data, light data, heightmaps...
        NBTList sections = new NBTList();
        for (int i = 0; i < LevelChunk.NUM_SECTIONS; i++) {
            sections.add(serializeSection(chunk, i));
        }
        root.put("sections", sections);
        
        return root;
    }
}
```

### Comparison Notes

| Feature | Minecraft | MattMC | Status |
|---------|-----------|--------|--------|
| NBT Format | Full spec | Full spec | ✓ Compatible |
| Region Files | .mca (Anvil) | .mca (Anvil) | ✓ Compatible |
| Chunk Sections | Yes | Yes | ✓ Similar |
| Light Storage | Per-section | Per-section | ✓ Similar |
| Player Data | Per-player | Per-player | ✓ Similar |

---

## Resource System

### Minecraft Java Edition

**Resource Packs:**
- `assets/<namespace>/` structure
- Textures, models, sounds, languages
- JSON blockstates and models
- Parent model inheritance

**Model System:**
```
assets/minecraft/
├── blockstates/          # Block → Model mapping
│   └── stone.json
├── models/
│   ├── block/           # Block models
│   │   ├── cube_all.json
│   │   └── stone.json
│   └── item/            # Item models
└── textures/
    └── block/
        └── stone.png
```

### MattMC

**Resource Structure:**
```
resources/assets/mattmc/
├── blockstates/         # Blockstate JSON
├── models/
│   ├── block/          # Block models
│   └── item/           # Item models
├── textures/
│   └── block/          # Block textures
└── shaders/            # GLSL shaders
```

**ResourceManager:**
```java
public class ResourceManager {
    // Load block model with parent resolution
    public static BlockModel loadBlockModel(String name);
    
    // Get texture paths for a block
    public static Map<String, String> getBlockTexturePaths(String blockName);
    
    // Load blockstate JSON
    public static BlockState loadBlockState(String name);
}
```

**Model System:**
```java
public class BlockModel {
    private String parent;                    // Parent model reference
    private Map<String, String> textures;     // Texture variables
    private List<ModelElement> elements;      // Geometry
    private Map<String, DisplayTransform> display;
    // Parent inheritance, texture variable resolution
}
```

### Comparison Notes

| Feature | Minecraft | MattMC | Status |
|---------|-----------|--------|--------|
| JSON Models | Yes | Yes | ✓ Compatible |
| Parent Inheritance | Yes | Yes | ✓ Similar |
| Texture Variables | Yes | Yes | ✓ Similar |
| Blockstates | Variants + Multipart | Variants only | ~ Simpler |
| Resource Packs | Yes | Planned | ~ Future |

---

## MattMC Innovations

### 1. Backend Abstraction
MattMC introduces a complete separation between rendering logic and graphics API implementation. This enables:
- Testing without OpenGL context
- Future Vulkan/DirectX backends without code changes
- Clear architectural boundaries

### 2. RGBI Colored Lighting
MattMC extends Minecraft's single-channel lighting to 4-channel RGBI:
- Full RGB color support for block lights
- Colored torches, glowstone, etc.
- Smooth color blending during propagation
- Maintains Minecraft-compatible intensity behavior

### 3. Modern Java Design
- Java 21 features
- Clean package structure
- Comprehensive JavaDoc
- Consistent coding style

### 4. Async Everything
- Async chunk loading/saving
- Background mesh building
- Region file caching
- No main thread blocking

---

## Key Differences Summary

| Category | Minecraft Java | MattMC |
|----------|---------------|--------|
| **Architecture** | Monolithic | Backend-abstracted |
| **Light System** | Grayscale | RGBI colored |
| **Graphics API** | OpenGL (fixed) | Abstracted (swappable) |
| **Entity System** | Full hierarchy | Player-focused (expanding) |
| **Blockstates** | Full multipart | Variants only |
| **Dimensions** | 3 (Overworld/Nether/End) | 1 (expandable) |
| **Multiplayer** | Native | Planned |
| **Chunk Format** | Anvil | Anvil-compatible |
| **NBT Support** | Full | Full |
| **Model System** | Full | Compatible subset |

---

## Conclusion

MattMC successfully implements a Minecraft-compatible voxel engine with several architectural improvements:

1. **Full Minecraft compatibility** in chunk structure, NBT format, and world storage
2. **Enhanced lighting** with RGBI colored light propagation
3. **Better architecture** with backend abstraction enabling testing and future expansion
4. **Modern codebase** using Java 21 and clean design patterns

The project serves as both a functional game and an educational example of how to structure a complex voxel engine with clean, maintainable code.

---

*Document generated: November 2024*
*Based on MattMC codebase analysis*

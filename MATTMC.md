# MattMC Rendering, Geometry, and UV System - Technical Documentation

## Table of Contents
1. [Overview](#overview)
2. [JSON Model System](#json-model-system)
3. [Geometry Calculation](#geometry-calculation)
4. [UV Mapping and Texture Atlas](#uv-mapping-and-texture-atlas)
5. [Face Ordering and Winding](#face-ordering-and-winding)
6. [In-World Block Rendering](#in-world-block-rendering)
7. [Item Rendering](#item-rendering)
8. [Lighting System](#lighting-system)
9. [Classes and Architecture](#classes-and-architecture)
10. [Algorithms and Methods](#algorithms-and-methods)

---

## Overview

MattMC implements a voxel-based rendering system closely modeled after Minecraft's JSON-based model format. The system supports:

- **JSON-based model definitions** for blocks and items
- **Parent model inheritance** with property merging
- **Texture variable resolution** (#variable references)
- **Custom geometry** via model elements (cuboids with UV-mapped faces)
- **Face culling** for performance optimization
- **Smooth vertex lighting** with RGB colored block light
- **Texture atlas** for efficient multi-texture rendering
- **Isometric item rendering** using 3D geometry projection

The rendering pipeline separates in-world block rendering (optimized chunk meshes with VBO/VAO) from item rendering (2D isometric projection from 3D geometry), while reusing the same underlying geometry definitions.

---

## JSON Model System

### 1. Model File Hierarchy

The JSON model system consists of three types of files:

1. **Blockstate JSON** (`/assets/blockstates/{name}.json`)
   - Maps block states to models with transformations
   - Supports single variants and property-based variants

2. **Block Model JSON** (`/assets/models/block/{name}.json`)
   - Defines 3D geometry (elements), textures, and display transforms
   - Can inherit from parent models

3. **Item Model JSON** (`/assets/models/item/{name}.json`)
   - References block models for block items
   - Supports tint definitions for colored items (e.g., grass blocks)

### 2. Blockstate Format

Blockstates define which model to use for each block state and apply transformations:

```json
{
  "variants": {
    "": {
      "model": "mattmc:block/cobblestone"
    }
  }
}
```

For blocks with properties (like stairs):

```json
{
  "variants": {
    "facing=north,half=bottom,shape=straight": {
      "model": "mattmc:block/birch_stairs",
      "y": 270,
      "uvlock": true
    }
  }
}
```

**Variant Properties:**
- `model`: Path to the block model (namespace:path format)
- `x`, `y`, `z`: Rotation angles in degrees (90° increments)
- `uvlock`: If true, UV coordinates rotate with the model

### 3. Block Model Format

Block models define geometry using **elements** (cuboids):

```json
{
  "parent": "block/block",
  "textures": {
    "particle": "#all",
    "all": "mattmc:block/dirt"
  },
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 16, 16],
      "faces": {
        "down": { "uv": [0, 0, 16, 16], "texture": "#all", "cullface": "down" },
        "up": { "uv": [0, 0, 16, 16], "texture": "#all", "cullface": "up" },
        "north": { "uv": [0, 0, 16, 16], "texture": "#all", "cullface": "north" },
        "south": { "uv": [0, 0, 16, 16], "texture": "#all", "cullface": "south" },
        "west": { "uv": [0, 0, 16, 16], "texture": "#all", "cullface": "west" },
        "east": { "uv": [0, 0, 16, 16], "texture": "#all", "cullface": "east" }
      }
    }
  ]
}
```

**Element Properties:**
- `from`: Starting corner `[x, y, z]` in 0-16 range (1/16th block units)
- `to`: Ending corner `[x, y, z]` in 0-16 range
- `faces`: Map of face directions to face definitions
- `rotation`: Optional rotation around an origin point
- `shade`: Whether to apply directional shading (default: true)

**Face Properties:**
- `uv`: UV coordinates `[u1, v1, u2, v2]` in 0-16 range (texture pixels)
- `texture`: Texture reference (e.g., `"#all"` or `"block/dirt"`)
- `cullface`: Direction to cull against (if adjacent block is solid)
- `rotation`: Texture rotation in degrees (0, 90, 180, 270)
- `tintindex`: Tint index for biome coloring

### 4. Parent Model Resolution

The `ResourceManager` class handles model resolution with parent inheritance:

**Resolution Process:**
1. Load raw model from JSON file
2. If model has a `parent` property, recursively load parent model
3. Merge parent and child properties (child overrides parent)
4. Resolve texture variables (replace `#variable` with actual paths)
5. Cache the fully resolved model

**Property Merging Rules:**
- Child `textures` map is merged with parent (child values override)
- Child `elements` completely replace parent elements (no merging)
- Child `display` transforms completely replace parent
- Child `ambientocclusion` setting overrides parent

**Example Resolution:**
```
dirt.json:
  parent: "block/cube_all"
  textures: {"all": "block/dirt"}

→ Loads cube_all.json:
  parent: "block/block"
  textures: {"particle": "#all"}
  elements: [full cube element]

→ Merged Result:
  textures: {"all": "block/dirt", "particle": "block/dirt"}
  elements: [full cube with all faces textured with dirt]
```

### 5. Texture Variable Resolution

Textures can use variables that reference other texture entries:

```json
{
  "textures": {
    "side": "block/planks",
    "top": "block/planks",
    "particle": "#side"
  }
}
```

The `resolveTextureVariables()` method:
1. Resolves variables in the textures map itself (e.g., `"#side"` → `"block/planks"`)
2. Resolves variables in element face textures
3. Follows variable chains recursively (e.g., `#particle` → `#side` → `"block/planks"`)

### 6. Complex Model Examples

**Stairs Model** (2 elements):
```json
{
  "parent": "block/block",
  "elements": [
    {
      "comment": "Bottom slab",
      "from": [0, 0, 0],
      "to": [16, 8, 16]
    },
    {
      "comment": "Top step",
      "from": [8, 8, 0],
      "to": [16, 16, 16]
    }
  ]
}
```

**Torch Model** (1 thin element):
```json
{
  "ambientocclusion": false,
  "elements": [
    {
      "from": [7, 0, 7],
      "to": [9, 10, 9],
      "shade": false,
      "faces": {
        "down": { "uv": [7, 13, 9, 15], "texture": "#torch", "cullface": "down" },
        "up": { "uv": [7, 6, 9, 8], "texture": "#torch" },
        "north": { "uv": [7, 6, 9, 16], "texture": "#torch" },
        "south": { "uv": [7, 6, 9, 16], "texture": "#torch" },
        "east": { "uv": [7, 6, 9, 16], "texture": "#torch" },
        "west": { "uv": [7, 6, 9, 16], "texture": "#torch" }
      }
    }
  ]
}
```

---

## Geometry Calculation

### 1. Coordinate Systems

MattMC uses multiple coordinate systems:

1. **World Coordinates**: Absolute position in the world (floating point)
2. **Chunk-Local Coordinates**: Position within a 16×384×16 chunk (integers 0-15, 0-383, 0-15)
3. **Block-Relative Coordinates**: Position within a 1×1×1 block (floating point 0.0-1.0)
4. **Model Coordinates**: Minecraft format (integers 0-16, 1/16th block units)

**Conversion Example:**
```
Model: from=[7, 0, 7], to=[9, 10, 9] (torch)
Block-Relative: from=[0.4375, 0, 0.4375], to=[0.5625, 0.625, 0.5625]
World: from=[blockX+0.4375, blockY, blockZ+0.4375], to=[blockX+0.5625, blockY+0.625, blockZ+0.5625]
```

### 2. Face Geometry Generation

The `BlockFaceGeometry` class generates vertices for standard cube faces using counter-clockwise winding:

**Top Face (y = 1):**
```java
// Two triangles forming a quad
// Triangle 1: (x0,y1,z0) → (x0,y1,z1) → (x1,y1,z1)
// Triangle 2: (x0,y1,z0) → (x1,y1,z1) → (x1,y1,z0)
glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
glTexCoord2f(0, 1); glVertex3f(x0, y1, z1);
glTexCoord2f(1, 1); glVertex3f(x1, y1, z1);

glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);
glTexCoord2f(1, 1); glVertex3f(x1, y1, z1);
glTexCoord2f(1, 0); glVertex3f(x1, y1, z0);
```

**Vertex Ordering:**
- All faces use counter-clockwise winding when viewed from outside
- Each quad is split into 2 triangles
- Texture coordinates map (0,0) to top-left, (1,1) to bottom-right
- V coordinates are flipped for vertical faces to correct texture orientation

### 3. Custom Geometry Builders

#### Stairs Geometry

The `StairsGeometryBuilder` class generates stair geometry based on blockstate properties:

**Bottom Half Stairs (facing=NORTH):**
- Bottom slab: (0, 0, 0) to (1, 0.5, 1) - full width/depth, half height
- Top step: (0, 0.5, 0) to (1, 1, 0.5) - north half only, full height

**Rotation by Facing:**
- NORTH: Step at z=0 (front)
- SOUTH: Step at z=1 (back)
- WEST: Step at x=0 (left)
- EAST: Step at x=1 (right)

**Top Half Stairs:**
- Slab and step positions swapped vertically

#### Torch Geometry

The `TorchGeometryBuilder` reads geometry from JSON model elements:

**Process:**
1. Load BlockModel for torch from ResourceManager
2. Iterate through model elements
3. Convert from model coordinates (0-16) to block-relative (0-1)
4. Apply Y-axis rotation for wall torches based on facing direction
5. Generate vertices for each face of each element

**Rotation Formula (Y-axis around block center):**
```java
float cx = x - 0.5f;  // Translate to origin
float cz = z - 0.5f;

// Rotate
switch (degrees) {
    case 90:  rotatedX = -cz; rotatedZ = cx; break;
    case 180: rotatedX = -cx; rotatedZ = -cz; break;
    case 270: rotatedX = cz;  rotatedZ = -cx; break;
}

// Translate back
x = rotatedX + 0.5f;
z = rotatedZ + 0.5f;
```

### 4. Face Culling

The `BlockFaceCollector` implements intelligent face culling to avoid rendering hidden faces:

**Culling Rules:**
```java
private boolean shouldRenderFace(LevelChunk chunk, int x, int y, int z) {
    // Check Y bounds (no neighboring chunks in Y)
    if (y < 0 || y >= HEIGHT) return true;
    
    // Get adjacent block
    Block adjacent = getBlockAcrossChunks(chunk, x, y, z);
    
    // Render if adjacent is air or transparent
    return adjacent.isAir() || !adjacent.isSolid();
}
```

**Cross-Chunk Culling:**
- Uses `ChunkNeighborAccessor` interface to query blocks in adjacent chunks
- Prevents rendering faces at chunk boundaries when adjacent chunk has solid block
- Significantly improves performance by reducing overdraw

### 5. Vertex Format

The `MeshBuilder` uses a comprehensive vertex format for chunk meshes:

**Vertex Attributes (17 floats per vertex):**
1. `x, y, z` - Position (3 floats)
2. `u, v` - Texture coordinates (2 floats)
3. `r, g, b, a` - Color/albedo (4 floats, 0-1 range)
4. `nx, ny, nz` - Normal vector (3 floats)
5. `skyLight` - Sky light level (1 float, 0-15 range)
6. `blockLightR, blockLightG, blockLightB` - RGB block light (3 floats, 0-15 range)
7. `ao` - Ambient occlusion (1 float, 0-3 range)

**Memory Layout:**
```
[x][y][z][u][v][r][g][b][a][nx][ny][nz][skyLight][blockLightR][blockLightG][blockLightB][ao]
```

**Index Buffer:**
- Uses element indices for efficient GPU rendering
- Each quad (4 vertices) produces 6 indices (2 triangles)
- Triangle 1: indices [0, 1, 2]
- Triangle 2: indices [0, 2, 3]

---

## UV Mapping and Texture Atlas

### 1. Texture Coordinates

UV coordinates in model JSON are specified in pixel units (0-16 range):

```json
"uv": [0, 0, 16, 16]  // Full texture
"uv": [0, 8, 16, 16]  // Bottom half of texture
```

**Conversion to 0-1 Range:**
```java
float u0 = uv[0] / 16.0f;
float v0 = uv[1] / 16.0f;
float u1 = uv[2] / 16.0f;
float v1 = uv[3] / 16.0f;
```

### 2. Texture Atlas System

The `TextureAtlas` class packs multiple block textures into a single large texture:

**Benefits:**
- Reduces texture binding calls (batch rendering)
- Allows multiple textures in a single draw call
- Improves GPU cache efficiency

**UV Mapping Structure:**
```java
public static class UVMapping {
    public final float u0, v0;  // Top-left in atlas
    public final float u1, v1;  // Bottom-right in atlas
}
```

**Mapping Process:**
1. Each block texture is assigned a region in the atlas
2. Model UVs (0-1) are remapped to atlas region
3. Formula: `atlasU = mapping.u0 + (mapping.u1 - mapping.u0) * modelU`

**Example:**
```
Model UV: (0.0, 0.0) to (1.0, 1.0)  // Full texture
Atlas Region: (0.25, 0.0) to (0.5, 0.25)  // 1/4 of atlas
Result: Model UV remapped to atlas region
```

### 3. UV Mapper Class

The `UVMapper` class handles UV coordinate mapping and color extraction:

**Key Methods:**
- `getUVMapping(FaceData)` - Gets atlas UV mapping for a block face
- `extractColor(FaceData)` - Extracts RGBA color with brightness adjustment
- Applies grass green tint for grass_block top faces
- Returns white color for textured blocks (texture modulation)
- Returns fallback colors for missing textures

### 4. Texture Resolution

**Texture Path Conversion:**
```
Model: "mattmc:block/dirt"
↓
Remove namespace: "block/dirt"
↓
Add prefix/suffix: "assets/textures/block/dirt.png"
↓
Atlas lookup: TextureAtlas.getUVMapping("assets/textures/block/dirt.png")
```

---

## Face Ordering and Winding

### 1. Winding Order

**Rule:** All faces use **counter-clockwise (CCW)** winding when viewed from outside the block.

**Why CCW?**
- OpenGL default front face is CCW
- Enables backface culling (glEnable(GL_CULL_FACE))
- Consistent with Minecraft's coordinate system

**Example (Top Face):**
```
Looking down (from +Y):
(x0,z0) -----> (x1,z0)
   |              |
   |              |
   v              v
(x0,z1) -----> (x1,z1)

Triangle 1: (x0,z0) → (x0,z1) → (x1,z1) [CCW]
Triangle 2: (x0,z0) → (x1,z1) → (x1,z0) [CCW]
```

### 2. Face Ordering in Mesh Building

The `MeshBuilder` processes faces in a specific order for optimal rendering:

**Order:**
1. Top faces (normal: 0, 1, 0)
2. Bottom faces (normal: 0, -1, 0)
3. North faces (normal: 0, 0, -1)
4. South faces (normal: 0, 0, 1)
5. West faces (normal: -1, 0, 0)
6. East faces (normal: 1, 0, 0)

**Rationale:**
- Groups faces by normal for potential shader optimizations
- Consistent ordering aids debugging
- No impact on visual result (GPU sorts by depth anyway)

### 3. Normal Vectors

Each vertex has an associated normal vector for lighting calculations:

**Face Normals:**
- Top: (0, 1, 0)
- Bottom: (0, -1, 0)
- North: (0, 0, -1)
- South: (0, 0, 1)
- West: (-1, 0, 0)
- East: (1, 0, 0)

**Usage:**
- Shader uses normals for directional lighting
- Enables Lambertian diffuse shading
- Supports smooth lighting at block corners

### 4. Texture Coordinate Flipping

Vertical faces have V coordinates flipped to correct texture orientation:

```java
// Original (would appear upside down):
// glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);  // Top-left
// glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);  // Bottom-left

// Corrected (proper orientation):
glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);  // Bottom-left
glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);  // Top-left
```

This ensures textures appear right-side up on all faces.

---

## In-World Block Rendering

### 1. Chunk Mesh Pipeline

The chunk rendering system converts blocks into optimized GPU meshes:

**Pipeline Steps:**
1. **Face Collection** (`BlockFaceCollector`)
   - Iterates through all blocks in chunk
   - Applies face culling
   - Separates faces by direction
   - Handles custom geometry (stairs, torches)

2. **Mesh Building** (`MeshBuilder`)
   - Converts collected faces to vertex arrays
   - Applies UV mapping from texture atlas
   - Samples smooth vertex lighting
   - Generates index buffer

3. **Upload to GPU** (`ChunkMeshBuffer`)
   - Creates VBO for vertex data
   - Creates EBO for index data
   - Configures VAO with vertex attributes
   - Stores for rendering

4. **Rendering** (`LevelRenderer`)
   - Binds texture atlas
   - Activates shader program
   - Binds VAO
   - Calls glDrawElements

### 2. Block Face Collector

**Responsibilities:**
- Iterates through chunk blocks (16×384×16)
- Determines which faces are visible (not culled)
- Collects face data with position, color, block reference
- Handles custom rendering blocks (stairs, torches)

**Face Data Structure:**
```java
public static class FaceData {
    float x, y, z;              // World position
    int cx, cy, cz;             // Chunk-local coords
    int color;                  // Base color
    float brightness;           // Brightness multiplier
    Block block;                // Block reference
    String faceType;            // "top", "side", etc.
    FaceRenderer renderer;      // Rendering function
    BlockState blockState;      // For custom rendering
    LevelChunk chunk;           // For light sampling
}
```

### 3. Mesh Builder

**Key Features:**
- Converts FaceData to vertex/index arrays
- Uses primitive FloatList/IntList to avoid boxing overhead
- Applies texture atlas UV mapping
- Samples smooth vertex lighting
- Handles custom geometry (stairs, torches)

**Optimization:**
- Indexed loop instead of enhanced for (avoids iterator allocation)
- Direct list access instead of stream operations
- Reuses arrays via efficient toArray()

### 4. Vertex Light Sampling

The `VertexLightSampler` implements smooth lighting by sampling adjacent blocks:

**Algorithm:**
```
For each vertex of a face:
  1. Identify the 4 adjacent positions (3 edges + 1 diagonal)
  2. Sample light from each position
  3. Average only non-zero samples
  4. Return [skyLight, blockLightR, blockLightG, blockLightB, ao]
```

**Example (Top Face, Corner 0 at x0,z0):**
```
Sample positions:
- (0, 1, 0)   - directly above
- (-1, 1, 0)  - left
- (0, 1, -1)  - front
- (-1, 1, -1) - diagonal
```

**Why Average Only Non-Zero?**
- Prevents solid blocks from darkening adjacent corners
- Fixes interior corner lighting artifacts
- Maintains proper light propagation

### 5. Cross-Chunk Operations

Both face culling and light sampling support cross-chunk queries:

**Interface:**
```java
public interface ChunkNeighborAccessor {
    Block getBlockAcrossChunks(LevelChunk chunk, int x, int y, int z);
}

public interface ChunkLightAccessor {
    int getSkyLightAcrossChunks(LevelChunk chunk, int x, int y, int z);
    int getBlockLightAcrossChunks(LevelChunk chunk, int x, int y, int z);
    int[] getBlockLightRGBAcrossChunks(LevelChunk chunk, int x, int y, int z);
}
```

**Coordinate Handling:**
- Chunk-local coords can be outside 0-15 range
- Negative x/z: access west/north neighbor
- x/z >= 16: access east/south neighbor
- Implementation calculates neighbor chunk and remaps coordinates

---

## Item Rendering

### 1. Overview

Item rendering uses a fundamentally different approach from in-world block rendering:

**Key Differences:**
- **In-World Blocks:** Full 3D rendering with perspective projection, GPU-based chunk meshes
- **Item Rendering:** Isometric 2D projection from 3D geometry, immediate mode rendering

**Similarities:**
- Both use the same JSON model definitions
- Both use the same geometry generation functions
- Both apply textures and colors

### 2. Isometric Projection

The `ItemRenderer` projects 3D block geometry to 2D screen space using isometric projection:

**Projection Formulas:**
```java
// X projection: diagonal axis (SW to NE)
screen_x = centerX + (wx - wz) * isoWidth

// Y projection: combines vertical and diagonal
screen_y = centerY - wy * isoHeight - (wx + wz) * isoHeight * 0.5
```

**Viewing Angle:**
- Camera positioned at southwest, looking northeast
- Visible faces: West (left), North (right), Top
- Creates classic isometric "2.5D" appearance

**Visual Example:**
```
3D Block (0,0,0) to (1,1,1):

World Coords → Screen Coords:
(0,1,0) → (centerX, centerY - isoHeight)          [top-left]
(1,1,0) → (centerX + isoWidth, centerY - isoHeight * 1.5)  [top-front]
(1,1,1) → (centerX, centerY - isoHeight * 2)      [top-back]
```

### 3. Rendering Pipeline

**Block Items:**
1. Load item model from ResourceManager
2. Detect block item (has "all", "top", "side", or "bottom" textures)
3. Capture 3D geometry using VertexCapture
4. Project vertices to 2D isometric coordinates
5. Render triangles with texture mapping
6. Apply face-specific brightness (top: 100%, west: 80%, north: 60%)
7. Apply tint for biome-colored blocks (grass)

**Flat Items (non-blocks):**
1. Load item model
2. Get "layer0" texture
3. Render as 2D quad (no projection)
4. Scale to match visual size of isometric blocks

### 4. Geometry Capture

The `VertexCapture` class captures vertices emitted during geometry generation:

**Process:**
```java
VertexCapture capture = new VertexCapture();
BlockGeometryCapture.captureTopFace(capture, 0, 0, 0);
List<Face> faces = capture.getFaces();

// Each face is a triangle with 3 vertices
// Each vertex has position (x,y,z) and UVs (u,v)
```

**Why Capture?**
- Reuses existing geometry generation code
- Maintains consistency between in-world and item rendering
- Allows 3D geometry to be processed in 2D space

### 5. Face Visibility and Ordering

**Isometric View (SW to NE):**
- **Visible:** West face (left), North face (right), Top face
- **Hidden:** East face (behind), South face (behind), Bottom face

**Rendering Order (back-to-front):**
1. West face (left, 80% brightness)
2. North face (right, 60% brightness)
3. Top face (brightest, 100% brightness, tint applied)

This order ensures proper visual appearance without depth buffer.

### 6. Stairs Item Rendering

Stairs items require special handling due to complex geometry:

**Approach:**
```java
1. Capture south-facing bottom stairs geometry
   - Step rises toward z=1 (back in isometric view)
   - Creates pleasing stepped appearance

2. Separate faces into:
   - Top faces (horizontal surfaces)
   - Visible side faces (West and North only)
   - Filter out East and South (hidden)

3. Render visible sides first (back-to-front)
4. Render top faces last (closest)
```

**Face Detection:**
```java
// Top face: all Y coords equal and > 0
boolean isTop = (y1 == y2) && (y2 == y3) && (y1 > 0.01f);

// West face: all X coords == 0
boolean isWest = (x1 < 0.01f) && (x2 < 0.01f) && (x3 < 0.01f);

// North face: all Z coords == 0
boolean isNorth = (z1 < 0.01f) && (z2 < 0.01f) && (z3 < 0.01f);
```

### 7. Texture Coordinate Flipping

Item rendering requires V-coordinate flipping because:
- 3D geometry has V flipped for proper 3D rendering
- 2D rendering needs unflipped coordinates
- Solution: `glTexCoord2f(u, 1.0f - v)` during projection

### 8. Item Positioning

**Hotbar vs Inventory:**
```java
// Block items in inventory: y + 18f offset
// Block items in hotbar: y + 18f offset
// Flat items in inventory: no offset
// Flat items in hotbar: y - 18f offset (move up)
```

This ensures proper alignment in both UI contexts.

### 9. Tint Application

For biome-colored items (grass blocks):

```java
// Get tint color from item model
int topTintColor = itemModel.getTints().get(0).getTintColor();

// Apply to top face only
float r = ((topTintColor >> 16) & 0xFF) / 255.0f;
float g = ((topTintColor >> 8) & 0xFF) / 255.0f;
float b = (topTintColor & 0xFF) / 255.0f;
glColor4f(r, g, b, 1.0f);
```

### 10. Comparison: Block vs Item Rendering

| Aspect | In-World Blocks | Item Rendering |
|--------|----------------|----------------|
| **Projection** | Perspective 3D | Isometric 2D from 3D |
| **Rendering Mode** | VBO/VAO (vertex buffer objects) | Immediate mode (glBegin/glEnd) |
| **Batching** | Entire chunks batched | Individual items |
| **Lighting** | Smooth vertex lighting, shader-based | Face brightness only (80%, 60%, 100%) |
| **Face Culling** | Dynamic based on neighbors | Fixed (West, North, Top) |
| **Texture Atlas** | Yes | Direct texture binding per face |
| **Performance** | Highly optimized (GPU-resident) | Less critical (fewer items) |
| **Rotation** | Blockstate-driven | Fixed isometric angle |
| **Scale** | World scale | Screen pixels |

**Why Different Approaches?**
- In-world: Needs to render millions of blocks efficiently
- Items: Only dozens visible, clarity more important than performance
- Isometric view provides better item recognition in small UI space

---

## Lighting System

### 1. Overview

MattMC implements a sophisticated lighting system with:
- **Sky light** (sunlight propagation from sky)
- **RGB block light** (colored light sources like torches)
- **Smooth vertex lighting** (gradients across faces)
- **Ambient occlusion** (corner darkening)

### 2. Light Storage

Light is stored per block in chunks:

**Sky Light:**
- 4 bits per block (0-15 levels)
- Propagates down from sky
- Decreases by 1 per block

**Block Light (RGB):**
- 4 bits per color channel (R, G, B)
- 4 bits for intensity (I)
- Total: 16 bits per block
- Allows colored light sources

### 3. Smooth Vertex Lighting

Each vertex samples light from 4 adjacent blocks:

**Algorithm:**
```
1. Identify vertex position (corner of face)
2. Determine 4 sample positions:
   - The face itself (normal direction)
   - Two edge neighbors
   - One diagonal corner
3. Sample sky light and RGB block light at each
4. Average only non-zero samples
5. Apply to vertex
```

**Example (Top Face, NW Corner):**
```
Sample positions relative to block:
(0, 1, 0)    - directly above (face itself)
(-1, 1, 0)   - west neighbor
(0, 1, -1)   - north neighbor
(-1, 1, -1)  - northwest diagonal
```

**Why This Works:**
- Creates smooth light gradients
- Prevents harsh transitions at block boundaries
- Interior corners receive light from open directions only

### 4. Light Propagation

**Sky Light:**
```
Initial: Sky blocks set to 15
Propagation: Each block receives max(neighbor_light - 1, 0)
```

**Block Light:**
```
Source: Light-emitting block (torch = 14)
Propagation: 
  - Intensity decreases by 1 per block
  - RGB values stay constant (color preserved)
  - Scaled at vertex: RGB * (intensity / maxRGB)
```

### 5. RGB Block Light

**Color Representation:**
```java
int r = chunk.getBlockLightR(x, y, z);  // 0-15
int g = chunk.getBlockLightG(x, y, z);  // 0-15
int b = chunk.getBlockLightB(x, y, z);  // 0-15
int i = chunk.getBlockLightI(x, y, z);  // 0-15
```

**Intensity Scaling:**
```java
// RGB values constant during propagation
// Intensity decreases with distance
// Scale RGB by intensity ratio
float scale = (float)intensity / max(r, g, b);
int scaledR = round(r * scale);
int scaledG = round(g * scale);
int scaledB = round(b * scale);
```

**Example:**
```
Torch at (0,0,0): R=14, G=7, B=0, I=14
Block at (1,0,0): R=14, G=7, B=0, I=13
Block at (2,0,0): R=14, G=7, B=0, I=12

Vertex sees I=12:
  scale = 12 / 14 = 0.857
  Final: R=12, G=6, B=0
```

### 6. Directional Shading

Faces receive different brightness based on orientation:

```java
Top:    100% (1.0f)
Bottom:  50% (0.5f) - darkest
North:   80% (0.8f)
South:   80% (0.8f)
West:    60% (0.6f)
East:    60% (0.6f)
```

This creates depth perception even with flat lighting.

### 7. Shader Lighting

The vertex shader receives per-vertex light data:

**Vertex Attributes:**
```glsl
attribute vec3 position;
attribute vec2 texCoord;
attribute vec4 color;           // Albedo (base color)
attribute vec3 normal;
attribute float skyLight;       // 0-15
attribute vec3 blockLight;      // RGB 0-15 each
attribute float ao;             // 0-3
```

**Lighting Calculation:**
```glsl
// Convert light levels to 0-1 range
float skyBrightness = skyLight / 15.0;
vec3 blockBrightness = blockLight / 15.0;

// Combine lights
vec3 totalLight = skyBrightness * sunColor + blockBrightness;

// Apply to albedo
vec3 finalColor = color.rgb * totalLight;
```

### 8. Ambient Occlusion

Currently placeholder (ao = 0.0), designed for future implementation:

**Planned Algorithm:**
- Sample solid blocks at vertex corners
- Count solid neighbors (0-3)
- Apply darkening: `brightness *= (1.0 - ao * 0.2)`

---

## Classes and Architecture

### 1. Model System

**ResourceManager**
- Loads and caches block/item models
- Resolves parent model chains
- Resolves texture variables
- Converts model paths to texture paths

**BlockModel**
- Represents a JSON block/item model
- Properties: parent, textures, elements, display, ambientocclusion
- Stores original parent for special rendering detection

**ModelElement**
- Represents a cuboid geometry element
- Properties: from, to, faces, rotation, shade
- Nested classes: ElementFace, ModelRotation

**BlockState**
- Represents a blockstate JSON file
- Maps block properties to model variants
- Handles variant selection and transformations

**BlockStateVariant**
- Single variant of a blockstate
- Properties: model path, x/y/z rotation, uvlock

### 2. Rendering System

**LevelRenderer**
- Manages chunk rendering
- Handles frustum culling
- Coordinates chunk mesh updates
- Applies global rendering state

**BlockFaceCollector**
- Collects visible block faces from chunks
- Performs face culling
- Separates faces by direction
- Handles custom rendering blocks
- Nested class: FaceData

**MeshBuilder**
- Converts collected faces to vertex/index arrays
- Applies UV mapping from texture atlas
- Samples smooth vertex lighting
- Generates optimized mesh buffers
- Uses FloatList and IntList for efficiency

**ChunkMeshBuffer**
- Stores vertex and index arrays for a chunk
- Handles VBO/VAO creation and binding
- Manages GPU memory

**ChunkMeshData**
- Intermediate data structure
- Holds vertex/index arrays before GPU upload

### 3. Geometry Generation

**BlockFaceGeometry**
- Static methods for generating cube face geometry
- Methods: drawTopFace, drawBottomFace, drawNorthFace, etc.
- Draws directly to OpenGL (immediate mode)
- Used for legacy rendering and debug visualization

**BlockGeometryCapture**
- Static methods for capturing cube face geometry
- Methods mirror BlockFaceGeometry but output to VertexCapture
- Used for item rendering
- Methods: captureTopFace, captureStairsNorthBottom, etc.

**StairsGeometryBuilder**
- Generates stairs geometry from blockstate
- Handles all 4 facing directions
- Supports top/bottom half variants
- Applies proper UV mapping and lighting

**TorchGeometryBuilder**
- Reads torch geometry from JSON model elements
- Converts model coordinates to world coordinates
- Applies Y-axis rotation for wall torches
- Generates vertices for each element face

### 4. UV and Textures

**TextureAtlas**
- Packs multiple textures into single atlas
- Provides UV mapping for each texture
- Reduces texture binding overhead
- Nested class: UVMapping

**UVMapper**
- Maps block faces to texture atlas UVs
- Extracts colors with brightness adjustment
- Applies tints (e.g., grass green)
- Handles missing texture fallback

**Texture**
- Represents a single texture image
- Loads PNG files from resources
- Binds to OpenGL texture units
- Manages texture lifecycle

### 5. Lighting

**VertexLightSampler**
- Samples smooth vertex lighting
- Queries adjacent blocks for light levels
- Averages non-zero light samples
- Handles cross-chunk light sampling
- Interface: ChunkLightAccessor

**WorldLightManager**
- Manages light propagation in the world
- Handles sky light and block light updates
- Queues light updates for processing
- Coordinates chunk relighting

### 6. Item Rendering

**ItemRenderer**
- Renders items in UI (hotbar, inventory)
- Generates isometric projections for block items
- Renders flat 2D quads for regular items
- Applies tints and brightness
- Caches textures

**VertexCapture**
- Captures vertices during geometry generation
- Stores triangles with position and UVs
- Allows 3D geometry to be processed in 2D
- Nested classes: Vertex, Face

### 7. Utilities

**ColorUtils**
- Adjusts color brightness
- Darkens colors for shading
- Applies tints
- Converts between color formats

**FloatList / IntList**
- Primitive array lists (no boxing)
- Reduces GC pressure
- Efficient toArray() methods
- Used extensively in MeshBuilder

---

## Algorithms and Methods

### 1. Face Culling Algorithm

**Purpose:** Determine which block faces are visible and should be rendered.

**Algorithm:**
```
FOR each block in chunk:
    FOR each potential face (up, down, north, south, east, west):
        adjacent = getAdjacentBlock(position + faceDirection)
        
        IF adjacent is out of bounds:
            renderFace = true  // World edge
        ELSE IF adjacent is air:
            renderFace = true  // Exposed to air
        ELSE IF adjacent is transparent (not solid):
            renderFace = true  // See-through block
        ELSE:
            renderFace = false  // Culled by solid block
        
        IF renderFace:
            collectFace(block, faceDirection)
```

**Complexity:** O(n) where n = number of blocks in chunk

### 2. Vertex Light Sampling Algorithm

**Purpose:** Create smooth lighting gradients by sampling adjacent blocks.

**Algorithm:**
```
FUNCTION sampleVertexLight(face, normalIndex, cornerIndex):
    offsets = getVertexSampleOffsets(normalIndex, cornerIndex)
    
    skyLightSum = 0
    blockLightRSum = 0, blockLightGSum = 0, blockLightBSum = 0
    skyLightSamples = 0
    blockLightSamples = 0
    
    FOR each of 4 sample positions:
        position = blockPosition + offsets[i]
        skyLight = getSkyLight(position)
        blockLightRGB = getBlockLightRGB(position)
        
        IF skyLight > 0:
            skyLightSum += skyLight
            skyLightSamples++
        
        IF any(blockLightRGB) > 0:
            blockLightRSum += blockLightRGB.r
            blockLightGSum += blockLightRGB.g
            blockLightBSum += blockLightRGB.b
            blockLightSamples++
    
    avgSkyLight = skyLightSamples > 0 ? skyLightSum / skyLightSamples : 0
    avgBlockLightR = blockLightSamples > 0 ? blockLightRSum / blockLightSamples : 0
    avgBlockLightG = blockLightSamples > 0 ? blockLightGSum / blockLightSamples : 0
    avgBlockLightB = blockLightSamples > 0 ? blockLightBSum / blockLightSamples : 0
    
    RETURN [avgSkyLight, avgBlockLightR, avgBlockLightG, avgBlockLightB, ao]
```

**Sample Offsets (Top Face, Corner 0):**
```
[
    (0, 1, 0),    // Face normal
    (-1, 1, 0),   // West edge
    (0, 1, -1),   // North edge
    (-1, 1, -1)   // Diagonal
]
```

**Complexity:** O(1) - always 4 samples per vertex

### 3. Model Resolution Algorithm

**Purpose:** Resolve parent model chains and merge properties.

**Algorithm:**
```
FUNCTION resolveBlockModel(name):
    model = loadBlockModelRaw(name)
    IF model is null:
        RETURN null
    
    IF model has parent:
        parent = resolveBlockModel(model.parent)  // Recursive
        model = mergeModels(parent, model)
    
    resolveTextureVariables(model)
    RETURN model

FUNCTION mergeModels(parent, child):
    merged = new BlockModel()
    
    // Merge textures (child overrides)
    merged.textures = parent.textures + child.textures
    
    // Child replaces parent (no merge)
    merged.elements = child.elements OR parent.elements
    merged.display = child.display OR parent.display
    merged.ambientocclusion = child.ambientocclusion OR parent.ambientocclusion
    
    RETURN merged

FUNCTION resolveTextureVariables(model):
    FOR each texture entry:
        WHILE texture starts with '#':
            variable = texture.substring(1)
            texture = model.textures[variable]
    
    FOR each element face:
        IF face.texture starts with '#':
            variable = face.texture.substring(1)
            face.texture = model.textures[variable]
```

**Complexity:** O(d * t) where d = depth of parent chain, t = number of textures

### 4. Isometric Projection Algorithm

**Purpose:** Convert 3D block geometry to 2D isometric screen coordinates.

**Algorithm:**
```
FUNCTION project2Dx(wx, wy, wz, centerX, isoWidth):
    RETURN centerX + (wx - wz) * isoWidth

FUNCTION project2Dy(wx, wy, wz, centerY, isoHeight):
    RETURN centerY - wy * isoHeight - (wx + wz) * isoHeight * 0.5

FUNCTION renderIsometricCube(textures, centerX, centerY, size):
    isoWidth = size * 0.5
    isoHeight = size * 0.5
    
    // Capture 3D geometry
    westFaces = captureWestFace(0, 0, 0)
    northFaces = captureNorthFace(0, 0, 0)
    topFaces = captureTopFace(0, 0, 0)
    
    // Render back-to-front
    FOR each face in [westFaces, northFaces, topFaces]:
        bindTexture(face.texture)
        setColor(face.brightness)
        
        FOR each triangle in face:
            FOR each vertex in triangle:
                screenX = project2Dx(vertex.x, vertex.y, vertex.z, centerX, isoWidth)
                screenY = project2Dy(vertex.x, vertex.y, vertex.z, centerY, isoHeight)
                glTexCoord2f(vertex.u, 1.0 - vertex.v)
                glVertex2f(screenX, screenY)
```

**Complexity:** O(f * t) where f = number of faces, t = triangles per face

### 5. UV Atlas Mapping Algorithm

**Purpose:** Remap model UVs to texture atlas coordinates.

**Algorithm:**
```
FUNCTION remapToAtlas(modelU, modelV, atlasMapping):
    // atlasMapping contains the region in atlas for this texture
    atlasU = atlasMapping.u0 + (atlasMapping.u1 - atlasMapping.u0) * modelU
    atlasV = atlasMapping.v0 + (atlasMapping.v1 - atlasMapping.v0) * modelV
    RETURN (atlasU, atlasV)

EXAMPLE:
    Model UV: (0.5, 0.5)  // Center of texture
    Atlas Mapping: u0=0.25, v0=0.0, u1=0.5, v1=0.25  // 1/4 of atlas
    
    atlasU = 0.25 + (0.5 - 0.25) * 0.5 = 0.25 + 0.125 = 0.375
    atlasV = 0.0 + (0.25 - 0.0) * 0.5 = 0.125
    
    Result: (0.375, 0.125)  // Center of texture region in atlas
```

**Complexity:** O(1) per UV coordinate

### 6. Mesh Building Algorithm

**Purpose:** Convert collected faces into optimized vertex/index arrays.

**Algorithm:**
```
FUNCTION buildChunkMesh(collector):
    vertices = new FloatList()
    indices = new IntList()
    currentVertex = 0
    
    FOR each face direction in [top, bottom, north, south, west, east]:
        faces = collector.getFaces(direction)
        
        FOR each face in faces:
            IF face is custom (stairs/torch):
                currentVertex = addCustomGeometry(face, vertices, indices, currentVertex)
                CONTINUE
            
            // Extract color and UV mapping
            color = extractColor(face)
            uvMapping = getUVMapping(face)
            
            // Sample lighting for 4 corners
            light0 = sampleVertexLight(face, direction, corner0)
            light1 = sampleVertexLight(face, direction, corner1)
            light2 = sampleVertexLight(face, direction, corner2)
            light3 = sampleVertexLight(face, direction, corner3)
            
            // Add 4 vertices
            addVertex(vertices, position0, uv0, color, normal, light0)
            addVertex(vertices, position1, uv1, color, normal, light1)
            addVertex(vertices, position2, uv2, color, normal, light2)
            addVertex(vertices, position3, uv3, color, normal, light3)
            
            // Add 6 indices (2 triangles)
            addQuadIndices(indices, currentVertex)
            currentVertex += 4
    
    RETURN ChunkMeshBuffer(vertices.toArray(), indices.toArray())
```

**Complexity:** O(f) where f = number of visible faces

---

## Performance Considerations

### 1. Memory Optimization

**Primitive Arrays:**
- `FloatList` and `IntList` avoid boxing overhead
- Direct array access eliminates wrapper object allocations
- Reduces GC pressure significantly

**Vertex Format:**
- Packed format minimizes memory transfer
- 17 floats per vertex = 68 bytes
- Typical chunk: ~5000 faces = 20000 vertices = 1.36 MB

### 2. GPU Optimization

**Texture Atlas:**
- Single texture bind per chunk
- Reduces state changes (major performance win)
- Better GPU cache utilization

**Indexed Rendering:**
- Reuses vertices (4 vertices per quad instead of 6)
- 33% reduction in vertex processing
- Smaller memory footprint

**Face Culling:**
- Eliminates 50-80% of faces
- Reduces overdraw significantly
- Saves both CPU and GPU time

### 3. CPU Optimization

**Loop Unrolling:**
- Manual loop unrolling for common operations
- Eliminates iterator allocations
- Indexed access instead of enhanced for

**Cached Calculations:**
- Model resolution cached in ResourceManager
- Texture atlas UVs cached
- Avoids redundant JSON parsing

### 4. Rendering Optimization

**Chunk Batching:**
- Entire chunk rendered in single draw call
- Minimizes state changes
- Maximizes GPU utilization

**Frustum Culling:**
- Chunks outside view frustum not rendered
- Significant savings in large worlds

**LOD Potential:**
- System designed to support level-of-detail
- Can reduce detail for distant chunks
- Not yet implemented

---

## Conclusion

The MattMC rendering system is a sophisticated implementation of Minecraft's JSON-based model format, featuring:

1. **Flexible Model System:** Hierarchical JSON models with inheritance and texture variables
2. **Efficient Rendering:** GPU-accelerated chunk meshes with face culling and texture atlasing
3. **Advanced Lighting:** Smooth vertex lighting with RGB colored block light
4. **Dual Rendering Modes:** Optimized 3D world rendering and isometric 2D item rendering
5. **Extensibility:** Custom geometry builders for complex blocks (stairs, torches)

The architecture cleanly separates concerns:
- Model loading and resolution (ResourceManager, BlockModel)
- Geometry generation (BlockFaceGeometry, StairsGeometryBuilder, TorchGeometryBuilder)
- Mesh optimization (MeshBuilder, BlockFaceCollector)
- Rendering (LevelRenderer, ItemRenderer)

This design enables both high performance (millions of blocks) and flexibility (custom geometry, colored lighting, JSON-driven models), making it suitable for a wide range of voxel rendering applications.

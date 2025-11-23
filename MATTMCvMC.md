# MattMC vs Minecraft: Rendering System Comparison and Migration Guide

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Architecture Comparison](#architecture-comparison)
3. [JSON Model System](#json-model-system)
4. [Coordinate Systems and Geometry](#coordinate-systems-and-geometry)
5. [UV Mapping and Texture Atlas](#uv-mapping-and-texture-atlas)
6. [Face Ordering and Winding](#face-ordering-and-winding)
7. [In-World Block Rendering](#in-world-block-rendering)
8. [Item Rendering](#item-rendering)
9. [Lighting Systems](#lighting-systems)
10. [Migration Guide: Making MattMC Compatible with Minecraft Assets](#migration-guide)
11. [Step-by-Step Instructions](#step-by-step-instructions)

---

## Executive Summary

### Purpose
This document provides an in-depth comparison between MattMC's rendering system and Minecraft Java Edition's rendering system, identifying similarities, differences, and providing actionable steps to make MattMC fully compatible with vanilla Minecraft JSON models and textures.

### Key Goal
Enable any Minecraft block JSON file and texture to work identically in MattMC (except for namespace differences: `minecraft:` → `mattmc:`).

### High-Level Similarities
Both systems:
- Use JSON-based model definitions (blockstates, block models, item models)
- Support parent model inheritance with property merging
- Use texture variable resolution (`#variable` references)
- Define geometry via cuboid elements with UV-mapped faces
- Support face culling for performance
- Use texture atlasing for batch rendering
- Share geometry between block and item rendering

### High-Level Differences

| Aspect | Minecraft Java Edition | MattMC |
|--------|----------------------|--------|
| **Model Baking** | Complex baking system (UnbakedModel → BakedModel) | Direct JSON parsing and resolution |
| **Chunk Rendering** | SectionCompiler + BufferBuilder → VBO batching | BlockFaceCollector + MeshBuilder → VBO |
| **Item Rendering** | 3D perspective with ItemRenderer + display transforms | Isometric 2D projection from 3D geometry |
| **Lighting** | Per-vertex lightmap coordinates (block + sky combined) | Separate sky + RGB block light channels |
| **Face Culling** | Shape-based (uses VoxelShapes) | Solid block boolean check |
| **Model Overrides** | BakedModel.getOverrides() system (bow pulling, compass) | Limited/not implemented |
| **Quad Storage** | BakedQuad objects with packed vertex data | Face-by-face collection with MeshBuilder conversion |

---

## Architecture Comparison

### Minecraft's Architecture

**Model Loading Pipeline:**
```
Resource Packs
    ↓
ModelBakery loads JSON files
    ↓
BlockModel (UnbakedModel) parsed
    ↓
Parent resolution + texture merging
    ↓
ModelBaker.bake() → BakedModel
    ↓
BakedQuads stored per face direction
    ↓
ModelManager caches all baked models
```

**World Rendering Pipeline:**
```
Chunk Section Update
    ↓
SectionCompiler schedules compile task
    ↓
BlockRenderDispatcher gets BakedModel
    ↓
BlockModelRenderer calls getQuads()
    ↓
For each BakedQuad:
  - Face culling check
  - Vertex lighting calculation
  - Color tinting
  - Write to BufferBuilder
    ↓
Finalize → GPU VBO
    ↓
Frame render: bind VBO + glDrawElements
```

**Item Rendering Pipeline:**
```
ItemStack
    ↓
ItemRenderer looks up BakedModel
    ↓
Apply model overrides (bow pulling, etc.)
    ↓
Get ItemDisplayContext (GUI, hand, ground)
    ↓
Apply display transform matrix
    ↓
Render BakedQuads with transform
```

### MattMC's Architecture

**Model Loading Pipeline:**
```
Resource files
    ↓
ResourceManager loads JSON
    ↓
BlockModel parsed directly
    ↓
Recursive parent resolution
    ↓
Texture variable resolution
    ↓
Cache resolved model
    ↓
(No separate "baking" step)
```

**World Rendering Pipeline:**
```
Chunk Update
    ↓
BlockFaceCollector iterates blocks
    ↓
For each block:
  - Check adjacent blocks
  - Collect visible faces
  - Store FaceData with position, color, block reference
    ↓
MeshBuilder processes FaceData:
  - Get UV mapping from TextureAtlas
  - Sample smooth vertex lighting
  - Generate vertices + indices
    ↓
ChunkMeshBuffer uploads to GPU
    ↓
LevelRenderer draws VAO
```

**Item Rendering Pipeline:**
```
ItemStack
    ↓
ItemRenderer loads BlockModel
    ↓
Detect block item (has "all", "top", "side" textures)
    ↓
Capture 3D geometry via VertexCapture
    ↓
Project vertices to 2D isometric coords
    ↓
Render triangles with texture mapping
    ↓
Apply face-specific brightness
    ↓
Apply tint for grass blocks
```

### Key Architectural Differences


**1. Model Baking Concept**
- **Minecraft**: Has explicit "baking" phase that converts UnbakedModel to BakedModel with pre-computed BakedQuads
- **MattMC**: No separate baking; models are used directly after resolution
- **Impact**: Minecraft pre-computes more, MattMC generates geometry dynamically

**2. Quad vs Face Collection**
- **Minecraft**: BakedModel stores pre-generated BakedQuad objects (4 vertices each, packed data)
- **MattMC**: BlockFaceCollector creates FaceData on-demand, MeshBuilder generates vertices
- **Impact**: Minecraft caches more in memory, MattMC is more dynamic

**3. Item Rendering Philosophy**
- **Minecraft**: Uses same 3D perspective rendering for items as blocks, with display transforms
- **MattMC**: Uses isometric 2D projection, fundamentally different approach
- **Impact**: Minecraft items look like miniature 3D blocks; MattMC items are pre-rendered isometric views

---

## JSON Model System

### Blockstate JSON

#### Similarities
Both support:
- `variants` mapping block properties to models
- `model` reference (with namespace)
- `x`, `y`, `z` rotation in 90° increments
- `uvlock` to lock UVs during rotation

#### Example (identical except namespace):
**Minecraft:**
```json
{
  "variants": {
    "": {
      "model": "minecraft:block/dirt"
    }
  }
}
```

**MattMC:**
```json
{
  "variants": {
    "": {
      "model": "mattmc:block/dirt"
    }
  }
}
```

#### Differences

| Feature | Minecraft | MattMC |
|---------|-----------|--------|
| **Multipart** | Fully supported (fences, walls) | Not mentioned in docs (likely not implemented) |
| **Random Variants** | Supports `weight` for randomization | Not mentioned |
| **Apply Section** | Used in multipart for conditional models | N/A |

### Block Model JSON

#### Similarities
Both support:
- `parent` model inheritance
- `textures` map with variable references (`#variable`)
- `elements` array of cuboids
- `ambientocclusion` boolean
- Element properties: `from`, `to`, `faces`, `rotation`, `shade`
- Face properties: `uv`, `texture`, `cullface`, `rotation`, `tintindex`

#### Format Comparison
The JSON format is nearly identical:

**Minecraft Example:**
```json
{
  "parent": "minecraft:block/cube_all",
  "textures": {
    "all": "minecraft:block/stone"
  }
}
```

**MattMC Equivalent:**
```json
{
  "parent": "mattmc:block/cube_all",
  "textures": {
    "all": "mattmc:block/stone"
  }
}
```

#### Differences

| Feature | Minecraft | MattMC |
|---------|-----------|--------|
| **Display Transforms** | Full `display` section with all contexts | Read by model parser, used differently in item rendering |
| **Texture Rotation** | Face `rotation` in 0, 90, 180, 270 | Supported in JSON parsing (check implementation) |
| **Element Rotation** | `rotation` with axis, origin, angle, rescale | Supported (TorchGeometryBuilder uses Y-axis rotation) |
| **Auto UV** | Automatic UV calculation if uv not specified | Unknown (check if implemented) |

### Item Model JSON

#### Similarities
- Both can reference block models via `parent`
- Both support texture definitions
- Both use same JSON structure

#### Differences

| Feature | Minecraft | MattMC |
|---------|-----------|--------|
| **Generated Items** | `item/generated` parent for flat sprites | Handled differently (2D quad rendering) |
| **Handheld Items** | `item/handheld` parent for tools | May not be fully implemented |
| **Model Overrides** | `overrides` array (bow pulling, compass, etc.) | Not mentioned in docs (likely not implemented) |
| **Display Context Usage** | Used directly for transforms | Converted to isometric projection |

### Parent Model Resolution

#### Similarities
Both:
- Recursively resolve parent chains
- Merge textures (child overrides parent)
- Child elements replace parent elements (no merging of element arrays)
- Resolve texture variables after merging

#### Algorithm Comparison

**Minecraft (simplified):**
```java
BakedModel bake(UnbakedModel model, ModelBaker baker, ...) {
    // 1. Resolve parent
    if (model.parent != null) {
        UnbakedModel parent = loadModel(model.parent);
        model = mergeWithParent(parent, model);
    }
    
    // 2. Resolve textures
    model.resolveTextures();
    
    // 3. Generate BakedQuads
    List<BakedQuad> quads = new ArrayList<>();
    for (Element element : model.elements) {
        quads.addAll(bakeFaces(element, sprites));
    }
    
    return new SimpleBakedModel(quads, ...);
}
```

**MattMC:**
```java
BlockModel resolveBlockModel(String name) {
    // 1. Load raw JSON
    BlockModel model = loadBlockModelRaw(name);
    if (model == null) return null;
    
    // 2. Recursive parent resolution
    if (model.parent != null) {
        BlockModel parent = resolveBlockModel(model.parent);
        model = mergeModels(parent, model);
    }
    
    // 3. Resolve texture variables
    resolveTextureVariables(model);
    
    // 4. Cache and return
    return model;
}
```

**Key Difference**: Minecraft generates BakedQuads during baking; MattMC keeps the model as-is and generates geometry on-demand.

---

## Coordinate Systems and Geometry

### Model Space (JSON Coordinates)

#### Complete Agreement
Both use **identical** model space:
- Range: 0 to 16 (1/16th block units)
- Origin: (0, 0, 0) at one corner
- Opposite: (16, 16, 16) at diagonal corner
- Example: `"from": [7, 0, 7]`, `"to": [9, 10, 9]` (torch)

### World Space Conversion

#### Similarities
Both convert model space to world space:
```
world_pos = block_pos + (model_pos / 16.0)
```

#### Differences

| Aspect | Minecraft | MattMC |
|--------|-----------|--------|
| **Coordinate System** | Right-handed (+Y up, +Z south, +X east) | Same |
| **Chunk Size** | 16×384×16 (1.18+) or 16×256×16 (older) | 16×384×16 |
| **Section Height** | 16 blocks | Appears to be 16 blocks based on docs |

### Face Generation from Elements

#### Similarities
Both:
- Generate one quad (4 vertices, 2 triangles) per face
- Use element's `from` and `to` to determine vertex positions
- Support element rotation around an origin point
- Apply face UVs from JSON or auto-calculate

#### Minecraft's Approach
```java
// Simplified from BlockModel
for (Element element : elements) {
    Vec3 from = element.from.scale(1/16f);
    Vec3 to = element.to.scale(1/16f);
    
    for (Direction dir : Direction.values()) {
        if (element.faces.containsKey(dir)) {
            ElementFace face = element.faces.get(dir);
            BakedQuad quad = bakeFace(dir, face, from, to, sprite);
            quads.add(quad);
        }
    }
}
```

#### MattMC's Approach
```java
// From MeshBuilder
for (FaceData face : collector.getFaces(direction)) {
    // Generate 4 vertices for quad
    // Position based on block position + face direction
    // Sample lighting at each vertex
    // Add to vertex buffer
    addQuadVertices(face, vertices, indices);
}
```

**Key Difference**: Minecraft bakes quads with transforms applied; MattMC generates vertices dynamically during chunk meshing.

### Element Rotation

#### Similarities
Both support:
- Rotation axis (x, y, z)
- Rotation origin point
- Rotation angle (typically multiples of 90°)

#### Minecraft
```json
"rotation": {
    "origin": [8, 8, 8],
    "axis": "y",
    "angle": 45,
    "rescale": false
}
```
- Applied during baking
- Vertex positions are transformed
- `rescale` adjusts for non-axis-aligned rotations

#### MattMC
```java
// From TorchGeometryBuilder
switch (degrees) {
    case 90:  rotatedX = -cz; rotatedZ = cx; break;
    case 180: rotatedX = -cx; rotatedZ = -cz; break;
    case 270: rotatedX = cz;  rotatedZ = -cx; break;
}
```
- Applied during geometry generation
- Currently only Y-axis rotation shown
- Used for wall torches

**Compatibility Note**: MattMC needs to support all rotation axes and angles for full Minecraft compatibility.

---

## UV Mapping and Texture Atlas

### UV Coordinate Definition

#### Complete Agreement
Both use **identical** UV format:
- Specified in JSON as `"uv": [u1, v1, u2, v2]`
- Range: 0 to 16 (texture pixel units, assuming 16×16 texture)
- (0, 0) = top-left corner
- (16, 16) = bottom-right corner
- Conversion to 0-1 range: `u_normalized = u / 16.0`

#### Example (identical):
```json
"north": {
    "uv": [0, 0, 16, 16],
    "texture": "#all"
}
```

### Texture Atlas System

#### Similarities
Both:
- Pack multiple textures into a single large atlas
- Reduce texture binding calls for batch rendering
- Remap model UVs to atlas coordinates
- Use TextureAtlasSprite / UVMapping to store region info

#### Minecraft's Atlas
- **Class**: `TextureAtlas` (or `Stitcher` in older versions)
- **Location**: Block and item textures share atlas
- **Sprites**: Each texture becomes a `TextureAtlasSprite` with UV bounds
- **Usage**: BakedQuads reference sprites directly


```java
// Minecraft sprite usage
BakedQuad quad = new BakedQuad(
    vertices,
    tintIndex,
    direction,
    sprite,  // TextureAtlasSprite
    shade
);
```

#### MattMC's Atlas
- **Class**: `TextureAtlas`
- **UVMapping**: Stores u0, v0, u1, v1 for each texture region
- **Usage**: MeshBuilder remaps UVs during vertex generation

```java
// MattMC UV mapping
UVMapping mapping = textureAtlas.getUVMapping(texturePath);
float atlasU = mapping.u0 + (mapping.u1 - mapping.u0) * modelU;
float atlasV = mapping.v0 + (mapping.v1 - mapping.v0) * modelV;
```

#### Differences

| Aspect | Minecraft | MattMC |
|--------|-----------|--------|
| **When Remapped** | During baking (stored in BakedQuads) | During mesh building (per chunk update) |
| **Sprite Object** | Full TextureAtlasSprite with animation support | Simple UVMapping struct |
| **Animated Textures** | Supported (water, lava, portals) | Not mentioned (likely not supported) |
| **Mipmap Support** | Full mipmap generation | Unknown |

### Texture Variable Resolution

#### Complete Agreement
Both:
1. Allow `#variable` references in texture paths
2. Resolve variables within textures map
3. Resolve variables in element faces
4. Follow variable chains recursively
5. Merge parent and child texture maps

#### Example Chain (identical behavior):
```json
// Parent model
{
  "textures": {
    "particle": "#side",
    "side": "block/planks"
  }
}

// Child model
{
  "parent": "parent_model",
  "textures": {
    "side": "block/oak_planks"
  }
}

// Result after resolution
{
  "textures": {
    "particle": "block/oak_planks",  // Resolved through chain
    "side": "block/oak_planks"
  }
}
```

### Texture Path Resolution

#### Differences

| Aspect | Minecraft | MattMC |
|--------|-----------|--------|
| **Namespace Format** | `minecraft:block/stone` | `mattmc:block/stone` |
| **File Path** | `assets/minecraft/textures/block/stone.png` | `assets/textures/block/stone.png` |
| **Namespace Handling** | Explicit namespace in resource location | Namespace stripped before file lookup |

**Critical Migration Point**: MattMC must handle `minecraft:` namespace and convert to appropriate file paths.

---

## Face Ordering and Winding

### Winding Order

#### Complete Agreement
Both use **counter-clockwise (CCW)** winding when viewed from outside the block.
- This is OpenGL standard for front faces
- Enables backface culling (glEnable(GL_CULL_FACE))
- All six faces consistently use CCW

#### Example - Top Face (identical approach):
```
Looking down from +Y:

(x0,z0) -----> (x1,z0)
   |              |
   v              v
(x0,z1) -----> (x1,z1)

Triangle 1: (x0,z0) → (x0,z1) → (x1,z1)  [CCW]
Triangle 2: (x0,z0) → (x1,z1) → (x1,z0)  [CCW]
```

### Face Normals

#### Complete Agreement
Both use identical face normals:
- **Up/Top**: (0, 1, 0)
- **Down/Bottom**: (0, -1, 0)
- **North**: (0, 0, -1)
- **South**: (0, 0, 1)
- **West**: (-1, 0, 0)
- **East**: (1, 0, 0)

### Vertex Order per Face

#### Minecraft's Vertex Ordering
Minecraft's `BlockModel` class generates vertices in a specific order for each direction:

```java
// Simplified from Minecraft source
private static final VertexPosition[][] VERTICES_BY_FACING = {
    // DOWN: 0,1,0,0  1,1,0,0  1,1,1,0  0,1,1,0
    // UP:   0,0,0,1  0,0,1,1  1,0,1,1  1,0,0,1
    // NORTH: 1,0,0,2  1,1,0,2  0,1,0,2  0,0,0,2
    // SOUTH: 0,0,1,3  0,1,1,3  1,1,1,3  1,0,1,3
    // WEST:  0,0,0,4  0,1,0,4  0,1,1,4  0,0,1,4
    // EAST:  1,0,1,5  1,1,1,5  1,1,0,5  1,0,0,5
};
```

Each face has 4 vertices in CCW order, forming 2 triangles via indices [0,1,2] and [0,2,3].

#### MattMC's Vertex Ordering
From `BlockFaceGeometry`:

```java
// Top face (looking down)
glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);  // v0
glTexCoord2f(0, 1); glVertex3f(x0, y1, z1);  // v1
glTexCoord2f(1, 1); glVertex3f(x1, y1, z1);  // v2

glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);  // v0
glTexCoord2f(1, 1); glVertex3f(x1, y1, z1);  // v2
glTexCoord2f(1, 0); glVertex3f(x1, y1, z0);  // v3
```

Triangle 1: v0, v1, v2 (CCW)
Triangle 2: v0, v2, v3 (CCW)

**Compatibility**: Both systems are CCW, so vertex ordering is compatible.

### UV Coordinate Orientation

#### Similarities
Both:
- Map (0, 0) to top-left of texture
- Map (1, 1) to bottom-right of texture
- Flip V coordinate for vertical faces to correct orientation

#### Minecraft
```java
// Vertical face UV application
float minU = sprite.getU(uv[0]);
float minV = sprite.getV(uv[1]);
float maxU = sprite.getU(uv[2]);
float maxV = sprite.getV(uv[3]);

// V coordinates are from sprite, which handles flipping
```

#### MattMC
```java
// From BlockFaceGeometry - vertical faces
glTexCoord2f(0, 1); glVertex3f(x0, y0, z0);  // Bottom-left (V flipped)
glTexCoord2f(0, 0); glVertex3f(x0, y1, z0);  // Top-left
```

**Compatibility**: Both handle UV flipping for vertical faces correctly.

---

## In-World Block Rendering

### Chunk Meshing Pipeline

#### Minecraft's Pipeline
```
Chunk Section Update
    ↓
SectionCompiler.compile()
    ↓
  For each block in 16×16×16 section:
    ↓
    BlockRenderDispatcher.renderBatched()
        ↓
        Get BakedModel from BlockModelShaper
        ↓
        BlockModelRenderer.tesselate()
            ↓
            For each BakedQuad:
                - Check face culling
                - Calculate per-vertex lighting
                - Apply color tinting
                - Write to BufferBuilder
    ↓
Finalize BufferBuilder → VertexBuffer (GPU VBO)
    ↓
Store in SectionRenderDispatcher.RenderSection
```

#### MattMC's Pipeline
```
Chunk Update
    ↓
BlockFaceCollector.collectFaces()
    ↓
  For each block in 16×?×16 chunk:
    ↓
    For each face direction:
        - Check adjacent block (face culling)
        - If visible, create FaceData
        - Store in direction-specific list
    ↓
MeshBuilder.buildMesh()
    ↓
  For each FaceData:
    - Get UV mapping from TextureAtlas
    - Sample vertex lighting (4 corners)
    - Generate 4 vertices + 6 indices
    - Add to FloatList/IntList
    ↓
ChunkMeshBuffer uploads to GPU (VBO + EBO + VAO)
    ↓
LevelRenderer draws with glDrawElements
```

#### Key Differences

| Aspect | Minecraft | MattMC |
|--------|-----------|--------|
| **Data Structure** | BakedQuads (pre-generated) | FaceData (per-chunk generation) |
| **Buffer Type** | BufferBuilder (state machine) | FloatList + IntList (primitive arrays) |
| **Vertex Format** | Position, Color, UV, Light (packed) | Position, UV, Color, Normal, SkyLight, BlockLightRGB, AO (17 floats) |
| **Indexing** | Can use indexed or non-indexed | Always uses indexed (EBO) |
| **Layer Separation** | Solid, Cutout, Translucent, etc. | Not mentioned (single layer?) |

### Face Culling

#### Minecraft
```java
// Simplified from BlockModelRenderer
boolean shouldRenderFace(BlockState state, BlockView world, BlockPos pos, Direction side) {
    BlockPos adjacentPos = pos.offset(side);
    BlockState adjacentState = world.getBlockState(adjacentPos);
    
    // Uses VoxelShape to determine occlusion
    return !Block.shouldDrawSide(state, world, pos, side, adjacentPos);
}

// Block.shouldDrawSide checks:
// 1. If cullface specified, use VoxelShape to test occlusion
// 2. If adjacent shape fully covers this face, cull
// 3. Special handling for translucent blocks
```

**Sophisticated approach**: Uses VoxelShapes to determine if adjacent block covers the face, even for non-full blocks.

#### MattMC
```java
// From BlockFaceCollector
private boolean shouldRenderFace(LevelChunk chunk, int x, int y, int z) {
    // Check Y bounds
    if (y < 0 || y >= HEIGHT) return true;
    
    // Get adjacent block
    Block adjacent = getBlockAcrossChunks(chunk, x, y, z);
    
    // Render if air or not solid
    return adjacent.isAir() || !adjacent.isSolid();
}
```

**Simpler approach**: Binary solid/non-solid check.

#### Compatibility Issue
**Problem**: MattMC's simple solid check won't handle:
- Partial blocks (slabs, stairs) adjacent to full blocks
- Glass blocks adjacent to each other (should cull in Minecraft)
- Leaves adjacent to each other (Minecraft's "smart leaves")

**Solution Required**: Implement VoxelShape-based culling or at minimum a cullface property check.

### Cross-Chunk Face Culling

#### Similarities
Both support querying adjacent chunks for face culling at chunk boundaries.

#### Minecraft
```java
// SectionCompiler has access to neighboring chunk sections
// Via SectionRenderDispatcher.SectionCompiler.getAdjacentSection()
```

#### MattMC
```java
// ChunkNeighborAccessor interface
public interface ChunkNeighborAccessor {
    Block getBlockAcrossChunks(LevelChunk chunk, int x, int y, int z);
}
```

**Compatibility**: Both handle cross-chunk culling; implementation details differ.

### Vertex Format and Layout

#### Minecraft's Vertex Format
```java
// DefaultVertexFormat.BLOCK
// Per vertex:
// - Position: 3 floats (x, y, z)
// - Color: 4 unsigned bytes (RGBA), often 0xFFFFFFFF
// - UV: 2 floats (u, v)
// - Light: 2 shorts packed as int (block light, sky light)
// - Normal: 3 bytes packed (signed, -1 to 1 scaled)

// Total: ~32 bytes per vertex
```

#### MattMC's Vertex Format
```java
// From MeshBuilder
// Per vertex (17 floats = 68 bytes):
// - Position: 3 floats (x, y, z)
// - UV: 2 floats (u, v)
// - Color: 4 floats (r, g, b, a)
// - Normal: 3 floats (nx, ny, nz)
// - SkyLight: 1 float (0-15)
// - BlockLight RGB: 3 floats (r, g, b, each 0-15)
// - AO: 1 float (0-3)
```

#### Compatibility Issue
**Problem**: MattMC uses 68 bytes/vertex vs Minecraft's ~32 bytes/vertex
- Doubles memory bandwidth requirements
- More GPU memory usage

**Optimization Opportunity**: Pack data more efficiently:
- Color can be 4 bytes (RGBA)
- Light can be packed into 1 int (4 bits each for sky + 3×4 bits for RGB)
- Normal can be 3 bytes
- Could reduce to ~40 bytes/vertex


### Render Layers

#### Minecraft
Separates geometry into multiple render layers:
- **Solid**: Opaque blocks (stone, dirt)
- **Cutout**: Fully transparent or opaque pixels (glass, leaves)
- **Cutout Mipped**: Same with mipmaps (grass, flowers)
- **Translucent**: Alpha blending (water, stained glass)

Each layer has separate BufferBuilder and is rendered with different GL state.

#### MattMC
Not explicitly mentioned in docs. Appears to render all geometry in single pass.

#### Compatibility Issue
**Problem**: Without layer separation, translucent blocks may not render correctly (z-fighting, incorrect blending).

**Solution Required**: Implement render layer system with separate buffers per layer.

---

## Item Rendering

### Fundamental Difference

This is the most significant difference between the two systems.

#### Minecraft: 3D Perspective Rendering
- Items are rendered as 3D models in 3D space
- Uses same BakedModel and BakedQuads as world rendering
- Applies display transforms (rotation, translation, scale) per context
- Different transforms for GUI, hand, ground, frame, etc.
- Items in GUI have perspective rendering (rotate with mouse in some cases)

**Example Flow:**
```java
// ItemRenderer
public void render(ItemStack stack, ModelTransformation.Mode mode, ...) {
    BakedModel model = getModel(stack);
    Transformation transform = model.getTransformation().getTransformation(mode);
    
    // Apply transform matrix
    matrices.push();
    transform.apply(matrices);
    
    // Render quads
    for (BakedQuad quad : model.getQuads(...)) {
        renderQuad(quad, ...);
    }
    
    matrices.pop();
}
```

#### MattMC: Isometric 2D Projection
- Items are pre-rendered as 2D isometric projections
- 3D geometry is captured and projected to 2D screen space
- Fixed viewing angle (southwest to northeast)
- Visible faces: West (left), North (right), Top
- Face-specific brightness: Top 100%, West 80%, North 60%

**Example Flow:**
```java
// ItemRenderer
public void renderBlockItem(BlockModel model, int x, int y) {
    // Capture 3D geometry
    VertexCapture capture = new VertexCapture();
    captureGeometry(model, capture);
    
    // Project to 2D isometric
    for (Face face : capture.getFaces()) {
        for (Vertex v : face.vertices) {
            float screenX = project2Dx(v.x, v.y, v.z, x, isoWidth);
            float screenY = project2Dy(v.x, v.y, v.z, y, isoHeight);
            // Render 2D triangle
        }
    }
}
```

#### Projection Formulas (MattMC-specific)
```java
// X: diagonal axis (SW to NE)
screenX = centerX + (wx - wz) * isoWidth

// Y: vertical + diagonal
screenY = centerY - wy * isoHeight - (wx + wz) * isoHeight * 0.5
```

### Compatibility Impact

**Critical Issue**: This is the **biggest barrier** to drop-in Minecraft asset compatibility.

**Problem**: 
1. Minecraft's `display` transforms cannot be directly applied to isometric projection
2. Item model overrides (bow pulling, compass) rely on 3D rendering
3. Custom item models may look wrong in isometric view
4. Handheld items in first-person view would need different handling

**Possible Solutions**:

**Option 1: Adopt Minecraft's 3D Item Rendering** (Recommended for full compatibility)
- Refactor ItemRenderer to use 3D perspective rendering
- Implement display transform system
- Render items using same approach as blocks (with transforms)
- Pros: Full compatibility with Minecraft item models
- Cons: Significant refactor required

**Option 2: Keep Isometric, Add Compatibility Layer**
- Parse Minecraft display transforms
- Convert transforms to isometric projection adjustments
- Pre-render items at different angles for different contexts
- Pros: Maintains unique MattMC style
- Cons: Complex conversion logic, may not look "Minecraft-like"

**Option 3: Hybrid Approach**
- Use 3D rendering for in-hand items (first/third person)
- Use isometric for GUI/inventory items
- Pros: Best of both worlds
- Cons: Most complex implementation

### Display Transform Contexts

#### Minecraft's Contexts
```java
public enum ItemDisplayContext {
    NONE,
    THIRD_PERSON_LEFT_HAND,
    THIRD_PERSON_RIGHT_HAND,
    FIRST_PERSON_LEFT_HAND,
    FIRST_PERSON_RIGHT_HAND,
    HEAD,
    GUI,
    GROUND,
    FIXED  // Item frames
}
```

Each context has a `ModelTransformation` with:
- `rotation`: [x, y, z] degrees
- `translation`: [x, y, z] in 1/16 block units
- `scale`: [x, y, z]

**Example from Minecraft's `block.json` base model:**
```json
"display": {
    "gui": {
        "rotation": [30, 225, 0],
        "translation": [0, 0, 0],
        "scale": [0.625, 0.625, 0.625]
    },
    "ground": {
        "rotation": [0, 0, 0],
        "translation": [0, 3, 0],
        "scale": [0.25, 0.25, 0.25]
    },
    "firstperson_righthand": {
        "rotation": [0, 45, 0],
        "translation": [0, 0, 0],
        "scale": [0.40, 0.40, 0.40]
    }
}
```

#### MattMC's Approach
- Isometric projection ignores display transforms
- Fixed viewing angle for all contexts
- Position adjustments for hotbar vs inventory:
  - Block items in inventory: `y + 18f`
  - Flat items in hotbar: `y - 18f`

**Compatibility Issue**: Cannot directly use Minecraft's display transforms.

### Item Model Overrides

#### Minecraft
```json
"overrides": [
    {
        "predicate": {"pulling": 1},
        "model": "item/bow_pulling_0"
    },
    {
        "predicate": {"pulling": 1, "pull": 0.65},
        "model": "item/bow_pulling_1"
    }
]
```

Predicates can check:
- `pulling`, `pull` (bows)
- `damage`, `damaged` (tools)
- `cast` (fishing rods)
- `angle` (compasses, clocks)
- Custom predicates (modded)

#### MattMC
Not mentioned in documentation. Likely not implemented.

**Compatibility Issue**: Dynamic items (bows, compasses, fishing rods) won't work correctly.

**Solution Required**: Implement model override system that can switch models based on ItemStack properties.

### Flat Items (Non-Block Items)

#### Minecraft
```json
{
    "parent": "item/generated",
    "textures": {
        "layer0": "item/diamond"
    }
}
```
- `item/generated` parent creates a flat quad in 3D space
- Can have multiple layers (layer0, layer1, etc.) for compound items
- Rendered with perspective in 3D

#### MattMC
```java
// From docs: "Flat Items (non-blocks)"
// Get "layer0" texture
// Render as 2D quad (no projection)
// Scale to match visual size of isometric blocks
```

**Similarity**: Both render flat items as single quads.
**Difference**: Minecraft renders in 3D space with perspective; MattMC renders as pure 2D.

### Tinting

#### Similarities
Both support color tinting via `tintindex` in face definitions.

#### Minecraft
```java
// ItemRenderer
int tintColor = itemColors.getColor(stack, tintIndex);
renderQuad(quad, tintColor);
```
- `ItemColors` registry maps items to color providers
- Grass, leaves, leather armor, potions, etc. have tint providers
- Biome-dependent for grass/leaves

#### MattMC
```java
// From docs: "Tint Application"
int topTintColor = itemModel.getTints().get(0).getTintColor();
float r = ((topTintColor >> 16) & 0xFF) / 255.0f;
// Apply to top face only
```
- Supports tinting for grass blocks
- Applied to top face during isometric rendering

**Compatibility**: Core tinting concept is compatible, but needs full ItemColors-like system for all tintable items.

---

## Lighting Systems

### Sky Light

#### Similarities
Both:
- Use 4 bits per block (0-15 levels)
- Propagate down from sky
- Decrease by 1 per block as light travels

#### Differences

| Aspect | Minecraft | MattMC |
|--------|-----------|--------|
| **Storage** | 4 bits per block in chunk sections | 4 bits per block in chunks |
| **Propagation** | Queue-based flood fill | Similar flood fill |
| **Time of Day** | Varies with sun angle (15 at noon, 4 at night) | Not mentioned (constant?) |

### Block Light

#### Minecraft: Single-Channel Intensity
```java
// Block light: 4 bits per block (0-15)
// Light sources (torch, glowstone) emit light at specific level
// Propagates omni-directionally, decreasing by 1 per block
// All light is "white" - no color information
```

**Example:**
- Torch: level 14
- Glowstone: level 15
- Redstone torch: level 7

#### MattMC: RGB Block Light
```java
// Block light: 16 bits per block
// - 4 bits R (0-15)
// - 4 bits G (0-15)
// - 4 bits B (0-15)
// - 4 bits I (intensity, 0-15)
// Allows colored light sources
```

**Example:**
- Torch: R=14, G=7, B=0, I=14 (orange)
- RGB preserved during propagation
- Intensity decreases by 1 per block

**Major Difference**: MattMC supports colored block light; Minecraft does not (without mods).

#### Compatibility Issue
**Problem**: Minecraft assets assume white block light. MattMC's RGB system is an enhancement.

**Solution**: 
- Parse Minecraft light values as white light (R=G=B=value, I=value)
- Add optional RGB override in MattMC-specific files
- Maintain compatibility with vanilla light values

### Smooth Vertex Lighting

#### Similarities
Both sample adjacent blocks to create smooth lighting gradients.

#### Minecraft
```java
// For each vertex of a quad:
// 1. Sample light from up to 4 adjacent blocks
// 2. Consider solid blocks (AO)
// 3. Calculate average brightness
// 4. Store in vertex lightmap coordinate

// Lightmap is a 2D texture: [blockLight][skyLight]
// Shader looks up lightmap to get final brightness
```

**Lightmap System:**
- 16×16 texture (or larger)
- X axis: block light (0-15)
- Y axis: sky light (0-15)
- Updated each frame for time of day changes
- Shader: `texture2D(lightmap, vec2(blockLight/15, skyLight/15))`

#### MattMC
```java
// For each vertex of a face:
// 1. Identify 4 adjacent sample positions
// 2. Sample sky light and RGB block light at each
// 3. Average only non-zero samples
// 4. Store in vertex attributes (4 light values)

// Vertex shader combines lights:
float skyBrightness = skyLight / 15.0;
vec3 blockBrightness = blockLightRGB / 15.0;
vec3 totalLight = skyBrightness * sunColor + blockBrightness;
vec3 finalColor = albedo * totalLight;
```

**Direct Calculation:**
- No lightmap texture
- Light values passed directly to shader
- Shader does arithmetic to combine

#### Compatibility
Both approaches achieve similar visual results, just different implementation.

**MattMC Advantage**: RGB block light allows colored lighting effects.
**Minecraft Advantage**: Lightmap allows easy gamma/brightness adjustments.

### Ambient Occlusion

#### Minecraft
```java
// For each vertex:
// Count solid blocks among the 3-4 neighbors
// Apply darkening based on count:
//   0 solid: 1.0 (full brightness)
//   1 solid: 0.8
//   2 solid: 0.6
//   3 solid: 0.2 (darkest)
```

Produces soft shadowing in corners and edges.

#### MattMC
```java
// From docs: "Currently placeholder (ao = 0.0)"
// Designed for future implementation
// Vertex format includes AO field
```

**Compatibility Issue**: MattMC doesn't calculate AO yet.

**Solution Required**: Implement AO calculation using Minecraft's algorithm.

### Directional Shading

#### Minecraft
Applies directional shading based on face normal:
```java
// Approximate values from observation
Top:    1.0 (100%)
Bottom: 0.5 (50%)
North:  0.8 (80%)
South:  0.8 (80%)
West:   0.6 (60%)
East:   0.6 (60%)
```

#### MattMC
```java
// From docs: identical values
Top:    1.0 (100%)
Bottom: 0.5 (50%)
North:  0.8 (80%)
South:  0.8 (80%)
West:   0.6 (60%)
East:   0.6 (60%)
```

**Complete Agreement**: Directional shading is identical.

---

## Migration Guide

This section provides the roadmap for making MattMC fully compatible with vanilla Minecraft JSON models and textures.

### Compatibility Matrix

| Feature | MC Format | MattMC Support | Priority | Effort |
|---------|-----------|----------------|----------|--------|
| **Blockstate JSON** | | | | |
| - Simple variants | ✓ | ✓ | - | - |
| - Multipart | ✓ | ✗ | HIGH | MEDIUM |
| - Weighted random | ✓ | ✗ | LOW | LOW |
| **Block Model JSON** | | | | |
| - Parent inheritance | ✓ | ✓ | - | - |
| - Elements/faces | ✓ | ✓ | - | - |
| - Texture variables | ✓ | ✓ | - | - |
| - Element rotation (all axes) | ✓ | Partial (Y only) | HIGH | MEDIUM |
| - Face rotation | ✓ | ? | MEDIUM | LOW |
| - Auto UV | ✓ | ? | MEDIUM | LOW |
| **Item Model JSON** | | | | |
| - Block parent | ✓ | ✓ | - | - |
| - item/generated | ✓ | ✓ | - | - |
| - Model overrides | ✓ | ✗ | HIGH | HIGH |
| - Display transforms | ✓ | ✗ (uses isometric) | CRITICAL | VERY HIGH |
| **Rendering** | | | | |
| - Face culling (shape-based) | ✓ | Partial (solid-only) | HIGH | MEDIUM |
| - Render layers | ✓ | ✗ | HIGH | MEDIUM |
| - Ambient occlusion | ✓ | ✗ | MEDIUM | MEDIUM |
| - Animated textures | ✓ | ✗ | LOW | HIGH |
| **Item Rendering** | | | | |
| - 3D perspective | ✓ | ✗ (isometric 2D) | CRITICAL | VERY HIGH |
| - Context transforms | ✓ | ✗ | CRITICAL | HIGH |
| **Namespace Handling** | | | | |
| - minecraft: namespace | ✓ | ✗ (mattmc: only) | CRITICAL | LOW |

### Critical Path Issues

These must be resolved for basic compatibility:

1. **Namespace Handling** (Priority: CRITICAL, Effort: LOW)
   - Accept `minecraft:` namespace in JSON files
   - Map to appropriate file paths

2. **3D Item Rendering** (Priority: CRITICAL, Effort: VERY HIGH)
   - Replace isometric projection with 3D perspective
   - Implement display transform system
   - This is the biggest change required

3. **Display Transforms** (Priority: CRITICAL, Effort: HIGH)
   - Parse and apply display transforms from JSON
   - Support all ItemDisplayContext values
   - Apply transforms before rendering

4. **Shape-Based Face Culling** (Priority: HIGH, Effort: MEDIUM)
   - Implement VoxelShape system or equivalent
   - Use cullface property from models
   - Handle partial blocks correctly

5. **Multipart Blockstates** (Priority: HIGH, Effort: MEDIUM)
   - Parse multipart format
   - Evaluate when conditions
   - Combine multiple models

6. **Model Overrides** (Priority: HIGH, Effort: HIGH)
   - Parse overrides from item models
   - Evaluate predicates on ItemStack
   - Switch models dynamically

7. **Render Layers** (Priority: HIGH, Effort: MEDIUM)
   - Separate geometry by render type
   - Render in correct order (solid, cutout, translucent)
   - Handle transparency correctly

8. **Ambient Occlusion** (Priority: MEDIUM, Effort: MEDIUM)
   - Implement AO calculation
   - Sample solid neighbors at vertices
   - Apply darkening factor


---

## Step-by-Step Instructions

This section provides detailed, actionable instructions that can be fed back to implement Minecraft compatibility.

### Phase 1: Namespace Handling (CRITICAL - Week 1)

**Goal**: Accept `minecraft:` namespace in all JSON files and map to correct file paths.

**Step 1.1: Update ResourceManager Path Resolution**

**File**: `src/main/java/mattmc/client/resources/ResourceManager.java`

**Current behavior**:
```java
// Assumes mattmc: namespace
String texturePath = "assets/textures/" + textureName + ".png";
```

**Required changes**:
```java
private String resolveTexturePath(String textureReference) {
    // Handle namespace:path format
    String namespace, path;
    if (textureReference.contains(":")) {
        String[] parts = textureReference.split(":", 2);
        namespace = parts[0];
        path = parts[1];
    } else {
        namespace = "mattmc";  // Default
        path = textureReference;
    }
    
    // Map minecraft: namespace to same assets directory
    if (namespace.equals("minecraft")) {
        // Both minecraft: and mattmc: use same texture directory
        return "assets/textures/" + path + ".png";
    } else if (namespace.equals("mattmc")) {
        return "assets/textures/" + path + ".png";
    } else {
        // Support mod namespaces in future
        return "assets/" + namespace + "/textures/" + path + ".png";
    }
}
```

**Step 1.2: Update Model Path Resolution**

**Current behavior**:
```java
// Loads models from hardcoded path
BlockModel model = loadJSON("assets/models/" + modelName + ".json");
```

**Required changes**:
```java
private BlockModel loadBlockModel(String modelReference) {
    // Parse namespace:path
    String namespace, path;
    if (modelReference.contains(":")) {
        String[] parts = modelReference.split(":", 2);
        namespace = parts[0];
        path = parts[1];
    } else {
        namespace = "mattmc";
        path = modelReference;
    }
    
    // Map to file path (both namespaces use same directory for now)
    String filePath = "assets/models/" + path + ".json";
    return loadJSON(filePath, BlockModel.class);
}
```

**Step 1.3: Test with Minecraft Assets**

1. Copy some Minecraft block models to test:
   ```bash
   # Example: dirt block
   cp minecraft_assets/minecraft/models/block/cube_all.json assets/models/block/
   cp minecraft_assets/minecraft/models/block/dirt.json assets/models/block/
   ```

2. Create test blockstate that uses minecraft: namespace:
   ```json
   {
     "variants": {
       "": {
         "model": "minecraft:block/dirt"
       }
     }
   }
   ```

3. Verify it loads correctly.

**Validation**:
- [ ] Loads models with `minecraft:` namespace
- [ ] Loads models with `mattmc:` namespace
- [ ] Texture paths resolve correctly
- [ ] Parent models resolve across namespaces

---

### Phase 2: Shape-Based Face Culling (HIGH - Week 2-3)

**Goal**: Implement proper face culling using cullface property from models.

**Step 2.1: Add Shape Information to Blocks**

**File**: `src/main/java/mattmc/world/level/block/Block.java`

**Add method**:
```java
public boolean isFullCube() {
    // Override in subclasses for partial blocks
    return true;  // Default: full cube
}

public boolean isCullFace(Direction face) {
    // By default, full cubes cull all faces
    return isFullCube();
}
```

**Step 2.2: Update Stairs, Slabs, etc.**

```java
public class StairsBlock extends Block {
    @Override
    public boolean isFullCube() {
        return false;
    }
    
    @Override
    public boolean isCullFace(Direction face) {
        // Stairs don't cull any faces
        return false;
    }
}

public class SlabBlock extends Block {
    @Override
    public boolean isFullCube() {
        return false;
    }
    
    @Override
    public boolean isCullFace(Direction face) {
        // Bottom slab only culls down, top slab only culls up
        BlockState state = getState();
        SlabType type = state.get(TYPE);
        if (type == SlabType.BOTTOM && face == Direction.DOWN) return true;
        if (type == SlabType.TOP && face == Direction.UP) return true;
        if (type == SlabType.DOUBLE) return true;  // Full block
        return false;
    }
}
```

**Step 2.3: Update Face Culling Logic**

**File**: `src/main/java/mattmc/client/renderer/block/BlockFaceCollector.java`

**Current**:
```java
private boolean shouldRenderFace(LevelChunk chunk, int x, int y, int z) {
    Block adjacent = getBlockAcrossChunks(chunk, x, y, z);
    return adjacent.isAir() || !adjacent.isSolid();
}
```

**Updated**:
```java
private boolean shouldRenderFace(Block block, Direction face, 
                                   LevelChunk chunk, int x, int y, int z) {
    Block adjacent = getBlockAcrossChunks(chunk, x, y, z);
    
    // Always render if adjacent is air
    if (adjacent.isAir()) return true;
    
    // Check if this face has cullface in model
    String cullface = getFaceCullface(block, face);
    if (cullface == null) return true;  // No culling specified
    
    // Check if adjacent block can cull this face
    Direction cullDirection = Direction.valueOf(cullface.toUpperCase());
    return !adjacent.isCullFace(cullDirection.getOpposite());
}

private String getFaceCullface(Block block, Direction face) {
    // Get block's model
    BlockModel model = resourceManager.getBlockModel(block);
    if (model == null || model.elements == null) return null;
    
    // Find face in model elements
    for (ModelElement element : model.elements) {
        ElementFace faceData = element.faces.get(face.getName());
        if (faceData != null) {
            return faceData.cullface;
        }
    }
    return null;
}
```

**Validation**:
- [ ] Full blocks adjacent to full blocks cull correctly
- [ ] Glass blocks adjacent to glass cull correctly
- [ ] Stairs adjacent to full blocks don't cull incorrectly
- [ ] Slabs adjacent to full blocks cull correctly

---

### Phase 3: Render Layers (HIGH - Week 4-5)

**Goal**: Separate geometry by render type for correct transparency rendering.

**Step 3.1: Define Render Layer Enum**

**New file**: `src/main/java/mattmc/client/renderer/RenderLayer.java`

```java
package mattmc.client.renderer;

public enum RenderLayer {
    SOLID,       // Opaque blocks: stone, dirt, planks
    CUTOUT,      // Binary transparency: glass, leaves
    CUTOUT_MIPPED,  // Same as cutout with mipmaps: grass, flowers
    TRANSLUCENT;    // Alpha blending: water, stained glass
    
    public static RenderLayer getBlockLayer(Block block) {
        // Default mapping based on block type
        if (block instanceof GlassBlock) return CUTOUT;
        if (block instanceof LeavesBlock) return CUTOUT_MIPPED;
        if (block instanceof WaterBlock) return TRANSLUCENT;
        // TODO: Read from block properties or model
        return SOLID;
    }
}
```

**Step 3.2: Separate Collection by Layer**

**File**: `src/main/java/mattmc/client/renderer/block/BlockFaceCollector.java`

**Add**:
```java
private Map<RenderLayer, List<FaceData>> facesByLayer = new EnumMap<>(RenderLayer.class);

public void collectFaces(LevelChunk chunk) {
    // Initialize maps
    for (RenderLayer layer : RenderLayer.values()) {
        facesByLayer.put(layer, new ArrayList<>());
    }
    
    // Collect faces per layer
    for (int y = 0; y < HEIGHT; y++) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                Block block = chunk.getBlock(x, y, z);
                if (block.isAir()) continue;
                
                RenderLayer layer = RenderLayer.getBlockLayer(block);
                List<FaceData> layerFaces = facesByLayer.get(layer);
                
                // Collect faces for this block
                collectBlockFaces(block, x, y, z, chunk, layerFaces);
            }
        }
    }
}

public List<FaceData> getFaces(RenderLayer layer, Direction direction) {
    // Return faces for specific layer and direction
    return facesByLayer.get(layer).stream()
        .filter(f -> f.direction == direction)
        .collect(Collectors.toList());
}
```

**Step 3.3: Build Separate Meshes per Layer**

**File**: `src/main/java/mattmc/client/renderer/ChunkMeshBuffer.java`

**Update to**:
```java
public class ChunkMeshBuffer {
    private Map<RenderLayer, LayerMesh> meshes = new EnumMap<>(RenderLayer.class);
    
    public static class LayerMesh {
        int vbo, ebo, vao;
        int indexCount;
        // ... existing fields
    }
    
    public void buildMeshes(BlockFaceCollector collector) {
        for (RenderLayer layer : RenderLayer.values()) {
            MeshBuilder builder = new MeshBuilder();
            
            // Build mesh for this layer
            for (Direction dir : Direction.values()) {
                List<FaceData> faces = collector.getFaces(layer, dir);
                builder.addFaces(faces, dir);
            }
            
            // Upload to GPU
            LayerMesh mesh = builder.finalize();
            meshes.put(layer, mesh);
        }
    }
    
    public void render(RenderLayer layer) {
        LayerMesh mesh = meshes.get(layer);
        if (mesh != null && mesh.indexCount > 0) {
            glBindVertexArray(mesh.vao);
            glDrawElements(GL_TRIANGLES, mesh.indexCount, GL_UNSIGNED_INT, 0);
        }
    }
}
```

**Step 3.4: Render Layers in Correct Order**

**File**: `src/main/java/mattmc/client/renderer/LevelRenderer.java`

```java
public void renderChunks() {
    // 1. Render solid layer (opaque, front-to-back for depth testing)
    setupSolidState();
    for (ChunkMeshBuffer chunk : visibleChunks) {
        chunk.render(RenderLayer.SOLID);
    }
    
    // 2. Render cutout layer (binary transparency)
    setupCutoutState();
    for (ChunkMeshBuffer chunk : visibleChunks) {
        chunk.render(RenderLayer.CUTOUT);
        chunk.render(RenderLayer.CUTOUT_MIPPED);
    }
    
    // 3. Render translucent layer (alpha blending, back-to-front)
    setupTranslucentState();
    List<ChunkMeshBuffer> sortedChunks = sortByDistance(visibleChunks);
    for (ChunkMeshBuffer chunk : sortedChunks) {
        chunk.render(RenderLayer.TRANSLUCENT);
    }
}

private void setupSolidState() {
    glEnable(GL_DEPTH_TEST);
    glDepthMask(true);
    glDisable(GL_BLEND);
    glEnable(GL_CULL_FACE);
}

private void setupCutoutState() {
    glEnable(GL_DEPTH_TEST);
    glDepthMask(true);
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
    glEnable(GL_ALPHA_TEST);  // Or use discard in shader
    glAlphaFunc(GL_GREATER, 0.5f);
}

private void setupTranslucentState() {
    glEnable(GL_DEPTH_TEST);
    glDepthMask(false);  // Don't write to depth buffer
    glEnable(GL_BLEND);
    glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
}
```

**Validation**:
- [ ] Solid blocks render opaque
- [ ] Glass renders with transparency
- [ ] Leaves render with cutout
- [ ] Water renders with alpha blending
- [ ] No z-fighting on transparent blocks
- [ ] Depth sorting works for translucent

---

### Phase 4: Ambient Occlusion (MEDIUM - Week 6)

**Goal**: Implement AO calculation for soft corner shadowing.

**Step 4.1: Implement AO Sampling**

**File**: `src/main/java/mattmc/client/renderer/lighting/VertexLightSampler.java`

**Add method**:
```java
private float calculateAO(LevelChunk chunk, int x, int y, int z, 
                           Direction face, int cornerIndex) {
    // Get 3 positions around this corner
    int[][] offsets = getAOOffsets(face, cornerIndex);
    
    int solidCount = 0;
    for (int[] offset : offsets) {
        int nx = x + offset[0];
        int ny = y + offset[1];
        int nz = z + offset[2];
        
        Block block = getBlockAcrossChunks(chunk, nx, ny, nz);
        if (!block.isAir() && block.isSolid()) {
            solidCount++;
        }
    }
    
    // Convert to AO value (0 = full brightness, 3 = darkest)
    return (float) solidCount;
}

private int[][] getAOOffsets(Direction face, int cornerIndex) {
    // Return 3 positions (2 edges + 1 corner) for AO sampling
    // Example for top face, corner 0 (x0, z0):
    if (face == Direction.UP && cornerIndex == 0) {
        return new int[][] {
            {0, 1, 0},    // Up (face direction)
            {-1, 1, 0},   // West edge
            {0, 1, -1},   // North edge
            {-1, 1, -1}   // Northwest corner
        };
    }
    // ... implement for all faces and corners
}
```

**Step 4.2: Apply AO in Mesh Builder**

**File**: `src/main/java/mattmc/client/renderer/block/MeshBuilder.java`

```java
// When sampling lighting for vertex:
float[] light = vertexLightSampler.sampleVertexLight(face, normalIndex, cornerIndex);
float skyLight = light[0];
float blockLightR = light[1];
float blockLightG = light[2];
float blockLightB = light[3];
float ao = light[4];  // Now populated instead of 0.0

// Add to vertex buffer
vertices.add(ao);  // AO value
```

**Step 4.3: Update Shader to Use AO**

**File**: `assets/shaders/block_vertex.glsl`

```glsl
attribute float ao;

varying float v_ao;

void main() {
    // ... existing code ...
    v_ao = ao;
}
```

**File**: `assets/shaders/block_fragment.glsl`

```glsl
varying float v_ao;

void main() {
    // ... existing lighting calculation ...
    
    // Apply AO darkening
    float aoDarkening = 1.0 - (v_ao / 3.0) * 0.8;  // 0-3 range, up to 80% darkening
    finalColor *= aoDarkening;
    
    gl_FragColor = vec4(finalColor, 1.0);
}
```

**Validation**:
- [ ] Corners appear darker when surrounded by blocks
- [ ] Open corners remain bright
- [ ] Smooth gradient between AO levels
- [ ] Matches Minecraft's AO appearance

---

### Phase 5: Element Rotation Support (HIGH - Week 7)

**Goal**: Support element rotation on all axes, not just Y.

**Step 5.1: Parse Rotation from JSON**

**File**: `src/main/java/mattmc/client/resources/model/ModelElement.java`

**Ensure rotation is parsed**:
```json
"rotation": {
    "origin": [8, 8, 8],
    "axis": "y",
    "angle": 45,
    "rescale": false
}
```

```java
public static class ModelRotation {
    public float[] origin;  // [x, y, z]
    public String axis;     // "x", "y", or "z"
    public float angle;     // Degrees
    public boolean rescale; // Adjust for non-axis-aligned
}
```

**Step 5.2: Implement 3D Rotation**

**New file**: `src/main/java/mattmc/client/renderer/geometry/RotationUtil.java`

```java
public class RotationUtil {
    public static float[] rotateVertex(float x, float y, float z, ModelRotation rotation) {
        if (rotation == null) return new float[]{x, y, z};
        
        // Translate to origin
        float ox = x - rotation.origin[0]/16f;
        float oy = y - rotation.origin[1]/16f;
        float oz = z - rotation.origin[2]/16f;
        
        // Rotate
        float rad = (float) Math.toRadians(rotation.angle);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        
        float nx, ny, nz;
        switch (rotation.axis) {
            case "x":
                nx = ox;
                ny = oy * cos - oz * sin;
                nz = oy * sin + oz * cos;
                break;
            case "y":
                nx = ox * cos + oz * sin;
                ny = oy;
                nz = -ox * sin + oz * cos;
                break;
            case "z":
                nx = ox * cos - oy * sin;
                ny = ox * sin + oy * cos;
                nz = oz;
                break;
            default:
                return new float[]{x, y, z};
        }
        
        // Translate back
        return new float[]{
            nx + rotation.origin[0]/16f,
            ny + rotation.origin[1]/16f,
            nz + rotation.origin[2]/16f
        };
    }
    
    public static float[] rotateNormal(float nx, float ny, float nz, ModelRotation rotation) {
        // Rotate normal vector (no translation)
        // ... similar to rotateVertex but without origin offset
    }
}
```

**Step 5.3: Apply Rotation During Geometry Generation**

**File**: Geometry builders (TorchGeometryBuilder, etc.)

```java
// When generating vertices for element faces:
for (ModelElement element : model.elements) {
    for (Direction dir : Direction.values()) {
        ElementFace face = element.faces.get(dir.getName());
        if (face != null) {
            // Generate base vertices
            float[] v0 = calculateVertexPosition(element, dir, 0);
            float[] v1 = calculateVertexPosition(element, dir, 1);
            float[] v2 = calculateVertexPosition(element, dir, 2);
            float[] v3 = calculateVertexPosition(element, dir, 3);
            
            // Apply rotation if specified
            if (element.rotation != null) {
                v0 = RotationUtil.rotateVertex(v0[0], v0[1], v0[2], element.rotation);
                v1 = RotationUtil.rotateVertex(v1[0], v1[1], v1[2], element.rotation);
                v2 = RotationUtil.rotateVertex(v2[0], v2[1], v2[2], element.rotation);
                v3 = RotationUtil.rotateVertex(v3[0], v3[1], v3[2], element.rotation);
            }
            
            // Add to mesh
            addQuad(v0, v1, v2, v3, face.uv, face.texture);
        }
    }
}
```

**Validation**:
- [ ] Torches rotate correctly on walls
- [ ] Rotated elements (e.g., anvils) render correctly
- [ ] All rotation axes (x, y, z) work
- [ ] Rescale option works for non-90° angles


---

### Phase 6: 3D Item Rendering System (CRITICAL - Week 8-12)

**Goal**: Replace isometric 2D item rendering with Minecraft-compatible 3D perspective rendering.

**This is the largest change required for full compatibility.**

**Step 6.1: Create ItemDisplayContext Enum**

**New file**: `src/main/java/mattmc/client/renderer/item/ItemDisplayContext.java`

```java
package mattmc.client.renderer.item;

public enum ItemDisplayContext {
    NONE,
    THIRD_PERSON_LEFT_HAND,
    THIRD_PERSON_RIGHT_HAND,
    FIRST_PERSON_LEFT_HAND,
    FIRST_PERSON_RIGHT_HAND,
    HEAD,
    GUI,
    GROUND,
    FIXED;  // Item frames
    
    public String getJsonKey() {
        return this.name().toLowerCase();
    }
}
```

**Step 6.2: Parse Display Transforms from Model JSON**

**File**: `src/main/java/mattmc/client/resources/model/ModelDisplay.java`

```java
public class ModelDisplay {
    public Map<String, ModelTransform> transforms;  // Context -> transform
    
    public static class ModelTransform {
        public float[] rotation;     // [x, y, z] degrees
        public float[] translation;  // [x, y, z] in 1/16 block units
        public float[] scale;        // [x, y, z]
        
        public ModelTransform() {
            rotation = new float[]{0, 0, 0};
            translation = new float[]{0, 0, 0};
            scale = new float[]{1, 1, 1};
        }
    }
    
    public ModelTransform getTransform(ItemDisplayContext context) {
        ModelTransform transform = transforms.get(context.getJsonKey());
        return transform != null ? transform : new ModelTransform();
    }
}
```

**Update BlockModel to include display**:
```java
public class BlockModel {
    // ... existing fields ...
    public ModelDisplay display;  // Add this
    
    // Update JSON parsing to include display section
}
```

**Step 6.3: Refactor ItemRenderer to Use 3D Rendering**

**File**: `src/main/java/mattmc/client/renderer/ItemRenderer.java`

**Major refactor**:
```java
public class ItemRenderer {
    private final ResourceManager resourceManager;
    private final TextureAtlas textureAtlas;
    
    public void renderItem(ItemStack stack, ItemDisplayContext context, 
                           int screenX, int screenY) {
        BlockModel model = getItemModel(stack);
        if (model == null) return;
        
        // Setup 3D rendering matrices
        setupItemRenderState(context, screenX, screenY);
        
        // Apply display transform
        applyDisplayTransform(model, context);
        
        // Render model as 3D geometry
        renderModel3D(model, stack);
        
        // Restore state
        restoreRenderState();
    }
    
    private void setupItemRenderState(ItemDisplayContext context, int x, int y) {
        GL11.glPushMatrix();
        
        // Setup viewport for item rendering
        if (context == ItemDisplayContext.GUI) {
            // GUI items: orthographic projection centered on item
            GL11.glTranslatef(x + 8, y + 8, 100);
            
            // Setup orthographic projection for GUI
            GL11.glMatrixMode(GL11.GL_PROJECTION);
            GL11.glPushMatrix();
            GL11.glLoadIdentity();
            GL11.glOrtho(-8, 8, -8, 8, -100, 100);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);
        } else if (context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            // First person: perspective projection, positioned in front of camera
            // ... setup first person transform ...
        }
        // ... other contexts ...
    }
    
    private void applyDisplayTransform(BlockModel model, ItemDisplayContext context) {
        if (model.display == null) return;
        
        ModelDisplay.ModelTransform transform = model.display.getTransform(context);
        
        // Apply translation (convert from 1/16 block units to pixels)
        GL11.glTranslatef(
            transform.translation[0] / 16f * 16,  // Scale to pixels
            transform.translation[1] / 16f * 16,
            transform.translation[2] / 16f * 16
        );
        
        // Apply rotation (order: Z, Y, X in Minecraft)
        GL11.glRotatef(transform.rotation[2], 0, 0, 1);  // Z
        GL11.glRotatef(transform.rotation[1], 0, 1, 0);  // Y
        GL11.glRotatef(transform.rotation[0], 1, 0, 0);  // X
        
        // Apply scale
        GL11.glScalef(transform.scale[0], transform.scale[1], transform.scale[2]);
    }
    
    private void renderModel3D(BlockModel model, ItemStack stack) {
        // Bind texture atlas
        textureAtlas.bind();
        
        // Render each element
        if (model.elements != null) {
            for (ModelElement element : model.elements) {
                renderElement3D(element, model);
            }
        }
        
        // Apply tinting if needed
        if (needsTint(stack)) {
            applyTint(stack, model);
        }
    }
    
    private void renderElement3D(ModelElement element, BlockModel model) {
        // For each face of the element
        for (Direction dir : Direction.values()) {
            ElementFace face = element.faces.get(dir.getName());
            if (face == null) continue;
            
            // Get texture
            String texturePath = resolveTexture(face.texture, model);
            UVMapping uvMapping = textureAtlas.getUVMapping(texturePath);
            
            // Generate quad vertices
            float[] from = element.from;
            float[] to = element.to;
            
            // Render quad using immediate mode or VBO
            GL11.glBegin(GL11.GL_QUADS);
            
            float[] vertices = getQuadVertices(dir, from, to);
            float[] uvs = mapUVs(face.uv, uvMapping);
            
            for (int i = 0; i < 4; i++) {
                GL11.glTexCoord2f(uvs[i*2], uvs[i*2+1]);
                GL11.glVertex3f(vertices[i*3], vertices[i*3+1], vertices[i*3+2]);
            }
            
            GL11.glEnd();
        }
    }
    
    private void restoreRenderState() {
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glPopMatrix();
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glPopMatrix();
    }
}
```

**Step 6.4: Update GUI Rendering Calls**

**File**: `src/main/java/mattmc/client/gui/screens/inventory/InventoryRenderer.java`

**Change from**:
```java
// Old isometric rendering
itemRenderer.renderBlockItem(model, x, y);
```

**To**:
```java
// New 3D rendering
itemRenderer.renderItem(itemStack, ItemDisplayContext.GUI, x, y);
```

**Step 6.5: Update Hotbar Rendering**

**File**: `src/main/java/mattmc/client/renderer/HotbarRenderer.java`

```java
// For each hotbar slot
for (int i = 0; i < 9; i++) {
    ItemStack stack = inventory.getItem(i);
    if (!stack.isEmpty()) {
        int x = hotbarX + i * 20 + 2;
        int y = hotbarY + 2;
        itemRenderer.renderItem(stack, ItemDisplayContext.GUI, x, y);
    }
}
```

**Step 6.6: Default Display Transforms**

**File**: Create default display transforms for common model parents

**assets/models/block/block.json**:
```json
{
  "display": {
    "gui": {
      "rotation": [30, 225, 0],
      "translation": [0, 0, 0],
      "scale": [0.625, 0.625, 0.625]
    },
    "ground": {
      "rotation": [0, 0, 0],
      "translation": [0, 3, 0],
      "scale": [0.25, 0.25, 0.25]
    },
    "fixed": {
      "rotation": [0, 0, 0],
      "translation": [0, 0, 0],
      "scale": [0.5, 0.5, 0.5]
    },
    "thirdperson_righthand": {
      "rotation": [75, 45, 0],
      "translation": [0, 2.5, 0],
      "scale": [0.375, 0.375, 0.375]
    },
    "firstperson_righthand": {
      "rotation": [0, 45, 0],
      "translation": [0, 0, 0],
      "scale": [0.40, 0.40, 0.40]
    },
    "firstperson_lefthand": {
      "rotation": [0, 225, 0],
      "translation": [0, 0, 0],
      "scale": [0.40, 0.40, 0.40]
    }
  }
}
```

**assets/models/item/generated.json**:
```json
{
  "display": {
    "ground": {
      "rotation": [0, 0, 0],
      "translation": [0, 2, 0],
      "scale": [0.5, 0.5, 0.5]
    },
    "gui": {
      "rotation": [0, 0, 0],
      "translation": [0, 0, 0],
      "scale": [1, 1, 1]
    },
    "fixed": {
      "rotation": [0, 0, 0],
      "translation": [0, 0, 0],
      "scale": [1, 1, 1]
    },
    "thirdperson_righthand": {
      "rotation": [0, 0, 0],
      "translation": [0, 3, 1],
      "scale": [0.55, 0.55, 0.55]
    },
    "firstperson_righthand": {
      "rotation": [0, -90, 25],
      "translation": [1.13, 3.2, 1.13],
      "scale": [0.68, 0.68, 0.68]
    }
  }
}
```

**Validation**:
- [ ] Items render as 3D models in inventory
- [ ] GUI display transform works (30°, 225° rotation, 0.625 scale)
- [ ] Items look like Minecraft items
- [ ] Tinting still works (grass blocks)
- [ ] Performance is acceptable

**Alternative**: If performance is a concern, pre-render items to textures:
- Render item to FBO texture once
- Cache texture
- Draw cached texture in GUI (faster but less flexible)

---

### Phase 7: Model Overrides (HIGH - Week 13-14)

**Goal**: Support dynamic model changes based on ItemStack properties (bow pulling, compass, etc.).

**Step 7.1: Parse Overrides from Item Models**

**File**: `src/main/java/mattmc/client/resources/model/BlockModel.java`

```java
public class BlockModel {
    // ... existing fields ...
    public List<ModelOverride> overrides;
    
    public static class ModelOverride {
        public Map<String, Float> predicate;  // Property -> value
        public String model;  // Override model path
    }
}
```

**Example JSON parsing**:
```json
{
  "parent": "item/handheld",
  "textures": {
    "layer0": "item/bow"
  },
  "overrides": [
    {"predicate": {"pulling": 1}, "model": "item/bow_pulling_0"},
    {"predicate": {"pulling": 1, "pull": 0.65}, "model": "item/bow_pulling_1"},
    {"predicate": {"pulling": 1, "pull": 0.9}, "model": "item/bow_pulling_2"}
  ]
}
```

**Step 7.2: Evaluate Predicates on ItemStack**

**New file**: `src/main/java/mattmc/client/renderer/item/ModelPredicateManager.java`

```java
public class ModelPredicateManager {
    private Map<Item, Map<String, PredicateFunction>> predicates = new HashMap<>();
    
    @FunctionalInterface
    public interface PredicateFunction {
        float getValue(ItemStack stack, Level world, LivingEntity entity);
    }
    
    public void registerDefaults() {
        // Bow pulling
        register(Items.BOW, "pulling", (stack, world, entity) -> {
            return entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0f : 0.0f;
        });
        
        register(Items.BOW, "pull", (stack, world, entity) -> {
            if (entity == null) return 0.0f;
            return entity.isUsingItem() && entity.getUseItem() == stack
                ? (stack.getUseDuration() - entity.getUseItemRemainingTicks()) / 20.0f
                : 0.0f;
        });
        
        // Compass angle
        register(Items.COMPASS, "angle", (stack, world, entity) -> {
            // Calculate angle to spawn point
            if (world == null || entity == null) return 0.0f;
            BlockPos spawn = world.getSpawnPoint();
            return calculateAngle(entity.getPosition(), spawn);
        });
        
        // Clock time
        register(Items.CLOCK, "time", (stack, world, entity) -> {
            if (world == null) return 0.0f;
            return (world.getDayTime() % 24000) / 24000.0f;
        });
        
        // Damage
        registerForAll("damage", (stack, world, entity) -> {
            if (!stack.isDamageableItem()) return 0.0f;
            return (float)stack.getDamageValue() / stack.getMaxDamage();
        });
        
        registerForAll("damaged", (stack, world, entity) -> {
            return stack.isDamaged() ? 1.0f : 0.0f;
        });
    }
    
    public float getValue(ItemStack stack, String predicate, Level world, LivingEntity entity) {
        Map<String, PredicateFunction> itemPredicates = predicates.get(stack.getItem());
        if (itemPredicates == null) return 0.0f;
        
        PredicateFunction func = itemPredicates.get(predicate);
        if (func == null) return 0.0f;
        
        return func.getValue(stack, world, entity);
    }
    
    private void register(Item item, String predicate, PredicateFunction function) {
        predicates.computeIfAbsent(item, k -> new HashMap<>()).put(predicate, function);
    }
}
```

**Step 7.3: Select Override Model**

**File**: `src/main/java/mattmc/client/renderer/ItemRenderer.java`

```java
private BlockModel getItemModel(ItemStack stack, Level world, LivingEntity entity) {
    // Get base model
    BlockModel model = resourceManager.getItemModel(stack.getItem());
    if (model == null) return null;
    
    // Check for overrides
    if (model.overrides != null) {
        for (ModelOverride override : model.overrides) {
            if (matchesOverride(stack, override, world, entity)) {
                // Load override model
                BlockModel overrideModel = resourceManager.loadBlockModel(override.model);
                if (overrideModel != null) {
                    return overrideModel;
                }
            }
        }
    }
    
    return model;
}

private boolean matchesOverride(ItemStack stack, ModelOverride override, 
                                 Level world, LivingEntity entity) {
    // Check if all predicates match
    for (Map.Entry<String, Float> entry : override.predicate.entrySet()) {
        String predicate = entry.getKey();
        float required = entry.getValue();
        float actual = predicateManager.getValue(stack, predicate, world, entity);
        
        if (actual < required) {
            return false;  // Predicate doesn't match
        }
    }
    return true;  // All predicates match
}
```

**Step 7.4: Update Render Calls**

```java
// Now pass world and entity context
public void renderItem(ItemStack stack, ItemDisplayContext context, 
                       int screenX, int screenY, Level world, LivingEntity entity) {
    BlockModel model = getItemModel(stack, world, entity);  // Get override model
    // ... rest of rendering ...
}
```

**Validation**:
- [ ] Bow changes model when pulling
- [ ] Pull animation progresses correctly
- [ ] Compass points to spawn
- [ ] Clock shows correct time
- [ ] Damaged tools show damage overlay

---

### Phase 8: Multipart Blockstates (HIGH - Week 15)

**Goal**: Support multipart blockstates for fences, walls, and other connectable blocks.

**Step 8.1: Parse Multipart Format**

**File**: `src/main/java/mattmc/client/resources/model/BlockState.java`

```java
public class BlockState {
    public Map<String, BlockStateVariant> variants;  // Existing
    public List<MultipartCase> multipart;  // New
    
    public static class MultipartCase {
        public When when;  // Condition
        public List<BlockStateVariant> apply;  // Models to apply
    }
    
    public static class When {
        public String OR;  // OR condition
        public String AND;  // AND condition
        public Map<String, String> properties;  // Simple property match
    }
}
```

**Example JSON**:
```json
{
  "multipart": [
    {
      "apply": { "model": "block/fence_post" }
    },
    {
      "when": { "north": "true" },
      "apply": { "model": "block/fence_side", "uvlock": true }
    },
    {
      "when": { "east": "true" },
      "apply": { "model": "block/fence_side", "y": 90, "uvlock": true }
    }
  ]
}
```

**Step 8.2: Evaluate When Conditions**

```java
public class MultipartEvaluator {
    public static boolean matches(When when, BlockState state) {
        if (when == null) return true;  // No condition = always match
        
        // Simple property match
        if (when.properties != null) {
            for (Map.Entry<String, String> entry : when.properties.entrySet()) {
                String property = entry.getKey();
                String required = entry.getValue();
                String actual = state.getProperty(property);
                
                if (!required.equals(actual)) {
                    return false;
                }
            }
            return true;
        }
        
        // OR condition
        if (when.OR != null) {
            // Parse and evaluate OR conditions
            // ... implementation ...
        }
        
        // AND condition
        if (when.AND != null) {
            // Parse and evaluate AND conditions
            // ... implementation ...
        }
        
        return true;
    }
}
```

**Step 8.3: Combine Multiple Models**

**File**: `src/main/java/mattmc/client/renderer/block/BlockFaceCollector.java`

```java
private void collectBlockFaces(Block block, int x, int y, int z, 
                                LevelChunk chunk, List<FaceData> faces) {
    BlockState state = block.getState();
    BlockStateJSON stateJSON = resourceManager.getBlockState(block);
    
    if (stateJSON.multipart != null) {
        // Multipart: combine multiple models
        for (MultipartCase part : stateJSON.multipart) {
            if (MultipartEvaluator.matches(part.when, state)) {
                // Apply each matching part
                for (BlockStateVariant variant : part.apply) {
                    BlockModel model = resourceManager.getBlockModel(variant.model);
                    collectModelFaces(model, variant, x, y, z, chunk, faces);
                }
            }
        }
    } else if (stateJSON.variants != null) {
        // Regular variant system
        // ... existing code ...
    }
}
```

**Validation**:
- [ ] Fence posts render correctly
- [ ] Fence sides connect based on neighbors
- [ ] Walls connect properly
- [ ] Multiple models combine correctly
- [ ] All when conditions work (OR, AND, property matching)

---

### Phase 9: Testing and Validation (Week 16)

**Goal**: Comprehensive testing with vanilla Minecraft assets.

**Step 9.1: Asset Extraction**

Extract assets from Minecraft JAR:
```bash
# Extract from minecraft.jar
unzip minecraft.jar -d minecraft_assets

# Copy to MattMC
cp -r minecraft_assets/assets/minecraft/models/* assets/models/
cp -r minecraft_assets/assets/minecraft/textures/* assets/textures/
cp -r minecraft_assets/assets/minecraft/blockstates/* assets/blockstates/
```

**Step 9.2: Namespace Conversion Script**

Create script to convert `minecraft:` to `mattmc:`:
```bash
#!/bin/bash
# convert_namespaces.sh

find assets -name "*.json" -type f -exec sed -i 's/minecraft:/mattmc:/g' {} \;
```

Or keep `minecraft:` namespace and ensure ResourceManager handles it.

**Step 9.3: Test Cases**

Create comprehensive test suite:

**Test 1: Simple Full Blocks**
- Stone, Dirt, Grass, Wood Planks
- Verify: Texture, culling, lighting

**Test 2: Partial Blocks**
- Slabs, Stairs, Fences, Walls
- Verify: Geometry, connections, culling

**Test 3: Transparent Blocks**
- Glass, Leaves, Ice
- Verify: Transparency, render layers, culling

**Test 4: Complex Models**
- Torches, Flowers, Crops
- Verify: Custom geometry, rotation

**Test 5: Items**
- Block items in inventory
- Flat items (tools, food)
- Verify: 3D rendering, transforms, appearance

**Test 6: Dynamic Items**
- Bow (pulling animation)
- Compass (rotation)
- Clock (time display)
- Verify: Overrides work correctly

**Step 9.4: Visual Comparison**

Take screenshots of:
- Minecraft rendering
- MattMC rendering

Compare side-by-side for each test case.

**Step 9.5: Performance Testing**

Measure:
- Chunk compilation time
- Frame rate with many chunks
- Memory usage
- Compare to baseline

---

## Summary of Required Changes

### Critical Changes (Must Have for Basic Compatibility)
1. ✅ Namespace handling (`minecraft:` → file paths)
2. ✅ 3D item rendering system (replace isometric)
3. ✅ Display transform support
4. ✅ Shape-based face culling
5. ✅ Render layer separation

### High Priority (Needed for Most Assets)
6. ✅ Multipart blockstates
7. ✅ Model overrides system
8. ✅ Element rotation (all axes)
9. ✅ Ambient occlusion

### Medium Priority (Nice to Have)
10. Face rotation in elements
11. Auto UV calculation
12. Animated textures
13. Model-defined render layers

### Low Priority (Advanced Features)
14. Weighted random variants
15. Custom item predicates
16. Mipmap generation
17. Shader-based effects

---

## Estimated Timeline

**Total Time**: ~16 weeks (4 months) for full compatibility

- **Phase 1** (Namespace): 1 week
- **Phase 2** (Face Culling): 2 weeks
- **Phase 3** (Render Layers): 2 weeks
- **Phase 4** (Ambient Occlusion): 1 week
- **Phase 5** (Element Rotation): 1 week
- **Phase 6** (3D Item Rendering): 5 weeks (largest change)
- **Phase 7** (Model Overrides): 2 weeks
- **Phase 8** (Multipart): 1 week
- **Phase 9** (Testing): 1 week

**Fast Track** (minimal compatibility): Phases 1, 2, 3, 6 = ~10 weeks

---

## Conclusion

This document has provided:

1. **Detailed comparison** of MattMC vs Minecraft rendering systems
2. **Identification of compatibility gaps**
3. **Step-by-step instructions** for each required change
4. **Code examples** showing exactly what to implement
5. **Validation criteria** for testing

**Key Takeaway**: MattMC is already very close to Minecraft's model system for blocks. The biggest gap is in item rendering, which uses a fundamentally different approach (isometric 2D vs 3D perspective). Addressing this, along with proper face culling, render layers, and multipart support, will achieve full drop-in compatibility with vanilla Minecraft assets.

**Next Steps**:
1. Start with Phase 1 (namespace handling) - quick win
2. Decide on item rendering approach (Phase 6) - major architectural decision
3. Implement phases in order, testing incrementally
4. Extract vanilla assets and test continuously


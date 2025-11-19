# MattMC vs Vanilla Java Minecraft: Technical Comparison

## Overview

MattMC is a performance-focused Minecraft clone built from the ground up, following Minecraft's core architecture while implementing key optimizations and custom solutions in specific areas. This document provides a detailed technical comparison between MattMC and vanilla Java Minecraft, with a focus on:

1. **Item Rendering** - How items are displayed in the UI (hotbar, inventory)
2. **JSON Handling** - How block models and blockstates are loaded and processed
3. **Block Rendering** - How blocks are rendered in the 3D world
4. **Custom Geometry Builders** - Special rendering for complex blocks (stairs, torches)

---

## Table of Contents

1. [Architecture Similarities](#architecture-similarities)
2. [Item Rendering](#item-rendering)
3. [JSON Model System](#json-model-system)
4. [Block Rendering](#block-rendering)
5. [Custom Geometry Builders](#custom-geometry-builders)
6. [Key Differences Summary](#key-differences-summary)

---

## Architecture Similarities

MattMC closely follows Minecraft Java Edition's proven architectural patterns:

### Core Systems (SIMILAR)

| System | Minecraft | MattMC | Status |
|--------|-----------|---------|--------|
| **Chunk Format** | 16×384×16 chunks | 16×384×16 chunks | ✅ Identical |
| **Y-Range** | -64 to 319 | -64 to 319 | ✅ Identical |
| **Block Registry** | `Blocks` class with identifiers | `Blocks` class with identifiers | ✅ Identical |
| **Item Registry** | `Items` class with identifiers | `Items` class with identifiers | ✅ Identical |
| **Resource Location** | `namespace:path` format | `namespace:path` format | ✅ Identical |
| **World Storage** | Anvil format (region files) | Anvil format (region files) | ✅ Identical |
| **NBT Data** | Named Binary Tag structures | Named Binary Tag structures | ✅ Identical |
| **Light Storage** | Per-block sky/block light | Per-block sky/block light | ✅ Identical |

### Class Structure (SIMILAR)

Both use similar package organization:
- **Minecraft**: `net.minecraft.client.renderer`, `net.minecraft.world.level.block`, etc.
- **MattMC**: `mattmc.client.renderer`, `mattmc.world.level.block`, etc.

Block and item registration follows the same pattern:
```java
// Both Minecraft and MattMC use this pattern
public static final Block DIRT = register("dirt", new Block(...));
public static final Item DIAMOND = register("diamond", new Item(...));
```

---

## Item Rendering

This is one of the **most significant differences** between MattMC and vanilla Minecraft.

### Vanilla Minecraft Approach

**Minecraft's ItemRenderer** uses a complex model-based system:

1. **BakedModel System**:
   - Models are "baked" during resource loading
   - BakedModels contain pre-processed quads with vertex data
   - Uses `ModelBakery` to transform JSON models into renderable quads
   - Each item has a compiled display list or VBO

2. **Item Rendering Pipeline**:
   ```
   JSON Model → ModelBakery → BakedModel → ItemRenderer → OpenGL
   ```

3. **2D/3D Rendering**:
   - Block items typically use 3D models (perspective transforms)
   - Flat items use 2D generated models from textures
   - Display transforms (`gui`, `firstperson`, etc.) in JSON control positioning

4. **Shader-Based**:
   - Uses modern shader pipeline
   - Matrix transformations for 3D positioning
   - Full perspective projection

### MattMC Approach (DIFFERENT)

**MattMC's ItemRenderer** uses a **custom isometric projection system**:

1. **Isometric 3D Projection**:
   - Block items are rendered as **isometric 3D cubes** (not perspective 3D)
   - Captures actual in-game 3D geometry at mesh generation time
   - Projects 3D world coordinates to 2D screen space using isometric formulas
   - No pre-baked models needed

2. **Geometry Capture System** (`ItemRenderer.java`):
   ```java
   // Capture the 3D geometry for rendering
   VertexCapture capture = new VertexCapture();
   BlockGeometryCapture.captureWestFace(capture, 0, 0, 0);
   BlockGeometryCapture.captureNorthFace(capture, 0, 0, 0);
   BlockGeometryCapture.captureTopFace(capture, 0, 0, 0);
   
   // Project to isometric view
   float screenX = centerX + (worldX - worldZ) * isoWidth;
   float screenY = centerY - worldY * isoHeight - (worldX + worldZ) * isoHeight * 0.5f;
   ```

3. **Three Visible Faces**:
   - Shows **west** (left, 80% brightness), **north** (right, 60% brightness), and **top** (100% brightness) faces
   - Creates a consistent isometric view from SW looking NE
   - Mimics the classic isometric game aesthetic

4. **Special Handling for Complex Blocks**:
   - **Stairs**: Uses `BlockGeometryCapture.captureStairsSouthBottom()` to get actual stepped geometry
   - **Torches**: Still experimental, renders using custom geometry
   - Falls back to basic cube for simple blocks

5. **Immediate Mode Rendering**:
   - Renders directly using OpenGL immediate mode (`glBegin/glEnd`)
   - No shader pipeline, uses fixed-function OpenGL
   - Simple texture mapping with vertex colors for shading

### Key Differences in Item Rendering

| Aspect | Minecraft | MattMC |
|--------|-----------|---------|
| **Rendering Style** | Perspective 3D with matrices | Isometric 3D projection |
| **Model System** | Pre-baked BakedModels | Runtime geometry capture |
| **Pipeline** | Shader-based modern GL | Fixed-function legacy GL |
| **Geometry Source** | JSON model elements | In-game block mesh capture |
| **View Angle** | Configurable via display transforms | Fixed isometric (SW→NE) |
| **Performance** | Optimized via model caching | Real-time capture + projection |
| **Complexity** | High (ModelBakery, baking, etc.) | Low (direct projection math) |

### Code Comparison

**Minecraft** (simplified):
```java
// Uses BakedModel with pre-processed quads
BakedModel model = itemRenderer.getModel(itemStack);
for (BakedQuad quad : model.getQuads()) {
    // Apply transforms from display settings
    // Render using shader pipeline with matrices
}
```

**MattMC**:
```java
// Captures geometry and projects to isometric
VertexCapture capture = new VertexCapture();
BlockGeometryCapture.captureWestFace(capture, 0, 0, 0);
for (VertexCapture.Face face : capture.getFaces()) {
    float x = centerX + (v.x - v.z) * isoWidth;
    float y = centerY - v.y * isoHeight - (v.x + v.z) * isoHeight * 0.5f;
    glTexCoord2f(v.u, 1.0f - v.v);
    glVertex2f(x, y);
}
```

---

## JSON Model System

Both projects use JSON for block models and blockstates, but with different loading and processing approaches.

### Similarities

Both use the **same JSON format**:

**Blockstate JSON** (`blockstates/torch.json`):
```json
{
  "variants": {
    "": {
      "model": "mattmc:block/torch"
    }
  }
}
```

**Model JSON** (`models/block/torch.json`):
```json
{
  "parent": "mattmc:block/template_torch",
  "textures": {
    "torch": "mattmc:block/torch"
  }
}
```

**Template JSON** (`models/block/template_torch.json`):
```json
{
  "ambientocclusion": false,
  "textures": {
    "particle": "#torch"
  },
  "elements": [
    {
      "from": [7, 0, 7],
      "to": [9, 10, 9],
      "faces": {
        "north": { "uv": [7, 6, 9, 16], "texture": "#torch" }
        // ... other faces
      }
    }
  ]
}
```

### Vanilla Minecraft JSON Processing

**Minecraft's ModelBakery** (`ModelBakery.java`):

1. **Loading Phase**:
   - `BLOCKSTATE_LISTER` and `MODEL_LISTER` scan resource packs
   - Loads all JSONs into memory during startup
   - Builds dependency graph for parent resolution

2. **Baking Phase**:
   - Resolves parent chain recursively
   - Substitutes texture variables (`#torch` → `block/torch`)
   - Generates `BakedModel` with pre-computed quads
   - Applies rotations, UV transforms, and culling
   - Stores in model cache indexed by `ModelResourceLocation`

3. **Complex Features**:
   - Multipart models (fences, walls)
   - Weighted random models
   - ItemModelGenerator for flat items
   - Block entity markers (`builtin/entity`)
   - Tint index support for grass/leaves

### MattMC JSON Processing

**MattMC's ResourceManager** (`ResourceManager.java`):

1. **Lazy Loading**:
   - Models loaded on-demand when first requested
   - Uses `loadBlockModel(name)` to trigger load
   - Caches loaded models in `MODEL_CACHE`

2. **Parent Resolution** (`resolveBlockModel()`):
   ```java
   private static BlockModel resolveBlockModel(String name) {
       BlockModel model = loadBlockModelRaw(name);
       if (model.getParent() != null) {
           BlockModel parent = resolveBlockModel(model.getParent());
           model = mergeModels(parent, model);
       }
       resolveTextureVariables(model);
       return model;
   }
   ```

3. **Model Merging**:
   - Child textures override parent textures
   - Child elements override parent elements
   - Display properties merged
   - Preserves `originalParent` for special detection (stairs, etc.)

4. **Texture Variable Resolution**:
   - Recursively replaces `#variable` references
   - Example: `#torch` → looks up `textures.torch` → `mattmc:block/torch`
   - Converts to file path: `assets/textures/block/torch.png`

5. **Simplified Features**:
   - No multipart support (yet)
   - No weighted random models
   - Basic element support (from, to, faces)
   - Tint support for grass blocks (specific implementation)

### Key Differences in JSON Handling

| Aspect | Minecraft | MattMC |
|--------|-----------|---------|
| **Loading Strategy** | Eager (all at startup) | Lazy (on-demand) |
| **Baking** | Pre-processes to BakedModel | Reads raw model at runtime |
| **Parent Resolution** | During baking phase | During load (recursive) |
| **Caching** | BakedModel cache | Raw BlockModel cache |
| **Texture Variables** | Resolved during baking | Resolved during load |
| **Multipart** | Fully supported | Not implemented |
| **Random Models** | Supported (weighted) | Not implemented |
| **Model Complexity** | Very high | Moderate |

### Code Comparison

**Minecraft's ModelBakery** (complex):
```java
// Bakes model into optimized quad format
public BakedModel bake(ModelBakery bakery, Function<Material, TextureAtlasSprite> textureGetter,
                       ModelState transform, ResourceLocation location) {
    // Complex baking logic with transformations
    List<BakedQuad> quads = new ArrayList<>();
    for (ModelElement element : elements) {
        for (ElementFace face : element.faces.values()) {
            BakedQuad quad = bakeFace(element, face, sprite, direction, transform);
            quads.add(quad);
        }
    }
    return new SimpleBakedModel(quads, ...);
}
```

**MattMC's ResourceManager** (simple):
```java
// Returns raw model with parent merged
public static BlockModel loadBlockModel(String name) {
    if (MODEL_CACHE.containsKey(cacheKey)) {
        return MODEL_CACHE.get(cacheKey);
    }
    BlockModel resolved = resolveBlockModel(name);
    MODEL_CACHE.put(cacheKey, resolved);
    return resolved;
}
```

---

## Block Rendering

Block rendering in the world (not in inventory) has significant optimizations in MattMC.

### Similarities

Both use:
- **Chunk-based rendering** with 16×16×16 sections
- **Face culling** (don't render faces between solid blocks)
- **Texture atlases** to batch multiple textures
- **Vertex buffer objects** (VBOs) for GPU-side storage

### Vanilla Minecraft Block Rendering

1. **ChunkRenderer** uses modern OpenGL:
   - Vertex Array Objects (VAOs)
   - Shader-based rendering
   - Multiple render layers (solid, cutout, translucent)
   - Frustum culling per chunk

2. **BakedModel Quads**:
   - Each block face represented as BakedQuad
   - Pre-computed normals, UVs, colors
   - Packed vertex format for efficiency

3. **Smooth Lighting**:
   - Calculates per-vertex lighting from neighbors
   - Ambient occlusion (AO) from corner blocks
   - Shader applies final lighting

### MattMC Block Rendering

1. **ChunkRenderer with Display Lists**:
   - Uses OpenGL display lists for caching compiled geometry
   - Fixed-function pipeline (no shaders)
   - Single render pass (solid blocks only currently)
   - Region-based rendering (32×32 chunk regions)

2. **Custom Geometry Builders**:
   - `MeshBuilder` generates vertices from `BlockFaceCollector`
   - `StairsGeometryBuilder` for stairs blocks
   - `TorchGeometryBuilder` for torch blocks
   - Immediate geometry generation (not pre-baked)

3. **Smooth Lighting** (`VertexLightSampler.java`):
   - Samples 8 surrounding blocks per vertex
   - Calculates AO using 3-block corner rule
   - RGB block light (colored lighting support)
   - Direct vertex color application (no shader)

4. **Face Collection System** (`BlockFaceCollector.java`):
   - Separates faces by direction (top, bottom, north, south, east, west)
   - Cross-chunk face culling support
   - Special markers for custom geometry ("stairs", "torch")

### Key Differences in Block Rendering

| Aspect | Minecraft | MattMC |
|--------|-----------|---------|
| **Rendering API** | Modern shader-based GL | Legacy fixed-function GL |
| **Optimization** | VAOs + VBOs | Display lists + VBOs |
| **Geometry Source** | Pre-baked BakedModel quads | Runtime geometry builders |
| **Lighting** | Shader-based | Per-vertex color calculation |
| **Colored Lighting** | No (single white light) | Yes (RGB block light) |
| **Special Blocks** | All via BakedModel | Custom geometry builders |
| **Pipeline** | Model → Bake → Quad → GPU | Block → Builder → Vertex → GPU |

---

## Custom Geometry Builders

This is where MattMC **differs most significantly** from Minecraft.

### Vanilla Minecraft Approach

**Everything is a BakedModel**:
- Stairs, slabs, fences, doors, etc. all use JSON model elements
- `ModelBakery` bakes elements into quads at load time
- Same rendering path for all blocks
- Special blocks like redstone use multipart models with variants

Example stairs model (`stairs.json`):
```json
{
  "elements": [
    { "from": [0, 0, 0], "to": [16, 8, 16], "faces": {...} },   // Bottom slab
    { "from": [8, 8, 0], "to": [16, 16, 16], "faces": {...} }   // Top step
  ]
}
```

### MattMC Approach: Specialized Geometry Builders

**MattMC uses dedicated Java classes** for complex block geometry:

#### 1. StairsGeometryBuilder (`StairsGeometryBuilder.java`)

**Purpose**: Generate optimized stairs geometry with proper lighting

**Key Features**:
- **Dynamic geometry**: Creates vertices at mesh build time, not from JSON
- **Facing direction**: Rotates step based on blockstate `facing` property
- **Half property**: Supports top and bottom stairs
- **Shape variants**: Could support inner/outer corners (not yet implemented)
- **Proper lighting**: Samples vertex light for each face with correct AO

**Implementation**:
```java
public int addStairsGeometry(BlockFaceCollector.FaceData face, 
                              FloatList vertices, IntList indices, int currentVertex) {
    // Get facing and half from blockstate
    Direction facing = state.getDirection("facing");
    Half half = state.getHalf("half");
    
    // Render bottom slab (full width/depth, half height)
    currentVertex = addStairsBottomSlabFace(face, x, y, z, 0.5f, facing, ...);
    
    // Render top step (half width/depth based on facing, half height)
    currentVertex = addStairsTopStepFace(face, x, y + 0.5f, z, facing, ...);
    
    return currentVertex;
}
```

**Why Different?**:
- **Performance**: Generates only visible faces, optimized for current blockstate
- **Flexibility**: Can easily add shape variants (inner/outer) without JSON complexity
- **Lighting**: Integrates directly with smooth lighting system
- **Control**: Full control over vertex positions, UVs, and normals

#### 2. TorchGeometryBuilder (`TorchGeometryBuilder.java`)

**Purpose**: Render torch blocks using JSON model elements

**Key Features**:
- **Reads JSON models**: Loads `torch.json` and `template_torch.json`
- **Element processing**: Interprets `elements` array with `from`/`to` coordinates
- **Y-rotation**: Applies rotation for wall torches based on `facing` direction
- **UV mapping**: Maps model UVs (0-16 range) to texture atlas UVs
- **Face rendering**: Processes each element face with correct normals and lighting

**Implementation**:
```java
public int addTorchGeometry(BlockFaceCollector.FaceData face, 
                             FloatList vertices, IntList indices, int currentVertex) {
    // Load the block model from JSON
    BlockModel model = ResourceManager.loadBlockModel(blockName);
    
    // Determine Y-rotation for wall torches
    int yRotation = getRotationFromFacing(face.blockState);
    
    // Process each model element
    for (ModelElement element : model.getElements()) {
        currentVertex = addModelElement(element, x, y, z, yRotation, vertices, indices, currentVertex);
    }
    
    return currentVertex;
}
```

**Coordinate Conversion**:
```java
// Minecraft models use 0-16 coordinate system
// MattMC converts to 0-1 block-relative coordinates
float localX0 = from.get(0) / 16.0f;  // 7 → 0.4375
float localY0 = from.get(1) / 16.0f;  // 0 → 0.0
float localZ0 = from.get(2) / 16.0f;  // 7 → 0.4375
```

**Why Different?**:
- **JSON-based but runtime**: Reads JSON but generates geometry at mesh time
- **Hybrid approach**: Combines JSON flexibility with runtime optimization
- **Learning step**: Demonstrates how to bridge JSON models and custom builders
- **Future potential**: Could be generalized for other block types

#### 3. MeshBuilder Integration (`MeshBuilder.java`)

**Orchestrates all geometry generation**:

```java
public ChunkMeshBuffer build(int chunkX, int chunkZ, BlockFaceCollector collector) {
    for (BlockFaceCollector.FaceData face : allFaces) {
        // Check for special block types
        if ("stairs".equals(face.faceType)) {
            currentVertex = stairsBuilder.addStairsGeometry(face, vertices, indices, currentVertex);
            continue;
        }
        
        if ("torch".equals(face.faceType)) {
            currentVertex = torchBuilder.addTorchGeometry(face, vertices, indices, currentVertex);
            continue;
        }
        
        // Standard cube face rendering
        addFace(face, vertices, indices, currentVertex);
    }
}
```

### Key Differences in Custom Geometry

| Aspect | Minecraft | MattMC |
|--------|-----------|---------|
| **Stairs Rendering** | JSON elements → BakedModel | StairsGeometryBuilder class |
| **Torch Rendering** | JSON elements → BakedModel | TorchGeometryBuilder + JSON |
| **Geometry Source** | Pre-baked at load time | Generated at mesh build time |
| **Extensibility** | Add JSON model variants | Add/modify geometry builder |
| **Complexity** | JSON + multipart system | Java code per block type |
| **Performance** | Cached BakedModel | Runtime generation with cache |
| **Customization** | JSON only (limited) | Full programmatic control |

### Why MattMC Uses Geometry Builders

1. **Performance Control**: Can optimize exactly what vertices to generate
2. **Dynamic Geometry**: Easy to change based on blockstate without JSON variants
3. **Learning Exercise**: Understanding how Minecraft could work under the hood
4. **Flexibility**: Can implement features not possible with JSON (e.g., animated geometry)
5. **Simplicity**: Avoids complex ModelBakery baking system
6. **Direct Integration**: Works seamlessly with smooth lighting and face culling

### Trade-offs

**Advantages**:
- ✅ Full control over geometry generation
- ✅ Can optimize for specific block types
- ✅ Easier to debug (Java code vs. JSON interpretation)
- ✅ Can implement features beyond JSON capabilities

**Disadvantages**:
- ❌ Requires Java code change to add new block types
- ❌ Not as data-driven as pure JSON approach
- ❌ More code to maintain than JSON models
- ❌ Harder for resource packs to modify geometry

---

## Key Differences Summary

### What's Similar to Minecraft ✅

1. **Core Architecture**:
   - Chunk system (16×384×16, Y -64 to 319)
   - Block and item registries with resource location identifiers
   - NBT data structures and world save format (Anvil/region files)
   - Light storage (per-block skyLight and blockLight)

2. **JSON Format**:
   - Blockstate JSON structure (`variants`, `model`)
   - Model JSON format (`parent`, `textures`, `elements`)
   - Element definition (`from`, `to`, `faces` with UVs)
   - Texture variable system (`#variable` references)

3. **Resource Loading**:
   - Resource location format (`namespace:path`)
   - Parent model inheritance
   - Texture atlas system

4. **Block Properties**:
   - Blockstate properties (facing, half, axis, etc.)
   - Solid vs. non-solid blocks
   - Light emission values

### What's Different from Minecraft ⚠️

1. **Item Rendering** (MAJOR DIFFERENCE):
   - MattMC: Isometric 3D projection with geometry capture
   - Minecraft: Perspective 3D with pre-baked BakedModels
   - MattMC: Fixed-function OpenGL immediate mode
   - Minecraft: Shader-based modern pipeline

2. **Block Rendering**:
   - MattMC: Custom geometry builders (StairsGeometryBuilder, TorchGeometryBuilder)
   - Minecraft: Everything through BakedModel system
   - MattMC: Runtime geometry generation
   - Minecraft: Load-time model baking

3. **JSON Processing**:
   - MattMC: Lazy loading on-demand
   - Minecraft: Eager loading at startup
   - MattMC: Raw BlockModel caching
   - Minecraft: BakedModel caching with quad pre-computation

4. **Lighting**:
   - MattMC: RGB colored block light (per-channel)
   - Minecraft: Single-channel block light
   - MattMC: Direct vertex color calculation
   - Minecraft: Shader-based lighting

5. **OpenGL Usage**:
   - MattMC: Legacy fixed-function pipeline with display lists
   - Minecraft: Modern shader-based pipeline with VAOs

---

## Detailed Architecture Comparison

### Block → Item → Rendering Pipeline

**Minecraft's Pipeline**:
```
Block Definition
    ↓
JSON Blockstate (maps state → model)
    ↓
JSON Model (elements + textures)
    ↓
ModelBakery.bake() → BakedModel
    ↓
ItemRenderer (3D perspective rendering)
    ↓
Shader Pipeline + Matrix Transforms
    ↓
GPU Rendering
```

**MattMC's Pipeline**:
```
Block Definition
    ↓
JSON Blockstate (maps state → model)
    ↓
JSON Model (elements + textures)
    ↓
ResourceManager.loadBlockModel() → BlockModel
    ↓
ItemRenderer (isometric 2D projection)
    ↓
BlockGeometryCapture → Isometric Math
    ↓
Fixed-Function OpenGL (glBegin/glEnd)
    ↓
GPU Rendering
```

### Special Block Rendering

**Minecraft**:
```
Stairs Block
    ↓
stairs.json (elements with rotations)
    ↓
ModelBakery + BlockStateVariant
    ↓
BakedModel with rotated quads
    ↓
Standard rendering path
```

**MattMC**:
```
Stairs Block
    ↓
BlockFaceCollector detects StairsBlock
    ↓
StairsGeometryBuilder.addStairsGeometry()
    ↓
Runtime vertex generation
    ↓
Direct to MeshBuilder vertex arrays
```

---

## Implementation Philosophy Differences

### Minecraft's Philosophy

- **Data-Driven**: Everything configurable via JSON
- **Resource Pack Friendly**: Easy to modify through resource packs
- **Modern GL**: Uses latest OpenGL features and shaders
- **Complex but Flexible**: ModelBakery system is complex but handles all cases
- **Baking Optimization**: Pre-process everything at load time for runtime speed

### MattMC's Philosophy

- **Performance-First**: Optimize for frame rate and efficiency
- **Pragmatic**: Use the simplest approach that works well
- **Control**: Prefer Java code control over JSON data
- **Legacy GL**: Use proven OpenGL techniques (display lists work great)
- **Runtime Generation**: Generate geometry when needed, cache aggressively
- **Learning-Oriented**: Demonstrate how game engines work internally

---

## Technical Deep Dive: Item Rendering

### The Isometric Projection System

MattMC's item renderer uses **isometric projection**, which is a type of parallel projection where the three coordinate axes appear equally foreshortened.

**Projection Formulas**:
```java
// Project 3D world coordinates to 2D isometric screen coordinates
screenX = centerX + (worldX - worldZ) * isoWidth
screenY = centerY - worldY * isoHeight - (worldX + worldZ) * isoHeight * 0.5
```

**Visual Layout**:
```
         ↗ +Y (up)
        /
       /
+Z ←→ +X (horizontal plane)

Isometric View:
      Top (100% bright)
     /\
    /  \
   /    \
West     North
(80%)    (60%)
```

**Why Isometric?**:
1. **Consistent Look**: All items have the same viewing angle
2. **Simple Math**: No perspective division or matrix math needed
3. **Classic Aesthetic**: Evokes classic isometric games
4. **Performance**: Fast calculation, no shader overhead
5. **Predictable**: Always shows same three faces clearly

### Geometry Capture System

**Purpose**: Reuse the same 3D geometry that's rendered in the world for item display

**Process**:
1. **Capture**: Use `BlockGeometryCapture` to get raw vertex data
2. **Store**: `VertexCapture` holds faces with positions, UVs, normals
3. **Project**: Apply isometric projection to each vertex
4. **Render**: Draw triangles using fixed-function OpenGL

**Example** (`ItemRenderer.java:147-156`):
```java
// Capture the three visible faces
VertexCapture capture = new VertexCapture();

BlockGeometryCapture.captureWestFace(capture, 0, 0, 0);
List<VertexCapture.Face> westFaces = List.copyOf(capture.getFaces());

capture.clear();
BlockGeometryCapture.captureNorthFace(capture, 0, 0, 0);
List<VertexCapture.Face> northFaces = List.copyOf(capture.getFaces());

capture.clear();
BlockGeometryCapture.captureTopFace(capture, 0, 0, 0);
List<VertexCapture.Face> topFaces = List.copyOf(capture.getFaces());
```

**Rendering with Brightness** (`ItemRenderer.java:161-192`):
```java
// West face (left side, medium brightness - 80%)
tex.bind();
glColor4f(0.8f, 0.8f, 0.8f, 1.0f);
renderFacesIsometric(westFaces, x, y, isoWidth, isoHeight);

// North face (right side, darker - 60%)
glColor4f(0.6f, 0.6f, 0.6f, 1.0f);
renderFacesIsometric(northFaces, x, y, isoWidth, isoHeight);

// Top face (brightest - 100% with tint)
float r = ((topTintColor >> 16) & 0xFF) / 255.0f;
float g = ((topTintColor >> 8) & 0xFF) / 255.0f;
float b = (topTintColor & 0xFF) / 255.0f;
glColor4f(r, g, b, 1.0f);
renderFacesIsometric(topFaces, x, y, isoWidth, isoHeight);
```

---

## Technical Deep Dive: JSON Model Resolution

### Parent Chain Resolution

Both Minecraft and MattMC resolve parent models recursively, but the timing differs.

**Example Model Chain**:
```
birch_stairs.json
    ↓ parent: "block/stairs"
stairs.json
    ↓ parent: "block/block"
block.json
    ↓ (no parent)
```

**MattMC's Resolution** (`ResourceManager.java:105-122`):
```java
private static BlockModel resolveBlockModel(String name) {
    // Load the raw JSON
    BlockModel model = loadBlockModelRaw(name);
    if (model == null) return null;
    
    // If model has a parent, resolve it recursively
    if (model.getParent() != null) {
        BlockModel parent = resolveBlockModel(model.getParent());  // RECURSIVE
        if (parent != null) {
            model = mergeModels(parent, model);  // Merge properties
        }
    }
    
    // Resolve texture variables (#side → mattmc:block/planks)
    resolveTextureVariables(model);
    
    return model;
}
```

**Model Merging** (`ResourceManager.java:129-165`):
```java
private static BlockModel mergeModels(BlockModel parent, BlockModel child) {
    BlockModel merged = new BlockModel();
    
    // Preserve child's original parent for special detection
    if (child.getParent() != null) {
        merged.setOriginalParent(child.getParent());
    }
    
    // Merge textures (child overrides parent)
    Map<String, String> mergedTextures = new HashMap<>();
    if (parent.getTextures() != null) {
        mergedTextures.putAll(parent.getTextures());
    }
    if (child.getTextures() != null) {
        mergedTextures.putAll(child.getTextures());  // Child wins
    }
    merged.setTextures(mergedTextures);
    
    // Child elements override parent elements completely
    merged.setElements(child.getElements() != null ? child.getElements() : parent.getElements());
    
    // Merge other properties...
    return merged;
}
```

### Texture Variable Resolution

**Process**:
1. Model defines: `"textures": { "side": "#planks" }`
2. Another entry defines: `"planks": "mattmc:block/birch_planks"`
3. Resolution: `#planks` → `mattmc:block/birch_planks`
4. Conversion: `mattmc:block/birch_planks` → `assets/textures/block/birch_planks.png`

**MattMC Implementation** (`ResourceManager.java:172-211`):
```java
private static void resolveTextureVariables(BlockModel model) {
    if (model.getTextures() == null) return;
    
    Map<String, String> textures = model.getTextures();
    boolean changed;
    
    // Keep resolving until no more variables
    do {
        changed = false;
        for (Map.Entry<String, String> entry : textures.entrySet()) {
            String value = entry.getValue();
            
            // Check if value is a variable reference (#variable)
            if (value != null && value.startsWith("#")) {
                String varName = value.substring(1);  // Remove #
                String resolved = textures.get(varName);
                
                if (resolved != null && !resolved.equals(value)) {
                    entry.setValue(resolved);  // Replace #var with actual value
                    changed = true;
                }
            }
        }
    } while (changed);  // Continue until nothing changes
}
```

---

## Technical Deep Dive: Stairs Geometry

### Stairs Structure

Stairs consist of two parts:
1. **Bottom slab**: Full-width, half-height base
2. **Top step**: Half-width (along facing direction), half-height step

**Visual**:
```
Side view (facing south):
┌────────┐
│ Step   │ ← Top half (8 pixels, south half)
└────────┘
┌────────────────┐
│    Slab        │ ← Bottom half (8 pixels, full width)
└────────────────┘
```

### StairsGeometryBuilder Implementation

**Entry Point** (`StairsGeometryBuilder.java:34-78`):
```java
public int addStairsGeometry(BlockFaceCollector.FaceData face, 
                              FloatList vertices, IntList indices, int currentVertex) {
    // Get blockstate properties
    Direction facing = state.getDirection("facing");  // NORTH, SOUTH, EAST, WEST
    Half half = state.getHalf("half");                // BOTTOM or TOP
    
    if (half == Half.BOTTOM) {
        // Bottom stairs: slab on bottom, step on top
        currentVertex = addStairsBottomSlabFace(x, y, z, 0.5f, facing, ...);
        currentVertex = addStairsTopStepFace(x, y + 0.5f, z, facing, ...);
    } else {
        // Top stairs: slab on top, step on bottom (inverted)
        currentVertex = addStairsBottomSlabFace(x, y + 0.5f, z, 0.5f, facing, ...);
        currentVertex = addStairsTopStepFace(x, y, z, facing, ...);
    }
    
    return currentVertex;
}
```

**Bottom Slab** (`StairsGeometryBuilder.java:84-192`):
- Generates 6 faces (top, bottom, north, south, east, west)
- Full width/depth (1.0 × 0.5 × 1.0 blocks)
- Texture UVs scaled to half-height (v05 = (v0 + v1) / 2)
- Proper lighting sampled for each vertex

**Top Step by Facing** (`StairsGeometryBuilder.java:197-619`):
- Different geometry method for each facing direction
- `addStairsStepNorth()`: Step in north half (z: 0 to 0.5)
- `addStairsStepSouth()`: Step in south half (z: 0.5 to 1)
- `addStairsStepWest()`: Step in west half (x: 0 to 0.5)
- `addStairsStepEast()`: Step in east half (x: 0.5 to 1)

**Face Breakdown** (North-facing stairs example):
```
Top face:     x: [0, 1],  y: 1.0,      z: [0, 0.5]   (north half)
Bottom face:  x: [0, 1],  y: 0.5,      z: [0, 0.5]   (north half)
North face:   x: [0, 1],  y: [0.5, 1], z: 0          (front of step)
South face:   x: [0, 1],  y: [0.5, 1], z: 0.5        (inner vertical)
West face:    x: 0,       y: [0.5, 1], z: [0, 0.5]   (left side, half depth)
East face:    x: 1,       y: [0.5, 1], z: [0, 0.5]   (right side, half depth)
```

### Lighting Integration

Each vertex gets lighting from `VertexLightSampler`:
```java
float[] light = lightSampler.sampleVertexLight(face, faceIndex, vertexIndex);
// Returns: [skyLight, blockLightR, blockLightG, blockLightB, ao]

addVertex(vertices, x, y, z, u, v, color, nx, ny, nz,
          light[0], light[1], light[2], light[3], light[4]);
```

This creates smooth lighting transitions across stairs surfaces.

---

## Technical Deep Dive: Torch Geometry

### Torch JSON Structure

**template_torch.json**:
```json
{
  "ambientocclusion": false,
  "elements": [
    {
      "from": [7, 0, 7],    // Start at (7/16, 0/16, 7/16)
      "to": [9, 10, 9],     // End at (9/16, 10/16, 9/16)
      "shade": false,
      "faces": {
        "down":  { "uv": [7, 13, 9, 15], "texture": "#torch" },
        "up":    { "uv": [7,  6, 9,  8], "texture": "#torch" },
        "north": { "uv": [7,  6, 9, 16], "texture": "#torch" },
        // ... other faces
      }
    }
  ]
}
```

This creates a thin vertical stick (2×10×2 pixels) centered in the block.

### TorchGeometryBuilder Implementation

**Loading Model** (`TorchGeometryBuilder.java:43-91`):
```java
public int addTorchGeometry(BlockFaceCollector.FaceData face, ...) {
    // Load the block model from JSON
    String blockName = extractNameFromIdentifier(block.getIdentifier());
    BlockModel model = ResourceManager.loadBlockModel(blockName);
    
    // Determine Y-rotation for wall torches
    int yRotation = 0;
    if (block instanceof WallTorchBlock && face.blockState != null) {
        Direction facing = face.blockState.getDirection("facing");
        yRotation = switch (facing) {
            case NORTH -> 270;
            case SOUTH -> 90;
            case WEST -> 180;
            case EAST -> 0;
            default -> 0;
        };
    }
    
    // Process each element in the model
    for (ModelElement element : model.getElements()) {
        currentVertex = addModelElement(element, x, y, z, yRotation, ...);
    }
    
    return currentVertex;
}
```

**Element Processing** (`TorchGeometryBuilder.java:98-178`):
```java
private int addModelElement(ModelElement element, ...) {
    // Convert from Minecraft's 0-16 coordinate system to 0-1
    float localX0 = from.get(0) / 16.0f;  // 7 → 0.4375
    float localY0 = from.get(1) / 16.0f;  // 0 → 0.0
    float localZ0 = from.get(2) / 16.0f;  // 7 → 0.4375
    float localX1 = to.get(0) / 16.0f;    // 9 → 0.5625
    float localY1 = to.get(1) / 16.0f;    // 10 → 0.625
    float localZ1 = to.get(2) / 16.0f;    // 9 → 0.5625
    
    // Apply Y-axis rotation if needed (for wall torches)
    if (yRotation != 0) {
        float[] rotated0 = rotateY(localX0, localZ0, yRotation);
        float[] rotated1 = rotateY(localX1, localZ1, yRotation);
        localX0 = rotated0[0];
        localZ0 = rotated0[1];
        localX1 = rotated1[0];
        localZ1 = rotated1[1];
    }
    
    // Add block position offset
    float x0 = blockX + localX0;
    float y0 = blockY + localY0;
    float z0 = blockZ + localZ0;
    // ... similar for x1, y1, z1
    
    // Render each face defined in the element
    for (String faceDirection : faces.keySet()) {
        currentVertex = addElementFace(face, faces.get(faceDirection), 
                                       texturePath, x0, y0, z0, x1, y1, z1,
                                       faceDirection, ...);
    }
}
```

**Y-Rotation** (`TorchGeometryBuilder.java:346-374`):
```java
private float[] rotateY(float x, float z, int degrees) {
    // Translate to origin (block center at 0.5, 0.5)
    float cx = x - 0.5f;
    float cz = z - 0.5f;
    
    // Rotate around origin
    float rotatedX, rotatedZ;
    switch (degrees) {
        case 90:
            rotatedX = -cz;
            rotatedZ = cx;
            break;
        case 180:
            rotatedX = -cx;
            rotatedZ = -cz;
            break;
        case 270:
            rotatedX = cz;
            rotatedZ = -cx;
            break;
        default:
            rotatedX = cx;
            rotatedZ = cz;
    }
    
    // Translate back
    return new float[]{rotatedX + 0.5f, rotatedZ + 0.5f};
}
```

### UV Mapping

**Process**:
1. Model face defines UV in 0-16 range: `[7, 6, 9, 16]`
2. Convert to 0-1 range: `[0.4375, 0.375, 0.5625, 1.0]`
3. Map to texture atlas coordinates using `TextureAtlas.UVMapping`
4. Apply to vertices for correct texture sampling

**Code** (`TorchGeometryBuilder.java:196-225`):
```java
// Get UV from model face
List<Float> uv = face.getUv();
float u0 = uv.get(0) / 16.0f;  // 7 → 0.4375
float v0 = uv.get(1) / 16.0f;  // 6 → 0.375
float u1 = uv.get(2) / 16.0f;  // 9 → 0.5625
float v1 = uv.get(3) / 16.0f;  // 16 → 1.0

// Get atlas mapping for torch texture
TextureAtlas.UVMapping uvMapping = getUVMappingForTexture(texturePath);

// Map model UVs to atlas UVs
float atlasU0 = uvMapping.u0 + (uvMapping.u1 - uvMapping.u0) * u0;
float atlasV0 = uvMapping.v0 + (uvMapping.v1 - uvMapping.v0) * v0;
float atlasU1 = uvMapping.u0 + (uvMapping.u1 - uvMapping.u0) * u1;
float atlasV1 = uvMapping.v0 + (uvMapping.v1 - uvMapping.v0) * v1;
```

---

## Conclusion

MattMC demonstrates a **hybrid approach** to Minecraft architecture:

### What MattMC Gets Right ✅

1. **Core Compatibility**: Uses same fundamental systems (chunks, NBT, resource locations)
2. **JSON Format**: Compatible with Minecraft's JSON model format
3. **Performance Focus**: Optimizations like display lists, face culling, RGB lighting
4. **Clean Code**: Well-documented, modular architecture
5. **Learning Value**: Clear demonstration of game engine concepts

### Where MattMC Diverges 🔄

1. **Item Rendering**: Isometric projection vs. perspective 3D
2. **Geometry Builders**: Custom Java classes vs. universal BakedModel system
3. **OpenGL**: Legacy fixed-function vs. modern shader-based
4. **JSON Processing**: Lazy runtime loading vs. eager load-time baking
5. **Data-Driven**: Less reliant on JSON for complex geometry

### Philosophy

- **Minecraft**: Maximum flexibility through data-driven design
- **MattMC**: Maximum control through programmatic geometry generation

Both approaches have merit depending on goals:
- Want **moddability**? → Minecraft's BakedModel system
- Want **performance control**? → MattMC's geometry builders
- Want **simplicity**? → MattMC's approach is easier to understand
- Want **compatibility**? → Minecraft's approach is industry-standard

---

## Further Reading

For more details on MattMC's architecture:
- [README.md](README.md) - Project overview and features
- [CHUNK_SYSTEM.md](CHUNK_SYSTEM.md) - Chunk rendering details
- [EFFICIENCY_ANALYSIS.md](EFFICIENCY_ANALYSIS.md) - Performance optimizations
- [WORLD_SAVE_FORMAT.md](WORLD_SAVE_FORMAT.md) - NBT and Anvil format

For Minecraft Java Edition reference:
- See `frnsrc/ModelBakery.java` - Minecraft's model baking system
- Minecraft Wiki: Block models - JSON format documentation
- Minecraft source code (via official mappings)

---

**Document Version**: 1.0  
**Last Updated**: 2025-11-19  
**MattMC Version**: Development build

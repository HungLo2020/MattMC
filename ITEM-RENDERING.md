# Item Rendering in Inventory - Technical Documentation

## Overview

This document provides a comprehensive explanation of how items are rendered in the MattMC inventory system, with a particular focus on the rendering pipeline, the isometric projection system, and the specific issues affecting torch rendering.

## Table of Contents

1. [High-Level Architecture](#high-level-architecture)
2. [Rendering Pipeline](#rendering-pipeline)
3. [Model Loading System](#model-loading-system)
4. [Isometric Projection System](#isometric-projection-system)
5. [Block Items vs Regular Items](#block-items-vs-regular-items)
6. [The Torch Rendering Issue](#the-torch-rendering-issue)
7. [Comparison with Working Items](#comparison-with-working-items)
8. [Technical Details](#technical-details)

---

## High-Level Architecture

The item rendering system in MattMC consists of several interconnected components:

```
ItemRenderer.java
    ↓
ResourceManager.java
    ↓
BlockModel.java / ModelElement.java
    ↓
BlockGeometryCapture.java / VertexCapture.java
    ↓
OpenGL Rendering
```

### Key Components:

1. **ItemRenderer**: The main rendering class that handles drawing items in the UI (inventory, hotbar, etc.)
2. **ResourceManager**: Manages loading and resolving JSON model files with parent inheritance
3. **BlockGeometryCapture**: Captures 3D block geometry and converts it to 2D isometric projection
4. **VertexCapture**: Stores captured vertices and faces for rendering

---

## Rendering Pipeline

When an item needs to be rendered in the inventory, the following sequence occurs:

### Step 1: Entry Point
```java
ItemRenderer.renderItem(ItemStack stack, float x, float y, float size)
```
- Called by the UI system to render an item at a specific screen position
- Parameters: item stack, screen coordinates (x, y), and size in pixels

### Step 2: Item Identification
```java
String itemId = stack.getItem().getIdentifier();
String itemName = itemId.substring(itemId.indexOf(':') + 1);  // e.g., "grass_block" from "mattmc:grass_block"
```

### Step 3: Texture/Model Resolution
```java
Map<String, String> texturePaths = ResourceManager.getItemTexturePaths(itemName);
```

This is where the system:
1. Looks for `assets/models/item/{itemName}.json`
2. If found, loads and resolves the item model with full parent inheritance
3. Extracts texture paths from the resolved model
4. Returns a map of texture keys (e.g., "all", "top", "side", "layer0") to file paths

### Step 4: Classification
The renderer determines if this is a block item or a regular item:

```java
boolean isBlockItem = texturePaths.containsKey("all") || 
                      texturePaths.containsKey("top") || 
                      texturePaths.containsKey("side") || 
                      texturePaths.containsKey("bottom");
```

**Block items** (like stone, dirt, grass) have face-specific textures and are rendered as isometric 3D cubes.

**Regular items** (like tools, food) typically have a single "layer0" texture and are rendered as flat 2D sprites.

### Step 5A: Block Item Rendering (Isometric 3D)
For block items, the renderer:

1. Gets the full item model to check for special properties (tints, stairs detection)
2. Determines if special geometry is needed (e.g., stairs blocks)
3. Captures the 3D geometry using `BlockGeometryCapture`
4. Projects the 3D vertices to 2D screen coordinates using isometric formulas
5. Renders the projected triangles with appropriate textures and shading

### Step 5B: Regular Item Rendering (Flat 2D)
For regular items:

1. Gets the "layer0" texture path
2. Renders a simple textured quad (square) at the specified position
3. No projection or 3D calculations needed

---

## Model Loading System

The model loading system implements Minecraft's standard JSON model format with full parent inheritance.

### Model Resolution Process

When `ResourceManager.loadItemModel("grass_block")` is called:

1. **Load Raw Model**: Load `assets/models/item/grass_block.json`
   ```json
   {
     "parent": "mattmc:block/grass_block"
   }
   ```

2. **Follow Parent Chain**: Since it has a parent, load the block model
   ```json
   {
     "parent": "mattmc:block/cube_column",
     "textures": {
       "end": "mattmc:block/dirt",
       "side": "mattmc:block/grass_block_side",
       "top": "mattmc:block/grass_block_top"
     }
   }
   ```

3. **Continue Parent Resolution**: Load `cube_column.json`
   ```json
   {
     "parent": "block/cube_all",
     "elements": [
       {
         "from": [0, 0, 0],
         "to": [16, 16, 16],
         "faces": {
           "down":  { "uv": [0, 0, 16, 16], "texture": "#end", "cullface": "down" },
           "up":    { "uv": [0, 0, 16, 16], "texture": "#end", "cullface": "up" },
           "north": { "uv": [0, 0, 16, 16], "texture": "#side", "cullface": "north" },
           "south": { "uv": [0, 0, 16, 16], "texture": "#side", "cullface": "south" },
           "west":  { "uv": [0, 0, 16, 16], "texture": "#side", "cullface": "west" },
           "east":  { "uv": [0, 0, 16, 16], "texture": "#side", "cullface": "east" }
         }
       }
     ]
   }
   ```

4. **Final Parent**: Load `cube_all.json` and eventually `block.json` (base)

5. **Merge**: Merge all models from parent to child (child properties override)

6. **Resolve Texture Variables**: Replace `#side`, `#top`, `#end` references with actual texture paths
   - `#side` → `mattmc:block/grass_block_side` → `assets/textures/block/grass_block_side.png`
   - `#top` → `mattmc:block/grass_block_top` → `assets/textures/block/grass_block_top.png`

7. **Cache and Return**: Cache the fully resolved model for future use

### Key Features:

- **Parent Inheritance**: Models can inherit from other models using the "parent" property
- **Texture Variables**: Use `#variable` notation to reference textures defined in the textures map
- **Elements**: Define the 3D geometry as a list of cuboids with UV-mapped faces
- **Display Transforms**: Control how items appear in different contexts (GUI, hand, etc.)
- **Tints**: Support for color tinting (used by grass blocks for biome-specific colors)

### Caching:

Models are cached with a key format: `"item:{itemName}"` or `"block:{blockName}"`. This prevents redundant loading and parsing of JSON files.

---

## Isometric Projection System

MattMC uses an **isometric projection** to render 3D block items in a 2D inventory. This creates a pleasing 3D effect similar to Minecraft's item rendering.

### The Isometric View

The isometric camera is positioned to view the block from the **southwest** direction, looking **northeast**. This means you can see three faces of a cube:
- **West face** (left side) - visible on the left
- **North face** (right side) - visible on the right  
- **Top face** - visible on top

### Projection Formulas

To convert a 3D world coordinate (wx, wy, wz) to a 2D screen coordinate:

```java
screenX = centerX + (wx - wz) * isoWidth
screenY = centerY - wy * isoHeight - (wx + wz) * isoHeight * 0.5f
```

Where:
- `centerX`, `centerY`: The center of the item on screen (where the item should be drawn)
- `isoWidth`, `isoHeight`: Scale factors that control the apparent size (typically `size * 2.0f * 0.5f`)
- `wx`, `wy`, `wz`: The 3D world coordinates of the vertex (in block units, 0-1 range)

### Understanding the Formula

The X formula `(wx - wz)` creates the diagonal axis effect:
- Moving east (positive wx) shifts right
- Moving south (positive wz) shifts left
- The difference creates the isometric diagonal

The Y formula has three components:
- `-wy * isoHeight`: Vertical position (negative because screen Y increases downward)
- `-(wx + wz) * isoHeight * 0.5f`: Depth effect (back-right points appear higher on screen)

### Scale Factor

The scale factor `size * 2.0f` determines the apparent size of the item:
- Larger values = bigger items on screen
- For standard 16x16 items, this is calibrated to match the inventory slot size
- The `isoWidth` and `isoHeight` are typically set to `scale * 0.5f`

### Block Geometry Capture

Before projection, the 3D block geometry must be captured:

```java
VertexCapture capture = new VertexCapture();
BlockGeometryCapture.captureWestFace(capture, 0, 0, 0);
BlockGeometryCapture.captureNorthFace(capture, 0, 0, 0);
BlockGeometryCapture.captureTopFace(capture, 0, 0, 0);
```

This generates vertex data for a **full cube** (0,0,0) to (1,1,1):
- Each face is composed of 2 triangles (6 vertices total)
- Each vertex has position (x,y,z) and texture coordinates (u,v)
- Coordinates are in normalized block space (0-1 range)

### Face Shading

To create depth perception, different faces receive different brightness values:

- **Top face**: 100% brightness (fully lit) - `glColor4f(1.0f, 1.0f, 1.0f, 1.0f)`
- **West face**: 80% brightness (medium) - `glColor4f(0.8f, 0.8f, 0.8f, 1.0f)`
- **North face**: 60% brightness (darker) - `glColor4f(0.6f, 0.6f, 0.6f, 1.0f)`

This shading simulates lighting from above-left, creating a 3D appearance.

### Rendering Order

Faces are rendered in back-to-front order for proper visibility:
1. West face (back-left)
2. North face (back-right)
3. Top face (front-top)

This ensures that closer faces draw over farther faces.

---

## Block Items vs Regular Items

The rendering system handles two distinct types of items differently:

### Block Items (3D Isometric Rendering)

**Identification**: Has block-specific texture keys (`"all"`, `"top"`, `"side"`, `"bottom"`)

**Examples**: stone, dirt, grass_block, cobblestone, planks, etc.

**Rendering Process**:
1. Load item model (e.g., `assets/models/item/stone.json`)
2. Follow parent to block model (e.g., `assets/models/block/stone.json`)
3. Resolve full model hierarchy with textures
4. Capture 3D geometry from model elements
5. Project to 2D isometric view
6. Render with face shading

**Geometry Source**:
- Standard cubes: `BlockGeometryCapture.captureTopFace/WestFace/NorthFace`
- Stairs: `BlockGeometryCapture.captureStairsSouthBottom` (special stepped geometry)
- Custom elements: Parsed from model's `elements` array (not yet implemented)

**Characteristics**:
- Appears as a 3D cube showing three faces
- Has depth and dimension
- Uses face-specific shading
- Properly represents block structure

### Regular Items (2D Flat Rendering)

**Identification**: Has `"layer0"` texture key, no block-specific keys

**Examples**: tools, food, materials (diamonds, sticks, etc.)

**Rendering Process**:
1. Load item model (e.g., `assets/models/item/diamond.json`)
2. Extract `"layer0"` texture path
3. Render as a simple textured quad (flat square)
4. No projection or 3D calculations

**Characteristics**:
- Appears flat (2D sprite)
- No depth or face shading
- Simpler and faster to render
- Appropriate for non-block items

### Why the Distinction?

Block items benefit from 3D representation because:
- They exist as 3D blocks in the game world
- Players expect to see their 3D structure in inventory
- The isometric view shows texture placement on different faces
- It matches Minecraft's visual style

Regular items don't need 3D rendering because:
- They're designed as 2D sprites
- 3D rendering would add unnecessary complexity
- The 2D appearance is more recognizable and cleaner

---

## The Torch Rendering Issue

### Problem Description

When rendered in the inventory, the torch item appears:
1. **Small**: Much smaller than other block items
2. **Upside down**: Appears inverted
3. **Centered too low**: Vertical position is incorrect

Meanwhile, other block items like stone render correctly.

### Root Cause Analysis

The torch has a **unique rendering issue** caused by its non-standard block geometry:

#### 1. Missing Item Model

**Critical Finding**: The torch has **NO** item model file.

```
assets/models/item/torch.json  ← DOES NOT EXIST
```

Other items have their item models:
```
assets/models/item/stone.json       ← EXISTS
assets/models/item/grass_block.json ← EXISTS
assets/models/item/cobblestone.json ← EXISTS
```

**What This Means**:

When `ResourceManager.getItemTexturePaths("torch")` is called:

```java
public static Map<String, String> getItemTexturePaths(String itemName) {
    BlockModel model = loadItemModel(itemName);  // Returns NULL for torch!
    if (model == null || model.getTextures() == null) {
        return null;  // ← Torch hits this path
    }
    // ... extract textures
}
```

The method returns `null` because there's no item model to load.

In `ItemRenderer.renderItem()`:
```java
Map<String, String> texturePaths = ResourceManager.getItemTexturePaths(itemName);
if (texturePaths == null || texturePaths.isEmpty()) {
    // Fallback: render magenta square
    renderFallbackItem(x, y, size);
    return;
}
```

**Expected Behavior**: The torch should render as a magenta square (missing texture indicator).

**Actual Behavior**: Something else is happening, suggesting there's a fallback path or the torch is being loaded differently.

#### 2. Non-Standard Block Geometry

The torch block model (`assets/models/block/torch.json`) defines unusual geometry:

```json
{
  "parent": "mattmc:block/template_torch",
  "textures": {
    "torch": "mattmc:block/torch"
  }
}
```

And `template_torch.json`:
```json
{
  "elements": [
    {
      "from": [ 7, 0, 7 ],
      "to": [ 9, 10, 9 ],
      ...
    }
  ]
}
```

**Analysis of Geometry**:

- **Normal block**: `[0, 0, 0]` to `[16, 16, 16]` (full cube, 16×16×16 units)
- **Torch**: `[7, 0, 7]` to `[9, 10, 9]` (thin stick, 2×10×2 units)

The torch is:
- **Width**: 2 units (12.5% of block width)
- **Height**: 10 units (62.5% of block height)
- **Depth**: 2 units (12.5% of block depth)
- **Position**: Centered in X/Z (7-9 out of 0-16), starts at bottom (Y=0)

#### 3. Isometric Projection Issues

When this non-standard geometry is captured and projected isometrically, several problems occur:

**Problem 1: Small Size**

The torch is only 2×10×2 units, while the projection system expects 16×16×16 units. The isometric projection formula:

```java
screenX = centerX + (wx - wz) * isoWidth
screenY = centerY - wy * isoHeight - (wx + wz) * isoHeight * 0.5f
```

When applied to the torch's coordinates:
- wx ranges from 7/16 = 0.4375 to 9/16 = 0.5625 (normalized)
- wy ranges from 0 to 10/16 = 0.625
- wz ranges from 7/16 = 0.4375 to 9/16 = 0.5625

The projected size is proportional to the coordinate range, making the torch appear much smaller than a full block.

**Problem 2: Wrong Position**

The torch's center is at (8, 5, 8) in block units, but the isometric projection assumes the block center is at (8, 8, 8). This causes vertical misalignment.

**Problem 3: Upside Down**

If the texture coordinates or face winding is incorrect for the torch geometry, it could appear upside down. This might be due to:
- Incorrect UV mapping in the template_torch.json
- Face culling or winding order issues
- Texture V-coordinate flipping (`1.0f - v` in rendering code)

#### 4. Current Rendering Path

Given the issues, here's what's likely happening:

**Scenario A: Fallback to Block Model**

If the item model doesn't exist, the system might be falling back to loading the block model directly:

```java
// If item model fails, maybe try block model?
if (texturePaths == null) {
    texturePaths = ResourceManager.getBlockTexturePaths(itemName);
}
```

This would load the torch block model, which has the problematic geometry.

**Scenario B: Special Handling**

There might be special code paths for certain items that aren't immediately visible in the main rendering function.

### Why Other Items Work

Items like stone work correctly because:

1. **They have item models**: `assets/models/item/stone.json` exists
2. **Standard geometry**: Full 16×16×16 cube
3. **Simple textures**: Cube with uniform or face-specific textures
4. **No special cases**: Follow the standard rendering path

The complete chain for stone:
```
Item: stone
→ ItemRenderer.renderItem()
→ ResourceManager.getItemTexturePaths("stone")
→ Load assets/models/item/stone.json
→ Parent: assets/models/block/stone.json
→ Parent: assets/models/block/cube_all.json
→ Elements: [0,0,0] to [16,16,16] (full cube)
→ Textures: { "all": "block/stone" }
→ Capture standard cube geometry
→ Project to isometric view
→ Render with proper shading
✓ Appears correct
```

### Expected Behavior

For the torch to render correctly as an item, one of the following approaches should be used:

**Option 1: Create Item Model with 2D Icon**

Create `assets/models/item/torch.json`:
```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "block/torch"
  }
}
```

This would render the torch as a flat 2D sprite, which is how most non-cube items are rendered.

**Option 2: Create Special 3D Item Model**

Create a custom item model with geometry optimized for isometric viewing:
```json
{
  "elements": [
    {
      "from": [ 6, 4, 6 ],
      "to": [ 10, 12, 10 ],
      ...
    }
  ]
}
```

Adjust the geometry to be larger and centered for better isometric appearance.

**Option 3: Special Rendering Code**

Add special handling in ItemRenderer for cross-shaped blocks like torches, flowers, and crops:
```java
if (isCrossModel(itemModel)) {
    renderCrossModelItem(texturePaths, x, y, size);
    return;
}
```

---

## Comparison with Working Items

Let's compare the complete rendering paths:

### Stone (Working)

```
1. Call: ItemRenderer.renderItem(stoneStack, x, y, size)
2. Extract: itemName = "stone"
3. Load: ResourceManager.getItemTexturePaths("stone")
   a. Load: assets/models/item/stone.json
      { "parent": "mattmc:block/stone" }
   b. Load: assets/models/block/stone.json
      { "parent": "mattmc:block/cube_all", "textures": { "all": "mattmc:block/stone" } }
   c. Load: assets/models/block/cube_all.json
      { "parent": "block/block", "elements": [...full cube...] }
   d. Merge and resolve textures
4. Result: { "all": "assets/textures/block/stone.png" }
5. Check: isBlockItem = true (has "all" key)
6. Capture geometry:
   - West face: (0,0,0)-(0,1,1) (full face, 1×1 units)
   - North face: (0,0,0)-(1,1,0) (full face, 1×1 units)
   - Top face: (0,1,0)-(1,1,1) (full face, 1×1 units)
7. Project to isometric coordinates
8. Render with textures and shading
9. Result: ✓ Correct appearance
```

### Torch (Broken)

```
1. Call: ItemRenderer.renderItem(torchStack, x, y, size)
2. Extract: itemName = "torch"
3. Load: ResourceManager.getItemTexturePaths("torch")
   a. Load: assets/models/item/torch.json
      ✗ FILE NOT FOUND
   b. Return: null
4. Result: null or empty map
5. Check: texturePaths == null || isEmpty() → TRUE
6. Fallback: renderFallbackItem(x, y, size)
   - Should render magenta square
   
OR (if there's undocumented fallback):

4. Alternative: Try block model instead
   a. Load: assets/models/block/torch.json
      { "parent": "mattmc:block/template_torch", "textures": { "torch": "mattmc:block/torch" } }
   b. Load: assets/models/block/template_torch.json
      { "elements": [{ "from": [7, 0, 7], "to": [9, 10, 9], ... }] }
5. Result: { "torch": "assets/textures/block/torch.png" }
6. Check: isBlockItem = ? (depends on key names)
7. Capture geometry:
   - Problem: Non-standard coordinates (7-9, 0-10, 7-9)
   - West face: (7/16, 0, 7/16)-(7/16, 10/16, 9/16)
   - North face: (7/16, 0, 7/16)-(9/16, 10/16, 7/16)
   - Top face: (7/16, 10/16, 7/16)-(9/16, 10/16, 9/16)
8. Project to isometric coordinates
   - ✗ Small size (2×10×2 instead of 16×16×16)
   - ✗ Off-center position
   - ✗ Potentially upside down
9. Result: ✗ Incorrect appearance (small, misaligned, inverted)
```

### Key Differences

| Aspect | Stone | Torch |
|--------|-------|-------|
| Item model exists | ✓ Yes | ✗ No |
| Model resolution | Complete chain | Broken chain |
| Geometry | Full cube (0-16, 0-16, 0-16) | Partial stick (7-9, 0-10, 7-9) |
| Size after projection | Full item slot | ~12% width, ~62% height |
| Position | Centered | Off-center and low |
| Texture mapping | Standard | Potentially incorrect |
| Rendering result | ✓ Correct | ✗ Small, low, inverted |

---

## Technical Details

### Model Element Structure

Model elements define the 3D geometry using cuboids:

```json
{
  "from": [x1, y1, z1],
  "to": [x2, y2, z2],
  "faces": {
    "north": { "uv": [u1, v1, u2, v2], "texture": "#texture_var" },
    "south": { "uv": [u1, v1, u2, v2], "texture": "#texture_var" },
    "east": { "uv": [u1, v1, u2, v2], "texture": "#texture_var" },
    "west": { "uv": [u1, v1, u2, v2], "texture": "#texture_var" },
    "up": { "uv": [u1, v1, u2, v2], "texture": "#texture_var" },
    "down": { "uv": [u1, v1, u2, v2], "texture": "#texture_var" }
  }
}
```

Coordinates are in pixel units (0-16 range for a full block).

### Current Limitations

The current implementation has several limitations:

1. **No Custom Element Parsing**: The system doesn't parse and render custom model elements. It only uses hardcoded geometry capture methods (cube faces, stairs).

2. **No Cross Model Support**: Models with cross geometry (X-shaped, like torches, flowers, saplings) aren't supported.

3. **No Item-Specific Overrides**: Can't specify different display transforms for inventory vs hand vs GUI.

4. **Limited Geometry Types**: Only supports:
   - Full cubes (via captureTopFace/WestFace/NorthFace)
   - Stairs blocks (via captureStairsSouthBottom)
   - No slabs, fences, panes, or custom shapes

5. **Missing Generated Item Support**: The `item/generated` parent, commonly used for 2D items, might not be fully supported.

### UV Coordinate Systems

Understanding UV coordinates is crucial for proper texture mapping:

**Texture Space** (0-1 normalized):
- U: Horizontal axis (0 = left, 1 = right)
- V: Vertical axis (0 = top, 1 = bottom)

**Model Space** (0-16 pixel units):
- UV coordinates in JSON are in pixel units
- Must be normalized when loading: `u_normalized = u / 16.0f`

**OpenGL Convention**:
- V=0 at bottom, V=1 at top (opposite of texture space)
- The code accounts for this: `glTexCoord2f(u, 1.0f - v)`

### Vertex Capture Process

The `VertexCapture` class accumulates vertices in groups of three (triangles):

```java
VertexCapture capture = new VertexCapture();

// Each quad is split into 2 triangles
// Triangle 1
capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);  // Top-left
capture.texCoord(0, 1); capture.addVertex(x0, y1, z1);  // Bottom-left
capture.texCoord(1, 1); capture.addVertex(x1, y1, z1);  // Bottom-right

// Triangle 2
capture.texCoord(0, 0); capture.addVertex(x0, y1, z0);  // Top-left
capture.texCoord(1, 1); capture.addVertex(x1, y1, z1);  // Bottom-right
capture.texCoord(1, 0); capture.addVertex(x1, y1, z0);  // Top-right
```

Every 3 vertices are automatically grouped into a `Face` object.

### Rendering Implementation

The final rendering uses immediate mode OpenGL:

```java
glBegin(GL_TRIANGLES);
for (VertexCapture.Face face : faces) {
    // Vertex 1
    float x1 = project2Dx(face.v1.x, face.v1.y, face.v1.z, centerX, isoWidth);
    float y1 = project2Dy(face.v1.x, face.v1.y, face.v1.z, centerY, isoHeight);
    glTexCoord2f(face.v1.u, 1.0f - face.v1.v);
    glVertex2f(x1, y1);
    
    // Vertex 2
    float x2 = project2Dx(face.v2.x, face.v2.y, face.v2.z, centerX, isoWidth);
    float y2 = project2Dy(face.v2.x, face.v2.y, face.v2.z, centerY, isoHeight);
    glTexCoord2f(face.v2.u, 1.0f - face.v2.v);
    glVertex2f(x2, y2);
    
    // Vertex 3
    float x3 = project2Dx(face.v3.x, face.v3.y, face.v3.z, centerX, isoWidth);
    float y3 = project2Dy(face.v3.x, face.v3.y, face.v3.z, centerY, isoHeight);
    glTexCoord2f(face.v3.u, 1.0f - face.v3.v);
    glVertex2f(x3, y3);
}
glEnd();
```

---

## Potential Solutions for Torch

To fix the torch rendering issue, consider these approaches:

### Solution 1: Add Item Model with 2D Sprite (Recommended)

Create `assets/models/item/torch.json`:
```json
{
  "parent": "item/generated",
  "textures": {
    "layer0": "block/torch"
  }
}
```

**Pros**: 
- Simple and matches Minecraft's approach
- Torches work better as 2D sprites in inventory
- Clear and recognizable icon

**Cons**:
- Doesn't show 3D structure
- Requires implementing `item/generated` parent support

### Solution 2: Custom Item Geometry

Create a special item model with larger, centered geometry:
```json
{
  "elements": [
    {
      "from": [6, 4, 6],
      "to": [10, 14, 10],
      "faces": {
        "north": {"uv": [7, 2, 9, 12], "texture": "#torch"},
        "south": {"uv": [7, 2, 9, 12], "texture": "#torch"},
        "east": {"uv": [7, 2, 9, 12], "texture": "#torch"},
        "west": {"uv": [7, 2, 9, 12], "texture": "#torch"},
        "up": {"uv": [7, 6, 9, 8], "texture": "#torch"},
        "down": {"uv": [7, 13, 9, 15], "texture": "#torch"}
      }
    }
  ],
  "textures": {
    "torch": "block/torch"
  }
}
```

**Pros**:
- Shows 3D structure
- Can be tailored for optimal isometric appearance

**Cons**:
- More complex
- Requires element parsing implementation
- May still look odd due to non-cubic shape

### Solution 3: Special Cross-Model Renderer

Implement special handling for cross-shaped models:

```java
// In ItemRenderer.renderItem():
if (isCrossModel(itemModel)) {
    renderCrossModelAsFlat(texturePath, x, y, size);
    return;
}
```

**Pros**:
- Works for all cross-shaped items (flowers, crops, etc.)
- Can create a nice flat representation

**Cons**:
- Adds complexity to rendering code
- Needs detection logic for cross models

### Solution 4: Fix Block Model Geometry

Modify `assets/models/block/template_torch.json` to use more centered geometry:

```json
{
  "elements": [
    {
      "from": [7, 3, 7],
      "to": [9, 13, 9],
      ...
    }
  ]
}
```

**Pros**:
- Simple change
- Maintains 3D rendering

**Cons**:
- Affects block rendering in world
- Still appears small due to thin geometry
- May break torch placement in world

---

## Conclusion

The MattMC item rendering system uses a sophisticated approach combining JSON model loading, parent inheritance, texture resolution, 3D geometry capture, and isometric projection to render items in the inventory.

The torch rendering issue is caused by:
1. **Missing item model file** causing fallback behavior
2. **Non-standard block geometry** designed for in-world placement, not inventory display
3. **Isometric projection** not handling non-cubic geometries well

Other items work correctly because they have proper item models and use standard cubic geometry that projects well to isometric view.

**Recommended Fix**: Create `assets/models/item/torch.json` with a 2D sprite approach using the `item/generated` parent and `layer0` texture.

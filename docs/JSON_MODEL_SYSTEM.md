# JSON Model System Implementation

## Overview

This implementation follows Minecraft's JSON model system architecture for loading and rendering blocks and items. The system supports:

1. **Blockstate JSON files** - Define which model to use for each block state
2. **Block model JSON files** - Define geometry (elements), textures, and display transforms
3. **Item model JSON files** - Reference block models or define custom item models
4. **Parent model inheritance** - Models can extend parent models
5. **Texture variable resolution** - Texture references like `#side` are resolved to actual paths

## Key Components

### 1. Model Data Structures

**BlockModel** (`mattmc.client.resources.model.BlockModel`)
- Represents a block/item model JSON file
- Contains: parent, textures, elements, display transforms, ambient occlusion

**ModelElement** (`mattmc.client.resources.model.ModelElement`)
- Represents a cuboid geometry element
- Contains: from/to coordinates, faces with UV mapping, rotation, shading

**BlockState** (`mattmc.client.resources.model.BlockState`)
- Represents a blockstate JSON file
- Supports both single variant and array variant formats
- Handles variant selection based on block properties

**ItemModelWrapper** (`mattmc.client.resources.model.ItemModelWrapper`)
- Handles the custom item model format:
  ```json
  {
    "model": {
      "type": "mattmc:model",
      "model": "mattmc:block/cobblestone"
    }
  }
  ```

### 2. Resource Loading (ResourceManager)

**Model Resolution Process:**

1. **Load Raw Model** - Load JSON file from resources
2. **Resolve Parent Chain** - Recursively load and merge parent models
3. **Merge Properties** - Child properties override parent (textures, elements, display)
4. **Resolve Texture Variables** - Replace `#variable` references with actual paths
5. **Cache Result** - Store resolved model for reuse

**Example Resolution:**

```
cobblestone.json (child)
  parent: "mattmc:block/cube_all"
  textures: { "all": "mattmc:block/cobblestone" }
  
→ Loads cube_all.json (parent)
  elements: [ { cube with 6 faces } ]
  textures: { "particle": "#all" }
  
→ Merged Result:
  textures: { "all": "mattmc:block/cobblestone", "particle": "mattmc:block/cobblestone" }
  elements: [ { cube with faces textured with "mattmc:block/cobblestone" } ]
```

### 3. Blockstate Variant Selection

Blockstates can have multiple variants based on block properties:

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

The system:
1. Parses variant conditions (e.g., "facing=north,half=bottom")
2. Matches block properties to select the correct variant
3. Applies transformations (rotation x/y, uvlock)

## Model Geometry

Models use **elements** to define 3D geometry:

```json
{
  "elements": [
    {
      "from": [0, 0, 0],
      "to": [16, 8, 16],
      "faces": {
        "down": { "uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down" },
        "up": { "uv": [0, 0, 16, 16], "texture": "#top" }
      }
    }
  ]
}
```

Each element:
- Defines a cuboid from `from` to `to` coordinates (0-16 range)
- Has up to 6 faces (up, down, north, south, east, west)
- Each face has UV coordinates, texture reference, and optional cullface

## Parent Models

### cube_all.json
Simple cube with one texture on all faces.
```json
{
  "parent": "block/block",
  "textures": { "particle": "#all" },
  "elements": [ /* single cube element */ ]
}
```

### stairs.json
Standard stairs with 2 elements (bottom slab + top corner).
```json
{
  "parent": "block/block",
  "textures": { "particle": "#side" },
  "elements": [ /* 2 stair elements */ ],
  "display": { /* GUI/hand transforms */ }
}
```

### inner_stairs.json
Inner corner stairs with 3 elements.

### outer_stairs.json
Outer corner stairs with 2 elements.

## Usage Examples

### Loading a Block Model

```java
BlockModel model = ResourceManager.loadBlockModel("cobblestone");
// Returns fully resolved model with textures and elements
```

### Loading Block Textures

```java
Map<String, String> textures = ResourceManager.getBlockTexturePaths("cobblestone");
// Returns: {"all": "assets/textures/block/cobblestone.png"}
```

### Loading Item Model

```java
BlockModel itemModel = ResourceManager.loadItemModel("cobblestone");
// Resolves the custom wrapper format and loads the referenced block model
```

### Loading Blockstate

```java
BlockState blockState = ResourceManager.loadBlockState("birch_stairs");
List<BlockStateVariant> variants = blockState.getVariantsForState("facing=north,half=bottom,shape=straight");
// Returns the variant(s) for that specific state
```

## Texture Variable Resolution

Textures can use variables (e.g., `#side`, `#all`) that get resolved:

1. Model defines: `"textures": { "side": "block/planks", "particle": "#side" }`
2. Element face uses: `"texture": "#side"`
3. Resolution: `#particle` → `#side` → `block/planks` → `mattmc:block/planks`

## Namespace Handling

The system supports both formats:
- With namespace: `"mattmc:block/cobblestone"`
- Without namespace: `"block/cobblestone"` (assumes `mattmc:` namespace)

Namespaces are stripped when converting to file paths:
- `"mattmc:block/cobblestone"` → `"assets/textures/block/cobblestone.png"`

## Testing

Comprehensive tests in `ModelLoadingTest.java` verify:
- Blockstate loading
- Model parent resolution
- Texture variable resolution
- Element inheritance
- Variant parsing
- Namespace handling

## Future Enhancements

The system is ready for:
1. **Geometry Generation** - Use elements to generate actual 3D meshes
2. **Display Transforms** - Apply GUI/hand/head transforms for rendering
3. **Rotation & UV Lock** - Apply blockstate variant rotations
4. **Multipart Models** - Support complex conditional geometry
5. **Custom Model Loaders** - Extend for special model types

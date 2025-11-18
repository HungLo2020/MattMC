# Lighting System Explanation: Transparency and Opaqueness

## Overview
This document explains how the lighting system works in MattMC, with a particular focus on how block transparency and opaqueness are determined and used. This is in response to questions about the apparent discrepancy between the boolean `solid` parameter in the Block constructor and the 0-15 opacity values used throughout the lighting system.

---

## Table of Contents
1. [The Block Constructor: Boolean vs Opacity](#the-block-constructor-boolean-vs-opacity)
2. [How Opacity is Determined](#how-opacity-is-determined)
3. [Opacity in the Lighting System](#opacity-in-the-lighting-system)
4. [Light Propagation and Opacity](#light-propagation-and-opacity)
5. [Storage Format: RGBI System](#storage-format-rgbi-system)
6. [Complete Data Flow](#complete-data-flow)
7. [Key Implementation Details](#key-implementation-details)
8. [Known Issues with Opacity](#known-issues-with-opacity)

---

## The Block Constructor: Boolean vs Opacity

### The Apparent Contradiction

When you look at the Block constructor in `Block.java`, you see:

```java
public Block(boolean solid) {
    this(solid, 0, 0, 0, 0);
}
```

This takes a **boolean** `solid` parameter, but throughout the lighting system, you'll see opacity being checked as a **0-15 value**:

```java
// From LightPropagator.java:142
if (block == null || block.getOpacity() >= 15) {
    return; // Fully opaque block stops light
}
```

### How This Works

The key is in the `Block.getOpacity()` method in `Block.java:228-231`:

```java
/**
 * Get the opacity of this block (how much it blocks light).
 * 
 * @return Opacity level (0-15), where 0 means fully transparent and 15 means fully opaque
 */
public int getOpacity() {
    // By default, solid blocks are fully opaque (15), air and non-solid blocks are transparent (0)
    return solid ? 15 : 0;
}
```

**This is the conversion point!**

- When you create a block with `new Block(true)`, it's marked as solid
- When lighting code calls `getOpacity()`, it returns **15** (fully opaque)
- When you create a block with `new Block(false)`, it's marked as non-solid
- When lighting code calls `getOpacity()`, it returns **0** (fully transparent)

### Why Use Both Systems?

The boolean `solid` property is used for:
- **Physics/Collision**: Does the player collide with this block? (via `isSolid()`)
- **Default behavior**: Simple yes/no for most game logic

The 0-15 opacity value is used for:
- **Light propagation**: How much does this block block light?
- **Future extensibility**: Allows for semi-transparent blocks (glass, water, leaves)

---

## How Opacity is Determined

### Default Implementation

In the base `Block` class:

```java
public int getOpacity() {
    return solid ? 15 : 0;
}
```

This provides a simple binary system:
- **Solid blocks (solid=true)**: opacity = 15 (fully opaque, blocks all light)
- **Non-solid blocks (solid=false)**: opacity = 0 (fully transparent, light passes through)

### Future Extensibility for Semi-Transparent Blocks

The 0-15 scale allows for blocks that partially block light:

| Opacity Value | Light Behavior | Example Block Types |
|--------------|----------------|---------------------|
| 0 | Fully transparent | Air, torch (non-solid emissive) |
| 1-14 | Semi-transparent | Glass, leaves, water, ice (not currently implemented) |
| 15 | Fully opaque | Stone, dirt, wood, most solid blocks |

**Note**: Currently, the system treats any opacity >= 15 as "blocks light completely" and opacity < 15 as "lets light through completely". There's no partial attenuation implemented yet, but the infrastructure is there for future enhancement.

### Overriding for Custom Blocks

Subclasses can override `getOpacity()` to provide custom opacity values:

```java
public class GlassBlock extends Block {
    public GlassBlock() {
        super(true); // Solid for collision
    }
    
    @Override
    public int getOpacity() {
        return 5; // Semi-transparent for lighting (not yet properly supported)
    }
}
```

---

## Opacity in the Lighting System

### Two Types of Light

The lighting system manages two types of light:

1. **Block Light (RGBI)**: Light emitted by blocks (torches, lanterns, etc.)
   - Stored as Red, Green, Blue, Intensity (4 bits each = 16 bits total per block)
   - Propagates outward from light-emitting blocks
   - Attenuates by 1 per block distance

2. **Skylight**: Natural light from the sky
   - Stored as single value (4 bits = 0-15)
   - Full brightness (15) above ground
   - Propagates downward and sideways into caves
   - Attenuates by 1 per block distance

### How Opacity Blocks Light

#### Block Light Propagation (`LightPropagator.java:124-166`)

When block light tries to propagate from one position to a neighbor:

```java
private void propagateRGBIToNeighbor(LevelChunk chunk, int x, int y, int z, 
                                      int r, int g, int b, int newI) {
    // ... boundary checks ...
    
    // Check if block is opaque (blocks light)
    Block block = chunk.getBlock(x, y, z);
    if (block == null || block.getOpacity() >= 15) {
        return; // Fully opaque block stops light or null
    }
    
    // Light can propagate - continue with propagation logic
    // ...
}
```

**Key Points:**
- If `opacity >= 15`: Light does NOT propagate into this position
- If `opacity < 15`: Light CAN propagate into this position
- The check uses the **0-15 value** from `getOpacity()`, not the boolean `solid`

#### Skylight Propagation (`SkylightEngine.java:144-162`)

Similar logic for skylight:

```java
private void propagateSkyToNeighbor(LevelChunk chunk, int x, int y, int z, int newLight) {
    // ... boundary checks ...
    
    // Check if block is opaque
    Block block = chunk.getBlock(x, y, z);
    if (block == null || block.getOpacity() >= 15) {
        return; // Opaque block stops light or null block
    }
    
    // Light can propagate
    // ...
}
```

### Heightmap and Opacity

The heightmap tracks the topmost opaque block in each column, which affects skylight:

```java
// From SkylightEngine.java:373-383
private int findTopmostOpaqueBlock(LevelChunk chunk, int x, int z) {
    for (int y = LevelChunk.HEIGHT - 1; y >= 0; y--) {
        Block block = chunk.getBlock(x, y, z);
        if (block != null && block.getOpacity() > 0) {  // ANY opacity > 0
            return LevelChunk.chunkYToWorldY(y);
        }
    }
    return LevelChunk.MIN_Y; // No opaque blocks found
}
```

**Important Note**: There's currently an inconsistency here (documented in LIGHTING-BUGS.md as BUG-001):
- Heightmap calculation uses `opacity > 0` (any opacity counts)
- Light propagation uses `opacity >= 15` (only fully opaque blocks)
- This causes issues with semi-transparent blocks like glass

---

## Light Propagation and Opacity

### Breadth-First Search (BFS) Algorithm

Light propagates using a BFS algorithm with attenuation:

1. **Start**: Light-emitting block has intensity 15, color (R,G,B)
2. **Propagate**: For each neighbor that isn't opaque:
   - New intensity = current intensity - 1
   - Color stays the same (R,G,B preserved)
   - Add neighbor to propagation queue
3. **Stop**: When intensity reaches 0 or hits opaque block

### Example Propagation

Consider a torch at position (5, 64, 5) with RGB=(14, 14, 11) (warm white/orange):

```
Position        Opacity  Block Light RGBI    Can Propagate?
(5, 64, 5)      0        (14,14,11,14)       Source
(6, 64, 5)      0 (air)  (14,14,11,13)       ✓ YES - intensity reduced to 13
(7, 64, 5)      15       (0,0,0,0)           ✗ NO - stone blocks light
(5, 64, 6)      0 (air)  (14,14,11,13)       ✓ YES
```

### The Opacity Check in Detail

```java
Block block = chunk.getBlock(x, y, z);
if (block == null || block.getOpacity() >= 15) {
    return; // Stop propagation
}
```

Breaking this down:
1. `chunk.getBlock(x, y, z)` - Get the block at target position
2. `block.getOpacity()` - Call the method that converts `solid` boolean to 0-15 value
3. `>= 15` - Check if fully opaque
4. If true: Light cannot propagate, return immediately
5. If false: Continue with propagation logic

---

## Storage Format: RGBI System

### Why RGBI Instead of Just RGB?

Block light is stored in **RGBI format** - Red, Green, Blue, **and** Intensity:

```java
// From LightStorage.java:254-257
// Pack RGBI into 2 bytes: RRRRGGGG BBBBIIII
int packed = ((r & 0x0F) << 12) | ((g & 0x0F) << 8) | 
             ((b & 0x0F) << 4) | (i & 0x0F);
```

**Why store Intensity separately when it's max(R,G,B)?**

1. **Performance**: Comparing intensities is faster than unpacking RGB values
2. **Propagation logic**: The algorithm compares intensities frequently
3. **Trade-off**: Uses extra 4 bits per block, but saves computation time

### Storage Layout

For each block position, light data is stored as:

| Component | Bits | Range | Purpose |
|-----------|------|-------|---------|
| Red (R) | 4 | 0-15 | Red color channel |
| Green (G) | 4 | 0-15 | Green color channel |
| Blue (B) | 4 | 0-15 | Blue color channel |
| Intensity (I) | 4 | 0-15 | Overall brightness (usually max(R,G,B)) |
| **Total** | **16** | - | **2 bytes per block** |

Plus skylight in a separate array:

| Component | Bits | Range | Purpose |
|-----------|------|-------|---------|
| Skylight | 4 | 0-15 | Natural light from sky |

### Memory Layout in LightStorage

```java
// From LightStorage.java:13-23
private static final int SECTION_SIZE = 16;
private static final int TOTAL_BLOCKS = 16 * 16 * 16; // 4096

// Skylight: 4 bits per block = 2048 bytes total (nibble array)
private final byte[] skyLight = new byte[2048];

// Block light RGBI: 16 bits per block = 8192 bytes total
private final byte[] blockLight = new byte[8192];
```

Per 16×16×16 chunk section:
- Skylight: 2,048 bytes (4 bits × 4096 blocks, packed)
- Block light: 8,192 bytes (16 bits × 4096 blocks)
- **Total: 10,240 bytes per section**

---

## Complete Data Flow

### When a Block is Placed/Removed

```
User Action: Place/Remove Block
         ↓
Level.setBlock(x, y, z, newBlock)
         ↓
WorldLightManager.updateBlockLight(chunk, x, y, z, newBlock, oldBlock)
         ↓
   ┌────┴────┐
   ↓         ↓
Block Light  Skylight
Propagation  Propagation
   ↓         ↓
LightPropagator.updateBlockLight()
         ↓
   Check oldBlock.getOpacity()  ← Boolean solid converted to 0 or 15
   Check newBlock.getOpacity()  ← Boolean solid converted to 0 or 15
         ↓
   If opacity changed:
      - Remove old light if opaque
      - Add new light if emissive
      - Propagate using BFS
         ↓
   For each neighbor:
      block.getOpacity() >= 15?  ← Check if light can propagate
         ↓
      NO: Propagate light (intensity - 1)
      YES: Stop propagation
         ↓
LightStorage.setBlockLightRGBI(x, y, z, r, g, b, i)
         ↓
   Pack into 2 bytes: RRRRGGGG BBBBIIII
         ↓
   Store in byte array at position (x,y,z)
         ↓
Chunk.setDirty(true) - Mark for mesh rebuild
         ↓
MeshBuilder rebuilds chunk mesh with new lighting
         ↓
VertexLightSampler samples light at vertices
         ↓
Shader applies lighting in rendering
```

### Step-by-Step Example

**Scenario**: Player places a stone block in an area lit by a torch

1. **Block Creation**: `new Block(true)` → solid=true
2. **Opacity Query**: Light system calls `getOpacity()` → returns 15
3. **Light Check**: System checks `getOpacity() >= 15` → true
4. **Remove Light**: Light at stone position is cleared (becomes 0,0,0,0)
5. **No Propagation**: Light from neighbors cannot enter (opacity=15 blocks it)
6. **Storage Update**: LightStorage updates the 2 bytes at this position to 0x0000
7. **Mesh Rebuild**: Chunk mesh is rebuilt with darker stone block
8. **Rendering**: Shader renders the stone with minimal lighting (ambient only)

---

## Key Implementation Details

### 1. Opacity Check Pattern

You'll see this pattern everywhere in the lighting code:

```java
Block block = chunk.getBlock(x, y, z);
if (block == null || block.getOpacity() >= 15) {
    return; // Don't propagate light here
}
```

**What this means:**
- `block == null`: Defensive check (shouldn't happen, but treated as opaque)
- `block.getOpacity()`: Gets the 0-15 value (converted from boolean solid)
- `>= 15`: Checks if fully opaque
- If true: Light stops, don't propagate
- If false: Light can propagate through this block

### 2. isOpaque() vs getOpacity()

There are two related methods in Block:

```java
// Boolean check (for convenience)
public boolean isOpaque() {
    return solid;  // Line 192
}

// Numeric value (for lighting)
public int getOpacity() {
    return solid ? 15 : 0;  // Line 230
}
```

**Usage:**
- `isOpaque()`: Used for game logic, returns true/false
- `getOpacity()`: Used for lighting, returns 0-15

Both derive from the same `solid` boolean, but:
- `isOpaque()` returns the boolean directly
- `getOpacity()` converts it to a number the lighting system can use

### 3. The >= 15 Threshold

The lighting system uses `>= 15` not `== 15`:

```java
if (block.getOpacity() >= 15) {
    return; // Block is opaque
}
```

**Why >= instead of ==?**

This allows for future expansion:
- Currently: Only 0 and 15 are used (from boolean solid)
- Future: Could have blocks with opacity 16, 17, etc. for special behaviors
- Or: Could clamp values that exceed 15 due to bugs

However, the current `getOpacity()` implementation only returns 0 or 15, so `>= 15` and `== 15` are equivalent in practice.

### 4. Null Block Handling

Notice the null check:

```java
if (block == null || block.getOpacity() >= 15) {
```

**Why null?**

- Null blocks should never exist in a properly functioning chunk
- But defensive programming treats them as opaque
- This prevents crashes if data corruption occurs
- Null is considered "blocks light" rather than "lets light through"

---

## Known Issues with Opacity

### Issue 1: Inconsistent Threshold (BUG-001)

**The Problem:**

Different parts of the code use different opacity thresholds:

```java
// SkylightInitializer.java:84 - Heightmap calculation
if (block.getOpacity() > 0) {  // ANY opacity counts as opaque
    heightmap = y;
}

// SkylightEngine.java:153 - Light propagation
if (block.getOpacity() >= 15) {  // Only FULL opacity blocks light
    return;
}
```

**Impact:**

If you created a glass block with `opacity = 5`:
- Heightmap would treat it as opaque (opacity > 0)
- Light propagation would let light through (opacity < 15)
- Result: Skylight above glass is 15, but heightmap thinks there's a solid block there

**Why This Exists:**

The system was designed with the assumption that all blocks are either:
- Fully solid (opacity = 15)
- Fully transparent (opacity = 0)

But the infrastructure for semi-transparent blocks exists, creating this inconsistency.

### Issue 2: No Partial Attenuation

**Current Behavior:**

```java
if (block.getOpacity() >= 15) {
    return; // Blocks ALL light
} else {
    // Lets ALL light through (minus normal attenuation)
}
```

**What's Missing:**

There's no code like:

```java
// Hypothetical semi-transparency support:
int newIntensity = currentIntensity - 1;
if (block.getOpacity() > 0 && block.getOpacity() < 15) {
    newIntensity -= block.getOpacity(); // Extra attenuation for semi-transparent
}
```

**Result:**

- Glass (if implemented) would let 100% of light through
- Water (if implemented) would let 100% of light through
- No realistic light dimming through semi-transparent materials

---

## Summary: The Boolean-to-Numeric Conversion

### The Key Insight

**The boolean `solid` parameter in the constructor is NOT what the lighting system uses directly.**

Instead:

1. **Construction**: `new Block(true)` sets `solid = true`
2. **Conversion**: `getOpacity()` method converts `true → 15, false → 0`
3. **Usage**: Lighting code uses the **0-15 value** from `getOpacity()`

### Visualization

```
Block Constructor         Block Field          Lighting System
─────────────────────────────────────────────────────────────
new Block(true)     →    solid = true    →    getOpacity() = 15
                                          →    Light blocked ✗

new Block(false)    →    solid = false   →    getOpacity() = 0
                                          →    Light passes ✓

new Block(false, 14) →   solid = false   →    getOpacity() = 0
(torch)                  emission = 14        Light emitted ☀
                                          →    Light passes ✓
```

### Why This Design?

1. **Simplicity**: Most code just needs "solid or not" (boolean)
2. **Extensibility**: Lighting needs granular control (0-15)
3. **Performance**: Boolean checks are fast for collision detection
4. **Future-proofing**: Numeric opacity allows semi-transparent blocks later

### The Takeaway

When you see:
- `new Block(true)` → Creates a solid block
- In lighting code → Becomes `opacity = 15` → Blocks all light
- `new Block(false)` → Creates a non-solid block  
- In lighting code → Becomes `opacity = 0` → Lets light pass

The "0-15 value" you see in the lighting system is **derived from** the boolean, not a separate value you need to set. The `getOpacity()` method does the conversion automatically.

---

## Related Files Reference

For deeper understanding, examine these files:

1. **Block.java** (lines 189-231)
   - `isOpaque()` method
   - `getOpacity()` method

2. **LightPropagator.java** (lines 124-166)
   - `propagateRGBIToNeighbor()` - block light propagation
   - Opacity check at line 142

3. **SkylightEngine.java** (lines 144-162)
   - `propagateSkyToNeighbor()` - skylight propagation
   - Opacity check at line 153

4. **LightStorage.java** (lines 1-291)
   - RGBI packing format
   - Storage layout and access methods

5. **LIGHTING-BUGS.md**
   - BUG-001: Opacity threshold inconsistency
   - Complete list of lighting system issues

---

**Document Author**: GitHub Copilot  
**Date**: 2024-11-18  
**Purpose**: Explain lighting system in response to user question about boolean vs 0-15 opacity values

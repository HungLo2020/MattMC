# Quick Summary: How Block Opacity Works

## Your Question
> "I want you to tell how a block's 'opaqueness' is determined? From what I can tell it seems like a 0-15 value is used but in the block constructor it's a boolean value."

## The Answer

You're absolutely right to notice this! Here's what's happening:

### 1. Constructor Uses Boolean

```java
// In Blocks.java - registering blocks
public static final Block STONE = register("stone", new Block(true));   // solid=true
public static final Block AIR = register("air", new Block(false));      // solid=false
```

### 2. Lighting System Uses 0-15

```java
// In LightPropagator.java - checking if light can propagate
Block block = chunk.getBlock(x, y, z);
if (block.getOpacity() >= 15) {  // Using 0-15 value!
    return; // Block is opaque, stop light propagation
}
```

### 3. The Conversion Happens Here

```java
// In Block.java:228-231
public int getOpacity() {
    // This is where the boolean becomes a number!
    return solid ? 15 : 0;
}
```

**Translation:**
- `solid = true` (from constructor) → `getOpacity()` returns **15** (fully opaque)
- `solid = false` (from constructor) → `getOpacity()` returns **0** (fully transparent)

## Visual Explanation

```
Block Creation          Internal Field         Lighting System Query
────────────────────────────────────────────────────────────────────
new Block(true)    →    solid = true     →     getOpacity() = 15
                                         →     ✗ Blocks ALL light

new Block(false)   →    solid = false    →     getOpacity() = 0  
                                         →     ✓ Lets light pass
```

## Why Both Systems?

### Boolean `solid` is used for:
- Player collision (do you bump into this block?)
- Simple game logic (is this a real block or air?)
- Fast boolean checks

### Numeric `opacity` (0-15) is used for:
- Light propagation (does light pass through?)
- Future semi-transparent blocks (glass, water, leaves)
- Granular control over light blocking

## Current Implementation

Right now, the system only uses two values:
- **0** - Fully transparent (air, non-solid blocks)
- **15** - Fully opaque (stone, dirt, all solid blocks)

But the infrastructure supports **0-15** for future features like:
- Glass (maybe opacity = 5)
- Water (maybe opacity = 8)  
- Leaves (maybe opacity = 3)

These would partially block light instead of being all-or-nothing.

## The Key Pattern

Everywhere in the lighting code, you'll see this pattern:

```java
Block block = chunk.getBlock(x, y, z);
if (block == null || block.getOpacity() >= 15) {
    // Block is opaque - don't let light through
    return;
}
// Block is transparent - let light propagate
```

The `getOpacity()` method automatically converts the boolean `solid` field into the 0-15 value that the lighting system needs.

## Where to Find This

1. **Block.java** - Line 228-231: `getOpacity()` method with the conversion
2. **LightPropagator.java** - Line 142: Opacity check in block light propagation
3. **SkylightEngine.java** - Line 153: Opacity check in skylight propagation
4. **Blocks.java** - Lines 50-73: Block registration with boolean constructor

## Full Details

For the complete explanation with diagrams, code flow, storage format, and more, see:
**LIGHTING-SYSTEM-EXPLANATION.md**

---

**Bottom Line:** The boolean you pass to the constructor is stored in the `solid` field, and the `getOpacity()` method converts it to 0 or 15 for the lighting system to use. You see 0-15 in the lighting code because it calls `getOpacity()`, not because you need to pass a number to the constructor!

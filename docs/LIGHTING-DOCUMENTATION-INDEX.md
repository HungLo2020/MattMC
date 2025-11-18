# Lighting System Documentation Index

This directory contains comprehensive documentation about the MattMC lighting system, specifically explaining how block transparency and opaqueness work.

## Quick Start

**Question**: How is a block's "opaqueness" determined? It seems like a 0-15 value is used in the lighting system, but the block constructor takes a boolean?

**Answer**: See → [**LIGHTING-OPACITY-SUMMARY.md**](LIGHTING-OPACITY-SUMMARY.md)

## Documentation Files

### 1. LIGHTING-OPACITY-SUMMARY.md
**Purpose**: Quick answer to the opacity question  
**Length**: ~3 pages  
**Best for**: Understanding the boolean → 0-15 conversion

**Contents:**
- Direct answer to "boolean vs 0-15" question
- Visual diagrams of the conversion
- Code examples showing where this happens
- Quick reference guide

### 2. LIGHTING-SYSTEM-EXPLANATION.md  
**Purpose**: Complete technical deep-dive  
**Length**: ~18 pages  
**Best for**: Understanding the entire lighting system

**Contents:**
- Boolean vs opacity conversion (detailed)
- How opacity blocks or allows light propagation
- RGBI (Red-Green-Blue-Intensity) storage format
- BFS light propagation algorithm
- Complete data flow from block placement to rendering
- Common code patterns
- Known issues and bugs

### 3. LIGHTING-BUGS.md
**Purpose**: Known issues and bug tracking  
**Length**: ~23 pages  
**Best for**: Understanding what needs fixing

**Contents:**
- 5 critical bugs
- 5 moderate issues
- 5 minor edge cases
- 5 design concerns
- 5 performance issues
- Prioritized fix recommendations

## The Core Answer

The boolean `solid` parameter in `new Block(boolean solid)` is converted to a 0-15 opacity value by the `getOpacity()` method:

```java
// In Block.java
public int getOpacity() {
    return solid ? 15 : 0;
}
```

- **solid = true** → opacity = 15 (fully opaque, blocks all light)
- **solid = false** → opacity = 0 (fully transparent, lets light pass)

The lighting system uses the 0-15 value from `getOpacity()`, not the boolean directly.

## Where to Look in Code

| What | File | Line(s) | Description |
|------|------|---------|-------------|
| Opacity conversion | Block.java | 228-231 | `getOpacity()` method |
| Block light check | LightPropagator.java | 142 | Opacity >= 15 check |
| Skylight check | SkylightEngine.java | 153 | Opacity >= 15 check |
| Block registration | Blocks.java | 50-73 | Boolean constructor usage |
| RGBI storage | LightStorage.java | 254-257 | 2-byte packing format |

## Common Misconceptions

### ❌ Wrong
"You need to pass a 0-15 value to the Block constructor"

### ✅ Correct  
"You pass a boolean to the constructor, which gets converted to 0 or 15 by `getOpacity()`"

---

### ❌ Wrong
"The lighting system uses the boolean `solid` field directly"

### ✅ Correct
"The lighting system calls `getOpacity()` which returns 0 or 15 based on `solid`"

---

### ❌ Wrong  
"Opacity values between 1-14 are currently used"

### ✅ Correct
"Only 0 and 15 are currently used, but 1-14 are reserved for future semi-transparent blocks"

## Understanding the Lighting System Flow

```
Block Created with Boolean
         ↓
new Block(true)  →  solid = true
         ↓
Lighting System Queries
         ↓
block.getOpacity()  →  returns 15
         ↓
Light Propagation Check
         ↓
if (opacity >= 15) → Blocks light ✗
if (opacity < 15)  → Lets light pass ✓
```

## Future Extensibility

The 0-15 opacity system allows for future semi-transparent blocks:

```java
// Hypothetical future implementation
public class GlassBlock extends Block {
    public GlassBlock() {
        super(true); // Solid for collision
    }
    
    @Override
    public int getOpacity() {
        return 5; // Partial opacity for lighting
    }
}
```

This would:
- Have collision (solid = true)
- Partially block light (opacity = 5)
- Allow realistic glass, water, ice, etc.

**Note**: Partial light attenuation is not yet implemented; the system treats anything < 15 as fully transparent.

## Related Documentation

- **README.md** - Project overview
- **REFACTOR-PLAN.md** - Planned system improvements  
- **FAILED-TESTS.md** - Test status

## Questions?

For specific questions about:
- **Boolean vs 0-15** → Read LIGHTING-OPACITY-SUMMARY.md
- **How light propagates** → Read LIGHTING-SYSTEM-EXPLANATION.md sections 4-5
- **RGBI storage format** → Read LIGHTING-SYSTEM-EXPLANATION.md section 5
- **Known bugs** → Read LIGHTING-BUGS.md
- **Performance concerns** → Read LIGHTING-BUGS.md section 6

---

**Last Updated**: 2024-11-18  
**Documentation Complete**: ✓  
**Code Changes**: None (documentation only)

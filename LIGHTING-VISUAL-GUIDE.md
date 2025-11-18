# Visual Guide: Boolean to Opacity Conversion

This visual guide illustrates how the boolean `solid` parameter becomes a 0-15 opacity value in the lighting system.

## The Conversion Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                        BLOCK CREATION                                │
└─────────────────────────────────────────────────────────────────────┘

  Developer Code                    Block Object Created
  ──────────────                    ───────────────────
  
  new Block(true)         ──────►   Block {
                                       solid: true
                                       identifier: "mattmc:stone"
                                       lightEmission: 0
                                       ...
                                    }
  
  new Block(false)        ──────►   Block {
                                       solid: false
                                       identifier: "mattmc:air"
                                       lightEmission: 0
                                       ...
                                    }


┌─────────────────────────────────────────────────────────────────────┐
│                    LIGHTING SYSTEM QUERY                             │
└─────────────────────────────────────────────────────────────────────┘

  Lighting Code                     Method Call                  Return Value
  ─────────────                     ───────────                  ────────────
  
  block.getOpacity()      ──────►   getOpacity() {      ──────►      15
                                       return solid ? 15 : 0;
                                    }
                                    
                                    solid = true  ───►  15
                                    solid = false ───►   0


┌─────────────────────────────────────────────────────────────────────┐
│                    LIGHT PROPAGATION CHECK                           │
└─────────────────────────────────────────────────────────────────────┘

  Check                             Condition                    Result
  ─────                             ─────────                    ──────
  
  if (block.getOpacity() >= 15)     15 >= 15?  ─────►  TRUE  ───►  ✗ BLOCK LIGHT
                                     0 >= 15?  ─────►  FALSE ───►  ✓ LET LIGHT PASS
```

## Example: Light Propagating From a Torch

```
World State:
┌───────┬───────┬───────┬───────┬───────┐
│  AIR  │  AIR  │ TORCH │  AIR  │ STONE │
│       │       │       │       │       │
│ (5,3) │ (6,3) │ (7,3) │ (8,3) │ (9,3) │
└───────┴───────┴───────┴───────┴───────┘

Step 1: Query Block Properties
────────────────────────────────

Position (5,3) - AIR:
  Constructor: new Block(false)
  solid = false
  getOpacity() = 0
  
Position (7,3) - TORCH:
  Constructor: new Block(false, 14)
  solid = false
  getOpacity() = 0
  lightEmission = 14
  
Position (9,3) - STONE:
  Constructor: new Block(true)
  solid = true
  getOpacity() = 15


Step 2: Light Propagation from TORCH
─────────────────────────────────────

  TORCH at (7,3)
  ──────────────
  Light: RGB(14,14,11), Intensity: 14
  
  Propagate LEFT to (6,3):
    block = AIR
    block.getOpacity() = 0
    if (0 >= 15) → FALSE
    ✓ Light propagates: RGB(14,14,11), Intensity: 13
    
  Propagate LEFT to (5,3):
    block = AIR
    block.getOpacity() = 0
    if (0 >= 15) → FALSE
    ✓ Light propagates: RGB(14,14,11), Intensity: 12
    
  Propagate RIGHT to (8,3):
    block = AIR
    block.getOpacity() = 0
    if (0 >= 15) → FALSE
    ✓ Light propagates: RGB(14,14,11), Intensity: 13
    
  Propagate RIGHT to (9,3):
    block = STONE
    block.getOpacity() = 15
    if (15 >= 15) → TRUE
    ✗ Light BLOCKED - propagation stops


Step 3: Final Light Values
───────────────────────────

┌───────────┬───────────┬───────────┬───────────┬───────────┐
│  (5,3)    │  (6,3)    │  (7,3)    │  (8,3)    │  (9,3)    │
│   AIR     │   AIR     │  TORCH    │   AIR     │  STONE    │
├───────────┼───────────┼───────────┼───────────┼───────────┤
│ Opacity:0 │ Opacity:0 │ Opacity:0 │ Opacity:0 │ Opacity:15│
├───────────┼───────────┼───────────┼───────────┼───────────┤
│ Light: 12 │ Light: 13 │ Light: 14 │ Light: 13 │ Light: 0  │
│ RGB(14,   │ RGB(14,   │ RGB(14,   │ RGB(14,   │ RGB(0,    │
│  14,11)   │  14,11)   │  14,11)   │  14,11)   │  0,0)     │
└───────────┴───────────┴───────────┴───────────┴───────────┘
           ◄──────────  14  ────────┼───────►
                     13            13
                  12                  BLOCKED
```

## Boolean vs Opacity in Different Contexts

```
╔════════════════════════════════════════════════════════════════════╗
║                        CONTEXT: PHYSICS                             ║
╚════════════════════════════════════════════════════════════════════╝

  Question: "Does the player collide with this block?"
  
  Code: if (block.isSolid()) { /* collision */ }
  
  Uses: Boolean `solid` field directly
  
  STONE: solid = true  → ✓ Player collides
  AIR:   solid = false → ✗ Player passes through


╔════════════════════════════════════════════════════════════════════╗
║                        CONTEXT: LIGHTING                            ║
╚════════════════════════════════════════════════════════════════════╝

  Question: "Does light propagate through this block?"
  
  Code: if (block.getOpacity() >= 15) { /* block light */ }
  
  Uses: Numeric 0-15 value from getOpacity()
  
  STONE: solid = true  → getOpacity() = 15 → ✗ Light blocked
  AIR:   solid = false → getOpacity() = 0  → ✓ Light passes


╔════════════════════════════════════════════════════════════════════╗
║                    CONTEXT: FUTURE GLASS BLOCK                      ║
╚════════════════════════════════════════════════════════════════════╝

  Question: "Partially block light but allow collision?"
  
  Code:
    class GlassBlock extends Block {
      GlassBlock() { super(true); }  // Solid for collision
      
      @Override
      int getOpacity() { return 5; }  // Partial opacity
    }
  
  Physics: solid = true  → ✓ Player collides
  Lighting: opacity = 5  → Light partially passes (future feature)
```

## The Key Insight

```
┌──────────────────────────────────────────────────────────────────┐
│                                                                   │
│  The boolean is NOT used directly by the lighting system!        │
│                                                                   │
│  Instead, it's converted to 0 or 15 by getOpacity()             │
│                                                                   │
│  This allows:                                                     │
│  • Simple boolean for most game logic                            │
│  • Granular 0-15 control for lighting                           │
│  • Future support for semi-transparent blocks                    │
│                                                                   │
└──────────────────────────────────────────────────────────────────┘
```

## Code Reference Table

| Location | Code | What It Does |
|----------|------|--------------|
| **Block.java:38** | `new Block(boolean solid)` | Constructor takes boolean |
| **Block.java:228-231** | `return solid ? 15 : 0;` | **Conversion happens here** |
| **LightPropagator.java:142** | `if (block.getOpacity() >= 15)` | Uses 0-15 value for block light |
| **SkylightEngine.java:153** | `if (block.getOpacity() >= 15)` | Uses 0-15 value for skylight |
| **Blocks.java:53** | `new Block(true)` | STONE created with boolean |
| **Blocks.java:50** | `new Block(false)` | AIR created with boolean |

## Summary

```
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│  new Block(true)  →  solid = true  →  getOpacity() = 15        │
│                                                                  │
│                       ↓                        ↓                 │
│                                                                  │
│                 Used for Physics       Used for Lighting       │
│                 (collision, etc.)      (light propagation)      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

**The lighting system ALWAYS calls `getOpacity()`, which converts the boolean to 0 or 15.**

This is why you see 0-15 values in the lighting code even though the constructor takes a boolean!

---

For more details, see:
- **LIGHTING-OPACITY-SUMMARY.md** - Quick explanation
- **LIGHTING-SYSTEM-EXPLANATION.md** - Complete technical guide
- **docs/LIGHTING-DOCUMENTATION-INDEX.md** - Navigation guide

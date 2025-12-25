================================================================================
  DATAFIX IMPLEMENTATION: quantize tuff blocks → minecraft tuff blocks
================================================================================

WHAT WAS IMPLEMENTED:
---------------------
A DataFixerUpper fix that automatically remaps quantize tuff blocks and items
to their minecraft equivalents when loading old save files.

BLOCKS AND ITEMS REMAPPED:
---------------------------
1. quantize:tuff_bricks → minecraft:tuff_bricks
2. quantize:chiseled_tuff_bricks → minecraft:chiseled_tuff_bricks
3. quantize:chiseled_tuff → minecraft:chiseled_tuff
4. quantize:polished_tuff → minecraft:polished_tuff
5. quantize:polished_tuff_slab → minecraft:polished_tuff_slab
6. quantize:polished_tuff_stairs → minecraft:polished_tuff_stairs
7. quantize:tuff_brick_slab → minecraft:tuff_brick_slab
8. quantize:tuff_brick_stairs → minecraft:tuff_brick_stairs

IMPORTANT: All block properties (facing direction, rotation, waterlogged state,
etc.) are automatically preserved during the conversion!

FILES CHANGED:
--------------
1. src/main/java/net/minecraft/util/datafix/schemas/V4549.java (NEW)
   - New schema class for version 4549

2. src/main/java/net/minecraft/util/datafix/DataFixers.java (MODIFIED)
   - Added import for V4549
   - Added 3 lines to register BlockRenameFix and ItemRenameFix

DOCUMENTATION:
--------------
1. docs/DATAFIX_TUFF_BRICKS.md
   - Technical details and testing instructions

2. docs/DATAFIX_IMPLEMENTATION_SUMMARY.md
   - Complete implementation guide with examples

HOW TO USE:
-----------
Simply load any world that contains quantize tuff blocks or items.
The datafix will automatically run if the world's DataVersion is less than 4549.

The conversion happens transparently - no user action required!
All 8 block types and their corresponding items are converted automatically.

TECHNICAL DETAILS:
------------------
Schema Version: 4549
Parent Schema: 4548 (SAME_NAMESPACED)

Fixes Applied:
- BlockRenameFix: Converts block states and block names
- ItemRenameFix: Converts item references in inventories, chests, etc.

BUILD STATUS:
-------------
✅ Compilation successful - no errors

HOW IT WORKS:
-------------
1. Player loads a world with DataVersion < 4549
2. DataFixerUpper checks all block and item references
3. Any "quantize:tuff_bricks" is replaced with "minecraft:tuff_bricks"
4. World DataVersion is updated to 4549+

EXAMPLE:
--------
Before: {Name: "quantize:polished_tuff_stairs", Properties: {facing: "north", half: "top"}}
After:  {Name: "minecraft:polished_tuff_stairs", Properties: {facing: "north", half: "top"}}

Notice how the properties (facing, half) are preserved!

For more details, see the documentation in the docs/ directory.
================================================================================

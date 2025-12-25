================================================================================
  DATAFIX IMPLEMENTATION: quantize:tuff_bricks → minecraft:tuff_bricks
================================================================================

WHAT WAS IMPLEMENTED:
---------------------
A DataFixerUpper fix that automatically remaps "quantize:tuff_bricks" blocks 
and items to "minecraft:tuff_bricks" when loading old save files.

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
Simply load any world that contains "quantize:tuff_bricks" blocks or items.
The datafix will automatically run if the world's DataVersion is less than 4549.

The conversion happens transparently - no user action required!

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
Before: {Name: "quantize:tuff_bricks", Properties: {}}
After:  {Name: "minecraft:tuff_bricks", Properties: {}}

For more details, see the documentation in the docs/ directory.
================================================================================

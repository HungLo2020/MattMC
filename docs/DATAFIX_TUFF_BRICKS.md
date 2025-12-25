# DataFix for quantize tuff blocks → minecraft tuff blocks

## Overview

This datafix automatically remaps blocks and items from the `quantize` namespace to `minecraft` namespace when loading old save files. This is useful when migrating worlds from a custom mod that added tuff bricks to the vanilla Minecraft implementation.

## Blocks and Items Remapped

The following blocks and items are automatically converted:

1. `quantize:tuff_bricks` → `minecraft:tuff_bricks`
2. `quantize:chiseled_tuff_bricks` → `minecraft:chiseled_tuff_bricks`
3. `quantize:chiseled_tuff` → `minecraft:chiseled_tuff`
4. `quantize:polished_tuff` → `minecraft:polished_tuff`
5. `quantize:polished_tuff_slab` → `minecraft:polished_tuff_slab`
6. `quantize:polished_tuff_stairs` → `minecraft:polished_tuff_stairs`
7. `quantize:tuff_brick_slab` → `minecraft:tuff_brick_slab`
8. `quantize:tuff_brick_stairs` → `minecraft:tuff_brick_stairs`

**Important:** All block properties (such as facing direction, rotation, waterlogged state, etc.) are automatically preserved during the conversion.

## Technical Details

### Schema Version
- **Version:** 4549
- **Parent Schema:** 4548 (SAME_NAMESPACED)

### Fixes Applied
1. **BlockRenameFix** - Remaps block references in:
   - Block states
   - Block names  
   - Flat block state strings
   
2. **ItemRenameFix** - Remaps item references in:
   - Player inventories
   - Chest contents
   - Item entities
   - Any other item storage

### Implementation Files
- `src/main/java/net/minecraft/util/datafix/schemas/V4549.java` - Schema definition
- `src/main/java/net/minecraft/util/datafix/DataFixers.java` - Datafix registration (lines 1530-1532)

## How to Test

### Method 1: Using NBT Tools

1. Create a test world with `quantize:tuff_bricks` blocks in older game version
2. Save the world and use an NBT editor to inspect the region files
3. Load the world in this version of MattMC
4. Verify the blocks are now `minecraft:tuff_bricks`

### Method 2: In-Game Testing

1. If you have a world with quantize tuff blocks:
   - Place various tuff blocks in the world (stairs, slabs, bricks, etc.)
   - Rotate stairs to different directions
   - Place slabs in top/bottom positions
   - Place items in chests/inventory
2. Load the world with MattMC
3. Break the blocks and check they drop the correct `minecraft:` variants
4. Verify block orientations are preserved (stairs facing, slab positions, etc.)
5. Check inventory items are now `minecraft:` variants

## How DataFixerUpper Works

When Minecraft loads a save file:

1. It checks the `DataVersion` tag in `level.dat`
2. If the save version is < 4549, it needs to apply this fix
3. The DataFixerUpper system processes all block and item references
4. Any occurrence of `quantize:tuff_bricks` is replaced with `minecraft:tuff_bricks`
5. The save is updated to version 4549+

## Adding More Block/Item Remaps

To add additional remaps, follow this pattern in `DataFixers.java`:

```java
// For a single block/item rename
dataFixerBuilder.addFixer(BlockRenameFix.create(
    schema, 
    "Description of fix", 
    createRenamer("old:block_name", "new:block_name")
));

// For multiple renames
dataFixerBuilder.addFixer(BlockRenameFix.create(
    schema,
    "Description of fix",
    createRenamer(ImmutableMap.of(
        "old:name1", "new:name1",
        "old:name2", "new:name2"
    ))
));
```

## References

- [Minecraft DataFixerUpper Documentation](https://minecraft.fandom.com/wiki/Data_version)
- [BlockRenameFix.java](../src/main/java/net/minecraft/util/datafix/fixes/BlockRenameFix.java)
- [ItemRenameFix.java](../src/main/java/net/minecraft/util/datafix/fixes/ItemRenameFix.java)

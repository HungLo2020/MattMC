# DataFix for quantize:tuff_bricks → minecraft:tuff_bricks

## Overview

This datafix automatically remaps blocks and items from `quantize:tuff_bricks` to `minecraft:tuff_bricks` when loading old save files. This is useful when migrating worlds from a custom mod that added tuff bricks to the vanilla Minecraft implementation.

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

1. If you have a world with `quantize:tuff_bricks`:
   - Place some tuff bricks blocks in the world
   - Place some tuff bricks items in chests/inventory
2. Load the world with MattMC
3. Break the blocks and check they drop `minecraft:tuff_bricks`
4. Check inventory items are now `minecraft:tuff_bricks`

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

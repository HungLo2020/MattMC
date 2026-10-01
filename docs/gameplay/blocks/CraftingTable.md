# Crafting Table

A Crafting Table opens a **3 × 3 crafting grid**, allowing recipes too large for the player's inventory grid. Its block and item ID is `minecraft:crafting_table`.

## Making and collecting one

Place four items from the planks tag in a **2 × 2 square** to craft one table. The recipe accepts the tag, not every block whose name contains “planks.” In particular, the checked [Pewen Planks](../items/PewenPlanks.md) integration lacks that generic tag membership.

The table drops itself under ordinary harvesting conditions, and its registration does not require a special tool for drops. An axe belongs to its mining-tool tag. Explosion survival still applies to its loot table.

## Using the grid

Place the table and interact with it to open the crafting menu. Arrange ingredients for a loaded recipe, then take the output. Shaped recipes depend on the pattern; shapeless recipes depend on the ingredients rather than their positions.

The table's grid is **not permanent storage**. Closing the menu clears its crafting slots through the container-clearing routine. Put supplies in a [Chest](Chest.md) instead of leaving them in the grid.

## Useful first recipes

- [Furnace](Furnace.md): eight stone-crafting materials around an empty center
- [Chest](Chest.md): eight planks around an empty center
- [Stone building recipes](Stone.md#uses)
- [Pewen building recipes and integration limits](Pewen.md#construction-and-recipes)

For recipes with empty cells, keep those cells empty. If no output appears, check the exact item/tag requirements and recipe loading rather than assuming an upstream recipe is present.

## Related pages

- [Crafting guide](../crafting/Crafting.md)
- [Crafting Table item](../items/CraftingTable.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not a gameplay test; data packs and later builds can change recipes and tags.

- [Table recipe](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/recipe/crafting/crafting_table.json)
- [Planks tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/item/planks.json)
- [Opening the menu](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/CraftingTableBlock.java)
- [Grid size, recipe lookup and cleanup](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/CraftingMenu.java)
- [Registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L1265-L1268)
- [Loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/crafting_table.json)
- [Axe tag](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/tags/block/mineable/axe.json)

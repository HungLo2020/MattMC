# Crafting Table

A Crafting Table opens a **3 × 3 crafting grid**, allowing recipes too large for the player's inventory grid. Its block and item ID is `minecraft:crafting_table`.

## Making and collecting one

Place four items from the planks tag in a **2 × 2 square** to craft one table. The recipe accepts the tag, not every block whose name contains “planks.” In particular, the checked [Pewen Planks](../items/PewenPlanks.md) integration lacks that generic tag membership.

The table drops itself under ordinary harvesting conditions, and its registration does not require a special tool for drops. An axe belongs to its mining-tool tag. Explosion survival still applies to its loot table.

## Using the grid

Place the table and interact with it to open the crafting menu. Arrange ingredients for a loaded recipe, then take the output. Shaped recipes depend on the pattern; shapeless recipes depend on the ingredients rather than their positions.

The table's grid is **not permanent storage**. Closing the menu clears its crafting slots through the container-clearing routine. Put supplies in a [Chest](Chest.md) instead of leaving them in the grid.

## Sharing a table and keeping ingredients safe

Two players can use the same placed table, but **each open menu has its own
crafting grid**. Ingredients placed in your grid do not become a shared inventory
inside the block, and another player's menu does not take over your arrangement.
[Menu creation][current-open] · [Per-menu containers][current-grid]

A [Hopper](Hopper.md) cannot load or extract this menu's ingredients: the table
is not a block inventory. Powering it does not craft a recipe. For redstone-driven
crafting with persistent slots, use a [Crafter](Crafter.md). Dropping ingredients
on top of a table does not place them in your open grid. [Block behavior][current-open]
· [Hopper container lookup][current-hopper]

Closing the screen returns leftover ingredients through the shared
[crafting cleanup rules](../crafting/Crafting.md#reading-recipes); inventory overflow
drops into the world. Keep room before closing, especially near hazards. The
server also checks that the table still exists and you remain within its allowed
interaction reach; the menu is not remote storage. Recipe-returned containers
such as Cake's Buckets follow the separate
[remainder rules](../crafting/Crafting.md#returned-containers-and-other-remainders)
when you take the result. [Table cleanup][current-menu] · [Block and reach check][current-valid]

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

The separate-menu, automation and ingredient-return guidance was additionally
source-reviewed on **2026-10-14** at `cdb9dc2b9c37d3f9b3fbdf7f09c06d0b5def3b31`. No multiplayer,
Hopper, crafting or dropped-item gameplay test was run.

[current-open]: https://github.com/HungLo2020/MattMC/blob/cdb9dc2b9c37d3f9b3fbdf7f09c06d0b5def3b31/src/main/java/net/minecraft/world/level/block/CraftingTableBlock.java
[current-grid]: https://github.com/HungLo2020/MattMC/blob/cdb9dc2b9c37d3f9b3fbdf7f09c06d0b5def3b31/src/main/java/net/minecraft/world/inventory/AbstractCraftingMenu.java
[current-menu]: https://github.com/HungLo2020/MattMC/blob/cdb9dc2b9c37d3f9b3fbdf7f09c06d0b5def3b31/src/main/java/net/minecraft/world/inventory/CraftingMenu.java
[current-hopper]: https://github.com/HungLo2020/MattMC/blob/cdb9dc2b9c37d3f9b3fbdf7f09c06d0b5def3b31/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L375-L387

[current-valid]: https://github.com/HungLo2020/MattMC/blob/cdb9dc2b9c37d3f9b3fbdf7f09c06d0b5def3b31/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L96-L98

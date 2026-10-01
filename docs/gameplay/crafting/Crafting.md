# Crafting

Crafting combines ingredients into items and blocks. MattMC's current recipe data, not an upstream wiki recipe alone, determines which combinations work.

## Getting started

Craft a [Crafting Table](../blocks/CraftingTable.md) from four planks in a 2 × 2 square. Its placed menu supplies a 3 × 3 grid for larger recipes. Useful next crafts are a [Furnace](../blocks/Furnace.md) for smelting and a [Chest](../blocks/Chest.md) for supplies.

## Reading recipes

- **Shaped:** arrange ingredients in the required pattern and leave the pattern's empty cells clear.
- **Shapeless:** provide the required ingredients; the specific grid positions do not define the recipe.
- **Tags:** a recipe may accept a group of items rather than one exact item. Membership matters, even when another item's name looks equivalent.
- **Output count:** distinguish the number of ingredients from the number of result items. Three Stone or Pewen Planks in an appropriate slab recipe does not mean three slabs by default; consult the actual recipe.

The server matches the grid against loaded crafting recipes and only provides an enabled output. Closing the crafting table clears the working grid; use a chest for storage.

## MattMC-specific checks

Imported blocks may be present in Creative before every recipe or tag is correctly integrated. The [Pewen guide](../blocks/Pewen.md#recipes-and-tools-that-need-caution) records concrete ingredient/tag problems. Do not replace an unknown recipe with an upstream guess.

If nothing appears, verify the device, grid pattern, exact ingredients or tag membership, enabled content, and the current build's recipe data. Cooking, smithing, stonecutting, and brewing are not all ordinary crafting-table recipes.

## Verified recipe guides

- [Crafting-grid repair](../mechanics/Durability.md#crafting-grid-repair-details): combines matching items but does not preserve the same names, trims, or enchantments as an Anvil

- [Crafting Table](../blocks/CraftingTable.md)
- [Furnace](../blocks/Furnace.md)
- [Chest](../blocks/Chest.md)
- [Stone recipes](../blocks/Stone.md#uses)
- [Pewen recipes and limitations](../blocks/Pewen.md#construction-and-recipes)
- [Honeycomb crafting](../items/Honeycomb.md#waxing-and-crafting)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not a gameplay test; data packs and later builds can change recipes and tags.

- [Crafting menu and recipe lookup](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/inventory/CraftingMenu.java)
- [Shaped recipe definition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/crafting/ShapedRecipe.java)
- [Shapeless recipe definition](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java)
- [Ingredient/tag handling](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/crafting/Ingredient.java)

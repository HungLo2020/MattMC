# Crafting

Crafting combines ingredients into items and blocks. MattMC's current recipe data, not an upstream wiki recipe alone, determines which combinations work.

## Getting started

Craft a [Crafting Table](../blocks/CraftingTable.md) from four planks in a 2 × 2 square. Its placed menu supplies a 3 × 3 grid for larger recipes. Useful next crafts are a [Furnace](../blocks/Furnace.md) for smelting and a [Chest](../blocks/Chest.md) for supplies.

## Reading recipes

Use the [Recipe Viewer](../mechanics/RecipeViewer.md#opening-a-recipe) to inspect producing recipes from a hovered item; its displays have [coverage and cache limits](../mechanics/RecipeViewer.md#when-a-recipe-is-missing).

- **Shaped:** keep the relative positions and internal empty cells of the pattern. A smaller pattern can move anywhere it fits in the grid, and its left-to-right mirror also works. Rotation is not a general recipe rule. [Positioning][craft-position] · [Pattern matching][craft-pattern]
- **Shapeless:** provide the required ingredients; the specific grid positions do not define the recipe. Repeated ingredients need separate occupied slots: six Beetroots for [Beetroot Soup](../items/BeetrootSoup.md#obtaining) use six slots, not one stack of six. Recipes needing more than four occupied slots require the table's 3 × 3 grid. [Input counting][craft-position] · [Shapeless matching][craft-shapeless] · [Personal grid][craft-inventory]
- **Tags:** a recipe may accept a group of items rather than one exact item. Membership matters, even when another item's name looks equivalent.
- **Output count:** distinguish the number of ingredients from the number of result items. Three Stone or Pewen Planks in an appropriate slab recipe does not mean three slabs by default; consult the actual recipe.

The server matches the grid against loaded crafting recipes and only provides an enabled output. For bulk crafting and moving ingredients, follow [Inventory controls](../mechanics/InventoryControls.md#slots-and-items-with-special-rules); Shift-click can repeat crafts as matching results replenish.

Closing either the personal crafting screen or the Crafting Table returns remaining grid ingredients to your inventory. **Anything that does not fit drops into the world**, so leave room and collect overflow. The grid is not permanent storage; use a chest for supplies. [Personal cleanup][craft-inventory] · [Table cleanup][craft-table-close] · [Return handling][craft-return] · [Overflow drops][craft-overflow]

## Returned containers and other remainders

Some recipes return an item from an ingredient slot as well as producing their
main output. For example, [Cake crafting](../blocks/Cake.md#crafting-and-carrying)
returns the Milk Buckets as empty Buckets. The recipe decides whether a remainder
exists; do not assume every bottle, tool or ingredient is returned.
[Recipe remainder lookup][current-remainder]

When you take the crafted result, the menu consumes one ingredient from each
occupied recipe slot and handles that slot's remainder. If the slot is empty,
the remainder goes there. If the remaining stack is the same item **with the
same components**, the stacks combine there. Otherwise it goes into your
inventory. In Survival, anything that cannot be inserted drops into the world;
Creative's infinite-materials insertion path can discard that uninsertable
remainder instead. Leave space when you want to keep it.
[Consumption and remainder handling][current-remainder] · [Inventory insertion][current-insertion]

A Bucket appearing in the grid is therefore a returned ingredient container,
not a second craftable output. Clear or refill the recipe pattern before the
next craft, and leave inventory room when crafting recipes with remainders.
Closing the screen then uses the ordinary grid cleanup described above. Automated
[Crafter output and remainders](../blocks/Crafter.md#outputs-remainders-and-failures)
have their own handling; these player-menu rules are not a promise about every
processing machine.

## MattMC-specific checks

Imported blocks may be present in Creative before every recipe or tag is correctly integrated. The [Pewen guide](../blocks/Pewen.md#recipes-and-tools-that-need-caution) records concrete ingredient/tag problems. Do not replace an unknown recipe with an upstream guess.

If nothing appears, verify the device, grid pattern, exact ingredients or tag membership, enabled content, and the current build's recipe data. Cooking, smithing, stonecutting, and brewing are not all ordinary crafting-table recipes.

## Verified recipe guides

Copying, recoloring and other special recipes can depend on an item's existing data. Use these guides for their exact inputs, returned items and preservation rules, including recipes absent from the [Recipe Viewer](../mechanics/RecipeViewer.md#when-a-recipe-is-missing):

- [Equipment dyeing](../items/Dyes.md#equipment-colors-and-washing), including color blending and washing
- [Banner duplication](../blocks/Banners.md#patterns-and-duplication) and [Shield decoration](../blocks/Banners.md#washing-and-shield-decoration)
- [Written Book copies and generations](../items/WrittenBook.md#copying-and-generations) and [Map copying/enlargement](../items/Map.md#copies-enlargement-and-locking)
- [Firework Rockets, Stars and fade colors](../mechanics/Fireworks.md)
- [Tipped Arrows](../items/TippedArrow.md#obtaining) and [Decorated Pots](../blocks/DecoratedPot.md)
- [Bundle recoloring](../items/Bundle.md#colors-and-recoloring) and [Shulker Box recoloring](../blocks/ShulkerBox.md#obtaining-and-colors)
- [Cake's returned Buckets](../blocks/Cake.md#crafting-and-carrying) and [Crafter remainder output](../blocks/Crafter.md#outputs-remainders-and-failures)

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

Shared grid positioning, ingredient-slot counting and close-screen returns were additionally source-reviewed on **2026-10-05** at `5218ac875eda9f2c4151ff1a97795f20f5f356cf`. No in-game crafting, inventory or overflow test was run. The linked recipe owners retain their own verification scope.

[craft-position]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/crafting/CraftingInput.java#L16-L81
[craft-pattern]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[craft-shapeless]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L64
[craft-inventory]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L93
[craft-table-close]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L100-L103
[craft-return]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/inventory/AbstractContainerMenu.java#L589-L613
[craft-overflow]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/player/Inventory.java#L302-L323

Player-menu remainder placement was source-reviewed on **2026-10-14** at
`cdb9dc2b9c37d3f9b3fbdf7f09c06d0b5def3b31`; no crafting, overflow or multiplayer
runtime test was run.

[current-remainder]: https://github.com/HungLo2020/MattMC/blob/cdb9dc2b9c37d3f9b3fbdf7f09c06d0b5def3b31/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L71-L115

[current-insertion]: https://github.com/HungLo2020/MattMC/blob/cdb9dc2b9c37d3f9b3fbdf7f09c06d0b5def3b31/src/main/java/net/minecraft/world/entity/player/Inventory.java#L249-L289

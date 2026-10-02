# Yellow Dye

**Yellow Dye** (`minecraft:yellow_dye`). Dandelions and Wildflowers each make one Yellow Dye; a Sunflower item makes two. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Dandelion](Dandelion.md) | **1** | Shapeless crafting · [Recipe 1] |
| **1** [Sunflower](Sunflower.md) | **2** | Shapeless crafting · [Recipe 2] |
| **1** [Wildflowers](Wildflowers.md) | **1** | Shapeless crafting · [Recipe 3] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

[Flowers](../blocks/Flowers.md) covers Dandelions and Sunflowers; [flowerbeds](../blocks/FlowerbedsAndLeafLitter.md#wildflowers) covers Wildflowers. A planted Sunflower can supply more flower items through the Flowers guide's tall-flower route.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), available in Survival and Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

One Yellow Dye plus one Red Dye makes **two [Orange Dye](OrangeDye.md)**. For a yellow build, see [Yellow Wool](YellowWool.md) and [Yellow Bundle](YellowBundle.md). [Further mixing]

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

Sunflower gives two Yellow Dye from **one flower item**. The Dandelion and Wildflowers recipes each give one; a planted Wildflowers patch's coverage does not change the item recipe.

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/yellow_dye_from_dandelion.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/yellow_dye_from_sunflower.json
[Recipe 3]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/yellow_dye_from_wildflowers.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870
[Further mixing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/orange_dye_from_red_yellow.json

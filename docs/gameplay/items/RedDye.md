# Red Dye

**Red Dye** (`minecraft:red_dye`). Poppies, Red Tulips, Beetroots and Rose Bushes produce Red Dye. A Rose Bush item gives two; each of the other ingredients gives one. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Beetroot](Beetroot.md) | **1** | Shapeless crafting · [Recipe 1] |
| **1** [Poppy](Poppy.md) | **1** | Shapeless crafting · [Recipe 2] |
| **1** [Rose Bush](RoseBush.md) | **2** | Shapeless crafting · [Recipe 3] |
| **1** [Red Tulip](RedTulip.md) | **1** | Shapeless crafting · [Recipe 4] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

See [Flowers](../blocks/Flowers.md) for the three flower ingredients and [root crops](../blocks/RootCrops.md) for Beetroot production.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), visible in Survival and Creative; insertion requires Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

Red Dye is a mixing ingredient for [Orange](OrangeDye.md), [Pink](PinkDye.md), [Purple](PurpleDye.md), and [Magenta](MagentaDye.md). Magenta's four-item recipe needs two separate Red Dye ingredients.

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

The Beetroot recipe uses **Beetroot** (`minecraft:beetroot`), not Beetroot Seeds. Its one-dye output differs from the two-dye Rose Bush route.

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_beetroot.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_poppy.json
[Recipe 3]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_rose_bush.json
[Recipe 4]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_tulip.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870

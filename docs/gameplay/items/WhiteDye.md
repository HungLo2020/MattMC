# White Dye

**White Dye** (`minecraft:white_dye`). Bone Meal and Lily of the Valley each make one White Dye. Keep a supply if you also want to mix the lighter named dye colors. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Bone Meal](BoneMeal.md) | **1** | Shapeless crafting · [Recipe 1] |
| **1** [Lily of the Valley](LilyOfTheValley.md) | **1** | Shapeless crafting · [Recipe 2] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

Use [Bone Meal](BoneMeal.md) for that ingredient, or [Flowers](../blocks/Flowers.md#lily-of-the-valley) for Lily of the Valley.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), visible in Survival and Creative; insertion requires Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

White Dye is a mixing ingredient for [Gray](GrayDye.md), [Light Gray](LightGrayDye.md), [Light Blue](LightBlueDye.md), [Lime](LimeDye.md), [Pink](PinkDye.md), and one [Magenta](MagentaDye.md) recipe. Use the destination page to choose the exact amounts.

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

White Tulip produces **Light Gray Dye**, not White Dye. The two direct White Dye recipes are the Bone Meal and Lily of the Valley rows above. [Other flower result]

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/white_dye.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/white_dye_from_lily_of_the_valley.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870
[Other flower result]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_white_tulip.json

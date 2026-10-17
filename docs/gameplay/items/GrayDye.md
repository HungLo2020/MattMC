# Gray Dye

**Gray Dye** (`minecraft:gray_dye`). A Closed Eyeblossom supplies Gray Dye directly. Black Dye mixed with White Dye provides a second route. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Black Dye](BlackDye.md) + **1** [White Dye](WhiteDye.md) | **2** | Shapeless crafting · [Recipe 1] |
| **1** [Closed Eyeblossom](ClosedEyeblossom.md) | **1** | Shapeless crafting · [Recipe 2] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

The [Eyeblossom guide](../blocks/Eyeblossoms.md) explains open and closed flowers. The ingredient here is the **closed** item.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), visible in Survival and Creative; insertion requires Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

Combine one Gray Dye with one White Dye for **two [Light Gray Dye](LightGrayDye.md)**. For an unchanged gray palette, see [Gray Wool](GrayWool.md) and [Gray Bundle](GrayBundle.md). [Further mixing]

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

Gray and Light Gray are separate dye colors. For the lighter recipe, combine one Gray Dye with one White Dye; adding White Dye is an item conversion, not a persistent tint on the Gray Dye stack.

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/gray_dye.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/gray_dye_from_closed_eyeblossom.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870
[Further mixing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_gray_white_dye.json

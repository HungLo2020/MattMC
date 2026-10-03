# Orange Dye

**Orange Dye** (`minecraft:orange_dye`). Orange Tulips, Torchflowers and Open Eyeblossoms make Orange Dye directly. Red and Yellow Dye provide a two-item mixing route. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Open Eyeblossom](OpenEyeblossom.md) | **1** | Shapeless crafting · [Recipe 1] |
| **1** [Orange Tulip](OrangeTulip.md) | **1** | Shapeless crafting · [Recipe 2] |
| **1** [Red Dye](RedDye.md) + **1** [Yellow Dye](YellowDye.md) | **2** | Shapeless crafting · [Recipe 3] |
| **1** [Torchflower](Torchflower.md) | **1** | Shapeless crafting · [Recipe 4] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

The direct sources have separate growing rules: [Flowers](../blocks/Flowers.md#orange-tulip), [Torchflower](../blocks/Torchflower.md), and [Eyeblossoms](../blocks/Eyeblossoms.md).

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), visible in Survival and Creative; insertion requires Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

Use [Orange Wool](OrangeWool.md), [Orange Carpet](OrangeCarpet.md), or an [Orange Bundle](OrangeBundle.md). Flower-derived and mixed Orange Dye work as the same ingredient.

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

Use **Open Eyeblossom** for the Orange Dye recipe. Closed Eyeblossom instead has the [Gray Dye](GrayDye.md) recipe; the two flower items are not interchangeable. [Other flower result]

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/orange_dye_from_open_eyeblossom.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/orange_dye_from_orange_tulip.json
[Recipe 3]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/orange_dye_from_red_yellow.json
[Recipe 4]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/orange_dye_from_torchflower.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870
[Other flower result]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/gray_dye_from_closed_eyeblossom.json

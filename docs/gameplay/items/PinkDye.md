# Pink Dye

**Pink Dye** (`minecraft:pink_dye`). Pink Dye can come from four different plant items or a Red-and-White Dye mixture. Peony is the two-dye plant recipe. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Cactus Flower](CactusFlower.md) | **1** | Shapeless crafting · [Recipe 1] |
| **1** [Peony](Peony.md) | **2** | Shapeless crafting · [Recipe 2] |
| **1** [Pink Petals](PinkPetals.md) | **1** | Shapeless crafting · [Recipe 3] |
| **1** [Pink Tulip](PinkTulip.md) | **1** | Shapeless crafting · [Recipe 4] |
| **1** [Red Dye](RedDye.md) + **1** [White Dye](WhiteDye.md) | **2** | Shapeless crafting · [Recipe 5] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

Use [Flowers](../blocks/Flowers.md) for Peonies and Pink Tulips, [flowerbeds](../blocks/FlowerbedsAndLeafLitter.md#pink-petals) for Pink Petals, and [Cactus](../blocks/Cactus.md) for Cactus Flowers.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), available in Survival and Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

Pink Dye combines with Purple Dye, or with Blue and Red Dye, in the [Magenta Dye](MagentaDye.md) recipes. For pink materials, see [Pink Wool](PinkWool.md) and [Pink Bundle](PinkBundle.md).

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

The recipes consume the listed plant **items**. One Peony item gives two Pink Dye, while one Pink Petals item gives one; the recipe does not multiply its output by the coverage of a planted flowerbed.

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_cactus_flower.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_peony.json
[Recipe 3]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_pink_petals.json
[Recipe 4]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_pink_tulip.json
[Recipe 5]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/pink_dye_from_red_white_dye.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870

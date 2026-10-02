# Magenta Dye

**Magenta Dye** (`minecraft:magenta_dye`). Magenta Dye has two flower sources and three mixing recipes. Choose the batch that fits the colors you already have. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Allium](Allium.md) | **1** | Shapeless crafting · [Recipe 1] |
| **1** [Blue Dye](BlueDye.md) + **1** [Red Dye](RedDye.md) + **1** [Pink Dye](PinkDye.md) | **3** | Shapeless crafting · [Recipe 2] |
| **1** [Blue Dye](BlueDye.md) + **2** [Red Dye](RedDye.md) + **1** [White Dye](WhiteDye.md) | **4** | Shapeless crafting · [Recipe 3] |
| **1** [Lilac](Lilac.md) | **2** | Shapeless crafting · [Recipe 4] |
| **1** [Purple Dye](PurpleDye.md) + **1** [Pink Dye](PinkDye.md) | **2** | Shapeless crafting · [Recipe 5] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

A collected [Lilac](../blocks/Flowers.md#lilac) gives twice the dye of one [Allium](../blocks/Flowers.md#allium). The Flowers guide explains how a planted Lilac can supply more flower items.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), available in Survival and Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

Use [Magenta Wool](MagentaWool.md), [Magenta Carpet](MagentaCarpet.md), or a [Magenta Bundle](MagentaBundle.md) for the finished color. All five acquisition recipes above produce the same registered dye item.

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

For the four-ingredient mix, put the **two Red Dye in separate slots** beside Blue and White Dye. All four ingredients fit in the inventory's 2×2 grid; a stack of two Red Dye in one slot does not satisfy both recipe entries. [Occupied input slots] · [Shapeless matching]

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/magenta_dye_from_allium.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/magenta_dye_from_blue_red_pink.json
[Recipe 3]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/magenta_dye_from_blue_red_white_dye.json
[Recipe 4]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/magenta_dye_from_lilac.json
[Recipe 5]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/magenta_dye_from_purple_and_pink.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870
[Occupied input slots]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/CraftingInput.java#L16-L29

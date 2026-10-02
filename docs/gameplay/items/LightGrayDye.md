# Light Gray Dye

**Light Gray Dye** (`minecraft:light_gray_dye`). Azure Bluets, Oxeye Daisies and White Tulips all make Light Gray Dye. Two dye-mixing recipes are also available. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Azure Bluet](AzureBluet.md) | **1** | Shapeless crafting · [Recipe 1] |
| **1** [Black Dye](BlackDye.md) + **2** [White Dye](WhiteDye.md) | **3** | Shapeless crafting · [Recipe 2] |
| **1** [Gray Dye](GrayDye.md) + **1** [White Dye](WhiteDye.md) | **2** | Shapeless crafting · [Recipe 3] |
| **1** [Oxeye Daisy](OxeyeDaisy.md) | **1** | Shapeless crafting · [Recipe 4] |
| **1** [White Tulip](WhiteTulip.md) | **1** | Shapeless crafting · [Recipe 5] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

[Flowers](../blocks/Flowers.md#small-flower-variants) covers the three direct plant ingredients and their collection. All three output Light Gray, including **White Tulip**.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), available in Survival and Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

Use [Light Gray Wool](LightGrayWool.md), [Light Gray Carpet](LightGrayCarpet.md), or [Light Gray Bundle](LightGrayBundle.md). These use the Light Gray dye item, not ordinary Gray Dye.

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

The three-ingredient mix needs **two separate White Dye slots**, plus one Black Dye slot. One slot containing a stack of two White Dye is still only one occupied ingredient slot. [Occupied input slots] · [Shapeless matching]

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_azure_bluet.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_black_white_dye.json
[Recipe 3]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_gray_white_dye.json
[Recipe 4]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_oxeye_daisy.json
[Recipe 5]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/light_gray_dye_from_white_tulip.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870
[Occupied input slots]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/CraftingInput.java#L16-L29

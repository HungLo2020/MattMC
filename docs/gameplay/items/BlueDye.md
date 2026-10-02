# Blue Dye

**Blue Dye** (`minecraft:blue_dye`). Lapis Lazuli and Cornflowers each convert into Blue Dye one for one, giving a choice between a mineral ingredient and a flower supply. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Lapis Lazuli](LapisLazuli.md) | **1** | Shapeless crafting · [Recipe 1] |
| **1** [Cornflower](Cornflower.md) | **1** | Shapeless crafting · [Recipe 2] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

See [Lapis Lazuli](LapisLazuli.md) for the mineral resource and [Flowers](../blocks/Flowers.md#cornflower) for Cornflowers and propagation.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), available in Survival and Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

Blue Dye feeds the [Cyan](CyanDye.md), [Light Blue](LightBlueDye.md), [Purple](PurpleDye.md) and two [Magenta](MagentaDye.md) mixing recipes. Those pages give the full ingredient counts.

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

The Lapis Lazuli recipe consumes the mineral item to make a separate Blue Dye item. Raw Lapis and Cornflowers are not interchangeable with Blue Dye in the color-mixing recipes above.

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/blue_dye.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/blue_dye_from_cornflower.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870

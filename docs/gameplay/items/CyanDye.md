# Cyan Dye

**Cyan Dye** (`minecraft:cyan_dye`). A Pitcher Plant makes two Cyan Dye; mixing Blue Dye with Green Dye also makes two. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Blue Dye](BlueDye.md) + **1** [Green Dye](GreenDye.md) | **2** | Shapeless crafting · [Recipe 1] |
| **1** [Pitcher Plant](PitcherPlant.md) | **2** | Shapeless crafting · [Recipe 2] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

Use [Pitcher Plant](../blocks/PitcherPlant.md) for the crop-to-plant lifecycle, or the linked Blue and Green Dye pages for mixing ingredients.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), visible in Survival and Creative; insertion requires Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

Use [Cyan Wool](CyanWool.md), [Cyan Carpet](CyanCarpet.md), or a [Cyan Bundle](CyanBundle.md) for matching colored supplies. Their recipes apply Cyan as the destination color.

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

The plant recipe uses **Pitcher Plant** (`minecraft:pitcher_plant`), not a Pitcher Pod. The full-grown plant and the planting item have different IDs.

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/cyan_dye.json
[Recipe 2]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/cyan_dye_from_pitcher_plant.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870

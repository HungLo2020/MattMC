# Purple Dye

**Purple Dye** (`minecraft:purple_dye`). Combine Blue Dye with Red Dye to produce two Purple Dye. This is the checked dye-making recipe for this color. [Registration]

## Obtaining

| Ingredients for one operation | Dye output | Method and source |
| --- | --- | --- |
| **1** [Blue Dye](BlueDye.md) + **1** [Red Dye](RedDye.md) | **2** | Shapeless crafting · [Recipe 1] |

The crafting rows are shapeless: put one ingredient in each occupied slot, with no fixed arrangement, then take the result. [Shapeless matching] · [Crafting and consumption]

The linked [Blue Dye](BlueDye.md) and [Red Dye](RedDye.md) pages give their exact ingredient routes. Convert the source materials into those dyes before combining them.

This color is also listed in the ordinary [inventory item browser](../mechanics/InventoryBrowser.md), available in Survival and Creative. For the selected Wandering Trader offer and shared acquisition notes, see [Dyes](Dyes.md#browser-and-trading). [Ordinary dye entries]

## Usage

One Purple Dye plus one Pink Dye makes **two [Magenta Dye](MagentaDye.md)**. Keep Purple Dye unchanged for [Purple Wool](PurpleWool.md) or [Purple Bundle](PurpleBundle.md). [Further mixing]

For [Sheep fleece, pet collars, sign text and other applications](Dyes.md#choosing-how-to-apply-a-dye), use the shared Dyes guide. [Wool and Carpet](../blocks/WoolAndCarpet.md) explains material recoloring and [Signs](../blocks/Signs.md#dye-glow-ink-and-wax) explains text decoration.

## Behavior

Blue and Red are the two named dye inputs. The resulting Purple Dye can be mixed again with Pink Dye, but the first recipe does not accept raw flowers or Lapis in place of its named dyes.

## Notes

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked this color's ordinary recipe inputs/results and item registration; the linked family guide covers shared callers and acquisition. Data packs can change recipes. No in-game crafting, smelting, trading or dye-use test was run.

[Recipe 1]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/purple_dye.json
[Registration]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1695-L1710
[Crafting and consumption]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L82-L110
[Shapeless matching]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[Ordinary dye entries]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1855-L1870
[Further mixing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/recipe/crafting/magenta_dye_from_purple_and_pink.json

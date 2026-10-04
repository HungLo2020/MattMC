# Cracked Deepslate Tiles

## Obtaining

Smelt **1 [Deepslate Tiles](DeepslateTiles.md) → 1 Cracked Deepslate Tiles** in a [Furnace](../blocks/Furnace.md). The recipe takes **200 game ticks** (normally 10 seconds) and specifies **0.1 experience**. [Recipe]

Mine the placed block with an **unbroken pickaxe** to recover **1 Cracked Deepslate Tiles**; Silk Touch is unnecessary. Using another tool does not recover the item, even with Silk Touch. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Deepslate.md#obtaining-and-mining) for tool tiers and drop conditions.

## Usage

Use Cracked Deepslate Tiles as a decorative full block. There are no registered cracked Deepslate stairs, slabs or walls; compare the [available building variants](../blocks/Deepslate.md#building-variants).

## Behavior

This finish has no raw Deepslate axis control, and normal pickaxe mining preserves it. [Block registration] · [Drop table] The checked stonecutting recipes do not accept this block as an ingredient or provide a reverse conversion to an earlier finish; see [stonecutting limits](../blocks/Deepslate.md#stonecutting-shortcuts).

## Notes

* This item is the item form of the `minecraft:cracked_deepslate_tiles` block. [Item registration] · [Block registration]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes and loot were checked; no in-game crafting, mining or placement test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L547-L547
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6711-L6711
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cracked_deepslate_tiles.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/cracked_deepslate_tiles.json

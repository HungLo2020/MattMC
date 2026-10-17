# Deepslate Bricks

## Obtaining

Craft **4 [Polished Deepslate](PolishedDeepslate.md) in a 2 × 2 square → 4 Deepslate Bricks**. [Recipe]

At a [Stonecutter](../blocks/Stonecutter.md), **1 input → 1 Deepslate Bricks**. Choose [Cobbled Deepslate](CobbledDeepslate.md) [Stonecutting 1] or [Polished Deepslate](PolishedDeepslate.md) [Stonecutting 2]. See the [exact input/output matrix](../blocks/Deepslate.md#stonecutting-shortcuts).

Mine the placed block with an **unbroken pickaxe** to recover **1 Deepslate Bricks**; Silk Touch is unnecessary. Using another tool does not recover the item, even with Silk Touch. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Deepslate.md#obtaining-and-mining) for tool tiers and drop conditions.

## Usage

Use Deepslate Bricks as a dark building finish or make its [stairs, slabs and walls](../blocks/Deepslate.md#building-variants). Craft **4 Deepslate Bricks in a 2 × 2 square → 4 [Deepslate Tiles](DeepslateTiles.md)**. [Next finish] Smelt **1 Deepslate Bricks → 1 [Cracked Deepslate Bricks](CrackedDeepslateBricks.md)**. [Cracking recipe] Follow the [crafting and smelting guide](../blocks/Deepslate.md#crafting-and-smelting) for exact shape counts.

## Behavior

This is a full building block without raw Deepslate’s axis placement. Mining keeps this finish; it does not turn into Cobbled Deepslate. [Block registration] · [Drop table] See [orientation differences](../blocks/Deepslate.md#orienting-ordinary-deepslate) and [block properties](../blocks/Deepslate.md#block-properties).

## Notes

* This item is the item form of the `minecraft:deepslate_bricks` block. [Item registration] · [Block registration]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes and loot were checked; no in-game crafting, mining or placement test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L544-L544
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6699-L6701
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/deepslate_bricks.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/deepslate_bricks.json
[Stonecutting 1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_bricks_from_cobbled_deepslate_stonecutting.json
[Stonecutting 2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_bricks_from_polished_deepslate_stonecutting.json
[Next finish]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/deepslate_tiles.json
[Cracking recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/cracked_deepslate_bricks.json

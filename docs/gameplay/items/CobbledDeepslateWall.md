# Cobbled Deepslate Wall

## Obtaining

Craft **6 [Cobbled Deepslate](CobbledDeepslate.md) in two full rows → 6 Cobbled Deepslate Wall**. [Recipe]

At a [Stonecutter](../blocks/Stonecutter.md), **1 input → 1 Cobbled Deepslate Wall**. Choose [Cobbled Deepslate](CobbledDeepslate.md) [Stonecutting]. See the [exact input/output matrix](../blocks/Deepslate.md#stonecutting-shortcuts).

Mine the placed block with an **unbroken pickaxe** to recover **1 Cobbled Deepslate Wall**; Silk Touch is unnecessary. Using another tool does not recover the item, even with Silk Touch. [Drop table] · [Pickaxe tag] [Wall tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Deepslate.md#obtaining-and-mining) for tool tiers and drop conditions.

## Usage

Use Cobbled Deepslate Wall for connected walls, posts and building edges.

## Behavior

The placed wall changes its side connections and post according to neighboring blocks. [Wall behavior] See [Deepslate shape behavior](../blocks/Deepslate.md#placing-shapes-and-other-uses) and the shared [slab, stair and wall guide](../blocks/Stone.md#placing-shaped-blocks) for placement, connections and waterlogging.

## Notes

* This item is the item form of the `minecraft:cobbled_deepslate_wall` block. [Item registration] · [Block registration]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes and loot were checked; no in-game crafting, mining or placement test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L623-L623
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6678-L6680
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cobbled_deepslate_wall.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Wall tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/walls.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/cobbled_deepslate_wall.json
[Stonecutting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/cobbled_deepslate_wall_from_cobbled_deepslate_stonecutting.json
[Wall behavior]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java

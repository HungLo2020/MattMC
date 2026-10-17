# Polished Deepslate Stairs

## Obtaining

Craft **6 [Polished Deepslate](PolishedDeepslate.md) in a 1/2/3 stair pattern → 4 Polished Deepslate Stairs**. [Recipe]

At a [Stonecutter](../blocks/Stonecutter.md), **1 input → 1 Polished Deepslate Stairs**. Choose [Cobbled Deepslate](CobbledDeepslate.md) [Stonecutting 1] or [Polished Deepslate](PolishedDeepslate.md) [Stonecutting 2]. This gives one stair per block, compared with four stairs per six blocks when crafting. See the [exact input/output matrix](../blocks/Deepslate.md#stonecutting-shortcuts).

Mine the placed block with an **unbroken pickaxe** to recover **1 Polished Deepslate Stairs**; Silk Touch is unnecessary. Using another tool does not recover the item, even with Silk Touch. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Deepslate.md#obtaining-and-mining) for tool tiers and drop conditions.

## Usage

Use Polished Deepslate Stairs for stairways, rooflines and trim.

## Behavior

Placement chooses the facing and upper/lower half; nearby stairs can create inner or outer corners. [Stair placement] See [Deepslate shape behavior](../blocks/Deepslate.md#placing-shapes-and-other-uses) and the shared [slab, stair and wall guide](../blocks/Stone.md#placing-shaped-blocks) for placement, connections and waterlogging.

## Notes

* This item is the item form of the `minecraft:polished_deepslate_stairs` block. [Item registration] · [Block registration]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes and loot were checked; no in-game crafting, mining or placement test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L971-L971
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6684-L6684
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/polished_deepslate_stairs.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/polished_deepslate_stairs.json
[Stonecutting 1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_deepslate_stairs_from_cobbled_deepslate_stonecutting.json
[Stonecutting 2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_deepslate_stairs_from_polished_deepslate_stonecutting.json
[Stair placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L153

# Deepslate Brick Slab

## Obtaining

Craft **3 [Deepslate Bricks](DeepslateBricks.md) across one row → 6 Deepslate Brick Slab**. [Recipe]

At a [Stonecutter](../blocks/Stonecutter.md), **1 input → 2 Deepslate Brick Slab**. Choose [Cobbled Deepslate](CobbledDeepslate.md) [Stonecutting 1], [Polished Deepslate](PolishedDeepslate.md) [Stonecutting 2], [Deepslate Bricks](DeepslateBricks.md) [Stonecutting 3]. See the [exact input/output matrix](../blocks/Deepslate.md#stonecutting-shortcuts).

Mine with an **unbroken pickaxe**: a single slab returns **1 Deepslate Brick Slab**, and a double slab returns **2**. Silk Touch does not change those counts or bypass the pickaxe requirement. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Deepslate.md#obtaining-and-mining) for tool tiers and drop conditions.

## Usage

Use Deepslate Brick Slab for half-height floors, paths and detailing.

## Behavior

Two **Deepslate Brick Slab** items can combine into one double slab of the same type; this remains a slab block and mines back into two slabs. Place single slabs in the upper or lower half of a space. [Slab placement] See [Deepslate shape behavior](../blocks/Deepslate.md#placing-shapes-and-other-uses) and the shared [slab, stair and wall guide](../blocks/Stone.md#placing-shaped-blocks) for placement, connections and waterlogging.

## Notes

* This item is the item form of the `minecraft:deepslate_brick_slab` block. [Item registration] · [Block registration]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes and loot were checked; no in-game crafting, mining or placement test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L989-L989
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6703-L6703
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/deepslate_brick_slab.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/deepslate_brick_slab.json
[Stonecutting 1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_slab_from_cobbled_deepslate_stonecutting.json
[Stonecutting 2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_slab_from_polished_deepslate_stonecutting.json
[Stonecutting 3]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/deepslate_brick_slab_from_deepslate_bricks_stonecutting.json
[Slab placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L99

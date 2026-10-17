# Cobbled Deepslate Slab

## Obtaining

Craft **3 [Cobbled Deepslate](CobbledDeepslate.md) across one row → 6 Cobbled Deepslate Slab**. [Recipe]

At a [Stonecutter](../blocks/Stonecutter.md), **1 input → 2 Cobbled Deepslate Slab**. Choose [Cobbled Deepslate](CobbledDeepslate.md) [Stonecutting]. See the [exact input/output matrix](../blocks/Deepslate.md#stonecutting-shortcuts).

Mine with an **unbroken pickaxe**: a single slab returns **1 Cobbled Deepslate Slab**, and a double slab returns **2**. Silk Touch does not change those counts or bypass the pickaxe requirement. [Drop table] · [Pickaxe tag] · [Tool gate] · [Broken tools] See the [family mining rules](../blocks/Deepslate.md#obtaining-and-mining) for tool tiers and drop conditions.

## Usage

Use Cobbled Deepslate Slab for half-height floors, paths and detailing. Two of these slabs stacked vertically craft **1 [Chiseled Deepslate](ChiseledDeepslate.md)**. [Chiseled recipe]

## Behavior

Two **Cobbled Deepslate Slab** items can combine into one double slab of the same type; this remains a slab block and mines back into two slabs. Place single slabs in the upper or lower half of a space. [Slab placement] See [Deepslate shape behavior](../blocks/Deepslate.md#placing-shapes-and-other-uses) and the shared [slab, stair and wall guide](../blocks/Stone.md#placing-shaped-blocks) for placement, connections and waterlogging.

## Notes

* This item is the item form of the `minecraft:cobbled_deepslate_slab` block. [Item registration] · [Block registration]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Bundled recipes and loot were checked; no in-game crafting, mining or placement test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L987-L987
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6675-L6677
[Drop table]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cobbled_deepslate_slab.json
[Pickaxe tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[Tool gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Broken tools]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[Recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/cobbled_deepslate_slab.json
[Stonecutting]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/cobbled_deepslate_slab_from_cobbled_deepslate_stonecutting.json
[Chiseled recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chiseled_deepslate.json
[Slab placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L99

# Quartz Stairs

**Quartz Stairs** (`minecraft:quartz_stairs`) makes stairways, rooflines and angled quartz trim. [Item registration][items] · [Block registration][blocks]

## Obtaining

- Craft **6 accepted quartz blocks in a 1/2/3 stair pattern → 4 Quartz Stairs**. Each occupied slot independently accepts [Block of Quartz](BlockOfQuartz.md), [Chiseled Quartz Block](ChiseledQuartzBlock.md) or [Quartz Pillar](QuartzPillar.md), so they can be mixed. Quartz Bricks and Smooth Quartz Block are excluded. [Crafting recipe][craft] · [Slot matching][ingredient] [pattern][]
- At a [Stonecutter](../blocks/Stonecutter.md), cut **1 ordinary Block of Quartz → 1 Quartz Stairs**. The cutting recipe does not accept chiseled blocks or pillars. [Cutting recipe][cut]

See the [canonical shape recipes](../blocks/Quartz.md#crafting-stairs-and-slabs) and [stonecutting table](../blocks/Quartz.md#stonecutting).

Mine a placed stair with an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier; explosions have a separate survival condition. See [quartz mining rules](../blocks/Quartz.md#obtaining-and-mining). [Loot][loot] · [Tool tags][pickaxe] [wood-denials][] · [Tool checks][unbroken] [player-tool][] [break-dispatch][]

## Usage

For a set of four stairs, stonecutting uses **four ordinary Blocks of Quartz**; crafting uses **six accepted blocks**. Choose the Stonecutter to save base blocks, or use the broader crafting inputs to spend spare chiseled blocks and pillars. [Crafting][craft] · [Cutting][cut]

## Behavior

Placement chooses the stair’s horizontal facing and upper or lower half. Compatible neighboring stairs can form inner or outer corners automatically, and the stair can waterlog. Follow the [shared stair placement rules](../blocks/Stone.md#placing-shaped-blocks) and [quartz placement guide](../blocks/Quartz.md#pillar-orientation-and-placement). [Stair behavior][shape]

## Notes

This item places `minecraft:quartz_stairs`. [Smooth Quartz Stairs](SmoothQuartzStairs.md) is a separate shape made from Smooth Quartz Block; compare the [registered variants](../blocks/Quartz.md#registered-variants).

Related: [Block of Quartz](BlockOfQuartz.md) · [Quartz Slab](QuartzSlab.md) · [Smooth Quartz Stairs](SmoothQuartzStairs.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registration, recipe inputs and yields, loot, tool requirements and placement behavior. No in-game crafting, mining, smelting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L634-L634
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2898-L2898
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/quartz_stairs.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/quartz_stairs_from_quartz_block_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/quartz_stairs.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[unbroken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[player-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[ingredient]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/Ingredient.java#L32-L66
[pattern]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L194
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164

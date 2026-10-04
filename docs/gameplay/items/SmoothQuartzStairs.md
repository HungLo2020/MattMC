# Smooth Quartz Stairs

**Smooth Quartz Stairs** (`minecraft:smooth_quartz_stairs`) makes stairways and angled trim in the smooth quartz finish. [Item registration][items] · [Block registration][blocks]

## Obtaining

- Craft **6 [Smooth Quartz Blocks](SmoothQuartzBlock.md) in a 1/2/3 stair pattern → 4 Smooth Quartz Stairs**. [Crafting recipe][craft]
- At a [Stonecutter](../blocks/Stonecutter.md), cut **1 Smooth Quartz Block → 1 Smooth Quartz Stairs**. [Cutting recipe][cut]

Both routes require the smooth full block. Ordinary Block of Quartz, chiseled blocks and pillars do not substitute; see the [shape recipes](../blocks/Quartz.md#crafting-stairs-and-slabs) and [stonecutting table](../blocks/Quartz.md#stonecutting).

Mine a placed stair with an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier; explosions have a separate survival condition. See [quartz mining rules](../blocks/Quartz.md#obtaining-and-mining). [Loot][loot] · [Tool tags][pickaxe] [wood-denials][] · [Tool checks][unbroken] [player-tool][] [break-dispatch][]

## Usage

For four stairs, stonecutting uses **four Smooth Quartz Blocks** instead of the **six** needed by crafting. Smelt only as many full blocks as your chosen route needs. [Crafting][craft] · [Cutting][cut] · [Smoothing recipe][smelt]

## Behavior

Placement chooses the stair’s horizontal facing and upper or lower half. Compatible neighboring stairs can form inner or outer corners automatically, and the stair can waterlog. Follow the [shared stair placement rules](../blocks/Stone.md#placing-shaped-blocks) and [quartz placement guide](../blocks/Quartz.md#pillar-orientation-and-placement). [Stair behavior][shape]

## Notes

This item places `minecraft:smooth_quartz_stairs`. It is separate from ordinary [Quartz Stairs](QuartzStairs.md); compare the [registered quartz variants](../blocks/Quartz.md#registered-variants).

Related: [Smooth Quartz Block](SmoothQuartzBlock.md) · [Smooth Quartz Slab](SmoothQuartzSlab.md) · [Quartz Stairs](QuartzStairs.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`: registration, recipe inputs and yields, loot, tool requirements and placement behavior. No in-game crafting, mining, smelting or placement test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L964-L964
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5244-L5244
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/smooth_quartz_stairs.json
[cut]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/smooth_quartz_stairs_from_smooth_quartz_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/smooth_quartz_stairs.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[wood-denials]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[unbroken]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[player-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[break-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/smooth_quartz.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164

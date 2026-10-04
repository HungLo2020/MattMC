# Blackstone Wall

**Blackstone Wall** (`minecraft:blackstone_wall`) provides the rough wall form for borders, posts and narrow barriers. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **6 [Blackstone](Blackstone.md) in two full rows of three → 6 Blackstone Walls**. A [Stonecutter](../blocks/Stonecutter.md) turns **1 Blackstone → 1 wall**. Cutting keeps the crafting yield while allowing a one-block batch. [Crafting][recipe-1] · [Cut from Blackstone][recipe-2]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

Connections form toward adjacent walls, eligible sturdy block faces, Iron Bars, Copper Bars or panes, and correctly aligned Fence Gates. The surroundings determine the connecting arms and post shape. [Wall connections and placement][shape] · [Shared wall rules](../blocks/Stone.md#placing-shaped-blocks) · [Copper Bars registration][copper-bars] [inheritance][copper-bars-inheritance]

## Behavior

This wall can hold Water. Mining returns one Blackstone Wall item, retaining its finish; connections are recalculated when it is placed again. [Placement and Water][shape] · [Matching loot][loot]

## Notes

Compare the [three Blackstone finishes](../blocks/BlackstoneAndBasalt.md#blackstone-variants), their [crafting recipes](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting), [cutting shortcuts](../blocks/BlackstoneAndBasalt.md#stonecutting) and [placed properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties).

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/blackstone_wall.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/blackstone_wall_from_blackstone_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L254
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L620
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5820
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/blackstone_wall.json

[copper-bars]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2310-L2316
[copper-bars-inheritance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopperBarsBlock.java#L11-L11

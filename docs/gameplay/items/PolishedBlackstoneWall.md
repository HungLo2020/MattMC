# Polished Blackstone Wall

**Polished Blackstone Wall** (`minecraft:polished_blackstone_wall`) provides the polished wall form for borders, posts and narrow barriers. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **6 [Polished Blackstone](PolishedBlackstone.md) in two full rows of three → 6 Polished Blackstone Walls**. A [Stonecutter](../blocks/Stonecutter.md) turns **1 Blackstone or 1 Polished Blackstone → 1 wall**. Cutting keeps the crafting yield while allowing a one-block batch. The raw Blackstone route skips the intermediate finishing crafts. [Crafting][recipe-1] · [Cut from Blackstone][recipe-2] · [Cut from Polished Blackstone][recipe-3]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

Connections form toward adjacent walls, eligible sturdy block faces, Iron Bars, Copper Bars or panes, and correctly aligned Fence Gates. The surroundings determine the connecting arms and post shape. [Wall connections and placement][shape] · [Shared wall rules](../blocks/Stone.md#placing-shaped-blocks) · [Copper Bars registration][copper-bars] [inheritance][copper-bars-inheritance]

## Behavior

This wall can hold Water. Mining returns one Polished Blackstone Wall item, retaining its finish; connections are recalculated when it is placed again. [Placement and Water][shape] · [Matching loot][loot]

## Notes

Compare the [three Blackstone finishes](../blocks/BlackstoneAndBasalt.md#blackstone-variants), their [crafting recipes](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting), [cutting shortcuts](../blocks/BlackstoneAndBasalt.md#stonecutting) and [placed properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties).

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/polished_blackstone_wall.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_wall_from_blackstone_stonecutting.json
[recipe-3]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_blackstone_wall_from_polished_blackstone_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L254
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L621
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5862-L5864
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/polished_blackstone_wall.json

[copper-bars]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2310-L2316
[copper-bars-inheritance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopperBarsBlock.java#L11-L11

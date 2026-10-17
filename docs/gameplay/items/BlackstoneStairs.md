# Blackstone Stairs

**Blackstone Stairs** (`minecraft:blackstone_stairs`) provides the rough stair form for steps, roof edges and corners. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **6 [Blackstone](Blackstone.md) in the 1/2/3 stair pattern → 4 Blackstone Stairs**. A [Stonecutter](../blocks/Stonecutter.md) turns **1 Blackstone → 1 stair**. Cutting six input blocks gives six stairs instead of the four from crafting. [Crafting][recipe-1] · [Cut from Blackstone][recipe-2]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

Your horizontal facing sets the stair direction; the clicked face and height choose upright or upside-down placement. Suitable neighboring stairs automatically form inner or outer corners, including other stair materials when their half and facing fit. [Placement and compatible corners][shape] · [Shared stair controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

These stairs can hold Water. Mining returns the Blackstone Stairs item, so choose its direction and half again when placing it; nearby stairs determine its new corner shape. [Stair states][shape] · [Matching loot][loot]

## Notes

Compare the [three Blackstone finishes](../blocks/BlackstoneAndBasalt.md#blackstone-variants), their [crafting recipes](../blocks/BlackstoneAndBasalt.md#crafting-and-smelting), [cutting shortcuts](../blocks/BlackstoneAndBasalt.md#stonecutting) and [placed properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties).

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/blackstone_stairs.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/blackstone_stairs_from_blackstone_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2487
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5819
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/blackstone_stairs.json

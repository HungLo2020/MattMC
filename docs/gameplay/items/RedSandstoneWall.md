# Red Sandstone Wall

A **Red Sandstone Wall** (`minecraft:red_sandstone_wall`) places a connecting wall for barriers, posts and edging.

## Obtaining

Craft **6 ordinary [Red Sandstone](RedSandstone.md) in two full rows into 6 Red Sandstone Walls** at a Crafting Table, or stonecut **1 ordinary Red Sandstone into 1 wall**. Both routes use the base full block of this color; Cut, Chiseled and Smooth blocks do not substitute. [Crafting][recipe-1] · [Stonecutting][recipe-2]

Mine with an **unbroken pickaxe, including Wood**. In ordinary mining, the placed block drops **1 Red Sandstone Wall item**. Hand mining does not collect the normal drop. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Family mining rules](../blocks/Sandstone.md#obtaining-and-mining)

## Usage

Crafting and stonecutting have the same material yield, while the cutter lets you make one wall at a time. Choose the wall output in the [family recipe table](../blocks/Sandstone.md#crafting-finishes-and-shapes) or [stonecutting list](../blocks/Sandstone.md#stonecutting).

## Behavior

The wall connects to other walls, eligible sturdy neighboring faces, bars or glass panes, and suitably aligned fence gates. It adjusts its connections to its neighbors and can be waterlogged. [Shared wall controls](../blocks/Stone.md#placing-shaped-blocks) · [Connection and placement rules][shape]

## Notes

This item places the `minecraft:red_sandstone_wall` block. The [Sandstone family guide](../blocks/Sandstone.md#registered-forms-and-loot) lists its related forms. [Item registration][item] · [Block registration][block]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game test. Data packs can change recipes and loot. [Items](Items.md) · [Blocks](../blocks/Blocks.md)

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/red_sandstone_wall.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/red_sandstone_wall_from_red_sandstone_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/red_sandstone_wall.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L131
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L609
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5273-L5275

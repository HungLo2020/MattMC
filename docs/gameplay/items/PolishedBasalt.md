# Polished Basalt

**Polished Basalt** (`minecraft:polished_basalt`) is the polished Basalt building block, with an axis you can align vertically or horizontally. [Item binding][item] · [Block registration][block]

## Obtaining

Craft **4 [Basalt](Basalt.md) in a 2 × 2 square → 4 Polished Basalt**, or stonecut **1 Basalt → 1 Polished Basalt**. Both routes keep the block count; cutting lets you polish a single block. [Crafting][recipe-1] · [Cut from Basalt][recipe-2]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Mining and tool requirements](../blocks/BlackstoneAndBasalt.md#mining-and-ordinary-drops)

## Usage

The face you click chooses the axis: a **top or bottom face** makes it vertical, an **east or west side** aligns it east–west, and a **north or south side** aligns it north–south. Use that control for columns, beams or aligned floor patterns. [Axis placement][pillar] · [Basalt orientation guide](../blocks/BlackstoneAndBasalt.md#basalt-variants-and-orientation)

## Behavior

Mining returns Polished Basalt without carrying over its previous axis, so choose the orientation again on placement. [Loot][loot] · [Placement][pillar]

[Ways to obtain ordinary Basalt](../blocks/BlackstoneAndBasalt.md#finding-blackstone-and-basalt) and the [lava conversion rule](../blocks/BlackstoneAndBasalt.md#making-basalt-with-lava) are documented in the family guide. To make [Smooth Basalt](SmoothBasalt.md), keep ordinary Basalt for its smelting recipe; that recipe does not accept Polished Basalt.

## Notes

Compare [Basalt finishes and their placement](../blocks/BlackstoneAndBasalt.md#basalt-variants-and-orientation) and [material properties](../blocks/BlackstoneAndBasalt.md#placement-and-properties).

[Items](Items.md)

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Recipes, loot, item/block registration, tool gates and relevant placement rules were checked. No in-game crafting, smelting, mining or placement test was run.

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/polished_basalt.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_basalt_from_basalt_stonecutting.json
[pillar]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L55
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L520
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2023-L2032
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/polished_basalt.json

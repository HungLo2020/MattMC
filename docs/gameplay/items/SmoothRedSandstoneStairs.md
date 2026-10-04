# Smooth Red Sandstone Stairs

**Smooth Red Sandstone Stairs** (`minecraft:smooth_red_sandstone_stairs`) places steps and angled building details in the red Sandstone family.

## Obtaining

Craft **6 [Smooth Red Sandstone](SmoothRedSandstone.md) in three stair rows of 1, 2 and 3 into 4 Smooth Red Sandstone Stairs** at a Crafting Table, or stonecut **1 Smooth Red Sandstone into 1 stair**. Both routes require the Smooth full block of this color. [Crafting][recipe-1] · [Stonecutting][recipe-2]

Mine with an **unbroken pickaxe, including Wood**. In ordinary mining, the placed block drops **1 Smooth Red Sandstone Stairs item**. Hand mining does not collect the normal drop. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Family mining rules](../blocks/Sandstone.md#obtaining-and-mining)

## Usage

Use the stairs for steps, rooflines and corner trim. For a batch of four, stonecutting spends **4 full blocks instead of the 6 used by crafting**. See the [family recipe table](../blocks/Sandstone.md#crafting-finishes-and-shapes) and [stonecutting choices](../blocks/Sandstone.md#stonecutting).

## Behavior

Placement sets the facing and the upper or lower half. Compatible neighboring stairs automatically form inner and outer corners, including stairs of other materials or colors when their facing and half allow it. The placed stairs can be waterlogged. [Shared stair controls](../blocks/Stone.md#placing-shaped-blocks) · [Placement and corners][shape]

## Notes

This item places the `minecraft:smooth_red_sandstone_stairs` block. The [Sandstone family guide](../blocks/Sandstone.md#registered-forms-and-loot) lists its related forms. [Item registration][item] · [Block registration][block]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game test. Data packs can change recipes and loot. [Items](Items.md) · [Blocks](../blocks/Blocks.md)

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/smooth_red_sandstone_stairs.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/smooth_red_sandstone_stairs_from_smooth_red_sandstone_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/smooth_red_sandstone_stairs.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L957
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5237

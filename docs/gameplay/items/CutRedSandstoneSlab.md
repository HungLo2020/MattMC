# Cut Red Sandstone Slab

A **Cut Red Sandstone Slab** (`minecraft:cut_red_sandstone_slab`) places a half-height building piece for floors, ledges and trim.

## Obtaining

Craft **3 [Cut Red Sandstone](CutRedSandstone.md) in one horizontal row into 6 Cut Red Sandstone Slabs** at a Crafting Table. Alternatively, stonecut **1 ordinary [Red Sandstone](RedSandstone.md) or 1 Cut Red Sandstone into 2 Cut Red Sandstone Slabs**. Cutting the ordinary block skips the separate Cut-block crafting step. [Crafting][recipe-1] · [Stonecutting from Cut Red Sandstone][recipe-2] · [Stonecutting from Red Sandstone][recipe-3]

Mine with an **unbroken pickaxe, including Wood**. In ordinary mining, a single slab drops **1 matching slab** and a double slab drops **2**. Hand mining does not collect the normal drop. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Family mining rules](../blocks/Sandstone.md#obtaining-and-mining)

## Usage

Use the Cut finish for half-height floors and edges. It is a separate item from ordinary and Smooth Red Sandstone Slabs; check the [family recipe table](../blocks/Sandstone.md#crafting-finishes-and-shapes) when choosing the result.

## Behavior

Choose the upper or lower half by the clicked face and height. A second **identical Cut Red Sandstone Slab** can fill the empty half to make a double slab; that state remains a slab block, rather than becoming the Cut Red Sandstone full-block ingredient. Single slabs can be waterlogged; doubling clears that state. [Shared slab controls](../blocks/Stone.md#placing-shaped-blocks) · [Placement rules][shape]

## Notes

This item places the `minecraft:cut_red_sandstone_slab` block. The [Sandstone family guide](../blocks/Sandstone.md#registered-forms-and-loot) lists its related forms. [Item registration][item] · [Block registration][block]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game test. Data packs can change recipes and loot. [Items](Items.md) · [Blocks](../blocks/Blocks.md)

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/cut_red_sandstone_slab.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/cut_red_sandstone_slab_from_cut_red_sandstone_stonecutting.json
[recipe-3]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/cut_red_sandstone_slab_from_red_sandstone_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/cut_red_sandstone_slab.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L408
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3907-L3911

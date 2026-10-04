# Smooth Sandstone Slab

A **Smooth Sandstone Slab** (`minecraft:smooth_sandstone_slab`) places a half-height building piece for floors, ledges and trim.

## Obtaining

Craft **3 [Smooth Sandstone](SmoothSandstone.md) in one horizontal row into 6 Smooth Sandstone Slabs** at a Crafting Table, or stonecut **1 Smooth Sandstone into 2 Smooth Sandstone Slabs**. Both routes use the Smooth full block of this color and give two slabs per input block. [Crafting][recipe-1] · [Stonecutting][recipe-2]

Mine with an **unbroken pickaxe, including Wood**. In ordinary mining, a single slab drops **1 matching slab** and a double slab drops **2**. Hand mining does not collect the normal drop. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Family mining rules](../blocks/Sandstone.md#obtaining-and-mining)

## Usage

Use it for half-height floors, ledges and details with the Smooth finish. Ordinary and Cut Sandstone Slabs are separate items; see the [family recipe table](../blocks/Sandstone.md#crafting-finishes-and-shapes) for their inputs.

## Behavior

Choose the upper or lower half by the clicked face and height. A second **identical Smooth Sandstone Slab** can fill the empty half to make a double slab; that state remains a slab block, rather than becoming the Smooth Sandstone full-block ingredient. Single slabs can be waterlogged; doubling clears that state. [Shared slab controls](../blocks/Stone.md#placing-shaped-blocks) · [Placement rules][shape]

## Notes

This item places the `minecraft:smooth_sandstone_slab` block. The [Sandstone family guide](../blocks/Sandstone.md#registered-forms-and-loot) lists its related forms. [Item registration][item] · [Block registration][block]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game test. Data packs can change recipes and loot. [Items](Items.md) · [Blocks](../blocks/Blocks.md)

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/smooth_sandstone_slab.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/smooth_sandstone_slab_from_smooth_sandstone_stonecutting.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/smooth_sandstone_slab.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L980
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5262

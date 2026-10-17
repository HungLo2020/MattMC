# Sandstone Slab

A **Sandstone Slab** (`minecraft:sandstone_slab`) places a half-height building piece for floors, ledges and trim.

## Obtaining

At a Crafting Table, fill **one horizontal row of 3 blocks to make 6 Sandstone Slabs**. Each slot independently accepts ordinary [Sandstone](Sandstone.md) or [Chiseled Sandstone](ChiseledSandstone.md), so those two finishes may be mixed within the row. Keep all three inputs pale; red blocks do not substitute. Stonecut **1 ordinary Sandstone into 2 Sandstone Slabs**; Chiseled blocks are not an input to that cutting recipe. [Crafting][recipe-1] · [Stonecutting][recipe-2]

Mine with an **unbroken pickaxe, including Wood**. In ordinary mining, a single slab drops **1 matching slab** and a double slab drops **2**. Hand mining does not collect the normal drop. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Family mining rules](../blocks/Sandstone.md#obtaining-and-mining)

## Usage

This is the base slab used to craft [Chiseled Sandstone](ChiseledSandstone.md): place two vertically for one Chiseled block. Cut and Smooth slabs are separate items. See the [family recipe table](../blocks/Sandstone.md#crafting-finishes-and-shapes). [Chiseled recipe][chiseled-recipe]

## Behavior

Choose the upper or lower half by the clicked face and height. A second **identical Sandstone Slab** can fill the empty half to make a double slab; that state remains a slab block, rather than becoming the Sandstone full-block ingredient. Single slabs can be waterlogged; doubling clears that state. [Shared slab controls](../blocks/Stone.md#placing-shaped-blocks) · [Placement rules][shape]

## Notes

This item places the `minecraft:sandstone_slab` block. The [Sandstone family guide](../blocks/Sandstone.md#registered-forms-and-loot) lists its related forms. [Item registration][item] · [Block registration][block]

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game test. Data packs can change recipes and loot. [Items](Items.md) · [Blocks](../blocks/Blocks.md)

[recipe-1]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/sandstone_slab.json
[recipe-2]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/sandstone_slab_from_sandstone_stonecutting.json
[chiseled-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/chiseled_sandstone.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/sandstone_slab.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L398
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3847-L3851

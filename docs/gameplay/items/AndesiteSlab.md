# Andesite Slab

**Andesite Slab** (`minecraft:andesite_slab`) is a half-height building piece for floors, paths and trim in the [Andesite family](../blocks/DecorativeStone.md#polished-and-shaped-variants). [Item binding][item]

## Obtaining

Craft **3 blocks across one row → 6 Andesite Slabs**. Every occupied slot requires [Andesite](Andesite.md); use the matching full-block finish. [Crafting][craft]

Alternatively, stonecut **1 Andesite → 2 Andesite Slabs** at a [Stonecutter](../blocks/Stonecutter.md). [Stonecutting][cut-raw] Cutting keeps the same material yield as crafting and works one input block at a time.

Use an **unbroken pickaxe**, including a Wooden Pickaxe, to recover **one matching slab from a single slab, or two from a double slab**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune does not increase the count. Explosion survival or decay is a separate condition. [Exact loot][loot] · [Tool and drop rules](../blocks/DecorativeStone.md#mining-and-drops)

## Usage

Use single slabs for half-height surfaces, or place a second **Andesite Slab item** into the empty half to make a full-height double slab. It remains a slab block and recovers as slab items, rather than becoming a [Andesite](Andesite.md) ingredient. [Placement and doubling][shape] · [Exact loot][loot]

## Behavior

Choose the upper or lower half through the clicked face and height. Only the same slab item can fill the other half. Single slabs can be waterlogged; doubling clears that waterlogged state. See the [shared slab placement rules](../blocks/Stone.md#placing-shaped-blocks). [Slab behavior][shape]

## Notes

This is the ordinary item form of the `minecraft:andesite_slab` block. Follow the [family guide](../blocks/DecorativeStone.md#polished-and-shaped-variants) for shared recipes and placed behavior. [Block registration][blocks] · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. The exact recipes, complete loot table, item binding, tool requirements and relevant placement code were checked. No in-game crafting, mining or placement test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L983-L983
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5265-L5265
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/andesite_slab.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/andesite_slab.json
[cut-raw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/andesite_slab_from_andesite_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115

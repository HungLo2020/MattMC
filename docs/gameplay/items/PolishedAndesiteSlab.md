# Polished Andesite Slab

**Polished Andesite Slab** (`minecraft:polished_andesite_slab`) is a half-height building piece for floors, paths and trim in the [Andesite family](../blocks/DecorativeStone.md#polished-and-shaped-variants). [Item binding][item]

## Obtaining

Craft **3 blocks across one row → 6 Polished Andesite Slabs**. Every occupied slot requires [Polished Andesite](PolishedAndesite.md); use the matching full-block finish. [Crafting][craft]

The [Stonecutter](../blocks/Stonecutter.md) accepts **1 Andesite or 1 Polished Andesite → 2 Polished Andesite Slabs**. The raw-block route skips the separate polishing craft. [From raw Andesite][cut-raw] · [From Polished Andesite][cut-polished] Cutting keeps the same material yield as crafting and works one input block at a time.

Use an **unbroken pickaxe**, including a Wooden Pickaxe, to recover **one matching slab from a single slab, or two from a double slab**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune does not increase the count. Explosion survival or decay is a separate condition. [Exact loot][loot] · [Tool and drop rules](../blocks/DecorativeStone.md#mining-and-drops)

## Usage

Use single slabs for half-height surfaces, or place a second **Polished Andesite Slab item** into the empty half to make a full-height double slab. It remains a slab block and recovers as slab items, rather than becoming a [Polished Andesite](PolishedAndesite.md) ingredient. [Placement and doubling][shape] · [Exact loot][loot]

## Behavior

Choose the upper or lower half through the clicked face and height. Only the same slab item can fill the other half. Single slabs can be waterlogged; doubling clears that waterlogged state. See the [shared slab placement rules](../blocks/Stone.md#placing-shaped-blocks). [Slab behavior][shape]

## Notes

This is the ordinary item form of the `minecraft:polished_andesite_slab` block. Follow the [family guide](../blocks/DecorativeStone.md#polished-and-shaped-variants) for shared recipes and placed behavior. [Block registration][blocks] · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. The exact recipes, complete loot table, item binding, tool requirements and relevant placement code were checked. No in-game crafting, mining or placement test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L985-L985
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5267-L5269
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/polished_andesite_slab.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/polished_andesite_slab.json
[cut-raw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_andesite_slab_from_andesite_stonecutting.json
[cut-polished]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/polished_andesite_slab_from_polished_andesite_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115

# Tuff Slab

**Tuff Slab** (`minecraft:tuff_slab`) is the ordinary half-height Tuff form in the [family variants table](../blocks/Tuff.md#variants), used for floors, paths and detail work. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **3 Tuff in one horizontal row → 6 Tuff Slabs**, or stonecut **1 Tuff → 2 Tuff Slabs**. Both routes give two slabs per input block. Two of these slab items stacked vertically also craft [Chiseled Tuff](ChiseledTuff.md#obtaining). [Slab craft][craft] · [Stonecutting][cut-tuff]

Use an **unbroken pickaxe**, including Wood, to collect it. A single slab returns **one matching slab** and a double returns **two**; Silk Touch is unnecessary and Fortune adds no multiplier. Hand breaking does not meet the correct-tool requirement. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Place a top or bottom slab, or add a second **Tuff Slab** to the empty half for a double slab. Polished and brick slabs do not combine with it. See the [shared slab placement controls](../blocks/Stone.md#placing-shaped-blocks). [Matching-item check][slab]

## Behavior

A single slab can be waterlogged. Combining two clears that Water state, and a double slab cannot accept Water through the waterlogging interface. A double slab is still the slab block and yields two slab items when properly mined. [Water and double-state rules][slab] · [Exact double-slab loot][loot]

## Notes

The exact item and block ID is `minecraft:tuff_slab`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/tuff_slab.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_slab_from_tuff_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/tuff_slab.json
[slab]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115

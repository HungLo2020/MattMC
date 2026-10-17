# Polished Tuff Slab

**Polished Tuff Slab** (`minecraft:polished_tuff_slab`) is the polished half-height option in the [Tuff family](../blocks/Tuff.md#variants), for smooth floors, steps and trim. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **3 Polished Tuff in one row → 6 Polished Tuff Slabs**. Stonecut **1 Tuff or 1 Polished Tuff → 2 slabs**. The raw-Tuff shortcut skips polishing; each route uses one input block per two slab items. [Craft][craft] · [From Tuff][cut-tuff] · [From Polished Tuff][cut-polished-tuff]

Use an **unbroken pickaxe**, including Wood, to collect it. A single slab returns **one matching slab** and a double returns **two**; Silk Touch is unnecessary and Fortune adds no multiplier. Hand breaking does not meet the correct-tool requirement. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Choose an upper or lower half by the placement face and height. Only another **Polished Tuff Slab** can fill the other half; ordinary Tuff and Tuff Brick Slabs cannot mix with it. [Slab placement and replacement][slab] · [Shared controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

Waterlogging works for a single slab. Doubling clears the waterlogged state and prevents further Water insertion into that double slab. Proper mining returns one polished slab from a single and two from a double, keeping the finish. [Water rules][slab] · [Exact loot][loot]

## Notes

The exact item and block ID is `minecraft:polished_tuff_slab`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/polished_tuff_slab.json
[cut-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_slab_from_polished_tuff_stonecutting.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_slab_from_tuff_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/polished_tuff_slab.json
[slab]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115

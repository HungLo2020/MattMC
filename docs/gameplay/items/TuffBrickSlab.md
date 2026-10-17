# Tuff Brick Slab

**Tuff Brick Slab** (`minecraft:tuff_brick_slab`) is the brick-pattern half-height form in the [Tuff variants table](../blocks/Tuff.md#variants), for floors, ledges and decorative trim. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **3 Tuff Bricks in one row → 6 Tuff Brick Slabs**. Stonecut **1 Tuff, 1 Polished Tuff or 1 Tuff Bricks → 2 slabs**. The direct raw-Tuff route avoids intermediate finishes. Two brick slabs stacked vertically are also the recipe for [Chiseled Tuff Bricks](ChiseledTuffBricks.md#obtaining). [Craft][craft] · [From Tuff][cut-tuff] · [From Polished Tuff][cut-polished-tuff] · [From Tuff Bricks][cut-tuff-bricks]

Use an **unbroken pickaxe**, including Wood, to collect it. A single slab returns **one matching slab** and a double returns **two**; Silk Touch is unnecessary and Fortune adds no multiplier. Hand breaking does not meet the correct-tool requirement. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Place in the top or bottom half, or combine two **Tuff Brick Slabs** in one space. Other Tuff slab finishes are different items and cannot complete that double slab. [Matching-item placement][slab] · [Shared slab controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

Singles can be waterlogged, but doubling clears the stored Water. Correct-tool mining returns one brick slab from a single or two from a double; it does not turn a double slab into the full Tuff Bricks item. [Water behavior][slab] · [Double-slab loot][loot]

## Notes

The exact item and block ID is `minecraft:tuff_brick_slab`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/tuff_brick_slab.json
[cut-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_slab_from_polished_tuff_stonecutting.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_slab_from_tuff_stonecutting.json
[cut-tuff-bricks]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_slab_from_tuff_bricks_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/tuff_brick_slab.json
[slab]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L69-L115

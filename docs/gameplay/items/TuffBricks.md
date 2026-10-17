# Tuff Bricks

**Tuff Bricks** (`minecraft:tuff_bricks`) is the full brick-pattern block of the [Tuff family](../blocks/Tuff.md#variants), and the crafting ingredient for its brick stairs, slabs and walls. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **4 Polished Tuff in a 2 × 2 square → 4 Tuff Bricks**. A Stonecutter instead accepts **1 Tuff or 1 Polished Tuff → 1 Tuff Bricks**. Cutting raw Tuff skips the polishing craft without changing the one-block yield. [Crafting][craft] · [From Tuff][cut-tuff] · [From Polished Tuff][cut-polished-tuff]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Place it for brickwork, or use it for [brick stairs](TuffBrickStairs.md), [brick slabs](TuffBrickSlab.md) and [brick walls](TuffBrickWall.md). Chiseled Tuff Bricks have their own [slab-crafting and direct-cutting routes](ChiseledTuffBricks.md#obtaining).

## Behavior

This full block has no player-selected facing. The checked family has **no cracked Tuff Bricks** and no smelting recipe for such a finish; do not plan a Furnace conversion. There is also no reverse stonecutting route to Polished or raw Tuff. [Registered forms][blocks] · [Family recipe limits](../blocks/Tuff.md#crafting) · [Stonecutting limits](../blocks/Tuff.md#stonecutting)

## Notes

The exact item and block ID is `minecraft:tuff_bricks`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/Blocks.java#L6001-L6030
[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/tuff_bricks.json
[cut-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_bricks_from_polished_tuff_stonecutting.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_bricks_from_tuff_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/tuff_bricks.json

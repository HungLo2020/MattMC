# Tuff Brick Stairs

**Tuff Brick Stairs** (`minecraft:tuff_brick_stairs`) places the brick-pattern stair form in the [Tuff family](../blocks/Tuff.md#variants), useful for matching brick roofs and stairways. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **6 Tuff Bricks in the 1/2/3 stair pattern → 4 Tuff Brick Stairs**. A Stonecutter accepts **1 Tuff, 1 Polished Tuff or 1 Tuff Bricks → 1 stair**. Direct cutting from raw Tuff avoids the polishing and brick-crafting steps and gives six stairs per six input blocks. [Craft][craft] · [From Tuff][cut-tuff] · [From Polished Tuff][cut-polished-tuff] · [From Tuff Bricks][cut-tuff-bricks]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Place normally or upside down, using your horizontal direction for facing and the clicked face/height for the half. Compatible neighboring stairs can form corners, and the form can be waterlogged. [Stair callback][stairs] · [Shared placement controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

The placed stair keeps the brick finish when collected. Do not use the full-block name `minecraft:tuff_bricks` when you need this separate stair item. The [family Stonecutter table](../blocks/Tuff.md#stonecutting) shows the output choices.

## Notes

The exact item and block ID is `minecraft:tuff_brick_stairs`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/tuff_brick_stairs.json
[cut-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_stairs_from_polished_tuff_stonecutting.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_stairs_from_tuff_stonecutting.json
[cut-tuff-bricks]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_stairs_from_tuff_bricks_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/tuff_brick_stairs.json
[stairs]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164

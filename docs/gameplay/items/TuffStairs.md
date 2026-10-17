# Tuff Stairs

**Tuff Stairs** (`minecraft:tuff_stairs`) places the ordinary Tuff stair form in the [family variants table](../blocks/Tuff.md#variants), useful for stairways, rooflines and angled trim. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **6 Tuff in the 1/2/3 stair pattern → 4 Tuff Stairs**, or stonecut **1 Tuff → 1 Tuff Stairs**. Stonecutting gives six stairs from six blocks instead of four, so it is the more material-efficient route. [Stair craft][craft] · [Stonecutting][cut-tuff] · [Shared crafting pattern](../blocks/Stone.md#crafting-yields)

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Place upright or upside-down stairs according to the clicked face and height. Their horizontal facing follows the player, and compatible nearby stairs form corners automatically. They can be waterlogged. [Stair placement and corners][stairs] · [Shared controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

A corner is a placed state of the same stair item. Mining preserves the Tuff stair form; place the item again to choose its direction and half. The [Tuff placement guide](../blocks/Tuff.md#placement-and-properties) covers the shared building properties.

## Notes

The exact item and block ID is `minecraft:tuff_stairs`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/tuff_stairs.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_stairs_from_tuff_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/tuff_stairs.json
[stairs]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164

# Polished Tuff Stairs

**Polished Tuff Stairs** (`minecraft:polished_tuff_stairs`) places the polished stair finish in the [Tuff variants table](../blocks/Tuff.md#variants), for smooth stairways, roof edges and trim. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **6 Polished Tuff in the 1/2/3 stair pattern → 4 Polished Tuff Stairs**. Stonecut **1 raw Tuff or 1 Polished Tuff → 1 stair** instead. The raw-Tuff shortcut avoids polishing first and gives the better stair yield. [Crafting][craft] · [From Tuff][cut-tuff] · [From Polished Tuff][cut-polished-tuff]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Use the clicked face and height to choose normal or upside-down placement. Compatible neighboring stairs of any material can make inner or outer corners when their half and facing fit. These stairs can hold Water. [Placement and class-based corner test][stairs] · [Shared stair controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

Collecting one returns the polished stair item, not Polished Tuff or raw Tuff. Its rotation and corner shape are selected again on placement. Compare [all three stair finishes](../blocks/Tuff.md#variants) before cutting a large batch.

## Notes

The exact item and block ID is `minecraft:polished_tuff_stairs`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/polished_tuff_stairs.json
[cut-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_stairs_from_polished_tuff_stonecutting.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_stairs_from_tuff_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/polished_tuff_stairs.json
[stairs]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L164

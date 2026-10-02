# Polished Tuff Wall

**Polished Tuff Wall** (`minecraft:polished_tuff_wall`) places the polished wall finish in the [Tuff family](../blocks/Tuff.md#variants), for matching edging and decorative posts. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **6 Polished Tuff in two full rows → 6 Polished Tuff Walls**. Stonecut **1 Tuff or 1 Polished Tuff → 1 wall**. Cutting raw Tuff skips the polishing craft, while keeping the one-wall-per-block yield. [Crafting][craft] · [From Tuff][cut-tuff] · [From Polished Tuff][cut-polished-tuff]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

It joins using the normal wall rules and is present in the bundled walls tag. Neighboring walls and suitable connecting blocks choose its arms; blocks overhead can alter the post. Waterlogging is supported. [Tag membership][wall-tag] · [Connections and Water state][walls] · [Shared controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

Mining preserves the polished wall finish. It is a separate inventory item from the full Polished Tuff block; choose the wall output in the [family Stonecutter table](../blocks/Tuff.md#stonecutting).

## Notes

The exact item and block ID is `minecraft:polished_tuff_wall`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/polished_tuff_wall.json
[cut-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_wall_from_polished_tuff_stonecutting.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_wall_from_tuff_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/polished_tuff_wall.json
[wall-tag]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/tags/block/walls.json
[walls]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L192

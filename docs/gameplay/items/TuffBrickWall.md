# Tuff Brick Wall

**Tuff Brick Wall** (`minecraft:tuff_brick_wall`) places the brick-pattern wall in the [Tuff family](../blocks/Tuff.md#variants), for brick edging, posts and barriers. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **6 Tuff Bricks in two full rows → 6 Tuff Brick Walls**. Stonecut **1 Tuff, 1 Polished Tuff or 1 Tuff Bricks → 1 wall** instead. The direct raw-Tuff route skips both intermediate crafting finishes without changing the yield. [Craft][craft] · [From Tuff][cut-tuff] · [From Polished Tuff][cut-polished-tuff] · [From Tuff Bricks][cut-tuff-bricks]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

The bundled walls tag includes this form, so it participates in ordinary wall-to-wall connections. Suitable sturdy faces, Iron Bars and aligned Fence Gates can also connect, while blocks above affect the post. [Membership][wall-tag] · [Connection rules][walls] · [Shared wall controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

Waterlogging is supported. The brick wall remains the matching item when collected; neighbor-dependent arms and posts are chosen again when it is placed. See [family placement](../blocks/Tuff.md#placement-and-properties) for shared behavior.

## Notes

The exact item and block ID is `minecraft:tuff_brick_wall`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/tuff_brick_wall.json
[cut-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_wall_from_polished_tuff_stonecutting.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_wall_from_tuff_stonecutting.json
[cut-tuff-bricks]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_brick_wall_from_tuff_bricks_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/tuff_brick_wall.json
[wall-tag]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/tags/block/walls.json
[walls]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L192

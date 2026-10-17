# Tuff Wall

**Tuff Wall** (`minecraft:tuff_wall`) places the ordinary Tuff wall form in the [family variants table](../blocks/Tuff.md#variants), useful for edging, posts and barriers. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **6 Tuff in two full rows → 6 Tuff Walls**, or stonecut **1 Tuff → 1 Tuff Wall**. Both give one wall item per full block; stonecutting allows single-item batches. [Wall craft][craft] · [Stonecutting][cut-tuff]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Adjacent tagged walls, suitable sturdy faces, Iron Bars and aligned Fence Gates determine its joining arms. Blocks above can change the post and arm shape. Tuff Wall is included in the bundled walls tag. [Connection callback][walls] · [Wall membership][wall-tag] · [Shared wall controls](../blocks/Stone.md#placing-shaped-blocks)

## Behavior

This wall can be waterlogged. Its shape is recalculated from neighbors when placed, and collection returns the ordinary Tuff Wall item. Use the [family placement guide](../blocks/Tuff.md#placement-and-properties) for the common construction rules.

## Notes

The exact item and block ID is `minecraft:tuff_wall`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/tuff_wall.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/tuff_wall_from_tuff_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/tuff_wall.json
[wall-tag]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/tags/block/walls.json
[walls]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L192

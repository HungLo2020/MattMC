# Polished Tuff

**Polished Tuff** (`minecraft:polished_tuff`) is the smooth-finished full block in the [Tuff family](../blocks/Tuff.md#variants). It also serves as the crafting material for Tuff Bricks and polished shapes. [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Craft **4 Tuff in a 2 × 2 square → 4 Polished Tuff**, or stonecut **1 Tuff → 1 Polished Tuff**. Both routes preserve the block count; the Stonecutter lets you work one block at a time. Use the [Tuff item guide](Tuff.md#obtaining-and-use) for the starting material. [Crafting][craft] · [Direct stonecutting][cut-tuff]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Place it as a full decorative block, or keep it as material for [Tuff Bricks](TuffBricks.md), [Polished Tuff Stairs](PolishedTuffStairs.md), [Slabs](PolishedTuffSlab.md) and [Walls](PolishedTuffWall.md). Compare the [family crafting](../blocks/Tuff.md#crafting) and [stonecutting routes](../blocks/Tuff.md#stonecutting) before choosing a finish.

## Behavior

The placed full block has no selectable facing or pillar axis. It stays in place when support beneath it is removed. The checked recipe set has no reverse stonecutting conversion to raw Tuff. [Block registration][blocks] · [Support rule][block-default] · [Conversion limits](../blocks/Tuff.md#stonecutting)

## Notes

The exact item and block ID is `minecraft:polished_tuff`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[block-default]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L310
[blocks]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/Blocks.java#L6001-L6030
[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/polished_tuff.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/polished_tuff_from_tuff_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/polished_tuff.json

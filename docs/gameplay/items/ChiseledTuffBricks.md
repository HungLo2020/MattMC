# Chiseled Tuff Bricks

**Chiseled Tuff Bricks** (`minecraft:chiseled_tuff_bricks`) is the chiseled brick-pattern full block in the [Tuff family](../blocks/Tuff.md#variants). It is a different item from [Chiseled Tuff](ChiseledTuff.md). [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Stack **2 Tuff Brick Slabs vertically → 1 Chiseled Tuff Bricks**. Alternatively, stonecut **1 Tuff, 1 Polished Tuff or 1 Tuff Bricks → 1 Chiseled Tuff Bricks**. Ordinary Tuff Slabs belong to the other chiseled recipe. [Brick-slab recipe][craft] · [From Tuff][cut-tuff] · [From Polished Tuff][cut-polished-tuff] · [From Tuff Bricks][cut-tuff-bricks]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Place it for patterned brick details. Direct cutting from raw Tuff avoids first making Polished Tuff, bricks and brick slabs. It is not a Stonecutter input in the checked recipe set, so choose the finish before converting supplies. [Stonecutting choices and limits](../blocks/Tuff.md#stonecutting)

## Behavior

The block has **no selectable facing or pillar axis**, and its registry does not provide chiseled brick stairs, slabs or walls. Removing a supporting block does not make it fall. [Full-block registration][blocks] · [Support rule][block-default] · [Family placement](../blocks/Tuff.md#placement-and-properties)

## Notes

The exact item and block ID is `minecraft:chiseled_tuff_bricks`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[block-default]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L310
[blocks]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/Blocks.java#L6001-L6030
[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/chiseled_tuff_bricks.json
[cut-polished-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_tuff_bricks_from_polished_tuff_stonecutting.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_tuff_bricks_from_tuff_stonecutting.json
[cut-tuff-bricks]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_tuff_bricks_from_tuff_bricks_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/chiseled_tuff_bricks.json

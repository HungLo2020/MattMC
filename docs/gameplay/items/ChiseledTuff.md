# Chiseled Tuff

**Chiseled Tuff** (`minecraft:chiseled_tuff`) is a decorative full block, separate from [Chiseled Tuff Bricks](ChiseledTuffBricks.md). Both are shown in the [Tuff variants](../blocks/Tuff.md#variants). [Ordinary item binding][item] · [Registration helper][factory]

## Obtaining

Stack **2 Tuff Slabs vertically → 1 Chiseled Tuff**, or stonecut **1 raw Tuff → 1 Chiseled Tuff**. The slab recipe requires the ordinary Tuff Slab item; Polished Tuff Slabs and Tuff Brick Slabs are not substitutes. [Exact slab recipe][craft] · [Direct cut][cut-tuff]

Use an **unbroken pickaxe**, including Wood, to recover **one matching item**. Hand breaking fails the correct-tool requirement. Silk Touch is unnecessary and Fortune adds no multiplier. [Exact loot][loot] · [Tool and harvesting rules](../blocks/Tuff.md#obtaining-and-mining)

## Usage

Use it as a patterned full block for accents and trim. It is a finished output: the checked Stonecutter recipes do not accept Chiseled Tuff as an input to recover raw Tuff or make other shapes. [Family conversion table](../blocks/Tuff.md#stonecutting)

## Behavior

Placement has **no selectable facing or pillar axis**. It remains placed when support is removed, and no chiseled stair/slab/wall variants are registered. The [family placement guide](../blocks/Tuff.md#placement-and-properties) explains these full-block limits. [Ordinary Block registration][blocks] · [Support rule][block-default]

## Notes

The exact item and block ID is `minecraft:chiseled_tuff`. Follow [Tuff variants](../blocks/Tuff.md#variants), [crafting](../blocks/Tuff.md#crafting), [stonecutting](../blocks/Tuff.md#stonecutting) and [placement](../blocks/Tuff.md#placement-and-properties) for the shared family details. [Items](Items.md)

Source-reviewed at `a3c151db954d436822a063bd7eb6f2684d1a9208` on 2026-10-02. Recipes, complete loot, ordinary item binding and the relevant block callbacks were checked. No gameplay crafting, harvesting, placement or Water test was run.

[block-default]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L310
[blocks]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/level/block/Blocks.java#L6001-L6030
[craft]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/crafting/chiseled_tuff.json
[cut-tuff]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/recipe/stonecutting/chiseled_tuff_from_tuff_stonecutting.json
[factory]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L2743-L2781
[item]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/java/net/minecraft/world/item/Items.java#L96-L109
[loot]: https://github.com/HungLo2020/MattMC/blob/a3c151db954d436822a063bd7eb6f2684d1a9208/src/main/resources/data/minecraft/loot_table/blocks/chiseled_tuff.json

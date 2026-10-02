# Brown Mushroom

**Brown Mushroom** (`minecraft:brown_mushroom`) is a placeable small mushroom and crafting ingredient. It is not directly edible. [Small mushroom items] · [Plain block item registration]

## Obtaining

Break a small Brown Mushroom to collect **one**, using any tool or your hand. Ordinary harvesting of a huge Brown Mushroom Block can return **zero, one, or two**; Silk Touch instead returns that building block. [The family guide](../blocks/Mushrooms.md#checked-acquisition-routes) covers verified natural patches, huge mushrooms, and Mooshroom shearing. [brown_mushroom loot] · [brown_mushroom_block loot]

## Usage

Plant it for [small-mushroom spreading](../blocks/Mushrooms.md#small-mushroom-spreading), use Bone Meal for [huge growth](../blocks/Mushrooms.md#growing-a-huge-mushroom), or put it in a [Flower Pot](../blocks/FlowerPot.md). Its [food-recipe connections](../blocks/Mushrooms.md#crafting-food-and-decoration) include Mushroom Stew, Rabbit Stew, and Suspicious Stew. Brown Mushroom is the color required by [Fermented Spider Eye](FermentedSpiderEye.md#crafting); Red Mushroom does not substitute. [fermented_spider_eye recipe]

## Behavior

Mycelium, Podzol, Crimson Nylium, and Warped Nylium support it at any light level. Other support must be solid-rendering, with **raw brightness below 13** at the mushroom. Huge growth has a narrower ground requirement than small-plant survival. Accepted Survival Bone Meal uses consume one meal and have a **40% growth-attempt chance**, with further placement checks. [Small mushroom support] · [Mushroom support tag] · [Huge mushroom height and clearance] · [Huge growth and recovery] · [Bone Meal consumption]

The small Brown Mushroom emits **light level 1**. It has no collision or waterlogged form; incoming water can wash it away and drop the small mushroom. [Small mushroom registry] · [Water occupancy] · [Water replacement] · [Water drops]

## Notes

The exact item and block ID is `minecraft:brown_mushroom`. No bundled crafting or smelting recipe produces it. Detailed support, spread, huge-cap clearance, and loot probabilities belong to [Mushrooms](../blocks/Mushrooms.md#brown-mushroom).

## Sources and verification

Source-reviewed on **2026-10-02** at `1d7b3bbf88ccad2196a6de69317d934e7c3aeced`. Checked the matching registration, complete small/cap loot tables, recipe references, and active placement, spread, Bone Meal, and water callbacks. No gameplay test was run.

[Small mushroom items]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Items.java#L360-L361
[Plain block item registration]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Items.java#L2750-L2783
[brown_mushroom loot]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/brown_mushroom.json
[brown_mushroom_block loot]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/brown_mushroom_block.json
[fermented_spider_eye recipe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/fermented_spider_eye.json
[Small mushroom support]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/MushroomBlock.java#L77-L89
[Mushroom support tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/mushroom_grow_block.json
[Huge mushroom height and clearance]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/levelgen/feature/AbstractHugeMushroomFeature.java#L18-L95
[Huge growth and recovery]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/MushroomBlock.java#L91-L119
[Bone Meal consumption]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L81
[Small mushroom registry]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/Blocks.java#L1060-L1084
[Water occupancy]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L401-L427
[Water replacement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/material/FlowingFluid.java#L268-L277
[Water drops]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/material/WaterFluid.java#L87-L91

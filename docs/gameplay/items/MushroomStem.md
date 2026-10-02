# Mushroom Stem

**Mushroom Stem** (`minecraft:mushroom_stem`) is the full building block shared by huge Brown and Red Mushrooms. It is a plain block item, not a food item or a small mushroom to plant. [Huge block items] · [Plain block item registration] · [Brown huge feature] · [Red huge feature]

## Obtaining

Use **Silk Touch level I or above** to recover one stem block. Without Silk Touch it drops **nothing**; Fortune and Shears have no special branch. The family guide explains [huge growth](../blocks/Mushrooms.md#growing-a-huge-mushroom) and [checked acquisition routes](../blocks/Mushrooms.md#checked-acquisition-routes). There is no bundled crafting or smelting recipe for this item. [mushroom_stem loot]

## Usage

Use it as a full decorative block, or compost it with a **65%** ordinary chance to raise the level. The first accepted item in an empty composter raises the level automatically. A stem cannot replace a small mushroom in the checked stew recipes. [Full block shape] · [Small and stem composting] · [Composter success] · [mushroom_stew recipe] · [rabbit_stew_from_brown_mushroom recipe] · [rabbit_stew_from_red_mushroom recipe]

## Behavior

Stems have hardness **0.2**, are axe-efficient, and have no tool-tier requirement; Silk Touch controls the item drop. Their six faces respond to adjacent **Mushroom Stems**, rather than a log-style axis. A side touching another stem becomes an interior face, and removing that neighbor does not turn it back into an exterior face. Normal Silk Touch drops do not preserve the old face flags. See [mining and building faces](../blocks/Mushrooms.md#mining-and-building-faces) before shaping a wall. [Huge block registry] · [Axe mining tag] · [Harvest gate] · [Huge face placement] · [Huge face properties] · [Generic shape update] · [mushroom_stem loot] · [mushroom_stem faces]

## Notes

The exact item and block ID is `minecraft:mushroom_stem`. This full block has no waterlogged state or dependence on soil below it. [Full block shape] · [Huge face properties] The shared [Mushrooms guide](../blocks/Mushrooms.md#mushroom-stem) covers caps, planting stock, and growth; [Crimson and Warped Fungi](../blocks/NetherFungi.md) have separate Nether growth mechanics.

## Sources and verification

Source-reviewed on **2026-10-02** at `1d7b3bbf88ccad2196a6de69317d934e7c3aeced`. Checked the block/item registration, complete loot table, mining tag, face-state placement/update/model rules, huge features, and composting. No gameplay test was run.

[Huge block items]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Items.java#L550-L552
[Plain block item registration]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/item/Items.java#L2750-L2783
[Brown huge feature]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/configured_feature/huge_brown_mushroom.json
[Red huge feature]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/worldgen/configured_feature/huge_red_mushroom.json
[mushroom_stem loot]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/loot_table/blocks/mushroom_stem.json
[Full block shape]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[Small and stem composting]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L137-L139
[Composter success]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L319
[mushroom_stew recipe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/mushroom_stew.json
[rabbit_stew_from_brown_mushroom recipe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_brown_mushroom.json
[rabbit_stew_from_red_mushroom recipe]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_red_mushroom.json
[Huge block registry]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/Blocks.java#L2292-L2306
[Axe mining tag]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[Harvest gate]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Huge face placement]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/HugeMushroomBlock.java#L33-L67
[Huge face properties]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/HugeMushroomBlock.java#L89-L92
[Generic shape update]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L150-L161
[mushroom_stem faces]: https://github.com/HungLo2020/MattMC/blob/1d7b3bbf88ccad2196a6de69317d934e7c3aeced/src/main/resources/assets/minecraft/blockstates/mushroom_stem.json

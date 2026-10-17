# Rose Bush

**Rose Bush** (`minecraft:rose_bush`) is a two-block flower you can collect, replant, or craft into Red Dye. The [flower guide](../blocks/Flowers.md#rose-bush) compares its uses with the other species. [Item registration][] · [Block registration][]

## Obtaining

**Forest** can generate natural **Rose Bush patches**. Break either half of a complete plant by hand to collect **one Rose Bush item**, not one per half. [Forest biome][] · [Forest placement][] · [Forest patches][] · [Flower loot][] · [Paired collection][] · [Hand collection][]

Once planted, use **one [Bone Meal](BoneMeal.md) on either half** to drop **one extra Rose Bush item** while keeping the original plant. Pick up that loose item. Forest’s natural tall-flower patches do not make Rose Bush available from Bone Meal on Grass Block; start with a collected plant. See [harvesting and propagation](../blocks/Flowers.md#harvesting-and-propagation) and the [Grass Block distinction](../blocks/Flowers.md#bone-meal-on-grass-block). [Tall-flower duplication][] · [Bone Meal dispatch][] · [Biome flower filter][]

## Usage

Craft **one Rose Bush into two Red Dyes** in a crafting grid. Keep a planted flower if you want to replace the one consumed by crafting. [Dye recipe][] · [Ingredient consumption][]

This tall flower does not substitute for the eligible small flower in the [compared Suspicious Stew recipes](SuspiciousStew.md#obtaining). See the [tall-flower comparison](../blocks/Flowers.md#tall-flower-variants) for the full dye choices.

## Behavior

One item places the **lower and upper halves together**. Plant it on Grass Block, Dirt, or another [accepted flower soil](../blocks/Flowers.md#planting-and-support), leaving a placeable cell above within the build height. Removing the support or either half removes the unsupported remainder. [Paired placement][] · [Soil support][] · [Soil tag][]

## Notes

The item yields above assume block drops are enabled. Silk Touch and Fortune do not increase the flower’s normal item yield; blasts can lose the drop. Crafting consumes the input flower. [Flower loot][] · [Block-drop rule][] · [Ingredient consumption][]

[Flower uses with Bees and composters](../blocks/Flowers.md#bees-pots-and-other-uses) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked this variant’s registrations, complete loot and recipe data, the selected natural/renewal routes, and the relevant placement, harvesting, Bone Meal callers. These are checked acquisition examples; other biome, structure, trade, or loot routes remain unreviewed here. Data packs and game rules can change the results. No in-game acquisition, crafting, placement, harvesting, or propagation test was run.

[Item registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L714
[Block registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L3310-L3321
[Forest biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/forest.json#L1-L207
[Forest placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/placed_feature/forest_flowers.json#L1-L32
[Forest patches]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/configured_feature/forest_flowers.json#L1-L148
[Flower loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/rose_bush.json#L1-L30
[Paired collection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L101-L134
[Hand collection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[Tall-flower duplication]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/TallFlowerBlock.java#L13-L38
[Bone Meal dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L78
[Biome flower filter]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/BiomeGenerationSettings.java#L47-L66
[Dye recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/red_dye_from_rose_bush.json#L1-L12
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
[Paired placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L40-L87
[Soil support]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Soil tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[Block-drop rule]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Block.java#L410-L415

# Sugar Cane

**Sugar Cane** (`minecraft:sugar_cane`) is a renewable source of [Sugar and Paper](../items/SugarCane.md#crafting). Plant it beside water and harvest the upper blocks while leaving the base to grow again.

## Finding the first plants

Bundled biome features place Sugar Cane in many biomes, including Plains, River, Beach, and Jungle. [Desert][desert], [Swamp][swamp], and [Badlands][badlands] use their own placement variants. Look along suitable water edges: the patch checks for empty space, valid plant support, and adjacent Water or Flowing Water at the supporting block's height. These are generation opportunities, not a guarantee of a patch beside every pool. [Biome examples][plains] · [River][river] · [Beach][beach] · [Jungle][jungle] · [Patch conditions][patch]

The patch chooses a column height of **2–4 blocks**, with obstructions able to shorten it. A naturally generated four-block plant is possible even though ordinary growth stops at three. [Patch configuration][patch] · [Column placement][column]

## Planting and water

Use the [Sugar Cane item](../items/SugarCane.md) to place a plant. The bottom block must stand on a block in the bundled Dirt or Sand tags, with **Water fluid or Frosted Ice directly beside that supporting block** on one of its four horizontal sides. Diagonal water and water at the plant's own height do not satisfy this check. Another Sugar Cane block is also valid support. [Placement][placement] · [Survival check][cane]

Useful supports include Dirt, Grass Block, Coarse Dirt, Podzol, Sand, and Red Sand. The tags also include Moss Block, Pale Moss Block, Mud, Muddy Mangrove Roots, and Suspicious Sand. **Farmland is not in either support tag.** Ordinary Ice is not the Frosted Ice exception. A neighboring waterlogged block can qualify through its Water fluid. [Dirt tag][dirt] · [Sand tag][sand] · [Survival check][cane]

Keep that support and water in place. If a neighbor update leaves the plant unsupported, a scheduled check destroys it and drops its resources. Removing the bottom Cane can therefore bring down the blocks above it. [Support updates][cane]

## Growth and harvesting

- Keep the block above the growing tip empty
- A tip below the **three-block column limit** counts through internal ages **0–15**. The next eligible random tick at age 15 adds one block above and resets the old tip's age
- There is **no brightness requirement** in the Cane growth or survival checks
- **Bone Meal does not grow Sugar Cane** in this implementation; the block does not implement the Bone Meal target interface

These are random ticks, not a fixed number of ordinary game ticks. This page does not promise a time to the next harvest. [Growth][cane] · [Random-tick caller][ticks] · [Bone Meal target check][bonemeal]

Sugar Cane breaks instantly and does not require a particular harvesting tool. An ordinary break drops **one Sugar Cane per block**; its loot has an explosion-survival condition. Break above the bottom block to leave a renewable plant, or replant a collected item if the whole column is removed. [Block registration][registry] · [Drops][loot]

MattMC's empty-hand harvest/reset and **3 × 3 hoe harvest** belong to the separate Wheat-style crop implementation. Sugar Cane does not inherit those controls. [Crop controls][crop] · [Cane implementation][cane]

Related: [Sugar Cane item and crafting](../items/SugarCane.md) · [Pumpkin and Melon farming](PumpkinAndMelon.md) · [Wheat](Wheat.md) · [Blocks](Blocks.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. Registration, current callbacks, biome-feature wiring, and bundled loot were checked. No in-game growth, generation, harvesting, or Bone Meal test was run. World settings and data packs can change the opportunities and drops described here.

The [world-generation loader][worldgen] reads biome and feature data used by [biome decoration][decoration]. The current [block-state dispatch][dispatch] calls Sugar Cane's growth, survival, and update callbacks. This establishes source wiring, not a measured in-game harvest rate.

[plains]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/plains.json
[river]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/river.json
[beach]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/beach.json
[jungle]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/jungle.json
[patch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/configured_feature/patch_sugar_cane.json
[column]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/feature/BlockColumnFeature.java
[placement]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BlockItem.java#L110-L137
[cane]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/SugarCaneBlock.java
[dirt]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/dirt.json
[sand]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/block/sand.json
[bonemeal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BoneMealItem.java#L66-L85
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/sugar_cane.json
[registry]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/Blocks.java#L1965-L1969
[crop]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/CropBlock.java#L193-L249
[worldgen]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L106
[decoration]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L308-L400
[dispatch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L765-L837
[ticks]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/level/ServerLevel.java#L487-L504
[desert]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/desert.json
[swamp]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/swamp.json
[badlands]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/badlands.json

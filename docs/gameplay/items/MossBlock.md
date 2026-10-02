# Moss Block

Moss Block is the inventory form of **`minecraft:moss_block`**. Collect it by hand or with a hoe, or obtain a starting supply from Lush Caves or a matching Wandering Trader offer. Silk Touch is unnecessary. The trader's configured offer is 1 Emerald for 2 blocks, with five uses when selected. [Item][items] · [Loot][loot-moss_block] · [Trade][trades]

Use Bone Meal on the placed block with air above it to attempt moss conversion and vegetation. It does not spread just by waiting. See [Moss and Pale Moss](../blocks/MossAndPaleMoss.md#moss-blocks-and-spreading) for valid terrain and failure conditions. [Callback][feature-block]

Two Moss Blocks side by side craft three Moss Carpets. One block plus Cobblestone or Stone Bricks crafts the corresponding mossy block. There is no recipe creating Moss Block itself. [Carpet][recipe-moss_carpet] · [Cobblestone recipe][recipe-mossy_cobblestone_from_moss_block] · [Brick recipe][recipe-mossy_stone_bricks_from_moss_block]

For placement, water, composting, and the pale variants, use the [canonical block guide](../blocks/MossAndPaleMoss.md).

[items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L359-L379
[loot-moss_block]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/moss_block.json
[trades]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L810-L827
[feature-block]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/BonemealableFeaturePlacerBlock.java
[recipe-moss_carpet]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/moss_carpet.json
[recipe-mossy_cobblestone_from_moss_block]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/mossy_cobblestone_from_moss_block.json
[recipe-mossy_stone_bricks_from_moss_block]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/recipe/crafting/mossy_stone_bricks_from_moss_block.json

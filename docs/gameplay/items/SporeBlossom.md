# Spore Blossom

Spore Blossom is the item for **`minecraft:spore_blossom`**. The active Lush Caves generation route can place it beneath cave ceilings. Break it by hand or with a tool to recover one; Shears and Silk Touch are unnecessary. [Item][items] · [Biome placement][biome-lush_caves] [placed-spore_blossom] · [Loot][loot-spore_blossom]

Place it beneath a ceiling that supports its center, away from Water. It emits decorative particles but has no Bone Meal growth or duplication callback, and there is no crafting recipe. [Block behavior][spore]

MattMC includes the item in Bee food and the placed block in the Bee attraction tag. It is also compostable. Follow the [Spore Blossom guide](../blocks/HangingRootsAndSporeBlossom.md#spore-blossom) and [Bees](../mobs/Bee.md) for the checked interactions. [Bee food][bee-food] [bee] · [Attraction][bee-attractive] [bee-target] · [Composting][compost]

[items]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/item/Items.java#L359-L379
[biome-lush_caves]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/biome/lush_caves.json
[placed-spore_blossom]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/worldgen/placed_feature/spore_blossom.json
[loot-spore_blossom]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/loot_table/blocks/spore_blossom.json
[spore]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/SporeBlossomBlock.java
[bee-food]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/item/bee_food.json
[bee]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/animal/Bee.java#L587-L590
[bee-attractive]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/resources/data/minecraft/tags/block/bee_attractive.json
[bee-target]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/entity/animal/Bee.java#L668-L677
[compost]: https://github.com/HungLo2020/MattMC/blob/88d85ee594bc770fa362129690eadb127272dc20/src/main/java/net/minecraft/world/level/block/ComposterBlock.java

# Sweet Berry Bush

Use [Sweet Berries](../items/SweetBerries.md) to plant a bush, then pick it once it reaches age 2 or 3. **Picking preserves the bush and resets it to age 1**; waiting for age 3 gives one extra berry. Keep bushes away from walking routes because most moving living entities are slowed and can take damage. [Planting item][berry-item] · [Harvest callback][berry-harvest] · [Picking loot][berry-pick-loot] · [Contact][berry-harm]

## Finding and planting

The bundled Taiga, Old Growth Pine Taiga, and Old Growth Spruce Taiga biomes include the common berry patch; Snowy Taiga includes the rarer version. Both select a patch that places **age-3 bushes on Grass Block with air above**. This identifies a natural source, not a guaranteed bush in every chunk. [Taiga][taiga-biome] · [Pine taiga][pine-taiga-biome] · [Spruce taiga][spruce-taiga-biome] · [Snowy taiga][snowy-taiga-biome] · [Common placement][berries-common] · [Rare placement][berries-rare] · [Patch][berries-feature]

Pick a wild bush and use one berry on suitable ground. For player planting, the accepted support is broader than the generation patch: **Grass Block, Dirt, Coarse Dirt, Podzol, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, Muddy Mangrove Roots, or Farmland**. Sand and Dirt Path are not in this support set. The placement consumes one berry in ordinary Survival use. [Plant support][plant-support] · [Soil tag][dirt-tag] · [Placement consumption][placement]

There is no irrigation requirement and no light test for the bush's continued survival. Losing valid ground removes the bush. A bush does not keep dry Farmland from becoming Dirt, but Dirt still supports it; ordinary soil is sufficient for a berry plot. [Support][plant-support] · [Farmland maintenance tag][maintains-farmland] · [Dry Farmland][farmland-moisture] · [Maintenance check][farmland-maintenance]

## Growing and picking

Bushes have **ages 0–3**. An immature bush advances one stage when its random tick passes a **1-in-5** roll and the raw brightness in the cell **above the bush is at least 9**. Farmland moisture and nearby bush arrangements are not part of this growth formula. Bone Meal advances one stage, with no extra success roll or brightness test in the Bone Meal callback. Mature bushes stop random ticking. [Natural growth][berry-growth] · [Bone Meal][berry-bonemeal]

| Age | Empty-hand use | Breaking, before Fortune or explosions |
| --- | --- | --- |
| 0 | No berries | No berries |
| 1 | No berries | No berries |
| 2 | **1–2 berries**, resets to age 1 | **1–2 berries**, removes bush |
| 3 | **2–3 berries**, resets to age 1 | **2–3 berries**, removes bush |

Picking calls the separate `minecraft:harvest/sweet_berry_bush` interaction loot table with **no tool supplied**. That table has no Fortune bonus. Breaking uses the block loot table instead, which adds a uniformly random **0 through the tool's Fortune level** berries at ages 2 and 3, then applies explosion decay where relevant. Breaking a newly planted or just-picked bush loses the planted berry; it does not refund one. [Harvest caller][berry-harvest] · [Named tables][loot-names] · [Interaction loot execution][interact-loot] · [Picking loot][berry-pick-loot] · [Breaking loot][berry-break-loot] · [Fortune formula][fortune-bonus]

Use an empty hand for deliberate picking. On an immature bush, holding Bone Meal passes the interaction to the item's growth action, including at harvestable age 2. This bush has its own picking callback and no same-type 3 × 3 hoe harvest. [Interaction routing][berry-harvest] · [Bone Meal execution][bonemeal-use]

## Contact damage and Foxes

Living entities other than **Foxes and Bees** receive the bush's movement slowdown at every age. At ages **1–3**, horizontal movement meeting the checked threshold can also deal **1 damage point** on the server. Age 0 still slows those entities but does not damage them. Standing still does not meet this movement damage check; this is not a promise of a fixed damage rate. [Contact callback][berry-harm]

Foxes have a registered berry-picking goal. They target bushes at age 2 or 3 and can pick after waiting at the target, provided **mob griefing is enabled**. The Fox uses a separate random calculation with the same 1–2 or 2–3 total: if its mouth is empty it keeps one berry there, drops the rest, and resets the bush to age 1. Fence off a plot if you want to reserve the harvest. [Goal registration][fox-goal] · [Fox picking][fox-berries]

## Uses and exact IDs

- Eating Sweet Berries restores **2 hunger points** and supplies **0.4 saturation** before normal food caps. A successful planting action takes precedence when using this edible block item on valid ground. [Food registration][berry-item] · [Food values][berry-food] · [Saturation builder][food-builder] · [Saturation formula][food-saturation] · [Placement versus eating][edible-placement]
- Sweet Berries are accepted Fox food. [Food check][fox-food] · [Food tag][fox-food-tag]
- Composting gives a **30%** chance to add one level after the first level; the first accepted item in an empty Composter succeeds automatically. [Value][berry-compost] · [Roll][compost-roll]

The block is `minecraft:sweet_berry_bush`; its planting, food, and harvested item is `minecraft:sweet_berries`. [Block registration][berry-registration] · [Item registration][berry-item]

## Related pages

- [Sweet Berries](../items/SweetBerries.md)
- [Fox](../mobs/Fox.md)
- [Farmland](Farmland.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled.

[berry-item]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L2449-L2451
[berry-harvest]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/SweetBerryBushBlock.java#L100-L134
[berry-pick-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/harvest/sweet_berry_bush.json#L1-L53
[berry-harm]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/SweetBerryBushBlock.java#L81-L98
[taiga-biome]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/biome/taiga.json#L72-L86
[pine-taiga-biome]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/biome/old_growth_pine_taiga.json#L88-L101
[spruce-taiga-biome]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/biome/old_growth_spruce_taiga.json#L88-L101
[snowy-taiga-biome]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/biome/snowy_taiga.json#L72-L86
[berries-common]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/placed_feature/patch_berry_common.json#L1-L19
[berries-rare]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/placed_feature/patch_berry_rare.json#L1-L19
[berries-feature]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/worldgen/configured_feature/patch_berry_bush.json#L1-L47
[plant-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[dirt-tag]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[placement]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BlockItem.java#L48-L84
[maintains-farmland]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/maintains_farmland.json#L1-L15
[farmland-moisture]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L96-L107
[farmland-maintenance]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L128-L139
[berry-growth]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/SweetBerryBushBlock.java#L34-L79
[berry-bonemeal]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/SweetBerryBushBlock.java#L141-L155
[loot-names]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L100-L106
[interact-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Block.java#L253-L285
[berry-break-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/sweet_berry_bush.json#L1-L87
[fortune-bonus]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java#L156-L171
[bonemeal-use]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[fox-goal]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/Fox.java#L181-L185
[fox-berries]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/Fox.java#L914-L970
[berry-food]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/food/Foods.java#L39-L43
[food-builder]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[food-saturation]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[edible-placement]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L45
[fox-food]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/Fox.java#L577-L580
[fox-food-tag]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/item/fox_food.json#L1-L6
[berry-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L93-L109
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317
[berry-registration]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L5447-L5451

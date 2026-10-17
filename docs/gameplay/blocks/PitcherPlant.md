# Pitcher Plant and Pitcher Crop

Plant a [Pitcher Pod](../items/PitcherPod.md) on [Farmland](Farmland.md), give the crop room to become two blocks tall, and break it at **age 4** for **one Pitcher Plant**. The mature crop remains `minecraft:pitcher_crop`; collecting and replanting its drop creates the separate decorative `minecraft:pitcher_plant`. Each mature harvest spends the pod, so plan a continuing Sniffer supply. [Planting item][ancient-seed-items] · [Growth][pitcher-growth] · [Crop drops][pitcher-crop-loot] · [Decorative block registration][ancient-crop-registration] · [Plant item][flower-items]

## Getting pods

Adult Sniffers dig up pods from the same working acquisition route as [Torchflower Seeds](Torchflower.md#sniffer-seeds-and-pods). A successful execution of `minecraft:gameplay/sniffer_digging` chooses **one pod or one seed at equal default weights**, giving a **50% pod chance per pool execution**. The Sniffer's active digging tick calls the seed-drop method, which resolves this registered gift table on the server; the chance is not a fixed-time promise. [Digging caller][sniffer-tick] · [Drop method][sniffer-conditions] · [Table registration][loot-names] · [Table][sniffer-loot] · [Default weights][loot-weights] · [Gift-table execution][gift-loot-context] · [Loaded table][gift-loot-execution]

Use accessible ground in the **Sniffer Diggable Block** tag and leave the animal able to search and dig. The shared [Sniffer seeds and pods section](Torchflower.md#sniffer-seeds-and-pods) lists the exact ground, movement conditions, and cooldown. The planted crop returns a pod only while immature; there is no extra-pod branch in its mature loot. [Digging conditions][sniffer-conditions] · [Ground tag][sniffer-ground] · [Crop loot][pitcher-crop-loot]

Pods also activate MattMC's [Primordial Caves portal conversion](../items/PitcherPod.md#portal-conversion). Reserve that pod before farming: an age-4 harvest gives a plant, which is not the portal ingredient. [Portal item check][portal-pod] · [Crop loot][pitcher-crop-loot]

## Planting and growth

The crop needs **Farmland** and **raw brightness at least 8** at its lower half to survive. It begins as one lower block at age 0. **Ages 0–2 occupy one block; ages 3–4 require both lower and upper halves.** Growth into the tall stages accepts only air or an existing Pitcher Crop in the upper cell, so leave that space clear. [Crop support][pitcher-survival] · [Light definition][crop-light-ravager] · [Tall-stage growth and space checks][pitcher-growth] · [Placement survival][placement-survival]

Only an immature lower half receives natural random growth ticks. It uses the ordinary crop growth-factor calculation: hydrated Farmland below and around it improves the roll, and same-crop crowding can halve that factor. A successful roll advances **one stage** if the light and upper-space checks allow it. **The Pitcher Crop's own growth gate uses brightness 8, not Wheat's natural-growth threshold of 9.** [Tick and growth][pitcher-growth] · [Shared factor][crop-speed] · [Shared light definition][crop-light-ravager]

Bone Meal advances **one stage**, with no extra success roll, when the same light and space checks permit growth. You may apply it to either half of a tall crop; the operation resolves and grows the lower half, then updates the upper half. A blocked age-2 crop or a fully mature age-4 crop is not a valid target. [Bone Meal target and lower-half lookup][pitcher-bonemeal] · [Growth checks][pitcher-growth]

The growing crop maintains dry Farmland, but hydration still improves natural growth. Protect the plot from trampling as described on [Farmland](Farmland.md). Ravagers can destroy the crop on contact while mob griefing is enabled. [Maintenance tag][maintains-farmland] · [Maintenance check][farmland-maintenance] · [Ravager contact][pitcher-ravager]

## Harvesting the crop

| Crop stage | Ordinary result from the complete crop |
| --- | --- |
| Ages 0–3 | **1 Pitcher Pod** |
| Age 4 | **1 Pitcher Plant**, no pod |

Only the **lower half** has loot entries. Breaking either half of an intact tall crop removes the pair through its paired-block updates; it does not give two drops. The shared double-plant mining path handles player breakage without a second normal block-mining drop. Neither the crop nor decorative plant table adds a Fortune or Silk Touch bonus, and explosion decay can reduce recovery. [Crop loot][pitcher-crop-loot] · [Paired-block updates][double-plant-support] · [Tall-crop update dispatch][pitcher-survival] · [Double-plant mining][double-plant-mining] · [Plant loot][pitcher-plant-loot]

**Break and replant with another pod.** Pitcher Crop extends the double-plant family, not the ordinary CropBlock family, and does not implement MattMC's use-to-harvest or 3 × 3 hoe harvesting callbacks. Right-clicking a mature crop does not collect and reset it through those controls. [Crop class][pitcher-class] · [Parent class][double-plant-class] · [Default interaction][default-use] · [Ordinary crop controls][crop-harvest]

## Placing the decorative plant

One Pitcher Plant item places both halves, provided the upper cell is replaceable and within the build height. The lower half accepts **Grass Block, Dirt, Coarse Dirt, Podzol, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, Muddy Mangrove Roots, or Farmland**. The decorative plant has no crop-age, light, hydration, or natural-growth requirement. Its upper half requires the matching lower half; loss of the ground or either half removes the unsupported remainder. [Plant registration][ancient-crop-registration] · [Two-block placement and survival][double-plant-support] · [Soil support][plant-support] · [Soil tag][dirt-tag]

Break a complete decorative plant to recover **one Pitcher Plant item**. Applying Bone Meal directly does **not** duplicate it: this registration uses DoublePlantBlock, which does not implement the Bone Meal interface. This differs from the four duplicating [tall flowers](Flowers.md#harvesting-and-propagation). [Plant loot][pitcher-plant-loot] · [Plant class][double-plant-class] · [Registration][ancient-crop-registration] · [Bone Meal dispatch][bonemeal-use]

## Uses and exact IDs

- One Pitcher Plant crafts into **two Cyan Dyes**. [Recipe][cyan-dye]
- Pitcher Pods have a **30%** compost chance and Pitcher Plants an **85%** chance after the first level; the first accepted compostable item in an empty Composter succeeds automatically. [Pod value][seed-compost] · [Plant value][flower-compost] · [Roll][compost-roll]
- The checked Sniffer food tag accepts **Torchflower Seeds**, not Pitcher Pods. [Food check][sniffer-food] · [Food tag][sniffer-food-tag]

| Form | Exact ID |
| --- | --- |
| Growing and mature crop block | `minecraft:pitcher_crop` |
| Decorative plant block and item | `minecraft:pitcher_plant` |
| Planting and portal-conversion item | `minecraft:pitcher_pod` |

[Block registrations][ancient-crop-registration] · [Plant item][flower-items] · [Pod item][ancient-seed-items]

## Related pages

- [Pitcher Pod](../items/PitcherPod.md)
- [Pitcher Plant item](../items/PitcherPlant.md)
- [Torchflower and Torchflower Crop](Torchflower.md)
- [Sniffer](../mobs/Sniffer.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled.

[ancient-seed-items]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L2236-L2237
[pitcher-growth]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L135-L183
[pitcher-crop-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/pitcher_crop.json#L8-L154
[ancient-crop-registration]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L4254-L4275
[flower-items]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L357-L358
[sniffer-tick]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L349-L360
[sniffer-conditions]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L258-L284
[loot-names]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L100-L106
[sniffer-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/gameplay/sniffer_digging.json#L1-L20
[loot-weights]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[gift-loot-context]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1544-L1553
[gift-loot-execution]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1569-L1581
[sniffer-ground]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/sniffer_diggable_block.json#L1-L13
[portal-pod]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L110-L172
[pitcher-survival]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L85-L111
[crop-light-ravager]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L149-L167
[placement-survival]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BlockItem.java#L130-L142
[crop-speed]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L105-L147
[pitcher-bonemeal]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L185-L213
[maintains-farmland]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/maintains_farmland.json#L1-L15
[farmland-maintenance]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L128-L139
[pitcher-ravager]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L119-L133
[double-plant-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L40-L87
[double-plant-mining]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L101-L130
[pitcher-plant-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/pitcher_plant.json#L1-L30
[pitcher-class]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/PitcherCropBlock.java#L32-L50
[double-plant-class]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L26-L38
[default-use]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L202-L210
[crop-harvest]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L193-L251
[plant-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[dirt-tag]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[bonemeal-use]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[cyan-dye]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/cyan_dye_from_pitcher_plant.json#L1-L12
[seed-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L108-L109
[flower-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L178-L184
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317
[sniffer-food]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L430-L433
[sniffer-food-tag]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/item/sniffer_food.json#L1-L5

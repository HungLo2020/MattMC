# Torchflower and Torchflower Crop

Grow [Torchflower Seeds](../items/TorchflowerSeeds.md) on [Farmland](Farmland.md) to obtain a Torchflower for decoration, Orange Dye, or Night Vision Suspicious Stew. **MattMC's mature Torchflower emits light level 7.** The growing crop and the mature flower are separate blocks with different support and harvesting behavior. [Seed registration][ancient-seed-items] · [Crop lifecycle][torchflower-crop] · [Flower registration and light][torchflower-registration] · [Dye][orange-dye] · [Stew][torchflower-stew]

## Sniffer seeds and pods

An adult [Sniffer](../mobs/Sniffer.md) can dig up **one Torchflower Seed or one Pitcher Pod**, each with **50% probability per execution of the bundled digging loot pool**. The table makes one roll between equal default-weight entries; it does not drop both. This is a renewable acquisition route once you have a working Sniffer, not a guarantee of a seed every fixed number of minutes. [Drop callback][sniffer-conditions] · [Digging table][sniffer-loot] · [Default weights][loot-weights] · [Weighted selection][loot-choice] · [Item creation][loot-item]

Provide reachable, previously unexplored digging ground: **Dirt, Grass Block, Podzol, Coarse Dirt, Rooted Dirt, Moss Block, Pale Moss Block, Mud, or Muddy Mangrove Roots**. Farmland and Mycelium are not in this digging tag. The Sniffer must be adult, on the ground, out of water, not riding, not panicking, and not currently tempted for the checked digging conditions. Its sniffing and AI state must also permit digging, so suitable ground alone does not force a dig. [Diggable ground][sniffer-ground] · [Digging checks][sniffer-conditions] · [AI start and continuation][sniffer-ai]

The acquisition is wired into active behavior: entering the digging state schedules the drop at tick +120, the digging tick calls the drop method, and that method uses the registered `minecraft:gameplay/sniffer_digging` gift table through the server's loaded loot registry. A stopped or interrupted dig can miss that scheduled drop. The AI also installs a **9,600-tick sniff cooldown** after a completed timed digging behavior; that cooldown is only part of the overall search-and-dig cycle. [Digging start][sniffer-start] · [Tick caller][sniffer-tick] · [Loot callback][sniffer-conditions] · [Table key][loot-names] · [Gift context][gift-loot-context] · [Loaded table execution][gift-loot-execution] · [AI and cooldown][sniffer-ai]

Torchflower Seeds also tempt and count as food for Sniffers; the checked food tag contains the seeds, **not the flower or Pitcher Pod**. Save seeds for further planting or animal use before converting all of them into flowers. [Temptation][sniffer-tempt] · [Food check][sniffer-food] · [Food tag][sniffer-food-tag]

## Growing the crop

Plant seeds on Farmland with **raw brightness at least 8** at the crop. The actual Torchflower Crop has only **age 0 and age 1**. Its next growth step replaces it with the separate mature `minecraft:torchflower` block; there is no placeable age-2 Torchflower Crop state in this lifecycle. [Placement support and growth][crop-growth] · [Survival light][crop-light-ravager] · [State transition][torchflower-crop] · [Placement survival check][placement-survival]

Natural growth requires raw brightness **at least 9** at the crop. Two thirds of its random ticks reach the ordinary crop growth roll. That roll benefits from hydrated Farmland below and in the surrounding 3 × 3 ground area, while same-crop neighbors in both horizontal axes or on a diagonal halve the growth factor. See [Farmland](Farmland.md) for hydration and trampling. These are random checks, not a fixed growing time. [Torchflower tick gate][torchflower-crop] · [Growth roll][crop-growth] · [Moisture and crowding][crop-speed]

Bone Meal advances the crop **one stage per accepted use**: age 0 → age 1 → mature flower. It does not repeat the natural-growth brightness or random roll. Both the growing crop and the mature flower are in the tag that keeps dry Farmland from reverting to Dirt. Ravagers can destroy the growing crop on contact when mob griefing is enabled. [Bone Meal increment][torchflower-crop] · [Growth application][crop-growth] · [Bone Meal target][crop-bonemeal] · [Farmland tag][maintains-farmland] · [Farmland check][farmland-maintenance] · [Ravager contact][crop-light-ravager]

## Harvesting and displaying the flower

Break the age-0 or age-1 crop to recover **one seed**. Break the mature flower to receive **one Torchflower item**, with no seed returned. Neither checked table has a Fortune or Silk Touch branch; explosions can reduce recovery. **Growing flowers does not multiply seeds.** [Crop loot][torchflower-crop-loot] · [Flower loot][torchflower-loot]

MattMC's ordinary mature-crop use-to-harvest and hoe-area-harvest controls do **not** apply here. The inherited crop callbacks require the effective maximum age of 2, but the Torchflower Crop's valid states stop at 1; reaching maturity has already replaced it with a FlowerBlock. Break the mature flower and plant another seed for another crop. [Age and conversion][torchflower-crop] · [Harvest age gates][crop-harvest] · [Mature flower class][flower-class]

The harvested flower can be replanted on **Grass Block, Dirt, Coarse Dirt, Podzol, Mycelium, Rooted Dirt, Moss Block, Pale Moss Block, Mud, Muddy Mangrove Roots, or Farmland**. Unlike the growing crop, this mature flower's support check has no light or irrigation requirement. It has no crop age and cannot be duplicated by applying Bone Meal directly. It can also go in a [Flower Pot](FlowerPot.md#supported-plants); potted behavior belongs to that guide. [Mature class][flower-class] · [Plant support][plant-support] · [Soil tag][dirt-tag] · [Bone Meal interface check][bonemeal-use] · [Potted registration][potted-torchflower]

## Uses and registered forms

- **Dye:** one Torchflower crafts into **one Orange Dye**. [Recipe][orange-dye]
- **Suspicious Stew:** a Bowl, Brown Mushroom, Red Mushroom, and Torchflower craft one stew carrying **Night Vision for 100 ticks**, or 5 seconds at 20 TPS. The effect belongs to the stew, not contact with the planted flower. [Recipe][torchflower-stew] · [Flower class][flower-class]
- **Composting:** seeds have a **30%** chance and flowers an **85%** chance to raise a partly filled Composter by one level. The first accepted item in an empty Composter succeeds automatically. [Seeds][seed-compost] · [Flowers][flower-compost] · [Roll][compost-roll]

| Form | Exact ID |
| --- | --- |
| Growing block | `minecraft:torchflower_crop` |
| Mature block and matching item | `minecraft:torchflower` |
| Planting item | `minecraft:torchflower_seeds` |

[Crop registration][ancient-crop-registration] · [Mature block registration][torchflower-registration] · [Flower item][flower-items] · [Seed item][ancient-seed-items]

## Related pages

- [Torchflower item](../items/Torchflower.md)
- [Torchflower Seeds](../items/TorchflowerSeeds.md)
- [Pitcher Plant and Pitcher Crop](PitcherPlant.md)
- [Sniffer](../mobs/Sniffer.md)
- [Small and tall flowers](Flowers.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled.

[ancient-seed-items]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L2236-L2237
[torchflower-crop]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/TorchflowerCropBlock.java#L19-L76
[torchflower-registration]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L916-L927
[orange-dye]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/orange_dye_from_torchflower.json#L1-L12
[torchflower-stew]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_torchflower.json#L1-L23
[sniffer-conditions]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L258-L284
[sniffer-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/gameplay/sniffer_digging.json#L1-L20
[loot-weights]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[loot-choice]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java#L75-L102
[loot-item]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/entries/LootItem.java#L33-L36
[sniffer-ground]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/sniffer_diggable_block.json#L1-L13
[sniffer-ai]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/SnifferAi.java#L185-L204
[sniffer-start]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L217-L236
[sniffer-tick]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L349-L360
[loot-names]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L100-L106
[gift-loot-context]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1544-L1553
[gift-loot-execution]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1569-L1581
[sniffer-tempt]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/SnifferAi.java#L71-L73
[sniffer-food]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L430-L433
[sniffer-food-tag]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/item/sniffer_food.json#L1-L5
[crop-growth]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L53-L99
[crop-light-ravager]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L149-L167
[placement-survival]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BlockItem.java#L130-L142
[crop-speed]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L105-L147
[crop-bonemeal]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L178-L191
[maintains-farmland]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/maintains_farmland.json#L1-L15
[farmland-maintenance]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/FarmBlock.java#L128-L139
[torchflower-crop-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/torchflower_crop.json#L1-L21
[torchflower-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/torchflower.json#L1-L21
[crop-harvest]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L193-L251
[flower-class]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L19-L59
[plant-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[dirt-tag]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/block/dirt.json#L1-L14
[bonemeal-use]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[potted-torchflower]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L2655-L2656
[seed-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L108-L109
[flower-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L178-L184
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317
[ancient-crop-registration]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L4254-L4275
[flower-items]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L357-L358

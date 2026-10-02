# Torchflower

The Torchflower is the harvested, placeable flower from [Torchflower farming](../blocks/Torchflower.md). **The mature placed flower emits light level 7 in MattMC.** [Flower registration][torchflower-registration] · [Item registration][flower-items]

## Obtaining

Grow [Torchflower Seeds](TorchflowerSeeds.md) through the two crop stages, then break the mature flower for **one Torchflower**. Breaking the immature crop returns one seed instead. A mature flower does not return replacement seeds. [Maturity conversion][torchflower-crop] · [Flower loot][torchflower-loot] · [Crop loot][torchflower-crop-loot]

## Usage

Place it on [supported soil](../blocks/Torchflower.md#harvesting-and-displaying-the-flower) or in a [Flower Pot](../blocks/FlowerPot.md#supported-plants). One flower crafts into **one Orange Dye**, or combines with a Bowl and both ordinary mushrooms to make a Suspicious Stew with **100 ticks of Night Vision**. Composting has an **85%** level-increase chance after the first level; the first accepted item in an empty Composter succeeds automatically. [Support][plant-support] · [Potted form][potted-torchflower] · [Dye recipe][orange-dye] · [Stew recipe][torchflower-stew] · [Compost value][flower-compost] · [Compost roll][compost-roll]

## Behavior

The mature flower is a separate block from the crop. It does not have a crop age and cannot be duplicated by applying Bone Meal directly. Sniffer food is **Torchflower Seeds**, not this flower. [Flower class][flower-class] · [Bone Meal dispatch][bonemeal-use] · [Sniffer food check][sniffer-food] · [Food tag][sniffer-food-tag]

## Notes

- Block and item ID: `minecraft:torchflower`. The growing block is `minecraft:torchflower_crop`. [Block registrations][torchflower-registration] · [Crop registration][ancient-crop-registration] · [Item registration][flower-items]
- [Items](Items.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled.

[torchflower-registration]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L916-L927
[flower-items]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L357-L358
[torchflower-crop]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/TorchflowerCropBlock.java#L19-L76
[torchflower-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/torchflower.json#L1-L21
[torchflower-crop-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/torchflower_crop.json#L1-L21
[plant-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[potted-torchflower]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L2655-L2656
[orange-dye]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/orange_dye_from_torchflower.json#L1-L12
[torchflower-stew]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_torchflower.json#L1-L23
[flower-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L178-L184
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317
[flower-class]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L19-L59
[bonemeal-use]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[sniffer-food]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L430-L433
[sniffer-food-tag]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/item/sniffer_food.json#L1-L5
[ancient-crop-registration]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L4254-L4275

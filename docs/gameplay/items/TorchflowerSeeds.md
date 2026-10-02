# Torchflower Seeds

Plant Torchflower Seeds on Farmland to grow a [Torchflower](../blocks/Torchflower.md). The finished flower is the harvest; growing it does not multiply seeds. [Planting item][ancient-seed-items] · [Growth transition][torchflower-crop] · [Flower drop][torchflower-loot]

## Obtaining

An adult Sniffer's active digging behavior invokes a loot table with one equal-weight choice between **one Torchflower Seed and one Pitcher Pod**. See [Sniffer seeds and pods](../blocks/Torchflower.md#sniffer-seeds-and-pods) for the ground, AI, and actual loot-caller chain. Breaking either immature Torchflower Crop stage returns **one seed**. [Digging drop][sniffer-conditions] · [Digging loot][sniffer-loot] · [Default weights][loot-weights] · [Crop loot][torchflower-crop-loot]

## Usage

Plant on Farmland with sufficient light; the crop needs brightness **8 to survive and 9 for natural growth**. Two accepted Bone Meal uses take a new age-0 crop through age 1 to the mature flower. [Crop support and natural growth][crop-growth] · [Survival light][crop-light-ravager] · [Bone Meal increment and maturity][torchflower-crop]

Seeds also tempt and count as food for Sniffers. They compost at **30%** chance after the first level, while the first accepted compostable item in an empty Composter succeeds automatically. [Temptation][sniffer-tempt] · [Food check][sniffer-food] · [Food tag][sniffer-food-tag] · [Compost value][seed-compost] · [Compost roll][compost-roll]

## Behavior

The seed places `minecraft:torchflower_crop`, not the finished flower. At maturity the block changes to `minecraft:torchflower`, whose ordinary drop is the flower item with no seed. Keep some seeds if you need another planting or Sniffer food. [Item registration][ancient-seed-items] · [Maturity][torchflower-crop] · [Flower loot][torchflower-loot]

## Notes

- Item ID: `minecraft:torchflower_seeds`. [Registration][ancient-seed-items]
- [Torchflower farming](../blocks/Torchflower.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled.

[ancient-seed-items]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L2236-L2237
[torchflower-crop]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/TorchflowerCropBlock.java#L19-L76
[torchflower-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/torchflower.json#L1-L21
[sniffer-conditions]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L258-L284
[sniffer-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/gameplay/sniffer_digging.json#L1-L20
[loot-weights]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/storage/loot/entries/LootPoolSingletonContainer.java#L45-L55
[torchflower-crop-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/torchflower_crop.json#L1-L21
[crop-growth]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L53-L99
[crop-light-ravager]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/CropBlock.java#L149-L167
[sniffer-tempt]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/SnifferAi.java#L71-L73
[sniffer-food]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L430-L433
[sniffer-food-tag]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/item/sniffer_food.json#L1-L5
[seed-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L108-L109
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317

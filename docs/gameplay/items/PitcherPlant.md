# Pitcher Plant

The Pitcher Plant is the decorative two-block plant harvested from a mature [Pitcher Crop](../blocks/PitcherPlant.md). It is a different item from the [Pitcher Pod](PitcherPod.md) used to start the crop. [Plant item][flower-items] · [Pod item][ancient-seed-items] · [Crop drops][pitcher-crop-loot]

## Obtaining

Grow a Pitcher Pod to **age 4**, then break the crop for **one Pitcher Plant**. Only the lower half has a loot entry; harvesting a complete two-block crop does not double the yield. Ages 0–3 return a pod instead. [Crop loot][pitcher-crop-loot] · [Paired halves][double-plant-support] · [Double-plant mining][double-plant-mining]

## Usage

One item places both halves on [supported soil](../blocks/PitcherPlant.md#placing-the-decorative-plant) with room above. One plant crafts into **two Cyan Dyes**. It has an **85%** chance to add one Composter level after the first level; the first accepted item in an empty Composter succeeds automatically. [Placement][double-plant-support] · [Dye recipe][cyan-dye] · [Compost value][flower-compost] · [Compost roll][compost-roll]

## Behavior

The placed decorative plant does not grow pods or need irrigation. Bone Meal cannot duplicate it, because its DoublePlantBlock registration does not implement the Bone Meal interface. Breaking the complete plant normally recovers **one plant item**. The Nether portal conversion checks for a **Pitcher Pod**, not this item. [Plant registration][ancient-crop-registration] · [Plant class][double-plant-class] · [Bone Meal dispatch][bonemeal-use] · [Plant loot][pitcher-plant-loot] · [Portal check][portal-pod]

## Notes

- Item and decorative block ID: `minecraft:pitcher_plant`; crop block ID: `minecraft:pitcher_crop`. [Block registration][ancient-crop-registration] · [Item registration][flower-items]
- [Pitcher Plant and Pitcher Crop](../blocks/PitcherPlant.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled.

[flower-items]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L357-L358
[ancient-seed-items]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L2236-L2237
[pitcher-crop-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/pitcher_crop.json#L8-L154
[double-plant-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L40-L87
[double-plant-mining]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L101-L130
[cyan-dye]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/recipe/crafting/cyan_dye_from_pitcher_plant.json#L1-L12
[flower-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L178-L184
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317
[ancient-crop-registration]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/Blocks.java#L4254-L4275
[double-plant-class]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L26-L38
[bonemeal-use]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BoneMealItem.java#L63-L78
[pitcher-plant-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/pitcher_plant.json#L1-L30
[portal-pod]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L110-L172

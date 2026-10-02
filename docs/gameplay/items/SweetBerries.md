# Sweet Berries

Sweet Berries are both food and the planting item for [Sweet Berry Bushes](../blocks/SweetBerryBush.md). [Item registration][berry-item]

## Obtaining

Pick a bush at **age 2 for 1–2 berries** or **age 3 for 2–3 berries**. Picking keeps the bush and resets it to age 1. Breaking gives the same base ranges and can add a Fortune bonus, but removes the bush; ages 0–1 drop nothing. See [finding bushes](../blocks/SweetBerryBush.md#finding-and-planting) for the checked taiga sources. [Picking callback][berry-harvest] · [Picking loot][berry-pick-loot] · [Breaking loot][berry-break-loot]

## Usage

- Eat one for **2 hunger points and 0.4 saturation** before normal caps. [Food values][berry-food] · [Builder][food-builder] · [Saturation calculation][food-saturation]
- Plant one on [the bush's accepted soils](../blocks/SweetBerryBush.md#finding-and-planting); water and Farmland are unnecessary. [Item registration][berry-item] · [Support][plant-support]
- Use as accepted Fox food, or compost with a **30%** level-increase chance after the first level. The first accepted item in an empty Composter succeeds automatically. [Fox food check][fox-food] · [Food tag][fox-food-tag] · [Compost value][berry-compost] · [Compost roll][compost-roll]

## Behavior

Using this edible block item on suitable ground tries planting before eating, so aim away from a valid planting surface when you intend to eat. The planted bush can slow and damage moving living entities; see [contact damage and Foxes](../blocks/SweetBerryBush.md#contact-damage-and-foxes). [Item use routing][edible-placement] · [Contact callback][berry-harm]

## Notes

- Item ID: `minecraft:sweet_berries`; placed block ID: `minecraft:sweet_berry_bush`. [Registration][berry-item]
- [Items](Items.md)

## Sources and verification

Source-reviewed at `85428ea17c51c31ddb67b6c1060b78f4c3a13515` on 2026-10-02. Inline source links identify the exact implementation and bundled data used here. No in-game growth timing, harvest, mob-interaction, or placement test was run. Data packs, world-generation settings, and game rules can change these results; quoted ordinary drops assume block drops are enabled.

[berry-item]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/Items.java#L2449-L2451
[berry-harvest]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/SweetBerryBushBlock.java#L100-L134
[berry-pick-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/harvest/sweet_berry_bush.json#L1-L53
[berry-break-loot]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/loot_table/blocks/sweet_berry_bush.json#L1-L87
[berry-food]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/food/Foods.java#L39-L43
[food-builder]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[food-saturation]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[plant-support]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[fox-food]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/entity/animal/Fox.java#L577-L580
[fox-food-tag]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/resources/data/minecraft/tags/item/fox_food.json#L1-L6
[berry-compost]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L93-L109
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L317
[edible-placement]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L45
[berry-harm]: https://github.com/HungLo2020/MattMC/blob/85428ea17c51c31ddb67b6c1060b78f4c3a13515/src/main/java/net/minecraft/world/level/block/SweetBerryBushBlock.java#L81-L98

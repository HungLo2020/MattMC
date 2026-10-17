# Dandelion

**Dandelion** (`minecraft:dandelion`) is the item form of its matching flower block. The [small and tall flower guide](../blocks/Flowers.md#dandelion) owns the family comparisons and exact variant routes. [Item registration dandelion]

## Obtaining

Collect a planted Dandelion by hand for **one item**. Forest’s ordinary flower feature contains it, and Bone Meal on suitable Grass Blocks can select it through that biome feature. Applying Bone Meal directly to the Dandelion does not duplicate it. See [flower acquisition and propagation](../blocks/Flowers.md#bone-meal-on-grass-block). [Loot dandelion] · [Forest biome] · [Default flower selection] · [Grass propagation] · [Small-flower class] · [Bone Meal dispatch]

## Usage

One Dandelion crafts into **one Yellow Dye**. It is also the Saturation choice in the [flower-to-stew table](../blocks/Flowers.md#dandelion), with a **7-tick** effect in the checked [Suspicious Stew recipe](SuspiciousStew.md#obtaining). [Dye dandelion] · [Stew dandelion]

## Behavior

Plant it on the [accepted flower soils](../blocks/Flowers.md#planting-and-support), or place it into a supported [Flower Pot](../blocks/FlowerPot.md#supported-plants). Its flower survival check has no light or nearby-water requirement. Bees accept it as food and as a pollination candidate; see [Bee care](../mobs/Bee.md). [Plant support] · [Bee food items] · [Bee-attractive blocks]

## Notes

Silk Touch and Fortune do not increase the normal flower-item drop. Recipe outputs consume their input flower. The family guide distinguishes natural patches, Grass Block Bone Meal, and planted tall-flower duplication. [Loot dandelion] · [Ingredient consumption]

## Sources and verification

Source-reviewed on **2026-10-02** at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7`. Checked this item’s registry, full block-loot table, exact dye/stew applicability, relevant acquisition and use callbacks, and links to the shared family guide. No in-game placement, harvesting, crafting, propagation, feeding, or world-generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Plant support]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/VegetationBlock.java#L23-L47
[Small-flower class]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L19-L59
[Bone Meal dispatch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[Grass propagation]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/GrassBlock.java#L43-L89
[Bee food items]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/item/bee_food.json
[Bee-attractive blocks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/block/bee_attractive.json
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
[Forest biome]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/biome/forest.json
[Default flower selection]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/configured_feature/flower_default.json
[Dye dandelion]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/yellow_dye_from_dandelion.json
[Stew dandelion]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_dandelion.json
[Loot dandelion]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/dandelion.json
[Item registration dandelion]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L342

# Wither Rose

**Wither Rose** (`minecraft:wither_rose`) is the item form of its matching flower block. The [small and tall flower guide](../blocks/Flowers.md#wither-rose) owns the family comparisons and exact variant routes. [Item registration wither_rose]

## Obtaining

A living entity’s death routine can create a Wither Rose when its kill credit is a [Wither](../mobs/Wither.md). It tries to plant the rose at the victim’s position when `mobGriefing` permits and the cell/support are valid; otherwise it drops a loose item. Collecting a planted rose normally returns **one Wither Rose** without a special tool. [Wither Rose creation] · [Loot wither_rose]

## Usage

One Wither Rose crafts into **one Black Dye**. Its [Suspicious Stew recipe](SuspiciousStew.md#obtaining) instead produces stew with **Wither I for 140 ticks**, about seven seconds at 20 TPS. It is not directly duplicated by Bone Meal. [Dye wither_rose] · [Stew wither_rose] · [Small-flower class] · [Bone Meal dispatch]

## Behavior

The plant accepts ordinary [flower soils](../blocks/Flowers.md#planting-and-support) plus **Netherrack, Soul Sand, and Soul Soil**. Outside Peaceful, its contact callback can apply **Wither I for 40 ticks** to living entities that pass the damage-invulnerability and effect checks. Feeding it to a Bee uses the harmful special interaction instead of breeding; keep it away from an apiary. See [the full hazard conditions](../blocks/Flowers.md#wither-rose-creation-and-hazard) and [Flower Pot](../blocks/FlowerPot.md#supported-plants) for its separate potted form. [Wither Rose extra support] · [Wither Rose contact] · [Bee feeding]

## Notes

Silk Touch and Fortune do not increase the normal flower-item drop. Recipe outputs consume their input flower. The family guide distinguishes natural patches, Grass Block Bone Meal, and planted tall-flower duplication. [Loot wither_rose] · [Ingredient consumption]

## Sources and verification

Source-reviewed on **2026-10-02** at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7`. Checked this item’s registry, full block-loot table, exact dye/stew applicability, relevant acquisition and use callbacks, and links to the shared family guide. No in-game placement, harvesting, crafting, propagation, feeding, or world-generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Small-flower class]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/FlowerBlock.java#L19-L59
[Bone Meal dispatch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/BoneMealItem.java#L35-L77
[Bee feeding]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/animal/Bee.java#L569-L590
[Wither Rose extra support]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/WitherRoseBlock.java#L44-L49
[Wither Rose contact]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/WitherRoseBlock.java#L74-L89
[Wither Rose creation]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1463
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
[Dye wither_rose]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/black_dye_from_wither_rose.json
[Stew wither_rose]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/suspicious_stew_from_wither_rose.json
[Loot wither_rose]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/wither_rose.json
[Item registration wither_rose]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L356

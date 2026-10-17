# Composter

**Composter** is the placeable item for `minecraft:composter`, which turns accepted plants and food into Bone Meal and serves as a Farmer job site. [Registration][items]

## Obtaining

Craft **7 wooden slabs into 1 Composter** using the [U-shaped recipe](../blocks/Composter.md#crafting-placement-and-recovery). Mining returns one Composter without requiring Silk Touch or a tool tier; an axe is faster. A fully ready **level-8** Composter also drops one Bone Meal when normally mined. The item does not retain partial compost or return the spent ingredients. [Recipe][recipe] · [Level-sensitive loot][loot]

## Uses

Place it empty, then use the [composting steps](../blocks/Composter.md#from-ingredients-to-bone-meal) and [complete accepted-item list](../blocks/Composter.md#complete-accepted-item-list). The first accepted item is guaranteed to raise an empty Composter; later failed rolls still consume the ingredient. A completed batch yields one Bone Meal after the maturation step.

The [block guide](../blocks/Composter.md) covers top-input/bottom-output automation, comparator levels, progress saving and Farmer behavior. The item in your inventory is not a container for compost or plants.

## Sources and verification

Source-reviewed on **2026-10-02** at `ae92d4575f1af7752d0f461bbf7bf0843c3cddf7`; the block guide provides current interaction, processing and automation evidence. No in-game test was run.

[Composter block](../blocks/Composter.md) · [Bone Meal](BoneMeal.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/java/net/minecraft/world/item/Items.java
[recipe]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/recipe/crafting/composter.json
[loot]: https://github.com/HungLo2020/MattMC/blob/ae92d4575f1af7752d0f461bbf7bf0843c3cddf7/src/main/resources/data/minecraft/loot_table/blocks/composter.json

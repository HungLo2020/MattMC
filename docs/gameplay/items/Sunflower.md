# Sunflower

**Sunflower** (`minecraft:sunflower`) is the item form of its matching flower block. The [small and tall flower guide](../blocks/Flowers.md#sunflower) owns the family comparisons and exact variant routes. [Item registration sunflower]

## Obtaining

Collect a complete planted Sunflower for **one item**, even though the plant occupies two blocks. Sunflower Plains selects a natural Sunflower patch. Once you have one planted, use Bone Meal on either half to drop **one more item** while retaining the original. [Loot sunflower] · [Tall-flower mining] · [Sunflower Plains biome] · [Sunflower patch placement] · [Sunflower patch] · [Tall-flower duplication]

## Usage

One Sunflower crafts into **two Yellow Dyes**. It can also feed Bees, and the planted upper half is the part recognized for pollination. It is not a small-flower Suspicious Stew ingredient. [Dye sunflower] · [Bee food items] · [Bee flower checks] · [Flower effect lookup] · [Tall-flower duplication]

## Behavior

One item places a lower and upper half on [accepted soil](../blocks/Flowers.md#planting-and-support), with a placeable upper cell. Neither half is waterlogged. Losing support or either half removes the unsupported remainder. Sunflowers have no filled [Flower Pot](../blocks/FlowerPot.md#supported-plants) registration. [Two-block placement and support]

## Notes

Silk Touch and Fortune do not increase the normal flower-item drop. Recipe outputs consume their input flower. The family guide distinguishes natural patches, Grass Block Bone Meal, and planted tall-flower duplication. [Loot sunflower] · [Ingredient consumption]

## Sources and verification

Source-reviewed on **2026-10-02** at `3c39e8cc456e3c76eac84ae45b12020c4b08a7a7`. Checked this item’s registry, full block-loot table, exact dye/stew applicability, relevant acquisition and use callbacks, and links to the shared family guide. No in-game placement, harvesting, crafting, propagation, feeding, or world-generation test was run. Data packs can change tags, recipes, loot, and biome features.

[Two-block placement and support]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L37-L99
[Tall-flower mining]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java#L101-L134
[Tall-flower duplication]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/TallFlowerBlock.java#L13-L37
[Bee food items]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/tags/item/bee_food.json
[Bee flower checks]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/entity/animal/Bee.java#L668-L678
[Ingredient consumption]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
[Flower effect lookup]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/level/block/SuspiciousEffectHolder.java#L23-L29
[Sunflower Plains biome]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/biome/sunflower_plains.json
[Sunflower patch placement]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/placed_feature/patch_sunflower.json
[Sunflower patch]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/worldgen/configured_feature/patch_sunflower.json
[Dye sunflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/recipe/crafting/yellow_dye_from_sunflower.json
[Loot sunflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/resources/data/minecraft/loot_table/blocks/sunflower.json
[Item registration sunflower]: https://github.com/HungLo2020/MattMC/blob/3c39e8cc456e3c76eac84ae45b12020c4b08a7a7/src/main/java/net/minecraft/world/item/Items.java#L712

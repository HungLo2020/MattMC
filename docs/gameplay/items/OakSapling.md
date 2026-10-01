# Oak Sapling

**Oak Sapling** (`minecraft:oak_sapling`) is the plantable item used to renew an oak supply. Save some after each harvest before spending extras as fuel. Its support, light, growth-stage, Bone Meal, and tree-selection rules live in the [Oak block guide](../blocks/Oak.md#planting-and-growing-saplings). [Item registration][item] · [Sapling registration][block]

## Obtaining

Oak leaves can drop saplings when broken without shears or Silk Touch, and naturally decaying leaves use the ordinary no-Fortune roll. Fortune can improve the sapling chance when a suitable tool supplies it. See the canonical [Oak leaf-drop table](../blocks/Oak.md#leaf-drops) for probabilities and the separate stick/apple rolls. A harvested leaf is not guaranteed to replace itself with a sapling. [Oak leaf loot][leaves-loot]

Breaking a planted oak sapling normally returns **one oak sapling**, subject to the loot table's explosion-survival condition. These are confirmed collection routes, not an exhaustive list of chest or merchant sources. [Sapling loot][loot]

## Planting and Bone Meal

Use the item on a valid planting surface to place the sapling. Plant support and enough room for a tree are separate requirements. Follow [Planting and growing saplings](../blocks/Oak.md#planting-and-growing-saplings) before treating an open patch as a working tree farm. [Block-item placement][placement] · [Support check][support]

[Bone Meal](BoneMeal.md) is an attempt to advance growth, not a guaranteed instant tree. It can be consumed with no visible change or with an obstructed tree attempt; see the [Bone Meal details](../blocks/Oak.md#bone-meal). [Sapling callbacks][sapling] · [Bone Meal dispatch][bone-meal]

## Fuel

An oak sapling supplies **100 default furnace burn ticks**, half the energy needed for a typical 200-tick recipe. A single sapling cannot fuel that entire operation by itself. Keeping enough for replanting is usually more useful than burning the last one. [Sapling fuel value][fuel] · [Sapling item tag][saplings] · [Furnace fuel lookup][furnace]

Related: [Oak](../blocks/Oak.md) · [Oak Log](OakLog.md) · [Bone Meal](BoneMeal.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game planting, Bone Meal, drop-rate, or furnace test was run. Data packs can alter loot and tags; growth depends on the active world conditions described in the block guide.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L133
[block]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/Blocks.java#L208-L212
[leaves-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/oak_leaves.json
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/oak_sapling.json
[placement]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BlockItem.java
[support]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/VegetationBlock.java
[sapling]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/SaplingBlock.java
[bone-meal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BoneMealItem.java
[fuel]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java
[saplings]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/saplings.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java

# Seagrass

**Seagrass** (`minecraft:seagrass`) is the item used to plant short underwater Seagrass. The tall plant also drops this same item. Its [block guide](../blocks/Seagrass.md) covers natural supply, water/support rules, and propagation. [Item registration][sea-items] · [Plant registrations][seagrass-reg]

## Obtaining and use

Use **Shears**: short Seagrass gives **one item**, and a tall plant gives **two items total**. Hand breaking gives none, Silk Touch alone is not a substitute, and Fortune adds no bonus. [Short loot][grass-loot] · [Tall loot][tall-loot] · [Paired-plant harvesting][double]

Plant the item over suitable ground in [full-strength water](../blocks/Seagrass.md#underwater-planting). With a Water block above it, [Bone Meal](BoneMeal.md) can turn the short plant into tall Seagrass for another Shears harvest. See [propagation](../blocks/Seagrass.md#bone-meal-and-propagation) for the checked multiplication loop and water-scattering route. [Planting and Bone Meal][grass]

Related: [Shears](Shears.md) · [Kelp](Kelp.md) · [Sea Pickle](SeaPickle.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Item registration, both loot tables, paired destruction, planting, and Bone Meal checked. No in-game test was run.

[sea-items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L324-L325
[seagrass-reg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L784-L806
[grass-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/seagrass.json
[tall-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/tall_seagrass.json
[double]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/DoublePlantBlock.java
[grass]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/SeagrassBlock.java

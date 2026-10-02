# Clay

**Clay** (`minecraft:clay`) is the placeable block form of the clay resource. Use it for building or as the input for [Terracotta](../blocks/Terracotta.md#crafting-and-smelting). [Block registration][clay-block] · [Item registration][clay-item]

## Obtaining and use

Collect a Clay block with **Silk Touch**, or re-form the Clay Balls from ordinary mining using the [2 × 2 Clay recipe](../blocks/ClayAndBricks.md#crafting-and-smelting). Without Silk Touch, ordinary mining returns **four Clay Balls**; Fortune does not increase them. The block does not require a mining tool, though shovels are faster. [Clay loot][clay-loot] · [Clay recipe][clay-recipe] · [Shovel tag][shovel]

The [Clay and Bricks guide](../blocks/ClayAndBricks.md) covers a verified natural source, [Mud conversion](../blocks/ClayAndBricks.md#turning-mud-into-clay), mining, and placement. Smelting the **block** follows the Terracotta route; use [Clay Balls](ClayBall.md) when making loose [Bricks](Brick.md).

Related: [Clay Ball](ClayBall.md) · [Terracotta](Terracotta.md) · [Bricks block](Bricks.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Registration, crafting, and loot checked; shared source evidence is in the linked block guides. No in-game test was run.

[clay-block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L1962-L1964
[clay-item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L491
[clay-loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/clay.json
[clay-recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/clay.json
[shovel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/shovel.json

# Dead Bush

The **Dead Bush** item (`minecraft:dead_bush`) places dry vegetation and is the current breeding food for [Gelada Monkeys](../mobs/GeladaMonkey.md). Preserve some before using the rest as decoration or fuel. [Item registration][item] · [Gelada food tag][food]

## Obtaining

Use **Shears** to collect a [placed Dead Bush](../blocks/DeadBush.md#finding-and-collecting). That block guide covers verified natural patches and the different Stick drop when harvested without Shears.

Desert village house chests can also supply **1–3 Dead Bushes when their Dead Bush loot entry is selected**. This is a possible entry in the house loot table, not a guaranteed chest total. The table is assigned to chests in bundled village templates, including `desert_small_house_4`, which is included in the desert house pool. [House loot][loot] · [House template][template] · [House pool][pool]

## Uses

- **Breed Geladas:** use one on an eligible adult to enter love mode, or feed a baby to speed growth. Hold it to attract Geladas when other behavior permits. See the [mob guide](../mobs/GeladaMonkey.md#feeding-and-breeding) for breeding and group-combat limits
- **Decorate:** place it on suitable ground or in an empty Flower Pot. Support and pot behavior belong to the [block guide](../blocks/DeadBush.md#placement-and-decoration)
- **Fuel:** a Dead Bush provides **100 burn ticks** in the standard Furnace, half of a 200-tick recipe's fuel requirement. Plan continuous batches using the [Furnace guide](../blocks/Furnace.md#fuel-planning)

Dead Bush feeding is different from giving a Gelada **Wheat**, which starts its clearing behavior. Clearing a planted bush does not collect the same breeding item: the monkey has no Shears in that destruction path. [Food tag][food] · [Food and clearing interactions][gelada] · [Fuel values][fuel] · [Server fuel initialization][fuel-caller]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked item registration, the linked harvesting route, decoded village chest data and pool membership, food dispatch, and server fuel values. No in-game harvesting, chest opening, feeding, or fuel test was run.

Related: [Dead Bush block](../blocks/DeadBush.md) · [Gelada Monkey](../mobs/GeladaMonkey.md) · [Wheat](Wheat.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L293
[food]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/item/gelada_monkey_breedables.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/chests/village/village_desert_house.json#L67-L82
[template]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/structure/village/desert/houses/desert_small_house_4.nbt
[pool]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/worldgen/template_pool/village/desert/houses.json
[gelada]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java
[fuel]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L95
[fuel-caller]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/server/MinecraftServer.java#L340

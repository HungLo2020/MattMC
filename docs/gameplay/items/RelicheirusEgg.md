# Relicheirus Egg

Relicheirus Egg is the block item `minecraft:relicheirus_egg`. It places a hatching egg, not the immediate-spawning [Relicheirus Spawn Egg](RelicheirusSpawnEgg.md).

## Obtaining

The item is explicitly listed in Creative. With command permission, use its registered ID to obtain it. A natural or working breeding source has not been established: the current Relicheirus breeding implementation lays ordinary Turtle Eggs instead.

An already placed species egg can be collected through its **Silk Touch** loot branch. Normal breaking does not provide that item, and collecting it does not preserve hatch progress in the bundled table.

## Use

Place it over solid ground and protect it from player footsteps. Its random-tick hatching path creates a baby Relicheirus. Read the [placed dinosaur egg guide](../blocks/DinosaurEggs.md) for growth, trampling, and the species-specific ownership rules.

## Related pages

- [Relicheirus](../mobs/Relicheirus.md)
- [Placed dinosaur eggs](../blocks/DinosaurEggs.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game hatching, ownership, collection, trampling, or breeding test was run. Data packs and modified block states can change these conditions.

- [Shared egg growth, hatching, ownership, and trampling](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/DinosaurEggBlock.java)
- [Tremorsaurus egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/TremorsaurusEggBlock.java)
- [Relicheirus egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/RelicheirusEggBlock.java)
- [Block registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Tremorsaurus egg loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/tremorsaurus_egg.json)
- [Relicheirus egg loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/relicheirus_egg.json)
- [Tremorsaurus ownership and breeding placeholder](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/TremorsaurusEntity.java)
- [Relicheirus breeding placeholder](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/RelicheirusEntity.java)

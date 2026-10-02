# Gelada Monkey Spawn Egg

The **Gelada Monkey Spawn Egg** (`minecraft:gelada_monkey_spawn_egg`) creates a [Gelada Monkey](../mobs/GeladaMonkey.md). It is available in the Creative spawn-egg collection. No bundled crafting recipe or Survival loot source was found, and an egg's availability does not establish natural Gelada spawning. [Registration][item] · [Creative listing][creative]

## Spawning and leader variants

Use the egg on an ordinary block surface to create a Gelada. A default individual spawn has a **25% chance to receive the leader flag**. Leaders express their larger form only as adults. The normal egg creation path calls the mob's spawn initialization with no existing group data; do not apply its separate group-spawn conditions to repeated individual egg uses. [Egg use][egg] · [Creation caller][create] · [Variant initialization][spawn] · [Leader check][leader]

Successful ordinary use consumes one egg outside Creative. The spawner-block interaction is a separate path controlled by the server's spawner setting. [Egg use and spawner gate][egg] · [Consumption][consume]

## Using on a Gelada

Use a matching egg on an existing Gelada to create a **baby Gelada**. This calls the offspring factory rather than the normal individual-spawn initializer. Its leader flag has a **50% chance**, independent of whether the parent is a leader, and remains visually inactive until adulthood. Custom entity data can change these defaults. [Mob dispatch][mob] · [Baby helper][baby] · [Offspring factory][offspring]

An egg does not tame the monkey or make a group safe from fighting. After spawning it, use the mob guide for [Dead Bush breeding](../mobs/GeladaMonkey.md#feeding-and-breeding), [Wheat clearing](../mobs/GeladaMonkey.md#clearing-plants-with-wheat), and the current group-combat limitations.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked registration, Creative listing, variant initialization, matching-mob interaction, offspring creation, and consumption. No in-game spawning or leader-frequency test was run.

Related: [Gelada Monkey](../mobs/GeladaMonkey.md) · [Gorilla Spawn Egg](GorillaSpawnEgg.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1880
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2022
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L103
[create]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1774
[spawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java#L378-L389
[leader]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java#L153-L158
[consume]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[baby]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[offspring]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGeladaMonkey.java#L337-L342

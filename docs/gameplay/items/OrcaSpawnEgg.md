# Orca Spawn Egg

The **Orca Spawn Egg** (`minecraft:orca_spawn_egg`) creates an [Orca](../mobs/Orca.md). It is listed in the Creative spawn-egg tab. This is a verified setup item; its existence does not establish natural Orca spawning or a Survival egg recipe. [Registration][item] · [Creative listing][creative]

## Spawning an Orca

Use the egg on a block face, or aim at a source-water block and use it. The generic egg handler creates its registered entity and consumes one egg on a successful ordinary use when the player lacks Creative's infinite materials. Prepare a pool with room to swim and an unobstructed route to air before spawning it. [Egg use][egg]

The new Orca can grant its swimming benefit, hunt other animals, and retaliate according to the [mob guide](../mobs/Orca.md). Spawning it beside you does not tame it or assign you as owner.

## Using on an Orca

Using this matching egg on an existing Orca invokes the animal's offspring factory, creates another Orca, marks it as a baby, and consumes an egg outside Creative. This does not require two adults, food, or love mode. [Mob interaction][mob] · [Egg offspring handling][offspring] · [Orca offspring][orca]

That route is separate from the missing ordinary Orca breeding AI: a baby appearing from a spawn egg does not prove that two Salmon-fed adults can breed. Babies can receive Raw Salmon through the inherited growth-feeding interaction described on the [Orca page](../mobs/Orca.md#feeding-and-the-breeding-limitation).

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game egg placement, source-water use, baby creation, or enclosure test was run. Custom egg entity data can change the ordinary result.

Related: [Orca](../mobs/Orca.md) · [Hammerhead Shark Spawn Egg](HammerheadSharkSpawnEgg.md) · [Creative](../gamemodes/Creative.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1921
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2054
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L128
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[offspring]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[orca]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityOrca.java#L338-L345

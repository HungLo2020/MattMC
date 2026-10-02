# Hammerhead Shark Spawn Egg

The **Hammerhead Shark Spawn Egg** (`minecraft:hammerhead_shark_spawn_egg`) creates a [Hammerhead Shark](../mobs/HammerheadShark.md). It is listed in the Creative spawn-egg tab. The checked biome data does not establish natural Hammerhead spawning, so the egg is useful for controlled Creative setups. [Registration][item] · [Creative listing][creative]

## Using the egg

Use it on a block face, or aim at source water and use it to create the shark there. Successful ordinary spawning consumes one egg when the player lacks Creative's infinite materials. Give it adequate water and keep vulnerable animals and low-health players away: being spawned from an egg does not change its predator targeting. [Egg use][egg] · [Mob behavior](../mobs/HammerheadShark.md#what-makes-it-attack)

**Using the egg directly on an existing Hammerhead does not create a baby shark.** The generic helper tries to mark a new mob as a baby, then rejects it if it remains an adult. Hammerheads inherit the empty baby setter and the default false baby-state check. Use a block face or source water to place another ordinary shark. [Egg-on-mob caller][mob] · [Baby-result check][offspring] · [Baby setter][baby-setter] · [Baby-state default][baby-state]

See the [Hammerhead Shark guide](../mobs/HammerheadShark.md) for water care, circling attacks, feeding/breeding limits, and the inactive Shark Tooth reward.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game egg placement, source-water use, baby-interaction, or combat test was run. Custom egg entity data can change the ordinary result.

Related: [Hammerhead Shark](../mobs/HammerheadShark.md) · [Orca Spawn Egg](OrcaSpawnEgg.md) · [Creative](../gamemodes/Creative.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1892
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2031
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L128
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[offspring]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[baby-setter]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1264-L1265
[baby-state]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L532-L534

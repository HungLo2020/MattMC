# Rattlesnake Spawn Egg

The **Rattlesnake Spawn Egg** (`minecraft:rattlesnake_spawn_egg`) creates a [Rattlesnake](../mobs/Rattlesnake.md). It is available in the Creative spawn-egg collection. No bundled crafting recipe or Survival loot source was found. [Registration][item] · [Creative listing][creative]

## Spawning and handling

Use the egg on an ordinary block surface to create a Rattlesnake. Spawning does not tame it or give the user immunity from its close-range targeting. Put it away from [Jerboas](../mobs/Jerboa.md) and Rabbits, which appear in its registered prey goals. [Egg use][egg] · [Snake targets][targets]

Successful ordinary use consumes one egg outside Creative. The spawner-block interaction is a separate path controlled by the server's spawner setting. The egg provides a setup route for the mob; it does not establish natural desert spawning. [Egg and spawner behavior][egg] · [Consumption][consume]

## Using on a Rattlesnake

A matching egg used directly on an existing Rattlesnake creates a **baby Rattlesnake** through its actual offspring factory. It needs neither a second snake nor breeding food. This is separate from the ordinary food-breeding route explained on the [mob page](../mobs/Rattlesnake.md#feeding-and-breeding). [Mob dispatch][mob] · [Factory][offspring] · [Active entity alias][alias] · [Baby helper][baby]

Babies skip the adult close-range player-targeting goal, but retain the mob's other registered goals. Do not infer stronger venom or harmlessness from the baby's appearance: the current melee/venom integration limits are covered by the [combat guide](../mobs/Rattlesnake.md#damage-poison-and-remedies). [Age check][age]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked item and entity registration, Creative listing, normal egg use, matching-mob dispatch, offspring creation, consumption, and age-gated targeting. No in-game spawning or combat test was run.

Related: [Rattlesnake](../mobs/Rattlesnake.md) · [Jerboa Spawn Egg](JerboaSpawnEgg.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1938
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2069
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L103
[targets]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L62-L74
[consume]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[offspring]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L206-L210
[alias]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/AMEntityRegistry.java#L46
[baby]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[age]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityRattlesnake.java#L286-L307

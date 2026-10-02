# Jerboa Spawn Egg

The **Jerboa Spawn Egg** (`minecraft:jerboa_spawn_egg`) creates a [Jerboa](../mobs/Jerboa.md). It is listed in Creative. No bundled crafting recipe or Survival loot source was found, and its availability does not establish a natural Jerboa spawning location. [Registration][item] · [Creative listing][creative]

## Spawning a Jerboa

Use the egg on an ordinary block surface to create a Jerboa. An ordinary new Jerboa starts **unbefriended**, so use its [seed interaction](../mobs/Jerboa.md#seeds-befriending-and-healing) if you want its protection from normal distance despawning. The egg does not assign ownership to the player. Custom entity data can change the default state. [Egg use][egg] · [Default flag][flag] · [Persistence][persistence]

Successful ordinary use consumes one egg outside Creative. The separate spawner-block path depends on the server's spawner setting. [Egg and spawner behavior][egg] · [Consumption][consume]

## Using on a Jerboa

Use a matching egg directly on an existing Jerboa to create a **befriended baby Jerboa**. The active mob interaction calls the Jerboa's offspring factory, which sets the befriended flag before the egg helper makes it a baby and adds it to the world. It does not require the parent to be befriended. [Mob dispatch][mob] · [Offspring factory][offspring] · [Baby helper][baby]

This route works independently of the broken bundled breeding-food tag. It does not prove that feeding two adults currently produces offspring. The [Jerboa guide](../mobs/Jerboa.md#breeding-limitation-and-babies) explains that limitation and the difference between befriending and ownership.

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked item registration, Creative listing, mob interaction dispatch, default flags, offspring creation, and consumption. No in-game egg or persistence test was run.

Related: [Jerboa](../mobs/Jerboa.md) · [Rattlesnake Spawn Egg](RattlesnakeSpawnEgg.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1897
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2037
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L103
[flag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L77-L84
[persistence]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L103-L121
[consume]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[offspring]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityJerboa.java#L422-L430
[baby]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184

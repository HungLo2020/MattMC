# Gorilla Spawn Egg

The **Gorilla Spawn Egg** (`minecraft:gorilla_spawn_egg`) creates a [Gorilla](../mobs/Gorilla.md). It is listed in the Creative spawn-egg collection; no bundled crafting recipe or Survival loot source was found. It provides access to a mob whose natural biome spawning is not established in this snapshot. [Registration][item] · [Creative listing][creative]

## Spawning a Gorilla

Use the egg on an ordinary block surface to create a Gorilla. A default individual spawn has a **50% chance of being a silverback**; its adult silverback stats are applied by the mob's ticking behavior. Custom entity data on an egg can alter the result. Spawning does not tame it or assign the user as its owner. [Egg use][egg] · [Creation caller][create] · [Spawn initialization][spawn] · [Gorilla behavior](../mobs/Gorilla.md)

Successful ordinary use consumes one egg outside Creative. The separate spawner-block interaction depends on the server's spawner setting and follows its own use path. [Egg use and spawner gate][egg] · [Creative consumption exemption][consume]

## Using on a Gorilla

Using a matching egg directly on an existing Gorilla calls its offspring factory and creates a **baby Gorilla**, without requiring food, love mode, or a second adult. This route works independently of the missing Gorilla food tags. [Mob interaction dispatch][mob] · [Baby-egg helper][baby] · [Offspring factory][offspring]

The ordinary offspring factory creates a fresh Gorilla with its default silverback flag unset. It does not copy the parent's silverback variant or owner, so using an ordinary egg on a silverback does not guarantee a baby silverback. This baby route also does not run the normal individual-spawn variant roll. [Default flag][flag] · [Offspring factory][offspring] · [Baby helper][baby]

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. Checked item registration, Creative listing, ordinary egg creation, matching-mob dispatch, offspring initialization, and consumption. No in-game spawn or offspring test was run.

Related: [Gorilla](../mobs/Gorilla.md) · [Gelada Monkey Spawn Egg](GeladaMonkeySpawnEgg.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1888
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2027
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L103
[spawn]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java#L188-L204
[consume]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1088-L1103
[baby]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L184
[offspring]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java#L537-L541
[flag]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/alexsmobs/entity/EntityGorilla.java#L253-L259
[create]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1774

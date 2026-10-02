# Mule Spawn Egg

The **Mule Spawn Egg** (`minecraft:mule_spawn_egg`) is a Creative tool for placing a [Mule](../mobs/Mule.md). It is registered with that entity type and appears in the Creative spawn-egg selection. [Item registration][items] · [Creative entry][creative]

## Use

Use the egg on an ordinary block to spawn its mob at the clicked position or adjacent face, depending on the block's collision shape. Leave suitable room for the animal. A successful ordinary spawn consumes one egg in Survival; Creative use preserves the held supply. [Placement and consumption][egg] · [Consumption helper][stack]

A matching egg used directly on a Mule can create a baby through the egg interaction even though Mules cannot mate. This egg-created baby route is available in **Survival as well as Creative** through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md); ordinary Mule mating remains disabled. [Registration](https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/Items.java#L1916) · [Category entry](https://github.com/HungLo2020/MattMC/blob/aaeea0b263d995334061e562cd5b71a853540f7f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2050) [Egg offspring interaction][egg] · [Live mob interaction dispatch][mob]

A spawned animal is not automatically tamed or saddled by the ordinary egg. Follow the [Mule guide](../mobs/Mule.md) for obtaining, feeding, taming, riding, equipment, and drops. Modified egg components can change the entity data. [Default tame state][shared] · [Egg components][egg]

Using an egg on a compatible Spawner configures its entity instead of placing an animal; see [Monster Spawner](../blocks/MonsterSpawner.md) for the current settings and restrictions. [Spawner interaction][egg]

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. Item registration, Creative entry, block use, and matching-mob offspring dispatch were inspected. No in-game egg, Spawner, or baby-spawn test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[egg]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java
[stack]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java
[mob]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java
[shared]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java

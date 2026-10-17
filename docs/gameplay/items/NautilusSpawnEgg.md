# Nautilus Spawn Egg

The **Nautilus Spawn Egg** creates a [Nautilus](../mobs/Nautilus.md). Its registry ID is `minecraft:nautilus_spawn_egg`. [Registration][egg-nautilus]

## Obtaining

It is listed in the ordinary Spawn Eggs category, so use the [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. No bundled recipe or natural loot route was found for this egg. Browser availability does not mean the mob naturally spawns. [Nautilus egg listing][egg-list-nautilus]

## Usage

Use the egg on a block face with room for the mob, or aim at source water. Successful ordinary placement consumes one egg outside infinite-material mode. Spawn-egg placement has its own path; the mob's natural biome and depth conditions are not requirements for this use. [Placement, water targeting and consumption][egg-use]

## Behavior

Use it in water so the animal does not dry out. Using a matching egg directly on an existing Nautilus creates a baby through its offspring method; a tamed parent's owner and tame state pass to that child. This is different from food-driven breeding. [Egg offspring handling][egg-baby] · [Nautilus offspring][offspring] · [Drying][care]

Using the egg on an enabled [Monster Spawner](../blocks/MonsterSpawner.md) changes the spawner's mob type and consumes an egg in the checked path. The server's spawner-block setting can refuse this operation. [Spawner interaction][egg-use]

## Notes

- The mob's complete care, combat and integration limits are on the [Nautilus page](../mobs/Nautilus.md)
- Related: [other Zombie Nautilus egg](ZombieNautilusSpawnEgg.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`. Checked registration, ordinary listing, block/water placement, direct-on-mob offspring handling and spawner interaction. No in-game egg, spawner or offspring test was run.

[egg-nautilus]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Items.java#L1919-L1919
[egg-list-nautilus]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2052-L2052
[egg-use]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L182
[offspring]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/Nautilus.java#L19-L34
[care]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/Nautilus.java#L84-L106

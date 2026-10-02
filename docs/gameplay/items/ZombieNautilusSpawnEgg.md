# Zombie Nautilus Spawn Egg

The **Zombie Nautilus Spawn Egg** creates a [Zombie Nautilus](../mobs/ZombieNautilus.md). Its registry ID is `minecraft:zombie_nautilus_spawn_egg`. [Registration][egg-zombie]

## Obtaining

It is listed in the ordinary Spawn Eggs category, so use the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative. No bundled recipe or natural loot route was found for this egg. Browser availability does not mean the mob naturally spawns. [Zombie Nautilus egg listing][egg-list-zombie]

## Usage

Use the egg on a block face with room for the mob, or aim at source water. Successful ordinary placement consumes one egg outside infinite-material mode. Spawn-egg placement has its own path; the mob's natural biome and depth conditions are not requirements for this use. [Placement, water targeting and consumption][egg-use]

## Behavior

Use it in a non-Peaceful world: normal placement refuses this mob in Peaceful. **Block or water placement does not create its possible Drowned rider**, because the mob skips that roll for spawn-item use. Using the egg directly on an existing Zombie Nautilus does not create a baby: its offspring method returns no child. [Rider rule][jockey] · [Offspring restriction][zombie] · [Egg offspring handling][egg-baby]

Using the egg on an enabled [Monster Spawner](../blocks/MonsterSpawner.md) changes the spawner's mob type and consumes an egg in the checked path. The server's spawner-block setting can refuse this operation. [Spawner interaction][egg-use]

## Notes

- The mob's complete care, combat and integration limits are on the [Zombie Nautilus page](../mobs/ZombieNautilus.md)
- Related: [other Nautilus egg](NautilusSpawnEgg.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f`. Checked registration, ordinary listing, block/water placement, direct-on-mob offspring handling and spawner interaction. No in-game egg, spawner or offspring test was run.

[egg-zombie]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/Items.java#L2013-L2015
[egg-list-zombie]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2123-L2123
[egg-use]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L49-L127
[egg-baby]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L182
[jockey]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/ZombieNautilus.java#L148-L176
[zombie]: https://github.com/HungLo2020/MattMC/blob/4532f95d76649fa60ddfcc5e7b9f7fea6f91ab7f/src/main/java/net/minecraft/world/entity/animal/nautilus/ZombieNautilus.java#L54-L92

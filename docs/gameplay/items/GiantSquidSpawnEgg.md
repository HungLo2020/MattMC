# Giant Squid Spawn Egg

The **Giant Squid Spawn Egg** places a [Giant Squid](../mobs/GiantSquid.md) for Creative aquariums, testing, and mapmaking. It is registered as `minecraft:giant_squid_spawn_egg` and points to the Giant Squid entity type. [Registration](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1885-L1886)

## Obtaining

The egg is included in the Creative inventory. No crafting recipe, loot entry, or other Survival acquisition route for this egg was found in the reviewed bundled source and data. Its Creative availability does not establish natural Giant Squid spawning. [Creative entry](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2023-L2027)

## Usage

- **On a block:** use the egg to place a squid at the clicked position if the block has no collision shape, or against the selected face otherwise. Successful placement consumes one egg unless the user has infinite materials. [Placement](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L76-L101) · [Consumption](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079)
- **At source water:** the egg also supports the standard source-fluid use path, subject to the player's build/use permissions. Place a display squid in water; its breathing and stranding behavior is described on the [mob page](../mobs/GiantSquid.md#keeping-a-giant-squid). [Fluid use](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L105-L127)
- **On a spawner:** it changes the spawner's entity type only when the server enables spawner blocks. This branch shrinks the egg stack by one. [Spawner use](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L59-L75)
- **On another Giant Squid:** it does not make a baby. The generic offspring path calls the mob's baby setter and rejects the result if it is not a baby; Giant Squid inherits the empty setter and the default false baby state. [Offspring path](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L157-L181) · [Baby setter](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/Mob.java#L1264-L1265) · [Baby state](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/LivingEntity.java#L532-L534)

Normal egg placement invokes entity creation and spawn finalization directly. It does not go through the natural-spawn selection or Giant Squid spawn-roll check, so the existence of a water predicate or roll setting does not establish an egg-use restriction. [Egg spawn call](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L89-L99) · [Entity creation](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1774)

## Related

- [Giant Squid](../mobs/GiantSquid.md)
- [Items](Items.md)

## Verification

Source-reviewed on **2026-10-02** at commit [`3e85592c4c78ebb420302360667a6c230dc0318d`](https://github.com/HungLo2020/MattMC/commit/3e85592c4c78ebb420302360667a6c230dc0318d). This is source-only verification; egg placement, spawner changes, and offspring interaction were not tested in-game.

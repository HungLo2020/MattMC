# Blobfish Spawn Egg

Blobfish Spawn Egg directly creates a [Blobfish](../mobs/Blobfish.md). Its item ID is `minecraft:blobfish_spawn_egg`, and it is listed in Creative's spawn-egg inventory.

## Placement

Use the egg on a block face with room for the mob. The general egg code places into an empty-collision clicked space or the adjacent face space otherwise. Permissions and server rules still apply.

Prepare a water habitat before using the egg. The live mob can lose air on land; creating it successfully does not make dry placement safe.

No Survival recipe or loot source for this spawn egg is established here. A Creative egg and a registered mob do not establish natural biome spawning; the mob article records the current availability limits.

## Related pages

- [Blobfish behavior and care](../mobs/Blobfish.md)
- [Items](Items.md)

## Sources and verification

Reviewed against `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source-defined behavior only; no in-game capture, breeding, combat, or persistence test.

- [Spawn egg registry](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
- [Creative egg inventory](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Generic placement behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/SpawnEggItem.java)

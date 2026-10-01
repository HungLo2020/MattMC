# Roadrunner Spawn Egg

Roadrunner Spawn Egg directly creates a [Roadrunner](../mobs/Roadrunner.md). Its item ID is `minecraft:roadrunner_spawn_egg`, and it is listed in Creative's spawn-egg inventory.

## Placement

Use the egg on a block face with room for the mob. The general egg code places into an empty-collision clicked space or the adjacent face space otherwise. Permissions and server rules still apply.

Prepare an enclosure before placement: the bird has a fast movement attribute, and its name-based speed change can make containment harder.

No Survival recipe or loot source for this spawn egg is established here. A Creative egg and a registered mob do not establish natural biome spawning; the mob article records the current availability limits.

## Related pages

- [Roadrunner behavior and care](../mobs/Roadrunner.md)
- [Items](Items.md)

## Sources and verification

Reviewed against `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source-defined behavior only; no in-game capture, breeding, combat, or persistence test.

- [Spawn egg registry](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
- [Creative egg inventory](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Generic placement behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/SpawnEggItem.java)

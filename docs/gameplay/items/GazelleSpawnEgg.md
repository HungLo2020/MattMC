# Gazelle Spawn Egg

Gazelle Spawn Egg directly creates a [Gazelle](../mobs/Gazelle.md). Its ID is `minecraft:gazelle_spawn_egg`, and it is listed in Creative's spawn-egg inventory.

## Using it

Use the egg on a block face with enough space for the animal. The generic egg code chooses the clicked space when its collision shape is empty, or the adjacent face space otherwise. Prepare an enclosure and follow the mob page's behavior guidance.

The spawned animal still has the current missing breeding-food tag limitation; Creative placement does not complete its Survival breeding integration.

No Survival crafting or loot source is established for this egg. Natural mob spawning must be verified separately from Creative egg availability.

## Related pages

- [Gazelle](../mobs/Gazelle.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No in-game spawning, breeding, combat, egg-throwing, or food test was run.

- [Item registry](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Placement behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/SpawnEggItem.java)

# Subterranodon Spawn Egg

Subterranodon Spawn Egg directly creates a [Subterranodon](../mobs/Subterranodon.md). It is registered as `minecraft:subterranodon_spawn_egg` and listed in Creative's spawn-egg inventory.

## Use

Use it on a block face with enough open space for the creature. This bypasses the placed egg's random-tick hatching process, but does not establish any natural Survival spawning or breeding route.

The generic spawn-egg interaction on an existing matching ageable mob calls its offspring method and creates a baby. Subterranodon's method returns another Subterranodon; this is separate from its incomplete breeding-and-laying goals.

Before mounting or breeding a spawned animal, read the mob article's **flight-input and egg-laying limitations**. A spawn egg does not repair those systems.

## Related pages

- [Subterranodon](../mobs/Subterranodon.md)
- [Hatchable Subterranodon Egg](SubterranodonEgg.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No running-world test was performed.

- [Egg registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1962)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Generic egg behavior](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/SpawnEggItem.java)
- [Subterranodon offspring and interactions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/living/SubterranodonEntity.java)

# Subterranodon Egg

A Subterranodon Egg is a placeable, hatchable block integrated from Alex's Caves. It is distinct from the [Subterranodon Spawn Egg](../items/SubterranodonSpawnEgg.md), which directly creates a mob.

## Obtaining and placing

The item and block are registered as `minecraft:subterranodon_egg`, and the item is included in Creative inventory. Natural generation or a working breeding source has not been established. The [mob's current egg-laying path](../mobs/Subterranodon.md#breeding-and-drops) is incomplete.

Place an egg above a **solid block**. The current habitat check uses solidity rather than requiring a particular cave biome or nesting material. Up to **four eggs** can occupy the same block position by placing more of the same egg there.

## Hatching

Eggs progress through hatch states 0, 1, and 2, then produce one baby per egg in the block and remove the egg block. Each random-tick opportunity has a **one-in-twenty** growth check, subject to the habitat and player requirements below. This is not one chance per ordinary game tick and does not establish a fixed number of minutes to hatch.

The `needs_player` block state defaults to false. If it is true, a non-spectator player must be within 15 blocks for hatching progress. At the moment babies spawn, the nearest non-spectator player within **10 blocks** becomes their owner, and they are ordered to sit. Stay nearby if you want ownership; merely placing the egg does not record you as its owner.

New hatchlings start at age -24,000 game ticks. They are babies, and cannot be mounted until adult. This page does not claim that all hatching or ownership behavior has been tested in-game.

## Protecting and collecting eggs

Keep player foot traffic away. The implementation can remove an egg when a player steps on it or falls onto it, and may make nearby animals of the same type target the trampler. Dinosaur entities are excluded from this trample check.

The bundled block loot table yields an egg item only for a tool with **Silk Touch**. It specifies one item, not a count scaled to the number of eggs in the block. Breaking and stacked-egg behavior should be tested before moving a valuable cluster; do not assume all four eggs are recovered.

## Related pages

- [Subterranodon](../mobs/Subterranodon.md)
- [Subterranodon Egg item](../items/SubterranodonEgg.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. No running-world test was performed.

- [Block registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/Blocks.java#L4800-L4804)
- [Correct hatch entity and four-egg limit](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/block/SubterranodonEggBlock.java)
- [Stacking and hatch count](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/block/MultipleDinosaurEggsBlock.java)
- [Habitat, random growth, ownership and trampling](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/block/DinosaurEggBlock.java)
- [Silk Touch loot](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/blocks/subterranodon_egg.json)
- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L876)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

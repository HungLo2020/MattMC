# Grizzly Bear Spawn Egg

The Grizzly Bear Spawn Egg places a [Grizzly Bear](../mobs/GrizzlyBear.md) for Creative building, testing, and mapmaking. Its item ID is `minecraft:grizzly_bear_spawn_egg`.

## Obtaining

The egg is listed in the Creative spawn-egg tab. No Survival crafting or loot source is established here. A spawn egg's existence does not establish natural spawning for the mob.

## Using the egg

Use the egg on a block to spawn its registered grizzly entity. The placement code uses the clicked space when its collision shape is empty, or the adjacent space in the clicked-face direction otherwise. Leave enough room for the adult bear's **1.7 × 1.95-block** body.

A newly placed wild adult can target a nearby player without first being hit. Prepare an enclosure and read the [bear's taming and behavior guide](../mobs/GrizzlyBear.md) before using it near a Survival player.

### Spawning a cub

Using a matching egg **on an existing grizzly** follows the ageable-mob offspring path and creates a **grizzly cub**. The offspring factory was corrected in [issue #778](https://github.com/HungLo2020/MattMC/issues/778); the egg path marks the new grizzly as a baby before spawning it.

### Spawners

Using the egg on a compatible spawner changes its entity type only when the server enables spawner blocks. That path is separate from ordinary block-face placement. Server permissions and settings still apply.

## Related pages

- [Grizzly Bear](../mobs/GrizzlyBear.md)
- [Raw Cod](RawCod.md), one of its accepted fish foods
- [Items](Items.md)

## Sources and verification

Reviewed against source snapshot `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. The offspring paragraph was rechecked after the merged #778 fix at `fb7d6979fb8d9773cfe05f084c6085f35feb885c` on 2026-10-02. These are source-defined rules, not an in-game test.

- [Egg registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1889)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2028)
- [Spawn egg placement and offspring path](https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/minecraft/world/item/SpawnEggItem.java)
- [Entity interaction dispatch](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/Mob.java#L1077-L1104)
- [Bear offspring override](https://github.com/HungLo2020/MattMC/blob/fb7d6979fb8d9773cfe05f084c6085f35feb885c/src/main/java/net/alexsmobs/entity/EntityGrizzlyBear.java#L646-L650)
- [Bear registration and dimensions](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L714-L720)

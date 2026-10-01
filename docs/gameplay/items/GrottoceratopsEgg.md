# Grottoceratops Egg

Grottoceratops Egg (`minecraft:grottoceratops_egg`) places a hatching species egg. It is distinct from the immediate-spawning [Grottoceratops Spawn Egg](GrottoceratopsSpawnEgg.md).

## Obtaining and collecting

The item is explicitly listed in Creative. A natural or working breeding supply is not established: current Grottoceratops breeding returns an ordinary **Turtle Egg** rather than this species egg.

An already placed named egg has a **Silk Touch** loot branch. Ordinary breaking does not select that item. The loot does not copy hatch progress into the recovered item, so re-placing an individual recovered egg begins with its default hatch state.

## Hatching and ownership

Place it above solid ground and keep players from walking on it. The [placed dinosaur egg guide](../blocks/DinosaurEggs.md) covers random growth, cracking stages, and trampling.

This single-egg block creates one baby Grottoceratops. The species does not enable hatching-based taming; standing nearby does not make the hatchling an owned mount or follower.

## Related pages

- [Grottoceratops](../mobs/Grottoceratops.md)
- [Placed dinosaur eggs](../blocks/DinosaurEggs.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game hatching, ownership, collection, or breeding test was run.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Shared hatch and ownership](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/DinosaurEggBlock.java)
- [Species egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/GrottoceratopsEggBlock.java)
- [Silk Touch loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/grottoceratops_egg.json)
- [Mob ownership and placeholder egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/GrottoceratopsEntity.java)

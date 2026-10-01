# Vallumraptor Egg

Vallumraptor Egg (`minecraft:vallumraptor_egg`) places a hatching species egg. It is distinct from the immediate-spawning [Vallumraptor Spawn Egg](VallumraptorSpawnEgg.md).

## Obtaining and collecting

The item is explicitly listed in Creative. A natural or working breeding supply is not established: current Vallumraptor breeding returns an ordinary **Dragon Egg** rather than this species egg.

An already placed named egg has a **Silk Touch** loot branch. Ordinary breaking does not select that item. The loot does not copy hatch progress into the recovered item, so re-placing an individual recovered egg begins with its default hatch state.

## Hatching and ownership

Place it above solid ground and keep players from walking on it. The [placed dinosaur egg guide](../blocks/DinosaurEggs.md) covers random growth, cracking stages, and trampling.

A Vallumraptor egg block can contain **one to four eggs** and hatches the stored count of babies. Each can become owned by the nearest non-spectator player within **10 blocks**, rather than necessarily the egg placer, and is ordered to sit. Without an eligible nearby player, that ownership branch does not assign an owner.

## Related pages

- [Vallumraptor](../mobs/Vallumraptor.md)
- [Placed dinosaur eggs](../blocks/DinosaurEggs.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game hatching, ownership, collection, or breeding test was run.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Creative listing](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)
- [Shared hatch and ownership](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/DinosaurEggBlock.java)
- [Species egg type](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/VallumraptorEggBlock.java)
- [Silk Touch loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/vallumraptor_egg.json)
- [Mob ownership and placeholder egg](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/entity/living/VallumraptorEntity.java)
- [Multiple-egg placement, removal, and hatch count](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/MultipleDinosaurEggsBlock.java)

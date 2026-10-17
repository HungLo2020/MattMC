# Platypus Egg

A **Platypus Egg** is the placeable item for `minecraft:platypus_egg`. It is separate from the [Platypus Spawn Egg](PlatypusSpawnEgg.md) and [Bucket of Platypus](BucketOfPlatypus.md). [Registration][items]

## Obtaining

Its ordinary category entry is available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Creative. Reliable breeding production is not established: the custom laying call is disabled, despite the block already being registered. See the [Platypus guide](../mobs/Platypus.md#fish-breeding-and-eggs) for the competing breeding goals. [Creative listing][creative] · [Disabled laying][platypus-lay]

## Usage

Use it to place an egg or add one to a cluster of fewer than four. Read the [placed Platypus Egg guide](../blocks/AnimalEggs.md#platypus-eggs) before choosing the ground: unsuitable support destroys the cluster on its next random tick. [Registration][items] · [Placement][platypus-cluster] · [Habitat failure][platypus-hatch]

## Behavior

This is a block item with no throwable-egg or food behavior in its registration. A placed cluster hatches through its own rules; that does not repair the animal’s disabled laying route. No bundled block-loot table was found to establish ordinary or Silk Touch recovery. [Block-item factory][block-items] · [Hatching and protection](../blocks/AnimalEggs.md#platypus-eggs)

## Notes

- Item and placed-block ID: `minecraft:platypus_egg`
- [Placed Platypus Egg guide](../blocks/AnimalEggs.md#platypus-eggs)
- [Platypus](../mobs/Platypus.md)
- [Items](Items.md)

Source-reviewed at `099b1184d1a7115915d880b436c8f58c1ed5a422` on 2026-10-02. No in-game acquisition, breeding, collection, or hatching test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L872-L875
[creative]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L949-L952
[platypus-lay]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L569-L586
[platypus-cluster]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockPlatypusEgg.java#L127-L148
[platypus-hatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/alexsmobs/block/BlockPlatypusEgg.java#L98-L125
[block-items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L2750-L2761

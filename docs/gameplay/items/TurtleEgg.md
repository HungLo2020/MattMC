# Turtle Egg

A **Turtle Egg** is the placeable item for `minecraft:turtle_egg`. Place it to hatch baby Turtles under the conditions in the block guide. [Registration][items]

## Obtaining

Turtles lay clusters through their Seagrass breeding route. Collect placed eggs with Silk Touch, or request its ordinary category entry through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative. The [Turtle Egg guide](../blocks/AnimalEggs.md#turtle-eggs) explains the parent’s home requirement and one-egg-at-a-time collection. No bundled crafting recipe was found. [Laying][turtle-lay] · [Silk Touch loot][turtle-loot] · [Creative listing][creative]

## Usage

Place it on sand-tag ground for hatching, or use it on a cluster of fewer than four eggs to add one. Secondary use (normally sneaking) skips this stacking path. [Placement][turtle-growth] · [Placed egg guide](../blocks/AnimalEggs.md#turtle-eggs)

## Behavior

This is a block item, not a throwable egg or food. An item recovered through the bundled loot table retains no hatch progress. Read the [block guide](../blocks/AnimalEggs.md#turtle-eggs) for hatching, trampling, Zombie attacks, and protecting a nest. [Registration][items] · [Block-item factory][block-items] · [Loot][turtle-loot]

## Notes

- Item and placed-block ID: `minecraft:turtle_egg`
- [Placed Turtle Egg guide](../blocks/AnimalEggs.md#turtle-eggs)
- [Turtle](../mobs/Turtle.md)
- [Items](Items.md)

Source-reviewed at `099b1184d1a7115915d880b436c8f58c1ed5a422` on 2026-10-02. No in-game acquisition, breeding, collection, or hatching test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L872-L875
[turtle-lay]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/animal/Turtle.java#L437-L483
[turtle-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/turtle_egg.json#L1-L33
[creative]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L949-L952
[turtle-growth]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/TurtleEggBlock.java#L132-L155
[block-items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L2750-L2761

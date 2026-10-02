# Sniffer Egg

A **Sniffer Egg** is the placeable item for `minecraft:sniffer_egg`. A placed egg hatches one baby Sniffer. [Registration][items]

## Obtaining

Brush warm ocean-ruin Suspicious Sand for a chance at an egg, breed ready adult Sniffers with Torchflower Seeds, or request its ordinary category entry through the [inventory item browser](../mechanics/InventoryBrowser.md) in Survival or Creative. Ordinary mining can recover a placed egg without Silk Touch. See the [Sniffer Egg guide](../blocks/AnimalEggs.md#sniffer-eggs) for the active acquisition routes. No bundled crafting recipe was found. [Archaeology loot][ruin-loot] · [Breeding result][sniffer-mate] · [Block loot][sniffer-loot] · [Creative listing][creative]

## Usage

Place the egg on Moss Block for faster hatching. It occupies one block and does not form a multi-egg cluster. The [block guide](../blocks/AnimalEggs.md#sniffer-eggs) explains the three scheduled stages and exact timing. [Hatching and schedule][sniffer-hatch] · [Boost tag][sniffer-boost]

## Behavior

This is a block item, not a throwable egg or food. Breaking and replacing it restarts its hatch progress. Its egg block has no special trampling callback. [Registration][items] · [Block-item factory][block-items] · [Loot][sniffer-loot] · [Placed behavior](../blocks/AnimalEggs.md#sniffer-eggs)

## Notes

- Item and placed-block ID: `minecraft:sniffer_egg`
- [Placed Sniffer Egg guide](../blocks/AnimalEggs.md#sniffer-eggs)
- [Sniffer](../mobs/Sniffer.md)
- [Items](Items.md)

Source-reviewed at `099b1184d1a7115915d880b436c8f58c1ed5a422` on 2026-10-02. No in-game acquisition, breeding, collection, or hatching test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L872-L875
[ruin-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/archaeology/ocean_ruin_warm.json#L1-L57
[sniffer-mate]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/animal/sniffer/Sniffer.java#L333-L341
[sniffer-loot]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sniffer_egg.json#L1-L21
[creative]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L949-L952
[sniffer-hatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SnifferEggBlock.java#L60-L93
[sniffer-boost]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/sniffer_egg_hatch_boost.json#L1-L5
[block-items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L2750-L2761

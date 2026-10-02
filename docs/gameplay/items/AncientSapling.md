# Ancient Sapling

**Ancient Sapling** (`minecraft:ancient_sapling`) grows through an active tree feature into Jungle Logs and Ancient Leaves. It appears in the Natural Blocks Creative tab. [Registration][blocks] [items][] [creative][] · [Grower and tree][grower] [tree]

## Obtaining

Ancient Leaves can drop a sapling through their [chance-based loot](../blocks/AncientPlants.md#ancient-leaves-decoration-decay-and-drops). A natural first starter was not established in the bundled generation data; Creative availability and this renewable loop are separate. Ordinary harvesting of a placed sapling returns one item. [Sapling loot][loot-ancient-sapling]

## Usage

Follow [Ancient Sapling growth](../blocks/AncientPlants.md#growing-an-ancient-sapling) for suitable ground, two growth stages, Bone Meal chances and clearance. The registered sapling has no giant-tree selection for a 2 × 2 or 3 × 3 arrangement. [Assigned grower][grower] [tree-grower]

It can also be displayed in a [Flower Pot](../blocks/FlowerPot.md#adding-and-removing-plants), where it does not grow into a tree. [Potted registration and interaction][blocks] [pot]

## Related pages

- [Ancient trees and plants](../blocks/AncientPlants.md), [Ancient Leaves](AncientLeaves.md), and [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[grower]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/alexscaves/server/block/grower/AncientTreeGrower.java
[tree]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/alexscaves/server/level/feature/AncientTreeFeature.java
[loot-ancient-sapling]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/ancient_sapling.json
[tree-grower]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/grower/TreeGrower.java
[pot]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/FlowerPotBlock.java

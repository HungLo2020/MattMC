# Ancient Leaves

**Ancient Leaves** (`minecraft:ancient_leaves`) are a placeable canopy block, explicitly listed in Natural Blocks Creative and used by the active Ancient Tree feature. [Registration][blocks] [items] [creative] · [Tree feature][tree]

## Obtaining

Use **Shears or Silk Touch** to recover the leaf block. Other harvesting follows the separate sapling/stick chances in [Ancient Leaves loot](../blocks/AncientPlants.md#ancient-leaves-decoration-decay-and-drops). Growing an Ancient Sapling supplies leaves once a sapling is available; a naturally generated starter supply remains unverified. [Loot][loot-ancient-leaves]

## Usage

Manually placed leaves become persistent and can be waterlogged. Read the [decay and tag distinctions](../blocks/AncientPlants.md#ancient-leaves-decoration-decay-and-drops) before treating them like every ordinary leaf type. In particular, they cannot support [Tree Stars](../blocks/AncientPlants.md#tree-star) in the bundled data. [Leaf behavior][leaves] · [Leaf tag and attachment][leaves-tag] [star]

## Related pages

- [Ancient trees and plants](../blocks/AncientPlants.md), [Ancient Sapling](AncientSapling.md), and [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[tree]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/alexscaves/server/level/feature/AncientTreeFeature.java
[loot-ancient-leaves]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/ancient_leaves.json
[leaves]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/LeavesBlock.java
[leaves-tag]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/leaves.json
[star]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/custom/TreeStarBlock.java

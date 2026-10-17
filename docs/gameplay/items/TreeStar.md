# Tree Star

**Tree Star** (`minecraft:tree_star`) is a placeable surface decoration, explicitly available in Natural Blocks Creative. A natural starter route, crafting recipe or Bone Meal duplication path was not established. [Registration][blocks] [items][] [creative][] · [Implemented behavior][star]

## Obtaining

Ordinary harvesting returns one Tree Star without requiring Shears or Silk Touch, subject to its explosion condition. Ancient Tree generation does not place these decorations. [Loot][loot-tree-star] · [Tree features][tree] [giant-tree]

## Usage

Click a suitable face to orient it outward; it can face up, down or sideways and can be waterlogged. Follow [Tree Star support](../blocks/AncientPlants.md#tree-star): sturdy faces and tagged leaves work, but Ancient Leaves fail both support checks in this snapshot. [Placement/support][star] · [Leaf support shape and tag][leaves] [leaves-tag]

## Related pages

- [Ancient trees, Flytraps and Tree Stars](../blocks/AncientPlants.md), [Flytrap](Flytrap.md), and [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[blocks]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[creative]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[star]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/custom/TreeStarBlock.java
[loot-tree-star]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/tree_star.json
[tree]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/alexscaves/server/level/feature/AncientTreeFeature.java
[giant-tree]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/alexscaves/server/level/feature/GiantAncientTreeFeature.java
[leaves]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/LeavesBlock.java
[leaves-tag]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/tags/block/leaves.json

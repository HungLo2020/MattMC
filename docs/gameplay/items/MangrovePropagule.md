# Mangrove Propagule

**Mangrove Propagule** (`minecraft:mangrove_propagule`) is the item form of the block described in [the shared tree-material guide](../blocks/TreeLeaves.md#mangrove-propagules). [Registration][s5]

Collect one by breaking a **mature, age-4 propagule**. Immature hanging forms drop nothing, even with Shears or Silk Touch. Mangrove Leaves produce the hanging form through [their Bone Meal interaction](../blocks/TreeLeaves.md#mangrove-propagules), rather than dropping it from the leaf loot table. [Propagule loot][s1] · [Leaf loot][s2]

Plant the item on farmland, a dirt-tag block, or Clay; it can be planted in source water. Item placement makes the planted, non-hanging form. For a small decoration, use a [Flower Pot](../blocks/FlowerPot.md). The shared guide covers valid support, maturation, fuel, and composting; it does not claim a fixed tree-growth time. [Placement and support][s3] · [Potted form][s4]

Related: [Placed behavior](../blocks/TreeLeaves.md#mangrove-propagules) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`. No in-game harvesting, crafting, or placement test was run. The shared block guide holds the checked recipes and behavior.

[s1]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/mangrove_propagule.json
[s2]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/mangrove_leaves.json
[s3]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/MangrovePropaguleBlock.java#L43-L75
[s4]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/Blocks.java#L2685-L2687
[s5]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java#L139-L144

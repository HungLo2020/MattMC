# Mangrove Leaves

**Mangrove Leaves** (`minecraft:mangrove_leaves`) is the item form of the block described in [the shared tree-material guide](../blocks/TreeLeaves.md#mangrove-leaves). [Registration][s3]

Collect the leaf block with **Shears or Silk Touch**. Other ordinary harvests can drop Sticks; the leaf's loot table has **no Mangrove Propagule drop**. [Tree Leaves](../blocks/TreeLeaves.md#fortune-chances) gives the checked chances and explains persistent placement and decay. [Leaf loot][s1]

For propagules, place the leaves with air beneath them and use [Bone Meal](BoneMeal.md). Follow the [hanging-propagule collection loop](../blocks/TreeLeaves.md#mangrove-propagules) before breaking the result. [Leaf Bone Meal callback][s2]

Related: [Placed behavior](../blocks/TreeLeaves.md#mangrove-leaves) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `688be47ebeaa38beb06130efddf19484a8b81580`. No in-game harvesting, crafting, or placement test was run. The shared block guide holds the checked recipes and behavior.

[s1]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/resources/data/minecraft/loot_table/blocks/mangrove_leaves.json
[s2]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/level/block/MangroveLeavesBlock.java#L31-L45
[s3]: https://github.com/HungLo2020/MattMC/blob/688be47ebeaa38beb06130efddf19484a8b81580/src/main/java/net/minecraft/world/item/Items.java#L220-L278

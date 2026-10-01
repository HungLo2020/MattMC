# Pewen Pines

Pewen Pines is a small, non-colliding plant block registered as `minecraft:pewen_pines`. The [Pewen tree feature](../blocks/Pewen.md) places it above the top of its trunk; it is distinct from the leafy tip state of a [Pewen Branch](PewenBranch.md).

## Obtaining

Use **Shears or Silk Touch** on placed Pewen Pines to select its own item. Otherwise, its loot table can give a Pewen Sapling plus a chance-based resource pool containing Sticks and [Pine Nuts](PineNuts.md). The resource pool selects one eligible entry; neither resource is a guaranteed drop.

The sapling chance is 5% without Fortune, then 6.25%, about 8.33%, and 10% for Fortune I–III. Explosions also have survival/decay conditions, so these figures are not promises of explosion recovery.

The sapling-to-tree route is wired, but finding naturally placed Pewen trees remains unverified. Creative provides Pines for decoration and testing.

## Placement and decoration

Pewen Pines needs a **sturdy upper face directly below it**. Its survival check is broader than a dirt-only planting rule. It has no collision, breaks instantly, and is replaceable by placement.

A potted Pewen Pines variant is registered through the standard Flower Pot block. Pines is not a sapling and does not itself use the Pewen sapling's tree grower. For growing a tree, use [Pewen Sapling](PewenSapling.md).

## Related pages

- [Pewen family](../blocks/Pewen.md)
- [Pewen Branch](PewenBranch.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game placement, harvesting, eating, or tree-generation test was run. Natural Pewen forest placement remains unverified.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Block registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Tree construction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/level/feature/PewenTreeFeature.java)
- [Ground support](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/PewenPinesBlock.java)
- [Pines loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/pewen_pines.json)
- [Resource-pool selection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java)

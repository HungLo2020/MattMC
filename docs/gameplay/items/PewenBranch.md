# Pewen Branch

Pewen Branch is the thin, horizontally extending crown block of a [Pewen tree](../blocks/Pewen.md), registered as `minecraft:pewen_branch`.

## Obtaining

The configured Pewen tree builds branches around its trunk and crown. The sapling is wired to this tree feature, but a naturally generated Pewen forest has not been established. Creative also provides the block.

Harvest a branch with **Shears or Silk Touch** to select the branch item from its loot table. Without either, the table instead allows a Pewen Sapling and a separate chance-based resource pool containing Sticks and [Pine Nuts](PineNuts.md). These resources are not guaranteed, and the resource pool chooses one eligible entry rather than awarding every listed resource.

The sapling chance is 5% without Fortune, increasing to 6.25%, about 8.33%, and 10% for Fortune I–III. The loot table does not require the branch's visible `pines` state for these drops.

## Placement and support

Branches use eight horizontal orientations, including diagonals. Placement starts from the player's facing and searches for a supported orientation. Support can be another Pewen Branch or a block with a full-block collision shape on the connecting side.

Removing the required support schedules a break check; unsupported branches are destroyed and nearby branches are scheduled for checks too. Keep support in place when building a canopy rather than treating branches as floating decorations.

Branches can be waterlogged. Their leafy-tip appearance is controlled by the neighboring branch on the outward side: extending a row can change the earlier branch's `pines` state. This is a visual block state, not a sap-extraction interaction.

## Related pages

- [Pewen family](../blocks/Pewen.md)
- [Pewen Pines](PewenPines.md)
- [Pewen Sapling](PewenSapling.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game placement, harvesting, eating, or tree-generation test was run. Natural Pewen forest placement remains unverified.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Block registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Tree construction](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/level/feature/PewenTreeFeature.java)
- [Placement, support, waterlogging, and tip state](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexscaves/server/block/PewenBranchBlock.java)
- [Branch loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/pewen_branch.json)
- [Resource-pool selection](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/LootPool.java)

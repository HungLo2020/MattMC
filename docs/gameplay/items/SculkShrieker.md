# Sculk Shrieker

**Sculk Shrieker** (`minecraft:sculk_shrieker`) is the inventory form of the placed block. Its canonical [block guide](../blocks/SculkShrieker.md) owns placement and behavior. [Item registration][items]

## Obtaining

Collect it with Silk Touch from [generated or catalyst-grown sculk](../blocks/Sculk.md#finding-and-collecting-the-family). No direct producing recipe was found in the checked inventory.

## Usage

Place it for its player-attributed shriek behavior. Ordinary item placement has can_summon=false, even when the item was collected from a naturally summoning block.

## Behavior

Without Silk Touch, ordinary mining gives 5 XP and no item. The plain loot does not preserve the original summoning state. See [warning and summoning gates](../blocks/SculkShrieker.md#warning-levels-and-summoning) before interacting with generated Shriekers. [Block loot][loot-sculk_shrieker]

## Notes

This item places `minecraft:sculk_shrieker`. Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`; no in-game acquisition, placement or harvesting test was run. The linked block guide records the active behavior sources and limits.

Related: [Placed-block guide](../blocks/SculkShrieker.md) · [Family mining table](../blocks/Sculk.md#mining-and-experience) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L578-L581
[loot-sculk_shrieker]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_shrieker.json#L1-L33

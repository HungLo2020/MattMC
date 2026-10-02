# Sculk

**Sculk** (`minecraft:sculk`) is the inventory form of the placed block. Its canonical [block guide](../blocks/Sculk.md#sculk) owns placement and behavior. [Item registration][items]

## Obtaining

Collect it with Silk Touch, find a selected Ancient City chest entry, or grow sculk with a Catalyst. See [the family acquisition guide](../blocks/Sculk.md#finding-and-collecting-the-family) for the checked routes.

## Usage

Place it as a full building block or as part of a catalyst-driven growing area. It does not detect vibrations or start spreading just because it is placed.

## Behavior

Ordinary mining without Silk Touch gives 1 XP and no Sculk item. Silk Touch gives one block and no XP. [Block loot][loot-sculk]

## Notes

This item places `minecraft:sculk`. Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`; no in-game acquisition, placement or harvesting test was run. The linked block guide records the active behavior sources and limits.

Related: [Placed-block guide](../blocks/Sculk.md#sculk) · [Family mining table](../blocks/Sculk.md#mining-and-experience) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L578-L581
[loot-sculk]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk.json#L1-L33

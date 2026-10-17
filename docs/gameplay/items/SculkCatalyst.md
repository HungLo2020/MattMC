# Sculk Catalyst

**Sculk Catalyst** (`minecraft:sculk_catalyst`) is the inventory form of the placed block. Its canonical [block guide](../blocks/Sculk.md#sculk-catalyst) owns placement and behavior. [Item registration][items]

## Obtaining

Collect a placed Catalyst with Silk Touch, find a selected Ancient City chest entry, or obtain the Warden loot drop. See [the checked acquisition routes](../blocks/Sculk.md#finding-and-collecting-the-family).

## Usage

Place it beside eligible ground to turn nearby eligible living-entity deaths into sculk spread. It consumes that death’s XP reward rather than collecting existing XP orbs.

## Behavior

Without Silk Touch, ordinary mining gives 5 XP and no Catalyst. The placed block emits light level 6; exact death range and spreading limits belong to the block guide. [Block loot][loot-sculk_catalyst]

## Notes

This item places `minecraft:sculk_catalyst`. Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`; no in-game acquisition, placement or harvesting test was run. The linked block guide records the active behavior sources and limits.

Related: [Placed-block guide](../blocks/Sculk.md#sculk-catalyst) · [Family mining table](../blocks/Sculk.md#mining-and-experience) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L578-L581
[loot-sculk_catalyst]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_catalyst.json#L1-L33

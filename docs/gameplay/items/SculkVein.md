# Sculk Vein

**Sculk Vein** (`minecraft:sculk_vein`) is the inventory form of the placed block. Its canonical [block guide](../blocks/Sculk.md#sculk-vein) owns placement and behavior. [Item registration][items]

## Obtaining

Collect generated or catalyst-spread Veins with Silk Touch. The [family guide](../blocks/Sculk.md#finding-and-collecting-the-family) documents generation and growth. There is no direct producing recipe in the checked recipe inventory.

## Usage

Place it on supported floor, ceiling or wall faces. Multiple faces can share one position, and the covering can be waterlogged.

## Behavior

Silk Touch gives one Vein item per occupied face. Without Silk Touch, including with ordinary Shears, it gives no item or XP. Collect it before removing its support. [Block loot][loot-sculk_vein]

## Notes

This item places `minecraft:sculk_vein`. Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`; no in-game acquisition, placement or harvesting test was run. The linked block guide records the active behavior sources and limits.

Related: [Placed-block guide](../blocks/Sculk.md#sculk-vein) · [Family mining table](../blocks/Sculk.md#mining-and-experience) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L578-L581
[loot-sculk_vein]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_vein.json#L1-L127

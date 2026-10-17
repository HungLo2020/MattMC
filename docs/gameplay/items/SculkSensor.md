# Sculk Sensor

**Sculk Sensor** (`minecraft:sculk_sensor`) is the inventory form of the placed block. Its canonical [block guide](../blocks/SculkSensors.md#sculk-sensor) owns placement and behavior. [Item registration][sensor-items]

## Obtaining

Collect a generated or catalyst-grown Sensor with Silk Touch, or find a selected Ancient City chest entry. See [the family acquisition guide](../blocks/Sculk.md#finding-and-collecting-the-family).

## Usage

Place it to detect eligible vibrations and produce distance-based redstone pulses. A Comparator reads event frequency. It is also an ingredient in [the Calibrated Sensor recipe](../blocks/Amethyst.md#shard-recipes-and-other-uses).

## Behavior

Without Silk Touch, ordinary mining gives 5 XP and no Sensor. Use [the Sensor guide](../blocks/SculkSensors.md) for range, timing, wool, sneaking, waterlogging and direct-step exceptions. [Block loot][loot-sculk_sensor]

## Notes

This item places `minecraft:sculk_sensor`. Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`; no in-game acquisition, placement or harvesting test was run. The linked block guide records the active behavior sources and limits.

Related: [Placed-block guide](../blocks/SculkSensors.md#sculk-sensor) · [Family mining table](../blocks/Sculk.md#mining-and-experience) · [Items](Items.md)

[loot-sculk_sensor]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_sensor.json#L1-L33
[sensor-items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L1029-L1030

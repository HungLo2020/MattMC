# Calibrated Sculk Sensor

**Calibrated Sculk Sensor** (`minecraft:calibrated_sculk_sensor`) is the inventory form of the placed block. Its canonical [block guide](../blocks/SculkSensors.md#calibrated-sculk-sensor) owns placement and behavior. [Item registration][sensor-items]

## Obtaining

Craft it using [the Amethyst recipe guide](../blocks/Amethyst.md#shard-recipes-and-other-uses). The checked direct recipe produces one Calibrated Sensor.

## Usage

Place it to detect eligible vibrations within 16 blocks. Supply back-side redstone power to choose an event frequency, or use zero input to leave it unfiltered.

## Behavior

Moving the crafted block still requires Silk Touch: ordinary mining without it gives 5 XP and no item. Output strength, Comparator frequency and input strength have different roles; see the block guide. [Block loot][loot-calibrated_sculk_sensor] · [Recipe][recipe]

## Notes

This item places `minecraft:calibrated_sculk_sensor`. Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`; no in-game acquisition, placement or harvesting test was run. The linked block guide records the active behavior sources and limits.

Related: [Placed-block guide](../blocks/SculkSensors.md#calibrated-sculk-sensor) · [Family mining table](../blocks/Sculk.md#mining-and-experience) · [Items](Items.md)

[loot-calibrated_sculk_sensor]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/calibrated_sculk_sensor.json#L1-L33
[recipe]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/recipe/crafting/calibrated_sculk_sensor.json#L1-L16
[sensor-items]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/item/Items.java#L1029-L1030

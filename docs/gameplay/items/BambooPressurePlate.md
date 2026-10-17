# Bamboo Pressure Plate

**Bamboo Pressure Plate** (`minecraft:bamboo_pressure_plate`) is a floor-mounted redstone control that detects eligible entities, including dropped items. Its exact material entry is in [Pressure plates](../blocks/PressurePlates.md#bamboo-pressure-plate). [Item registration][item]

## Obtaining

Put **2 [Bamboo Planks](BambooPlanks.md) side by side in one row → 1 Bamboo Pressure Plate**. This fits the inventory grid. Raw Bamboo, Bamboo Mosaic, and other plank materials are not accepted inputs. [Recipe][recipe]

Mining normally returns **1 Bamboo Pressure Plate**, including by hand. An unbroken axe is efficient; see [crafting and collecting](../blocks/PressurePlates.md#crafting-and-collecting). [Loot][loot]

## Usage

Place it on a block whose upper face supplies **rigid or center support**; a normal full solid floor is a simple choice. A plate cannot mount on a wall or ceiling. Losing valid support breaks it, and hand interaction does not press it. See [placement and output](../blocks/PressurePlates.md#placement-and-output). [Support and contact checks][plate]

## Behavior

It supplies **signal 15 while at least one eligible entity overlaps its detection region**, otherwise 0. Players, mobs, dropped items, and arrows can qualify; spectators and entities that ignore block triggers do not. [Bamboo sensitivity][sets] · [Binary detection][binary] · [Shared exclusions][plate]

While powered, it rechecks occupants every **20 game ticks**. It can remain powered while occupied, and departure is noticed at the next scheduled empty check. This interval is not a fixed button pulse. See [activation and release timing](../blocks/PressurePlates.md#activation-and-release-timing). [Scheduled checks][plate]

## Notes

Related: [Bamboo Button](BambooButton.md) · [Pressure plate water and fuel rules](../blocks/PressurePlates.md#water-pistons-and-fuel) · [Items](Items.md)

Source-reviewed on **2026-10-03** at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716`. No in-game crafting, mining, placement, or circuit test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1065
[recipe]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/recipe/crafting/bamboo_pressure_plate.json
[loot]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/loot_table/blocks/bamboo_pressure_plate.json
[plate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/BasePressurePlateBlock.java#L25-L155
[sets]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/state/properties/BlockSetType.java#L181-L198
[binary]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/PressurePlateBlock.java#L33-L50

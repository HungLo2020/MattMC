# Stone Pressure Plate

The Stone Pressure Plate item places `minecraft:stone_pressure_plate`, a floor sensor with output 15 when qualifying living entities overlap it.

## Obtaining and use

Craft one from two ordinary Stone placed side by side. See the [pressure-plate recipe table](../blocks/PressurePlates.md#crafting-and-collecting). Its pickaxe tag affects mining, but this registration does not require a specific tool for the normal block-item drop.

Place it on valid upper support. It detects players and other qualifying living entities but ignores dropped-item entities. It rechecks occupancy every 20 game ticks while powered. The [Pressure plates guide](../blocks/PressurePlates.md) explains entity exclusions, support, output, and release timing.

## Related pages

- [Pressure plates](../blocks/PressurePlates.md)
- [Oak Pressure Plate](OakPressurePlate.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run. The canonical block guide contains the recipe, loot, tool, and placed-behavior evidence.

- [Block-item registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Redstone Creative entries](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

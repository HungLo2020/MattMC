# Oak Pressure Plate

The Oak Pressure Plate item places `minecraft:oak_pressure_plate`, a floor sensor that provides output 15 when at least one eligible entity overlaps it.

## Obtaining and use

Craft one from two Oak Planks placed side by side, using the [pressure-plate recipe table](../blocks/PressurePlates.md#crafting-and-collecting). The recipe requires Oak Planks specifically. Ordinary Survival mining returns the plate without a special tool requirement; an axe is its tagged tool.

Oak detects a broader range than Stone, including dropped items. Spectators and entities that ignore block triggers are excluded. Its powered occupancy recheck is 20 game ticks. See the [Pressure plates guide](../blocks/PressurePlates.md) for support, output, and a small circuit.

## Related pages

- [Pressure plates](../blocks/PressurePlates.md)
- [Stone Pressure Plate](StonePressurePlate.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run. The canonical block guide contains the recipe, loot, tool, and placed-behavior evidence.

- [Block-item registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Redstone Creative entries](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

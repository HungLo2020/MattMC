# Heavy Weighted Pressure Plate

The Heavy Weighted Pressure Plate item places `minecraft:heavy_weighted_pressure_plate`, the iron plate whose signal rises by one level for each ten eligible entities, rounded up and capped at 15.

## Obtaining and use

Craft one from two Iron Ingots placed side by side using the [pressure-plate recipe table](../blocks/PressurePlates.md#crafting-and-collecting). A pickaxe is its tagged tool, but the registration does not require a correct tool for its ordinary block-item drop.

One to ten eligible entities produce strength 1; 141 or more reach 15. A dropped stack counts as one entity. While powered, the count is rechecked every 10 game ticks. See the [Pressure plates guide](../blocks/PressurePlates.md) for exact filtering, support, and release behavior.

## Related pages

- [Pressure plates](../blocks/PressurePlates.md)
- [Light Weighted Pressure Plate](LightWeightedPressurePlate.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run. The canonical block guide contains the recipe, loot, tool, and placed-behavior evidence.

- [Block-item registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Redstone Creative entries](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

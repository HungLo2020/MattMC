# Light Weighted Pressure Plate

The Light Weighted Pressure Plate item places `minecraft:light_weighted_pressure_plate`, the gold plate that gives one signal level per eligible entity, capped at 15.

## Obtaining and use

Craft one from two Gold Ingots placed side by side, as shown in the [pressure-plate recipe table](../blocks/PressurePlates.md#crafting-and-collecting). A pickaxe is its tagged tool, but a correct tool is not required for its ordinary block-item drop in this registration.

It counts entity objects, not the number of items inside dropped stacks. While powered, it rechecks the count every 10 game ticks. The [Pressure plates guide](../blocks/PressurePlates.md) covers placement, entity filtering, low-strength output, and timing.

## Related pages

- [Pressure plates](../blocks/PressurePlates.md)
- [Heavy Weighted Pressure Plate](HeavyWeightedPressurePlate.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `3e85592c4c78ebb420302360667a6c230dc0318d` on 2026-10-02. No gameplay test was run. The canonical block guide contains the recipe, loot, tool, and placed-behavior evidence.

- [Block-item registrations](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java)
- [Redstone Creative entries](https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

# Smelting and cooking

Smelting uses a [Furnace](../blocks/Furnace.md) to transform an input through a loaded smelting recipe. Smoking, blasting, and campfire cooking use separate recipe types; an input accepted by one device is not automatically accepted by another.

## Choose the right device

| Device | Recipe type | Example verified here |
| --- | --- | --- |
| Furnace | Smelting | Cobblestone → Stone; Raw Cod → Cooked Cod |
| Smoker | Smoking | Raw Cod → Cooked Cod |
| Blast Furnace | Blasting | Check a blasting recipe for the specific input; not a universal faster furnace |
| Campfire | Campfire cooking | Raw Cod → Cooked Cod |

Cod and Trilocaris Tail have 200-tick furnace, 100-tick smoker, and 600-tick campfire recipes in this snapshot. At 20 game ticks per second, those are 10, 5, and 30 seconds. This does not establish those times for every other item.

## Fuel and output

For furnace-style devices, put the ingredient in the input slot and a valid fuel in the fuel slot, with room for the result in the output. The [Furnace guide](../blocks/Furnace.md#fuel-planning) lists default fuel values and why lit fuel can be wasted when processing stops.

The smoker and blast furnace halve their fuel burn duration relative to the furnace. Faster recipe processing therefore should not be confused with automatically doubling the number of items one piece of fuel can process.

Experience is tracked through recipe use and device-specific collection behavior. A numeric XP field in a recipe is not proof of identical experience delivery on every device.

## If nothing cooks

1. Check that the item has a recipe for this device's recipe type.
2. Check fuel validity and whether output is full or contains a different item.
3. Check source-reviewed integration caveats for imported content.
4. Use the exact current recipe rather than assuming a recipe from another Minecraft or mod version.

## Detailed examples

- [Cooked Cod](../items/CookedCod.md)
- [Cooked Trilocaris Tail](../items/CookedTrilocarisTail.md)
- [Stone](../blocks/Stone.md)
- [Furnace](../blocks/Furnace.md)
- [Gameplay](../Gameplay.md)

## Sources and verification

Source-reviewed at `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. This is not a gameplay test; data packs and later builds can change recipes and tags.

- [Furnace recipe type](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/FurnaceBlockEntity.java)
- [Smoker fuel and recipe type](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/SmokerBlockEntity.java)
- [Blast furnace fuel and recipe type](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/BlastFurnaceBlockEntity.java)
- [Campfire recipe handling](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java)
- [Furnace processing](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java)

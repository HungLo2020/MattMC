# Waxed Weathered Copper Door

Waxed Weathered Copper Door (`minecraft:waxed_weathered_copper_door`) is the **Weathered, waxed** item for a two-block-high doorway. [Item registration][item-registration] · [Block registration][registration]

## Obtaining

Combine **1 [Weathered Copper Door](WeatheredCopperDoor.md) + 1 [Honeycomb](Honeycomb.md)** in any arrangement to make **1 Waxed Weathered Copper Door**. This preserves the Weathered finish. [Waxing recipe][wax-recipe]

You can also use one Honeycomb on the placed [Weathered Copper Door](WeatheredCopperDoor.md) and collect the waxed result. [Waxing interaction][waxing]

Ordinary Survival collection returns **1 Waxed Weathered Copper Door for the whole door**, through its lower-half loot entry. This door has **no correct-tool requirement for drops**; a pickaxe is still the efficient mining tool. See [copper mining and collection](../blocks/CopperConstruction.md#mining-and-collection). [Exact variant loot][loot] · [Block registration][registration]

## Usage

One item places a door occupying **two vertically adjacent blocks**. It opens by hand and responds to redstone in this finish. Use the [door guide](../blocks/CopperConstruction.md#doors-and-trapdoors) for support, hinge placement, power changes and Wind Charge interaction. [Door placement and controls][controls]

## Behavior

The placed block stays at the **Weathered** stage while waxed. One use of an **unbroken axe** removes the wax and produces [Weathered Copper Door](WeatheredCopperDoor.md), keeping that stage; removing wax does not return Honeycomb. [Wax removal][axe]

After removing the wax, a separate axe use scrapes the placed block back to [Exposed Copper Door](ExposedCopperDoor.md). [Previous-stage mapping][stages]

**Hold secondary use, normally sneak, when waxing or scraping** so opening the door does not take priority. The shared [waxing and scraping guide](../blocks/CopperConstruction.md#waxing-and-scraping) covers these controls and axe durability.

## Notes

- This item and [Weathered Copper Door](WeatheredCopperDoor.md) use the **same bundled item-display model**. Check the item name or ID to distinguish wax status; the model alone does not determine the placed block’s mechanics. [This item definition][display] · [Paired item definition][paired-display]
- See [Copper construction](../blocks/CopperConstruction.md) for shared placed-block rules, complete recipe comparisons and collection details. [Items](Items.md) lists the individual inventory entries.

## Sources and verification

Source-reviewed at `f5473e41dc4af8ced756db517fada27288df07a3` on **2026-10-04**. Recipes and loot describe bundled data; data packs can replace them, and resource packs can change presentation. No in-game crafting, mining, oxidation, redstone or rendering test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L6239-L6270
[item-registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1081-L1088
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_door_from_honeycomb.json
[waxing]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/HoneycombItem.java#L29-L114
[loot]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_door.json
[controls]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L136-L277
[axe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L118
[stages]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java
[display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/waxed_weathered_copper_door.json
[paired-display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/weathered_copper_door.json

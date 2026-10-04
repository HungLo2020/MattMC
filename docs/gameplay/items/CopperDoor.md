# Copper Door

Copper Door (`minecraft:copper_door`) is the **Unaffected, unwaxed** item for a two-block-high doorway. [Item registration][item-registration] · [Block registration][registration]

## Obtaining

At a Crafting Table, arrange **6 [Copper Ingots](CopperIngot.md) in 2 columns of 3** to make **3 Copper Doors**. The output is this Unaffected, unwaxed form. [Crafting recipe][crafting]

Ordinary Survival collection returns **1 Copper Door for the whole door**, through its lower-half loot entry. This door has **no correct-tool requirement for drops**; a pickaxe is still the efficient mining tool. See [copper mining and collection](../blocks/CopperConstruction.md#mining-and-collection). [Exact variant loot][loot] · [Block registration][registration]

## Usage

One item places a door occupying **two vertically adjacent blocks**. It opens by hand and responds to redstone in this finish. Use the [door guide](../blocks/CopperConstruction.md#doors-and-trapdoors) for support, hinge placement, power changes and Wind Charge interaction. [Door placement and controls][controls]

## Behavior

When placed and receiving random ticks, this block can advance to [Exposed Copper Door](ExposedCopperDoor.md). Oxidation has no fixed completion time; see [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing). [Stage transitions][stages]

This is the first oxidation stage, so there is no earlier finish to scrape.

To keep this finish, use one Honeycomb on the placed block or combine **1 of this item + 1 Honeycomb** shapelessly to make **1 [Waxed Copper Door](WaxedCopperDoor.md)**. [Exact waxing recipe][wax-recipe] · [Placed waxing][waxing]

**Hold secondary use, normally sneak, when waxing or scraping** so opening the door does not take priority. The shared [waxing and scraping guide](../blocks/CopperConstruction.md#waxing-and-scraping) covers these controls and axe durability.

## Notes

- This item and [Waxed Copper Door](WaxedCopperDoor.md) use the **same bundled item-display model**. Check the item name or ID to distinguish wax status; the model alone does not determine the placed block’s mechanics. [This item definition][display] · [Paired item definition][paired-display]
- See [Copper construction](../blocks/CopperConstruction.md) for shared placed-block rules, complete recipe comparisons and collection details. [Items](Items.md) lists the individual inventory entries.

## Sources and verification

Source-reviewed at `f5473e41dc4af8ced756db517fada27288df07a3` on **2026-10-04**. Recipes and loot describe bundled data; data packs can replace them, and resource packs can change presentation. No in-game crafting, mining, oxidation, redstone or rendering test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L6239-L6270
[item-registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1081-L1088
[crafting]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/copper_door.json
[loot]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/loot_table/blocks/copper_door.json
[controls]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/DoorBlock.java#L136-L277
[stages]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_door_from_honeycomb.json
[waxing]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/HoneycombItem.java#L29-L114
[display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/copper_door.json
[paired-display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/waxed_copper_door.json

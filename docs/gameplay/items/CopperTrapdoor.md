# Copper Trapdoor

Copper Trapdoor (`minecraft:copper_trapdoor`) is the **Unaffected, unwaxed** item for a hinged panel for floors or walls. [Item registration][item-registration] · [Block registration][registration]

## Obtaining

Arrange **4 [Copper Ingots](CopperIngot.md) in a 2 × 2 square** to make **1 Copper Trapdoor**. This fits the inventory crafting grid and produces the Unaffected, unwaxed form. [Crafting recipe][crafting]

To collect **1 matching Copper Trapdoor** from the placed block in Survival, use an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe**. Wooden and Golden Pickaxes do not qualify. An axe changes the finish but is not the harvesting tool; see [copper mining and collection](../blocks/CopperConstruction.md#mining-and-collection). [Exact variant loot][loot] · [Block registration][registration]

## Usage

One item places one trapdoor. It opens by hand, responds to redstone and can be waterlogged in this finish. The [door and trapdoor guide](../blocks/CopperConstruction.md#doors-and-trapdoors) explains placement, panel orientation, stored water and power changes. [Trapdoor placement and controls][controls]

## Behavior

When placed and receiving random ticks, this block can advance to [Exposed Copper Trapdoor](ExposedCopperTrapdoor.md). Oxidation has no fixed completion time; see [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing). [Stage transitions][stages]

This is the first oxidation stage, so there is no earlier finish to scrape.

To keep this finish, use one Honeycomb on the placed block or combine **1 of this item + 1 Honeycomb** shapelessly to make **1 [Waxed Copper Trapdoor](WaxedCopperTrapdoor.md)**. [Exact waxing recipe][wax-recipe] · [Placed waxing][waxing]

**Hold secondary use, normally sneak, when waxing or scraping** so opening the trapdoor does not take priority. The shared [waxing and scraping guide](../blocks/CopperConstruction.md#waxing-and-scraping) covers these controls and axe durability.

## Notes

- This item and [Waxed Copper Trapdoor](WaxedCopperTrapdoor.md) use the **same bundled item-display model**. Check the item name or ID to distinguish wax status; the model alone does not determine the placed block’s mechanics. [This item definition][display] · [Paired item definition][paired-display]
- See [Copper construction](../blocks/CopperConstruction.md) for shared placed-block rules, complete recipe comparisons and collection details. [Items](Items.md) lists the individual inventory entries.

## Sources and verification

Source-reviewed at `f5473e41dc4af8ced756db517fada27288df07a3` on **2026-10-04**. Recipes and loot describe bundled data; data packs can replace them, and resource packs can change presentation. No in-game crafting, mining, oxidation, redstone or rendering test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L6271-L6313
[item-registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1102-L1109
[crafting]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/copper_trapdoor.json
[loot]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/loot_table/blocks/copper_trapdoor.json
[controls]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L92-L193
[stages]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_trapdoor_from_honeycomb.json
[waxing]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/HoneycombItem.java#L29-L114
[display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/copper_trapdoor.json
[paired-display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/waxed_copper_trapdoor.json

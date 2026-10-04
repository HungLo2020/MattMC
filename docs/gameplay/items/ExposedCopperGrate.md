# Exposed Copper Grate

Exposed Copper Grate (`minecraft:exposed_copper_grate`) is the **Exposed, unwaxed** item for a waterloggable building block. [Item registration][item-registration] · [Block registration][registration]

## Obtaining

- **Stonecutter:** 1 [Exposed Copper](ExposedCopper.md) → **4 Exposed Copper Grates**. This uses the plain full copper block in exactly this stage and wax state. [Stonecutting recipe][stonecutting]
- **Crafting Table:** place 4 of those same full blocks at top-center, middle-left, middle-right and bottom-center, leaving the center and corners empty → **4 Exposed Copper Grates**. [Crafting recipe][crafting]

Stonecutting therefore gives four times as many grates per full block as crafting. The complete [conversion table](../blocks/CopperConstruction.md#stonecutter-conversions) compares the other copper shapes.

Another route is to let a placed [Copper Grate](CopperGrate.md) oxidize to this stage before collection. A matching full copper block can also be aged first and then converted using the recipes above. [Stage transitions][stages]

To collect **1 matching Exposed Copper Grate** from the placed block in Survival, use an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe**. Wooden and Golden Pickaxes do not qualify. An axe changes the finish but is not the harvesting tool; see [copper mining and collection](../blocks/CopperConstruction.md#mining-and-collection). [Exact variant loot][loot] · [Block registration][registration]

## Usage

One item places one grate. It can hold water, but its visible grid still has full-block collision. It is not a redstone conductor. See [grates](../blocks/CopperConstruction.md#grates) for water handling, light behavior and collision. [Waterlogged grate implementation][water] · [Block registration][registration]

## Behavior

When placed and receiving random ticks, this block can advance to [Weathered Copper Grate](WeatheredCopperGrate.md). Oxidation has no fixed completion time; see [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing). [Stage transitions][stages]

Use an **unbroken axe** on the placed block to return it one stage to [Copper Grate](CopperGrate.md). [Scraping interaction][axe]

To keep this finish, use one Honeycomb on the placed block or combine **1 of this item + 1 Honeycomb** shapelessly to make **1 [Waxed Exposed Copper Grate](WaxedExposedCopperGrate.md)**. [Exact waxing recipe][wax-recipe] · [Placed waxing][waxing]

Waxing and scraping preserve stored water. See [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for conversion controls and axe durability.

## Notes

- This item and [Waxed Exposed Copper Grate](WaxedExposedCopperGrate.md) use the **same bundled item-display model**. Check the item name or ID to distinguish wax status; the model alone does not determine the placed block’s mechanics. [This item definition][display] · [Paired item definition][paired-display]
- See [Copper construction](../blocks/CopperConstruction.md) for shared placed-block rules, complete recipe comparisons and collection details. [Items](Items.md) lists the individual inventory entries.

## Sources and verification

Source-reviewed at `f5473e41dc4af8ced756db517fada27288df07a3` on **2026-10-04**. Recipes and loot describe bundled data; data packs can replace them, and resource packs can change presentation. No in-game crafting, mining, oxidation, redstone or rendering test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L6314-L6354
[item-registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L2610-L2617
[crafting]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/exposed_copper_grate.json
[stonecutting]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/stonecutting/exposed_copper_grate_from_exposed_copper_stonecutting.json
[stages]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java
[loot]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/loot_table/blocks/exposed_copper_grate.json
[water]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/WaterloggedTransparentBlock.java
[axe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L118
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_copper_grate_from_honeycomb.json
[waxing]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/HoneycombItem.java#L29-L114
[display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/exposed_copper_grate.json
[paired-display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/waxed_exposed_copper_grate.json

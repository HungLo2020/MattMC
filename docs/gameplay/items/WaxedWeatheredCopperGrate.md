# Waxed Weathered Copper Grate

Waxed Weathered Copper Grate (`minecraft:waxed_weathered_copper_grate`) is the **Weathered, waxed** item for a waterloggable building block. [Item registration][item-registration] · [Block registration][registration]

## Obtaining

- **Stonecutter:** 1 [Waxed Weathered Copper](WaxedWeatheredCopper.md) → **4 Waxed Weathered Copper Grates**. This uses the plain full copper block in exactly this stage and wax state. [Stonecutting recipe][stonecutting]
- **Crafting Table:** place 4 of those same full blocks at top-center, middle-left, middle-right and bottom-center, leaving the center and corners empty → **4 Waxed Weathered Copper Grates**. [Crafting recipe][crafting]

Stonecutting therefore gives four times as many grates per full block as crafting. The complete [conversion table](../blocks/CopperConstruction.md#stonecutter-conversions) compares the other copper shapes.

Combine **1 [Weathered Copper Grate](WeatheredCopperGrate.md) + 1 [Honeycomb](Honeycomb.md)** in any arrangement to make **1 Waxed Weathered Copper Grate**. This preserves the Weathered finish. [Waxing recipe][wax-recipe]

You can also use one Honeycomb on the placed [Weathered Copper Grate](WeatheredCopperGrate.md) and collect the waxed result. [Waxing interaction][waxing]

To collect **1 matching Waxed Weathered Copper Grate** from the placed block in Survival, use an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe**. Wooden and Golden Pickaxes do not qualify. An axe changes the finish but is not the harvesting tool; see [copper mining and collection](../blocks/CopperConstruction.md#mining-and-collection). [Exact variant loot][loot] · [Block registration][registration]

## Usage

One item places one grate. It can hold water, but its visible grid still has full-block collision. It is not a redstone conductor. See [grates](../blocks/CopperConstruction.md#grates) for water handling, light behavior and collision. [Waterlogged grate implementation][water] · [Block registration][registration]

## Behavior

The placed block stays at the **Weathered** stage while waxed. One use of an **unbroken axe** removes the wax and produces [Weathered Copper Grate](WeatheredCopperGrate.md), keeping that stage; removing wax does not return Honeycomb. [Wax removal][axe]

After removing the wax, a separate axe use scrapes the placed block back to [Exposed Copper Grate](ExposedCopperGrate.md). [Previous-stage mapping][stages]

Waxing and scraping preserve stored water. See [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for conversion controls and axe durability.

## Notes

- This item and [Weathered Copper Grate](WeatheredCopperGrate.md) use the **same bundled item-display model**. Check the item name or ID to distinguish wax status; the model alone does not determine the placed block’s mechanics. [This item definition][display] · [Paired item definition][paired-display]
- See [Copper construction](../blocks/CopperConstruction.md) for shared placed-block rules, complete recipe comparisons and collection details. [Items](Items.md) lists the individual inventory entries.

## Sources and verification

Source-reviewed at `f5473e41dc4af8ced756db517fada27288df07a3` on **2026-10-04**. Recipes and loot describe bundled data; data packs can replace them, and resource packs can change presentation. No in-game crafting, mining, oxidation, redstone or rendering test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L6314-L6354
[item-registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L2610-L2617
[crafting]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_grate.json
[stonecutting]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/stonecutting/waxed_weathered_copper_grate_from_waxed_weathered_copper_stonecutting.json
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_weathered_copper_grate_from_honeycomb.json
[waxing]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/HoneycombItem.java#L29-L114
[loot]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/loot_table/blocks/waxed_weathered_copper_grate.json
[water]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/WaterloggedTransparentBlock.java
[axe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L118
[stages]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java
[display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/waxed_weathered_copper_grate.json
[paired-display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/weathered_copper_grate.json

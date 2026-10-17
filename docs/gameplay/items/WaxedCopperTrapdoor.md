# Waxed Copper Trapdoor

Waxed Copper Trapdoor (`minecraft:waxed_copper_trapdoor`) is the **Unaffected, waxed** item for a hinged panel for floors or walls. [Item registration][item-registration] · [Block registration][registration]

## Obtaining

Combine **1 [Copper Trapdoor](CopperTrapdoor.md) + 1 [Honeycomb](Honeycomb.md)** in any arrangement to make **1 Waxed Copper Trapdoor**. This preserves the Unaffected finish. [Waxing recipe][wax-recipe]

You can also use one Honeycomb on the placed [Copper Trapdoor](CopperTrapdoor.md) and collect the waxed result. [Waxing interaction][waxing]

To collect **1 matching Waxed Copper Trapdoor** from the placed block in Survival, use an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe**. Wooden and Golden Pickaxes do not qualify. An axe changes the finish but is not the harvesting tool; see [copper mining and collection](../blocks/CopperConstruction.md#mining-and-collection). [Exact variant loot][loot] · [Block registration][registration]

## Usage

One item places one trapdoor. It opens by hand, responds to redstone and can be waterlogged in this finish. The [door and trapdoor guide](../blocks/CopperConstruction.md#doors-and-trapdoors) explains placement, panel orientation, stored water and power changes. [Trapdoor placement and controls][controls]

## Behavior

The placed block stays at the **Unaffected** stage while waxed. One use of an **unbroken axe** removes the wax and produces [Copper Trapdoor](CopperTrapdoor.md), keeping that stage; removing wax does not return Honeycomb. [Wax removal][axe]

The unwaxed result has no earlier oxidation stage to scrape. To let it age, leave that unwaxed block placed; see [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing).

**Hold secondary use, normally sneak, when waxing or scraping** so opening the trapdoor does not take priority. The shared [waxing and scraping guide](../blocks/CopperConstruction.md#waxing-and-scraping) covers these controls and axe durability.

## Notes

- This item and [Copper Trapdoor](CopperTrapdoor.md) use the **same bundled item-display model**. Check the item name or ID to distinguish wax status; the model alone does not determine the placed block’s mechanics. [This item definition][display] · [Paired item definition][paired-display]
- See [Copper construction](../blocks/CopperConstruction.md) for shared placed-block rules, complete recipe comparisons and collection details. [Items](Items.md) lists the individual inventory entries.

## Sources and verification

Source-reviewed at `f5473e41dc4af8ced756db517fada27288df07a3` on **2026-10-04**. Recipes and loot describe bundled data; data packs can replace them, and resource packs can change presentation. No in-game crafting, mining, oxidation, redstone or rendering test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L6271-L6313
[item-registration]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L1102-L1109
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_copper_trapdoor_from_honeycomb.json
[waxing]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/HoneycombItem.java#L29-L114
[loot]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/loot_table/blocks/waxed_copper_trapdoor.json
[controls]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/TrapDoorBlock.java#L92-L193
[axe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/AxeItem.java#L62-L118
[display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/waxed_copper_trapdoor.json
[paired-display]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/assets/minecraft/items/copper_trapdoor.json

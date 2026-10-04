# Waxed Exposed Cut Copper Stairs

**Waxed Exposed Cut Copper Stairs** (`minecraft:waxed_exposed_cut_copper_stairs`) is the **Exposed, waxed** stair-shaped member of the copper building family. Use [Copper construction](../blocks/CopperConstruction.md) for the shared oxidation, waxing, placement and mining rules. [Item registration][item] · [Block registration][block]

## Obtaining

The following recipes produce this exact variant:

| Method | Ingredients and layout | Output count | Evidence |
| --- | --- | ---: | --- |
| Crafting Table | [Waxed Exposed Cut Copper](WaxedExposedCutCopper.md): 6 in rows of 1, 2, then 3 along one side | 4 | [Recipe][craft] |
| Stonecutter | 1 [Waxed Exposed Copper](WaxedExposedCopper.md) | 4 | [Recipe][stone-full] |
| Stonecutter | 1 [Waxed Exposed Cut Copper](WaxedExposedCutCopper.md) | 1 | [Recipe][stone-cut] |
| Shapeless crafting | 1 [Exposed Cut Copper Stairs](ExposedCutCopperStairs.md) + 1 [Honeycomb](Honeycomb.md) | 1 | [Recipe][wax-recipe] |

The copper inputs must have the named oxidation stage and wax state; other variants cannot replace or mix with them. The stair layout also accepts its horizontal mirror. [Pattern matching][pattern] See the [family recipe guide](../blocks/CopperConstruction.md#crafting-and-stonecutter-yields) for yields across other copper forms.

You can also use **1 Honeycomb** on placed [Exposed Cut Copper Stairs](ExposedCutCopperStairs.md) and collect the resulting waxed stairs. This keeps its existing oxidation stage. [Waxing interaction][wax-use]

For ordinary Survival collection, use an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe**. Mining the matching placed variant returns **1 stair item**. Wooden and Golden Pickaxes do not qualify; see [the complete mining rules](../blocks/CopperConstruction.md#mining-and-collection). [Pickaxe targets][pickaxe-tag] · [Tier][tier] · [Tool rules][tool] · [Broken-tool gate][broken-drop] · [Harvest dispatch][harvest] · [Loot][loot]

## Usage

Place the stair upright or upside down. Its facing and neighboring stairs determine straight, inner-corner or outer-corner shape; compatible stairs can connect across different oxidation stages and wax states. Stairs can be waterlogged. Use [stair placement controls](../blocks/WoodConstruction.md#stairs) and [copper stairs and waterlogging](../blocks/CopperConstruction.md#slabs-and-stairs). [Placement and corner rules][shape]

## Behavior

This waxed variant keeps its **Exposed** finish instead of oxidizing. One use of an unbroken axe removes the wax, producing placed [Exposed Cut Copper Stairs](ExposedCutCopperStairs.md) at the **same stage**; it does not also scrape away a stage. Follow [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for durability and interaction controls. [Waxed implementation][block] · [Axe conversion][axe]

## Notes

This exact block item is listed in the **Building Blocks** catalog. For finding and requesting it, use the [inventory browser guide](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); its [mode limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits) distinguish catalog visibility from item supply. [Category entry][category]

Related: [Exposed Cut Copper Stairs](ExposedCutCopperStairs.md) · [Waxed Exposed Cut Copper Slab](WaxedExposedCutCopperSlab.md) · [Copper block catalog](../blocks/catalog/copper.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at [`f5473e41dc4a`](https://github.com/HungLo2020/MattMC/tree/f5473e41dc4af8ced756db517fada27288df07a3). Recipes, loot, registrations and active shared callbacks were checked against bundled source data. These are source-reviewed results, not an in-game verification; enabled world data packs may change recipes or loot.

[item]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L192-L219
[block]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L6175-L6238
[craft]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_cut_copper_stairs.json
[stone-full]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_stairs_from_waxed_exposed_copper_stonecutting.json
[stone-cut]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/stonecutting/waxed_exposed_cut_copper_stairs_from_waxed_exposed_cut_copper_stonecutting.json
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_exposed_cut_copper_stairs_from_honeycomb.json
[pattern]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/crafting/ShapedRecipePattern.java#L158-L193
[wax-use]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/HoneycombItem.java#L89-L119
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tier]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[tool]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[broken-drop]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[harvest]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[loot]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/loot_table/blocks/waxed_exposed_cut_copper_stairs.json
[shape]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/StairBlock.java#L94-L167
[axe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L117
[category]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L451-L535

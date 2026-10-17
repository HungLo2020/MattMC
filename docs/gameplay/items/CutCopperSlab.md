# Cut Copper Slab

**Cut Copper Slab** (`minecraft:cut_copper_slab`) is the **Unaffected, unwaxed** half-height member of the copper building family. Use [Copper construction](../blocks/CopperConstruction.md) for the shared oxidation, waxing, placement and mining rules. [Item registration][item] · [Block registration][block]

## Obtaining

The following recipes produce this exact variant:

| Method | Ingredients and layout | Output count | Evidence |
| --- | --- | ---: | --- |
| Crafting Table | [Cut Copper](CutCopper.md): 3 in one horizontal row | 6 | [Recipe][craft] |
| Stonecutter | 1 [Block of Copper](BlockOfCopper.md) | 8 | [Recipe][stone-full] |
| Stonecutter | 1 [Cut Copper](CutCopper.md) | 2 | [Recipe][stone-cut] |

The copper inputs must have the named oxidation stage and wax state; other variants cannot replace or mix with them. See the [family recipe guide](../blocks/CopperConstruction.md#crafting-and-stonecutter-yields) for yields across other copper forms.

To obtain this finish through block conversion, scrape placed [Exposed Cut Copper Slab](ExposedCutCopperSlab.md) once with an unbroken axe, or remove the wax from placed [Waxed Cut Copper Slab](WaxedCutCopperSlab.md) with an unbroken axe. Collect the changed block with the tool below. [Stage pairs][weather] · [Axe conversions][axe]

For ordinary Survival collection, use an **unbroken Stone, Copper, Iron, Diamond or Netherite Pickaxe**. Mining the matching placed variant returns **1 slab from a single slab, or 2 slabs from a double slab**. Wooden and Golden Pickaxes do not qualify; see [the complete mining rules](../blocks/CopperConstruction.md#mining-and-collection). [Pickaxe targets][pickaxe-tag] · [Tier][tier] · [Tool rules][tool] · [Broken-tool gate][broken-drop] · [Harvest dispatch][harvest] · [Loot][loot]

## Usage

Place it as a **bottom or top slab**, or add a second **identical Cut Copper Slab** item to the missing half to make a double slab. A different oxidation stage or wax state cannot merge with it. Single slabs can be waterlogged; combining clears the water and the double slab cannot be waterlogged. Use [slab controls](../blocks/WoodConstruction.md#slabs) and [copper slab behavior](../blocks/CopperConstruction.md#slabs-and-stairs). [Placement and water rules][shape]

For another building shape, arrange **2 of these slab items vertically → 1 [Chiseled Copper](ChiseledCopper.md)**. This recipe fits the inventory grid and preserves the named stage and wax state. [Chiseled recipe][chiseled]

## Behavior

While placed, this variant can oxidize to [Exposed Cut Copper Slab](ExposedCutCopperSlab.md). It has no earlier oxidation stage to scrape back to. Use **1 Honeycomb** on the placed block, or craft **1 of these items + 1 Honeycomb**, to make [Waxed Cut Copper Slab](WaxedCutCopperSlab.md). A placed double slab needs only one Honeycomb for the pair. [Stage pairs][weather] · [Axe conversion][axe] · [Waxing interaction][wax-use] · [Waxing recipe][wax-recipe]

See [oxidation and spacing](../blocks/CopperConstruction.md#oxidation-and-spacing) for aging conditions and [waxing and scraping](../blocks/CopperConstruction.md#waxing-and-scraping) for controls. Aging is random rather than a fixed timer; the family guide also explains [lightning cleaning](../blocks/CopperConstruction.md#lightning-cleaning).

## Notes

This exact block item is listed in the **Building Blocks** catalog. For finding and requesting it, use the [inventory browser guide](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item); its [mode limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits) distinguish catalog visibility from item supply. [Category entry][category]

Related: [Waxed Cut Copper Slab](WaxedCutCopperSlab.md) · [Cut Copper Stairs](CutCopperStairs.md) · [Copper block catalog](../blocks/catalog/copper.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-04** at [`f5473e41dc4a`](https://github.com/HungLo2020/MattMC/tree/f5473e41dc4af8ced756db517fada27288df07a3). Recipes, loot, registrations and active shared callbacks were checked against bundled source data. These are source-reviewed results, not an in-game verification; enabled world data packs may change recipes or loot.

[item]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/Items.java#L192-L219
[block]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/Blocks.java#L6175-L6238
[craft]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/cut_copper_slab.json
[stone-full]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_slab_from_copper_block_stonecutting.json
[stone-cut]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/stonecutting/cut_copper_slab_from_cut_copper_stonecutting.json
[weather]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L29-L34
[axe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L117
[pickaxe-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[tier]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[tool]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[broken-drop]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[harvest]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L283-L292
[loot]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/loot_table/blocks/cut_copper_slab.json
[shape]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L53-L113
[chiseled]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/chiseled_copper.json
[wax-use]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/HoneycombItem.java#L89-L119
[wax-recipe]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/resources/data/minecraft/recipe/crafting/waxed_cut_copper_slab_from_honeycomb.json
[category]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L451-L535

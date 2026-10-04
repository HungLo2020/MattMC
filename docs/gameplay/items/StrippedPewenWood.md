# Stripped Pewen Wood

`minecraft:stripped_pewen_wood` places a full block with the stripped Pewen side texture on every face. It is useful for beams and trim where exposed log ends would look different. [Item binding][item] · [Placement][placement] · [Texture model][model]

## Obtaining

Arrange **four [Stripped Pewen Logs](StrippedPewenLog.md) in a 2 × 2 square** to craft **three Stripped Pewen Wood**, using either crafting grid. The recipe requires already-obtained stripped logs; it does not establish a Survival source for them. [Recipe][stripped-recipe]

It is listed with Building Blocks. The [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can supply it in Creative, subject to its insertion checks; a visible catalog entry in Survival does not supply the item. [Listing][listing]

Using an axe on [Pewen Wood](PewenWood.md) does **not** produce this block in the checked implementation. Neither Pewen Wood nor Pewen Log is in the standard stripping map. [Stripping map][stripping] · [Map lookup][strip-use]

## Usage

Place it for matching stripped surfaces throughout a [Pewen build](../blocks/Pewen.md#block-behavior). Its axis follows the clicked face: top or bottom for a vertical axis, side for a horizontal one. [Axis placement][axis]

## Behavior

Normal Survival mining, including by hand, returns **one Stripped Pewen Wood**. It has no correct-tool drop requirement, Silk Touch requirement, or Fortune increase in its bundled loot table. [Block settings][block] · [Player drop gate][drop-gate] · [Harvest path][harvest] · [Loot][loot]

The bundled axe-mineable tag omits this block, so an ordinary axe does not receive its tag-based mining-speed bonus here. [Axe tag][axe-tag] · [Axe tool][axe-tool] · [Speed rule][tool-speed]

## Notes

The separate `pewen_logs` item tag does not put this item into the bundled `logs` or `logs_that_burn` tags. It therefore has no default furnace-fuel or charcoal-smelting route through those tags. The [Pewen recipe cautions](../blocks/Pewen.md#recipes-and-tools-that-need-caution) also cover the incompatible plank recipe. [Pewen tag][pewen-tag] · [Log tags][logs-tag] · [Burnable logs][burning-logs-tag] · [Fuel list][fuels] · [Charcoal recipe][charcoal]

Related: [Stripped Pewen Log](StrippedPewenLog.md) · [Pewen Wood](PewenWood.md) · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. These are checked bundled recipes, tags, loot, and placement rules; data packs can change them. No in-game crafting, placement, or harvesting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L301-L310
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L93
[model]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/models/block/stripped_pewen_wood.json
[stripped-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stripped_pewen_wood.json
[listing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L237-L246
[stripping]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L30-L55
[strip-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L129-L131
[axis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L56
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7001-L7009
[drop-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L277-L293
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/stripped_pewen_wood.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[axe-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L439-L441
[tool-speed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[pewen-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/pewen_logs.json
[logs-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs.json
[burning-logs-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[fuels]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L42-L108
[charcoal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/charcoal.json

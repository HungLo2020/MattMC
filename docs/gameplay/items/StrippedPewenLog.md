# Stripped Pewen Log

`minecraft:stripped_pewen_log` places a stripped Pewen log for pillars, beams, and other wood details. It is an ordinary block item with axis-oriented placement. [Item binding][item] · [Block settings][block] · [Placement][placement]

## Obtaining

It is listed with Building Blocks. The [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can supply it in Creative, subject to its insertion checks; a visible catalog entry in Survival does not supply the item. [Listing][listing]

The checked bundled recipes contain **no recipe producing this log**, and using an axe on [Pewen Log](PewenLog.md) does not produce it: the standard stripping map has no Pewen entry. Registration and Creative listing do not establish an ordinary Survival supply of stripped logs. [Bundled recipes][recipes] · [Stripping map][stripping] · [Map lookup][strip-use]

## Usage

**Four already-obtained Stripped Pewen Logs in a 2 × 2 square craft three [Stripped Pewen Wood](StrippedPewenWood.md)**. This fits the inventory crafting grid. [Recipe][stripped-recipe]

The hanging-sign recipe also names this log, but its chain ingredient is mismatched. See the [Pewen recipe cautions](../blocks/Pewen.md#recipes-and-tools-that-need-caution) and the separate [Pewen sign placement limits](../blocks/Signs.md#pewen-signs-incomplete-integration) before treating that as a usable crafting and placement route. [Hanging-sign definition][hanging-recipe]

## Behavior

Its axis follows the clicked face: top or bottom for a vertical log, side for a horizontal log. Normal Survival mining, including by hand, returns **one Stripped Pewen Log**. No special tool or Silk Touch is needed, and Fortune does not increase the bundled loot count. [Axis placement][axis] · [Block settings][block] · [Player drop gate][drop-gate] · [Harvest path][harvest] · [Loot][loot]

The bundled axe-mineable tag omits this block, so an ordinary axe does not receive its tag-based mining-speed bonus here. [Axe tag][axe-tag] · [Axe tool][axe-tool] · [Speed rule][tool-speed]

## Notes

The separate `pewen_logs` item tag does not put this item into the bundled `logs` or `logs_that_burn` tags. It therefore has no default furnace-fuel or charcoal-smelting route through those tags. The [Pewen recipe cautions](../blocks/Pewen.md#recipes-and-tools-that-need-caution) also cover the incompatible plank recipe. [Pewen tag][pewen-tag] · [Log tags][logs-tag] · [Burnable logs][burning-logs-tag] · [Fuel list][fuels] · [Charcoal recipe][charcoal]

Related: [Pewen family](../blocks/Pewen.md) · [Pewen Log](PewenLog.md) · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. These are checked bundled recipes, tags, loot, and placement rules; data packs can change them. No in-game crafting, placement, or harvesting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L301-L310
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L6992-L7000
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L93
[listing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L237-L246
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[stripping]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L30-L55
[strip-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/AxeItem.java#L129-L131
[stripped-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stripped_pewen_wood.json
[hanging-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/pewen_hanging_sign.json
[axis]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/RotatedPillarBlock.java#L48-L56
[drop-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L277-L293
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/stripped_pewen_log.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[axe-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L439-L441
[tool-speed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[pewen-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/pewen_logs.json
[logs-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs.json
[burning-logs-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/logs_that_burn.json
[fuels]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L42-L108
[charcoal]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/charcoal.json

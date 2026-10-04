# Pewen Stairs

`minecraft:pewen_stairs` places Pewen steps for stairways, rooflines, seating, and trim. [Item binding][item] · [Block settings][block] · [Placement][placement]

## Obtaining

At a Crafting Table, arrange **six [Pewen Planks](PewenPlanks.md) in a stair pattern**: one in the first row, two in the next, and three in the last, all aligned to the same side. This crafts **four Pewen Stairs**. [Recipe][recipe]

This recipe uses Pewen Planks specifically. Obtaining those planks is a separate step: the bundled log-to-plank recipe has an incompatible ingredient format, as explained in the [Pewen recipe cautions](../blocks/Pewen.md#recipes-and-tools-that-need-caution).

It is listed with Building Blocks. The [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can supply it in Creative, subject to its insertion checks; a visible catalog entry in Survival does not supply the item. [Listing][listing]

## Usage

Placement uses your horizontal facing. Clicking the top of a block or the lower half of a side places upright stairs; clicking underneath or on the upper half of a side places upside-down stairs. Nearby stairs can make inner or outer corners when their directions and top/bottom halves match the corner rules. [Placement and corner rules][shape]

Pewen Stairs can be waterlogged. See the [Pewen family](../blocks/Pewen.md#block-behavior) for the shared material and construction context. [Waterlogging][water]

## Behavior

Normal Survival mining, including by hand, returns **one Pewen Stairs item**. There is no special-tool or Silk Touch requirement and no Fortune increase in the bundled loot. Explosion recovery is conditional. [Block settings][block] · [Stair property copy][stair-copy] · [Player drop gate][drop-gate] · [Harvest path][harvest] · [Loot][loot]

The bundled axe-mineable tag omits this block, so an ordinary axe does not receive its tag-based mining-speed bonus here. [Axe tag][axe-tag] · [Axe tool][axe-tool] · [Speed rule][tool-speed]

## Notes

The bundled wooden-stairs item tag omits Pewen Stairs, so the default wood-stair furnace-fuel entry does not cover it. [Item tag][fuel-tag] · [Fuel list][fuels]

Related: [Pewen Slab](PewenSlab.md) · [Pewen Planks](PewenPlanks.md) · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. These are checked bundled recipes, tags, loot, and placement rules; data packs can change them. No in-game crafting, placement, or harvesting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L301-L310
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7010-L7018
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L93
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/pewen_stairs.json
[listing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L237-L246
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L93-L164
[water]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StairBlock.java#L212-L220
[stair-copy]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7256-L7258
[drop-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L277-L293
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pewen_stairs.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[axe-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L439-L441
[tool-speed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/wooden_stairs.json
[fuels]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L42-L108

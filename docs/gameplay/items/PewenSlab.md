# Pewen Slab

`minecraft:pewen_slab` places a half-height Pewen block for floors, paths, roofs, and detail work. [Item binding][item] · [Block settings][block] · [Placement][placement]

## Obtaining

Place **three [Pewen Planks](PewenPlanks.md) in one row** at a Crafting Table to make **six Pewen Slabs**. [Recipe][recipe]

This recipe uses Pewen Planks specifically. Obtaining those planks is a separate step: the bundled log-to-plank recipe has an incompatible ingredient format, as explained in the [Pewen recipe cautions](../blocks/Pewen.md#recipes-and-tools-that-need-caution).

It is listed with Building Blocks. The [inventory browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) can supply it in Creative, subject to its insertion checks; a visible catalog entry in Survival does not supply the item. [Listing][listing]

## Usage

Click a block's top or the lower half of its side for a bottom slab; click underneath or on the upper half of a side for a top slab. Add another Pewen Slab to the unoccupied half of the same space to make a full-height double slab. The second slab must be the same item. [Placement and combination][shape]

A single slab can be waterlogged. Combining two clears the waterlogged state, and double slabs cannot hold water. See the [Pewen family](../blocks/Pewen.md#block-behavior) for the broader construction set. [Water handling][water]

## Behavior

Normal Survival mining, including by hand, returns **one Pewen Slab from a single slab, or two from a double slab**. No special tool or Silk Touch is needed, and Fortune does not increase these counts. Explosion decay can reduce recovery. [Block settings][block] · [Player drop gate][drop-gate] · [Harvest path][harvest] · [Loot][loot]

The bundled axe-mineable tag omits this block, so an ordinary axe does not receive its tag-based mining-speed bonus here. [Axe tag][axe-tag] · [Axe tool][axe-tool] · [Speed rule][tool-speed]

## Notes

The bundled wooden-slabs item tag omits Pewen Slab, so the default wood-slab furnace-fuel entry does not cover it. [Item tag][fuel-tag] · [Fuel list][fuels]

Related: [Pewen Stairs](PewenStairs.md) · [Pewen Planks](PewenPlanks.md) · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. These are checked bundled recipes, tags, loot, and placement rules; data packs can change them. No in-game crafting, placement, or harvesting test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L301-L310
[block]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L7010-L7023
[placement]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/BlockItem.java#L40-L93
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/pewen_slab.json
[listing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L237-L246
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L58-L99
[water]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/SlabBlock.java#L67-L115
[drop-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[harvest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L277-L293
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/pewen_slab.json
[axe-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[axe-tool]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L439-L441
[tool-speed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ToolMaterial.java#L35-L48
[fuel-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/wooden_slabs.json
[fuels]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L42-L108

# Andesite Wall

**Andesite Wall** (`minecraft:andesite_wall`) is a connecting building piece for barriers, posts and edging in the [Andesite family](../blocks/DecorativeStone.md#polished-and-shaped-variants). [Item binding][item]

## Obtaining

Craft **6 blocks in two rows of three → 6 Andesite Walls**. Every occupied slot requires [Andesite](Andesite.md); use the matching full-block finish. [Crafting][craft]

Alternatively, stonecut **1 Andesite → 1 Andesite Wall** at a [Stonecutter](../blocks/Stonecutter.md). [Stonecutting][cut-raw] Cutting keeps the same material yield as crafting and works one input block at a time.

Use an **unbroken pickaxe**, including a Wooden Pickaxe, to recover **one matching item**. Hand breaking does not collect it. Silk Touch is unnecessary and Fortune does not increase the count. Explosion survival or decay is a separate condition. [Exact loot][loot] · [Tool and drop rules](../blocks/DecorativeStone.md#mining-and-drops)

## Usage

Use the walls for connected barriers, free-standing posts or stone edging alongside [Andesite](Andesite.md). The [family variants guide](../blocks/DecorativeStone.md#polished-and-shaped-variants) lists the matching stairs and slabs; this material has no polished wall form.

## Behavior

Connects to adjacent walls, suitable sturdy block faces, Iron Bars, Copper Bars or glass panes, and correctly aligned Fence Gates. Its shape changes with neighboring blocks, and it can be waterlogged. Ordinary fences are not a wall-connection category. See the [shared wall placement rules](../blocks/Stone.md#placing-shaped-blocks). [Connection and Water rules][shape] · [Glass Pane registration][panes] · [Stained panes][stained-panes] · [Copper Bars registration][copper-bars] [inheritance][copper-bars-inheritance]

## Notes

This is the ordinary item form of the `minecraft:andesite_wall` block. Follow the [family guide](../blocks/DecorativeStone.md#polished-and-shaped-variants) for shared recipes and placed behavior. [Block registration][blocks] · [Items](Items.md)

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. The exact recipes, complete loot table, item binding, tool requirements and relevant placement code were checked. No in-game crafting, mining or placement test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L615-L615
[blocks]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L5285-L5285
[craft]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/andesite_wall.json
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/andesite_wall.json
[cut-raw]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/stonecutting/andesite_wall_from_andesite_stonecutting.json
[shape]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WallBlock.java#L105-L134
[panes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2329-L2331
[stained-panes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/StainedGlassPaneBlock.java#L8-L24

[copper-bars]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L2310-L2316
[copper-bars-inheritance]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/WeatheringCopperBarsBlock.java#L11-L11

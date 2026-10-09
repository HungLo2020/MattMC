# All registered blocks

Browse **1,235 block IDs** traced through the inspected built-in registration path, including direct fields and helper-created Copper forms. Separate wall, plant, moving-part and technical forms are included; this is not a count of obtainable inventory items. Categories are navigation choices, not game registry tags.

All listed IDs currently have a **related guide**, which may cover a whole family or only shared behavior. A link does not establish complete variant detail or runtime verification. Use placed-block guides for block behavior; an inventory item page alone does not cover it.

| Browse | Registered forms | Includes |
| --- | ---: | --- |
| [Soil, sand, ice and fluids](terrain.md) | 29 | Dirt and spreading soils, falling terrain, snow and ice, water and lava. |
| [Stone and masonry](stone.md) | 199 | Stone, deepslate, tuff, sandstone, brick, Nether and End building families, including shaped variants. |
| [Ores, minerals and resource blocks](ores.md) | 35 | Overworld and Nether ores, raw-resource blocks, refined storage blocks, amethyst and obsidian. |
| [Wood and tree families](wood.md) | 269 | Logs, wood, stripped forms, planks, stems, roots, saplings, foliage and wooden building forms. |
| [Copper and oxidation variants](copper.md) | 125 | Copper ores and resource blocks, building forms, doors, trapdoors, grates, bulbs, chests, statues, torches, lightning rods, bars, chains and lanterns. |
| [Colored building and decoration](colored.md) | 199 | Wool, carpets, glass, concrete, terracotta, beds, banners and Shulker Boxes, with every registered color. |
| [Plants, crops and coral](plants.md) | 134 | Flowers, grasses, crops, vines, mushrooms, underwater vegetation, coral and decorative plants. |
| [Flower pots and potted plants](pots.md) | 41 | The empty Flower Pot and every separately registered planted form. |
| [Workstations, storage and utility](workstations.md) | 54 | Crafting and processing stations, containers, village work blocks, beacons, conduits and other utility blocks. |
| [Redstone and transport](redstone.md) | 40 | Inputs, signal components, powered machinery, rails and related moving parts. Wooden and copper components are listed in their material catalogs. |
| [Light sources and fire](lighting.md) | 55 | Torches, lanterns, candles, glowing blocks, campfires and fire. Copper forms are in the copper catalog. |
| [Spawning, portals and technical blocks](special.md) | 55 | Spawners, eggs, portals, skulls, sculk, technical blocks and special world objects. |

## Scope of the inventory

The inventory traces **1,211 direct static Block fields plus 24 helper-created IDs**, including six pumpkin/melon ResourceKey names. Three WeatheringCopperBlocks fields each pass Blocks::register to eight explicit helper calls. Block states such as facing, age, waterlogging and power are not additional registry IDs.

MattMC additions use the minecraft namespace too; namespace alone does not identify vanilla content. Related routes do not certify complete mining, recipes, loot, spawning, placement, redstone or acquisition coverage. Future source changes can add or remove IDs.

## Registration-method correction

The earlier 1,211 count covered only direct Block declarations and omitted Copper Bars, Copper Chain and Copper Lantern helper registrations. Those 24 forms already existed in the original c87803e7 source; correcting the catalog does not add game content. They now link to the reviewed Copper Bars, Chains and Lanterns family guide; a related route still does not certify every gameplay detail.

The method traces the [Bars/Chain calls](https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/Blocks.java#L2310-L2328), [Lantern call](https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/Blocks.java#L5409-L5422), [eight-name expansion](https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/WeatheringCopperBlocks.java#L14-L47), [registry write](https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/Blocks.java#L7274-L7292) and [bootstrap](https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/core/registries/BuiltInRegistries.java#L164). The corresponding [item-family helper](https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/WeatheringCopperItems.java#L12-L23) creates the eight matching items per family. This is a deduplicated source inventory, not a running-game registry dump, external-mod survey or proof of every variant behavior.

[Back to Blocks](../Blocks.md)

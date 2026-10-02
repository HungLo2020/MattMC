# All registered blocks

Browse every **1,211 built-in block ID** registered in the inspected MattMC source. The lists include separate wall, plant, moving-part, and technical forms, so the count is not a count of obtainable inventory items. Categories are navigation choices, not game registry tags.

A **related guide** can cover a whole family or only a shared behavior. Its presence does not mean every variant has a complete article. **Article needed** means this catalog has no placed-block guide to offer yet. Item stubs are not used as substitutes.

| Browse | Registered forms | Includes |
| --- | ---: | --- |
| [Soil, sand, ice and fluids](terrain.md) | 29 | Dirt and spreading soils, falling terrain, snow and ice, water and lava. |
| [Stone and masonry](stone.md) | 199 | Stone, deepslate, tuff, sandstone, brick, Nether and End building families, including shaped variants. |
| [Ores, minerals and resource blocks](ores.md) | 35 | Overworld and Nether ores, raw-resource blocks, refined storage blocks, amethyst and obsidian. |
| [Wood and tree families](wood.md) | 269 | Logs, wood, stripped forms, planks, stems, roots, saplings, foliage and wooden building forms. |
| [Copper and oxidation variants](copper.md) | 101 | Copper ores and resource blocks, building forms, doors, trapdoors, grates, bulbs, chests, bars, chains, decorations and lightning rods. |
| [Colored building and decoration](colored.md) | 199 | Wool, carpets, glass, concrete, terracotta, beds, banners and Shulker Boxes, with every registered color. |
| [Plants, crops and coral](plants.md) | 134 | Flowers, grasses, crops, vines, mushrooms, underwater vegetation, coral and decorative plants. |
| [Flower pots and potted plants](pots.md) | 41 | The empty Flower Pot and every separately registered planted form. |
| [Workstations, storage and utility](workstations.md) | 54 | Crafting and processing stations, containers, village work blocks, beacons, conduits and other utility blocks. |
| [Redstone and transport](redstone.md) | 40 | Inputs, signal components, powered machinery, rails and related moving parts. Wooden and copper components are listed in their material catalogs. |
| [Light sources and fire](lighting.md) | 55 | Torches, lanterns, candles, glowing blocks, campfires and fire. Copper Bulbs are in the copper catalog. |
| [Spawning, portals and technical blocks](special.md) | 55 | Spawners, eggs, portals, skulls, sculk, technical blocks and special world objects. |

## Scope of the inventory

The inventory follows all block registrations in [the active block registry](https://github.com/HungLo2020/MattMC/blob/c87803e75d339e5d643ca812efc70a6def06a401/src/main/java/net/minecraft/world/level/block/Blocks.java), including the six pumpkin/melon names resolved through its ResourceKey constants. The source bootstraps the built-in registry through this class. It includes MattMC additions under the `minecraft` namespace, rather than assuming namespace alone identifies vanilla content.

The catalog makes omitted families visible; it does **not** establish that their behavior articles are finished. Mining, recipes, loot, spawning, placement, redstone, and Survival access still need article-level verification. Future game changes can add or remove registrations.

[Back to Blocks](../Blocks.md)

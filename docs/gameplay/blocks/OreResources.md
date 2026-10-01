# Iron, Copper, and Diamond ores

These ore families illustrate two different resource routes: **Iron and Copper produce raw metal to process**, while **Diamond drops the usable gem directly**. Regular and Deepslate variants use matching resource drops, but their block forms remain separate items.

## Bring a suitable pickaxe

The six blocks require a correct tool for drops and belong to the pickaxe mining group. Tool speed alone does not establish permission to collect them.

| Ore family | Suitable ordinary pickaxes in the checked tags | Base ordinary drop without Fortune |
| --- | --- | --- |
| Iron and Deepslate Iron | Stone, Copper, Iron, Diamond, Netherite | 1 Raw Iron |
| Copper and Deepslate Copper | Stone, Copper, Iron, Diamond, Netherite | 2–5 Raw Copper |
| Diamond and Deepslate Diamond | Iron, Diamond, Netherite | 1 Diamond |

Wooden and Golden Pickaxes do not meet these drop requirements. Copper tools are real tools in MattMC, but a Copper Pickaxe still cannot collect Diamond Ore's expected drop. See [Mining](../mechanics/Mining.md) for the distinction between tool family, material restrictions, and mining speed.

The ordinary stone-host ores have hardness 3; the Deepslate variants have hardness 4.5. Those values affect mining time, not how many raw materials the base table awards.

## Fortune versus Silk Touch

- **Fortune** modifies the ordinary resource branch through the ore-drop bonus formula. It can increase raw-metal or gem yields; it does not always multiply every block by the same amount
- **Silk Touch** selects the corresponding ore block instead. The checked alternatives evaluate this branch before the raw-material or gem branch
- Explosion decay can reduce the ordinary resource drops; the table above is not a promise of explosion yields

Keep the tool requirement even when using an enchantment. Silk Touch does not make an unsuitable pickaxe into a valid harvest tool.

## Processing raw metal

One [Raw Iron](../items/RawIron.md) produces one [Iron Ingot](../items/IronIngot.md), and one [Raw Copper](../items/RawCopper.md) produces one [Copper Ingot](../items/CopperIngot.md). The bundled routes take **200 recipe ticks in a Furnace** or **100 in a Blast Furnace**, requiring their normal fuel and operating conditions.

The corresponding ore blocks collected with Silk Touch also have one-ingot processing recipes. For Copper, directly processing one ore gives one ingot rather than the 2–5 raw pieces of an ordinary correct-tool harvest. Choose the route deliberately rather than treating ore blocks and raw items as interchangeable quantities.

A [Diamond](../items/Diamond.md) is already the usable material after ordinary mining. It does not need the raw-metal-to-ingot step.

## Storage and availability

Nine raw metal pieces can be packed into the matching raw-metal block and unpacked back into nine pieces. Unpack before using the individual-raw-item processing recipes described above. Raw metal, ingots, ore blocks, and metal storage blocks are different inputs.

This guide verifies harvesting and processing, not a best-height mining chart or a complete generation survey. In particular, [Dry Midlands](../biomes/DryMidlands.md) has a documented ore-target-tag limitation tracked in [#781](https://github.com/HungLo2020/MattMC/issues/781). Do not infer working ore veins there from registration or a familiar block name; other generation routes such as geodes are separate.

## Related pages

- [Raw Iron](../items/RawIron.md) and [Iron Ingot](../items/IronIngot.md)
- [Raw Copper](../items/RawCopper.md) and [Copper Ingot](../items/CopperIngot.md)
- [Diamond](../items/Diamond.md)
- [Furnace](Furnace.md)
- [Blocks](Blocks.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game mining, crafting, smelting, or world-generation test was run. Counts describe bundled recipes and loot; data packs can change them.

- [Ore registrations and properties](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/Blocks.java)
- [Tool material restrictions](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ToolMaterial.java)
- [Pickaxe group](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json)
- [Stone-tier requirement group](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json)
- [Iron-tier requirement group](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/needs_iron_tool.json)
- [Iron Ore loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/iron_ore.json)
- [Deepslate Iron Ore loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/deepslate_iron_ore.json)
- [Copper Ore loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/copper_ore.json)
- [Deepslate Copper Ore loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/deepslate_copper_ore.json)
- [Diamond Ore loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/diamond_ore.json)
- [Deepslate Diamond Ore loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/deepslate_diamond_ore.json)
- [Fortune ore-drop formula](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/functions/ApplyBonusCount.java)
- [Iron smelting recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smelting/iron_ingot_from_smelting_raw_iron.json)
- [Iron blasting recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/blasting/iron_ingot_from_blasting_raw_iron.json)
- [Copper smelting recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smelting/copper_ingot_from_smelting_raw_copper.json)
- [Copper blasting recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/blasting/copper_ingot_from_blasting_raw_copper.json)

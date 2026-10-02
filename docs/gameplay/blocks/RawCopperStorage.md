# Raw Copper storage

**Block of Raw Copper** (`minecraft:raw_copper_block`) packs mined Raw Copper into a full building/storage block. It is distinct from the Copper Ingot storage block used for [copper construction](CopperConstruction.md). Its current registration is a plain block: it does not enter the copper oxidation chain, and Honeycomb waxing or axe scraping has no conversion entry for it. [Registration][raw-reg] · [Oxidation map][weather] · [Wax map][wax] · [Axe conversions][axe]

## Collecting and processing

Use the canonical [Raw Copper processing and storage recipes](../items/RawCopper.md#processing-and-storage) for packing nine raw pieces into a block and unpacking them again. Processing takes the individual Raw Copper items, so unpack before using the verified Furnace or Blast Furnace route. These recipes do not turn the storage block directly into a Copper Ingot block. [Packing][r-raw_copper_block] · [Unpacking][raw-unpack] · [Individual-item smelting][raw-smelt] · [Individual-item blasting][raw-blast]

The placed block has **hardness 5 and blast resistance 6**. Its efficient tool is a pickaxe, and its block drop requires an **unbroken Stone, Copper, Iron, Diamond, or Netherite Pickaxe**. Wooden/Golden Pickaxes and hand mining fail that requirement. [Properties][raw-reg] · [Pickaxe tag][mineable-pickaxe] · [Tier requirement][needs_stone_tool] · [Wood exclusions][incorrect_for_wooden_tool] · [Gold exclusions][incorrect_for_gold_tool] · [Tool rules][tool] · [Harvest gate][harvest] · [Broken drop guard][broken-drop]

Ordinary mining gives **1 Block of Raw Copper**, not nine loose pieces. Silk Touch and Fortune do not change this loot table; explosions have a survival check. Use the crafting-grid unpacking route to recover loose material. [Exact loot][loot-raw_copper_block] · [Unpacking recipe][raw-unpack]

## Building with the block

It uses a full-cube shape with no facing or waterlogged state. Once placed, it needs no continuing support and does not fall. Its appearance remains fixed through the checked oxidation system; it is not a substitute for the exact full-copper inputs in construction recipes. [Plain-block registration][raw-reg] · [Default shape and support][default] · [Weathering map][weather] · [Copper recipe distinctions](CopperConstruction.md#crafting-and-stonecutter-yields)

For mining the ore itself and understanding Raw Copper yields, see [Ore resources](OreResources.md). This article does not claim a natural generation location for the storage block.

## Exact variants, recipes, and loot

| Registry ID | Recipes | Block loot |
| --- | --- | --- |
| `minecraft:raw_copper_block` | [Craft][r-raw_copper_block] | [Loot][loot-raw_copper_block] |

## Sources and verification

Source-reviewed on **2026-10-02** at `1879e5f54378351fd773b9a2c10839eb9259c504`. Registration, packing/unpacking and selected raw-item processing recipes, tool tags, loot, and the absence of weathering/wax conversions were checked. No in-game test was run. Recipes, tags, loot, server rules, and later code changes can alter these results.

Related: [Blocks](Blocks.md) · [Copper construction](CopperConstruction.md) · [Copper catalog](catalog/copper.md) · [Items](../items/Items.md)

[raw-reg]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/Blocks.java#L6722-L6725
[default]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[harvest]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/entity/player/Player.java#L655-L657
[broken-drop]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ItemStack.java#L586-L589
[tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[wax]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/HoneycombItem.java#L24-L113
[weather]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/level/block/WeatheringCopper.java#L17-L103
[axe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/java/net/minecraft/world/item/AxeItem.java#L61-L117
[mineable-pickaxe]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[needs_stone_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/needs_stone_tool.json
[incorrect_for_wooden_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_wooden_tool.json
[incorrect_for_gold_tool]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/tags/block/incorrect_for_gold_tool.json
[loot-raw_copper_block]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/loot_table/blocks/raw_copper_block.json
[r-raw_copper_block]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/raw_copper_block.json
[raw-unpack]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/crafting/raw_copper.json
[raw-smelt]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/smelting/copper_ingot_from_smelting_raw_copper.json
[raw-blast]: https://github.com/HungLo2020/MattMC/blob/1879e5f54378351fd773b9a2c10839eb9259c504/src/main/resources/data/minecraft/recipe/blasting/copper_ingot_from_blasting_raw_copper.json

# Copper Ingot

Copper Ingot (`minecraft:copper_ingot`) is a processed metal for building, tools, and utility recipes. MattMC includes Copper tools; it is not solely a decorative material.

## Obtaining

Smelt or blast **Raw Copper, Copper Ore, or Deepslate Copper Ore** for one ingot per input. Furnace recipes take 200 ticks; Blast Furnace recipes take 100. A normal correct-tool Copper Ore harvest gives several Raw Copper pieces, so processing an intact Silk Touch ore block is not the same yield as processing all of its possible raw drops.

A standard Block of Copper unpacks into nine ingots. Nine Copper Nuggets in a 3 × 3 square craft one ingot, and one ingot shapelessly returns nine nuggets. The standard ingot/block packing recipes are reversible; this does not establish every weathered or cut construction variant as an ingot source.

## Selected uses

- **Copper Pickaxe:** three Copper Ingots across the top above two centered Sticks, using the copper-tool-material tag
- **Lightning Rod:** three Copper Ingots in a vertical column make one
- **Spyglass:** one Amethyst Shard above two Copper Ingots in a vertical column makes one
- **Block of Copper:** nine ingots in a 3 × 3 square make one storage/building block

Copper tool repair uses the Copper Ingot material tag. See [Anvil mechanics](../mechanics/AnvilMechanics.md). A Copper Pickaxe can harvest Iron and Copper Ore under the bundled tool rules, but not Diamond Ore; see [Mining](../mechanics/Mining.md).

These recipes describe the crafted outputs, not a complete guide to lightning protection, oxidation, or the Spyglass interface.

## Related pages

- [Raw Copper](RawCopper.md)
- [Ore resources](../blocks/OreResources.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game mining, crafting, smelting, or world-generation test was run. Counts describe bundled recipes and loot; data packs can change them.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java)
- [Tool materials and repair tags](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/ToolMaterial.java)
- [Raw Copper smelting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smelting/copper_ingot_from_smelting_raw_copper.json)
- [Ore smelting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smelting/copper_ingot_from_smelting_copper_ore.json)
- [Deepslate ore smelting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smelting/copper_ingot_from_smelting_deepslate_copper_ore.json)
- [Raw Copper blasting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/blasting/copper_ingot_from_blasting_raw_copper.json)
- [Ore blasting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/blasting/copper_ingot_from_blasting_copper_ore.json)
- [Deepslate ore blasting](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/blasting/copper_ingot_from_blasting_deepslate_copper_ore.json)
- [Unpacking](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_ingot.json)
- [Nuggets to ingot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_ingot_from_nuggets.json)
- [Ingot to nuggets](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_nugget.json)
- [Storage block](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_block.json)
- [Pickaxe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/copper_pickaxe.json)
- [Lightning Rod](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/lightning_rod.json)
- [Spyglass](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/spyglass.json)
- [Copper repair material](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/item/copper_tool_materials.json)

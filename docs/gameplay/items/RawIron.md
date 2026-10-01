# Raw Iron

Raw Iron (`minecraft:raw_iron`) is the mined resource used to produce Iron Ingots. It is not an ingot and does not substitute in an ingot recipe.

## Mining

A correct-tool harvest of ordinary or Deepslate Iron Ore gives **one Raw Iron** before Fortune. Use a Stone or Copper Pickaxe, or a suitable higher-tier pickaxe; Wooden and Golden Pickaxes do not satisfy the checked restrictions. Silk Touch selects the ore block instead.

See [ore resources](../blocks/OreResources.md) for tool rules, Fortune, and why a registered ore does not guarantee a working natural source in every dimension.

## Processing and storage

- One Raw Iron makes **one Iron Ingot** in a Furnace (200 recipe ticks) or Blast Furnace (100 recipe ticks)
- A 3 × 3 square of nine pieces makes one Block of Raw Iron
- Shapelessly unpacking that block returns all nine pieces

The listed processing recipes take individual raw pieces. Storage packing is reversible; unpack before processing the raw items. At normal 20-tick speed the recipe times are 10 and 5 seconds, excluding interruptions.

## Related pages

- [Iron Ingot](IronIngot.md)
- [Ore resources](../blocks/OreResources.md)
- [Smelting](../smelting/Smelting.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. No in-game mining, crafting, smelting, or world-generation test was run. Counts describe bundled recipes and loot; data packs can change them.

- [Iron ore loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/iron_ore.json)
- [Deepslate Iron ore loot](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/blocks/deepslate_iron_ore.json)
- [Furnace recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/smelting/iron_ingot_from_smelting_raw_iron.json)
- [Blast Furnace recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/blasting/iron_ingot_from_blasting_raw_iron.json)
- [Packing recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/raw_iron_block.json)
- [Unpacking recipe](https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/recipe/crafting/raw_iron.json)

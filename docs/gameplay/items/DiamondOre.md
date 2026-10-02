# Diamond Ore

**Diamond Ore** (`minecraft:diamond_ore`) is the inventory form of the corresponding placed resource block. Its complete mining, tool, Fortune, XP and generation guidance is in [Ores and Ancient Debris](../blocks/OreResources.md).

## Collecting the block

Use **Silk Touch** with a suitable unbroken pickaxe to collect **1 Diamond Ore**. The accepted ordinary materials are **Iron, Diamond, Netherite**. Without Silk Touch, its base ordinary loot is **1 Diamond**, not this ore item. [Exact loot](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/diamond_ore.json) · [Tool requirements](../blocks/OreResources.md#bring-a-suitable-pickaxe)

## Processing and placement

Process **1 Diamond Ore into 1 Diamond** in a Furnace or Blast Furnace. The bundled recipes specify **200 / 100 ticks** respectively and **1 recipe XP** per input. Fuel, XP collection and operating conditions follow the [Furnace guide](../blocks/Furnace.md). [Smelting recipe](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/diamond_from_smelting_diamond_ore.json) · [Blasting recipe](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/diamond_from_blasting_diamond_ore.json)

Place the item as a full block when you want to keep it in the world, then apply the same harvesting rules to collect it again. The [block guide](../blocks/OreResources.md) owns placed behavior and the exact recipe/loot matrix.

## Verification

Source-reviewed on **2026-10-02** at `2f6c6d4689df9796912eea87cf9def80fc320ee1`; no in-game mining, processing or placement test was run. Data packs and game rules can change results. [Active registration](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java#L1252)

[Items](Items.md) · [Blocks](../blocks/Blocks.md)

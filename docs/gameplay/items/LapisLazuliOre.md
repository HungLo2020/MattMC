# Lapis Lazuli Ore

**Lapis Lazuli Ore** (`minecraft:lapis_ore`) is the inventory form of the corresponding placed resource block. Its complete mining, tool, Fortune, XP and generation guidance is in [Ores and Ancient Debris](../blocks/OreResources.md).

## Collecting the block

Use **Silk Touch** with a suitable unbroken pickaxe to collect **1 Lapis Lazuli Ore**. The accepted ordinary materials are **Stone, Copper, Iron, Diamond, Netherite**. Without Silk Touch, its base ordinary loot is **4–9 Lapis Lazuli**, not this ore item. [Exact loot](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/lapis_ore.json) · [Tool requirements](../blocks/OreResources.md#bring-a-suitable-pickaxe)

## Processing and placement

Process **1 Lapis Lazuli Ore into 1 Lapis Lazuli** in a Furnace or Blast Furnace. The bundled recipes specify **200 / 100 ticks** respectively and **0.2 recipe XP** per input. Fuel, XP collection and operating conditions follow the [Furnace guide](../blocks/Furnace.md). [Smelting recipe](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/lapis_lazuli_from_smelting_lapis_ore.json) · [Blasting recipe](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/lapis_lazuli_from_blasting_lapis_ore.json)

Cooking the ore gives only one output; ordinary correct-tool mining gives multiple resource pieces before Fortune. Place the item as a full block when you want to keep it in the world, then apply the same harvesting rules to collect it again. The [block guide](../blocks/OreResources.md) owns placed behavior and the exact recipe/loot matrix.

## Verification

Source-reviewed on **2026-10-02** at `2f6c6d4689df9796912eea87cf9def80fc320ee1`; no in-game mining, processing or placement test was run. Data packs and game rules can change results. [Active registration](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java#L633)

[Items](Items.md) · [Blocks](../blocks/Blocks.md)

# Ancient Debris

**Ancient Debris** (`minecraft:ancient_debris`) is the inventory form of the corresponding placed resource block. Its complete mining, tool, Fortune, XP and generation guidance is in [Ores and Ancient Debris](../blocks/OreResources.md).

## Collecting the block

Use an unbroken **Diamond or Netherite Pickaxe** to collect **1 Ancient Debris** from the placed block. Silk Touch is unnecessary and Fortune does not multiply this loot. [Exact loot](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/loot_table/blocks/ancient_debris.json) · [Tool requirements](../blocks/OreResources.md#bring-a-suitable-pickaxe)

## Processing and placement

Process **1 Ancient Debris into 1 Netherite Scrap** in a Furnace or Blast Furnace. The bundled recipes specify **200 / 100 ticks** respectively and **2 recipe XP** per input. Fuel, XP collection and operating conditions follow the [Furnace guide](../blocks/Furnace.md). [Smelting recipe](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/smelting/netherite_scrap.json) · [Blasting recipe](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/resources/data/minecraft/recipe/blasting/netherite_scrap_from_blasting.json)

Four Netherite Scraps and four Gold Ingots make one Netherite Ingot; see the [complete processing chain](../blocks/OreResources.md#ancient-debris-to-netherite). Place the item as a full block when you want to keep it in the world, then apply the same harvesting rules to collect it again. The [block guide](../blocks/OreResources.md) owns placed behavior and the exact recipe/loot matrix.

## Verification

Source-reviewed on **2026-10-02** at `2f6c6d4689df9796912eea87cf9def80fc320ee1`; no in-game mining, processing or placement test was run. Data packs and game rules can change results. [Active registration](https://github.com/HungLo2020/MattMC/blob/2f6c6d4689df9796912eea87cf9def80fc320ee1/src/main/java/net/minecraft/world/level/block/Blocks.java#L5770)

[Items](Items.md) · [Blocks](../blocks/Blocks.md)

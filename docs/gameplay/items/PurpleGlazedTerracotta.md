# Purple Glazed Terracotta

**Purple Glazed Terracotta** (`minecraft:purple_glazed_terracotta`) is the directional, glazed form made from [Purple Terracotta](PurpleTerracotta.md). It shares the [Terracotta family's](../blocks/Terracotta.md) pickaxe collection rules but has distinct placement and piston behavior. [Block registration][block] · [Item registration][item]

## Smelting

Smelt **one [Purple Terracotta](PurpleTerracotta.md)** in a fueled [Furnace](../blocks/Furnace.md) to make **one purple glazed terracotta**. The recipe takes **200 game ticks**, approximately **10 seconds at 20 ticks per second**. Its input must be the matching dyed form; uncolored Terracotta and other colors are not accepted. [Exact recipe][recipe]

See [shared crafting and smelting](../blocks/Terracotta.md#crafting-and-smelting) for the prior dye step, recipe experience, and conversion limits.

## Placement and collection

The block has four horizontal facings, set **opposite your horizontal facing when placed**. Change your facing before placement to choose its orientation. See [placement and facing](../blocks/Terracotta.md#placement-and-facing) for a directional example. [Placement callback][facing]

Use an **unbroken pickaxe** to collect **one purple glazed terracotta**. It stays glazed; the loot has no Silk Touch requirement or Fortune count bonus and has an explosion-survival condition. [Correct-tool registration][block] · [Block loot][loot]

It is **push-only** for pistons. Follow the [shared piston rules](../blocks/Terracotta.md#pistons-and-sticky-blocks) for Sticky Pistons and Slime/Honey movement; the decorative facing does not change those rules.

Related: [Purple Terracotta](PurpleTerracotta.md) · [All family colors](../blocks/Terracotta.md#forms-and-colors) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting, smelting, and collection routes, not a survey of structures or trades. No gameplay test was run. Data packs can change recipes, tags, and loot.

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L4430-L4589
[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L823-L838
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/purple_glazed_terracotta.json
[facing]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/GlazedTerracottaBlock.java
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/purple_glazed_terracotta.json

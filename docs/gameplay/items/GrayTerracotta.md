# Gray Terracotta

**Gray Terracotta** (`minecraft:gray_terracotta`) is the gray dyed member of the [Terracotta family](../blocks/Terracotta.md). It can be used directly for building or smelted into [Gray Glazed Terracotta](GrayGlazedTerracotta.md). [Block registration][block] · [Item registration][item]

## Making this color

Use **[Gray Dye](GrayDye.md)** in the [shared terracotta dye recipe](../blocks/Terracotta.md#crafting-and-smelting) to produce **eight gray terracotta**. The recipe accepts only uncolored [Terracotta](Terracotta.md), not another dyed color. [Exact recipe][recipe]

For glazing, follow the [Gray Glazed Terracotta smelting route](GrayGlazedTerracotta.md#smelting). The bundled recipes do not recolor this dyed block or remove its dye; choose your color before crafting. [Family recipe limits](../blocks/Terracotta.md#crafting-and-smelting)

## Collecting and building

An **unbroken pickaxe** collects **one gray terracotta**; a Wooden Pickaxe is sufficient. Its block loot has no Silk Touch requirement or Fortune count bonus and includes an explosion-survival condition. See [mining and drops](../blocks/Terracotta.md#mining-and-drops) for the shared tool gate. [Correct-tool registration][block] · [Pickaxe tag][pickaxe] · [Block loot][loot]

This dyed form has normal [piston behavior](../blocks/Terracotta.md#pistons-and-sticky-blocks). Glazing it changes that behavior to push-only.

Related: [Terracotta](Terracotta.md) · [Gray Glazed Terracotta](GrayGlazedTerracotta.md) · [All family colors](../blocks/Terracotta.md#forms-and-colors) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting, smelting, and collection routes, not a survey of structures or trades. No gameplay test was run. Data packs can change recipes, tags, and loot.

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2907-L3030
[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L635-L650
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/gray_terracotta.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/gray_terracotta.json

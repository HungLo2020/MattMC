# Cyan Terracotta

**Cyan Terracotta** (`minecraft:cyan_terracotta`) is the cyan dyed member of the [Terracotta family](../blocks/Terracotta.md). It can be used directly for building or smelted into [Cyan Glazed Terracotta](CyanGlazedTerracotta.md). [Block registration][block] · [Item registration][item]

## Making this color

Use **[Cyan Dye](CyanDye.md)** in the [shared terracotta dye recipe](../blocks/Terracotta.md#crafting-and-smelting) to produce **eight cyan terracotta**. The recipe accepts only uncolored [Terracotta](Terracotta.md), not another dyed color. [Exact recipe][recipe]

For glazing, follow the [Cyan Glazed Terracotta smelting route](CyanGlazedTerracotta.md#smelting). The bundled recipes do not recolor this dyed block or remove its dye; choose your color before crafting. [Family recipe limits](../blocks/Terracotta.md#crafting-and-smelting)

## Collecting and building

An **unbroken pickaxe** collects **one cyan terracotta**; a Wooden Pickaxe is sufficient. Its block loot has no Silk Touch requirement or Fortune count bonus and includes an explosion-survival condition. See [mining and drops](../blocks/Terracotta.md#mining-and-drops) for the shared tool gate. [Correct-tool registration][block] · [Pickaxe tag][pickaxe] · [Block loot][loot]

This dyed form has normal [piston behavior](../blocks/Terracotta.md#pistons-and-sticky-blocks). Glazing it changes that behavior to push-only.

Related: [Terracotta](Terracotta.md) · [Cyan Glazed Terracotta](CyanGlazedTerracotta.md) · [All family colors](../blocks/Terracotta.md#forms-and-colors) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting, smelting, and collection routes, not a survey of structures or trades. No gameplay test was run. Data packs can change recipes, tags, and loot.

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L2907-L3030
[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L635-L650
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/crafting/cyan_terracotta.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/cyan_terracotta.json

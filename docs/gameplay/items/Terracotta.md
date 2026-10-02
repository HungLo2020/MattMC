# Terracotta

**Terracotta** (`minecraft:terracotta`) is the uncolored base material for the 16 dyed terracotta colors. It is distinct from White Terracotta and from all glazed forms. Use the [Terracotta block-family guide](../blocks/Terracotta.md) for the complete conversion chain, mining, and piston behavior. [Block registration][block] · [Item registration][item]

## Obtaining

Smelt a **[Clay block](Clay.md)** in a fueled [Furnace](../blocks/Furnace.md) using the [shared smelting recipe](../blocks/Terracotta.md#crafting-and-smelting). The input is `minecraft:clay`, not an individual Clay Ball. [Recipe][recipe]

Existing placed Terracotta can be collected with an **unbroken pickaxe**, including a Wooden Pickaxe. Its block loot returns **one Terracotta**, has no Silk Touch condition or Fortune count bonus, and includes an explosion-survival condition. [Tool requirement][block] · [Pickaxe tag][pickaxe] · [Loot][loot]

## Dyeing and building

Choose a [dyed color](../blocks/Terracotta.md#forms-and-colors) and follow the [common dye recipe](../blocks/Terracotta.md#crafting-and-smelting). The uncolored block is the required input; White Terracotta is already dyed and cannot replace it in those recipes.

To make Glazed Terracotta, dye the base material first, then smelt the matching dyed form. Uncolored Terracotta has normal [piston behavior](../blocks/Terracotta.md#pistons-and-sticky-blocks), and does not require continuing support underneath once placed.

Related: [Clay](Clay.md) · [Terracotta family](../blocks/Terracotta.md) · [Furnace](../blocks/Furnace.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. These are selected crafting, smelting, and collection routes, not a survey of structures or trades. No gameplay test was run. Data packs can change recipes, tags, and loot.

[block]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/level/block/Blocks.java#L3274-L3277
[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L709
[recipe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/recipe/smelting/terracotta.json
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[loot]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/resources/data/minecraft/loot_table/blocks/terracotta.json

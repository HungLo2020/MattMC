# Gravel

**Gravel** (`minecraft:gravel`) is a terrain block item. The [Soil, Sand, and Gravel guide](../blocks/SoilSandAndGravel.md) covers its placed behavior, support, conversions, and collection. [Item registration][registration] · [Block registration][block-registration]

## Collecting and moving it

Ordinary mining selects **1 Gravel or 1 Flint**. Fortune changes the Flint chance; **Silk Touch always selects Gravel**. See the [exact chance table](../blocks/SoilSandAndGravel.md#gravel-and-flint). [Block loot][loot]

A shovel is the efficient mining tool. No tool material tier is required for this block’s ordinary loot. [Mining tag][shovel-tag] · [Player harvest gate][player]

## Placed uses

Gravel follows the [falling rules](../blocks/SoilSandAndGravel.md#sand-red-sand-and-gravel-falling). The falling-entity item-drop route returns Gravel directly and does not roll Flint. Its selected crafting uses include [Coarse Dirt](../blocks/SoilSandAndGravel.md#coarse-dirt-recipe) and [Concrete Powder](../blocks/Concrete.md).

Follow the shared guide for the exact conditions and recipe sources. [Soil, Sand, and Gravel](../blocks/SoilSandAndGravel.md) · [Blocks](../blocks/Blocks.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game placement, mining, spread, or conversion test was run. This page covers the checked collection route and links the shared placed-block mechanics; it is not an exhaustive acquisition or world-generation inventory.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L149
[block-registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L313-L347
[loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/gravel.json
[shovel-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[player]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L656

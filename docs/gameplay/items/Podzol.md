# Podzol

**Podzol** (`minecraft:podzol`) is a terrain block item. The [Soil, Sand, and Gravel guide](../blocks/SoilSandAndGravel.md) covers its placed behavior, support, conversions, and collection. [Item registration][registration] · [Block registration][block-registration]

## Collecting and moving it

For a supply to collect, follow [natural Podzol and large-Spruce ground conversion](../blocks/SoilSandAndGravel.md#podzol-from-natural-ground-or-large-spruce-trees). The shared guide also lists [optional Wandering Trader purchases](../blocks/SoilSandAndGravel.md#optional-wandering-trader-supplies), which provide Podzol as an item.

Use **Silk Touch** to retain Podzol. Ordinary mining instead gives **1 Dirt**; Fortune does not change the result. [Block loot][loot]

A shovel is the efficient mining tool. No tool material tier is required for this block’s ordinary loot. [Mining tag][shovel-tag] · [Player harvest gate][player]

## Placed uses

Podzol uses [stable surface and mushroom-support rules](../blocks/SoilSandAndGravel.md#podzol-and-mushroom-support), without Grass/Mycelium spreading. A shovel can turn it into a Dirt Path, but the checked hoe map does not include Podzol.

Follow the shared guide for the exact conditions and recipe sources. [Soil, Sand, and Gravel](../blocks/SoilSandAndGravel.md) · [Blocks](../blocks/Blocks.md) · [Items](Items.md)

## Sources and verification

Acquisition links added on **2026-10-10**, following the shared guide’s source review at `f86206767dadde696adfed4e04c5ee97cd0d0885`. The shared guide owns the new generation, replenishment, and trade evidence; the earlier collection review below remains in place. No new in-game acquisition or growth test was run.

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game placement, mining, spread, or conversion test was run. This page covers the checked collection route and links the shared placed-block mechanics; it is not an exhaustive acquisition or world-generation inventory.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L114
[block-registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L127-L134
[loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/podzol.json
[shovel-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[player]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L656

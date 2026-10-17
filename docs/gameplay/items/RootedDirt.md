# Rooted Dirt

**Rooted Dirt** (`minecraft:rooted_dirt`) is a terrain block item. The [Soil, Sand, and Gravel guide](../blocks/SoilSandAndGravel.md) covers its placed behavior, support, conversions, and collection. [Item registration][registration] · [Block registration][block-registration]

## Collecting and moving it

For a supply to collect, follow [natural root systems and planted-Azalea ground conversion](../blocks/SoilSandAndGravel.md#rooted-dirt-from-natural-roots-or-planted-azalea). The shared guide also lists [optional Wandering Trader purchases](../blocks/SoilSandAndGravel.md#optional-wandering-trader-supplies). Growing Hanging Roots beneath an existing block is a different route from producing more Rooted Dirt.

Mine a Rooted Dirt block with any tool or by hand to receive **1 Rooted Dirt**. Its loot does not require Silk Touch. [Block loot][loot]

A shovel is the efficient mining tool. No tool material tier is required for this block’s ordinary loot. [Mining tag][shovel-tag] · [Player harvest gate][player]

## Placed uses

[Bone Meal can grow Hanging Roots beneath it](../blocks/SoilSandAndGravel.md#rooted-dirt-and-hanging-roots) when that space is air. Hoeing instead converts the source to Dirt and drops Hanging Roots; shovel and water-bottle conversions have different results.

Follow the shared guide for the exact conditions and recipe sources. [Soil, Sand, and Gravel](../blocks/SoilSandAndGravel.md) · [Blocks](../blocks/Blocks.md) · [Items](Items.md)

## Sources and verification

Acquisition links added on **2026-10-10**, following the shared guide’s source review at `f86206767dadde696adfed4e04c5ee97cd0d0885`. The shared guide owns the new generation, replenishment, and trade evidence; the earlier collection review below remains in place. No new in-game acquisition or growth test was run.

Source-reviewed on **2026-10-02** at `6fe3f1e877707e45ee3159929bb9cd8769d6bda7`. No in-game placement, mining, spread, or conversion test was run. This page covers the checked collection route and links the shared placed-block mechanics; it is not an exhaustive acquisition or world-generation inventory.

[registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/item/Items.java#L115
[block-registration]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/level/block/Blocks.java#L6649-L6651
[loot]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/loot_table/blocks/rooted_dirt.json
[shovel-tag]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/resources/data/minecraft/tags/block/mineable/shovel.json
[player]: https://github.com/HungLo2020/MattMC/blob/6fe3f1e877707e45ee3159929bb9cd8769d6bda7/src/main/java/net/minecraft/world/entity/player/Player.java#L623-L656

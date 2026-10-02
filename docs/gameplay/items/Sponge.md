# Sponge

## Obtaining

Dry a [Wet Sponge](WetSponge.md) in a Furnace, or place it in an ultrawarm dimension such as the bundled Nether. The [Sponge and Wet Sponge guide](../blocks/Sponge.md#getting-your-first-sponges) explains the checked Ocean Monument and Elder Guardian starting routes. [Smelting recipe][recipe] · [Placement drying][wet]

## Usage

Place a dry Sponge beside reachable water to absorb it. A successful absorption changes the block to Wet Sponge; recover and dry it before reuse. See [Dry Sponge](../blocks/Sponge.md#dry-sponge) for the six-step search, 64-water-position traversal limit and supported waterlogged blocks. [Absorption][absorb]

## Behavior

A dry Sponge drops one Sponge when normally mined, including by hand. A Hoe is the speed tool. Mining an already wet block gives Wet Sponge instead. [Block properties][register] · [Hoe tag][hoe] · [Dry loot][loot]

## Notes

* This item is the item form of the `minecraft:sponge` block. [Item registration][items]
* Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`; no in-game absorption, mining or drying test was run

Related: [Placed-block guide](../blocks/Sponge.md) · [Wet Sponge](WetSponge.md) · [Items](Items.md)

[recipe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/smelting/sponge.json#L1-L10
[wet]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/WetSpongeBlock.java#L14-L72
[absorb]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/SpongeBlock.java#L32-L84
[register]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L614-L619
[hoe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/mineable/hoe.json#L1-L17
[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L279-L280
[loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/sponge.json#L1-L21

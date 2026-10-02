# Wet Sponge

## Obtaining

Recover a Sponge after it absorbs water, collect Wet Sponge blocks from a generated Ocean Monument sponge room, or defeat an Elder Guardian with player kill credit. The checked Elder Guardian loot pool gives one Wet Sponge without a Looting multiplier. See [starting sources](../blocks/Sponge.md#getting-your-first-sponges) for room-generation and loot conditions. [Absorption][absorb] · [Monument placement][sponge-room] · [Elder Guardian loot][elder-loot]

## Usage

Smelt **1 Wet Sponge → 1 Sponge** in a Furnace, or place it in an ultrawarm dimension such as the bundled Nether for immediate drying. Have an empty Bucket in the Furnace fuel slot when smelting finishes to obtain one Water Bucket as well. [Drying and bucket instructions](../blocks/Sponge.md#wet-sponge) · [Recipe][recipe] · [Bucket conversion][furnace-bucket] · [Placement drying][wet]

## Behavior

A Wet Sponge absorbs no further water until dried. Normal mining, including by hand, returns one Wet Sponge; a Hoe speeds mining. Its dripping particles do not gradually dry the block. [Wet block][wet] · [Block properties][register] · [Hoe tag][hoe] · [Wet loot][loot]

## Notes

* This item is the item form of the `minecraft:wet_sponge` block. [Item registration][items]
* Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`; no in-game acquisition, absorption, mining or drying test was run

Related: [Placed-block guide](../blocks/Sponge.md) · [Sponge](Sponge.md) · [Items](Items.md)

[absorb]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/SpongeBlock.java#L32-L84
[sponge-room]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/levelgen/structure/structures/OceanMonumentPieces.java#L1725-L1760
[elder-loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/entities/elder_guardian.json#L111-L125
[recipe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/smelting/sponge.json#L1-L10
[furnace-bucket]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L210-L261
[wet]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/WetSpongeBlock.java#L14-L72
[register]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L614-L619
[hoe]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/tags/block/mineable/hoe.json#L1-L17
[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L279-L280
[loot]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/wet_sponge.json#L1-L21

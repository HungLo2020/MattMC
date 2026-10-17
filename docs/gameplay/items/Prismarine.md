# Prismarine

**Prismarine** (`minecraft:prismarine`) is the ordinary full-block item in the [Prismarine construction family](../blocks/Prismarine.md). It is distinct from Prismarine Bricks, Dark Prismarine and the small crafting Shards. [Item registration][items]

## Obtaining

Collect the placed block from Ocean Monuments with an unbroken pickaxe, including Wood, or make it from Shards. The [family guide](../blocks/Prismarine.md#obtaining-and-mining) explains the natural route and tool requirement; its [full-block recipes](../blocks/Prismarine.md#crafting-full-blocks) give the exact arrangement and yield. Ordinary block loot returns Prismarine, not Shards. [Loot][loot-prismarine]

## Usage

Place it as a building block, or turn it into [Prismarine stairs, slabs and walls](../blocks/Prismarine.md#crafting-stairs-slabs-and-walls). The [Stonecutter choices](../blocks/Prismarine.md#stonecutting) save material for stairs. The full block also works in a [Conduit frame](../blocks/Conduit.md#build-a-valid-frame) and in [Tide template duplication](../blocks/Prismarine.md#conduit-frames-and-other-uses).

## Behavior

The [placed-block guide](../blocks/Prismarine.md#placement-water-and-support) owns placement, mining properties, support and piston behavior. This full block does not emit light; [Sea Lantern](../blocks/LuminousBlocks.md#sea-lantern) is the related lighting material. [Block properties][blocks] [Properties][properties]

## Notes

The full-block item is not reversible Shard storage. The checked bundled recipes do not convert ordinary Prismarine into Prismarine Bricks or Dark Prismarine; each finish has its own [ingredient recipe](../blocks/Prismarine.md#crafting-full-blocks).

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`; no gameplay test. Related: [Prismarine Shard](PrismarineShard.md) · [Prismarine Bricks](PrismarineBricks.md) · [Dark Prismarine](DarkPrismarine.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L750-L755
[loot-prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/prismarine.json#L1-L21
[blocks]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/Blocks.java#L3149-L3178
[properties]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1021

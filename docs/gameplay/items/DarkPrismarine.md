# Dark Prismarine

**Dark Prismarine** (`minecraft:dark_prismarine`) is the dark full-block finish of the [Prismarine family](../blocks/Prismarine.md#dark-prismarine-forms). It has its own stairs and slabs. [Item registration][items]

## Obtaining

Recover placed Dark Prismarine from Ocean Monuments with an unbroken pickaxe, including Wood. For crafting, use the [full-block recipe](../blocks/Prismarine.md#crafting-full-blocks), which requires Prismarine Shards and **Black Dye**. An Ink Sac or an ordinary Prismarine block is not a direct replacement for either named ingredient. [Recipe][craft-dark_prismarine] · [Matching-item loot][loot-dark_prismarine]

## Usage

Place it as a solid building block or make its [stairs and slabs](../blocks/Prismarine.md#crafting-stairs-slabs-and-walls). The [Stonecutter](../blocks/Prismarine.md#stonecutting) gives one matching stair or two matching slabs per full block. The full block also qualifies for a [Conduit frame](../blocks/Conduit.md#build-a-valid-frame); its shaped forms do not.

## Behavior

Use the [family placement and mining guide](../blocks/Prismarine.md#placement-water-and-support) for the placed block's properties, water, support and piston rules. Mining returns the block item, not the Shards or Dye used to craft it. Silk Touch is unnecessary and Fortune does not increase that ordinary drop. [Loot][loot-dark_prismarine]

## Notes

There is no registered Dark Prismarine Wall in the checked family. A double Dark Prismarine Slab remains a slab block state and is not equivalent to a full Dark Prismarine block for Conduit frames. [Frame materials][conduit]

Source-reviewed on **2026-10-02** at `fc47f0654e74d1e5bd3b7f3522cc1e8117694418`; no gameplay test. Related: [Prismarine](Prismarine.md) · [Prismarine Shard](PrismarineShard.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/item/Items.java#L750-L755
[craft-dark_prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/recipe/crafting/dark_prismarine.json#L1-L17
[loot-dark_prismarine]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/resources/data/minecraft/loot_table/blocks/dark_prismarine.json#L1-L21
[conduit]: https://github.com/HungLo2020/MattMC/blob/fc47f0654e74d1e5bd3b7f3522cc1e8117694418/src/main/java/net/minecraft/world/level/block/entity/ConduitBlockEntity.java#L36-L42

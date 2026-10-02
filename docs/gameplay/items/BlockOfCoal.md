# Block of Coal

A **Block of Coal** (`minecraft:coal_block`) packs nine Coal into one placeable fuel and building block. Keep placed blocks away from spreading fire.

## Obtaining

Fill a 3 × 3 Crafting Table grid with **nine [Coal](Coal.md)** to make one block. One block unpacks shapelessly into nine of the same resource. [Packing][pack-coal_block] · [Unpacking][unpack-coal_block]

Recover a placed block with an **unbroken pickaxe** made from one of these materials: **Wood, Stone, Copper, Iron, Gold, Diamond, Netherite**. Successful normal mining returns one matching block; no Silk Touch is required and Fortune does not increase the count. See [the full harvesting comparison](../blocks/ResourceStorageBlocks.md#recovering-a-placed-block) for tier restrictions. [Pickaxe tag][pickaxe] · [Material rules][tool-material] · [Harvest gate][harvest] · [Broken guard][broken] · [Loot][loot-coal_block]

## Usage

One block supplies **16,000 default Furnace burn ticks**, enough for 80 uninterrupted 200-tick recipes; nine loose Coal supply enough for 72. Already-lit fuel can be wasted when processing stops. Use [Furnace fuel planning](../blocks/Furnace.md#fuel-planning) for practical operation. [Fuel values][fuel] · [Burn loop][fuel-use]

[Charcoal](Charcoal.md) is not accepted by the block recipe. This is not a way to compress Charcoal. [Exact ingredient][pack-coal_block]

## Behavior

The full block stays in place without support and has no waterlogged state. It is flammable in the active fire table; furnace fuel value does not protect a placed stockpile from burning. [Placement defaults][shape-support] [empty-fluid] · [Coal fire entry][coal-fire] · [Fire consumption][fire]

## Notes

This item and its placed block both use `minecraft:coal_block`. The [Resource Storage Blocks guide](../blocks/ResourceStorageBlocks.md#coal-block) compares all ten covered materials, exact conversions, tool requirements and special uses. [Item registration][items]

## Related pages

- [Coal](Coal.md)
- [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. Registration, both conversion recipes, complete loot, tool restrictions and the behavior cited above were checked. No gameplay test was run. Data packs and later source changes may alter these rules.

[items]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L169-L180
[tool-material]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[harvest]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[shape-support]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[empty-fluid]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L224-L234
[fuel]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/FuelValues.java#L38-L48
[fuel-use]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L142-L194
[fire]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/FireBlock.java#L222-L249
[coal-fire]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/FireBlock.java#L460-L463
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[pack-coal_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/coal_block.json
[loot-coal_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/coal_block.json
[unpack-coal_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/coal.json

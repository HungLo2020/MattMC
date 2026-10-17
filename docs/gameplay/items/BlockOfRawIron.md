# Block of Raw Iron

A **Block of Raw Iron** (`minecraft:raw_iron_block`) stores nine pieces of Raw Iron. Unpack it before processing the pieces into ingots.

## Obtaining

Fill a 3 × 3 Crafting Table grid with **nine [Raw Iron](RawIron.md)** to make one block. One block unpacks shapelessly into nine of the same resource. [Packing][pack-raw_iron_block] · [Unpacking][unpack-raw_iron_block]

Recover a placed block with an **unbroken pickaxe** made from one of these materials: **Stone, Copper, Iron, Diamond, Netherite**. Successful normal mining returns one matching block; no Silk Touch is required and Fortune does not increase the count. See [the full harvesting comparison](../blocks/ResourceStorageBlocks.md#recovering-a-placed-block) for tier restrictions. [Pickaxe tag][pickaxe] · [Material rules][tool-material] · [Harvest gate][harvest] · [Broken guard][broken] · [Loot][loot-raw_iron_block]

## Usage

Unpack the block to recover nine [Raw Iron](RawIron.md), then follow [raw-metal smelting or blasting](../blocks/OreResources.md#processing-raw-metal). There is no bundled recipe that smelts or blasts this whole block into nine ingots. [Unpacking][unpack-raw_iron_block]

It is not a Beacon base material or an Iron Golem body block. Those uses require refined [Blocks of Iron](BlockOfIron.md). [Beacon base tag][beacon-tag] · [Golem pattern][golem]

## Behavior

The full block has hardness 5 and blast resistance 6. It has no inventory, falling behavior or waterlogged state; removing support leaves it in place. Mining returns a block rather than automatically unpacking its raw pieces. [Registration][blocks] · [Placement defaults][shape-support] [empty-fluid] · [Loot][loot-raw_iron_block]

## Notes

This item and its placed block both use `minecraft:raw_iron_block`. The [Resource Storage Blocks guide](../blocks/ResourceStorageBlocks.md#raw-iron-block) compares all ten covered materials, exact conversions, tool requirements and special uses. [Item registration][items]

## Related pages

- [Raw Iron](RawIron.md)
- [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. Registration, both conversion recipes, complete loot, tool restrictions and the behavior cited above were checked. No gameplay test was run. Data packs and later source changes may alter these rules.

[blocks]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java
[items]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L169-L180
[tool-material]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[harvest]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[shape-support]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[empty-fluid]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L224-L234
[golem]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L191-L201
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[beacon-tag]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[pack-raw_iron_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/raw_iron_block.json
[loot-raw_iron_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/raw_iron_block.json
[unpack-raw_iron_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/raw_iron.json

# Block of Redstone

A **Block of Redstone** (`minecraft:redstone_block`) stores nine Redstone Dust and becomes a continuous strength-15 power source when placed.

## Obtaining

Fill a 3 × 3 Crafting Table grid with **nine [Redstone Dust](RedstoneDust.md)** to make one block. One block unpacks shapelessly into nine of the same resource. [Packing][pack-redstone_block] · [Unpacking][unpack-redstone_block]

Recover a placed block with an **unbroken pickaxe** made from one of these materials: **Wood, Stone, Copper, Iron, Gold, Diamond, Netherite**. Successful normal mining returns one matching block; no Silk Touch is required and Fortune does not increase the count. See [the full harvesting comparison](../blocks/ResourceStorageBlocks.md#recovering-a-placed-block) for tier restrictions. [Pickaxe tag][pickaxe] · [Material rules][tool-material] · [Harvest gate][harvest] · [Broken guard][broken] · [Loot][loot-redstone_block]

## Usage

Place it beside a component that needs constant power, or unpack it into [Redstone Dust](RedstoneDust.md). It emits **strength 15 in all six neighboring directions**, with no timed pulse or off state. See [the placed power guide](../blocks/ResourceStorageBlocks.md#redstone-power) for its conductor limitation and circuit links. [Output][powered] · [Signal dispatch][signal]

## Behavior

The full block has hardness 5 and blast resistance 6, stays in place without support, and emits no light. Its registration disables redstone conduction despite its solid shape. It is not an accepted Beacon base material. [Properties][redstone-properties] [defaults] · [Support][shape-support] · [Beacon tag][beacon-tag]

## Notes

This item and its placed block both use `minecraft:redstone_block`. The [Resource Storage Blocks guide](../blocks/ResourceStorageBlocks.md#redstone-block) compares all ten covered materials, exact conversions, tool requirements and special uses. [Item registration][redstone-item]

## Related pages

- [Redstone Dust](RedstoneDust.md)
- [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. Registration, both conversion recipes, complete loot, tool restrictions and the behavior cited above were checked. No gameplay test was run. Data packs and later source changes may alter these rules.

[redstone-item]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L999
[tool-material]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[harvest]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[defaults]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1029
[shape-support]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L304-L326
[powered]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/PoweredBlock.java#L18-L30
[signal]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/SignalGetter.java#L48-L91
[redstone-properties]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L2860-L2869
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[beacon-tag]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[pack-redstone_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/redstone_block.json
[loot-redstone_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/redstone_block.json
[unpack-redstone_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/redstone.json

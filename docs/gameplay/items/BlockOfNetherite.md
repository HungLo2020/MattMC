# Block of Netherite

A **Block of Netherite** (`minecraft:netherite_block`) stores nine Netherite Ingots in a hard, highly blast-resistant building block. Recover it with a Diamond or Netherite Pickaxe.

## Obtaining

Fill a 3 × 3 Crafting Table grid with **nine [Netherite Ingots](NetheriteIngot.md)** to make one block. One block unpacks shapelessly into nine of the same resource. [Packing][pack-netherite_block] · [Unpacking][unpack-netherite_block]

Recover a placed block with an **unbroken pickaxe** made from one of these materials: **Diamond, Netherite**. Successful normal mining returns one matching block; no Silk Touch is required and Fortune does not increase the count. See [the full harvesting comparison](../blocks/ResourceStorageBlocks.md#recovering-a-placed-block) for tier restrictions. [Pickaxe tag][pickaxe] · [Material rules][tool-material] · [Harvest gate][harvest] · [Broken guard][broken] · [Loot][loot-netherite_block]

## Usage

Use it for storage, construction or a [Beacon base](../blocks/Beacon.md#build-the-base). The base check treats all five accepted materials alike, so Netherite does not grant a stronger base bonus. Unpack the block into [Netherite Ingots](NetheriteIngot.md) for [Smithing Table upgrades](../blocks/SmithingTable.md). [Base tag][beacon-tag] · [Base check][beacon-base] · [Unpacking][unpack-netherite_block]

## Behavior

The placed block has **hardness 50 and blast resistance 1,200**. Its dropped item separately resists the fire damage group, including lava. That item property does not grant explosion immunity or prevent normal dropped-item despawning. [Block properties][netherite-properties] · [Item registration][items] · [Resistance component][resistant] · [Fire group][fire-damage] · [Item damage][item-damage] · [Despawn][despawn]

## Notes

This item and its placed block both use `minecraft:netherite_block`. The [Resource Storage Blocks guide](../blocks/ResourceStorageBlocks.md#netherite-block) compares all ten covered materials, exact conversions, tool requirements and special uses. [Item registration][items]

## Related pages

- [Netherite Ingots](NetheriteIngot.md)
- [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. Registration, both conversion recipes, complete loot, tool restrictions and the behavior cited above were checked. No gameplay test was run. Data packs and later source changes may alter these rules.

[items]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L169-L180
[tool-material]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[harvest]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[netherite-properties]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Blocks.java#L5766-L5769
[resistant]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Item.java#L402-L404
[item-damage]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L291
[despawn]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L161-L176
[beacon-base]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/entity/BeaconBlockEntity.java#L203-L228
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[beacon-tag]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[fire-damage]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[pack-netherite_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/netherite_block.json
[loot-netherite_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/netherite_block.json
[unpack-netherite_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/netherite_ingot_from_netherite_block.json

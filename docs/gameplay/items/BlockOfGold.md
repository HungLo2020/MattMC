# Block of Gold

A **Block of Gold** (`minecraft:gold_block`) stores nine Gold Ingots and can form part of a Beacon base. It has different uses from a Block of Raw Gold.

## Obtaining

Fill a 3 × 3 Crafting Table grid with **nine [Gold Ingots](GoldIngot.md)** to make one block. One block unpacks shapelessly into nine of the same resource. [Packing][pack-gold_block] · [Unpacking][unpack-gold_block]

Recover a placed block with an **unbroken pickaxe** made from one of these materials: **Iron, Diamond, Netherite**. Successful normal mining returns one matching block; no Silk Touch is required and Fortune does not increase the count. See [the full harvesting comparison](../blocks/ResourceStorageBlocks.md#recovering-a-placed-block) for tier restrictions. [Pickaxe tag][pickaxe] · [Material rules][tool-material] · [Harvest gate][harvest] · [Broken guard][broken] · [Loot][loot-gold_block]

## Usage

Use it in a [Beacon base](../blocks/Beacon.md#build-the-base), or unpack it into [Gold Ingots](GoldIngot.md) for their recipes and uses. The block itself is not a Beacon payment item or Piglin barter currency. [Base tag][beacon-tag] · [Payment tag][payment] · [Barter currency][currency] [currency-test]

## Behavior

Breaking a placed Gold Block can anger nearby eligible idle [Piglins](../mobs/Piglin.md), including when your tool would not recover it. Raw Gold Blocks share this guarded status. Read [the family warning](../blocks/ResourceStorageBlocks.md#gold-and-piglins) before moving either near Piglins. [Guarded tag][guarded] · [Break callback][piglin-break] · [Anger conditions][piglin-anger] [piglin-target]

## Notes

This item and its placed block both use `minecraft:gold_block`. The [Resource Storage Blocks guide](../blocks/ResourceStorageBlocks.md#gold-block) compares all ten covered materials, exact conversions, tool requirements and special uses. [Item registration][items]

## Related pages

- [Gold Ingots](GoldIngot.md)
- [Resource Storage Blocks](../blocks/ResourceStorageBlocks.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at `8d065c943710a8d4e3d609b0406dda95ed62cc63` on 2026-10-02. Registration, both conversion recipes, complete loot, tool restrictions and the behavior cited above were checked. No gameplay test was run. Data packs and later source changes may alter these rules.

[items]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/Items.java#L169-L180
[tool-material]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ToolMaterial.java#L23-L48
[harvest]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/server/level/ServerPlayerGameMode.java#L273-L292
[broken]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/item/ItemStack.java#L587-L589
[piglin-break]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/level/block/Block.java#L480-L487
[piglin-anger]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L524-L533
[piglin-target]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L676-L688
[currency]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L80
[currency-test]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L801-L803
[pickaxe]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json
[beacon-tag]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/beacon_base_blocks.json
[payment]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/item/beacon_payment_items.json
[guarded]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/tags/block/guarded_by_piglins.json
[pack-gold_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/gold_block.json
[loot-gold_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/loot_table/blocks/gold_block.json
[unpack-gold_block]: https://github.com/HungLo2020/MattMC/blob/8d065c943710a8d4e3d609b0406dda95ed62cc63/src/main/resources/data/minecraft/recipe/crafting/gold_ingot_from_gold_block.json

# Resin Clump

## Obtaining

Collect placed Resin Clumps produced by an operating [Creaking Heart](../blocks/CreakingHeart.md#producing-resin), unpack **one Block of Resin into nine clumps**, or obtain the Heart's non-Silk loot. Woodland Mansion chests also have a weighted clump entry; it is not guaranteed in every chest. [Unpacking recipe][craft-resin_clump] · [Heart loot][loot-creaking_heart] · [Mansion entry][mansion-loot]

## Usage

Place clumps as thin surface decorations, pack nine into a Block of Resin, or smelt each clump into **one Resin Brick** in **200 ticks** with **0.1 recipe XP**. The [Resin guide](../blocks/Resin.md#crafting-and-smelting) covers the complete building conversions. [Packing][craft-resin_block] · [Smelting][smelt]

## Behavior

One block space can carry up to **six attached faces** and can waterlog. Each face needs a suitable neighboring surface. Ordinary direct mining needs no special tool and returns **one clump item per occupied face**; Fortune and Silk Touch do not increase this loot. [Placement][clump-placement] · [Support][clump-support] · [Loot][loot-resin_clump]

## Notes

* This block item is registered as `minecraft:resin_clump`; it is not the smelted `minecraft:resin_brick` ingredient. [Registration][items]
* Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`; no gameplay test was run. See the [placed-block guide](../blocks/Resin.md#resin-clump).

[craft-resin_clump]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_clump.json
[loot-creaking_heart]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/creaking_heart.json
[mansion-loot]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/chests/woodland_mansion.json#L211-L232
[craft-resin_block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_block.json
[smelt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/smelting/resin_brick.json
[clump-placement]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L175-L218
[clump-support]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/MultifaceBlock.java#L124-L173
[loot-resin_clump]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/resin_clump.json
[items]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L561-L567

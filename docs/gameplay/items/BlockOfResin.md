# Block of Resin

## Obtaining

Craft **nine Resin Clumps in a filled 3 × 3 grid** to make one Block of Resin. Ordinary mining returns the block itself without requiring a tool. [Recipe][craft-resin_block] · [Loot][loot-resin_block] · [Registration][blocks]

## Usage

Use it as a full building/storage block, unpack one into **nine Resin Clumps**, or combine it with **two unstripped Pale Oak Logs** to craft a [Creaking Heart](../blocks/CreakingHeart.md#crafting-and-placing-a-heart). It is not the Resin Bricks masonry block. [Unpacking][craft-resin_clump] · [Heart recipe][craft-creaking_heart]

## Behavior

It has **zero default hardness and blast resistance**, needs no continuing support, and has no waterlogged state. Silk Touch is unnecessary and Fortune adds no block drops. To make Resin Brick ingredients, unpack the block and smelt the clumps individually. [Properties][blocks] · [Defaults][default-properties] · [Full-block behavior][full-shape] · [Loot][loot-resin_block] · [Smelting][smelt]

## Notes

* This item is the item form of the `minecraft:resin_block` block. [Item registration][items]
* Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`; no gameplay test was run. See [Resin](../blocks/Resin.md#block-of-resin) for the full material family.

[craft-resin_block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_block.json
[loot-resin_block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/resin_block.json
[blocks]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L2416-L2489
[craft-resin_clump]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/resin_clump.json
[craft-creaking_heart]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/creaking_heart.json
[default-properties]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L992-L1022
[full-shape]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/state/BlockBehaviour.java#L309-L326
[smelt]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/smelting/resin_brick.json
[items]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L561-L567

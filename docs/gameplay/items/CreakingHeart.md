# Creaking Heart

## Obtaining

Craft **one Block of Resin between two unstripped Pale Oak Logs in a vertical column**, or mine a Heart with **Silk Touch I or higher**. Without Silk Touch, its normal loot is **1–3 Resin Clumps**, with a Fortune bonus, rather than the Heart item. [Recipe][craft-creaking_heart] · [Loot][loot-creaking_heart]

## Usage

Place it between **two accepted Pale Oak timber blocks on its axis**, with all three axes aligned. The [Creaking Heart block guide](../blocks/CreakingHeart.md) covers night, dimension, player-distance and spawn-space conditions, and the [Resin-production loop](../blocks/CreakingHeart.md#producing-resin). A placed Heart does not need natural origin to function. [Activation][heart-placement] · [Operating conditions][heart-tick]

## Behavior

An axe speeds mining, but no tool type is required for the non-Silk drops. Natural Hearts have a separate **20–24 XP** player-destruction reward; ordinary item placement resets the Heart to **natural=false**, even for a Silk Touch pickup. Breaking the Heart removes its linked Creaking. Removing just one required log does not reliably remove an existing protector. [Properties][heart-block] · [Axe tag][axe] · [Loot][loot-creaking_heart] · [XP and removal][heart-removal] · [Placement default][heart-state] · [Missing-log behavior][heart-tick]

## Notes

* This item is the item form of the `minecraft:creaking_heart` block. [Item registration][heart-item]
* Source-reviewed on **2026-10-02** at `cf1c134b3f9ff634490e448fe26c335b90f82227`; no gameplay test was run. Full conditions and qualifications belong to the [placed-block guide](../blocks/CreakingHeart.md).

[craft-creaking_heart]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/recipe/crafting/creaking_heart.json
[loot-creaking_heart]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/loot_table/blocks/creaking_heart.json
[heart-placement]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L84-L154
[heart-tick]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/entity/CreakingHeartBlockEntity.java#L71-L145
[heart-block]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/Blocks.java#L1238-L1242
[axe]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/resources/data/minecraft/tags/block/mineable/axe.json
[heart-removal]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L161-L205
[heart-state]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/level/block/CreakingHeartBlock.java#L36-L71
[heart-item]: https://github.com/HungLo2020/MattMC/blob/cf1c134b3f9ff634490e448fe26c335b90f82227/src/main/java/net/minecraft/world/item/Items.java#L475

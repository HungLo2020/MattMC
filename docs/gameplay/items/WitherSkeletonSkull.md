# Wither Skeleton Skull

**Wither Skeleton Skull** (`minecraft:wither_skeleton_skull`) is a rare drop, a placeable decoration, and a head-slot item. Save **three** if your goal is summoning the [Wither](../mobs/Wither.md#summoning); completing that construction creates a dangerous boss. [Item registration][item] · [Summoning check][summon]

## Obtaining

### Ordinary mob drop

With mob loot enabled, a [Wither Skeleton](../mobs/WitherSkeleton.md) has a separate chance to drop **one skull** on a **player-attributed kill**:

| Looting level | Skull chance per qualifying kill |
| --- | ---: |
| None | 2.5% |
| I | 3.5% |
| II | 4.5% |
| III | 5.5% |

These are independent loot-roll chances, not a guarantee after a certain number of kills. Player attribution is not identical to requiring the player's final hit: a recent qualifying player or owned tame-wolf attack can supply credit. Looting's bonus is separately checked against the attacking living entity in the killing damage source, so recent player credit alone does not preserve a weapon's Looting bonus for an environmental finishing blow. [Skull loot pool][loot] · [Player-credit tracking][credit] · [Loot context][context] · [Player condition][player-condition] · [Looting chance calculation][chance] · [Looting equipment slot][looting]

### Charged Creeper route

A charged [Creeper](../mobs/Creeper.md) that kills a Wither Skeleton can also create **one skull** without the ordinary percentage or player-kill condition, provided mob loot is enabled and that Creeper has not already produced an eligible head drop. Its special head route is limited to **one eligible victim per Creeper**, so an explosion killing several eligible mobs does not give a special skull for each one. [Charged-Creeper callback][creeper] · [Victim selection][charged-root] · [Special skull table][charged-skull]

The special route runs separately from ordinary death loot. It is not a claim that only one skull can ever appear from the entire explosion if ordinary player-attributed rolls also qualify. These are selected verified acquisition routes, not an exhaustive list of world-specific rewards. [Death dispatch order][death-order]

## Placement and wearing

Place the item on a suitable surface for a standing skull or on a wall for its wall form. Breaking the placed skull normally returns **one skull item**. [Standing/wall placement][placement] · [Block loot][block-loot]

To wear it, put it in the inventory's **head equipment slot**. Its registration explicitly disables held-item quick swapping, so ordinary use in the air does not equip it like a swappable helmet. Wearing this skull does not grant the matching-mob detection reduction used by some other mob heads: Wither Skeletons are absent from that reduction check. [Head-slot registration][item] · [Held-use behavior][item-use] · [Head-slot acceptance][armor-slot] · [Detection check][detection]

**Be careful when decorating around Soul Sand or Soul Soil.** Placing a completing standing or wall skull can trigger the Wither check in non-Peaceful difficulty and consume the construction blocks. Follow the canonical [Wither summoning guide](../mobs/Wither.md#summoning) for the exact layout, then read its arrival-explosion warning before placing the final skull. [Standing-skull trigger and pattern][summon] · [Wall-skull trigger][wall-trigger]

## Crafting use

Combine **one [Paper](Paper.md)** and **one Wither Skeleton Skull**, shapeless, to craft **one [Skull Banner Pattern](SkullBannerPattern.md)**. This uses a skull that could otherwise count toward the three needed for a Wither summon. [Banner-pattern recipe][recipe]

Related: [Wither Skeleton](../mobs/WitherSkeleton.md) · [Wither](../mobs/Wither.md) · [Nether Star](NetherStar.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game skull farming, equipment, charged-Creeper, crafting, or summoning test was run. Data packs can change loot and recipes; custom item data and world conditions can change the interactions described here.

[item]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L2068-L2072
[summon]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/WitherSkullBlock.java#L42-L104
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/wither_skeleton.json#L64-L88
[credit]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1324-L1338
[context]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1513-L1527
[player-condition]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[chance]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemRandomChanceWithEnchantedBonusCondition.java#L41-L45
[looting]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/looting.json#L32-L40
[creeper]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/Creeper.java#L170-L179
[charged-root]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/charged_creeper/root.json#L49-L61
[charged-skull]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/charged_creeper/wither_skeleton.json
[death-order]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1413-L1435
[placement]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/StandingAndWallBlockItem.java#L27-L45
[block-loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/blocks/wither_skeleton_skull.json
[item-use]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Item.java#L173-L188
[armor-slot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/inventory/ArmorSlot.java#L32-L40
[detection]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L902-L911
[wall-trigger]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/WitherWallSkullBlock.java#L24-L27
[recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/skull_banner_pattern.json

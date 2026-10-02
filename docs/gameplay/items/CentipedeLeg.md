# Centipede Leg

A **Centipede Leg** is both food and the crafting material for [Centipede Leggings](CentipedeLeggings.md). Save seven if you want the armor; eating a leg carries a Hunger risk. [Registration][centipede-items] · [Recipe][leggings-recipe]

## Obtaining

A [Cave Centipede](../mobs/CaveCentipede.md) head drops **1–3 legs**, with **up to one extra leg per Looting level** (up to **6 total with Looting III**). Its death-loot table has no player-kill condition or fire-cooking conversion; body and tail tables are empty. Mob-loot rules still apply. Natural centipede spawning is not wired into the checked bundled spawn lists, so the mob guide explains the egg route. [Head loot][head-loot] · [Body loot][body-loot] · [Tail loot][tail-loot] · [Death-loot caller][death-loot] · [Looting count calculation][looting]

The leg is also an ordinary category entry available through the [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival and Creative**. Browser insertion is separate from a mob drop or recipe. [Category entry][categories]

## Usage

Use **seven legs** in the [leggings recipe](CentipedeLeggings.md#obtaining). This is the checked bundled recipe that uses the item. [Recipe][leggings-recipe]

You can eat a leg to restore **2 hunger points (one drumstick)**. It uses the raw-chicken food and consumption settings, including a **30% chance of Hunger I for 30 seconds** at 20 ticks per second. Burning the mob does not cook these legs or remove their Hunger risk. [Item food settings][centipede-items] · [Nutrition][foods] · [Consumption effect][consume]

## Behavior

Ordinary legs stack to **64** and have no durability. No cooking recipe for them is present in the checked bundled recipe data. [Registration][centipede-items] · [Default stack components][common-components] · [Bundled recipes][recipe-data]

## Notes

Registered as `minecraft:centipede_leg`. [Registration][centipede-items]

Related: [Cave Centipede](../mobs/CaveCentipede.md) · [Centipede Leggings](CentipedeLeggings.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `c1adfb58c73bd6918cde87943be31afef6a2ccf4`. Checked active registrations, relevant callers and bundled data. No in-game spawn, combat, feeding, drop, crafting, equipment or repair test was run. Server data packs and custom components may change these defaults.

[centipede-items]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/Items.java#L1754-L1755
[leggings-recipe]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe/centipede_leggings.json
[head-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/cave_centipede_head.json
[body-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/cave_centipede_body.json
[tail-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/loot_table/entities/cave_centipede_tail.json
[death-loot]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[looting]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L65-L80
[categories]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[foods]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/food/Foods.java#L12
[consume]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/world/item/component/Consumables.java#L26-L28
[common-components]: https://github.com/HungLo2020/MattMC/blob/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L389
[recipe-data]: https://github.com/HungLo2020/MattMC/tree/c1adfb58c73bd6918cde87943be31afef6a2ccf4/src/main/resources/data/minecraft/recipe

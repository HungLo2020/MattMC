# Cooked Trilocaris Tail

Cooked Trilocaris Tail is a food integrated from Alex's Caves. Cooking a raw tail improves its food value.

Registry ID: `minecraft:cooked_trilocaris_tail`. [Item registration](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1679)

## Obtaining

[Trilocaris](../mobs/Trilocaris.md) have a loot-table entry for **0–1 raw tail**. That entry applies furnace smelting when the mob is on fire, allowing the cooked form instead. A drop is not guaranteed, and the checked table has no Looting count bonus.

One raw tail makes one cooked tail using any of these recipes:

| Method | Recipe time | At 20 ticks per second |
| --- | --- | --- |
| Furnace | 200 ticks | 10 seconds |
| Smoker | 100 ticks | 5 seconds |
| Campfire | 600 ticks | 30 seconds |

Each recipe specifies 0.15 experience; that recipe field is not a claim that every cooking device awards experience in the same way. Both forms are also listed in the Creative inventory.

## Eating

| Food | Hunger points | Saturation supplied before caps |
| --- | --- | --- |
| Raw tail | 2 (one hunger icon) | 1.2 |
| Cooked tail | 5 (two and a half hunger icons) | 5 |

These values come from the food registrations and saturation calculation. Actual restoration is limited by the player's current food and saturation levels. Cooking is the better choice when you want more food from each tail.

For the shared Subterranodon interaction, see [other tail uses](TrilocarisTail.md#other-uses).

## Related pages

- [Raw Trilocaris Tail](TrilocarisTail.md)
- [Trilocaris](../mobs/Trilocaris.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed at [snapshot fffe4a073f0b](https://github.com/HungLo2020/MattMC/commit/fffe4a073f0b8d867902b067a6dd022cda31926f) on 2026-10-01; not an in-game test.

- [Trilocaris loot](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/loot_table/entities/trilocaris.json)
- [Food values](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/food/Foods.java)
- [Saturation calculation](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/food/FoodConstants.java)
- [Furnace recipe](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/cooked_trilocaris_tail_smelting.json)
- [Smoker recipe](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/cooked_trilocaris_tail_smoking.json)
- [Campfire recipe](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/cooked_trilocaris_tail_campfire.json)
- [Creative inventory](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java)

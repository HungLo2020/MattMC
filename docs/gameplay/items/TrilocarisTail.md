# Trilocaris Tail

Trilocaris Tail is a food integrated from Alex's Caves. It can be eaten raw or cooked for a larger hunger and saturation gain.

Registry ID: `minecraft:trilocaris_tail`. [Item registration](https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1678)

## Obtaining

[Trilocaris](../mobs/Trilocaris.md) have a loot-table entry for **0–1 raw tail**. That entry applies furnace smelting when the mob is on fire, allowing the cooked form instead. A drop is not guaranteed, and the checked table has no Looting count bonus.

Cook a raw tail to get one [Cooked Trilocaris Tail](CookedTrilocarisTail.md). That page lists furnace, smoker, and campfire times. Both forms are also listed in the Creative inventory.

## Eating

| Food | Hunger points | Saturation supplied before caps |
| --- | --- | --- |
| Raw tail | 2 (one hunger icon) | 1.2 |
| Cooked tail | 5 (two and a half hunger icons) | 5 |

These values come from the food registrations and saturation calculation. Actual restoration is limited by the player's current food and saturation levels. Cooking is the better choice when you want more food from each tail.

## Other uses

Both raw and cooked tails are accepted in the current [Subterranodon](../mobs/Subterranodon.md) taming interaction. See the [source interaction](https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexscaves/server/entity/living/SubterranodonEntity.java#L507-L523); this use has not been tested in a running world.

## Related pages

- [Cooked Trilocaris Tail](CookedTrilocarisTail.md)
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

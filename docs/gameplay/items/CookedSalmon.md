# Cooked Salmon

Cooked Salmon is a portable food with the same hunger and saturation values as Cooked Mutton. Its item ID is `minecraft:cooked_salmon`. [Registration][item]

## Obtaining

Cook **one [Raw Salmon](RawSalmon.md) into one Cooked Salmon** using one of the following bundled recipes:

| Device | Recipe time | At 20 game ticks per second |
| --- | ---: | ---: |
| Furnace ([Furnace recipe][smelt]) | **200 ticks** | 10 seconds |
| Smoker ([Smoker recipe][smoke]) | **100 ticks** | 5 seconds |
| Campfire ([Campfire recipe][campfire]) | **600 ticks** | 30 seconds |

These are configured cooking times while the device is processing, not measured wall-clock guarantees. See [Smelting and cooking](../smelting/Smelting.md) for device and fuel rules.

Each recipe declares **0.35 experience**. Furnaces and smokers accumulate recipe use and release rounded experience through their result-slot collection; campfires drop the cooked item without awarding recipe experience in their cooking callback. [Furnace/smoker XP][xp] · [Result collection][xp-slot] · [Campfire completion][campfire-xp]

The checked [Salmon drops](../mobs/Salmon.md#drops) can also supply cooked meat when the animal is on fire, or its direct attacker holds a main-hand item with a smelts-loot enchantment. The bundled tag contains **Fire Aspect**. Follow that species guide for the full mob-loot conditions. [Meat loot][loot] · [Enchantment tag][tag] · [Loaded smelting lookup][loot-smelt]

## Usage

Eating one serving supplies **6 hunger points** (three hunger icons) and **9.6 saturation before caps**. The food’s 0.8 saturation modifier produces that 9.6-point contribution. Compare foods in the [Food reference](FoodReference.md); [Hunger](../mechanics/Hunger.md#food-values) explains the caps. [Food value][food] · [Saturation calculation][formula] · [Food caps][caps]

Its values match [Cooked Mutton](CookedMutton.md). [Mutton value][mutton-food]

## Behavior

The bundled food uses the default consumable with **no special consumption effect**. [Food component binding][component] · [Default consumable][default]

Hold use for **32 game ticks** (1.6 seconds at 20 ticks per second). Ordinary Survival eating starts only when hunger is below **20 points**; Creative bypasses that hunger check and does not spend the held serving. See [Hunger](../mechanics/Hunger.md) for shared food behavior. [Use duration][duration] · [Consumption gate][listener] · [Player gate][gate] · [Creative abilities][creative] · [Stack consumption][consume] · [Infinite-materials check][infinite]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the active item/food/consumable path and the acquisition routes described here. These are source-defined rules, not in-game eating, loot or cooking tests. Loaded recipes, loot and item components can change the results.

Related: [Food reference](FoodReference.md) · [Hunger](../mechanics/Hunger.md) · [Smelting and cooking](../smelting/Smelting.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1675
[food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L21
[smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/cooked_salmon.json
[smoke]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smoking/cooked_salmon_from_smoking.json
[campfire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_salmon_from_campfire_cooking.json
[xp]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L353-L387
[xp-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/FurnaceResultSlot.java#L31-L52
[campfire-xp]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L53-L87
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/salmon.json
[tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[loot-smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/SmeltItemFunction.java#L35-L47
[formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
[mutton-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L18
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L15
[duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L102
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1508-L1514
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L76
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079
[infinite]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215

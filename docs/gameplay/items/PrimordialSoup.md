# Primordial Soup

Primordial Soup (`minecraft:primordial_soup`) is an **unstackable food** from bundled content. A serving restores **6 hunger points (three icons) and 7.2 saturation points before caps**, but **does not return a Bowl** after ordinary Survival eating. [Item registration][item] · [Food and stew modifier][food] · [Saturation calculation][food-properties] · [Formula][food-formula]

## Obtaining

Use the [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative** for the checked acquisition route. Primordial Soup has an ordinary category listing. Its visibility in Survival does not make browser insertion a Survival source. [Category listing][category]

**No bundled recipe, loot reward or trade supplying Primordial Soup was found in this review.** An ordinary Survival supply is not established here; server-added data packs or changed code may alter availability. [Bundled data][data] · [Pack source][packs] · [Optional-pack discovery][bundled-packs] · [Recipe loader][recipe-loader] · [Workbench loader][workbench-recipes] · [Trade definitions][trades]

## Usage

Hold use to eat for **32 use ticks**, nominally 1.6 seconds at 20 TPS. Ordinary Survival eating requires missing hunger. The item uses the default food consumable, with **no added status effect** in its ordinary registration. Its name does not give the player a special effect. [Item registration][item] · [Default food binding][food-component] · [Default consumable][default-food] · [Effect-list default][food-defaults] · [Eating and food gate][consume] · [Player check][player-food]

See [Hunger, saturation, and healing](../mechanics/Hunger.md) for food caps and recovery, and [Food reference](FoodReference.md) for comparisons. Each serving occupies its own inventory slot.

## Behavior

Eating a Survival serving consumes it completely: there is **no configured Bowl remainder**. This differs from [Beetroot Soup](BeetrootSoup.md) and [Rabbit Stew](RabbitStew.md). Creative permits eating at full hunger and keeps the serving, with no extra Bowl. [Item components][item] · [Shared component defaults][common-components] · [Finish-use callback][finish-use] · [Remainder dispatch][remainder-dispatch] · [Serving consumption][consume-count] · [Creative abilities][creative]

**Do not spend Primordial Soup on a Relicheirus expecting a tree-cutting helper.** When the earlier interactions pass, its ordinary soup interaction consumes the serving in Survival but does not call the separate mixture method that enables tree pushing. Follow the established [Relicheirus warning](../mobs/Relicheirus.md#trees-and-primordial-soup) for that limitation. This is a source-confirmed distinction between two methods, not a successful in-game tree-work test. [Active species registration][species] · [Interaction dispatch][mob-dispatch] · [Shared dinosaur interaction][dinosaur] · [Soup interaction and separate setter][relicheirus] · [Mob item consumption][mob-consume] [Tracked consumption/activation limitation (#813)](https://github.com/HungLo2020/MattMC/issues/813); the issue is open and source-verified, without an in-game reproduction.

## Notes

The food behavior above describes MattMC's ordinary registered item, not every upstream recipe or custom-component serving. Related: [Mushroom Stew](MushroomStew.md) · [Suspicious Stew](SuspiciousStew.md) · [Items](Items.md)

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Acquisition checks covered the shipped data namespaces and optional packs, standard recipes, the separate TaCZ recipe loader, item tags, nested loot references, trades and direct item uses. They do not certify external server packs, commands or preexisting supplied items. Checked food, fullness, default effects, stack size and finish-use handling, plus the active Relicheirus interaction. No in-game acquisition, eating, feeding or inventory-insertion test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1687-L1688
[food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L57-L61
[food-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[food-formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1746-L1751
[data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data
[packs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/packs/repository/ServerPacksSource.java#L37-L54
[bundled-packs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/packs/repository/BuiltInPackSource.java#L62-L86
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[workbench-recipes]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L151
[trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java
[food-component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[default-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L14-L15
[food-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L132-L137
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L62-L102
[player-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[common-components]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[finish-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3255-L3267
[remainder-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L399-L412
[consume-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[species]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L1133-L1139
[mob-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1077
[dinosaur]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexscaves/server/entity/living/DinosaurEntity.java#L286-L330
[relicheirus]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexscaves/server/entity/living/RelicheirusEntity.java#L106-L124
[mob-consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L1113-L1121

# Raw Mutton

Raw Mutton is edible sheep meat; cook it for more hunger and saturation. Its item ID is `minecraft:mutton`. [Registration][item]

## Obtaining

With mob loot enabled, killing an adult [Sheep](../mobs/Sheep.md) supplies **1–2 Raw Mutton before Looting**. The drop is converted to [Cooked Mutton](CookedMutton.md) when the animal is on fire or its direct attacker has a qualifying main-hand **Fire Aspect** enchantment. For the full loot conditions and a supply of animals, see [Sheep](../mobs/Sheep.md#death-drops-and-experience). [Meat loot][loot] · [Mob-loot gate][loot-gate] · [Enchantment tag][tag] · [Smelting lookup][loot-smelt]

## Usage

Eating one serving supplies **2 hunger points** (one hunger icon) and **1.2 saturation before caps**. The food’s 0.3 saturation modifier produces that 1.2-point contribution. Compare foods in the [Food reference](FoodReference.md); [Hunger](../mechanics/Hunger.md#food-values) explains the caps. [Food value][food] · [Saturation calculation][formula] · [Food caps][caps]

Cooking **one Raw Mutton into one [Cooked Mutton](CookedMutton.md)** raises those values to **6 hunger points and 9.6 saturation**. Use a furnace for **200 ticks**, a smoker for **100 ticks**, or a campfire for **600 ticks**: 10, 5 or 30 seconds respectively at 20 ticks per second while processing. The cooked-item page and [cooking guide](../smelting/Smelting.md) cover the device details. [Cooked food value][cooked-food] · [Furnace recipe][smelt] · [Smoker recipe][smoke] · [Campfire recipe][campfire]

The recipes each declare **0.35 experience**. Furnace/smoker collection pays rounded accumulated recipe XP; the campfire’s cooking callback only drops the food. [Recipe XP collection][xp] · [Result slot][xp-slot] · [Campfire completion][campfire-xp]

## Behavior

The bundled food uses the default consumable with **no special consumption effect**. [Food component binding][component] · [Default consumable][default]

Hold use for **32 game ticks** (1.6 seconds at 20 ticks per second). Ordinary Survival eating starts only when hunger is below **20 points**; Creative bypasses that hunger check and does not spend the held serving. See [Hunger](../mechanics/Hunger.md) for shared food behavior. [Use duration][duration] · [Consumption gate][listener] · [Player gate][gate] · [Creative abilities][creative] · [Stack consumption][consume] · [Infinite-materials check][infinite]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the active item/food/consumable path and the acquisition routes described here. These are source-defined rules, not in-game eating, loot or cooking tests. Loaded recipes, loot and item components can change the results.

Related: [Food reference](FoodReference.md) · [Hunger](../mechanics/Hunger.md) · [Smelting and cooking](../smelting/Smelting.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2147
[food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L30
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/sheep.json
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[loot-smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/SmeltItemFunction.java#L35-L47
[formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
[cooked-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L18
[smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/cooked_mutton.json
[smoke]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smoking/cooked_mutton_from_smoking.json
[campfire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_mutton_from_campfire_cooking.json
[xp]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L353-L387
[xp-slot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/FurnaceResultSlot.java#L31-L52
[campfire-xp]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/CampfireBlockEntity.java#L53-L87
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L15
[duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L102
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1508-L1514
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L76
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079
[infinite]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215

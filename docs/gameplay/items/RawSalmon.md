# Raw Salmon

Raw Salmon can be eaten immediately or cooked for a much larger saturation contribution. Its item ID is `minecraft:salmon`. [Registration][item]

## Obtaining

With mob loot enabled, killing a [Salmon](../mobs/Salmon.md) supplies **one Raw Salmon**; its fish drop has no Looting count bonus. The drop is converted to [Cooked Salmon](CookedSalmon.md) when the animal is on fire or its direct attacker has a qualifying main-hand **Fire Aspect** enchantment. For the full loot conditions and a supply of animals, see [Salmon](../mobs/Salmon.md#drops). [Meat loot][loot] · [Mob-loot gate][loot-gate] · [Enchantment tag][tag] · [Smelting lookup][loot-smelt]

## Usage

Eating one serving supplies **2 hunger points** (one hunger icon) and **0.4 saturation before caps**. The food’s 0.1 saturation modifier produces that 0.4-point contribution. Compare foods in the [Food reference](FoodReference.md); [Hunger](../mechanics/Hunger.md#food-values) explains the caps. [Food value][food] · [Saturation calculation][formula] · [Food caps][caps]

Cooking **one Raw Salmon into one [Cooked Salmon](CookedSalmon.md)** raises those values to **6 hunger points and 9.6 saturation**. Use a furnace for **200 ticks**, a smoker for **100 ticks**, or a campfire for **600 ticks**: 10, 5 or 30 seconds respectively at 20 ticks per second while processing. The cooked-item page and [cooking guide](../smelting/Smelting.md) cover the device details. [Cooked food value][cooked-food] · [Furnace recipe][smelt] · [Smoker recipe][smoke] · [Campfire recipe][campfire]

## Animal uses

**Keep some Raw Salmon for [Cats](../mobs/Cat.md#taming-and-care) and [Ocelots](../mobs/Ocelot.md#building-trust): cooking makes it unusable for either animal.** Their bundled food tags accept only Raw Cod and Raw Salmon. Cook the rest for better player food or more healing per serving for a tame [Wolf](../mobs/Wolf.md#healing-and-food). [Dolphins](../mobs/Dolphin.md#feeding-and-finding-structures) and [Grizzly Bears](../mobs/GrizzlyBear.md#taming-and-care) still accept cooked salmon. See the shared [raw-versus-cooked animal-use comparison](RawCod.md#animal-uses-raw-or-cooked) for feeding priorities and the difference between taming, trust, breeding, and guidance. [Cat food][animal-cat-food] · [Ocelot food][animal-ocelot-food] · [Wolf healing][animal-wolf] · [Wolf food][animal-wolf-food] · [Fish tag][animal-fishes]

## Behavior

The bundled food uses the default consumable with **no special consumption effect**. [Food component binding][component] · [Default consumable][default]

Hold use for **32 game ticks** (1.6 seconds at 20 ticks per second). Ordinary Survival eating starts only when hunger is below **20 points**; Creative bypasses that hunger check and does not spend the held serving. See [Hunger](../mechanics/Hunger.md) for shared food behavior. [Use duration][duration] · [Consumption gate][listener] · [Player gate][gate] · [Creative abilities][creative] · [Stack consumption][consume] · [Infinite-materials check][infinite]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the active item/food/consumable path and the acquisition routes described here. These are source-defined rules, not in-game eating, loot or cooking tests. Loaded recipes, loot and item components can change the results.

The animal-use section was separately source-reviewed on **2026-10-10** at `f86206767dadde696adfed4e04c5ee97cd0d0885`; active food tags and interaction paths were checked. No in-game animal-feeding test was run. The linked [shared comparison](RawCod.md#animal-uses-raw-or-cooked) carries the detailed animal-use sources; the earlier acquisition, eating and cooking review remains unchanged.

Related: [Food reference](FoodReference.md) · [Hunger](../mechanics/Hunger.md) · [Smelting and cooking](../smelting/Smelting.md) · [Items](Items.md)

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1670
[food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L39
[loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/entities/salmon.json
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json
[loot-smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/functions/SmeltItemFunction.java#L35-L47
[formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
[cooked-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L21
[smelt]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smelting/cooked_salmon.json
[smoke]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/smoking/cooked_salmon_from_smoking.json
[campfire]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_salmon_from_campfire_cooking.json
[component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L15
[duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L102
[gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1508-L1514
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L76
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079
[infinite]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1212-L1215

[animal-cat-food]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/item/cat_food.json
[animal-ocelot-food]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/item/ocelot_food.json
[animal-wolf]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/java/net/minecraft/world/entity/animal/wolf/Wolf.java#L454-L512
[animal-wolf-food]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/item/wolf_food.json
[animal-fishes]: https://github.com/HungLo2020/MattMC/blob/f86206767dadde696adfed4e04c5ee97cd0d0885/src/main/resources/data/minecraft/tags/item/fishes.json

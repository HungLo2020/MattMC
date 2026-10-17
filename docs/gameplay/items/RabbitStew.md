# Rabbit Stew

Rabbit Stew (`minecraft:rabbit_stew`) is a large, **unstackable meal**: one serving restores **10 hunger points (five icons) and 12 saturation points before caps**. Each serving takes a separate inventory slot and leaves a Bowl after ordinary Survival eating. [Item registration][item] · [Food and stew modifier][food] · [Saturation calculation][food-properties] · [Formula][food-formula]

## Obtaining

Use a **Crafting Table** to combine **one Baked Potato, one Cooked Rabbit, one Bowl, one Carrot, and one mushroom**. There are two exact shapeless recipes: one takes a **Brown Mushroom**, the other a **Red Mushroom**. Either arrangement makes **one Rabbit Stew** and consumes one of each ingredient. The five ingredients need five occupied slots, so this does not fit the inventory's two-by-two grid. [Brown recipe][brown-recipe] · [Red recipe][red-recipe] · [Shapeless matching][shapeless] · [Ingredient consumption][craft-take] · [Inventory grid][inventory-grid] · [Table grid][table-grid]

The potato must already be baked and the rabbit already cooked; see [cooking](../smelting/Smelting.md) for the shared process. [Root crops](../blocks/RootCrops.md) covers growing the vegetables, and [Mushrooms](../blocks/Mushrooms.md) covers collecting and growing the mushroom ingredient.

Rabbit Stew is also listed in the [inventory item browser](../mechanics/InventoryBrowser.md). Its insertion route works in Creative; seeing the entry in Survival is not a way to obtain a serving. [Category listing][category]

## Usage

Hold use to eat for **32 use ticks**, nominally 1.6 seconds at 20 TPS. Ordinary Survival eating requires missing hunger; Rabbit Stew does not have the always-eat flag. Its ordinary food components add no status effect. [Food definition][food] · [Default food binding][food-component] · [Default consumable][default-food] · [Effect-list default][food-defaults] · [Eating and food gate][consume] · [Player check][player-food]

The whole serving is spent even when some of its food value exceeds your remaining capacity. [Hunger, saturation, and healing](../mechanics/Hunger.md) explains the caps; [Food reference](FoodReference.md) compares smaller meals and stackable travel food.

## Behavior

After a completed Survival eating action, **one Bowl replaces the consumed serving**. Reuse it for another meal. Creative permits eating at full hunger, preserves the stew, and does not create an extra Bowl. [Finish-use callback][finish-use] · [Remainder dispatch][remainder-dispatch] · [Bowl conversion][remainder] · [Serving consumption][consume-count] · [Creative abilities][creative]

## Notes

This page gives the two crafting recipes, not an exhaustive list of possible loot or trade sources. Related: [Bowl](Bowl.md) · [Mushroom Stew](MushroomStew.md) · [Items](Items.md)

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Checked exact recipes, occupied-slot requirements, item/food components, the active eating and remainder paths, and the category listing. No in-game crafting, eating or inventory-insertion test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2124
[food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L37-L61
[food-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[food-formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[brown-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_brown_mushroom.json
[red-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_red_mushroom.json
[shapeless]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/ShapelessRecipe.java#L58-L69
[craft-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L81-L98
[inventory-grid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L50
[table-grid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L38-L40
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1746-L1751
[food-component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[default-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L14-L15
[food-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L132-L137
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L62-L102
[player-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[finish-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3255-L3267
[remainder-dispatch]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L399-L412
[remainder]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/UseRemainder.java#L14-L27
[consume-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1079
[creative]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79

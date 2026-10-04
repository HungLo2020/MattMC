# Beetroot Soup

Beetroot Soup (`minecraft:beetroot_soup`) turns a crop harvest into an **unstackable meal**. One serving restores **6 hunger points (three icons) and 7.2 saturation points before caps**, then leaves a Bowl after ordinary Survival eating. [Item registration][item] · [Food value][food] · [Stew modifier][stew] · [Saturation calculation][food-properties] · [Formula][food-formula]

## Obtaining

Combine **six Beetroots and one Bowl** in any arrangement in a **Crafting Table** to make **one Beetroot Soup**. Spread the six Beetroots across six ingredient slots and put the Bowl in a seventh; a single stack of six Beetroots does not fill six slots. The inventory's two-by-two grid is too small. Each craft consumes all seven ingredients. [Exact recipe][recipe] · [Shapeless matching][shapeless] · [Ingredient consumption][craft-take] · [Inventory grid][inventory-grid] · [Table grid][table-grid]

Use harvested Beetroots, not Beetroot Seeds. The [Root crops guide](../blocks/RootCrops.md) owns planting, growth and harvesting.

Beetroot Soup is also listed in the [inventory item browser](../mechanics/InventoryBrowser.md). Creative insertion can supply a serving; the visible Survival catalog does not provide the same acquisition route. [Category listing][category]

## Usage

Hold use to eat for **32 use ticks**, nominally 1.6 seconds at 20 TPS. Ordinary Survival eating requires missing hunger, and the ordinary serving adds no status effect. [Food value][food] · [Default food binding][food-component] · [Default consumable][default-food] · [Effect-list default][food-defaults] · [Eating and food gate][consume] · [Player check][player-food]

One soup occupies an entire inventory slot, so prepare servings with your available carrying space in mind. The values above are contributions before the shared caps, not a guaranteed amount of healing; see [Hunger, saturation, and healing](../mechanics/Hunger.md) and the [Food reference](FoodReference.md).

## Behavior

A completed Survival eating action replaces the serving with **one Bowl** for reuse. Creative allows eating at full hunger and keeps the soup without producing an extra Bowl. [Finish-use callback][finish-use] · [Remainder dispatch][remainder-dispatch] · [Bowl conversion][remainder] · [Serving consumption][consume-count] · [Creative abilities][creative]

## Notes

This page establishes the crop-to-soup crafting route; it does not enumerate every structure-loot source. Related: [Beetroot](Beetroot.md) · [Bowl](Bowl.md) · [Mushroom Stew](MushroomStew.md) · [Items](Items.md)

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Checked the seven-ingredient recipe, grid sizes, food values, active eating and Bowl-return handling, and category listing. No in-game farming, crafting, eating or inventory-insertion test was run.

[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2240
[food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L7-L8
[stew]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L59-L61
[food-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[food-formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/beetroot_soup.json
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

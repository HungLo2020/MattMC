# Cookie

A Cookie (`minecraft:cookie`) is a small food made in batches of eight. **Do not feed Cookies to Parrots:** the animal interaction poisons them and normally kills them. [Food registration][cookie-item] · [Poisonous-food tag][parrot-poison-tag] · [Parrot interaction][parrot-poison]

## Obtaining

At a **Crafting Table**, arrange **Wheat–Cocoa Beans–Wheat across one row** to make **8 Cookies**. Use one item in each of the three slots; the inventory's two-by-two grid cannot fit this three-wide recipe. See [Cocoa](../blocks/Cocoa.md) and [Wheat](../blocks/Wheat.md) for growing the ingredients. [Exact recipe][cookie-recipe] · [Inventory grid][inventory-grid] · [Table grid][table-grid] · [Ingredient consumption][craft-take]

A **journeyman Farmer** has a Cookie offer with a base exchange of **3 Emeralds for 18 Cookies**. Check the actual [trade](../trading/Trading.md) for its current price and stock. This is a verified alternative to growing Cocoa. [Farmer offers][farmer-trades] · [Output/count construction][trade-output] · [Active offer selection][trade-selection]

Cookies are listed in **Food and Drinks** for the [inventory item browser](../mechanics/InventoryBrowser.md). Creative insertion supplies them independently; the visible Survival catalog does not replace crafting or trading. [Category entry][cookie-category]

## Usage

Eat one Cookie for **2 hunger points (one icon)** and **0.4 saturation points before caps**. Hold use for **32 ticks**, nominally **1.6 seconds at 20 TPS**. Ordinary Survival eating requires missing hunger, and the default Cookie adds no status effect to its eater. [Food values][food-values] · [Saturation calculation][food-calculation] · [Saturation formula][food-formula] · [Caps][food-caps] · [Default food binding][food-component] · [Duration][default-food] · [Consumption][consume] · [Food listener][food-listener] · [Empty effect default][effect-default] · [Hunger gate][player-food]

The small saturation contribution makes Cookies a light snack. Use [Food reference](FoodReference.md) to compare meals, and [Hunger](../mechanics/Hunger.md) for food's relationship to natural healing.

## Behavior

Using a Cookie on a [Parrot](../mobs/Parrot.md) consumes one in ordinary Survival, applies **Poison for 900 ticks** (45 seconds at 20 TPS), and attempts lethal damage unless the Parrot is invulnerable. Creative feeding attempts that damage even against an invulnerable Parrot. This is separate from player eating; a Cookie is not a safe taming treat. [Poisonous tag][parrot-poison-tag] · [Exact interaction][parrot-poison]

For Parrot taming, use the accepted seeds, such as [Beetroot Seeds](BeetrootSeeds.md), and follow the mob guide. [Seed tag][parrot-food-tag] · [Taming interaction][parrot-tame]

## Notes

Cookies can be composted with an **85% chance to add one level at levels 1–6**. The first accepted item in an empty [Composter](../blocks/Composter.md#from-ingredients-to-bone-meal) always succeeds. Survival insertion consumes the Cookie even when the subsequent chance roll fails. [Cookie chance][compost-cookie] · [Player insertion][compost-use] · [Roll][compost-roll]

Related: [Wheat](Wheat.md) · [Cocoa Beans](CocoaBeans.md) · [Items](Items.md)

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Checked the exact recipe, active Farmer trade selection, food values/consumption, the Parrot's poisonous-food interaction and composting. No in-game crafting, trading, eating, animal interaction or insertion test was run. Data packs can change recipes, tags and loot. [Active recipe loading][recipe-loader]

[cookie-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1733
[parrot-poison-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/parrot_poisonous_food.json
[parrot-poison]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L276-L295
[cookie-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/cookie.json
[inventory-grid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/InventoryMenu.java#L48-L50
[table-grid]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/CraftingMenu.java#L38-L40
[craft-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L108
[farmer-trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L81-L102
[trade-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1434-L1478
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L836
[cookie-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1735-L1739
[food-values]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L4-L24
[food-calculation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[food-caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
[food-component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[default-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L68
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L62-L102
[food-listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L52-L56
[effect-default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L132-L137
[player-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[parrot-food-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/parrot_food.json
[parrot-tame]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/Parrot.java#L248-L275
[compost-cookie]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L178-L181
[compost-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L244-L259
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L318
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[food-formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32

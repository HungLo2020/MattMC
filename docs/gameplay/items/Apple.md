# Apple

An Apple (`minecraft:apple`) is food and the main ingredient for an ordinary [Golden Apple](GoldenApple.md). Keep some for animal care or crafting before eating the harvest. [Registration][apple-item]

## Obtaining

**Oak and Dark Oak Leaves can drop one Apple** when broken without Shears or Silk Touch, or when they decay. The ordinary chance per eligible leaf is **0.5%**; Fortune I, II and III raise it to about **0.556%, 0.625% and 0.833%**. These are individual drop rolls, not a promised yield from one tree. Explosion survival is checked separately. [Oak loot][oak-loot] · [Dark Oak loot][dark-oak-loot] · [Decay drops][leaf-decay] · [Fortune lookup][fortune-roll]

An **apprentice Farmer** can offer **4 Apples for a base price of 1 Emerald**. Its level selects offers from a pool, so inspect the actual trades; see [Trading](../trading/Trading.md) for price changes and restocking. Apples also appear in the checked **Igloo basement chest** table. Only Igloos selected to have a basement provide that route, and an Apple roll is not guaranteed. These are selected Survival sources. [Farmer offers][farmer-trades] · [Offer construction][trade-output] · [Selection][trade-selection] · [Igloo loot][igloo-loot] · [Basement selection][igloo] · [Chest assignment][igloo-assignment]

Apples appear in the **Food and Drinks** category feeding the [inventory item browser](../mechanics/InventoryBrowser.md). Creative insertion is a separate acquisition route; a visible Survival catalog entry does not supply items. [Category entry][apple-category]

## Usage

At a Crafting Table, surround **one Apple with eight Gold Ingots** to make **one Golden Apple**. This consumes the Apple and produces the ordinary item, not an Enchanted Golden Apple. [Recipe][golden-recipe] · [Ingredient consumption][craft-take]

Apples attract and feed [Sugar Gliders](../mobs/SugarGlider.md#apples-taming-and-healing); use that guide for taming, healing, breeding, and feeding-order limits. [Apple interaction][glider]

Other useful animal interactions have different conditions:

- [Horses, Donkeys and Mules](../mobs/Horse.md#taming-and-feeding) can gain **3 health points**, baby growth and temper from an accepted Apple feeding. An ordinary Apple neither lures this family nor starts its breeding love mode. [Feeding][horse-feed] · [Temptation tag][horse-lure]
- [Toucans](../mobs/Toucan.md#giving-fruit-for-saplings) use Apples for breeding/growth and as Oak Sapling planting gifts. One interaction with a stack can spend fruit on both paths; follow the owner guide before handing over a stack. [Food tag][toucan-food-tag] · [Active food check][toucan-food-check] · [Interaction order][toucan-use] · [Default planting map][toucan-map]
- [Rhinoceroses](../mobs/Rhinoceros.md#trust-and-feeding) accept Apples for trust and breeding. Those interactions can overlap; the mob guide explains feeding order and what trust allows. [Trust-food tag][rhino-food] · [Breeding-food tag][rhino-breed] · [Breeding-food check][rhino-food-check] · [Trust interaction][rhino]

## Behavior

Eating one Apple restores **4 hunger points (two icons)** and contributes **2.4 saturation points before caps**. Hold use for **32 ticks**, nominally **1.6 seconds at 20 TPS**. Ordinary Survival eating requires missing hunger; the default Apple has no consumption status effect. [Food values][food-values] · [Saturation calculation][food-calculation] · [Saturation formula][food-formula] · [Caps][food-caps] · [Food binding][food-component] · [Duration][default-food] · [Consumption][consume] · [Food listener][food-listener] · [Effect default][effect-default] · [Hunger gate][player-food]

See [Hunger, saturation, and healing](../mechanics/Hunger.md) for how those food values affect recovery; eating is not an immediate fixed-health heal.

## Notes

An Apple has a **65% chance to add one compost level** at levels 1–6. The first accepted item in an empty [Composter](../blocks/Composter.md#from-ingredients-to-bone-meal) always succeeds, and an accepted Survival insertion spends the Apple even if the later roll fails. [Apple chance][compost-apple] · [Player insertion][compost-use] · [Roll and empty-composter exception][compost-roll]

Related: [Food reference](FoodReference.md) · [Items](Items.md)

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Checked leaf loot and decay, selected trade/chest routes, crafting, food consumption, animal interactions and composting. No in-game harvesting, crafting, feeding, eating or insertion test was run. Data packs can change recipes, tags and loot. [Active recipe loading][recipe-loader]

[apple-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1287-L1288
[oak-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/oak_leaves.json#L134-L189
[dark-oak-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/blocks/dark_oak_leaves.json#L134-L189
[leaf-decay]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/LeavesBlock.java#L53-L66
[fortune-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/predicates/BonusLevelTableCondition.java#L37-L41
[farmer-trades]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L81-L102
[trade-output]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/VillagerTrades.java#L1434-L1478
[trade-selection]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/npc/Villager.java#L819-L836
[igloo-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/igloo_chest.json#L7-L22
[igloo]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/IglooPieces.java#L48-L62
[igloo-assignment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/IglooPieces.java#L103-L114
[apple-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1680-L1694
[golden-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/golden_apple.json
[craft-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L108
[glider]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntitySugarGlider.java#L478-L503
[horse-feed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L410-L484
[horse-lure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/horse_tempt_items.json
[toucan-food-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/toucan_breedables.json
[toucan-food-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityToucan.java#L194-L196
[toucan-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityToucan.java#L141-L166
[toucan-map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/config/AMConfig.java#L41-L48
[rhino-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/rhinoceros_foodstuffs.json
[rhino-food-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityRhinoceros.java#L215-L217
[rhino]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityRhinoceros.java#L420-L442
[food-values]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L4-L24
[food-calculation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[food-caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
[food-component]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[default-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L68
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L62-L102
[food-listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L52-L56
[effect-default]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L132-L137
[player-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[compost-apple]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L129-L132
[compost-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L244-L259
[compost-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/ComposterBlock.java#L304-L318
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L88
[food-formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[rhino-breed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/rhinoceros_breedables.json

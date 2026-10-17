# Enchanted Golden Apple

An Enchanted Golden Apple (`minecraft:enchanted_golden_apple`) is an always-edible food with four beneficial effects. It is a separate item from the craftable [Golden Apple](GoldenApple.md), with its own consumption effects and uses. [Registration][enchanted-item]

## Obtaining

Two checked Survival sources are **monster-room chests** and **abandoned-mineshaft chest minecarts**. Their ordinary bundled loot tables include Enchanted Golden Apples as random entries, so finding either container does not guarantee one. These examples are not a complete loot catalog. [Monster-room entry][dungeon-apple] · [Chest assignment][dungeon-assignment] · [Loot key][dungeon-key] · [Mineshaft entry][mineshaft-apple] · [Minecart creation][mineshaft-chest] · [Mineshaft placement][mineshaft-assignment]

**No producing recipe was found in the checked built-in recipe bundle.** Surrounding an Apple with eight Gold Ingots produces an ordinary Golden Apple; it does not upgrade into this item. The recipe that names an Enchanted Golden Apple uses it as an ingredient for a banner pattern. [Golden Apple recipe][golden-recipe] · [Pattern recipe][mojang-recipe]

It is listed in **Food and Drinks** for the [inventory item browser](../mechanics/InventoryBrowser.md). Creative insertion can supply one independently of loot; the catalog's visibility in Survival is not an acquisition route. [Category entry][apple-category]

## Usage

Hold use to eat for **32 ticks**, nominally **1.6 seconds at 20 TPS**. It can be eaten with a full hunger bar. Completing consumption contributes **4 hunger points (two icons)** and **9.6 saturation points before caps**, then applies:

| Effect | Level | Duration at 20 TPS |
| --- | --- | --- |
| Regeneration | II | 20 seconds (400 ticks) |
| Resistance | I | 5 minutes (6,000 ticks) |
| Fire Resistance | I | 5 minutes (6,000 ticks) |
| Absorption | IV | 2 minutes (2,400 ticks) |

[Food values][food-values] · [Saturation calculation][food-calculation] · [Saturation formula][food-formula] · [Caps][food-caps] · [Duration][default-food] · [Active eating/effect dispatch][consume] · [Food listener][food-listener] · [Exact effect list][enchanted-effects] · [Effect application][effect-apply]

Carry it where you can reach it before a dangerous encounter: holding it does not activate its benefits. See [Hunger](../mechanics/Hunger.md) and [Effects](../effects/Effects.md) for the shared systems.

## Behavior

**It does not cure a weakened Zombie Villager.** That interaction checks for the ordinary Golden Apple specifically; follow [Zombie Villager curing](../mobs/ZombieVillager.md#curing-step-by-step). [Exact cure check][zombie-cure]

Decide before spending one on these other uses:

- **One Paper + one Enchanted Golden Apple → one [Mojang Banner Pattern](MojangBannerPattern.md)**, in either crafting grid. Taking the result consumes the Apple. [Pattern recipe][mojang-recipe] · [Ingredient consumption][craft-take]
- The [Horse family's feeding and breeding](../mobs/Horse.md#taming-and-feeding) accepts it for the same feeding benefits as an ordinary Golden Apple: up to **10 health points**, baby growth, temper and eligible love mode. It is also a lure food. [Feeding][horse-feed] · [Lure tag][horse-lure]
- A [Toucan's planting-gift interaction](../mobs/Toucan.md#giving-fruit-for-saplings) consumes it to remember Oak Sapling and become enchanted, allowing repeated successful planting without clearing that memory. Giving this gift is separate from player eating. [Default map][toucan-map] · [Enchanted-food tag][toucan-golden-tag] · [Eating the gift][toucan-eat] · [Planting memory][toucan-plant]

## Notes

Loot entries and effect durations above describe the reviewed bundle; data packs and later builds can change them. Related: [Apple](Apple.md) · [Golden Apple](GoldenApple.md) · [Items](Items.md)

## Sources and verification

Source-reviewed at `78e8e0423084f010bb47e36132550619b37644c2` on 2026-10-04. Checked the item/effect components, active consumption, selected loot assignments, built-in recipe search, cure restriction and animal uses. No in-game looting, eating, crafting, curing, feeding or insertion test was run.

[enchanted-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1411-L1418
[dungeon-apple]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/simple_dungeon.json#L23-L32
[dungeon-assignment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java#L91-L111
[dungeon-key]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/loot/BuiltInLootTables.java#L18
[mineshaft-apple]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table/chests/abandoned_mineshaft.json#L7-L16
[mineshaft-chest]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L340-L356
[mineshaft-assignment]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java#L394-L400
[golden-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/golden_apple.json
[mojang-recipe]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe/crafting/mojang_banner_pattern.json
[apple-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1680-L1694
[food-values]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L4-L24
[food-calculation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L60-L83
[food-caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30
[default-food]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L68
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L62-L102
[food-listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L52-L56
[enchanted-effects]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L29-L40
[effect-apply]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/consume_effects/ApplyStatusEffectsConsumeEffect.java#L35-L62
[zombie-cure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/monster/ZombieVillager.java#L136-L156
[craft-take]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/inventory/ResultSlot.java#L72-L108
[horse-feed]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/animal/horse/AbstractHorse.java#L410-L484
[horse-lure]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/horse_tempt_items.json
[toucan-map]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/config/AMConfig.java#L41-L48
[toucan-golden-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/item/toucan_enchanted_golden_foods.json
[toucan-eat]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityToucan.java#L266-L282
[toucan-plant]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityToucan.java#L716-L734
[food-formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32

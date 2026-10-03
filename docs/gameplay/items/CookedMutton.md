# Cooked Mutton

Cooked Mutton (`minecraft:cooked_mutton`) is the cooked food from [Sheep](../mobs/Sheep.md). A serving supplies **6 hunger points and 9.6 saturation before caps**, compared with **2 and 1.2** for Raw Mutton. [Registration][item] · [Cooked value][food] · [Raw value][raw-food] · [Calculation][formula]

## Obtaining

Cook **one [Raw Mutton](RawMutton.md) into one Cooked Mutton** with one of these bundled recipes. Their durations are configured game ticks, not measured elapsed times. See [Smelting and cooking](../smelting/Smelting.md) for the devices and fuel rules.

| Device | Recipe duration | Exact recipe |
| --- | ---: | --- |
| Furnace | **200 ticks** | [Smelting][smelt] |
| Smoker | **100 ticks** | [Smoking][smoke] |
| Campfire | **600 ticks** | [Campfire cooking][campfire] |

The checked Sheep meat loot starts with **1–2 Raw Mutton before Looting** and requests smelting when the sheep is burning or the direct attacker's main-hand item has an enchantment in the bundled smelts-loot tag. That tag contains **Fire Aspect**. The loot function looks up the loaded smelting recipe, which supplies Cooked Mutton here. See [Sheep death drops](../mobs/Sheep.md#death-drops-and-experience) for mob-loot and adult-animal conditions. [Sheep meat loot][loot] · [Smelts-loot tag][fire-aspect] · [Recipe lookup][loot-smelt] · [Mutton recipe][smelt]

The ordinary listed item is also available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**, separately from the cooking and mob-loot routes. [Food listing][browser-list] · [Client request][browser-client] · [Server check][browser-server]

## Usage

Eat Cooked Mutton as a portable food supply. Its **6 hunger and 9.6 saturation** equal the registered values for [Cooked Salmon](CookedSalmon.md); [Cooked Chicken](CookedChicken.md) supplies the same hunger with **7.2 saturation**. The [Food reference](FoodReference.md) compares the full registered selection. [Cooked food values][food-comparison] · [Calculation][formula]

## Behavior

Cooked Mutton uses **32 use ticks**, from the configured duration value of 1.6. It does not set the always-eat flag, add a special consumption effect, or return a container. [Hunger](../mechanics/Hunger.md) explains the shared hunger gate and caps. [Registration][item] · [Food value][food] · [Default component][component] · [Duration][duration] · [Use ticks and hunger check][ticks]

## Notes

Source-reviewed on **2026-10-02** at `ac333e7655e092e93f2423a5ab47b50b2b2a9d9a`. Checked the item/food, all three cooking recipes, the Sheep meat loot condition, its smelting function and the browser route. No animal spawning, combat, loot, cooking or eating test was run. Loaded recipes and loot can change the checked data-driven routes.

Related: [Raw Mutton](RawMutton.md) · [Sheep](../mobs/Sheep.md) · [Cooking](../smelting/Smelting.md) · [Items](Items.md)

[component]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[duration]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[ticks]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumable.java#L95-L102
[formula]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[browser-list]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1691-L1754
[browser-client]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[item]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2147-L2148
[food]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L18
[raw-food]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L30
[food-comparison]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L16-L21
[smelt]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/recipe/smelting/cooked_mutton.json#L1-L10
[smoke]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/recipe/smoking/cooked_mutton_from_smoking.json#L1-L10
[campfire]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/recipe/campfire_cooking/cooked_mutton_from_campfire_cooking.json#L1-L10
[loot]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/loot_table/entities/sheep.json#L8-L68
[fire-aspect]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/tags/enchantment/smelts_loot.json#L1-L5
[loot-smelt]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/level/storage/loot/functions/SmeltItemFunction.java#L35-L47

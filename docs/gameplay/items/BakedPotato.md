# Baked Potato

Baked Potato (`minecraft:baked_potato`) is cooked [Potato](Potato.md), supplying **5 hunger points and 6 saturation before caps**. A raw Potato supplies only **1 hunger and 0.6 saturation**. [Registration][item] · [Baked value][food] · [Raw value][raw-food] · [Calculation][formula]

## Obtaining

Cook **one ordinary Potato into one Baked Potato** using one of these bundled recipes. These are configured recipe durations in game ticks, not measured wall-clock times. [Cooking devices](../smelting/Smelting.md) explains the device and fuel differences.

| Device | Recipe duration | Exact recipe |
| --- | ---: | --- |
| Furnace | **200 ticks** | [Smelting][smelt] |
| Smoker | **100 ticks** | [Smoking][smoke] |
| Campfire | **600 ticks** | [Campfire cooking][campfire] |

[Potato crops](../blocks/RootCrops.md) provide the raw ingredient; the crop loot names ordinary Potatoes separately from the possible Poisonous Potato drop. The cooking recipes above specifically accept `minecraft:potato`. [Crop loot][crop]

For a checked ready-cooked loot option, the **Ancient City ice-box chest** table includes an entry supplying **1–10 Baked Potatoes when selected**. This does not guarantee that a chest contains them. [Ice-box entry][loot]

The ordinary listed item is also available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**, separately from recipes, crops and chest loot. [Food listing][browser-list] · [Client request][browser-client] · [Server check][browser-server]

## Usage

Eat it directly, or combine **one Baked Potato, one Cooked Rabbit, one Carrot, one Bowl, and either one Brown Mushroom or one Red Mushroom** in any arrangement to make **one [Rabbit Stew](RabbitStew.md)**. Both checked recipes have five ingredients, so use a crafting table. [Brown-mushroom recipe][stew-brown] · [Red-mushroom recipe][stew-red]

Keep ordinary Potatoes for planting: the Baked Potato registration is food, while the raw Potato is the item linked to the potato crop block. [Item registrations][item]

## Behavior

Baked Potato uses **32 use ticks**, from the configured duration value of 1.6. It has no always-eat flag, special consumption effect or returned container in its registration. Use [Food reference](FoodReference.md) to compare meals and [Hunger](../mechanics/Hunger.md) for the shared caps and hunger rules. [Item][item] · [Food][food] · [Default component][component] · [Duration][duration] · [Use ticks and hunger check][ticks]

## Notes

Source-reviewed on **2026-10-02** at `ac333e7655e092e93f2423a5ab47b50b2b2a9d9a`. Checked three cooking recipes, both Rabbit Stew recipes, crop/chest loot and the browser path. No in-game growing, cooking, crafting, chest generation or eating test was run.

Related: [Potato](Potato.md) · [Root crops](../blocks/RootCrops.md) · [Cooking](../smelting/Smelting.md) · [Items](Items.md)

[component]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[duration]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[ticks]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumable.java#L95-L102
[formula]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[browser-list]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1691-L1754
[browser-client]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[item]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L2056-L2059
[food]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L5
[raw-food]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L33
[smelt]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/recipe/smelting/baked_potato.json#L1-L10
[smoke]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/recipe/smoking/baked_potato_from_smoking.json#L1-L10
[campfire]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/recipe/campfire_cooking/baked_potato_from_campfire_cooking.json#L1-L10
[crop]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/loot_table/blocks/potatoes.json#L8-L76
[loot]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/loot_table/chests/ancient_city_ice_box.json#L58-L72
[stew-brown]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_brown_mushroom.json#L1-L16
[stew-red]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/recipe/crafting/rabbit_stew_from_red_mushroom.json#L1-L16

# Bread

Bread (`minecraft:bread`) turns harvested [Wheat](Wheat.md) into food without a cooking step. One loaf supplies **5 hunger points and 6 saturation before caps**. [Recipe][recipe] · [Registration][item] · [Food value][food] · [Calculation][formula]

## Obtaining

Arrange **three Wheat in one horizontal row** at a [Crafting Table](../blocks/CraftingTable.md) to make **one Bread**. The three-wide recipe does not fit the inventory's two-by-two crafting grid. Follow [Wheat farming](../blocks/Wheat.md) for growing and harvesting the ingredient. [Bread recipe][recipe]

Bread also appears in the checked **Plains Village house chest** loot table: its entry supplies **1–4 Bread when selected**. That is one verified loot option, not a guaranteed chest reward or a complete list of Bread sources. [Chest entry][loot]

The ordinary listed item is also available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**, separately from crafting and loot. [Food listing][browser-list] · [Client request][browser-client] · [Server check][browser-server]

## Usage

Hold Bread and use it to eat. Its food values match [Baked Potato](BakedPotato.md): **5 hunger and 6 saturation before caps**. Bread is a direct use for a Wheat harvest when you want food without processing fuel or a cooking device. Compare other supplies in the [Food reference](FoodReference.md). [Bread and Potato values][food] · [Recipe][recipe]

## Behavior

Bread uses the default eating action: **32 use ticks**, from the configured duration value of 1.6. It does not set the always-eat flag, attach a special consumption effect, or register a returned container. The shared hunger check and caps are explained in [Hunger, saturation, and healing](../mechanics/Hunger.md). [Item][item] · [Food][food] · [Default component][component] · [Duration][duration] · [Use ticks and hunger check][ticks]

## Notes

Source-reviewed on **2026-10-02** at `ac333e7655e092e93f2423a5ab47b50b2b2a9d9a`. Checked the registered item/food, one exact crafting recipe, the named chest entry and the browser path. No in-game crafting, loot, inventory or eating test was run. The chest example does not establish the chance of finding a village or a particular chest.

Related: [Wheat ingredient](Wheat.md) · [Wheat crop](../blocks/Wheat.md) · [Food reference](FoodReference.md) · [Items](Items.md)

[component]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[duration]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[ticks]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/component/Consumable.java#L95-L102
[formula]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[browser-list]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1691-L1754
[browser-client]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[recipe]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/recipe/crafting/bread.json#L1-L14
[item]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/item/Items.java#L1355-L1356
[food]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/java/net/minecraft/world/food/Foods.java#L4-L10
[loot]: https://github.com/HungLo2020/MattMC/blob/ac333e7655e092e93f2423a5ab47b50b2b2a9d9a/src/main/resources/data/minecraft/loot_table/chests/village/village_plains_house.json#L47-L62

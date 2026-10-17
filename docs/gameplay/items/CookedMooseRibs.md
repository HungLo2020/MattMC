# Cooked Moose Ribs

**Cooked Moose Ribs** (`minecraft:cooked_moose_ribs`) supplies **8 hunger points and 12.8 saturation before caps**. Its registration uses the Cooked Porkchop food definition; the current [Raw Moose Ribs](RawMooseRibs.md) item supplies **3 and 1.8**. [Cooked registration][item] · [Both food registrations][items] · [Cooked values][food] · [Raw values][raw-food] · [Saturation calculation][food-formula]

## Obtaining

The ordinary Food & Drinks listing is a verified supply through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. [Category entry][tab] · [Browser list][browser-list] · [Client request][browser-client] · [Server check][browser-server]

**No bundled cooking recipe for Moose Ribs was found** in the active furnace, smoker, campfire or other recipe files. A “cooked” item registration does not make a raw counterpart cookable. The active loot set also provides no default Moose entity table establishing cooked ribs from a burning animal. Use the [Moose guide](../mobs/Moose.md) for its actual resources and current supply limits. [Registered items][items] · [Bundled recipes][recipes] · [Default loot key][loot-key] · [Missing-table fallback][loot-missing] · [Bundled loot][loot-tables]

## Usage

Eat the ribs as a portable food supply. Their **8 hunger and 12.8 saturation** match [Cooked Porkchop](CookedPorkchop.md) and [Steak](Steak.md) in the checked food definitions. The [Food reference](FoodReference.md#mattmc-additions) compares all registered MattMC foods without turning this numerical comparison into a recipe or acquisition claim. [Food definitions][comparison] · [Item mapping][item] · [Calculation][food-formula]

## Behavior

The item uses the default **32 use ticks**, from the configured 1.6-duration food action, and the default **64-item stack limit**. It adds no special consumption effect, always-eat flag or returned container. These are registered use values, not an eating-speed benchmark. [Registration][item] · [Food definition][food] · [Food component][food-default] · [Default action][food-use] · [Use ticks][food-ticks] · [Stack default][default-stack]

[Hunger](../mechanics/Hunger.md) explains the shared food caps, exhaustion and recovery rules.

## Notes

Source-reviewed on **2026-10-02** at `25319cecd6bee492767c5b15ee53b9213d1ec1ee`. Checked registration/category access, exact food definition and default consumption, and bounded active recipe/loot absence. No cooking, animal-drop, food-use or inventory test was run. This page does not import the cooking recipes present in reference mod sources outside active `src/main`.

Related: [Raw Moose Ribs](RawMooseRibs.md) · [Moose](../mobs/Moose.md) · [Food reference](FoodReference.md) · [Items](Items.md)

[browser-client]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-list]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[comparison]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/food/Foods.java#L15-L19
[default-stack]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[food]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/food/Foods.java#L19
[food-default]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[food-formula]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[food-ticks]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/component/Consumable.java#L95-L102
[food-use]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[item]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1784
[items]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1783-L1784
[loot-key]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[loot-tables]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/loot_table
[raw-food]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/food/Foods.java#L32
[recipes]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/recipe
[tab]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1717

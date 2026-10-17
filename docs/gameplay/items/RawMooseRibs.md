# Raw Moose Ribs

**Raw Moose Ribs** (`minecraft:moose_ribs`) is a handheld food with **3 hunger points and 1.8 saturation before caps**. It uses the registered Raw Porkchop food definition. Its name does not establish a Moose drop or a working cooking conversion. [Item registration][item] · [Food values][food] · [Saturation calculation][food-formula]

## Obtaining

Raw Moose Ribs is listed in the ordinary Food & Drinks category. Request it through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. [Category entry][tab] · [Browser construction][browser-list] · [Client request][browser-client] · [Server check][browser-server]

**A bundled Moose death-drop source is not established.** The entity's default loot key would use `entities/moose`, but the checked active loot data contains no table at that location. Missing tables resolve to the empty table. This is separate from Antlers, which a [living Moose sheds](../mobs/Moose.md#antler-shedding) directly. [Default loot key][loot-key] · [Loot lookup][loot-read] · [Missing-table fallback][loot-missing] · [Bundled loot][loot-tables]

## Usage

Eat the ribs directly, or compare them with other supplies in the [Food reference](FoodReference.md#mattmc-additions). **No bundled recipe converts these raw ribs into [Cooked Moose Ribs](CookedMooseRibs.md)** in the checked recipe set, including furnace, smoker and campfire recipes. The separately registered cooked item is therefore not proof that those devices accept the raw item. [Both registrations][items] · [Bundled recipes][recipes]

## Behavior

Raw Moose Ribs uses the default **32 use ticks** from the configured 1.6-duration food action, and inherits a **64-item stack limit**. It does not register the always-eat flag, an added consumption effect, or a returned container. Raw food is not automatically harmful: this registration has no Raw Chicken-style Hunger effect. [Food definition][food] · [Registration][item] · [Default food component][food-default] · [Use action][food-use] · [Tick conversion][food-ticks] · [Default stack][default-stack]

The values above are before shared caps. [Hunger](../mechanics/Hunger.md) owns those limits and recovery mechanics; the [Food reference](FoodReference.md) owns the complete item comparison.

## Notes

Source-reviewed on **2026-10-02** at `25319cecd6bee492767c5b15ee53b9213d1ec1ee`. Checked the actual food registration, category/browser route, default consumption behavior, death-loot resolution and all active recipe/loot files for the target item. No natural drop, cooking, eating or inventory test was run. A data pack or later build can add a recipe or loot route; none is claimed here.

Related: [Moose](../mobs/Moose.md) · [Cooked Moose Ribs](CookedMooseRibs.md) · [Food reference](FoodReference.md) · [Items](Items.md)

[browser-client]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-list]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1906
[default-stack]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L390
[food]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/food/Foods.java#L32
[food-default]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Item.java#L366-L375
[food-formula]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[food-ticks]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/component/Consumable.java#L95-L102
[food-use]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[item]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1783
[items]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/Items.java#L1783-L1784
[loot-key]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[loot-read]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[loot-tables]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/loot_table
[recipes]: https://github.com/HungLo2020/MattMC/tree/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/resources/data/minecraft/recipe
[tab]: https://github.com/HungLo2020/MattMC/blob/25319cecd6bee492767c5b15ee53b9213d1ec1ee/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1716

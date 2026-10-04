# Kangaroo Burger

**Kangaroo Burger** (`minecraft:kangaroo_burger`) restores **8 hunger points (four drumsticks)** and **12.8 saturation before caps**, matching Steak's food values. Its default consumption has **no added status effect**. A burger is registered and edible, but no built-in burger recipe was found in the reviewed MattMC bundle. [Registration][items] · [Nutrition][foods] · [Saturation calculation][formula] · [Default food component][default-properties] · [Default consumable][default-consumable] · [Bundled recipes][recipes]

## Obtaining

The burger is listed in the ordinary **Food & Drinks** category, which feeds the combined [inventory item browser](../mechanics/InventoryBrowser.md). Search its displayed name there. You can view the catalog in Survival; accepted insertion requires the server player's infinite-materials ability, normally supplied by **Creative**. A visible burger does not establish an ordinary Survival supply. [Category entry][food-category] · [Catalog assembly][browser-list] · [Packet gate][browser-gate] · [Server ability check][browser-context] · [Mode abilities][mode-abilities]

The checked built-in data and active Java references contain **no crafting, cooking, loot or direct production route** for the burger. In particular, neither [Raw Kangaroo Meat](RawKangarooMeat.md) nor [Cooked Kangaroo Meat](CookedKangarooMeat.md) has a bundled recipe leading to it. Do not copy a burger recipe from another Alex's Mobs release and assume MattMC accepts it. [Registrations][items] · [Bundled recipes][recipes] · [Bundled loot][loot-data]

## Usage

Hold use to eat a burger already in your inventory. If choosing between supplied kangaroo foods, a burger gives **2 more hunger points and 5.6 more saturation** per serving than Cooked Kangaroo Meat. Both avoid the raw meat's Hunger roll. [Food values][foods] · [Food registrations][items] · [Raw consumption effect][raw-effect] · [Held use][use]

An injured, tamed Kangaroo can also consume a burger for **up to 8 health points by hand**, or **16 from its pouch**. Keeping burgers in that pouch allows the animal to eat them automatically when hurt. The [Kangaroo pouch and healing guide](../mobs/Kangaroo.md#pouch-equipment-and-healing) owns the storage controls and shared feeding rules. [Hand feeding][hand-healing] · [Pouch feeding][pouch-healing] · [Health cap][health-cap]

## Behavior

The burger takes **32 use ticks** to eat. It is not an always-edible food, so ordinary Survival players need missing hunger before starting. Completed consumption invokes the food component, adds nutrition and saturation, and uses **one burger** unless the eater has infinite materials. [Duration][duration] · [Use ticks, eligibility and completion][consume] · [Player hunger check][hunger-check] · [Food listener][food-listener] · [Stack consumption][stack-consumption]

Ordinary burgers stack to **64** and leave no bowl or other returned container. The listed restoration is before caps, so a nearly full hunger bar can waste part of the serving. See [Hunger](../mechanics/Hunger.md) for shared limits and [Food reference](FoodReference.md) for the wider comparison. [Registration][items] · [Stack default][default-stack] · [Caps][caps]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the active consumption and Kangaroo feeding paths, category/browser admission, and full active Java/resource references for acquisition and recipes. No in-game eating, feeding, crafting, cooking or loot test was run. Server data packs and custom components may add routes or change these defaults.

Related: [Raw Kangaroo Meat](RawKangarooMeat.md) · [Cooked Kangaroo Meat](CookedKangarooMeat.md) · [Kangaroo Hide](KangarooHide.md) · [Kangaroo](../mobs/Kangaroo.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1756-L1759
[foods]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L12-L16
[formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[default-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L371
[default-consumable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L15
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[food-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1683-L1718
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L89-L108
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[browser-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[mode-abilities]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[loot-data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table
[raw-effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L26-L28
[use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L196
[hand-healing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L244-L251
[pouch-healing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L494-L515
[health-cap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1140
[duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L102
[hunger-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[food-listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L57
[stack-consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[default-stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30

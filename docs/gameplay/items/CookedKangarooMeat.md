# Cooked Kangaroo Meat

**Cooked Kangaroo Meat** (`minecraft:cooked_kangaroo_meat`) restores **6 hunger points (three drumsticks)** and **7.2 saturation before caps**, using the same food values as Cooked Chicken. Its ordinary eating behavior has **no added status effect**, unlike [Raw Kangaroo Meat](RawKangarooMeat.md). [Registration][items] · [Nutrition][foods] · [Saturation calculation][formula] · [Default food component][default-properties] · [Default consumable][default-consumable]

## Obtaining

Cooked Kangaroo Meat is an ordinary **Food & Drinks** category entry in the combined [inventory item browser](../mechanics/InventoryBrowser.md). The catalog is visible in Survival, but accepted insertion requires the server player's infinite-materials ability, normally supplied by **Creative**. Its catalog entry is not a Survival cooking route. [Category entry][food-category] · [Catalog assembly][browser-list] · [Packet gate][browser-gate] · [Server ability check][browser-context] · [Mode abilities][mode-abilities]

**No recipe producing this cooked meat was found** in the checked built-in data: there is no verified Furnace, Smoker or Campfire conversion from Raw Kangaroo Meat. Do not spend fuel expecting the raw and cooked names to supply a missing recipe. [Registrations][items] · [Bundled recipes][recipes]

No built-in loot or Java producer for it was found either. In particular, the bundled data has no Kangaroo entity loot table, and its custom death path only returns existing pouch contents. Burning a Kangaroo is therefore not a verified source of cooked meat. See the [animal's drop limits](../mobs/Kangaroo.md#safety-and-drops). [Bundled loot][loot-data] · [Missing-table handling][loot-missing] · [Custom and pouch drops][pouch-drops]

## Usage

Eat it by holding use until consumption finishes. If both foods have already been supplied, cooked meat provides **4 more hunger points and 6 more saturation** per serving than raw meat, without the raw item's Hunger roll. This compares their item behavior; it does not imply a working conversion recipe. [Food values][foods] · [Registrations][items] · [Raw effect][raw-effect] · [Held use][use] · [Consumption][consume]

The checked recipes also do not establish Cooked Kangaroo Meat as a [Kangaroo Burger](KangarooBurger.md) ingredient. Consult server-specific recipes if a pack adds that route. [Bundled recipes][recipes]

An injured, tamed Kangaroo can consume one for **up to 6 health points by hand**, or **12 from its pouch**. Stored food may be eaten automatically; the [pouch and healing guide](../mobs/Kangaroo.md#pouch-equipment-and-healing) explains the shared controls. [Hand feeding][hand-healing] · [Pouch feeding][pouch-healing] · [Health cap][health-cap]

## Behavior

Eating takes **32 use ticks** and requires missing hunger for an ordinary Survival player. Each completed meal consumes **one item**, except when the eater has infinite materials. The default item stacks to **64**, has no returned container, and has no always-eat flag. [Duration][duration] · [Use ticks and eligibility][consume] · [Player hunger check][hunger-check] · [Consumption exemption][stack-consumption] · [Registration][items] · [Stack default][default-stack]

Its food component is invoked by the active consumption path. Actual restoration is limited by the shared caps in [Hunger](../mechanics/Hunger.md); use [Food reference](FoodReference.md) to compare it with other meals. [Food listener][food-listener] · [Caps][caps]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked active food consumption and Kangaroo feeding, category/browser access, inherited/custom loot, and full active Java/resource references. No in-game eating, feeding, cooking, crafting or burning/drop test was run. The acquisition limits describe the built-in bundle; server packs and custom components may differ.

Related: [Raw Kangaroo Meat](RawKangarooMeat.md) · [Kangaroo Burger](KangarooBurger.md) · [Kangaroo Hide](KangarooHide.md) · [Kangaroo](../mobs/Kangaroo.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1756-L1759
[foods]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L12-L16
[formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[default-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L371
[default-consumable]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L13-L15
[food-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1683-L1718
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L89-L108
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[browser-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[mode-abilities]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[loot-data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[pouch-drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L210-L225
[raw-effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L26-L28
[use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L196
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L102
[hand-healing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L244-L251
[pouch-healing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L494-L515
[health-cap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1140
[duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[hunger-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[stack-consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[default-stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[food-listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L57
[caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30

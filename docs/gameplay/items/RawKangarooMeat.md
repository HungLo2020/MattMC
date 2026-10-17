# Raw Kangaroo Meat

**Raw Kangaroo Meat** (`minecraft:kangaroo_meat`) restores **2 hunger points (one drumstick)** and **1.2 saturation before caps**. Eating it has a **30% chance of Hunger I for 600 ticks**, nominally 30 seconds at 20 ticks per second. It uses both the raw-chicken food values and its harmful consumption effect. [Registration][items] · [Nutrition][foods] · [Saturation calculation][formula] · [Hunger effect][raw-effect] · [Probability check][effect-roll]

## Obtaining

The ordinary **Food & Drinks** category includes Raw Kangaroo Meat. Find it by displayed name in the [inventory item browser](../mechanics/InventoryBrowser.md). The catalog can be viewed in Survival, but accepted insertion requires the server player's infinite-materials ability, normally **Creative**. Catalog visibility is not an ordinary Survival supply. [Category entry][food-category] · [Catalog assembly][browser-list] · [Packet gate][browser-gate] · [Server ability check][browser-context] · [Mode abilities][mode-abilities]

**No built-in Survival production route was found** in the reviewed Java and resource data. In particular, there is no bundled Kangaroo entity loot table or recipe producing this meat. Missing default loot resolves to an empty table. A [Kangaroo's pouch drops](../mobs/Kangaroo.md#safety-and-drops) return existing contents; they do not establish a fresh meat drop. [Bundled loot][loot-data] · [Default key][loot-key] · [Missing-table handling][loot-missing] · [Custom and pouch drops][pouch-drops] · [Bundled recipes][recipes]

## Usage

Hold the meat and use it to eat. Finishing consumption applies its food values and then rolls the Hunger effect; the effect is part of the active eating path, not just a tooltip warning. See [Hunger and Saturation effects](../effects/HungerAndSaturation.md) for what Hunger does. [Held use][use] · [Consumption and effects][consume] · [Food listener][food-listener]

**No raw-to-cooked conversion was found** in the checked bundled recipes. [Cooked Kangaroo Meat](CookedKangarooMeat.md) and [Kangaroo Burger](KangarooBurger.md) exist as separate registered foods, but their names do not establish furnace, smoker, campfire or burger recipes. [Registrations][items] · [Bundled recipes][recipes]

An injured, tamed Kangaroo can instead consume this food for **up to 2 health points by hand**, or **4 from its pouch**. Food stored there can be eaten automatically, so follow the [pouch and healing guide](../mobs/Kangaroo.md#pouch-equipment-and-healing) before treating it as cargo. [Hand feeding][hand-healing] · [Pouch feeding][pouch-healing] · [Health cap][health-cap]

## Behavior

Eating takes **32 use ticks**. Ordinary Survival players need missing hunger to begin; this food is not always edible. Completed consumption uses **one item**, except for the infinite-materials exemption. It leaves no container and ordinary stacks hold **64**. [Duration][duration] · [Use ticks and eligibility][consume] · [Player hunger check][hunger-check] · [Item consumption][stack-consumption] · [Registration][items] · [Stack default][default-stack]

The values above are before the shared food caps. Use [Food reference](FoodReference.md) to compare meals and [Hunger](../mechanics/Hunger.md) for saturation, exhaustion and healing rules. [Food caps][caps]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked registration through active consumption, the generic Kangaroo feeding paths, category/browser access, inherited loot and the active resource/Java search. No in-game eating, effect-frequency, feeding, loot, cooking or crafting test was run. Data packs, assigned loot tables and custom components can change the defaults.

Related: [Cooked Kangaroo Meat](CookedKangarooMeat.md) · [Kangaroo Burger](KangarooBurger.md) · [Kangaroo Hide](KangarooHide.md) · [Kangaroo](../mobs/Kangaroo.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1756-L1759
[foods]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/Foods.java#L12-L16
[formula]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodConstants.java#L30-L32
[raw-effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L26-L28
[effect-roll]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/consume_effects/ApplyStatusEffectsConsumeEffect.java#L48-L62
[food-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1683-L1718
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L89-L108
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[browser-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[mode-abilities]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[loot-data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table
[loot-key]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[pouch-drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L210-L225
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L196
[consume]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumable.java#L77-L102
[food-listener]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodProperties.java#L39-L57
[hand-healing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L244-L251
[pouch-healing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L494-L515
[health-cap]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1127-L1140
[duration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/Consumables.java#L67-L69
[hunger-check]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1512-L1514
[stack-consumption]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1076-L1080
[default-stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[caps]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/food/FoodData.java#L19-L30

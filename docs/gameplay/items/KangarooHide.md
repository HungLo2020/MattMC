# Kangaroo Hide

**Kangaroo Hide** (`minecraft:kangaroo_hide`) is a plain material item that stacks to **64**. It is listed in the Ingredients category, but **no built-in Survival supply or crafting use was found** in the checked MattMC bundle. Do not assume the recipes or animal drops from another Alex's Mobs version apply here. [Registration][items] · [Plain item][plain-item] · [Default stack][default-stack]

## Obtaining

Find Hide in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), whose combined catalog includes the ordinary **Ingredients** entry. You can browse it in Survival, but accepted insertion requires the server player's infinite-materials ability, normally supplied by **Creative**. Seeing Hide in the catalog does not make it obtainable through ordinary Survival insertion. [Category][ingredients-category] · [Hide entry][hide-category] · [Catalog assembly][browser-list] · [Packet gate][browser-gate] · [Server ability check][browser-context] · [Mode abilities][mode-abilities]

The checked built-in data has **no Kangaroo entity death-loot table** and no recipe producing Hide. The default entity loot key points to the animal's own table; when that table is missing, it resolves to empty loot. Killing a [Kangaroo](../mobs/Kangaroo.md#safety-and-drops) is therefore not a verified source of newly generated Hide. [Bundled loot][loot-data] · [Default key][loot-key] · [Missing-table handling][loot-missing] · [Bundled recipes][recipes]

A Kangaroo's pouch-drop path returns items already stored in its pouch. Its custom death callback does not generate Hide, so returning a supplied stack is different from producing more. A server pack or explicitly assigned death-loot table can change the available routes. [Pouch and custom drops][pouch-drops] · [Loot override][loot-override]

## Usage

No recipe consuming Hide was found in the checked built-in data. The Ingredients category is a catalog grouping, not evidence of a Hide-to-Leather, pouch, armor or other crafting recipe. Keep it as a collected material unless your server supplies a use. [Bundled recipes][recipes] · [Registration][items]

For the animal's storage feature, use the [Kangaroo pouch guide](../mobs/Kangaroo.md#pouch-equipment-and-healing). A Hide item is not the pouch-opening tool; that guide owns the taming and interaction controls.

## Behavior

The registration creates an ordinary item with default properties: **no food value, durability, equipment component or special held-use action**. It cannot be eaten like [Raw Kangaroo Meat](RawKangarooMeat.md), and the plain item is not a placeable block. [Registration][items] · [Ordinary item constructor][plain-item] · [Default components][default-stack] · [Property defaults][default-properties] · [Use behavior][use]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. Checked the active item/category registrations, browser admission, inherited and custom Kangaroo loot paths, and full active Java/resource references. No in-game acquisition, killing, crafting or item-use test was run. The absence findings concern this built-in bundle; data packs and custom item components can change it.

Related: [Kangaroo](../mobs/Kangaroo.md) · [Raw Kangaroo Meat](RawKangarooMeat.md) · [Cooked Kangaroo Meat](CookedKangarooMeat.md) · [Kangaroo Burger](KangarooBurger.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1756-L1759
[plain-item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2792-L2797
[default-stack]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[ingredients-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1775-L1781
[hide-category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1818-L1825
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L89-L108
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[browser-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[mode-abilities]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameType.java#L62-L79
[loot-data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/loot_table
[loot-key]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-missing]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[recipes]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/recipe
[pouch-drops]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityKangaroo.java#L210-L225
[loot-override]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/Mob.java#L390-L404
[default-properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L354-L371
[use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L196

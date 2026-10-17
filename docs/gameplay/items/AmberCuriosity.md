# Amber Curiosity

**Amber Curiosity** (`minecraft:amber_curiosity`) is a registered material with no confirmed bundled Survival source or progression use in this snapshot. Do not plan a [Primordial Caves](../dimensions/PrimordialCaves.md) expedition around obtaining or spending it. [Registration][item]

## Obtaining

Amber Curiosity is an Ingredients-category entry. In Creative, request it through the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). Ordinary Survival can display the entry but cannot use that browser to insert it: admission requires the server player's infinite-materials ability. [Category][category] · [Browser list][browser-list] · [Admission gate][browser-gate] · [Server context][browser-context]

No recipe output, loot entry or prefilled structure inventory containing this item was found in the reviewed bundle. Its name and category placement do not establish a dinosaur drop or an Alex's Caves progression reward. [Bundled data][data] · [Optional packs][packs]

## Usage

No bundled recipe ingredient or dedicated Java interaction consuming Amber Curiosity was found. The current registration adds no special behavior; upstream uses should not be assumed to work here. [Item][item] · [Default use][plain-use]

## Behavior

The normal stack limit is **64** and rarity is **common**. This is a plain Item with no food, consumable, equipment or durability component added by its registration. Holding or using an unmodified Amber Curiosity does not grant a special effect. [Item][item] · [Registration helper][registration] · [Defaults][defaults] · [Properties][properties] · [Default use][plain-use]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game acquisition or use test was run. The bundled-resource review enumerated `minecraft` recipe and loot filenames, parsed their contents and nested references, resolved item tags, and decoded structure inventories. It covered the ordinary data and bundled optional packs; external server data packs can change the result. [Ordinary data][data] · [Optional packs][packs]


Related: [Heavy Bone](HeavyBone.md) · [Tough Hide](ToughHide.md) · [Items](Items.md)

[browser-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1825-L1831
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1683-L1690
[plain-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L198
[properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L357-L388
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2784-L2798
[data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft
[packs]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks

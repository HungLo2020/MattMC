# Tough Hide

**Tough Hide** (`minecraft:tough_hide`) is a registered material with no confirmed bundled Survival source or equipment recipe in this snapshot. [Registration][item]

## Obtaining

Tough Hide is an Ingredients-category entry. In Creative, request it through the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). Ordinary Survival can display the entry but cannot use that browser to insert it: admission requires the server player's infinite-materials ability. [Category][category] · [Browser list][browser-list] · [Admission gate][browser-gate] · [Server context][browser-context]

No recipe output, loot entry or prefilled structure inventory containing Tough Hide was found in the reviewed bundle. The [Atlatitan](../mobs/Atlatitan.md) has the default loot key `minecraft:entities/atlatitan`, but its exact `loot_table/entities/atlatitan.json` file is absent from ordinary and optional-pack data. Do not assume that hunting this dinosaur supplies hides. [Entity registration][atlatitan] · [Loot-key construction][entity-loot] · [Bundled data][data] · [Optional packs][packs]

## Usage

No bundled recipe ingredient or dedicated Java interaction consuming Tough Hide was found. It is not registered as wearable equipment, and merely carrying it grants no armor or protection. [Item][item] · [Default use][plain-use]

## Behavior

The normal stack limit is **64** and rarity is **common**. This is a plain Item with no food, consumable, equipment or durability component added by its registration. Holding or using an unmodified Tough Hide does not grant a special effect. [Item][item] · [Registration helper][registration] · [Defaults][defaults] · [Properties][properties] · [Default use][plain-use]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game acquisition or use test was run. The bundled-resource review enumerated `minecraft` recipe and loot filenames, parsed their contents and nested references, resolved item tags, and decoded structure inventories. It covered the ordinary data and bundled optional packs; external server data packs can change the result. [Ordinary data][data] · [Optional packs][packs]


Related: [Heavy Bone](HeavyBone.md) · [Amber Curiosity](AmberCuriosity.md) · [Items](Items.md)

[atlatitan]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L259-L265
[browser-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1822-L1830
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L2061-L2066
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1684-L1690
[plain-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L198
[properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L357-L388
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2784-L2798
[data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft
[packs]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks

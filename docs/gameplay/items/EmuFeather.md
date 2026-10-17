# Emu Feather

**Emu Feather** (`minecraft:emu_feather`) is registered, but farming or killing [Emus](../mobs/Emu.md) is not an established source of it in this snapshot. The Emu's verified periodic product is an **[Emu Egg](EmuEgg.md)**. [Feather registration][item] · [Adult egg production][emu-eggs]

## Obtaining

Emu Feather is an Ingredients-category entry. In Creative, request it through the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). Ordinary Survival can display the entry but cannot use that browser to insert it: admission requires the server player's infinite-materials ability. [Category][category] · [Browser list][browser-list] · [Admission gate][browser-gate] · [Server context][browser-context]

The Emu's default death-loot key is `minecraft:entities/emu`. The exact `loot_table/entities/emu.json` file is absent from ordinary and bundled optional-pack data, and no feather-producing callback was found in the Emu implementation. The compatibility registry only points to the existing item; it does not add a drop. [Emu registration][emu-type] · [Loot-key construction][entity-loot] · [Emu implementation][emu] · [Compatibility alias][alias]

No recipe output, other loot entry or prefilled structure inventory containing Emu Feather was found in the reviewed bundle. No Survival feather farm is established here. [Bundled data][data] · [Optional packs][packs]

## Usage

No bundled recipe ingredient or dedicated Java interaction consuming Emu Feather was found. The fact that it is called a feather does not make it an ingredient in a recipe that specifically requests the ordinary Feather. [Item][item] · [Bundled data][data]

## Behavior

The normal stack limit is **64** and rarity is **common**. This is a plain Item with no food, consumable, equipment or durability component added by its registration. Holding or using an unmodified Emu Feather does not grant a special effect. [Item][item] · [Registration helper][registration] · [Defaults][defaults] · [Properties][properties] · [Default use][plain-use]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game acquisition or use test was run. The bundled-resource review enumerated `minecraft` recipe and loot filenames, parsed their contents and nested references, resolved item tags, and decoded structure inventories. It covered the ordinary data and bundled optional packs; external server data packs can change the result. [Ordinary data][data] · [Optional packs][packs]


Related: [Emu](../mobs/Emu.md) · [Emu Egg](EmuEgg.md) · [Items](Items.md)

[alias]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L48-L54
[browser-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1806-L1812
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[emu]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityEmu.java
[emu-eggs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityEmu.java#L193-L200
[emu-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L504-L510
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L2061-L2066
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L1856-L1859
[plain-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L198
[properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L357-L388
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2784-L2798
[data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft
[packs]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks

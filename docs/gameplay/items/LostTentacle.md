# Lost Tentacle

**Lost Tentacle** (`minecraft:lost_tentacle`) has a Giant Squid drop branch, but the whale-capture sequence that would reach it is disconnected in this snapshot. Do not build a [Giant Squid](../mobs/GiantSquid.md)/[Cachalot Whale](../mobs/CachalotWhale.md) farm expecting tentacles. [Drop branch][capture] · [Disabled caller][caller] · [Disabled capture start][capture-start]

## Obtaining

An authorized player with permission level **2** can request one with `/give @s minecraft:lost_tentacle`. The item is registered but has no entry in the checked Creative category lists; the [inventory browser](../mechanics/InventoryBrowser.md#which-items-appear) builds its catalog from those lists, rather than every registered item. [Item][item] · [Give command][give] · [Command registration][give-registration] · [Registry lookup][item-parser] · [Category lists][creative-tabs] · [Browser list][browser-list]

The squid's `tickCaptured` method can produce **one** Lost Tentacle during a successful random escape branch. However, the whale's call to that method and its squid-grab initiation are both commented out. The method's conditional **20%** tentacle check is therefore not a working drop rate or farm yield. [Capture method][capture] · [Whale caller][caller] · [Capture initiation][capture-start]

No bundled recipe output, loot entry or prefilled structure inventory containing the item was found. The squid's default `minecraft:entities/giant_squid` table also has no `loot_table/entities/giant_squid.json` file in ordinary or optional-pack data. Killing a squid is not an established alternative source. [Default loot key][entity-loot] · [Squid registration][squid-type] · [Bundled data][data] · [Optional packs][packs]

## Usage

No bundled recipe ingredient or dedicated Java consumer of Lost Tentacle was found. The compatibility item holder supplies the same registered item to the disconnected drop branch; it does not supply a use. [Alias][alias] · [Item][item]

## Behavior

The normal stack limit is **64** and rarity is **common**. This is a plain Item with no food, consumable, equipment or durability component added by its registration. Holding or using an unmodified Lost Tentacle does not grant a special effect. [Item][item] · [Registration helper][registration] · [Defaults][defaults] · [Properties][properties] · [Default use][plain-use]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game acquisition or use test was run. The bundled-resource review enumerated `minecraft` recipe and loot filenames, parsed their contents and nested references, resolved item tags, and decoded structure inventories. It covered the ordinary data and bundled optional packs; external server data packs can change the result. [Ordinary data][data] · [Optional packs][packs]


Related: [Giant Squid](../mobs/GiantSquid.md) · [Cachalot Whale](../mobs/CachalotWhale.md) · [Commands](../commands/Commands.md) · [Items](Items.md)

[alias]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L53-L54
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[caller]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L590-L622
[capture]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityGiantSquid.java#L618-L634
[capture-start]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/alexsmobs/entity/EntityCachalotWhale.java#L682-L693
[creative-tabs]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[entity-loot]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L2061-L2066
[give]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/GiveCommand.java#L22-L67
[give-registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/commands/Commands.java#L205-L209
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2483
[item-parser]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/commands/arguments/item/ItemParser.java#L149-L155
[plain-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L198
[properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L357-L388
[registration]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2784-L2798
[squid-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/EntityType.java#L697-L703
[data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft
[packs]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks

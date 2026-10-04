# Ominous Catalyst

**Ominous Catalyst** (`minecraft:ominous_catalyst`) is an uncommon, fire-resistant item. No active omen, trial or event use was found for it in this snapshot. For the working Bad Omen route, use the separate **[Ominous Bottle](OminousBottle.md)** guide. [Catalyst registration][item] · [Bottle registration][bottle] · [Bottle effect][bottle-effect]

## Obtaining

Ominous Catalyst is an Ingredients-category entry. In Creative, request it through the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item). Ordinary Survival can display the entry but cannot use that browser to insert it: admission requires the server player's infinite-materials ability. [Category][category] · [Browser list][browser-list] · [Admission gate][browser-gate] · [Server context][browser-context]

No recipe output, loot entry or prefilled structure inventory supplying Ominous Catalyst was found in ordinary or bundled optional-pack data. Its rarity and name do not make it a [Vault](../blocks/Vault.md) reward or a [Trial Spawner](../blocks/TrialSpawner.md) ingredient. [Bundled data][data] · [Optional packs][packs]

### Catalog name

For default item stacks with the reviewed bundled English resources, search the [inventory item browser](../mechanics/InventoryBrowser.md#finding-and-requesting-an-item) for `ominous_catalyst`, including the underscores. The source-resolved display name is `item.minecraft.ominous_catalyst`; this page's readable title is not a bundled translation. Custom item names, another language or resource-pack translations can change that text. This name/search guidance is source-derived, without a running-client check. [Default item names][catalog-name-init] · [Hover name][catalog-hover-name] · [Missing-key fallback][catalog-name-fallback] · [Name search][catalog-name-search] · [Bundled English][catalog-english]

## Usage

No bundled recipe ingredient or dedicated Java interaction consuming this item was found. It has no consumable or omen component. Carrying or using the normal Catalyst does not apply Bad Omen or turn a Trial Spawner ominous; the active system checks omen effects on players. [Item][item] · [Default use][plain-use] · [Trial effect checks][trial]

## Behavior

- **Stack limit: 64; rarity: uncommon.** [Registration][item] · [Defaults][defaults]
- **Dropped-item fire resistance:** its damage-resistant component uses the `minecraft:is_fire` damage-type tag, which includes lava and fire. The dropped-item damage path honors this component. This is protection for the dropped item, not a Fire Resistance effect for its holder. [Component][fire-property] · [Damage tag][fire-tag] · [Stack check][stack-damage] · [Dropped-item check][drop-damage]
- The registration adds no food, equipment or durability component. [Registration][item] · [Property defaults][properties]

## Notes

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`; no in-game acquisition or use test was run. The bundled-resource review enumerated `minecraft` recipe and loot filenames, parsed their contents and nested references, resolved item tags, and decoded structure inventories. It covered the ordinary data and bundled optional packs; external server data packs can change the result. [Ordinary data][data] · [Optional packs][packs]


Related: [Ominous Bottle](OminousBottle.md) · [Trial Spawner](../blocks/TrialSpawner.md#becoming-ominous) · [Vault](../blocks/Vault.md) · [Items](Items.md)

[bottle]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2696-L2702
[bottle-effect]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/component/OminousBottleAmplifier.java#L21-L33
[browser-context]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2320-L2323
[browser-gate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/network/protocol/game/GameProtocols.java#L40-L58
[browser-list]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[category]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1848-L1855
[defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/core/component/DataComponents.java#L383-L392
[drop-damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L253-L275
[fire-property]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L402-L404
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[item]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Items.java#L2526
[plain-use]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L164-L198
[properties]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L357-L388
[stack-damage]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L1101-L1104
[trial]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerStateData.java#L159-L178
[data]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft
[packs]: https://github.com/HungLo2020/MattMC/tree/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/datapacks

[catalog-name-init]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/Item.java#L122-L125
[catalog-hover-name]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/item/ItemStack.java#L792-L817
[catalog-name-fallback]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/locale/Language.java#L110-L114
[catalog-name-search]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L115-L132
[catalog-english]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/assets/minecraft/lang/en_us.json

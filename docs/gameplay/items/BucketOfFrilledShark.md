# Bucket of Frilled Shark

**Bucket of Frilled Shark** (`minecraft:frilled_shark_bucket`) captures and releases a [Frilled Shark](../mobs/FrilledShark.md). It stacks to **one**, contains Water and uses the standard mob-bucket release class. That class adds the created shark to the server level. [Registration][items] · [Release caller][bucket-release]

## Obtaining

Use a **Water Bucket** on a living Frilled Shark. An empty Bucket does not qualify. Capture makes the filled item and discards the original creature. You can also request the listed item through MattMC's [inventory browser](../mechanics/InventoryBrowser.md) in Survival or Creative. [Species interaction][shark-bucket] · [Shared capture][bucket-capture] · [Listed bucket][shark-bucket-list] · [Browser entries][browser-list] · [Client request][browser-client] · [Server handling][browser-server]

No crafting recipe or loot-table supply was found for this bucket in the checked bundled data. Capture and inventory access are distinct acquisition routes. [Recipe loader][recipe-loader] · [Bundled data][bundled-data]

## Usage

Use it at a suitable placement target in a prepared water enclosure. After the ordinary water action succeeds, the server creates a shark, loads its bucket data, sets its from-bucket flag and adds it to the world. Successful ordinary use returns an **empty Bucket in Survival**; Creative's infinite-material handling retains the filled item. [Water-use order][bucket-use] · [Entity release][bucket-release] · [Inventory result][item-result]

**Do not empty it in an ultra-warm dimension such as the Nether.** Water evaporation returns success to the bucket-use caller, so the creature callback can still run even though no Water was placed. A released shark then lacks the Water it needs. Waterlogging a target is also not proof of sufficient open swimming space; the bucket's success result is not a check of aquarium size. [Water placement and evaporation][bucket-fluid] · [Callback dispatch][bucket-use] · [Water care](../mobs/FrilledShark.md#water-care-and-swimming)

## Behavior

The common bucket component restores saved **health** and supported flags, while the item-to-entity component path applies the custom name. Release then explicitly marks the shark as from a bucket, protecting it from ordinary distance despawning. This is source-verified wiring, not a tested lossless transfer. [Common saved data][bucket-data] · [Item component path][item-components] · [Component application][custom-components] · [Release order][bucket-release] · [Persistence](../mobs/FrilledShark.md#capture-and-persistence)

The shark writes its extra `FrilledSharkData` into `CUSTOM_DATA`, but the standard release loader passes `BUCKET_ENTITY_DATA` to its species reader. Those saved extra fields therefore are not restored through that reader. The generic `CUSTOM_DATA` application stores the component without interpreting the shark's nested pressure flag. **Do not expect a bucket to preserve a chosen pressure appearance:** the shark recalculates that appearance from the water column after release anyway. [Species writer and reader][shark-bucket] · [Shared component loader][bucket-data] · [Generic custom-data handling][custom-components] · [Pressure updates][shark-attack] · [Required water column](../mobs/FrilledShark.md#pressure-appearance-and-names)

## Notes

[Frilled Shark](../mobs/FrilledShark.md) owns the care, prey, combat and retention details. The separate [Bucket of Comb Jelly](BucketOfCombJelly.md) uses a different release override with a missing creature-insertion call; its warning remains relevant to that item. [Frilled Shark binding][items] · [Standard insertion][bucket-release] · [Comb Jelly override][jelly-release]

Source-reviewed at `4ae465c9594b9a6507f67b4fedf81cdbafa7a490` on 2026-10-02. No gameplay, capture/release, saved-state or Nether-release test was run.

[browser-client]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-list]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-server]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1927
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[bucket-data]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L74
[bucket-fluid]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/BucketItem.java#L99-L142
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L51
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L92
[bundled-data]: https://github.com/HungLo2020/MattMC/tree/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/resources/data
[custom-components]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/Entity.java#L3926-L3964
[item-components]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1728
[item-result]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/ItemUtils.java#L15-L39
[items]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/Items.java#L1825-L1842
[jelly-release]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/item/ItemModFishBucket.java#L39-L70
[recipe-loader]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/crafting/RecipeManager.java#L60-L83
[shark-attack]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L240-L275
[shark-bucket]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/alexsmobs/entity/EntityFrilledShark.java#L180-L217
[shark-bucket-list]: https://github.com/HungLo2020/MattMC/blob/4ae465c9594b9a6507f67b4fedf81cdbafa7a490/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1434-L1445

# Bucket of Salmon

A **Bucket of Salmon** transports a living [Salmon](../mobs/Salmon.md) with water. Its ID is `minecraft:salmon_bucket`, and it stacks to **1**. [Registration][bucket-items]

## Obtaining

Use a **Water Bucket** on the living fish; an empty Bucket is not enough. The filled item is also ordinarily listed in the [inventory item browser](../mechanics/InventoryBrowser.md), which allows insertion in Survival and Creative. Browser insertion is a separate route from natural capture. [Capture][bucket-capture] · [Listing][bucket-list] · [Client request][browser-client] · [Server handling][browser-server]

## Usage

Release it into a prepared enclosure using the [common fish-bucket placement steps](BucketOfCod.md#usage). Ordinary successful Survival use returns an empty Bucket. Avoid ultra-warm dimensions, where the water evaporates but the animal can still be released. [Bucket use][bucket-use] · [Evaporation][bucket-water] · [Animal release][bucket-release]

## Behavior

Capture stores the **small, medium or large size** alongside health and custom name. Release initializes a Salmon, then applies the saved size component, preserving the captured variant. A plain browser-supplied bucket has no captured size component; its released Salmon uses the random spawn initialization. [Saved size][salmon-bucket] · [Creation order][spawn-order] · [Item components][stack-config]

Release marks the fish as bucket-origin, preventing ordinary distance despawning. See the [shared saved-data rules](BucketOfCod.md#behavior) and the mob's care instructions. [Release flag][bucket-release] · [Persistence][fish]

## Notes

Use [Nautilus feeding](../mobs/Nautilus.md#taming-and-feeding) for that separate interaction, including its bucket-return limitation.

Related: [Water Bucket](WaterBucket.md) · [Salmon](../mobs/Salmon.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked the active capture and release paths, species-specific saved state, browser listing and container behavior. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[bucket-items]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/Items.java#L1534-L1573
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L93
[bucket-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1438-L1448
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/BucketItem.java#L40-L93
[bucket-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/BucketItem.java#L99-L140
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[salmon-bucket]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Salmon.java#L98-L133
[spawn-order]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1774
[stack-config]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1728
[fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AbstractFish.java#L34-L142

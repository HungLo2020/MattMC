# Bucket of Cod

A **Bucket of Cod** carries one living [Cod](../mobs/Cod.md) and water. Its ID is `minecraft:cod_bucket`, and it stacks to **1**. This page also covers the common release rules for the four ordinary fish buckets. [Item registration][bucket-items]

## Obtaining

Use a **Water Bucket** directly on a living Cod. An empty Bucket does not capture it. The captured fish is removed from the world and becomes the bucket's saved animal; this is not a death or a source of fish loot. [Interaction][fish] · [Capture][bucket-capture]

The filled bucket is an ordinary category-listed item, so the [inventory item browser](../mechanics/InventoryBrowser.md) can also supply it in Creative. Browser insertion is separate from finding and capturing a natural fish. [Listing][bucket-list] · [List construction][browser-list] · [Client request][browser-client] · [Server handling][browser-server]

## Usage

1. Prepare a water-filled enclosure with enough space for the fish
2. Use the filled bucket on a suitable block face. The bucket's normal placement checks decide whether water can be placed or an accepting block can hold it
3. After successful placement, the fish is created at that location. Ordinary Survival use returns an **empty Bucket**; infinite-material use retains the filled bucket

The item already contains water, so an ordinary successful release can create a water source in a replaceable space. **Avoid ultra-warm dimensions:** water evaporates there but the bucket still reaches its creature-release step, leaving a fish without that water. A waterlogged block also does not automatically provide a spacious aquarium. [Use and returned container][bucket-use] · [Water placement and evaporation][bucket-water] · [Creature release][bucket-release]

## Behavior

Capture preserves **health and custom name**, plus supported special flags such as no-AI, silence, no-gravity, glowing and invulnerability when present. Release applies the item components and saved bucket data, then marks the fish as bucket-origin. That flag prevents ordinary distance despawning; it does not prevent drying, attacks or other damage. Capturing and releasing a fish is not a full-health reset. [Saved data][bucket-capture] · [Item components][stack-config] · [Release][bucket-release] · [Persistence][fish]

## Notes

- Keep the released fish wet; see [Cod care](../mobs/Cod.md#behavior)
- [Salmon buckets](BucketOfSalmon.md) additionally preserve size; [Tropical Fish buckets](BucketOfTropicalFish.md) preserve appearance
- [Nautilus feeding](../mobs/Nautilus.md#taming-and-feeding) uses different consumption rules; it is not the normal world-release interaction

Related: [Water Bucket](WaterBucket.md) · [Bucket](Bucket.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `eafe6b61edf55cefa8036ec540a3029da1143637`. Checked capture dispatch, saved data, item registration, browser listing, water placement, creature creation and returned container. No in-game test was run. Timing assumes 20 ticks per second; data packs can change the listed biome entries, tags, recipes and loot.

[bucket-items]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/Items.java#L1534-L1573
[fish]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/AbstractFish.java#L34-L142
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L93
[bucket-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1438-L1448
[browser-list]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L87-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1928
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/BucketItem.java#L40-L93
[bucket-water]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/BucketItem.java#L99-L140
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[stack-config]: https://github.com/HungLo2020/MattMC/blob/eafe6b61edf55cefa8036ec540a3029da1143637/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1728

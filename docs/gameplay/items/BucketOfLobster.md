# Bucket of Lobster

The **Bucket of Lobster** (`minecraft:lobster_bucket`) captures and releases a [Lobster](../mobs/Lobster.md). **Its current release does not preserve the captured color**: the saved variant is written to a component that the standard release path does not read. [Registration][l-bucket] · [Species bucket data][l-bucket-data] · [Component reader][bucket-component]

## Obtaining

Use a **Water Bucket** on a living Lobster. Capture replaces the ordinary Survival Water Bucket with the filled item and removes the mob. An empty Bucket does not work. Common health/name data is captured, but do not collect a rare color expecting it to survive release unchanged. [Interaction][l-bucket-data] · [Capture caller][bucket-capture] · [Common data][bucket-common-data]

A plain filled bucket is also available from MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival as well as Creative**, because it is an ordinary category entry. [Category entry][l-bucket-list]

## Usage

Use the bucket where water can be placed and leave space for the Lobster. After successful water handling, the standard MobBucketItem method creates the creature, loads its bucket data, marks it as bucket-origin and adds it to the level. Ordinary Survival use returns an empty Bucket; Creative's infinite-materials handling keeps the filled item. [Bucket-use caller][bucket-use] · [Creature release][standard-release]

In an ultra-warm dimension, the water-evaporation branch still reports success, so creature release can run without leaving water. Keep the Lobster in a suitable water habitat: its active dry-air damage is described in [Lobster care](../mobs/Lobster.md#keeping-it-alive). [Water placement and evaporation][bucket-water]

## Behavior

The creature is initialized before the bucket data is loaded. Its health and custom name use the common capture path, but `BucketVariantTag` is saved in `CUSTOM_DATA` while the standard loader reads `BUCKET_ENTITY_DATA`. The released Lobster therefore retains its **new random initialization color**. A plain browser bucket also uses that random selection. [Initialization before components][entity-create] · [Species save/load][l-bucket-data] · [Common data][bucket-common-data] · [Reader][bucket-component] · [Variant roll][l-variants]

The released creature receives the bucket-origin flag, which protects it from ordinary distance despawning. It does not become a tame pet or gain breeding behavior. [Release flag][standard-release] · [Persistence and interaction][l-bucket-data]

## Notes

- The filled item stacks to one and uses the standard MobBucketItem Water release class. [Registration][l-bucket]
- Normal world saving preserves a Lobster's color; the limitation above concerns the bucket transition. [World save][l-save]
- Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02. No in-game color-retention, bucket or air test was run.

Related: [Lobster](../mobs/Lobster.md) · [Water Bucket](WaterBucket.md) · [Items](Items.md)

[l-bucket]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1905-L1909
[l-bucket-data]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L145-L184
[bucket-component]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L32
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[bucket-common-data]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L39-L76
[l-bucket-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1451
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L92
[standard-release]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[bucket-water]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L140
[entity-create]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1776
[l-variants]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L265-L281
[l-save]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/EntityLobster.java#L237-L257

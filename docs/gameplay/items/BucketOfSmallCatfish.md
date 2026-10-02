# Bucket of Small Catfish

The **Bucket of Small Catfish** (`minecraft:small_catfish_bucket`) can be filled by capturing a [small Catfish](../mobs/Catfish.md), but **its current release code does not add a fish to the world**. The Medium and Large Catfish buckets share this problem. Avoid relying on these items to transport fish or stored cargo. [Registration][c-buckets] · [Custom release][c-bucket-release]

## Obtaining

Use a **Water Bucket** on a living small Catfish. Capture removes the creature and produces the bucket matching its size; an empty Bucket does not perform this interaction. Read the release limitation before capturing a fish you want to keep. [Species interaction][c-use] · [Capture caller][bucket-capture] · [Bucket selection][c-bucket-save] · [Active item aliases][c-bucket-alias]

This filled bucket is also listed in the ordinary Tools and Utilities category. Request it through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival as well as Creative**. A browser bucket has no captured fish's cargo data. [Category entries][c-bucket-list] · [Item components][c-buckets]

## Usage

Using the filled bucket reaches ordinary water placement, then its custom creature-release method. In the checked implementation, that method creates and configures a Catfish, loads custom data and selects size zero for this item, but **never calls the level's entity-add method**. A successful ordinary bucket use can therefore place water and return an empty Bucket in Survival without restoring the captured fish. Creative's infinite-materials handling preserves the filled item, but does not repair the missing creature addition. [Custom release][c-bucket-release] · [Bucket-use caller and result][bucket-use] · [Entity creation versus addition][entity-create]

The [Medium](BucketOfMediumCatfish.md) and [Large](BucketOfLargeCatfish.md) versions select sizes one and two in the same method. Their names do not supply a separate working release path.

## Behavior

Capture writes the Catfish's size, swallowed-creature data and item inventory into custom item data. Common capture stores health in `BUCKET_ENTITY_DATA` and the custom name in `CUSTOM_NAME`. The custom release reads the custom-data container directly and calls the Catfish's load method. Because the fish is never added, these saved fields are not evidence of a working round trip. No health, name, cargo or swallowed-mob restoration is promised here. [Species save and load][c-bucket-save] · [Common saved data][bucket-common-data] · [Custom reader][c-bucket-release]

Use the [Catfish guide](../mobs/Catfish.md#behavior) for item collection, swallowing and Sea Pickle release controls. A bucket is not a food or breeding item.

## Notes

The shared Catfish and Comb Jelly release limitation is tracked in [#801](https://github.com/HungLo2020/MattMC/issues/801).

- The item stacks to **one** and uses the Water fluid with the custom `ItemModFishBucket` class. [Registration][c-buckets]
- The missing release call is a source-only integration finding. No capture, loss or release test was performed.
- Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02.

Related: [Catfish](../mobs/Catfish.md) · [Water Bucket](WaterBucket.md) · [Items](Items.md)

[c-buckets]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1549-L1563
[c-bucket-release]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/item/ItemModFishBucket.java#L39-L69
[c-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L357-L375
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[c-bucket-save]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L248-L300
[c-bucket-alias]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/item/AMItemRegistry.java#L27-L31
[c-bucket-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1437-L1439
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L92
[entity-create]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1776
[bucket-common-data]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L39-L76

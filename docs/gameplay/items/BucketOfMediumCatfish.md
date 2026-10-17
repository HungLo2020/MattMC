# Bucket of Medium Catfish

The **Bucket of Medium Catfish** (`minecraft:medium_catfish_bucket`) is the registered filled bucket for a medium [Catfish](../mobs/Catfish.md). It shares the **incomplete release method** used by all three Catfish bucket items. [Registration][c-buckets] · [Release implementation][c-bucket-release]

## Obtaining

Use a **Water Bucket** on a living medium Catfish to capture it and remove it from the world. The fish selects this item from its current size. An empty Bucket does not capture it. Read the release limitation before collecting a fish or cargo you want back. [Capture interaction][c-use] · [Shared capture][bucket-capture] · [Size-based item selection][c-bucket-save]

The item is also an ordinary category entry available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. This route is separate from finding a wild fish. [Category entries][c-bucket-list]

## Usage

The custom release selects size **1 (medium)**, but never adds the newly created Catfish to the level. Ordinary Survival use can still place water and leave an empty Bucket. This item therefore does not provide a working creature-transport round trip in the checked code. [Size selection and missing addition][c-bucket-release] · [Bucket result][bucket-use]

See [the shared Catfish bucket limitation](BucketOfSmallCatfish.md#usage) for the full caller chain, saved-data limits and Creative behavior.

## Behavior

A captured fish's size and contents are written into the item, but those fields do not make the incomplete release succeed. The [Catfish guide](../mobs/Catfish.md#sizes-and-initialization) explains actual size selection and the separate behavior of medium fish. [Capture data][c-bucket-save]

## Notes

- This item stacks to one. [Registration][c-buckets]
- Source-reviewed at `a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1` on 2026-10-02. No bucket or gameplay test was run.

Related: [Small Catfish bucket](BucketOfSmallCatfish.md) · [Catfish](../mobs/Catfish.md) · [Items](Items.md)

[c-buckets]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/Items.java#L1549-L1563
[c-bucket-release]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/item/ItemModFishBucket.java#L39-L69
[c-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L357-L375
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[c-bucket-save]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/alexsmobs/entity/EntityCatfish.java#L248-L300
[c-bucket-list]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1437-L1439
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/a24aecea45ed1b91bc5c3f8cf9fb89de728fffa1/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L92

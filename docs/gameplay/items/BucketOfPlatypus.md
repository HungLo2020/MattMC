# Bucket of Platypus

A **Bucket of Platypus** carries and releases one [Platypus](../mobs/Platypus.md). The bucket preserves its sensing and egg-carrying state, along with the other saved flags below. It still **does not preserve age**, so do not use it to keep a baby at the same age. [Registration][registration] · [Platypus bucket data][platypus-data]

## Obtaining

Use a **[Water Bucket](WaterBucket.md)** on a living Platypus. An empty [Bucket](Bucket.md) does not satisfy the capture check. No taming, ownership, or food is required, and the capture check has no adult-only restriction. Successful pickup removes the world entity and produces the filled bucket. [Interaction][interaction] · [Capture flow][capture]

Creative also lists the filled bucket. It is registered as `minecraft:platypus_bucket` and stacks to **one**. This does not establish where Platypuses spawn naturally; see the [mob page](../mobs/Platypus.md) for that separate limitation. [Creative entry][creative] · [Registration][registration]

## Releasing and keeping the animal

Use the filled bucket at a suitable placement spot. On success, the bucket's water-placement flow runs and the server creates a Platypus at the destination. In Survival, the item becomes an empty Bucket. Prepare an enclosure with water, land access, and a clear surface rather than releasing it into an exposed or blocked space. [Bucket use][use] · [Mob release][release]

The released animal is marked **from a bucket**, which its distance-despawning checks respect. This is persistence, not ownership: it does not add follow/sit commands or tame the Platypus. [Release marker][release] · [Persistence checks][persistence]

## What the bucket preserves

The shared bucket helper writes **health**, a custom name, and basic flags such as No AI, silence, glowing, invulnerability, and no gravity. The release path reads that shared bucket data back. A custom name is also carried on the item. A live capture/release round trip still needs verification; the focused automated coverage is described below. [Shared data][shared-data] · [Platypus name][name] · [Release][release]

The Platypus also saves its **sensing, egg-carrying, supercharged, fedora, and previous from-bucket flags** in the same bucket component. Release restores those fields, then marks the animal as from a bucket. This preserves an existing state; it does not add working treasure rewards, egg laying, or a Fedora item. [Bucket save/load][platypus-data] · [Release][release]

Older buckets are supported: the loader reads only those five known flags from legacy custom data when the corresponding field is absent from the current bucket data. Current data takes precedence, including an explicit false value. Unrelated custom data is left untouched.

**Age is still not saved.** A captured baby is not restored as the same-age baby. Temporary digging/visual animation state also starts fresh; sensing itself is preserved. Ordinary world saving follows its separate existing path. [World save/load][world-data]

## Related pages

- [Platypus](../mobs/Platypus.md)
- [Platypus Spawn Egg](PlatypusSpawnEgg.md)
- [Platypus Egg](PlatypusEgg.md)
- [Water Bucket](WaterBucket.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Item registration, Creative availability, capture, placement, and data-transfer paths were inspected. No in-game capture/release, name, health, age, or special-state preservation test was run.

The transfer section includes the correction for [issue #782](https://github.com/HungLo2020/MattMC/issues/782) on `fix/issue-782-platypus-bucket`. Focused automated item-codec and bucket-dispatch tests are provided. A live placement and dedicated-server synchronization check is still needed.

[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1523-L1527
[platypus-data]: https://github.com/HungLo2020/MattMC/blob/fix/issue-782-platypus-bucket/src/main/java/net/alexsmobs/entity/EntityPlatypus.java
[interaction]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L144-L166
[capture]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L71-L88
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1454
[use]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L93
[release]: https://github.com/HungLo2020/MattMC/blob/fix/issue-782-platypus-bucket/src/main/java/net/minecraft/world/item/MobBucketItem.java
[persistence]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L359-L367
[shared-data]: https://github.com/HungLo2020/MattMC/blob/fix/issue-782-platypus-bucket/src/main/java/net/minecraft/world/entity/animal/Bucketable.java
[name]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L109-L124
[world-data]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L325-L341

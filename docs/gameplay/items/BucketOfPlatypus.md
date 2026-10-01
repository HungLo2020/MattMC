# Bucket of Platypus

A **Bucket of Platypus** carries and releases one [Platypus](../mobs/Platypus.md). Capture and release are wired into MattMC's mob-bucket system, but the current bucket data does **not preserve every part of the animal's state**. Avoid using it as a way to safely preserve breeding progress or a baby's age. [Registration][registration] · [Platypus bucket data][platypus-data]

## Obtaining

Use a **[Water Bucket](WaterBucket.md)** on a living Platypus. An empty [Bucket](Bucket.md) does not satisfy the capture check. No taming, ownership, or food is required, and the capture check has no adult-only restriction. Successful pickup removes the world entity and produces the filled bucket. [Interaction][interaction] · [Capture flow][capture]

Creative also lists the filled bucket. It is registered as `minecraft:platypus_bucket` and stacks to **one**. This does not establish where Platypuses spawn naturally; see the [mob page](../mobs/Platypus.md) for that separate limitation. [Creative entry][creative] · [Registration][registration]

## Releasing and keeping the animal

Use the filled bucket at a suitable placement spot. On success, the bucket's water-placement flow runs and the server creates a Platypus at the destination. In Survival, the item becomes an empty Bucket. Prepare an enclosure with water, land access, and a clear surface rather than releasing it into an exposed or blocked space. [Bucket use][use] · [Mob release][release]

The released animal is marked **from a bucket**, which its distance-despawning checks respect. This is persistence, not ownership: it does not add follow/sit commands or tame the Platypus. [Release marker][release] · [Persistence checks][persistence]

## What the bucket preserves

The shared bucket helper writes **health**, a custom name, and basic flags such as No AI, silence, glowing, invulnerability, and no gravity. The release path reads that shared bucket data back. A custom name is also carried on the item. These are source-defined transfers, not results of a tested capture/release cycle. [Shared data][shared-data] · [Platypus name][name] · [Release][release]

Two limitations matter when moving a particular animal:

- **Age is not saved** by the Platypus's bucket method or the shared helper. Capturing a baby therefore does not establish a same-age baby on release
- **Special state uses mismatched data fields.** The Platypus writes sensing, egg-carrying, supercharged, fedora, and previous bucket-state flags into `CUSTOM_DATA`; the generic release code instead passes `BUCKET_ENTITY_DATA` to its loader. The special flags can consequently reset to their defaults on release. The release flow separately sets the new animal's from-bucket flag to true. The component mismatch is tracked in [issue #782](https://github.com/HungLo2020/MattMC/issues/782); this is a documented limitation, not a completed fix

Ordinary world saving uses a different path and does save those Platypus flags; that does not repair the bucket transfer. If an animal is already sensing or carrying an egg, keep it in its enclosure instead of relying on the bucket to preserve that progress. [Bucket save/load][platypus-data] · [Generic release data][release] · [World save/load][world-data]

## Related pages

- [Platypus](../mobs/Platypus.md)
- [Platypus Spawn Egg](PlatypusSpawnEgg.md)
- [Platypus Egg](PlatypusEgg.md)
- [Water Bucket](WaterBucket.md)
- [Items](Items.md)

## Verification scope

Source-reviewed at MattMC commit `b81c01943c9f3254e713c365a1dd633392929cb2` on 2026-10-01. Item registration, Creative availability, capture, placement, and data-transfer paths were inspected. No in-game capture/release, name, health, age, or special-state preservation test was run.

[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L1523-L1527
[platypus-data]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L119-L142
[interaction]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L144-L166
[capture]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L71-L88
[creative]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1454
[use]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L93
[release]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/MobBucketItem.java#L43-L54
[persistence]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L359-L367
[shared-data]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L33-L69
[name]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L109-L124
[world-data]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/alexsmobs/entity/EntityPlatypus.java#L325-L341

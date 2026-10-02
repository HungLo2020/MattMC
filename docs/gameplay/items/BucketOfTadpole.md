# Bucket of Tadpole

**Bucket of Tadpole** (`minecraft:tadpole_bucket`) carries one Tadpole and its saved growth progress. Move it to a suitable biome **before it becomes a Frog** if you want to choose the eventual [Frog variant](../mobs/Frog.md#variants-and-where-to-grow-tadpoles). The item has a stack limit of one. [Registration][item] · [Age save/load][tadpole]

## Capturing a tadpole

Use a **[Water Bucket](WaterBucket.md)** on a **living [Tadpole](../mobs/Tadpole.md)**. The active capture helper creates the filled mob bucket, saves the animal's data, replaces the held bucket, and removes the captured entity. An empty Bucket is not accepted by this capture check. [Tadpole interaction][tadpole] · [Capture helper][bucketable]

[Frogspawn](../blocks/Frogspawn.md) must hatch first. Adult Frogs do not use the Tadpole's bucket-capture path; transport the young while they are still Tadpoles. [Frog lifecycle](../mobs/Frog.md#breeding-and-laying-frogspawn)

## Releasing it safely

Use the bucket at a suitable water-placement position in an open water enclosure. Successful bucket use releases the Tadpole and leaves an empty Bucket in Survival. A dispenser uses the same fluid-placement and entity-release path for this item, returning an empty Bucket after success. If the dispenser cannot place the contents, it falls back to dispensing the filled item. [Bucket use][bucket] · [Mob release][mob-bucket] · [Dispenser wiring][dispenser]

**Do not empty it in the Nether expecting a safe pool.** In an ultrawarm dimension, the water-placement branch returns success after evaporation effects, and the mob-bucket path can still release the Tadpole without water. Tadpoles need water and take damage when kept out of it. [Ultrawarm branch][bucket] · [Release callback][mob-bucket] · [Air and water care][water-animal] · [Water Bucket restriction](WaterBucket.md#the-nether-restriction)

## What the bucket preserves

The paired save/load callbacks preserve the Tadpole's **age and health**; the standard entity-component path also carries its **custom name**. Supported special flags, such as No AI or invulnerability, are copied by the shared bucket helper. The age is stored in `BUCKET_ENTITY_DATA` and restored through the actual release path, so capturing it does not reset growth. [Tadpole age storage][tadpole] · [Shared bucket data][bucketable] · [Release data loading][mob-bucket] · [Item-component application][stack-config]

Growth advances while the Tadpole entity ticks; the stored age does not advance while it is an item in a bucket. After release, it continues toward adulthood from that saved age. The final Frog variant is selected at maturation from the **biome where it grows up**, rather than a variant stored by this bucket. [Growth and conversion][tadpole] · [Frog variant selection][frog]

Related: [Tadpole](../mobs/Tadpole.md) · [Frog](../mobs/Frog.md) · [Frogspawn](../blocks/Frogspawn.md) · [Bucket](Bucket.md) · [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `3e85592c4c78ebb420302360667a6c230dc0318d`. No in-game capture, release, growth preservation, dispenser, or Nether test was run. Data packs can change tags, variant selection, and loot; the values above describe bundled source behavior.

[item]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/Items.java#L1589-L1593
[tadpole]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Tadpole.java
[bucketable]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/Bucketable.java
[bucket]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/BucketItem.java#L37-L143
[mob-bucket]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/item/MobBucketItem.java
[dispenser]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L167-L189
[water-animal]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L40-L64
[stack-config]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1724
[frog]: https://github.com/HungLo2020/MattMC/blob/3e85592c4c78ebb420302360667a6c230dc0318d/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L296-L303

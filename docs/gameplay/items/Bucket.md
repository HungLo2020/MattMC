# Bucket

An empty **Bucket** (`minecraft:bucket`) carries a collected fluid or becomes the container for certain other resources. Empty buckets stack to **16**; ordinary Water and Lava Buckets stack to **one**. [Registration][items]

## Crafting

Craft one Bucket from **three [Iron Ingots](IronIngot.md)** arranged in a V:

```text
Iron . Iron
. Iron .
```

This is a shaped recipe requiring the three-wide Crafting Table grid. It is a verified crafting route, not an exhaustive list of chest or merchant sources. [Recipe][recipe]

## Collecting water or lava

Hold the empty bucket and use it on a **source block** of water or lava. The bucket's aiming check selects source fluids, and an ordinary liquid block only returns a filled bucket when its stored liquid level is zero. A visible flowing stream is not enough: target its source. Successful ordinary collection removes that source and provides the matching filled bucket. [Bucket interaction][bucket] · [Liquid pickup][liquid]

In Survival, collecting from a stack consumes one empty bucket. The filled item replaces an emptied stack or goes into inventory; if it cannot fit, it drops. Creative handles filled results differently, so the Survival inventory exchange is not a universal item-count rule. [Filled-result handling][result]

A full Water Cauldron can supply one Water Bucket; it must be at **level 3**. A Lava Cauldron can supply one Lava Bucket. The successful collection empties the cauldron. Partial water cauldrons do not satisfy the bucket-filling condition. [Cauldron interactions][cauldron]

Use [Water Bucket](WaterBucket.md) for placing water, waterlogging, and renewable source pools, or [Lava Bucket](LavaBucket.md) for placement and furnace fuel.

## Animals and other contents

An empty bucket used on a [Cow](../mobs/Cow.md) provides a [Milk Bucket](MilkBucket.md); milk is used through its drinking/crafting behavior rather than placed as a world fluid.

**Ordinary aquatic-mob collection uses a Water Bucket, not an empty bucket.** The shared bucketable-animal helper checks specifically for `minecraft:water_bucket` and a living animal, then saves its supported data into the result. Individual integrated species can have different data-handling limits, so use that animal's guide rather than assuming every similarly named bucket preserves everything. [Bucketable helper][bucketable] · [Trilocaris](../mobs/Trilocaris.md) · [Platypus](../mobs/Platypus.md)

A Dispenser containing empty buckets can also collect a compatible block immediately in front. It calls the block's pickup method; if collection fails, it falls back to ordinary item dispensing. This is block collection, not a general promise that a dispenser catches every bucketable animal. [Dispenser collection][dispenser]

## Sources and verification

Source-reviewed on 2026-10-01 at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No gameplay test of collection, cauldrons, animal transfer, or dispensers was run. Permissions, block states, components, and custom data can change particular interactions.

Related: [Water Bucket](WaterBucket.md) · [Lava Bucket](LavaBucket.md) · [Milk Bucket](MilkBucket.md) · [Items](Items.md)

[items]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1511-L1517
[recipe]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/recipe/crafting/bucket.json
[bucket]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BucketItem.java
[liquid]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/LiquidBlock.java#L203-L209
[result]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ItemUtils.java
[cauldron]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java
[bucketable]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L71-L88
[dispenser]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L190-L208

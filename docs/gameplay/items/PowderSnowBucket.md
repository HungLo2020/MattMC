# Powder Snow Bucket

A **Powder Snow Bucket** (`minecraft:powder_snow_bucket`) carries one placeable Powder Snow block and stacks to **one**. It is registered as a solid-block bucket, with different placement behavior from a Water Bucket. [Registration][items] · [Implementation][solid-bucket]

## Obtaining

Use an empty [Bucket](Bucket.md) on placed Powder Snow, or on a full Powder Snow [Cauldron](Cauldron.md). Powder Snow has no block loot even with Silk Touch, and the filled bucket has no crafting recipe. See the [Powder Snow guide](../blocks/Snow.md#powder-snow-buckets-and-collision) for collection and natural terrain examples. [Pickup][bucket] [powder] · [Full-cauldron route][cauldron-bucket] · [Empty block loot][loot-powder-snow]

## Usage

Successful Survival placement gives back an empty Bucket; Creative's infinite-materials path keeps the filled bucket. A [Dispenser](../blocks/DispenserAndDropper.md#some-verified-dispenser-actions) can place the block into empty space. [Placement][solid-bucket] [bucket] · [Dispenser behavior][dispenser]

Read [collision and Leather Boots](../blocks/Snow.md#powder-snow-buckets-and-collision) and [freezing protection](../blocks/Snow.md#powder-snow-freezing-and-fire) before crossing placed Powder Snow. This bucket does not place ordinary Snow layers or a solid Snow Block.

## Related pages

- [Snow and Powder Snow](../blocks/Snow.md), [Leather Boots](LeatherBoots.md), and [Items](Items.md)

## Sources and verification

Source-reviewed at `e87cde38c872d30ae86139bbee181937603af769` on 2026-10-02. No gameplay test was run.

[items]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/Items.java
[solid-bucket]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/SolidBucketItem.java
[bucket]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/item/BucketItem.java
[powder]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/world/level/block/PowderSnowBlock.java
[cauldron-bucket]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/core/cauldron/CauldronInteraction.java#L170-L185
[loot-powder-snow]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/resources/data/minecraft/loot_table/blocks/powder_snow.json
[dispenser]: https://github.com/HungLo2020/MattMC/blob/e87cde38c872d30ae86139bbee181937603af769/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java

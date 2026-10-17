# Bucket of Flying Fish

A **Bucket of Flying Fish** carries one [Flying Fish][fish] and water. Hand use releases the fish, but **its visual variant is rerolled rather than restored**. The item stacks to one. [Registration and defaults][fish-items] · [Species data][fish-bucket] · [Active reader][bucket-common] · [Release path][bucket-release]

## Obtaining

Use a [Water Bucket][water-bucket] on a **living Flying Fish**. In Survival, this replaces the held Water Bucket with the filled fish bucket. Capture removes the fish from the world. An empty Bucket does not capture it. [Species interaction][fish-interact] · [Top-level interaction][interact-dispatch] · [Capture helper][bucket-capture]

The item is also category-listed for MattMC's [inventory item browser][browser] in **Creative**. A plain browser bucket has empty bucket-entity data and no preset variant; releasing it initializes a fresh fish with one of the three appearances. The [Flying Fish guide][fish] covers the absence of a verified natural spawn route. [Category listing][fish-bucket-category] · [Item defaults][fish-items] · [Creation order][entity-create] · [Variant selection][fish-variants]

## Usage

Use the filled bucket at a suitable block face to empty its water and release the fish. It can place water into an eligible space or a block that accepts water; it does not require an existing pond. Prepare a contained pool first. On a successful use, **Survival returns an empty [Bucket][empty-bucket]**, while Creative retains the filled item. [Placement and item return][bucket-use] · [Water placement][bucket-placement] · [Fish creation and addition][bucket-release]

Avoid releasing it in the Nether or another ultrawarm dimension: the water evaporates, but that branch still reports success and the caller proceeds to release the fish. The fish can then run out of air on dry ground. [Evaporation branch][bucket-placement] · [Release caller][bucket-use] · [Air damage][water-air]

## Behavior

### Saved data and appearance

Capture saves health and common entity flags into `BUCKET_ENTITY_DATA`, and preserves a custom name as `CUSTOM_NAME`. The release path restores those values, marks the fish as bucket-origin and adds it to the server world. That flag prevents ordinary distance despawning. [Common save/load][bucket-common] · [Release dispatch][bucket-release] · [Name application][stack-config] · [Component handling][entity-components] · [Persistence][fish-persistence]

**The variant takes a different path.** Flying Fish writes `Variant` into `CUSTOM_DATA`, but it inherits the bucket reader that passes only `BUCKET_ENTITY_DATA` to its variant loader. Creation has already selected a random variant. Applying the generic custom-data component merely stores that component on the entity; it does not call the fish's variant setter. The released fish therefore keeps the new roll, which may happen to match its previous appearance. A plain Creative bucket also rolls a fresh variant. [Species writer and loader][fish-bucket] · [Inherited item reader][bucket-common] · [Release call][bucket-release] · [Initialization][entity-create] · [Variant roll][fish-variants] · [Generic component handling][entity-components]

### Dispensers

A [Dispenser][dispenser] **ejects this filled bucket as an item**; it does not pour the water or release the fish. Flying Fish Bucket is absent from the registered fluid-container behaviors, so the active lookup falls back to ordinary item dispensing. [Fluid registrations][fluid-dispenser] · [Behavior lookup][dispenser-dispatch] · [Fallback item ejection][dispenser-default]

## Notes

* Item ID: `minecraft:flying_fish_bucket`
* It uses the standard water-filled `MobBucketItem` registered for Flying Fish [Registration][fish-items]

Related: [Flying Fish][fish] · [Water Bucket][water-bucket] · [Items][items]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on 2026-10-03. Capture, active item release, default components, variant transport, persistence and Dispenser dispatch were traced. No runtime bucket test was run.

[fish]: ../mobs/FlyingFish.md
[fish-items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1871-L1876
[fish-bucket]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L262-L288
[bucket-common]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L77
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L52
[water-bucket]: WaterBucket.md
[fish-interact]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L304-L308
[interact-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Mob.java#L1053-L1111
[bucket-capture]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[browser]: ../mechanics/InventoryBrowser.md
[fish-bucket-category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1443
[entity-create]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1775
[fish-variants]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L290-L317
[empty-bucket]: Bucket.md
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L93
[bucket-placement]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L143
[water-air]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L39-L58
[stack-config]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1728
[entity-components]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L3926-L3964
[fish-persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityFlyingFish.java#L80-L86
[dispenser]: ../blocks/DispenserAndDropper.md
[fluid-dispenser]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L165-L189
[dispenser-dispatch]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L105-L116
[dispenser-default]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DefaultDispenseItemBehavior.java#L21-L47
[items]: Items.md

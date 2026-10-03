# Bucket of Mimic Octopus

A **Bucket of Mimic Octopus** carries one [Mimic Octopus][octopus] and water, and stacks to one. **Hand release works, but capture does not preserve taming, ownership, upgrades, age or held equipment.** Retrieve its held item before capture and expect to tame and upgrade the released octopus again. [Registration][items] · [Species bucket data][bucket-data] · [Common data][common-data] · [Release path][bucket-release]

## Obtaining

Use a [Water Bucket][water-bucket] on a **living, tamed Mimic Octopus**. This replaces the Water Bucket with the filled bucket in Survival and removes the octopus from the world. Wild octopuses cannot be captured this way; an empty Bucket does not work. Any player can use the capture interaction on a tame octopus because this branch does not check ownership. [Tame-only gate][bucket-gate] · [Capture helper][capture]

The filled bucket is registered but **absent from the checked Creative category entries and their generation helpers**. MattMC's [inventory item browser][browser] combines category display stacks rather than enumerating every registered item, so registration alone does not make this bucket available there. The [Mimic Octopus Spawn Egg][egg] is category-listed; Creative players can spawn an octopus, tame it and capture it. No bundled recipe, loot source or natural octopus spawn route was found. [Registration][items] · [Category definitions][categories] · [Generated entries][category-helpers] · [Category build][category-build] · [Browser assembly][browser-list] · [Egg listing][egg-category] · [Bundled data][data]

## Usage

Use the filled bucket at a suitable block face to place its water and release an octopus. An existing pond is not required: the ordinary bucket path can place water in an eligible space or a block that accepts it. Successful use returns an empty [Bucket][empty-bucket] in Survival; Creative retains the filled item. The active registered item is the standard `MobBucketItem`, whose release method **adds the new octopus to the server world**. [Use and return][bucket-use] · [Water placement][water-place] · [Actual release][bucket-release]

Prepare an enclosure with water and access to air. Mimic Octopus moisture and breathing are separate: this snapshot's ordinary octopus can drown with its eyes continuously submerged. Its [mob guide][octopus] explains that integration limit. In the Nether or another ultrawarm dimension, water evaporates but the bucket still proceeds to release the octopus on land. [Air handling][air] · [Breathing tag][breathers] · [Evaporation branch][water-place] · [Release caller][bucket-use]

## Behavior

### What capture preserves

Capture saves **health and common flags** (`NoAI`, `Silent`, `NoGravity`, `Glowing` and `Invulnerable`) in `BUCKET_ENTITY_DATA`, and a custom name in `CUSTOM_NAME`. The inherited bucket-item reader passes that bucket component to the octopus's loader. The loader restores the common values and sets moisture to **60,000 ticks**; normal stack-component application restores the name. Release then sets the bucket-origin flag, preventing ordinary distance despawning. [Octopus writer and loader][bucket-data] · [Common writer and reader][common-data] · [Stack setup][stack-config] · [Name application][components] · [Release order][bucket-release] · [Persistence][persistence]

### What capture loses

**This capture writer never saves the pet's owner/taming, combat upgrade, command/sitting state, form lock, mimic state/block, feeding counters, age or held item.** Those fields are written during ordinary world saves, but that save method is not the bucket writer. The registered item initializes a fresh octopus and restores only the bucket data described above. A captured baby therefore releases as an adult; a trained octopus releases untamed, without its upgrade or carried equipment. [World-save fields][world-save] · [Owner save][owner-save] · [Age save][age-save] · [Actual bucket writer][bucket-data] · [Creation order][create] · [Adult initialization][age] · [Release loader][bucket-release]

The released octopus still has bucket-origin persistence, but **it must be tamed again before a Water Bucket will capture it**. Sneaking with a Water Bucket does not avoid capture or give it the bucket as equipment: capture runs before the owner's item-transfer handler. Use an empty-hand sneak interaction to retrieve its held item before capture. [Persistence][persistence] · [Capture priority][bucket-gate] · [Item retrieval][commands]

### Dispensers

A [Dispenser][dispenser] **ejects this filled bucket as an item**. Mimic Octopus Bucket is absent from the fluid-container behavior registrations, so the active lookup uses ordinary item dispensing; it does not pour water or release the octopus. [Fluid registrations][dispenser-fluid] · [Behavior lookup][dispenser-lookup] · [Fallback ejection][dispenser-default]

## Notes

* Item ID: `minecraft:mimic_octopus_bucket`
* It is registered as a water-filled `MobBucketItem` for `minecraft:mimic_octopus` [Registration][items]

Related: [Mimic Octopus][octopus] · [Water Bucket][water-bucket] · [Items][item-index]

Source-reviewed at `2fff1ef19106350f806ddedd4fb3c3b4fbc44716` on 2026-10-03. The exact registered item, tame-only capture, common-component writer/reader, initialization, server-world addition, missing pet data, category assembly and Dispenser fallback were traced. No runtime capture/release or Dispenser test was run.

[octopus]: ../mobs/MimicOctopus.md
[items]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/Items.java#L1912-L1913
[bucket-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L214-L239
[common-data]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L77
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L52
[water-bucket]: WaterBucket.md
[bucket-gate]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L355-L360
[capture]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L76-L94
[browser]: ../mechanics/InventoryBrowser.md
[egg]: MimicOctopusSpawnEgg.md
[categories]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java
[category-helpers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2207-L2284
[category-build]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2286-L2320
[browser-list]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[egg-category]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2046
[data]: https://github.com/HungLo2020/MattMC/tree/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data
[empty-bucket]: Bucket.md
[bucket-use]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/BucketItem.java#L72-L93
[water-place]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L143
[air]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L443
[breathers]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[stack-config]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1728
[components]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/Entity.java#L3926-L3964
[persistence]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L727-L734
[world-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L170-L212
[owner-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/TamableAnimal.java#L53-L79
[age-save]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L106-L118
[create]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/EntityType.java#L1750-L1775
[age]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/entity/AgeableMob.java#L30-L48
[commands]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/alexsmobs/entity/EntityMimicOctopus.java#L383-L417
[dispenser]: ../blocks/DispenserAndDropper.md
[dispenser-fluid]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L165-L189
[dispenser-lookup]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/world/level/block/DispenserBlock.java#L105-L116
[dispenser-default]: https://github.com/HungLo2020/MattMC/blob/2fff1ef19106350f806ddedd4fb3c3b4fbc44716/src/main/java/net/minecraft/core/dispenser/DefaultDispenseItemBehavior.java#L21-L47
[item-index]: Items.md

# Trilocaris

The **Trilocaris** is a small aquatic mob from bundled Alex's Caves content. It swims and crawls, retaliates when hurt, and can be carried in a water bucket. Its target selector only adds retaliation, rather than a goal to hunt nearby players. [Behavior][behavior]

## At a glance

- **Health:** 10 points (5 hearts)
- **Base attack damage:** 1 point (half a heart), before combat modifiers
- **Size:** 0.8 blocks wide and 0.4 blocks tall
- **Entity ID:** `minecraft:trilocaris`

[Attributes][attributes] · [Entity registration][registration]

## Obtaining

For Creative testing, use the [Trilocaris Spawn Egg](../items/TrilocarisSpawnEgg.md), a [Bucket of Trilocaris](../items/BucketOfTrilocaris.md), or `/summon minecraft:trilocaris` with command permission. The bucket is registered to release this entity into water. [Spawn egg][egg] · [Bucket item][bucket-item]

**Natural availability is not established in this snapshot.** The entity contains a cave-water spawn test: full water, with the top of the water column below sea level and no view of the sky. However, no call registering that test or trilocaris entry in the bundled biome spawn tables was found. This test alone does not mean the mob naturally spawns in a particular cave biome. [Spawn test][spawn] · [Spawn placements][placements] · [Biome data][biomes]

## Collecting and keeping one

Use a **[Water Bucket](../items/WaterBucket.md)** on a living trilocaris to collect it. An empty bucket does not satisfy the pickup check. Its custom name and health are saved into the bucket and restored when released. [Pickup and saved data][pickup] · [Release][release]

A released trilocaris is marked as coming from a bucket, which prevents its normal distance-based despawning. A custom-named trilocaris also declines distance-based removal. Capture and release is therefore useful when keeping one in an aquarium. [Persistence][persistence]

**Keep it in water.** Although its movement can take it onto land, the current inherited water-animal air routine still removes air and causes damage out of water. The trilocaris's separate one-argument air method does not replace that active routine. No taming or breeding interaction is implemented in this class. [Water-animal air handling][air] · [Trilocaris air method][attributes] · [Interactions][interactions]

## Drops and uses

- Its bundled loot table yields **0–1 [Trilocaris Tail](../items/TrilocarisTail.md)**. The table has no Looting count bonus. If the mob is on fire, the tail is smelted into a [Cooked Trilocaris Tail](../items/CookedTrilocarisTail.md). [Loot table][loot]
- A raw tail can be cooked in a furnace, smoker, or campfire. Each recipe produces one cooked tail; their cooking times are 200, 100, and 600 ticks respectively. [Furnace][furnace] · [Smoker][smoker] · [Campfire][campfire]
- Raw and cooked tails are both accepted for taming a [Subterranodon](Subterranodon.md). [Taming interaction][tail-use]

## Verification scope

Checked against MattMC source and bundled data at commit `fffe4a073f0b8d867902b067a6dd022cda31926f`. Natural spawning, bucket persistence, combat, and loot were not tested in a running world; the integration limits above should be considered when planning survival use.

[behavior]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexscaves/server/entity/living/TrilocarisEntity.java#L66-L72
[attributes]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexscaves/server/entity/living/TrilocarisEntity.java#L98-L108
[registration]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/EntityType.java#L1432-L1438
[egg]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/item/Items.java#L1986
[bucket-item]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/item/Items.java#L1564-L1568
[spawn]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexscaves/server/entity/living/TrilocarisEntity.java#L74-L84
[placements]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[biomes]: https://github.com/HungLo2020/MattMC/tree/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/worldgen/biome
[pickup]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L33-L89
[release]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/item/MobBucketItem.java#L43-L54
[persistence]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexscaves/server/entity/living/TrilocarisEntity.java#L228-L244
[air]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L37-L56
[interactions]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexscaves/server/entity/living/TrilocarisEntity.java#L195-L226
[loot]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/loot_table/entities/trilocaris.json
[furnace]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/cooked_trilocaris_tail_smelting.json
[smoker]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/cooked_trilocaris_tail_smoking.json
[campfire]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/recipe/cooked_trilocaris_tail_campfire.json
[tail-use]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/alexscaves/server/entity/living/SubterranodonEntity.java#L507-L523

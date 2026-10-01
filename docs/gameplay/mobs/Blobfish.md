# Blobfish

Blobfish is a small passive aquatic mob integrated from Alex's Mobs. It swims near the bottom, panics rather than hunting players, and can be collected with a water bucket. Keep it in water: the imported slime interaction does **not establish protection from out-of-water damage** in this build.

## At a glance

- Entity ID: `minecraft:blobfish`
- Health: **3 points** (1.5 hearts)
- Base registered size: **0.7 × 0.45 blocks**, scaled by the individual fish's size value
- Spawn initialization chooses a scale from 0.75 up to, but not including, 1.25

## Obtaining and keeping one

Creative provides a [Blobfish Spawn Egg](../items/BlobfishSpawnEgg.md) and [Bucket of Blobfish](../items/BucketOfBlobfish.md). Natural spawning is **not established**: the class contains a water/depth test, but no active registration of that test or blobfish biome-spawn entry was found in the checked sources. Do not treat the standalone depth check as a reliable fishing-ground guide.

Use a [Water Bucket](../items/WaterBucket.md) on a living blobfish to collect it. An empty bucket is not sufficient. Releasing a bucket marks the fish as bucket-origin and prevents its normal distance-based removal. A custom name also prevents distance-based removal.

## Pressure appearance and slime

The fish checks ten block positions beginning at its own position and continuing upward. Each must contain water or be solid for the clearance test to pass; otherwise it sets its depressurized state. This is a simplified source test, not a requirement to find a specific real-world depth or biome.

Use a [Slimeball](../items/Slimeball.md) on a living, not-yet-slimed blobfish to set its slimed state and consume one slimeball. However, the custom one-argument air routine is not the two-argument method called by the current water-animal superclass. The active inherited routine still removes air and damages it out of water. **Do not use slime as permission to keep it on land.**

Bucket transport also has a data mismatch: health is saved into bucket entity data, while scale and slime are written to a different component than the registered bucket reads. Their preservation through capture/release is not reliable; see the bucket article.

## Drops and other interactions

No dedicated blobfish entity loot table was found in the bundled entity loot directory. The separately registered [Blobfish food item](../items/Blobfish.md) therefore is not assumed to be a verified death drop. No breeding or taming interaction is established for this mob.

## Related pages

- [Bucket of Blobfish](../items/BucketOfBlobfish.md)
- [Blobfish food item](../items/Blobfish.md)
- [Trilocaris](Trilocaris.md)
- [Mobs](Mobs.md)

## Sources and verification

Reviewed against `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source-defined behavior only; no in-game capture, breeding, combat, or persistence test.

- [Mob implementation](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityBlobfish.java)
- [Active attribute registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L130)
- [Entity registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L322-L328)
- [Actual air-handling method](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java#L37-L56)
- [Bucket registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1579-L1583)
- [Bucket release data](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/MobBucketItem.java)
- [Spawn registrations](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/SpawnPlacements.java)

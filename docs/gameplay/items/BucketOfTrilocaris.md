# Bucket of Trilocaris

Bucket of Trilocaris carries a live [Trilocaris](../mobs/Trilocaris.md), an aquatic Alex's Caves mob. Its ID is `minecraft:trilocaris_bucket`, and it stacks to **one**.

## Capturing

Interact with a living Trilocaris while holding a **Water Bucket**. An empty bucket does not satisfy the pickup check. The game creates a filled bucket, saves the animal's default bucket data, and removes the original live entity.

The standard saved data includes custom name, health, and special entity flags when present. This is a capture-and-release interaction, not a taming or breeding action.

## Releasing and keeping

Use the filled bucket to release the creature in a suitable water habitat. The item is registered with water and the Trilocaris entity type. Its standard release path loads the bucket entity data and sets the fish's from-bucket flag.

That flag prevents the Trilocaris's normal distance-based removal. This makes capture and release useful for an aquarium, but it does not protect the creature from damage or unsafe terrain. Keep it in water; the current inherited air routine still damages it on land.

Creative also lists the bucket. Its existence does not establish natural Trilocaris spawning in Survival; see the mob page's availability caveat.

## Related pages

- [Trilocaris](../mobs/Trilocaris.md)
- [Trilocaris Spawn Egg](TrilocarisSpawnEgg.md)
- [Items](Items.md)

## Sources and verification

Reviewed against `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source-defined behavior only; no in-game capture, breeding, combat, or persistence test.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1564-L1568)
- [Capture and default data](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/Bucketable.java)
- [Release](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/MobBucketItem.java)
- [Trilocaris interactions and persistence](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexscaves/server/entity/living/TrilocarisEntity.java#L195-L244)
- [Air handling](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/WaterAnimal.java)

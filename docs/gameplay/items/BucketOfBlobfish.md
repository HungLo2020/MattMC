# Bucket of Blobfish

Bucket of Blobfish carries a live [Blobfish](../mobs/Blobfish.md). It is registered as `minecraft:blobfish_bucket`, stacks to **one**, and uses the standard water-filled mob-bucket implementation.

## Capturing and releasing

Use a **Water Bucket**, not an empty bucket, on a living blobfish. The pickup creates the filled item and removes that live entity from the world. The fish's name and health are recorded by the default bucket save path.

Use the filled bucket in a suitable water habitat to release the fish. The registered bucket reads its saved bucket entity data and marks the new fish as coming from a bucket, preventing normal distance-based despawning. Water placement and permissions still follow the normal bucket rules.

## Important saved-data limit

This fish writes its custom size and slimed flag into `CUSTOM_DATA`, while its registered standard bucket passes `BUCKET_ENTITY_DATA` into the load routine. Those custom values therefore are not established as surviving capture and release. Name and health use the standard path, but do not assume the fish retains its exact size or slime coating.

Keep the released fish submerged. The [mob page](../mobs/Blobfish.md#pressure-appearance-and-slime) explains why slime is not dependable protection from air loss on land.

## Related pages

- [Blobfish](../mobs/Blobfish.md)
- [Blobfish Spawn Egg](BlobfishSpawnEgg.md)
- [Items](Items.md)

## Sources and verification

Reviewed against `9bd57e1d0057903f6a9196e592d5e2a087c9248a` on 2026-10-01. Source-defined behavior only; no in-game capture, breeding, combat, or persistence test.

- [Item registration](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/Items.java#L1579-L1583)
- [Fish bucket save/load](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/alexsmobs/entity/EntityBlobfish.java#L236-L268)
- [Default capture and save](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/animal/Bucketable.java)
- [Standard release](https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/item/MobBucketItem.java)

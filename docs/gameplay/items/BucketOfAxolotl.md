# Bucket of Axolotl

A **Bucket of Axolotl** carries a living [Axolotl](../mobs/Axolotl.md) and releases it together with the bucket's water. It is registered as `minecraft:axolotl_bucket` and stacks to **one**.

## Obtaining

Use a [Water Bucket](WaterBucket.md) on a **living axolotl**. The water bucket becomes a Bucket of Axolotl and the captured creature is removed from the world. This also works on an axolotl out of water. An [empty Bucket](Bucket.md) is not sufficient.

Capture is the reviewed Survival acquisition route. There is no bundled crafting recipe or loot-table award for this item. Creative players can obtain it from the Creative inventory.

## Releasing the axolotl

Use the filled bucket on a suitable block face where its water can be placed. A successful use places water or fills a compatible water-containing block, then releases the axolotl. In Survival it returns an **empty Bucket**. A dispenser can also empty the bucket into its facing position and release the axolotl; if the placement fails, it uses the ordinary item-dispensing fallback.

Choose an open water enclosure with room for the animal. A successful fluid interaction is not a guarantee that the release spot is a good habitat. In particular, **water evaporates in an ultrawarm dimension such as the Nether, but the successful bucket-use path still releases the axolotl**. Do not empty the bucket there expecting it to create a safe pool.

The released axolotl is marked as having come from a bucket. That protects it from ordinary distance despawning; it does not protect it from damage or drying out.

## What the bucket preserves

Capturing and releasing transfers:

- **Variant**, including blue
- **Age**, including a baby's remaining growth time or an adult's breeding cooldown
- **Health** and **custom name**
- The remaining **hunting cooldown**, if one is active
- Special entity flags for no AI, silence, no gravity, glowing, and invulnerability when present

This preserves an injured axolotl's health rather than healing it. It is a selected set of stored values, not a copy of every live state: the bucket does not save ordinary active potion effects, the current attack target, or playing-dead progress.

For data-aware mapmaking, the variant uses the `minecraft:axolotl/variant` item component with the string values `lucy`, `wild`, `gold`, `cyan`, or `blue`. Age, health, and hunting cooldown are in the separate `minecraft:bucket_entity_data` component. The item's variant component is distinct from the legacy numeric `Variant` field used when saving the live entity.

## Related pages

- [Axolotl care, breeding, colors, and combat support](../mobs/Axolotl.md)
- [Bucket of Tropical Fish](BucketOfTropicalFish.md), the axolotl's food
- [Water Bucket](WaterBucket.md)
- [Items](Items.md)

## Sources and verification

Source-reviewed on **2026-10-01** at commit `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game capture/release, dispenser, data-component, or Nether test was run. The preservation claims trace both the save and load paths in active source.

- [Item registration, water content, and stack limit](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1574-L1578); [Creative entry](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1449)
- [Capture and shared name/health/flag storage](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/Bucketable.java); [axolotl-specific save/load and persistence](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/axolotl/Axolotl.java)
- [Hand use, empty-bucket return, and water evaporation](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/BucketItem.java); [creature-release callback](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/MobBucketItem.java); [dispenser callback and registration](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L165-L189)
- [Variant and bucket-data codecs](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/core/component/DataComponents.java); [item-component application during entity creation](https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/EntityType.java#L1710-L1776)

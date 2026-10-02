# Bucket of Cosmic Cod

A **Bucket of Cosmic Cod** (`minecraft:cosmic_cod_bucket`) carries a [Cosmic Cod](../mobs/CosmicCod.md). Capture uses an **empty Bucket**, while release currently places water through the standard mob-bucket implementation. That mismatch matters because Cosmic Cod are harmed by water and rain. [Capture][capture] · [Registered item][item] · [Water sensitivity][sensitive] · [Water damage][water-damage]

## Obtaining

Use an **empty Bucket** on a living Cosmic Cod. Its interaction creates the filled item, saves supported entity data and removes the captured mob. A Water Bucket is not accepted by this capture route. [Capture interaction][capture]

The filled bucket is also an ordinary category-listed item, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can supply it in **Survival as well as Creative**. This does not establish a crafting recipe, loot source or natural Cosmic Cod habitat. [Category entry][category]

## Usage

Use the filled bucket at a valid bucket-placement location. After successful fluid handling, the item creates a Cosmic Cod with the bucket spawn reason and marks it as released from a bucket. In ordinary Survival use it returns an empty Bucket; with infinite materials the filled item is retained. The filled bucket stacks to **one**. [Fluid and item registration][item] · [Use and returned item][use] · [Mob creation][release]

## Behavior

**Water is a hazard to the released cod.** This item is registered with water, not an empty fluid, and follows the normal water-placement path. Cosmic Cod explicitly report water sensitivity and the living-entity tick applies damage while in water or rain. Release is therefore not a verified safe way to place one in an aquarium. For a browser-supplied cod, the [spawn egg](CosmicCodSpawnEgg.md) used in dry space avoids adding water. [Registration][item] · [Water placement][water] · [Sensitivity][sensitive] · [Active damage][water-damage]

The common bucket data preserves health and supported flags such as no-AI, silence, gravity, glow and invulnerability, and the item carries the creature's custom name. Release loads the standard bucket-data component. The cod separately writes its old `FromBucket` value into custom data, which this standard loader does not read; the release code explicitly sets `FromBucket` to true anyway. Do not infer preservation of a school leader, formation or teleport countdown. [Capture data][save] · [Standard data][bucket-data] · [Loading and release marker][release]

A released cod or a named cod will not use ordinary distance-based despawning. This is persistence, not protection from damage. [Persistence checks][persistence]

## Notes

- The item points to the actual `minecraft:cosmic_cod` entity factory. [Item registration][item] · [Entity registration][entity]
- Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. No capture, release, water-damage, data-round-trip or inventory-use test was run.

Related: [Cosmic Cod](../mobs/CosmicCod.md) · [Cosmic Cod Spawn Egg](CosmicCodSpawnEgg.md) · [Cosmaw](../mobs/Cosmaw.md#feeding-and-taming-missing-item-dependency) · [Items](Items.md)

[capture]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L405-L425
[item]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/Items.java#L1832-L1836
[sensitive]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L166-L168
[water-damage]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2865-L2867
[category]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1441
[use]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/BucketItem.java#L73-L92
[release]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/MobBucketItem.java#L41-L50
[water]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/BucketItem.java#L100-L141
[save]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L118-L145
[bucket-data]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/animal/Bucketable.java#L29-L74
[persistence]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/alexsmobs/entity/EntityCosmicCod.java#L148-L154
[entity]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L394-L400

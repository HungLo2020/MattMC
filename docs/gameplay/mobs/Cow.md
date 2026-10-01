# Cow

The **Cow** is a passive farm animal that provides milk, leather, and beef. It has **10 health points (5 hearts)**, follows players holding its food, and uses a panic goal rather than an attack goal. [Behavior and health][cow-behavior]

## Obtaining

Look for cows in biomes with farm-animal spawns. For example, the bundled **plains** biome lists cows in groups of four. Their normal ground-spawn check requires a block in the animal-spawnable tag below them and brightness above 8; that tag currently contains **grass blocks**. These are placement conditions, not a guarantee that every suitable patch will spawn cows. [Plains spawn table][plains] · [Spawn registration][placement] · [Spawn conditions][spawn] · [Ground tag][ground]

In Creative, use the [Cow Spawn Egg](../items/CowSpawnEgg.md). With command permission, `/summon minecraft:cow` creates one. The registered adult size is **0.9 blocks wide × 1.4 blocks tall**. [Entity registration][registration]

## Farming and breeding

1. Hold **[Wheat](../items/Wheat.md)** to lead cows into an enclosure. Wheat is the only item in the bundled cow-food tag.
2. Feed two ready adult cows one wheat each to start breeding. The parents have a **6,000-tick cooldown**, about 5 minutes at 20 ticks per second.
3. A newborn calf normally takes **24,000 ticking game ticks**, about 20 minutes, to mature. Feeding it wheat advances growth by approximately 10% of the remaining time per feeding, with whole-second rounding.

Calves follow adults, and ordinary cows do not despawn just because the player moves far away. Breeding and growth timers need the animals to be ticking. [Cow food][food] · [Feeding and breeding][breeding] · [Growth timer][growth] · [Following][cow-behavior] · [Persistence][persistence]

## Milking

Use an **empty bucket on an adult cow** to receive a [Milk Bucket](../items/MilkBucket.md). Calves cannot be milked. The interaction does not consume the cow and has no milking cooldown, so one adult can fill repeated buckets. [Milking interaction][milk]

## Drops

With mob loot enabled, an adult cow's base drops are:

- **0–2 [Leather](../items/Leather.md)**
- **1–3 [Raw Beef](../items/RawBeef.md)**, changed to [Steak](../items/Steak.md) if the cow is on fire or the direct attacker's main-hand item satisfies the smelts-loot enchantment condition

Looting adds a count bonus to each of those drops. Calves do not drop normal mob loot or experience. A qualifying player kill of an adult has a base reward of **1–3 experience**; breeding awards **1–7 experience** when mob loot is enabled. [Loot table][loot] · [Baby loot restriction][baby-loot] · [Experience conditions][experience] · [Animal rewards][rewards]

## Appearance variants

Cows have **temperate, warm, and cold** variants. The warm and cold variants are selected by their biome tags at spawning, with temperate as the fallback. A bred calf randomly inherits one parent's variant. These appearance rules do not themselves add cow spawns to a biome. [Variant definitions][variants] · [Warm biomes][warm] · [Cold biomes][cold] · [Variant inheritance][inheritance]

## Verification scope

Checked against MattMC source and bundled data at commit `fffe4a073f0b8d867902b067a6dd022cda31926f`. These values describe the bundled rules; data packs can change tags, loot, and biome data. No in-game farm or timed growth test was run for this page.

[cow-behavior]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/animal/AbstractCow.java#L37-L56
[plains]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/worldgen/biome/plains.json#L120-L125
[placement]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L114
[spawn]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L105-L114
[ground]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/tags/block/animals_spawnable_on.json
[registration]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/EntityType.java#L452-L454
[food]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/tags/item/cow_food.json
[breeding]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L228
[growth]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[persistence]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[milk]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/animal/AbstractCow.java#L83-L94
[loot]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/loot_table/entities/cow.json
[baby-loot]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[experience]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1480-L1487
[rewards]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/animal/Animal.java#L127-L129
[variants]: https://github.com/HungLo2020/MattMC/tree/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/cow_variant
[warm]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_warm_variant_farm_animals.json
[cold]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/resources/data/minecraft/tags/worldgen/biome/spawns_cold_variant_farm_animals.json
[inheritance]: https://github.com/HungLo2020/MattMC/blob/fffe4a073f0b8d867902b067a6dd022cda31926f/src/main/java/net/minecraft/world/entity/animal/Cow.java#L49-L66

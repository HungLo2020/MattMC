# Skeleton

The **Skeleton** is a hostile archer and a source of [Bones](../items/Bone.md) and [Arrows](../items/Arrow.md). Use solid cover to interrupt its shots, and watch for skeletons that survive daylight under shelter or headgear. An ordinary skeleton has **20 health points (10 hearts)** and a registered body size of **0.6 blocks wide × 1.99 blocks tall**. [Behavior][behavior] · [Health default][attributes] · [Attribute inheritance][living] · [Registration][registration]

## Where to find it

Confirmed bundled spawn routes include:

- **Overworld monster spawns:** plains and forest list groups of four. Snowy plains also contain ordinary skeletons, alongside the more heavily weighted [Stray](Stray.md) entry; swamps contain both skeletons and [Bogged](Bogged.md).
- **Soul Sand Valley:** its Nether biome table lists skeleton groups of five.
- **Nether fortresses:** their special monster list includes ordinary skeletons as well as [Wither Skeletons](WitherSkeleton.md).

Deep Dark and Mushroom Fields have no ordinary skeleton entry in their normal biome spawn tables. An existing skeleton can still enter those biomes. [Plains][plains] · [Forest][forest] · [Snowy Plains][snowy] · [Swamp][swamp] · [Soul Sand Valley][valley] · [Fortress list][fortress] · [Fortress spawn selection][fortress-spawning] · [Deep Dark][deep-dark] · [Mushroom Fields][mushroom-fields]

The normal ground-spawn check requires a difficulty other than Peaceful, suitable ground, and the dimension's monster-light checks. In the bundled Overworld, **block light must be 0**, with additional sky/local-light checks. Nether light settings differ, so do not apply the Overworld block-light requirement to a Soul Sand Valley. A spawn-table group size is not a guarantee of that many mobs appearing. [Spawn registration][placement] · [Monster checks][monster] · [Ground check][mob] · [Overworld settings][overworld] · [Nether settings][nether]

In Creative, use the [Skeleton Spawn Egg](../items/SkeletonSpawnEgg.md). With command permission, use `/summon minecraft:skeleton`.

## Fighting and staying safe

Skeletons normally equip a **bow**. They target players, iron golems, and baby turtles on land, and can retaliate when hurt. Without a bow, their combat AI switches to melee. Some can pick up equipment, depending on local difficulty. [Equipment and targets][behavior]

- **Break line of sight.** A skeleton releases its bow shot only while it sees its target. If you remain out of sight it may move toward you, so use cover to reposition rather than assuming it has forgotten you.
- **Expect strafing.** Within its **15-block combat radius**, after maintaining sight for 20 ticks, it can stop pathfinding and move sideways or backward while aiming. This radius is an AI movement threshold, not a maximum arrow-travel distance.
- **Hard difficulty gives less breathing room.** The shot cooldown is **20 ticks on Hard**, versus **40 ticks otherwise**. A shot also requires at least **20 ticks of bow draw**, so the cooldown alone is not the full time between arrows.
- **Wolves can drive it away.** Skeletons have a wolf-avoidance goal within **6 blocks**.

Arrow damage is not a single fixed health value: the projectile code incorporates difficulty, random variation, and projectile motion. These are code-grounded tactics, not measured combat results. [Bow movement and timing][bow-ai] · [Difficulty and wolf avoidance][behavior] · [Arrow damage][arrow]

## Daylight and equipment

An exposed skeleton can catch fire in bright daylight and tries to find shade. The burn check excludes water/rain and powder snow. **Any nonempty head slot prevents that sun-burn trigger**; damageable headgear gradually takes damage and can break. A sheltered or helmeted skeleton can therefore remain dangerous during the day. [Daylight behavior][behavior] · [Sun-burn conditions][sun]

On **October 31 according to the server's local calendar**, a newly finalized skeleton with an empty head slot has a **25% chance** to receive a seasonal headpiece. Of those headpieces, 90% are carved pumpkins and 10% are jack o'lanterns. The seasonal item is assigned a **zero drop chance**. [Seasonal equipment][behavior]

## Powder-snow conversion

An ordinary skeleton held in powder snow begins converting after roughly **7 seconds**, then takes roughly **15 more seconds** to become a [Stray](Stray.md), assuming 20 TPS and continuous ticking. The code uses a 140-tick exposure threshold and a 300-tick conversion counter, completing when that counter falls below zero. It shakes during conversion and does not take ordinary freezing damage. **Leaving powder snow cancels conversion and resets exposure.** The timer only advances while its AI is enabled. [Conversion implementation][conversion]

## Drops

With mob loot enabled, the normal loot table rolls:

- **0–2 arrows**
- **0–2 bones**

Looting increases each drop's possible maximum by one per level, reaching **5 arrows and 5 bones with Looting III**. The two drops are separate rolls, and neither has a fire-based replacement. [Loot table][loot]

Naturally equipped bows and armor use separate equipment-drop rules: the ordinary base chance is **8.5% per equipped slot** on a qualifying player-attributed kill, before enchantment changes. Dropped damageable equipment can be heavily worn. Seasonal headpieces are excluded as described above. A qualifying kill has a base reward of **5 experience**, with additional experience possible from qualifying equipment. [Equipment rules][equipment] · [Default chance][drop-chances] · [Base experience][monster] · [Equipment experience][equipment-xp] · [Reward conditions][experience]

A skeleton killed by a **charged creeper** can drop a [Skeleton Skull](../items/SkeletonSkull.md), subject to the charged creeper's one-special-head limit. A skeleton can also supply the killing hit needed for a creeper's music-disc drop; see [Creeper](Creeper.md). [Skull table][skull] · [Head limit][creeper]

## Verification scope

Source-reviewed on **2026-10-01** against active MattMC code and bundled data at commit `9bd57e1d0057903f6a9196e592d5e2a087c9248a`. No in-game combat, spawn-rate, seasonal-equipment, or conversion test was run for this page. Data packs can change biome tables and loot; equipment, effects, difficulty, and entity data can change individual encounters.

Related: [Stray](Stray.md) · [Bogged](Bogged.md) · [Wither Skeleton](WitherSkeleton.md) · [Skeleton Horse](SkeletonHorse.md) · [Creeper](Creeper.md) · [Mobs](Mobs.md)

[behavior]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L57-L59
[living]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L326-L343
[registration]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/EntityType.java#L1208-L1212
[plains]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/plains.json
[forest]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/forest.json
[snowy]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/snowy_plains.json
[swamp]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/swamp.json
[valley]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/soul_sand_valley.json
[fortress]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/structure/fortress.json
[deep-dark]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/deep_dark.json
[mushroom-fields]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json
[placement]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L143
[monster]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/monster/Monster.java
[mob]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/Mob.java#L728-L740
[overworld]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/overworld.json
[nether]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/dimension_type/the_nether.json
[bow-ai]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/ai/goal/RangedBowAttackGoal.java
[arrow]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java
[sun]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/Mob.java#L1326-L1339
[conversion]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/monster/Skeleton.java
[loot]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/entities/skeleton.json
[equipment]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/Mob.java#L814-L844
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/DropChances.java
[equipment-xp]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/Mob.java#L290-L306
[experience]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1488
[skull]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/resources/data/minecraft/loot_table/charged_creeper/skeleton.json
[creeper]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/entity/monster/Creeper.java
[fortress-spawning]: https://github.com/HungLo2020/MattMC/blob/9bd57e1d0057903f6a9196e592d5e2a087c9248a/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L340

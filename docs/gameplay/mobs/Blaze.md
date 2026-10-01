# Blaze

**Blazes** (`minecraft:blaze`) are fire-immune hostile mobs found in [Nether Fortresses](../structures/NetherFortress.md). Hunt them for [Blaze Rods](../items/BlazeRod.md), but prepare cover before approaching: they can shoot in bursts and still strike at close range. Their default health is **20 points (10 hearts)**, and their registered size is **0.6 blocks wide × 1.8 blocks tall**. [Behavior and attributes][blaze] · [Active attribute registration][defaults] · [Health default][health] · [Entity registration][registration]

## Finding Blazes

Fortresses supply two confirmed encounter routes:

- **Natural fortress monsters:** Blazes are in the fortress selection with listed groups of **2–3**. A structure override applies inside fortress pieces, and a separate natural-spawn route uses that selection above **Nether Bricks inside a recognized fortress**. Nether Bricks placed elsewhere do not create a fortress
- **Blaze spawners:** an optional monster-throne piece places a spawner configured for Blazes. The piece is not guaranteed in every generated fortress

[Fortress spawn data][fortress] · [Fortress enemy list][fortress-enemies] · [Piece-bounded selection][piece-spawns] · [Nether-brick route][natural] · [Spawner piece][pieces]

### Light, ground, and difficulty

**Blaze spawning has no darkness requirement in this source snapshot.** Its registered predicate is the any-light monster check, which requires a difficulty other than Peaceful. Natural spawning also checks a valid supporting block, empty spawn space, and obstruction conditions. Do not assume a light-level cutoff from another game version. [Spawn registration][spawn] · [Any-light predicate][monster] · [Ground placement][ground] · [Support and obstruction checks][mob-spawn]

The generated spawner uses default spawn data. It activates near a living non-spectator player within its default **16-block range**, subject to the server's spawner setting. It checks space and nearby-mob limits, but its Blaze predicate still has **no light check**, and the spawner spawn reason bypasses the supporting-floor check. **Torches alone do not disable this bundled Blaze spawner.** This is not a validated farm or containment design. [Spawner defaults and checks][spawner] · [Player activation check][near-player] · [Spawn data defaults][spawn-data] · [Spawner tick dispatch][spawner-tick] · [Mob support predicate][mob-spawn]

## Attacks and useful defenses

Blazes target players and retaliate when hurt, alerting nearby members through their retaliation goal. When a visible target is within their **48-block follow range**, their uninterrupted ranged cycle has a **60-tick charge**, then **three fireballs 6 ticks apart**, followed by a **100-tick reset cooldown**. At 20 TPS, those intervals are 3 seconds, 0.3 seconds, and 5 seconds. Losing sight or moving into melee range changes that sequence; the range is not a guarantee that every player within 48 blocks is detected. [Targeting, attributes, and attack goal][blaze]

At less than **2 blocks** of entity-position distance and with sight of the target, the attack goal can switch to melee, using a **20-tick cooldown** and an ordinary base attack value of **6 health points** before difficulty and defenses. Closing the gap does not make the Blaze harmless. [Melee branch][attack] · [Melee damage dispatch][mob-attack] · [Melee damage type][mob-damage-type] · [Player difficulty scaling][player-damage]

A small-fireball hit attempts **5 health points of direct damage** before difficulty and defenses, and **5 seconds of ignition**. If the hit is rejected, it restores the target's previous fire timer. A block impact can place fire in the adjacent empty block when `mobGriefing` allows the mob-owned projectile to do so; the small fireball itself does not create an explosion. [Small-fireball collision][fireball] · [Fireball damage type][damage-type] · [Difficulty scaling][player-damage]

Practical options grounded in those attacks:

- **Use solid cover and corners.** Ranged attacks need line of sight; break that sight while repositioning instead of remaining exposed through a volley
- **Bring Fire Resistance if available.** The bundled fireball damage is fire-tagged, so Fire Resistance rejects it along with ordinary burning damage. It does **not** stop the Blaze's separate melee attack. See [Brewing](../brewing/Brewing.md) for the potion route
- **Snowballs are a damaging option:** a direct snowball hit attempts **3 damage** specifically against a Blaze
- **Do not rely on lava or a fall to kill it.** Blazes are registered fire-immune and belong to the fall-damage-immune tag

[Attack visibility][attack] · [Fire damage tag][fire-tag] · [Fire Resistance handling][resistance] · [Snowball hit][snowball] · [Fire immunity][registration] · [Fall immunity tag][fall-tag]

Blazes are sensitive to water and rain, and a nearby splash water-potion impact can attempt **1 damage** to them. However, an ordinary water bucket is not a dependable Nether combat plan: read the [Nether's water-placement restriction](../dimensions/Nether.md#hazards-to-plan-around). [Water sensitivity][blaze] · [Water/rain damage][water] · [Water-potion splash][splash]

## Drops and experience

The bundled rod pool requires a **player-attributed kill** and mob loot enabled. It gives **0–1 Blaze Rod**, with a Looting count bonus up to a possible **4 rods at Looting III**. A qualifying kill can still give no rod. Environmental killing alone does not meet the rod pool's player condition. [Rod loot][loot] · [Looting count][looting] · [Mob-loot gate][monster]

A qualifying player-attributed kill has a **base reward of 10 experience**, subject to the ordinary XP-drop conditions. Keep the [Blaze Rod guide](../items/BlazeRod.md) handy for powder, brewing stands, selected recipes, and furnace fuel; those details are owned there. [Experience value][blaze] · [XP conditions][death]

Related: [Wither Skeleton](WitherSkeleton.md) · [Nether Fortress](../structures/NetherFortress.md) · [Blaze Rod](../items/BlazeRod.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game combat, spawner-lighting, farm, or drop-rate test was run. Data packs and custom spawner data can change the bundled rules. Timings assume normal 20-TPS ticking and the stated uninterrupted attack conditions.

[defaults]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[health]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L57-L59
[fortress]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/structure/fortress.json
[fortress-enemies]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressStructure.java#L17-L23
[natural]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L336
[piece-spawns]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L452
[spawn]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[monster]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/Monster.java
[ground]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java
[mob-spawn]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L728-L741
[player-damage]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L748
[death]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[mob-attack]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1321
[looting]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[blaze]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/Blaze.java
[registration]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/EntityType.java#L319-L321
[pieces]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java
[spawner]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/BaseSpawner.java#L31-L187
[near-player]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/EntityGetter.java#L103-L114
[spawn-data]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/SpawnData.java#L16-L30
[spawner-tick]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/block/entity/SpawnerBlockEntity.java
[attack]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/Blaze.java#L156-L256
[fireball]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/SmallFireball.java#L32-L69
[damage-type]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/damage_type/fireball.json
[fire-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/damage_type/is_fire.json
[resistance]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1146-L1154
[snowball]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/Snowball.java#L51-L57
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json
[water]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2865-L2867
[splash]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/projectile/AbstractThrownPotion.java#L69-L106
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/blaze.json
[mob-damage-type]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/damage_type/mob_attack.json

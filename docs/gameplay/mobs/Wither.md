# Wither

The **Wither** is a flying boss summoned for its [Nether Star](../items/NetherStar.md). Prepare the site before completing the summon: its arrival ends in an explosion, its skulls damage terrain, and its low-health defenses require a change of weapons. The default attributes are **300 health points (150 hearts)** and **4 armor points**, with a body **0.9 blocks wide × 3.5 blocks tall**. The health attribute is the same across the checked difficulty settings. [Boss behavior and attributes][wither] · [Attribute registration][defaults] · [Entity registration][registration]

## Summoning

The verified Survival construction uses **four [Soul Sand](../items/SoulSand.md) or [Soul Soil](../items/SoulSoil.md) blocks** and **three [Wither Skeleton Skulls](../items/WitherSkeletonSkull.md)**. The two base materials can be mixed because both belong to the accepted summoning-base tag. [Summoning code][summon] · [Accepted base blocks][base-tag]

Build a standing T shape, then place the skulls across its top:

```text
K K K
B B B
. B .
```

`K` is a Wither Skeleton Skull, `B` is an accepted base block, and `.` must be **air**. The lower side spaces cannot contain another block. Place a skull last to trigger the pattern check. A successful summon clears the construction blocks and creates the boss. The server-side check requires a difficulty **other than Peaceful**; this route has no biome or darkness requirement. [Exact pattern and trigger][summon] · [Consumed construction][clear-pattern]

A dispenser can place the completing skull when its target position is empty and the base-pattern check succeeds. In Creative, a [Wither Spawn Egg](../items/WitherSpawnEgg.md) is also registered. The charge sequence below describes the construction summon, which explicitly initializes it. [Dispenser route][dispenser] · [Spawn-egg registration][egg]

## Arrival and charge

A construction-summoned Wither starts at **one-third of maximum health** and receives **220 invulnerable ticks**, about **11 seconds at 20 TPS**. During this charge it heals **10 health points every 10 ticks**, up to its maximum, and its movement/attack goals are suppressed. Ordinary damage does not interrupt the charge. [Charge initialization, healing, and defenses][wither]

At the end it creates an explosion with **power 7**. This is an explosion-strength value, not a guaranteed destruction radius or a fixed amount of player damage. Move away before the charge finishes and summon far from valuable builds. The `mobGriefing` rule controls the explosion's block destruction; disabling it does not disable the explosion or its entity damage. [Charge explosion][wither] · [Explosion rule handling][explosions]

## Fighting it

- **Bring a close-range weapon as well as any bow.** At **half health or below**, ordinarily **150 points (75 hearts)**, it rejects hits whose direct entity is an arrow-family projectile or a Wind Charge item projectile. **Thrown tridents are included**, because they use the arrow base class. This is not a blanket rule covering every possible projectile. The check follows its current health.
- **Expect a flying target throughout.** The low-health state changes its altitude-following behavior, but does not make it a grounded mob.
- **Do not let a fight stall indefinitely.** Outside the charge, it heals **1 health point every 20 ticks**, about half a heart each second at 20 TPS.
- **Keep animals and villagers away.** It seeks attackable living targets outside the wither-friends tag, which currently contains undead mobs. A skull that kills its victim can heal the skull's living owner by **5 health points**.
- **Do not rely on fire, drowning, or Poison.** It is fire-immune, rejects the bundled drowning damage type, and refuses applied status effects. Poison, Slowness, and Wither are not dependable ways to weaken it. This does not claim that every instant-effect potion uses the same application path.

A **Smite** weapon has a source-supported damage bonus against it: the Wither belongs to the undead group included in the Smite-sensitive tag. This is a useful weapon choice, not a guarantee of a particular number of hits. [Low-health defenses, movement, healing, and targeting][wither] · [Trident inheritance][trident] · [Wind Charge projectile type][wind-charge] · [Fire immunity][registration] · [Damage immunity tag][immune-damage] · [Friendly targets][friends] · [Undead membership][undead] · [Smite-sensitive tag][smite-tag] · [Smite bonus][smite]

## Skull and terrain hazards

A skull fired by the Wither attempts **8 health points of direct-hit damage** before difficulty and defenses, then creates a separate **power-1 explosion**. After a successful direct hit on a living target, it applies **Wither II** for:

| Difficulty | Wither effect from the skull hit |
| --- | --- |
| Easy | None |
| Normal | 200 ticks, about 10 seconds |
| Hard | 800 ticks, about 40 seconds |

Easy still has impact and explosion damage. [Skull impact, explosion, and effect][skull] · [Damage-type scaling][skull-damage] · [Player difficulty scaling][player-damage]

The [Wither effect](../effects/Wither.md) can be lethal: its damage method has no low-health cutoff. [Milk](../items/MilkBucket.md) removes it, along with other effects you may want to keep; a Honey Bottle's Poison removal does not cure Wither. Get space before stopping to drink. [Wither damage][effect] · [Consumable effects][consumables]

Dangerous skulls cap the blast resistance of destroyable blocks at **0.8**, making ordinary blast-resistant material an unreliable guarantee of containment. The boss can also schedule nearby block destruction after being attacked, with a default **20-tick delay** and a `mobGriefing` check. Its protected-block tag includes bedrock and reinforced deepslate, but **does not include obsidian**. No cage or farm design is validated by this page. [Dangerous skull resistance][skull] · [Nearby block destruction][wither] · [Protected blocks][immune-blocks]

## Death, drops, and persistence

With **mob loot enabled**, normal death produces **one Nether Star** through the boss's custom drop code. Looting does not increase that fixed star drop; the bundled entity loot table itself has no item pools. A qualifying player-attributed kill has a **base reward of 50 experience**, subject to the ordinary experience-drop conditions. [Custom star drop and experience value][wither] · [Empty entity loot table][loot] · [Loot and experience conditions][death] · [Mob-loot rule][monster]

The newly dropped star has an extended lifetime and explosion resistance, but still needs collecting and protecting from other hazards. See [Nether Star](../items/NetherStar.md) for the precise collection window and its Beacon recipe.

The Wither does not use ordinary distance-based despawning and cannot use portals. Switching to Peaceful removes it through the discard path rather than normal death, so that is not a Nether Star collection method. [Persistence and portal restrictions][wither]

## Verification scope

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`, using active MattMC code and bundled data. No in-game summoning, damage, combat, containment, or loot test was run. Timings assume 20 TPS; game rules, tags, entity data, and equipment can alter an encounter.

Related: [Nether Star](../items/NetherStar.md) · [Wither Skeleton](WitherSkeleton.md) · [Wither effect](../effects/Wither.md) · [Mobs](Mobs.md)

[wither]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/boss/wither/WitherBoss.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L269
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1511-L1519
[summon]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/WitherSkullBlock.java
[base-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/wither_summon_base_blocks.json
[clear-pattern]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/CarvedPumpkinBlock.java#L127-L143
[dispenser]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/core/dispenser/DispenseItemBehavior.java#L275-L294
[egg]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java#L2002
[explosions]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/server/level/ServerLevel.java#L1156-L1169
[trident]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/projectile/ThrownTrident.java#L27
[wind-charge]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/projectile/windcharge/WindCharge.java#L22
[immune-damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/damage_type/wither_immune_to.json
[friends]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/wither_friends.json
[undead]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/undead.json
[smite-tag]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/entity_type/sensitive_to_smite.json
[smite]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/enchantment/smite.json
[skull]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/projectile/WitherSkull.java
[skull-damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/damage_type/wither_skull.json
[player-damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L750
[effect]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/effect/WitherMobEffect.java
[consumables]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/Consumables.java
[immune-blocks]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/tags/block/wither_immune.json
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/wither.json
[death]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1488
[monster]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Monster.java

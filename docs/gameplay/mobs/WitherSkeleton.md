# Wither Skeleton

**Wither Skeletons** (`minecraft:wither_skeleton`) are tall, fire-immune fortress enemies. Their successful melee hits add the potentially lethal [Wither effect](../effects/Wither.md), and their rare [skulls](../items/WitherSkeletonSkull.md) are needed for summoning the [Wither boss](Wither.md). They have **20 default health points (10 hearts)** and a registered body **0.7 blocks wide × 2.4 blocks tall**. [Entity registration][registration] · [Skeleton attributes][skeleton] · [Active attribute registration][defaults] · [Health default][health]

## Finding and spawning

The verified natural route is a [Nether Fortress](../structures/NetherFortress.md). Its monster selection lists Wither Skeleton groups of **five**, subject to successful spawn attempts. The selection applies inside fortress pieces; another route applies above **Nether Bricks inside a recognized fortress**. A Nether-brick platform outside a fortress does not gain this special spawn list. [Fortress spawn data][fortress] · [Enemy list][fortress-enemies] · [Piece selection][piece-spawns] · [Nether-brick selection][natural]

Ordinary spawning requires:

- A difficulty **other than Peaceful**
- Valid ground and enough unobstructed space for the entity
- The dimension's monster-darkness test

In the bundled **Nether**, that darkness test accepts local raw brightness **7 or lower**; the dimension has no skylight. This threshold comes from the Nether's dimension data, so do not apply it unchanged to another dimension. Unlike [Blazes](Blaze.md), Wither Skeletons do use the darkness check. [Spawn registration][spawn] · [Monster predicate][monster] · [Nether light settings][nether-type] · [Ground placement][ground] · [Obstruction checks][mob-spawn]

The fortress's generated monster-throne spawner is configured for **Blazes**, not Wither Skeletons. This page verifies the natural fortress route rather than claiming a Survival Wither Skeleton spawner. In Creative, a [Wither Skeleton Spawn Egg](../items/WitherSkeletonSpawnEgg.md) is available. [Spawner configuration][pieces]

## Combat and status effects

A normally initialized Wither Skeleton carries a **Stone Sword** and uses the inherited melee attack goal. The spawn callback sets its base attack damage to **4**, and the ordinary stone sword adds **4** more: **8 damage before defenses on Normal**, calculated from the active equipment modifiers and attack path. Difficulty scaling changes that direct player damage; armor, effects, enchantments, and altered equipment can change the result further. [Spawn equipment and base attack][wither-skeleton] · [Stone-sword registration][sword] · [Sword modifier][material] · [Equipment modifiers][equipment] · [Melee dispatch][mob-attack] · [Melee damage type][mob-damage-type] · [Difficulty scaling][player-damage]

After a **successful melee hit on a living target**, it attempts to apply **Wither I for 200 ticks**, about **10 seconds at 20 TPS**. That callback has no Easy/Normal/Hard duration split. Wither damage can kill; use the [Wither-effect guide](../effects/Wither.md) for its timing and removal. [Melee effect callback][melee-effect]

**Keep distance and use terrain to limit approach.** Its ordinary melee goal requires reaching the target; its 2.4-block body also needs more clearance than a player. A low, properly closed passage can restrict its movement, but the size alone is not proof that every doorway or attack position is safe. The inherited targeting includes players, Iron Golems, and vulnerable baby turtles; this subtype also targets piglins. It also inherits wolf avoidance. [Body size][registration] · [Goals][skeleton] · [Piglin targeting][wither-skeleton] · [Melee approach][melee-goal]

Do not rely on fire or Poison to weaken it:

- **Fire and lava damage are blocked** by its fire immunity
- It rejects the **Wither** status effect directly
- Its undead tag membership also rejects **Poison and Regeneration** effects
- **Smite** has a source-supported damage bonus against its tagged undead type

These are specific defenses, not immunity to every potion or damage type. [Fire immunity][registration] · [Fire-damage rejection][fire-immunity] · [Wither rejection][wither-skeleton] · [Skeleton tag][skeleton-tag] · [Undead tag][undead] · [Poison/Regeneration tag][poison-tag] · [Effect check][effects] · [Smite-sensitive tag][smite-tag] · [Smite enchantment][smite]

## Equipment variations

The default equipment override supplies a stone sword and does not run the ordinary skeleton armor or equipment-enchantment setup. The inherited spawn routine can still enable loot pickup according to local difficulty, and its October 31 headwear roll remains inherited. A newly encountered Wither Skeleton is therefore not guaranteed to retain only its starting sword forever. [Default equipment overrides][wither-skeleton] · [Inherited initialization][skeleton]

The bundled disliked-weapons tag excludes **bows and crossbows from normal item pickup**. If custom entity equipment nevertheless gives it a bow, inherited weapon-goal reassessment selects ranged behavior, and this subtype ignites its arrows for **100 seconds**. That is the arrow's fire timer, not a promise of a 100-second burn on whatever it hits. [Pickup filter and arrow override][wither-skeleton] · [Disliked weapons][disliked] · [Weapon-goal reassessment][skeleton]

## Drops and experience

With mob loot enabled, the ordinary table gives **0–1 Coal** and **0–2 [Bones](../items/Bone.md)** before Looting. The coal amount comes from a **−1 to 1** count roll, with negative and zero results giving no coal before bonuses, so it is not a simple 50% one-coal roll. Looting can increase the maxima to **4 coal** and **5 bones at Looting III**. These two material pools do not require a player-attributed kill. [Death loot][loot] · [Looting count][looting] · [Mob-loot gate][monster]

The skull pool is separate and **requires player attribution**. Read [Wither Skeleton Skull](../items/WitherSkeletonSkull.md#obtaining) for exact odds, Looting changes, and the separate charged-Creeper route. The carried sword is an equipment drop rather than part of the ordinary coal/bone/skull table; do not assume it drops every time. [Death loot][loot] · [Equipment-drop handling][equipment-drop]

A qualifying player-attributed kill starts with **5 base experience**, and eligible carried equipment can add to that reward. [Monster base reward][monster] · [Equipment XP][xp] · [XP conditions][death]

Related: [Wither Skeleton Skull](../items/WitherSkeletonSkull.md) · [Wither](Wither.md) · [Blaze](Blaze.md) · [Nether Fortress](../structures/NetherFortress.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game spawning, clearance, combat, skull-rate, or equipment test was run. Data packs, difficulty, and custom equipment can alter these bundled rules; no complete farm or safe corridor design is validated here.

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
[fire-immunity]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Entity.java#L2881-L2885
[player-damage]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L748
[death]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[mob-attack]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1321
[looting]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[registration]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/EntityType.java#L1520-L1531
[skeleton]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java
[nether-type]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/dimension_type/the_nether.json
[pieces]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressPieces.java#L1092-L1100
[wither-skeleton]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/WitherSkeleton.java
[sword]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/Items.java#L1316
[material]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/item/ToolMaterial.java
[equipment]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2648-L2688
[melee-effect]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/WitherSkeleton.java#L93-L104
[melee-goal]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/goal/MeleeAttackGoal.java
[skeleton-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/skeletons.json
[undead]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/undead.json
[poison-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/ignores_poison_and_regen.json
[effects]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1003-L1012
[smite-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/sensitive_to_smite.json
[smite]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/enchantment/smite.json
[disliked]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/item/wither_skeleton_disliked_weapons.json
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/wither_skeleton.json
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[xp]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L289-L306
[mob-damage-type]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/damage_type/mob_attack.json

# Cave Spider

The **Cave Spider** is a small spider whose successful bites can apply Poison. Expect it around special spawners, and keep a retreat route clear of cobwebs. It has **12 health points (6 hearts)** and a body only **0.7 blocks wide × 0.5 blocks tall**, much smaller than an ordinary [Spider](Spider.md). A one-block-wide opening is not a dependable barrier. [Cave-spider attributes][cave-spider] · [Registered attributes][defaults] · [Size][registration]

## Where to find it

Two verified structure routes are:

- **Mineshafts:** selected non-rail corridors can generate a cave-spider spawner. The structure's corridor code explicitly places an ordinary spawner and sets its entity to `minecraft:cave_spider`; not every corridor receives one
- **Trial Chambers:** cave spider is one of the structure's possible **small-melee spawner** selections. Its template points to cave-spider normal and ominous trial-spawner configurations, both of which contain cave spiders

The bundled biome spawn tables reviewed for this page contain **no cave-spider entry**. Its ground-spawn registration alone is not evidence of ordinary roaming biome spawns. [Mineshaft placement][mineshaft] · [Mineshaft world-generation entry][mineshaft-set] · [Trial structure choices][trial-structure] · [Spawner template][trial-template] · [Normal configuration][trial-normal] · [Ominous configuration][trial-ominous] · [Biome tables][biomes]

In Creative, use the [Cave Spider Spawn Egg](../items/CaveSpiderSpawnEgg.md). With command permission, use `/summon minecraft:cave_spider`.

### Ordinary and trial spawners behave differently

An unmodified ordinary mineshaft spawner activates around a nearby player, using a default **16-block player range**. Its normal spawn attempt still runs the monster darkness test; in the bundled Overworld that includes **block light 0**. Light the potential spawning spaces rather than assuming a single light beside the cage covers every candidate position. Obstruction and nearby-mob limits also affect attempts. [Ordinary-spawner checks][spawner] · [Spawn registration][placement] · [Monster darkness rules][monster] · [Overworld settings][overworld]

**Trial-spawner spawn reasons bypass the monster light requirement.** The cave-spider trial configurations do not add a custom darkness requirement, so torches are not a way to disable these encounters. Trial attempts still have their own collision, visibility, and obstruction checks. [Spawn-reason exception][spawn-reason] · [Trial spawning][trial-runtime] · [Normal configuration][trial-normal] · [Ominous configuration][trial-ominous]

## Bites and Poison

A successful melee hit on a living target adds **Poison I** according to difficulty:

| Difficulty | Poison from a bite |
| --- | --- |
| Easy | None |
| Normal | 140 ticks, about 7 seconds |
| Hard | 300 ticks, about 15 seconds |

These durations assume 20 TPS. The inherited base melee attack attribute is **2 health points (1 heart)** before difficulty, effects, and defenses; Poison is an additional effect rather than a replacement for the bite's damage. Easy therefore removes the bite's Poison effect, not its melee attack. [Poison application][cave-spider] · [Spider inheritance][spider] · [Monster attributes][monster] · [Base attack default][attributes] · [Player difficulty scaling][player-damage]

Poison damage ticks only while health is above **1 point (half a heart)**. Another bite or another damage source can still kill a weakened player. Retreat before drinking a remedy: [Milk](../items/MilkBucket.md) removes all status effects, including useful ones, while a [Honey Bottle](../items/HoneyBottle.md) specifically removes Poison. Further bites can apply Poison again. [Poison damage][poison] · [Milk and honey effects][remedies] · [Remedy item wiring][remedy-items]

## Movement and practical precautions

Cave spiders inherit the ordinary spider's **wall climbing**, **leaping**, **cobweb-slowing immunity**, and **Poison immunity**. Cobwebs around a spawner can slow your retreat while leaving the cave spider unimpeded. Its smaller hitbox also means a barrier that excludes a normal spider may still admit this one. Use enclosed barriers and keep an exit clear rather than depending on webbing or wall height alone. [Inherited behavior][spider] · [Wall navigation][climbing] · [Registered size][registration]

The inherited targeting is also brightness-dependent: bright conditions suppress ordinary player/iron-golem target acquisition, but retaliation remains possible and an existing fight does not end immediately when the area is lit. Cave spiders also inherit avoidance of **non-scared armadillos within 6 blocks**. See [Spider](Spider.md) for the shared movement and targeting details. [Goals and brightness checks][spider]

Cave spiders override spawn finalization without running the ordinary spider's setup. Consequently, they do **not** use its random skeleton-rider or Hard-difficulty spawn-group effect rolls. This does not prevent effects or passengers supplied by other mechanisms. [Cave-spider spawn setup][cave-spider] · [Ordinary-spider rolls][spider]

## Drops

With mob loot enabled:

- **0–2 [String](../items/String.md)**, with a possible maximum of **5 at Looting III**
- On a qualifying player-attributed kill, a **1-in-3 base chance of one [Spider Eye](../items/SpiderEye.md)** without Looting; Looting can raise the possible maximum to **4 eyes at Looting III**

The string pool has no player-kill condition, while the eye pool does. Neither has a fire-based cooked replacement. A qualifying kill has a base reward of **5 experience**. Trial-spawner rewards are separate from these entity drops. [Loot table][loot] · [Integer count rolls][uniform] · [Looting increase][looting-count] · [Mob-loot rule and base experience][monster] · [Experience conditions][experience]

## Verification scope

Source-reviewed on **2026-10-01** at `b81c01943c9f3254e713c365a1dd633392929cb2`, using active MattMC code and bundled data, including the trial-spawner template references. No in-game spawner, combat, poison, barrier, or loot test was run. Data packs, server settings, and entity/spawner data can change these defaults.

Related: [Spider](Spider.md) · [Spider Eye](../items/SpiderEye.md) · [String](../items/String.md) · [Poison](../effects/Poison.md) · [Mobs](Mobs.md)

[cave-spider]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/CaveSpider.java
[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L370-L372
[mineshaft]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/structure/structures/MineshaftPieces.java
[mineshaft-set]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/structure_set/mineshafts.json
[trial-structure]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json
[trial-template]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/structure/trial_chambers/spawner/small_melee/cave_spider.nbt
[trial-normal]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/trial_spawner/trial_chamber/small_melee/cave_spider/normal.json
[trial-ominous]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/trial_spawner/trial_chamber/small_melee/cave_spider/ominous.json
[biomes]: https://github.com/HungLo2020/MattMC/tree/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome
[spawner]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/BaseSpawner.java
[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L112
[monster]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Monster.java
[overworld]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/dimension_type/overworld.json
[spawn-reason]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntitySpawnReason.java
[trial-runtime]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java
[spider]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Spider.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java
[player-damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L750
[poison]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/effect/PoisonMobEffect.java
[remedies]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/component/Consumables.java
[remedy-items]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/item/Items.java
[climbing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/navigation/WallClimberNavigation.java
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/cave_spider.json
[uniform]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/providers/number/UniformGenerator.java
[looting-count]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java
[experience]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1488

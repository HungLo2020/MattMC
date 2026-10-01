# Spider

The **Spider** is a wall-climbing monster that usually hunts in darkness and can retaliate when attacked. It supplies [String](../items/String.md) and [Spider Eyes](../items/SpiderEye.md). A normal spider has **16 health points (8 hearts)** and a body **1.4 blocks wide × 0.9 blocks tall**. Its ordinary melee attack does not apply Poison; the [Cave Spider](CaveSpider.md) is a separate mob. [Spider behavior][spider] · [Registered attributes][defaults] · [Size][registration] · [Cave-spider attack][cave-spider]

## Where to find it

The bundled **plains**, **forest**, and **desert** monster tables each include spiders in groups of four. **Deep Dark** and **Mushroom Fields** have no spider entry in their normal biome spawn tables. This does not prevent an existing spider from entering them. [Plains][plains] · [Forest][forest] · [Desert][desert] · [Deep Dark][deep-dark] · [Mushroom Fields][mushroom-fields]

Normal spiders are registered for **ground spawning** with the shared monster predicate. Natural spawning requires a non-Peaceful difficulty, suitable ground, and the dimension's darkness checks. In the bundled Overworld, **block light must be 0**, with additional sky/local-light tests. Lighting a spawnable area helps prevent new spiders; it does not remove those already present. Spawn-table group sizes are attempted groups, not guaranteed counts. [Spawn registration][placement] · [Monster rules][monster] · [Ground check][mob-spawn] · [Overworld settings][overworld]

Ordinary spiders can also appear from **monster-room spawners**. The generator chooses a spider in one of four equally selected entries, and plains generation includes that feature. This is different from a cave-spider spawner. [Room generator][monster-room] · [Generation wiring][plains]

In Creative, use the [Spider Spawn Egg](../items/SpiderSpawnEgg.md). With command permission, use `/summon minecraft:spider`.

## When it attacks

The spider's usual player and iron-golem target acquisition is **disabled in bright light** and enabled when its brightness check is below its threshold. Retaliation is a separate goal, so hitting a spider can still provoke it. A spider already fighting does not instantly become safe when daylight arrives: in bright conditions its melee goal only has a small random chance to clear its target during continuation checks. [Targeting and retaliation][spider]

Spiders have **no daylight-burning routine**. Leave a calm daytime spider alone if you do not need its drops, and do not assume a pursuing one has stopped because the sun rose. [Spider implementation][spider] · [Shared monster behavior][monster]

## Fighting and building safely

- **A tall wall alone is not a dependable barrier.** Horizontal collision enables the spider's climbing state, and it uses wall-climbing navigation. Prefer a fully enclosed, roofed shelter over relying only on extra wall height.
- **Leave space for its leap.** Its leap goal can start from the ground when its target is **2–4 blocks away**, subject to a random check. Keep an escape route rather than backing against a wall.
- **Do not use cobwebs as your only trap.** Spiders skip cobweb slowing, and they cannot receive the Poison effect.
- **Armadillos can deter spiders.** The avoidance goal covers armadillos within **6 blocks**, but only while the armadillo is **not scared**.

The ordinary base attack attribute is **2 health points (1 heart)** before difficulty scaling, effects, and defenses. Some Hard-difficulty spiders have Strength, so encounters need not deal identical damage. These are code-grounded precautions, not a tested shelter design or combat benchmark. [Climbing, immunities, and avoidance][spider] · [Wall navigation][climbing] · [Leap conditions][leap] · [Base attack attribute][attributes] · [Monster attribute inheritance][monster] · [Player difficulty scaling][player-damage]

## Riders and special effects

A spider's spawn setup has a **1% chance** to attach a [Skeleton](Skeleton.md) rider. Treat the mounted skeleton as a separate ranged threat. The spider's own melee goal cannot start while it has a passenger, so a jockey's behavior should not be assumed identical to an ordinary spider. [Rider creation and melee condition][spider]

On **Hard**, a newly selected spawn-group profile can receive an effect with a chance of **10% multiplied by the local-difficulty special multiplier**, which ranges from 0 to 1. The choices are **Speed**, **Strength**, **Regeneration**, and **Invisibility**; Speed takes two of five equally selected outcomes and each other effect takes one. Members using that effect-bearing group data receive the same effect, with no normal duration countdown. The maximum 10% chance is therefore not a fixed rate for every individual spider encounter. [Effect selection and application][spider] · [Local-difficulty multiplier][difficulty]

## Drops

With mob loot enabled:

- **0–2 string**, with a possible maximum of **5 at Looting III**
- On a qualifying player-attributed kill, a **1-in-3 base chance of one spider eye** without Looting. Looting can increase the amount, reaching a possible maximum of **4 eyes at Looting III**

The eye pool has a player-kill condition; simply letting spiders die to the environment is not equivalent to a qualifying player-attributed kill. The string pool has no such condition. Neither drop has a fire-based replacement. A qualifying kill has a base reward of **5 experience**. [Loot table][loot] · [Count rolls][uniform] · [Looting count increase][looting-count] · [Base experience][monster] · [Reward conditions][experience]

## Verification scope

Source-reviewed on **2026-10-01** against active MattMC code and bundled data at commit `b81c01943c9f3254e713c365a1dd633392929cb2`. No in-game spawning, jockey, climbing, or loot test was run for this page. Data packs can change biome tables and loot; effects, difficulty, and entity data can change individual encounters. The current code's conditional hostility is why this spider remains in the wiki's neutral browsing group.

Related: [Cave Spider](CaveSpider.md) · [Skeleton](Skeleton.md) · [Armadillo](Armadillo.md) · [Mobs](Mobs.md)

[defaults]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[attributes]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java
[plains]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/plains.json
[forest]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/forest.json
[desert]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/desert.json
[deep-dark]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/deep_dark.json
[mushroom-fields]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json
[monster]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Monster.java
[mob-spawn]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/Mob.java#L728-L740
[overworld]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/dimension_type/overworld.json
[monster-room]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/levelgen/feature/MonsterRoomFeature.java
[player-damage]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L750
[experience]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1488
[spider]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/Spider.java
[registration]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/EntityType.java#L1316-L1325
[placement]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L147
[cave-spider]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/monster/CaveSpider.java
[climbing]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/navigation/WallClimberNavigation.java
[leap]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/entity/ai/goal/LeapAtTargetGoal.java
[difficulty]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/DifficultyInstance.java
[loot]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/resources/data/minecraft/loot_table/entities/spider.json
[uniform]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/providers/number/UniformGenerator.java
[looting-count]: https://github.com/HungLo2020/MattMC/blob/b81c01943c9f3254e713c365a1dd633392929cb2/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java

# Slime

**Slimes** (`minecraft:slime`) are hopping hostile mobs that divide into smaller Slimes when killed. Finish the smallest ones to collect [Slimeballs](../items/Slimeball.md) for Sticky Pistons, Slime Blocks, and Magma Cream. A small Slime still follows targets, but its ordinary contact attack does no damage. [Size, targeting, and splitting][slime] · [Loot table][loot]

## Finding Slimes

The bundled biome data lists Slimes in many Overworld biomes, including **Plains, Swamp, and Mangrove Swamp**. Natural spawning then applies one of two routes. A biome entry is necessary: Mushroom Fields, for example, has no ordinary Slime entry. [Plains data][plains] · [Swamp data][swamp] · [Mangrove data][mangrove] · [Mushroom Fields data][mushroom] · [Spawn selection][natural]

| Route | Conditions specific to Slimes |
| --- | --- |
| Surface swamp route | Swamp or Mangrove Swamp, with the spawn position at **Y 51–69**; a 50% random gate, a moon-brightness gate, and a randomized light check |
| Underground slime-chunk route | A seed-dependent Slime chunk, spawn position **below Y 40**, and a further 1-in-10 attempt gate; **no light-level or moon-phase check** |

For the surface route, local raw brightness must be at most a random integer from **0 through 7**. Light level 7 can therefore pass, but is less favorable than 0; light level 8 cannot pass. Brighter moon phases improve the separate moon roll, and a zero-brightness moon fails it. The underground route selects roughly one in ten chunks from the world seed; this is not a one-in-ten chance of finding a Slime whenever you enter a chunk. [Spawn predicate][slime] · [Surface-biome tag][surface] · [Moon brightness values][moon]

Both natural routes require a difficulty other than Peaceful, valid supporting ground, and unobstructed spawn space without liquid. Normal mob caps and player-distance checks still apply. Lighting an underground Slime chunk does not disable its Slime predicate. This source review does not establish a seed-specific farm location or measured spawn rate. [Registered placement checks][spawn] · [Ground placement][ground] · [Obstruction rules][mob] · [Natural spawning][natural]

In Creative, use the [Slime Spawn Egg](../items/SlimeSpawnEgg.md). Custom spawners use a different path: the Slime predicate bypasses the swamp, moon, and slime-chunk checks for a spawner spawn reason, while still rejecting Peaceful. That does not establish a naturally generated Survival Slime spawner. [Spawner branch][slime] · [Spawner caller][spawner]

## Sizes, attacks, and splitting

See [Oozing](../effects/TriggeredEffects.md#oozing) for effect-created medium Slimes and the nearby-Slime count limit.

Ordinary spawn initialization chooses sizes **1, 2, or 4**; local difficulty can shift the roll toward a larger size. These are size categories, not ages. [Spawn initialization and size attributes][slime] · [Active default attributes][defaults]

| Size | Default health | Width and height | Ordinary contact damage on Normal, before defenses |
| --- | --- | --- | --- |
| Small, size 1 | 1 point, half a heart | 0.52 blocks | None |
| Medium, size 2 | 4 points, 2 hearts | 1.04 blocks | 2 points, 1 heart |
| Large, size 4 | 16 points, 8 hearts | 2.08 blocks | 4 points, 2 hearts |

The body scales from the registered 0.52-block dimensions. Player damage also depends on difficulty, armor, and other defenses. The contact attack requires a living Slime with active AI, melee reach, and line of sight. Player contact and Iron Golem collisions call this attack; the targeting goals pursue those two target types. Small Slimes fail the damage check even though they can still approach. [Size and attacks][slime] · [Registered dimensions][registration] · [Player contact dispatch][player-touch] · [Difficulty scaling][player-damage] · [Mob-attack damage type][damage-type]

On death, each Slime larger than size 1 creates **2–4 Slimes at half its size**, rounded down. A large Slime therefore produces medium Slimes, which split again into small ones. The children start with full health. Ordinary despawning is not a death split. Give yourself room to finish the new group instead of treating the first kill as the end of the encounter. [Death removal and split callback][slime] · [Conversion dispatch][conversion]

Slimes do not have the [Magma Cube's](MagmaCube.md) fire or fall immunity. Ordinary fire and fall damage can hurt them. Slimes are also tagged immune to the **Oozing** status effect, so applying that effect to a Slime does not create an extra reproduction loop. [Entity registration][registration] · [Fire/fall handling][entity] · [Fall-damage calculation][fall] · [Fall-immunity tag][fall-tag] · [Oozing immunity][oozing-tag] · [Effect admission][effects]

## Drops and experience

With mob loot enabled:

- **Small Slimes** normally drop **0–2 Slimeballs**. Looting can raise the maximum to **5 at Looting III**, but does not guarantee a drop
- **Medium and large Slimes** do not drop Slimeballs themselves; their small descendants are the material source
- If a **Frog kills a small Slime**, the separate frog branch drops **one Slimeball** without the ordinary Looting function

The ordinary Slimeball pool does **not** require a player-attributed kill. Looting is read from the living attacker associated with the killing damage, so an earlier sword hit does not itself prove that an environmental finishing blow gets the bonus. [Loot table][loot] · [Size predicate][size-predicate] · [Looting function][looting] · [Loot context and death gates][death] · [Frog size eligibility][frog]

Each qualifying player-attributed kill has base experience equal to that Slime's size: **1, 2, or 4 XP** for the ordinary sizes. The split offspring are separate mobs with their own kill rewards and attribution checks. See [Slimeball](../items/Slimeball.md) for recipes and other acquisition routes. [Size XP assignment][slime] · [Experience gates][death]

Related: [Magma Cube](MagmaCube.md) · [Slimeball](../items/Slimeball.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed on **2026-10-01** at `4285adff2e35307c277a3a5bf54ebd064aa5e64b`. No in-game spawn, combat, drop-rate, or farm test was run. The reviewed biome and loot data are loaded through the game's registry codecs; active data packs and custom entity data can change the bundled rules. [World-generation registry loading][worldgen-load] · [Loot registry loading][loot-load]

[slime]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/monster/Slime.java
[loot]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/loot_table/entities/slime.json
[plains]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/plains.json
[swamp]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/swamp.json
[mangrove]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/mangrove_swamp.json
[mushroom]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/worldgen/biome/mushroom_fields.json
[natural]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L155-L336
[surface]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/worldgen/biome/allows_surface_slime_spawns.json
[moon]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/dimension/DimensionType.java#L75
[spawn]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[ground]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java
[mob]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L728-L741
[spawner]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/BaseSpawner.java#L31-L187
[defaults]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java
[registration]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/EntityType.java#L1223-L1232
[player-touch]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/player/Player.java#L477-L529
[player-damage]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/player/Player.java#L722-L748
[damage-type]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/damage_type/mob_attack.json
[conversion]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Mob.java#L1157-L1184
[entity]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/Entity.java#L2881-L2885
[fall]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1711-L1734
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json
[oozing-tag]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/resources/data/minecraft/tags/entity_type/immune_to_oozing.json
[effects]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java
[size-predicate]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/advancements/critereon/SlimePredicate.java
[looting]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[death]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[frog]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/world/entity/animal/frog/Frog.java#L357-L375
[worldgen-load]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[loot-load]: https://github.com/HungLo2020/MattMC/blob/4285adff2e35307c277a3a5bf54ebd064aa5e64b/src/main/java/net/minecraft/server/ReloadableServerRegistries.java

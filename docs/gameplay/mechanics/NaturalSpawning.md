# Natural spawning and despawning

A quiet patch of suitable ground does not guarantee a mob. Ordinary natural spawning needs an eligible area near a player, room in the relevant population category, a creature selected for that location, and a successful placement. Use this guide to diagnose missing encounters or disappearing mobs; use the [mob directory](../mobs/Mobs.md) for each species' habitat, light, food and retention rules. [Active natural-spawn path][natural-tick] · [Selection and placement][attempts]

## Identify the source first

These routes create mobs through different checks:

| Source | What to consult |
| --- | --- |
| Repeated natural attempts in existing terrain | The shared rules below, then the relevant [biome](../biomes/Biomes.md) and mob guide |
| Initial population while new terrain generates | A separate generation pass; the ordinary noise generator can populate the biome's creature category without the ongoing natural-spawn cap check |
| A cage | [Monster Spawner](../blocks/MonsterSpawner.md): its own player activation, nearby-entity limit and attempt rules |
| A trial encounter | [Trial Spawner](../blocks/TrialSpawner.md): registered participants, encounter quotas and cooldown |
| An egg or bucket | [Spawn eggs](../items/SpawnEggs.md) or the relevant bucket/species guide |
| Breeding, summoned defenders or an event | The species guide, [Raids](Raid.md), or the relevant event/device guide |

The generation pass is called during chunk generation and uses its own creature selection and placement loop. It is not the repeated population-replacement process described here. Likewise, an egg working in a biome does not establish a natural encounter route there. [Generation caller][worldgen-caller] · [Noise-generator population][worldgen-generator] · [Generation loop][worldgen-spawns]

`doMobSpawning` gates the repeated natural pass. Ordinary monster attempts also depend on `spawnMonsters` and non-Peaceful difficulty. The same server branch invokes separate custom spawners, but their own rules still matter: see [Phantoms](../mobs/Phantom.md) and [Pillagers](../mobs/Pillager.md), for example. Do not treat one switch or this guide's distances as a universal rule for eggs, cages, breeding or an already-running raid. [Natural and custom-spawner caller][natural-tick] · [Monster setting][monster-setting] · [Game rules][rules]

## How a natural encounter is chosen

After the shared population and area checks, a natural attempt works through several independent filters:

1. **Choose a candidate from this location.** The current dimension's generator uses the biome at the attempted position and the population category. A matching structure override can replace that category's biome list. The weighted entry also supplies a group-size range; neither its weight nor its group size guarantees an encounter. See [Swamp Hut](../structures/SwampHut.md) and [Nether Fortress](../structures/NetherFortress.md) for those specific boundaries. [Biome/structure selection][selection] · [Structure overrides][structure-selection]
2. **Check the candidate's placement and registered predicate.** Ground, water and lava placement have different tests. Ground placement checks the supporting block and the two spaces above it; the natural path also checks the creature's actual collision box. A registered species predicate can add light, height, difficulty, biome, weather or other restrictions. [Placement types][placement-types] · [Registration and dispatch][placements] · [Candidate checks][candidate-checks]
3. **Check the created mob.** The mob's own spawn-rule and obstruction checks can reject the attempt even after the registered predicate passed. Successful mobs are initialized and added to the level, with group limits and the population accounting updated. [Final checks and addition][attempts] · [Per-mob obstruction][mob-placement]

**A biome entry and a spawn helper are different pieces of evidence.** A helper only affects this route when registered or otherwise called. Conversely, an entity missing from the placement registry gets permissive defaults at that particular stage; that does not insert it into a biome list or bypass the later checks. Bundled creatures can have incomplete wiring, so follow the availability notes on pages such as [Rain Frog](../mobs/RainFrog.md) and [Trilocaris](../mobs/Trilocaris.md). [Registry defaults][placement-defaults]

There is no single darkness rule for every mob or dimension. The standard monster predicate reads the dimension's light settings, while species such as [Slimes](../mobs/Slime.md#finding-slimes) have their own tests. For ordinary Overworld Zombies, use the [Zombie guide](../mobs/Zombie.md#where-to-find-it). Lighting that blocks one species can leave another's route open. [Monster light dispatch][monster-light] · [Species registrations][placements]

## Player distance and active terrain

For the ordinary natural attempt, the selected position must be **more than 24 blocks from the nearest nonspectator player**, measured in three dimensions. Creative players count for this check. A separate exclusion applies within 24 blocks of the world's shared respawn position when that position is in the current dimension; this is not a radius around every player's Bed. [Position-distance checks][distance] · [Player selection][nearest-player] · [Shared respawn position][shared-spawn]

Being far enough away is only one requirement:

- **The chunk needs a nearby nonspectator player.** The chunk-selection test uses a horizontal distance of **less than 128 blocks to the chunk center**, not the distance to every block in that chunk. A Spectator alone does not provide this eligibility. [Chunk collection][chunk-selection] · [Nearby-player calculation][chunk-players]
- **The server must have usable, ticking terrain.** Collection requires an available ticking chunk; the final chunk gate also requires entity-ticking eligibility and the world border. Simulation distance affects server ticking tickets. Seeing distant terrain, or merely keeping terrain loaded, is not enough to establish eligibility. [Chunk collection][chunk-selection] · [Final chunk gate][chunk-gate] · [Simulation tickets][simulation]
- **The candidate has its own far-distance checks.** Most distance-removable categories use **128 blocks**, while `water_ambient` uses **64 blocks**, measured in three dimensions from the nearest player. Entity-type flags and species removal rules can change admission beyond those distances. [Candidate distance][candidate-checks] · [Created-mob distance][created-distance] · [Category distances][categories]

A useful starting point is to stand beyond 24 blocks from the desired spawn spaces while keeping them in active nearby terrain. Moving farther away can stop attempts or make eligible mobs despawn. The horizontal chunk-center test and the three-dimensional mob checks are different measurements; they do not establish one universal spawn-proof radius or a tested farm waiting position.

## Category caps and competition

Natural spawning shares capacity by **registered population category**, not by species. Zombies and other ordinary monsters compete within the monster category; an empty Slime room can still face competition from other monsters. The wiki's passive/neutral/hostile browsing groups are not the category definitions used by these caps. [Category counts][counting] · [Registered types][entity-types]

| Natural-spawn category | Base cap / local player threshold |
| --- | ---: |
| `monster` | 70 |
| `creature` | 10 |
| `ambient` | 15 |
| `axolotls` | 5 |
| `underground_water_creature` | 5 |
| `water_creature` | 5 |
| `water_ambient` | 20 |

`misc` is excluded from the ordinary natural categories. The numbers above are **not allowances per individual chunk**. [Category definitions][categories] · [Category filtering][category-filter]

Two gates apply:

- **Global, within the current dimension:** the threshold is the base cap multiplied by the spawn tracker's chunk count, divided by **289**, rounded down. The tracker covers chunks up to eight chunk steps from tracked players and counts overlapping chunks once. With a tracker count of 289, the threshold is the table's base value; several players do not automatically multiply capacity by their headcount. The tracker count is also separate from the later loaded/ticking checks. [Global calculation][caps] · [Tracker creation][tracker] · [Tracker population][tracker-population]
- **Local, around players:** a counted mob contributes to each nearby qualifying player's category count. A chunk can pass this gate when **at least one** of its nearby qualifying players remains below the base threshold. A busy area around one player can therefore affect nearby attempts without describing the whole dimension. [Local accounting][local-caps] · [Nearby-player calculation][chunk-players]

The count includes relevant loaded entities whose full chunk is available, regardless of whether they originally came from natural spawning, a cage, an egg or breeding. It skips `misc` and mobs with required or custom persistence. **A farm animal's refusal to distance-despawn is not, by itself, an exemption from the cap count.** Thus keeping ordinary animals nearby can reduce further natural creature attempts even though those animals remain. [Counting exclusions][counting] · [Full-chunk lookup][full-chunk] · [Animal distance-removal rule][animal-retention]

These caps gate natural attempts; they do not delete excess mobs or set a maximum herd size. The global category list is selected before the chunk loop, and successful groups can take counts beyond a threshold. Ordinary animal breeding has a separate birth path. Some biome/species combinations also apply a local spawn-density budget, so passing both category caps is not sufficient. [Caller order][natural-tick] · [Group loop][attempts] · [Birth path][breeding] · [Density budget][density]

The `creature` category is considered only on game-time multiples of **400 ticks**, nominally once every 20 seconds at 20 TPS; the other listed categories do not use that cadence filter. This is an opportunity to attempt spawning, not a promise of replacement animals every 20 seconds. Increasing `randomTickSpeed` does not increase that natural-spawn cadence: the caller sends it to block random ticking separately. [Cadence and random-tick dispatch][natural-tick] · [Category flags][categories]

## Persistence and despawning

For a mob using the shared despawn routine, the checks run in this order:

1. **Peaceful removal:** a type marked disallowed in Peaceful is discarded, even if it has required persistence.
2. **Required or custom persistence:** these bypass the ordinary distance/inactivity checks.
3. **Distance and inactivity:** if there is a nearest nonspectator player, a mob that permits distance removal is discarded beyond its category's far distance: normally 128 blocks, or 64 for `water_ambient`. Beyond **32 blocks**, an inactivity counter over **600** also allows a **1-in-800 roll per despawn check**. Being closer than 32 blocks resets that counter.

[Active server caller][despawn-caller] · [Shared despawn routine][despawn] · [Category distances][categories] · [Nonspectator selection][nearest-player]

The inactivity counter is not a stopwatch for standing still: normal AI increments it, other behavior can reset it, and the standard monster implementation can add to it faster in bright conditions. The random roll is not a fixed disappearance deadline. These are server checks on active entities, not a promise about elapsed time while their terrain is unloaded. [AI increment][ai-inactivity] · [Damage reset][damage-reset] · [Monster adjustment][monster-inactivity]

To keep a valued mob, use its documented retention route:

- A successfully used **[Name Tag](../items/NameTag.md#behavior)** sets required persistence, which also excludes that mob from the ordinary natural cap count. [Name Tag application][name-tag] · [Counting exclusions][counting]
- **Bucket-released ordinary fish** use custom persistence. Check each imported creature's own bucket guide before extending that rule to it. [Bucket release][bucket-release] · [Fish retention][fish-retention]
- **Species rules differ.** Ordinary farm animals inherit a refusal to distance-despawn; Cats have a tame-state rule; imported [Rain Frogs](../mobs/RainFrog.md) have a different retention condition. A passive appearance or an egg origin is not a universal guarantee. [Animal rule][animal-retention] · [Cat rule][cat-retention] · [Rain Frog rule][rain-frog-retention]

Persistence does **not** confer invulnerability, safe habitat or immunity to every removal path. A named monster can still be removed on Peaceful, and a [Wandering Trader](../mobs/WanderingTrader.md) has a separate departure timer. A missing mob may also have died, moved or changed form; see its species page before concluding that distance despawning was responsible. [Peaceful ordering][despawn] · [Trader timer][trader-timer]

## Troubleshooting a quiet area

1. **Identify the intended source.** A natural encounter needs a verified candidate entry. For a cage, trial, egg, breeding attempt or event, use that route's guide first.
2. **Check world settings.** Confirm `doMobSpawning`; for ordinary monsters, also check difficulty and `spawnMonsters`. Allow time for creature-category attempts.
3. **Check where players stand.** Keep the intended positions beyond the 24-block natural exclusion, and consider other nearby players, the world's shared spawn position and active terrain. A Spectator-only observation is not an ordinary spawning setup.
4. **Check the exact location and species.** Confirm dimension, biome or structure boundary, ground or fluid, clearance and the species' actual light/height conditions. An advertised biome candidate is not a guaranteed spawn.
5. **Look for category competition.** Existing mobs in other nearby rooms, caves or water may occupy capacity. More floor space alone does not clear a cap or a density budget.
6. **If mobs appear and disappear, check retention.** Distinguish ordinary distance removal, Peaceful removal, species timers and habitat damage. Use a documented Name Tag, bucket or species-specific route when keeping a mob.

These checks identify source-defined failure points. They do not establish a seed-specific location, an optimized farm layout or a measured output rate.

Related: [Mechanics](Mechanics.md) · [Mobs](../mobs/Mobs.md) · [Biomes](../biomes/Biomes.md) · [Dimensions](../dimensions/Dimensions.md) · [Name Tags](../items/NameTag.md) · [Time, weather, and sleep](TimeWeatherAndSleep.md#read-the-day-cycle)

## Sources and verification

Source-reviewed on **2026-10-04** at `f5473e41dc4af8ced756db517fada27288df07a3`. The review follows the server-level/chunk caller through category counting, player/chunk eligibility, biome/structure selection, registered and per-mob predicates, addition and despawning. It also checks the separate generation/breeding routes and selected persistence exceptions. No in-game spawning, despawn timing, multiplayer, simulation-distance or farm test was run. Active data packs, dimensions, game rules and custom entity data can change the relevant inputs. Species-specific acquisition and environmental conditions remain with their existing guides.

[natural-tick]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L338-L424
[attempts]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L150-L225
[worldgen-caller]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/chunk/status/ChunkStatusTasks.java#L176-L183
[worldgen-generator]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/levelgen/NoiseBasedChunkGenerator.java#L442-L450
[worldgen-spawns]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L360-L434
[monster-setting]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/MinecraftServer.java#L1445-L1476
[rules]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/GameRules.java#L233-L243
[selection]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L335
[structure-selection]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[placement-types]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L11-L49
[placements]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L55-L178
[candidate-checks]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L244-L266
[mob-placement]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/Mob.java#L728-L748
[placement-defaults]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L64-L83
[monster-light]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/monster/Monster.java#L86-L118
[distance]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L227-L241
[nearest-player]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/EntityGetter.java#L75-L100
[shared-spawn]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerLevel.java#L1395-L1398
[chunk-selection]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ChunkMap.java#L932-L944
[chunk-players]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ChunkMap.java#L973-L1005
[chunk-gate]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerLevel.java#L1805-L1806
[simulation]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/DistanceManager.java#L112-L163
[created-distance]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L284-L287
[categories]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/MobCategory.java#L6-L58
[counting]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L68-L95
[entity-types]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/EntityType.java
[category-filter]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L60-L63
[caps]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L534-L540
[tracker]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/DistanceManager.java#L40-L48
[tracker-population]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/DistanceManager.java#L186-L240
[local-caps]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/LocalMobCapCalculator.java#L24-L55
[full-chunk]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L427-L431
[animal-retention]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L124
[breeding]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/animal/Animal.java#L205-L227
[density]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L490-L523
[despawn-caller]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/server/level/ServerLevel.java#L388-L402
[despawn]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/Mob.java#L598-L630
[ai-inactivity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/Mob.java#L633-L638
[damage-reset]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1150-L1161
[monster-inactivity]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/monster/Monster.java#L42-L53
[name-tag]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/NameTagItem.java#L16-L27
[bucket-release]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/item/MobBucketItem.java#L28-L50
[fish-retention]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/animal/AbstractFish.java#L47-L54
[cat-retention]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/animal/Cat.java#L432-L434
[rain-frog-retention]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/alexsmobs/entity/EntityRainFrog.java#L249-L255
[trader-timer]: https://github.com/HungLo2020/MattMC/blob/f5473e41dc4af8ced756db517fada27288df07a3/src/main/java/net/minecraft/world/entity/npc/WanderingTrader.java#L209-L220

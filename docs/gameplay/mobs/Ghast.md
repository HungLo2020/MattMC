# Ghast

A **Ghast** (`minecraft:ghast`) is a hostile flying mob that fires explosive fireballs at players. Keep a route to solid cover when crossing open Nether terrain. Its drops include **Ghast Tears**, and returning a fireball can also earn the **Tears music disc**. This is a different creature from the rideable [Happy Ghast](HappyGhast.md); a [Dried Ghast](../blocks/DriedGhast.md) hatches the latter. [Identity][ghast-id] · [Targeting][ghast-target] · [Fireballs][fireball] · [Drops][ghast-loot] · [Disc condition][ghast-disc]

## Obtaining

The bundled biome monster lists include Ghasts in **[Nether Wastes](../biomes/NetherBiomes.md#nether-wastes), [Soul Sand Valley](../biomes/NetherBiomes.md#soul-sand-valley), and [Basalt Deltas](../biomes/NetherBiomes.md#basalt-deltas)**. Soul Sand Valley adds a local spawn-cost check, so list weights alone do not predict encounter rates. Use the [Nether biome guide](../biomes/NetherBiomes.md) to choose a route. [Wastes list][wastes] · [Valley list and cost][valley] · [Active cost check][spawn-costs] · [Deltas list][deltas]

Natural spawning still needs the registered ground placement, suitable support and clearance for the Ghast's large body. Its own predicate rejects Peaceful and applies a **1-in-20 random check**; that is one condition on a spawn attempt, not a 5% chance per chunk or minute. The species also limits its spawn cluster to one even where a biome entry requests a larger group. [Placement registration][ghast-placement] · [Species checks][ghast-spawn] · [Cluster-limit caller][cluster-call] · [Support and obstruction][base-spawn] · [Natural placement and collision][spawn-checks]

The category-listed [Ghast Spawn Egg](../items/GhastSpawnEgg.md) is available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival and Creative**. This insertion/placement route is separate from finding a naturally spawned Ghast. [Egg listing][ghast-egg]

## Behavior

### Fighting a Ghast

The default maximum is **10 health points (5 hearts)**. Its registered body is **4 blocks wide and 4 blocks tall**. It flies, has no ordinary fall-damage response, and is registered fire-immune; setting it on fire is not a reliable attack. [Registered attributes][ghast-registration] · [Health][ghast-attributes] · [Size and fire immunity][ghast-id] · [Flight][ghast-flight] · [Fire-damage gate][fire-immunity]

The player-target goal requires an attackable player and a vertical difference of at most **4 blocks when choosing a target**. This is a target-acquisition condition, not a guarantee that moving five blocks vertically ends an existing fight. Its firing goal requires line of sight and a target **less than 64 blocks away**. [Target goal][ghast-target] · [Firing checks][ghast-shot]

With uninterrupted visibility, the first shot takes **20 goal ticks**, about one second at normal 20 TPS. A warning occurs at tick 10, the charging appearance follows, and the shot resets the counter to −40, giving about **60 eligible ticks between later shots**. Breaking line of sight prevents further charging and reduces a positive charge; cover does not erase a fireball already in flight. [Charging and firing][ghast-shot]

Fireballs cause a direct hit and an explosion, so do not treat their base direct-hit value as the complete damage you will receive. The default explosion power is **1**. The `mobGriefing` rule controls the projectile's fire creation and its block-destruction path; it does not remove the entity-hit branch. Damage, protection and terrain still matter. [Impact and explosion][fireball] · [Block-destruction rule][mob-explosion]

### Returning a fireball

Aim toward the Ghast and **attack the incoming fireball**. Fireballs belong to the redirectable-projectile tag, and the player attack path redirects them along the player's look direction while assigning the player as owner. Timing and aim still need practice; this is not an automatic reflection shield. [Redirectable tag][redirectable] · [Attack dispatch][player-deflect] · [Aim direction][aim-deflect] · [New ownership][deflect-owner]

A Large Fireball whose responsible entity is a player triggers the Ghast's special **1,000-damage** response, normally killing an unmodified Ghast. That route also bypasses its ordinary fire-damage immunity, but does not promise to defeat custom invulnerability. [Reflected-fireball handling][ghast-reflection]

## Drops

With mob loot enabled, the bundled table rolls **0–1 Ghast Tear** and **0–2 Gunpowder**. Neither ordinary pool requires a player-attributed kill. Looting increases each possible maximum by one per level, giving maxima of **4 Tears and 5 Gunpowder with Looting III**; it does not guarantee those amounts. [Loot table][ghast-loot] · [Looting count calculation][looting-count] · [Loot gate][animal-loot]

A separate pool gives **one [Music Disc (Tears)](../items/MusicDiscTears.md)** when the killing damage is a projectile with a direct `minecraft:fireball` entity **and** the death has player attribution. Returning a fireball for a direct hit is the intended practical route through those checked conditions; an ordinary arrow kill or explosion-only kill does not satisfy the direct-fireball projectile condition. [Disc conditions][ghast-disc] · [Projectile damage tag][projectile-tag] · [Player-attribution test][player-kill] · [Death context][loot-call]

Save Tears for [Dried Ghasts](../blocks/DriedGhast.md#obtaining), [End Crystals](../items/EndCrystal.md#crafting), and brewing; follow [Ghast Tear](../items/GhastTear.md) for the ingredient's uses. Plan where the drops will land before fighting above Lava.

## Notes

The Ghast's entity registration uses the monster category and removes it on Peaceful. A name or other persistence flag does not override that difficulty removal. It is not a tameable growth stage of a Happy Ghast. [Registration][ghast-id] · [Despawn ordering][despawn]

Related: [Mobs](Mobs.md) · [Nether](../dimensions/Nether.md) · [Nether biomes](../biomes/NetherBiomes.md) · [Happy Ghast](HappyGhast.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `780a7733d1804088e86c248a1c8ec6957def4915`. Checked registration, active goals/attributes, loaded biome candidates, spawn predicates, reflection and explosion callers, and loot conditions. No in-game spawn-rate, projectile-timing, combat, reflection or drop test was run. Server tick rate, game rules, data packs and entity data can change results. [Server AI dispatch][ai-call]

The checked world-generation registry loads biome and structure resources, and natural spawning reads their active lists before placement and collision checks. Listed candidates are not guaranteed encounters. Living entities take their registered default attributes on construction; entity deaths resolve the species' entity loot table through the reloadable loot registry. [World loader][world-loader] · [Registry entries][registry-list] · [Resource loading][registry-load] · [Spawn-list caller][spawn-selection] · [Biome/structure lookup][spawn-tables] · [Placement checks][spawn-checks] · [Loot identity][loot-id] · [Loot registry][loot-registry] · [Loot loading][loot-load] · [Attribute construction][attribute-call] · [Death loot caller][loot-call]

[ghast-id]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/EntityType.java#L661-L671
[ghast-target]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Ghast.java#L49-L64
[fireball]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/projectile/LargeFireball.java#L25-L49
[ghast-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/loot_table/entities/ghast.json#L1-L63
[ghast-disc]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/loot_table/entities/ghast.json#L64-L99
[wastes]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json#L79-L109
[valley]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/worldgen/biome/soul_sand_valley.json#L73-L120
[spawn-costs]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L490-L501
[deltas]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/worldgen/biome/basalt_deltas.json#L83-L96
[ghast-placement]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L121-L122
[ghast-spawn]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Ghast.java#L151-L162
[cluster-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L193-L216
[base-spawn]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Mob.java#L728-L741
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[ghast-egg]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2023
[ghast-registration]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L171-L172
[ghast-attributes]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Ghast.java#L118-L124
[ghast-flight]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Ghast.java#L88-L100
[fire-immunity]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Entity.java#L2878-L2886
[ghast-shot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Ghast.java#L331-L379
[mob-explosion]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/server/level/ServerLevel.java#L1156-L1164
[redirectable]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/tags/entity_type/redirectable_projectile.json#L1-L7
[player-deflect]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/player/Player.java#L974-L987
[aim-deflect]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/projectile/ProjectileDeflection.java#L18-L24
[deflect-owner]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L250-L258
[ghast-reflection]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Ghast.java#L78-L109
[looting-count]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L79
[animal-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[projectile-tag]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/tags/damage_type/is_projectile.json#L1-L13
[player-kill]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[loot-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[despawn]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[ai-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Mob.java#L633-L662
[world-loader]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-list]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[registry-load]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L335
[spawn-tables]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[loot-id]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-registry]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L20-L28
[loot-load]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[attribute-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L274

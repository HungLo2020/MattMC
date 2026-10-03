# Breeze

A **Breeze** (`minecraft:breeze`) is a hostile Trial Chamber mob that jumps and slides around targets while firing wind projectiles. It has **30 base health points (15 hearts)**. Its push attacks can turn ledges, open passages and nearby mechanisms into hazards; its ordinary projectile deflection also makes a bow an unreliable default plan. [Identity][breeze-id] · [Attribute registration][breeze-registration] · [Health][breeze-stats] · [Combat activities][breeze-brain] · [Deflection][breeze-deflect]

## Obtaining

The checked world-generation route is a **[Trial Spawner](../blocks/TrialSpawner.md)** configured for Breezes. Connected Trial Chamber templates lead to a dedicated Breeze contents pool; its actual spawner template stores both `trial_chamber/breeze/normal` and `trial_chamber/breeze/ominous` configuration references. Both resources name `minecraft:breeze`. A particular chamber's layout and spawner placement are not guaranteed by the pool entry alone. [Connected chamber][trial-assembly] · [Contents pool][breeze-trial-pool] · [Spawner template][breeze-trial-template] · [Normal configuration][breeze-trial-normal] · [Ominous configuration][breeze-trial-ominous]

No Breeze entry was found in the **68 bundled biome definitions or 34 structure definitions' ordinary spawn lists** at this snapshot. Trial Chamber monster overrides are empty; the block's configured encounter is a separate mechanism. Its registered any-light ground-spawn predicate alone does not create a natural population. [Placement registration][breeze-placement] · [Any-light predicate][monster-spawn] · [Chamber override][trial-no-monsters] · [Ordinary list selection][spawn-selection] · [Structure/biome lookup][spawn-tables]

Follow [Trial Spawner activation](../blocks/TrialSpawner.md#activation-and-participants), [wave scaling](../blocks/TrialSpawner.md#wave-size-and-spawning) and [clearing/rewards](../blocks/TrialSpawner.md#clearing-rewards-and-cooldown) before beginning. The normal and ominous configurations differ; neither is an endless unrestricted mob stream. Ordinary Trial Spawner gates include non-Peaceful difficulty and the relevant spawning rules. [Spawner gates][trial-gate] · [Active configuration][trial-active]

The listed [Breeze Spawn Egg](../items/BreezeSpawnEgg.md) is separately available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. [Egg listing][breeze-egg]

## Behavior

### Movement and targeting

The active sensor finds attackable living targets, excluding Creative/Spectator players, while the species permits **players and Iron Golems** as attackable types. Its brain combines shooting, long jumps and sliding, and also has a hurt-response target path. The default follow-range attribute is **24 blocks**, but target memory and other conditions mean this is not a universal safe-distance boundary. [Sensor][breeze-sensor] · [Permitted types and brain tick][breeze-targets] · [Activities][breeze-brain] · [Attributes][breeze-stats]

It can choose a jump landing behind a target after checking a surface and visibility. On landing it schedules shooting; sliding can also reposition it when close to the target. Water, riding another entity or Levitation can activate a separate shoot-when-stuck behavior. **Preventing a jump does not necessarily prevent shooting.** Keep track of its new position instead of swinging at where it stood before. [Jump conditions][breeze-jump] · [Landing and shoot request][breeze-landing] · [Sliding][breeze-slide] · [Stuck shooting][breeze-stuck]

### Wind shots and nearby mechanisms

A shooting attempt requires a target **less than 16 blocks away** when that action begins. It uses a **15-tick charge**, a 4-tick recovery memory and a 10-tick cooldown, but other AI state controls when another attempt becomes available. These are not a fixed repeating fire rate. [Shoot conditions and timers][breeze-charge] · [Charge, projectile and distance check][breeze-shot]

The projectile is **`minecraft:breeze_wind_charge`**, distinct from the player's throwable [Wind Charge](../items/WindCharge.md). Its direct hit attempts **1 damage point before defenses**, then produces a burst with explosion power **3**. That burst is configured for **knockback without explosion damage**; being thrown into a fall or other hazard can still hurt. [Breeze projectile][breeze-projectile] · [Direct hit][wind-direct] · [Burst calculator selection][wind-calc] · [Damage/knockback separation][burst-calculator]

It uses trigger-style block interaction. Supported mechanisms can react through the same block handlers documented under [Wind Charge bursts](../items/WindCharge.md#direct-hits-and-the-burst), including eligible buttons, levers, doors, trapdoors, bells and candles. **For the Breeze projectile specifically, burst-driven block triggers require `mobGriefing`.** Disabling that rule does not remove the direct entity-hit branch, and a direct hit on an eligible Bell can still ring it. [Burst type][breeze-projectile] · [Breeze-specific trigger gate][breeze-triggers] · [Entity hit][wind-direct] · [Direct block callback][wind-block-hit] · [Bell direct-hit path][bell-direct-hit]

A Breeze shot does **not** grant the special fall-damage allowance attached to the player's `minecraft:wind_charge` projectile. Do not treat being launched by a Breeze as a safe substitute for a controlled player Wind Charge jump. [Exact projectile-type allowance][fall-allowance]

### Deflection and immunities

The bundled deflection tag contains the Breeze. Its deflection handler reverses incoming projectiles through the common collision path, **except** player Wind Charges and Breeze Wind Charges. Melee avoids that projectile-deflection branch, while wind projectiles still have their own direct-hit, owner and immunity rules. This is not a guarantee that every custom projectile behaves identically. [Deflection tag][deflect-tag] · [Exceptions][breeze-deflect] · [Active collision dispatch][deflect-call]

It is immune to damage attributed to a Breeze and belongs to the **fall-damage-immune** entity tag. It is **not registered fire-immune**. These are separate checks; its floating appearance is not proof of immunity to every environmental hazard. [Breeze-source immunity][breeze-immune] · [Fall tag][fall-tag] · [Base damage-tag gate][fire-immunity] · [Entity registration][breeze-id]

## Drops

With mob loot enabled and **player-attributed death**, the bundled table gives **1–2 [Breeze Rods](../items/BreezeRod.md)**. The table's Looting increase makes the final range **4–8 with Looting III**. Without player attribution, this Rod pool does not run. [Exact Rod table][breeze-loot] · [Looting calculation][looting-count] · [Attribution condition][player-kill] · [Death context][loot-call] · [Mob-loot gate][monster-loot]

Use the existing [Wind Charge recipe](../items/WindCharge.md#obtaining), [Mace crafting](../items/Mace.md#obtaining) and [Mace repair](../items/Mace.md#durability-repair-and-a-broken-mace) guides for Rod uses. A Breeze's projectile is not a collectible Wind Charge item, and Trial Spawner reward ejection is separate from its entity loot. See [Trial rewards](../blocks/TrialSpawner.md#clearing-rewards-and-cooldown). [Projectile removal and item representation][wind-pickup]

## Notes

The mob is registered as a monster and disallowed in Peaceful. Trial-spawned persistence does not override the common Peaceful-removal check. The player Wind Charge item and Mace Wind Burst enchantment remain separate systems with their own behavior. [Registration][breeze-id] · [Trial persistence][trial-spawn] · [Removal ordering][despawn]

Related: [Mobs](Mobs.md) · [Trial Spawner](../blocks/TrialSpawner.md) · [Wind Charge](../items/WindCharge.md) · [Breeze Rod](../items/BreezeRod.md) · [Mace](../items/Mace.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `f9e4a4c96fcf7fb744bce869c085cdfab189810e`. Checked registration/attributes, actual biome/structure definition inventories, connected Trial Chamber spawner/configuration, active movement/shooting, deflection and damage tags, burst/fall-allowance differences and loot. No in-game chamber generation, wave, combat, launch, mechanism-trigger or drop test was run. Tags, data packs, rules, entity data and ticking can change results; no safe arena or farm design is certified.

The checked Trial Chamber route follows connected templates and their saved normal/ominous configuration references. The configuration registry loads those resources; the active block ticker advances the Trial Spawner state and calls the entity-spawn path. A configuration entry does not guarantee a particular chamber layout or a successful spawn. [Connected chamber template][trial-assembly] · [Template placement][pool-place] · [Block-entity loading][block-entity-place] · [Spawner data loading][trial-load] · [Configuration registry][trial-registry] · [Saved configuration references][trial-holder] · [Active selection][trial-active] · [Block ticker][trial-ticker] · [State caller][trial-state] · [Spawn/initialization checks][trial-spawn]

World loading reads the bundled biome/structure resources, and the natural-spawn caller selects their active lists before placement checks. Registered attributes are applied when living entities are constructed; deaths load the species' entity loot table from the reloadable registry. [World loader][world-loader] · [Registry inputs][registry-list] · [Resource loading][registry-load] · [Spawn selection][spawn-selection] · [Structure/biome lookup][spawn-tables] · [Natural checks][spawn-checks] · [Attribute construction][attribute-call] · [Loot identity][loot-id] · [Loot registry][loot-registry] · [Loot loading][loot-load] · [Death loot caller][loot-call]

[breeze-id]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntityType.java#L345-L347
[breeze-registration]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L165
[breeze-stats]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/Breeze.java#L62-L75
[breeze-brain]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/BreezeAi.java#L68-L99
[breeze-deflect]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/Breeze.java#L193-L200
[trial-assembly]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/structure/trial_chambers/chamber/assembly.nbt
[breeze-trial-pool]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/spawner/contents/breeze.json#L1-L16
[breeze-trial-template]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/structure/trial_chambers/spawner/breeze/breeze.nbt
[breeze-trial-normal]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/trial_spawner/trial_chamber/breeze/normal.json#L1-L17
[breeze-trial-ominous]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/trial_spawner/trial_chamber/breeze/ominous.json#L1-L26
[breeze-placement]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L110
[monster-spawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L83-L119
[trial-no-monsters]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json#L117-L120
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L335
[spawn-tables]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[trial-gate]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L147-L155
[trial-active]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L77-L92
[breeze-egg]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1987
[breeze-sensor]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/sensing/BreezeAttackEntitySensor.java#L19-L31
[breeze-targets]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/Breeze.java#L222-L249
[breeze-jump]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/LongJump.java#L67-L102
[breeze-landing]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/LongJump.java#L112-L154
[breeze-slide]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/Slide.java#L32-L57
[breeze-stuck]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/ShootWhenStuck.java#L29-L39
[breeze-charge]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/Shoot.java#L19-L48
[breeze-shot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/Shoot.java#L67-L103
[breeze-projectile]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/windcharge/BreezeWindCharge.java#L18-L40
[wind-direct]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/windcharge/AbstractWindCharge.java#L72-L88
[wind-calc]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/windcharge/AbstractWindCharge.java#L28-L31
[burst-calculator]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/SimpleExplosionDamageCalculator.java#L12-L49
[breeze-triggers]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/ServerExplosion.java#L291-L306
[fall-allowance]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1272-L1278
[deflect-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/entity_type/deflects_projectiles.json#L1-L5
[deflect-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L222-L242
[breeze-immune]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/breeze/Breeze.java#L265-L282
[fall-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/entity_type/fall_damage_immune.json#L1-L22
[fire-immunity]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/Entity.java#L2878-L2886
[breeze-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/entities/breeze.json#L1-L41
[looting-count]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L79
[player-kill]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[loot-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[monster-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L125-L133
[wind-pickup]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/windcharge/AbstractWindCharge.java#L109-L125
[trial-spawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L180-L233
[despawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[pool-place]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L137-L181
[block-entity-place]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L292-L310
[trial-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/TrialSpawnerBlockEntity.java#L35-L42
[trial-registry]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L110-L115
[trial-holder]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L382-L393
[trial-ticker]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/TrialSpawnerBlock.java#L47-L56
[trial-state]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L55-L104
[world-loader]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-list]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[registry-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[attribute-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L274
[loot-id]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-registry]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L20-L28
[loot-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[wind-block-hit]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/windcharge/AbstractWindCharge.java#L97-L106
[bell-direct-hit]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/BellBlock.java#L80-L144

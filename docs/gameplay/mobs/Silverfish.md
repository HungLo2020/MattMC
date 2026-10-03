# Silverfish

A **Silverfish** (`minecraft:silverfish`) is a small hostile mob that can emerge from infested masonry, spawners or the Infested effect. **An encounter can release more Silverfish from nearby blocks**, so make room to retreat before breaking suspicious stone. Its ordinary mob-loot table has no item rewards. [Registration][silver-id] · [Wake-up behavior][wake] · [Empty loot][silver-loot]

## Obtaining

### Infested blocks

Breaking an infested block through its normal drop callback can release one Silverfish when `doTileDrops` is enabled. A tool carrying an enchantment from the prevention tag suppresses that release; the bundled tag contains **Silk Touch**. Follow [infested stone variants](../blocks/Stone.md#infested-stone-variants) and [Infested Deepslate](../blocks/Deepslate.md#infested-deepslate) for host blocks, world-generation routes and the separate block-item drops. These block callbacks create the mob directly; they are not ordinary biome-spawn attempts. [Break and creation callback][infested] · [Active drop dispatch][block-drops] · [Prevention tag][silk]

### Spawners

- **Stronghold portal rooms:** their generation code installs an ordinary [Monster Spawner](../blocks/MonsterSpawner.md) configured for Silverfish. See [Stronghold](../structures/Stronghold.md) for the surrounding structure. [Portal-room placement][stronghold] · [Spawner execution][spawner]
- **Trial Chambers:** the small-melee pool alias can select Silverfish. The checked connector and resident template lead to a [Trial Spawner](../blocks/TrialSpawner.md) with Silverfish in both its normal and ominous configurations. This is a configured trial encounter, not a Silverfish population in every chamber. Follow that block's guide for activation, wave counts and rewards. [Alias selection][trial-alias] · [Connector template][trial-connector] · [Selected pool][trial-pool] · [Spawner template][trial-template] · [Normal configuration][trial-normal] · [Ominous configuration][trial-ominous] · [Alias caller][jigsaw] · [Spawner server tick][trial-tick] · [Active spawning][trial-spawn]

**Torches are not a reliable general off switch for a Silverfish spawner.** Its registered predicate does not require darkness. An ordinary spawner also runs a later mob check: positions over compatible infestation hosts pass that check regardless of light, while other positions can be rejected for brightness. Difficulty, activation, clearance and any custom spawner rules still matter. [Placement registration][silver-placement] · [Species predicate][silver-spawn] · [Any-light monster gate][monster-gate] · [Later spawner check][spawner-mob-check] · [Inherited mob check][pathfinder-spawn] · [Host preference][silver-walk] · [Light-sensitive fallback][monster-walk] · [Light cost][light-cost]

Spawner and Trial Spawner reasons skip the species predicate's non-spawner check for a nearby eligible player within 5 blocks. No Silverfish candidate was found in the checked bundled biome lists or structure spawn overrides; those are separate from placed spawners and triggered releases. [Species predicate][silver-spawn] · [Spawner reason classification][spawn-reason]

### Infested effect and supplied eggs

See [Infested](../effects/TriggeredEffects.md#infested) for damage gates, lethal-hit ordering, launch positions, and effect clearing.

When a living entity with **Infested** reaches the effect's hurt callback, the bundled effect has a **10% chance** to create **1–2 Silverfish** around that entity. The effect does not scale this count or chance with its amplifier. Silverfish themselves are in the Infested-immunity tag, so the ordinary effect application cannot create a self-repeating chain on them. This route does not require breaking an infested block. [Effect registration][infested-effect] · [Hurt callback and creation][effect-create] · [Damage dispatch][effect-hurt] · [Immunity gate][effect-immunity] · [Immunity tag][immune-tag]

The brewing registration uses **Stone with an Awkward Potion** for the Infested potion; starting with a Water Bottle instead makes a Mundane Potion. See [Brewing](../brewing/Brewing.md) for the brewing stand process. [Ingredient registration][infested-brew] · [Starting-potion selection][brew-start]

The listed [Silverfish Spawn Egg](../items/SilverfishSpawnEgg.md) is available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Survival and Creative**. That insertion/placement route is separate from the encounter systems above. [Egg category entry][silver-egg]

## Behavior

### Combat and waking neighbors

Default maximum health is **8 points (4 hearts)**, with a base attack attribute of **1**. Actual damage depends on difficulty and defenses. The registered body is **0.4 blocks wide and 0.3 blocks tall**. It targets attackable players and retaliates against attackers; the hurt-target goal can also alert nearby idle Silverfish. [Registered attributes][silver-defaults] · [Goals and attributes][silver-goals] · [Size][silver-id] · [Shared alert][hurt-alert]

A separate hurt-response path schedules a search for infested blocks after a short delay. A responsible damage entity or the `always_triggers_silverfish` damage tag can trigger it; the bundled tag contains magic damage. This scheduling occurs before the shared damage call, so it should not be simplified to a fixed number of successful hits. [Hurt-response caller][silver-hurt] · [Trigger tag][wake-tag] · [Delayed search][wake]

The search covers offsets up to **10 blocks horizontally and 5 vertically**. It may stop early after a matching block, so do not treat one response as a guaranteed full-area release or cleanup. With `mobGriefing` enabled it destroys matching infested blocks, reaching the release callback when block drops are enabled; with `mobGriefing` disabled it replaces them with their ordinary hosts instead. [Search and rule branches][wake] · [Block-drop release][infested]

### Hiding and containment

With no attack target, finished navigation and a successful random check, a Silverfish can merge into an adjacent compatible host block when `mobGriefing` is enabled. It replaces that block with the corresponding infested state and discards the moving mob. A disappearing Silverfish near masonry is therefore not necessarily a kill. Use the [stone](../blocks/Stone.md#infested-stone-variants) and [deepslate](../blocks/Deepslate.md#infested-deepslate) guides to identify compatible hosts. [Merge conditions and result][merge] · [Host mapping][infested-hosts]

It does not have a sunlight-burning routine in its checked tick path. Its registration is disallowed in **Peaceful**, and the shared Peaceful-removal check runs before ordinary persistence checks. Neither daylight nor a name should be confused with a general containment method. [Tick path][silver-tick] · [Inherited monster tick][monster-tick] · [Registration][silver-id] · [Despawn ordering][despawn]

## Drops

The bundled Silverfish entity table has **no item pools**, so it supplies no ordinary item drop and Looting cannot add an item to that empty table. A qualifying player-attributed death has **5 base experience**, with mob loot enabled. Custom equipment or entity data can change separate death behavior. Trial Spawner rewards belong to the spawner encounter, not to this empty mob table. [Empty table][silver-loot] · [Base experience][silver-xp] · [Experience calculation][xp-count] · [Death and experience gates][death]

## Notes

This is a monster-category mob, distinct from the purple, short-lived [Endermite](Endermite.md). An ordinary infestation release does not establish a natural biome-spawn population. [Registration][silver-id]

Related: [Mobs](Mobs.md) · [Stone](../blocks/Stone.md) · [Deepslate](../blocks/Deepslate.md) · [Stronghold](../structures/Stronghold.md) · [Trial Spawner](../blocks/TrialSpawner.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `1d7e3e91f2a2694339f78b8673993e98d496ca5f`. Checked registered attributes and active goals, direct creation paths, loot/experience dispatch, and bundled resource loading. No game client/server or in-game encounter, timing, damage, spawn-frequency or loot test was run. Custom entity data, data packs, game rules and ticking can change results. [Attribute construction][attribute-call] · [Server AI dispatch][ai-call]

The spawn-list audit covers the packaged biome and structure definitions selected by the active world-generation registry loader, not similarly named tag files or reference source trees. Deaths resolve the entity's `entities/<id>` loot table through the reloadable registry; its active directory is the singular `loot_table`. [World loader][world-loader] · [Loaded registries][registry-list] · [Registry resource reader][registry-load] · [Directory keys][registry-keys] · [Resource path conversion][resource-paths] · [Spawn-list caller][spawn-selection] · [Entity loot identity][loot-id] · [Loot loading][loot-load] · [Loot directory reader][loot-directory] · [Death loot caller][loot-call]

[attribute-call]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L274
[loot-call]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[death]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[loot-id]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-load]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[loot-directory]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/packs/resources/SimpleJsonResourceReloadListener.java#L49-L67
[registry-keys]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/core/registries/Registries.java#L238-L279
[world-loader]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-list]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L115
[registry-load]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[resource-paths]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/resources/FileToIdConverter.java#L19-L37
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L335
[despawn]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[monster-gate]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Monster.java#L115-L133
[hurt-alert]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L60-L115
[ai-call]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/Mob.java#L633-L662
[silver-id]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/EntityType.java#L1199-L1207
[silver-loot]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/entities/silverfish.json#L1-L4
[infested]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/block/InfestedBlock.java#L51-L66
[block-drops]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/block/Block.java#L364-L386
[silk]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/tags/enchantment/prevents_infested_spawns.json#L1-L5
[stronghold]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/levelgen/structure/structures/StrongholdPieces.java#L850-L859
[spawner]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/BaseSpawner.java#L84-L175
[trial-alias]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json#L75-L97
[trial-connector]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/structure/trial_chambers/spawner/connectors/small_melee.nbt
[trial-pool]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/spawner/small_melee/silverfish.json#L1-L16
[trial-template]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/structure/trial_chambers/spawner/small_melee/silverfish.nbt
[trial-normal]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/trial_spawner/trial_chamber/small_melee/silverfish/normal.json#L1-L15
[trial-ominous]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/trial_spawner/trial_chamber/small_melee/silverfish/ominous.json#L1-L26
[jigsaw]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L135-L153
[trial-tick]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/block/TrialSpawnerBlock.java#L48-L61
[trial-spawn]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L147-L224
[silver-placement]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L142
[silver-spawn]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Silverfish.java#L113-L124
[spawn-reason]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/EntitySpawnReason.java#L25-L31
[infested-effect]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/effect/MobEffects.java#L124-L126
[effect-create]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/effect/InfestedMobEffect.java#L27-L51
[effect-hurt]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1245-L1253
[effect-immunity]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1003-L1006
[immune-tag]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/tags/entity_type/immune_to_infested.json#L1-L5
[infested-brew]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L141-L145
[brew-start]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/alchemy/PotionBrewing.java#L230-L235
[silver-egg]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2085
[silver-defaults]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L236
[silver-goals]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Silverfish.java#L42-L56
[silver-hurt]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Silverfish.java#L83-L94
[wake-tag]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/tags/damage_type/always_triggers_silverfish.json#L1-L5
[wake]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Silverfish.java#L189-L227
[merge]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Silverfish.java#L136-L178
[infested-hosts]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/block/InfestedBlock.java#L35-L49
[silver-tick]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Silverfish.java#L96-L100
[monster-tick]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Monster.java#L42-L54
[silver-xp]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Monster.java#L31-L35
[xp-count]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/Mob.java#L290-L306
[spawner-mob-check]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/BaseSpawner.java#L149-L159
[pathfinder-spawn]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/PathfinderMob.java#L28-L30
[silver-walk]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Silverfish.java#L108-L110
[monster-walk]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Monster.java#L81-L84
[light-cost]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/LevelReader.java#L113-L121

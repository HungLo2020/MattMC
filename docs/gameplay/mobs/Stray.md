# Stray

A **Stray** (`minecraft:stray`) is a hostile Skeleton relative whose ordinary arrows add **Slowness**. It has **20 base health points (10 hearts)** and shares the Skeleton's bow combat, sunlight behavior and equipment handling. Plan cover before crossing an exposed snowy area: being slowed can make the next shot harder to avoid. [Identity][stray-id] · [Attribute registration][stray-registration] · [Shared attributes][skeleton-targets] · [Base health][health-default] · [Slowness arrow][stray-arrow]

## Obtaining

The bundled natural biome lists name **Snowy Plains and Ice Spikes**, each with weight **80** and a requested group of **4**. These are the two checked biome entries for this snapshot; a snowy name alone does not establish a Stray spawn list. Use [Taiga and snowy biomes](../biomes/TaigaAndSnowyBiomes.md) for exploration choices. [Snowy Plains][snowy-plains] · [Ice Spikes][ice-spikes]

Natural spawning needs the ordinary monster darkness/difficulty checks and registered ground placement. The Stray's extra check looks upward through Powder Snow and requires visible sky from the last Powder Snow position (or the spawn position when there is no such column). Spawner and Trial Spawner reasons bypass that sky check; ordinary natural spawning does not. [Placement registration][stray-placement] · [Sky and Powder Snow check][stray-spawn] · [Monster predicate][monster-spawn] · [Spawner reasons][spawner-reason]

Two further routes matter:

- **Powder Snow conversion of an ordinary Skeleton.** With AI enabled, sustained exposure starts a conversion counter and eventually creates a Stray. Leaving Powder Snow cancels the conversion. Follow [Skeleton conversion](Skeleton.md#powder-snow-conversion) for the roughly 7-second exposure plus 15-second conversion stages and equipment preservation. [Exposure and reset][skeleton-convert] · [Conversion result][skeleton-convert-result] · [Equipment transfer][conversion-equipment]
- **Trial Chamber spawners.** A ranged alias group can select Stray templates, with both normal and ominous configurations. The checked connected template stores those resource IDs. This route works underground because the spawn reason bypasses the sky and ordinary light requirements, while the Trial Spawner's other conditions still apply. [Aliases][trial-aliases] · [Alias construction][alias-build] · [Active alias lookup][alias-lookup] · [Pool][stray-trial-pool] · [Template][stray-trial-template] · [Normal][stray-trial-normal] · [Ominous][stray-trial-ominous] · [Spawn-reason handling][spawner-reason]

A category-listed [Stray Spawn Egg](../items/StraySpawnEgg.md) is a separate option in MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), including **Survival and Creative** insertion. [Egg listing][stray-egg]

## Behavior

### Slowness arrows

The ordinary bow attack adds **Slowness I for 600 ticks**, normally **30 seconds at 20 TPS**, to its usual Arrow projectile. A successful hit applies that effect as well as the arrow's damage. Custom ammunition/components can alter duration handling; this duration describes the normal ordinary-arrow route. [Effect addition][stray-arrow] · [Shared projectile caller][skeleton-arrows] · [Arrow factory][mob-arrow] · [Default ammunition][ordinary-ammo] · [Duration-scale lookup][arrow-effects] · [Successful-hit gate][arrow-hit-gate] · [Effect application][arrow-hit]

It uses the shared bow cooldown of **40 ticks outside Hard** and **20 ticks on Hard**, followed by the bow's drawing and visibility requirements. These cooldowns are not exact shot-to-shot periods. It can strafe, target players and Iron Golems, attack eligible baby Turtles on land, and retaliate against eligible attackers. The shared goals also avoid Wolves. Follow [Skeleton combat](Skeleton.md#fighting-and-staying-safe) for common positioning and weapon behavior. [Cooldown and ranged code][skeleton-arrows] · [Difficulty/weapon selection][skeleton-equipment] · [Bow drawing][bow-draw] · [Shared targets][skeleton-targets]

[Milk](../items/MilkBucket.md) can remove Slowness through its general effect-removal path, but also clears beneficial effects. Find cover first; clearing one hit's Slowness does not stop the Stray from firing again. See [Effects](../effects/Effects.md) for that distinction.

### Sunlight, cold and status effects

A Stray uses the Skeleton's sun-burning handler and head-slot protection. A damaged helmet or shade can change the result, so daylight does not guarantee that a particular equipped Stray will burn away. See [Skeleton daylight and equipment](Skeleton.md#daylight-and-equipment). [Shared sunlight behavior][skeleton-sun]

It belongs to the bundled **freezing-immune** entity tag, and the common freeze check consults that tag. Powder Snow is a route for creating a Stray from an ordinary Skeleton, not a verified way to freeze the resulting Stray to death. [Freeze tag][freeze-tag] · [Active freeze check][freeze-check] · [Conversion result][skeleton-convert-result]

The Skeleton/undead tag chain also makes it reject ordinary **Poison and Regeneration** effects. That is a specific pair of status effects, not immunity to all potions or magic damage. [Skeleton tag][skeleton-tag] · [Undead tag][undead-tag] · [Effect-immunity tag][poison-immunity] · [Effect-admission check][effect-check]

## Drops

With mob loot enabled, the table independently rolls **0–2 Arrows** and **0–2 Bones**, neither requiring player attribution. Looting increases each possible maximum by one per level, up to **5 of each with Looting III**. The separate **player-attributed** Slowness Tipped Arrow pool rolls **0–1**, and its Looting function is explicitly capped at **one**. Looting does not make that pool drop four special arrows. [Ordinary pools][stray-ordinary-loot] · [Tipped pool and cap][stray-tipped-loot] · [Looting calculation][looting-count] · [Player-attribution condition][player-kill] · [Loot gate][monster-loot]

The dropped tipped item uses the regular **Slowness potion component** and tipped-arrow duration scale; it is different from the 600-tick custom effect added to the mob's ordinary shot. Its bow and armor use the separate equipment-drop path. Use [Arrow](../items/Arrow.md) and [Skeleton drops](Skeleton.md#drops) for ammunition/equipment handling. [Tipped item][tipped-item] · [Effect scaling][potion-scale] · [Equipment path][equipment-drop]

## Notes

The mob's ID is `minecraft:stray`, and its registration is disallowed in Peaceful. Persistence from conversion or a Trial Spawner does not override the shared Peaceful-removal check. A [Bogged](Bogged.md) supplies Poison arrows and has its own shearing behavior; those are not Stray abilities. [Registration][stray-id] · [Despawn ordering][despawn]

Related: [Mobs](Mobs.md) · [Skeleton](Skeleton.md) · [Bogged](Bogged.md) · [Trial Spawner](../blocks/TrialSpawner.md) · [Powder Snow Bucket](../items/PowderSnowBucket.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `f9e4a4c96fcf7fb744bce869c085cdfab189810e`. Checked registration/attribute inheritance, both natural biome entries and sky predicate, ordinary Skeleton conversion, connected Trial Spawner configurations, arrow effects, sunlight/freezing/status behavior and loot. No in-game spawn, conversion, effect-duration, sunlight or drop test was run. Data packs, equipment/components and server rules can differ.

The checked Trial Chamber route follows connected templates and their saved normal/ominous configuration references. The configuration registry loads those resources; the active block ticker advances the Trial Spawner state and calls the entity-spawn path. A configuration entry does not guarantee a particular chamber layout or a successful spawn. [Connected chamber template][trial-assembly] · [Template placement][pool-place] · [Block-entity loading][block-entity-place] · [Spawner data loading][trial-load] · [Configuration registry][trial-registry] · [Saved configuration references][trial-holder] · [Active selection][trial-active] · [Block ticker][trial-ticker] · [State caller][trial-state] · [Spawn/initialization checks][trial-spawn]

World loading reads the bundled biome/structure resources, and the natural-spawn caller selects their active lists before placement checks. Registered attributes are applied when living entities are constructed; deaths load the species' entity loot table from the reloadable registry. [World loader][world-loader] · [Registry inputs][registry-list] · [Resource loading][registry-load] · [Spawn selection][spawn-selection] · [Structure/biome lookup][spawn-tables] · [Natural checks][spawn-checks] · [Attribute construction][attribute-call] · [Loot identity][loot-id] · [Loot registry][loot-registry] · [Loot loading][loot-load] · [Death loot caller][loot-call]

[stray-id]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntityType.java#L1340-L1349
[stray-registration]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L256
[skeleton-targets]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L74-L89
[health-default]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L57-L59
[stray-arrow]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Stray.java#L58-L66
[snowy-plains]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/biome/snowy_plains.json#L164-L169
[ice-spikes]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/biome/ice_spikes.json#L167-L172
[stray-placement]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L148
[stray-spawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Stray.java#L25-L36
[monster-spawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L83-L119
[spawner-reason]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntitySpawnReason.java#L24-L30
[skeleton-convert]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Skeleton.java#L47-L69
[skeleton-convert-result]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Skeleton.java#L94-L105
[conversion-equipment]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ConversionType.java#L39-L47
[trial-aliases]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json#L7-L57
[alias-build]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L136-L153
[alias-lookup]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L316-L332
[stray-trial-pool]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/spawner/ranged/stray.json#L1-L16
[stray-trial-template]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/structure/trial_chambers/spawner/ranged/stray.nbt
[stray-trial-normal]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/trial_spawner/trial_chamber/ranged/stray/normal.json#L1-L15
[stray-trial-ominous]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/trial_spawner/trial_chamber/ranged/stray/ominous.json#L1-L29
[stray-egg]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2097
[skeleton-arrows]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L181-L207
[mob-arrow]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/ProjectileUtil.java#L160-L165
[ordinary-ammo]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L139-L147
[arrow-effects]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L39-L65
[arrow-hit-gate]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L421-L436
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L104-L111
[skeleton-equipment]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L132-L179
[bow-draw]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/goal/RangedBowAttackGoal.java#L123-L136
[skeleton-sun]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L98-L122
[freeze-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/entity_type/freeze_immune_entity_types.json#L1-L8
[freeze-check]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/Entity.java#L3777-L3779
[skeleton-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/entity_type/skeletons.json#L1-L9
[undead-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/entity_type/undead.json#L1-L9
[poison-immunity]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/entity_type/ignores_poison_and_regen.json#L1-L5
[effect-check]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1003-L1012
[stray-ordinary-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/entities/stray.json#L1-L63
[stray-tipped-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/entities/stray.json#L64-L106
[looting-count]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L79
[player-kill]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[monster-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L125-L133
[tipped-item]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/Items.java#L2246-L2250
[potion-scale]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L93-L102
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/Mob.java#L814-L838
[despawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[trial-assembly]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/structure/trial_chambers/chamber/assembly.nbt
[pool-place]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L137-L181
[block-entity-place]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L292-L310
[trial-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/TrialSpawnerBlockEntity.java#L35-L42
[trial-registry]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L110-L115
[trial-holder]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L382-L393
[trial-active]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L77-L92
[trial-ticker]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/TrialSpawnerBlock.java#L47-L56
[trial-state]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawnerState.java#L55-L104
[trial-spawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L180-L233
[world-loader]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-list]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[registry-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L335
[spawn-tables]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[attribute-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L274
[loot-id]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-registry]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L20-L28
[loot-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[loot-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527

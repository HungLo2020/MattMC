# Bogged

A **Bogged** (`minecraft:bogged`) is a hostile Skeleton relative whose ordinary arrows add **Poison**. It has **16 base health points (8 hearts)** and a slower configured bow cooldown than the ordinary Skeleton. Its mushrooms can be sheared, but **shearing does not remove its Poison arrows or make it friendly**. [Identity][bogged-id] · [Registered attributes][bogged-registration] · [Health][bogged-stats] · [Arrow and cooldown][bogged-arrow] · [Shearing result][bogged-shear-result]

## Obtaining

Natural Bogged candidates are listed in **Swamp and Mangrove Swamp**, with weight **30** and a requested group of **4** in each bundled biome. Those are selection settings, not guaranteed group sizes in the world. Natural spawning uses the standard monster darkness/difficulty checks, ground placement and clearance. Follow [Jungles and swamps](../biomes/JunglesAndSwamps.md) for choosing the biome. [Swamp list][swamp] · [Mangrove list][mangrove] · [Placement registration][bogged-placement] · [Monster predicate][monster-spawn] · [Natural checks][spawn-checks]

**Trial Chambers** provide a separate route through configured [Trial Spawners](../blocks/TrialSpawner.md). The structure's ranged aliases can choose the group named **`poison_skeleton`**, whose normal and ominous resources actually create **`minecraft:bogged`**. The checked connected template stores those configuration IDs. Do not read the resource name as an ordinary Skeleton with a changed bow. [Alias group][trial-aliases] · [Alias construction][alias-build] · [Active pool lookup][alias-lookup] · [Selected pool][bogged-trial-pool] · [Spawner template][bogged-trial-template] · [Normal configuration][bogged-trial-normal] · [Ominous configuration][bogged-trial-ominous]

The Trial Spawner reason bypasses ordinary monster light requirements. Lighting a chamber therefore does not establish that this configured encounter has been disabled. Follow [Trial Spawner activation and waves](../blocks/TrialSpawner.md#activation-and-participants) for the actual controls, participants and rewards. [Spawn-reason exception][spawner-reason] · [Species predicate][monster-spawn] · [Spawner checks][trial-spawn]

The listed [Bogged Spawn Egg](../items/BoggedSpawnEgg.md) offers another placement route through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), in **Creative**. [Egg listing][bogged-egg]

## Behavior

### Poison arrows and fighting

The normal spawned bow uses the shared Skeleton ranged-attack path. The Bogged adds **Poison I for 100 ticks**, normally **5 seconds at 20 TPS**, to its ordinary Arrow projectile. The effect is applied through the successful-arrow-hit path, in addition to the arrow's damage. Custom ammunition/components can change duration handling; this is the default ordinary-arrow route. [Bow initialization][skeleton-equipment] · [Ranged caller][skeleton-arrows] · [Arrow creation][mob-arrow] · [Default ammunition][ordinary-ammo] · [Poison addition][bogged-arrow] · [Arrow duration scale][arrow-effects] · [Successful-hit gate][arrow-hit-gate] · [Effect application][arrow-hit]

Its bow cooldown is configured to **70 ticks outside Hard** or **50 ticks on Hard**. The shared bow goal also draws for at least 20 ticks and checks visibility, so those numbers are not exact shot-to-shot times. Use cover and avoid standing still while dealing with Poison. [Cooldown values][bogged-arrow] · [Difficulty selection][skeleton-equipment] · [Drawing and visibility][bow-draw]

The shared goals target players, Iron Golems and eligible baby Turtles on land, retaliate against eligible attackers, and avoid Wolves. If its weapon-selection check no longer finds a Bow, it uses the shared melee behavior. See [Skeleton combat](Skeleton.md#fighting-and-staying-safe) for those common controls, rather than treating the mushroom covering as protection. [Shared goals][skeleton-targets] · [Weapon selection][skeleton-equipment]

### Shearing the mushrooms

Use unbroken **[Shears](../items/Shears.md)** on a living, unsheared Bogged. A successful player interaction requests **1 durability**, before enchantment and infinite-material handling, and evaluates a separate shearing table. That table makes **two one-mushroom selections**, each choosing Brown or Red Mushroom; the two can be the same color. This is not a guaranteed one-of-each pair. [Interaction][bogged-shear] · [Durability handling][shear-wear-rules] · [Shearing callback][bogged-shear-result] · [Exact mushroom table][shear-loot] · [Shearing loot caller][shear-call]

A dispenser can invoke the same ready-for-shearing path on an eligible entity in the block in front of it. The sheared flag is saved, and no automatic mushroom-regrowth step appears in this checked class. Shearing does not change the arrow-effect method or attack goals, so prepare containment before approaching. [Dispenser target][shear-dispenser] · [Dispenser wear][shear-dispenser-wear] · [Saved flag][bogged-shear-save] · [Poison method][bogged-arrow]

### Sunlight and effects

Bogged use the shared Skeleton sunlight-burning and head-slot protection behavior. A hat or other head item can prevent a particular ignition check; ordinary daylight is not proof that every equipped Bogged has been removed. Follow [Skeleton daylight and equipment](Skeleton.md#daylight-and-equipment) for the common limits. [Sunlight handler][skeleton-sun]

The bundled Skeleton → undead → ignores-Poison-and-Regeneration tag chain includes Bogged. The normal effect-admission check therefore rejects those two effects on the mob. That does not prevent its arrows from poisoning a susceptible player. Use [Poison](../effects/Poison.md) and the [effects guide](../effects/Effects.md) for recovery; Poison's low-health cutoff does not prevent another arrow from killing you. [Skeleton membership][skeleton-tag] · [Undead nesting][undead-tag] · [Effect-immunity tag][poison-immunity] · [Effect check][effect-check]

## Drops

With mob loot enabled, the ordinary table separately rolls **0–2 Arrows** and **0–2 Bones**, without requiring player attribution. Looting increases each possible maximum by one per level, up to **5 of each with Looting III**. A separate **player-attributed** pool rolls **0–1 Tipped Arrow of Poison**. Looting can help that roll, but its explicit count limit keeps it at **one**, even with Looting III. [Ordinary pools][bogged-ordinary-loot] · [Tipped-arrow pool and limit][bogged-tipped-loot] · [Looting calculation][looting-count] · [Player-attribution test][player-kill] · [Mob-loot gate][monster-loot]

The dropped tipped item carries the ordinary **Poison potion component** and the tipped-arrow item's duration scale. It is not the same representation as the 100-tick custom effect added to the mob's usual shot. Carried bows/armor use the separate equipment-drop path; they are not guaranteed by these three table pools. Follow [Arrow](../items/Arrow.md) and [Skeleton drops](Skeleton.md#drops) for common ammunition and equipment rules. [Tipped item properties][tipped-item] · [Potion scaling][potion-scale] · [Equipment drops][equipment-drop]

## Notes

Bogged and [Strays](Stray.md) both extend the shared Skeleton behavior, but the ordinary Skeleton's Powder Snow conversion code belongs to the **Skeleton class**, not this mob. Do not expect freezing a Bogged to turn it into a Stray. Bogged are disallowed in Peaceful. [Ordinary Skeleton conversion][skeleton-convert] · [Bogged class][bogged-stats] · [Registration][bogged-id] · [Difficulty removal][despawn]

Related: [Mobs](Mobs.md) · [Skeleton](Skeleton.md) · [Stray](Stray.md) · [Trial Spawner](../blocks/TrialSpawner.md) · [Mushrooms](../blocks/Mushrooms.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `f9e4a4c96fcf7fb744bce869c085cdfab189810e`. Checked registration, both natural biome entries, connected Trial Spawner template/configuration, shared combat and arrow-effect callers, shearing/saved state, immunity tags and loot. No in-game spawning, shearing/regrowth, Poison-duration, combat or loot test was run. Data packs, equipment/components, rules and ticking can differ.

The checked Trial Chamber route follows connected templates and their saved normal/ominous configuration references. The configuration registry loads those resources; the active block ticker advances the Trial Spawner state and calls the entity-spawn path. A configuration entry does not guarantee a particular chamber layout or a successful spawn. [Connected chamber template][trial-assembly] · [Template placement][pool-place] · [Block-entity loading][block-entity-place] · [Spawner data loading][trial-load] · [Configuration registry][trial-registry] · [Saved configuration references][trial-holder] · [Active selection][trial-active] · [Block ticker][trial-ticker] · [State caller][trial-state] · [Spawn/initialization checks][trial-spawn]

World loading reads the bundled biome/structure resources, and the natural-spawn caller selects their active lists before placement checks. Registered attributes are applied when living entities are constructed; deaths load the species' entity loot table from the reloadable registry. [World loader][world-loader] · [Registry inputs][registry-list] · [Resource loading][registry-load] · [Spawn selection][spawn-selection] · [Structure/biome lookup][spawn-tables] · [Natural checks][spawn-checks] · [Attribute construction][attribute-call] · [Loot identity][loot-id] · [Loot registry][loot-registry] · [Loot loading][loot-load] · [Death loot caller][loot-call]

[bogged-id]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntityType.java#L341-L344
[bogged-registration]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L132
[bogged-stats]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Bogged.java#L31-L40
[bogged-arrow]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Bogged.java#L108-L126
[bogged-shear-result]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Bogged.java#L128-L144
[swamp]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/biome/swamp.json#L195-L213
[mangrove]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/biome/mangrove_swamp.json#L164-L182
[bogged-placement]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L109
[monster-spawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L83-L119
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[trial-aliases]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/structure/trial_chambers.json#L7-L57
[alias-build]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/structures/JigsawStructure.java#L136-L153
[alias-lookup]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/pools/JigsawPlacement.java#L316-L332
[bogged-trial-pool]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/template_pool/trial_chambers/spawner/ranged/poison_skeleton.json#L1-L16
[bogged-trial-template]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/structure/trial_chambers/spawner/ranged/poison_skeleton.nbt
[bogged-trial-normal]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/trial_spawner/trial_chamber/ranged/poison_skeleton/normal.json#L1-L15
[bogged-trial-ominous]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/trial_spawner/trial_chamber/ranged/poison_skeleton/ominous.json#L1-L29
[spawner-reason]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntitySpawnReason.java#L24-L30
[trial-spawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/block/entity/trialspawner/TrialSpawner.java#L180-L233
[bogged-egg]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1986
[skeleton-equipment]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L132-L179
[skeleton-arrows]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L181-L207
[mob-arrow]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/ProjectileUtil.java#L160-L165
[ordinary-ammo]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L139-L147
[arrow-effects]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L39-L65
[arrow-hit-gate]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/AbstractArrow.java#L421-L436
[arrow-hit]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/Arrow.java#L104-L111
[bow-draw]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/goal/RangedBowAttackGoal.java#L123-L136
[skeleton-targets]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L74-L89
[bogged-shear]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Bogged.java#L72-L85
[shear-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/shearing/bogged.json#L1-L34
[shear-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1555-L1574
[shear-dispenser]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java#L53-L66
[shear-dispenser-wear]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/core/dispenser/ShearsDispenseItemBehavior.java#L19-L31
[bogged-shear-save]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Bogged.java#L52-L70
[skeleton-sun]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/AbstractSkeleton.java#L98-L122
[skeleton-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/entity_type/skeletons.json#L1-L9
[undead-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/entity_type/undead.json#L1-L9
[poison-immunity]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/entity_type/ignores_poison_and_regen.json#L1-L5
[effect-check]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1003-L1012
[bogged-ordinary-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/entities/bogged.json#L1-L63
[bogged-tipped-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/entities/bogged.json#L64-L106
[looting-count]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L79
[player-kill]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[monster-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L125-L133
[tipped-item]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/Items.java#L2246-L2250
[potion-scale]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/alchemy/PotionContents.java#L93-L102
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/Mob.java#L814-L838
[skeleton-convert]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Skeleton.java#L47-L69
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
[world-loader]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-list]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[registry-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L335
[spawn-tables]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[attribute-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L274
[loot-id]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-registry]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L20-L28
[loot-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[loot-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[shear-wear-rules]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/ItemStack.java#L457-L473

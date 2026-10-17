# Skelewag

The **Skelewag** (`minecraft:skelewag`) is a hostile swimming mob with **20 health points (10 hearts)**. It targets players and Dolphins, can appear with a Drowned rider, and is available through its [spawn egg](../items/SkelewagSpawnEgg.md). **Do not assume it is safe to keep permanently submerged:** the current source predicts drowning damage despite its aquatic movement. [Registration][registry] · [Applied attributes][attributes] · [Stats and targets][stats-targets] · [Breathing rules][breathing]

## Obtaining

Request a **Skelewag Spawn Egg** from MattMC's [inventory item browser](../mechanics/InventoryBrowser.md). It is in the ordinary Spawn Eggs category, available in Creative when inventory space permits. Use it on a block or target a source fluid block; ordinary egg use refuses Skelewag placement in **Peaceful**. The egg's active creation path runs the species' spawn initialization and adds passengers too. [Egg registration][egg-item] · [Category][egg-list] · [Browser list][browser-list] · [Client request][browser-client] · [Server insertion][browser-server] · [Egg use][egg-use] · [Creation and insertion][entity-create]

**Rendering limitation:** on the inspected native rendering path, Skelewag has a model compatibility gap: its model exposes an empty part tree to native extraction, so source inspection predicts an empty-model exception. That runtime outcome has **not been reproduced here**. The shared adapter limitation is tracked in [#803](https://github.com/HungLo2020/MattMC/issues/803); it applies to the inspected visible-body submission with a readable texture and valid transform, not every frame or every imported mob. [Native model caller][render-call] · [Proxy model root][render-proxy] · [Extraction traversal][render-extract] · [Part traversal][render-visit] · [Empty-model exception][render-empty]

**No bundled natural encounter location was found.** The checked Java biome-spawn assembly and biome/structure selection, world-generation data and decompressed structure templates contain no named Skelewag entry. Its registered Water placement predicate does not itself add the mob to a biome. There is no verified ocean, shipwreck or other hunting location to recommend. [Placement binding][placement] · [Natural selection][natural-selection] · [Biome and structure lists][spawn-lists] · [Spawn-list assembly][spawn-assembly] · [Bundled data][data]

For a [Monster Spawner](../blocks/MonsterSpawner.md) configured to Skelewag, the ordinary predicate requires Water **below the spawn position**, non-Peaceful difficulty and the monster darkness test. The `SPAWNER` branch skips the predicate's 1-in-40 roll and its additional Water-at-position test; other spawner checks still apply. That roll is not a natural encounter rate or an egg-use requirement. See [Spawn eggs](../items/SpawnEggs.md) for changing spawners and shared placement rules. [Species predicate][spawn-predicate] · [Spawner caller][spawner] · [Darkness test][darkness]

## Behavior

### Targets and attacks

Players are its first ordinary prey target, followed by Dolphins. Both target searches use sight checks. It can retaliate against other attackers, but its retaliation goal ignores Drowned and other Skelewags; those exclusions are not general immunity to damage. Keep Dolphins and other vulnerable mobs out of its enclosure. [Installed goals][stats-targets] · [Goal installation][goal-install] · [Target search][target-search] · [Retaliation exclusions][retaliation]

Its pursuit switches to a faster charge after the target is more than **5 blocks** away. Close-range combat uses two server-side damage checks:

- **Stab:** attempts **3 base damage points** on animation tick 7
- **Slash:** attempts **1.5 base damage points** on animation ticks 5, 10, 15 and 20 against eligible living entities in the target's bounding box expanded by **2 blocks** in every direction

Both checks require the main target to remain closer than **2 blocks plus that target's width**, with line of sight to that target. A slash excludes the Skelewag itself, allies and entities sharing its vehicle, but does not separately check sight to each nearby victim. Keep companions away from the fight. These are attempted base values, before difficulty, blocking, armor and damage cooldowns; four slash checks do not guarantee four damaging hits. [Pursuit][attack-goal] · [Damage checks][attacks] · [Active damage adapter][damage-adapter] · [Player difficulty][difficulty] · [Damage cooldown][damage-cooldown]

The attack goal can change the selected attack while its animation clock is running. The checked model source also does not consume the extracted attack-animation fields, and the shared animation handler has no network synchronization. Do not rely on a visible wind-up or a fixed attack rhythm as a tested warning. [Attack selection][attack-goal] · [Animation clock][animation] · [Render state][renderer] · [Model poses][model]

### Water and land

It has aquatic steering, random swimming and a nearby-water search when stranded. Water currents do not push it. Out of Water, it randomly flops while grounded and ejects passengers once its land-progress counter reaches 5. These movement rules do not make a dry enclosure secure or guarantee that it will find a safe route. [Movement setup][movement-setup] · [Travel and fluid push][travel] · [Flopping and ejection][land] · [Water search][find-water]

**The checked implementation predicts that an ordinary, unmodified Skelewag can drown.** Its active breathing check uses an entity-type tag that excludes Skelewag, including through the nested undead tags. No active species air refill or drowning-damage exception was found; the species' similarly named `canDrownInFluidType()` method is not called by that path. With its eyes continuously underwater outside a Bubble Column and no breathing effect, a full 300-tick supply reaches the first **2-point drowning hit after about 16 seconds** at 20 ticks per second, then repeats about once per second. Air, Water Breathing or Conduit Power allows the active rules to replenish air; custom data packs or altered entity data can also change the conditions. This is source-predicted behavior, **not a reproduced gameplay test**; do not treat a deep tank as a verified permanent habitat. [Active breathing and damage][breathing] · [Breathing tag][breathing-tag] · [Nested undead tag][undead-tag] · [Species method][legacy-breathing] · [Air capacity][air-capacity] · [Air updates][air-updates] · [Breathing effects][breathing-effects] · [Damage threshold][air-threshold]

### Variants and riders

Spawn initialization chooses **variant 1 with a 30% chance**, otherwise variant 0. The renderer source defines two texture choices, but their current in-game appearance is unverified; the checked variant branch does not change health or attacks. A separate **20% roll** attempts to create and mount a Drowned. Egg placement inserts that passenger with the Skelewag, so a single egg can introduce two hostile mobs. [Spawn rolls][spawn-rolls] · [Textures][renderer] · [Passenger insertion][entity-create]

The Skelewag has passenger positioning but no species interaction for taming, feeding, breeding, bucket capture or player mounting. It also cannot be attached to a Lead. A Drowned rider is not evidence of a player-controlled mount. Its unused fluid-riding method is also not the active dismount rule; the active rule checks a tag that does not include Skelewag. [Species implementation][class] · [Shared interaction][interaction] · [Lead rule][lead] · [Passenger positioning][rider-position] · [Active underwater dismount][dismount] · [Dismount tag][dismount-tag]

## Keeping a Skelewag

An ordinary egg-spawned Skelewag has no species persistence protection. It follows normal hostile distance despawning; carrying a Drowned does not make the Skelewag itself a passenger. Use a **[Name Tag](../items/NameTag.md)** to set persistence if you are keeping one. This prevents ordinary distance removal, but **Peaceful still removes it**, and naming does not prevent drowning or other damage. Its variant and the inherited persistence flag use the current save/load paths. [Despawn checks][despawn] · [Name Tag][name-tag] · [Variant saves][variant-save] · [Persistence saves][persistence-save]

## Drops

There is **no bundled Skelewag item loot table or species item-drop override** in the checked source. Its default table would be `minecraft:entities/skelewag`; the missing-table lookup falls back to an empty table. In particular, it has **no configured Skelewag Sword drop**. Use the [Skelewag Sword guide](../items/SkelewagSword.md) for that separately listed item's acquisition and implementation limits. [Default loot name][loot-name] · [Bundled loot][loot-data] · [Empty fallback][loot-fallback] · [Species implementation][class]

A qualifying player-credited death gives **10 base XP** with `doMobLoot` enabled, before experience modifiers. Equipment deliberately supplied by commands or other systems follows inherited equipment-drop rules; that is not a default species resource. Any Drowned passenger has its own death and loot, separate from its mount. A Wither-credited kill can still create a Wither Rose through the inherited special-case rule. [Wither Rose rule][wither-rose] · [Base XP][movement-setup] · [Equipment XP][equipment-xp] · [Death and XP gates][death-loot] · [Equipment drops][equipment-loot]

## Notes

- Registered category: `MobCategory.MONSTER`; base dimensions: **2 × 1.2 blocks**. These are entity dimensions, not a tested enclosure design. [Registration][registry]
- Base movement-speed attribute: **0.45**; this is not a blocks-per-second measurement. [Attributes][stats-targets] · [Aquatic steering][steering]
- Source-reviewed at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6` on 2026-10-02. Actual callers, spawn/data routes, render code, tag membership, persistence and loot fallback were inspected. No gameplay, combat, breathing, rider, spawn or multiplayer test was run

Related: [Skelewag Spawn Egg](../items/SkelewagSpawnEgg.md) · [Skelewag Sword](../items/SkelewagSword.md) · [Drowned](Drowned.md) · [Mobs](Mobs.md)

[registry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1212-L1214
[attributes]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L238
[stats-targets]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L92-L113
[egg-item]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1956
[egg-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2087
[browser-list]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L1963
[egg-use]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L128
[entity-create]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L1736-L1775
[placement]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L98
[natural-selection]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L250-L325
[spawn-lists]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L450
[spawn-predicate]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L64-L73
[spawner]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/BaseSpawner.java#L115-L164
[darkness]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/monster/Monster.java#L86-L100
[goal-install]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L141-L153
[target-search]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L34-L75
[retaliation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L27-L68
[attack-goal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L264-L294
[attacks]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L137-L151
[damage-adapter]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L1772-L1776
[difficulty]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L733-L747
[damage-cooldown]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1164-L1195
[animation]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/citadel/animation/AnimationHandler.java#L25-L64
[renderer]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/client/render/RenderSkelewag.java#L10-L37
[model]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/client/model/ModelSkelewag.java#L82-L120
[movement-setup]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L52-L62
[travel]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L184-L203
[land]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L116-L164
[find-water]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/ai/goal/TryFindWaterGoal.java#L15-L40
[breathing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L384-L443
[legacy-breathing]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L235-L237
[air-capacity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L2660-L2669
[air-updates]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L569-L582
[air-threshold]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L491-L493
[spawn-rolls]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L218-L233
[class]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L42-L296
[interaction]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L1054-L1111
[rider-position]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L205-L216
[dismount]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L2533-L2535
[despawn]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L598-L630
[name-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/NameTagItem.java#L16-L29
[variant-save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySkelewag.java#L166-L181
[persistence-save]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L352-L392
[loot-name]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-fallback]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L115-L120
[equipment-xp]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L289-L306
[death-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[equipment-loot]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[steering]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/ai/AquaticMoveController.java#L27-L49
[breathing-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/can_breathe_under_water.json
[undead-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/undead.json
[dismount-tag]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/dismounts_underwater.json
[data]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data
[loot-data]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/loot_table/entities
[spawn-assembly]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/biome/MobSpawnSettings.java#L55-L98
[breathing-effects]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L43-L44
[lead]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Mob.java#L1217-L1219
[wither-rose]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1431-L1462

[render-call]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/renderer/SubmitNodeCollection.java#L280-L302
[render-proxy]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/citadel/client/model/basic/BasicEntityModel.java#L13-L56
[render-extract]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L11910-L11925
[render-visit]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/client/model/geom/ModelPart.java#L150-L169
[render-empty]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L9643-L9690

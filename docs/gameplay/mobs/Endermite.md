# Endermite

An **Endermite** (`minecraft:endermite`) is a small hostile mob that can appear during an [Ender Pearl](../items/EnderPearl.md) teleport attempt. It has a short ordinary lifetime and attracts [Endermen](Enderman.md), so keep those risks in mind before trying to contain one. Killing it does not normally return an Ender Pearl. [Registration][mite-id] · [Pearl creation][pearl] · [Lifetime][lifetime] · [Enderman target goal][enderman] · [Empty loot][mite-loot]

## Obtaining

### Ender Pearl spawns

The eligible **player-owner impact branch** of a thrown Pearl makes a **5% Endermite roll** when monster spawning is enabled. The Pearl must still exist, have an owner allowed to teleport, and reach an accepting player connection. The monster-spawning flag includes **non-Peaceful difficulty, `doMobSpawning` and `spawnMonsters`**. This is a chance per eligible impact, not a guaranteed mob every twentieth throw. [Impact and owner checks][pearl] · [Teleport eligibility][pearl-eligibility] · [Server-level flag][level-monsters] · [Actual monster-spawning gate][server-monsters]

**The mob is created at the owner's coordinates before the teleport, in the Pearl's world.** The creation attempt occurs before the player's teleport call; it is not conditional on that call returning a successful result. Do not assume it appears at the Pearl's landing spot. The full Pearl guide covers teleport restrictions and player damage. [Spawn position and call order][pearl]

No Endermite entry was found in the checked bundled biome lists or structure spawn overrides. Its registered ground-spawn predicate alone does not add it to those lists. That predicate uses an any-light monster check and excludes a nearby eligible player within 5 blocks for non-spawner attempts, but **the Pearl's direct creation path does not call it**. [Placement registration][mite-placement] · [Species predicate][mite-spawn] · [Any-light gate][monster-gate] · [Direct creation][pearl]

The listed [Endermite Spawn Egg](../items/EndermiteSpawnEgg.md) offers a separate placement route through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), available in **Survival and Creative**. [Egg category entry][mite-egg]

## Behavior

### Combat and Endermen

Default maximum health is **8 points (4 hearts)** and the base attack attribute is **2**. Actual damage depends on difficulty and defenses. Its registered body is **0.4 blocks wide and 0.3 blocks tall**. It targets attackable players, uses melee attacks and can alert nearby idle Endermites through its retaliation goal. [Registered attributes][mite-defaults] · [Goals and attributes][mite-goals] · [Size][mite-id] · [Shared hurt alert][hurt-alert]

Endermen have a target goal for the **Endermite class** with visibility checks. That goal does **not** require a remembered Pearl origin, so an egg-created Endermite is not excluded merely because it came from an egg. Attackability, sight and other goal conditions still apply; this is not a certified Enderman-farm design. [Enderman target registration][enderman] · [Goal checks][nearest-target] · [Attackability and sight][targeting]

The checked Endermite implementation has no feeding, breeding or teleport action. Its purple particles are a client-side visual effect, not a teleport. It also has no sunlight-burning routine in its tick path, so do not rely on sunrise to dispose of one. [Active goals][mite-goals] · [Particles and lifetime][lifetime] · [Inherited monster tick][monster-tick]

### Lifetime and keeping one

An ordinary Endermite increments its stored lifetime on server ticks and discards itself at **2,400 ticks**, normally about **two minutes at 20 TPS**. Time while the entity is not ticking is not added, and the lifetime is saved rather than starting over on a normal reload. Ordinary distance despawning can remove an unprotected mob earlier. [Lifetime and expiry][lifetime] · [Saved lifetime][save-life] · [Shared despawning][despawn]

Using a properly named [Name Tag](../items/NameTag.md) sets the required persistence flag, which **stops further lifetime increments**. Do this before the cutoff: setting persistence does not reset the saved counter, and the expiry comparison still runs. Naming also does not protect it from attacks or the unconditional **Peaceful** removal for this registered type. [Name Tag interaction][name-tag] · [Persistence flag][persistence] · [Lifetime condition][lifetime] · [Peaceful registration][mite-id] · [Despawn ordering][despawn]

## Drops

The bundled entity loot table has **no item pools**: there is no normal Pearl or other item drop, and Looting cannot add an item to this empty table. A qualifying player-attributed death has **3 base experience**, with mob loot enabled. Its lifetime-expiry branch discards the mob rather than running the normal death-loot sequence. [Empty table][mite-loot] · [Base experience][mite-constructor] · [Experience calculation][xp-count] · [Death/experience gates][death] · [Expiry branch][lifetime]

## Notes

Endermites are monster-category entities. They do not use the [Silverfish](Silverfish.md) block-infestation or wake-neighbor goals; a purple Endermite should not be treated as a source of infested masonry. [Registration][mite-id] · [Complete goal list][mite-goals]

Related: [Mobs](Mobs.md) · [Ender Pearl](../items/EnderPearl.md) · [Enderman](Enderman.md) · [Silverfish](Silverfish.md) · [Name Tag](../items/NameTag.md)

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
[mite-id]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/EntityType.java#L538-L546
[pearl]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/projectile/ThrownEnderpearl.java#L95-L120
[pearl-eligibility]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/projectile/ThrownEnderpearl.java#L142-L147
[level-monsters]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/level/ServerLevel.java#L1766-L1768
[server-monsters]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/MinecraftServer.java#L1473-L1477
[mite-placement]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L118
[mite-spawn]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Endermite.java#L131-L142
[mite-egg]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2016
[mite-defaults]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L162
[mite-goals]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Endermite.java#L40-L55
[enderman]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/EnderMan.java#L96-L106
[nearest-target]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestAttackableTargetGoal.java#L30-L69
[targeting]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L60-L92
[lifetime]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Endermite.java#L104-L130
[save-life]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Endermite.java#L80-L90
[monster-tick]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Monster.java#L42-L54
[name-tag]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/NameTagItem.java#L16-L29
[persistence]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/Mob.java#L1026-L1028
[mite-loot]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/entities/endermite.json#L1-L4
[mite-constructor]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Endermite.java#L34-L37
[xp-count]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/Mob.java#L290-L306

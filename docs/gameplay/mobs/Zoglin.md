# Zoglin

A **Zoglin** (`minecraft:zoglin`) is the hostile result of [Hoglin zombification](Hoglin.md#zombification). It attacks a much wider range of living targets than a Hoglin and keeps the adult tusk-throw attack. **Do not transport a Hoglin into a livestock area and assume it will remain a breedable animal.** [Identity][zoglin-id] · [Conversion result][hoglin-conversion-result] · [Targets][zoglin-target] · [Attacks][zoglin-hit]

## Obtaining

The checked ordinary route is to let a Hoglin convert in a dimension whose type is **not Piglin-safe**. With AI enabled and no conversion immunity, this happens after more than **300 consecutive server AI ticks**, normally just over 15 seconds at 20 TPS. The bundled Overworld, End and Primordial Caves meet the unsafe-dimension condition; the Nether does not. Use the [Hoglin guide](Hoglin.md#zombification) for transport and reset conditions. [Conversion timing][hoglin-conversion] · [Condition][hoglin-conversion-gate] · [Nether][nether-type] · [Overworld][overworld-type] · [End][end-type] · [Primordial Caves][primordial-type]

No Zoglin entry was found in the bundled biome monster lists or structure spawn overrides at this snapshot. It has a registered ground-placement predicate, but that alone does not put it in the natural-spawn selector. Returning a Zoglin to the Nether does not trigger a reverse-conversion path in its checked implementation. [Placement registration][zoglin-placement] · [List selection][spawn-selection] · [Biome/structure lookup][spawn-tables] · [Zoglin server tick][zoglin-tick]

The listed [Zoglin Spawn Egg](../items/ZoglinSpawnEgg.md) is a separate placement option through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), which supplies it in **Creative**. Egg availability does not establish a natural Zoglin population. [Egg listing][zoglin-egg]

## Behavior

### Targets and attacks

A Zoglin chooses the nearest visible living entity that passes its attackability checks, excluding **other Zoglins and Creepers** from its ordinary target search. This includes many animals and friendly mobs as well as players. The exclusions describe ordinary target selection; the separate retaliation path can react to a living attacker if attackability and target-distance conditions allow it. [Target search][zoglin-target] · [Retaliation][zoglin-hit]

Default maximum health is **40 points (20 hearts)**. Adults have the same base attack attribute of 6 as Hoglins and roll **3–8 damage before difficulty, armor and other adjustments**. A successful adult hit can throw its target, and a blocked adult attack can also cause a throw. Keep solid containment and distance from ledges. [Attribute registration][zoglin-registration] · [Base attributes][zoglin-attributes] · [Damage and throw][hoglin-melee] · [Hit and blocking dispatch][zoglin-hit]

Adult and baby fight activities use **40- and 15-tick melee cooldowns**, respectively, when their other attack conditions are met. Baby attack damage starts at **0.5**, and babies skip the adult throwing branches. These base values do not promise a particular final damage result against equipped players. [Fight activity][zoglin-target] · [Baby damage][zoglin-tick] · [Throwing conditions][hoglin-melee] · [Blocked attacks][zoglin-hit]

### Containment, food and babies

**Warped Fungus, Nether Portals and Respawn Anchors do not provide the Hoglin pacification behavior for Zoglins.** The Zoglin brain has no repellent sensor/activity or animal mating behavior. Crimson Fungus has no normal Zoglin feeding or breeding interaction; there is no taming route in the checked implementation. A [Lead](../items/Lead.md) is accepted, but leashing does not make its attack selection safe around other mobs. [Class and sensors][zoglin-class] · [Activities][zoglin-target] · [Leashing][zoglin-hit]

A baby Hoglin converts into a baby Zoglin; direct spawn initialization can also select a baby. Zoglins store a baby flag rather than an animal growth age, and their tick path does not age babies into adults. Feeding cannot grow them through the Hoglin system. [Conversion preservation][conversion-baby] · [Spawn initialization][zoglin-baby] · [Baby state and tick][zoglin-tick] · [Saved baby flag][zoglin-save]

Zoglins are registered **fire-immune** and **disallowed in Peaceful**. Fire is not a reliable disposal method; Peaceful removal happens before ordinary persistence checks. [Registration][zoglin-id] · [Fire-damage gate][fire-immunity] · [Despawn ordering][despawn]

## Drops

The bundled entity loot table rolls **1–3 [Rotten Flesh](../items/RottenFlesh.md)**, with Looting increasing the possible maximum by one per level, up to **6 with Looting III**. It does not drop the Hoglin table's Porkchops or Leather. The flesh pool does not require a player-attributed kill. [Zoglin table][zoglin-loot] · [Looting calculation][looting-count]

Both adult and baby Zoglins use the Monster item-loot gate, which requires mob loot to be enabled but does **not** reject babies. This differs from the adult-only ordinary item drops of Hoglins. [Monster gate][monster-loot] · [Death dispatch][death] · [Hoglin's inherited gate][animal-loot]

## Notes

The entity ID is `minecraft:zoglin`, and its registered body is about **1.396 blocks wide by 1.4 blocks tall** before baby scaling. It is distinct from the [Zombified Piglin](ZombifiedPiglin.md), whose normal behavior is neutral until anger or retaliation conditions apply. [Registration][zoglin-id]

Related: [Mobs](Mobs.md) · [Hoglin](Hoglin.md) · [Nether](../dimensions/Nether.md) · [Rotten Flesh](../items/RottenFlesh.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `780a7733d1804088e86c248a1c8ec6957def4915`. Checked registration/attributes, bundled biome and structure spawn lists, conversion, active targeting/retaliation, baby state, leashing, fire/difficulty flags and loot. No in-game conversion, containment, baby-growth, combat or drop test was run. Negative natural-spawn and interaction findings concern this checked implementation; custom data and later builds can differ. [AI dispatch][ai-call] · [Zoglin brain tick][zoglin-tick]

The checked world-generation registry loads biome and structure resources, and natural spawning reads their active lists before placement and collision checks. Listed candidates are not guaranteed encounters. Living entities take their registered default attributes on construction; entity deaths resolve the species' entity loot table through the reloadable loot registry. [World loader][world-loader] · [Registry entries][registry-list] · [Resource loading][registry-load] · [Spawn-list caller][spawn-selection] · [Biome/structure lookup][spawn-tables] · [Placement checks][spawn-checks] · [Loot identity][loot-id] · [Loot registry][loot-registry] · [Loot loading][loot-load] · [Attribute construction][attribute-call] · [Death loot caller][loot-call]

[zoglin-id]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/EntityType.java#L1543-L1551
[hoglin-conversion-result]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L267-L269
[zoglin-target]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zoglin.java#L110-L148
[zoglin-hit]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zoglin.java#L185-L230
[hoglin-conversion]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L162-L178
[hoglin-conversion-gate]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L302-L319
[nether-type]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/dimension_type/the_nether.json
[overworld-type]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/dimension_type/overworld.json
[end-type]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/dimension_type/the_end.json
[primordial-type]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[zoglin-placement]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L156
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L335
[spawn-tables]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[zoglin-tick]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zoglin.java#L248-L277
[zoglin-egg]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2120
[zoglin-registration]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L272
[zoglin-attributes]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zoglin.java#L176-L183
[hoglin-melee]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/HoglinBase.java#L16-L49
[zoglin-class]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zoglin.java#L54-L103
[conversion-baby]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ConversionType.java#L94-L109
[zoglin-baby]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zoglin.java#L164-L174
[zoglin-save]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zoglin.java#L328-L338
[fire-immunity]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Entity.java#L2878-L2886
[despawn]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[zoglin-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/loot_table/entities/zoglin.json#L1-L36
[looting-count]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L79
[monster-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Monster.java#L125-L133
[death]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[animal-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[ai-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Mob.java#L633-L662
[world-loader]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-list]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[registry-load]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[loot-id]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-registry]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L20-L28
[loot-load]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[attribute-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L274
[loot-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527

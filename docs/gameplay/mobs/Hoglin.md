# Hoglin

A **Hoglin** (`minecraft:hoglin`) is an aggressive, breedable Nether animal. Adults can throw targets with their tusks, making narrow paths and Lava edges dangerous places to fight. **Crimson Fungus feeds it; placed Warped Fungus repels it.** Keeping a Hoglin outside a Piglin-safe dimension can turn it into a [Zoglin](Zoglin.md). [Identity][hoglin-id] · [Attack][hoglin-attributes] · [Food][hoglin-food] · [Repellents][hoglin-repellents] · [Conversion check][hoglin-conversion-gate]

## Obtaining

Find natural Hoglin candidates in **[Crimson Forest](../biomes/NetherBiomes.md#crimson-forest)**. The bundled list requests groups of **3–4** with weight **9**, but population limits, placement and other checks determine actual encounters. Its registered ground-spawn predicate rejects a **Nether Wart Block directly below** the spawn position; ordinary clearance and ground-placement checks still apply. Babies can be selected during spawn initialization. [Biome list][crimson] · [Placement registration][hoglin-placement] · [Species checks and initialization][hoglin-spawn] · [Natural-spawn checks][spawn-checks]

**[Bastion Remnants](../structures/BastionRemnant.md#layouts-and-residents)** have another route: stable connectors can choose a Hoglin resident template or an empty piece. The checked Hoglin template stores **40 health**, persistence, and `CannotBeHunted`; this stops the ordinary Piglin hunt selection for that resident, not all possible combat. It is a placed resident, not evidence that every cleared stable continuously generates replacements. [Stable connector][hoglin-connector] · [Resident pool][hoglin-pool] · [Resident template][hoglin-template] · [Placement caller][pool-place] · [Entity placement][entity-place] · [Hunt eligibility][hoglin-conversion-gate] · [Piglin hunt sensor][hunt-sensor] · [Hunt activity][hunt-activity]

The listed [Hoglin Spawn Egg](../items/HoglinSpawnEgg.md) provides a separate placement route through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md), in **Creative**. [Egg category entry][hoglin-egg]

## Behavior

### Combat and keeping distance

A Hoglin has **40 base health points (20 hearts)**. Adults use a base attack attribute of 6, but their tusk attack rolls **3–8 damage before difficulty, armor and other adjustments**, then can throw the target. Baby attack damage starts at **0.5** and babies do not use the adult throw-on-hit branch. These are source values, not a guarantee of final hearts lost. [Registered attributes][hoglin-registration] · [Base attributes][hoglin-attributes] · [Damage and throwing][hoglin-melee] · [Baby/adult adjustment][hoglin-baby]

Adults can also throw a target when their attack is blocked by an item. Do not depend on blocking alone to hold your position beside a cliff. The fighting activities use a **40-tick adult melee cooldown** and a **15-tick baby cooldown** when their attack conditions are met. [Blocked attack][hoglin-attributes] · [Fight activity][hoglin-idle]

An unpacified Hoglin without a breeding target chooses a visible attackable player. Hurting an adult can make it retaliate and pass a target to nearby visible adults; hurting a baby makes it retreat. Adults can retreat from Piglins when the visible adult Piglins outnumber their Hoglin group. Gold armor is not part of this Hoglin player-target test. [Player selection][hoglin-target] · [Retaliation][hoglin-retaliate] · [Group retreat][hoglin-retreat] · [Outnumbering check][hoglin-count]

### Repelling and containing Hoglins

The bundled repellent tag contains **Warped Fungus, Potted Warped Fungus, Nether Portal blocks, and Respawn Anchors**. The sensor searches within **8 blocks horizontally and 4 vertically**; idle behavior moves away and the idle/fight activities can apply a **200-tick pacified memory**, clearing the attack target. The tag check does not ask whether an Anchor is charged. [Repellent tag][hoglin-repellents] · [Sensor][hoglin-sensor] · [Avoidance and pacification][hoglin-idle] · [Pacification effect][pacify]

Place repellents as part of an enclosure plan, but do not treat them as taming. A successful hit attributed to a living attacker clears pacification, and repelled Hoglins cannot enter love mode while that pacified state is active. **A repellent beside a breeding pen can therefore prevent breeding.** [Damage caller][hoglin-hurt] · [Hurt response][hoglin-retaliate] · [Love-mode gate][hoglin-breed]

Hoglins accept a [Lead](../items/Lead.md). The checked activities do not include held-food temptation, so holding Crimson Fungus is not an established follow-the-player method here. Use containment or a Lead for controlled movement. A successful feeding interaction marks the Hoglin persistent, and bred offspring are also persistent; untouched wild Hoglins remain eligible for distance despawning. [Leashing][hoglin-attributes] · [Activity list][hoglin-idle] · [Interaction and retention][hoglin-retain] · [Offspring][hoglin-breed]

### Feeding and breeding

Use **Crimson Fungus** directly on two ready adults that are not pacified. Keep them close enough to see and reach each other. Their breeding activity produces a baby; the parents receive a **6,000-tick cooldown**, normally five minutes at 20 TPS. Feeding does not tame the parents or directly heal them through this shared animal interaction. [Food tag][hoglin-food] · [Food check][hoglin-feeding] · [Feeding interaction][animal-feed] · [Partner and birth checks][animal-mating] · [Birth and cooldown][animal-birth]

A newborn starts at **−24,000 age ticks**, normally about **20 minutes** to adulthood while ticking. Feeding a baby Crimson Fungus advances it by about **10% of the remaining growth time**, rounded down to whole seconds. Growth and the adults' breeding cooldown both depend on ticking, not real time while the world is inactive. [Age progression and feeding amount][age-tick] · [Feeding caller][animal-feed]

## Zombification

With AI enabled and no entity-data immunity, **more than 300 consecutive server AI ticks** outside a Piglin-safe dimension converts a Hoglin to a [Zoglin](Zoglin.md), normally just over **15 seconds at 20 TPS**. Returning to safe conditions resets the counter. The new Zoglin receives 200 ticks of Nausea; a baby Hoglin remains a baby through the shared conversion path. [Tick and reset][hoglin-conversion] · [Conditions][hoglin-conversion-gate] · [Result][hoglin-conversion-result] · [Baby preservation][conversion-baby]

The bundled **Nether is Piglin-safe**; the **Overworld, End and Primordial Caves are not**. Plan an enclosure before transporting one through a [portal](../blocks/NetherPortals.md), because the resulting Zoglin has broader attack targets and loses the Hoglin feeding/repellent behavior. This is a dimension-type property, so custom dimension data can differ. [Nether type][nether-type] · [Overworld type][overworld-type] · [End type][end-type] · [Primordial type][primordial-type]

## Drops

An **adult** Hoglin's bundled table rolls **2–4 Raw Porkchops** and **0–1 Leather** with mob loot enabled. Looting increases each possible maximum by one per level, reaching **7 Porkchops and 4 Leather with Looting III**. These ordinary pools do not require a player-attributed kill. Baby Hoglins fail the inherited ordinary item-loot gate. [Hoglin loot][hoglin-loot] · [Baby/mob-loot gate][animal-loot] · [Looting calculation][looting-count] · [Death dispatch][death]

The meat becomes **Cooked Porkchop** when the table's burning or qualifying loot-smelting-enchantment condition succeeds. Hoglins are **not registered fire-immune**, unlike Zoglins. Follow [Raw Porkchop](../items/RawPorkchop.md) and [Cooked Porkchop](../items/CookedPorkchop.md) for eating and cooking details. [Meat conditions][hoglin-loot] · [Hoglin registration][hoglin-id]

## Notes

Although registered in the monster category, a Hoglin uses animal feeding and age behavior. Its registration also leaves the default **allowed-in-Peaceful** flag enabled, unlike Ghasts, Zoglins and Zombified Piglins. That means the common unconditional Peaceful-removal branch does not remove a Hoglin; it is not a promise about the availability of natural monster spawning on Peaceful. [Registration][hoglin-id] · [Default flag][peaceful-default] · [Despawn check][despawn]

Related: [Mobs](Mobs.md) · [Nether Fungi](../blocks/NetherFungi.md) · [Piglin](Piglin.md) · [Zoglin](Zoglin.md) · [Bastion Remnant](../structures/BastionRemnant.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `780a7733d1804088e86c248a1c8ec6957def4915`. Checked registration/attributes, loaded biome candidates, decoded Bastion residents, active combat/repellent/breeding behavior, conversion and loot. No in-game spawning, Bastion generation, breeding, transport, Peaceful or drop test was run. Timings assume ticking at 20 TPS; data packs, rules and entity data can differ. [Active AI caller][ai-call]

The checked world-generation registry loads biome and structure resources, and natural spawning reads their active lists before placement and collision checks. Listed candidates are not guaranteed encounters. Living entities take their registered default attributes on construction; entity deaths resolve the species' entity loot table through the reloadable loot registry. [World loader][world-loader] · [Registry entries][registry-list] · [Resource loading][registry-load] · [Spawn-list caller][spawn-selection] · [Biome/structure lookup][spawn-tables] · [Placement checks][spawn-checks] · [Loot identity][loot-id] · [Loot registry][loot-registry] · [Loot loading][loot-load] · [Attribute construction][attribute-call] · [Death loot caller][loot-call]

[hoglin-id]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/EntityType.java#L744-L746
[hoglin-attributes]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L103-L145
[hoglin-food]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/tags/item/hoglin_food.json#L1-L5
[hoglin-repellents]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/tags/block/hoglin_repellents.json#L1-L8
[hoglin-conversion-gate]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L302-L319
[crimson]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/worldgen/biome/crimson_forest.json#L85-L104
[hoglin-placement]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L136
[hoglin-spawn]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L200-L215
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[hoglin-connector]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/structure/bastion/hoglin_stable/small_stables/inner_2.nbt
[hoglin-pool]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/worldgen/template_pool/bastion/mobs/hoglin.json#L1-L27
[hoglin-template]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/structure/bastion/mobs/hoglin.nbt
[pool-place]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/levelgen/structure/pools/SinglePoolElement.java#L137-L181
[entity-place]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplate.java#L480-L522
[hunt-sensor]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/sensing/PiglinSpecificSensor.java#L63-L76
[hunt-activity]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/piglin/PiglinAi.java#L154-L162
[hoglin-egg]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2033
[hoglin-registration]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L182
[hoglin-melee]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/HoglinBase.java#L16-L49
[hoglin-baby]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L189-L198
[hoglin-idle]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/HoglinAi.java#L74-L105
[hoglin-target]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/HoglinAi.java#L168-L175
[hoglin-hurt]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L137-L145
[hoglin-retaliate]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/HoglinAi.java#L191-L231
[hoglin-retreat]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/HoglinAi.java#L140-L165
[hoglin-count]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/HoglinAi.java#L177-L189
[hoglin-sensor]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/sensing/HoglinSpecificSensor.java#L31-L63
[pacify]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/behavior/BecomePassiveIfMemoryPresent.java#L7-L24
[hoglin-breed]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L322-L336
[hoglin-retain]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L218-L239
[hoglin-feeding]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L271-L274
[animal-feed]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/animal/Animal.java#L134-L157
[animal-mating]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/behavior/AnimalMakeLove.java#L49-L85
[animal-birth]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/animal/Animal.java#L194-L228
[age-tick]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[hoglin-conversion]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L162-L178
[hoglin-conversion-result]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/hoglin/Hoglin.java#L267-L269
[conversion-baby]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ConversionType.java#L94-L109
[nether-type]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/dimension_type/the_nether.json
[overworld-type]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/dimension_type/overworld.json
[end-type]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/dimension_type/the_end.json
[primordial-type]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[hoglin-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/loot_table/entities/hoglin.json#L1-L102
[animal-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[looting-count]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L79
[death]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[peaceful-default]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/EntityType.java#L2055-L2068
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
[loot-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527

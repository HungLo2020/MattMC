# Strider

A **Strider** (`minecraft:strider`) is a passive, fire-immune mount for crossing Lava. Equip an adult with a [Saddle](../items/Saddle.md), then hold a [Warped Fungus on a Stick](../items/WarpedFungusonaStick.md) to steer. Keep the animal out of Water and rain, and reach solid ground before dismounting. [Registration][strider-id] · [Equipment gate][strider-equip] · [Control][strider-control] · [Water sensitivity][strider-seats] · [Water damage][water-damage]

## Obtaining

Look in **Lava in the Nether**. The bundled Nether Wastes, Soul Sand Valley, Crimson Forest, Warped Forest and Basalt Deltas biome files all include Striders in the creature category with **weight 60 and group bounds 1–2**. These are selection settings, not a guaranteed number at every Lava pool. The normal world preset selects the Nether biome source containing these five biomes. [Nether Wastes][nether_wastes] · [Soul Sand Valley][soul_sand_valley] · [Crimson Forest][crimson_forest] · [Warped Forest][warped_forest] · [Basalt Deltas][basalt_deltas] · [World preset][normal-preset] · [Parameter data][nether-parameters] · [Biome mapping][nether-biomes]

The live spawn path reads loaded biome/structure lists and applies normal spawn gates. A Strider's placement requires Lava within the world border; its additional predicate scans upward through Lava and requires the first non-Lava cell above to be air. Collision and ordinary population/range rules still apply. Soul Sand Valley also supplies a Strider spawn-cost entry, so biome membership alone is not an assurance of frequent spawning. [Registry loading][registry-load] · [Biome registry][registry-list] · [Table selection][spawn-selection] · [Table lookup][spawn-tables] · [Population and rule gates][spawn-gates] · [Placement registration][spawn-registration] · [Lava placement][lava-placement] · [Surface test][strider-spawn] · [Final spawn checks][natural-spawn] · [Valley cost][soul_sand_valley]

An adult's spawn initialization can attempt a **Zombified Piglin rider on a 1-in-30 roll**, equipping the Strider with a Saddle and the rider with a Warped Fungus on a Stick. If that roll fails, a separate **1-in-10 roll** attempts a baby Strider rider. These are conditional initialization branches, not independent percentages of all animals seen, and a rider occupies the mount's seat. [Jockey initialization][strider-jockey] · [Seat limit][strider-seats]

The listed [Strider Spawn Egg](../items/StriderSpawnEgg.md) is also obtainable through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in Survival and Creative. This is a separate insertion route from natural spawning and breeding. [Egg registration][strider-egg-item] · [Category entry][strider-egg-category]

## Behavior

Striders flee danger, follow a player holding **Warped Fungus or Warped Fungus on a Stick**, and seek nearby Lava. The held stick attracts them but is not their breeding food. Ordinary animals of this type do not despawn merely for being far from a player. This does not prevent wandering or damage. [Goals][strider-goals] · [Tempting items][strider-tempt] · [Food][strider-food] · [Distance retention][animal-retention]

### Breeding and growth

Feed **Warped Fungus** to two ready adults to put them in love mode. Their breeding goal produces a baby Strider; parents then have a **6,000-tick** breeding cooldown. A newborn starts at **−24,000 age ticks** and matures while ticking, about **20 minutes at 20 ticks per second**. Feeding a baby accelerates growth by about **10% of its remaining time**, rounded down to whole seconds by the shared feeding calculation. Feeding is not a source-defined health-recovery action here. [Food tag][strider-food] · [Breeding goal registration][strider-goals] · [Active mating caller][breed-goal] · [Offspring and food dispatch][strider-breeding] · [Feeding][animal-feeding] · [Breeding completion][animal-mating] · [Age progression][aging] · [Age advancement][age-up] · [Default tick rate][tick-rate]

### Heat, cold and Water

The Strider stays warm when its occupied block or supporting block is in `strider_warm_blocks`, or when it is touching Lava. The bundled warm-block tag contains **Lava only**: do not assume Magma Blocks, Fire or a warm biome will substitute. Riding another chilled Strider also makes it chilled. [Temperature update][strider-temperature] · [Warm-block tag][warm-blocks]

Chilling reduces its movement-speed attribute by **34% of the base**, and changes the ridden multiplier from **0.55 to 0.35**. It can still move on land, but much more slowly. Lava provides buoyancy and resets its fall distance while submerged; ordinary falls outside Lava still use the shared fall-damage path. [Cold modifier][strider-cold-modifier] · [Applying the modifier][strider-warm-modifier] · [Ridden speed][strider-riding] · [Buoyancy][strider-temperature] · [Fall handling][strider-fall]

**Water and rain hurt it.** The active water-sensitivity caller attempts 1 point of drowning-type damage while exposed; damage immunity timing can affect the resulting rate. Fire immunity belongs to the animal and is not a promise that an exposed rider or a dismounted player is protected. [Water sensitivity][strider-seats] · [Damage caller][water-damage] · [Fire-immune registration][strider-id]

## Riding and steering

1. Equip a living **adult** with a Saddle; this species has no taming prerequisite. A dispenser can also fill its eligible empty saddle slot
2. Interact without Sneak/Crouch, holding something other than its breeding food, to mount an unoccupied saddled Strider
3. Hold a **Warped Fungus on a Stick** in either hand. The first passenger must be a player holding that item to control it
4. Turn to steer; controlled movement supplies a constant forward input. Use the stick to request a boost, subject to its durability and boost rules

It accepts **one passenger**, and rejects boarding while its eyes are in Lava. A baby cannot be saddled through the normal equipment path. See [Saddle](../items/Saddle.md#removing-a-saddle) for shared recovery with Shears and [Warped Fungus on a Stick](../items/WarpedFungusonaStick.md#behavior) for the exact boost and broken-item limits. [Saddle and dispenser slots][strider-equip] · [Mounting][strider-breeding] · [Steering requirement][strider-control] · [Forward input][strider-riding] · [Active ridden dispatch][ridden-dispatch] · [Ridden callback calls][ridden-callbacks] · [Passenger gate][strider-seats] · [Dispenser checks][dispenser-gates]

Use **Sneak/Crouch** to dismount; MattMC's default is **Left Ctrl**, while Shift is Sprint. Its dismount search tries nearby non-Lava standing positions but falls back above the Strider if none works. Plan a landing at the shore instead of treating that search as guaranteed safe recovery over a Lava lake. [Default keys][keys] · [Dismount control][dismount] · [Landing search and fallback][strider-dismount]

## Drops

With mob loot enabled, an adult drops **2–5 String** from its entity loot table. Looting adds a rounded random amount from **0 up to the Looting level**; the table has no player-kill requirement for the base String. Babies are excluded by the shared loot gate. Player-equipped Saddles, and the Saddle installed by the jockey branch, are marked for guaranteed equipment recovery, subject to the same adult/mob-loot gate and equipment-drop prevention effects. Removing the Saddle first is the controllable recovery route. [String table][strider-loot] · [Default loot ID][loot-id] · [Registered loot data][loot-registry] · [Resource loading][loot-loading] · [Looting calculation][looting-count] · [Loot gate][loot-gate] · [Table caller][loot-call] · [Equipping marks a drop][equip-action] · [Jockey Saddle][strider-jockey] · [Equipment-drop checks][equipment-drops]

The shared base animal reward is **1–3 experience points**, before applicable experience modifiers, when adult/player-credit and mob-loot requirements pass. Breeding awards **1–7 experience** when mob loot is enabled. [Animal experience][animal-retention] · [Death experience gate][death-drops] · [Experience modifier dispatch][xp-modifiers] · [Breeding reward][animal-mating]

## Notes

Strider is registered as a **creature**, with a **0.9 × 1.7-block** adult size. It uses the shared **20-health-point (10-heart)** default; its own attribute setup changes movement speed rather than maximum health. [Registration][strider-id] · [Species attributes][strider-attributes] · [Shared health attribute][attribute-base] · [Default value][default-health] · [Active attribute registration][attribute-registration]

## Related pages

- [Warped Fungus on a Stick](../items/WarpedFungusonaStick.md), [Warped Fungus](../items/WarpedFungus.md) and [Saddle](../items/Saddle.md)
- [Happy Ghast](HappyGhast.md), [Horse](Horse.md) and [Transport](../mechanics/Transport.md)
- [Nether](../dimensions/Nether.md), [Strider Spawn Egg](../items/StriderSpawnEgg.md) and [Mobs](Mobs.md)

## Verification scope

Source-reviewed at MattMC commit `bb9a8a060da02b64f23508b77794fb0f79307de4` on 2026-10-02. Registration, loaded spawn data and callers, food, age, equipment, steering, heat/Water behavior and loot were inspected. No in-game spawn, breeding, riding, damage, dismount or loot test was run. Data packs, components and world settings may change these defaults.

[strider-id]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/EntityType.java#L1350-L1352
[strider-equip]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L123-L136
[strider-control]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L190-L196
[strider-seats]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L344-L352
[water-damage]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2863-L2876
[nether_wastes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json
[soul_sand_valley]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/worldgen/biome/soul_sand_valley.json
[crimson_forest]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/worldgen/biome/crimson_forest.json
[warped_forest]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/worldgen/biome/warped_forest.json
[basalt_deltas]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/worldgen/biome/basalt_deltas.json
[normal-preset]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/worldgen/world_preset/normal.json
[nether-parameters]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/worldgen/multi_noise_biome_source_parameter_list/nether.json
[nether-biomes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/biome/MultiNoiseBiomeSourceParameterList.java#L56-L71
[registry-load]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[registry-list]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L125
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L326
[spawn-tables]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[spawn-gates]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L371-L423
[spawn-registration]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L122-L149
[lava-placement]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/SpawnPlacementTypes.java#L21-L23
[strider-spawn]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L95-L105
[natural-spawn]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L244-L266
[strider-jockey]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L424-L461
[strider-egg-item]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/Items.java#L1969
[strider-egg-category]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2098
[strider-goals]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L139-L149
[strider-tempt]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/strider_tempt_items.json
[strider-food]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/item/strider_food.json
[animal-retention]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/Animal.java#L121-L129
[breed-goal]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/goal/BreedGoal.java#L32-L81
[strider-breeding]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L373-L417
[animal-feeding]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/Animal.java#L133-L158
[animal-mating]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/animal/Animal.java#L163-L227
[aging]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L168
[age-up]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/AgeableMob.java#L72-L90
[tick-rate]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/TickRateManager.java#L7-L21
[strider-temperature]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L283-L321
[warm-blocks]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/tags/block/strider_warm_blocks.json
[strider-cold-modifier]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L73-L82
[strider-warm-modifier]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L152-L171
[strider-riding]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L241-L257
[strider-fall]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L274-L281
[ridden-dispatch]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2823-L2837
[ridden-callbacks]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2422-L2441
[dispenser-gates]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3555-L3568
[keys]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/client/Options.java#L551-L565
[dismount]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/player/Player.java#L451-L459
[strider-dismount]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L198-L239
[strider-loot]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/resources/data/minecraft/loot_table/entities/strider.json
[loot-id]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-registry]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L20-L28
[loot-loading]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[looting-count]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L80
[loot-gate]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L561-L567
[loot-call]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1501-L1527
[equip-action]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/item/equipment/Equippable.java#L159-L175
[equipment-drops]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/Mob.java#L813-L838
[death-drops]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[xp-modifiers]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L585-L587
[strider-attributes]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/monster/Strider.java#L324-L326
[attribute-base]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/LivingEntity.java#L324-L336
[default-health]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L57-L59
[attribute-registration]: https://github.com/HungLo2020/MattMC/blob/bb9a8a060da02b64f23508b77794fb0f79307de4/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java

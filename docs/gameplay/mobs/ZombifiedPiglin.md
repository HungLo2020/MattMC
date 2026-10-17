# Zombified Piglin

A **Zombified Piglin** (`minecraft:zombified_piglin`) is normally neutral, but attacking one can bring nearby Zombified Piglins into the fight. Avoid accidental hits when crossing a group or mining around them. It is a different mob from an ordinary [Piglin](Piglin.md) or [Piglin Brute](PiglinBrute.md); gold armor and bartering belong to those guides, not this mob's anger system. [Registration][zombified-id] · [Targets and retaliation][zombified-attributes] · [Shared alert][hurt-alert]

## Obtaining

The bundled biome monster lists include Zombified Piglins in **[Nether Wastes](../biomes/NetherBiomes.md#nether-wastes)** and **[Crimson Forest](../biomes/NetherBiomes.md#crimson-forest)**. **[Nether Fortresses](../structures/NetherFortress.md)** also provide them through the structure monster override and the special Nether-Brick-floor lookup. This makes a Fortress a separate route even where the surrounding biome does not list them. These are spawn candidates, not a promised group at every visit. [Wastes list][wastes] · [Crimson list][crimson] · [Fortress override][fortress] · [Special list][fortress-special] · [Fortress lookup][spawn-selection]

The registered ground-spawn predicate rejects Peaceful and positions directly above **Nether Wart Blocks**. The species' obstruction check rejects intersecting liquid and obstructed space; ordinary natural placement and collision checks still apply. [Placement registration][zombified-placement] · [Species checks][zombified-spawn] · [Natural checks][spawn-checks]

Other checked routes are:

- **Piglin or Piglin Brute conversion** outside a Piglin-safe dimension. Follow their [zombification guidance](Piglin.md#zombification) for the more-than-300-tick timer, reset and immunity conditions. Conversion can preserve carried equipment rather than supplying a fresh default sword. [Conversion caller][piglin-conversion] · [Equipment preservation][conversion-equipment]
- **Lightning striking a Pig** outside Peaceful. The Pig's callback converts it and marks the result persistent. See [Pig](Pig.md) for the animal's separate care and transport rules. [Lightning callback][pig-lightning]
- **Nether Portal random ticks** in a natural dimension, when monster spawning is enabled, a player is close enough, the difficulty-based roll passes, and a valid base position is found. This creates a mob at the portal; it does not require an existing Piglin to walk through. Follow the [portal guide](../blocks/NetherPortals.md#nether-portal) for portal construction and travel. [Portal spawn callback][portal-spawn]

The listed [Zombified Piglin Spawn Egg](../items/ZombifiedPiglinSpawnEgg.md) is separately obtainable through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. [Egg listing][zombified-egg]

## Behavior

### Anger and retaliation

Its normal player-target goal checks whether it is **angry at that player**. A living attacker can trigger retaliation, and the hurt-response goal alerts nearby members of the same mob class that lack a target and are not allied to the attacker. Do not treat an isolated-looking hit as a one-mob encounter. [Target goals][zombified-attributes] · [Immediate retaliation alerts][hurt-alert]

While it has a target, a separate alert check repeats at sampled **4–6 second intervals**. The caller must see its target; recipients are idle Zombified Piglins in a box expanded by its current follow range horizontally and **10 blocks vertically**, excluding those allied to the target. Spawn modifiers can change follow range, so this is not a fixed safe-radius rule. [Alert timer][zombified-anger] · [Repeated alert conditions][zombified-alert] · [Inherited follow range][zombie-attributes] · [Spawn modifiers][zombie-spawn-bonus]

Persistent anger is sampled for **20–39 seconds of ticking**, but the countdown **does not decrease while it has a player as its active target**. Standing nearby for 39 seconds is therefore not a reliable way to end the fight. Angry adults also gain a movement-speed modifier. Break the encounter and protect your escape route rather than relying on the nominal timer alone. [Timer range][zombified-anger] · [Countdown rule][neutral-anger] · [Adult speed change][zombified-alert]

With `universalAnger` enabled, player-caused anger can be shared as anger toward all eligible players. With `forgiveDeadPlayers` enabled, a dead player's matching anger target can be cleared, but the death notification only reaches nearby neutral mobs within its **32-horizontal/10-vertical-block** box. Neither rule should be read as an unconditional world-wide reset. [Universal-anger goal][universal-anger] · [Neutral checks and forgiveness][neutral-anger] · [Death notification area][forgive-call]

### Equipment, babies and hazards

The inherited default maximum is **20 health points (10 hearts)** before spawn modifiers or custom data. Ordinary spawn initialization equips a **Golden Sword**. Its base attack attribute is **5**, but equipment, difficulty, enchantments and modifiers affect actual damage. Do not mistake that attribute for the final damage of the sword attack. [Registered attributes][zombified-registration] · [Species attributes][zombified-attributes] · [Inherited attributes][zombie-attributes] · [Base living attributes][living-attributes] · [Mob inheritance][mob-attributes] · [Health default][health-default] · [Sword][zombified-equipment] · [Possible spawn bonuses][zombie-spawn-bonus]

Spawn initialization can create babies, including possible chicken jockeys. Babies use the Zombie baby flag and speed adjustment, not the animal aging/feeding system; they do not grow up through feeding. No ordinary gold-barter interaction is provided by this class. [Spawn initialization][zombie-initialization] · [Baby flag and speed][baby-zombie] · [Species behavior][zombified-attributes]

Zombified Piglins are **fire-immune** and explicitly disable the Zombie's water-conversion route. They do not become Drowned by staying submerged. Their registration is disallowed in Peaceful, and ordinary persistence does not override that removal. They also inherit the Zombie's Turtle Egg attack goal when `mobGriefing` allows it; neutrality toward a player does not make them safe around a [turtle nesting area](Turtle.md). [Fire and difficulty flags][zombified-id] · [Water-conversion rejection][zombified-attributes] · [Despawn ordering][despawn] · [Inherited egg goal][zombie-goals] · [Egg-destruction rule][turtle-egg-rule]

## Drops

With mob loot enabled, the bundled table independently rolls **0–1 [Rotten Flesh](../items/RottenFlesh.md)** and **0–1 [Gold Nugget](../items/GoldNugget.md)**. Neither ordinary pool requires a player-attributed kill. Looting increases each possible maximum by one per level, up to **4 of each with Looting III**. Babies pass the Monster item-loot gate too. [Entity table][zombified-loot] · [Looting calculation][looting-count] · [Monster gate][monster-loot]

A separate **Gold Ingot** roll requires player attribution: **2.5% without Looting**, rising by **1 percentage point per Looting level** to **5.5% with Looting III** in the bundled table. This is an additional chance, not a guaranteed conversion of the nugget drop into an ingot. [Ingot conditions][zombified-loot] · [Player-attribution test][player-kill] · [Death context][loot-call]

Carried equipment is separate from those item pools. A normal spawned Golden Sword has the ordinary **8.5% base equipment-drop chance** with recent player attribution, increased by **1 percentage point per player Looting level**. Prevent-equipment-drop effects can block it, ordinary damageable drops receive randomized wear, and picked-up/preserved equipment may use different chances. Converted mobs can carry different gear. [Equipment drop path][equipment-drop] · [Default chance][drop-chance] · [Looting equipment effect][looting-equipment] · [Death dispatch][death]

## Notes

The entity registration uses the monster category, despite its neutral player-target behavior. Its default body is **0.6 blocks wide and 1.95 blocks tall**, with a separate baby form. Piglin bartering, Brute hostility and Hoglin conversion are separate systems; follow [Piglin](Piglin.md), [Piglin Brute](PiglinBrute.md) and [Hoglin](Hoglin.md) for those encounters. [Registration][zombified-id] · [Baby dimensions][zombified-attributes]

Related: [Mobs](Mobs.md) · [Nether biomes](../biomes/NetherBiomes.md) · [Nether Fortress](../structures/NetherFortress.md) · [Nether portals](../blocks/NetherPortals.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `780a7733d1804088e86c248a1c8ec6957def4915`. Checked registration/attribute inheritance, loaded biome and Fortress candidates, conversion/portal/lightning callers, anger and group alerts, baby/equipment initialization, fire/water behavior and loot. No in-game spawn-rate, anger-propagation, conversion, portal, combat or drop test was run. Game rules, data packs, entity data and ticking can change outcomes; no farm design or safe waiting distance is certified. [Active AI dispatch][ai-call]

The checked world-generation registry loads biome and structure resources, and natural spawning reads their active lists before placement and collision checks. Listed candidates are not guaranteed encounters. Living entities take their registered default attributes on construction; entity deaths resolve the species' entity loot table through the reloadable loot registry. [World loader][world-loader] · [Registry entries][registry-list] · [Resource loading][registry-load] · [Spawn-list caller][spawn-selection] · [Biome/structure lookup][spawn-tables] · [Placement checks][spawn-checks] · [Loot identity][loot-id] · [Loot registry][loot-registry] · [Loot loading][loot-load] · [Attribute construction][attribute-call] · [Death loot caller][loot-call]

[zombified-id]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/EntityType.java#L1585-L1595
[zombified-attributes]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/ZombifiedPiglin.java#L72-L91
[hurt-alert]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/goal/target/HurtByTargetGoal.java#L60-L115
[wastes]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/worldgen/biome/nether_wastes.json#L79-L109
[crimson]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/worldgen/biome/crimson_forest.json#L85-L104
[fortress]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/worldgen/structure/fortress.json#L4-L40
[fortress-special]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/levelgen/structure/structures/NetherFortressStructure.java#L17-L24
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L335
[zombified-placement]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L160-L162
[zombified-spawn]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/ZombifiedPiglin.java#L167-L176
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[piglin-conversion]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/piglin/AbstractPiglin.java#L77-L106
[conversion-equipment]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ConversionType.java#L39-L47
[pig-lightning]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/animal/Pig.java#L209-L225
[portal-spawn]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/block/NetherPortalBlock.java#L68-L89
[zombified-egg]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2125
[zombified-anger]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/ZombifiedPiglin.java#L51-L59
[zombified-alert]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/ZombifiedPiglin.java#L94-L145
[zombie-attributes]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L124-L130
[zombie-spawn-bonus]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L545-L563
[neutral-anger]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/NeutralMob.java#L45-L89
[universal-anger]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/goal/target/ResetUniversalAngerTargetGoal.java#L24-L53
[forgive-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/server/level/ServerPlayer.java#L929-L935
[zombified-registration]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L276
[living-attributes]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L326-L341
[mob-attributes]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Mob.java#L159-L161
[health-default]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L57-L59
[zombified-equipment]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/ZombifiedPiglin.java#L215-L218
[zombie-initialization]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L470-L515
[baby-zombie]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L180-L201
[despawn]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[zombie-goals]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Zombie.java#L105-L111
[turtle-egg-rule]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/ai/goal/RemoveBlockGoal.java#L36-L49
[zombified-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/loot_table/entities/zombified_piglin.json#L1-L90
[looting-count]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L79
[monster-loot]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/monster/Monster.java#L125-L133
[player-kill]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[loot-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Mob.java#L814-L838
[drop-chance]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/DropChances.java#L9-L13
[looting-equipment]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/resources/data/minecraft/enchantment/looting.json#L6-L26
[death]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[ai-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/Mob.java#L633-L662
[world-loader]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-list]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[registry-load]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[spawn-tables]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[loot-id]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-registry]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L20-L28
[loot-load]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[attribute-call]: https://github.com/HungLo2020/MattMC/blob/780a7733d1804088e86c248a1c8ec6957def4915/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L274

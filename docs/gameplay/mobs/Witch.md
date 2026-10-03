# Witch

A **Witch** (`minecraft:witch`) is a hostile potion thrower with **26 base health points (13 hearts)**. It can poison, slow or weaken a player, then use Harming potions; it also drinks defensive potions and can heal other raiders. Bring cover and a way to handle effects instead of assuming that fire or a long exchange will finish the fight. [Identity][witch-id] · [Registered attributes][witch-registration] · [Health][witch-stats] · [Potion selection][witch-throw] · [Drinking][witch-drinking]

## Obtaining

Witches are candidates in many bundled biome monster lists, including **Plains, Forest, Swamp and Mangrove Swamp**. They are not exclusive to huts or wetlands. Normal spawning uses the registered ground placement and the monster difficulty, darkness, support and collision checks. These lists establish possible encounters, not a Witch at every dark location. [Plains][witch-plains] · [Forest][witch-forest] · [Swamp][swamp] · [Mangrove Swamp][mangrove] · [Placement registration][witch-placement] · [Monster rules][monster-spawn]

Other checked routes are:

- **Swamp Huts.** The structure's start tag names the ordinary Swamp biome. The hut piece attempts to place a persistent Witch once, while its separate piece-bounds monster override allows further Witch spawn attempts under the normal checks. A replaced hut-shaped building does not acquire the generated structure's bounds merely by looking the same. [Biome eligibility][hut-biomes] · [Structure definition][hut-data] · [Piece creation][hut-start] · [Resident placement][hut-piece] · [Active override lookup][spawn-tables]
- **Raids.** Witches are part of the raid roster; the active wave caller creates the selected raiders. Follow [Raid waves and difficulty](../mechanics/Raid.md#waves-and-difficulty) for counts, random additions and event conditions. [Roster][raid-witch] · [Wave creation][raid-spawn]
- **Lightning striking a Villager** outside Peaceful. The callback converts the Villager to a persistent Witch. This is a transformation, not a trading interaction. [Lightning conversion][witch-lightning]

The category-listed [Witch Spawn Egg](../items/WitchSpawnEgg.md) is separately available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. [Egg listing][witch-egg]

## Behavior

### Which potion it throws

The ranged goal pursues a target and requires visibility when firing. It uses a **60-tick attack interval**, about three seconds at 20 TPS, but drinking and other conditions can delay a throw. The configured 10-block radius controls its approach behavior; it is not a hard promise that standing just beyond ten blocks prevents every throw. [Ranged goal][witch-goals] · [Goal's active range and visibility handling][ranged-call]

Against a non-raider target, the checked choice order is below. The distance is the horizontal aiming separation, including the target's current movement:

| First matching condition | Potion selected |
| --- | --- |
| Horizontal distance at least 8 blocks, target lacks Slowness | Slowness |
| Otherwise, target has at least 8 health points and lacks Poison | Poison |
| Otherwise, distance at most 3 blocks, target lacks Weakness, and a 25% roll passes | Weakness |
| Otherwise | Harming |

The earlier row takes priority; a nearby healthy player can still receive Poison before the Weakness roll is considered. These are regular splash potions, and splash strength/duration depends on the affected target's distance from the impact. The table is not a fixed sequence or a guaranteed full-duration effect. [Choice and projectile creation][witch-throw] · [Splash application][splash-effects]

Use the [effects guide](../effects/Effects.md) for removal and [Poison](../effects/Poison.md) for its low-health cutoff. Poison's cutoff does not protect you from the next potion or another enemy. [Brewing](../brewing/Brewing.md) owns player-made potion recipes; a Witch's combat choices are a separate system.

### Drinking and damage resistance

A Witch can choose **Water Breathing** when its eyes are underwater, **Fire Resistance** when burning or after fire damage, **Healing** when injured, or **Swiftness** for a target more than 11 blocks away when it lacks Speed. The checks include separate random rolls and run in that priority order. Protection is not instantaneous or unconditional. [Drinking selection][witch-drinking]

While drinking, it holds the potion, applies a **−0.25 movement-speed modifier**, and does not perform its potion throw. Completion applies that potion's effects, empties the hand and removes the speed modifier. This offers a change in its attack behavior, not immunity from other nearby mobs. [Drinking modifier][witch-slow] · [Completion][witch-drinking] · [Throw gate][witch-throw]

Damage attributed to the Witch itself is set to zero in its checked damage-absorption path. For damage tagged as **magic, indirect magic, Sonic Boom or Thorns**, it multiplies the remaining damage by **0.15**, an **85% reduction** at that stage. This is not general resistance to every weapon or hazard, and it is not innate fire immunity. [Damage handling][witch-resist] · [Exact resistance tag][witch-resist-tag] · [Entity registration][witch-id]

### During a raid

With an active raid, its healing-target goal can choose another raider, excluding Witches. It throws **Healing** at a raider with at most 4 health points, otherwise **Regeneration**, then clears that target. The healing-goal cooldown also temporarily disables its player-target goal. A Witch supporting a group can prolong the encounter, so keep it in view. It cannot be a raid leader. [Target setup][witch-goals] · [Healing target conditions][raid-heal] · [Ally potion choice][witch-throw] · [Player-target cooldown][witch-drinking] · [Leader rejection][witch-leader]

## Drops

With mob loot enabled, the bundled table gives **4–8 Redstone Dust**, with Looting increasing the possible maximum by one per level, up to **11 with Looting III**. A separate pool makes **1–3 selections** among Glowstone Dust, Sugar, Spider Eyes, Glass Bottles, Gunpowder and Sticks. Each selected entry supplies **0–2** before Looting; Sticks have weight 2 and the others weight 1. Multiple selections can choose the same item. [Redstone pool][witch-redstone] · [Random ingredient pool][witch-random-loot] · [Looting calculation][looting-count]

Those table pools do not require player attribution. A potion currently held for drinking is instead **equipment loot**: its ordinary base chance is **8.5%** with recent player attribution, with the bundled player Looting effect adding one percentage point per level. Finishing the drink empties that slot, and equipment-drop prevention can block the drop. Do not expect a potion from every Witch. [Held potion and emptying][witch-drinking] · [Equipment path][equipment-drop] · [Base chance][drop-chance] · [Looting equipment effect][looting-equipment] · [Mob-loot gate][monster-loot] · [Death dispatch][death]

## Notes

The Witch is registered in the monster category and is disallowed in Peaceful. The shared removal check runs before ordinary persistence checks, including for a hut resident or converted Villager. [Registration][witch-id] · [Despawn ordering][despawn]

Related: [Mobs](Mobs.md) · [Raids](../mechanics/Raid.md) · [Brewing](../brewing/Brewing.md) · [Effects](../effects/Effects.md) · [Jungles and swamps](../biomes/JunglesAndSwamps.md)

## Sources and verification

Source-reviewed on **2026-10-02** at `f9e4a4c96fcf7fb744bce869c085cdfab189810e`. Checked registration/attributes, biome and hut routes, raid/lightning creation, active potion goals, resistance and loot/equipment handling. No in-game spawning, raid, potion-timing, resistance or drop test was run. Rules, data packs, entity state and ticking can alter results. [Server AI dispatch][ai-call]

World loading reads the bundled biome/structure resources, and the natural-spawn caller selects their active lists before placement checks. Registered attributes are applied when living entities are constructed; deaths load the species' entity loot table from the reloadable registry. [World loader][world-loader] · [Registry inputs][registry-list] · [Resource loading][registry-load] · [Spawn selection][spawn-selection] · [Structure/biome lookup][spawn-tables] · [Natural checks][spawn-checks] · [Attribute construction][attribute-call] · [Loot identity][loot-id] · [Loot registry][loot-registry] · [Loot loading][loot-load] · [Death loot caller][loot-call]

[witch-id]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntityType.java#L1502-L1510
[witch-registration]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L268
[witch-stats]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Witch.java#L105-L107
[witch-throw]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Witch.java#L208-L242
[witch-drinking]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Witch.java#L109-L158
[witch-plains]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/biome/plains.json#L183-L188
[witch-forest]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/biome/forest.json#L188-L194
[swamp]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/biome/swamp.json#L195-L213
[mangrove]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/biome/mangrove_swamp.json#L164-L182
[witch-placement]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/SpawnPlacements.java#L152
[monster-spawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L83-L119
[hut-biomes]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/worldgen/biome/has_structure/swamp_hut.json#L1-L5
[hut-data]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/worldgen/structure/swamp_hut.json#L1-L29
[hut-start]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/structures/SwampHutStructure.java#L15-L27
[hut-piece]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/levelgen/structure/structures/SwampHutPiece.java#L97-L109
[spawn-tables]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/chunk/ChunkGenerator.java#L426-L451
[raid-witch]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/raid/Raid.java#L802-L807
[raid-spawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/raid/Raid.java#L499-L535
[witch-lightning]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/npc/Villager.java#L772-L787
[witch-egg]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2117
[witch-goals]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Witch.java#L59-L74
[ranged-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/goal/RangedAttackGoal.java#L70-L98
[splash-effects]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/projectile/ThrownSplashPotion.java#L38-L74
[witch-slow]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Witch.java#L45-L49
[witch-resist]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Witch.java#L194-L206
[witch-resist-tag]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/tags/damage_type/witch_resistant_to.json#L1-L8
[raid-heal]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/ai/goal/target/NearestHealableRaiderTargetGoal.java#L24-L39
[witch-leader]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Witch.java#L245-L252
[witch-redstone]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/entities/witch.json#L160-L191
[witch-random-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/loot_table/entities/witch.json#L1-L159
[looting-count]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L64-L79
[equipment-drop]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/Mob.java#L814-L838
[drop-chance]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/DropChances.java#L9-L13
[looting-equipment]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/resources/data/minecraft/enchantment/looting.json#L6-L26
[monster-loot]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/monster/Monster.java#L125-L133
[death]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1487
[despawn]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/Mob.java#L606-L630
[ai-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/Mob.java#L633-L662
[world-loader]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/WorldLoader.java#L36-L44
[registry-list]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L96-L110
[registry-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/resources/RegistryDataLoader.java#L310-L335
[spawn-selection]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L315-L335
[spawn-checks]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L245-L265
[attribute-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L274
[loot-id]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066
[loot-registry]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/level/storage/loot/LootDataType.java#L20-L28
[loot-load]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[loot-call]: https://github.com/HungLo2020/MattMC/blob/f9e4a4c96fcf7fb744bce869c085cdfab189810e/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1502-L1527

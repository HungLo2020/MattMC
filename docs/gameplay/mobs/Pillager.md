# Pillager

The **Pillager** (`minecraft:pillager`) is a hostile crossbow user found at [Pillager Outposts](../structures/PillagerOutpost.md), in patrols and in [raids](../mechanics/Raid.md). It attacks players, villagers and Iron Golems. Use solid cover and protect villagers before approaching a group. [Registration][pillager-registry] · [Targeting][pillager-ai]

## Obtaining

### Outposts and raids

Outposts provide a natural-spawn selection inside their full recorded structure bounds; the [outpost guide](../structures/PillagerOutpost.md#pillager-spawning-and-a-safer-approach) owns the loaded generation and lighting conditions. Raids explicitly create Pillagers as wave members, and wave 5 can also place them on Ravagers. These are separate routes from ordinary biome spawn lists. [Structure override][outpost] · [Natural selection][natural-selection] · [Raid creation and riders][raid-spawn]

### Patrols

The Overworld server installs a patrol spawner. Its caller requires mob spawning enabled and passes the hostile-spawning setting; the patrol code additionally requires `doPatrolSpawning`. It begins considering patrols when day time divided by 24,000 is at least 5 and it is bright outside. Each scheduled check has a one-in-five random gate, followed by player, village-distance, loaded-area, biome and placement checks. The interval is 12,000–13,199 ticks between scheduled checks, **not a guaranteed patrol arrival time**. Mushroom Fields is the bundled no-patrol biome. [Overworld installation][patrol-init] · [Caller gates][patrol-tick] · [Server dispatch][patrol-server] · [Patrol checks][patrol-spawner] · [Excluded biome][patrol-biomes]

A successful patrol starts with a designated captain and attempts followers according to local difficulty. Its placement uses block light no higher than 8 and non-Peaceful difficulty, so daylight patrols are expected by this source path. [Leader/followers][patrol-spawner] · [Light test][patrol-captain] · [Difficulty rule][light-rule]

### Spawn egg and item browser

The ordinary [Pillager Spawn Egg](../items/PillagerSpawnEgg.md) is listed in the item category, so MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) can insert it in Creative. Using it creates a hostile Pillager, subject to the egg's placement and difficulty checks; this does not establish a natural spawn route. [Egg listing][eggs-list] · [Browser assembly][browser-list] · [Client request][browser-client] · [Server handling][browser-server] · [Egg use][egg-use]

## Behavior

Pillagers have **24 health points (12 hearts)**. Their initial equipment includes a Crossbow; the ranged goal moves toward a target until it has an appropriate firing position, charges, waits another 20–39 ticks and fires when line of sight is available. Briefly breaking sight can delay a shot without forcing a loaded crossbow to start charging again. Avoid standing exposed while assuming every pause is a fresh reload. [Health/goal registration][pillager-ai] · [Starting Crossbow][pillager-equip] · [Firing state machine][crossbow-ai] · [Attribute registration][attributes]

A patrol's hold-ground behavior can make a group pause while facing a player; do not interpret that pause as neutrality. The mob still has hostile player/villager/golem targets. Nearby eligible raiders may join an active raid, and raid membership adds persistence until removed from that raid. [Targeting][pillager-ai] · [Joining][raider-join] · [Persistence][raider-save]

Higher raid omen levels increase a raid equipment-enchantment chance. For Pillagers, the selected raid buff uses Quick Charge I after wave 3 and Quick Charge II after wave 5. Not every Pillager in those waves receives it. Use the [raid guide](../mechanics/Raid.md#waves-and-difficulty) for the wave and omen relationship. [Buff application][pillager-buff] · [Raid odds][raid-counts] · [First provider][quick-one] · [Later provider][quick-two]

## Captains and ominous bottles

A captain must have both the patrol-leader flag and the matching ominous banner equipped on its head. The patrol spawner explicitly appoints its first member; ordinary non-patrol/non-event/non-structure spawn finalization has a **6% leader choice** for eligible mobs, including naturally spawned outpost Pillagers. [Captain identity][raider-identity] · [Patrol leader caller][patrol-spawner] · [Natural-spawn finalization][natural-finalize] · [Leader selection/banner][patrol-captain]

The Pillager loot table gives **one Ominous Bottle with level I–V** when its captain predicate passes. That predicate also requires `has_raid=false`, even though the JSON omits the field: false is the codec default. At the time it is checked, the mob must have neither an assigned raid nor a nearby active raid recognized by the server. A captain fighting inside an active raid therefore is not the ordinary bottle-farming route. [Bottle table][pillager-loot] · [Default and predicate comparison][captain-predicate] · [Assigned/nearby test][raider-identity]

The bottle pool has no player-kill condition and no Looting count increase, but the normal mob-loot game rule still gates it. Killing the captain does **not** directly apply Bad Omen in the checked death callback; drinking the dropped bottle does. Carrying a bottle or banner is not the effect. See [Raid](../mechanics/Raid.md#starting-or-avoiding-a-raid) before consuming it near villagers. [Death callback][raid-death] · [Loot rule][light-rule] · [Loot dispatch][loot-dispatch] · [Drink effect][bottle-effect]

## Drops

Besides the conditional bottle pool, equipment drops follow separate rules:

- **Crossbow:** the standard equipment chance is **8.5%**, subject to recent player-attributed damage and the equipment-drop conditions; a dropped ordinary Crossbow is damaged. The bundled Looting enchantment adds one percentage point per level to applicable player equipment-drop chances
- **Captain's banner:** leader finalization assigns it a preserved drop chance of 2.0, which passes the ordinary random roll and does not need the recent-player condition. Mob loot and equipment-drop prevention still apply
- **No normal Emerald pool:** the bundled Pillager table contains only the conditional bottle pool; do not expect ordinary guards to yield Emeralds from that table

[Starting equipment][pillager-equip] · [Base chance][drop-chances] · [Equipment conditions][equip-drops] · [Looting data][looting] · [Captain banner][patrol-captain] · [Entity loot table][pillager-loot]

## Notes

The crossbow's projectile behavior is distinct from the mob's base attack-damage attribute; this page does not label that attribute as guaranteed arrow damage. [Combat](../mechanics/Combat.md) and [defensive items](../mechanics/DefensiveItems.md) own shared damage and blocking rules.

Related: [Pillager Outpost](../structures/PillagerOutpost.md) · [Raid](../mechanics/Raid.md) · [Ravager](Ravager.md) · [Ominous Bottle](../items/OminousBottle.md) · [Mobs](Mobs.md)

## Sources and verification

Source-reviewed at `79f20bccc697135bd56a472c64f59981dce47fe0` on 2026-10-02. Followed registration/attributes, patrol and raid callers, outpost natural-spawn selection, captain predicate defaults, entity loot and equipment drops. No patrol, outpost spawning, captain drop, crossbow or raid gameplay test was run.

[pillager-registry]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/EntityType.java#L1049-L1058
[pillager-ai]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Pillager.java#L67-L89
[outpost]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/worldgen/structure/pillager_outpost.json
[natural-selection]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L290-L326
[raid-spawn]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L499-L565
[patrol-init]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/MinecraftServer.java#L414-L422
[patrol-tick]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L370-L408
[patrol-server]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/level/ServerLevel.java#L460-L464
[patrol-spawner]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/levelgen/PatrolSpawner.java#L21-L93
[patrol-biomes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/tags/worldgen/biome/without_patrol_spawns.json
[patrol-captain]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/PatrollingMonster.java#L60-L99
[light-rule]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Monster.java#L115-L132
[eggs-list]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2061-L2072
[browser-list]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/client/gui/screens/inventory/JeiPanel.java#L73-L108
[browser-client]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/client/multiplayer/MultiPlayerGameMode.java#L483-L490
[browser-server]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1882-L2029
[egg-use]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L50-L103
[pillager-equip]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Pillager.java#L155-L181
[crossbow-ai]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/ai/goal/RangedCrossbowAttackGoal.java#L70-L127
[attributes]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L211-L224
[raider-join]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raider.java#L86-L108
[raider-save]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raider.java#L182-L243
[pillager-buff]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/monster/Pillager.java#L235-L257
[raid-counts]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raid.java#L716-L777
[quick-one]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/enchantment_provider/raid/pillager_post_wave_3.json
[quick-two]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/enchantment_provider/raid/pillager_post_wave_5.json
[raider-identity]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raider.java#L150-L164
[natural-finalize]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/level/NaturalSpawner.java#L193-L212
[pillager-loot]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/loot_table/entities/pillager.json
[captain-predicate]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/advancements/critereon/RaiderPredicate.java#L13-L31
[raid-death]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/raid/Raider.java#L115-L134
[loot-dispatch]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1525
[bottle-effect]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/item/component/OminousBottleAmplifier.java#L21-L38
[drop-chances]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/DropChances.java#L9-L15
[equip-drops]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/java/net/minecraft/world/entity/Mob.java#L813-L839
[looting]: https://github.com/HungLo2020/MattMC/blob/79f20bccc697135bd56a472c64f59981dce47fe0/src/main/resources/data/minecraft/enchantment/looting.json

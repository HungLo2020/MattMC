# Phantom

The **Phantom** is a flying hostile mob that circles and swoops at players. **MattMC disables insomnia spawning by default:** the `doInsomnia` game rule starts as `false`. A world that enables it can produce phantoms around players who have gone too long without resting. [Default rule][phantom-default-rule]

## Obtaining

### Insomnia encounters

The special Phantom spawner is installed for the **Overworld**. Other dimensions receive no such spawner in the normal level setup. It needs `doMobSpawning`, `spawnMonsters`, and `doInsomnia` enabled, and a difficulty other than Peaceful. [Overworld setup][overworld-spawner] / [other dimensions][other-dimensions] · [Hostile-spawn flags][spawn-flags] / [tick caller][spawn-tick]

With those settings enabled, an attempt requires:

- A sufficiently dark sky: the spawner checks sky darkening of **at least 5** in the normal skylit Overworld
- A non-spectator player **at or above sea level with open sky overhead**
- More than **72,000 ticks since rest**, just over **60 minutes at 20 ticks per second**, followed by a random roll that becomes more favorable the longer the player stays awake
- A separate local-difficulty roll and a valid empty, fluid-free spawn block

The first active check can run immediately; later checks are roughly **60–119 seconds apart** at 20 ticks per second while the spawner remains active. Successful attempts create **1–2 phantoms on Easy, 1–3 on Normal, or 1–4 on Hard**, 20–34 blocks above the player with up to 10 blocks of offset on each horizontal axis. These are conditional attempts, not guaranteed nightly waves. [Complete spawn checks][phantom-spawner]

Starting to sleep resets the rest timer; the player does not need to finish skipping the night. Death also resets it. The timer advances while the player is not sleeping. Sleeping or keeping a roof overhead prevents that player from qualifying for new insomnia encounters; it does not remove phantoms already present. Creative players are not excluded from the special spawner's player loop, although they are excluded from normal combat targeting. [Rest timer][rest-tick] / [sleep reset][rest-reset] / [death reset][death-reset] · [Spawn loop][phantom-spawner] · [Creative target check][creative-target]

A [Phantom Spawn Egg](../items/PhantomSpawnEgg.md) is another creation route outside Peaceful difficulty. See the [Inventory Browser](../mechanics/InventoryBrowser.md) for item insertion. [Egg registration][phantom-egg] / [use and Peaceful check][egg-use]

## Behavior

Phantoms search for attackable players, preferring the highest eligible player in their search area. They circle above a target, then swoop into contact to attack. Line of sight and visibility affect acquisition. A collision or being hurt can interrupt the swoop. [Target selection and circling][phantom-targets] · [Targeting checks][targeting] · [Swoop attack][phantom-swoop]

**Cats can interrupt an attack.** During a swoop, the phantom periodically checks for living [Cats](Cat.md) in a box extending 16 blocks around itself. Those cats hiss, and a detected cat makes the phantom abandon its target and return to circling. The check does not require the cat to be tamed or sitting. [Cat check][phantom-swoop]

Phantoms can catch fire in daylight. The ignition check requires bright outdoor conditions, enough light, open sky at the eyes, and a successful random roll; water, rain, and current or recent powder-snow contact prevent that check. A successful check ignites the phantom for **8 seconds**. They skip fall-damage handling. [Ignition and fall behavior][phantom-sun] · [Sunburn conditions][sun-conditions]

## Drops

With mob loot enabled and a player credited for the kill, the bundled table gives **0–1 [Phantom Membranes](../items/PhantomMembrane.md)**. Looting raises the possible maximum by its level, giving **0–4 with Looting III**; the extra amount is randomized. The table has no separate fire or size condition. An eligible kill of an ordinary phantom also yields **5 experience points**. [Loot table][phantom-loot] · [Looting calculation][loot-count] · [Player-credit condition][player-loot-condition] / [death and XP caller][loot-xp-caller] · [XP reward][phantom-xp]

## Notes

- Entity ID: `minecraft:phantom`; category: monster; not allowed in Peaceful. Registered size at size 0: **0.9 blocks wide × 0.5 blocks tall**. [Registration][phantom-registry]
- Base health: **20 health points (10 hearts)**, from the registered monster attribute supplier and the shared health default. [Attribute registration][default-attributes] / [defaults][attribute-values] / [constructor][attribute-construction]
- Normal spawn finalization sets size to **0**. A configured size is clamped to **0–64**; each size point scales the dimensions by another **15% of the original dimensions**. Changing the size sets base attack damage to **6 + size**; it does not raise health. [Size update][phantom-size] / [spawn initialization][phantom-finalize] / [dimensions][phantom-dimensions]

**Damage initialization caveat:** source tracing shows a newly created size-0 phantom retaining the shared **2-point base attack attribute**. Setting the already-zero size does not run the size-change callback. Thus `6 + size` is the size-update rule, not a verified universal damage value for every normal spawn. Actual player damage also depends on difficulty and defenses. This initialization path has not been checked in-game. [Monster supplier][monster-attributes] / [attribute default][attribute-values] · [Same-value data updates][synched-set] · [Attack caller][attack-caller] / [player difficulty scaling][player-damage]

Source-reviewed **2026-10-02** at commit `1d7e3e91f2a2694339f78b8673993e98d496ca5f`; no in-game test was performed. The entity's default [loot key][loot-key] resolves through the [runtime loot loader][loader], using [registry directory scopes][loader-scope]. Data packs and saved entity attributes can change the bundled defaults.

Related: [Bat](Bat.md), [Cat](Cat.md), [Bed](../blocks/Bed.md), [Mobs](Mobs.md).

[phantom-egg]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/Items.java#L1924-L1924
[egg-use]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/item/SpawnEggItem.java#L89-L102
[phantom-default-rule]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/GameRules.java#L133-L138
[phantom-spawner]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/levelgen/PhantomSpawner.java#L25-L67
[overworld-spawner]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/MinecraftServer.java#L417-L422
[other-dimensions]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/MinecraftServer.java#L458-L466
[spawn-flags]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/MinecraftServer.java#L1473-L1476
[spawn-tick]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L370-L407
[rest-tick]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L650-L664
[rest-reset]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1214-L1218
[death-reset]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/level/ServerPlayer.java#L917-L923
[phantom-targets]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Phantom.java#L236-L317
[phantom-swoop]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Phantom.java#L463-L525
[targeting]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ai/targeting/TargetingConditions.java#L60-L92
[creative-target]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/player/Player.java#L764-L766
[phantom-sun]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Phantom.java#L139-L150
[sun-conditions]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/Mob.java#L1326-L1336
[phantom-loot]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/resources/data/minecraft/loot_table/entities/phantom.json#L1-L41
[loot-count]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/storage/loot/functions/EnchantedCountIncreaseFunction.java#L65-L80
[player-loot-condition]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/level/storage/loot/predicates/LootItemKilledByPlayerCondition.java#L21-L28
[loot-xp-caller]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L1466-L1527
[phantom-xp]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Phantom.java#L53-L58
[default-attributes]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ai/attributes/DefaultAttributes.java#L201-L207
[attribute-construction]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/LivingEntity.java#L269-L278
[attribute-values]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/ai/attributes/Attributes.java#L9-L59
[monster-attributes]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Monster.java#L121-L123
[phantom-size]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Phantom.java#L78-L104
[phantom-finalize]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Phantom.java#L162-L175
[phantom-dimensions]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/monster/Phantom.java#L220-L225
[phantom-registry]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/EntityType.java#L1017-L1026
[synched-set]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/network/syncher/SynchedEntityData.java#L56-L67
[attack-caller]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/Mob.java#L1292-L1303
[player-damage]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/player/Player.java#L721-L747
[loader]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/server/ReloadableServerRegistries.java#L40-L67
[loader-scope]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/resources/FileToIdConverter.java#L19-L37
[loot-key]: https://github.com/HungLo2020/MattMC/blob/1d7e3e91f2a2694339f78b8673993e98d496ca5f/src/main/java/net/minecraft/world/entity/EntityType.java#L2064-L2066

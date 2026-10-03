# Sunbird

Stay near a **Sunbird** to receive its timed blessing; damaging it can replace the blessing with a curse that interrupts player gliding. Check the [player-movement limits](#player-movement-limits) before relying on the blessing for a flight or a high drop. [Nearby grant][sun-grant] · [Damage response][sun-hurt] · [Active effect][sun-effect]

## Obtaining

Use the [Sunbird Spawn Egg](../items/SunbirdSpawnEgg.md), which is listed in the Spawn Eggs category and available through MattMC's [inventory item browser](../mechanics/InventoryBrowser.md) in **Creative**. [Egg registration][sun-egg] · [Category listing][sun-category]

**Natural spawning is not established in this snapshot.** No Sunbird entry appears in the checked bundled biome/structure spawn data, biome-building code, or spawn-placement registrations. Its always-true spawn helper is not a confirmed route to a wild encounter. The effects below apply when a Sunbird is present. [Biome data][sun-biomes] · [Structure data][sun-structures] · [Biome-building code][sun-biome-code] · [Spawn placements][sun-placements] · [Spawn helper][sun-spawn-helper]

## Behavior

Its installed goals handle flight and looking around, without a conventional melee or player-targeting goal. That does not make attacking it safe: its damage response applies Sunbird Curse. Ordinary feeding is not required for its blessing, and its food check accepts no item. [Goals and food check][sun-goals] · [Damage response][sun-hurt]

## Sunbird Blessing

Effect ID: `minecraft:sunbird_blessing`. [Registration][sun-registry]

Every **100 game ticks / 5 seconds**, the Sunbird checks for players in a box formed by expanding its own bounding box **15 blocks horizontally and 32 vertically** in each direction. A player with **neither Sunbird Blessing nor Sunbird Curse** receives **Blessing I for 600 ticks / 30 seconds**. This is a box, not a 15-block spherical radius. The grant has no food, Beacon, daylight, or line-of-sight requirement. [Grant and exclusions][sun-grant] · [Area][sun-area]

**Staying nearby does not continuously refresh an active blessing.** You must lose the existing effect before a later five-second check can grant it again. There can therefore be a gap around expiry, and a curse blocks the next grant until it ends or is removed. [Presence checks][sun-grant]

Each active server effect tick **resets accumulated fall distance to zero**. Its additional motion rules are:

- While gliding and looking upward more than **10 degrees**, add upward velocity of **0.02 + 0.02 × upward angle / 90**
- When **not gliding**, airborne, descending, and **not crouching**, multiply downward velocity by **0.6**, leaving horizontal velocity unchanged

These are server-side velocity adjustments, not guaranteed ascent or descent speeds for a player; see the integration limit below. Crouching skips the descending-motion adjustment but does not skip the fall-distance reset. Higher stored levels do not strengthen either rule. The effect is distinct from ordinary [Slow Falling](../effects/MovementEffects.md#slow-falling). [Blessing rules][sun-effect] · [Server/client tick split][sun-ticks]

## Sunbird Curse

Effect ID: `minecraft:sunbird_curse`. [Registration][sun-registry]

If the Sunbird accepts damage attributed to a living attacker, it **removes that attacker's Sunbird Blessing** and applies **Curse I for 600 ticks / 30 seconds**. The recipient is the living entity credited as the damage source; merely being near an injured Sunbird is not this trigger. Repeated qualifying hits can refresh the same-level curse timer rather than add durations. [Damage response][sun-hurt] · [Refresh rules][sun-refresh]

On each active server effect tick, the curse **stops a player who is currently gliding**. That gliding flag is synchronized to the player. It does not add a separate block to the glide-start eligibility check; a newly started glide can be interrupted again on a later effect tick. [Curse rule][sun-effect] · [Glide-stop flag][sun-glide-stop] · [Flag synchronization][sun-sync] · [Glide eligibility][sun-glide-eligibility] · [Player glide start][sun-glide-start]

For an airborne recipient, it also adds **−0.2 to vertical velocity** on the server. Players who are **both in Creative and actively using Creative flight** are exempt from that downward adjustment; merely being in Creative is insufficient. Higher levels do not increase the adjustment. This motion change has the player integration limit below. [Curse conditions][sun-effect]

Nearby **Phantoms** have another route: during the Sunbird's scorching cycle they can be ignited for **4 seconds** and receive **Curse I for 200 ticks / 10 seconds**, with repeat applications during that cycle. The bundled scorch-target tag contains **only Phantoms**; it is not a general attack on all undead or all hostile mobs. This route does not require a Phantom to damage the Sunbird first. [Scorch selection and cycle][sun-scorch] · [Exact target tag][sun-targets]

## Player-movement limits

**Do not treat the blessing as a verified player flight boost or smooth slow-fall effect.** Its velocity formulas, and the curse's extra downward velocity, run only on the server. The player client advances their timers without running those formulas, and these effects do not request a self-directed motion update. The normal server player tick restores the tracked player position after simulation; damage and movement corrections are separate update paths. The checked source therefore does not establish sustained client-controlled lift, slower descent, or faster falling from these velocity changes alone. [Effect tick dispatch][sun-ticks] · [Velocity setter][sun-velocity] · [Server player tick][sun-player-tick] · [Client movement handling][sun-player-move] · [Motion packets][sun-motion] · [Tracking excludes self][sun-tracking]

The **server fall-distance reset** and **player glide interruption** are separate active behaviors. Neither establishes general invulnerability, a safe fall after expiry, or protection from gliding into a wall. Plan a safe landing before the timer ends. [Effect rules][sun-effect] · [Effect expiry][sun-ticks] · [Wall-impact damage][sun-wall]

## Clearing and reacquiring

[Milk](../items/MilkBucket.md) removes either Sunbird effect along with other beneficial and harmful effects. A [Honey Bottle](../items/HoneyBottle.md) removes Poison only. After the curse is cleared, a nearby Sunbird can grant its blessing on a later five-second check; Milk itself does not supply the blessing. [Milk][sun-milk] · [All-effects removal][sun-removal] · [Honey][sun-honey] · [Grant check][sun-grant]

## Notes

* This mob is registered as `minecraft:sunbird`.
* Its spawn egg is registered as `minecraft:sunbird_spawn_egg`.
* Its entity registration uses `MobCategory.CREATURE`.
* Its entity class is `EntitySunbird`.
* Its registered size is `0.8, 1.2` blocks.
* This mob comes from bundled Alex's Mobs content integrated into MattMC.

## Sources and verification

Source-reviewed on **2026-10-02** at `8b9173b399a629578a7bf0168e4d3ea32b10e8a6`. Checked registration, active mob/effect ticks, player movement and packet paths, and the bundled Phantom target tag. No in-game spawning, effect, fall, flight, scorching, or network test was run. Server data packs and custom effect sources may change these defaults. This review makes no claim about visible model or particle rendering.

Related: [Status effects](../effects/Effects.md) · [Sunbird Spawn Egg](../items/SunbirdSpawnEgg.md) · [Mobs](Mobs.md)

[sun-registry]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffects.java#L139-L148
[sun-grant]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySunbird.java#L180-L207
[sun-hurt]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySunbird.java#L134-L147
[sun-effect]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/effect/EffectSunbird.java#L13-L56
[sun-egg]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/Items.java#L1952
[sun-category]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L2083
[sun-placements]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/SpawnPlacements.java
[sun-spawn-helper]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySunbird.java#L88-L94
[sun-goals]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySunbird.java#L108-L117
[sun-area]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySunbird.java#L298-L300
[sun-ticks]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L214-L250
[sun-refresh]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/effect/MobEffectInstance.java#L123-L147
[sun-glide-stop]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2381-L2384
[sun-sync]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerEntity.java#L325-L337
[sun-glide-eligibility]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2908-L2919
[sun-glide-start]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/player/Player.java#L1375-L1385
[sun-scorch]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/alexsmobs/entity/EntitySunbird.java#L236-L264
[sun-targets]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/tags/entity_type/sunbird_scorch_targets.json#L1-L5
[sun-velocity]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/Entity.java#L3640-L3653
[sun-player-tick]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L307-L314
[sun-player-move]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L1023-L1115
[sun-motion]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ServerEntity.java#L166-L216
[sun-tracking]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/server/level/ChunkMap.java#L1321-L1372
[sun-wall]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L2411-L2418
[sun-milk]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L64
[sun-removal]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/entity/LivingEntity.java#L943-L952
[sun-honey]: https://github.com/HungLo2020/MattMC/blob/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/item/component/Consumables.java#L16-L20
[sun-biomes]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/worldgen/biome
[sun-structures]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/resources/data/minecraft/worldgen/structure
[sun-biome-code]: https://github.com/HungLo2020/MattMC/tree/8b9173b399a629578a7bf0168e4d3ea32b10e8a6/src/main/java/net/minecraft/world/level/biome

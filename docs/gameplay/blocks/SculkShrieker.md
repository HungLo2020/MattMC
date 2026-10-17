# Sculk Shrieker

**Sculk Shrieker** (`minecraft:sculk_shrieker`) reacts to player-attributed contact or nearby Sensor activation. Whether it can warn players and attempt to summon a [Warden](../mobs/Warden.md) depends on its **`can_summon` state**, difficulty, game rules and warning history. A sound alone does not establish summoning eligibility. [Trigger and response gate][shriek-trigger] · [Summon caller][shriek-response]

## Obtaining and placement

Find Shriekers through [the checked sculk generation routes](Sculk.md#finding-and-collecting-the-family), or grow them through [a Catalyst](Sculk.md#what-spreads). Silk Touch collects one Shrieker; ordinary mining without it gives **5 XP and no item**. The block has hardness and blast resistance **3**, is hoe-mineable and can be waterlogged. [Registration][blocks] · [Hoe tag][hoe] · [Loot][loot-sculk_shrieker] · [Placement, fluid and XP callbacks][shrieker-place]

Ordinary item placement starts from **`can_summon=false`**. The Silk Touch loot table drops a plain item and does not preserve the source block's summoning state. Picking up and replacing a naturally summoning Shrieker therefore does not preserve that capability. Catalyst-grown Shriekers also receive `can_summon=false`; the world-generation spreader and extra-growth placement explicitly create summoning-eligible states. Commands or edited data can set different states, so “player-placed” here means ordinary item placement. [Default state][shrieker] · [Placement][shrieker-place] · [Plain loot][loot-sculk_shrieker] · [Growth-state choice][growth] · [World-generation spreader][spread-settings] · [Extra world-generation Shriekers][patch]

## What triggers a shriek

There are two checked routes:

- **Direct contact:** the ground-contact callback passes a player standing on the block to the Shrieker
- **Sensor event:** its **8-block-radius** listener accepts only `minecraft:sculk_sensor_tendrils_clicking` in the bundled event tag, with a source that can be attributed to a player

Player attribution includes the player, a player controlling the source entity, a player's projectile, or an item entity owned by a player. It does not mean every nearby mob can trigger the same response. Already-shrieking blocks do not restart their shriek. [Ground callback][shrieker] · [Entity ground dispatch][step-dispatch] · [Player attribution and trigger][shriek-trigger] · [Listener checks][shriek-listener] · [Listenable event][shriek-events]

The Sensor route uses the [vibration listener's filtering, occlusion and travel rules](SculkSensors.md#detection-travel-and-cooldown), and its delivery waits for adjacent chunks to tick. A sensor's audible click is not required: a **waterlogged Sensor still emits the tendril event**. Directly standing on a Shrieker uses its contact callback instead of that filtered vibration route; sneaking is not checked by that callback. [Shared listener][listener] · [Delivery gate][delivery] · [Waterlogged Sensor event][sensor-water] · [Direct contact][shrieker]

## Warning levels and summoning

A Shrieker can enter the warning/response path only when all three are true:

1. Its block state has **`can_summon=true`**
2. Difficulty is **not Peaceful**
3. **`doWardenSpawning` is true**, its bundled default

An ineligible Shrieker can still perform its ordinary shriek; this path does not raise the warning level or run the later Warden/Darkness response. That describes this Shrieker's response, not the behavior of an already present Warden or other nearby Shriekers. [Response gate and ordinary shriek][shriek-trigger] · [Game-rule default][rules] · [Response conditions][shriek-response]

For an eligible Shrieker, the warning tracker adds further checks:

- A Warden intersecting the **48 × 48 × 48** search box centered on the Shrieker prevents a new warning; this is a box, not a 48-block radius
- The tracker collects living non-spectator players **less than 16 blocks** from the center, plus the triggering player
- If any of those players has an active warning cooldown, no new warning is issued
- Otherwise, the group's highest warning level increases by one, capped at **4**, and its data is copied to the group

Each successful increase starts a **200-game-tick cooldown**. Warning levels belong to players and can carry between different Shriekers; switching blocks does not start a fresh four-warning sequence. The player tracker reduces its level after its **12,000-tick** no-warning counter reaches the decay threshold, then restarts that counter. These counters advance with player ticking, so they are not offline wall-clock timers. [Tracker gates and sharing][warnings] · [Cooldown and decay counters][warning-tick] · [Active player tick][player-tick]

An accepted shriek lasts **90 game ticks**. Its scheduled completion clears the shrieking state and calls the response. With a positive accepted warning level and the eligibility gates still satisfied, it applies the nearby-player Darkness response; at level **4** it first tries to spawn a Warden. Failed spawning produces a warning reply sound instead. The Darkness caller uses radius **40** and an effect duration of **260 ticks**, subject to its player-effect handling. [Shriek schedule][shriek-trigger] · [Completion callback][shrieker] · [Response and summon threshold][shriek-response] · [Darkness helper][darkness] · [Eligible-player effect handling][darkness-players]

## Why a fourth warning may not spawn a Warden

The actual caller uses **20 spawn attempts**, choosing X/Z offsets from **−5 to +5** around the Shrieker and scanning candidate feet positions from **6 above to 6 below**. It requires an in-border candidate, a full upper collision face below an empty-collision space, and the created Warden's obstruction checks. Those checks reject liquid overlap and require room for the Warden's full collision box. The trigger does not guarantee a valid position exists. [Active caller][shriek-response] · [Attempt and vertical-search loop][spawn] · [Ground strategy][spawn-floor] · [Inherited obstruction check][mob-spawn] · [Warden collision check][warden-space]

This path is a **triggered spawn**, not a normal biome monster roll. The caller itself has no Deep Dark biome, Ancient City, dimension, light-level or ordinary `doMobSpawning` requirement. Natural generation supplies eligible Shriekers, but the spawn decision here is controlled by the actual gates above. [Caller and response gates][shriek-trigger] · [Triggered spawn call][shriek-response] · [Spawn utility][spawn] · [Inherited instance spawn check][pathfinder-spawn] · [Warden target-value override][warden-space]

**Breaking an already shrieking eligible block is not a reliable cancellation method.** Its block entity's removal side effects call the same response routine while the old shrieking state is available. That routine still checks eligibility and a positive warning level; it does not blindly spawn on every break. [Removal response][shriek-response]

## Sources and verification

Source-reviewed on **2026-10-02** at `099b1184d1a7115915d880b436c8f58c1ed5a422`. Checked generation/item states, Silk Touch/XP, contact and Sensor event paths, player attribution, listener registration/ticking, warning sharing/cooldowns, response/removal callbacks and the actual spawn utility and obstruction gates. No in-game shriek, summon, avoidance, harvesting, removal or multiplayer test was run. This page documents the block's trigger path; it does not claim complete Warden behavior coverage or guarantee a safe encounter.

Related: [Sculk family](Sculk.md) · [Sensors](SculkSensors.md) · [Shrieker item](../items/SculkShrieker.md) · [Warden](../mobs/Warden.md) · [Blocks](Blocks.md)

[blocks]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/Blocks.java#L6062-L6096
[darkness]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L393-L396
[darkness-players]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/effect/MobEffectUtil.java#L47-L62
[delivery]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L319-L350
[growth]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkBlock.java#L28-L93
[hoe]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/block/mineable/hoe.json#L1-L23
[listener]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/gameevent/vibrations/VibrationSystem.java#L200-L255
[loot-sculk_shrieker]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/loot_table/blocks/sculk_shrieker.json#L1-L33
[mob-spawn]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/Mob.java#L739-L740
[patch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/levelgen/feature/SculkPatchFeature.java#L21-L77
[pathfinder-spawn]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/PathfinderMob.java#L28-L31
[player-tick]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/server/level/ServerPlayer.java#L569-L579
[rules]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/GameRules.java#L170-L172
[sensor-water]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSensorBlock.java#L210-L238
[shriek-events]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/resources/data/minecraft/tags/game_event/shrieker_can_listen.json#L1-L5
[shriek-listener]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/entity/SculkShriekerBlockEntity.java#L174-L217
[shriek-response]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/entity/SculkShriekerBlockEntity.java#L133-L167
[shriek-trigger]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/entity/SculkShriekerBlockEntity.java#L87-L131
[shrieker]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkShriekerBlock.java#L33-L76
[shrieker-place]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkShriekerBlock.java#L118-L149
[spawn]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/util/SpawnUtil.java#L19-L71
[spawn-floor]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/util/SpawnUtil.java#L93-L95
[spread-settings]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/level/block/SculkSpreader.java#L36-L85
[step-dispatch]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/Entity.java#L847-L853
[warden-space]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/monster/warden/Warden.java#L141-L148
[warning-tick]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/monster/warden/WardenSpawnTracker.java#L26-L55
[warnings]: https://github.com/HungLo2020/MattMC/blob/099b1184d1a7115915d880b436c8f58c1ed5a422/src/main/java/net/minecraft/world/entity/monster/warden/WardenSpawnTracker.java#L64-L118

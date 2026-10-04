# Time, weather, and sleep

Plan outdoor work around **daytime, the weather at your actual location, and the server's sleep rules**. A rainy world does not make every spot wet, and skipping the night does not simulate the hours you skipped. Use this guide to decide whether to wait, find an exposed location, or coordinate with other players.

## Read the day cycle

The server keeps two different clocks:

| Clock | What normally advances it | What it means for play |
| --- | --- | --- |
| **Game time** | One count per normal world tick | The continuously advancing world clock used by scheduled block/fluid ticks and other tick-based systems |
| **Day time** | One count per normal world tick while `doDaylightCycle` is enabled | The day number and position within the day; sleep and time commands can change it separately |

Turning off daylight cycling stops ordinary **day-time** advancement, not game time or all world activity. Both clocks are saved; reopening a world loads their saved values. [Clock advancement][clock-tick] · [Scheduled ticks][level-tick] · [Saved clocks][saved-time] · [Saving world state][save-state]

An ordinary day-time cycle is **24,000 ticks**. At a steady 20 ticks per second, that is nominally **20 minutes**; pauses, tick-rate changes and server performance make this a conversion, not a measured waiting time. Useful landmarks are the next day's **0** boundary, **6,000** for noon and **18,000** for midnight. The time command's named `day` and `night` positions are **1,000** and **13,000**. These landmarks do not establish a universal monster-spawn or bed-use threshold. [Day length][day-length] · [Default tick rate][tick-rate] · [Named times][time-command]

Bed entry checks the world's calculated outdoor brightness, which includes time and weather; it is not a test of light beside the bed. Strong enough storm darkening can therefore permit daytime rest, while ordinary rain is not a promise that a bed will accept you. Mob spawning has its own species and location checks: see [Natural spawning](NaturalSpawning.md). For a portable Overworld dial, use a [Clock](../items/Clock.md); for a circuit, use a [Daylight Detector](../blocks/DaylightDetector.md). [Bed admission][sleep-entry] · [Outdoor brightness][brightness] · [Sky darkening][sky-darkening]

## Three settings to check

| Game rule | Ordinary default | Effect |
| --- | --- | --- |
| `doDaylightCycle` | `true` | Advances day time normally and permits the sleep-triggered jump to the next day |
| `doWeatherCycle` | `true` | Advances the natural weather timers and permits the sleep-triggered weather reset |
| `playersSleepingPercentage` | `100` | Chooses how many players must sleep deeply enough for that level's wake-up/skip check |

These are separate controls. You can have moving weather with a stationary sun, or a moving sun with natural weather changes disabled. Disabling weather cycling does not itself clear an existing storm; the rain/thunder intensities can still finish fading toward the stored weather state. Existing saves load their own rule values, and special debug-world settings can differ from the ordinary defaults. [Rule definitions][rules] · [Rule loading][rule-load] · [World settings][settings-load] · [Debug defaults][debug-defaults] · [Weather update][weather-tick]

Use [Commands](../commands/Commands.md#reading-before-changing) for permission requirements and the difference between querying and changing a rule. Time and weather commands alter shared world state; they are not personal display controls. [Active command registration][commands] · [Rule query/change][rule-command] · [Time changes][time-command] · [Weather command scope][weather-command]

## Rain, thunder, and the place you stand

Weather has separate stored **rain** and **thunder** states, with separate timers. Thunder is not guaranteed during every rain spell. Natural advancement runs in dimension types with skylight and depends on `doWeatherCycle`; a positive clear-weather timer temporarily holds both states off. [Weather update][weather-tick]

At the gameplay-query boundary, a level counts as raining only if its type has **skylight and no ceiling**, and its rain intensity is above **0.2**. It counts as thundering only with those same dimension properties and an effective thunder intensity above **0.9**; that effective value includes rain intensity. Changes fade rather than switching every weather-dependent check at the instant a stored flag changes. [Weather predicates and intensities][weather-queries] · [Fading][weather-tick]

For **rain at a specific block**, more conditions must pass:

1. The level must currently count as raining
2. The position must see the sky and must not be below its column's motion-blocking height
3. Its biome must allow precipitation
4. The local, height-adjusted temperature must choose **rain**, rather than snow

A roof, a dry biome, or snowy conditions can therefore stop a mechanic that specifically requires rain at that position even while the world is in a rainy state. Snow is precipitation, but it does **not** satisfy the shared `isRainingAt` check. Check the position used by the particular mechanic, such as above a fishing bobber. [Position checks][weather-queries] · [Biome precipitation and height][biome-weather] · [Rain/snow temperature boundary][biome-temperature]

The natural timer samples below are **inclusive source ranges in ticks**, not a storm forecast or tested wall-clock frequency. Rain and thunder count down independently, and saved timers, clear-weather periods, sleep, commands and rules can change what happens next. [Timer providers][weather-ranges] · [Inclusive sampling][uniform-int] · [Timer transitions][weather-tick] · [Saved weather][saved-time]

| Timer sampled when a new interval is needed | Source range |
| --- | ---: |
| Rain-off interval | 12,000–180,000 ticks |
| Rain-on interval | 12,000–24,000 ticks |
| Thunder-off interval | 12,000–180,000 ticks |
| Thunder-on interval | 3,600–15,600 ticks |

## Dimensions and what the client shows

**Stored time is shared; the sky is dimension-dependent.** The active server creates the Overworld as the clock owner. Its other levels read the Overworld's game time, day time and stored weather through a wrapper whose time/weather setters do nothing. A dimension's fixed-time property still chooses its sky angle instead of the advancing day-time value. Moving to another dimension does not give you an independent clock. [Level creation][level-creation] · [Shared data wrapper][derived-data] · [Fixed-time angle][dimension-time]

The server keeps rain/thunder intensities on each level and applies that level's dimension and biome checks. The bundled Nether and Primordial Caves types fail the shared rain/thunder predicate because they have no skylight and have ceilings. **The End type explicitly has skylight and no ceiling**, so do not assume every weather-dependent check is disabled there. Its five ordinary biomes forbid precipitation; see [End biomes](../biomes/EndBiomes.md) and the [dimension guides](../dimensions/Dimensions.md) for their environmental limits. [Weather predicates][weather-queries] · [Nether type][nether-type] · [Primordial Caves type][primordial-type] · [End type][end-type] · [End biome data][end-biome] · [Highlands][end-highlands] · [Midlands][end-midlands] · [Barrens][end-barrens] · [Small Islands][end-islands]

Do not use matching sky appearance or visible drops as proof that two dimensions have identical weather effects. Routine time and weather-intensity updates are sent to players in the relevant dimension, but rain-state transitions also send weather events to **all connected players**. The client applies these events to its current level; its active weather-rendering path then uses rain intensity, loaded terrain heights and local biome precipitation to prepare rain/snow columns for the Rust renderer. That display path is distinct from the server's position-based gameplay test. Cross-dimension visual transitions have not been tested in game for this guide. [Time synchronization][time-sync] · [Weather packet scope][weather-packets] · [Broadcast destinations][broadcast] · [Client time][client-time] · [Client weather events][client-weather] · [Active render caller][render-caller] · [Weather extraction][weather-render] · [Rust submission][weather-submit]

## Coordinate multiplayer sleep

The count uses **the players in the current server level**, so a player in another dimension does not raise the Overworld's requirement. In this count, “active” means **not a Spectator**; it does not mean recently moving or typing. Creative players count too. The required number is the active-player count multiplied by `playersSleepingPercentage`, divided by 100, **rounded up**, with a **minimum of one sleeper**. [Per-level player membership][players-in-level] · [Sleep counting][sleep-count]

For example, three active players at 50% need **two** sleepers. With the ordinary 100% setting, all active players in that level are needed. A setting of 0 still needs one; 101 asks for more sleepers than the ordinary active population can supply. [Threshold calculation][sleep-count] · [Skip-setting message][sleep-message]

Lying in bed is not yet the final threshold. The server separately requires the necessary number to be **still sleeping after at least 100 player ticks asleep**, nominally five seconds at 20 TPS. A player who gets up before then does not pass that deep-sleep test. Follow [Beds](../blocks/Bed.md) for safe dimensions, placement, respawn setting and reasons entry may be refused. [Deep-sleep count][sleep-count] · [Sleep timer][sleep-timer] · [Start/stop and deep-sleep test][sleep-state] · [Active player ticking][player-tick]

Actually entering sleep also resets your personal **time since rest**, without waiting for the group to skip the night. The [Phantom guide](../mobs/Phantom.md) explains how that statistic relates to its separately configured spawning route. [Rest reset][rest-reset]

### What a successful Overworld sleep changes

When both counts pass, the server:

- Targets the **next multiple of 24,000** in day time if `doDaylightCycle` is enabled
- Wakes the sleeping players in that level
- Resets the rain/thunder timers to zero and their stored states to off if `doWeatherCycle` is enabled **and the level currently counts as raining**; the visible intensities then fade through their normal update

These effects are checked separately. With daylight cycling off, the group can wake without moving the sun. With weather cycling off, skipping to morning does not clear the storm. The reset is not a promise of a particular clear-weather duration. Sleeping elsewhere is still subject to that dimension's bed rules and the shared-data wrapper; it does not provide a separate writable clock or weather schedule. [Skip and wake order][level-tick] · [Wake targets][wake] · [Weather reset][weather-reset] · [Intensity update][weather-tick] · [Other-level setters][derived-data]

**Sleep does not replay the skipped ticks.** The jump changes day time, not game time. For example, Wheat growth still needs its random-tick opportunities, and the shared ageable-mob growth timer still advances through actual entity updates. Scheduled block/fluid ticks continue to use game time. Do not expect sleeping until morning, changing day time, or leaving an area unloaded to finish arbitrary crops, breeding/growth timers or other machinery. A particular system can react to the new daylight or date, so use that system's own guide rather than treating every timer alike. [Clock and scheduled-tick order][level-tick] · [Block-tick caller][chunk-tick] · [Random-tick dispatch][random-tick] · [Wheat registration][wheat] · [Crop example][crop-tick] · [Entity eligibility][entity-tick] · [Ageable-mob timer][mob-age]

## Choose the relevant follow-up

| What you want to do | Detailed guide |
| --- | --- |
| Sleep safely or set a respawn point | [Beds](../blocks/Bed.md) |
| Read time indoors or build a light-sensitive circuit | [Clock](../items/Clock.md) · [Daylight Detector](../blocks/DaylightDetector.md) |
| Use rain while fishing | [Fishing: weather and waiting](Fishing.md#waiting-weather-and-enchantments) |
| Use Riptide or Channeling | [Trident](../items/Trident.md) |
| Redirect natural lightning | [Lightning Rods](../blocks/LightningRods.md) |
| Understand accumulating snow or freezing water | [Snow](../blocks/Snow.md) · [Ice](../blocks/Ice.md) |
| Find a suitable climate or understand mob encounters | [Biomes](../biomes/Biomes.md) · [Natural spawning](NaturalSpawning.md) |
| Inspect or change world settings | [Commands](../commands/Commands.md) |

## Sources and verification

Source-reviewed on **2026-10-04** at `78e8e0423084f010bb47e36132550619b37644c2`. The review followed saved settings, active server-level construction and ticking, sleep entry/counting, command registration, packet destinations and the client weather-extraction route. No game commands, multiplayer sleep, weather waits, crop-growth runs or cross-dimension visual tests were performed. Tick conversions and timer ranges are source-derived; data packs, saved settings and altered dimension/biome definitions can change the result.

Related: [Mechanics](Mechanics.md) · [Gameplay](../Gameplay.md)

[clock-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L443-L458
[level-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L324-L368
[saved-time]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/PrimaryLevelData.java#L165-L184
[day-length]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/SharedConstants.java#L144-L147
[tick-rate]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/TickRateManager.java#L8-L34
[time-command]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/TimeCommand.java#L12-L77
[sleep-entry]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1168-L1210
[brightness]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/Level.java#L357-L362
[sky-darkening]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/Level.java#L639-L644
[rules]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameRules.java#L72-L181
[rule-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/GameRules.java#L276-L318
[settings-load]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/LevelSettings.java#L27-L38
[debug-defaults]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/SharedConstants.java#L125-L175
[weather-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L662-L722
[commands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/commands/Commands.java#L200-L249
[rule-command]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/GameRuleCommand.java#L13-L44
[weather-command]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/commands/WeatherCommand.java#L17-L66
[weather-queries]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/Level.java#L827-L876
[biome-weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/Biome.java#L110-L132
[biome-temperature]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/biome/Biome.java#L181-L187
[weather-ranges]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L182-L185
[uniform-int]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/util/valueproviders/UniformInt.java#L17-L57
[level-creation]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L406-L474
[derived-data]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/DerivedLevelData.java#L18-L113
[dimension-time]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/dimension/DimensionType.java#L144-L155
[nether-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/the_nether.json
[primordial-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/primordial_caves.json
[end-type]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/dimension_type/the_end.json
[end-biome]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/the_end.json#L34
[time-sync]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/MinecraftServer.java#L1297-L1310
[weather-packets]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L724-L743
[broadcast]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/players/PlayerList.java#L521-L533
[client-time]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/ClientLevel.java#L361-L372
[client-weather]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/multiplayer/ClientPacketListener.java#L1532-L1584
[render-caller]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/GameRenderer.java#L875-L880
[weather-render]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/client/renderer/WeatherEffectRenderer.java#L62-L107
[weather-submit]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/vulkanic/world/RustGalWorldPrimitiveRenderer.java#L14857-L14934
[players-in-level]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L1882-L1922
[sleep-count]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/players/SleepStatus.java#L9-L49
[sleep-message]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L626-L645
[sleep-timer]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L246-L270
[sleep-state]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/player/Player.java#L1232-L1254
[player-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayer.java#L642-L665
[wake]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L466-L472
[weather-reset]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L746-L752
[chunk-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L338-L408
[random-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L474-L517
[crop-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/CropBlock.java#L77-L95
[entity-tick]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerLevel.java#L388-L424
[mob-age]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/entity/AgeableMob.java#L129-L148

[save-state]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/storage/PrimaryLevelData.java#L234-L250
[end-highlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/end_highlands.json#L33
[end-midlands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/end_midlands.json#L18
[end-barrens]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/end_barrens.json#L18
[end-islands]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/resources/data/minecraft/worldgen/biome/small_end_islands.json#L22
[wheat]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/world/level/block/Blocks.java#L1270-L1277
[rest-reset]: https://github.com/HungLo2020/MattMC/blob/78e8e0423084f010bb47e36132550619b37644c2/src/main/java/net/minecraft/server/level/ServerPlayer.java#L1214-L1218

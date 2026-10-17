# Ticks and chunk activity

Keep the working parts of a farm or machine in active terrain while you use them. Seeing a distant build does not establish that its crops, dropped items or machines are advancing. MattMC separately decides which terrain to keep loaded, which terrain to send to your client, and which gameplay updates to run. [View and tracking][view-distance] · [Simulation range][simulation] · [Chunk update selection][random-selection]

## Game ticks and elapsed time

The ordinary target is **20 game ticks per second**: one tick is nominally **0.05 seconds**, and 100 ticks are nominally five seconds. Those conversions assume the server is running normally and keeping up. Pausing, a changed tick rate or a slow server can change the elapsed time needed for a tick-based process. [Default rate][tick-rate] · [Server timing][server-timing] · [Local pause][integrated]

Game ticks are also distinct from the sun's position. Turning off `doDaylightCycle` stops ordinary day-time advancement, not all world activity. See [Time, weather, and sleep](TimeWeatherAndSleep.md) for the two clocks, sleeping and weather rules. [World clock][clock]

## What kind of tick does the task need?

| Update | What it does | Practical example |
| --- | --- | --- |
| **Random block/fluid tick** | Samples positions in eligible chunk sections; only states that request random ticking receive the callback | [Wheat](../blocks/Wheat.md) can grow after selection, sufficient light and its own growth roll. It has no guaranteed minutes-to-maturity timer. [Selection][random-ticks] · [Registered Wheat][wheat-registration] · [Growth][crop-growth] |
| **Scheduled block/fluid tick** | Queues a particular block or fluid update for a game-time deadline, then waits for the chunk's ticking/readiness checks | [Repeaters](../blocks/RedstoneRepeater.md) schedule delayed state changes. Their delay is separate from random selection. [Queue and eligibility][scheduled] · [Repeater scheduling][repeater] |
| **Entity tick** | Advances an eligible entity, such as a mob or dropped stack | Ordinary dropped-item age increases in this callback. [Entity dispatch][entities] · [Item age][item-age] |
| **Block-entity tick** | Runs the update attached to an eligible device block | [Furnaces](../blocks/Furnace.md) advance fuel/cooking counters; [Hoppers](../blocks/Hopper.md) advance their transfer cooldown. [Device dispatch][devices] · [Furnace ticker][furnace-ticker] · [Cooking][furnace] · [Hopper ticker][hopper-ticker] · [Transfers][hopper] |

The registered default `randomTickSpeed` is **3**. In each eligible 16×16×16 section containing randomly ticking states, this supplies three position samples per game tick, **not three updates for every crop**. Setting it to zero stops these random selections; it does not set the server's overall tick rate or freeze Furnace, Hopper and entity timers. Query the current world's [game rules](GameRules.md) before assuming the default applies. The same setting also controls the loop that attempts ice/snow updates. [Default][random-rule] · [Section size][section-size] · [Random and precipitation loops][random-ticks]

## Loaded, visible and simulated terrain

A chunk covers a **16×16-block horizontal column**. Its data can be loaded without every kind of gameplay update being eligible there. The current server has separate block-ticking and entity-ticking ranges, plus checks that the relevant chunk/entity data is ready. Consequently, one working device at a boundary does not prove every part of a nearby farm is active. [Chunk bounds][chunk-size] · [Range distinction][chunk-levels] · [Scheduled readiness][scheduled-ready] · [Device readiness][device-ready]

- **Render/view distance** controls the terrain requested or supplied for your view. Your effective Render Distance is capped by the distance supplied by the server; chunk sending also respects the server's view limit. Increasing this alone does not increase the server's player simulation range. [Client limit][render-limit] · [Server view limit][view-distance] · [Separate server controls][distance-controls]
- **Simulation distance** changes the server's simulation range around players. In a local world, the integrated server takes the client's Simulation Distance setting. On a dedicated server, `simulation-distance` is a server property, separate from `view-distance`; the registered default for each is 10 chunks. A guest's graphics settings do not replace that server setting. See [Graphics settings](GraphicsAndPacks.md) for Apply/Undo and [Dedicated servers](DedicatedServers.md) for the actual properties file. [Local setting][integrated] · [Dedicated defaults][dedicated-properties] · [Dedicated initialization][dedicated-init] · [Sent client settings][client-settings] · [Simulation update][simulation]

The random-tick pass specifically selects ready chunks in **entity-ticking simulation range**. Scheduled block/fluid ticks use the **block-ticking range** with chunk and entity-data readiness checks; block entities have their own block-ticking/readiness checks. These paths do not all share a single boundary. Ordinary [natural mob spawning](NaturalSpawning.md) adds its own nearby-player and population checks, so active crops do not prove a mob farm can spawn creatures there. [Random selection][random-selection] · [Scheduled gate][scheduled-ready] · [Entity-data readiness][scheduled-data] · [Block-entity gate][devices] · [Device readiness][device-ready] · [Spawning selection][spawning-selection]

## What happens when you leave?

Moving away can remove the player-based reason for an area to tick; other players or another active ticket can keep it eligible. Actual unloading is a separate save/unload process. The useful question is whether **the required update still runs**, rather than whether you can see the build. [Player tickets][simulation] · [Unload process][unload]

- **Crops:** without eligible random ticks, Wheat does not accumulate growth rolls for the time you spend elsewhere. On return it must receive new selections and pass its growth conditions. [Random pass][random-ticks] · [Growth callback][crop-growth]
- **Furnaces and Hoppers:** ordinary cooking/fuel counters and transfer cooldowns advance through their device callbacks. If those callbacks stop, waiting elsewhere does not perform those steps. A Furnace saves its cooking and fuel counters for loading again. [Device gate][devices] · [Furnace counters and saving][furnace] · [Hopper cooldown][hopper]
- **Dropped items:** the ordinary **6,000-tick** age limit advances while the item entity ticks; its age is saved and restored. Unloading does not reset it to a fresh five-minute allowance. Other players can keep the area active, and damage or collection can remove the item sooner. See [Death and respawn](DeathAndRespawn.md) for recovery risks. [Entity dispatch][entities] · [Age limit][item-age] · [Saved age][item-save]

Scheduled work needs a separate distinction: a due tick can wait while a loaded chunk fails eligibility, then run when eligible again. On actual unloading, its chunk queue is removed from active dispatch; serialized ticks store a relative delay and reconstruct it against game time when the chunk starts ticking again. Do not treat a trip away as a guaranteed replay of every missed redstone or fluid step. [Due-queue checks][scheduled] · [Unload cleanup][unload-cleanup] · [Saved delay][saved-delay] · [Restored delay][restored-delay] · [Resume caller][resume-ticks]

## Forced chunks and paused servers

An operator's `/forceload` can provide a separate loading and simulation ticket. It can keep an area eligible away from players once its data is ready, but it does not supply the nearby player required by ordinary natural spawning. `/forceload query` lists the forced chunks in the command's current dimension; it is not a list of every loaded or active chunk. These commands require permission level 2. [Command registration][command-registration] · [Permission and query][forceload-query] · [Active setter][forceload-setter] · [Ticket creation][forced-ticket] · [Ticket properties][ticket-types] · [Spawning selection][spawning-selection]

Chunk tickets do not override a paused server. A paused integrated world takes its pause path; a dedicated server's `pause-when-empty-seconds` setting can also stop world ticking after its empty-player threshold. Its registered default is 60, and the threshold is counted in server ticks. Check that setting before expecting a forced farm to produce while nobody is connected. [Local pause][integrated] · [Empty-server default][empty-default] · [Dedicated setting][empty-setting] · [Empty-server pause][empty-pause]

Related: [Mechanics](Mechanics.md) · [Time, weather, and sleep](TimeWeatherAndSleep.md) · [Game rules](GameRules.md) · [Natural spawning](NaturalSpawning.md) · [Redstone](../redstone/Redstone.md)

## Sources and verification

Source-reviewed on **2026-10-05** at `5218ac875eda9f2c4151ff1a97795f20f5f356cf`. Reviewed the active server/world callers, distance and readiness gates, example block registration/tickers, item-age persistence, scheduled queues, settings and force-load command path. This is source-derived guidance; no live farm, chunk-boundary, unload/reload, command, server-pause or performance test was run. Current native graph implementation and its scoped verification remain in the developer [chunk-loading guides](../../development/world/chunk-loading/index.md).

[view-distance]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ChunkMap.java#L817-L830
[simulation]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/DistanceManager.java#L118-L163
[random-selection]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ChunkMap.java#L946-L955
[tick-rate]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/TickRateManager.java#L7-L33
[server-timing]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/MinecraftServer.java#L833-L865
[integrated]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/server/IntegratedServer.java#L92-L123
[clock]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L443-L453
[random-ticks]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L474-L517
[wheat-registration]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/Blocks.java#L1270-L1280
[crop-growth]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/CropBlock.java#L78-L94
[scheduled]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/ticks/LevelTicks.java#L91-L149
[repeater]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/DiodeBlock.java#L54-L110
[entities]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L377-L420
[item-age]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L108-L175
[devices]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/Level.java#L440-L456
[furnace-ticker]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/AbstractFurnaceBlock.java#L84-L97
[furnace]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/entity/AbstractFurnaceBlockEntity.java#L119-L208
[hopper-ticker]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/HopperBlock.java#L95-L98
[hopper]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java#L97-L129
[random-rule]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/GameRules.java#L81-L83
[section-size]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/core/SectionPos.java#L16-L21
[chunk-size]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/ChunkPos.java#L120-L134
[chunk-levels]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ChunkLevel.java#L40-L69
[scheduled-ready]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L295-L301
[device-ready]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/chunk/LevelChunk.java#L425-L432
[render-limit]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/Options.java#L1750-L1752
[distance-controls]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerChunkCache.java#L545-L551
[dedicated-properties]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/dedicated/DedicatedServerProperties.java#L93-L94
[dedicated-init]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/dedicated/DedicatedPlayerList.java#L15-L18
[spawning-selection]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ChunkMap.java#L932-L944
[unload]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ChunkMap.java#L521-L547
[item-save]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/entity/item/ItemEntity.java#L295-L313
[unload-cleanup]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L964-L967
[saved-delay]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/ticks/ScheduledTick.java#L55-L57
[restored-delay]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/ticks/SavedTick.java#L51-L53
[resume-ticks]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L1739-L1741
[command-registration]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/commands/Commands.java#L198-L206
[forceload-query]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/commands/ForceLoadCommand.java#L35-L126
[forceload-setter]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/commands/ForceLoadCommand.java#L137-L166
[forced-ticket]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/world/level/TicketStorage.java#L391-L398
[ticket-types]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/TicketType.java#L6-L41
[empty-default]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/dedicated/DedicatedServerProperties.java#L112-L115
[empty-setting]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/dedicated/DedicatedServer.java#L847-L848
[empty-pause]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/MinecraftServer.java#L1081-L1111
[scheduled-data]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/server/level/ServerLevel.java#L1781-L1787
[client-settings]: https://github.com/HungLo2020/MattMC/blob/5218ac875eda9f2c4151ff1a97795f20f5f356cf/src/main/java/net/minecraft/client/Options.java#L1632-L1656

# P90 PDW

The P90 PDW is a 50-round TaCZ firearm using 5.7x28mm ammunition. Its five-round BURST mode can repeat while Shoot is held and changes damage, headshots, and spread. [Definition][definition] · [Modes][modes] · [Burst definition][burst]

## Obtaining

Craft **one P90 PDW** at the [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **SMG**, using **8 Gold Ingots, 72 Iron Ingots, 3 Nether Quartz, and 2 Diamonds**. Carry the materials and follow the [workbench procedure](../blocks/TaCZWorkbenches.md#using-a-workbench). [Recipe][recipe] · [Gun group][gun-index] · [Recipe loading][workbench-loading] · [Bench groups][groups]

The gun is registered as `minecraft:p90`, stacks to **1**, and also appears in Creative. A fresh craft starts with **50 loaded rounds**, **AUTO** selected, and no installed attachments. Survival catalog visibility alone does not supply the item; see [inventory-browser limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Registration][registration] · [Creative entry][creative] · [Craft output][workbench-loading] · [Initial ammunition and mode][magazine]

Use only [5.7x28mm AP Bullet](57x28mmAPBullet.md) (`minecraft:57x28`) for reloads. Its [Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table) recipe under **Personal Defense Cartridges** makes **48 rounds** from **15 Copper Ingots, 5 Lapis Lazuli, and 2 Gunpowder**. An installed Ammo Modifier does not replace this carried ammunition. [Ammo recipe][ammo-recipe] · [Bench groups][groups] · [Exact reload item][reload-ammo]

## Usage

Hold the gun in your **main hand**. Default controls are **left mouse: Shoot**, **hold right mouse: Aim**, **R: Reload**, **G: Fire Mode**, and **Z: Refit**. See [TaCZ controls](../mechanics/TaCZFirearms.md#controls) for remapping and restrictions. [Bindings][keys] · [Held-gun input][input]

**G** cycles **AUTO → BURST**, returning to AUTO. AUTO repeats shots while Shoot is held. BURST requests **up to 5 rounds**, limited by loaded ammunition. Holding the default mouse Shoot binding repeats bursts when the trigger cooldown permits. Keyboard repeat can request further triggers when Shoot is remapped; see [firing and fire modes](../mechanics/TaCZFirearms.md#firing-and-fire-modes). [Mode list][modes] · [Mode cycling][magazine] · [Input repetition][input] · [Burst definition][burst]

| Firing path | Configured timing input | Server trigger cooldown |
| --- | --- | --- |
| AUTO | 810 RPM → 74 integer milliseconds | **1 tick** |
| BURST | 600 milliseconds between triggers | **12 ticks** |

The non-BURST interval is calculated as integer `60,000 / RPM`, rounded to the nearest tick from milliseconds / 50, with a one-tick minimum. These are cooldown inputs and gates, not measured sustained firing rates. The burst cooldown begins at the trigger pull; it is not added after the queued rounds. [Cooldown calculation and scheduling][trigger] · [RPM adjustments][rpm-adjustments] · [Server shooting check][server]

With at least two rounds available, BURST schedules a burst using **1200** configured rounds per minute: integer division gives **50 ms**, rounded to **1 tick** for task stamps, nominally at offsets **0, 1, 2, 3, and 4 ticks** for a full burst. Fewer loaded rounds shorten the burst; one remaining round fires immediately without entering the multi-round queue. **These stamps do not guarantee round spacing.** The server may admit future-stamped tasks when it has processing time, allowing rounds to run together or early; this is the shared [#810 scheduling limitation](https://github.com/HungLo2020/MattMC/issues/810). It does not establish that every burst always fires simultaneously. Queued rounds require the player to stay alive, hold the same gun stack, and still have ammunition. [Burst conversion][burst-conversion] · [Queued rounds][trigger] · [Task admission][task-admission] · [Queue][task-queue]

Reload takes **49 ticks** (**2.45 nominal seconds at 20 TPS**), whether the magazine is empty or partly loaded. The selected mode and accepted attachments do not change this server duration. Keep holding the gun until completion; rounds are added then, and existing loaded rounds are retained. Imported feed, cooldown, or animation timings are not substitutes for this completion time. [Definition][definition] · [Fixed reload rule][reload-timing] · [Reload start and completion][reload-start] · [Use completion][use-completion]

Survival draws from the **first matching carried ammunition stack only**, up to the missing capacity, so a small first stack can cause a partial reload despite more total reserve. Creative supplies missing rounds without reserve items. Each fired round still uses one loaded round in both Survival and Creative. There is **no extra chamber round**, and a full or over-capacity gun cannot begin reloading. See [magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Reload supply][reload-ammo] · [Reload gate][reload-start] · [Round use][round]

## Properties

These are the base configuration values; AUTO is the base ballistic state, with BURST differences shown below. **Damage** is the definition fallback; the active distance bands in [Damage Falloff](#damage-falloff) determine the projectile’s damage before headshots and target handling. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats) for nominal speed units. [Definition][definition] · [Curve selection and mode adjustments][ballistics]

| Property | Value |
| --- | --- |
| Ammo | [5.7x28mm AP Bullet](57x28mmAPBullet.md) |
| Magazine size | 50 |
| Extended magazine sizes | 35, 45, 50 declared; unavailable through accepted attachments |
| Fire modes | AUTO, BURST |
| RPM | 810 (configured input; see firing cooldowns above) |
| Damage | 5.5 (definition fallback; active bands below) |
| Pellets per shot | 1 |
| Bullet speed | 310 m/s (nominal) |
| Lifetime | 0.8 seconds at 20 TPS (16 ticks) |
| Pierce | 1 |
| Headshot multiplier | 1.25 in AUTO; 1.0 in BURST |
| Knockback | 0 |

Each round launches **one projectile**. Pierce **1** is consumed by a successful entity hit; an ordinary blocking collision discards the projectile. Knockback is the extra bullet-specific push setting, so zero does not rule out all target movement. Lifetime limits flight even in the last damage band. [Projectile creation][round] · [Hits and knockback][hit] · [Flight and lifetime][flight]

## Accuracy

| Property | AUTO | BURST |
| --- | ---: | ---: |
| Standing | 3.5 | 1.4 |
| Moving | 4 | 1.9 |
| Sneaking | 1.75 | 0 |
| Prone | 1 | 0 |
| Aiming down sights | 0.14 | 0.22 |

BURST subtracts **2.1** from the other pose inputs, clamping at zero, but adds **0.08** to the aimed input. Its aimed spread input is therefore higher than AUTO’s; a zero input in another pose is not a guarantee of a hit. [Bundled spread][accuracy-data] · [Mode values][mode-data] · [Adjustment and clamp][ballistics]

These values are random projectile-spread inputs, not accuracy percentages or recoil scores. Lower inputs reduce the random perturbation. Selection prioritizes completed aiming, then non-swimming low pose, sneaking, moving, and standing. The **Prone** row describes the low-pose branch; it does not make the Crawl key functional. The aimed value applies after the held-aim transition completes. [Pose priority][pose] · [Launch spread][launch] · [Aim gate][input] · [Control limitations](../mechanics/TaCZFirearms.md#controls)

## Damage Falloff

| Distance from shot origin, in blocks | AUTO damage | BURST damage |
| --- | ---: | ---: |
| Below 14 | 5.5 | 6 |
| 14 to below 36 | 4.5 | 5 |
| 36 to below 50 | 4 | 4.5 |
| 50 or farther | 3 | 3.5 |

Each finite threshold is an **exclusive upper boundary**: a hit exactly at that distance uses the next band. Distance is straight-line from the projectile’s starting position to its hit position, and bands change in steps. The final row corresponds to the raw “infinite” threshold only while the projectile remains alive; it does not promise unlimited range. The table gives damage before headshot multiplication and the target’s own damage handling. [Bundled curve][curve-data] · [Distance selection][distance] · [Hit handling][hit] · [Lifetime][flight]

BURST adds **0.5** to each AUTO band but changes the headshot multiplier from **1.25 to 1.0**. It leaves the speed and extra knockback settings unchanged. [Mode values][mode-data] · [Active adjustments][ballistics]

## Attachments

Use [Refit](../mechanics/TaCZFirearms.md#refitting-attachments) to install carried attachments. Compatibility requires both the accepted category and the exact item in the lists below. Imported attachment profiles or category names do not expand this list or prove every advertised effect is active. [Definition][definition] · [Fit checks][fit]

The accepted categories have **no Extended Mag slot**. The declared **35, 45, 50** array does not provide an installation route; capacity remains **50** through normal supported refitting. [Definition][definition] · [Fit checks][fit]

Supported attachment categories: Ammo Modifier, Laser, Muzzle, Scope.

* Scope: [Aimpoint ACRO P-1 Sight](AimpointACROP1Sight.md), [Contender 4x Scope](Contender4xScope.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight](DeltaPointSight.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight](FastFireSight.md), [LPVO 1-6x Scope](LPVO16xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [PK06 Sight](PK06Sight.md), [RMR Mini Red Dot](RMRMiniRedDot.md), [Scout 4-10x Scope](Scout410xScope.md), [sight_p90](sightp90.md), [SRO Mini Red Dot](SROMiniRedDot.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [UH-1 HCOG](UH1HCOG.md)
* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Mirage Silencer](MirageSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [PO-2 "Ptilopsis" Silencer](PO2PtilopsisSilencer.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md), [Wraith Silencer](WraithSilencer.md)
* Laser: [Militech Compact Laser](MilitechCompactLaser.md), [Nightstick Compact laser](NightstickCompactlaser.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

## Notes

The ammunition’s “AP” name and imported armor-ignore values do not establish a special armor-bypass effect in this reviewed projectile path. [Loaded ballistic fields][parse] · [Hit handling][hit]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Registration, recipes, accepted attachments, input/server firing, reload completion, and active ballistic consumers were inspected. This is source review, without in-game crafting, firing-rate, reload, or multiplayer testing.

[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L55
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2694-L2722
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/p90.json#L1-L33
[gun-index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/p90.json#L1-L8
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/ammo/57x28.json#L1-L29
[workbench-loading]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[groups]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L185
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L23
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L125
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L48
[burst]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L15
[burst-conversion]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L32-L43
[trigger]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L83-L143
[rpm-adjustments]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModeAdjustments.java#L5-L22
[server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2224
[task-admission]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/MinecraftServer.java#L1008-L1014
[task-queue]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/util/thread/BlockableEventLoop.java#L87-L128
[round]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[reload-start]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[reload-timing]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L32
[use-completion]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3255-L3270
[reload-ammo]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L261-L305
[magazine]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L308-L348
[ballistics]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L80
[parse]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L155-L222
[curve-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/p90_data.json#L12-L29
[accuracy-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/p90_data.json#L162-L168
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L250
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[mode-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/p90_data.json#L77-L87

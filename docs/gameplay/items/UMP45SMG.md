# UMP45 SMG

The UMP45 SMG is a .45 ACP TaCZ submachine gun with AUTO and two-round BURST modes. It holds 25 rounds before refitting. [Definition][definition] · [Modes][modes] · [Burst definition][burst]

## Obtaining

Craft **one UMP45 SMG** at the [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), under **SMG**, using **5 Gold Ingots, 62 Iron Ingots, and 3 Nether Quartz**. Carry the materials and follow the [workbench procedure](../blocks/TaCZWorkbenches.md#using-a-workbench). [Recipe][recipe] · [Gun group][gun-index] · [Recipe loading][workbench-loading] · [Bench groups][groups]

The gun is registered as `minecraft:ump45`, stacks to **1**, and also appears in Creative. A fresh craft starts with **25 loaded rounds**, **AUTO** selected, and no installed attachments. Survival catalog visibility alone does not supply the item; see [inventory-browser limits](../mechanics/InventoryBrowser.md#mode-and-permission-limits). [Registration][registration] · [Creative entry][creative] · [Craft output][workbench-loading] · [Initial ammunition and mode][magazine]

Use only [.45 ACP Bullet](45ACPBullet.md) (`minecraft:45acp`) for reloads. Its [Ammo Assembly Table](../blocks/TaCZWorkbenches.md#ammo-assembly-table) recipe under **Personal Defense Cartridges** makes **30 rounds** from **10 Copper Ingots and 2 Gunpowder**. An installed Ammo Modifier does not replace this carried ammunition. [Ammo recipe][ammo-recipe] · [Bench groups][groups] · [Exact reload item][reload-ammo]

## Usage

Hold the gun in your **main hand**. Default controls are **left mouse: Shoot**, **hold right mouse: Aim**, **R: Reload**, **G: Fire Mode**, and **Z: Refit**. See [TaCZ controls](../mechanics/TaCZFirearms.md#controls) for remapping and restrictions. [Bindings][keys] · [Held-gun input][input]

**G** cycles **AUTO → BURST**, returning to AUTO. AUTO repeats shots while Shoot is held. BURST requests **up to 2 rounds**, limited by loaded ammunition. Holding the default mouse Shoot binding does not repeat bursts; click again for another trigger pull. Keyboard repeat can request further triggers when Shoot is remapped; see [firing and fire modes](../mechanics/TaCZFirearms.md#firing-and-fire-modes). [Mode list][modes] · [Mode cycling][magazine] · [Input repetition][input] · [Burst definition][burst]

| Firing path | Configured timing input | Server trigger cooldown |
| --- | --- | --- |
| AUTO | 660 RPM → 90 integer milliseconds | **2 ticks** |
| BURST | 350 milliseconds between triggers | **7 ticks** |

The non-BURST interval is calculated as integer `60,000 / RPM`, rounded to the nearest tick from milliseconds / 50, with a one-tick minimum. These are cooldown inputs and gates, not measured sustained firing rates. The burst cooldown begins at the trigger pull; it is not added after the queued rounds. [Cooldown calculation and scheduling][trigger] · [RPM adjustments][rpm-adjustments] · [Server shooting check][server]

With at least two rounds available, BURST schedules a burst using **700** configured rounds per minute: integer division gives **85 ms**, rounded to **2 ticks** for task stamps, nominally at offsets **0 and 2 ticks** for a full burst. Fewer loaded rounds shorten the burst; one remaining round fires immediately without entering the multi-round queue. **These stamps do not guarantee round spacing.** The server may admit future-stamped tasks when it has processing time, allowing rounds to run together or early; this is the shared [#810 scheduling limitation](https://github.com/HungLo2020/MattMC/issues/810). It does not establish that every burst always fires simultaneously. Queued rounds require the player to stay alive, hold the same gun stack, and still have ammunition. [Burst conversion][burst-conversion] · [Queued rounds][trigger] · [Task admission][task-admission] · [Queue][task-queue]

Reload takes **47 ticks** (**2.35 nominal seconds at 20 TPS**), whether the magazine is empty or partly loaded. The selected mode and accepted extended-magazine level do not change this server duration. Keep holding the gun until completion; rounds are added then, and existing loaded rounds are retained. Imported feed, cooldown, or animation timings are not substitutes for this completion time. [Definition][definition] · [Fixed reload rule][reload-timing] · [Reload start and completion][reload-start] · [Use completion][use-completion]

Survival draws from the **first matching carried ammunition stack only**, up to the missing capacity, so a small first stack can cause a partial reload despite more total reserve. Creative supplies missing rounds without reserve items. Each fired round still uses one loaded round in both Survival and Creative. There is **no extra chamber round**, and a full or over-capacity gun cannot begin reloading. See [magazine, reserve, and reloading](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading). [Reload supply][reload-ammo] · [Reload gate][reload-start] · [Round use][round]

## Properties

These are the base configuration values. **Damage** is the definition fallback; the active distance bands in [Damage Falloff](#damage-falloff) determine the projectile’s damage before headshots and target handling. See [reading firearm stats](../mechanics/TaCZFirearms.md#reading-firearm-stats) for nominal speed units. [Definition][definition] · [Curve selection and mode adjustments][ballistics]

| Property | Value |
| --- | --- |
| Ammo | [.45 ACP Bullet](45ACPBullet.md) |
| Magazine size | 25 |
| Extended magazine sizes | 32, 40, 48 with Light I, II, III respectively |
| Fire modes | AUTO, BURST |
| RPM | 660 (configured input; see firing cooldowns above) |
| Damage | 9 (definition fallback; active bands below) |
| Pellets per shot | 1 |
| Bullet speed | 190 m/s (nominal) |
| Lifetime | 0.55 seconds at 20 TPS (11 ticks) |
| Pierce | 1 |
| Headshot multiplier | 1.5 |
| Knockback | 0.1 |

Each round launches **one projectile**. Pierce **1** is consumed by a successful entity hit; an ordinary blocking collision discards the projectile. Knockback is the extra bullet-specific push setting. Lifetime limits flight even in the last damage band. [Projectile creation][round] · [Hits and knockback][hit] · [Flight and lifetime][flight]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 3.25 |
| Moving | 4 |
| Sneaking | 2 |
| Prone | 1.25 |
| Aiming down sights | 0.2 |

These base inputs also apply in BURST. [Bundled spread][accuracy-data] · [Active selection][ballistics]

These values are random projectile-spread inputs, not accuracy percentages or recoil scores. Lower inputs reduce the random perturbation. Selection prioritizes completed aiming, then non-swimming low pose, sneaking, moving, and standing. The **Prone** row describes the low-pose branch; it does not make the Crawl key functional. The aimed value applies after the held-aim transition completes. [Pose priority][pose] · [Launch spread][launch] · [Aim gate][input] · [Control limitations](../mechanics/TaCZFirearms.md#controls)

## Damage Falloff

| Distance from shot origin, in blocks | Damage in every supported mode |
| --- | ---: |
| Below 30 | 9 |
| 30 to below 55 | 7 |
| 55 or farther | 5.5 |

Each finite threshold is an **exclusive upper boundary**: a hit exactly at that distance uses the next band. Distance is straight-line from the projectile’s starting position to its hit position, and bands change in steps. The final row corresponds to the raw “infinite” threshold only while the projectile remains alive; it does not promise unlimited range. The table gives damage before headshot multiplication and the target’s own damage handling. [Bundled curve][curve-data] · [Distance selection][distance] · [Hit handling][hit] · [Lifetime][flight]

The listed bands and **1.5** headshot multiplier apply in every supported mode; this gun has no bundled ballistic fire-mode adjustment. [Bundled data][data] · [Active adjustments][ballistics]

## Attachments

Use [Refit](../mechanics/TaCZFirearms.md#refitting-attachments) to install carried attachments. Compatibility requires both the accepted category and the exact item in the lists below. Imported attachment profiles or category names do not expand this list or prove every advertised effect is active. [Definition][definition] · [Fit checks][fit]

Light Ammo Extended Mag **I / II / III** set capacity to **32 / 40 / 48** rounds. These are replacement capacities, and installation adds no ammunition. Reload after increasing capacity. Before removing one or fitting a smaller capacity, use up excess loaded rounds: refitting preserves the count initially, but the next shot subtracts one and then clamps to the new capacity without refund. [Magazine levels][mag-levels] · [Capacity lookup][mag-size] · [Stored ammunition][magazine] · [Refit storage][refit-storage] · [Round use][round]

Supported attachment categories: Ammo Modifier, Extended Mag, Grip, Laser, Muzzle, Scope.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Contender 4x Scope](Contender4xScope.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md)
* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Mirage Silencer](MirageSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [PO-2 "Ptilopsis" Silencer](PO2PtilopsisSilencer.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md), [Wraith Silencer](WraithSilencer.md)
* Grip: [Hera Arms CQR Grip](HeraArmsCQRGrip.md), [Koch Ranger Heavy Grip](KochRangerHeavyGrip.md), [Nagoma Military Standard Grip](NagomaMilitaryStandardGrip.md), [P-2 Grip](P2Grip.md), [RK-0 Grip](RK0Grip.md), [RK-1 B25U Grip](RK1B25UGrip.md), [RK-6 Grip](RK6Grip.md), [SE-5 Express Grip](SE5ExpressGrip.md), [SI Grip](SIGrip.md), [Talon AFG1 Handstop](TalonAFG1Handstop.md), [Talon SG2 Grip](TalonSG2Grip.md), [TD Grip](TDGrip.md)
* Laser: [Militech Compact Laser](MilitechCompactLaser.md), [Nightstick Compact laser](NightstickCompactlaser.md)
* Extended Mag: [Light Ammo Extended Mag I](LightAmmoExtendedMagI.md), [Light Ammo Extended Mag II](LightAmmoExtendedMagII.md), [Light Ammo Extended Mag III](LightAmmoExtendedMagIII.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

## Notes

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. Registration, recipes, accepted attachments, input/server firing, reload completion, and active ballistic consumers were inspected. This is source review, without in-game crafting, firing-rate, reload, or multiplayer testing.

[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L71
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2694-L2722
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/ump45.json#L1-L27
[gun-index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/ump45.json#L1-L8
[ammo-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/ammo/45acp.json#L1-L23
[workbench-loading]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L174
[groups]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/inventory/TaczWorkbenchMenu.java#L125-L185
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L23
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L125
[modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L66
[burst]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBurstData.java#L21
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
[data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/ump45_data.json#L1-L154
[curve-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/ump45_data.json#L12-L25
[accuracy-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/ump45_data.json#L135-L141
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L162-L250
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[mag-levels]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L139-L141
[mag-size]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L215-L216
[refit-storage]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczRefitGun.java#L16-L82

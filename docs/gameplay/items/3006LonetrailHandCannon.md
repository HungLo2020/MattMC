# .30-06 Lonetrail Hand Cannon

The .30-06 Lonetrail Hand Cannon is a single-round, SEMI-only TaCZ handgun. Crafting gives it one loaded round, but does not install the scope preset written in its recipe. [Definition][definition] · [Output parser][craft] · [Starting ammunition][loaded]

## Obtaining

.30-06 Lonetrail Hand Cannon can be obtained from the Creative Menu. It is registered as `minecraft:lonetrail`. [Registration][registration] · [Creative listing][creative]

At a [Gun Smith Table](../blocks/TaCZWorkbenches.md#gun-smith-table), choose **Pistol** and carry the following materials in your inventory:

**20 Lapis Lazuli + 4 Gold Ingots + 40 Iron Ingots → one .30-06 Lonetrail Hand Cannon.** [Recipe][gun-recipe] · [Pistol group][gun-group]

The gun stacks to **1**. Crafted guns and default Creative instances start with **1 loaded round** and no stored attachments. See [using a workbench](../blocks/TaCZWorkbenches.md#using-a-workbench) for crafting controls and Creative material checks. [Registration][registration] · [Output parser][craft] · [Starting ammunition][loaded]

**The recipe's `scope=minecraft:scope_contender` preset is ignored by the output parser.** Obtain the Contender 4x Scope separately and install it through Refit if wanted; the gun can fire without an optic. [Recipe][gun-recipe] · [Output parser][craft] · [Firing checks][admission]

## Usage

Use [.30-06 Springfield Bullet](3006SpringfieldBullet.md) (`minecraft:30_06`). Hold the gun in your **main hand**: Shoot is left mouse, held Aim is right mouse, Reload is **R**, Fire Mode is **G**, and Refit is **Z** by default. See [TaCZ firearm controls](../mechanics/TaCZFirearms.md#controls) for the shared controls and remapping limits. [Controls][input] · [Bindings][keys]

**SEMI is the only supported mode and the default.** Each default mouse click requests one shot; holding it does not keep firing, and G leaves the mode unchanged. A successful shot immediately creates **one projectile** and spends **one loaded round**, including in Creative. Keyboard repeat can request further shots after a keyboard remap. [Allowed mode][allowed-modes] · [Mode selection][modes-cycle] · [Trigger and firing][cadence] · [Ammunition use][fire] · [Keyboard repeat][keyboard]

The configured **90 RPM** becomes an integer **666 ms** interval, then `max(1, round(666 / 50))` gives a **13-tick trigger cooldown**, enforced by the server. This is a cooldown calculation, not a measured sustained firing rate; clicks, reloading and game ticking still matter. No RPM or ballistic mode adjustment is applied for this gun. [Cooldown][cadence] · [Server checks][server] · [RPM adjustments][rpm-adjust] · [Ballistic inputs][ballistics] · [Gun data][gun-data]

Reload takes **30 ticks (1.50 nominal seconds at 20 TPS)**. With capacity one, reload is available when empty; the full gun cannot start it. [Definition][definition] · [Timing selection][timings] · [Reload checks][reload]

Keep holding the gun until completion: the duration is captured at the start, ammunition arrives at the end, and already loaded rounds are retained. Survival takes up to the missing capacity from the **first matching inventory stack only**. The normal ammo stack limit is **36**; a first stack containing at least **1** round can fill this gun from empty in one completed reload. Creative refills missing capacity without reserve ammunition. [Shared reload behavior](../mechanics/TaCZFirearms.md#magazine-reserve-and-reloading) · [Ammo stack limit][ammo-index] · [Supply][loaded] · [Duration capture][duration] · [Held-item continuity][continuity]

## Properties

| Property | Value |
| --- | --- |
| Ammo | [.30-06 Springfield Bullet](3006SpringfieldBullet.md) |
| Magazine size | 1 |
| Extended magazine sizes | 1, 1, 1 |
| Fire modes | SEMI |
| RPM | 90 |
| Damage | 21.5 |
| Pellets per shot | 1 |
| Bullet speed | 360 m/s |
| Lifetime | 0.8 seconds |
| Pierce | 1 |
| Headshot multiplier | 1.75 |
| Knockback | 0.25 |

Capacity is fixed at **1 round**. The **1, 1, 1** extended-magazine row is a reference array; no Extended Mag, Grip or Stock slot is accepted. [Definition and fit][definition] · [Accepted attachment checks][fit]

The **Damage 21.5** row is the definition fallback. Normal hits use the bundled distance curve, starting at **24 below 45 blocks**, before headshot and target handling. “Pellets per shot: 1” means one projectile for that round. [Curve conversion][ballistics] · [Bundled curve][curve] · [Shot creation][fire]

**Bullet speed 360 m/s** is the nominal conversion of **18 blocks/tick × 20**, treating one block as one metre. Lifetime is **16 game ticks**. Spread, shooter movement, friction, gravity, water and collisions affect flight, so speed × lifetime is not a promised range. [Definition][definition] · [Launch][launch] · [Flight][flight] · [Tick rate][ticks]

**Pierce 1** is a budget of successful entity-damage events, not block penetration or a guarantee of that many different targets. Block impact invokes the ordinary block callback and discards the bullet. **Knockback 0.25** is extra horizontal bullet push, with a 0.03 upward component when a usable horizontal direction exists; it does not guarantee displacement. [Hit handling][hit]

## Accuracy

| Property | Value |
| --- | --- |
| Standing | 1.25 |
| Moving | 2.5 |
| Sneaking | 1.75 |
| Prone | 1 |
| Aiming down sights | 0.11 |

These are spread inputs: lower values reduce random launch perturbation, not percentages or camera recoil. Selection priority is completed precision aim, non-swimming low pose, sneaking, moving, then standing. “Prone” names that low-pose input; it does not establish a working Crawl key. Attachment spread bonuses do not enter this spread calculation, and raw per-gun aim-time fields do not set the shared aim transition. See [shared controls](../mechanics/TaCZFirearms.md#controls). [Bundled inputs][spread] · [Pose selection][pose] · [Spread consumer][ballistics] · [Aim transition][aim] · [Launch][launch]

## Damage Falloff

Each finite distance below is an **exclusive upper threshold**, not damage exactly at that distance:

* 45 blocks: 24
* 75 blocks: 21
* infinite blocks: 18

| Straight-line origin-to-hit distance | Damage per projectile before headshot/target handling |
| --- | --- |
| Below 45 blocks | 24 |
| 45 to below 75 blocks | 21 |
| 75 blocks or farther | 18 |

Equality moves to the next band; values are not interpolated. The terminal “infinite” entry still requires a projectile that survives its lifetime and collisions. [Bundled curve][curve] · [Distance selection][distance] · [Flight][flight]

The **1.75× headshot multiplier** applies to the selected band when a living target is hit at or above its Y position plus 0.85 × eye height. The target's normal damage handling follows, so these values are not guaranteed health loss. Imported `armor_ignore` does not add an armor-bypass bonus in this hit path. [Headshot and damage handling][hit] · [Loaded ballistic fields][parser]

## Attachments

Supported attachment categories: Ammo Modifier, Laser, Muzzle, Scope.

* Scope: [Aimpoint ACRO P-1 Sight Rised](AimpointACROP1SightRised.md), [Contender 4x Scope](Contender4xScope.md), [Coyote Sight](CoyoteSight.md), [DeltaPoint Sight Rised](DeltaPointSightRised.md), [Elcan 4x Scope](Elcan4xScope.md), [EXP3 HCOG](EXP3HCOG.md), [FastFire Sight Rised](FastFireSightRised.md), [HAMR 3x Scope](HAMR3xScope.md), [LPVO 1-6x Scope](LPVO16xScope.md), [Mark 5 HD 5-25x Scope](Mark5HD525xScope.md), [Militech 552 HCOG](Militech552HCOG.md), [OKP-7 Sight](OKP7Sight.md), [PK06 Sight Rised](PK06SightRised.md), [QMK-152 3x White Light Sight](QMK1523xWhiteLightSight.md), [Scout 4-10x Scope](Scout410xScope.md), [T2 red dot](T2reddot.md), [TA31 2x ACOG](TA312xACOG.md), [Trijicon SRS-02 Reflex Sight](TrijiconSRS02ReflexSight.md), [UH-1 HCOG](UH1HCOG.md), [Vudu 1-6x Scope](Vudu16xScope.md)
* Muzzle: [Cthulhu K7 Brake](CthulhuK7Brake.md), [Cyclone D2 Brake](CycloneD2Brake.md), [Knight QD Silencer](KnightQDSilencer.md), [Phantom S1 Silencer](PhantomS1Silencer.md), [Pioneer A3 Brake](PioneerA3Brake.md), [T-Rex Heavy Brake](TRexHeavyBrake.md), [Tempest Trident Compensator](TempestTridentCompensator.md), [Ursus Military Standard Silencer](UrsusMilitaryStandardSilencer.md)
* Laser: [Militech Compact Laser](MilitechCompactLaser.md), [Nightstick Compact laser](NightstickCompactlaser.md)
* Ammo Modifier: [Full Metal Jacket Ammo](FullMetalJacketAmmo.md), [Hollow-Point Ammo](HollowPointAmmo.md), [Incendiary Ammo](IncendiaryAmmo.md)

Compatibility requires the accepted category **and exact attachment ID**; the list above is the complete set for this gun. Make attachments at the [Attachment Table](../blocks/TaCZWorkbenches.md#attachment-table), then follow [Refitting attachments](../mechanics/TaCZFirearms.md#refitting-attachments). Capacity is fixed at **1 round**. The **1, 1, 1** extended-magazine row is a reference array; no Extended Mag, Grip or Stock slot is accepted. [Definition][definition] · [Fit checks][fit]

Accepted Ammo Modifiers do not change the reload cartridge or enable their imported damage, speed, armor, ignition or explosion effects in this reviewed shot path. Attachment fit does not mean every advertised field works; follow the individual owners for effect limits. Scope view and recoil can have active consumers. [Shot inputs][fire] · [Ballistic consumer][ballistics] · [Imported-field parser][parser] · [Recoil parser][recoil] · [Scope view][fov] Installed silencers do not change this path's gun-specific shoot-sound selection. [Shot sound][fire] The linked optic owners carry their existing scoped-view caveats where applicable; compatibility alone does not verify every scoped rendering setup.

## Notes

* This item is part of the integrated TaCZ firearms system.

The imported 2.5-second `bolt_action_time`, movement, weight and melee fields do not establish additional firing delays or working abilities in the reviewed controls and shot path. [Imported gun profile][gun-data] · [Imported reload fields][raw-reload] · [Firing checks][admission] · [Firing path][fire] · [Reload duration][reload] · [Timing selection][timings]

Source-reviewed on **2026-10-04** at `cc140840a21e5c6c932c23abf34124418d6506b0`. This page covers the bundled MattMC definitions, recipe parser, controls, reloads, projectile consumers and attachment fit. No in-game firing, timing, damage, refit or rendering test was run; upstream descriptions and imported metadata alone do not establish working behavior here.

[definition]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunDefinitions.java#L30
[craft]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/crafting/TaczWorkbenchRecipe.java#L108-L180
[loaded]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L254-L331
[registration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/Items.java#L2708-L2744
[creative]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/CreativeModeTabs.java#L1663-L1671
[gun-recipe]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/recipes/gun/lonetrail.json#L1-L30
[gun-group]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/guns/lonetrail.json#L1-L8
[admission]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L83-L99
[input]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczClientInputHandler.java#L36-L124
[keys]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczKeyMappings.java#L13-L23
[allowed-modes]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModes.java#L27
[modes-cycle]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L334-L348
[cadence]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L101-L144
[fire]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L146-L181
[keyboard]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/KeyboardHandler.java#L525-L560
[server]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/server/network/ServerGamePacketListenerImpl.java#L2194-L2267
[rpm-adjust]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunFireModeAdjustments.java#L1-L23
[ballistics]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L40-L87
[gun-data]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/lonetrail_data.json#L1-L193
[timings]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunReloadTimings.java#L7-L31
[reload]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L184-L218
[ammo-index]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/index/ammo/30_06.json#L1-L6
[duration]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3195-L3206
[continuity]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/LivingEntity.java#L3124-L3133
[fit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczMvpGunItem.java#L54-L67
[curve]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/lonetrail_data.json#L19-L32
[launch]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/Projectile.java#L129-L154
[flight]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L99-L134
[ticks]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/TickRateManager.java#L7-L29
[hit]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L136-L250
[spread]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/lonetrail_data.json#L169-L175
[pose]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L224-L238
[aim]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczGlock17AnimationController.java#L13-L61
[distance]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/entity/projectile/TaczBullet.java#L262-L273
[parser]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L151-L222
[recoil]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/world/item/TaczGunBallistics.java#L285-L331
[fov]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/java/net/minecraft/client/tacz/TaczScopeData.java#L27-L86
[raw-reload]: https://github.com/HungLo2020/MattMC/blob/cc140840a21e5c6c932c23abf34124418d6506b0/src/main/resources/data/minecraft/data/guns/lonetrail_data.json#L41-L49
